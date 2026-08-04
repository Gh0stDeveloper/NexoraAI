export type AgentMode = "auto" | "fullstack" | "android" | "backend" | "security" | "data" | "devops";
export type IntelligenceLevel = "instant" | "medium" | "high" | "maximum";

export type AgentAttachment = {
  name: string;
  mimeType: string;
  sizeBytes: number;
  text?: string;
  base64?: string;
};

type AgentOptions = {
  intelligence?: IntelligenceLevel;
  attachments?: AgentAttachment[];
  conversationId?: string;
};

type AgentResult = {
  agent: AgentMode;
  answer: string;
  safety: "allowed" | "blocked";
  provider: "ollama" | "fallback";
  nextActions: string[];
};

const abusePattern = /(phishing|robar credenciales|credential theft|malware|ransomware|exfiltrar|exfiltrate|stealer|bypass\s+auth|evadir antivirus|keylogger)/i;

const modeGuidance: Record<AgentMode, string> = {
  auto: "Analiza la solicitud y elige el enfoque técnico más adecuado.",
  fullstack: "Prioriza arquitectura web, frontend, API, base de datos, validación y despliegue.",
  android: "Prioriza Kotlin, Jetpack Compose, Gradle, permisos, arquitectura y experiencia móvil.",
  backend: "Prioriza endpoints, contratos, autenticación, errores controlados, seguridad y persistencia.",
  security: "Trabaja solo en defensa, auditoría autorizada, hardening y reducción de riesgo.",
  data: "Prioriza SQL seguro, análisis, transformación, reportes y preparación de datos.",
  devops: "Prioriza CI/CD, VPS, Docker, Nginx, observabilidad, backups y rollback.",
};

const intelligenceOptions: Record<IntelligenceLevel, { temperature: number; numPredict: number; instruction: string }> = {
  instant: {
    temperature: 0.15,
    numPredict: 700,
    instruction: "Responde rápido, directo y con los pasos mínimos necesarios.",
  },
  medium: {
    temperature: 0.25,
    numPredict: 1400,
    instruction: "Equilibra velocidad, explicación y precisión.",
  },
  high: {
    temperature: 0.3,
    numPredict: 2600,
    instruction: "Analiza alternativas, riesgos y validación antes de responder.",
  },
  maximum: {
    temperature: 0.35,
    numPredict: 4200,
    instruction: "Realiza el análisis más profundo posible, verifica supuestos y entrega una solución completa.",
  },
};

function modelFor(mode: AgentMode, hasImages: boolean): string {
  if (hasImages) return process.env.OLLAMA_VISION_MODEL || process.env.OLLAMA_MODEL || "gemma3:4b";
  const perMode = process.env[`OLLAMA_MODEL_${mode.toUpperCase()}`];
  return perMode || process.env.OLLAMA_MODEL || "qwen2.5-coder:7b";
}

function buildAttachmentContext(attachments: AgentAttachment[]): string {
  const documents = attachments.filter((attachment) => attachment.text);
  if (documents.length === 0) return "";

  return documents
    .map((attachment) => {
      const content = attachment.text?.slice(0, 80_000) ?? "";
      return [`\n--- Archivo: ${attachment.name} (${attachment.mimeType}) ---`, content, "--- Fin del archivo ---"].join("\n");
    })
    .join("\n");
}

async function callOllama(
  message: string,
  mode: AgentMode,
  intelligence: IntelligenceLevel,
  attachments: AgentAttachment[],
): Promise<string> {
  const baseUrl = (process.env.OLLAMA_BASE_URL || "http://127.0.0.1:11434").replace(/\/$/, "");
  const images = attachments.flatMap((attachment) =>
    attachment.base64 && attachment.mimeType.startsWith("image/") ? [attachment.base64] : [],
  );
  const profile = intelligenceOptions[intelligence];
  const systemPrompt = [
    "Eres Nexora AI, un asistente técnico especializado en programación full-stack, Android, backend, ciberseguridad defensiva, datos y DevOps.",
    modeGuidance[mode],
    profile.instruction,
    "No reveles secretos, URLs internas, claves ni detalles de infraestructura innecesarios.",
    "Cuando recibas archivos, analiza su contenido y responde exactamente a la petición del usuario.",
    "Cuando recibas imágenes, describe y analiza únicamente lo visible y relevante para la solicitud.",
    "Responde en español claro, técnico y verificable.",
  ].join(" ");

  const response = await fetch(`${baseUrl}/api/chat`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    signal: AbortSignal.timeout(intelligence === "maximum" ? 180_000 : 120_000),
    body: JSON.stringify({
      model: modelFor(mode, images.length > 0),
      stream: false,
      messages: [
        { role: "system", content: systemPrompt },
        {
          role: "user",
          content: `${message}${buildAttachmentContext(attachments)}`,
          ...(images.length > 0 ? { images } : {}),
        },
      ],
      options: {
        temperature: profile.temperature,
        num_predict: profile.numPredict,
      },
    }),
  });

  if (!response.ok) {
    throw new Error(`Local provider returned ${response.status}`);
  }

  const payload = (await response.json()) as { message?: { content?: string } };
  const answer = payload.message?.content?.trim();
  if (!answer) throw new Error("Local provider returned an empty response");
  return answer;
}

export async function runAgent(
  message: string,
  mode: AgentMode = "auto",
  options: AgentOptions = {},
): Promise<AgentResult> {
  const blocked = abusePattern.test(message);
  if (blocked) {
    return {
      agent: mode,
      safety: "blocked",
      provider: "fallback",
      answer:
        "No puedo ayudar con abuso ofensivo, robo de credenciales, malware, evasión o exfiltración. Sí puedo ayudarte a auditar, endurecer y corregir sistemas propios o autorizados.",
      nextActions: [
        "Convertir la solicitud en una auditoría defensiva autorizada",
        "Revisar permisos, secretos, dependencias y endpoints expuestos",
        "Crear un plan de hardening y validación segura",
      ],
    };
  }

  const intelligence = options.intelligence ?? "medium";
  const attachments = options.attachments ?? [];

  try {
    const answer = await callOllama(message, mode, intelligence, attachments);
    return {
      agent: mode,
      safety: "allowed",
      provider: "ollama",
      answer,
      nextActions: ["Revisar la respuesta", "Aplicar los cambios", "Validar con pruebas"],
    };
  } catch {
    const attachmentSummary = attachments.length
      ? `Se recibieron ${attachments.length} adjunto(s): ${attachments.map((item) => item.name).join(", ")}.`
      : "No se recibieron adjuntos.";

    return {
      agent: mode,
      safety: "allowed",
      provider: "fallback",
      answer: [
        `Modelo seleccionado: ${mode}.`,
        `Nivel de inteligencia: ${intelligence}.`,
        attachmentSummary,
        "El modelo local todavía no está disponible en el servidor. La solicitud quedó validada y lista para procesarse cuando Ollama esté activo.",
      ].join("\n"),
      nextActions: [
        "Verificar que Ollama esté ejecutándose",
        "Configurar OLLAMA_MODEL y OLLAMA_VISION_MODEL",
        "Reintentar la solicitud desde la aplicación",
      ],
    };
  }
}
