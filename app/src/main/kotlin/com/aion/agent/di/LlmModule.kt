package com.aion.agent.di

import com.aion.agent.llm.CloudLlmEngine
import com.aion.agent.llm.LlmEngine
import com.aion.agent.skills.SkillRegistry
import com.aion.agent.skills.builtin.SmsSkill
import com.aion.agent.skills.builtin.TimerSkill
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Wires up the LLM and skill subsystems. Per AION_GUIDELINES §16, all
 * bindings live in Hilt modules — no manual wiring in application code.
 *
 * Phase 1 provides [CloudLlmEngine] as the sole [LlmEngine]. Phase 2 adds
 * [LocalLlmEngine] and a qualifier-based switcher.
 */
@Module
@InstallIn(SingletonComponent::class)
object LlmModule {

    @Provides
    @Singleton
    fun provideLlmEngine(impl: CloudLlmEngine): LlmEngine = impl

    @Provides
    @Singleton
    fun providePopulatedSkillRegistry(
        sms: SmsSkill,
        timer: TimerSkill,
    ): SkillRegistry {
        val registry = SkillRegistry()
        registry.register(sms)
        registry.register(timer)
        return registry
    }
}
