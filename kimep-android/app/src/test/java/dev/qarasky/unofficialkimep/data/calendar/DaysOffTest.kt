package dev.qarasky.unofficialkimep.data.calendar

import dev.qarasky.unofficialkimep.data.model.CalendarData
import java.io.File
import java.time.LocalDate
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DaysOffTest {

    private val data: CalendarData = Json { ignoreUnknownKeys = true }
        .decodeFromString(File("src/main/assets/calendar.json").readText())
    private val days = DaysOff.build(data)

    private fun d(s: String) = LocalDate.parse(s)

    @Test
    fun aHolidayWithoutNotesIsJustADayOff() {
        val day = days.getValue(d("2026-12-16"))
        assertEquals("Independence Day", day.title)
        assertEquals(DayOffKind.Holiday, day.kind)
    }

    @Test
    fun noMakeUpNotesAreRecognised() {
        assertEquals(MakeUp.NotRequired, days.getValue(d("2026-10-25")).makeUp) // Republic Day
        assertEquals(MakeUp.NotRequired, days.getValue(d("2027-07-06")).makeUp) // Capital Day
    }

    @Test
    fun makeUpRequiredNotesAreRecognised() {
        assertEquals(MakeUp.Required, days.getValue(d("2027-03-08")).makeUp) // Women's Day
        assertEquals(MakeUp.Required, days.getValue(d("2027-05-01")).makeUp) // Labor Day, Saturday classes
    }

    @Test
    fun aDateNamedOnlyInTheNoteCountsAsADayOff() {
        // Women's Day 2026 is listed on Sunday 8 March; the note moves the day off to Monday 9.
        val monday = days.getValue(d("2026-03-09"))
        assertEquals("Women's Day", monday.title)
        assertEquals(MakeUp.Required, monday.makeUp)
    }

    @Test
    fun studyBreakCoversTheWholeWeek() {
        for (offset in 0..6) {
            val date = d("2026-10-12").plusDays(offset.toLong())
            assertEquals("Study Break", days.getValue(date).title)
            assertEquals(DayOffKind.StudyBreak, days.getValue(date).kind)
        }
        assertNull(days[d("2026-10-11")])
        assertNull(days[d("2026-10-19")])
    }

    @Test
    fun consecutiveDaysAreGroupedIntoOneRange() {
        val nauryz = DaysOff.ranges(days).first { it.title == "Nauryz Day" && it.start == d("2027-03-21") }
        assertEquals(d("2027-03-25"), nauryz.end)
        assertNotNull(nauryz.note)
    }

    @Test
    fun parsesMakeUpWording() {
        assertEquals(MakeUp.NotRequired, DaysOff.makeUpOf("NO Make ups required"))
        assertEquals(MakeUp.NotRequired, DaysOff.makeUpOf("(No make-ups are required for Tuesday classes)"))
        assertEquals(MakeUp.Required, DaysOff.makeUpOf("(make up is required for Monday classes March 9, 2026)"))
        assertEquals(MakeUp.Required, DaysOff.makeUpOf("(Make-up classes are required for Monday classes)"))
        assertEquals(MakeUp.Unspecified, DaysOff.makeUpOf(""))
        assertEquals(MakeUp.Unspecified, DaysOff.makeUpOf(null))
    }

    @Test
    fun parsesDatesWrittenInNotes() {
        assertEquals(listOf(d("2026-03-09")), DaysOff.noteDates("(make up is required for Monday classes March 9, 2026)"))
        assertEquals(
            listOf(d("2026-03-24"), d("2026-03-25")),
            DaysOff.noteDates("(No make-ups are required for Tuesday and Wednesday classes on March 24-25, 2026)"),
        )
        assertEquals(emptyList<LocalDate>(), DaysOff.noteDates("NO Make ups required"))
    }
}
