package dev.qarasky.unofficialkimep.data.calendar

import dev.qarasky.unofficialkimep.data.MidtermPhase
import dev.qarasky.unofficialkimep.data.MidtermWindow
import dev.qarasky.unofficialkimep.data.model.MidtermMark
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

enum class NoticeKind { MidtermWeek, MidtermsMayStart, DayOff, MarkedMidterm }

/** One line of "heads up" shown above the timetable. */
data class ScheduleNotice(val kind: NoticeKind, val title: String, val detail: String? = null)

/** Decides which heads-up lines the Schedule screen shows for a given day. */
object ScheduleNotices {

    private val dayFormat = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
    const val LOOKAHEAD_DAYS = 7L
    const val MIDTERM_LOOKAHEAD_DAYS = 14L
    private const val MAX_NOTICES = 4

    fun build(
        today: LocalDate,
        windows: List<MidtermWindow>,
        dayOffs: List<DayOffRange>,
        marks: List<MidtermMark>,
    ): List<ScheduleNotice> {
        val notices = mutableListOf<ScheduleNotice>()

        // Time-critical first: a day off today or this week.
        dayOffs.filter { !it.end.isBefore(today) && !it.start.isAfter(today.plusDays(LOOKAHEAD_DAYS)) }
            .take(2)
            .forEach { notices += dayOffNotice(it, today) }

        windows.firstOrNull { it.contains(today) }?.let { notices += midtermNotice(it) }

        marks.mapNotNull { m -> m.localDate?.let { m to it } }
            .filter { (_, date) -> !date.isBefore(today) && !date.isAfter(today.plusDays(MIDTERM_LOOKAHEAD_DAYS)) }
            .sortedBy { it.second }
            .forEach { (mark, date) ->
                notices += ScheduleNotice(
                    NoticeKind.MarkedMidterm,
                    "Midterm · ${mark.courseTitle}",
                    "${dayFormat.format(date)} · ${relative(today, date)}",
                )
            }
        return notices.take(MAX_NOTICES)
    }

    private fun midtermNotice(window: MidtermWindow): ScheduleNotice = when (window.phase) {
        MidtermPhase.Official -> ScheduleNotice(
            NoticeKind.MidtermWeek,
            "Official midterm week",
            "Progress grades are due ${dayFormat.format(window.deadline)}",
        )
        MidtermPhase.Early -> ScheduleNotice(
            NoticeKind.MidtermsMayStart,
            "Some professors start midterms this week",
            "The official midterm week begins ${dayFormat.format(window.end.plusDays(1))}",
        )
    }

    private fun dayOffNotice(range: DayOffRange, today: LocalDate): ScheduleNotice {
        val span = if (range.start == range.end) dayFormat.format(range.start)
        else "${dayFormat.format(range.start)} – ${dayFormat.format(range.end)}"
        val makeUp = when (range.makeUp) {
            MakeUp.Required -> "Make-up classes are required"
            MakeUp.NotRequired -> "No make-up needed"
            MakeUp.Unspecified -> null
        }
        return if (range.contains(today)) {
            ScheduleNotice(
                NoticeKind.DayOff,
                "No classes today · ${range.title}",
                listOfNotNull(if (range.end != today) "Until ${dayFormat.format(range.end)}" else null, makeUp)
                    .joinToString(" · ").ifEmpty { null },
            )
        } else {
            ScheduleNotice(
                NoticeKind.DayOff,
                "No classes ${relative(today, range.start)} · ${range.title}",
                listOfNotNull(span, makeUp).joinToString(" · "),
            )
        }
    }

    internal fun relative(today: LocalDate, date: LocalDate): String {
        val days = ChronoUnit.DAYS.between(today, date)
        return when (days) {
            0L -> "today"
            1L -> "tomorrow"
            else -> "in $days days"
        }
    }
}
