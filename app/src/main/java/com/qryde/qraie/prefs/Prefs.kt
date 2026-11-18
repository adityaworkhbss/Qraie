package com.qryde.qraie.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.MasterKey

object Prefs {

    // Plain prefs — non-sensitive data (FCM token)
    private const val NAME = "qraie_prefs"
    private const val KEY_FCM_TOKEN = "fcm_token"

    // Encrypted prefs — sensitive data (login credentials)
    private const val SECURE_NAME = "qraie_secure_prefs"
    private const val KEY_USERNAME = "username"
    private const val KEY_PASSWORD = "password"

    private lateinit var prefs: SharedPreferences
    private lateinit var securePrefs: SharedPreferences

    fun init(context: Context) {

        prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        securePrefs = EncryptedSharedPreferences.create(
            context,
            SECURE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // ── FCM token (plain) ──────────────────────────────────────

    var fcmToken: String?
        get() = prefs.getString(KEY_FCM_TOKEN, null)
        set(value) = prefs.edit { putString(KEY_FCM_TOKEN, value) }

    // ── Login credentials (AES-256 encrypted) ─────────────────

    var username: String?
        get() = securePrefs.getString(KEY_USERNAME, null)
        set(value) = securePrefs.edit { putString(KEY_USERNAME, value) }

    var password: String?
        get() = securePrefs.getString(KEY_PASSWORD, null)
        set(value) = securePrefs.edit { putString(KEY_PASSWORD, value) }

    val hasCredentials: Boolean
        get() = !username.isNullOrEmpty() && !password.isNullOrEmpty()

    fun clearCredentials() {
        securePrefs.edit {
            remove(KEY_USERNAME)
                .remove(KEY_PASSWORD)
        }
    }
}
