import { NextRequest, NextResponse } from "next/server";
import { z } from "zod";
import { runAgent } from "@/lib/agent";

const schema = z.object({
  message: z.string().trim().min(1).max(32000),
  mode: z.enum(["auto", "fullstack", "android", "backend", "security", "data", "devops"]).default("auto"),
  projectId: z.string().trim().min(1).max(160).optional(),
  client: z.string().trim().max(80).default("android"),
});

export async function POST(req: NextRequest) {
  try {
    const body = schema.parse(await req.json());
    const scopedMessage = body.projectId ? `[project:${body.projectId}]\n${body.message}` : body.message;
    const result = await runAgent(scopedMessage, body.mode);

    return NextResponse.json({
      ok: true,
      requestId: crypto.randomUUID(),
      client: body.client,
      projectId: body.projectId ?? null,
      mode: body.mode,
      ...result,
    });
  } catch (error) {
    const message = error instanceof z.ZodError ? error.issues.map((issue) => issue.message).join("; ") : "Invalid mobile chat request";
    return NextResponse.json({ ok: false, error: message }, { status: 400 });
  }
}
