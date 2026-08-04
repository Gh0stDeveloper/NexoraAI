import { NextRequest, NextResponse } from "next/server";
import { z } from "zod";
import { runAgent } from "@/lib/agent";

const attachmentSchema = z
  .object({
    name: z.string().trim().min(1).max(240),
    mimeType: z.string().trim().min(1).max(160),
    sizeBytes: z.number().int().nonnegative().max(8 * 1024 * 1024),
    text: z.string().max(80_000).optional(),
    base64: z.string().max(12_000_000).optional(),
  })
  .refine((attachment) => Boolean(attachment.text || attachment.base64), {
    message: "Each attachment must include text or base64 content",
  });

const schema = z.object({
  message: z.string().trim().min(1).max(32_000),
  mode: z.enum(["auto", "fullstack", "android", "backend", "security", "data", "devops"]).default("auto"),
  intelligence: z.enum(["instant", "medium", "high", "maximum"]).default("medium"),
  projectId: z.string().trim().min(1).max(160).optional(),
  conversationId: z.string().trim().min(1).max(160).optional(),
  client: z.string().trim().max(80).default("android"),
  attachments: z.array(attachmentSchema).max(3).default([]),
});

export async function POST(req: NextRequest) {
  try {
    const body = schema.parse(await req.json());
    const scopedMessage = body.projectId ? `[project:${body.projectId}]\n${body.message}` : body.message;
    const result = await runAgent(scopedMessage, body.mode, {
      intelligence: body.intelligence,
      attachments: body.attachments,
      conversationId: body.conversationId,
    });

    return NextResponse.json({
      ok: true,
      requestId: crypto.randomUUID(),
      client: body.client,
      projectId: body.projectId ?? null,
      conversationId: body.conversationId ?? null,
      mode: body.mode,
      intelligence: body.intelligence,
      attachmentCount: body.attachments.length,
      ...result,
    });
  } catch (error) {
    const message =
      error instanceof z.ZodError
        ? error.issues.map((issue) => issue.message).join("; ")
        : "Invalid mobile chat request";
    return NextResponse.json({ ok: false, error: message }, { status: 400 });
  }
}
