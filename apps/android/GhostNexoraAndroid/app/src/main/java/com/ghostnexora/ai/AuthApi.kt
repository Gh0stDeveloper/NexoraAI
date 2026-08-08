package com.ghostnexora.ai

import android.net.Uri
import android.os.Build
import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant

object AuthApi {
    class AuthApiException(message: String, val status: Int) : Exception(message)

    fun socialStart(provider: String, store: AuthStore): Uri {
        val verifier = randomUrlToken(64)
        val challenge = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
            .toBase64Url()
        val state = randomUrlToken(32)
        store.savePendingOAuth(
            PendingOAuth(
                provider = provider,
                state = state,
                verifier = verifier,
            ),
        )
        return Uri.parse(NativeBridge.apiOrigin().trimEnd('/') + "/api/auth/mobile/start")
            .buildUpon()
            .appendQueryParameter("provider", provider)
            .appendQueryParameter("redirect_uri", MOBILE_CALLBACK)
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", challenge)
            .build()
    }

    fun exchangeOAuth(code: String, verifier: String): NexoraAuthSession {
        val response = executeJson(
            method = "POST",
            path = "/api/auth/mobile/exchange",
            body = JSONObject()
                .put("code", code)
                .put("codeVerifier", verifier),
        )
        return response.getJSONObject("session").toSession()
    }

    fun emailAuth(
        register: Boolean,
        name: String,
        email: String,
        password: String,
    ): NexoraAuthSession {
        val response = executeJson(
            method = "POST",
            path = "/api/auth/mobile/email",
            body = JSONObject()
                .put("action", if (register) "register" else "login")
                .put("name", name)
                .put("email", email)
                .put("password", password),
        )
        return response.getJSONObject("session").toSession()
    }

    fun requestPasswordReset(email: String) {
        executeJson(
            method = "POST",
            path = "/api/auth/mobile/password/reset",
            body = JSONObject()
                .put("action", "request")
                .put("email", email),
        )
    }

    fun confirmPasswordReset(email: String, code: String, password: String) {
        executeJson(
            method = "POST",
            path = "/api/auth/mobile/password/reset",
            body = JSONObject()
                .put("action", "confirm")
                .put("email", email)
                .put("code", code)
                .put("password", password),
        )
    }

    fun ensureFreshSession(store: AuthStore): NexoraAuthSession? {
        val current = store.loadSession() ?: return null
        val now = System.currentTimeMillis()
        if (current.refreshExpiresAt <= now) {
            store.clearSession()
            return null
        }
        if (current.accessExpiresAt > now + ACCESS_REFRESH_MARGIN_MS) return current

        val response = executeJson(
            method = "POST",
            path = "/api/auth/mobile/refresh",
            body = JSONObject().put("refreshToken", current.refreshToken),
        )
        return response.getJSONObject("session").toSession().also(store::saveSession)
    }

    fun getCurrentUser(store: AuthStore): NexoraUser? {
        val session = ensureFreshSession(store) ?: return null
        val response = executeJson(
            method = "GET",
            path = "/api/auth/mobile/me",
            bearerToken = session.accessToken,
        )
        return response.getJSONObject("user").toUser()
    }

