package dev.qarasky.unofficialkimep.data.calendar

import dev.qarasky.unofficialkimep.data.model.CalendarClosure
import dev.qarasky.unofficialkimep.data.model.CalendarEvent
import dev.qarasky.unofficialkimep.data.model.CalendarSemester
import dev.qarasky.unofficialkimep.data.model.CalendarYear

/** One word of PDF text with its position, top-left origin (same shape as `pdftotext -bbox`). */
data class PdfWord(val x0: Double, val y: Double, val x1: Double, val text: String)

class CalendarLayoutException(message: String) : Exception(message)

/** Thrown for PDFs that are only a scanned picture, so there is no text to read. */
class NoTextLayerException : Exception("The PDF has no text layer (it is a scanned image)")

/**
 * Rebuilds KIMEP's academic-calendar table from positioned words. This is a port of
 * tools/parse_calendar.py and must stay in step with it (a test parses the same PDFs'
 * words and compares the result with the bundled calendar.json).
 *
 * The calendar is a four-column table (Fall / Spring / Summer1 / Summer2) whose event
 * labels wrap over several lines, so the words are grouped into rows by y, the column
 * centres are taken from the header row, and each value cluster goes to the nearest column.
 */
object CalendarPdfParser {

    private val months = listOf(
        "january", "february", "march", "april", "may", "june",
        "july", "august", "september", "october", "november", "december",
    )
    private val headerTokens = listOf("Fall", "Spring", "Summer1", "Summer2")
    private val noDate = setOf("-", "–", "—", "NA", "N/A", "n/a")

    private val usDate = Regex("""(\d{1,2})/(\d{1,2})/(\d{4})""")
    private val yearRegex = Regex("""(\d{4})""")
    private val dayRegex = Regex("""\b(\d{1,2})\b""")

    fun parse(words: List<PdfWord>, yearId: String): CalendarYear {
        val rows = groupRows(words)

        var header: List<PdfWord>? = null
        var closedY: Double? = null
        var advisingY: Double? = null
        for (row in rows) {
            val texts = row.map { it.text }
            if (header == null && headerTokens.all { tok -> texts.any { it.startsWith(tok) } }) header = row
            if (closedY == null && texts.any { "KIMEP" in it } && texts.any { "closed" in it.lowercase() }) {
                closedY = row[0].y
            }
            if (advisingY == null && texts.any { it == "Advising" }) advisingY = row[0].y
        }
        if (header == null || advisingY == null) throw CalendarLayoutException("layout not recognised")

        val centers = headerTokens.map { tok ->
            val w = header.first { it.text.startsWith(tok) }
            (w.x0 + w.x1) / 2
        }
        val labelMax = centers[0] - 45.0

        val startYear = yearId.substringBefore('-').toInt()
        val endYear = yearId.substringAfter('-').toInt()
        val semesterDefs = listOf(
            "F" to "Fall $startYear",
            "S" to "Spring $endYear",
            "SU1" to "Summer 1 $endYear",
            "SU2" to "Summer 2 $endYear",
        )
        val events = semesterDefs.map { mutableListOf<CalendarEvent>() }
        val closed = mutableListOf<CalendarClosure>()

        var labelLines = mutableListOf<List<PdfWord>>()
        var holidayMode = false

        for (row in rows) {
            val y = row[0].y
            if (y < advisingY - 1) continue

            val joined = row.joinToString(" ") { it.text }
            // the footnote block starts with two or more asterisks at the line start
            if (Regex("""^\*{2,}""").containsMatchIn(joined.trim()) || "Ресми" in joined || "Formally" in joined) break

            if (closedY != null && y > closedY + 1) holidayMode = true
            if (closedY != null && kotlin.math.abs(y - closedY) < 1) {
                // the "KIMEP is closed" heading is not part of the first holiday's label
                labelLines = mutableListOf()
                continue
            }

            val labelWords = row.filter { it.x0 < labelMax }
            val colWords = row.filter { it.x0 >= labelMax }

            if (colWords.isEmpty()) {
                labelLines.add(labelWords)
                continue
            }

            val cols = MutableList(4) { "" }
            for (cl in clusters(colWords)) {
                val span = cl.last().x1 - cl.first().x0
                if (span > 190) continue // a long sentence spanning the table, not a cell
                val center = (cl.first().x0 + cl.last().x1) / 2
                val idx = (0 until 4).minBy { kotlin.math.abs(centers[it] - center) }
                cols[idx] = cl.joinToString(" ") { it.text }.trim()
            }

            val lines = labelLines.filter { it.isNotEmpty() } + (if (labelWords.isNotEmpty()) listOf(labelWords) else emptyList())
            labelLines = mutableListOf()

            if (holidayMode) {
                if (lines.isEmpty()) continue
                val (title, note) = splitHoliday(lines[0], lines.drop(1))
                if (title.isEmpty() || title.lowercase().startsWith("kimep")) continue
                val dates = cols.mapNotNull { c ->
                    val (s, e, d) = normalizeDate(c)
                    if (d.isNullOrEmpty()) null else CalendarEvent(title = "", start = s, end = e, display = d)
                }
                if (dates.isNotEmpty() || note.isNotEmpty()) {
                    closed.add(CalendarClosure(title = title, note = note, dates = dates))
                }
            } else {
                val label = lines.joinToString(" ") { line -> line.joinToString(" ") { it.text } }.trim()
                if (label.isEmpty()) continue
                cols.forEachIndexed { i, c ->
                    if (c.isEmpty()) return@forEachIndexed
                    val (start, end, display) = normalizeDate(c)
                    if (display == null) return@forEachIndexed
                    events[i].add(CalendarEvent(title = label, start = start, end = end, display = display))
                }
            }
        }

        val semesters = semesterDefs.mapIndexed { i, (id, name) ->
            // the same event can repeat a value in adjacent rows
            val unique = events[i].distinctBy { it.title to it.display }
            CalendarSemester(id = id, name = name, events = unique)
        }
        return CalendarYear(id = yearId, title = "Academic Calendar $yearId", semesters = semesters, closed = closed)
    }

