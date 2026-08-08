import { type NextRequest } from "next/server";
import {
  completeOAuthCallback,
  isMobileProvider,
  MobileAuthError,
} from "@/lib/mobile-auth";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

function failureRedirect(message: string, code: string): Response {
  const url = new URL("nexoraai://auth/callback");
  url.searchParams.set("error", message);
  url.searchParams.set("error_code", code);
  return Response.redirect(url, 302);
}

export async function GET(
  request: NextRequest,
  context: { params: Promise<{ provider: string }> },
) {
  const { provider } = await context.params;
  if (!isMobileProvider(provider)) {
    return failureRedirect("Proveedor de identidad no válido.", "invalid_provider");
  }

  const providerError = request.nextUrl.searchParams.get("error");
  if (providerError) {
    return failureRedirect(
      request.nextUrl.searchParams.get("error_description") || "Inicio de sesión cancelado.",
      providerError,
    );
  }

  try {
    const redirect = await completeOAuthCallback({
      provider,
      state: request.nextUrl.searchParams.get("state") || "",
      code: request.nextUrl.searchParams.get("code") || "",
    });
    return Response.redirect(redirect, 302);
  } catch (error) {
    if (error instanceof MobileAuthError) {
      return failureRedirect(error.message, error.code);
    }
    console.error("[NexoraAI] OAuth callback failed", error);
    return failureRedirect("No se pudo completar el inicio de sesión.", "oauth_callback_failed");
  }
}
