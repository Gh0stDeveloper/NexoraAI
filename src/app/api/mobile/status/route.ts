import { NextResponse } from "next/server";

export async function GET() {
  return NextResponse.json({
    ok: true,
    app: "Nexora AI",
    status: "ready",
    version: process.env.APP_VERSION ?? "0.2.0-client-api-android",
    provider: process.env.AI_PROVIDER ?? "ollama",
    productionApiUrl: process.env.MOBILE_PRODUCTION_API_URL ?? "https://api.nexoraia.com/",
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
