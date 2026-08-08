package com.ghostnexora.ai

import org.json.JSONObject

data class NexoraUser(
    val id: String,
    val name: String,
    val email: String?,
    val imageUrl: String?,
)

data class NexoraAuthSession(
    val user: NexoraUser,
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresAt: Long,
    val refreshExpiresAt: Long,
)

data class NexoraAccountSession(
    val id: String,
    val deviceName: String,
    val createdAt: String,
    val lastUsedAt: String,
    val current: Boolean,
)

data class NexoraAccountOverview(
    val user: NexoraUser,
    val emailVerified: Boolean,
    val providers: List<String>,
    val hasPassword: Boolean,
    val sessions: List<NexoraAccountSession>,
)

data class PendingOAuth(
    val provider: String,
    val state: String,
    val verifier: String,
    val linking: Boolean = false,
    val startedAt: Long = System.currentTimeMillis(),
)

enum class AuthScreenMode {
    WELCOME,
    LOGIN,
    REGISTER,
    FORGOT_PASSWORD,
    RESET_PASSWORD,
}

internal fun NexoraAuthSession.toJson(): JSONObject = JSONObject()
    .put(
        "user",
        JSONObject()
            .put("id", user.id)
            .put("name", user.name)
            .put("email", user.email ?: JSONObject.NULL)
            .put("imageUrl", user.imageUrl ?: JSONObject.NULL),
    )
    .put("accessToken", accessToken)
    .put("refreshToken", refreshToken)
    .put("accessExpiresAt", accessExpiresAt)
    .put("refreshExpiresAt", refreshExpiresAt)

internal fun JSONObject.toAuthSession(): NexoraAuthSession {
    val user = getJSONObject("user")
    return NexoraAuthSession(
        user = NexoraUser(
            id = user.getString("id"),
            name = user.optString("name", "Usuario Nexora"),
            email = user.optNullableString("email"),
            imageUrl = user.optNullableString("imageUrl"),
        ),
        accessToken = getString("accessToken"),
        refreshToken = getString("refreshToken"),
        accessExpiresAt = getLong("accessExpiresAt"),
        refreshExpiresAt = getLong("refreshExpiresAt"),
    )
}

internal fun PendingOAuth.toJson(): JSONObject = JSONObject()
    .put("provider", provider)
    .put("state", state)
    .put("verifier", verifier)
    .put("linking", linking)
    .put("startedAt", startedAt)

internal fun JSONObject.toPendingOAuth(): PendingOAuth = PendingOAuth(
    provider = getString("provider"),
    state = getString("state"),
    verifier = getString("verifier"),
    linking = optBoolean("linking", false),
    startedAt = optLong("startedAt", System.currentTimeMillis()),
)

private fun JSONObject.optNullableString(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
