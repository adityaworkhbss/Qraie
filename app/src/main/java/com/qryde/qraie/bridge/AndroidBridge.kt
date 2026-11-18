package com.qryde.qraie.bridge

import android.util.Log
import android.webkit.JavascriptInterface
import com.qryde.qraie.prefs.Prefs
import org.json.JSONObject

class AndroidBridge {

    companion object {
        private const val TAG = "AndroidBridge"
    }

    @JavascriptInterface
    fun saveCredentials(username: String, password: String) {
        Log.i(TAG, "saveCredentials() called | username=$username | passwordLength=${password.length}")
        try {
            Prefs.username = username
            Prefs.password = password

            val savedUsername = Prefs.username
            val savedPassword = Prefs.password
            Log.i(TAG, "saveCredentials() persisted | readBack username=$savedUsername | readBack passwordLength=${savedPassword?.length ?: 0} | match=${savedUsername == username && savedPassword == password}")
        } catch (e: Exception) {
            Log.e(TAG, "saveCredentials() FAILED | ${e::class.simpleName}: ${e.message}")
        }
    }

    @JavascriptInterface
    fun getCredentials(): String {
        Log.i(TAG, "getCredentials() called | hasCredentials=${Prefs.hasCredentials}")

        if (!Prefs.hasCredentials) return "{}"

        val credentials = JSONObject().apply {
            put("username", Prefs.username)
            put("password", Prefs.password)
        }.toString()

        Log.i(TAG, "getCredentials() returning credentials for username=${Prefs.username}")
        return credentials
    }

    @JavascriptInterface
    fun clearCredentials() {
        Log.i(TAG, "clearCredentials() called")
        Prefs.clearCredentials()
        Log.i(TAG, "clearCredentials() done")
    }

    @JavascriptInterface
    fun hasCredentials(): Boolean {
        val result = Prefs.hasCredentials
        Log.i(TAG, "hasCredentials() -> $result")
        return result
    }

    @JavascriptInterface
    fun getFcmToken(): String {
        val token = Prefs.fcmToken ?: ""
        Log.i(TAG, "getFcmToken() -> tokenPresent=${token.isNotEmpty()}")
        return token
    }
}