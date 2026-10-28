package dev.qarasky.unofficialkimep.data

import android.content.Context
import dev.qarasky.unofficialkimep.data.calendar.ImportedCalendars
import dev.qarasky.unofficialkimep.data.model.CalendarData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * The academic calendar: the years bundled with the app, overlaid with any years the
 * on-device importer has read from KIMEP's PDFs (an imported year replaces a bundled one).
 */
class CalendarRepository(
    private val context: Context,
    private val imported: ImportedCalendars,
) {

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cached: CalendarData? = null

    suspend fun load(): CalendarData = cached ?: withContext(Dispatchers.IO) {
        val text = context.assets.open("calendar.json").bufferedReader().use { it.readText() }
        val bundled = json.decodeFromString(CalendarData.serializer(), text)
        val extra = imported.read().years
        val years = (bundled.years.filter { b -> extra.none { it.id == b.id } } + extra)
            .sortedByDescending { it.id }
        CalendarData(years).also { cached = it }
    }

    /** Forget the merged copy so the next [load] sees newly imported years. */
    fun invalidate() {
        cached = null
    }
}
