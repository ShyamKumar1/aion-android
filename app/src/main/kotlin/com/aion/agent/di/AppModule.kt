package com.aion.agent.di

import android.content.Context
import androidx.room.Room
import com.aion.agent.memory.db.AionDatabase
import com.aion.agent.memory.db.ContextSummaryDao
import com.aion.agent.memory.db.ConversationDao
import com.aion.agent.memory.db.MemoryDao
import com.aion.agent.memory.db.MessageDao
import com.aion.agent.memory.db.NotificationDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * App-wide singletons. Per AION_GUIDELINES §16, dependencies are registered
 * here exactly once and consumed via constructor injection.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            // BASIC level strips request/response bodies so API keys are never logged.
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS) // long for slow LLM streams
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AionDatabase =
        Room.databaseBuilder(context, AionDatabase::class.java, AionDatabase.NAME)
            .fallbackToDestructiveMigration() // Phase 1 — replace with real migrations in Phase 2
            .build()

    @Provides
    fun provideConversationDao(db: AionDatabase): ConversationDao = db.conversationDao()

    @Provides
    fun provideMessageDao(db: AionDatabase): MessageDao = db.messageDao()

    @Provides
    fun provideNotificationDao(db: AionDatabase): NotificationDao = db.notificationDao()

    @Provides
    fun provideMemoryDao(db: AionDatabase): MemoryDao = db.memoryDao()

    @Provides
    fun provideContextSummaryDao(db: AionDatabase): ContextSummaryDao = db.contextSummaryDao()
}
