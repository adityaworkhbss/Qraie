# ==============================================================
# Qraie ProGuard / R8 Rules
# ==============================================================


# ── Crash reporting: keep line numbers ─────────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile


# ── Kotlin ──────────────────────────────────────────────────
-keepattributes *Annotation*, Exceptions, InnerClasses, Signature

-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

-dontwarn kotlin.**


# ── WebView JavaScript interface ────────────────────────────
# Keeps every method annotated with @JavascriptInterface so the
# web layer can still call window.QraieNative.*
-keep class com.qryde.qraie.bridge.AndroidBridge { *; }
-keepclassmembers class com.qryde.qraie.bridge.AndroidBridge {
    @android.webkit.JavascriptInterface public *;
}


# ── App internals ───────────────────────────────────────────
-keep class com.qryde.qraie.prefs.Prefs { *; }
-keep class com.qryde.qraie.utils.WebViewHolder { *; }
-keep class com.qryde.qraie.service.MyFirebaseMessagingService { *; }
-keep class com.qryde.qraie.service.NotificationHelper { *; }


# ── Firebase ────────────────────────────────────────────────
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**


# ── JSON (used for FCM payloads) ────────────────────────────
-keep class org.json.** { *; }


# ── Strip debug + verbose logs in release ───────────────────
# Removes Log.d / Log.v call sites entirely — shrinks code and
# prevents leaking internal tag names in the release binary.
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}


# ── Suppress noisy warnings from platform stubs ─────────────
-dontwarn java.lang.invoke.**
-dontwarn javax.**
-dontwarn sun.misc.**
