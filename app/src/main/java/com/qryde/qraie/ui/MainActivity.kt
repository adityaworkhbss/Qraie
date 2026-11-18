package com.qryde.qraie.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.qryde.qraie.BuildConfig
import com.qryde.qraie.bridge.AndroidBridge
import com.qryde.qraie.prefs.Prefs
import com.qryde.qraie.utils.WebViewHolder
import com.qryde.qraie.utils.WebViewHolder.webView

class MainActivity : ComponentActivity() {

    private val bridge = AndroidBridge()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Permissions result — WebView is already showing, nothing to do here
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        showWebView()
        askPermissions()

        Log.d(
            "FIREBASE",
            FirebaseApp.getApps(this).toString()
        )

        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->

                Log.d(
                    "FCM_TOKEN",
                    token
                )

                Prefs.fcmToken = token
            }
            .addOnFailureListener {

                Log.e(
                    "FCM_TOKEN",
                    "FAILED",
                    it
                )
            }
    }

    private fun showWebView() {

        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        val webView = WebView(this).apply {

            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true

            settings.allowFileAccess = true
            settings.allowContentAccess = true

            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true

            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.setSupportMultipleWindows(true)

            settings.databaseEnabled = true

            settings.loadsImagesAutomatically = true

            settings.mixedContentMode =
                WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

            CookieManager.getInstance()
                .setAcceptThirdPartyCookies(
                    this,
                    true
                )


            webViewClient = object : WebViewClient() {

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {

                    return false
                }

                override fun onPageFinished(
                    view: WebView?,
                    url: String?
                ) {

                    super.onPageFinished(view, url)

                    val info = buildDeviceInfo()

                    view?.evaluateJavascript(
                        """
                        (function() {
                            var nativeInfo = $info;

                            function pushDeviceInfo() {
                                if (window.QraieNative && typeof window.QraieNative.setDeviceInfo === 'function') {
                                    window.QraieNative.setDeviceInfo(nativeInfo);
                                } else if (typeof window.setNativeDeviceInfo === 'function') {
                                    window.setNativeDeviceInfo(nativeInfo);
                                } else {
                                    window.dispatchEvent(
                                        new CustomEvent('nativeDeviceInfo', { detail: nativeInfo })
                                    );
                                }
                            }

                            function isLoggedIn() {
                                try {
                                    var raw = localStorage.getItem('user');
                                    if (!raw) return false;
                                    var parsed = JSON.parse(raw);
                                    return !!(parsed && parsed.data && parsed.data.member_id);
                                } catch (e) {
                                    return false;
                                }
                            }

                            if (isLoggedIn()) {

                                pushDeviceInfo();

                            } else {

                                var _orig = Storage.prototype.setItem;

                                Storage.prototype.setItem = function(key, value) {
                                    _orig.call(this, key, value);
                                    if (key === 'user') {
                                        try {
                                            var parsed = JSON.parse(value);
                                            if (parsed && parsed.data && parsed.data.member_id) {
                                                Storage.prototype.setItem = _orig;
                                                pushDeviceInfo();
                                            }
                                        } catch (e) {}
                                    }
                                };
                            }
                        })();
                        """.trimIndent(),
                        null
                    )
                }
            }

            webChromeClient = object : WebChromeClient() {

                override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                    Log.i("WebConsole", "${message.message()} [${message.sourceId()}:${message.lineNumber()}]")
                    return true
                }

                override fun onGeolocationPermissionsShowPrompt(
                    origin: String?,
                    callback: GeolocationPermissions.Callback?
                ) {

                    callback?.invoke(
                        origin,
                        true,
                        false
                    )
                }
            }

            addJavascriptInterface(bridge, "QraieNative")

            loadUrl(BuildConfig.WEB_URL)
        }

        WebViewHolder.webView = webView
        setContentView(webView)
    }

    private fun askPermissions() {

        val permissionsToRequest = mutableListOf<String>()

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            permissionsToRequest.add(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            permissionsToRequest.add(
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            permissionsToRequest.add(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun buildDeviceInfo(): JSONObject {

        return JSONObject().apply {

            put("fcmToken", Prefs.fcmToken ?: "")

            put(
                "deviceId",
                Settings.Secure.getString(
                    contentResolver,
                    Settings.Secure.ANDROID_ID
                )
            )

            put("platform", "Android")

            put("deviceModel", "${Build.MANUFACTURER} ${Build.MODEL}")

            put("osVersion", Build.VERSION.RELEASE)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()

        WebViewHolder.webView?.destroy()
        WebViewHolder.webView = null
    }
}
