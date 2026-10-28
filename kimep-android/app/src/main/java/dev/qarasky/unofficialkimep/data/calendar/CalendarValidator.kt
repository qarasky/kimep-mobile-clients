package dev.qarasky.unofficialkimep.data.calendar

import dev.qarasky.unofficialkimep.data.model.CalendarYear
import java.time.LocalDate

/**
 * Sanity checks on a freshly parsed calendar, so a PDF whose layout changed can't replace
 * good data with garbage. Returns null when the year looks right, otherwise why it doesn't.
 */
object CalendarValidator {

    fun problem(year: CalendarYear): String? {
        val startYear = year.id.substringBefore('-').toIntOrNull() ?: return "unknown academic year"
        if (year.semesters.map { it.id } != listOf("F", "S", "SU1", "SU2")) return "semesters are missing"

        for (id in listOf("F", "S")) {
            val semester = year.semesters.first { it.id == id }
            if (semester.events.size < 15) return "${semester.name} has only ${semester.events.size} dates"

            val start = semester.events.firstOrNull { it.title.equals("Classes start", ignoreCase = true) }
                ?.start?.let(::date) ?: return "${semester.name} has no 'Classes start' date"
            val end = semester.events.firstOrNull { it.title.equals("Classes End", ignoreCase = true) }
                ?.start?.let(::date) ?: return "${semester.name} has no 'Classes End' date"

            val expectedYear = if (id == "F") startYear else startYear + 1
            if (start.year != expectedYear) return "${semester.name} classes start in ${start.year}"
            if (!end.isAfter(start) || end.isAfter(start.plusMonths(6))) return "${semester.name} class dates don't add up"
        }
        return null
    }

    fun eventCount(year: CalendarYear): Int = year.semesters.sumOf { it.events.size }

    private fun date(iso: String): LocalDate? = runCatching { LocalDate.parse(iso) }.getOrNull()
}
