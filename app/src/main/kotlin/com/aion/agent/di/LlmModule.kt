package com.aion.agent.di

import com.aion.agent.llm.CloudLlmEngine
import com.aion.agent.llm.LlmEngine
import com.aion.agent.llm.LocalLlmEngine
import com.aion.agent.skills.SkillRegistry
import com.aion.agent.skills.builtin.CalendarSkill
import com.aion.agent.skills.builtin.CallSkill
import com.aion.agent.skills.builtin.ClipboardSkill
import com.aion.agent.skills.builtin.ContactsSkill
import com.aion.agent.skills.builtin.NotificationSkill
import com.aion.agent.skills.builtin.ScreenSkill
import com.aion.agent.skills.builtin.SmsSkill
import com.aion.agent.skills.builtin.TimerSkill
import com.aion.agent.skills.builtin.WebSearchSkill
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Wires up the LLM and skill subsystems. Per AION_GUIDELINES §16, all
 * bindings live in Hilt modules — no manual wiring in application code.
 *
 * Phase 2 provides both [CloudLlmEngine] and [LocalLlmEngine], with
 * [ModelRouter] selecting between them at runtime.
 */
@Module
@InstallIn(SingletonComponent::class)
object LlmModule {

    @CloudLlm
    @Provides
    @Singleton
    fun provideCloudLlmEngine(impl: CloudLlmEngine): LlmEngine = impl

    @LocalLlm
    @Provides
    @Singleton
    fun provideLocalLlmEngine(impl: LocalLlmEngine): LlmEngine = impl

    // Default binding for backward compatibility (e.g. tests that inject LlmEngine)
    @Provides
    @Singleton
    fun provideDefaultLlmEngine(@CloudLlm impl: LlmEngine): LlmEngine = impl

    @Provides
    @Singleton
    fun providePopulatedSkillRegistry(
        sms: SmsSkill,
        timer: TimerSkill,
        call: CallSkill,
        notification: NotificationSkill,
        screen: ScreenSkill,
        clipboard: ClipboardSkill,
        calendar: CalendarSkill,
        contacts: ContactsSkill,
        webSearch: WebSearchSkill,
    ): SkillRegistry {
        val registry = SkillRegistry()
        registry.register(sms)
        registry.register(timer)
        registry.register(call)
        registry.register(notification)
        registry.register(screen)
        registry.register(clipboard)
        registry.register(calendar)
        registry.register(contacts)
        registry.register(webSearch)
        return registry
    }
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CloudLlm

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LocalLlm
