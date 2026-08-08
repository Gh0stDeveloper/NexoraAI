import { type NextRequest } from "next/server";
import {
  mobileAuthErrorResponse,
  registerWithEmail,
  signInWithEmail,
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
    const action = typeof body.action === "string" ? body.action : "login";
    const email = typeof body.email === "string" ? body.email : "";
    const password = typeof body.password === "string" ? body.password : "";
    const session = action === "register"
      ? await registerWithEmail({
          name: typeof body.name === "string" ? body.name : "",
          email,
          password,
        })
      : await signInWithEmail(email, password);
    return Response.json({ ok: true, session });
  } catch (error) {
    return mobileAuthErrorResponse(error);
  }
}
