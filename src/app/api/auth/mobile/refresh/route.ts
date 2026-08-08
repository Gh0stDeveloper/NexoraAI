import { type NextRequest } from "next/server";
import { mobileAuthErrorResponse, refreshMobileSession } from "@/lib/mobile-auth";
import { checkMobileRateLimit } from "@/lib/rate-limit";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function POST(request: NextRequest) {
  const rate = checkMobileRateLimit(request);
  if (!rate.allowed) {
    return Response.json(
      { ok: false, error: "Demasiadas solicitudes. Inténtalo de nuevo en un minuto." },
      { status: 429, headers: { "Retry-After": String(rate.retryAfterSeconds) } },
    );
  }
  try {
    const body: unknown = await request.json();
    const refreshToken =
      typeof body === "object" && body !== null && "refreshToken" in body &&
      typeof body.refreshToken === "string"
        ? body.refreshToken
        : "";
    const session = await refreshMobileSession(refreshToken);
    return Response.json({ ok: true, session });
  } catch (error) {
    return mobileAuthErrorResponse(error);
  }
}
