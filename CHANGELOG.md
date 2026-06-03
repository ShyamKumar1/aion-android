# Changelog

All notable changes to AION are documented here.
Format: https://keepachangelog.com/en/1.1.0/

## [Unreleased]

### Added
- Initial project scaffold (Phase 1, Week 1)
- Cloud LLM integration (OpenRouter, Opencode Go, NVIDIA NIM)
- Foreground service with persistent notification
- SMS tool + capability gate (FULL/PARTIAL/MINIMAL)
- BM25 skill router with single built-in skill (SMS)
- Onboarding skeleton (Welcome, Capability, Model Setup)
- Settings screen with provider configuration
- CI pipeline (ktlint, unit tests, debug APK build)
- Memory vault (Room) with conversation persistence
- Master implementation plan in `.hermes/plans/aion-master.md`

### Known limitations
- Phase 2 (local LLM) not yet integrated — LocalLlmEngine is a stub
- Phase 3 (NotificationListener + AccessibilityService) not yet implemented
- Phase 4 (YAML skills, triggers) not yet implemented
- Phase 5 (MCP server) not yet implemented
- Tested on emulator only — real-device testing pending Nothing Phone 2 connect
