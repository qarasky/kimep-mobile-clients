package dev.qarasky.unofficialkimep.data.calendar

import dev.qarasky.unofficialkimep.data.model.CalendarData
import dev.qarasky.unofficialkimep.data.model.CalendarYear
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarPdfParserTest {

    private val json = Json { ignoreUnknownKeys = true }

    /** Words of a real KIMEP PDF, dumped with `parse_calendar.py --dump-words`. */
    private fun words(name: String): List<PdfWord> =
        javaClass.getResourceAsStream("/calendar/$name.tsv")!!.bufferedReader().readLines()
            .filter { it.isNotBlank() }
            .map { line ->
                val p = line.split('\t', limit = 4)
                PdfWord(p[0].toDouble(), p[1].toDouble(), p[2].toDouble(), p[3])
            }

    private fun bundled(id: String): CalendarYear =
        json.decodeFromString<CalendarData>(File("src/main/assets/calendar.json").readText())
            .years.first { it.id == id }

    @Test
    fun parsingTheRealPdfMatchesTheBundledCalendar() {
        for ((file, id) in listOf("AY25-26" to "2025-2026", "AY26-27" to "2026-2027")) {
            val parsed = CalendarPdfParser.parse(words(file), id)
            assertEquals(id, bundled(id).copy(source = ""), parsed)
        }
    }

    @Test
    fun findsEveryHolidayIncludingTheFirstRow() {
        val year = CalendarPdfParser.parse(words("AY26-27"), "2026-2027")
        assertEquals(12, year.closed.size)
        val republic = year.closed.first { it.title == "Republic Day" }
        assertEquals("2026-10-25", republic.dates.single().start)
        assertTrue(republic.note!!.contains("NO Make ups", ignoreCase = true))
    }

    @Test
    fun readsProgressDeadlinesAndStudyBreaks() {
        val fall = CalendarPdfParser.parse(words("AY26-27"), "2026-2027").semesters.first { it.id == "F" }
        val first = fall.events.first { it.title.startsWith("First Progress Submission") }
        assertEquals("2026-10-12", first.start)
        val studyBreak = fall.events.first { it.title == "Study Break" }
        assertEquals("2026-10-12", studyBreak.start)
        assertEquals("2026-10-18", studyBreak.end)
    }

    @Test
    fun aPageWithoutTheCalendarTableIsRejected() {
        val junk = listOf(PdfWord(10.0, 10.0, 40.0, "Hello"), PdfWord(50.0, 10.0, 80.0, "world"))
        val error = runCatching { CalendarPdfParser.parse(junk, "2026-2027") }.exceptionOrNull()
        assertTrue(error is CalendarLayoutException)
    }

    @Test
    fun normalizesTheDateShapesUsedInTheTable() {
        fun n(s: String) = CalendarPdfParser.normalizeDate(s).let { it.first to it.second }
        assertEquals("2026-09-25" to "2026-09-25", n("Friday, September 25, 2026"))
        assertEquals("2026-09-22" to "2026-09-28", n("9/22/2026 - 09/28/2026"))
        assertEquals("2026-12-15" to "2026-12-27", n("15 and 17-27 December 2026"))
        assertEquals("2027-05-06" to "2027-05-19", n("May 6,8,10-19, 2027"))
        assertEquals("2027-03-21" to "2027-03-25", n("21-25 March, 2027"))
        assertNull(CalendarPdfParser.normalizeDate("-").third)
        assertNotNull(CalendarPdfParser.normalizeDate("no later than one week").third)
    }
}
