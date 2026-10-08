package dev.qarasky.unofficialkimep.di

import android.content.Context
import dev.qarasky.unofficialkimep.data.CalendarRepository
import dev.qarasky.unofficialkimep.data.KimepApi
import dev.qarasky.unofficialkimep.data.KimepRepository
import dev.qarasky.unofficialkimep.data.ScheduleCache
import dev.qarasky.unofficialkimep.data.SessionStore
import dev.qarasky.unofficialkimep.data.SettingsStore
import dev.qarasky.unofficialkimep.data.UpdateStore
import dev.qarasky.unofficialkimep.data.notify.ReminderManager
import dev.qarasky.unofficialkimep.data.notify.ReminderScheduler

class AppContainer(context: Context, private val api: KimepApi = KimepApi()) {
    private val appContext = context.applicationContext

    val sessionStore: SessionStore = SessionStore(appContext)
    val settingsStore: SettingsStore = SettingsStore(appContext)
    val calendarRepository: CalendarRepository = CalendarRepository(appContext)
    val scheduleCache: ScheduleCache = ScheduleCache(appContext)
    val updateStore: UpdateStore = UpdateStore(appContext)

    val repository: KimepRepository = KimepRepository(api, sessionStore)

    private val reminderScheduler: ReminderScheduler = ReminderScheduler(appContext)
    val reminderManager: ReminderManager =
        ReminderManager(repository, sessionStore, settingsStore, reminderScheduler)
}
