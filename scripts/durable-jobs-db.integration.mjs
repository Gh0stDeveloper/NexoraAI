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

test("creates durable chat and Android build tables in PostgreSQL", async () => {
  await ensureDatabase();
  const tables = await databaseQuery(
    `select table_name
       from information_schema.tables
      where table_schema = 'public'
        and table_name in ('chat_jobs', 'android_build_jobs')
      order by table_name`,
  );
  assert.deepEqual(
    tables.rows.map((row) => row.table_name),
    ["android_build_jobs", "chat_jobs"],
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
