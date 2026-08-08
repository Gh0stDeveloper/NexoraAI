import { type NextRequest } from "next/server";
import { authenticateMobileRequest, mobileAuthErrorResponse } from "@/lib/mobile-auth";
import { databaseQuery } from "@/lib/db";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

const MAX_STATE_BYTES = 2_000_000;

type ChatStateRow = {
  revision: string | number;
  payload: unknown;
  updated_at: Date;
};

function validPayload(value: unknown): value is Record<string, unknown> {
  if (!value || typeof value !== "object" || Array.isArray(value)) return false;
  const candidate = value as Record<string, unknown>;
  return Array.isArray(candidate.sessions) && Array.isArray(candidate.projects);
}

export async function GET(request: NextRequest) {
  try {
    const user = await authenticateMobileRequest(request);
    const result = await databaseQuery<ChatStateRow>(
      `select revision, payload, updated_at
         from mobile_user_chat_state
        where user_id = $1
        limit 1`,
      [user.id],
    );
    const row = result.rows[0];
    return Response.json({
      ok: true,
      state: row
        ? {
            revision: Number(row.revision),
            payload: row.payload,
            updatedAt: row.updated_at.toISOString(),
          }
        : {
            revision: 0,
            payload: { sessions: [], projects: [] },
            updatedAt: null,
          },
    });
  } catch (error) {
    return mobileAuthErrorResponse(error);
  }
}

export async function PUT(request: NextRequest) {
  try {
    const user = await authenticateMobileRequest(request);
    const body = (await request.json()) as Record<string, unknown>;
    const payload = body.payload;
    if (!validPayload(payload)) {
      return Response.json({ ok: false, error: "El historial enviado no es válido." }, { status: 400 });
    }
    const serialized = JSON.stringify(payload);
    if (Buffer.byteLength(serialized, "utf8") > MAX_STATE_BYTES) {
      return Response.json({ ok: false, error: "El historial supera el tamaño permitido." }, { status: 413 });
    }

    const result = await databaseQuery<ChatStateRow>(
      `insert into mobile_user_chat_state (user_id, revision, payload, updated_at)
       values ($1, 1, $2::jsonb, now())
       on conflict (user_id)
       do update set
         revision = mobile_user_chat_state.revision + 1,
         payload = excluded.payload,
         updated_at = now()
       returning revision, payload, updated_at`,
      [user.id, serialized],
    );
    const row = result.rows[0];
    return Response.json({
      ok: true,
      state: {
        revision: Number(row.revision),
        payload: row.payload,
        updatedAt: row.updated_at.toISOString(),
      },
    });
  } catch (error) {
    return mobileAuthErrorResponse(error);
  }
}
