package dev.qarasky.unofficialkimep.data

import dev.qarasky.unofficialkimep.data.model.CalendarData
import java.io.File
import java.time.LocalDate
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AcademicCalendarTest {

    private val data: CalendarData = Json { ignoreUnknownKeys = true }
        .decodeFromString(File("src/main/assets/calendar.json").readText())

    private fun d(s: String) = LocalDate.parse(s)

    @Test
    fun fall2026HasAnEarlyAndAnOfficialWeekBeforeEachDeadline() {
        val windows = AcademicCalendar.midtermWindows(data, "F2026")
        assertEquals(4, windows.size)

        // First progress submission: Monday 12 Oct 2026 -> official week 5-11 Oct.
        val first = windows.filter { it.round == 1 }
        assertEquals(d("2026-09-28"), first.single { it.phase == MidtermPhase.Early }.start)
        assertEquals(d("2026-10-04"), first.single { it.phase == MidtermPhase.Early }.end)
        assertEquals(d("2026-10-05"), first.single { it.phase == MidtermPhase.Official }.start)
        assertEquals(d("2026-10-11"), first.single { it.phase == MidtermPhase.Official }.end)
        assertEquals(d("2026-10-12"), first.first().deadline)

        // Second: Monday 23 Nov 2026 -> official 16-22 Nov, early 9-15 Nov.
        val second = windows.filter { it.round == 2 }
        assertEquals(d("2026-11-09"), second.single { it.phase == MidtermPhase.Early }.start)
        assertEquals(d("2026-11-16"), second.single { it.phase == MidtermPhase.Official }.start)
        assertEquals(d("2026-11-22"), second.single { it.phase == MidtermPhase.Official }.end)
    }

    @Test
    fun theStudyBreakWeekItselfIsNotAMidtermWeek() {
        val windows = AcademicCalendar.midtermWindows(data, "F2026")
        assertTrue(windows.none { it.contains(d("2026-10-14")) })
    }

    @Test
    fun aSubmissionPeriodUsesItsLastDayAsTheDeadline() {
        // Fall 2025 lists a period 29 Sep - 6 Oct, so grades are due Monday 6 Oct.
        val official = AcademicCalendar.midtermWindows(data, "F2025")
            .first { it.round == 1 && it.phase == MidtermPhase.Official }
        assertEquals(d("2025-10-06"), official.deadline)
        assertEquals(d("2025-09-29"), official.start)
        assertEquals(d("2025-10-05"), official.end)
    }

    @Test
    fun summerSemestersHaveOneRound() {
        val windows = AcademicCalendar.midtermWindows(data, "SU1/2027")
        assertEquals(setOf(1), windows.map { it.round }.toSet())
        assertEquals(2, windows.size)
    }

    @Test
    fun unknownSemestersYieldNothing() {
        assertTrue(AcademicCalendar.midtermWindows(data, "F1999").isEmpty())
        assertTrue(AcademicCalendar.midtermWindows(data, "nonsense").isEmpty())
        assertFalse(AcademicCalendar.midtermWindows(data, "S2027").isEmpty())
    }
}
