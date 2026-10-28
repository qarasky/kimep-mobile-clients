package dev.qarasky.unofficialkimep.di

import android.content.Context
import dev.qarasky.unofficialkimep.data.CalendarRepository
import dev.qarasky.unofficialkimep.data.KimepApi
import dev.qarasky.unofficialkimep.data.KimepRepository
import dev.qarasky.unofficialkimep.data.ScheduleCache
import dev.qarasky.unofficialkimep.data.SessionStore
import dev.qarasky.unofficialkimep.data.MidtermStore
import dev.qarasky.unofficialkimep.data.SettingsStore
import dev.qarasky.unofficialkimep.data.UpdateStore
import dev.qarasky.unofficialkimep.data.calendar.CalendarImporter
import dev.qarasky.unofficialkimep.data.calendar.ImportedCalendars
import dev.qarasky.unofficialkimep.data.calendar.PdfTextExtractor
import dev.qarasky.unofficialkimep.data.calendar.calendarHttpClient
import dev.qarasky.unofficialkimep.data.export.CalendarExporter
import dev.qarasky.unofficialkimep.data.notify.ReminderManager
import dev.qarasky.unofficialkimep.data.notify.ReminderScheduler
import java.io.File

class AppContainer(context: Context, private val api: KimepApi = KimepApi()) {
    private val appContext = context.applicationContext

    val sessionStore: SessionStore = SessionStore(appContext)
    val settingsStore: SettingsStore = SettingsStore(appContext)
    val importedCalendars: ImportedCalendars =
        ImportedCalendars(File(appContext.filesDir, "calendar_imported.json"))
    val calendarRepository: CalendarRepository = CalendarRepository(appContext, importedCalendars)
    val midtermStore: MidtermStore = MidtermStore(appContext)
    val scheduleCache: ScheduleCache = ScheduleCache(appContext)
    val updateStore: UpdateStore = UpdateStore(appContext)

    val repository: KimepRepository = KimepRepository(api, sessionStore)

    val calendarExporter: CalendarExporter =
        CalendarExporter(appContext, repository, scheduleCache, calendarRepository, midtermStore)

    val calendarImporter: CalendarImporter = CalendarImporter(
        store = importedCalendars,
        existing = { calendarRepository.load() },
        onChanged = { calendarRepository.invalidate() },
        readWords = { PdfTextExtractor.words(appContext, it) },
        clientFactory = ::calendarHttpClient,
    )

    private val reminderScheduler: ReminderScheduler = ReminderScheduler(appContext)
    val reminderManager: ReminderManager =
        ReminderManager(repository, sessionStore, settingsStore, reminderScheduler, calendarRepository)
}
