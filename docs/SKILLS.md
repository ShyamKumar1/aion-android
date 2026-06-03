# AION Skills System

> **Status:** Phase 1 (chat + cloud LLM + SMS). YAML skill authoring (Phase 4) and
> autonomous triggers are scaffolded but not yet available at runtime.
> Built-in skills are functional in Phase 1.

## Overview

AION's skill system lets the AI agent interact with your Android device. Skills
are registered at startup, routed via BM25 keyword matching, and executed in a
sandboxed coroutine scope with a 30-second timeout. Each skill declares exactly
what permissions and capability tier it requires — nothing more.

---

## Built-in Skills

AION ships with 9 built-in skills. Each is a Kotlin class implementing the
`AgentSkill` interface. Skills are registered in `SkillRegistry` at startup and
exposed to the LLM as tool definitions.

| ID | Name | Capability | Description |
|---|---|---|---|
| `sms.send` | Send SMS | PARTIAL | Sends an SMS text message to a phone number. Requires user confirmation before sending. |
| `call.make` | Make Call | PARTIAL | Places a phone call via `ACTION_CALL`. Requires user confirmation. |
| `notification.read` | Read Notifications | PARTIAL | Reads recent device notifications from the local Room database. |
| `calendar.read` | Read Calendar | PARTIAL | Queries the device calendar for upcoming events on a given date. |
| `contacts.find` | Find Contact | MINIMAL | Searches the device address book for a contact by name, returning phone numbers. |
| `timer.set` | Set Timer | MINIMAL | Sets a timer for N minutes (Phase 1 stub — real WorkManager scheduling lands in Phase 2). |
| `clipboard.manage` | Clipboard | MINIMAL | Reads from or writes to the system clipboard. Writes require user confirmation. |
| `web.search` | Web Search | MINIMAL | Opens a web search in the device browser. Requires user confirmation before launching the browser. |
| `screen.read` | Read Screen | FULL | Reads the text currently visible on screen via AccessibilityService. Stub in Phase 1 — handled continuously by the agent loop when FULL capability is active. |

### Capability Tiers

| Tier | Label | Required Service | Available Skills |
|---|---|---|---|
| `MINIMAL` | Chat Only | None | `contacts.find`, `timer.set`, `clipboard.manage`, `web.search` |
| `PARTIAL` | Notification Access | `NotificationListenerService` | All MINIMAL + `sms.send`, `call.make`, `notification.read`, `calendar.read` |
| `FULL` | Full Access | `AccessibilityService` | All skills including `screen.read` |

The single source of truth for the current tier is `CapabilityManager`, which
emits a `StateFlow<AgentCapability>`. Every feature that depends on a tier
collects from this flow — no component makes its own assessment.

---

## BM25 Skill Router

When the user sends a message, the `Bm25Router` scores all available skills
(those whose `requiredCapability` is met) against the input text. It tokenizes
both the query and each skill's `keywords + name + description`, then applies
BM25 ranking with fixed parameters (`k1 = 1.2`, `b = 0.75`, `avgDocLen = 20`).

### Routing Logic

```
User input
    ↓
Bm25Router.rank(skills, input)
    ↓
Score > 0.35 AND clear of runner-up by > 0.05?
    ├── Yes → Route to that skill (with user confirmation if mutating)
    └── No  → Fall through to general LLM chat (no tool invoked)
```

- **Confidence threshold:** 0.35. Below this, the input is considered ambiguous
  or unrelated to any skill — the LLM handles it as plain chat.
- **Ambiguity margin:** 0.05. If the top two skills score within 0.05 of each
  other, the router declines to auto-route and the LLM handles the request.
  (Future: present both as suggestions to the user.)
- **Performance requirement:** Must complete in under 15ms. The current
  O(n) scan against 10–20 skills satisfies this easily.

### BM25 Parameters

```kotlin
const val K1 = 1.2f             // Term saturation factor
const val B = 0.75f             // Length normalization
const val AVG_DOC_LEN = 20f     // Average document length in tokens
const val DEFAULT_THRESHOLD = 0.35f
const val DEFAULT_AMBIGUITY_MARGIN = 0.05f
```

Stop words (a, an, and, are, as, at, be, by, do, for, from, has, have, i, in,
is, it, me, my, of, on, or, please, that, the, this, to, with, you, your) are
filtered out before scoring.

---

## YAML Skill Format

> **Availability:** Phase 4 (not yet available at runtime). The
> `SkillScriptEngine` is implemented and unit-tested; the UI for importing and
> managing YAML skills lands in Phase 4.

