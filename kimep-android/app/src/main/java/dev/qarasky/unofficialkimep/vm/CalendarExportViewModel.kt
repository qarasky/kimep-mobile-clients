package dev.qarasky.unofficialkimep.vm

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.qarasky.unofficialkimep.data.export.CalendarExporter
import dev.qarasky.unofficialkimep.data.export.DeviceCalendar
import dev.qarasky.unofficialkimep.data.friendlyMessage
import kotlinx.coroutines.launch

sealed interface ExportState {
    data object Idle : ExportState
    data object Working : ExportState
    data class Success(val message: String) : ExportState
    data class Failure(val message: String) : ExportState
}

class CalendarExportViewModel(
    private val exporter: CalendarExporter,
    private val sessionId: String,
) : ViewModel() {

    var state: ExportState by mutableStateOf(ExportState.Idle)
        private set

    /** Non-null while the calendar picker should be shown. */
    var calendars: List<DeviceCalendar>? by mutableStateOf(null)
        private set

    /** Set once an .ics file is ready to hand to another app; cleared by [icsHandled]. */
    var icsToShare: Uri? by mutableStateOf(null)
        private set

    fun hasCalendarPermission(): Boolean = exporter.deviceCalendars.hasPermission()

    fun showCalendarPicker() {
        viewModelScope.launch {
            val found = runCatching { exporter.listCalendars() }
            found.onSuccess {
                if (it.isEmpty()) {
                    state = ExportState.Failure(
                        "No writable calendars found. Add a Google account (or another calendar account) in Android settings.",
                    )
                } else {
                    calendars = it
                }
            }.onFailure { state = ExportState.Failure(it.friendlyMessage()) }
        }
    }

    fun dismissPicker() {
        calendars = null
    }

    fun permissionDenied() {
        state = ExportState.Failure("Calendar permission is needed to add events to your calendar.")
    }

    fun addToCalendar(calendar: DeviceCalendar) {
        calendars = null
        state = ExportState.Working
        viewModelScope.launch {
            exporter.addToCalendar(sessionId, calendar.id)
                .onSuccess {
                    state = ExportState.Success("Added $it entries to ${calendar.name} (${calendar.account}).")
                }
                .onFailure { state = ExportState.Failure(it.friendlyMessage()) }
        }
    }

    fun exportIcs() {
        state = ExportState.Working
        viewModelScope.launch {
            exporter.createIcsFile(sessionId)
                .onSuccess {
                    icsToShare = it.uri
                    state = ExportState.Success(
                        "Created a file with ${it.eventCount} entries. Pick Proton Calendar to import it.",
                    )
                }
                .onFailure { state = ExportState.Failure(it.friendlyMessage()) }
        }
    }

    fun icsHandled() {
        icsToShare = null
    }

    companion object {
        fun factory(exporter: CalendarExporter, sessionId: String) = viewModelFactory {
            initializer { CalendarExportViewModel(exporter, sessionId) }
        }
    }
}
