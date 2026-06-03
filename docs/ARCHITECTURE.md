# AION Architecture

> **Status:** Phase 1 (chat + cloud LLM + SMS). Phase 2+ layers are
> scaffolded but not yet implemented. This document is updated as each phase lands.

## High-level

AION is a single-Activity Android app. All UI is Jetpack Compose. The agent
loop runs in coroutines, coordinated by Hilt-injected singletons.

```
┌─────────────────────────────────────────────────────────────┐
│  UI Layer (ui/)                                             │
│   ChatScreen · SettingsScreen · OnboardingScreen            │
│   ViewModels (collect StateFlow, emit events)               │
└────────────────────┬────────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────────┐
│  Core Agent (core/)                                         │
│   AgentLoop · IntentClassifier · ContextManager             │
│   AgentCapability (FULL/PARTIAL/MINIMAL)                    │
└──────┬────────────────────────────────────┬─────────────────┘
       │                                    │
┌──────▼──────────────┐           ┌─────────▼─────────────────┐
│  Skills (skills/)   │           │  LLM (llm/)               │
│   SkillRegistry     │           │   CloudLlmEngine          │
│   Bm25Router        │           │   (Phase 2: LocalLlm)     │
│   AgentSkill        │           │   providers/              │
│   builtin/          │           │    LlmProviderRegistry    │
└─────────────────────┘           └───────────┬──────────────┘
                                              │
                                  ┌───────────▼──────────────┐
                                  │  Network                 │
                                  │   OkHttp + kotlinx-srl   │
                                  │   SSE streaming          │
                                  └──────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│  System Services (system/)                                  │
│   AgentForegroundService (PHASE 1 ✓)                        │
│   CapabilityManager (PHASE 1 ✓)                             │
│   AgentAccessibilityService (PHASE 3 — pending)             │
│   AgentNotificationListener (PHASE 3 — pending)             │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│  Persistence                                                │
│   Room: ConversationEntity, MessageEntity (PHASE 1 ✓)       │
│   DataStore: settings (PHASE 1 ✓)                           │
│   EncryptedSharedPreferences: API keys (PHASE 1 ✓)          │
│   sqlite-vec: Phase 3 — pending                             │
└─────────────────────────────────────────────────────────────┘
```

## Capability gating (per AION_PLAN §4)

| Tier | Granted by | Features available |
|---|---|---|
| `MINIMAL` | Always | Chat with LLM, no system access |
| `PARTIAL` | `NotificationListenerService` enabled | + read/manage notifications, SMS, calls |
| `FULL` | `AccessibilityService` enabled | + screen reading, UI automation, app event monitoring |

The single source of truth for the current tier is `CapabilityManager`.
Every feature that depends on a tier collects from
`capabilityManager.capability` and never makes its own assessment.

## Model router (Phase 1 simplified)

Phase 1 ships with **only the cloud router**. The local router is Phase 2.

```
User input
    ↓
IntentClassifier (heuristic, BM25 over skills)
    ↓
  ┌─────────────────┐
  │  Above 0.35?    │──No──→  CloudLlmEngine (chat, no tools)
  │  Clear winner?  │              ↓
  └────────┬────────┘         SSE stream
           │Yes                     ↓
           ↓                  Token buffer
  Execute skill (with        (5-token chunks)
   user confirmation if            ↓
   mutating)                  ChatViewModel
```

## Streaming token architecture (Phase 1)

Per AION_PLAN §5:

```
CloudLlmEngine.streamReply() emits Flow<LlmEvent>
  → callbackFlow wrapping OkHttp SSE
  → AgentLoop collects, appends to current assistant MessageEntity
  → ChatViewModel updates StateFlow<ChatUiState>
  → Compose recomposes one MessageBubble in place
```

The 5-token batch buffer described in AION_PLAN §5 is implemented in
`llm/TokenBuffer.kt` and is used by the chat layer if/when we need to
squeeze UI responsiveness. Phase 1 with cloud LLM at 30-60 tok/s is
fast enough that we emit per-token and let Compose handle batching.

## Memory budget

Per AION_GUIDELINES §6:

| Component | RAM |
|---|---|
| AION process (foreground service, Compose UI) | ~120-200MB |
| Conversation database (Room, capped at ~5000 messages) | ~30-80MB |
| HTTP client buffers (per-request) | ~5MB |
| **Total Phase 1** | **~200-300MB** |

Phase 2 will add the local LLM (~1.8GB resident) and the embedding model
(~200MB). On 6GB devices the app will prompt the user to choose between
local and cloud, with cloud as the default.

## Security

Per AION_GUIDELINES §7:

- API keys: **EncryptedSharedPreferences** with Android Keystore master key.
- Logs: **AionLogger** redacts `sk-*`, `AIza*`, `nvapi-*`, `Bearer *` patterns.
- Crash reports (when enabled in Phase 6): no message content, no contacts,
  no notification text, no keys.
- Future MCP server (Phase 5): loopback-only by default, WSS + token auth.

## Testing

- `app/src/test/` — unit tests (BM25 router, capability ordering, context manager)
- `app/src/androidTest/` — reserved for Phase 2+ instrumented tests

Coverage minimums per AION_GUIDELINES §9 are enforced in CI when coverage
tooling is added (Phase 6).
