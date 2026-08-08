import { type NextRequest } from "next/server";
import { changeAccountPassword } from "@/lib/mobile-account";
import { mobileAuthErrorResponse } from "@/lib/mobile-auth";
import { checkMobileRateLimit } from "@/lib/rate-limit";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function PUT(request: NextRequest) {
  const rate = checkMobileRateLimit(request);
  if (!rate.allowed) {
    return Response.json(
      { ok: false, error: "Demasiados cambios. Inténtalo de nuevo en un minuto." },
      { status: 429, headers: { "Retry-After": String(rate.retryAfterSeconds) } },
    );
  }
  try {
    const body = (await request.json()) as Record<string, unknown>;
    await changeAccountPassword(request, {
      currentPassword: typeof body.currentPassword === "string" ? body.currentPassword : "",
      newPassword: typeof body.newPassword === "string" ? body.newPassword : "",
    });
    return Response.json({ ok: true });
  } catch (error) {
    return mobileAuthErrorResponse(error);
  }
}