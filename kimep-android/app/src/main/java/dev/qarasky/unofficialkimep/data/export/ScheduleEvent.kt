package dev.qarasky.unofficialkimep.data.export

import dev.qarasky.unofficialkimep.data.ApiDate
import dev.qarasky.unofficialkimep.data.model.ClassMeeting
import dev.qarasky.unofficialkimep.data.model.FinalExam
import dev.qarasky.unofficialkimep.data.model.MidtermMark
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters

/**
 * A calendar entry derived from the timetable, independent of where it is written
 * (system calendar provider or an .ics file).
 *
 * [repeatUntilMillis] is null for one-off events (final exams). Otherwise the event
 * repeats weekly, starting at [startMillis], through that instant (inclusive), except for
 * the occurrences starting at [excludedStartMillis] (holidays, Study Break).
 *
 * An [allDay] event starts at UTC midnight of its date and lasts one day.
 */
data class ScheduleEvent(
    val uid: String,
    val title: String,
    val location: String?,
    val description: String?,
    val startMillis: Long,
    val durationMinutes: Int,
    val repeatUntilMillis: Long? = null,
    val excludedStartMillis: List<Long> = emptyList(),
    val allDay: Boolean = false,
) {
    companion object {
        /** Wall-clock times from the API are KIMEP local time. */
        const val TIME_ZONE_ID = "Asia/Almaty"

        private const val DEFAULT_CLASS_MINUTES = 75
        private const val DEFAULT_EXAM_MINUTES = 120
        private val DEFAULT_EXAM_START: LocalTime = LocalTime.of(9, 0)

        /**
         * @param daysOff dates with no classes; class occurrences on them are left out.
         * @param marks midterm dates the student marked, exported as all-day events.
         */
        fun from(
            meetings: List<ClassMeeting>,
            finals: List<FinalExam>,
            daysOff: Set<LocalDate> = emptySet(),
            marks: List<MidtermMark> = emptyList(),
        ): List<ScheduleEvent> =
            meetings.mapNotNull { fromMeeting(it, daysOff) } +
                finals.mapNotNull(::fromFinal) +
                marks.mapNotNull(::fromMark)

        private fun fromMark(mark: MidtermMark): ScheduleEvent? {
            val date = mark.localDate ?: return null
            val key = mark.courseKey.filter { it.isLetterOrDigit() }.ifEmpty { "course" }
            return ScheduleEvent(
                uid = "kimep-midterm-$key-$date@unofficialkimep",
                title = "Midterm: ${mark.courseTitle}",
                location = null,
                description = "Marked in the KIMEP app",
                startMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
                durationMinutes = 24 * 60,
                allDay = true,
            )
        }

        private fun fromMeeting(meeting: ClassMeeting, daysOff: Set<LocalDate>): ScheduleEvent? {
            val from = ApiDate.localDate(meeting.dateFrom) ?: return null
            val to = ApiDate.localDate(meeting.dateTo) ?: return null
            val start = ApiDate.localTime(meeting.timeFrom) ?: return null
            val day = runCatching { DayOfWeek.valueOf(meeting.weekDay.trim().uppercase()) }
                .getOrNull() ?: return null

            val first: LocalDate = from.with(TemporalAdjusters.nextOrSame(day))
            if (first.isAfter(to)) return null

            val excluded = generateSequence(first) { it.plusWeeks(1) }
                .takeWhile { !it.isAfter(to) }
                .filter { it in daysOff }
                .map { millis(it, start) }
                .toList()
            if (to.isBefore(first.plusWeeks(1)) && excluded.isNotEmpty()) return null // its only class is a day off

            val minutes = minutesBetween(start, ApiDate.localTime(meeting.timeTo), DEFAULT_CLASS_MINUTES)
            val startMillis = millis(first, start)
            // Last day of the range, end of day, so a final occurrence on `to` is included.
            val untilMillis = millis(to, LocalTime.MAX)

            return ScheduleEvent(
                uid = "kimep-class-${meeting.id}@unofficialkimep",
                title = meeting.title.ifBlank { meeting.courseId ?: "Class" },
                location = meeting.hall?.takeIf { it.isNotBlank() },
                description = description(
                    "Course" to meeting.courseId,
                    "Section" to meeting.section,
                    "Instructor" to meeting.instructor,
                ),
                startMillis = startMillis,
                durationMinutes = minutes,
                repeatUntilMillis = if (!to.isBefore(first.plusWeeks(1))) untilMillis else null,
                excludedStartMillis = excluded,
            )
        }

        private fun fromFinal(exam: FinalExam): ScheduleEvent? {
            val date = ApiDate.localDate(exam.date) ?: return null
            val start = ApiDate.localTime(exam.timeFrom) ?: DEFAULT_EXAM_START
            val minutes = minutesBetween(start, ApiDate.localTime(exam.timeTo), DEFAULT_EXAM_MINUTES)
            val name = exam.title ?: exam.code ?: "Final exam"
            return ScheduleEvent(
                uid = "kimep-final-${exam.id}@unofficialkimep",
                title = "Final exam: $name",
                location = exam.hall?.takeIf { it.isNotBlank() },
                description = description("Course" to (exam.courseId ?: exam.code), "Section" to exam.section),
                startMillis = millis(date, start),
                durationMinutes = minutes,
            )
        }

        private fun minutesBetween(start: LocalTime, end: LocalTime?, fallback: Int): Int {
            val minutes = end?.let { Duration.between(start, it).toMinutes().toInt() } ?: return fallback
            return if (minutes > 0) minutes else fallback
        }

        private fun millis(date: LocalDate, time: LocalTime): Long =
            date.atTime(time).toInstant(ApiDate.KIMEP_OFFSET).toEpochMilli()

        private fun description(vararg parts: Pair<String, String?>): String? =
            parts.filter { !it.second.isNullOrBlank() }
                .joinToString("\n") { "${it.first}: ${it.second}" }
                .ifEmpty { null }
    }
}
