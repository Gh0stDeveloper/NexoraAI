import type { NextRequest } from "next/server";
import {
  AndroidBuildAccessError,
  AndroidBuildQuotaError,
  createAndroidBuild,
} from "@/lib/android-builds";
import { androidBuildRequestSchema } from "@/lib/android-build-request";
import { checkMobileRateLimit } from "@/lib/rate-limit";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

function clientAddress(request: NextRequest): string {
  return (
    request.headers.get("x-real-ip")?.trim() ||
    request.headers.get("x-forwarded-for")?.split(",")[0]?.trim() ||
    "direct"
  );
}

export async function POST(request: NextRequest) {
  const rate = checkMobileRateLimit(request);
  if (!rate.allowed) {
    return Response.json(
      { ok: false, error: "Demasiadas solicitudes. Inténtalo de nuevo en un minuto." },
      { status: 429, headers: { "Retry-After": String(rate.retryAfterSeconds) } },
    );
  }

  try {
    const body = androidBuildRequestSchema.parse(await request.json());
    const build = await createAndroidBuild(body, clientAddress(request));
    return Response.json({ ok: true, build }, { status: 202 });
  } catch (error) {
    if (error instanceof AndroidBuildQuotaError) {
      const disabled = error.retryAfterSeconds === 0;
      return Response.json(
        { ok: false, error: error.message },
        {
          status: disabled ? 403 : 429,
          headers: disabled
            ? undefined
            : { "Retry-After": String(error.retryAfterSeconds) },
        },
      );
    }
    if (error instanceof AndroidBuildAccessError) {
      return Response.json({ ok: false, error: error.message }, { status: 404 });
    }
    return Response.json(
      { ok: false, error: "La solicitud de compilación contiene datos inválidos." },
      { status: 400 },
    );
  }
}
