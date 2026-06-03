# AION — Android AI Agent Operating System

> **Private build, in active development.**
> The README below describes the *target* v1.0 product. The current source tree
> ships Phase 1 (chat + cloud LLM + SMS) on top of the Phase 2+ architecture
> stubs. See `CHANGELOG.md` for what's actually working today.

AION is a private, on-device AI agent for Android. It can see your screen, read
your notifications, send SMS, place calls, and run automations — using any LLM
you choose (cloud or local). No host PC. No ADB. No cloud lock-in.

## Status

- [x] **Phase 1** — Chat + cloud LLM (OpenRouter, Opencode Go, NVIDIA NIM) + SMS tool
- [x] Foreground service with persistent notification
- [x] BM25 skill router with capability gating
- [x] Encrypted API key storage
- [x] Conversation persistence (Room)
- [ ] **Phase 2** — Local LLM (llama.cpp, intent classifier, sleep mode)
- [ ] **Phase 3** — NotificationListener + AccessibilityService + vector memory
- [ ] **Phase 4** — YAML skill authoring + autonomous triggers
- [ ] **Phase 5** — On-device MCP server
- [ ] **Phase 6** — Polish, onboarding, release

See `/.hermes/plans/aion-master.md` (in Hermes Vault) for the full execution plan.

## Test devices

Currently tested manually on:
- **Nothing Phone 2** (primary, custom ROM)
- **Oppo** (secondary, ColorOS — battery-killer torture test)

## Build

Requirements:
- JDK 21
- Android SDK with platform 34, build-tools 34.0.0, NDK 27.0.12077973, CMake 3.22.1
- (Or use Android Studio with the SDK manager)

```bash
# Set up the SDK paths
cp local.properties.example local.properties
# Edit local.properties to point at your SDK

# Build the debug APK
./gradlew assembleDebug

# Install on a connected device
./gradlew installDebug
# or: adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## First-run setup

1. Open AION.
2. Tap **Settings** in the bottom bar.
3. Pick a provider (OpenRouter recommended) and a model.
4. Paste your API key. Tap **Save**.
5. Back to **Chat** and send a message.

> **Note:** the API key is stored in EncryptedSharedPreferences (Android
> Keystore-backed) and is never written to disk in plaintext or logged.

## Architecture

See `docs/ARCHITECTURE.md`. High-level:

```
UI (Compose) → ChatViewModel → AgentLoop → [IntentClassifier + SkillRegistry | CloudLlmEngine]
                                                       ↓
                                          Foreground Service
                                                       ↓
                                          Room (conversations)
                                          DataStore (settings)
                                          EncryptedSharedPreferences (API keys)
```

## Privacy

- **No accounts.** AION has no sign-up.
- **No telemetry.** Nothing leaves the device unless you make a cloud LLM call.
- **No background recording.** Screenshots, notification text, and SMS content
  stay on-device. They are persisted in the local Room database for the
  conversation history you can see in the app — they are never uploaded to any
  server AION controls (because AION has no server).
- **Cloud providers** receive the content of your messages if and only if you
  send a message and have a provider configured. The disclosure is shown the
  first time you configure a provider.

Full policy in `docs/PRIVACY.md` (TBD Phase 6).

## License

All Rights Reserved. See `LICENSE`.
