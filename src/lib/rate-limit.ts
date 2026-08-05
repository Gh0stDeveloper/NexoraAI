import type { NextRequest } from "next/server";

type WindowEntry = { count: number; resetAt: number };

const globalRateLimit = globalThis as typeof globalThis & {
  nexoraMobileRateWindows?: Map<string, WindowEntry>;
};

const windows =
  globalRateLimit.nexoraMobileRateWindows ?? new Map<string, WindowEntry>();
globalRateLimit.nexoraMobileRateWindows = windows;

function configuredLimit(): number {
  const parsed = Number.parseInt(process.env.RATE_LIMIT_PER_MINUTE || "80", 10);
  return Number.isFinite(parsed) ? Math.min(600, Math.max(5, parsed)) : 80;
}

function clientKey(request: NextRequest): string {
  const realIp = request.headers.get("x-real-ip")?.trim();
  if (realIp) return realIp;

  const forwarded = request.headers.get("x-forwarded-for");
  if (forwarded) return forwarded.split(",").at(-1)?.trim() || "direct";
  return "direct";
}

export function checkMobileRateLimit(request: NextRequest): {
  allowed: boolean;
  limit: number;
  remaining: number;
  retryAfterSeconds: number;
} {
  const now = Date.now();
  const limit = configuredLimit();
  const key = clientKey(request);
  const current = windows.get(key);
  const entry =
    !current || current.resetAt <= now
      ? { count: 0, resetAt: now + 60_000 }
      : current;

  entry.count += 1;
  windows.set(key, entry);

  if (windows.size > 5_000) {
    for (const [candidate, value] of windows) {
      if (value.resetAt <= now) windows.delete(candidate);
    }
  }

  return {
    allowed: entry.count <= limit,
    limit,
    remaining: Math.max(0, limit - entry.count),
    retryAfterSeconds: Math.max(1, Math.ceil((entry.resetAt - now) / 1_000)),
  };
}
