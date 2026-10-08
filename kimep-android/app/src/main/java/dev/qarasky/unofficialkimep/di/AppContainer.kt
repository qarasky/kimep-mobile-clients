package dev.qarasky.unofficialkimep.di

import android.content.Context
import dev.qarasky.unofficialkimep.BuildConfig
import dev.qarasky.unofficialkimep.data.CalendarRepository
import dev.qarasky.unofficialkimep.data.KimepApi
import dev.qarasky.unofficialkimep.data.KimepRepository
import dev.qarasky.unofficialkimep.data.ScheduleCache
import dev.qarasky.unofficialkimep.data.SessionStore
import dev.qarasky.unofficialkimep.data.SettingsStore
import dev.qarasky.unofficialkimep.data.UpdateStore
import dev.qarasky.unofficialkimep.data.analytics.Analytics
import dev.qarasky.unofficialkimep.data.analytics.AnalyticsStore
import dev.qarasky.unofficialkimep.data.analytics.NoOpAnalytics
import dev.qarasky.unofficialkimep.data.analytics.UmamiAnalytics
import dev.qarasky.unofficialkimep.data.notify.ReminderManager
import dev.qarasky.unofficialkimep.data.notify.ReminderScheduler

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val sessionStore: SessionStore = SessionStore(appContext)
    val settingsStore: SettingsStore = SettingsStore(appContext)
    val calendarRepository: CalendarRepository = CalendarRepository(appContext)
    val scheduleCache: ScheduleCache = ScheduleCache(appContext)
    val analyticsStore: AnalyticsStore = AnalyticsStore(appContext)
    val updateStore: UpdateStore = UpdateStore(appContext)

    private val api: KimepApi = KimepApi()
    val repository: KimepRepository = KimepRepository(api, sessionStore)

    private val reminderScheduler: ReminderScheduler = ReminderScheduler(appContext)
    val reminderManager: ReminderManager =
        ReminderManager(repository, sessionStore, settingsStore, reminderScheduler)

    /** True only when a Umami host + website id were configured at build time. */
    val analyticsEnabled: Boolean =
        BuildConfig.UMAMI_HOST.isNotBlank() && BuildConfig.UMAMI_WEBSITE_ID.isNotBlank()

    val analytics: Analytics =
        if (!analyticsEnabled) {
            NoOpAnalytics
        } else {
            UmamiAnalytics(
                host = BuildConfig.UMAMI_HOST,
                websiteId = BuildConfig.UMAMI_WEBSITE_ID,
                store = analyticsStore,
                context = appContext,
                appVersion = BuildConfig.VERSION_NAME,
            )
        }
}
