# Qraie Android App — Knowledge Transfer

## Overview

**Qraie** is a native Android WebView wrapper that hosts the QRaie web application (`qraie.ai`). The app provides native integration points that the web app cannot achieve on its own: push notifications, encrypted credential storage, geolocation, and device identity — while the full product UI lives in the web layer.

**Package ID:** `com.qryde.qraie`  
**App Name:** QRaie (live) / QRaie STG (staging)  
**Current Versions:** live `1.0.2`, stg `2.0.1`

---

## Tech Stack

| Layer | Technology | Version |
|---|---|---|
| Language | Kotlin | 2.1.0 |
| Build System | Gradle (Kotlin DSL) | AGP 8.10.1 |
| Min Android | API 24 (Android 7.0) | — |
| Target Android | API 35 (Android 15) | — |
| UI | WebView (full-screen) | AndroidX WebKit 1.12.1 |
| Push Notifications | Firebase Cloud Messaging | Firebase BOM 33.15.0 |
| Analytics | Firebase Analytics | Firebase BOM 33.15.0 |
| Encrypted Storage | EncryptedSharedPreferences | security-crypto 1.1.0-alpha06 |
| Compose (declared) | Jetpack Compose | BOM 2025.04.01 |

> Jetpack Compose is declared as a dependency but the app currently uses `setContentView(webView)` directly — Compose is not actively used in the UI.

---

## Project Structure

```
Qraie/
├── app/
│   ├── build.gradle.kts          # App-level build config, flavors, dependencies
│   ├── src/main/
│   │   ├── AndroidManifest.xml   # Permissions, activity, FCM service declarations
│   │   └── java/com/qryde/qraie/
│   │       ├── QraieApp.kt       # Application class — Prefs init, Firebase init, channel creation
│   │       ├── ui/
│   │       │   └── MainActivity.kt    # Single activity — WebView host
│   │       ├── bridge/
│   │       │   └── AndroidBridge.kt  # JS ↔ Native interface
│   │       ├── prefs/
│   │       │   └── Prefs.kt          # SharedPreferences + EncryptedSharedPreferences
│   │       ├── service/
│   │       │   ├── MyFirebaseMessagingService.kt  # FCM handler
│   │       │   └── NotificationHelper.kt          # Notification display
│   │       └── utils/
│   │           └── WebViewHolder.kt  # Global WebView reference (singleton)
├── build.gradle.kts              # Root build file, plugin declarations
├── gradle/
│   └── libs.versions.toml        # Version catalog (all dependency versions)
└── certificates/
    ├── QRydeMarketplace_Bundle_key.jks   # Keystore (for release signing)
    ├── QrydeMarketplace_key              # Key file
    └── credentials.txt                   # Keystore credentials — keep secure
```

---

## Build Flavors & Environments

Two product flavors share a single `env` dimension:

| Flavor | App Name | Version | Web URL |
|---|---|---|---|
| `stg` | QRaie STG | 2.0.1 | `https://hbss-bridgestg.qraie.ai/workplace/` |
| `live` | QRaie | 1.0.2 | `https://hbss-bridge.qraie.ai/workplace/en/hbss` |

Combined with build types (`debug` / `release`), this yields four variants:
- `stgDebug`, `stgRelease`, `liveDebug`, `liveRelease`

### APK / AAB Naming

Output files are automatically renamed at build time:
- **Release:** `qraie-{flavor}-{version}.apk` / `.aab`
- **Debug:** `qraie-{flavor}-{version}-debug.apk`

---

## Features & How They Work

### 1. Full-Screen WebView

`MainActivity.showWebView()` creates a `WebView` and calls `setContentView()` on it directly. The WebView is configured with:

- JavaScript, DOM Storage, database storage all enabled
- File and content access enabled
- Wide viewport and overview mode (responsive layout support)
- Mixed content always allowed (`MIXED_CONTENT_ALWAYS_ALLOW`)
- Third-party cookies accepted
- Multiple windows supported
- WebContents debugging enabled in debug builds (use Chrome DevTools → `chrome://inspect`)

The WebView is stored in `WebViewHolder.webView` (a global singleton) so the FCM service can reach it from a background thread.

### 2. JavaScript Bridge — `window.QraieNative`

`AndroidBridge` is registered with `addJavascriptInterface(bridge, "QraieNative")`, exposing these methods to JavaScript:

| JS call | What it does |
|---|---|
| `QraieNative.saveCredentials(username, password)` | Encrypts and persists login credentials |
| `QraieNative.getCredentials()` | Returns `{"username":"…","password":"…"}` or `{}` |
| `QraieNative.clearCredentials()` | Wipes stored credentials |
| `QraieNative.hasCredentials()` | Returns `true`/`false` |
| `QraieNative.getFcmToken()` | Returns the current FCM token string |

### 3. Device Info Injection

