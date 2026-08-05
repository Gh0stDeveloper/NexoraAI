import { Pool, type QueryResult, type QueryResultRow } from "pg";

const globalDatabase = globalThis as typeof globalThis & {
  nexoraDatabasePool?: Pool;
  nexoraDatabaseSchema?: Promise<void>;
};

const schemaSql = String.raw`
create extension if not exists vector;

create table if not exists chat_jobs (
  id uuid primary key,
  access_token_hash char(64) not null,
  conversation_id text not null,
  project_id text,
  client text not null default 'android',
  request_payload jsonb not null,
  status text not null default 'queued'
    check (status in ('queued', 'processing', 'completed', 'failed')),
  progress jsonb not null default '[]'::jsonb,
  result jsonb,
  error text,
  attempts integer not null default 0,
  created_at timestamptz not null default now(),
  started_at timestamptz,
  completed_at timestamptz,
  updated_at timestamptz not null default now()
);
create index if not exists chat_jobs_status_updated_idx
  on chat_jobs(status, updated_at);
create index if not exists chat_jobs_conversation_idx
  on chat_jobs(conversation_id, created_at desc);

create table if not exists android_build_jobs (
  id uuid primary key,
  access_token_hash char(64) not null,
  device_id uuid not null,
  client_ip_hash char(64) not null,
  app_name text not null,
  package_name text not null unique,
  accent_color char(7) not null,
  source_prompt text not null,
  source_content text not null,
  status text not null default 'queued'
    check (status in ('queued', 'building', 'completed', 'failed', 'expired')),
  progress_label text not null default 'En cola',
  output_path text,
  file_name text,
  sha256 char(64),
  signature_schemes text[] not null default array[]::text[],
  error text,
  attempts integer not null default 0,
  created_at timestamptz not null default now(),
  started_at timestamptz,
  completed_at timestamptz,
  expires_at timestamptz,
  updated_at timestamptz not null default now()
);
create index if not exists android_build_jobs_queue_idx
  on android_build_jobs(status, created_at);
create index if not exists android_build_jobs_device_rate_idx
  on android_build_jobs(device_id, created_at desc);
create index if not exists android_build_jobs_ip_rate_idx
  on android_build_jobs(client_ip_hash, created_at desc);
create index if not exists android_build_jobs_expiry_idx
  on android_build_jobs(expires_at)
  where expires_at is not null;
`;

function databaseUrl(): string {
  const value = process.env.DATABASE_URL?.trim();
  if (!value) {
    throw new Error("DATABASE_URL is required for durable Nexora jobs");
  }
  return value;
}

export function databasePool(): Pool {
  if (!globalDatabase.nexoraDatabasePool) {
    globalDatabase.nexoraDatabasePool = new Pool({
      connectionString: databaseUrl(),
      max: 8,
      idleTimeoutMillis: 30_000,
      connectionTimeoutMillis: 10_000,
      allowExitOnIdle: false,
    });
  }
  return globalDatabase.nexoraDatabasePool;
}

export async function ensureDatabase(): Promise<void> {
  if (!globalDatabase.nexoraDatabaseSchema) {
    globalDatabase.nexoraDatabaseSchema = databasePool()
      .query(schemaSql)
      .then(() => undefined)
      .catch((error) => {
        globalDatabase.nexoraDatabaseSchema = undefined;
        throw error;
      });
  }
  await globalDatabase.nexoraDatabaseSchema;
}

export async function databaseQuery<T extends QueryResultRow = QueryResultRow>(
  text: string,
  values: readonly unknown[] = [],
): Promise<QueryResult<T>> {
  await ensureDatabase();
  return databasePool().query<T>(text, [...values]);
}
