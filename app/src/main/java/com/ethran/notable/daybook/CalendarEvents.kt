package com.ethran.notable.daybook

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** One occurrence of an event from the calendars synced to this tablet. */
data class CalendarEvent(
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val location: String?,
)

fun hasCalendarPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

/**
 * Reads the tablet's own calendar store, which the Google Calendar and Outlook apps sync into.
 * Read-only, and returns nothing without the calendar permission.
 */
object CalendarEvents {

    /** Events on [day], all-day ones first, then by start time. */
    fun forDay(context: Context, day: LocalDate): List<CalendarEvent> =
        query(context, day, day)
            .filter { it.second == day }
            .map { it.first }
            .sortedWith(compareBy({ !it.allDay }, { it.begin }))

    /** The days from [first] to [last] that have at least one event. */
    fun daysWithEvents(context: Context, first: LocalDate, last: LocalDate): Set<LocalDate> =
        query(context, first, last).map { it.second }.toSet()

    /**
     * Event occurrences touching [first]..[last], each paired with a day it falls on. A
     * multi-day event yields one pair per day in range.
     */
    private fun query(
        context: Context,
        first: LocalDate,
        last: LocalDate,
    ): List<Pair<CalendarEvent, LocalDate>> {
        if (!hasCalendarPermission(context)) return emptyList()
        val zone = ZoneId.systemDefault()
        // All-day events are stored as UTC midnights, so widen the window by a day each way
        // and decide which days each occurrence belongs to below.
        val windowStart = first.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val windowEnd = last.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()

        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, windowStart)
            ContentUris.appendId(it, windowEnd)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION,
        )

        val results = mutableListOf<Pair<CalendarEvent, LocalDate>>()
        try {
            context.contentResolver.query(
                uri,
                projection,
                "${CalendarContract.Instances.VISIBLE} = 1",
                null,
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val begin = cursor.getLong(1)
                    val end = cursor.getLong(2).coerceAtLeast(begin)
                    val allDay = cursor.getInt(3) != 0
                    val event = CalendarEvent(
                        title = cursor.getString(0)?.takeIf { it.isNotBlank() } ?: "(no title)",
                        begin = begin,
                        end = end,
                        allDay = allDay,
                        location = cursor.getString(4)?.takeIf { it.isNotBlank() },
                    )
                    // The first and last calendar day the occurrence covers. An event ending
                    // exactly at midnight does not count for the day that starts then.
                    val eventZone = if (allDay) ZoneOffset.UTC else zone
                    val startDay = Instant.ofEpochMilli(begin).atZone(eventZone).toLocalDate()
                    val lastInstant = if (end > begin) end - 1 else end
                    val endDay = Instant.ofEpochMilli(lastInstant).atZone(eventZone).toLocalDate()

                    var day = if (startDay.isBefore(first)) first else startDay
                    val stop = if (endDay.isAfter(last)) last else endDay
                    while (!day.isAfter(stop)) {
                        results += event to day
                        day = day.plusDays(1)
                    }
                }
            }
        } catch (e: SecurityException) {
            return emptyList()
        } catch (e: IllegalArgumentException) {
            // No calendar provider on this device.
            return emptyList()
        }
        return results
    }
}
