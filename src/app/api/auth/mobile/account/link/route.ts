import { type NextRequest } from "next/server";
import {
  createAccountLinkStart,
  unlinkAccountProvider,
} from "@/lib/mobile-link";
import {
  isMobileProvider,
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
    const provider = typeof body.provider === "string" ? body.provider : "";
    if (!isMobileProvider(provider)) {
      return Response.json({ ok: false, error: "Proveedor no válido." }, { status: 400 });
    }
    const authorizationUrl = await createAccountLinkStart(request, {
      provider,
      redirectUri: typeof body.redirectUri === "string" ? body.redirectUri : "",
      clientState: typeof body.state === "string" ? body.state : "",
      codeChallenge: typeof body.codeChallenge === "string" ? body.codeChallenge : "",
    });
    return Response.json({ ok: true, authorizationUrl });
  } catch (error) {
    return mobileAuthErrorResponse(error);
  }
}

export async function DELETE(request: NextRequest) {
  const rate = checkMobileRateLimit(request);
  if (!rate.allowed) {
    return Response.json(
      { ok: false, error: "Demasiados cambios. Inténtalo de nuevo en un minuto." },
      { status: 429, headers: { "Retry-After": String(rate.retryAfterSeconds) } },
    );
  }
  try {
    const body = (await request.json()) as Record<string, unknown>;
    const provider = typeof body.provider === "string" ? body.provider : "";
    if (!isMobileProvider(provider)) {
      return Response.json({ ok: false, error: "Proveedor no válido." }, { status: 400 });
    }
    await unlinkAccountProvider(request, provider);
    return Response.json({ ok: true });
  } catch (error) {
    return mobileAuthErrorResponse(error);
  }
}