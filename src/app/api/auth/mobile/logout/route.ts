import { type NextRequest } from "next/server";
import { mobileAuthErrorResponse, revokeMobileSession } from "@/lib/mobile-auth";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function POST(request: NextRequest) {
  try {
    const body: unknown = await request.json();
    const refreshToken =
      typeof body === "object" && body !== null && "refreshToken" in body &&
      typeof body.refreshToken === "string"
        ? body.refreshToken
        : null;
    const authorization = request.headers.get("authorization") || "";
    const accessToken = authorization.match(/^Bearer\s+(.+)$/i)?.[1] || null;
    await revokeMobileSession({ accessToken, refreshToken });
    return Response.json({ ok: true });
  } catch (error) {
    return mobileAuthErrorResponse(error);
  }
}
