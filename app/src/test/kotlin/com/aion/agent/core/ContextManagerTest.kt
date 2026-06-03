package com.aion.agent.core

import com.aion.agent.llm.LlmMessage
import com.aion.agent.llm.LlmRole
import com.aion.agent.memory.db.MessageEntity
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ContextManagerTest {

    private fun msg(role: String, content: String): MessageEntity = MessageEntity(
        id = role + System.nanoTime(),
        conversationId = "c1",
        role = role,
        content = content,
        createdAt = 0L,
    )

    @Test
    fun assemble_emptyHistory_returnsEmpty() {
        val out = ContextManager().assemble(emptyList(), "hi")
        // The new user text is appended in AgentLoop, not ContextManager.
        assertThat(out).isEmpty()
    }

    @Test
    fun assemble_preservesRoles() {
        val history = listOf(
            msg("user", "hello"),
            msg("assistant", "hi there"),
            msg("user", "send a text"),
            msg("assistant", "ok"),
        )
        val out = ContextManager().assemble(history, "thanks")
        assertThat(out).hasSize(4)
        assertThat(out[0].role).isEqualTo(LlmRole.USER)
        assertThat(out[1].role).isEqualTo(LlmRole.ASSISTANT)
    }

    @Test
    fun assemble_truncatesAtWindowSize() {
        val history = (1..100).map { msg("user", "msg $it") }
        val out = ContextManager().assemble(history, "now")
        // Mirrors ContextManager.MAX_HISTORY_MESSAGES = 50.
        assertThat(out.size).isAtMost(50)
    }
}
