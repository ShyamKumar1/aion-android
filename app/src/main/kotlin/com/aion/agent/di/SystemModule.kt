package com.aion.agent.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * System-layer DI module. BatteryMonitor, SleepController, and other system
 * components use @Inject constructors with @Singleton, so Hilt discovers
 * them automatically — no explicit @Provides needed.
 */
@Module
@InstallIn(SingletonComponent::class)
object SystemModule

