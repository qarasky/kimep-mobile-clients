package dev.qarasky.unofficialkimep.data.calendar

import java.net.URI

/** Finds the published academic-calendar PDFs on KIMEP's calendar page. */
object CalendarSource {
    const val PAGE_URL = "https://www.kimep.kz/current-students/kz/calendar/"

    /** [englishPdfUrl] is the English edition of the calendar for [yearId] ("2027-2028"). */
    data class Published(val yearId: String, val englishPdfUrl: String)

    private val link = Regex(
        """href\s*=\s*["']([^"']*AY(\d{2})-(\d{2})-detailed-(?:KAZ|ENG|RUS)\.pdf)["']""",
        RegexOption.IGNORE_CASE,
    )
    private val language = Regex("""-detailed-(?:KAZ|RUS)\.pdf$""", RegexOption.IGNORE_CASE)

    /**
     * The page links the Kazakh PDFs; the English and Russian editions sit in the same folder
     * with the language swapped, so the English URL is derived. Only KIMEP's own hosts count.
     */
    fun parsePage(html: String, pageUrl: String = PAGE_URL): List<Published> {
        val base = URI(pageUrl)
        return link.findAll(html).mapNotNull { m ->
            val first = m.groupValues[2].toInt()
            val second = m.groupValues[3].toInt()
            if (second != (first + 1) % 100) return@mapNotNull null
            val url = runCatching { base.resolve(m.groupValues[1]) }.getOrNull() ?: return@mapNotNull null
            val host = url.host?.lowercase() ?: return@mapNotNull null
            if (url.scheme != "https" || !(host == "kimep.kz" || host.endsWith(".kimep.kz"))) return@mapNotNull null
            Published(
                yearId = "20%02d-20%02d".format(first, second),
                englishPdfUrl = language.replace(url.toString(), "-detailed-ENG.pdf"),
            )
        }.distinctBy { it.yearId }.toList()
    }
}