    private fun groupRows(words: List<PdfWord>, tol: Double = 3.0): List<List<PdfWord>> {
        val rows = mutableListOf<List<PdfWord>>()
        var current = mutableListOf<PdfWord>()
        var currentY: Double? = null
        for (w in words.sortedWith(compareBy({ it.y }, { it.x0 }))) {
            if (currentY == null || kotlin.math.abs(w.y - currentY) <= tol) {
                current.add(w)
                if (currentY == null) currentY = w.y
            } else {
                rows.add(current.sortedBy { it.x0 })
                current = mutableListOf(w)
                currentY = w.y
            }
        }
        if (current.isNotEmpty()) rows.add(current.sortedBy { it.x0 })
        return rows
    }

    private fun clusters(words: List<PdfWord>, gap: Double = 18.0): List<List<PdfWord>> {
        val result = mutableListOf<List<PdfWord>>()
        var cur = mutableListOf<PdfWord>()
        for (w in words.sortedBy { it.x0 }) {
            if (cur.isNotEmpty() && w.x0 - cur.last().x1 > gap) {
                result.add(cur)
                cur = mutableListOf()
            }
            cur.add(w)
        }
        if (cur.isNotEmpty()) result.add(cur)
        return result
    }

    private fun iso(year: Int, month: Int, day: Int) = "%04d-%02d-%02d".format(year, month, day)

    /** @return (start, end, display); display is null when the cell holds no date at all. */
    internal fun normalizeDate(text: String?): Triple<String?, String?, String?> {
        val raw = (text ?: "").trim()
        if (raw.isEmpty() || raw in noDate) return Triple(null, null, null)

        val t = raw.replace('–', '-').replace('—', '-')

        val us = usDate.findAll(t).toList()
        if (us.isNotEmpty()) {
            val dates = us.map { m ->
                val (mo, da, y) = m.destructured
                iso(y.toInt(), mo.toInt(), da.toInt())
            }
            return Triple(dates.first(), dates.last(), raw)
        }

        val month = months.indexOfFirst { name -> Regex("""\b$name""", RegexOption.IGNORE_CASE).containsMatchIn(t) }
            .takeIf { it >= 0 }?.plus(1) ?: return Triple(null, null, raw)
        val year = yearRegex.find(t)?.value?.toInt() ?: return Triple(null, null, raw)
        val days = dayRegex.findAll(t).map { it.groupValues[1].toInt() }.filter { it in 1..31 }.toList()
        if (days.isEmpty()) return Triple(null, null, raw)
        return Triple(iso(year, month, days.min()), iso(year, month, days.max()), raw)
    }

    /** "Independence Day   NO Make ups required" -> ("Independence Day", "NO Make ups required"). */
    private fun splitHoliday(firstLine: List<PdfWord>, extraLines: List<List<PdfWord>>): Pair<String, String> {
        val title = mutableListOf<String>()
        val note = mutableListOf<String>()
        var prevMax: Double? = null
        var inNote = false
        for (w in firstLine) {
            if (prevMax != null && (w.x0 - prevMax) > 15) inNote = true
            (if (inNote) note else title).add(w.text)
            prevMax = w.x1
        }
        extraLines.forEach { line -> note.addAll(line.map { it.text }) }
        return title.joinToString(" ").trim { it == ' ' || it == '.' } to note.joinToString(" ").trim()
    }
}
