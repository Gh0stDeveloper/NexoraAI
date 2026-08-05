import { z } from "zod";

const mobileIdentifierSchema = z
  .string()
  .trim()
  .min(1)
  .max(160)
  .nullish()
  .transform((identifier) => identifier ?? undefined);

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
    .enum([
      "assistant",
      "auto",
      "fullstack",
      "android",
      "backend",
      "security",
      "data",
      "devops",
    ])
    .default("auto"),
  intelligence: z.enum(["instant", "medium", "high", "maximum"]).default("medium"),
  projectId: mobileIdentifierSchema,
  conversationId: mobileIdentifierSchema,
  client: z.string().trim().max(80).default("android"),
  validateCode: z.boolean().default(false),
  attachments: z.array(mobileAttachmentSchema).max(3).default([]),
});

export const mobileChatJobSchema = mobileChatSchema.extend({
  requestId: z.string().uuid(),
  requestToken: z.string().regex(/^[a-f0-9]{64}$/i),
});

export type MobileChatRequest = z.infer<typeof mobileChatSchema>;
export type MobileChatJobRequest = z.infer<typeof mobileChatJobSchema>;

export function mobileChatError(error: unknown): string {
  if (!(error instanceof z.ZodError)) {
    return "No se pudo validar la solicitud móvil.";
  }

  const invalidFields = [
    ...new Set(
      error.issues
        .map((issue) => issue.path.join("."))
        .filter((field) => field.length > 0),
    ),
  ];

  return invalidFields.length > 0
    ? `La solicitud contiene campos inválidos: ${invalidFields.join(", ")}.`
    : "La solicitud móvil contiene datos inválidos.";
}
