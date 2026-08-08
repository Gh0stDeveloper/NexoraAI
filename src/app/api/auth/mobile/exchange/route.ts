import { type NextRequest } from "next/server";
import {
  exchangeMobileCode,
  mobileAuthErrorResponse,
} from "@/lib/mobile-auth";
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
    const session = await exchangeMobileCode(
      typeof body.code === "string" ? body.code : "",
      typeof body.codeVerifier === "string" ? body.codeVerifier : "",
    );
    return Response.json({ ok: true, session });
  } catch (error) {
    return mobileAuthErrorResponse(error);
  }
}
