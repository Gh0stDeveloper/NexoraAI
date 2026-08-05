import { NextRequest } from "next/server";
import { runAgent } from "@/lib/agent";
import { mobileChatError, mobileChatSchema } from "@/lib/mobile-chat";
import { checkMobileRateLimit } from "@/lib/rate-limit";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function POST(req: NextRequest) {
  const rate = checkMobileRateLimit(req);
  if (!rate.allowed) {
    return Response.json(
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

  let body;
  try {
    body = mobileChatSchema.parse(await req.json());
  } catch (error) {
    return Response.json(
      { ok: false, error: mobileChatError(error) },
      { status: 400 },
    );
  }

  const encoder = new TextEncoder();
  const requestId = crypto.randomUUID();
  const stream = new ReadableStream<Uint8Array>({
    start(controller) {
      const send = (event: unknown) => {
        controller.enqueue(encoder.encode(`${JSON.stringify(event)}\n`));
      };

      send({
        type: "connected",
        requestId,
        conversationId: body.conversationId ?? null,
      });

      void runAgent(body.message, body.mode, {
        intelligence: body.intelligence,
        attachments: body.attachments,
        conversationId: body.conversationId,
        projectId: body.projectId,
        validateCode: body.validateCode,
        onProgress(progress) {
          send({ type: "progress", requestId, progress });
        },
      })
        .then((result) => {
          send({
            type: "result",
            ok: true,
            requestId,
            client: body.client,
            projectId: body.projectId ?? null,
            conversationId: body.conversationId ?? null,
            mode: body.mode,
            intelligence: body.intelligence,
            attachmentCount: body.attachments.length,
            ...result,
          });
        })
        .catch((error) => {
          console.error("[NexoraAI] Mobile stream failed", {
            requestId,
            error: error instanceof Error ? error.message : String(error),
          });
          send({
            type: "error",
            ok: false,
            requestId,
            error: "Nexora AI no pudo completar la solicitud.",
          });
        })
        .finally(() => controller.close());
    },
  });

  return new Response(stream, {
    headers: {
      "Content-Type": "application/x-ndjson; charset=utf-8",
      "Cache-Control": "no-cache, no-store, must-revalidate",
      Connection: "keep-alive",
      "X-Accel-Buffering": "no",
    },
  });
}
