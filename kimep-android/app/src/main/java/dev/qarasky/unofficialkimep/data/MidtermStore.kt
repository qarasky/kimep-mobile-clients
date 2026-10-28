package dev.qarasky.unofficialkimep.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.qarasky.unofficialkimep.data.model.MidtermMark
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.midtermDataStore: DataStore<Preferences> by preferencesDataStore(name = "kimep_midterms")

/** The midterm dates the student marked per course, kept on the device only. */
class MidtermStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val key = stringPreferencesKey("marks_json")

    val marks: Flow<List<MidtermMark>> = context.midtermDataStore.data.map { decode(it[key]) }

    suspend fun current(): List<MidtermMark> = marks.first()

    suspend fun add(mark: MidtermMark) = edit { list ->
        if (list.any { it.courseKey == mark.courseKey && it.date == mark.date }) list else list + mark
    }

    suspend fun remove(mark: MidtermMark) = edit { list ->
        list.filterNot { it.courseKey == mark.courseKey && it.date == mark.date }
    }

    suspend fun clear() {
        context.midtermDataStore.edit { it.clear() }
    }

    private suspend fun edit(transform: (List<MidtermMark>) -> List<MidtermMark>) {
        context.midtermDataStore.edit { prefs ->
            val next = transform(decode(prefs[key])).sortedBy { it.date }
            prefs[key] = json.encodeToString(ListSerializer(MidtermMark.serializer()), next)
        }
    }

    private fun decode(raw: String?): List<MidtermMark> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(ListSerializer(MidtermMark.serializer()), raw) }
            .getOrDefault(emptyList())
    }
}
