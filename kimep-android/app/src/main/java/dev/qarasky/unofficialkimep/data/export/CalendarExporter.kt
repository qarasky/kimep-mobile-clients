package dev.qarasky.unofficialkimep.data.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dev.qarasky.unofficialkimep.data.CalendarRepository
import dev.qarasky.unofficialkimep.data.KimepRepository
import dev.qarasky.unofficialkimep.data.MidtermStore
import dev.qarasky.unofficialkimep.data.ScheduleCache
import dev.qarasky.unofficialkimep.data.calendar.DaysOff
import dev.qarasky.unofficialkimep.data.model.ClassMeeting
import dev.qarasky.unofficialkimep.data.model.FinalExam
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Sends the full timetable (weekly classes, final exams and marked midterms) to a calendar app, either
 * directly into a system calendar (Google Calendar and any other synced account) or as
 * an .ics file (Proton Calendar, which keeps its events outside the system provider).
 */
class CalendarExporter(
    private val context: Context,
    private val repository: KimepRepository,
    private val scheduleCache: ScheduleCache,
    private val calendarRepository: CalendarRepository,
    private val midtermStore: MidtermStore,
) {
    val deviceCalendars = DeviceCalendarWriter(context)

    class IcsFile(val uri: Uri, val eventCount: Int)

    suspend fun listCalendars(): List<DeviceCalendar> =
        withContext(Dispatchers.IO) { deviceCalendars.calendars() }

    /** @return the number of events written. */
    suspend fun addToCalendar(sessionId: String, calendarId: Long): Result<Int> = runCatching {
        val events = loadEvents(sessionId)
        withContext(Dispatchers.IO) { deviceCalendars.replaceEvents(calendarId, events) }
        events.size
    }

    suspend fun createIcsFile(sessionId: String): Result<IcsFile> = runCatching {
        val events = loadEvents(sessionId)
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, "kimep-schedule.ics")
            file.writeText(IcsBuilder.build(events), Charsets.UTF_8)
            IcsFile(
                uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file),
                eventCount = events.size,
            )
        }
    }

    /** Live timetable if reachable, otherwise the last cached one. */
    private suspend fun loadEvents(sessionId: String): List<ScheduleEvent> {
        val live = repository.schedule(sessionId).getOrNull()
        val meetings: List<ClassMeeting>
        val finals: List<FinalExam>
        if (live != null) {
            meetings = live
            finals = repository.finalExams(sessionId).getOrNull()
                ?: scheduleCache.read()?.finals.orEmpty()
        } else {
            val cached = scheduleCache.read() ?: error("No timetable available. Open the Schedule tab while online first.")
            meetings = cached.meetings
            finals = cached.finals
        }
        // Classes are left out on holidays and Study Break, and the student's marked
        // midterm dates for this semester are added as all-day events.
        val daysOff = runCatching { DaysOff.build(calendarRepository.load()).keys }.getOrDefault(emptySet())
        val semester = meetings.firstOrNull()?.semester
        val marks = midtermStore.current().filter { semester == null || it.semester == semester }
        val events = ScheduleEvent.from(meetings, finals, daysOff, marks)
        if (events.isEmpty()) error("Your timetable has no classes or exams to export.")
        return events
    }
}
