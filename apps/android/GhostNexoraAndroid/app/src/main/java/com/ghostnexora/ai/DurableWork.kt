package com.ghostnexora.ai

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException
import java.util.UUID
import java.util.concurrent.TimeUnit

class PendingWorkStore(private val context: Context) {
    private val chatDirectory = File(context.filesDir, "pending_requests/chat")
    private val buildDirectory = File(context.filesDir, "pending_requests/builds")

    init {
        chatDirectory.mkdirs()
        buildDirectory.mkdirs()
    }

    fun saveChat(request: PendingChatRequest) {
        writeAtomically(chatFile(request.requestId), request.toJson())
    }

    fun loadChat(requestId: String): PendingChatRequest? =
        readJson(chatFile(requestId))?.toPendingChatRequest()

    fun markChatSubmitted(request: PendingChatRequest) {
        saveChat(request.copy(submitted = true))
    }

    fun deleteChat(requestId: String) {
        chatFile(requestId).delete()
    }

    fun pendingChatIds(): List<String> = pendingIds(chatDirectory)

    fun saveBuild(request: PendingAndroidBuildRequest) {
        writeAtomically(buildFile(request.requestId), request.toJson())
    }

    fun loadBuild(requestId: String): PendingAndroidBuildRequest? =
        readJson(buildFile(requestId))?.toPendingAndroidBuildRequest()

    fun markBuildSubmitted(request: PendingAndroidBuildRequest) {
        saveBuild(request.copy(submitted = true))
    }

    fun deleteBuild(requestId: String) {
        buildFile(requestId).delete()
    }

    fun pendingBuildIds(): List<String> = pendingIds(buildDirectory)

    fun deviceId(): String {
        val preferences = context.getSharedPreferences("nexora_device", Context.MODE_PRIVATE)
        val existing = preferences.getString("installation_id", null)
        if (!existing.isNullOrBlank()) return existing
        val created = UUID.randomUUID().toString()
        preferences.edit().putString("installation_id", created).commit()
        return created
    }

    private fun chatFile(id: String): File = safeFile(chatDirectory, id)

    private fun buildFile(id: String): File = safeFile(buildDirectory, id)

    private fun safeFile(directory: File, id: String): File {
        require(runCatching { UUID.fromString(id) }.isSuccess) { "Invalid work identifier" }
        return File(directory, "$id.json")
    }

    private fun pendingIds(directory: File): List<String> = directory
        .listFiles { file -> file.isFile && file.extension == "json" }
        .orEmpty()
        .mapNotNull { file ->
            file.nameWithoutExtension.takeIf { runCatching { UUID.fromString(it) }.isSuccess }
        }

    private fun readJson(file: File): JSONObject? = runCatching {
        JSONObject(file.readText(Charsets.UTF_8))
    }.getOrNull()

