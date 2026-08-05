import { NextResponse } from "next/server";

export async function GET() {
  return NextResponse.json({
    openapi: "3.1.0",
    info: {
      title: "Nexora AI API",
      version: "0.5.1",
      description: "API para el cliente Android de Nexora AI.",
    },
    paths: {
      "/api/health": { get: { summary: "Healthcheck del servicio" } },
      "/api/mobile/status": { get: { summary: "Versión y capacidades móviles" } },
      "/api/mobile/chat": {
        post: {
          summary: "Respuesta de chat en JSON",
          description: "Contrato compatible sin progreso incremental. Puede responder 429 al superar el límite por cliente.",
        },
      },
      "/api/mobile/chat/stream": {
        post: {
          summary: "Chat Android con progreso",
          description: "Flujo NDJSON de eventos connected, progress, result y error. Las etapas no contienen razonamiento privado. Puede responder 429 antes de abrir el flujo.",
        },
      },
    },
  });
}
