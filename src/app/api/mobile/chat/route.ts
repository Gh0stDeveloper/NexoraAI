import { NextRequest, NextResponse } from "next/server";
import { runAgent } from "@/lib/agent";
import { mobileChatError, mobileChatSchema } from "@/lib/mobile-chat";
import { checkMobileRateLimit } from "@/lib/rate-limit";

export async function POST(req: NextRequest) {
  const rate = checkMobileRateLimit(req);
  if (!rate.allowed) {
    return NextResponse.json(
      { ok: false, error: "Demasiadas solicitudes. Inténtalo de nuevo en un minuto." },
      {
        status: 429,
        headers: {
          "Retry-After": String(rate.retryAfterSeconds),
          "X-RateLimit-Limit": String(rate.limit),
          "X-RateLimit-Remaining": String(rate.remaining),
        },
      },
    );
  }

  try {
    const body = mobileChatSchema.parse(await req.json());
    const result = await runAgent(body.message, body.mode, {
      intelligence: body.intelligence,
      attachments: body.attachments,
      conversationId: body.conversationId,
      projectId: body.projectId,
      validateCode: body.validateCode,
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
    return NextResponse.json(
      { ok: false, error: mobileChatError(error) },
      { status: 400 },
    );
  }
}
