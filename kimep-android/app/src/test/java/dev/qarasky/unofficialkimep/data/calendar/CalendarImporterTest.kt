package dev.qarasky.unofficialkimep.data.calendar

import dev.qarasky.unofficialkimep.data.model.CalendarData
import dev.qarasky.unofficialkimep.data.model.CalendarYear
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarImporterTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val day = 24L * 60 * 60 * 1000

    private val page = """
        <a href="https://www.kimep.kz/current-students/files/2025/04/AY25-26-detailed-KAZ.pdf">KAZ</a>
        <a href="https://www.kimep.kz/current-students/files/2026/10/AY26-27-detailed-KAZ.pdf">KAZ</a>
        <a href="https://www.kimep.kz/current-students/files/2026/10/AY27-28-detailed-KAZ.pdf">KAZ</a>
    """.trimIndent()

    private fun words(name: String): List<PdfWord> =
        javaClass.getResourceAsStream("/calendar/$name.tsv")!!.bufferedReader().readLines()
            .filter { it.isNotBlank() }
            .map { line ->
                val p = line.split('\t', limit = 4)
                PdfWord(p[0].toDouble(), p[1].toDouble(), p[2].toDouble(), p[3])
            }

    private fun bundled(): CalendarData =
        json.decodeFromString(File("src/main/assets/calendar.json").readText())

    private class Env(val importer: CalendarImporter, val store: ImportedCalendars, val requests: MutableList<String>, val changes: IntArray, val clock: LongArray)

    /** The PDF "downloads" are the year's file name; [pdfWords] decides what the extractor sees. */
    private fun env(
        existing: CalendarData,
        clockStart: Long = 1_000 * day,
        pageStatus: HttpStatusCode = HttpStatusCode.OK,
        pdfWords: (String) -> List<PdfWord>,
    ): Env {
        val requests = mutableListOf<String>()
        val changes = intArrayOf(0)
        val clock = longArrayOf(clockStart)
        val dir = File.createTempFile("cal", "").also { it.delete(); it.mkdirs() }
        val store = ImportedCalendars(File(dir, "imported.json"))
        val importer = CalendarImporter(
            store = store,
            existing = { existing },
            onChanged = { changes[0]++ },
            readWords = { bytes -> pdfWords(String(bytes)) },
            clientFactory = {
                HttpClient(MockEngine { request ->
                    val url = request.url.toString()
                    requests.add(url)
                    when {
                        url == CalendarSource.PAGE_URL -> respond(page, pageStatus)
                        url.endsWith("-detailed-ENG.pdf") ->
                            respond(Regex("AY\\d\\d-\\d\\d").find(url)!!.value.toByteArray(), HttpStatusCode.OK)
                        else -> respond("", HttpStatusCode.NotFound)
                    }
                })
            },
            now = { clock[0] },
        )
        return Env(importer, store, requests, changes, clock)
    }

    private fun realWords(file: String): (String) -> List<PdfWord> = { name ->
        when (name) {
            "AY25-26" -> words("AY25-26")
            "AY26-27" -> words("AY26-27")
            else -> throw NoTextLayerException()
        }
    }

    @Test
    fun theNetworkIsNotTouchedWhenNothingIsNeeded() = runBlocking {
        // October 2026: the current year (2026-2027) is bundled, the next isn't needed until June.
        val env = env(bundled(), pdfWords = realWords(""))
        assertNull(env.importer.autoCheck(LocalDate.of(2026, 10, 28)))
        assertTrue(env.requests.isEmpty())
    }

    @Test
    fun aScannedPdfIsReportedAndNotDownloadedAgain() = runBlocking {
        // June 2027: 2027-2028 is only a hand transcription, and KIMEP's file is a scan.
        val env = env(bundled(), pdfWords = realWords(""))
        val first = env.importer.autoCheck(LocalDate.of(2027, 6, 5))!!
        assertEquals(listOf("2027-2028"), first.unreadable)
        assertTrue(first.imported.isEmpty())
        assertEquals(1, env.requests.count { it.contains("AY27-28") })

        env.clock[0] += 2 * day
        val second = env.importer.autoCheck(LocalDate.of(2027, 6, 7))
        assertEquals(1, env.requests.count { it.contains("AY27-28") }) // remembered as unreadable
        assertTrue(second == null || second.unreadable.isEmpty())
    }

    @Test
    fun aMissingYearIsImportedAndStored() = runBlocking {
        val env = env(CalendarData(), pdfWords = realWords(""))
        val report = env.importer.autoCheck(LocalDate.of(2025, 8, 20))!!
        assertEquals(listOf("2025-2026"), report.imported)
        assertEquals(1, env.changes[0])
        val stored = env.store.read().years.single()
        assertEquals("2025-2026", stored.id)
        assertEquals(CalendarYear.SOURCE_IMPORTED, stored.source)
        assertEquals(97, CalendarValidator.eventCount(stored))
    }

    @Test
    fun theAutomaticCheckRunsAtMostOncePerDay() = runBlocking {
        val env = env(CalendarData(), pdfWords = realWords(""))
        assertTrue(env.importer.autoCheck(LocalDate.of(2025, 8, 20)) != null)
        val requestsAfterFirst = env.requests.size
        env.clock[0] += day / 2
        assertNull(env.importer.autoCheck(LocalDate.of(2025, 8, 20)))
        assertEquals(requestsAfterFirst, env.requests.size)
    }

    @Test
    fun aManualCheckReportsUnchangedYears() = runBlocking {
        val env = env(bundled(), pdfWords = realWords(""))
        val report = env.importer.checkNow(LocalDate.of(2026, 10, 28))
        assertEquals(listOf("2025-2026", "2026-2027"), report.unchanged)
        assertEquals(listOf("2027-2028"), report.unreadable)
        assertTrue(report.imported.isEmpty())
        assertEquals(0, env.changes[0])
    }

    @Test
    fun aParseThatFailsValidationNeverReplacesData() = runBlocking {
        // Pretend the 2027-2028 file contains last year's table: the dates are a year out.
        val env = env(CalendarData(), pdfWords = { words("AY26-27") })
        val report = env.importer.autoCheck(LocalDate.of(2027, 9, 1))!!
        assertTrue(report.imported.isEmpty())
        assertTrue(report.failed.containsKey("2027-2028"))
        assertTrue(env.store.read().years.isEmpty())
    }

    @Test
    fun whenTheCalendarPageIsDownWeRetrySoon() = runBlocking {
        val env = env(CalendarData(), pageStatus = HttpStatusCode.InternalServerError, pdfWords = realWords(""))
        val report = env.importer.autoCheck(LocalDate.of(2025, 8, 20))!!
        assertTrue(report.pageFailed)
        env.clock[0] += 7 * 60 * 60 * 1000 // 7 hours later
        assertTrue(env.importer.autoCheck(LocalDate.of(2025, 8, 20)) != null)
    }

    @Test
    fun neededYearsFollowTheAcademicYear() {
        assertEquals(listOf("2026-2027"), CalendarImporter.neededYearIds(LocalDate.of(2026, 10, 28)))
        assertEquals(listOf("2026-2027"), CalendarImporter.neededYearIds(LocalDate.of(2027, 3, 1)))
        assertEquals(listOf("2026-2027", "2027-2028"), CalendarImporter.neededYearIds(LocalDate.of(2027, 6, 1)))
        assertEquals(listOf("2027-2028"), CalendarImporter.neededYearIds(LocalDate.of(2027, 8, 1)))
    }
}
