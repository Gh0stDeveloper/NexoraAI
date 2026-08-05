import assert from "node:assert/strict";
import test from "node:test";

import {
  mobileChatError,
  mobileChatSchema,
} from "../src/lib/mobile-chat.ts";

test("normalizes legacy null chat identifiers", () => {
  const request = mobileChatSchema.parse({
    message: "Hola",
    projectId: null,
    conversationId: null,
  });

  assert.equal(request.projectId, undefined);
  assert.equal(request.conversationId, undefined);
});

test("preserves valid chat identifiers", () => {
  const request = mobileChatSchema.parse({
    message: "Hola",
    projectId: "project-1",
    conversationId: "conversation-1",
  });

  assert.equal(request.projectId, "project-1");
  assert.equal(request.conversationId, "conversation-1");
});

test("still rejects invalid identifier types without exposing Zod internals", () => {
  const result = mobileChatSchema.safeParse({
    message: "Hola",
    projectId: 42,
  });

  assert.equal(result.success, false);
  if (!result.success) {
    assert.equal(
      mobileChatError(result.error),
      "La solicitud contiene campos inválidos: projectId.",
    );
  }
});
