package dev.qarasky.unofficialkimep.data

import dev.qarasky.unofficialkimep.data.export.IcsBuilder
import dev.qarasky.unofficialkimep.data.export.ScheduleEvent
import dev.qarasky.unofficialkimep.data.model.ClassMeeting
import dev.qarasky.unofficialkimep.data.model.FinalExam
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleExportTest {

    private val oleBase = LocalDate.of(1899, 12, 30)

    private fun date(d: LocalDate) =
        "/Date(${d.atStartOfDay().toInstant(ApiDate.KIMEP_OFFSET).toEpochMilli()})/"

    private fun time(t: LocalTime) =
        "/Date(${oleBase.atTime(t).toInstant(ApiDate.KIMEP_TIME_OFFSET).toEpochMilli()})/"

    private fun meeting(
        day: String = "Tuesday",
        from: LocalDate = LocalDate.of(2026, 9, 1), // a Tuesday
        to: LocalDate = LocalDate.of(2026, 12, 15),
        start: LocalTime = LocalTime.of(10, 30),
        end: LocalTime? = LocalTime.of(11, 45),
    ) = ClassMeeting(
        id = 7,
        weekDay = day,
        title = "Macroeconomics, Intro; Part 1",
        courseId = "ECON101",
        hall = "B-210",
        instructor = "Dr. Kim",
        section = "A",
        timeFrom = time(start),
        timeTo = end?.let(::time),
        dateFrom = date(from),
        dateTo = date(to),
    )

    @Test
    fun classRepeatsWeeklyFromFirstMatchingWeekday() {
        // Range starts on a Tuesday but the class is on Thursday -> first one is Sept 3.
        val event = ScheduleEvent.from(listOf(meeting(day = "Thursday")), emptyList()).single()
        val start = Instant.ofEpochMilli(event.startMillis).atOffset(ApiDate.KIMEP_OFFSET)
        assertEquals(LocalDate.of(2026, 9, 3), start.toLocalDate())
        assertEquals(LocalTime.of(10, 30), start.toLocalTime())
        assertEquals(75, event.durationMinutes)
        assertNotNull(event.repeatUntilMillis)
        val until = Instant.ofEpochMilli(event.repeatUntilMillis!!).atOffset(ApiDate.KIMEP_OFFSET)
        assertEquals(LocalDate.of(2026, 12, 15), until.toLocalDate())
    }

    @Test
    fun missingEndTimeFallsBackToDefaultDuration() {
        val event = ScheduleEvent.from(listOf(meeting(end = null)), emptyList()).single()
        assertEquals(75, event.durationMinutes)
    }

    @Test
    fun singleOccurrenceIsNotRepeating() {
        val day = LocalDate.of(2026, 9, 1)
        val event = ScheduleEvent.from(listOf(meeting(from = day, to = day)), emptyList()).single()
        assertNull(event.repeatUntilMillis)
    }

    @Test
    fun meetingWithNoOccurrenceInRangeIsSkipped() {
        // Tuesday-only range that contains no Thursday.
        val day = LocalDate.of(2026, 9, 1)
        assertTrue(ScheduleEvent.from(listOf(meeting(day = "Thursday", from = day, to = day)), emptyList()).isEmpty())
    }

    @Test
    fun finalExamIsAOneOffEventWithDefaults() {
        val exam = FinalExam(id = 3, title = "Calculus", date = date(LocalDate.of(2026, 12, 20)), hall = "A-101")
        val event = ScheduleEvent.from(emptyList(), listOf(exam)).single()
        val start = Instant.ofEpochMilli(event.startMillis).atOffset(ApiDate.KIMEP_OFFSET)
        assertEquals("Final exam: Calculus", event.title)
        assertEquals(LocalTime.of(9, 0), start.toLocalTime())
        assertEquals(120, event.durationMinutes)
        assertNull(event.repeatUntilMillis)
    }

    @Test
    fun icsContainsUtcTimesRecurrenceAndEscapedText() {
        val events = ScheduleEvent.from(listOf(meeting()), emptyList())
        val ics = IcsBuilder.build(events, now = Instant.parse("2026-08-20T00:00:00Z"))
        assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\n"))
        assertTrue(ics.endsWith("END:VCALENDAR\r\n"))
        // 10:30 at UTC+5 on 2026-09-01 is 05:30Z; 75 minutes later is 06:45Z.
        assertTrue(ics, ics.contains("DTSTART:20260901T053000Z\r\n"))
        assertTrue(ics, ics.contains("DTEND:20260901T064500Z\r\n"))
        assertTrue(ics, ics.contains("RRULE:FREQ=WEEKLY;UNTIL=20261215T185959Z\r\n"))
        assertTrue(ics, ics.contains("SUMMARY:Macroeconomics\\, Intro\\; Part 1\r\n"))
        assertTrue(ics, ics.contains("DESCRIPTION:Course: ECON101\\nSection: A\\nInstructor: Dr. Kim\r\n"))
        assertTrue(ics, ics.contains("UID:kimep-class-7@unofficialkimep\r\n"))
    }

    @Test
    fun longLinesFoldAtSeventyFiveOctets() {
        val folded = IcsBuilder.fold("SUMMARY:" + "é".repeat(100))
        folded.split("\r\n").forEachIndexed { i, line ->
            val octets = line.toByteArray(Charsets.UTF_8).size
            assertTrue("line $i has $octets octets", octets <= 75)
        }
        assertEquals("SUMMARY:" + "é".repeat(100), folded.replace("\r\n ", ""))
    }
}
