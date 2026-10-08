package dev.qarasky.unofficialkimep.screenshots

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import dev.qarasky.unofficialkimep.data.SessionState
import dev.qarasky.unofficialkimep.di.AppContainer
import dev.qarasky.unofficialkimep.ui.MainScreen
import dev.qarasky.unofficialkimep.ui.theme.KimepTheme
import kotlinx.coroutines.launch

/** Exists only in the isolated screenshots APK; never included in production builds. */
class DemoActivity : ComponentActivity() {
    private val api = DemoStudent.api()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = AppContainer(this, api)
        lifecycleScope.launch {
            container.sessionStore.save("DEMO-0001", "screenshot-demo", null, DemoStudent.profile)
            // The sample timetable must never generate notifications on the user's phone.
            container.settingsStore.setLessonReminderHour(false)
            container.settingsStore.setLessonReminderTenMinutes(false)
            container.settingsStore.setFinalReminders(false)
            setContent {
                KimepTheme {
                    MainScreen(
                        session = SessionState.LoggedIn("screenshot-demo", "DEMO-0001", "Alex", "Student", DemoStudent.profile.programId, null),
                        repository = container.repository,
                        calendarRepository = container.calendarRepository,
                        scheduleCache = container.scheduleCache,
                        settingsStore = container.settingsStore,
                        reminderManager = container.reminderManager,
                        onLogout = { finish() },
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        api.http.close()
    }
}