User-created skills are defined in YAML format. Each file represents one skill
with metadata, triggers, and a series of steps.

### Full Example

```yaml
id: morning-briefing
name: Morning Briefing
version: 1.0.0
description: >
  Reads calendar events and recent notifications aloud every morning.
  Activates at 7 AM or when the user says "good morning."
keywords:
  - morning
  - briefing
  - summary
  - today
  - good morning
required_permissions:
  - calendar:read
  - notifications:read
required_capability: PARTIAL
triggers:
  - type: time
    value: "07:00"
  - type: phrase
    value: "good morning"
steps:
  - id: events
    tool: calendar.readEvents
    params:
      date: today
      limit: 5
    on_error: continue
  - id: brief
    tool: system.speak
    params:
      text: >
        Good morning! You have
        {{ steps.events.result.length }} events today.
    on_error: stop
```

### Field Reference

| Field | Required | Type | Description |
|---|---|---|---|
| `id` | Yes | string | Unique, immutable kebab-case identifier. Changing it creates a new skill. |
| `name` | Yes | string | Human-readable display name shown in the UI. |
| `version` | No | string | Semver version. Defaults to `"1.0.0"`. |
| `description` | Yes | string | 1–3 sentences explaining what the skill does and when it activates. |
| `keywords` | Yes | list of strings | 5–20 terms used by BM25 for input matching. |
| `required_permissions` | No | list of strings | `android.permission.*` names the skill needs. |
| `required_capability` | No | string | One of `MINIMAL`, `PARTIAL`, `FULL`. Defaults to `MINIMAL`. |
| `triggers` | No | list of triggers | Conditions for autonomous execution (see below). |
| `steps` | Yes | list of steps | 1–20 steps executed in sequence (see below). |

### Triggers

| Type | Value Example | Description |
|---|---|---|
| `time` | `"07:00"` | WorkManager periodic task at a specific time. |
| `phrase` | `"good morning"` | Chat message contains the trigger phrase (case-insensitive). |
| `event` | `"whatsapp_message"` | NotificationListener callback matches the event. |
| `state` | `"app.opened.spotify"` | AccessibilityService detects a specific app opened. |

### Steps

| Field | Required | Type | Description |
|---|---|---|---|
| `id` | No | string | Step identifier for variable binding. |
| `tool` | Yes | string | Must map to a registered `AgentSkill.id` or built-in tool name. |
| `params` | No | map | Key-value parameters passed to the tool. |
| `on_error` | No | string | `"stop"` (default, fails the skill), `"continue"` (skip step), or `"retry(n)"` (retry N times). |

### Template Expressions

Step parameters support `{{ }}` template expressions:

| Expression | Resolves to |
|---|---|
| `{{ steps.<id>.result }}` | Output of a previous step |
| `{{ steps.<id>.summary }}` | Summary of a previous step |
| `{{ context.date }}` | Current date |
| `{{ context.time }}` | Current time |

Maximum 20 steps per skill. Skills requiring more should be refactored into
sub-skills.

---

## Sandboxed Execution

Every skill — built-in or YAML-authored — executes in a restricted environment:

- **Timeout:** 30 seconds maximum. After 30s, the coroutine is cancelled and
  `SkillResult.Timeout` is returned.
- **Permissions:** Skills cannot access Android system services not declared in
  their `requiredPermissions` list. Violations throw `PermissionDeniedException`
  at runtime.
- **Scope:** Skills run in a restricted coroutine scope and cannot cancel the
  parent agent loop.
- **Network:** Skills must declare `"network"` in their `requiredPermissions` to
  make network calls. YAML skills cannot make implicit network requests.
- **SDK Access:** Skills cannot use Android framework types directly. All system
  access goes through the declared skill interface.

---

## Capability Gating

Skills are masked from the BM25 index and the tool list when their
`requiredCapability` exceeds the current device tier:

```kotlin
fun availableAt(tier: AgentCapability): List<AgentSkill> =
    skills.filter { it.definition.requiredCapability.ordinal <= tier.ordinal }
```

- A `PARTIAL`-tier device never sees FULL-only skills in routing or tool lists.
- MCP server connections respect the same gating — a connected client cannot
  invoke a skill above its session's capability scope.
- The UI hides or disables features above the current tier with a clear
  explanation: "Requires Full Access — enable AccessibilityService in Settings."

---

## Automations & Triggers

> **Availability:** Phase 4 (scaffolded, not yet functional at runtime).

