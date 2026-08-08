import { type NextRequest } from "next/server";
import {
  revokeAccountSession,
  revokeOtherAccountSessions,
} from "@/lib/mobile-account";
import { mobileAuthErrorResponse } from "@/lib/mobile-auth";
import { checkMobileRateLimit } from "@/lib/rate-limit";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function DELETE(request: NextRequest) {
  const rate = checkMobileRateLimit(request);
  if (!rate.allowed) {
    return Response.json(
      { ok: false, error: "Demasiadas operaciones. Inténtalo de nuevo en un minuto." },
      { status: 429, headers: { "Retry-After": String(rate.retryAfterSeconds) } },
    );
  }

  try {
    const body = (await request.json()) as Record<string, unknown>;
    if (body.others === true) {
      await revokeOtherAccountSessions(request);
      return Response.json({ ok: true, revoked: "others" });
    }
    const sessionId = typeof body.sessionId === "string" ? body.sessionId : "";
    if (!/^[0-9a-f-]{36}$/i.test(sessionId)) {
      return Response.json({ ok: false, error: "Sesión no válida." }, { status: 400 });
    }
    await revokeAccountSession(request, sessionId);
    return Response.json({ ok: true, revoked: sessionId });
  } catch (error) {
    return mobileAuthErrorResponse(error);
  }
}
