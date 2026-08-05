package com.ghostnexora.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class ChatStore(context: Context) {
    private val preferences = context.getSharedPreferences("nexora_chat_history", Context.MODE_PRIVATE)

    fun loadSessions(): List<ChatSession> = decodeArray(KEY_SESSIONS) { it.toChatSession() }

    fun loadProjects(): List<ChatProject> = decodeArray(KEY_PROJECTS) { it.toChatProject() }

    fun save(sessions: List<ChatSession>, projects: List<ChatProject>) {
        val sessionArray = JSONArray()
        sessions
            .sortedWith(compareByDescending<ChatSession> { it.isPinned }.thenByDescending { it.updatedAt })
            .take(MAX_SESSIONS)
            .forEach { sessionArray.put(it.toJson()) }

        val projectArray = JSONArray()
        projects
            .sortedWith(compareByDescending<ChatProject> { it.isPinned }.thenByDescending { it.updatedAt })
            .take(MAX_PROJECTS)
            .forEach { projectArray.put(it.toJson()) }

        preferences.edit()
            .putString(KEY_SESSIONS, sessionArray.toString())
            .putString(KEY_PROJECTS, projectArray.toString())
            .apply()
    }

    private fun <T> decodeArray(key: String, transform: (JSONObject) -> T): List<T> {
        val raw = preferences.getString(key, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) add(transform(array.getJSONObject(index)))
            }
        }.getOrElse { emptyList() }
    }

    private fun ChatSession.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)
        .put("model", model.wireValue)
        .put("intelligence", intelligence.wireValue)
        .put("projectId", projectId ?: JSONObject.NULL)
        .put("isPinned", isPinned)
        .put("validateCode", validateCode)
        .put(
            "messages",
            JSONArray().apply {
                messages.takeLast(MAX_MESSAGES_PER_SESSION).forEach { put(it.toJson()) }
            },
        )

    private fun ChatProject.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)
        .put("isPinned", isPinned)

    private fun ChatMessage.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("role", role)
        .put("content", content)
        .put("createdAt", createdAt)
        .put("attachmentNames", JSONArray(attachmentNames))
        .put("elapsedMs", elapsedMs ?: JSONObject.NULL)
        .put("agentsUsed", agentsUsed ?: JSONObject.NULL)
        .put("provider", provider ?: JSONObject.NULL)
        .put("trace", JSONArray().apply { trace.forEach { put(it.toJson()) } })
        .put("codeValidation", codeValidation?.toJson() ?: JSONObject.NULL)

    private fun AgentProgress.toJson(): JSONObject = JSONObject()
        .put("stage", stage)
        .put("label", label)
        .put("status", status)
        .put("step", step)
        .put("totalSteps", totalSteps)
        .put("elapsedMs", elapsedMs)
        .put("agent", agent ?: JSONObject.NULL)

    private fun CodeValidationSummary.toJson(): JSONObject = JSONObject()
        .put("status", status)
        .put("language", language ?: JSONObject.NULL)
        .put("exitCode", exitCode ?: JSONObject.NULL)
        .put("durationMs", durationMs ?: JSONObject.NULL)
        .put("output", output ?: JSONObject.NULL)
        .put("reason", reason ?: JSONObject.NULL)

    private fun JSONObject.toChatSession(): ChatSession {
        val messageArray = optJSONArray("messages") ?: JSONArray()
        val messages = buildList {
            for (index in 0 until messageArray.length()) {
                add(messageArray.getJSONObject(index).toChatMessage())
            }
        }
        return ChatSession(
            id = optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
            title = optString("title", "Nuevo chat"),
            createdAt = optLong("createdAt", System.currentTimeMillis()),
            updatedAt = optLong("updatedAt", System.currentTimeMillis()),
            model = NexoraModel.entries.firstOrNull { it.wireValue == optString("model") }
                ?: NexoraModel.AUTO,
            intelligence = IntelligenceLevel.entries.firstOrNull {
                it.wireValue == optString("intelligence")
            } ?: IntelligenceLevel.MEDIUM,
            projectId = nullableString("projectId"),
            isPinned = optBoolean("isPinned", false),
            validateCode = optBoolean("validateCode", false),
            messages = messages,
        )
    }

    private fun JSONObject.toChatProject(): ChatProject = ChatProject(
        id = optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
        name = optString("name", "Proyecto"),
        createdAt = optLong("createdAt", System.currentTimeMillis()),
        updatedAt = optLong("updatedAt", System.currentTimeMillis()),
        isPinned = optBoolean("isPinned", false),
    )

    private fun JSONObject.toChatMessage(): ChatMessage {
        val attachmentArray = optJSONArray("attachmentNames") ?: JSONArray()
        val attachmentNames = buildList {
            for (index in 0 until attachmentArray.length()) add(attachmentArray.optString(index))
        }
        val traceArray = optJSONArray("trace") ?: JSONArray()
        val trace = buildList {
            for (index in 0 until traceArray.length()) add(traceArray.getJSONObject(index).toProgress())
        }
        return ChatMessage(
            id = optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
            role = optString("role", "assistant"),
            content = optString("content"),
            createdAt = optLong("createdAt", System.currentTimeMillis()),
            attachmentNames = attachmentNames,
            elapsedMs = nullableLong("elapsedMs"),
            agentsUsed = nullableInt("agentsUsed"),
            provider = nullableString("provider"),
            trace = trace,
            codeValidation = optJSONObject("codeValidation")?.toCodeValidation(),
        )
    }

    private fun JSONObject.toProgress(): AgentProgress = AgentProgress(
        stage = optString("stage"),
        label = optString("label"),
        status = optString("status"),
        step = optInt("step"),
        totalSteps = optInt("totalSteps"),
        elapsedMs = optLong("elapsedMs"),
        agent = nullableString("agent"),
    )

    private fun JSONObject.toCodeValidation(): CodeValidationSummary = CodeValidationSummary(
        status = optString("status"),
        language = nullableString("language"),
        exitCode = nullableInt("exitCode"),
        durationMs = nullableLong("durationMs"),
        output = nullableString("output"),
        reason = nullableString("reason"),
    )

    private fun JSONObject.nullableString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun JSONObject.nullableLong(key: String): Long? =
        if (isNull(key) || !has(key)) null else optLong(key)

    private fun JSONObject.nullableInt(key: String): Int? =
        if (isNull(key) || !has(key)) null else optInt(key)

    private companion object {
        const val KEY_SESSIONS = "sessions"
        const val KEY_PROJECTS = "projects"
        const val MAX_PROJECTS = 30
        const val MAX_SESSIONS = 100
        const val MAX_MESSAGES_PER_SESSION = 300
    }
}
