# AION Privacy Policy

**Last updated:** June 2026

## Summary

AION is built on a privacy-first architecture. There are no AION-operated servers.
Your data stays on your device unless you explicitly choose to use a third-party
cloud LLM provider, at which point your message content is sent to that provider
under their privacy policy.

## Data collection

AION does not collect, transmit, or store personal data on servers controlled
by AION. There is no analytics SDK, no telemetry, no crash reporting until the
user explicitly opts in (Phase 6, not yet built).

## Data that stays on your device

- **Conversation history:** stored in the local Room database
- **SMS metadata:** sender phone (for the conversation history), timestamp,
  direction (sent/received), character count. Full SMS body is stored only for
  messages sent through AION.
- **Notification history:** app package, category, text (for summarization),
  action taken. Full notification body text is discarded after processing.
- **Settings and preferences:** stored in DataStore and EncryptedSharedPreferences
- **API keys:** stored in EncryptedSharedPreferences (Android Keystore-backed,
  AES-256-GCM encrypted)

## Data you can export or wipe

Navigate to Settings → Privacy Dashboard (Phase 3, not yet built) to export or
wipe all stored data. Until the dashboard is built, you can clear app data from
the system settings menu.

## Third-party cloud providers

When you configure a cloud LLM provider (OpenRouter, Opencode Go, NVIDIA NIM,
or any other), your message content is sent to that provider's API. AION does
not intermediate, inspect, or log these requests.

Before your first cloud call, a disclosure screen is shown that reads:

> **"Your messages will be sent to [Provider Name]. They are not saved or
> inspected by AION. See [Provider Name]'s privacy policy for how they handle
> your data."**

This disclosure is stored in SharedPreferences once accepted and is not shown
again for the same provider.

## Open source

The complete source code for AION is available in the private repository at
https://github.com/ShyamKumar1/aion-android. You can verify every claim on
this page by reading the source.

## Contact

https://github.com/ShyamKumar1/aion-android/issues
