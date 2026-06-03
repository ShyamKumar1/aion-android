package com.aion.agent.skills

import com.aion.agent.core.AgentCapability
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds all available skills. Per AION_GUIDELINES §13, the BM25 index is
 * rebuilt whenever a skill is installed/removed. We don't use a lazy index —
 * the registry is small enough (Phase 4 target: 10-20 skills) that an
 * O(n) scan per query is faster than maintaining an inverted index.
 */
@Singleton
class SkillRegistry @Inject constructor() {

    private val skills: MutableList<AgentSkill> = mutableListOf()
    private val router = Bm25Router()

    fun register(skill: AgentSkill) {
        require(skills.none { it.definition.id == skill.definition.id }) {
            "Skill id already registered: ${skill.definition.id}"
        }
        skills += skill
    }

    fun unregister(skillId: String) {
        skills.removeAll { it.definition.id == skillId }
    }

    fun all(): List<AgentSkill> = skills.toList()

    fun byId(id: String): AgentSkill? = skills.firstOrNull { it.definition.id == id }

    fun definitions(): List<SkillDefinition> = skills.map { it.definition }

    /**
     * Skills whose [SkillDefinition.requiredCapability] is at most [tier].
     * The BM25 router and UI tool list use this so a PARTIAL user never sees
     * FULL-only skills.
     */
    fun availableAt(tier: AgentCapability): List<AgentSkill> =
        skills.filter { it.definition.requiredCapability.ordinal <= tier.ordinal }

    /**
     * Rank skills for a given input, filtering by capability tier.
     * Returns ranked matches — caller applies the threshold/ambiguity policy.
     */
    fun rankFor(input: String, tier: AgentCapability): List<Bm25Router.RankedSkill> =
        router.rank(availableAt(tier), input)
}
