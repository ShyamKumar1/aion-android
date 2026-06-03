package com.aion.agent.core

/**
 * Sealed hierarchy of domain-level errors thrown by AION. Per AION_GUIDELINES §8,
 * cross-layer operations return [Result] with one of these as the failure type —
 * never a raw [Exception] or [Throwable].
 *
 * Every subclass must include enough context to surface a user-actionable message
 * in the UI without leaking internal types.
 */
sealed class AionException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    class ModelNotLoadedException(model: String) : AionException("Model not loaded: $model")

    class InsufficientRamException(
        requiredBytes: Long,
        availableBytes: Long,
    ) : AionException("Insufficient RAM. Need ${requiredBytes / 1_000_000}MB, have ${availableBytes / 1_000_000}MB.")

    class PermissionDeniedException(permission: String) : AionException("Permission denied: $permission")

    class ToolExecutionException(
        tool: String,
        reason: String,
        cause: Throwable? = null,
    ) : AionException("Tool $tool failed: $reason", cause)

    class ContextLimitExceededException(
        tokens: Int,
        limit: Int,
    ) : AionException("Context $tokens tokens exceeds limit $limit.")

    class NetworkUnavailableException : AionException("No network available")

    class ProviderAuthException(provider: String) : AionException("Authentication failed for $provider")

    class ProviderRateLimitException(provider: String) : AionException("$provider rate limit hit")

    class ProviderHttpException(
        provider: String,
        statusCode: Int,
        body: String? = null,
    ) : AionException("$provider returned HTTP $statusCode${body?.let { ": $it" } ?: ""}")

    class SkillNotFoundException(skillId: String) : AionException("Skill not found: $skillId")

    class SecureWindowException : AionException("Cannot read secure window")

    class CapabilityInsufficientException(
        required: String,
        current: String,
    ) : AionException("Feature requires $required capability, current tier is $current.")

    class InvalidConfigurationException(reason: String) : AionException("Invalid configuration: $reason")
}
