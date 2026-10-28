package dev.qarasky.unofficialkimep.data.calendar

import dev.qarasky.unofficialkimep.data.model.CalendarYear
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What the on-device importer has learned: the calendar years it parsed, when it last
 * looked, and PDFs it could not read (so it doesn't download them again every day).
 */
@Serializable
data class ImportedState(
    val years: List<CalendarYear> = emptyList(),
    val lastCheckMs: Long = 0L,
    val unreadableUrls: List<String> = emptyList(),
)

/** One JSON file in the app's private storage. A corrupt or missing file reads as empty. */
class ImportedCalendars(private val file: File) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()

    suspend fun read(): ImportedState = mutex.withLock { readLocked() }

    suspend fun update(transform: (ImportedState) -> ImportedState) {
        mutex.withLock {
            val next = transform(readLocked())
            withContext(Dispatchers.IO) {
                file.parentFile?.mkdirs()
                val tmp = File(file.path + ".tmp")
                tmp.writeText(json.encodeToString(ImportedState.serializer(), next))
                if (!tmp.renameTo(file)) {
                    file.delete()
                    check(tmp.renameTo(file)) { "Couldn't save the imported calendar" }
                }
            }
        }
    }

    private suspend fun readLocked(): ImportedState = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext ImportedState()
        runCatching { json.decodeFromString(ImportedState.serializer(), file.readText()) }
            .getOrDefault(ImportedState())
    }
}
