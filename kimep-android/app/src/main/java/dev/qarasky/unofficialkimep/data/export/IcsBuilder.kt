package dev.qarasky.unofficialkimep.data.export

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Serialises [ScheduleEvent]s to an RFC 5545 calendar. Times are written in UTC, so
 * no VTIMEZONE block is needed (Kazakhstan has no DST, so weekly repeats stay aligned).
 */
object IcsBuilder {

    private val stamp: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

    fun build(events: List<ScheduleEvent>, now: Instant = Instant.now()): String {
        val lines = mutableListOf(
            "BEGIN:VCALENDAR",
            "VERSION:2.0",
            "PRODID:-//Unofficial KIMEP//Schedule//EN",
            "CALSCALE:GREGORIAN",
            "METHOD:PUBLISH",
        )
        events.forEach { event ->
            val start = Instant.ofEpochMilli(event.startMillis)
            val end = start.plusSeconds(event.durationMinutes * 60L)
            lines += "BEGIN:VEVENT"
            lines += "UID:${event.uid}"
            lines += "DTSTAMP:${stamp.format(now)}"
            lines += "DTSTART:${stamp.format(start)}"
            lines += "DTEND:${stamp.format(end)}"
            event.repeatUntilMillis?.let {
                lines += "RRULE:FREQ=WEEKLY;UNTIL=${stamp.format(Instant.ofEpochMilli(it))}"
            }
            lines += "SUMMARY:${escape(event.title)}"
            event.location?.let { lines += "LOCATION:${escape(it)}" }
            event.description?.let { lines += "DESCRIPTION:${escape(it)}" }
            lines += "END:VEVENT"
        }
        lines += "END:VCALENDAR"
        return lines.joinToString("") { fold(it) + "\r\n" }
    }

    internal fun escape(text: String): String = text
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\r\n", "\\n")
        .replace("\n", "\\n")

    /** Folds a content line at 75 octets, continuing with a leading space (RFC 5545 §3.1). */
    internal fun fold(line: String): String {
        if (line.toByteArray(Charsets.UTF_8).size <= 75) return line
        val out = StringBuilder()
        var octets = 0
        var limit = 75
        var i = 0
        while (i < line.length) {
            val cp = line.codePointAt(i)
            val chunk = String(Character.toChars(cp))
            val size = chunk.toByteArray(Charsets.UTF_8).size
            if (octets + size > limit) {
                out.append("\r\n ")
                octets = 0
                limit = 74 // the continuation space counts toward the 75
            }
            out.append(chunk)
            octets += size
            i += Character.charCount(cp)
        }
        return out.toString()
    }
}
