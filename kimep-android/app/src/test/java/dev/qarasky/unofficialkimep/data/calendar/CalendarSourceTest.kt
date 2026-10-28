package dev.qarasky.unofficialkimep.data.calendar

import dev.qarasky.unofficialkimep.data.model.CalendarData
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarSourceTest {

    @Test
    fun findsEachYearOnceAndDerivesTheEnglishFile() {
        val html = """
            <a href="https://www.kimep.kz/current-students/files/2025/04/AY25-26-detailed-KAZ.pdf">KAZ</a>
            <a href='https://www.kimep.kz/current-students/files/2026/10/AY26-27-detailed-KAZ.pdf'>KAZ</a>
            <a href="https://www.kimep.kz/current-students/files/2026/10/AY26-27-detailed-RUS.pdf">RUS</a>
            <a href="/current-students/files/2026/10/AY27-28-detailed-KAZ.pdf">KAZ</a>
        """.trimIndent()
        val found = CalendarSource.parsePage(html)
        assertEquals(listOf("2025-2026", "2026-2027", "2027-2028"), found.map { it.yearId })
        assertEquals(
            "https://www.kimep.kz/current-students/files/2026/10/AY26-27-detailed-ENG.pdf",
            found[1].englishPdfUrl,
        )
        assertEquals(
            "https://www.kimep.kz/current-students/files/2026/10/AY27-28-detailed-ENG.pdf",
            found[2].englishPdfUrl,
        )
    }

    @Test
    fun ignoresOtherHostsAndOddYearPairs() {
        val html = """
            <a href="https://evil.example/files/AY28-29-detailed-KAZ.pdf">x</a>
            <a href="http://www.kimep.kz/files/AY28-29-detailed-KAZ.pdf">x</a>
            <a href="https://www.kimep.kz/files/AY28-30-detailed-KAZ.pdf">x</a>
            <a href="https://www.kimep.kz/files/handbook.pdf">x</a>
        """.trimIndent()
        assertTrue(CalendarSource.parsePage(html).isEmpty())
    }

    private val data: CalendarData = Json { ignoreUnknownKeys = true }
        .decodeFromString(File("src/main/assets/calendar.json").readText())

    @Test
    fun theBundledYearsPassValidation() {
        data.years.forEach { assertNull(it.id, CalendarValidator.problem(it)) }
    }

    @Test
    fun validationRejectsBrokenYears() {
        val year = data.years.first { it.id == "2026-2027" }
        val noFall = year.copy(semesters = year.semesters.map { if (it.id == "F") it.copy(events = emptyList()) else it })
        assertTrue(CalendarValidator.problem(noFall)!!.contains("Fall"))
        assertTrue(CalendarValidator.problem(year.copy(semesters = year.semesters.take(2)))!!.contains("missing"))
        val wrongYear = year.copy(id = "2030-2031")
        assertTrue(CalendarValidator.problem(wrongYear)!!.contains("2026"))
    }
}
