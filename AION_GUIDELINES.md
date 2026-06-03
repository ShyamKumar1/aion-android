# AION — Developer Guidelines
**Version:** 1.0 | Applies to all phases of development

---

## TABLE OF CONTENTS

1. [Non-Negotiables](#1-non-negotiables)
2. [Project Structure](#2-project-structure)
3. [Code Standards](#3-code-standards)
4. [Architecture Rules](#4-architecture-rules)
5. [Git Workflow](#5-git-workflow)
6. [Performance Budgets](#6-performance-budgets)
7. [Security Rules](#7-security-rules)
8. [Error Handling](#8-error-handling)
9. [Testing Requirements](#9-testing-requirements)
10. [LLM Integration Rules](#10-llm-integration-rules)
11. [Permission & Privacy Rules](#11-permission--privacy-rules)
12. [AccessibilityService Rules](#12-accessibilityservice-rules)
13. [Skill System Rules](#13-skill-system-rules)
14. [MCP Server Rules](#14-mcp-server-rules)
15. [UI & Compose Rules](#15-ui--compose-rules)
16. [Dependency Management](#16-dependency-management)
17. [Documentation Standards](#17-documentation-standards)
18. [CI/CD & Release](#18-cicd--release)
19. [Device Compatibility Contract](#19-device-compatibility-contract)
20. [What Never Ships](#20-what-never-ships)

---

## 1. NON-NEGOTIABLES

These rules are not subject to debate or "we'll fix it later." Violating them blocks a merge.

**N1. Every user-facing action that mutates state requires explicit user confirmation.**
Sending an SMS, making a call, dismissing a notification, clicking a UI element in another app — the agent must ask before doing it unless the user has explicitly set that action to auto-approve. No exceptions.

**N2. No user data leaves the device without the user being told exactly what, where, and when.**
Cloud LLM calls include message content. The user must have opted in to a specific provider and been shown a plain-language disclosure before their first cloud call. This disclosure is shown once per provider, stored in SharedPreferences, and never shown again unless the provider changes.

**N3. The app is fully functional without AccessibilityService.**
Reduced capability, yes. Broken, no. Every feature that depends on `AccessibilityService` must have a defined degradation path. If a capability is `MINIMAL`-tier only, the UI hides or disables it with a clear explanation — it does not crash or throw.

**N4. API keys are never logged, never written to a file, never included in crash reports.**
No exceptions. Not in debug builds. Not "just for testing."

**N5. The foreground service never crashes silently.**
Every uncaught exception in the agent loop is caught, logged to the local crash log, and surfaced to the user via a notification. The service restarts via the AlarmManager watchdog. "Silent failure" is not an acceptable outcome.

**N6. Memory (RAM) usage is always the developer's responsibility, never the OS's problem.**
Before loading any model, check available RAM. Before expanding context, check token count. The app must never be the reason a user's phone runs out of memory.

**N7. The main thread is for UI only.**
No LLM inference, no DB reads, no file I/O, no JNI calls, no network calls on the main thread. No exceptions.

---

## 2. PROJECT STRUCTURE

### Package Layout

```
com.aion.agent/
├── ui/                         # Jetpack Compose screens and components only
│   ├── chat/                   # ChatScreen, ChatViewModel, ChatUiState
│   ├── skills/                 # SkillMarketScreen, SkillDetailScreen
│   ├── settings/               # SettingsScreen, sub-screens
│   ├── onboarding/             # OnboardingScreen, PermissionWizard
│   ├── components/             # Shared Composables (MessageBubble, TypingIndicator, etc.)
│   └── theme/                  # Color, Typography, Shape, Theme.kt
│
├── core/                       # Agent orchestration — no Android UI dependencies
│   ├── AgentLoop.kt            # The main Observe→Plan→Execute→Verify loop
│   ├── IntentClassifier.kt     # Input → AgentIntent
│   ├── PlanningEngine.kt       # AgentIntent → ExecutionPlan
│   ├── ExecutionEngine.kt      # ExecutionPlan → ToolCall dispatch
│   ├── ModelRouter.kt          # Selects Local / Edge / Cloud per query
│   ├── ContextManager.kt       # Builds and trims LLM context windows
│   └── AgentCapability.kt      # Enum: FULL / PARTIAL / MINIMAL
│
├── llm/                        # All inference backends — pure Kotlin + JNI
│   ├── LocalLlmEngine.kt       # llama.cpp JNI wrapper
│   ├── CloudLlmEngine.kt       # OpenAI-compatible HTTP
│   ├── EdgeServerEngine.kt     # Ollama/vLLM LAN discovery
│   ├── LlamaBridge.kt          # Raw JNI declarations
│   ├── ModelManager.kt         # Download, verify, list, delete GGUF models
│   └── providers/              # ClaudeProvider, OpenAIProvider, OpenRouterProvider, GeminiProvider
│
├── system/                     # Android system service wrappers
│   ├── AgentForegroundService.kt
│   ├── AgentAccessibilityService.kt
│   ├── AgentNotificationListener.kt
│   ├── TelephonyTool.kt
│   ├── AccessibilityTree.kt    # Tree → structured JSON converter
│   └── CapabilityManager.kt    # Observes what's granted, emits AgentCapability
│
├── skills/                     # Skill engine — no Android framework dependencies
│   ├── AgentSkill.kt           # Interface
│   ├── SkillResult.kt          # Sealed class
│   ├── SkillRegistry.kt        # Registration + lookup
│   ├── Bm25Router.kt           # BM25 ranking
│   ├── SkillScriptEngine.kt    # YAML skill interpreter
│   ├── SkillPackager.kt        # .skill file import/export
│   └── builtin/                # One file per built-in skill
│       ├── SmsSkill.kt
│       ├── CallSkill.kt
│       ├── NotificationSkill.kt
│       ├── ScreenSkill.kt
│       ├── CalendarSkill.kt
│       ├── ContactsSkill.kt
│       ├── TimerSkill.kt
│       ├── ClipboardSkill.kt
│       └── WebSearchSkill.kt
│
├── memory/                     # Persistence — Room + sqlite-vec
│   ├── db/                     # Room DAOs, Entities, Database class
│   ├── VectorStore.kt          # sqlite-vec wrapper
│   ├── EmbeddingEngine.kt      # gte-small via llama.cpp
│   ├── MemoryRepository.kt     # Single access point for memory
│   └── ForgettingPolicy.kt     # LRU eviction + importance scoring
│
├── mcp/                        # MCP server + client
│   ├── McpServer.kt            # Ktor WebSocket server
│   ├── McpClient.kt            # Connect to external MCP servers
│   ├── McpProtocol.kt          # Message types, serialization
│   ├── McpToolMapper.kt        # Skill → MCP tool definition
│   └── McpAuthManager.kt       # Token generation, validation
│
├── data/                       # Repositories — single source of truth for UI
│   ├── ConversationRepository.kt
│   ├── SettingsRepository.kt   # Backed by DataStore, not SharedPreferences
│   ├── ProviderRepository.kt
│   └── TriggerRepository.kt
│
└── di/                         # Hilt modules only
    ├── AppModule.kt
    ├── LlmModule.kt
    ├── SystemModule.kt
    └── DatabaseModule.kt
```

### File Naming Rules

- Screens: `<Name>Screen.kt`
- ViewModels: `<Name>ViewModel.kt`
- UI state: `<Name>UiState.kt` (sealed class or data class)
- Hilt modules: `<Layer>Module.kt`
- No file named `Utils.kt`, `Helper.kt`, `Manager.kt` without a clear domain noun. `ModelManager.kt` is fine. `Utils.kt` is not.
- One top-level class per file. Nested classes and sealed subclasses are the only exception.

---

## 3. CODE STANDARDS

### Kotlin Style

Follow the [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html) without exception. Key specifics:

- **Indentation:** 4 spaces. No tabs.
- **Line length:** 120 characters max.
- **Trailing commas:** Required in all multi-line parameter lists, argument lists, and destructuring declarations.
- **`val` over `var`:** Default to `val`. Use `var` only when mutation is provably necessary. Every `var` needs a comment explaining why it cannot be `val`.
- **Nullability:** Prefer `?` types with `?.let` or `?: return` over `!!`. Every `!!` is a code smell that must be justified in a comment.
- **Scope functions:** `let` for null-check transforms, `apply` for object initialization, `run` for scoped computation, `also` for side effects. Do not use `with` — it's ambiguous in nested scopes.
- **Coroutines:** All suspend functions must declare which dispatcher they run on via KDoc if they have specific threading requirements. Never hardcode `Dispatchers.Main` inside a repository or engine class.

### Naming Conventions

| Element | Convention | Example |
|---|---|---|
| Classes | PascalCase | `AgentNotificationListener` |
| Functions | camelCase | `classifyIntent()` |
| Properties | camelCase | `isModelLoaded` |
| Constants | SCREAMING_SNAKE_CASE inside `companion object` | `MAX_CONTEXT_TOKENS` |
| Hilt modules | PascalCase + `Module` suffix | `LlmModule` |
| Composables | PascalCase | `MessageBubble()` |
| Flow/StateFlow properties | camelCase, no `flow` suffix | `uiState: StateFlow<ChatUiState>` |
| Room entities | PascalCase + `Entity` suffix | `MessageEntity` |
| Room DAOs | PascalCase + `Dao` suffix | `MessageDao` |

### Things That Are Banned

- `runBlocking` in production code. Tests only.
- `GlobalScope`. Use `viewModelScope`, `lifecycleScope`, or an injected `CoroutineScope`.
- `Thread.sleep()`. Use `delay()`.
- Mutable public state in ViewModels. Expose `StateFlow`, back it with `MutableStateFlow` that is `private`.
- Catching bare `Exception` or `Throwable` without rethrowing or explicitly handling every subtype.
- `Log.d` / `Log.e` with raw user content (messages, contact names, notification text) in release builds. Use the project's `AionLogger` wrapper which strips PII in release.

---

## 4. ARCHITECTURE RULES

### MVVM + MVI Contract

Every screen follows this exact data flow. No deviations.

```
User interaction
    ↓
Composable emits event → ViewModel.onEvent(event: ScreenEvent)
    ↓
ViewModel processes → calls Repository or UseCase
    ↓
Repository returns Result<T>
    ↓
ViewModel updates → _uiState.update { ... }
    ↓
StateFlow emits → Composable recomposes
```

- ViewModels must not hold references to `Context`, `Activity`, or any Android framework type except `Application` (injected via Hilt's `@ApplicationContext`).
- ViewModels must not import anything from `androidx.compose`.
- Composables must not call repository methods directly. They emit events. Period.
- Business logic lives in the domain layer (`core/`, `skills/`, `llm/`), not in ViewModels. ViewModels translate between UI events and domain calls.

### Layer Dependency Rule

```
ui/ → data/ → core/ → llm/, skills/, memory/, system/
        ↑
       mcp/
```

- Upper layers can depend on lower layers.
- Lower layers must not import from upper layers.
- `core/` must not import from `ui/`.
- `llm/` must not import from `system/`.
- `mcp/` depends on `skills/` and `system/`, but nothing in `ui/` or `data/` imports from `mcp/` directly — only through `data/` repositories.

If you find yourself needing to break this dependency chain, the answer is an interface in the lower layer and an implementation in the upper layer, injected via Hilt.

### State Management

- The single source of truth for agent capability state is `CapabilityManager`, which emits a `StateFlow<AgentCapability>`. Every feature that conditionally requires a capability collects from this flow.
- The single source of truth for model state is `ModelManager`, which emits a `StateFlow<ModelState>`.
- The single source of truth for conversation history is `ConversationRepository`.
- There is no "local cache in the ViewModel" pattern. ViewModels collect from repositories. Repositories cache. Never duplicate state.

---

## 5. GIT WORKFLOW

### Branch Naming

```
feature/<phase>-<short-description>     # feature/p1-sms-tool
fix/<short-description>                 # fix/foreground-service-crash
refactor/<short-description>            # refactor/model-router-dispatch
chore/<short-description>               # chore/update-dependencies
docs/<short-description>                # docs/mcp-server-readme
```

- `main` is always shippable. Every commit on `main` must build and pass tests.
- `develop` is the integration branch. Features merge here first.
- Direct commits to `main` are forbidden except for hotfixes, which require a `fix/` branch and immediate merge to both `main` and `develop`.

### Commit Message Format

```
<type>(<scope>): <imperative short description>

[optional body — what and why, not how]

[optional footer — closes #issue, breaking change notes]
```

Types: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `perf`, `build`

Scope: package name — `llm`, `skills`, `mcp`, `ui`, `system`, `memory`, `core`

Examples:
```
feat(skills): add BM25 router with configurable threshold
fix(system): handle AccessibilityService 30-min timeout on Android 14+
perf(llm): buffer token emissions to 5-token chunks to reduce Compose recomposition
```

Rules:
- Subject line: 72 characters max, imperative mood ("add" not "added"), no period.
- Body: wrap at 80 characters.
- Every commit that closes an issue includes `Closes #<number>` in the footer.
- No commit message of "fix bug", "WIP", "update", or "changes."

### Pull Request Rules

Every PR must include:
- A description of what changed and why.
- A test plan — what was manually tested and on which device(s).
- Screenshots or a screen recording for any UI change.
- Reference to the phase and task checklist item it completes.

A PR is not mergeable if:
- CI fails (lint, build, unit tests).
- It introduces a new `!!` operator without a justification comment.
- It adds a new dependency not listed in the approved dependency list (see Section 16).
- It touches `AgentForegroundService.kt`, `AgentAccessibilityService.kt`, or `LlamaBridge.kt` without a specific test plan for that service.

---

## 6. PERFORMANCE BUDGETS

These are hard constraints. Exceeding them is a bug, not a "nice to have."

### RAM Budget

| Condition | RAM Ceiling |
|---|---|
| App idle (no model loaded) | 80MB |
| Intent classifier loaded (3B model) | 1.9GB |
| Intent classifier + embedding model | 2.1GB |
| Planning model loaded (7B model) | 3.8GB |
| Two models simultaneously | Only on devices with ≥6GB free RAM. Check before loading. |

Before loading any model, query `ActivityManager.MemoryInfo`. If `availMem < modelSize * 1.2`, do not load the model. Show the user a specific error: "Not enough free RAM. [Model name] needs approximately [X]GB free. Please close other apps or choose a smaller model."

### Latency Targets

| Operation | Target | Hard Limit |
|---|---|---|
| Intent classification (model warm) | < 600ms | 1500ms |
| Tool dispatch (single tool) | < 300ms | 1000ms |
| SMS send | < 500ms | 2000ms |
| Notification capture → DB write | < 100ms | 300ms |
| BM25 skill routing | < 15ms | 50ms |
| Screen tree JSON conversion | < 200ms | 500ms |
| Token emission to UI (first token) | < 800ms | 2000ms |
| App cold start to chat ready | < 3 seconds | 5 seconds |

Cold start means the process was killed. Warm start (process alive, activity recreated) must be under 1 second.

### Battery Budget

- The foreground service idle (no inference, no active task) must consume no more than 1% battery per hour on a Pixel 7-class device. Measure with Android Battery Historian, not estimation.
- Background inference (triggered by notification or schedule) is limited to a maximum 5-second wall-clock execution window per trigger. If a task cannot complete in 5 seconds, it must either request user interaction or defer to the next opportunity.
- "Sleep mode" — unload the intent classifier model — activates after 5 minutes of no user interaction. Re-load on next user message. The re-load latency must be shown to the user with a loading indicator; it must never appear as "the app is frozen."
- Show the actual battery impact percentage in Settings from Phase 3 onward. Do not wait for Phase 6.

### Token Budget

| Context Component | Token Limit |
|---|---|
| System prompt | 512 tokens |
| Conversation history (rolling window) | 2048 tokens |
| Current screen tree | 800 tokens |
| Recent notifications | 400 tokens |
| Retrieved memory snippets | 600 tokens |
| Tool definitions | 400 tokens |
| **Total context ceiling** | **4760 tokens** |

`ContextManager.kt` is responsible for enforcing these limits before every LLM call. If the conversation history exceeds its budget, summarize the oldest 50% of messages into a single summary message and discard the originals from context (keep them in DB). Never truncate mid-message.

---

## 7. SECURITY RULES

### API Key Storage

- API keys are stored in Android Keystore-backed `EncryptedSharedPreferences` (Jetpack Security library). Never in plain `SharedPreferences`. Never in Room. Never in any file.
- Keys are never passed as constructor parameters to classes. They are read from the repository at call time.
- Keys are never included in log output. The `AionLogger` wrapper must redact any string matching the pattern `sk-...`, `AIza...`, or any key prefix registered by the user.
- If a key is found in a Logcat trace during code review, the PR is rejected and the key must be rotated.

### MCP Server Security

- The MCP server binds to `127.0.0.1` (loopback) by default. LAN access requires explicit user opt-in from Settings.
- When LAN mode is enabled, the server binds to the device's local network IP, not `0.0.0.0`.
- Authentication token: 32 bytes, cryptographically random (`SecureRandom`), base64url-encoded. Generated on first server start, stored in EncryptedSharedPreferences.
- Every WebSocket connection must provide the token in the initial handshake within 3 seconds. Connections that fail to authenticate are dropped immediately without any response data.
- Maximum 3 simultaneous MCP client connections. New connections beyond this limit are rejected.
- Rate limit: 60 tool calls per minute per connected client. Exceeding this closes the connection.
- All MCP session activity is logged to the local audit log with timestamp, client identifier (IP hash), tool name, and result status — not the full input/output.

### Sensitive Screen Handling

- When `AgentAccessibilityService` detects a window with `FLAG_SECURE` set, or whose package is in the hardcoded exclusion list (banking apps, password managers), it must immediately stop reading the screen, discard any buffered tree data, and log "Secure window detected — screen reading suspended."
- The hardcoded exclusion list minimum: `com.android.settings`, Google Pay, all known password managers (`com.lastpass.lpandroid`, `com.agilebits.onepassword`, `com.bitwarden.mobile`), all known banking app package prefixes.
- This list is not user-editable. Users cannot override secure screen exclusions.

### What Never Goes in Crash Reports

Even opt-in crash reports must never include: message content, contact names or numbers, notification text, model API keys, file paths containing usernames, location data, app-usage patterns tied to an identity.

Crash reports contain: stack trace, device model, Android API level, AION version, AgentCapability tier at time of crash, anonymized session ID.

---

## 8. ERROR HANDLING

### The Result Type

All domain operations return `Result<T>` (Kotlin stdlib). No throwing exceptions across layer boundaries.

```kotlin
// Correct
suspend fun sendSms(to: String, body: String): Result<Unit>

// Wrong
suspend fun sendSms(to: String, body: String): Unit  // throws on failure
```

- `Result.success()` means the operation completed as intended.
- `Result.failure()` wraps a typed `AionException` subclass, never a raw `Exception`.
- The `AionException` hierarchy lives in `core/AionException.kt`:

```kotlin
sealed class AionException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class ModelNotLoadedException(model: String) : AionException("Model not loaded: $model")
    class InsufficientRamException(required: Long, available: Long) : AionException(...)
    class PermissionDeniedException(permission: String) : AionException(...)
    class ToolExecutionException(tool: String, reason: String) : AionException(...)
    class ContextLimitExceededException(tokens: Int, limit: Int) : AionException(...)
    class NetworkUnavailableException : AionException("No network available")
    class ProviderAuthException(provider: String) : AionException(...)
    class SkillNotFoundException(skillId: String) : AionException(...)
    class SecureWindowException : AionException("Cannot read secure window")
}
```

### Failure Behavior Per Layer

| Layer | On failure | User sees |
|---|---|---|
| JNI / llama.cpp | Catch `Throwable`, return `Result.failure(ModelNotLoadedException)` | "Model error. Tap to reload." |
| Cloud LLM HTTP | Catch `IOException`, check status code, return typed failure | Provider-specific error: "OpenRouter returned 429 — rate limited." |
| Tool execution | Return `SkillResult.Failure(reason)`, do not stop agent loop | "I couldn't send that SMS: [reason]. Want me to try again?" |
| Agent loop | Log, notify user, attempt recovery, do not silently drop | Notification: "AION hit an error. Tap to see details." |
| Foreground service | AlarmManager watchdog restarts it within 30 seconds | Persistent notification reappears |

### Never Do This

- Catch an exception and return a hardcoded "Something went wrong" message without logging the actual error.
- Retry a failed LLM call more than 3 times without user knowledge.
- Continue executing an agent plan after a critical step fails. Mark the plan as failed, report to user, stop.
- Swallow a `SecurityException` from a permission denial. Surface it immediately as a `PermissionDeniedException`.

---

## 9. TESTING REQUIREMENTS

### Coverage Minimums (Enforced by CI)

| Module | Minimum line coverage |
|---|---|
| `core/` | 80% |
| `skills/builtin/` | 90% |
| `skills/Bm25Router.kt` | 95% |
| `memory/` | 75% |
| `llm/` (excluding JNI) | 70% |
| `mcp/McpProtocol.kt` | 85% |
| `ui/` | 40% (Compose UI testing is supplemental) |

### What Must Have a Unit Test

Every function that:
- Routes a query to a model tier
- Selects a skill from the registry
- Trims or summarizes a context window
- Parses a YAML skill definition
- Generates or validates an MCP token
- Scores a BM25 match
- Calculates whether there is sufficient RAM to load a model
- Handles any `AionException` type

### Integration Tests

Required before each phase milestone:
- `ForegroundService` stays alive through a simulated Doze cycle (use `adb shell dumpsys deviceidle force-idle`).
- SMS send end-to-end with a mocked `SmsManager`.
- Notification capture → DB storage → retrieval round trip.
- Local LLM loads, generates 20 tokens, unloads, RAM returns to baseline.
- BM25 router returns correct skill for the top 20 most common user queries (fixed test set, expected outputs documented).
- MCP server accepts a valid token connection and rejects an invalid one.

### Test Naming Convention

```kotlin
// Format: methodName_condition_expectedResult
@Test
fun classifyIntent_emptyInput_returnsUnknownIntent()

@Test
fun bm25Router_querySendSms_returnsSmsSkillFirst()

@Test
fun loadModel_insufficientRam_returnsInsufficientRamException()
```

### What Is Not Tested

- JNI layer internals (llama.cpp itself). Test the `LocalLlmEngine` Kotlin wrapper, not the C++ code.
- UI pixel-perfection. Test behavior, not layout.
- The contents of actual LLM responses. Test the routing, parsing, and dispatch around them, not the model output itself.

---

## 10. LLM INTEGRATION RULES

### Model Lifecycle

There are two model slots: `CLASSIFIER` and `PLANNER`. They are managed exclusively by `ModelManager`.

- `CLASSIFIER` slot: always the 3B model. Loaded on first user interaction after app start. Never unloaded while the app is in foreground unless sleep mode activates.
- `PLANNER` slot: the user-configured model (3B–7B local, or cloud). Loaded on demand when a `PLANNING` intent is classified. Unloaded when sleep mode activates.
- Embedding model (`gte-small`): loaded on demand for memory retrieval. Shares no slot with inference models; loaded and unloaded independently.
- Only one model per slot loads at a time. Loading a new model into a slot unloads the previous one. The load sequence: verify RAM headroom → unload old → load new → verify load succeeded → update `ModelState`.
- Model files are stored in `Context.filesDir/models/`. Not on external storage. Never in cache (cache is not guaranteed to persist).

### Prompt Format

Every LLM call uses this structure:

```kotlin
data class LlmRequest(
    val systemPrompt: String,
    val messages: List<Message>,
    val tools: List<ToolDefinition>? = null,
    val maxTokens: Int = 512,
    val temperature: Float = 0.2f,    // Low temp for tool calls, higher for chat
    val stream: Boolean = true
)
```

- System prompt maximum: 512 tokens (enforced before call, not assumed).
- Tool-calling requests always use `temperature = 0.1f` to reduce hallucinated tool arguments.
- Chat responses use `temperature = 0.4f` unless the user has configured otherwise.
- Never send the raw `AccessibilityTree` JSON to the LLM without first compressing it through `AccessibilityTree.toTokenEfficientString()`.

### Streaming Rules

- Token streaming from `LocalLlmEngine` emits a `Flow<String>`.
- The `ChatViewModel` collects this flow and buffers: emit to UI every 5 tokens, not every single token.
- The UI updates the last message in the list in-place. No new message appended per token.
- If the stream is interrupted (user navigates away, OOM, timeout), the partial response is kept in the DB with a `MessageStatus.INCOMPLETE` flag. On next resume, the partial text is visible with a "(incomplete)" label. It is never silently discarded.
- Cloud SSE streaming follows the same 5-token buffer rule applied on the collector side.

### Context Assembly Order

When building the context for an LLM call, `ContextManager` assembles in this order and trims from the oldest messages first if over budget:

1. System prompt (never trimmed)
2. Memory snippets relevant to the current query (trim: oldest first)
3. Current screen tree if screen capability is active (trim: remove if over budget)
4. Recent notifications summary if relevant (trim: remove if over budget)
5. Conversation history (trim: summarize oldest 50% first)
6. Current user message (never trimmed)

---

## 11. PERMISSION & PRIVACY RULES

### When to Request Permissions

| Permission | When to request |
|---|---|
| `SEND_SMS`, `READ_SMS`, `RECEIVE_SMS` | Only when user first invokes an SMS-related command |
| `CALL_PHONE`, `READ_CALL_LOG` | Only when user first invokes a call-related command |
| `POST_NOTIFICATIONS` | On first launch, before showing any UI |
| `READ_CONTACTS` | Only when a skill that needs contacts is first used |
| `ACCESS_FINE_LOCATION` | Only when a location-triggered automation is first created |

Never request all permissions upfront. Never request a permission "just in case" a user might want the feature. Never request a permission and then not use it within the same user session.

If a permission is denied, record it in `SettingsRepository`. Do not ask again for 7 days. On the third denial, do not ask again ever — show a permanent "this feature requires [permission] — tap to open settings" message in that feature's UI.

### Notification Access and Accessibility Service

These are not runtime permissions — they require navigating the user to a system settings screen. Rules:
- Show the request in context: only ask when the user tries to use a feature that requires it.
- Show a plain-language explanation screen before sending the user to Settings, explaining exactly what the permission enables and what it does not do.
- Never deep-link directly to the Settings screen without the explanation screen first.
- After returning from Settings, check whether it was granted. If not, do not ask again in the same session.

### Data That Is Never Stored

- The content of messages sent or received via SMS (only metadata: sender, timestamp, direction, character count).
- The full accessibility tree of any screen (only structured summaries after the agent has acted).
- Full notification body text after it has been processed (store: app package, category, timestamp, action taken. Discard: notification text).
- Cloud LLM request/response pairs (stored only in conversation history for the user's viewing — not in any analytics or telemetry pipeline).

### The Privacy Dashboard (Required by Phase 3)

A screen in Settings that shows, for the last 30 days:
- Which permissions are currently granted.
- How many times each tool was invoked.
- Which cloud providers received requests (zero if zero-cloud mode).
- A button to export or wipe all stored data.

This screen is not a Phase 6 polish item. It is a Phase 3 requirement. Build it when the first system tool is integrated.

---

## 12. ACCESSIBILITYSERVICE RULES

### What It Is Allowed To Do

- Read the accessibility tree of any non-secure, non-excluded-package window.
- Perform click, scroll, long-press, and global actions (HOME, BACK, RECENTS, NOTIFICATIONS) when the agent has been instructed to do so by the user.
- Detect window state changes and emit them to the agent loop.
- Read the content of text fields, but only when the agent has a specific task that requires it and the user confirmed the task.

### What It Is Never Allowed To Do

- Read the content of password fields (identified by `isPassword = true` on `AccessibilityNodeInfo`). If a password field is detected in a form the agent is filling, stop, notify the user, and request manual input.
- Perform any click, gesture, or action autonomously without the user having explicitly triggered a task in this session. "Autonomously" means without a triggering event in the agent loop.
- Screenshot any screen that has `FLAG_SECURE` set.
- Stay connected to windows that belong to excluded packages (banking apps, password managers — see Section 7).

### Android Version Handling

| Android Version | Issue | Mitigation |
|---|---|---|
| Android 14+ | 30-minute inactivity timeout disconnects the service | Implement `AccessibilityService.setCacheEnabled(true)`. Show a persistent notification with a re-enable button when timeout is detected. Log the disconnection event. |
| Android 13+ | `MANAGE_MEDIA` tightened | Avoid `MANAGE_MEDIA` entirely. Do not add it to the manifest. |
| Android 12+ | Background start restrictions | All background task initiation goes through `WorkManager` or the foreground service notification action, never directly. |

### Capability Degradation Contract

When `AgentCapability` is not `FULL`:

| Feature | FULL | PARTIAL | MINIMAL |
|---|---|---|---|
| Screen reading | ✅ | ❌ | ❌ |
| UI automation (tap/click) | ✅ | ❌ | ❌ |
| Notification management | ✅ | ✅ | ❌ |
| SMS send/read | ✅ | ✅ | ✅ |
| Cloud LLM chat | ✅ | ✅ | ✅ |
| Local LLM chat | ✅ | ✅ | ✅ |
| Skill triggers (notification) | ✅ | ✅ | ❌ |
| Skill triggers (schedule) | ✅ | ✅ | ✅ |

Features marked ❌ at a given tier are hidden in the UI or shown with a "Requires [permission] — tap to enable" label. They never show as available and then fail at runtime.

---

## 13. SKILL SYSTEM RULES

### The AgentSkill Interface Contract

```kotlin
interface AgentSkill {
    val id: String
    val name: String
    val description: String
    val version: String                     // semver: "1.0.0"
    val keywords: List<String>              // minimum 5, maximum 20
    val requiredPermissions: List<String>   // declared, checked before execute()
    val requiredCapability: AgentCapability // minimum tier needed

    suspend fun canHandle(input: String): Float  // 0.0–1.0 confidence
    suspend fun execute(context: AgentContext, params: Map<String, Any>): SkillResult
}
```

- `execute()` must complete within 30 seconds. After 30 seconds, the `SkillExecutionEngine` cancels the coroutine and returns `SkillResult.Timeout`.
- `execute()` must not make network calls unless `requiredPermissions` includes `"network"`.
- `execute()` must not access any Android system service not declared in `requiredPermissions`.
- `canHandle()` must not call the LLM. It is a fast keyword/pattern check. Its result feeds BM25; it is not a standalone router.

### YAML Skill Format

```yaml
id: skill-id-kebab-case       # required, unique, immutable across versions
name: Human Readable Name     # required
version: 1.0.0                # required, semver
description: >
  One to three sentences.
  What this skill does and when it activates.
keywords:                     # minimum 5, maximum 20
  - keyword1
  - keyword2
required_permissions:         # only declare what is actually used
  - sms:read
  - sms:write
required_capability: MINIMAL  # MINIMAL | PARTIAL | FULL
triggers:                     # optional
  - type: time
    value: "07:00"
  - type: phrase
    value: "good morning"
steps:                        # required
  - id: step1
    tool: calendar.readEvents
    params:
      date: today
      limit: 5
    on_error: stop            # stop | continue | retry(n)
  - id: step2
    tool: system.speak
    params:
      text: "Good morning! You have {{ steps.step1.result.length }} events today."
```

Rules for YAML skills:
- `id` is immutable across versions. Changing it creates a new skill, not an update.
- All `tool` references must map to a registered `AgentSkill.id` or a built-in tool name.
- `{{ }}` template expressions support: `steps.<id>.result`, `context.time`, `context.date`, `context.user.name`. Nothing else without an explicit extension.
- `on_error: stop` is the default. Skills that use `continue` must document why in their YAML comments.
- Maximum 20 steps per skill. Skills that need more should be refactored into sub-skills.

### Skill Security

- Skills are sandboxed in a restricted coroutine scope. They cannot cancel the parent scope.
- A skill that declares `required_permissions: [sms:write]` and then attempts to access `ContactsSkill` gets a `PermissionDeniedException` at runtime.
- Third-party skills from the marketplace are shown a permission review screen before installation. The user explicitly approves each declared permission.
- Skills from GitHub are treated as untrusted until verified (GPG-signed by a known developer key). Unverified skills show a yellow "Unverified" badge and an additional confirmation dialog on execution.

### BM25 Router Rules

- The BM25 index is rebuilt whenever a skill is installed, uninstalled, or updated. Never lazily.
- The confidence threshold for routing to a specific skill is 0.35. Below this, route to the general LLM.
- If two skills score within 0.05 of each other, do not automatically route to either. Present both to the user as suggestions: "I think you want [Skill A] or [Skill B] — which one?"
- BM25 routing must complete in under 15ms. If it does not on a given device, profile and fix before shipping that phase.

---

## 14. MCP SERVER RULES

### Protocol Compliance

Implement MCP protocol version `2025-03-26`. Every message type listed below must be implemented and tested:

- `initialize` / `initialized` — capability exchange
- `tools/list` — return all installed skills as tool definitions
- `tools/call` — invoke a skill by name with validated parameters
- `resources/list` — expose: `screen/current`, `notifications/recent`, `contacts/list`
- `resources/read` — read a named resource
- `prompts/list` and `prompts/get` — expose agent prompts
- `ping` / `pong` — keepalive, required every 30 seconds

Non-compliant messages return a structured JSON error, not an HTTP 500 or silent drop.

### Tool Definition Mapping

Every registered `AgentSkill` maps to an MCP tool definition at server start. The mapping is automatic — `McpToolMapper` converts `AgentSkill` properties to the MCP tool schema. The `id` becomes the tool name. The `description` becomes the tool description. The `requiredPermissions` are listed in the tool description as "Requires: [permission list]."

If a skill's `requiredCapability` exceeds the current `AgentCapability`, the tool is still listed but returns `{"error": "insufficient_capability", "required": "FULL", "current": "PARTIAL"}` when called. It is never hidden from the tool list.

### Port and Network

- Default port: `8765`. User-configurable in Settings (range: 1024–65535).
- Binding: loopback only by default. LAN binding requires explicit toggle in Settings.
- Port conflicts: if `8765` is taken, try `8766`, `8767`, `8768`. If all fail, report "Port unavailable — please configure a custom port" and do not start the server.
- The MCP server stops when the foreground service stops. It does not run independently.
- mDNS advertisement (`_aion._tcp`) activates only when LAN mode is enabled.

---

## 15. UI & COMPOSE RULES

### Performance

- Every `LazyColumn` item must be stable or explicitly use `@Stable` / `@Immutable` annotations on its data class. Unstable items cause full list recompositions.
- No `derivedStateOf` inside a loop or a `LazyColumn` item. Compute outside.
- Images (model download previews, avatars) are loaded with Coil. Never `BitmapFactory.decodeFile()` on the main thread.
- The chat message list renders a maximum of 200 messages at a time. Older messages are paginated. The list does not hold the full conversation history in memory.

### Compose-Specific Rules

- `@Composable` functions are stateless (pure functions of their parameters) or collect from state via `collectAsStateWithLifecycle()`. No internal `remember { mutableStateOf(...) }` that mirrors data from the DB — use the ViewModel.
- Side effects in Composables use `LaunchedEffect`, `SideEffect`, or `DisposableEffect` as appropriate. Never `remember { coroutineScope.launch { ... } }`.
- Do not pass a `ViewModel` down the composable tree. Pass state (data classes) and event lambdas.
- `Modifier` parameters: every Composable that renders visible content accepts an external `modifier: Modifier = Modifier` parameter as its last positional parameter.

### Theming

- All colors come from `MaterialTheme.colorScheme`. No hardcoded hex values in Composables.
- All text styles come from `MaterialTheme.typography`. No hardcoded `fontSize` or `fontWeight` in Composables.
- Dark theme is the primary theme. Light theme is supported. Test both before any UI PR is merged.
- Minimum touch target: 48dp × 48dp for all interactive elements (Material 3 requirement, Android accessibility requirement).

### Error State and Empty State

Every screen that loads async data must have three states handled:
1. Loading — skeleton or progress indicator
2. Error — specific, human-readable error message + retry action
3. Empty — a message explaining why there is nothing, not a blank screen

---

## 16. DEPENDENCY MANAGEMENT

### How to Add a Dependency

Before adding any new library:
1. Check if the existing stack already provides the functionality.
2. Check the library's last commit date, open issue count, and Android compatibility.
3. Add it to `libs.versions.toml` only — never hardcode versions in `build.gradle.kts`.
4. Add a comment in `libs.versions.toml` explaining why this dependency was added.
5. Include the dependency change in the PR description with a brief justification.

No transitive dependency upgrades without understanding what changed. `./gradlew dependencies` before and after adding any new library.

### Approved Core Dependencies (Do Not Replace Without a Design Discussion)

| Library | Version constraint | Purpose |
|---|---|---|
| Kotlin | 2.1.x | Language |
| Jetpack Compose BOM | Latest stable | UI |
| Hilt | 2.51.x | DI |
| Room | 2.6.x | Local DB |
| Ktor (server + client) | 2.3.x | MCP server + HTTP client |
| OkHttp | 4.12.x | Network |
| Kotlinx Serialization | 1.6.x | Serialization |
| DataStore (Preferences) | 1.1.x | Settings persistence |
| WorkManager | 2.9.x | Background scheduling |
| Coil | 2.6.x | Image loading |
| Accompanist (Permissions) | 0.34.x | Permission handling |
| sqlite-vec | Pin exact version | Vector search |
| JmDNS | 3.5.x | mDNS for edge server discovery |

### Dependencies That Are Banned

- Any reflection-based serialization library (Gson, Moshi-reflection). Kotlin Serialization only.
- Any analytics SDK that sends data to a third party (Firebase Analytics, Mixpanel, etc.) without an explicit opt-in build flavor.
- Any library that pulls in `com.google.code.findbugs:jsr305` — it conflicts with the Kotlin compiler on Android.
- Apache Commons. Use Kotlin stdlib.
- Any library last updated more than 18 months ago without an explicit justification.

---

## 17. DOCUMENTATION STANDARDS

### KDoc Requirements

Every `public` or `internal` function, class, and property in the following modules requires a KDoc comment: `core/`, `llm/`, `skills/`, `memory/`, `mcp/`, `system/`.

KDoc format:

```kotlin
/**
 * Classifies a user input string into an [AgentIntent].
 *
 * Runs on [Dispatchers.Default]. The local LLM must be loaded before calling.
 * Returns [AgentIntent.Unknown] if the model is not loaded rather than throwing.
 *
 * @param input Raw user message text. Must not be blank.
 * @param contextSnapshot Current [AgentContext] at time of classification.
 * @return [AgentIntent] with a confidence score.
 */
suspend fun classifyIntent(input: String, contextSnapshot: AgentContext): AgentIntent
```

- Do not document what the code obviously does. Document why and what callers need to know.
- Document threading requirements (which dispatcher) on all suspend functions that are not dispatcher-agnostic.
- Document side effects (emits to a flow, writes to DB, starts a service) explicitly.

### Required Docs Files

| File | Required by | Contents |
|---|---|---|
| `README.md` | Phase 1 | Build instructions, features list, screenshots (Phase 6) |
| `docs/ARCHITECTURE.md` | Phase 2 | Layer diagram, data flow, key decisions |
| `docs/PERMISSIONS.md` | Phase 3 | Every permission: why needed, when requested, what it enables |
| `docs/PRIVACY.md` | Phase 3 | Data handling, zero-cloud mode, what is stored, export/wipe |
| `docs/SKILLS.md` | Phase 4 | YAML skill format reference with examples |
| `docs/MCP.md` | Phase 5 | How to connect, tool list, auth flow, example Claude Desktop config |
| `CONTRIBUTING.md` | Phase 1 | Setup guide, PR process, code style reference |
| `CHANGELOG.md` | Phase 1 | Updated on every tagged release, follows Keep a Changelog format |

---

## 18. CI/CD & RELEASE

### GitHub Actions Pipeline

Every push to any branch triggers:
1. `./gradlew ktlintCheck` — Kotlin lint. Failure blocks merge.
2. `./gradlew testDebugUnitTest` — Unit tests. Failure blocks merge.
3. `./gradlew assembleDebug` — Debug APK build. Failure blocks merge.

Every push to `develop`:
4. `./gradlew connectedAndroidTest` — Instrumented tests on API 33 emulator.

Every push to `main`:
5. Everything above plus `./gradlew assembleRelease` with signing.
6. Automatic GitHub Release draft created with signed APK attached.

### Versioning

Format: `MAJOR.MINOR.PATCH`

- `MAJOR`: breaking change to skill API, MCP protocol, or DB schema requiring migration.
- `MINOR`: new feature, new built-in skill, new model support.
- `PATCH`: bug fix, performance improvement, UI polish.

`versionCode` in `build.gradle.kts` is calculated as `(MAJOR * 10000) + (MINOR * 100) + PATCH`. It is never manually set.

### Release Checklist (Before Every Tagged Release)

- [ ] `CHANGELOG.md` updated.
- [ ] All tests passing on CI.
- [ ] Tested on minimum three target devices (see Section 19).
- [ ] Privacy Dashboard reviewed — no unexpected data in local DB.
- [ ] ProGuard/R8 rules verified — `LlamaBridge` JNI methods not obfuscated.
- [ ] APK size checked. Debug: any size. Release: flag for review if over 50MB before model download.
- [ ] Permissions in manifest match the permissions documented in `docs/PERMISSIONS.md`.
- [ ] `minSdk` confirmed at 26. `targetSdk` confirmed at the latest stable Android API level.

### Build Flavors

| Flavor | AccessibilityService | Purpose |
|---|---|---|
| `full` | Included | F-Droid, GitHub Releases, sideload |
| `lite` | Excluded | Google Play Store submission |

Both flavors share all code. `lite` uses a `@Qualifier`-differentiated Hilt binding that swaps `AgentAccessibilityService` for `NoOpAccessibilityService`, which returns `AgentCapability.PARTIAL` always.

---

## 19. DEVICE COMPATIBILITY CONTRACT

### Minimum Supported Configuration

- Android API 26 (Android 8.0) minimum.
- `arm64-v8a` ABI only. No `x86`, no `armeabi-v7a` for v1.0.
- 4GB RAM device minimum. The app runs on 4GB but without simultaneous model + embedding. Document this clearly.

### Target Test Devices (Must Test Before Every Release)

| Device | Reason |
|---|---|
| Pixel 7 or 8 (API 33–34) | Stock Android, baseline reference |
| Samsung Galaxy S23 (One UI 6) | Largest Android OEM, aggressive battery management |
| OnePlus or Xiaomi (any recent) | MIUI/ColorOS background kill behavior |

If you do not have physical access to all three, use the Android Device Farm (Firebase Test Lab free tier) for the Samsung and Xiaomi tests.

### Known OEM Issues to Test Explicitly

- **Samsung One UI:** `FOREGROUND_SERVICE` is killed by Adaptive Battery by default. Test the "exclude from battery optimization" flow specifically on Samsung.
- **Xiaomi MIUI:** `NotificationListenerService` is disabled by MIUI's "Autostart" restriction. The onboarding flow must detect this and guide the user to `Settings > Apps > Autostart`.
- **Any OEM with Android 14+:** AccessibilityService 30-minute timeout. Simulate by running `adb shell settings put secure accessibility_service_timeout 0` and verify the re-enable notification appears.

---

## 20. WHAT NEVER SHIPS

This section is a hard stop. These items are not in scope for any phase of the project and are not to be implemented, experimented with, or "quickly prototyped."

**No root access.** The app does not request, use, or benefit from root. Users with rooted devices are not a target segment. ADB is not a deployment mechanism.

**No background recording.** The app does not record audio, camera, or screen content continuously. Screenshots are taken only synchronously during an active agent task, immediately processed, and immediately discarded. No screenshot is written to storage.

**No contact upload.** Contact data used by the ContactsSkill stays on-device. No contact name, number, or email leaves the device unless the user explicitly sends it as part of a message to a cloud LLM — in which case the user already confirmed the cloud LLM call.

**No autonomous financial transactions.** The app has no payment skills, no crypto wallet integration, and no ability to submit forms that involve financial data unless each individual form submission is confirmed by the user.

**No multi-agent protocol in v1.0.** Agent-to-agent LAN communication (Phase 5, Days 109–115 in the original plan) is deferred to v1.2. Do not begin implementation until v1.0 is shipped and has active users.

**No Play Store submission for the `full` flavor.** The `full` build with `AccessibilityService` is distributed via GitHub Releases and F-Droid only. The `lite` flavor is the Play Store build. This is not a future decision — it is a current architectural constraint.

**No telemetry without explicit user opt-in and a clear, specific disclosure of what is collected.** Default is zero telemetry. The opt-in screen lists every data point collected in plain language. There is no "trust us" phrasing.

---

*End of AION Developer Guidelines v1.0*
*Update this document when a guideline changes — do not let the code drift from the document.*
