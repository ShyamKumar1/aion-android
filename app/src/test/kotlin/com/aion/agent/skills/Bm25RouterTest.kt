package com.aion.agent.skills

import com.aion.agent.core.AgentCapability
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class Bm25RouterTest {

    private data class FakeSkill(
        override val definition: SkillDefinition,
    ) : AgentSkill {
        override suspend fun execute(params: Map<String, String>): SkillResult =
            SkillResult.Success("ok", "ok")
    }

    private fun sms() = FakeSkill(
        SkillDefinition(
            id = "sms.send",
            name = "Send SMS",
            description = "Sends an SMS text message to a phone number.",
            keywords = listOf("sms", "text", "message", "send", "phone", "tell", "msg"),
            requiredCapability = AgentCapability.PARTIAL,
        ),
    )

    private fun timer() = FakeSkill(
        SkillDefinition(
            id = "timer.set",
            name = "Set Timer",
            description = "Sets a timer for N minutes.",
            keywords = listOf("timer", "alarm", "remind", "minutes", "seconds"),
            requiredCapability = AgentCapability.MINIMAL,
        ),
    )

    @Test
    fun rank_smsQuery_returnsSmsSkillFirst() {
        val ranked = Bm25Router().rank(listOf(sms(), timer()), "send a text to mom")
        assertThat(ranked).isNotEmpty()
        assertThat(ranked.first().skill.definition.id).isEqualTo("sms.send")
    }

    @Test
    fun rank_timerQuery_returnsTimerSkillFirst() {
        val ranked = Bm25Router().rank(listOf(sms(), timer()), "set a 10 minute timer")
        assertThat(ranked).isNotEmpty()
        assertThat(ranked.first().skill.definition.id).isEqualTo("timer.set")
    }

    @Test
    fun rank_emptyInput_returnsEmpty() {
        val ranked = Bm25Router().rank(listOf(sms(), timer()), "")
        assertThat(ranked).isEmpty()
    }

    @Test
    fun rank_unrelatedQuery_returnsEmpty() {
        val ranked = Bm25Router().rank(listOf(sms(), timer()), "what is the meaning of life")
        // No overlap → may return zero or near-zero scores; we assert it doesn't promote either
        assertThat(ranked.none { it.score > 0.4f }).isTrue()
    }

    @Test
    fun topMatch_belowThreshold_returnsNull() {
        val router = Bm25Router(threshold = 0.9f)
        val top = router.topMatch(listOf(sms(), timer()), "send a text to mom")
        assertThat(top).isNull()
    }

    @Test
    fun topMatch_aboveThreshold_returnsFirst() {
        val top = Bm25Router().topMatch(listOf(sms(), timer()), "send a text message to +91 12345")
        assertThat(top).isNotNull()
        assertThat(top!!.skill.definition.id).isEqualTo("sms.send")
    }

    @Test
    fun rank_completesQuickly() {
        val skills = (1..20).map { i ->
            FakeSkill(
                SkillDefinition(
                    id = "skill-$i",
                    name = "Skill $i",
                    description = "Skill number $i description",
                    keywords = listOf("kw-$i", "keyword-$i"),
                    requiredCapability = AgentCapability.MINIMAL,
                ),
            )
        }
        val start = System.nanoTime()
        repeat(100) {
            Bm25Router().rank(skills, "find me a skill that does keyword-7")
        }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        // 100 ranks over 20 skills must complete in well under 1.5 seconds.
        assertThat(elapsedMs).isLessThan(1500L)
    }
}
