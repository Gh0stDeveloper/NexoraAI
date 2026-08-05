import { NextResponse } from "next/server";
import { readLatestAndroidRelease } from "@/lib/android-release";

export const dynamic = "force-dynamic";

export async function GET() {
  const release = await readLatestAndroidRelease();
  const destination =
    release?.downloadUrl ||
    process.env.ANDROID_APK_URL ||
    "https://github.com/Gh0stDeveloper/NexoraAI/releases/latest";
  return NextResponse.redirect(destination, 307);
}
