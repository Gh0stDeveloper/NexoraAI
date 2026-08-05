package com.ghostnexora.ai

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object ApiClient {
    class ApiException(message: String, val retryable: Boolean) : Exception(message)

    fun submitChatJob(request: PendingChatRequest): ChatJobSnapshot {
        val payload = JSONObject()
            .put("requestId", request.requestId)
            .put("requestToken", request.requestToken)
            .put("message", request.message)
            .put("mode", request.model.wireValue)
            .put("intelligence", request.intelligence.wireValue)
            .put("client", "android")
            .put("conversationId", request.conversationId)
            .put("validateCode", request.validateCode)
            .apply { request.projectId?.let { put("projectId", it) } }
            .put("attachments", request.attachments.toJson())
        val response = executeJson(
            method = "POST",
            path = "/api/mobile/chat/jobs",
            body = payload,
        )
        return response.getJSONObject("job").toChatJobSnapshot()
    }

    fun getChatJob(requestId: String, requestToken: String): ChatJobSnapshot {
        val response = executeJson(
            method = "GET",
            path = "/api/mobile/chat/jobs/$requestId",
            requestToken = requestToken,
        )
        return response.getJSONObject("job").toChatJobSnapshot()
    }

    fun submitAndroidBuild(request: PendingAndroidBuildRequest): AndroidBuildArtifact {
        val payload = JSONObject()
            .put("requestId", request.requestId)
            .put("requestToken", request.requestToken)
            .put("deviceId", request.deviceId)
            .put("appName", request.appName)
            .put("accentColor", request.accentColor)
            .put("sourcePrompt", request.sourcePrompt)
            .put("sourceContent", request.sourceContent)
        val response = executeJson(
            method = "POST",
            path = "/api/mobile/builds",
            body = payload,
        )
        return response.getJSONObject("build").toAndroidBuildArtifact()
    }

    fun getAndroidBuild(requestId: String, requestToken: String): AndroidBuildArtifact {
        val response = executeJson(
            method = "GET",
            path = "/api/mobile/builds/$requestId",
            requestToken = requestToken,
        )
        return response.getJSONObject("build").toAndroidBuildArtifact()
    }

    fun getLatestMobileRelease(): MobileRelease? {
        val response = executeJson(method = "GET", path = "/api/mobile/status")
        val release = response.optJSONObject("androidRelease") ?: return null
        val downloadUrl = release.nullableString("downloadUrl")
            ?: release.nullableString("stableDownloadUrl")
            ?: return null
        return MobileRelease(
            version = release.optString("version"),
            versionCode = release.optInt("versionCode"),
            downloadUrl = downloadUrl,
            sha256 = release.optString("sha256"),
            userBuildsEnabled = response
                .optJSONObject("userBuilds")
                ?.optBoolean("enabled") == true,
        )
    }

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
                .apply {
                    projectId?.let { put("projectId", it) }
                }
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

    private fun JSONObject.toChatJobSnapshot(): ChatJobSnapshot {
        val progressArray = optJSONArray("progress") ?: JSONArray()
        val progress = buildList {
            for (index in 0 until progressArray.length()) {
                add(progressArray.getJSONObject(index).toAgentProgress())
            }
        }
        return ChatJobSnapshot(
            requestId = optString("id"),
            status = RequestStatus.entries.firstOrNull {
                it.wireValue == optString("status")
            } ?: RequestStatus.QUEUED,
            progress = progress,
            result = optJSONObject("result")?.toChatResponse(),
            error = optString("error").takeIf { it.isNotBlank() },
        )
    }

    private fun JSONObject.toAndroidBuildArtifact(): AndroidBuildArtifact {
        val schemes = optJSONArray("signatureSchemes") ?: JSONArray()
        return AndroidBuildArtifact(
            requestId = optString("id"),
            status = AndroidBuildStatus.entries.firstOrNull {
                it.wireValue == optString("status")
            } ?: AndroidBuildStatus.QUEUED,
            appName = optString("appName", "Aplicación Nexora"),
            progressLabel = optString("progressLabel", "En cola"),
            fileName = nullableString("fileName"),
            downloadUrl = nullableString("downloadUrl"),
            sha256 = nullableString("sha256"),
            expiresAt = nullableString("expiresAt"),
            signatureSchemes = buildList {
                for (index in 0 until schemes.length()) add(schemes.optString(index))
            },
            error = nullableString("error"),
        )
    }

    private fun JSONObject.nullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

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

    private fun List<PendingAttachment>.toJson(): JSONArray = JSONArray().apply {
        forEach { attachment ->
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
    }

    private fun executeJson(
        method: String,
        path: String,
        requestToken: String? = null,
        body: JSONObject? = null,
    ): JSONObject {
        var connection: HttpURLConnection? = null
        try {
            val endpoint = NativeBridge.apiOrigin().trimEnd('/') + path
            val activeConnection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 20_000
                readTimeout = 120_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty(NativeBridge.clientHeaderName(), "android")
                setRequestProperty(NativeBridge.versionHeaderName(), BuildConfig.VERSION_NAME)
                requestToken?.let { setRequestProperty("X-Nexora-Request-Token", it) }
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
            }
            connection = activeConnection
            if (body != null) {
                activeConnection.outputStream.use {
                    it.write(body.toString().toByteArray(Charsets.UTF_8))
                }
            }
            val status = activeConnection.responseCode
            val responseBody = (if (status in 200..299) {
                activeConnection.inputStream
            } else {
                activeConnection.errorStream
            })?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                throw ApiException(
                    parseError(responseBody),
                    retryable = status == 408 || status == 409 || status == 425 ||
                        status == 429 || status >= 500,
                )
            }
            return JSONObject(responseBody)
        } catch (error: ApiException) {
            throw error
        } catch (error: Exception) {
            throw ApiException(
                error.message ?: "No se pudo conectar con Nexora AI.",
                retryable = true,
            )
        } finally {
            connection?.disconnect()
        }
    }

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
