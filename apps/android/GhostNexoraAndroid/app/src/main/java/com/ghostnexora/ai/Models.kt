package com.ghostnexora.ai

import java.util.UUID

enum class NexoraModel(
    val wireValue: String,
    val label: String,
    val description: String,
) {
    ASSISTANT("assistant", "Asistente", "Conversaciones naturales, ideas, aprendizaje y ayuda cotidiana."),
    AUTO("auto", "Automático", "Nexora selecciona la especialidad más adecuada."),
    FULL_STACK("fullstack", "Full-stack", "Web, frontend, backend, APIs y bases de datos."),
    ANDROID("android", "Android", "Kotlin, Jetpack Compose, Gradle y arquitectura móvil."),
    BACKEND("backend", "Backend", "Servicios, contratos, autenticación y persistencia."),
    SECURITY("security", "Security", "Auditoría defensiva, hardening y reducción de riesgo."),
    DATA("data", "Data", "SQL, análisis, transformación y reportes."),
    DEVOPS("devops", "DevOps", "CI/CD, Docker, VPS, observabilidad y despliegue."),
}

enum class IntelligenceLevel(
    val wireValue: String,
    val label: String,
    val description: String,
    val agentCount: Int,
) {
    INSTANT("instant", "Instantánea", "Una IA responde con máxima rapidez.", 1),
    MEDIUM("medium", "Media", "Tres agentes planifican, resuelven y sintetizan.", 3),
    HIGH("high", "Alta", "Cuatro agentes añaden revisión técnica y calidad.", 4),
    MAXIMUM("maximum", "Máxima", "Seis agentes colaboran, verifican y consolidan.", 6),
}

data class PendingAttachment(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val mimeType: String,
    val textContent: String? = null,
    val imageBase64: String? = null,
    val sizeBytes: Long,
)

data class AgentProgress(
    val stage: String,
    val label: String,
    val status: String,
    val step: Int,
    val totalSteps: Int,
    val elapsedMs: Long,
    val agent: String? = null,
)

data class CodeValidationSummary(
    val status: String,
    val language: String? = null,
    val exitCode: Int? = null,
    val durationMs: Long? = null,
    val output: String? = null,
    val reason: String? = null,
)

data class ChatResponse(
    val answer: String,
    val elapsedMs: Long,
    val agentsUsed: Int,
    val provider: String,
    val orchestration: String,
    val trace: List<AgentProgress>,
    val codeValidation: CodeValidationSummary? = null,
    val error: String? = null,
)

enum class RequestStatus(val wireValue: String) {
    QUEUED("queued"),
    PROCESSING("processing"),
    COMPLETED("completed"),
    FAILED("failed"),
}

enum class AndroidBuildStatus(val wireValue: String) {
    QUEUED("queued"),
    BUILDING("building"),
    COMPLETED("completed"),
    FAILED("failed"),
    EXPIRED("expired"),
}

data class AndroidBuildArtifact(
    val requestId: String,
    val status: AndroidBuildStatus,
    val appName: String,
    val progressLabel: String,
    val fileName: String? = null,
    val downloadUrl: String? = null,
    val sha256: String? = null,
    val expiresAt: String? = null,
    val signatureSchemes: List<String> = emptyList(),
    val error: String? = null,
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val attachmentNames: List<String> = emptyList(),
    val elapsedMs: Long? = null,
    val agentsUsed: Int? = null,
    val provider: String? = null,
    val trace: List<AgentProgress> = emptyList(),
    val codeValidation: CodeValidationSummary? = null,
    val requestId: String? = null,
    val requestStatus: RequestStatus? = null,
    val buildArtifact: AndroidBuildArtifact? = null,
    val variantGroupId: String? = null,
    val variantIndex: Int? = null,
)

data class PendingChatRequest(
    val requestId: String,
    val requestToken: String,
    val conversationId: String,
    val projectId: String?,
    val message: String,
    val model: NexoraModel,
    val intelligence: IntelligenceLevel,
    val validateCode: Boolean,
    val attachments: List<PendingAttachment>,
    val submitted: Boolean = false,
)

data class PendingAndroidBuildRequest(
    val requestId: String,
    val requestToken: String,
    val deviceId: String,
    val conversationId: String,
    val messageId: String,
    val appName: String,
    val accentColor: String,
    val sourcePrompt: String,
    val sourceContent: String,
    val submitted: Boolean = false,
)

data class ChatJobSnapshot(
    val requestId: String,
    val status: RequestStatus,
    val progress: List<AgentProgress>,
    val result: ChatResponse? = null,
    val error: String? = null,
)

data class MobileRelease(
    val version: String,
    val versionCode: Int,
    val downloadUrl: String,
    val sha256: String,
    val userBuildsEnabled: Boolean,
)

data class ChatProject(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
)

data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Nuevo chat",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val model: NexoraModel = NexoraModel.AUTO,
    val intelligence: IntelligenceLevel = IntelligenceLevel.MEDIUM,
    val projectId: String? = null,
    val isPinned: Boolean = false,
    val validateCode: Boolean = false,
    val parentSessionId: String? = null,
    val branchedFromMessageId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
)