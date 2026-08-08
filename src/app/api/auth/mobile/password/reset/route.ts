import { type NextRequest } from "next/server";
import {
  confirmPasswordReset,
  requestPasswordReset,
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
    const email = typeof body.email === "string" ? body.email : "";
    if (action === "confirm") {
      await confirmPasswordReset({
        email,
        code: typeof body.code === "string" ? body.code.trim() : "",
        password: typeof body.password === "string" ? body.password : "",
      });
      return Response.json({ ok: true, reset: true });
    }

    // Always return the same response so this endpoint cannot be used to
    // enumerate whether an email has a Nexora password account.
    await requestPasswordReset(email);
    return Response.json({ ok: true, sent: true, expiresInSeconds: 600 });
  } catch (error) {
    return mobileAuthErrorResponse(error);
  }
}
