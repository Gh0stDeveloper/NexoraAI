import { z } from "zod";

export const mobileAttachmentSchema = z
  .object({
    name: z.string().trim().min(1).max(240),
    mimeType: z.string().trim().min(1).max(160),
    sizeBytes: z.number().int().nonnegative().max(8 * 1024 * 1024),
    text: z.string().max(80_000).optional(),
    base64: z.string().max(12_000_000).optional(),
  })
  .refine((attachment) => Boolean(attachment.text || attachment.base64), {
    message: "Each attachment must include text or base64 content",
  });

export const mobileChatSchema = z.object({
  message: z.string().trim().min(1).max(32_000),
  mode: z
    .enum(["auto", "fullstack", "android", "backend", "security", "data", "devops"])
    .default("auto"),
  intelligence: z.enum(["instant", "medium", "high", "maximum"]).default("medium"),
  projectId: z.string().trim().min(1).max(160).optional(),
  conversationId: z.string().trim().min(1).max(160).optional(),
  client: z.string().trim().max(80).default("android"),
  validateCode: z.boolean().default(false),
  attachments: z.array(mobileAttachmentSchema).max(3).default([]),
});

export type MobileChatRequest = z.infer<typeof mobileChatSchema>;

export function mobileChatError(error: unknown): string {
  return error instanceof z.ZodError
    ? error.issues.map((issue) => issue.message).join("; ")
    : "Invalid mobile chat request";
}
