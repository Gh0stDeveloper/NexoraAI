import { createReadStream } from "node:fs";
import { stat } from "node:fs/promises";
import path from "node:path";
import { Readable } from "node:stream";
import type { NextRequest } from "next/server";
import {
  AndroidBuildAccessError,
  getAndroidBuildForDownload,
} from "@/lib/android-builds";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

const buildRoot = path.resolve(
  /* turbopackIgnore: true */
  process.env.USER_BUILD_JOBS_PATH ||
    "/var/lib/nexora-ai/android-build-jobs",
);

export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> },
) {
  const token = new URL(request.url).searchParams.get("token")?.trim();
  if (!token) {
    return Response.json({ ok: false, error: "El enlace no es válido." }, { status: 401 });
  }

  try {
    const { id } = await params;
    const build = await getAndroidBuildForDownload(id, token);
    if (build.status === "expired" || (build.expires_at?.getTime() ?? 0) <= Date.now()) {
      return Response.json(
        { ok: false, error: "Este enlace temporal ya expiró." },
        { status: 410, headers: { "Cache-Control": "no-store" } },
      );
    }
    if (build.status !== "completed" || !build.output_path || !build.file_name) {
      return Response.json(
        { ok: false, error: "El APK todavía no está disponible." },
        { status: 409, headers: { "Cache-Control": "no-store" } },
      );
    }

    const target = path.resolve(build.output_path);
    if (!target.startsWith(`${buildRoot}${path.sep}`) || path.extname(target) !== ".apk") {
      throw new Error("Unsafe build artifact path");
    }
    const metadata = await stat(target);
    if (!metadata.isFile()) throw new Error("Build artifact is not a file");

    const stream = createReadStream(target);
    return new Response(Readable.toWeb(stream) as ReadableStream, {
      headers: {
        "Content-Type": "application/vnd.android.package-archive",
        "Content-Length": String(metadata.size),
        "Content-Disposition": `attachment; filename="${build.file_name.replaceAll('"', "")}"`,
        "Cache-Control": "private, no-store, max-age=0",
        "X-Content-Type-Options": "nosniff",
        ...(build.sha256 ? { "X-Nexora-SHA256": build.sha256 } : {}),
      },
    });
  } catch (error) {
    const status = error instanceof AndroidBuildAccessError ? 404 : 410;
    const message =
      error instanceof AndroidBuildAccessError
        ? error.message
        : "El APK temporal ya no está disponible.";
    return Response.json(
      { ok: false, error: message },
      { status, headers: { "Cache-Control": "no-store" } },
    );
  }
}
