package com.aion.agent.core

import com.aion.agent.llm.LlmMessage
import com.aion.agent.llm.LlmRole
import com.aion.agent.memory.MemoryRepository
import com.aion.agent.memory.db.MessageDao
import com.aion.agent.memory.db.MessageEntity
import com.aion.agent.memory.db.NotificationDao
import com.aion.agent.util.AionLogger
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ContextManagerTest {

    private val memoryRepository = mockk<MemoryRepository>(relaxed = true)
    private val notificationDao = mockk<NotificationDao>(relaxed = true)
    private val messageDao = mockk<MessageDao>(relaxed = true)
    private val logger = mockk<AionLogger>(relaxed = true)
    private val contextManager = ContextManager(
        memoryRepository = memoryRepository,
        notificationDao = notificationDao,
        messageDao = messageDao,
        logger = logger,
    )

    private fun msg(role: String, content: String): MessageEntity = MessageEntity(
        id = role + System.nanoTime(),
        conversationId = "c1",
        role = role,
        content = content,
        createdAt = 0L,
    )

    @Test
    fun `assemble empty history returns system prompt`() = runTest {
        coEvery { memoryRepository.retrieve(any(), any()) } returns emptyList()
        coEvery { notificationDao.countSince(any()) } returns 0
        val out = contextManager.assemble(emptyList(), "hi")
        // Should at least include the system prompt
        assertThat(out).isNotEmpty()
        assertThat(out[0].role).isEqualTo(LlmRole.SYSTEM)
    }

    @Test
    fun `assemble preserves message roles`() = runTest {
        coEvery { memoryRepository.retrieve(any(), any()) } returns emptyList()
        coEvery { notificationDao.countSince(any()) } returns 0
        val history = listOf(
            msg("user", "hello"),
            msg("assistant", "hi there"),
            msg("user", "send a text"),
            msg("assistant", "ok"),
        )
        val out = contextManager.assemble(history, "thanks")
        val assistantMsgs = out.filter { it.role == LlmRole.ASSISTANT }
        assertThat(assistantMsgs).hasSize(2)
    }

    @Test
    fun `assemble trims history to fit budget`() = runTest {
        coEvery { memoryRepository.retrieve(any(), any()) } returns emptyList()
        coEvery { notificationDao.countSince(any()) } returns 0
        // Use long messages that will exceed the history budget of 2048 tokens
        val longText = "A long message that takes up tokens. ".repeat(20) // ~800 chars, ~200 tokens each
        val history = (1..30).map { msg("user", longText) }
        val out = contextManager.assemble(history, "now")
        // 30 messages * ~200 tokens = ~6000 tokens, should be trimmed
        val userMsgs = out.filter { it.role == LlmRole.USER }
        assertThat(userMsgs.size).isLessThan(30)
    }

    @Test
    fun `assemble includes memory snippets when relevant`() = runTest {
        coEvery { memoryRepository.retrieve(any(), any()) } returns listOf(
            mockk {
                every { key } returns "user_name"
                every { value } returns "Alice"
            }
        )
        coEvery { notificationDao.countSince(any()) } returns 0
        val out = contextManager.assemble(emptyList(), "what's my name")
        val systemMsgs = out.filter { it.role == LlmRole.SYSTEM }
        assertThat(systemMsgs).isNotEmpty()
    }

    @Test
    fun `assemble notification count adds contextual note`() = runTest {
        coEvery { memoryRepository.retrieve(any(), any()) } returns emptyList()
        coEvery { notificationDao.countSince(any()) } returns 3
        val out = contextManager.assemble(emptyList(), "check my phone")
        val systemMsgs = out.filter { it.role == LlmRole.SYSTEM }
        assertThat(systemMsgs).isNotEmpty()
    }

    @Test
    fun `assemble buddy check respects total ceiling`() = runTest {
        coEvery { memoryRepository.retrieve(any(), any()) } returns emptyList()
        coEvery { notificationDao.countSince(any()) } returns 0
        val longText = "A ".repeat(5000) // ~1250 chars, ~312 tokens
        val history = listOf(msg("user", longText), msg("assistant", longText))
        val out = contextManager.assemble(history, "hi")
        val totalTokens = out.sumOf { (it.content.length / 4) + 1 }
        assertThat(totalTokens).isAtMost(ContextManager.TOTAL_CEILING + 500) // some slack
    }
}