After every page load (`onPageFinished`), the app injects a JavaScript snippet that:
1. Checks `localStorage.user` to see if the user is already logged in (`member_id` present).
2. If logged in → immediately calls `window.QraieNative.setDeviceInfo(info)`.
3. If not logged in → monkey-patches `localStorage.setItem` to watch for the `user` key being written (i.e., login event), then fires `setDeviceInfo` once login is detected.

The injected device info object:
```json
{
  "fcmToken": "<FCM token>",
  "deviceId": "<Android ID>",
  "platform": "Android",
  "deviceModel": "<Manufacturer Model>",
  "osVersion": "<Android version>"
}
```

Fallback chain (in order):
1. `window.QraieNative.setDeviceInfo(info)` — preferred
2. `window.setNativeDeviceInfo(info)` — legacy fallback
3. `CustomEvent('nativeDeviceInfo', { detail: info })` — event-based fallback

### 4. Push Notifications (FCM)

**`MyFirebaseMessagingService`** handles two FCM events:

**`onNewToken(token)`** — called when the FCM token rotates:
- Saves the new token to `Prefs.fcmToken`
- If the WebView is alive and the user is logged in, notifies via `window.QraieNative.setFcmToken(token)` or `CustomEvent('fcmTokenReceived')`

**`onMessageReceived(message)`** — called when a push arrives while the app is in foreground:
- Extracts `sender_name`, `type`, `message_text` from `message.data`
- Builds a human-readable title (e.g., "New message from Alice" for `direct_message` type)
- Calls `NotificationHelper.showNotification()` to display a system notification
- Forwards the full data payload to the WebView via `CustomEvent('pushNotificationReceived')`

**`handleIntent()`** — overridden to map Firebase's `gcm.notification.*` bundle keys to flat `title`/`body`/`channelId` extras before the base class processes them (fixes notification handling when the app is in background/killed).

**`NotificationHelper`** — builds and shows the notification:
- Channel ID: `qraie_notifications`, HIGH importance
- Tapping the notification brings `MainActivity` to the front with the `channelId` in the intent extras
- Each notification gets a unique ID (`System.currentTimeMillis().toInt()`)

### 5. Encrypted Credential Storage — `Prefs`

Two SharedPreferences stores initialized in `QraieApp.onCreate()`:

| Store | Encryption | Used for |
|---|---|---|
| `qraie_prefs` (plain) | None | FCM token |
| `qraie_secure_prefs` (encrypted) | AES-256-GCM (values) + AES-256-SIV (keys) | username, password |

The encrypted store uses `MasterKey` with `AES256_GCM` key scheme backed by Android Keystore.

### 6. Runtime Permissions

Requested on first launch by `MainActivity.askPermissions()`:
- `ACCESS_FINE_LOCATION`
- `ACCESS_COARSE_LOCATION`
- `POST_NOTIFICATIONS` (Android 13+ / API 33+ only)

Geolocation permissions for the WebView are auto-granted inside `WebChromeClient.onGeolocationPermissionsShowPrompt()` once the user has granted the system-level location permission.

---

## Native ↔ Web Communication Summary

```
Web → Native:  window.QraieNative.{method}(args)
                via @JavascriptInterface on AndroidBridge

Native → Web:  webView.evaluateJavascript("(function(){ ... })()", null)
                tries window.QraieNative.{method}() first,
                falls back to window.dispatchEvent(new CustomEvent(...))
```

---

## Signing & Release

The release keystore lives in `certificates/`:
- **Keystore:** `QRydeMarketplace_Bundle_key.jks`
- **Credentials:** `certificates/credentials.txt`

Release builds have ProGuard minification and resource shrinking enabled (`isMinifyEnabled = true`, `isShrinkResources = true`).

---

## Running & Debugging

- **Select variant** in Android Studio: Build > Select Build Variant → choose e.g. `liveDebug`
- **WebView debugging:** With a debug build running on a device, open Chrome and navigate to `chrome://inspect/#devices`
- **FCM token:** Logged on startup at tag `FCM_TOKEN`
- **Firebase init:** Logged at tag `FIREBASE`
- **WebView console:** Bridged to Logcat at tag `WebConsole`
- **Push events:** Logged at tag `PUSH_EVENT`
- **Bridge calls:** Logged at tag `AndroidBridge`

---

## Key Decisions & Notes

- The app is intentionally thin — all product logic lives in the web app. The native layer exists solely to provide capabilities the browser cannot: encrypted local storage, FCM tokens, system notifications, and device identity.
- `WebViewHolder` is a global mutable singleton. It is set in `MainActivity.showWebView()` and cleared in `onDestroy()`. The FCM service checks for null before posting to it.
- `handleIntent()` is overridden in `MyFirebaseMessagingService` to handle a Firebase SDK behaviour where `gcm.notification.e` being present causes the base class to drop the notification on some devices.
- The login-detection mechanism in `onPageFinished` patches `Storage.prototype.setItem` temporarily, restoring the original once the login key is detected, to avoid a permanent monkey-patch.
