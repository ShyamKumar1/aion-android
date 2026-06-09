package com.aion.agent.memory.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single structured log entry persisted in the local database.
 *
 * Every action, error, navigation event, provider call, and system state change
 * that matters for debugging is captured here with enough context to reconstruct
 * what happened and why.
 *
 * Levels: DEBUG | INFO | WARN | ERROR | FATAL
 * Categories: UI | NAV | SKILL | PROVIDER | NETWORK | SYSTEM | MEMORY | SECURITY
 */
@Entity(
    tableName = "logs",
    indices = [
        Index("timestamp"),
        Index("level"),
        Index("category"),
        Index("tag"),
    ],
)
data class LogEntry(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "level")
    val level: String, // DEBUG | INFO | WARN | ERROR | FATAL

    @ColumnInfo(name = "category")
    val category: String, // UI | NAV | SKILL | PROVIDER | NETWORK | SYSTEM | MEMORY | SECURITY

    @ColumnInfo(name = "tag")
    val tag: String, // e.g. "ChatVM", "SettingsVM", "AgentLoop"

    @ColumnInfo(name = "message")
    val message: String,

    @ColumnInfo(name = "details")
    val details: String? = null, // stack trace or JSON context blob
)
