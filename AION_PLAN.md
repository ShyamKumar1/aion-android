# Project AION — Android AI Agent Operating System

**Version:** 2.0 — June 2026
**Status:** Incorporating production engineering review

---

## TABLE OF CONTENTS

1. [Executive Summary](#1-executive-summary)
2. [The Honest Verdict — Key Changes from v1.0](#2-the-honest-verdict)
3. [Competitive Landscape](#3-competitive-landscape)
4. [Capability Level Architecture — The Load-Bearing Decision](#4-capability-level-architecture)
5. [Core Architecture](#5-core-architecture)
6. [Tech Stack & Why](#6-tech-stack--why)
7. [Device Compatibility Matrix](#7-device-compatibility-matrix)
8. [Onboarding & Trust Design](#8-onboarding--trust-design)
9. [Memory Budget & Context Window Model](#9-memory-budget--context-window)
10. [MCP Server Security Model](#10-mcp-server-security-model)
11. [Phase 1 — Chat + SMS + Cloud Mode (Weeks 1-3)](#11-phase-1--chat--sms--cloud-mode-weeks-1-3)
12. [Phase 2 — Local LLM Integration (Weeks 4-7)](#12-phase-2--local-llm-integration-weeks-4-7)
13. [Phase 3 — System Eyes & Ears (Weeks 8-14)](#13-phase-3--system-eyes--ears-weeks-8-14)
14. [Phase 4 — Skill System & Autonomy (Weeks 15-21)](#14-phase-4--skill-system--autonomy-weeks-15-21)
15. [Phase 5 — MCP Server & Ecosystem Launch (Weeks 22-26)](#15-phase-5--mcp-server--ecosystem-launch-weeks-22-26)
16. [Phase 6 — Polish, Onboarding & Ship (Weeks 27-32)](#16-phase-6--polish-onboarding--ship-weeks-27-32)
17. [v1.1+ Roadmap (Cut from v1.0)](#17-v11-roadmap)
18. [Monetization Strategy](#18-monetization-strategy)
19. [Risk Matrix — Revised](#19-risk-matrix--revised)

---

## 1. EXECUTIVE SUMMARY

### What We're Building

An **open-source Android AI agent** that runs on your phone, sees your screen, reads your notifications, monitors your apps, and executes tasks autonomously — using **any LLM** the user chooses (local GGUF on-device, private edge server, or cloud provider).

Think **Hermes Agent + Claude Computer Use + Tasker**, but native to Android and running fully on your phone. No host PC. No ADB. No cloud dependency.

### The Gap (Validated Against 85+ Projects)

| What Exists | What's Missing |
|---|---|
| MCP servers that control Android FROM a PC (5k⭐) | MCP server running ON the Android device |
| Chat apps with on-device LLMs (MLC LLM, Maid) | An AI that can see your screen and tap buttons |
| Tasker / MacroDroid automation | AI-driven autonomous planning & decision-making |
| AccessibilityService screen agents (20⭐) | Full skill system + persistent memory + MCP |
| NotificationListenerService | Zero projects combine this with AI |
| On-device LLM inference (LiteRT, llama.cpp) | An agentic layer that orchestrates it all |

### What Changed Between v1.0 and v2.0

This revision incorporates a production engineering review that identified:

- 🔴 **AccessibilityService is a plan-killing risk**, not a medium one. The entire architecture must degrade gracefully without it.
- 🟠 **Timeline was fiction** for a solo developer. Adjusted from 150 days to ~32 weeks (8 months).
- 🟠 **Battery is a Day-1 concern**, not a Phase-6 afterthought.
- 🟠 **On-device model onboarding** will kill conversion. "Zero model" cloud-first mode required.
- 🟡 **Multi-agent protocol** cut from v1.0. Cool but scope bomb.
- 🟡 **MCP security model** needs a proper design, not a bullet point.
- 🟡 **Context window management, device compatibility, and trust onboarding** all needed dedicated sections.
- ✅ Core architecture, BM25 routing, three-tier model router, MCP-on-phone concept all validated as correct.

---

## 2. THE HONEST VERDICT

From the reviewer (paraphrased):

> This is one of the most thoughtful solo-developer AI agent plans I've seen. The research is real, the gap is real, the architecture is conceptually sound, and the timing is genuinely right. The three-tier model router, BM25 skill routing, and MCP-server-on-phone idea are all legitimately smart. I'd fund this if I were a VC.

**True. But the plan had blind spots. They are now addressed.**

---

## 3. COMPETITIVE LANDSCAPE

### Direct Competitors

| App | Approach | Our Advantage |
|---|---|---|
| **Google Assistant / Gemini** | Cloud AI, limited device control | Run locally, see screen, use any model |
| **Tasker + AutoApps** | Rule-based automation, no AI | Autonomous planning & LLM reasoning |
| **Maid / LM Studio Mobile** | Chat UI + on-device LLM, no agent | Screen automation + skills + system access |
| **Opendroid (20⭐)** | Accessibility + vision + planning | We add skills + MCP + NotificationListener + local LLM |
| **ZeroClawAndroid (11⭐)** | FGS + LiteRT LM + messaging | We add AccessibilityService + screen vision + MCP + skills |
| **Skales (1k⭐)** | Desktop-first, Android is companion | Android-native, full on-device autonomy |
| **Claude in Mobile (279⭐)** | Host-side MCP + ADB | Everything on the phone — no PC required |

### Our Moat

1. **Data gravity** — 6 months of personal context makes switching painful
2. **Skill marketplace network effects** — More skills → more users → more developers
3. **Privacy-first architecture** — Zero-cloud mode is unique; nobody else offers it
4. **Model flexibility** — Any GGUF model + any cloud provider = no AI lock-in
5. **First-mover in a wide-open space** — No production-grade on-device Android agent exists

---

## 4. CAPABILITY LEVEL ARCHITECTURE

**This is the most critical architectural decision. AccessibilityService is the load-bearing pillar, and it is under existential threat from Google Play policies.**

Since October 2022, Google requires explicit justification that AccessibilityService usage "directly supports a disability." AI screen agents do not qualify. Android 16 tightened this further. **You cannot treat this as a medium risk.** It is a fundamental product constraint.

### The Solution: Capability Levels from Day 1

```kotlin
enum class AgentCapability(val label: String, val description: String) {
    FULL(
        "Full Access",
        "AccessibilityService enabled — agent sees screen, taps buttons, " +
        "reads all apps, observes system events. Maximum autonomy."
    ),
    PARTIAL(
        "Notification Access",
        "NotificationListenerService enabled — agent reads and manages " +
        "notifications, sends SMS, places calls. Cannot see screen."
    ),
    MINIMAL(
        "Chat Only",
        "No special permissions granted. Agent works via chat interface " +
        "with cloud/local LLM. No system access beyond what user grants."
    )
}
```

**Every feature in the app degrades gracefully based on this enum:**

| Feature | FULL | PARTIAL | MINIMAL |
|---|---|---|---|
| Chat with LLM | ✅ | ✅ | ✅ |
| Send SMS | ✅ | ✅ | ✅ (if permission granted) |
| Read notifications | ✅ | ✅ | ❌ |
| Snooze/dismiss notifications | ✅ | ✅ | ❌ |
| Read screen content | ✅ | ❌ | ❌ |
| Tap buttons / fill forms | ✅ | ❌ | ❌ |
| Monitor app changes | ✅ | ❌ | ❌ |
| MCP server (external AI connects) | ✅ | ✅ (limited tools) | ❌ |
| Autonomous triggers | ✅ | ✅ (notification-based only) | ❌ |
| Skill marketplace | ✅ | ✅ (no screen skills) | ✅ (text-only skills) |

**Design rule:** The UI must never show a button that the current capability level cannot support. Greyed-out features should explain *why* and *how to unlock*.

### Distribution Strategy Based on This

| Build Target | Included Capabilities | Distribution |
|---|---|---|
| **AION Pro (Full)** | All — AccessibilityService + everything | GitHub Releases + F-Droid (sideload) |
| **AION Lite (Play)** | PARTIAL + MINIMAL only | Google Play Store |
| **AION Core (Minimal)** | Chat + cloud LLM only | Google Play Store (free tier) |

**Decision:** We are a **F-Droid / GitHub-first app.** The Play Store build is a lead-generation funnel (get users on Lite, upsell them to sideload Pro). Design this mindset from Day 1 — Play Store is marketing, not distribution.

---

## 5. CORE ARCHITECTURE

```
                        ┌─────────────────────────────────────┐
                        │         USER INTERFACE              │
                        │  Chat · Skill Market · Settings     │
                        │  Capability Dashboard · Permissions │
                        └──────────────┬──────────────────────┘
                                       │
                        ┌──────────────▼──────────────────────┐
                        │        AGENT CORE (FGS)             │
                        │                                     │
                        │  ┌──────────┐ ┌──────────┐ ┌──────┐│
                        │  │ Observe  │→│ Plan     │→│ Exec ││
                        │  │ (events) │ │ (LLM)    │ │(tools)││
                        │  └──────────┘ └──────────┘ └──┬───┘│
                        │                                │    │
                        │  ┌─────────────────────────────▼──┐ │
                        │  │        MODEL ROUTER            │ │
                        │  │  Local(llama) ∥ Edge(Ollama)   │ │
                        │  │  Cloud(OpenAI) ∥ MCP(Remote)   │ │
                        │  └────────────────────────────────┘ │
                        └─────────────────────────────────────┘
                                       │
        ┌──────────────────────────────┼──────────────────────────┐
        │              ┌───────────────▼───────────────┐          │
        │              │   ANDROID SYSTEM LAYER         │          │
        │              │                                │          │
        │  ┌───────────▼────┐  ┌──────────────────────┐ │          │
        │  │ Capability     │  │ System Services      │ │          │
        │  │ Level Gate     │  │  • NotifListener     │ │          │
        │  │ (FULL/PARTIAL/)│  │  • AccessibilitySvc  │ │          │
        │  │  MINIMAL)      │  │  • Telephony/SMS     │ │          │
        │  └────────────────┘  │  • WorkManager       │ │          │
        │                      └──────────────────────┘ │          │
        │              ┌───────────────▼───────────────┐ │          │
        │              │   LOCAL PERSISTENCE            │          │
        │              │  Room + sqlite-vec + Memory    │          │
        │              │  • Conversations               │          │
        │              │  • Notification history         │          │
        │              │  • Vector embeddings            │          │
        │              │  • Context window summaries      │          │
        │              └────────────────────────────────┘          │
        └──────────────────────────────────────────────────────────┘
```

### The Model Router (Detailed)

```
User Input
    │
    ▼
┌─────────────────────────────────────────────┐
│  Intent Classifier (3B local model, RESIDENT)│
│  ~200-500ms prefill, ~100ms per token        │
│  Output: intent + complexity + urgency       │
└─────────────────┬───────────────────────────┘
                  │
        ┌────────┴────────┐
        ▼                 ▼
┌──────────────┐  ┌─────────────────────────────┐
│ LOCAL-ELIGIBLE│  │ NEEDS BIG MODEL             │
│ tool calls,   │  │ complex reasoning, planning, │
│ simple Q&A    │  │ code gen, long context       │
│ quick replies │  │                             │
└──────┬───────┘  └──────────┬──────────────────┘
       │                     │
       ▼                     ▼
┌──────────────┐  ┌─────────────────────────────┐
│ Execute with │  │ Route to best available:    │
│ local 3B     │  │                             │
│ Q4_K_M       │  │  Edge Server (Ollama)? → Yes│
│              │  │  Cloud API key?         → Yes│
│ "Turn on     │  │  Fallback to local 7B    →   │
│  flashlight" │  │  (if 12GB+ RAM device)       │
└──────────────┘  └─────────────────────────────┘
```

### The Agent Loop (Observe→Plan→Execute→Verify)

```
┌────────────────────────────────────────────────────────┐
│                    AGENT LOOP                           │
│                                                         │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌────────┐ │
│  │ OBSERVE  │→ │  PLAN    │→ │ EXECUTE  │→ │ VERIFY  │ │
│  │          │  │          │  │          │  │        │ │
│  │ • Chat   │  │ • Break  │  │ • Invoke │  │ • Did  │ │
│  │   input  │  │   down   │  │   skill  │  │   it   │ │
│  │ • Notif  │  │ • Select │  │ • LLM    │  │   work?│ │
│  │ • Screen │  │   tools  │  │   call   │  │ • Retry│ │
│  │ • Timer  │  │ • Stage  │  │ • System │  │ • Adapt│ │
│  │ • Memory │  │   params │  │   action │  │ • Esc  │ │
│  └────▲─────┘  └────▲─────┘  └────▲─────┘  └────┬───┘ │
│       │              │            │             │      │
│       └──────────────┴────────────┴─────────────┘      │
│                                                         │
│  FEEDBACK CHANNEL → UI updates, notifications, TTS     │
└────────────────────────────────────────────────────────┘
```

### Streaming Token Architecture

To prevent UI jitter at 40+ tok/s through Compose recomposition:

```
llama.cpp JNI → BlockingQueue<String>
    → TokenBuffer (collects 3-5 tokens, ~80-150ms window)
    → Flow<List<String>> (emit batch)
    → Compose StateFlow (recompose per batch)
    → UI renders chunk
```

This is **not** a Phase 6 polish detail. Implement it from the first LLM integration or the app will feel like a stuttery prototype.

---

## 6. TECH STACK & WHY

### Core Stack

| Layer | Technology | Justification |
|---|---|---|
| **Language** | Kotlin 2.0+ | Native Android, coroutines, Compose, KMP-ready |
| **UI** | Jetpack Compose + Material 3 | Declarative, modern, first-class |
| **Architecture** | MVVM + MVI (Unidirectional data flow) | Testable, maintainable, predictable |
| **DI** | Hilt | First-class Android DI, ViewModel scoping |
| **Async** | Kotlin Coroutines + Flow | Structured concurrency, reactive |
| **Local LLM** | llama.cpp via **llama-android** (reference impl) | Use existing bindings, don't build from scratch |
| **Cloud LLM** | OpenAI-compatible HTTP client | Works with OpenRouter, Claude, GPT, Gemini, Grok |
| **Persistence** | Room | SQLite, compile-time query validation |
| **Vector Search** | sqlite-vec (pinned version) + brute-force fallback | On-device. Pin exact version, migration tests required |
| **Local Server** | Ktor (embedded HTTP + WebSocket) | Kotlin-native, lightweight, runs in-process |
| **Serialization** | kotlinx.serialization | Kotlin-native, compile-time safe |
| **Networking** | OkHttp + Retrofit | Battle-tested, coroutine support |
| **Background** | WorkManager + Foreground Service | Reliable scheduling + persistent daemon |
| **Permissions** | Accompanist Permissions API | Modern permission handling |

### Model Inference Backend Decision

| Backend | Decision | Reasoning |
|---|---|---|
| **llama.cpp** | ✅ PRIMARY | GGUF ecosystem largest, any model, Vulkan GPU offload mature, `llama-android` gives JNI bindings out of the box |
| **LiteRT LM** | ❌ DEFERRED | Google-gated, limited to Gemma 4, experimental — reconsider when Android 16 AI Core API opens |
| **MLC LLM** | ⏸️ SECONDARY | Better GPU throughput but AOT compilation per model kills "download any GGUF" flexibility |
| **ExecuTorch** | ⏸️ FUTURE | Pre-production (v0.4). Reconsider at v1.0 |
| **Qualcomm QNN** | ❌ NOPE | NDA-gated, Snapdragon-only, INT8-only, dead end |

### Why `llama-android` Instead of Building From Scratch

The reviewer called this out correctly. The `llama.cpp` repo now ships `examples/llama-android/` — a proper Android library module with:

- CMake integration via Android NDK
- Pre-built Vulkan GPU offload
- Streaming callback JNI bridge
- Model download + loading helpers

**Our approach:** Fork/extend `llama-android`, don't start from a `make -j$(nproc)` bash script. Saves 1-2 weeks of NDK debugging.

### On-Device Model Strategy

The intent classifier model **must stay resident in RAM** — you cannot load/unload per request (cold load is 2-5 seconds, not 500ms).

| Model | Purpose | Quant | RAM | Resident? |
|---|---|---|---|---|
| `Qwen2.5-3B-Q4_K_M` | Intent classifier + simple tasks | Q4_K_M | ~1.8GB | ✅ Always loaded |
| `gte-small` (or similar) | Text embeddings | Q8_0 | ~200MB | ⏸️ Load on-demand |
| `Qwen2.5-7B-Q4_K_M` | Complex reasoning (optional) | Q4_K_M | ~3.5GB | ❌ Load when user initiates complex task |

**Total baseline RAM with 3B + embeddings:** ~2GB. On an 8GB device with ~3-4GB OS overhead, you have ~2-3GB headroom for other apps. **On a 6GB device, expect aggressive app killing by the OS.** Document this clearly in the compatibility matrix.

---

## 7. DEVICE COMPATIBILITY MATRIX

Android fragmentation is the silent killer. Explicit targets from Day 1.

### v1.0 Supported Devices

| Tier | Device Examples | RAM | Expected Performance | Model |
|---|---|---|---|---|
| **Target** | Pixel 9 Pro, Galaxy S24/S25, OnePlus 12 | 12-16GB | Local 3B @ 35-50 tok/s, 7B @ 18-22 tok/s | Qwen2.5-3B + optional 7B |
| **Compatible** | Pixel 8 Pro, Galaxy S23, OnePlus 11 | 8-12GB | Local 3B @ 25-40 tok/s, 7B slow | Qwen2.5-3B only recommended |
| **Minimum** | Pixel 7, Galaxy S22, any 6GB+ | 6-8GB | Local 3B @ 15-25 tok/s (CPU mode), cloud recommended | Gemma 3 2B Q4_K_M |

### Requirements

| Requirement | Minimum | Recommended |
|---|---|---|
| **OS** | Android 10 (API 29) | Android 14+ (API 34) |
| **Architecture** | arm64-v8a only | arm64-v8a |
| **RAM** | 6GB | 8GB+ |
| **Storage** | 500MB free (app + small model) | 3GB free (app + 3B model) |
| **GPU** | Any (CPU fallback) | Vulkan 1.1+ (Adreno 7xx, Mali G7xx, Xclipse) |
| **Play Services** | Optional (for cloud model downloads) | Not required (F-Droid build) |

### Devices NOT Supported in v1.0

| Device Class | Reason |
|---|---|
| x86 Android emulators | llama.cpp JNI requires arm64 |
| 4GB RAM devices | OS + app + model will OOM |
| Android Go / low-RAM devices | AccessibilityService limited, foreground service unreliable |
| Huawei (no GMS) | Cloud provider downloads broken; local mode works |

---

## 8. ONBOARDING & TRUST DESIGN

This is a conversion funnel, not a settings page. An app that requests SMS, notification, and screen access looks terrifying. First impressions are everything.

### Onboarding Flow (Dedicated Phase Spec)

```
SCREEN 1: WELCOME
─────────────────────────────────────────────
"AION — Your private AI phone agent"
[3 screenshots carousel: Chat, Skills, Automation]

CTA: "Get Started" → next
Link: "Open Source. AGPLv3. Privacy-first." → opens GitHub
─────────────────────────────────────────────

SCREEN 2: PRIVACY PROMISE
─────────────────────────────────────────────
"Everything stays on your phone."
  ✅ Your data never leaves this device unless you choose
  ✅ Open source — anyone can verify the code
  ✅ You control which permissions to grant
  ✅ We have no servers. No accounts. No tracking.

"You can use AION with ZERO cloud services."

CTA: "I understand" → next
Link: "Read the privacy white paper" → opens docs/PRIVACY.md
─────────────────────────────────────────────

SCREEN 3: CAPABILITY CHOICE
─────────────────────────────────────────────
"Choose your level of access"

[3 cards, each with radio button]

┌──────────────────────────────────────────┐
│ [●] FULL ACCESS (Recommended)            │
│     AI sees screen, reads notifications, │
│     executes actions across apps         │
│     Requires: Accessibility permissions  │
│     Best for: Power users                │
├──────────────────────────────────────────┤
│ [○] NOTIFICATION ACCESS                 │
│     AI reads notifications, sends SMS,   │
│     places calls. Cannot see screen.     │
│     Requires: Notification permissions    │
│     Best for: Privacy-conscious users     │
├──────────────────────────────────────────┤
│ [○] CHAT ONLY                            │
│     AI answers questions via chat.       │
│     No system access.                    │
│     No special permissions.              │
│     Best for: Trying it out              │
└──────────────────────────────────────────┘

CTA: "Continue" → next (changes based on selection)
─────────────────────────────────────────────

SCREEN 4: PERMISSION GRANT (FULL PATH)
─────────────────────────────────────────────
[Progress indicator: Step 1 of 3 — SMS & Calls]
Request SEND_SMS, RECEIVE_SMS, CALL_PHONE
"Used for: sending messages, placing calls"
CTA: "Grant" → system dialog → next

[Step 2 of 3 — Notifications]
Request NotificationListenerService
"Used for: reading incoming messages, detecting events"
CTA: "Go to Settings" → opens notification access settings
[Detect when enabled] → auto-advance

[Step 3 of 3 — Screen Access]
Request AccessibilityService
"Used for: understanding what's on your screen, performing actions"
CTA: "Go to Settings" → opens accessibility settings
[Detect when enabled] → auto-advance
─────────────────────────────────────────────

SCREEN 5: MODEL SETUP
─────────────────────────────────────────────
"How should AION work?"

[○] Cloud-first (Recommended for setup)
    "Works immediately. Add a local model later."
    CTA: "Enter API Key" → provider list → done

[○] Try it now (no setup)
    "Uses cloud demo endpoint with limited queries."
    CTA: "Start Chatting"

[○] Local model (for privacy)
    "Download 1.8GB model. Works offline."
    CTA: "Download Qwen2.5-3B" → download progress → done

"Don't worry — you can change this later."
─────────────────────────────────────────────

SCREEN 6: FIRST QUESTION
─────────────────────────────────────────────
"You're all set. Try something:"
[Text input with placeholder: "What can I do with my phone?"]

[Suggested prompts:]
  "Send a text to myself with a shopping list"
  "What's on my screen right now?"
  "Read my last 3 notifications"

Agent responds, user sees capability in action → retention
─────────────────────────────────────────────
```

### Conversion Goals

| Screen | Goal | Metric |
|---|---|---|
| Welcome | 100% see it | — |
| Privacy Promise | 90% click through | Drop-off rate |
| Capability Choice | FULL selected >50% | FULL vs PARTIAL ratio |
| Permission Grant | All 3 granted >30% | Step-by-step drop-off |
| Model Setup | Cloud or local >80% | "Try it now" rate |
| First Question | User asks something >70% | Engagement rate |

**Build this onboarding in Phase 1 (as a skeleton) and iterate through Phase 6.** Don't leave it to the end.

---

## 9. MEMORY BUDGET & CONTEXT WINDOW

### RAM Budget (8GB Device Example)

| Component | RAM | Notes |
|---|---|---|
| Android OS + system apps | ~2.5-3.5GB | Varies by OEM (Samsung is heavier than Pixel) |
| AION Foreground Service | ~200MB | Code, state, connections |
| Intent Classifier (3B Q4_K_M) | ~1.8GB | Always resident |
| Tokenizer + Runtime buffers | ~300MB | KV cache, generation buffers |
| Other user apps (avg) | ~1-2GB | Whatever is in recent tasks |
| **Total used** | ~5.5-7.5GB | **Leaves 0.5-2.5GB headroom** |
| **Headroom** | **0.5-2.5GB** | **If low, OS kills AION** |

### On 6GB Devices

OOM is a real risk. Mitigations:
- Use Gemma 3 2B Q4_K_M (~1.1GB) instead of 3B
- Unload intent classifier to disk after 5 min idle (sleep mode)
- Detect low-RAM condition and prompt user to switch to cloud-only

### Context Window Management

A 3B model has 4K-8K context. With conversation history + memory recall + system prompt, you'll hit this limit in 20-30 messages.

**Strategy:**

```
┌─────────────────────────────────────────────┐
│            CONTEXT MANAGER                  │
│                                             │
│  Current conversation (maintains full       │
│  message history up to context limit)       │
│      │                                      │
│      ▼                                      │
│  When context reaches 70% capacity:         │
│      │                                      │
│      ├→ Summarize old messages:             │
│      │   LLM call: "Summarize messages 1-15 │
│      │   in under 200 words"                │
│      │   → Store summary in Room            │
│      │   → Remove old messages from context │
│      │   → Insert summary as system message │
│      │                                      │
│      ├→ Prune low-importance memories:      │
│      │   "What did the user say about       │
│      │    restaurant preferences at message │
│      │    8?" → already stored in Memory    │
│      │   Entity → remove from context       │
│      │                                      │
│      └→ Last resort: truncate oldest 25%    │
│         with a note: "Earlier messages      │
│         were removed to save context space" │
│                                             │
└─────────────────────────────────────────────┘
```

This is built in Phase 3 (Memory System), not an afterthought.

---

## 10. MCP SERVER SECURITY MODEL

The MCP server is the hero feature — flipping the paradigm so external AI tools (Claude Desktop, Cursor, etc.) can call your phone directly. But a WebSocket server on your phone is also an attack surface on every WiFi network you join.

### Threat Model

| Threat | Scenario | Severity |
|---|---|---|
| **Same-network attacker** | Someone on public WiFi scans LAN, finds WebSocket port, calls phone tools | HIGH |
| **Rogue skill injection** | Attacker sends crafted MCP request that executes dangerous skill (send SMS, place call) | HIGH |
| **Eavesdropping** | Attacker listens to unencrypted WebSocket traffic | MEDIUM |
| **Brute-force token** | Attacker enumerates auth tokens | MEDIUM |

### Design

```
┌──────────────────────────────────────────────────────────────┐
│                    MCP SERVER (Ktor WebSocket)                │
│                                                               │
│  Bind: 127.0.0.1:8899 (localhost only, no LAN)               │
│       OR 0.0.0.0:8899 (LAN, user opt-in)                     │
│                                                               │
│  Authentication: Token                                       │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  • Generated on first launch (cryptographically random)│  │
│  │  • Displayed as QR code in settings                     │  │
│  │  • User can rotate at any time                          │  │
│  │  • Rate-limited: 5 attempts/minute per IP               │  │
│  │  • Tokens expire after 24 hours (auto-renew for        │  │
│  │    authorized clients with refresh)                     │  │
│  └────────────────────────────────────────────────────────┘  │
│                                                               │
│  Transport: WSS (WebSocket Secure)                            │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  • Self-signed cert for localhost (generated on device) │  │
│  │  • QR code includes cert fingerprint for verification   │  │
│  │  • LAN mode uses self-signed cert + manual trust        │  │
│  └────────────────────────────────────────────────────────┘  │
│                                                               │
│  Capability Scoping                                           │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  Each MCP client connection specifies requested          │  │
│  │  capability level: { read_only / notification / full }  │  │
│  │                                                         │  │
│  │  User approves on first connect:                        │  │
│  │  "Claude Desktop wants to connect. Access level: FULL   │  │
│  │   (can read screen, send messages, tap buttons)"        │  │
│  │   [Allow] [Allow Read-Only] [Deny]                      │  │
│  │                                                         │  │
│  │  User can revoke at any time from settings               │  │
│  └────────────────────────────────────────────────────────┘  │
│                                                               │
│  Audit Log                                                    │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  Every MCP action logged: {client, tool, params, time,  │  │
│  │  result}                                                  │  │
│  │  User can review audit log in settings                   │  │
│  │  Alert on suspicious patterns (10+ rapid SMS sends etc)  │  │
│  └────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
```

---

## 11. PHASE 1 — CHAT + SMS + CLOUD MODE (Weeks 1-3)

**Goal:** Shippable app that does something useful on Day 1. No local model required. Cloud-first onboarding hooks users before asking for storage commitment.

### Week 1 — Project Scaffolding + Chat UI

- [ ] Initialize Android Studio project (Kotlin + Compose + Hilt + Room)
- [ ] Package structure:
  ```
  com.aion.agent/
    .ui/              — Compose screens
    .core/            — Agent loop, model router, capability gate
    .llm/             — Model backends (LocalLlm, CloudLlm)
    .system/          — Android integrations (AccessibilitySvc, NotifListener, etc.)
    .skills/          — Skill registry, BM25 router, skill interface
    .memory/          — Room DB, vector search, context manager
    .mcp/             — Ktor MCP server
    .data/            — Repositories, Room DAOs
    .onboarding/      — Trust onboarding flow
  ```
- [ ] CI/CD: GitHub Actions (lint + debug APK on push)
- [ ] `.gitignore`, `README.md`, `LICENSE` (AGPLv3), `CONTRIBUTING.md`
- [ ] **Chat UI v1:**
  - Message list (Compose LazyColumn, user + agent bubbles)
  - Input field + send button
  - Markdown rendering (compose-richtext or custom)
  - Typing indicator
  - **Token streaming buffer** (3-5 token batches, 80-150ms window — build this now)
- [ ] Conversation history in Room (MessageEntity + ConversationEntity)

### Week 2 — Foreground Service + Cloud LLM

- [ ] `AgentForegroundService`:
  - Persistent notification ("AION is running" + quick actions)
  - Wake lock management
  - Battery optimization exclusion prompt (guide user to Settings)
  - Crash recovery (AlarmManager watchdog)
  - **Battery impact measurement** (track CPU wake time, report in settings)
- [ ] Register in manifest with `FOREGROUND_SERVICE_TYPE_DATA_SYNC`
- [ ] **Cloud LLM engine:**
  - OpenAI-compatible HTTP client (OkHttp + SSE streaming)
  - Provider wrappers: OpenRouter (primary), OpenAI (secondary)
  - BYO API key configuration UI
  - Token counting + cost estimation display
- [ ] **"Zero model" onboarding path:** User enters API key → agent works immediately → no 1.8GB download needed

### Week 3 — SMS Tool + Permissions

- [ ] Permission declarations in manifest
- [ ] Runtime permission request flow (Compose + Accompanist)
- [ ] `SmsTool` implementing `AgentTool` interface:
  - `send(destination, message)`
  - `readInbox(limit)`
  - `readLastFrom(sender)`
- [ ] BM25 skill router v1 (with single skill: SMS)
- [ ] **Capability Level enum + gate** (FULL/PARTIAL/MINIMAL) — even though only MINIMAL is active, the structure exists
- [ ] Integration test: "Send SMS to Mom saying I'll be home at 8" via chat

**Milestone 1: App launches, user enters API key, chats with LLM, sends SMS via natural language. Runs in background. Battery impact visible in settings.**

---

## 12. PHASE 2 — LOCAL LLM INTEGRATION (Weeks 4-7)

**Goal:** Local inference working. Intent classifier model resident. Sleep mode for battery.

### Week 4-5 — llama.cpp Integration (Using llama-android)

- [ ] Add `llama-android` module from reference project
- [ ] Build NDK integration (Vulkan GPU offload)
- [ ] `LocalLlmEngine`:
  - Load/unload GGUF models
  - Streaming generation via JNI callback → Coroutine Flow
  - Configurable context length, GPU layers, temperature
  - OOM detection + graceful fallback
  - **Token streaming buffer** (3-5 token batch) wired to UI
- [ ] Model download manager:
  - HuggingFace Hub API (list available models, show sizes)
  - Download with progress notification
  - Resume interrupted downloads
  - Storage check before download starts

### Week 6 — Intent Classifier + Model Router

- [ ] Load `Qwen2.5-3B-Q4_K_M` as **resident intent classifier**
  - Loaded at app startup (show progress in onboarding)
  - Stays in memory until user quits or sleep mode triggers
  - Structured output: `{ "intent": "TOOL_CALL", "tool": "sms.send", "params": {...}, "complexity": 0.3 }`
- [ ] `ModelRouter` implementation:
  - Routes based on intent + complexity + user preference + battery state + network
  - User config: "Auto", "Always Local", "Maximum Intelligence", "Battery Saver"
  - Fallback chain: local → edge → cloud → user notification
- [ ] Test routing matrix exhaustively

### Week 7 — Sleep Mode (Battery First-Class)

- [ ] **Sleep mode architecture:**
  - After 5 min of inactivity: unload intent classifier to disk (free ~1.8GB RAM)
  - On new trigger: reload model (~2-3s cold start, acceptable for async task)
  - Active mode: user is chatting, model stays loaded
  - Passive mode: user hasn't interacted but trigger fires → cold load + process
- [ ] Battery status dashboard:
  - "% battery used by AION today"
  - "CPU time: 12 min active, 8 hours background"
  - "Model loaded: 3h 24m"
  - Compare: "AION used 4% of your battery today" (vs screen 35%, etc.)
- [ ] Self-defense against OEM battery killers:
  - Detect MIUI/ColorOS/One UI adaptive battery
  - Guide user to exclusion list
  - Use WorkManager keep-alive as fallback

**Milestone 2: App runs with local intent classifier. Battery impact visible and manageable. Model routing works across local/cloud.**

---

## 13. PHASE 3 — SYSTEM EYES & EARS (Weeks 8-14)

**Goal:** Agent sees notifications, reads screen, has persistent memory.

### Week 8-9 — NotificationListenerService

- [ ] `AgentNotificationListener`:
  - Capture `onNotificationPosted` → extract app, title, text, category, priority
  - Classify: message / alert / spam / system
  - Store in `NotificationEntity` (Room)
  - `onNotificationRemoved` → log
  - `cancelNotification()`, `snoozeNotification()`
- [ ] Notification history UI (timeline view)
- [ ] Per-app notification controls (allow/block summarization per app)
- [ ] Permission flow: Guide user to Settings > Notification Access
- [ ] Notification-based triggers:
  - "When WhatsApp message from Sarah arrives, read it silently"
  - "Auto-dismiss spam notifications"

### Week 10-12 — AccessibilityService

- [ ] `AgentAccessibilityService`:
  - `getRootInActiveWindow()` → traverse `AccessibilityNodeInfo` tree
  - Extract: visible text, button labels, checkboxes, scroll position
  - Return structured JSON for LLM consumption
  - `dispatchGesture()` for taps, swipes, scrolls
  - `performGlobalAction()` for back/home/recents
- [ ] UI hierarchy → token-efficient JSON converter:
  - Skip invisible elements, truncate long texts, deduplicate identical adjacent entries
  - Map element bounds to action IDs
- [ ] Screen change monitoring:
  - Register for window state/content change events
  - Debounce rapid events (50ms window)
  - Emit screen snapshots to agent loop
- [ ] Handle Android 14+ timeout:
  - Cache last known screen state
  - Persistent notification with "reconnect" button
  - Auto-reconnect on next user interaction trigger
- [ ] **Capability Gate integration:**
  - If AccessibilityService not enabled, `getAgentCapability()` returns `PARTIAL`
  - All screen-related UI elements greyed with explanation
  - "Enable Screen Access" button opens settings

### Week 13-14 — Memory System + Context Manager

- [ ] Room database schema finalised:
  - `ConversationEntity`
  - `MessageEntity`
  - `NotificationEntity`
  - `MemoryEntity` (persistent facts: key-value pairs with categories + importance scores)
  - `ContextSummaryEntity` (LLM-generated conversation summaries for context pruning)
- [ ] Vector search with sqlite-vec:
  - Download `gte-small` as GGUF (on-demand, not always resident)
  - Index conversations, notifications, memories
  - Semantic recall: "What did Sarah say about the party?"
  - **Fallback:** Brute-force cosine similarity for <5000 items (avoids sqlite-vec dependency risk)
- [ ] **Context window manager:**
  - Monitor context usage per conversation
  - At 70% capacity: trigger summarization
  - Summarize oldest messages, insert into context, prune originals
  - Store summaries in `ContextSummaryEntity`
- [ ] Knowledge base (user profile, learned patterns, explicitly saved facts)
- [ ] Forgetting mechanism (LRU eviction, importance-based pruning)

**Milestone 3: Agent reads notifications, sees screen, has persistent memory with semantic recall. Context stays within model limit.**

---

## 14. PHASE 4 — SKILL SYSTEM & AUTONOMY (Weeks 15-21)

**Goal:** Extensible skills, user-creatable automations, autonomous triggers.

### Week 15-17 — Skill Engine

- [ ] `AgentSkill` interface finalized:
  ```kotlin
  interface AgentSkill {
      val id: String
      val name: String
      val description: String
      val keywords: List<String>
      val permissions: List<String>
      val capabilityLevel: AgentCapability  // FULL, PARTIAL, or MINIMAL
      
      suspend fun canHandle(input: String): Float
      suspend fun execute(context: AgentContext, params: Map<String, Any>): SkillResult
  }
  ```
- [ ] BM25 skill router:
  - Index all installed skill descriptions + keywords
  - Rank by relevance on each user input
  - Return top-3 with scores
  - Threshold: 0.3 minimum, below that → general LLM
- [ ] Built-in skill set:
  - `SmsSkill` (FULL/PARTIAL)
  - `CallSkill` (FULL/PARTIAL)
  - `NotificationSkill` (FULL/PARTIAL)
  - `ScreenSkill` (FULL only — grey below that)
  - `ClipboardSkill` (FULL)
  - `TimerSkill` (MINIMAL)
  - `CalendarSkill` (FULL/PARTIAL)
  - `ContactsSkill` (MINIMAL)
  - `WebSearchSkill` (MINIMAL via browser intent)
- [ ] Capability-gated skill execution:
  - If skill requires FULL and device is PARTIAL, return clear error: "This skill requires screen access. Enable in Settings > Accessibility."
  - Skills masked from BM25 index if their requirement isn't met

### Week 18-19 — User-Creatable Skills

- [ ] YAML skill format (with branching):
  ```yaml
  id: morning-briefing
  name: Morning Briefing
  description: Reads calendar events and notifications aloud
  keywords: [morning, briefing, summary, today]
  triggers:
    - time: "07:00"
    - phrase: "good morning"
  permissions: [calendar:read, notifications:read]
  capability: PARTIAL
  steps:
    - tool: calendar.readEvents
      params: { date: "today", limit: 5 }
      result: events
    - if: events.length == 0
      then:
        - tool: system.speak
          params: { text: "Good morning! No events today." }
      else:
        - tool: system.speak
          params: { text: "Good morning! You have {events.length} events." }
    - tool: notifications.summarize
      params: { since: "last_dismissed", max: 3 }
  ```
  - Supports: variables, `if/else`, `result` binding
  - Jinja-style templating for string interpolation
- [ ] Skill editor (in-app or guide to external editor)
- [ ] Sandboxed execution (restricted coroutine scope, max 30s, permission manifest, no implicit network)
- [ ] Validation: YAML parse → permission check → capability level check → circular dependency

### Week 20-21 — Autonomous Triggers

- [ ] Trigger engine:
  - **Time:** WorkManager periodic tasks
  - **Event:** NotificationListener callbacks
  - **Phrase:** Chat message contains trigger phrase
  - **State:** AccessibilityService detects "app opened" events
- [ ] Trigger registration UI
- [ ] Debouncing (prevent double-fire)
- [ ] Passive pattern learning:
  - "I notice you open Spotify when Bluetooth connects. Automate it?"
  - Store learned patterns in memory, present as suggestion

**Milestone 4: Skills work across all capability levels. Users can create automations. Agent runs autonomously on triggers.**

---

## 15. PHASE 5 — MCP SERVER & ECOSYSTEM LAUNCH (Weeks 22-26)

**Goal:** External AI tools connect to phone. This is the Product Hunt moment.

### Week 22-24 — On-Device MCP Server

- [ ] Ktor WebSocket server (embedded, runs in-process)
- [ ] MCP protocol v2025-03-26:
  - `initialize` — capability negotiation, auth
  - `tools/list` — expose installed skills as MCP tools (filtered by capability level)
  - `tools/call` — invoke skill with params
  - `resources/list` — device state (notifications count, last screen, sensor data)
  - `resources/read` — read a resource
- [ ] **Security (from Section 10):**
  - Token-based auth with QR code display
  - Self-signed cert generation for WSS
  - Rate limiting (5 attempts/min/IP)
  - Token rotation (24h expiry)
  - Client approval dialog on first connect
  - Capability scoping per client connection
  - Full audit log
- [ ] Dual-mode binding:
  - Localhost only (127.0.0.1:8899) — default, for apps on the same device
  - LAN (0.0.0.0:8899) — opt-in, for desktop tools
- [ ] MCP client mode (connect to external MCP servers):
  - User adds custom MCP server URL + token
  - External skills appear alongside local skills in BM25 router
  - Capability mapping: external skills are `MINIMAL` by default (no phone access)

### Week 25-26 — Launch Prep

- [ ] Write MCP integration docs: "Connect Claude Desktop / Cursor / Cline to your phone"
- [ ] Build demo video: "Watch me control my phone from Claude Desktop via MCP"
- [ ] GitHub release of MCP server as standalone npm package (optional, for non-Android users)

**Milestone 5: Claude Desktop on your PC can send SMS, read notifications, and control your phone via the on-device MCP server.**

---

## 16. PHASE 6 — POLISH, ONBOARDING & SHIP (Weeks 27-32)

**Goal:** Production app on GitHub Releases + F-Droid.

### Week 27-28 — UI Polish

- [ ] Design system (dark theme + Material 3)
- [ ] **Onboarding flow (from Section 8):** Implement as designed — this is the conversion funnel
- [ ] Agent status indicator (notification bar + home screen widget)
- [ ] Settings redesign (categorized, searchable)
- [ ] Privacy dashboard (what was accessed, when, how many times)
- [ ] Battery dashboard (from Phase 2)

### Week 29-30 — Error Handling

- [ ] OOM protection: RAM check before model load, graceful fallback suggestion
- [ ] Network handling: offline mode, download resume, API key validation
- [ ] Crash recovery: never show blank screen, restart service, offer bug report
- [ ] AccessibilityService failure modes:
  - Disabled by user → capability level drops → UI degrades
  - Timeout (A14+) → notification to re-enable
  - Secure screens (lock screen, password fields) → skip gracefully
- [ ] ProGuard/R8 optimization
- [ ] Split APKs by architecture (arm64-v8a only)

### Week 31-32 — Distribution

- [ ] GitHub Actions release workflow (signed APK + AAB)
- [ ] F-Droid submission (AGPLv3, no proprietary deps, reproducible builds)
- [ ] Play Store build (Lite version: PARTIAL + MINIMAL only, no AccessibilityService)
- [ ] Documentation:
  - `README.md` with screenshots + demo GIF + capability comparison table
  - `docs/INSTALL.md` (Play + F-Droid + sideload)
  - `docs/SKILLS.md` (create, install, permissions)
  - `docs/MCP.md` (connect from desktop tools)
  - `docs/ARCHITECTURE.md` (for contributors)
  - `docs/PRIVACY.md` (data handling, zero-cloud mode, open-source verification)
- [ ] Landing page (GitHub Pages): features, screenshots, download links

**Milestone 6: Production app. Three distribution channels. Growing community.**

---

## 17. v1.1+ ROADMAP

These are **not** in v1.0. Cut to ship faster.

| Feature | Target | Why Not v1.0 |
|---|---|---|
| **Multi-agent protocol** | v1.2 | Scope bomb, zero users on day one |
| **Voice interface** | v1.1 | Adds STT/TTS pipeline complexity |
| **Vision (screenshot analysis)** | v1.1 | Requires multimodal model or cloud vision API |
| **Home Assistant integration** | v1.2 | Niche, adds HA connection complexity |
| **Tasker plugin** | v1.1 | Third-party dependency, can come after launch |
| **Skill marketplace server** | v1.1 | v1.0 ships with git-based + local import |
| **Remote model inference (paid)** | v1.1 | v1.0 uses user's own API key |
| **iOS / KMP version** | v2.0 | iOS APIs are locked down. Android-first, own the space |

---

## 18. MONETIZATION STRATEGY

### Tier Model

| Tier | Price | Capabilities | Distribution |
|---|---|---|---|
| **AION Free (OS)** | $0 | MINIMAL — chat + cloud LLM (BYO key) + 5 skills | Play Store, F-Droid, GitHub |
| **AION Pro** | $5/mo or $50/yr | FULL — all capabilities, all skills, MCP server, sleep mode | Sideload APK from GitHub |
| **AION Lifetime** | $150 one-time | Same as Pro, forever | GitHub |
| **AION Enterprise** | $20/seat/mo | MDM, fleet management, audit logging, custom skills | Direct sales |

### Revenue Streams

1. **Pro subscriptions** — Primary. 5% of 100k users × $50/year = $250k ARR
2. **Skill marketplace commission** — 20% on paid skills (v1.1)
3. **Managed inference credits** — $10/mo for bundled tokens (v1.1)
4. **Enterprise licenses** — Work phone fleets
5. **Consulting** — Custom skill development ($5k-20k/engagement)

**Marginal cost: near zero.** Users bring their own compute (phone NPU) and their own API keys.

### Growth Strategy

- **Months 1-4:** Open source community on GitHub
- **Month 5:** F-Droid listing
- **Month 6:** Launch on Product Hunt + HN — MCP-server-on-phone is the hero moment
- **Month 7+:** Skill marketplace viral loop
- **Month 8+:** Tech press — "This open-source app turns Android into an AI agent"

---

## 19. RISK MATRIX — REVISED

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| **🔴 Google bans AI agents using AccessibilityService** | HIGH | CRITICAL | Capability-level architecture (FULL/PARTIAL/MINIMAL) from Day 1. Play Store gets Lite build. Full build on F-Droid + GitHub. |
| **🟠 Battery drain kills retention** | HIGH | HIGH | Sleep mode (unload model after 5 min idle). Battery dashboard from Phase 1. Background processing limits. |
| **🟠 Android 16 further restricts AccessibilityService** | HIGH | HIGH | Capability-level degradation. ADB-based fallback for rooted/power users. |
| **🟠 OEM firmware kills foreground service** | HIGH | MEDIUM | Guide user to exclusion list. WorkManager keep-alive. Multiple restart strategies. |
| **🟠 Onboarding conversion is terrible (too many permissions)** | HIGH | HIGH | Dedicated conversion funnel design (Section 8). Cloud-first mode skips model download. Capability choice lets users start small. |
| **🟡 llama.cpp Vulkan regressions on specific devices** | MEDIUM | MEDIUM | CPU fallback always available. Test on 3 target devices before release. Error reporting. |
| **🟡 sqlite-vec breaking changes** | LOW | MEDIUM | Pin exact version. Write migration tests. Brute-force fallback for <5k items. |
| **🟡 Solo developer burnout** | MEDIUM | HIGH | Modular architecture — each phase ships as a usable app. Open-source contributions. Realistic timeline (32 weeks, not 150 days). |
| **🟡 On-device models too slow for interactive use** | MEDIUM | LOW | 3B models already achieve 35-55 tok/s on flagships. Cloud fallback for complex tasks. Future NPU will improve. |
| **🟢 User data privacy concerns** | MEDIUM | HIGH | Zero-cloud mode. Open-source. Privacy white paper. Transparent permission model. No tracking. |
| **🟢 Google builds "Gemini Agent for Android"** | HIGH | MEDIUM | We're any-model, open-source, privacy-first. Google will be cloud-locked and Gemini-only. Different positioning. |
| **🟢 MCP server compromised on public WiFi** | MEDIUM | HIGH | Localhost-only by default. LAN is opt-in. WSS + token auth + rate limiting + audit log. |

---

## APPENDIX A: QUICK-START FOR TOMORROW

```bash
# Create the project directory
mkdir -p ~/Aion && cd ~/Aion

# Initialize Android project (manual or `android init`)
# build.gradle.kts with Kotlin 2.1+, Compose, Hilt, Room, Ktor, OkHttp

# First line of code — build.gradle.kts (project-level)
cat > build.gradle.kts << 'EOF'
plugins {
    id("com.android.application") version "8.7.0" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
    id("com.google.dagger.hilt.android") version "2.51.1" apply false
    id("com.google.devtools.ksp") version "2.1.0-1.0.29" apply false
}
EOF

# Build a chat screen that sends "Hello, world" to start
# Then add SMS. Then cloud LLM. Then local LLM.
```

## APPENDIX B: KEY DECISIONS SUMMARY

| Decision | Choice | Rationale |
|---|---|---|
| Automation method | AccessibilityService (with capability gate) | Only on-device path without root/host PC |
| Local LLM backend | llama.cpp via `llama-android` | Largest model ecosystem, Vulkan offload, use existing bindings |
| Cloud LLM protocol | OpenAI-compatible HTTP | Works with every major provider |
| MCP transport | WebSocket (localhost-first, LAN opt-in) | Secure by default, LAN when user chooses |
| Skill routing | BM25 | Fast (10ms), no LLM call needed, benchmarked working |
| Memory | Room + sqlite-vec + brute-force fallback | Native SQLite, on-device vector search, fallback de-risks dependency |
| On-device model range | 3B Q4_K_M (resident) + optional 7B | Sweet spot for speed/quality on current flagships |
| Distribution | GitHub + F-Droid primary, Play Store Lite | Play Store won't accept AccessibilityService for agent use |
| License | AGPLv3 | Protects against closed-source exploitation, commercial licenses available |
| v1.0 scope | Ship without multi-agent, vision, voice | Cut scope to ship in 8 months |

---

## APPENDIX C: FILES CREATED DURING RESEARCH

| File | Content |
|---|---|
| `~/android_ai_agent_research.md` | 85+ projects analyzed, competitive gaps, key decisions |
| `~/android-ai-agent-apis.md` | AOSP source-verified API capabilities, permission matrix |
| `~/.hermes/plans/android-local-llm-research.md` | 7 backend benchmarks, model recommendations, RAM budgets |
| `/storage/emulated/0/Download/PLAN.md` | This document — v2.0 with all review feedback incorporated |

---

**Ready when you are, Sir. Say the word and I'll write Phase 1 implementation specs — exact Gradle files, the first Activity, Foreground Service skeleton, SmsTool interface — ready to open in Android Studio and compile.**
