package dev.qarasky.unofficialkimep.data.model

import kotlinx.serialization.Serializable

/**
 * Academic calendar parsed from the official KIMEP PDFs by tools/parse_calendar.py
 * and bundled as assets/calendar.json.
 */
@Serializable
data class CalendarData(
    val years: List<CalendarYear> = emptyList(),
)

@Serializable
data class CalendarYear(
    val id: String = "",
    val title: String = "",
    val semesters: List<CalendarSemester> = emptyList(),
    val closed: List<CalendarClosure> = emptyList(),
    /** [SOURCE_PDF], [SOURCE_IMPORTED] or [SOURCE_MANUAL]; empty in older data. */
    val source: String = "",
) {
    /** Hand-typed from a scanned PDF, so worth double-checking against KIMEP's own copy. */
    val isManual: Boolean get() = source == SOURCE_MANUAL

    companion object {
        /** Parsed from the PDF at build time by tools/parse_calendar.py. */
        const val SOURCE_PDF = "pdf"
        /** Parsed from the PDF on this device. */
        const val SOURCE_IMPORTED = "imported"
        /** Transcribed by hand because KIMEP published only a scanned image. */
        const val SOURCE_MANUAL = "manual"
    }
}

@Serializable
data class CalendarSemester(
    val id: String = "",
    val name: String = "",
    val events: List<CalendarEvent> = emptyList(),
)

@Serializable
data class CalendarEvent(
    val title: String = "",
    val start: String? = null,
    val end: String? = null,
    val display: String = "",
)

@Serializable
data class CalendarClosure(
    val title: String = "",
    val note: String? = null,
    val dates: List<CalendarEvent> = emptyList(),
)
