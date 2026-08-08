import { type NextRequest } from "next/server";
import { authenticateMobileRequest, mobileAuthErrorResponse } from "@/lib/mobile-auth";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function GET(request: NextRequest) {
  try {
    const user = await authenticateMobileRequest(request);
    return Response.json({ ok: true, user });
  } catch (error) {
    return mobileAuthErrorResponse(error);
  }
}
