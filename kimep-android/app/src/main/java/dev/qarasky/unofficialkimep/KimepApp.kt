package dev.qarasky.unofficialkimep

import android.app.Application
import dev.qarasky.unofficialkimep.data.LegacyPrivacyCleanup
import dev.qarasky.unofficialkimep.data.notify.Notifications
import dev.qarasky.unofficialkimep.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class KimepApp : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        applicationScope.launch {
            // Best effort, retried each launch (including after restoring an old backup).
            runCatching { LegacyPrivacyCleanup.removeTrackingPreferences(filesDir) }
        }
    }
}