The `TriggerEngine` watches for conditions and executes associated skills
autonomously:

- **Time triggers:** WorkManager periodic tasks fire at scheduled times.
- **Phrase triggers:** Chat messages containing a trigger phrase invoke a skill.
- **Event triggers:** NotificationListener callbacks (incoming message, app
  installed) fire associated skills.
- **State triggers:** AccessibilityService detects app-opened events.

### Debouncing

Triggers are debounced at 30 seconds — the same trigger cannot fire twice within
that window regardless of how many times the condition is met.

### Example Automation Skills

**After-work briefing:**
```yaml
id: after-work-summary
name: After-Work Summary
description: Reads notifications and calendar events at 5 PM on weekdays.
keywords: [summary, end of day, 5pm, work end]
required_capability: PARTIAL
triggers:
  - type: time
    value: "17:00"
steps:
  - id: calendar
    tool: calendar.read
    params:
      date: today
      limit: 10
    on_error: continue
  - id: notifications
    tool: notification.read
    params:
      limit: 5
    on_error: continue
```

**Battery saver (state trigger):**
```yaml
id: battery-saver
name: Battery Saver
description: Enables battery saver when battery drops below 20%.
keywords: [battery, low battery, power save]
required_capability: MINIMAL
triggers:
  - type: state
    value: "battery.low"
steps:
  - id: alert
    tool: system.speak
    params:
      text: "Battery is low. Would you like to enable battery saver?"
```

---

## Execution Result Types

Every skill returns one of four sealed `SkillResult` types:

| Type | Meaning | User sees |
|---|---|---|
| `Success` | Completed as intended | The output/summary |
| `ConfirmationRequired` | Action needs user approval | Confirmation dialog with prompt |
| `Failure` | Operation failed | Error message with reason |
| `Timeout` | Exceeded 30s limit | "Skill took too long" |

Per AION guideline **N1**, every mutating action (send SMS, place call, dismiss
notification, write to clipboard) must return `ConfirmationRequired` rather than
acting directly. The user taps "Confirm" before the action executes.

---

## Creating a Skill

### Prerequisites
- AION with the capability tier required by your skill (MINIMAL/PARTIAL/FULL)
- For YAML skills: a text editor and access to Phase 4's skill import UI

### Steps
1. Write a YAML file following the format above.
2. Validate: all `tool` references must map to registered skill IDs.
3. Import the skill through the Skills screen (Phase 4).
4. The BM25 index is rebuilt automatically on import.
5. Test the skill via chat or trigger.

### Security Rules for Third-Party Skills

- Skills from the Skill Marketplace show a permission review screen before
  installation. The user explicitly approves each declared permission.
- Skills from GitHub are treated as untrusted until verified (GPG-signed by a
  known developer key). Unverified skills show a yellow "Unverified" badge and
  an additional confirmation dialog on execution.
- Skills are always sandboxed — they cannot access capabilities not declared
  in their manifest.

---

## Skill Execution Flow

```
User input or trigger event
    ↓
Bm25Router.rank() scores all available skills
    ↓
Top match > 0.35 and unambiguous (> 0.05 gap)?
    ├── Yes → Skill matched
    │           ↓
    │     requiredCapability met?
    │       ├── No  → Return error: "Requires [tier]"
    │       └── Yes →
    │               requiredPermissions granted?
    │                 ├── No  → Trigger permission request flow
    │                 └── Yes →
    │                         Action is mutating?
    │                           ├── Yes → Return ConfirmationRequired
    │                           └── No  → Execute skill
    │                                          ↓
    │                                   Return Success/Failure
    │
    └── No  → Pass input to LLM for general chat
```

---

## Appendix: SkillInterface (Kotlin)

```kotlin
data class SkillDefinition(
    val id: String,                    // kebab-case unique ID
    val name: String,                  // human-readable name
    val description: String,           // LLM-facing description
    val keywords: List<String>,        // BM25 keywords, 5-20
    val parameters: List<SkillParameter> = emptyList(),
    val requiredPermissions: List<String> = emptyList(),
    val requiredCapability: AgentCapability = AgentCapability.MINIMAL,
    val version: String = "1.0.0",
)

data class SkillParameter(
    val name: String,
    val description: String,
    val jsonType: String = "string",
    val required: Boolean = true,
    val enum: List<String> = emptyList(),
)

interface AgentSkill {
    val definition: SkillDefinition
    suspend fun execute(params: Map<String, String>): SkillResult
}
```
