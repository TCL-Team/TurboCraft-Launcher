/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery and contributors
 * Licensed under the GNU GPL v3 or later.
 */

package com.movtery.zalithlauncher.chat

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.gson.Gson
import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores each launcher's chat session encrypted with an Android Keystore AES-GCM key. */
internal class ChatSessionStore(context: Context, accountId: String?) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val sessionKey = accountId
        ?.takeIf { it.isNotBlank() }
        ?.let { "encrypted_session_v2_${it.replace(SESSION_KEY_UNSAFE_CHARS, "_")}" }

    fun load(): ChatAuthSession? {
        val scopedKey = sessionKey ?: return null
        preferences.getString(scopedKey, null)?.let { encoded ->
            return decrypt(encoded, scopedKey)
        }

        // Carry forward sessions created by the previous app version. The old session is assigned
        // to the launcher account selected on the first chat open after this update.
        val legacy = preferences.getString(LEGACY_SESSION_KEY, null) ?: return null
        val session = decrypt(legacy, LEGACY_SESSION_KEY) ?: return null
        save(session)
        preferences.edit().remove(LEGACY_SESSION_KEY).apply()
        return session
    }

    fun save(session: ChatAuthSession) {
        val key = sessionKey ?: return
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(Gson().toJson(session).toByteArray(Charsets.UTF_8))
        val payload = ByteBuffer.allocate(Int.SIZE_BYTES + cipher.iv.size + encrypted.size)
            .putInt(cipher.iv.size)
            .put(cipher.iv)
            .put(encrypted)
            .array()
        preferences.edit()
            .putString(key, Base64.encodeToString(payload, Base64.NO_WRAP))
            .apply()
    }

    fun clear() {
        sessionKey?.let { preferences.edit().remove(it).apply() }
    }

    private fun decrypt(encoded: String, key: String): ChatAuthSession? = try {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val buffer = ByteBuffer.wrap(bytes)
        val ivSize = buffer.int
        require(ivSize in 12..16 && ivSize <= buffer.remaining())
        val iv = ByteArray(ivSize).also { buffer.get(it) }
        val encrypted = ByteArray(buffer.remaining()).also { buffer.get(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        Gson().fromJson<ChatAuthSession>(
            cipher.doFinal(encrypted).toString(Charsets.UTF_8),
            ChatAuthSession::class.java
        ) ?: throw IllegalStateException("Invalid saved chat session")
    } catch (_: Exception) {
        preferences.edit().remove(key).apply()
        null
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = java.security.KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            generateKey()
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "supabase_chat_session"
        const val LEGACY_SESSION_KEY = "encrypted_session_v1"
        const val KEY_ALIAS = "zalith_chat_session_aes"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        val SESSION_KEY_UNSAFE_CHARS = Regex("[^A-Za-z0-9_-]")
    }
}