    fun getAccountOverview(store: AuthStore): NexoraAccountOverview {
        val session = ensureFreshSession(store)
            ?: throw AuthApiException("Debes iniciar sesión.", 401)
        val account = executeJson(
            method = "GET",
            path = "/api/auth/mobile/account",
            bearerToken = session.accessToken,
        ).getJSONObject("account")
        val userObject = account.getJSONObject("user")
        val providersJson = account.optJSONArray("providers")
        val providers = buildList {
            if (providersJson != null) {
                for (index in 0 until providersJson.length()) {
                    providersJson.optString(index).takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }
        val sessionsJson = account.optJSONArray("sessions")
        val sessions = buildList {
            if (sessionsJson != null) {
                for (index in 0 until sessionsJson.length()) {
                    val item = sessionsJson.optJSONObject(index) ?: continue
                    add(
                        NexoraAccountSession(
                            id = item.optString("id"),
                            deviceName = item.optString("deviceName", "Android"),
                            createdAt = item.optString("createdAt"),
                            lastUsedAt = item.optString("lastUsedAt"),
                            current = item.optBoolean("current", false),
                        ),
                    )
                }
            }
        }
        return NexoraAccountOverview(
            user = userObject.toUser(),
            emailVerified = userObject.optBoolean("emailVerified", false),
            providers = providers,
            hasPassword = account.optBoolean("hasPassword", false),
            sessions = sessions,
        )
    }

    fun updateAccountName(store: AuthStore, name: String): NexoraUser {
        val session = ensureFreshSession(store)
            ?: throw AuthApiException("Debes iniciar sesión.", 401)
        val response = executeJson(
            method = "PATCH",
            path = "/api/auth/mobile/account",
            bearerToken = session.accessToken,
            body = JSONObject().put("name", name),
        )
        return response.getJSONObject("user").toUser()
    }

    fun requestEmailVerification(store: AuthStore) {
        val session = ensureFreshSession(store)
            ?: throw AuthApiException("Debes iniciar sesión.", 401)
        executeJson(
            method = "POST",
            path = "/api/auth/mobile/account/verify",
            bearerToken = session.accessToken,
            body = JSONObject().put("action", "request"),
        )
    }

    fun confirmEmailVerification(store: AuthStore, code: String) {
        val session = ensureFreshSession(store)
            ?: throw AuthApiException("Debes iniciar sesión.", 401)
        executeJson(
            method = "POST",
            path = "/api/auth/mobile/account/verify",
            bearerToken = session.accessToken,
            body = JSONObject()
                .put("action", "confirm")
                .put("code", code),
        )
    }

    fun revokeOtherSessions(store: AuthStore) {
        val session = ensureFreshSession(store)
            ?: throw AuthApiException("Debes iniciar sesión.", 401)
        executeJson(
            method = "DELETE",
            path = "/api/auth/mobile/account/sessions",
            bearerToken = session.accessToken,
            body = JSONObject().put("others", true),
        )
    }

    fun revokeSession(store: AuthStore, sessionId: String) {
        val session = ensureFreshSession(store)
            ?: throw AuthApiException("Debes iniciar sesión.", 401)
        executeJson(
            method = "DELETE",
            path = "/api/auth/mobile/account/sessions",
            bearerToken = session.accessToken,
            body = JSONObject().put("sessionId", sessionId),
        )
    }

    fun logout(store: AuthStore) {
        val current = store.loadSession()
        try {
            if (current != null) {
                executeJson(
                    method = "POST",
                    path = "/api/auth/mobile/logout",
                    bearerToken = current.accessToken,
                    body = JSONObject().put("refreshToken", current.refreshToken),
                )
            }
        } finally {
            store.clearSession()
            store.clearPendingOAuth()
        }
    }

    fun getCloudState(store: AuthStore): JSONObject {
        val session = ensureFreshSession(store)
            ?: throw AuthApiException("Debes iniciar sesión.", 401)
        return executeJson(
            method = "GET",
            path = "/api/mobile/user/state",
            bearerToken = session.accessToken,
        ).getJSONObject("state")
    }

    fun putCloudState(store: AuthStore, payload: JSONObject): JSONObject {
        val session = ensureFreshSession(store)
            ?: throw AuthApiException("Debes iniciar sesión.", 401)
        return executeJson(
            method = "PUT",
            path = "/api/mobile/user/state",
            bearerToken = session.accessToken,
            body = JSONObject().put("payload", payload),
        ).getJSONObject("state")
    }

    private fun JSONObject.toSession(): NexoraAuthSession = NexoraAuthSession(
        user = getJSONObject("user").toUser(),
        accessToken = getString("accessToken"),
        refreshToken = getString("refreshToken"),
        accessExpiresAt = Instant.parse(getString("accessExpiresAt")).toEpochMilli(),
        refreshExpiresAt = Instant.parse(getString("refreshExpiresAt")).toEpochMilli(),
    )

    private fun JSONObject.toUser(): NexoraUser = NexoraUser(
        id = getString("id"),
        name = optString("name", "Usuario Nexora"),
        email = nullableString("email"),
        imageUrl = nullableString("image") ?: nullableString("imageUrl"),
    )

    private fun JSONObject.nullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun executeJson(
        method: String,
        path: String,
        bearerToken: String? = null,
        body: JSONObject? = null,
    ): JSONObject {
        var connection: HttpURLConnection? = null
        try {
            val endpoint = NativeBridge.apiOrigin().trimEnd('/') + path
            val active = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 20_000
                readTimeout = 60_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty(NativeBridge.clientHeaderName(), "android")
                setRequestProperty(NativeBridge.versionHeaderName(), BuildConfig.VERSION_NAME)
                setRequestProperty(
                    "X-Nexora-Device",
                    "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(100),
                )
                bearerToken?.let { setRequestProperty("Authorization", "Bearer $it") }
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
            }
            connection = active
            if (body != null) {
                active.outputStream.use { stream ->
                    stream.write(body.toString().toByteArray(Charsets.UTF_8))
                }
            }
            val status = active.responseCode
            val raw = (if (status in 200..299) active.inputStream else active.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
            if (status !in 200..299) {
                throw AuthApiException(
                    json.optString("error").ifBlank { "No se pudo completar la autenticación." },
                    status,
                )
            }
            return json
        } catch (error: AuthApiException) {
            throw error
        } catch (error: Exception) {
            throw AuthApiException(
                error.message ?: "No se pudo conectar con Nexora AI.",
                0,
            )
        } finally {
            connection?.disconnect()
        }
    }

    private fun randomUrlToken(bytes: Int): String {
        val buffer = ByteArray(bytes)
        SecureRandom().nextBytes(buffer)
        return buffer.toBase64Url()
    }

    private fun ByteArray.toBase64Url(): String = Base64.encodeToString(
        this,
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
    )

    private const val MOBILE_CALLBACK = "nexoraai://auth/callback"
    private const val ACCESS_REFRESH_MARGIN_MS = 2 * 60 * 1_000L
}
