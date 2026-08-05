import { readFile } from "node:fs/promises";
import { z } from "zod";

const androidReleaseSchema = z.object({
  version: z.string().min(1).max(32),
  versionCode: z.number().int().positive(),
  fileName: z.string().min(1).max(160),
  stableFileName: z.string().min(1).max(160),
  downloadUrl: z.string().url(),
  stableDownloadUrl: z.string().url(),
  sha256: z.string().regex(/^[a-f0-9]{64}$/i),
  publishedAt: z.string().datetime(),
  signatureSchemes: z.array(z.enum(["V1", "V2", "V3", "V4"])).min(1),
});

export type AndroidRelease = z.infer<typeof androidReleaseSchema>;

export async function readLatestAndroidRelease(): Promise<AndroidRelease | null> {
  const manifestPath =
    process.env.NEXORA_RELEASE_MANIFEST_PATH ||
    "/var/lib/nexora-ai/releases/latest.json";
  try {
    return androidReleaseSchema.parse(
      JSON.parse(
        await readFile(/* turbopackIgnore: true */ manifestPath, "utf8"),
      ),
    );
  } catch (error) {
    if ((error as NodeJS.ErrnoException).code !== "ENOENT") {
      console.warn("[NexoraAI] Android release manifest is unavailable", {
        error: error instanceof Error ? error.message : String(error),
      });
    }
    return null;
  }
}
