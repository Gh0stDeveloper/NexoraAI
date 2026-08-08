import { type NextRequest } from "next/server";
import {
  confirmEmailVerification,
  requestEmailVerification,
} from "@/lib/mobile-account";
import { mobileAuthErrorResponse } from "@/lib/mobile-auth";
import { checkMobileRateLimit } from "@/lib/rate-limit";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function POST(request: NextRequest) {
  const rate = checkMobileRateLimit(request);
  if (!rate.allowed) {
    return Response.json(
      { ok: false, error: "Demasiados intentos. Inténtalo de nuevo en un minuto." },
      { status: 429, headers: { "Retry-After": String(rate.retryAfterSeconds) } },
    );
  }

  try {
    const body = (await request.json()) as Record<string, unknown>;
    const action = typeof body.action === "string" ? body.action : "request";
    if (action === "confirm") {
      await confirmEmailVerification(
        request,
        typeof body.code === "string" ? body.code.trim() : "",
      );
      return Response.json({ ok: true, verified: true });
    }
    await requestEmailVerification(request);
    return Response.json({ ok: true, sent: true, expiresInSeconds: 600 });
  } catch (error) {
    return mobileAuthErrorResponse(error);
  }
}
