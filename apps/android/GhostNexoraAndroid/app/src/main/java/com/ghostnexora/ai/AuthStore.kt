package com.ghostnexora.ai

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AuthStore(context: Context) {
    private val preferences = context.getSharedPreferences(
        "nexora_auth_secure",
        Context.MODE_PRIVATE,
    )

    fun loadSession(): NexoraAuthSession? = decrypt(KEY_SESSION)?.let { raw ->
        runCatching { JSONObject(raw).toAuthSession() }.getOrNull()
    }

    fun saveSession(session: NexoraAuthSession) {
        preferences.edit()
            .putString(KEY_SESSION, encrypt(session.toJson().toString()))
            .commit()
    }

    fun clearSession() {
        preferences.edit().remove(KEY_SESSION).commit()
    }

    fun loadPendingOAuth(): PendingOAuth? = decrypt(KEY_PENDING_OAUTH)?.let { raw ->
        runCatching { JSONObject(raw).toPendingOAuth() }.getOrNull()
    }

    fun savePendingOAuth(pending: PendingOAuth) {
        preferences.edit()
            .putString(KEY_PENDING_OAUTH, encrypt(pending.toJson().toString()))
            .commit()
    }

    fun clearPendingOAuth() {
        preferences.edit().remove(KEY_PENDING_OAUTH).commit()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = Base64.encodeToString(cipher.iv, BASE64_FLAGS)
        val encrypted = Base64.encodeToString(
            cipher.doFinal(value.toByteArray(Charsets.UTF_8)),
            BASE64_FLAGS,
        )
        return "$iv.$encrypted"
    }

    private fun decrypt(value: String): String? = runCatching {
        val parts = value.split('.', limit = 2)
        require(parts.size == 2)
        val iv = Base64.decode(parts[0], BASE64_FLAGS)
        val encrypted = Base64.decode(parts[1], BASE64_FLAGS)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(128, iv),
        )
        cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }.getOrNull()

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER,
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return keyGenerator.generateKey()
    }

    private companion object {
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "nexora_mobile_auth_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SESSION = "auth_session"
        const val KEY_PENDING_OAUTH = "pending_oauth"
        const val BASE64_FLAGS = Base64.NO_WRAP or Base64.URL_SAFE
    }
}

object AuthCallbackBus {
    private val mutableCallback = MutableStateFlow<Uri?>(null)
    val callback: StateFlow<Uri?> = mutableCallback

    fun publish(uri: Uri?) {
        if (uri?.scheme == "nexoraai" && uri.host == "auth" && uri.path == "/callback") {
            mutableCallback.value = uri
        }
    }

    fun consume() {
        mutableCallback.value = null
    }
}
