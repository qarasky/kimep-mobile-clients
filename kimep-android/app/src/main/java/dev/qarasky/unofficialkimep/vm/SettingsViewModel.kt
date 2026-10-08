package dev.qarasky.unofficialkimep.vm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.qarasky.unofficialkimep.data.ReminderSettings
import dev.qarasky.unofficialkimep.data.SettingsStore
import dev.qarasky.unofficialkimep.BuildConfig
import dev.qarasky.unofficialkimep.data.UpdateCheck
import dev.qarasky.unofficialkimep.data.notify.ReminderManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

sealed interface ManualUpdateState {
    data object Idle : ManualUpdateState
    data object Checking : ManualUpdateState
    data class Finished(val result: UpdateCheck.Result) : ManualUpdateState
}

class SettingsViewModel(
    private val settingsStore: SettingsStore,
    private val reminderManager: ReminderManager,
) : ViewModel() {

    val settings: Flow<ReminderSettings> = settingsStore.settings
    var updateState: ManualUpdateState by mutableStateOf(ManualUpdateState.Idle)
        private set

    fun checkForUpdates() {
        if (updateState == ManualUpdateState.Checking) return
        updateState = ManualUpdateState.Checking
        viewModelScope.launch {
            updateState = ManualUpdateState.Finished(UpdateCheck.checkForUpdates(BuildConfig.VERSION_NAME))
        }
    }

    fun dismissUpdate() {
        updateState = ManualUpdateState.Idle
    }

    fun setLessonReminderHour(enabled: Boolean) = update {
        settingsStore.setLessonReminderHour(enabled)
    }

    fun setLessonReminderTenMinutes(enabled: Boolean) = update {
        settingsStore.setLessonReminderTenMinutes(enabled)
    }

    fun setFinalReminders(enabled: Boolean) = update {
        settingsStore.setFinalReminders(enabled)
    }

    /** Apply a preference change, then rebuild alarms from the new state. */
    private fun update(block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            reminderManager.refresh()
        }
    }

    companion object {
        fun factory(
            settingsStore: SettingsStore,
            reminderManager: ReminderManager,
        ) = viewModelFactory {
            initializer {
                SettingsViewModel(settingsStore, reminderManager)
            }
        }
    }
}
