export type AgentMode = "auto" | "fullstack" | "android" | "backend" | "security" | "data" | "devops";

type AgentResult = {
  agent: AgentMode;
  answer: string;
  safety: "allowed" | "blocked";
  nextActions: string[];
};

const abusePattern = /(phishing|robar credenciales|credential theft|malware|ransomware|exfiltrar|exfiltrate|stealer|bypass\s+auth|evadir antivirus|keylogger)/i;

const modeGuidance: Record<AgentMode, string> = {
  auto: "Analizo la solicitud y elijo el flujo técnico más adecuado.",
  fullstack: "Priorizaré arquitectura web, API, base de datos, validación y despliegue.",
  android: "Priorizaré Kotlin, Jetpack Compose, Gradle, permisos, builds y cliente móvil.",
  backend: "Priorizaré endpoints, contratos JSON, errores controlados, seguridad y persistencia.",
  security: "Trabajaré solo en defensa, auditoría autorizada, hardening y reducción de riesgo.",
  data: "Priorizaré SQL seguro, análisis de datos, reportes y preparación para dashboards.",
  devops: "Priorizaré VPS, Docker, Nginx, Actions, observabilidad, backups y rollback.",
};

export async function runAgent(message: string, mode: AgentMode = "auto"): Promise<AgentResult> {
  const blocked = abusePattern.test(message);
  if (blocked) {
    return {
      agent: mode,
      safety: "blocked",
      answer:
        "No puedo ayudar con abuso ofensivo, robo de credenciales, malware, evasión o exfiltración. Sí puedo ayudarte a auditar, endurecer y corregir sistemas propios o autorizados.",
      nextActions: [
        "Convertir la solicitud en una auditoría defensiva autorizada",
        "Revisar permisos, secretos, dependencias y endpoints expuestos",
        "Crear un plan de hardening y validación segura",
      ],
    };
  }

  const selectedMode = mode;
  const guidance = modeGuidance[selectedMode];

  return {
    agent: selectedMode,
    safety: "allowed",
    answer: [
      `Modo activo: ${selectedMode}`,
      guidance,
      "",
      "Estado actual: este endpoint ya está normalizado para cliente web y Android. En producción debe conectarse al proveedor local Ollama/vLLM, RAG por proyecto y herramientas autorizadas por política.",
      "",
      "Solicitud recibida:",
      message,
    ].join("\n"),
    nextActions: [
      "Buscar contexto del workspace/RAG",
      "Elegir agente especializado",
      "Ejecutar solo herramientas permitidas",
      "Devolver respuesta con pasos verificables",
    ],
  };
}
