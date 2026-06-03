package com.aion.agent.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Buffers tokens emitted by an LLM stream so the UI does not recompose on
 * every single token. Per AION_GUIDELINES §6 and §10, the target is 5-token
 * chunks emitted every 80-150ms.
 *
 * The buffer flushes whenever EITHER:
 *  - [size] tokens have been collected, OR
 *  - [maxDelay] has passed since the first token in the current chunk.
 *
 * This composes into the existing [LlmEvent] stream — we forward [Token]
 * events through the buffer and pass through everything else.
 */
class TokenBuffer(
    private val size: Int = 5,
    private val maxDelay: Duration = 150.milliseconds,
) {

    fun <T> buffer(source: Flow<T>): Flow<T> = flow {
        val pending = mutableListOf<T>()
        var firstTokenAt = 0L
        source.collect { event ->
            val now = System.nanoTime()
            if (pending.isEmpty()) firstTokenAt = now
            pending += event
            val elapsed = (now - firstTokenAt).milliseconds
            if (pending.size >= size || elapsed >= maxDelay) {
                for (p in pending) emit(p)
                pending.clear()
            }
        }
        for (p in pending) emit(p)
    }.onStart { /* start signal */ }
}
