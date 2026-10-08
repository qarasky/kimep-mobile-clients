package dev.qarasky.unofficialkimep.screenshots

import dev.qarasky.unofficialkimep.data.ApiDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoStudentTest {
    @Test
    fun semesterHasSixThreeCreditCourses() {
        assertEquals(6, DemoStudent.courses.size)
        assertEquals(6, DemoStudent.courses.map { it.code }.distinct().size)
        assertTrue(DemoStudent.courses.all { it.credits == 3 })
        assertEquals(18, DemoStudent.courses.sumOf { it.credits })
        assertEquals(DemoStudent.courses.map { it.code }, DemoStudent.scores.map { it.code })
    }

    @Test
    fun everyCourseRepeatsOnItsCatalogWeekdays() {
        assertEquals(12, DemoStudent.meetings.size)
        assertEquals(12, DemoStudent.meetings.map { it.id }.distinct().size)
        DemoStudent.courses.forEach { course ->
            val meetings = DemoStudent.meetings.filter { it.courseId == course.code }
            assertEquals(course.days.toSet(), meetings.map { it.weekDay }.toSet())
            assertTrue(meetings.all { it.section == course.section && it.instructor == course.instructor && it.hall == course.hall })
            assertTrue(course.days.toSet() in listOf(setOf("Monday", "Wednesday"), setOf("Tuesday", "Thursday"), setOf("Monday", "Wednesday", "Friday")))
        }
    }

    @Test
    fun timetableHasNoConflictsAndAllMeetingsLast75Minutes() {
        DemoStudent.meetings.groupBy { it.weekDay }.values.forEach { meetings ->
            val sorted = meetings.sortedBy { ApiDate.localTime(it.timeFrom) }
            sorted.forEach { meeting ->
                val start = ApiDate.localTime(meeting.timeFrom)!!
                val end = ApiDate.localTime(meeting.timeTo)!!
                assertEquals(75L, java.time.Duration.between(start, end).toMinutes())
            }
            sorted.zipWithNext().forEach { (first, next) ->
                assertTrue(ApiDate.localTime(first.timeTo)!! <= ApiDate.localTime(next.timeFrom)!!)
            }
        }
    }
}
