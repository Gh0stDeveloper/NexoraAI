import { createHash, timingSafeEqual } from "node:crypto";

export function hashRequestToken(token: string): string {
  return createHash("sha256").update(token, "utf8").digest("hex");
}

export function requestTokenMatches(token: string, expectedHash: string): boolean {
  const actual = Buffer.from(hashRequestToken(token), "hex");
  const expected = Buffer.from(expectedHash, "hex");
  return actual.length === expected.length && timingSafeEqual(actual, expected);
}
