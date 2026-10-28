package dev.qarasky.unofficialkimep.data.model

import kotlinx.serialization.Serializable

/**
 * A midterm date the student marked for one of their courses. KIMEP doesn't publish
 * individual midterm dates, so these come from the student (usually relayed by the instructor).
 */
@Serializable
data class MidtermMark(
    /** Course id, or the title when the timetable has none; stable across a course's meetings. */
    val courseKey: String,
    val courseTitle: String,
    /** Timetable semester code ("F2026"), so old semesters don't clutter the list. */
    val semester: String,
    /** ISO date. */
    val date: String,
) {
    val localDate: java.time.LocalDate? get() = runCatching { java.time.LocalDate.parse(date) }.getOrNull()

    companion object {
        fun courseKey(meeting: ClassMeeting): String = meeting.courseId?.takeIf { it.isNotBlank() } ?: meeting.title
    }
}
