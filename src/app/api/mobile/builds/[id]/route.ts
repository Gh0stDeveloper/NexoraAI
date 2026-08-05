import type { NextRequest } from "next/server";
import {
  AndroidBuildAccessError,
  getAndroidBuild,
} from "@/lib/android-builds";
import { checkMobileRateLimit } from "@/lib/rate-limit";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> },
) {
  const rate = checkMobileRateLimit(request);
  if (!rate.allowed) {
    return Response.json(
      { ok: false, error: "Demasiadas solicitudes. Inténtalo de nuevo en un minuto." },
      { status: 429, headers: { "Retry-After": String(rate.retryAfterSeconds) } },
    );
  }
  const token = request.headers.get("x-nexora-request-token")?.trim();
  if (!token) {
    return Response.json(
      { ok: false, error: "Falta el token privado de la compilación." },
      { status: 401 },
    );
  }

  try {
    const { id } = await params;
    return Response.json({ ok: true, build: await getAndroidBuild(id, token) });
  } catch (error) {
    const message =
      error instanceof AndroidBuildAccessError
        ? error.message
        : "No se pudo consultar la compilación.";
    return Response.json({ ok: false, error: message }, { status: 404 });
  }
}
