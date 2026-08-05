import { NextRequest, NextResponse } from "next/server";
import { z } from "zod";
import { runAgent } from "@/lib/agent";

const schema = z.object({
  message: z.string().trim().min(1).max(32_000),
  mode: z
    .enum(["auto", "fullstack", "android", "backend", "security", "data", "devops"])
    .default("auto"),
  intelligence: z
    .enum(["instant", "medium", "high", "maximum"])
    .default("instant"),
});

export async function POST(req: NextRequest) {
  try {
    const body = schema.parse(await req.json());
    const result = await runAgent(body.message, body.mode, {
      intelligence: body.intelligence,
    });

    return NextResponse.json({
      ok: true,
      requestId: crypto.randomUUID(),
      mode: body.mode,
      intelligence: body.intelligence,
      ...result,
    });
  } catch (error) {
    const message =
      error instanceof z.ZodError
        ? error.issues.map((issue) => issue.message).join("; ")
        : "Invalid chat request";
    return NextResponse.json({ ok: false, error: message }, { status: 400 });
  }
}
