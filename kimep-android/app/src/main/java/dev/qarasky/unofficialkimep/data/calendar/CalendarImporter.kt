package dev.qarasky.unofficialkimep.data.calendar

import dev.qarasky.unofficialkimep.data.model.CalendarData
import dev.qarasky.unofficialkimep.data.model.CalendarYear
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What a calendar check found, for the Settings screen. */
data class ImportReport(
    val imported: List<String> = emptyList(),
    val unchanged: List<String> = emptyList(),
    /** Published only as a scanned image, so it can't be read on the device. */
    val unreadable: List<String> = emptyList(),
    val failed: Map<String, String> = emptyMap(),
    /** The calendar page itself couldn't be fetched. */
    val pageFailed: Boolean = false,
) {
    val changedAnything: Boolean get() = imported.isNotEmpty()
}

/**
 * Downloads KIMEP's published academic-calendar PDFs and parses them on the device.
 *
 * Automatic checks only touch the network once a year's calendar is actually needed (the
 * current academic year from August, the next one from June) and still missing, or only
 * available as a hand transcription; they run at most once a day. A manual check re-reads
 * every published year. A year that fails validation never replaces existing data.
 */
class CalendarImporter(
    private val store: ImportedCalendars,
    private val existing: suspend () -> CalendarData,
    private val onChanged: () -> Unit,
    private val readWords: suspend (ByteArray) -> List<PdfWord>,
    private val clientFactory: () -> HttpClient,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /** Runs when a calendar is due; null means nothing to do (no network was used). */
    suspend fun autoCheck(today: LocalDate = LocalDate.now()): ImportReport? {
        val state = store.read()
        if (now() - state.lastCheckMs < DAY_MS) return null
        val data = existing()
        if (neededYearIds(today).none { id -> data.years.firstOrNull { it.id == id }.let { it == null || it.isManual } }) {
            return null
        }
        return run(force = false, today = today)
    }

    suspend fun checkNow(today: LocalDate = LocalDate.now()): ImportReport = run(force = true, today = today)

    private suspend fun run(force: Boolean, today: LocalDate): ImportReport {
        val client = clientFactory()
        try {
            val published = try {
                val response = client.get(CalendarSource.PAGE_URL) { header(HttpHeaders.UserAgent, USER_AGENT) }
                if (!response.status.isSuccess()) return pageFailed()
                CalendarSource.parsePage(response.bodyAsText())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return pageFailed()
            }

            val data = existing()
            val state = store.read()
            val needed = neededYearIds(today)
            val candidates = published.filter { p ->
                force || (
                    p.yearId in needed &&
                        data.years.firstOrNull { it.id == p.yearId }.let { it == null || it.isManual } &&
                        p.englishPdfUrl !in state.unreadableUrls
                    )
            }

            val imported = mutableListOf<String>()
            val unchanged = mutableListOf<String>()
            val unreadable = mutableListOf<String>()
            val failed = linkedMapOf<String, String>()
            val unreadableUrls = state.unreadableUrls.toMutableList()
            var current = data

            for (p in candidates) {
                try {
                    val year = download(client, p)
                    val old = current.years.firstOrNull { it.id == p.yearId }
                    when {
                        old != null && !old.isManual && old.copy(source = "") == year.copy(source = "") ->
                            unchanged.add(p.yearId)
                        old != null && !old.isManual &&
                            CalendarValidator.eventCount(year) < CalendarValidator.eventCount(old) * 0.8 ->
                            failed[p.yearId] = "the new file looks incomplete"
                        else -> {
                            store.update { s ->
                                s.copy(years = s.years.filter { it.id != p.yearId } + year)
                            }
                            current = current.copy(years = current.years.filter { it.id != p.yearId } + year)
                            imported.add(p.yearId)
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: NoTextLayerException) {
                    unreadable.add(p.yearId)
                    if (p.englishPdfUrl !in unreadableUrls) unreadableUrls.add(p.englishPdfUrl)
                } catch (e: Exception) {
                    failed[p.yearId] = e.message?.takeIf { it.isNotBlank() } ?: "couldn't be read"
                }
            }

            store.update { it.copy(lastCheckMs = now(), unreadableUrls = unreadableUrls) }
            if (imported.isNotEmpty()) onChanged()
            return ImportReport(imported, unchanged, unreadable, failed)
        } finally {
            client.close()
        }
    }

    private suspend fun pageFailed(): ImportReport {
        // Look again in about six hours rather than tomorrow.
        store.update { it.copy(lastCheckMs = now() - DAY_MS + RETRY_MS) }
        return ImportReport(pageFailed = true)
    }

    private suspend fun download(client: HttpClient, published: CalendarSource.Published): CalendarYear {
        val response: HttpResponse = client.get(published.englishPdfUrl) { header(HttpHeaders.UserAgent, USER_AGENT) }
        if (!response.status.isSuccess()) error("download failed (${response.status.value})")
        val bytes: ByteArray = response.body()
        if (bytes.size > MAX_PDF_BYTES) error("the file is unexpectedly large")

        val words = withContext(Dispatchers.Default) { readWords(bytes) }
        val parsed = try {
            CalendarPdfParser.parse(words, published.yearId)
        } catch (e: CalendarLayoutException) {
            error("the layout isn't recognised")
        }
        val year = parsed.copy(source = CalendarYear.SOURCE_IMPORTED)
        CalendarValidator.problem(year)?.let { error(it) }
        return year
    }

    companion object {
        private const val DAY_MS = 24L * 60 * 60 * 1000
        private const val RETRY_MS = 6L * 60 * 60 * 1000
        private const val MAX_PDF_BYTES = 10 * 1024 * 1024
        private const val USER_AGENT = "UnofficialKimepApp (Android)"

        /** "2026-2027": the academic year runs from the August Fall semester to the next summer. */
        fun yearIdContaining(date: LocalDate): String {
            val start = if (date.monthValue >= 8) date.year else date.year - 1
            return "$start-${start + 1}"
        }

        /** The current academic year, plus the next one from June (when it's usually published). */
        fun neededYearIds(today: LocalDate): List<String> {
            val current = yearIdContaining(today)
            val start = current.substringBefore('-').toInt()
            return if (today.monthValue in 6..7) listOf(current, "${start + 1}-${start + 2}") else listOf(current)
        }
    }
}
