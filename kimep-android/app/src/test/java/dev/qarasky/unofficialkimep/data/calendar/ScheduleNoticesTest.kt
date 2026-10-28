package dev.qarasky.unofficialkimep.data.calendar

import dev.qarasky.unofficialkimep.data.AcademicCalendar
import dev.qarasky.unofficialkimep.data.model.CalendarData
import dev.qarasky.unofficialkimep.data.model.MidtermMark
import java.io.File
import java.time.LocalDate
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleNoticesTest {

    private val data: CalendarData = Json { ignoreUnknownKeys = true }
        .decodeFromString(File("src/main/assets/calendar.json").readText())
    private val windows = AcademicCalendar.midtermWindows(data, "F2026")
    private val ranges = DaysOff.ranges(DaysOff.build(data))

    private fun d(s: String) = LocalDate.parse(s)
    private fun notices(today: String, marks: List<MidtermMark> = emptyList()) =
        ScheduleNotices.build(d(today), windows, ranges, marks)

    @Test
    fun quietDaysHaveNoNotices() {
        assertTrue(notices("2026-09-15").isEmpty())
    }

    @Test
    fun theWeekBeforeTheOfficialWeekWarnsThatMidtermsMayStart() {
        val n = notices("2026-09-30").single()
        assertEquals(NoticeKind.MidtermsMayStart, n.kind)
        assertTrue(n.detail!!.contains("Mon, 5 Oct"))
    }

    @Test
    fun theOfficialWeekShowsWhenGradesAreDue() {
        val all = notices("2026-10-07")
        val n = all.single { it.kind == NoticeKind.MidtermWeek }
        assertTrue(n.detail!!.contains("Mon, 12 Oct"))
        // The Study Break that follows is announced as well.
        assertEquals("No classes in 5 days · Study Break", all.single { it.kind == NoticeKind.DayOff }.title)
    }

    @Test
    fun aHolidayThisWeekIsAnnouncedWithItsMakeUpRule() {
        // Women's Day 2027 is a Monday with make-up classes required.
        val n = notices("2027-03-04").first { it.title.contains("Women") }
        assertTrue(n.title, n.title.startsWith("No classes in 4 days"))
        assertTrue(n.detail!!.contains("Make-up classes are required"))
    }

    @Test
    fun todayBeingADayOffSaysSo() {
        val n = notices("2026-10-14").first { it.kind == NoticeKind.DayOff }
        assertEquals("No classes today · Study Break", n.title)
        assertTrue(n.detail!!.contains("Until Sun, 18 Oct"))
    }

    @Test
    fun markedMidtermsAppearWithinTwoWeeksAndNotAfterwards() {
        val soon = MidtermMark("ECON101", "Macroeconomics", "F2026", "2026-10-02")
        val past = MidtermMark("ECON101", "Macroeconomics", "F2026", "2026-09-20")
        val far = MidtermMark("MATH201", "Calculus", "F2026", "2026-11-20")
        val n = notices("2026-09-29", listOf(soon, past, far)).filter { it.kind == NoticeKind.MarkedMidterm }
        assertEquals(1, n.size)
        assertEquals("Midterm · Macroeconomics", n.single().title)
        assertTrue(n.single().detail!!.contains("in 3 days"))
    }

    @Test
    fun neverMoreThanFourNotices() {
        val marks = (1..6).map { MidtermMark("C$it", "Course $it", "F2026", "2026-10-0${(it % 5) + 1}") }
        assertTrue(notices("2026-10-01", marks).size <= 4)
    }
}
