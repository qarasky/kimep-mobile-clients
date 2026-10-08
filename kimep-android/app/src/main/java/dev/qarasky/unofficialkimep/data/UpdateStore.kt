package dev.qarasky.unofficialkimep.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.updateDataStore: DataStore<Preferences> by preferencesDataStore(name = "kimep_updates")

/** Throttles the update check (once a day) and remembers the dismissed tag. */
class UpdateStore(private val context: Context) {

    private object Keys {
        val LAST_CHECK = longPreferencesKey("last_check_ms")
        val DISMISSED_TAG = stringPreferencesKey("dismissed_tag")
    }

    suspend fun lastCheckMs(): Long =
        context.updateDataStore.data.first()[Keys.LAST_CHECK] ?: 0L

    suspend fun saveChecked(nowMs: Long = System.currentTimeMillis()) {
        context.updateDataStore.edit { it[Keys.LAST_CHECK] = nowMs }
    }

    suspend fun dismissedTag(): String? =
        context.updateDataStore.data.first()[Keys.DISMISSED_TAG]

    suspend fun dismiss(tag: String) {
        context.updateDataStore.edit { it[Keys.DISMISSED_TAG] = tag }
    }
}
