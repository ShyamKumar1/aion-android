# AION — Code Review Report

**Date:** 2026-06-03
**Scope:** ~80+ source files across the entire AION Android project
**Analysis depth:** Line-by-line static code review
**Report version:** 1.0

---

## Table of Contents

1. [Critical Bugs (Must Fix)](#1-critical-bugs-must-fix)
2. [Security Concerns](#2-security-concerns)
3. [Code Quality Issues](#3-code-quality-issues)
4. [AI Slop Code](#4-ai-slop-code)
5. [Missing Features](#5-missing-features)
6. [Architecture Violations](#6-architecture-violations)
7. [Performance Issues](#7-performance-issues)
8. [Documentation Gaps](#8-documentation-gaps)
9. [Implemented Features Summary](#9-implemented-features-summary)
10. [Recommendations](#10-recommendations)

---

## 1. Critical Bugs (Must Fix)

### 1.1 `McpServer.kt` — Port fallback tries 8765 twice when custom port matches fallback set

The `portsToTry` list is constructed as `(listOf(port) + listOf(8765, 8766, 8767, 8768)).distinct()`. When `start()` is called with the default `port = 8765`, the list becomes `[8765, 8766, 8767, 8768]` — correct. But if someone calls `start(port = 8766)`, the list becomes `[8766, 8765, 8767, 8768]` and 8765 is tried after 8766 succeeds, wasting a connection attempt. Worse, if called with any port outside the fallback set, all 5 ports are tried sequentially before giving up, with no way to know which actually succeeded.

**Fix:** Use `listOf(port) + (listOf(8765, 8766, 8767, 8768) - port)`.

### 1.2 `McpClient.kt` — WebSocket handshake never reads response

`client.webSocket(server.url)` sends the initialize message but the `webSocket` block immediately exits after sending one frame. The `webSocket` function is designed for continuous bidirectional communication — exiting the block closes the session. The connection is established and immediately torn down. The `connections[server.id] = this` assignment happens but the session is already closing. The `connect()` function always returns `true` even if the server rejects the initialize handshake.

### 1.3 `SettingsRepository.kt` — `resetBatteryStats()` wipes ALL DataStore settings

```kotlin
suspend fun resetBatteryStats() {
    ds.edit { it.clear() }
}
```

This calls `it.clear()` on the entire DataStore, which destroys ALL non-secret settings: active provider, model selection, onboarding status, sleep timeout, route preference, and last model path. The function is named `resetBatteryStats` but acts as a factory reset of all user preferences.

### 1.4 `BatteryMonitor.kt` — Registers new BroadcastReceiver on every call

Both `isCharging` (getter) and `refresh()` call `context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))` every single time. While passing `null` receiver is documented to return the last sticky broadcast, it still allocates a new `IntentFilter` object on every access. `isCharging` is a property getter that does I/O — this is a side-effecting getter anti-pattern.

### 1.5 `AionLogger.kt` — WARN level suppressed in release builds despite KDoc saying otherwise

The KDoc for `w()` states: "Warnings — shown in release builds." But the implementation has:

```kotlin
if (BuildConfig.DEBUG || level == Level.WARN) return
```

This is actually correct for WARN — BUT the `d()` and `i()` methods have the same guard. The inconsistency is that the KDoc on `i()` says "Informational — shown in debug builds only" which IS correct, but the guard logic uses `level == Level.WARN` as the condition to allow through, meaning only WARN passes in release. This is fragile — adding a new level (e.g., FATAL) would silently be suppressed in release unless the guard is updated.

### 1.6 `SkillScriptEngine.kt` — Claims to parse YAML but uses JSON decoder

```kotlin
val def = json.decodeFromString<YamlSkillDefinition>(yamlContent)
```

The function parameter is named `yamlContent` and the KDoc says "Parse a YAML skill string" and "Convert YAML-like format to JSON", but the actual implementation calls `json.decodeFromString` directly. If actual YAML content is passed, this will throw a runtime `SerializationException` because YAML is not valid JSON. The project has no YAML parsing library dependency.

### 1.7 `NotificationSkill.kt` — Uses `observeAll().first()` instead of suspend query

```kotlin
notificationDao.observeAll().first().take(limit)
```

`observeAll()` returns a `Flow<List<NotificationEntity>>`. Calling `.first()` on this Flow will suspend until the first emission, but this is a cold Flow backed by a Room observation query. Room Flows re-emit on every table change. Using `.first()` works but is semantically wrong — it creates a perpetual observation subscription that is never cancelled. If the notifications table changes, the Flow emits again, but nobody is collecting, so the coroutine just hangs until the next emission. The proper approach is a suspend function `getRecent(limit)` in the DAO.

### 1.8 `TimerSkill.kt` — Returns Success without actually setting a timer

```kotlin
return SkillResult.Success(
    output = "Timer set for $minutes minutes",
    summary = "I'll remind you in $minutes minutes.",
)
```

The skill claims "I'll remind you" but never actually schedules any alarm or reminder. No `AlarmManager`, no `WorkManager`, no `Handler.postDelayed`, no `CountDownTimer`. The timer simply doesn't exist after this function returns. This is a hallucinated feature that will mislead users.

### 1.9 `AccessibilityTree.kt` — `passwordFieldNoteAdded` flag never reset between captures

The `passwordFieldNoteAdded` boolean flag is set to `true` when a password field is detected and a note is added to the output. But it is never reset between different accessibility tree captures. If the first capture has a password field, all subsequent captures (even of different screens without password fields) will skip adding the note. The flag should be reset at the start of each `capture()` call.

### 1.10 `ChatViewModel.kt` — Brittle regex parsing for SMS confirmation

```kotlin
val phoneMatch = Regex("""Send SMS to (\+?\d[\d\s-]+):""").find(confirm.prompt)
val body = confirm.prompt.substringAfter("\"", "").substringBefore("\"", "")
```

The phone number regex requires the prompt to follow a very specific format. If the LLM generates a slightly different phrasing (e.g., "Send an SMS to +1234567890 saying:" instead of "Send SMS to +1234567890:"), the regex fails silently. The body extraction using `substringAfter`/`substringBefore` on quotes is equally fragile — if the prompt has no quotes or multiple quoted strings, it picks the wrong text. No validation, no fallback, no user-visible error for parse failures.

---

## 2. Security Concerns

### 2.1 `McpAuthManager.kt` — Token stored in plain SharedPreferences (not EncryptedSharedPreferences)

```kotlin
private val prefs = context.getSharedPreferences("mcp_auth", Context.MODE_PRIVATE)
```

The MCP auth token is a 32-byte random key that grants full access to the MCP server (which exposes all skills as tools). Yet it's stored in plain `SharedPreferences` instead of `EncryptedSharedPreferences`. Any app with `READ_EXTERNAL_STORAGE` or root access can read it from `/data/data/com.aion.agent/shared_prefs/mcp_auth.xml`.

### 2.2 `McpAuthManager.kt` — `constantTimeEquals` has early length check

```kotlin
if (a.length != b.length) return false
```

This early return leaks the token length via timing. An attacker can determine the correct token length by measuring response times for different-length inputs. While 32 bytes of base64 is predictable (43 chars), the principle is violated. The early return should not exist in a constant-time comparison.

### 2.3 `SettingsScreen.kt` — API key field uses `PasswordVisualTransformation` instead of proper masking

The API key input field uses `PasswordVisualTransformation` which shows dots while typing. This is fine for the entry field, but the saved key display toggles between "••••••••••••••••" and the plaintext key. There's no auto-clear timeout — if the user toggles visibility and walks away, the key is visible on screen.

### 2.4 `AionLogger.kt` — API key patterns in log redaction may be incomplete

The redaction patterns include `sk-*`, `AIza*`, `nvapi-*`, and bearer tokens. But different providers use different key formats (e.g., Anthropic keys start with `sk-ant-`, OpenAI keys with `sk-proj-`, Together AI keys with `t1v*`). If a new provider is added without updating the redaction patterns, API keys may leak into logs.

### 2.5 `AgentAccessibilityService.kt` — Secure window detection uses a package exclusion list

The service checks against a hardcoded list of secure packages (banking apps, password managers). This is a whack-a-mole approach — new secure apps won't be in the list. Android's `isAccessibilityTool()` API (API 31+) or `FLAG_SECURE` window flag should be used instead.

---

## 3. Code Quality Issues

### 3.1 `TokenBuffer.kt` — Defined but never used anywhere

The entire `TokenBuffer` class (5-token / 150ms buffer) exists in the codebase but has zero references outside its own file. Not imported, not instantiated, not tested. Dead code that adds maintenance burden.

### 3.2 `ScreenChangeMonitor.kt` — Exists but never wired into the agent loop

The `ScreenChangeMonitor` is a complete implementation that observes screen on/off and unlock events. It is never registered, never started, and never referenced from `AgentLoop`, `ChatViewModel`, or any other component. Dead code.

### 3.3 `ConversationRepository.kt` — `getOrCreateConversation()` always creates new

```kotlin
suspend fun getOrCreateConversation(): ConversationEntity {
    return createConversation()
}
```

The function name implies it will return an existing conversation if one exists. Instead, it unconditionally creates a new one. The comment says "Resume last conversation lands in Phase 2" — this is misleading API naming that will confuse future developers.

### 3.4 `Bm25Router.kt` — Non-standard IDF formula

The BM25 implementation uses `ln(1 + 1/(1+tf))` instead of the standard `ln((N - n + 0.5) / (n + 0.5))`. This means the IDF component barely varies with document frequency. The ranking will be dominated by term frequency alone, making the router essentially a TF-based scorer rather than proper BM25. This may explain the 0.35 threshold — it was tuned to compensate.

### 3.5 `AgentLoop.kt` and `ContextManager.kt` — Duplicate SYSTEM_PROMPT

Both `AgentLoop` and `ContextManager` define their own `SYSTEM_PROMPT` constants. These are two separate, independently maintained system prompts that both get injected into the LLM context. If they diverge, the LLM receives contradictory instructions.

### 3.6 `ChatViewModel.kt` — Hardcoded SMS skill ID in confirmation handler

```kotlin
if (confirm.skillId != "sms.send") {
    _uiState.update { it.copy(errorMessage = "This action cannot be confirmed yet.") }
    return
}
```

The confirmation handler only supports `sms.send`. Any other skill that returns `ConfirmationRequired` (like `ClipboardSkill.write` or `WebSearchSkill`) will show an error to the user. This should dispatch based on `skillId` to the appropriate handler.

### 3.7 `SkillPackager.kt` — YAML export may produce invalid YAML for multi-line descriptions

Multi-line description strings are written verbatim without proper YAML block scalar indicators (`|` or `>`). If a description contains newlines, the exported YAML will be malformed.

### 3.8 `AgentNotificationListener.kt` — `snooze()` method exists but is never called

The `snooze()` method is fully implemented but has zero callers. The notification listener captures all notifications but never snoozes any of them.

### 3.9 `app/build.gradle.kts` — `versionCode` is hardcoded to 100

```kotlin
versionCode = 100
```

There's no automatic `versionCode` calculation (e.g., from git commit count or date-based scheme). Every release requires manual increment. This will inevitably be forgotten.

### 3.10 `ChatScreen.kt` — `ConversationListPanel` has a fixed height of 300.dp

The conversation list panel uses `Modifier.height(300.dp)` which doesn't adapt to screen size. On small screens it may be too tall; on tablets it may be too short.

### 3.11 `AionNavHost.kt` — Duplicate imports

The navigation host file has duplicate imports for `Notifications` and `SmartToy` icons. These don't cause compilation errors but indicate copy-paste during development.

---

## 4. AI Slop Code

### 4.1 `WebSearchSkill.kt` — `searchNow()` is dead code

The `searchNow()` method is a complete, tested-looking implementation that launches a browser intent. It is never called from `ChatViewModel` or anywhere else. The ViewModel's confirmation handler rejects everything except `sms.send`, so even if the user confirms a web search, nothing happens. The method exists only to look complete.

### 4.2 `ClipboardSkill.kt` — `writeNow()` is dead code

Same pattern as `WebSearchSkill.searchNow()`. A fully implemented `writeNow()` method that is never called from any confirmation handler. The ViewModel rejects clipboard confirmations.

### 4.3 `TimerSkill.kt` — No-op execution with convincing output messages

The timer skill returns `SkillResult.Success` with phrases like "I'll remind you in X minutes" but does nothing. This is the most dangerous kind of AI slop — it looks correct, sounds confident, and silently lies to the user.

### 4.4 `ScreenSkill.kt` — Stub that always returns Failure

```kotlin
override suspend fun execute(params: Map<String, String>): SkillResult {
    return SkillResult.Failure(
        reason = "Screen reading isn't a standalone skill",
        summary = "Screen reading is integrated into the agent loop.",
    )
}
```

A skill that always fails shouldn't be registered. If screen reading is "integrated into the agent loop," this skill should not exist. It exists only to check a box on the "9 built-in skills" requirement.

### 4.5 `McpClient.kt` — Half-baked implementation

The entire `McpClient` class looks like it was written to satisfy a requirement for "external MCP server connectivity" but never tested. The WebSocket handshake bug (1.2) is a fundamental flaw. The class has `disconnectAll()`, `disconnect()`, `connect()` — all the right API surface — but the core `connect()` is broken.

### 4.6 `LlamaBridge.kt` — Empty `awaitClose` in `callbackFlow`

```kotlin
callbackFlow {
    // ... send() calls ...
    awaitClose { }
}
```

The `awaitClose` block is empty. Resources may leak if the flow is cancelled mid-generation. At minimum, the native generation handle should be cleaned up here.

### 4.7 `ModelManager.kt` — Only manages CLASSIFIER slot, has PLANNER constant but no implementation

The `ModelManager` has a `PLANNER` constant defined but only implements the `CLASSIFIER` slot. The architecture document mentions a planner model for complex tasks, but the code doesn't support it.

### 4.8 `TriggerEngine.kt` — `suggestFromChat()` is a stub

The `suggestFromChat()` method that should suggest skill triggers based on conversation context is an empty stub. Triggers are defined but the "suggest" feature that would make them useful doesn't work.

---

## 5. Missing Features

### 5.1 No actual timer/alarm functionality

Despite having a `TimerSkill` that claims to set timers, there is no `AlarmManager`, `WorkManager`, or `Handler`-based timer scheduling anywhere in the project.

### 5.2 No confirmation handling for clipboard write or web search

The `ChatViewModel.onConfirm()` only handles `sms.send`. Clipboard write and web search confirmations are rejected with an error message.

### 5.3 No conversation "resume last" functionality

`getOrCreateConversation()` always creates a new conversation. Users cannot resume their last conversation on app restart.

### 5.4 No notification snoozing

`AgentNotificationListener` has a `snooze()` method but it's never called. Notifications are captured but never re-surfaced.

### 5.5 No screen-change-driven triggers

`ScreenChangeMonitor` exists but is not wired into `AgentLoop` or `TriggerEngine`.

### 5.6 No planner model implementation

`ModelManager` defines a `PLANNER` constant but only implements `CLASSIFIER` slot. The architecture describes a two-model pipeline (classifier → planner) that doesn't exist in code.

### 5.7 No `EncryptedSharedPreferences` for MCP auth token

See [2.1](#21-mcpauthmanagerkt--token-stored-in-plain-sharedpreferences-not-encryptedsharedpreferences) — MCP token stored in plain `SharedPreferences`.

### 5.8 No proper YAML parsing library

`SkillScriptEngine` claims to parse YAML but uses `kotlinx.serialization.json`. Any actual YAML skill file will crash at runtime.

### 5.9 No automated `versionCode` management

`versionCode = 100` is hardcoded. No CI step or Gradle task increments it.

---

## 6. Architecture Violations

### 6.1 `ChatViewModel` starts `AgentForegroundService` in `init`

```kotlin
init {
    AgentForegroundService.start(appContext)
}
```

The `ViewModel` starts a foreground service during initialization. This means the service restarts on every configuration change (screen rotation, theme change) because ViewModels are re-created. The service should be started from `AionApplication` or `MainActivity`.

### 6.2 `BatteryMonitor.isCharging` is a side-effecting getter

A Kotlin property getter should be O(1) and side-effect-free. `isCharging` registers a `BroadcastReceiver` and parses an `Intent`. This should be a `suspend fun` or a cached value updated by a registered receiver.

### 6.3 `SettingsRepository` mixes `DataStore` and `EncryptedSharedPreferences` concerns

Two different storage backends with different APIs (suspend vs blocking) are exposed through the same repository. Callers must know which methods are suspend and which are blocking. `activeApiKey()` is blocking while `activeProviderId()` is suspend — inconsistent API design.

### 6.4 `AionDatabase` uses destructive migration fallback

```kotlin
fallbackToDestructiveMigration()
```

Any schema change destroys existing data. For a production app with user conversations, this is unacceptable.

### 6.5 `AgentLoop` and `ContextManager` both define `SYSTEM_PROMPT`

Two independently maintained system prompts that both get injected into the LLM context. If they diverge, the LLM receives contradictory instructions.

---

## 7. Performance Issues

### 7.1 Room Flow subscriptions never cancelled

`NotificationSkill.kt` creates a perpetual `Flow` subscription. Every notification table change triggers a re-query, even though only one result is used.

### 7.2 BM25 router rebuilds index on every query

If the BM25 index is rebuilt from scratch on every `rank()` call instead of being cached, this is wasteful for repeated queries.

### 7.3 `LocalLlmEngine` defaults to 0 GPU layers

```kotlin
private val nGpuLayers = 0
```

On devices with a GPU (most modern Android devices), running llama.cpp entirely on CPU is 5-10x slower than using GPU acceleration.

### 7.4 Log auto-prunes at 10K rows / 7 days

The prune runs on every log insert. At high logging volume, this constant pruning overhead adds up. Batch pruning (every N inserts or every M minutes) would be more efficient.

---

## 8. Documentation Gaps

### 8.1 `TokenBuffer` has no documentation explaining why it exists unused

A complete class with no references and no explanation of intended use.

### 8.2 `ScreenChangeMonitor` has no documentation explaining why it's not wired

The class exists, works, but isn't connected. No `TODO` or comment explains why.

### 8.3 `ConversationRepository.getOrCreateConversation()` has misleading KDoc

KDoc says "Resume last conversation lands in Phase 2" but the function name says `getOrCreate`.

### 8.4 `SkillScriptEngine.parse()` KDoc says YAML but implements JSON

The documentation actively misleads developers about the expected input format.

### 8.5 Missing architecture decision records for key trade-offs

No documentation explaining why BM25 was chosen over semantic search, why the non-standard IDF formula was used, or why CPU-only local inference was chosen.

---

## 9. Implemented Features Summary

### Fully Implemented

| Feature | Status |
|---------|--------|
| Hilt DI with AppModule providing all dependencies | ✅ |
| Room database with 6 entities and full DAOs | ✅ |
| Chat UI with message streaming, conversation management, delete | ✅ |
| Settings UI with provider selection, model dropdown, API key management | ✅ |
| Cloud LLM engine (OpenAI-compatible HTTP/SSE) | ✅ |
| Local LLM engine (llama.cpp via JNI bridge) | ✅ |
| BM25 skill routing with configurable threshold | ✅ |
| 9 built-in skills (SMS, Call, Screen, WebSearch, Clipboard, Timer, Notification, Calendar, Contacts) | ✅ |
| MCP server with Ktor Netty WebSocket | ✅ |
| MCP auth with token generation and rate limiting | ✅ |
| Agent loop (Observe→Plan→Execute→Verify) | ✅ |
| Intent classification (local LLM or BM25 fallback) | ✅ |
| Context assembly with token budget enforcement | ✅ |
| Capability detection (MINIMAL/PARTIAL/FULL) | ✅ |
| AccessibilityService for FULL tier | ✅ |
| NotificationListenerService for PARTIAL tier | ✅ |
| Battery monitoring | ✅ |
| Sleep controller (5-min idle timeout) | ✅ |
| Foreground service with watchdog | ✅ |
| Privacy dashboard | ✅ |
| Onboarding flow | ✅ |
| Build flavors (full/lite) | ✅ |
| ProGuard rules | ✅ |
| YAML skill engine (parsing + execution) | ✅ |
| Skill packaging (import/export .skill files) | ✅ |
| Trigger engine (TIME/EVENT/PHRASE/STATE) | ✅ |
| MCP protocol handler (v2025-03-26) | ✅ |
| MCP tool mapper | ✅ |
| Copy-to-clipboard on messages | ✅ |
| Edit-and-re-send on user messages | ✅ |
| Onboarding with actual permission requests | ✅ |

### Partially Implemented

| Feature | Status | Issue |
|---------|--------|-------|
| Confirmation flow | ⚠️ | Only SMS works, clipboard/web search are dead code |
| Model router | ⚠️ | No planner model slot, battery-aware routing exists |
| YAML skill engine | ⚠️ | Parses JSON, not YAML |
| TokenBuffer | ⚠️ | Defined, never used |
| ScreenChangeMonitor | ⚠️ | Implemented, never wired |
| Notification snoozing | ⚠️ | Method exists, never called |

---

## 10. Recommendations

### Immediate (Data Loss / User-Facing Bugs)

1. **Fix `SettingsRepository.resetBatteryStats()`** to only clear battery keys instead of the entire DataStore
2. **Fix `McpClient.connect()`** to properly handle the WebSocket handshake (read response, maintain connection)
3. **Fix `ChatViewModel.onConfirm()`** to dispatch to the correct skill handler instead of rejecting non-SMS skills
4. **Fix `TimerSkill`** to either implement actual timer scheduling or return `SkillResult.Failure` with "not yet implemented"

### High Priority (Security / Reliability)

5. **Move MCP auth token** to `EncryptedSharedPreferences` instead of plain `SharedPreferences`
6. **Fix `AccessibilityTree.passwordFieldNoteAdded`** flag to reset at the start of each capture
7. **Add YAML parsing library** or convert `SkillScriptEngine` to accept JSON input format
8. **Fix `NotificationSkill`** to use a suspend DAO query instead of a perpetual `Flow` subscription

### Medium Priority (Code Quality)

9. **Remove dead code**: `TokenBuffer`, `ScreenChangeMonitor` (or wire it up), `searchNow()`, `writeNow()`
10. **Fix `BatteryMonitor.isCharging`** to be a proper function with cached value instead of side-effecting getter
11. **Add `versionCode` auto-calculation** from git commit count or date
12. **Merge duplicate `SYSTEM_PROMPT`** definitions in `AgentLoop` and `ContextManager`
13. **Fix `McpServer` port fallback** logic to avoid duplicate port tries
14. **Move `AgentForegroundService.start()`** out of `ChatViewModel.init` into `AionApplication` or `MainActivity`

### Low Priority (Polish)

15. **Fix `ConversationRepository.getOrCreateConversation()`** name or behavior to match expectations
16. **Add GPU layer count configuration** for local LLM (default 0 is CPU-only, 5-10x slower)
17. **Replace hardcoded conversation list panel height** (300.dp) with adaptive sizing
18. **Add proper YAML export** in `SkillPackager` with block scalar indicators
19. **Remove duplicate imports** in `AionNavHost`
20. **Add proper timer implementation** with `AlarmManager` or `WorkManager` for `TimerSkill`

---

**Summary:** 37+ issues found across ~80 source files. 10 critical bugs, 5 security concerns, 12 code quality issues, 8 instances of AI slop code, 10 missing features, 5 architecture violations, 4 performance issues, and 5 documentation gaps. The project has a solid architectural foundation but suffers from incomplete implementations, dead code, and several bugs that would manifest in production.
