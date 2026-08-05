import { NextResponse } from "next/server";

export async function GET() {
  return NextResponse.json({
    ok: true,
    app: "Nexora AI",
    status: "ready",
    version: process.env.APP_VERSION ?? "0.4.1-duckdns-production",
    provider: process.env.AI_PROVIDER ?? "ollama",
    siteUrl:
      process.env.NEXT_PUBLIC_SITE_URL ??
      "https://ghostnexoraai.duckdns.org",
    productionApiUrl:
      process.env.MOBILE_PRODUCTION_API_URL ??
      "https://apighostnexoraai.duckdns.org/",
    features: [
      "web-chat-client",
      "android-chat-client",
      "local-model-provider",
      "vps-docker-compose",
      "defensive-security-policy",
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
