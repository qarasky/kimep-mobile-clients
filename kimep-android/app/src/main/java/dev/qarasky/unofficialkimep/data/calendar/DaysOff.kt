package dev.qarasky.unofficialkimep.data.calendar

import dev.qarasky.unofficialkimep.data.model.CalendarData
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class DayOffKind { Holiday, StudyBreak }

/** Whether KIMEP says classes missed on a day off must be made up. */
enum class MakeUp { Required, NotRequired, Unspecified }

/** A day with no classes: a public holiday ("KIMEP is closed") or a Study Break day. */
data class DayOff(
    val date: LocalDate,
    val title: String,
    val kind: DayOffKind,
    val makeUp: MakeUp,
    val note: String?,
)

/** Consecutive days off sharing a title, e.g. Nauryz Day on 21-25 March. */
data class DayOffRange(
    val title: String,
    val kind: DayOffKind,
    val start: LocalDate,
    val end: LocalDate,
    val makeUp: MakeUp,
    val note: String?,
) {
    fun contains(date: LocalDate) = !date.isBefore(start) && !date.isAfter(end)
}

/**
 * Turns the calendar's "KIMEP is closed" list and its Study Break weeks into a lookup of
 * days without classes.
 *
 * A holiday row lists its own date(s), but its note can name another day whose classes are
 * cancelled (e.g. Women's Day on Sunday 8 March 2026, "make up is required for Monday
 * classes March 9, 2026"), so dates mentioned in notes count too.
 */
object DaysOff {

    private val monthNames = listOf(
        "january", "february", "march", "april", "may", "june",
        "july", "august", "september", "october", "november", "december",
    )
    private val noteDate = Regex(
        """(${monthNames.joinToString("|")})\s+(\d{1,2})(?:\s*-\s*(\d{1,2}))?\s*,?\s*(\d{4})""",
        RegexOption.IGNORE_CASE,
    )
    private const val MAX_SPAN_DAYS = 14L

    fun build(data: CalendarData): Map<LocalDate, DayOff> {
        val days = mutableMapOf<LocalDate, DayOff>()

        for (year in data.years) {
            for (semester in year.semesters) {
                for (event in semester.events.filter { it.title.contains("Study Break", ignoreCase = true) }) {
                    for (date in span(event.start, event.end)) {
                        days.putIfAbsent(date, DayOff(date, "Study Break", DayOffKind.StudyBreak, MakeUp.Unspecified, null))
                    }
                }
            }
        }

        // Holidays win over a Study Break that happens to overlap them.
        for (year in data.years) {
            for (closure in year.closed) {
                val makeUp = makeUpOf(closure.note)
                val dates = closure.dates.flatMap { span(it.start, it.end) } + noteDates(closure.note)
                for (date in dates) {
                    days[date] = DayOff(date, closure.title, DayOffKind.Holiday, makeUp, closure.note?.takeIf { it.isNotBlank() })
                }
            }
        }
        return days
    }

    /** Groups consecutive days with the same title, earliest first. */
    fun ranges(days: Map<LocalDate, DayOff>): List<DayOffRange> {
        val result = mutableListOf<DayOffRange>()
        for (day in days.values.sortedBy { it.date }) {
            val last = result.lastOrNull()
            if (last != null && last.title == day.title && last.kind == day.kind &&
                ChronoUnit.DAYS.between(last.end, day.date) == 1L
            ) {
                result[result.lastIndex] = last.copy(end = day.date)
            } else {
                result.add(DayOffRange(day.title, day.kind, day.date, day.date, day.makeUp, day.note))
            }
        }
        return result
    }

    internal fun makeUpOf(note: String?): MakeUp {
        val n = note?.lowercase() ?: return MakeUp.Unspecified
        return when {
            Regex("""\bno make[\s-]?ups?\b""").containsMatchIn(n) -> MakeUp.NotRequired
            Regex("""make[\s-]?ups?\b.*\brequired""").containsMatchIn(n) -> MakeUp.Required
            else -> MakeUp.Unspecified
        }
    }

    /** Dates written out in a note: "March 9, 2026" or "March 24-25, 2026". */
    internal fun noteDates(note: String?): List<LocalDate> {
        if (note.isNullOrBlank()) return emptyList()
        return noteDate.findAll(note).flatMap { m ->
            val month = monthNames.indexOf(m.groupValues[1].lowercase()) + 1
            val year = m.groupValues[4].toInt()
            val first = m.groupValues[2].toInt()
            val last = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt() ?: first
            (first..last).mapNotNull { day -> runCatching { LocalDate.of(year, month, day) }.getOrNull() }
        }.toList()
    }

    private fun span(start: String?, end: String?): List<LocalDate> {
        val s = parse(start) ?: return emptyList()
        val e = parse(end) ?: s
        if (e.isBefore(s) || ChronoUnit.DAYS.between(s, e) > MAX_SPAN_DAYS) return listOf(s)
        return generateSequence(s) { it.plusDays(1) }.takeWhile { !it.isAfter(e) }.toList()
    }

    private fun parse(iso: String?): LocalDate? =
        iso?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}
