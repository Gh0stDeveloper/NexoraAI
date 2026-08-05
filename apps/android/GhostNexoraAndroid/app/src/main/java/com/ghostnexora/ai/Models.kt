package com.ghostnexora.ai

import java.util.UUID

enum class NexoraModel(
    val wireValue: String,
    val label: String,
    val description: String,
) {
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

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val attachmentNames: List<String> = emptyList(),
)

data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Nuevo chat",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val model: NexoraModel = NexoraModel.AUTO,
    val intelligence: IntelligenceLevel = IntelligenceLevel.MEDIUM,
    val messages: List<ChatMessage> = emptyList(),
)
