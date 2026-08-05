import { NextResponse } from "next/server";
import { env } from "@/lib/env";
import { userAndroidBuildsEnabled } from "@/lib/android-builds";
import { readLatestAndroidRelease } from "@/lib/android-release";

export const dynamic = "force-dynamic";

export async function GET() {
  const release = await readLatestAndroidRelease();
  return NextResponse.json({
    ok: true,
    app: env.appName,
    version: release?.version ?? env.appVersion,
    apiUrl: env.mobileApi,
    features: [
      "chat",
      "durable-chat-jobs",
      "request-recovery",
      "assistant-mode",
      "history",
      "projects",
      "training",
      "vps",
      ...(userAndroidBuildsEnabled()
        ? ["temporary-user-apk-builds"]
        : []),
    ],
  });
}
