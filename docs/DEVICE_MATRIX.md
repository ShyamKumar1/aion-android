# AION Device Compatibility Matrix

## Phase 1 development targets

| Device | Status | Background behavior | Notes |
|---|---|---|---|
| Nothing Phone 2 (custom ROM) | Primary test device | TBD | Snapdragon 8+ Gen 1, 12GB RAM |
| Oppo (ColorOS) | Secondary test | Known aggressive battery-killer | Expected FGS restart issues |
| Android emulator (API 34) | CI only | N/A | arm64-v8a image |

## Minimum requirements (v1.0)

| Requirement | Minimum | Recommended |
|---|---|---|
| OS | Android 8.0 (API 26) | Android 14+ (API 34) |
| Architecture | arm64-v8a | arm64-v8a |
| RAM | 4GB | 8GB+ |
| Storage | 500MB free | 3GB free (with 3B GGUF model) |
| GPU | Any (CPU fallback) | Vulkan 1.1+ |
| Play Services | Optional | Not required |

## Not supported (v1.0)

- x86 / x86_64 Android emulators (llama.cpp requires arm64)
- 4GB RAM devices (OS + app + model will OOM)
- Android Go / low-RAM devices
- Huawei without Google Mobile Services (cloud provider downloads broken)

## Known OEM issues

| OEM | Issue | Mitigation |
|---|---|---|
| Samsung (One UI) | Adaptive Battery kills FGS | Guide user to "Unrestricted" battery setting |
| Xiaomi/Redmi (MIUI) | Autostart restriction disables NLS | Guide user to Settings > Apps > Autostart |
| Oppo/OnePlus (ColorOS) | Background FGS aggressive kill | Guide user to "Allow background activity" |
| Any Android 14+ | AccessibilityService 30-min timeout | Re-enable notification (Phase 3) |

## Testing checklist (before each release)

- [ ] Builds on CI (Ubuntu, JDK 21)
- [ ] Fresh install on Nothing Phone 2
- [ ] Chat + cloud LLM round trip
- [ ] SMS send with user confirmation
- [ ] Foreground service persists after app close
- [ ] Settings: switch provider, change model, save/clear API key
