package com.ghostnexora.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class ChatStore(context: Context) {
    private val preferences = context.getSharedPreferences("nexora_chat_history", Context.MODE_PRIVATE)

    fun load(): List<ChatSession> {
        val raw = preferences.getString(KEY_SESSIONS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    add(array.getJSONObject(index).toChatSession())
                }
            }
        }.getOrElse { emptyList() }
    }

    fun save(sessions: List<ChatSession>) {
        val array = JSONArray()
        sessions.sortedByDescending { it.updatedAt }.take(MAX_SESSIONS).forEach { session ->
            array.put(session.toJson())
        }
        preferences.edit().putString(KEY_SESSIONS, array.toString()).apply()
    }

    private fun ChatSession.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)
        .put("model", model.wireValue)
        .put("intelligence", intelligence.wireValue)
        .put(
            "messages",
            JSONArray().apply {
                messages.takeLast(MAX_MESSAGES_PER_SESSION).forEach { put(it.toJson()) }
            },
        )

    private fun ChatMessage.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("role", role)
        .put("content", content)
        .put("createdAt", createdAt)
        .put("attachmentNames", JSONArray(attachmentNames))

    private fun JSONObject.toChatSession(): ChatSession {
        val messageArray = optJSONArray("messages") ?: JSONArray()
        val messages = buildList {
            for (index in 0 until messageArray.length()) {
                add(messageArray.getJSONObject(index).toChatMessage())
            }
        }
        return ChatSession(
            id = optString("id"),
            title = optString("title", "Nuevo chat"),
            createdAt = optLong("createdAt", System.currentTimeMillis()),
            updatedAt = optLong("updatedAt", System.currentTimeMillis()),
            model = NexoraModel.entries.firstOrNull { it.wireValue == optString("model") } ?: NexoraModel.AUTO,
            intelligence = IntelligenceLevel.entries.firstOrNull {
                it.wireValue == optString("intelligence")
            } ?: IntelligenceLevel.MEDIUM,
            messages = messages,
        )
    }

    private fun JSONObject.toChatMessage(): ChatMessage {
        val attachmentArray = optJSONArray("attachmentNames") ?: JSONArray()
        val attachmentNames = buildList {
            for (index in 0 until attachmentArray.length()) add(attachmentArray.optString(index))
        }
        return ChatMessage(
            id = optString("id"),
            role = optString("role", "assistant"),
            content = optString("content"),
            createdAt = optLong("createdAt", System.currentTimeMillis()),
            attachmentNames = attachmentNames,
        )
    }

    private companion object {
        const val KEY_SESSIONS = "sessions"
        const val MAX_SESSIONS = 50
        const val MAX_MESSAGES_PER_SESSION = 200
    }
}
