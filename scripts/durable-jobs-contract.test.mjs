import assert from "node:assert/strict";
import { mkdtemp, rm, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";

import { androidBuildRequestSchema } from "../src/lib/android-build-request.ts";
import { readLatestAndroidRelease } from "../src/lib/android-release.ts";
import { mobileChatJobSchema } from "../src/lib/mobile-chat.ts";
import { hashRequestToken } from "../src/lib/request-token.ts";

const requestId = "00000000-0000-4000-8000-000000000001";
const deviceId = "00000000-0000-4000-8000-000000000002";
const token = "a".repeat(64);

test("accepts durable general-assistant chat jobs", () => {
  const request = mobileChatJobSchema.parse({
    requestId,
    requestToken: token,
    message: "Hola, conversemos",
    mode: "assistant",
    conversationId: "00000000-0000-4000-8000-000000000003",
  });
  assert.equal(request.mode, "assistant");
  assert.equal(request.requestId, requestId);
});

test("rejects weak request tokens", () => {
  assert.equal(
    mobileChatJobSchema.safeParse({
      requestId,
      requestToken: "predictable",
      message: "Hola",
    }).success,
    false,
  );
});

test("accepts bounded Android build specifications", () => {
  const request = androidBuildRequestSchema.parse({
    requestId,
    requestToken: token,
    deviceId,
    appName: "Mi aplicación",
    accentColor: "#10A37F",
    sourcePrompt: "Crea una aplicación de notas",
    sourceContent: "Diseño y funciones de la aplicación",
  });
  assert.equal(request.appName, "Mi aplicación");
});

test("hashes temporary access tokens before persistence", () => {
  assert.match(hashRequestToken(token), /^[a-f0-9]{64}$/);
  assert.notEqual(hashRequestToken(token), token);
});

test("loads the dynamic official Android release manifest", async () => {
  const directory = await mkdtemp(path.join(os.tmpdir(), "nexora-release-test-"));
  const manifest = path.join(directory, "latest.json");
  await writeFile(
    manifest,
    JSON.stringify({
      version: "0.6.0",
      versionCode: 8,
      fileName: "NexoraAI-0.6.0.apk",
      stableFileName: "NexoraAI-latest.apk",
      downloadUrl: "https://ghostnexoraai.duckdns.org/downloads/NexoraAI-0.6.0.apk",
      stableDownloadUrl: "https://ghostnexoraai.duckdns.org/downloads/NexoraAI-latest.apk",
      sha256: "b".repeat(64),
      publishedAt: "2026-08-05T22:00:00Z",
      signatureSchemes: ["V1", "V2", "V3"],
    }),
  );
  const previous = process.env.NEXORA_RELEASE_MANIFEST_PATH;
  process.env.NEXORA_RELEASE_MANIFEST_PATH = manifest;
  try {
    const release = await readLatestAndroidRelease();
    assert.equal(release?.version, "0.6.0");
    assert.deepEqual(release?.signatureSchemes, ["V1", "V2", "V3"]);
  } finally {
    if (previous === undefined) delete process.env.NEXORA_RELEASE_MANIFEST_PATH;
    else process.env.NEXORA_RELEASE_MANIFEST_PATH = previous;
    await rm(directory, { recursive: true, force: true });
  }
});
