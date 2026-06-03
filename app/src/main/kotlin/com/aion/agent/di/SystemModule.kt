package com.aion.agent.di

import com.aion.agent.system.BatteryMonitor
import com.aion.agent.system.SleepController
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides system-layer singletons for battery monitoring and sleep control.
 * Per AION_GUIDELINES §16, all bindings live in Hilt modules.
 */
@Module
@InstallIn(SingletonComponent::class)
object SystemModule {

    @Provides
    @Singleton
    fun provideBatteryMonitor(impl: BatteryMonitor): BatteryMonitor = impl

    @Provides
    @Singleton
    fun provideSleepController(impl: SleepController): SleepController = impl
}
