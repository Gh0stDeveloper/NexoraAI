import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { after, test } from "node:test";
import {
  databasePool,
  databaseQuery,
  ensureDatabase,
} from "../src/lib/db.ts";

after(async () => {
  await databasePool().end();
});

test("creates durable chat, Android build and mobile account tables in PostgreSQL", async () => {
  await ensureDatabase();
  const tables = await databaseQuery(
    `select table_name
       from information_schema.tables
      where table_schema = 'public'
        and table_name in (
          'app_account_codes',
          'app_auth_sessions',
          'app_users',
          'android_build_jobs',
          'chat_jobs'
        )
      order by table_name`,
  );
  assert.deepEqual(
    tables.rows.map((row) => row.table_name),
    [
      "android_build_jobs",
      "app_account_codes",
      "app_auth_sessions",
      "app_users",
      "chat_jobs",
    ],
  );

  const columns = await databaseQuery(
    `select table_name, column_name
       from information_schema.columns
      where table_schema = 'public'
        and (
          (table_name = 'app_users' and column_name = 'email_verified_at')
          or (table_name = 'app_auth_sessions' and column_name in ('device_name', 'user_agent'))
        )
      order by table_name, column_name`,
  );
  assert.deepEqual(
    columns.rows.map((row) => `${row.table_name}.${row.column_name}`),
    [
      "app_auth_sessions.device_name",
      "app_auth_sessions.user_agent",
      "app_users.email_verified_at",
    ],
  );
});

test("round-trips durable payloads and enforces job states", async () => {
  const requestId = randomUUID();
  const tokenHash = "a".repeat(64);
  await databaseQuery(
    `insert into chat_jobs (
       id, access_token_hash, conversation_id, client, request_payload
     ) values ($1, $2, $3, 'ci', $4::jsonb)`,
    [requestId, tokenHash, "ci-conversation", JSON.stringify({ message: "hola" })],
  );
  const selected = await databaseQuery(
    `select request_payload, status from chat_jobs where id = $1`,
    [requestId],
  );
  assert.equal(selected.rows[0]?.request_payload.message, "hola");
  assert.equal(selected.rows[0]?.status, "queued");

  await assert.rejects(
    databaseQuery(
      `update chat_jobs set status = 'invalid-state' where id = $1`,
      [requestId],
    ),
  );
  await databaseQuery(`delete from chat_jobs where id = $1`, [requestId]);
});

test("account codes enforce purpose and session metadata defaults", async () => {
  const userId = randomUUID();
  await databaseQuery(
    `insert into app_users (id, name, email) values ($1, 'CI User', $2)`,
    [userId, `ci-${userId}@example.test`],
  );
  const sessionId = randomUUID();
  await databaseQuery(
    `insert into app_auth_sessions (
       id, user_id, access_token_hash, refresh_token_hash,
       access_expires_at, refresh_expires_at
     ) values ($1, $2, $3, $4, now() + interval '1 hour', now() + interval '1 day')`,
    [sessionId, userId, "b".repeat(64), "c".repeat(64)],
  );
  const session = await databaseQuery(
    `select device_name, user_agent from app_auth_sessions where id = $1`,
    [sessionId],
  );
  assert.equal(session.rows[0]?.device_name, "Android");
  assert.equal(session.rows[0]?.user_agent, null);

  const codeId = randomUUID();
  await databaseQuery(
    `insert into app_account_codes (
       id, user_id, purpose, email, code_hash, expires_at
     ) values ($1, $2, 'verify_email', $3, $4, now() + interval '10 minutes')`,
    [codeId, userId, `ci-${userId}@example.test`, "d".repeat(64)],
  );
  const code = await databaseQuery(
    `select purpose, attempts from app_account_codes where id = $1`,
    [codeId],
  );
  assert.equal(code.rows[0]?.purpose, "verify_email");
  assert.equal(code.rows[0]?.attempts, 0);

  await assert.rejects(
    databaseQuery(
      `insert into app_account_codes (
         id, user_id, purpose, email, code_hash, expires_at
       ) values ($1, $2, 'unknown', $3, $4, now() + interval '10 minutes')`,
      [randomUUID(), userId, `ci-${userId}@example.test`, "e".repeat(64)],
    ),
  );

  await databaseQuery(`delete from app_users where id = $1`, [userId]);
});
