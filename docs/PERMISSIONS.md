# AION Permissions Guide

## Phase 1 (declared in AndroidManifest.xml)

| Permission | Why | When requested |
|---|---|---|
| `INTERNET` | Cloud LLM calls (OpenRouter, Opencode Go, NVIDIA NIM) | Manifest — always on (no runtime prompt) |
| `ACCESS_NETWORK_STATE` | Detect connectivity before cloud calls | Manifest — always on |
| `FOREGROUND_SERVICE` | Keep agent process alive | Manifest — always on |
| `FOREGROUND_SERVICE_DATA_SYNC` | Service type declaration (Android 14+) | Manifest — always on |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Guide user to exclude AION from battery killing | First launch — shown as dialog |
| `POST_NOTIFICATIONS` | Foreground service persistent notification | First launch (Android 13+) |
| `WAKE_LOCK` | Prevent device sleep during active tasks | Manifest — always on |
| `SEND_SMS` | Send messages via the SMS skill | First time user invokes SMS |
| `READ_SMS` | Read message history (Phase 3) | In-context when SMS skill used |
| `RECEIVE_SMS` | Detect incoming SMS (Phase 3 triggers) | In-context |

## Phase 2+

| Permission | Why | When |
|---|---|---|
| `READ_CONTACTS` | Contact-aware SMS/Phone skill | First time contacts skill used |
| `CALL_PHONE` | Place calls via the Call skill | First time call skill used |
| `READ_CALL_LOG` | Read call history | In-context when call skill used |

## Backend permissions (not runtime)

| Service | How to enable |
|---|---|
| `NotificationListenerService` | Settings > Apps > AION > Notification Access |
| `AccessibilityService` | Settings > Accessibility > AION |
