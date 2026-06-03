package com.aion.agent.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AgentCapabilityTest {

    @Test
    fun isAtLeast_fullAtLeastMinimal_isTrue() {
        assertThat(AgentCapability.FULL.isAtLeast(AgentCapability.MINIMAL)).isTrue()
    }

    @Test
    fun isAtLeast_minimalAtLeastFull_isFalse() {
        assertThat(AgentCapability.MINIMAL.isAtLeast(AgentCapability.FULL)).isFalse()
    }

    @Test
    fun isAtLeast_sameTier_isTrue() {
        assertThat(AgentCapability.PARTIAL.isAtLeast(AgentCapability.PARTIAL)).isTrue()
    }

    @Test
    fun ordering_isCorrect() {
        assertThat(AgentCapability.MINIMAL.ordinal).isLessThan(AgentCapability.PARTIAL.ordinal)
        assertThat(AgentCapability.PARTIAL.ordinal).isLessThan(AgentCapability.FULL.ordinal)
    }
}
