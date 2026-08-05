import { NextResponse } from "next/server";

export async function GET() {
  return NextResponse.json({
    ok: true,
    docs: [
      "README-INSTALL",
      "README-UPDATE",
      "ANDROID-BUILD-VPS",
      "SUPPORT-MATRIX",
      "SANDBOX",
      "TROUBLESHOOTING",
      "ci-actions",
      "duckdns-vps",
    ],
  });
}
