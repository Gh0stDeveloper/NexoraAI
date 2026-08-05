import { NextResponse } from "next/server";

export async function GET() {
  return NextResponse.json({
    openapi: "3.1.0",
    info: {
      title: "Nexora AI API",
      version: "0.6.0",
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
      "/api/mobile/chat/jobs": {
        post: {
          summary: "Crear o recuperar una solicitud de chat persistente",
          description: "Contrato idempotente protegido por requestId y requestToken. Devuelve 202 mientras PostgreSQL conserva progreso y resultado.",
        },
      },
      "/api/mobile/chat/jobs/{id}": {
        get: {
          summary: "Consultar una solicitud persistente",
          parameters: [
            { name: "id", in: "path", required: true, schema: { type: "string", format: "uuid" } },
            { name: "X-Nexora-Request-Token", in: "header", required: true, schema: { type: "string" } },
          ],
        },
      },
      "/api/mobile/builds": {
        post: {
          summary: "Solicitar un APK temporal",
          description: "Compila una plantilla Android aislada con una keystore global de usuarios distinta de la firma oficial.",
        },
      },
      "/api/mobile/builds/{id}": {
        get: {
          summary: "Consultar una compilación temporal",
          description: "El APK y su URL expiran una hora después de completarse.",
          parameters: [
            { name: "id", in: "path", required: true, schema: { type: "string", format: "uuid" } },
            { name: "X-Nexora-Request-Token", in: "header", required: true, schema: { type: "string" } },
          ],
        },
      },
      "/api/mobile/builds/{id}/download": {
        get: {
          summary: "Descargar un APK temporal",
          description: "El token aleatorio viaja en una URL privada, no se registra en Nginx y deja de ser válido al vencer el trabajo.",
          parameters: [
            { name: "id", in: "path", required: true, schema: { type: "string", format: "uuid" } },
            { name: "token", in: "query", required: true, schema: { type: "string" } },
          ],
        },
      },
    },
  });
}
