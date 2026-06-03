package com.aion.agent.mcp

import android.content.Context
import android.util.Base64
import com.aion.agent.util.AionLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages MCP authentication tokens and rate limiting.
 *
 * Per AION_PLAN §10 (Security):
 *  - Token: 32 bytes, cryptographically random, base64url-encoded
 *  - Generated on first launch, stored in SharedPreferences
 *  - Rate limited: 5 attempts/min/IP
 *  - Constant-time comparison to prevent timing attacks
 */
@Singleton
class McpAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: AionLogger,
) {
    private val prefs = context.getSharedPreferences("mcp_auth", Context.MODE_PRIVATE)
    private val rateLimitCounts = ConcurrentHashMap<String, RateLimitEntry>()
    private val random = SecureRandom()

    /** Get the current token, generating one if it doesn't exist. */
    fun getToken(): String {
        val existing = prefs.getString(KEY_TOKEN, null)
        if (existing != null) return existing
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        val token = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        prefs.edit().putString(KEY_TOKEN, token).apply()
        return token
    }

    /** Rotate the token (invalidating the old one). */
    fun rotateToken(): String {
        prefs.edit().remove(KEY_TOKEN).apply()
        return getToken()
    }

    /**
     * Validate a token with rate limiting.
     * @return true if valid and within rate limit.
     */
    fun validateToken(token: String, clientIp: String): Boolean {
        // Rate limit check
        val now = System.currentTimeMillis()
        val entry = rateLimitCounts.getOrPut(clientIp) { RateLimitEntry(0, now) }
        if (now - entry.windowStart > 60_000) {
            entry.count = 1
            entry.windowStart = now
        } else {
            entry.count++
            if (entry.count > MAX_ATTEMPTS_PER_MINUTE) {
                logger.w(TAG) { "Rate limit exceeded for $clientIp" }
                return false
            }
        }
        val validToken = prefs.getString(KEY_TOKEN, null) ?: return false
        return constantTimeEquals(token, validToken)
    }

    /** Generate a short client ID for tracking connections. */
    fun generateClientId(): String {
        val bytes = ByteArray(6)
        random.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) result = result or (a[i].code xor b[i].code)
        return result == 0
    }

    private data class RateLimitEntry(var count: Int, var windowStart: Long)

    companion object {
        private const val TAG = "McpAuth"
        private const val KEY_TOKEN = "mcp_auth_token"
        private const val MAX_ATTEMPTS_PER_MINUTE = 5
    }
}
