import { z } from "zod";

export const androidBuildRequestSchema = z.object({
  requestId: z.string().uuid(),
  requestToken: z.string().regex(/^[a-f0-9]{64}$/i),
  deviceId: z.string().uuid(),
  appName: z.string().trim().min(2).max(48),
  accentColor: z
    .string()
    .trim()
    .regex(/^#[0-9a-f]{6}$/i)
    .default("#10A37F"),
  sourcePrompt: z.string().trim().min(1).max(16_000),
  sourceContent: z.string().trim().min(1).max(80_000),
});

export type AndroidBuildRequest = z.infer<typeof androidBuildRequestSchema>;
