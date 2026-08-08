import { Pool, type QueryResult, type QueryResultRow } from "pg";

const globalDatabase = globalThis as typeof globalThis & {
  nexoraDatabasePool?: Pool;
  nexoraDatabaseSchema?: Promise<void>;
};

const schemaSql = String.raw`
create extension if not exists vector;

create table if not exists app_users (
  id uuid primary key,
  name text not null,
  email text,
  image_url text,
  email_verified_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
alter table app_users
  add column if not exists email_verified_at timestamptz;
create unique index if not exists app_users_email_unique_idx
  on app_users(lower(email))
  where email is not null;

create table if not exists app_auth_accounts (
  id uuid primary key,
  user_id uuid not null references app_users(id) on delete cascade,
  provider text not null check (provider in ('google', 'facebook', 'discord')),
  provider_account_id text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(provider, provider_account_id)
);
create index if not exists app_auth_accounts_user_idx
  on app_auth_accounts(user_id);

create table if not exists app_password_credentials (
  user_id uuid primary key references app_users(id) on delete cascade,
  password_salt text not null,
  password_hash text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists app_auth_link_authorizations (
  id uuid primary key,
  user_id uuid not null references app_users(id) on delete cascade,
  provider text not null check (provider in ('google', 'facebook', 'discord')),
  provider_account_id text not null,
  expires_at timestamptz not null,
  created_at timestamptz not null default now()
);
create index if not exists app_auth_link_authorizations_lookup_idx
  on app_auth_link_authorizations(user_id, provider, provider_account_id, expires_at);
create index if not exists app_auth_link_authorizations_expiry_idx
  on app_auth_link_authorizations(expires_at);

create or replace function nexora_prevent_implicit_auth_link()
returns trigger
language plpgsql
as $$
declare
  explicit_link boolean;
begin
  select exists (
    select 1
      from app_auth_link_authorizations l
     where l.user_id = new.user_id
       and l.provider = new.provider
       and l.provider_account_id = new.provider_account_id
       and l.expires_at > now()
  ) into explicit_link;

  if (
    exists (
      select 1
        from app_password_credentials p
       where p.user_id = new.user_id
    ) or exists (
      select 1
        from app_auth_accounts a
       where a.user_id = new.user_id
         and a.id <> new.id
         and (
           a.provider <> new.provider
           or a.provider_account_id <> new.provider_account_id
         )
    )
  ) and not explicit_link then
    raise exception using
      errcode = '23514',
      message = 'implicit auth account linking is not allowed';
  end if;
  return new;
end;
$$;

drop trigger if exists app_auth_accounts_prevent_implicit_link
  on app_auth_accounts;
create trigger app_auth_accounts_prevent_implicit_link
before insert or update of user_id, provider, provider_account_id
on app_auth_accounts
for each row
execute function nexora_prevent_implicit_auth_link();

create table if not exists app_auth_sessions (
  id uuid primary key,
  user_id uuid not null references app_users(id) on delete cascade,
  access_token_hash char(64) not null unique,
  refresh_token_hash char(64) not null unique,
  access_expires_at timestamptz not null,
  refresh_expires_at timestamptz not null,
  device_name text not null default 'Android',
  user_agent text,
  revoked_at timestamptz,
  created_at timestamptz not null default now(),
  last_used_at timestamptz not null default now()
);
alter table app_auth_sessions
  add column if not exists device_name text not null default 'Android';
alter table app_auth_sessions
  add column if not exists user_agent text;
create index if not exists app_auth_sessions_user_idx
  on app_auth_sessions(user_id, last_used_at desc);
create index if not exists app_auth_sessions_expiry_idx
  on app_auth_sessions(refresh_expires_at);

create table if not exists app_account_codes (
  id uuid primary key,
  user_id uuid not null references app_users(id) on delete cascade,
  purpose text not null check (purpose in ('verify_email', 'reset_password')),
  email text not null,
  code_hash char(64) not null,
  attempts integer not null default 0,
  expires_at timestamptz not null,
  consumed_at timestamptz,
  created_at timestamptz not null default now()
);
create index if not exists app_account_codes_user_purpose_idx
  on app_account_codes(user_id, purpose, created_at desc);
create index if not exists app_account_codes_expiry_idx
  on app_account_codes(expires_at)
  where consumed_at is null;

create table if not exists mobile_oauth_states (
  state_hash char(64) primary key,
  provider text not null check (provider in ('google', 'facebook', 'discord')),
  redirect_uri text not null,
  client_state text not null,
  code_challenge varchar(128) not null,
  expires_at timestamptz not null,
  created_at timestamptz not null default now()
);
create index if not exists mobile_oauth_states_expiry_idx
  on mobile_oauth_states(expires_at);

create table if not exists mobile_auth_codes (
  code_hash char(64) primary key,
  user_id uuid not null references app_users(id) on delete cascade,
  code_challenge varchar(128) not null,
  expires_at timestamptz not null,
  created_at timestamptz not null default now()
);
create index if not exists mobile_auth_codes_expiry_idx
  on mobile_auth_codes(expires_at);

create table if not exists mobile_user_chat_state (
  user_id uuid primary key references app_users(id) on delete cascade,
  revision bigint not null default 1,
  payload jsonb not null default '{"sessions":[],"projects":[]}'::jsonb,
  updated_at timestamptz not null default now()
);

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