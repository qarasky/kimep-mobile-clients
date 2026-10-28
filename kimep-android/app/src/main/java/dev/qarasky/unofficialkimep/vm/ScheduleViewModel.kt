package dev.qarasky.unofficialkimep.vm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.qarasky.unofficialkimep.data.AcademicCalendar
import dev.qarasky.unofficialkimep.data.CalendarRepository
import dev.qarasky.unofficialkimep.data.KimepRepository
import dev.qarasky.unofficialkimep.data.MidtermStore
import dev.qarasky.unofficialkimep.data.MidtermWindow
import dev.qarasky.unofficialkimep.data.ScheduleCache
import dev.qarasky.unofficialkimep.data.calendar.DayOff
import dev.qarasky.unofficialkimep.data.calendar.DayOffRange
import dev.qarasky.unofficialkimep.data.calendar.DaysOff
import dev.qarasky.unofficialkimep.data.calendar.ScheduleNotice
import dev.qarasky.unofficialkimep.data.calendar.ScheduleNotices
import dev.qarasky.unofficialkimep.data.friendlyMessage
import dev.qarasky.unofficialkimep.data.model.ClassMeeting
import dev.qarasky.unofficialkimep.data.model.FinalExam
import dev.qarasky.unofficialkimep.data.model.MidtermMark
import dev.qarasky.unofficialkimep.data.notify.ReminderManager
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ScheduleUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val meetings: List<ClassMeeting> = emptyList(),
    val finals: List<FinalExam> = emptyList(),
    val fromCache: Boolean = false,
    /** Heads-up lines for today: midterm weeks, days off, upcoming marked midterms. */
    val notices: List<ScheduleNotice> = emptyList(),
    val midtermWindows: List<MidtermWindow> = emptyList(),
    val daysOff: Map<LocalDate, DayOff> = emptyMap(),
    /** The student's marked midterm dates for the current semester. */
    val marks: List<MidtermMark> = emptyList(),
)

class ScheduleViewModel(
    private val repository: KimepRepository,
    private val cache: ScheduleCache,
    private val calendarRepository: CalendarRepository,
    private val reminderManager: ReminderManager,
    private val midtermStore: MidtermStore,
    private val sessionId: String,
) : ViewModel() {

    var uiState by mutableStateOf(ScheduleUiState())
        private set

    private var allMarks: List<MidtermMark> = emptyList()
    private var dayOffRanges: List<DayOffRange> = emptyList()

    init {
        viewModelScope.launch {
            midtermStore.marks.collect { marks ->
                allMarks = marks
                applyCalendarInfo()
            }
        }
        viewModelScope.launch {
            // 1. Show the cached timetable instantly (stale-while-revalidate).
            val cached = runCatching { cache.read() }.getOrNull()
            if (cached != null && cached.meetings.isNotEmpty()) {
                uiState = ScheduleUiState(
                    loading = false,
                    meetings = cached.meetings,
                    finals = cached.finals,
                    fromCache = true,
                )
                loadCalendarInfo(cached.meetings)
            }
            // 2. Refresh in the background.
            load()
        }
    }

    fun load(refresh: Boolean = false) {
        viewModelScope.launch {
            uiState = uiState.copy(
                loading = uiState.meetings.isEmpty(),
                refreshing = refresh,
                error = null,
            )

            val meetingsResult = repository.schedule(sessionId)
            val finalsResult = repository.finalExams(sessionId)
            val hadData = uiState.meetings.isNotEmpty()

            meetingsResult.onSuccess { cache.saveSchedule(it) }
            finalsResult.onSuccess { cache.saveFinals(it) }

            val meetings = meetingsResult.getOrElse { uiState.meetings }

            uiState = uiState.copy(
                loading = false,
                refreshing = false,
                error = if (meetingsResult.isFailure && !hadData) {
                    meetingsResult.exceptionOrNull()?.friendlyMessage()
                } else {
                    null
                },
                meetings = meetings,
                finals = finalsResult.getOrElse { uiState.finals },
                fromCache = meetingsResult.isFailure && hadData,
            )
            loadCalendarInfo(meetings)

            if (meetingsResult.isSuccess) {
                reminderManager.refresh()
            }
        }
    }

    fun removeMark(mark: MidtermMark) {
        viewModelScope.launch { midtermStore.remove(mark) }
    }

    /** Reads the academic calendar for this timetable's semester: midterm weeks and days off. */
    private suspend fun loadCalendarInfo(meetings: List<ClassMeeting>) {
        val code = meetings.firstOrNull()?.semester?.takeIf { it.isNotBlank() }
        val data = runCatching { calendarRepository.load() }.getOrNull() ?: return
        val dayOffs = DaysOff.build(data)
        dayOffRanges = DaysOff.ranges(dayOffs)
        uiState = uiState.copy(
            midtermWindows = code?.let { AcademicCalendar.midtermWindows(data, it) }.orEmpty(),
            daysOff = dayOffs,
        )
        applyCalendarInfo()
    }

    /** Recomputes what depends on both the calendar and the marked midterms. */
    private fun applyCalendarInfo() {
        val code = uiState.meetings.firstOrNull()?.semester?.takeIf { it.isNotBlank() }
        val marks = allMarks.filter { code == null || it.semester == code }
        uiState = uiState.copy(
            marks = marks,
            notices = ScheduleNotices.build(LocalDate.now(), uiState.midtermWindows, dayOffRanges, marks),
        )
    }

    companion object {
        fun factory(
            repository: KimepRepository,
            cache: ScheduleCache,
            calendarRepository: CalendarRepository,
            reminderManager: ReminderManager,
            midtermStore: MidtermStore,
            sessionId: String,
        ) = viewModelFactory {
            initializer {
                ScheduleViewModel(repository, cache, calendarRepository, reminderManager, midtermStore, sessionId)
            }
        }
    }
}
