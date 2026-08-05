import { NextResponse } from "next/server";
import { readLatestAndroidRelease } from "@/lib/android-release";
import { userAndroidBuildsEnabled } from "@/lib/android-builds";
import { ensureDatabase } from "@/lib/db";

export const dynamic = "force-dynamic";

export async function GET() {
  await ensureDatabase();
  const androidRelease = await readLatestAndroidRelease();
  return NextResponse.json({
    ok: true,
    app: "Nexora AI",
    status: "ready",
    version: androidRelease?.version ?? process.env.APP_VERSION ?? "0.6.0",
    provider: process.env.AI_PROVIDER ?? "ollama",
    siteUrl:
      process.env.NEXT_PUBLIC_SITE_URL ??
      "https://ghostnexoraai.duckdns.org",
    productionApiUrl:
      process.env.MOBILE_PRODUCTION_API_URL ??
      "https://apighostnexoraai.duckdns.org/",
    features: [
      "public-download-website",
      "android-chat-client",
      "durable-chat-jobs",
      "request-recovery",
      "general-conversation-assistant",
      "automatic-android-release-manifest",
      "temporary-user-apk-builds",
      "streaming-progress",
      "per-client-rate-limits",
      "projects-and-pinned-chats",
      "optional-ephemeral-code-sandbox",
      "local-model-provider",
      "vps-docker-compose",
      "defensive-security-policy",
      "release-https-only",
      "debug-apk-ci",
      "conditional-release-apk-ci",
    ],
    checks: {
      api: true,
      android: true,
      vps: true,
      docs: true,
      ci: true,
    },
    androidRelease,
    userBuilds: {
      enabled: userAndroidBuildsEnabled(),
      retentionSeconds: 3600,
      signatureSchemes: ["V1", "V2", "V3"],
    },
  });
}
