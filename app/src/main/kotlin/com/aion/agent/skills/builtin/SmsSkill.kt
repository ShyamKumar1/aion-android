package com.aion.agent.skills.builtin

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import com.aion.agent.core.AgentCapability
import com.aion.agent.core.AionException
import com.aion.agent.skills.AgentSkill
import com.aion.agent.skills.SkillDefinition
import com.aion.agent.skills.SkillParameter
import com.aion.agent.skills.SkillResult
import com.aion.agent.util.AionLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Built-in skill for sending SMS. Per AION_GUIDELINES §13 and AION_PLAN §11
 * (Phase 1 Week 3), the skill:
 *  - Requires [AgentCapability.PARTIAL] or above (SMS permission is a runtime perm)
 *  - Returns [SkillResult.ConfirmationRequired] for every send — the user must
 *    confirm the destination and message before the SMS actually goes out
 *  - Verifies the SEND_SMS permission before claiming success
 */
@Singleton
class SmsSkill @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: AionLogger,
) : AgentSkill {

    override val definition: SkillDefinition = SkillDefinition(
        id = "sms.send",
        name = "Send SMS",
        description = "Sends an SMS text message to a phone number. " +
            "Use when the user asks to text, message, or SMS a person.",
        keywords = listOf(
            "sms", "text", "message", "send", "tell", "ping", "msg",
            "text message", "send text", "send a message", "whatsapp",
        ),
        parameters = listOf(
            SkillParameter(
                name = "to",
                description = "Destination phone number, in E.164 or local format",
                jsonType = "string",
                required = true,
            ),
            SkillParameter(
                name = "body",
                description = "The text content of the message",
                jsonType = "string",
                required = true,
            ),
        ),
        requiredPermissions = listOf("android.permission.SEND_SMS"),
        requiredCapability = AgentCapability.PARTIAL,
    )

    override fun canHandle(input: String): Float {
        val lower = input.lowercase()
        val hits = listOf("sms", "text", "message", "msg", "send to", "tell ")
            .count { lower.contains(it) }
        return if (hits == 0) 0f else hits * 0.3f
    }

    override suspend fun execute(params: Map<String, String>): SkillResult {
        val to = params["to"]?.trim().orEmpty()
        val body = params["body"]?.trim().orEmpty()
        if (to.isBlank() || body.isBlank()) {
            return SkillResult.Failure(
                reason = "Missing 'to' or 'body' parameter",
                summary = "Couldn't send — need a phone number and message.",
            )
        }
        if (context.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            return SkillResult.Failure(
                reason = "SEND_SMS permission not granted",
                summary = "I need SMS permission to send that. Enable it in Settings.",
            )
        }

        // Per AION_GUIDELINES N1, mutating actions require user confirmation.
        // Return a ConfirmationRequired result; the UI shows a confirm card.
        // The actual send happens when the user taps "Confirm" — that's wired
        // up in ChatViewModel.handleConfirmation.
        return SkillResult.ConfirmationRequired(
            prompt = "Send SMS to $to: \"${body.take(80)}${if (body.length > 80) "…" else ""}\"?",
            summary = "Awaiting confirmation",
        )
    }

    /**
     * Actually perform the SMS send. Called by ChatViewModel after the user
     * confirms. This is the only place that touches [SmsManager].
     */
    suspend fun sendNow(to: String, body: String): SkillResult = withContext(Dispatchers.IO) {
        try {
            val smsManager = context.getSystemService(SmsManager::class.java)
                ?: return@withContext SkillResult.Failure(
                    reason = "SmsManager unavailable",
                    summary = "This device doesn't support SMS.",
                )
            val parts = smsManager.divideMessage(body)
            if (parts.size == 1) {
                smsManager.sendTextMessage(to, null, body, null, null)
            } else {
                smsManager.sendMultipartTextMessage(to, null, parts, null, null)
            }
            logger.i("SmsSkill") { "SMS dispatched to ${to.take(4)}***" }
            SkillResult.Success(
                output = "Sent to $to",
                summary = "Sent SMS to $to",
            )
        } catch (t: Throwable) {
            logger.e("SmsSkill", t) { "SMS send failed" }
            SkillResult.Failure(
                reason = t.message ?: "Unknown error",
                summary = "Couldn't send: ${t.message ?: "unknown error"}",
            )
        }
    }
}
