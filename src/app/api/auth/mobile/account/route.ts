import { type NextRequest } from "next/server";
import {
  getAccountOverview,
  updateAccountProfile,
} from "@/lib/mobile-account";
import { mobileAuthErrorResponse } from "@/lib/mobile-auth";
import { checkMobileRateLimit } from "@/lib/rate-limit";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function GET(request: NextRequest) {
  try {
    const account = await getAccountOverview(request);
    return Response.json({ ok: true, account });
  } catch (error) {
    return mobileAuthErrorResponse(error);
  }
}

export async function PATCH(request: NextRequest) {
  const rate = checkMobileRateLimit(request);
  if (!rate.allowed) {
    return Response.json(
      { ok: false, error: "Demasiados cambios. Inténtalo de nuevo en un minuto." },
      { status: 429, headers: { "Retry-After": String(rate.retryAfterSeconds) } },
    );
  }

  try {
    const body = (await request.json()) as Record<string, unknown>;
    const user = await updateAccountProfile(request, {
      name: typeof body.name === "string" ? body.name : "",
    });
    return Response.json({ ok: true, user });
  } catch (error) {
    return mobileAuthErrorResponse(error);
  }
}
