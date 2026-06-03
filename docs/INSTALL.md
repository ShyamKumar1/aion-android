# AION Installation Guide

> **Status:** Phase 1. AION is in active development. These instructions cover
> the current pre-release builds and the target distribution model.

---

## Distribution Channels

| Channel | Version | Capabilities | Best for |
|---|---|---|---|
| GitHub Releases | Full | All features including AccessibilityService | Power users, testing |
| F-Droid | Full | All features including AccessibilityService | Privacy-conscious users |
| Google Play Store | Lite | PARTIAL + MINIMAL only (no AccessibilityService) | General users, trying it out |
| Build from source | Full | All features | Developers, contributors |

---

## Prerequisites

| Requirement | Minimum | Recommended |
|---|---|---|
| OS | Android 8.0 (API 26) | Android 14+ (API 34) |
| Architecture | arm64-v8a | arm64-v8a |
| RAM | 4GB | 8GB+ |
| Storage | 500MB free | 3GB free (with 3B GGUF model) |
| Play Services | Optional | Not required |

> **Note:** x86 / x86_64 Android emulators are not supported (llama.cpp requires
> arm64). 4GB RAM devices may experience aggressive app killing by the OS.

---

## Option 1: GitHub Releases (Sideload APK)

1. Go to the [GitHub Releases page](https://github.com/ShyamKumar1/aion-android/releases).
2. Download the latest `aion-full-<version>.apk` (Full build) or `aion-lite-<version>.apk` (Lite build).
3. On your Android device:
   - Open **Settings → Security**.
   - Enable **Install from unknown apps** (or **Install from this source** on Android 14+).
4. Open the downloaded APK file and tap **Install**.
5. Tap **Open** to start AION.

> **Full build** includes AccessibilityService for screen reading and UI
> automation. **Lite build** is limited to notification access and chat.

---

## Option 2: F-Droid

> **Status:** Planned for v1.0 release.

1. Add the AION repository to your F-Droid client:
   - Open F-Droid → Settings → Repositories.
   - Tap **+** and add the repository URL (published at release).
2. Search for **AION** in F-Droid.
3. Tap **Install**.

The F-Droid build is the **Full** version (all capabilities including AccessibilityService).

---

## Option 3: Google Play Store

> **Status:** Planned for v1.0 release.

1. Open the Google Play Store on your device.
2. Search for **AION**.
3. Tap **Install**.

The Play Store build is the **Lite** version:
- ✅ Chat with cloud/local LLM
- ✅ Notification management
- ✅ SMS and calling
- ✅ Calendar and contacts access
- ❌ No AccessibilityService (screen reading, UI automation)
- ❌ No MCP server

> The Lite version shares all code with the Full version — it simply swaps
> `AgentAccessibilityService` for a no-op implementation that returns
> `AgentCapability.PARTIAL` always. Upgrading to the Full version requires
> sideloading from GitHub or F-Droid.

---

## Option 4: Build from Source

### Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK | 21 | Required for Kotlin 2.0+ compilation |
| Android SDK | API 34, build-tools 34.0.0 | Install via Android Studio SDK Manager |
| NDK | 27.0.12077973 | Required for llama.cpp JNI bindings |
| CMake | 3.22.1 | Required for NDK native code |
| Gradle | 8.x (wrapper included) | Use `./gradlew`, not system Gradle |

### Step-by-Step

```bash
# 1. Clone the repository
git clone https://github.com/ShyamKumar1/aion-android.git
cd aion-android

# 2. Set up SDK paths
cp local.properties.example local.properties
# Edit local.properties to point at your Android SDK:
#   sdk.dir=/path/to/Android/sdk
#   ndk.dir=/path/to/Android/sdk/ndk/27.0.12077973

# 3. Build all variants
./gradlew assembleDebug

# 4. Install on a connected device
./gradlew installDebug

# Or install manually:
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Build Variants

```bash
# Debug build (for development)
./gradlew assembleDebug

# Release build (unsigned — configure signing in build.gradle.kts first)
./gradlew assembleRelease

# Full variant (all features, including AccessibilityService)
# Release APK location: app/build/outputs/apk/full/release/

# Lite variant (no AccessibilityService)
# Release APK location: app/build/outputs/apk/lite/release/
```

> **Note:** Build flavors (`full` / `lite`) are defined in the project plan but
> product flavor configuration in `app/build.gradle.kts` is pending Phase 6.
> Currently, `assembleDebug` produces a single debug APK.

### Android Studio

1. Open Android Studio.
2. **File → Open** → select the `aion-android` directory.
3. Wait for Gradle sync to complete.
4. Select a device from the run target dropdown.
5. Click **Run** (green triangle) or press **Ctrl+R**.

### Troubleshooting Builds

| Error | Likely Cause | Fix |
|---|---|---|
| `No matching toolchains found` | JDK 17 or older | Install JDK 21 (`brew install openjdk@21` on macOS) |
| `SDK location not found` | `local.properties` missing | Copy `local.properties.example` and set `sdk.dir` |
| `NDK not configured` | NDK not installed | Install NDK 27 in Android Studio SDK Manager |
| `llamabridge.cpp: jni.h not found` | NDK path incorrect | Verify `ndk.dir` in `local.properties` |
| `AAPT: error: resource android:attr/lStar not found` | Wrong compileSdk | Set `compileSdk = 34` in `app/build.gradle.kts` |
| `java.lang.UnsatisfiedLinkError` on device | Wrong ABI | AION targets arm64-v8a only. Not compatible with emulators or x86 devices. |

---

## First-Run Setup

### 1. Permissions

When you first launch AION, it requests permissions incrementally — only when
needed:

| Permission | When Requested |
|---|---|
| `POST_NOTIFICATIONS` | First launch (foreground service notification) |
| `BATTERY_OPTIMIZATIONS` | First launch (guide to exclude from battery killing) |
| `SEND_SMS` | First time you ask the agent to send an SMS |
| `CALL_PHONE` | First time you ask the agent to place a call |
| `READ_CONTACTS` | First time you ask the agent to find a contact |
| `READ_CALENDAR` | First time you ask the agent to read your calendar |

### 2. Backend Permissions (System Settings)

These require navigating to system settings:

| Service | How to Enable |
|---|---|
| Notification Listener | Settings → Apps → AION → Notification Access |
| Accessibility Service | Settings → Accessibility → AION |

### 3. Configure a Provider

1. Open AION.
2. Tap **Settings** in the bottom bar.
3. Tap **Provider** and select one:
   - [OpenRouter](https://openrouter.ai/keys) — recommended, 200+ models, free tiers available
   - [Opencode Go](https://opencode.ai) — MiniMax M3, DeepSeek V4 Flash, free tier available
   - [NVIDIA NIM](https://build.nvidia.com) — Llama 3.1, Nemotron, free tier available
4. Paste your API key. Tap **Save**.

> **Security:** API keys are stored in `EncryptedSharedPreferences` (Android
> Keystore-backed, AES-256-GCM). They are never written to disk in plaintext,
> logged, or transmitted anywhere except to the configured provider.

### 4. Send Your First Message

Go to the **Chat** tab and type:

> *"Send an SMS to myself saying AION is running"*

If SMS permission is not yet granted, AION will request it. After granting, the
agent will ask you to confirm the SMS before sending.

---

## Updating

### GitHub Releases / Sideload

1. Download the latest APK from the [Releases page](https://github.com/ShyamKumar1/aion-android/releases).
2. Install it over the existing version. User data is preserved.

### F-Droid

F-Droid handles updates automatically if auto-updates are enabled, or shows a
notification when a new version is available.

### Play Store

Updates are delivered through the Play Store like any other app.

### From Source

```bash
git pull
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Uninstalling

### Android Settings

Settings → Apps → AION → Uninstall

### ADB

```bash
adb uninstall com.aion.agent
adb uninstall com.aion.agent.debug  # if debug build installed
```

---

## Post-Install Checklist

- [ ] App launches and shows the chat screen
- [ ] Foreground service notification appears ("AION is running")
- [ ] Settings → Provider shows available LLM providers
- [ ] API key can be saved and cleared
- [ ] Chat message round-trips through a cloud provider
- [ ] SMS sends with user confirmation
- [ ] App persists in recent apps after pressing Home
- [ ] Notification access can be enabled (Settings → Apps → AION)

---

## Device-Specific Notes

### Samsung (One UI)

Adaptive Battery may kill the foreground service. To fix:
- Settings → Apps → AION → Battery → Unrestricted

### Xiaomi / Redmi (MIUI)

Autostart restriction disables NotificationListenerService. To fix:
- Settings → Apps → AION → Autostart → On

### Oppo / OnePlus (ColorOS)

Background FGS may be aggressively killed. To fix:
- Settings → Apps → AION → Allow background activity

### Android 14+

AccessibilityService disconnects after 30 minutes of inactivity. AION shows a
persistent notification with a re-enable button. Tap it to reconnect.

---

## Version Compatibility

| AION Version | Android Min | Build Flavor | Distribution |
|---|---|---|---|
| 0.1.0 (dev) | API 26 | Debug only | From source |
| 1.0.0 (target) | API 26 | Full + Lite | GitHub + F-Droid + Play Store |

---

## See Also

- [AION Architecture](ARCHITECTURE.md)
- [Permissions Guide](PERMISSIONS.md)
- [Privacy Policy](PRIVACY.md)
- [Device Compatibility Matrix](DEVICE_MATRIX.md)
- [Cloud Providers Reference](PROVIDERS.md)
