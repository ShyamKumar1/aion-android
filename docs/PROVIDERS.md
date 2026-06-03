# AION Cloud Providers

AION supports every OpenAI-compatible chat completions API. Phase 1 ships with
three pre-configured providers.

## OpenRouter (recommended)

- **Models:** GPT-4o mini, Claude 3.5 Sonnet, Gemini 2.0 Flash, Llama 3.3 70B, and 200+ more
- **API key:** Get at https://openrouter.ai/keys (requires free account)
- **Cost:** Pay-per-use. GPT-4o mini ~$0.15/1M tokens. Most open models have free tiers.
- **Tool calling:** Full support

## Opencode Go

- **Models:** MiniMax M3, DeepSeek V4 Flash
- **API key:** Get at https://opencode.ai
- **Cost:** Free tier available
- **Tool calling:** Supported

## NVIDIA NIM

- **Models:** Llama 3.1 70B (NIM), Llama 3.1 8B (NIM), Nemotron 4 340B
- **API key:** Get at https://build.nvidia.com (free tier) or use a self-hosted NIM
- **Cost:** Free tier at build.nvidia.com; self-hosted NIM is free to deploy
- **Tool calling:** Supported

> **Note:** All providers are OpenAI-compatible. AION uses a single
> `CloudLlmEngine` that swaps the `baseUrl` and `apiKeyHeader` per provider.
> Adding a new provider is a 5-line addition to `llm/providers/LlmProviderRegistry.kt`.

## How to add a custom provider

1. Open `LlmProviderRegistry.kt`
2. Add a new `ProviderConfig` entry with the base URL, header, and models
3. The provider is instantly visible in the Settings screen

The provider must support `POST /v1/chat/completions` with SSE streaming
(`stream: true`).
