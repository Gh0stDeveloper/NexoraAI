import { createHash } from "node:crypto";
import type { AndroidBuildRequest } from "@/lib/android-build-request";
import { databaseQuery } from "@/lib/db";
import { hashRequestToken } from "@/lib/request-token";
type AndroidBuildStatus =
  | "queued"
  | "building"
  | "completed"
  | "failed"
  | "expired";

export type AndroidBuildRow = {
  id: string;
  access_token_hash: string;
  device_id: string;
  client_ip_hash: string;
  app_name: string;
  package_name: string;
  accent_color: string;
  source_prompt: string;
  source_content: string;
  status: AndroidBuildStatus;
  progress_label: string;
  output_path: string | null;
  file_name: string | null;
  sha256: string | null;
  signature_schemes: string[];
  error: string | null;
  attempts: number;
  created_at: Date;
  started_at: Date | null;
  completed_at: Date | null;
  expires_at: Date | null;
  updated_at: Date;
};

export class AndroidBuildAccessError extends Error {
  constructor() {
    super("No se encontró la compilación o el token temporal no es válido.");
    this.name = "AndroidBuildAccessError";
  }
}

export class AndroidBuildQuotaError extends Error {
  readonly retryAfterSeconds: number;

  constructor(message: string, retryAfterSeconds = 3600) {
    super(message);
    this.name = "AndroidBuildQuotaError";
    this.retryAfterSeconds = retryAfterSeconds;
  }
}

export function userAndroidBuildsEnabled(): boolean {
  return (
    process.env.ENABLE_USER_ANDROID_BUILDS === "true" &&
    /^[a-f0-9]{64}$/i.test(process.env.USER_BUILD_RATE_LIMIT_SALT || "")
  );
}

function maxBuildsPerHour(): number {
  const parsed = Number.parseInt(process.env.USER_BUILD_MAX_PER_HOUR || "3", 10);
  return Number.isFinite(parsed) ? Math.min(10, Math.max(1, parsed)) : 3;
}

function maxQueuedBuilds(): number {
  const parsed = Number.parseInt(process.env.USER_BUILD_MAX_QUEUED || "10", 10);
  return Number.isFinite(parsed) ? Math.min(50, Math.max(1, parsed)) : 10;
}

export function hashClientAddress(address: string): string {
  const salt = process.env.USER_BUILD_RATE_LIMIT_SALT;
  if (!salt || !/^[a-f0-9]{64}$/i.test(salt)) {
    throw new Error("USER_BUILD_RATE_LIMIT_SALT is not configured safely");
  }
  return createHash("sha256")
    .update(`${salt}\0${address}`, "utf8")
    .digest("hex");
}

function generatedPackageName(requestId: string): string {
  return `com.ghostnexora.generated.n${requestId.replaceAll("-", "").slice(0, 20)}`;
}

async function selectBuild(
  requestId: string,
  requestToken: string,
): Promise<AndroidBuildRow> {
  await databaseQuery(
    `update android_build_jobs
       set status = 'expired', updated_at = now()
     where id = $1
       and status = 'completed'
       and expires_at <= now()`,
    [requestId],
  );
  const result = await databaseQuery<AndroidBuildRow>(
    `select * from android_build_jobs
      where id = $1 and access_token_hash = $2
      limit 1`,
    [requestId, hashRequestToken(requestToken)],
  );
  const row = result.rows[0];
  if (!row) throw new AndroidBuildAccessError();
  return row;
}

function downloadBaseUrl(): string {
  return (
    process.env.MOBILE_PRODUCTION_API_URL ||
    process.env.NEXT_PUBLIC_API_URL ||
    "https://apighostnexoraai.duckdns.org/"
  ).replace(/\/$/, "");
}

export function publicAndroidBuild(row: AndroidBuildRow, requestToken: string) {
  const downloadable =
    row.status === "completed" &&
    row.expires_at !== null &&
    row.expires_at.getTime() > Date.now();
  return {
    id: row.id,
    appName: row.app_name,
    packageName: row.package_name,
    status: row.status,
    progressLabel: row.progress_label,
    fileName: row.file_name,
    sha256: row.sha256,
    signatureSchemes: row.signature_schemes,
    error: row.error,
    attempts: row.attempts,
    createdAt: row.created_at.toISOString(),
    startedAt: row.started_at?.toISOString() ?? null,
    completedAt: row.completed_at?.toISOString() ?? null,
    expiresAt: row.expires_at?.toISOString() ?? null,
    downloadUrl: downloadable
      ? `${downloadBaseUrl()}/api/mobile/builds/${row.id}/download?token=${encodeURIComponent(requestToken)}`
      : null,
  };
}

export async function createAndroidBuild(
  request: AndroidBuildRequest,
  clientAddress: string,
) {
  if (!userAndroidBuildsEnabled()) {
    throw new AndroidBuildQuotaError(
      "Las compilaciones de usuarios están desactivadas temporalmente.",
      0,
    );
  }

  const existing = await databaseQuery<{ exists: boolean }>(
    `select exists(select 1 from android_build_jobs where id = $1) as exists`,
    [request.requestId],
  );
  if (existing.rows[0]?.exists) {
    return publicAndroidBuild(
      await selectBuild(request.requestId, request.requestToken),
      request.requestToken,
    );
  }

  const ipHash = hashClientAddress(clientAddress);
  const quota = await databaseQuery<{ recent: string; queued: string }>(
    `select
       (select count(*)::text from android_build_jobs
         where created_at > now() - interval '1 hour'
           and (device_id = $1 or client_ip_hash = $2)) as recent,
       (select count(*)::text from android_build_jobs
         where status in ('queued', 'building')) as queued`,
    [request.deviceId, ipHash],
  );
  const recent = Number.parseInt(quota.rows[0]?.recent || "0", 10);
  const queued = Number.parseInt(quota.rows[0]?.queued || "0", 10);
  if (recent >= maxBuildsPerHour()) {
    throw new AndroidBuildQuotaError(
      "Alcanzaste el límite de compilaciones por hora. Inténtalo más tarde.",
    );
  }
  if (queued >= maxQueuedBuilds()) {
    throw new AndroidBuildQuotaError(
      "La cola de compilación está llena. Inténtalo en unos minutos.",
      300,
    );
  }

  await databaseQuery(
    `insert into android_build_jobs (
       id, access_token_hash, device_id, client_ip_hash, app_name,
       package_name, accent_color, source_prompt, source_content
     ) values ($1, $2, $3, $4, $5, $6, $7, $8, $9)`,
    [
      request.requestId,
      hashRequestToken(request.requestToken),
      request.deviceId,
      ipHash,
      request.appName,
      generatedPackageName(request.requestId),
      request.accentColor.toUpperCase(),
      request.sourcePrompt,
      request.sourceContent,
    ],
  );

  return publicAndroidBuild(
    await selectBuild(request.requestId, request.requestToken),
    request.requestToken,
  );
}

export async function getAndroidBuild(
  requestId: string,
  requestToken: string,
) {
  return publicAndroidBuild(
    await selectBuild(requestId, requestToken),
    requestToken,
  );
}

export async function getAndroidBuildForDownload(
  requestId: string,
  requestToken: string,
): Promise<AndroidBuildRow> {
  return selectBuild(requestId, requestToken);
}
