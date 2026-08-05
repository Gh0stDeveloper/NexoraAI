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
        projectId: String?,
        validateCode: Boolean,
        onProgress: (AgentProgress) -> Unit,
    ): ChatResponse {
        var connection: HttpURLConnection? = null
        return try {
            val endpoint = NativeBridge.apiOrigin().trimEnd('/') + NativeBridge.chatPath()
            val activeConnection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20_000
                readTimeout = when (intelligence) {
                    IntelligenceLevel.INSTANT -> 180_000
                    IntelligenceLevel.MEDIUM -> 420_000
                    IntelligenceLevel.HIGH -> 720_000
                    IntelligenceLevel.MAXIMUM -> 900_000
                }
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/x-ndjson")
                setRequestProperty(NativeBridge.clientHeaderName(), "android")
                setRequestProperty(NativeBridge.versionHeaderName(), BuildConfig.VERSION_NAME)
                doOutput = true
            }
            connection = activeConnection

            val payload = JSONObject()
                .put("message", message)
                .put("mode", model.wireValue)
                .put("intelligence", intelligence.wireValue)
                .put("client", "android")
                .put("conversationId", conversationId)
                .put("projectId", projectId ?: JSONObject.NULL)
                .put("validateCode", validateCode)
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

            activeConnection.outputStream.use { stream ->
                stream.write(payload.toByteArray(Charsets.UTF_8))
            }

            if (activeConnection.responseCode !in 200..299) {
                val errorBody = activeConnection.errorStream
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    .orEmpty()
                return failureResponse(parseError(errorBody))
            }

            var result: ChatResponse? = null
            activeConnection.inputStream.bufferedReader().useLines { lines ->
                lines.filter { it.isNotBlank() }.forEach { line ->
                    val event = JSONObject(line)
                    when (event.optString("type")) {
                        "progress" -> event.optJSONObject("progress")
                            ?.toAgentProgress()
                            ?.let(onProgress)

                        "result" -> result = event.toChatResponse()
                        "error" -> result = failureResponse(
                            event.optString("error").ifBlank {
                                "Nexora AI no pudo completar la solicitud."
                            },
                        )
                    }
                }
            }

            result ?: failureResponse("La conexión terminó antes de recibir una respuesta completa.")
        } catch (_: Exception) {
            failureResponse(
                "No se pudo conectar con Nexora AI. Revisa tu conexión e inténtalo de nuevo.",
            )
        } finally {
            connection?.disconnect()
        }
    }

    private fun JSONObject.toChatResponse(): ChatResponse {
        val traceArray = optJSONArray("trace") ?: JSONArray()
        val trace = buildList {
            for (index in 0 until traceArray.length()) {
                add(traceArray.getJSONObject(index).toAgentProgress())
            }
        }
        return ChatResponse(
            answer = optString("answer").ifBlank { "Nexora AI no devolvió contenido." },
            elapsedMs = optLong("elapsedMs"),
            agentsUsed = optInt("agentsUsed"),
            provider = optString("provider", "fallback"),
            orchestration = optString("orchestration", "single"),
            trace = trace,
            codeValidation = optJSONObject("codeValidation")?.toCodeValidation(),
            error = optString("error").takeIf { it.isNotBlank() },
        )
    }

    private fun JSONObject.toAgentProgress(): AgentProgress = AgentProgress(
        stage = optString("stage"),
        label = optString("label"),
        status = optString("status"),
        step = optInt("step"),
        totalSteps = optInt("totalSteps"),
        elapsedMs = optLong("elapsedMs"),
        agent = optString("agent").takeIf { it.isNotBlank() },
    )

    private fun JSONObject.toCodeValidation(): CodeValidationSummary = CodeValidationSummary(
        status = optString("status"),
        language = optString("language").takeIf { it.isNotBlank() },
        exitCode = if (isNull("exitCode")) null else optInt("exitCode"),
        durationMs = if (isNull("durationMs")) null else optLong("durationMs"),
        output = optString("output").takeIf { it.isNotBlank() },
        reason = optString("reason").takeIf { it.isNotBlank() },
    )

    private fun parseError(body: String): String = runCatching {
        JSONObject(body).optString("error")
    }.getOrNull().orEmpty().ifBlank { "No se pudo procesar la solicitud." }

    private fun failureResponse(message: String) = ChatResponse(
        answer = message,
        elapsedMs = 0,
        agentsUsed = 0,
        provider = "fallback",
        orchestration = "single",
        trace = emptyList(),
        error = message,
    )
}