    private fun writeAtomically(file: File, value: JSONObject) {
        val temporary = File(file.parentFile, ".${file.name}.${System.nanoTime()}")
        temporary.writeText(value.toString(), Charsets.UTF_8)
        try {
            Files.move(
                temporary.toPath(),
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                temporary.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (error: Exception) {
            temporary.delete()
            throw IllegalStateException("No se pudo guardar la solicitud pendiente.", error)
        }
    }

    private fun PendingChatRequest.toJson(): JSONObject = JSONObject()
        .put("requestId", requestId)
        .put("requestToken", requestToken)
        .put("conversationId", conversationId)
        .put("projectId", projectId ?: JSONObject.NULL)
        .put("message", message)
        .put("model", model.wireValue)
        .put("intelligence", intelligence.wireValue)
        .put("validateCode", validateCode)
        .put("submitted", submitted)
        .put(
            "attachments",
            JSONArray().apply {
                attachments.forEach { attachment ->
                    put(
                        JSONObject()
                            .put("id", attachment.id)
                            .put("name", attachment.name)
                            .put("mimeType", attachment.mimeType)
                            .put("textContent", attachment.textContent ?: JSONObject.NULL)
                            .put("imageBase64", attachment.imageBase64 ?: JSONObject.NULL)
                            .put("sizeBytes", attachment.sizeBytes),
                    )
                }
            },
        )

    private fun JSONObject.toPendingChatRequest(): PendingChatRequest {
        val attachmentArray = optJSONArray("attachments") ?: JSONArray()
        return PendingChatRequest(
            requestId = getString("requestId"),
            requestToken = getString("requestToken"),
            conversationId = getString("conversationId"),
            projectId = nullableString("projectId"),
            message = getString("message"),
            model = NexoraModel.entries.firstOrNull { it.wireValue == optString("model") }
                ?: NexoraModel.ASSISTANT,
            intelligence = IntelligenceLevel.entries.firstOrNull {
                it.wireValue == optString("intelligence")
            } ?: IntelligenceLevel.MEDIUM,
            validateCode = optBoolean("validateCode"),
            attachments = buildList {
                for (index in 0 until attachmentArray.length()) {
                    val item = attachmentArray.getJSONObject(index)
                    add(
                        PendingAttachment(
                            id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                            name = item.getString("name"),
                            mimeType = item.getString("mimeType"),
                            textContent = item.nullableString("textContent"),
                            imageBase64 = item.nullableString("imageBase64"),
                            sizeBytes = item.getLong("sizeBytes"),
                        ),
                    )
                }
            },
            submitted = optBoolean("submitted"),
        )
    }

    private fun PendingAndroidBuildRequest.toJson(): JSONObject = JSONObject()
        .put("requestId", requestId)
        .put("requestToken", requestToken)
        .put("deviceId", deviceId)
        .put("conversationId", conversationId)
        .put("messageId", messageId)
        .put("appName", appName)
        .put("accentColor", accentColor)
        .put("sourcePrompt", sourcePrompt)
        .put("sourceContent", sourceContent)
        .put("submitted", submitted)

    private fun JSONObject.toPendingAndroidBuildRequest(): PendingAndroidBuildRequest =
        PendingAndroidBuildRequest(
            requestId = getString("requestId"),
            requestToken = getString("requestToken"),
            deviceId = getString("deviceId"),
            conversationId = getString("conversationId"),
            messageId = getString("messageId"),
            appName = getString("appName"),
            accentColor = getString("accentColor"),
            sourcePrompt = getString("sourcePrompt"),
            sourceContent = getString("sourceContent"),
            submitted = optBoolean("submitted"),
        )

    private fun JSONObject.nullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
}

object DurableWorkScheduler {
    private const val REQUEST_ID = "request_id"
    private val connectedNetwork = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun newRequestToken(): String {
        val bytes = ByteArray(32).also(SecureRandom()::nextBytes)
        return bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    fun enqueueChat(context: Context, requestId: String) {
        val work = OneTimeWorkRequestBuilder<ChatJobWorker>()
            .setInputData(workDataOf(REQUEST_ID to requestId))
            .setConstraints(connectedNetwork)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "nexora-chat-$requestId",
            ExistingWorkPolicy.KEEP,
            work,
        )
    }

    fun enqueueBuild(context: Context, requestId: String) {
        val work = OneTimeWorkRequestBuilder<AndroidBuildJobWorker>()
            .setInputData(workDataOf(REQUEST_ID to requestId))
            .setConstraints(connectedNetwork)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "nexora-build-$requestId",
            ExistingWorkPolicy.KEEP,
            work,
        )
    }

    fun resumeAll(context: Context) {
        val store = PendingWorkStore(context)
        store.pendingChatIds().forEach { enqueueChat(context, it) }
        store.pendingBuildIds().forEach { enqueueBuild(context, it) }
    }

    internal fun requestId(data: androidx.work.Data): String? = data.getString(REQUEST_ID)
}

class ChatJobWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result {
        val requestId = DurableWorkScheduler.requestId(inputData) ?: return Result.failure()
        val pendingStore = PendingWorkStore(applicationContext)
        val request = pendingStore.loadChat(requestId) ?: return Result.success()
        val chatStore = ChatStore(applicationContext)
        return try {
            val snapshot = if (request.submitted) {
                ApiClient.getChatJob(request.requestId, request.requestToken)
            } else {
                ApiClient.submitChatJob(request).also { pendingStore.markChatSubmitted(request) }
            }
            chatStore.updateChatJob(request.conversationId, snapshot)
            when (snapshot.status) {
                RequestStatus.COMPLETED, RequestStatus.FAILED -> {
                    pendingStore.deleteChat(request.requestId)
                    Result.success()
                }
                else -> Result.retry()
            }
        } catch (error: ApiClient.ApiException) {
            if (error.retryable && runAttemptCount < MAX_ATTEMPTS) {
                Result.retry()
            } else {
                chatStore.updateChatJob(
                    request.conversationId,
                    ChatJobSnapshot(
                        requestId = request.requestId,
                        status = RequestStatus.FAILED,
                        progress = emptyList(),
                        error = error.message ?: "No se pudo completar la solicitud.",
                    ),
                )
                pendingStore.deleteChat(request.requestId)
                Result.success()
            }
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 90
    }
}

class AndroidBuildJobWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result {
        val requestId = DurableWorkScheduler.requestId(inputData) ?: return Result.failure()
        val pendingStore = PendingWorkStore(applicationContext)
        val request = pendingStore.loadBuild(requestId) ?: return Result.success()
        val chatStore = ChatStore(applicationContext)
        return try {
            val artifact = if (request.submitted) {
                ApiClient.getAndroidBuild(request.requestId, request.requestToken)
            } else {
                ApiClient.submitAndroidBuild(request).also {
                    pendingStore.markBuildSubmitted(request)
                }
            }
            chatStore.updateAndroidBuild(request.conversationId, request.messageId, artifact)
            when (artifact.status) {
                AndroidBuildStatus.COMPLETED,
                AndroidBuildStatus.FAILED,
                AndroidBuildStatus.EXPIRED -> {
                    pendingStore.deleteBuild(request.requestId)
                    Result.success()
                }
                else -> Result.retry()
            }
        } catch (error: ApiClient.ApiException) {
            if (error.retryable && runAttemptCount < MAX_ATTEMPTS) {
                Result.retry()
            } else {
                chatStore.updateAndroidBuild(
                    request.conversationId,
                    request.messageId,
                    AndroidBuildArtifact(
                        requestId = request.requestId,
                        status = AndroidBuildStatus.FAILED,
                        appName = request.appName,
                        progressLabel = "La compilación no pudo completarse",
                        error = error.message,
                    ),
                )
                pendingStore.deleteBuild(request.requestId)
                Result.success()
            }
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 90
    }
}
