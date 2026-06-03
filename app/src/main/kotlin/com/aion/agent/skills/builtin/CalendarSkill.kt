package com.aion.agent.skills.builtin

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import com.aion.agent.core.AgentCapability
import com.aion.agent.skills.AgentSkill
import com.aion.agent.skills.SkillDefinition
import com.aion.agent.skills.SkillParameter
import com.aion.agent.skills.SkillResult
import com.aion.agent.util.AionLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Built-in skill for reading the device calendar. Per AION_GUIDELINES §13
 * (Phase 1 Week 3), the skill:
 *  - Requires [AgentCapability.PARTIAL] or above (READ_CALENDAR is a runtime perm)
 *  - Returns [SkillResult.Success] with upcoming events (read-only → no confirmation)
 *  - Verifies the READ_CALENDAR permission before querying
 */
@Singleton
class CalendarSkill @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: AionLogger,
) : AgentSkill {

    override val definition: SkillDefinition = SkillDefinition(
        id = "calendar.read",
        name = "Read Calendar",
        description = "Reads upcoming calendar events. " +
            "Use when the user asks about their calendar, schedule, events, " +
            "appointments, or what's coming up.",
        keywords = listOf(
            "calendar", "event", "schedule", "appointment", "meeting",
            "upcoming", "planned", "agenda", "reminder", "plans",
            "today", "tomorrow", "this week",
        ),
        parameters = listOf(
            SkillParameter(
                name = "date",
                description = "Specific date to read events for (e.g., 2026-06-03). " +
                    "Defaults to today if omitted.",
                jsonType = "string",
                required = false,
            ),
            SkillParameter(
                name = "limit",
                description = "Maximum number of events to return. Defaults to 5.",
                jsonType = "string",
                required = false,
            ),
        ),
        requiredPermissions = listOf("android.permission.READ_CALENDAR"),
        requiredCapability = AgentCapability.PARTIAL,
    )

    override fun canHandle(input: String): Float {
        val lower = input.lowercase()
        return if ("calendar" in lower || "event" in lower) 0.5f else 0f
    }

    override suspend fun execute(params: Map<String, String>): SkillResult {
        if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return SkillResult.Failure(
                reason = "READ_CALENDAR permission not granted",
                summary = "I need calendar access to read your events. Enable it in Settings.",
            )
        }

        val limit = params["limit"]?.toIntOrNull() ?: 5
        val dateStr = params["date"] ?: todayDateString()

        return queryEvents(dateStr, limit)
    }

    private fun queryEvents(date: String, limit: Int): SkillResult {
        val resolver: ContentResolver = context.contentResolver
        val uri = CalendarContract.Events.CONTENT_URI

        // Build a range for the given date: start of day → start of next day
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
        val dateStart: Long = try {
            sdf.parse(date)?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
        // 24 hours later
        val dateEnd = dateStart + 86_400_000L

        val projection = arrayOf(
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DESCRIPTION,
        )
        val selection = "${CalendarContract.Events.DTSTART} >= ? AND " +
            "${CalendarContract.Events.DTSTART} < ?"
        val selectionArgs = arrayOf(dateStart.toString(), dateEnd.toString())
        val sortOrder = "${CalendarContract.Events.DTSTART} ASC LIMIT $limit"

        val events = mutableListOf<String>()
        try {
            resolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val title = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.TITLE))
                    val startMs = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Events.DTSTART))
                    val endMs = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Events.DTEND))
                    val location = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION))
                    val description = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.DESCRIPTION))

                    val timeFmt = SimpleDateFormat("h:mm a", Locale.US).apply {
                        timeZone = TimeZone.getDefault()
                    }
                    val startTime = timeFmt.format(Date(startMs))
                    val endTime = timeFmt.format(Date(endMs))

                    val sb = StringBuilder()
                    sb.append("• $title ($startTime – $endTime)")
                    if (!location.isNullOrBlank()) sb.append(" @ $location")
                    if (!description.isNullOrBlank()) {
                        val descTrimmed = description.trim().take(80)
                        sb.append(" — $descTrimmed")
                    }
                    events.add(sb.toString())
                }
            }
        } catch (t: Throwable) {
            logger.e("CalendarSkill", t) { "Calendar query failed" }
            return SkillResult.Failure(
                reason = t.message ?: "Query error",
                summary = "Couldn't read calendar: ${t.message ?: "unknown error"}",
            )
        }

        if (events.isEmpty()) {
            return SkillResult.Success(
                output = "No events found for $date.",
                summary = "No events on $date.",
            )
        }

        val output = events.joinToString("\n")
        val summary = "${events.size} event${if (events.size != 1) "s" else ""} on $date"
        logger.i("CalendarSkill") { summary }
        return SkillResult.Success(
            output = output,
            summary = summary,
        )
    }

    private fun todayDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
        return sdf.format(Date())
    }
}
