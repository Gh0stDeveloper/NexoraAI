import { NextResponse } from "next/server";

export async function GET() {
  return NextResponse.json({
    ok: true,
    app: "Nexora AI",
    status: "ready",
    version: process.env.APP_VERSION ?? "0.5.0",
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
  });
}
