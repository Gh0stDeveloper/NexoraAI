package com.ghostnexora.ai

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object ApiClient {
    fun postChat(
        message: String,
        model: NexoraModel,
        intelligence: IntelligenceLevel,
        attachments: List<PendingAttachment>,
        conversationId: String,
    ): String {
        return try {
            val endpoint = NativeConfig.apiBaseUrl().trimEnd('/') + "/api/mobile/chat"
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20_000
                readTimeout = when (intelligence) {
                    IntelligenceLevel.INSTANT -> 90_000
                    IntelligenceLevel.MEDIUM -> 240_000
                    IntelligenceLevel.HIGH -> 420_000
                    IntelligenceLevel.MAXIMUM -> 720_000
                }
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("X-Nexora-Client", "android")
                setRequestProperty("X-Nexora-Version", BuildConfig.VERSION_NAME)
                doOutput = true
            }

            val payload = JSONObject()
                .put("message", message)
                .put("mode", model.wireValue)
                .put("intelligence", intelligence.wireValue)
                .put("client", "android")
                .put("conversationId", conversationId)
                .put(
                    "attachments",
                    JSONArray().apply {
                        attachments.forEach { attachment ->
                            put(
                                JSONObject()
                                    .put("name", attachment.name)
                                    .put("mimeType", attachment.mimeType)
                                    .put("sizeBytes", attachment.sizeBytes)
                                    .apply {
                                        attachment.textContent?.let { put("text", it) }
                                        attachment.imageBase64?.let { put("base64", it) }
                                    },
                            )
                        }
                    },
                )
                .toString()

            connection.outputStream.use { stream -> stream.write(payload.toByteArray(Charsets.UTF_8)) }
            val responseBody = if (connection.responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }

            val parsed = JSONObject(responseBody)
            if (connection.responseCode in 200..299 && parsed.optBoolean("ok", true)) {
                parsed.optString("answer").ifBlank { "Nexora AI no devolvió contenido." }
            } else {
                parsed.optString("error").ifBlank { "No se pudo procesar la solicitud." }
            }
        } catch (_: Exception) {
            "No se pudo conectar con Nexora AI. Revisa tu conexión e inténtalo de nuevo."
        }
    }
}
