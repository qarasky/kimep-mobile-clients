package dev.qarasky.unofficialkimep.vm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.qarasky.unofficialkimep.data.CalendarRepository
import dev.qarasky.unofficialkimep.data.calendar.CalendarImporter
import dev.qarasky.unofficialkimep.data.calendar.ImportReport
import dev.qarasky.unofficialkimep.data.model.CalendarYear
import dev.qarasky.unofficialkimep.data.notify.ReminderManager
import kotlinx.coroutines.launch

sealed interface ImportUiState {
    data object Idle : ImportUiState
    data object Checking : ImportUiState
    data class Done(val report: ImportReport) : ImportUiState
}

class CalendarImportViewModel(
    private val importer: CalendarImporter,
    private val repository: CalendarRepository,
    private val reminderManager: ReminderManager,
) : ViewModel() {

    var state: ImportUiState by mutableStateOf(ImportUiState.Idle)
        private set

    /** The calendar years the app currently knows, newest first. */
    var years: List<CalendarYear> by mutableStateOf(emptyList())
        private set

    init {
        refreshYears()
    }

    fun check() {
        if (state == ImportUiState.Checking) return
        state = ImportUiState.Checking
        viewModelScope.launch {
            val report = importer.checkNow()
            refreshYears()
            // Holidays and Study Break feed the reminders, so rebuild them with the new data.
            if (report.changedAnything) runCatching { reminderManager.refresh() }
            state = ImportUiState.Done(report)
        }
    }

    private fun refreshYears() {
        viewModelScope.launch {
            years = runCatching { repository.load().years }.getOrDefault(years)
        }
    }

    companion object {
        fun factory(
            importer: CalendarImporter,
            repository: CalendarRepository,
            reminderManager: ReminderManager,
        ) = viewModelFactory {
            initializer { CalendarImportViewModel(importer, repository, reminderManager) }
        }
    }
}
