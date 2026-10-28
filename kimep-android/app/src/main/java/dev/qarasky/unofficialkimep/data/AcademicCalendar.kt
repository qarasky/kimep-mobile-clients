package dev.qarasky.unofficialkimep.data

import dev.qarasky.unofficialkimep.data.model.CalendarData
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class MidtermPhase {
    /** The week before the official one: many professors already hold midterms then. */
    Early,

    /** The official midterm week: the Monday-to-Sunday week before progress grades are due. */
    Official,
}

/**
 * A week that counts as "midterms" for a semester, derived from the academic calendar.
 * [round] is 1 for the first progress submission and 2 for the second; [deadline] is the
 * day progress grades are due.
 */
data class MidtermWindow(
    val round: Int,
    val phase: MidtermPhase,
    val start: LocalDate,
    val end: LocalDate,
    val deadline: LocalDate,
) {
    fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)
}

/**
 * Maps a timetable semester code (e.g. "F2026", "S2026", "SU1/2026") onto the academic
 * calendar's academic-year + semester identifiers, and derives midterm weeks from the
 * calendar's progress-submission deadlines.
 */
object AcademicCalendar {

    /** "F2026" -> "2026-2027"; "S2026" / "SU1/2026" -> "2025-2026". */
    fun yearIdForSemester(semesterCode: String): String? {
        val year = Regex("\\d{4}").find(semesterCode)?.value?.toIntOrNull() ?: return null
        val head = semesterCode.substringBefore('/')
        return when {
            head.startsWith("F", ignoreCase = true) -> "$year-${year + 1}"
            head.startsWith("SU", ignoreCase = true) -> "${year - 1}-$year"
            head.startsWith("S", ignoreCase = true) -> "${year - 1}-$year"
            else -> null
        }
    }

    /** "F2026" -> "F"; "S2026" -> "S"; "SU1/2026" -> "SU1". */
    fun semesterId(semesterCode: String): String? {
        val head = semesterCode.substringBefore('/')
        return when {
            head.startsWith("SU1", ignoreCase = true) -> "SU1"
            head.startsWith("SU2", ignoreCase = true) -> "SU2"
            head.startsWith("F", ignoreCase = true) -> "F"
            head.startsWith("S", ignoreCase = true) -> "S"
            else -> null
        }
    }

    /**
     * Midterm weeks for the given semester, earliest first.
     *
     * KIMEP's calendar lists the progress-submission deadline (a Monday, or a period ending
     * on one). The week before it is the official midterm week, but professors often start a
     * week earlier, so that week is returned as an [MidtermPhase.Early] window. Falls back to
     * an empty list when the calendar has no matching data.
     */
    fun midtermWindows(data: CalendarData, semesterCode: String): List<MidtermWindow> {
        val yearId = yearIdForSemester(semesterCode) ?: return emptyList()
        val semId = semesterId(semesterCode) ?: return emptyList()
        val year = data.years.firstOrNull { it.id == yearId } ?: return emptyList()
        val semester = year.semesters.firstOrNull { it.id == semId } ?: return emptyList()

        return semester.events
            .filter { it.title.contains("Progress Submission", ignoreCase = true) }
            .mapIndexedNotNull { index, event ->
                // For a submission *period* the deadline is its last day.
                val deadline = (event.end ?: event.start)
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    ?: return@mapIndexedNotNull null
                val round = when {
                    event.title.contains("First", ignoreCase = true) -> 1
                    event.title.contains("Second", ignoreCase = true) -> 2
                    else -> index + 1
                }
                val deadlineWeek = deadline.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val official = deadlineWeek.minusWeeks(1)
                listOf(
                    MidtermWindow(round, MidtermPhase.Early, official.minusWeeks(1), official.minusDays(1), deadline),
                    MidtermWindow(round, MidtermPhase.Official, official, official.plusDays(6), deadline),
                )
            }
            .flatten()
            .sortedBy { it.start }
    }
}
