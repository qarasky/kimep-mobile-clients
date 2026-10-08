package dev.qarasky.unofficialkimep

import android.app.Application
import dev.qarasky.unofficialkimep.data.notify.Notifications
import dev.qarasky.unofficialkimep.di.AppContainer

class KimepApp : Application() {

    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
    }
}
