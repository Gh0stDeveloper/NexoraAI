import { type NextRequest } from "next/server";
import {
  createOAuthStart,
  isMobileProvider,
  mobileAuthErrorResponse,
} from "@/lib/mobile-auth";
import { checkMobileRateLimit } from "@/lib/rate-limit";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function GET(request: NextRequest) {
  const rate = checkMobileRateLimit(request);
  if (!rate.allowed) {
    return Response.json(
      { ok: false, error: "Demasiados intentos. Inténtalo de nuevo en un minuto." },
      { status: 429, headers: { "Retry-After": String(rate.retryAfterSeconds) } },
    );
  }

  try {
    const providerValue = request.nextUrl.searchParams.get("provider") || "";
    if (!isMobileProvider(providerValue)) {
      return Response.json({ ok: false, error: "Proveedor no válido." }, { status: 400 });
    }
    const url = await createOAuthStart({
      provider: providerValue,
      redirectUri: request.nextUrl.searchParams.get("redirect_uri") || "",
      clientState: request.nextUrl.searchParams.get("state") || "",
      codeChallenge: request.nextUrl.searchParams.get("code_challenge") || "",
    });
    return Response.redirect(url, 302);
  } catch (error) {
    return mobileAuthErrorResponse(error);
  }
}
