package com.aion.agent.skills.builtin

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.aion.agent.core.AgentCapability
import com.aion.agent.skills.AgentSkill
import com.aion.agent.skills.SkillDefinition
import com.aion.agent.skills.SkillParameter
import com.aion.agent.skills.SkillResult
import com.aion.agent.util.AionLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Built-in skill for placing phone calls. Follows the same pattern as [SmsSkill]:
 *  - Requires [AgentCapability.PARTIAL] or above (CALL_PHONE is a runtime permission)
 *  - Returns [SkillResult.ConfirmationRequired] for every call — the user must
 *    confirm the destination before the call is placed
 *  - Verifies the CALL_PHONE permission before proceeding
 */
@Singleton
class CallSkill @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: AionLogger,
) : AgentSkill {

    override val definition: SkillDefinition = SkillDefinition(
        id = "call.make",
        name = "Make Call",
        description = "Places a phone call on behalf of the user. " +
            "Use when the user asks to call, dial, or ring someone.",
        keywords = listOf(
            "call", "phone", "dial", "ring", "call someone",
            "phone call", "make a call", "give a ring", "touch base",
        ),
        parameters = listOf(
            SkillParameter(
                name = "to",
                description = "Destination phone number, in E.164 or local format",
                jsonType = "string",
                required = true,
            ),
        ),
        requiredPermissions = listOf("android.permission.CALL_PHONE"),
        requiredCapability = AgentCapability.PARTIAL,
    )

    override fun canHandle(input: String): Float {
        return if ("call" in input.lowercase()) 0.5f else 0f
    }

    override suspend fun execute(params: Map<String, String>): SkillResult {
        val to = params["to"]?.trim().orEmpty()
        if (to.isBlank()) {
            return SkillResult.Failure(
                reason = "Missing 'to' parameter",
                summary = "Couldn't place call — need a phone number.",
            )
        }
        if (context.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            return SkillResult.Failure(
                reason = "CALL_PHONE permission not granted",
                summary = "I need phone permission to place that call. Enable it in Settings.",
            )
        }

        return SkillResult.ConfirmationRequired(
            prompt = "Call $to?",
            summary = "Awaiting confirmation",
        )
    }

    /**
     * Actually place the phone call. Called by ChatViewModel after the user
     * confirms. Launches ACTION_CALL with the given phone number.
     */
    fun makeNow(phoneNumber: String): SkillResult {
        return try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            logger.i("CallSkill") { "Call placed to ${phoneNumber.take(4)}***" }
            SkillResult.Success(
                output = "Calling $phoneNumber",
                summary = "Placing call to $phoneNumber",
            )
        } catch (t: Throwable) {
            logger.e("CallSkill", t) { "Call failed" }
            SkillResult.Failure(
                reason = t.message ?: "Unknown error",
                summary = "Couldn't place call: ${t.message ?: "unknown error"}",
            )
        }
    }
}
