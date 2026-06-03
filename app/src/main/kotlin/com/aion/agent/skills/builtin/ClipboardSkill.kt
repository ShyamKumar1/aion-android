package com.aion.agent.skills.builtin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.aion.agent.core.AgentCapability
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
 * Built-in skill for reading from and writing to the system clipboard.
 *
 * - [AgentCapability.MINIMAL] — no special runtime permissions are required.
 * - "read" action returns the current clipboard text immediately.
 * - "write" action returns [SkillResult.ConfirmationRequired] per N1; the
 *   actual write happens when the user confirms via [writeNow].
 */
@Singleton
class ClipboardSkill @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: AionLogger,
) : AgentSkill {

    override val definition: SkillDefinition = SkillDefinition(
        id = "clipboard.manage",
        name = "Clipboard",
        description = "Reads from or writes to the system clipboard. " +
            "Use when the user asks to copy, paste, or check the clipboard.",
        keywords = listOf(
            "clipboard", "copy", "paste", "clip", "copy to clipboard",
            "copy text", "paste from clipboard", "clipboard content",
        ),
        parameters = listOf(
            SkillParameter(
                name = "action",
                description = "What to do: \"read\" to get clipboard contents, " +
                    "\"write\" to replace clipboard contents",
                jsonType = "string",
                required = true,
                enum = listOf("read", "write"),
            ),
            SkillParameter(
                name = "text",
                description = "The text to write to the clipboard (required for write action)",
                jsonType = "string",
                required = false,
            ),
        ),
        requiredCapability = AgentCapability.MINIMAL,
    )

    override fun canHandle(input: String): Float {
        val lower = input.lowercase()
        return if ("clipboard" in lower || "copy" in lower) 0.4f else 0f
    }

    override suspend fun execute(params: Map<String, String>): SkillResult {
        val action = params["action"]?.trim()?.lowercase().orEmpty()

        when (action) {
            "read" -> {
                val clipManager =
                    context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clip = clipManager?.primaryClip
                val text = clip?.getItemAt(0)?.text?.toString().orEmpty()

                if (text.isBlank()) {
                    return SkillResult.Success(
                        output = "Clipboard is empty",
                        summary = "Your clipboard is empty.",
                    )
                }
                return SkillResult.Success(
                    output = text,
                    summary = "Here is what's on your clipboard.",
                    data = mapOf("text" to text),
                )
            }

            "write" -> {
                val text = params["text"]?.trim().orEmpty()
                if (text.isBlank()) {
                    return SkillResult.Failure(
                        reason = "Missing 'text' parameter for write action",
                        summary = "I need the text to copy to the clipboard.",
                    )
                }
                // Per N1, mutating actions require user confirmation.
                return SkillResult.ConfirmationRequired(
                    prompt = "Copy to clipboard: \"${text.take(120)}${if (text.length > 120) "…" else ""}\"?",
                    summary = "Awaiting confirmation to copy to clipboard",
                )
            }

            else -> {
                return SkillResult.Failure(
                    reason = "Unknown action: '$action'. Use 'read' or 'write'.",
                    summary = "I don't understand that clipboard action.",
                )
            }
        }
    }

    /**
     * Actually writes [text] to the system clipboard. Called by
     * ChatViewModel after the user confirms the write action.
     */
    suspend fun writeNow(text: String): SkillResult = withContext(Dispatchers.IO) {
        try {
            val clipManager =
                context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipManager == null) {
                return@withContext SkillResult.Failure(
                    reason = "ClipboardManager unavailable",
                    summary = "Couldn't access the system clipboard.",
                )
            }
            val clip = ClipData.newPlainText("AION Clipboard", text)
            clipManager.setPrimaryClip(clip)
            logger.i("ClipboardSkill") { "Wrote ${text.length} chars to clipboard" }
            SkillResult.Success(
                output = "Copied to clipboard",
                summary = "Copied to clipboard.",
            )
        } catch (t: Throwable) {
            logger.e("ClipboardSkill", t) { "Clipboard write failed" }
            SkillResult.Failure(
                reason = t.message ?: "Unknown error",
                summary = "Couldn't copy: ${t.message ?: "unknown error"}",
            )
        }
    }
}
