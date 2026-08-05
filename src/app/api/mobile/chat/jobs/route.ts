import { after, type NextRequest } from "next/server";
import {
  ChatJobAccessError,
  createChatJob,
  runChatJobInBackground,
} from "@/lib/chat-jobs";
import {
  mobileChatError,
  mobileChatJobSchema,
} from "@/lib/mobile-chat";
import { checkMobileRateLimit } from "@/lib/rate-limit";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";
export const maxDuration = 3600;

export async function POST(request: NextRequest) {
  const rate = checkMobileRateLimit(request);
  if (!rate.allowed) {
    return Response.json(
      { ok: false, error: "Demasiadas solicitudes. Inténtalo de nuevo en un minuto." },
      { status: 429, headers: { "Retry-After": String(rate.retryAfterSeconds) } },
    );
  }

  try {
    const body = mobileChatJobSchema.parse(await request.json());
    const job = await createChatJob(body);
    if (job.status === "queued" || job.status === "processing") {
      after(() => runChatJobInBackground(job.id));
    }
    return Response.json({ ok: true, job }, { status: 202 });
  } catch (error) {
    const accessError = error instanceof ChatJobAccessError;
    return Response.json(
      {
        ok: false,
        error: accessError ? error.message : mobileChatError(error),
      },
      { status: accessError ? 404 : 400 },
    );
  }
}
