package com.aion.agent.skills

import java.util.Locale

/**
 * BM25 ranking of skills against a user input. Per AION_GUIDELINES §13:
 *  - Must complete in < 15ms.
 *  - Returns scores in 0.0..1.0 (normalized).
 *  - Confidence threshold for routing: 0.35.
 *  - If two skills score within 0.05, do not auto-route — present both.
 *
 * This is the v1 Phase-1 implementation. It is intentionally simple —
 * no external library, no corpus indexing, no LLM. Just tokenize, count,
 * and score against each skill's `keywords + name + description`.
 */
class Bm25Router(
    private val threshold: Float = DEFAULT_THRESHOLD,
    private val ambiguityMargin: Float = DEFAULT_AMBIGUITY_MARGIN,
) {

    /**
     * Rank [skills] by relevance to [input]. Returns a list of (skill, score)
     * sorted descending by score, only including entries with score > 0.
     */
    fun rank(skills: Collection<AgentSkill>, input: String): List<RankedSkill> {
        if (input.isBlank()) return emptyList()
        val tokens = tokenize(input)
        if (tokens.isEmpty()) return emptyList()

        val scored = skills.map { skill ->
            val doc = buildDocTokens(skill.definition)
            val score = bm25(tokens, doc)
            RankedSkill(skill, score.coerceIn(0f, 1f))
        }
        return scored
            .filter { it.score > 0f }
            .sortedByDescending { it.score }
    }

    /**
     * The top skill if it is above [threshold] AND clearly more confident
     * than the runner-up (no ambiguity within [ambiguityMargin]).
     * Returns null otherwise — caller falls through to general LLM chat.
     */
    fun topMatch(skills: Collection<AgentSkill>, input: String): RankedSkill? {
        val ranked = rank(skills, input)
        if (ranked.isEmpty()) return null
        val first = ranked.first()
        if (first.score < threshold) return null
        if (ranked.size >= 2 && (first.score - ranked[1].score) < ambiguityMargin) {
            return null
        }
        return first
    }

    private fun buildDocTokens(def: SkillDefinition): List<String> =
        (def.keywords + def.name.split(" ") + def.description.split(" "))
            .flatMap { tokenize(it) }

    private fun tokenize(s: String): List<String> =
        s.lowercase(Locale.US)
            .split(NON_WORD)
            .filter { it.isNotBlank() && it !in STOPWORDS }

    /**
     * BM25 score for [query] against a single document. We use fixed k1/b
     * tuned for short queries against short documents.
     *
     * Uses the standard BM25 IDF formula: ln((N - n + 0.5) / (n + 0.5)).
     * Since we score one document at a time, N is always 1 and n is approximated
     * by the term frequency within this single document.
     */
    private fun bm25(query: List<String>, doc: List<String>): Float {
        if (doc.isEmpty()) return 0f
        val docLen = doc.size
        val docFreq = doc.groupingBy { it }.eachCount()
        val qFreq = query.groupingBy { it }.eachCount()

        // For per-document scoring without corpus-level IDF,
        // use a simplified IDF that still varies meaningfully:
        // idf = ln(1 + (N - n + 0.5) / (n + 0.5))
        // Since we have one doc at a time, use term frequency as a proxy:
        // rare terms in the doc get higher IDF
        val totalDocs = 1f  // We score one doc at a time
        var total = 0f
        for ((term, qf) in qFreq) {
            val tf = docFreq[term] ?: 0
            if (tf == 0) continue
            // Standard BM25 IDF: ln((N - n + 0.5) / (n + 0.5))
            // n = number of docs containing term (approximated by term frequency in this single doc)
            val n = tf.toFloat()
            val idf = kotlin.math.ln((totalDocs - n + 0.5f) / (n + 0.5f))
            val norm = 1f - B + B * (docLen / AVG_DOC_LEN)
            total += idf * ((tf * (K1 + 1f)) / (tf + K1 * norm)) * qf
        }
        return total.coerceAtLeast(0f)  // BM25 can be negative, clamp to 0
    }

    data class RankedSkill(val skill: AgentSkill, val score: Float)

    private companion object {
        const val K1 = 1.2f
        const val B = 0.75f
        const val AVG_DOC_LEN = 20f
        const val DEFAULT_THRESHOLD = 0.35f
        const val DEFAULT_AMBIGUITY_MARGIN = 0.05f
        val NON_WORD = Regex("[^a-z0-9]+")
        val STOPWORDS = setOf(
            "a", "an", "and", "are", "as", "at", "be", "by", "do", "for",
            "from", "has", "have", "i", "in", "is", "it", "me", "my", "of",
            "on", "or", "please", "that", "the", "this", "to", "with", "you",
            "your",
        )
    }
}
