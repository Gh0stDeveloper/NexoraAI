import { NextResponse } from "next/server";

export function GET() {
  const destination =
    process.env.ANDROID_APK_URL ||
    "https://github.com/Gh0stDeveloper/NexoraAI/releases/latest";
  return NextResponse.redirect(destination, 307);
}
