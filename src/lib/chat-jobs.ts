import { runAgent, type AgentProgress, type AgentResult } from "@/lib/agent";
import { databaseQuery } from "@/lib/db";
import {
  mobileChatSchema,
  type MobileChatJobRequest,
  type MobileChatRequest,
} from "@/lib/mobile-chat";
import { hashRequestToken } from "@/lib/request-token";

type ChatJobStatus = "queued" | "processing" | "completed" | "failed";

type ChatJobRow = {
  id: string;
  access_token_hash: string;
  conversation_id: string;
  project_id: string | null;
  client: string;
  request_payload: MobileChatRequest;
  status: ChatJobStatus;
  progress: AgentProgress[];
  result: AgentResult | null;
  error: string | null;
  attempts: number;
  created_at: Date;
  started_at: Date | null;
  completed_at: Date | null;
  updated_at: Date;
};

export class ChatJobAccessError extends Error {
  constructor() {
    super("No se encontró la solicitud o el token no es válido.");
    this.name = "ChatJobAccessError";
  }
}

const globalChatJobs = globalThis as typeof globalThis & {
  nexoraRunningChatJobs?: Map<string, Promise<void>>;
};

const runningJobs =
  globalChatJobs.nexoraRunningChatJobs ?? new Map<string, Promise<void>>();
globalChatJobs.nexoraRunningChatJobs = runningJobs;

function staleMinutes(): number {
  const parsed = Number.parseInt(process.env.CHAT_JOB_STALE_MINUTES || "25", 10);
  return Number.isFinite(parsed) ? Math.min(120, Math.max(10, parsed)) : 25;
}

function publicJob(row: ChatJobRow) {
  return {
    id: row.id,
    conversationId: row.conversation_id,
    projectId: row.project_id,
    client: row.client,
    status: row.status,
    progress: row.progress,
    result: row.result,
    error: row.error,
    attempts: row.attempts,
    createdAt: row.created_at.toISOString(),
    startedAt: row.started_at?.toISOString() ?? null,
    completedAt: row.completed_at?.toISOString() ?? null,
    updatedAt: row.updated_at.toISOString(),
  };
}

async function selectJob(
  requestId: string,
  requestToken: string,
): Promise<ChatJobRow> {
  const result = await databaseQuery<ChatJobRow>(
    `select * from chat_jobs
       where id = $1 and access_token_hash = $2
       limit 1`,
    [requestId, hashRequestToken(requestToken)],
  );
  const row = result.rows[0];
  if (!row) throw new ChatJobAccessError();
  return row;
}

export async function createChatJob(request: MobileChatJobRequest) {
  const { requestId, requestToken, ...payload } = request;
  const tokenHash = hashRequestToken(requestToken);

  await databaseQuery(
    `insert into chat_jobs (
       id, access_token_hash, conversation_id, project_id, client, request_payload
     ) values ($1, $2, $3, $4, $5, $6::jsonb)
     on conflict (id) do nothing`,
    [
      requestId,
      tokenHash,
      payload.conversationId ?? requestId,
      payload.projectId ?? null,
      payload.client,
      JSON.stringify(payload),
    ],
  );

  return publicJob(await selectJob(requestId, requestToken));
}

export async function getChatJob(requestId: string, requestToken: string) {
  return publicJob(await selectJob(requestId, requestToken));
}

async function appendProgress(requestId: string, progress: AgentProgress) {
  await databaseQuery(
    `update chat_jobs
       set progress = progress || $2::jsonb,
           updated_at = now()
     where id = $1 and status = 'processing'`,
    [requestId, JSON.stringify([progress])],
  );
}

async function processChatJob(requestId: string): Promise<void> {
  const claim = await databaseQuery<Pick<ChatJobRow, "request_payload">>(
    `update chat_jobs
       set status = 'processing',
           attempts = attempts + 1,
           started_at = coalesce(started_at, now()),
           error = null,
           updated_at = now()
     where id = $1
       and (
         status = 'queued'
         or (
           status = 'processing'
           and updated_at < now() - make_interval(mins => $2)
         )
       )
     returning request_payload`,
    [requestId, staleMinutes()],
  );
  const row = claim.rows[0];
  if (!row) return;

  try {
    const payload = mobileChatSchema.parse(row.request_payload);
    const result = await runAgent(payload.message, payload.mode, {
      intelligence: payload.intelligence,
      attachments: payload.attachments,
      conversationId: payload.conversationId,
      projectId: payload.projectId,
      validateCode: payload.validateCode,
      onProgress: (progress) => appendProgress(requestId, progress),
    });

    await databaseQuery(
      `update chat_jobs
         set status = 'completed',
             result = $2::jsonb,
             error = null,
             completed_at = now(),
             updated_at = now()
       where id = $1`,
      [requestId, JSON.stringify(result)],
    );
  } catch (error) {
    console.error("[NexoraAI] Durable chat job failed", {
      requestId,
      error: error instanceof Error ? error.message : String(error),
    });
    await databaseQuery(
      `update chat_jobs
         set status = 'failed',
             error = $2,
             completed_at = now(),
             updated_at = now()
       where id = $1`,
      [requestId, "Nexora AI no pudo completar la solicitud."],
    );
  }
}

export function runChatJobInBackground(requestId: string): Promise<void> {
  const existing = runningJobs.get(requestId);
  if (existing) return existing;

  const task = processChatJob(requestId).finally(() => {
    runningJobs.delete(requestId);
  });
  runningJobs.set(requestId, task);
  return task;
}
