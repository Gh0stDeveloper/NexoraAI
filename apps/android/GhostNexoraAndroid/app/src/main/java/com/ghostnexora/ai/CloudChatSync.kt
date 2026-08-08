package com.ghostnexora.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class CloudChatSync(context: Context) {
    private val preferences = context.getSharedPreferences(
        "nexora_chat_history",
        Context.MODE_PRIVATE,
    )

    fun sync(authStore: AuthStore) {
        val remoteState = AuthApi.getCloudState(authStore)
        val remotePayload = remoteState.optJSONObject("payload") ?: emptyPayload()
        val merged = mergePayload(localPayload(), remotePayload)
        persistPayload(merged)
        AuthApi.putCloudState(authStore, merged)
    }

    fun push(authStore: AuthStore) {
        AuthApi.putCloudState(authStore, localPayload())
    }

    private fun localPayload(): JSONObject = JSONObject()
        .put("sessions", readArray(KEY_SESSIONS))
        .put("projects", readArray(KEY_PROJECTS))

    private fun persistPayload(payload: JSONObject) {
        preferences.edit()
            .putString(KEY_SESSIONS, payload.optJSONArray("sessions")?.toString() ?: "[]")
            .putString(KEY_PROJECTS, payload.optJSONArray("projects")?.toString() ?: "[]")
            .commit()
    }

    private fun readArray(key: String): JSONArray = runCatching {
        JSONArray(preferences.getString(key, "[]") ?: "[]")
    }.getOrElse { JSONArray() }

    private fun mergePayload(local: JSONObject, remote: JSONObject): JSONObject = JSONObject()
        .put(
            "sessions",
            mergeArrays(
                local.optJSONArray("sessions") ?: JSONArray(),
                remote.optJSONArray("sessions") ?: JSONArray(),
                MAX_SESSIONS,
                ::mergeSession,
            ),
        )
        .put(
            "projects",
            mergeArrays(
                local.optJSONArray("projects") ?: JSONArray(),
                remote.optJSONArray("projects") ?: JSONArray(),
                MAX_PROJECTS,
            ) { left, right -> newer(left, right) },
        )

    private fun mergeArrays(
        left: JSONArray,
        right: JSONArray,
        limit: Int,
        merge: (JSONObject, JSONObject) -> JSONObject,
    ): JSONArray {
        val items = linkedMapOf<String, JSONObject>()
        fun include(array: JSONArray) {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id")
                if (id.isBlank()) continue
                val existing = items[id]
                items[id] = if (existing == null) clone(item) else merge(existing, item)
            }
        }
        include(left)
        include(right)

        return JSONArray().apply {
            items.values
                .sortedWith(
                    compareByDescending<JSONObject> { it.optBoolean("isPinned", false) }
                        .thenByDescending { it.optLong("updatedAt", 0L) },
                )
                .take(limit)
                .forEach(::put)
        }
    }

    private fun mergeSession(left: JSONObject, right: JSONObject): JSONObject {
        val latest = newer(left, right)
        val mergedMessages = mergeMessages(
            left.optJSONArray("messages") ?: JSONArray(),
            right.optJSONArray("messages") ?: JSONArray(),
        )
        return clone(latest).put("messages", mergedMessages)
    }

    private fun mergeMessages(left: JSONArray, right: JSONArray): JSONArray {
        val items = linkedMapOf<String, JSONObject>()
        fun include(array: JSONArray) {
            for (index in 0 until array.length()) {
                val message = array.optJSONObject(index) ?: continue
                val id = message.optString("id")
                if (id.isBlank()) continue
                val existing = items[id]
                items[id] = if (existing == null) clone(message) else newer(existing, message)
            }
        }
        include(left)
        include(right)
        return JSONArray().apply {
            items.values
                .sortedBy { it.optLong("createdAt", 0L) }
                .takeLast(MAX_MESSAGES_PER_SESSION)
                .forEach(::put)
        }
    }

    private fun newer(left: JSONObject, right: JSONObject): JSONObject =
        if (right.optLong("updatedAt", 0L) > left.optLong("updatedAt", 0L)) {
            clone(right)
        } else {
            clone(left)
        }

    private fun clone(value: JSONObject): JSONObject = JSONObject(value.toString())

    private fun emptyPayload(): JSONObject = JSONObject()
        .put("sessions", JSONArray())
        .put("projects", JSONArray())

    private companion object {
        const val KEY_SESSIONS = "sessions"
        const val KEY_PROJECTS = "projects"
        const val MAX_PROJECTS = 30
        const val MAX_SESSIONS = 100
        const val MAX_MESSAGES_PER_SESSION = 300
    }
}
