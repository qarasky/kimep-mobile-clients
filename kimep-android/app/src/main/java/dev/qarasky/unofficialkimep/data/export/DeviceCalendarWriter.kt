package dev.qarasky.unofficialkimep.data.export

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** A writable calendar in Android's system calendar provider (Google, DAVx5, Samsung, ...). */
data class DeviceCalendar(
    val id: Long,
    val name: String,
    val account: String,
    val color: Int,
)

/**
 * Writes timetable events into a calendar the user picked. Imported events are tagged
 * with this app's package so a later import replaces them instead of duplicating.
 * All methods block; call them off the main thread.
 */
class DeviceCalendarWriter(private val context: Context) {

    private val resolver = context.contentResolver

    fun hasPermission(): Boolean = PERMISSIONS.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    fun calendars(): List<DeviceCalendar> {
        val projection = arrayOf(
            Calendars._ID,
            Calendars.CALENDAR_DISPLAY_NAME,
            Calendars.ACCOUNT_NAME,
            Calendars.CALENDAR_COLOR,
        )
        val result = mutableListOf<DeviceCalendar>()
        resolver.query(
            Calendars.CONTENT_URI,
            projection,
            "${Calendars.CALENDAR_ACCESS_LEVEL} >= ?",
            arrayOf(Calendars.CAL_ACCESS_CONTRIBUTOR.toString()),
            "${Calendars.ACCOUNT_NAME}, ${Calendars.CALENDAR_DISPLAY_NAME}",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                result += DeviceCalendar(
                    id = cursor.getLong(0),
                    name = cursor.getString(1).orEmpty(),
                    account = cursor.getString(2).orEmpty(),
                    color = cursor.getInt(3),
                )
            }
        }
        return result
    }

    /** Removes events previously imported by this app, then inserts [events]. */
    fun replaceEvents(calendarId: Long, events: List<ScheduleEvent>) {
        resolver.delete(
            Events.CONTENT_URI,
            "${Events.CUSTOM_APP_PACKAGE} = ? AND ${Events.CUSTOM_APP_URI} = ?",
            arrayOf(context.packageName, APP_URI),
        )
        if (events.isEmpty()) return
        val ops = ArrayList<ContentProviderOperation>(events.size)
        events.forEach {
            ops += ContentProviderOperation.newInsert(Events.CONTENT_URI)
                .withValues(values(calendarId, it))
                .build()
        }
        resolver.applyBatch(CalendarContract.AUTHORITY, ops)
    }

    private fun values(calendarId: Long, event: ScheduleEvent) = ContentValues().apply {
        put(Events.CALENDAR_ID, calendarId)
        put(Events.TITLE, event.title)
        put(Events.EVENT_LOCATION, event.location)
        put(Events.DESCRIPTION, event.description)
        put(Events.DTSTART, event.startMillis)
        // All-day events are anchored to UTC midnight; everything else to KIMEP time.
        put(Events.EVENT_TIMEZONE, if (event.allDay) "UTC" else ScheduleEvent.TIME_ZONE_ID)
        if (event.allDay) put(Events.ALL_DAY, 1)
        put(Events.CUSTOM_APP_PACKAGE, context.packageName)
        put(Events.CUSTOM_APP_URI, APP_URI)
        val until = event.repeatUntilMillis
        if (until != null) {
            // The provider wants DURATION (not DTEND) on recurring events.
            put(Events.DURATION, "PT${event.durationMinutes}M")
            put(Events.RRULE, "FREQ=WEEKLY;UNTIL=${UNTIL_FORMAT.format(Instant.ofEpochMilli(until))}")
            if (event.excludedStartMillis.isNotEmpty()) {
                put(Events.EXDATE, event.excludedStartMillis.joinToString(",") { UNTIL_FORMAT.format(Instant.ofEpochMilli(it)) })
            }
        } else {
            put(Events.DTEND, event.startMillis + event.durationMinutes * 60_000L)
        }
    }

    companion object {
        val PERMISSIONS = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        private const val APP_URI = "kimep://schedule"
        private val UNTIL_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
    }
}
