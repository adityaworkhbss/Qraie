package com.qryde.qraie.service

import android.Manifest
import android.content.Intent
import android.util.Log
import androidx.annotation.RequiresPermission
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.qryde.qraie.utils.WebViewHolder
import com.qryde.qraie.prefs.Prefs
import org.json.JSONObject

class MyFirebaseMessagingService : FirebaseMessagingService() {

    private val TAG = "FirebaseMessagingService"

    override fun handleIntent(intent: Intent) {

        intent.removeExtra("gcm.notification.e")

        val bundle = intent.extras
        if (bundle != null) {
            val mappings = mapOf(
                "gcm.notification.title"     to "title",
                "gcm.notification.body"      to "body",
                "gcm.notification.channelId" to "channelId"
            )
            for ((notifKey, dataKey) in mappings) {
                if (bundle.containsKey(notifKey) && !bundle.containsKey(dataKey)) {
                    intent.putExtra(dataKey, bundle.getString(notifKey))
                }
            }
        }

        super.handleIntent(intent)
    }

    override fun onNewToken(token: String) {

        super.onNewToken(token)
        Log.d(TAG, "New FCM token: $token")
        Prefs.fcmToken = token

        val webView = WebViewHolder.webView

        webView?.post {

            val tokenData = JSONObject().apply { put("token", token) }

            webView.evaluateJavascript(
                """
                (function() {
                    try {
                        var raw = localStorage.getItem('user');
                        var parsed = raw ? JSON.parse(raw) : null;
                        if (!parsed || !parsed.data || !parsed.data.member_id) return;
                    } catch (e) { return; }

                    var data = $tokenData;
                    if (window.QraieNative && typeof window.QraieNative.setFcmToken === 'function') {
                        window.QraieNative.setFcmToken(data.token);
                    } else {
                        window.dispatchEvent(
                            new CustomEvent('fcmTokenReceived', { detail: data })
                        );
                    }
                })();
                """.trimIndent(),
                null
            )
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override fun onMessageReceived(message: RemoteMessage) {

        super.onMessageReceived(message)

        val name = message.data["sender_name"] ?: ""
        val type = message.data["type"] ?: ""
        val title = when {
            type == "direct_message" -> "New message from $name"
            name.isNotEmpty() -> name
            else -> message.notification?.title ?: ""
        }
        val body = message.data["message_text"]
            ?: message.notification?.body
            ?: ""
        val channelId = message.data["channelId"] ?: ""

        Log.d(TAG, "new notification data :" + JSONObject(message.data).toString())

        Log.d(TAG, "Title = '$title'")
        Log.d(TAG, "Body = '$body'")
        Log.d(TAG, "Data = ${message.data}")
        Log.d(TAG, "Notification title = ${message.notification?.title}")
        Log.d(TAG, "Notification body = ${message.notification?.body}")

        NotificationHelper.showNotification(
            context = this,
            title = title,
            body = body,
            channelId = channelId
        )

        sendToWebView(message)
    }

    private fun sendToWebView(
        message: RemoteMessage
    ) {

        val data = JSONObject()

        message.data.forEach { (key, value) ->
            data.put(key, value)
        }

        val webView = WebViewHolder.webView

        if (webView == null) {

            Log.d(
                "PUSH_EVENT",
                "WebView not available"
            )

            return
        }

        webView.post {

            webView.evaluateJavascript(
                """
                (function() {
                    var data = $data;
                    if (window.QraieNative && typeof window.QraieNative.pushNotification === 'function') {
                        window.QraieNative.pushNotification(data);
                    } else {
                        window.dispatchEvent(
                            new CustomEvent('pushNotificationReceived', { detail: data })
                        );
                    }
                })();
                """.trimIndent(),
                null
            )
        }
    }
}
