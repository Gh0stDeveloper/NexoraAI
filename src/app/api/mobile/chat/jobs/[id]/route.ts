import { after, type NextRequest } from "next/server";
import {
  ChatJobAccessError,
  getChatJob,
  runChatJobInBackground,
} from "@/lib/chat-jobs";
import { checkMobileRateLimit } from "@/lib/rate-limit";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";
export const maxDuration = 3600;

export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> },
) {
  const rate = checkMobileRateLimit(request);
  if (!rate.allowed) {
    return Response.json(
      { ok: false, error: "Demasiadas solicitudes. Inténtalo de nuevo en un minuto." },
      { status: 429, headers: { "Retry-After": String(rate.retryAfterSeconds) } },
    );
  }

  const token = request.headers.get("x-nexora-request-token")?.trim();
  if (!token) {
    return Response.json(
      { ok: false, error: "Falta el token privado de la solicitud." },
      { status: 401 },
    );
  }

  try {
    const { id } = await params;
    const job = await getChatJob(id, token);
    if (job.status === "queued" || job.status === "processing") {
      after(() => runChatJobInBackground(job.id));
    }
    return Response.json({ ok: true, job });
  } catch (error) {
    const message =
      error instanceof ChatJobAccessError
        ? error.message
        : "No se pudo consultar la solicitud.";
    return Response.json({ ok: false, error: message }, { status: 404 });
  }
}
