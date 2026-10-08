package dev.qarasky.unofficialkimep.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.qarasky.unofficialkimep.BuildConfig
import dev.qarasky.unofficialkimep.KimepApp
import dev.qarasky.unofficialkimep.data.SessionState
import dev.qarasky.unofficialkimep.data.UpdateCheck
import dev.qarasky.unofficialkimep.ui.components.LoadingState
import kotlinx.coroutines.launch

@Composable
fun KimepRoot() {
    val context = LocalContext.current
    val container = (context.applicationContext as KimepApp).container
    val scope = rememberCoroutineScope()

    val session by container.sessionStore.state
        .collectAsStateWithLifecycle(initialValue = SessionState.Loading)

    var update: UpdateCheck.AppUpdate? by remember { mutableStateOf(null) }

    // Update check, at most once a day. Silent on failure; per-version dismiss.
    LaunchedEffect(Unit) {
        val store = container.updateStore
        if (System.currentTimeMillis() - store.lastCheckMs() < 24 * 60 * 60 * 1000L) return@LaunchedEffect
        val found = UpdateCheck.latestNewerThan(BuildConfig.VERSION_NAME)
        store.saveChecked()
        if (found != null && found.tag != store.dismissedTag()) {
            update = found
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        when (val current = session) {
            SessionState.Loading -> LoadingState()

            SessionState.LoggedOut -> LoginScreen(
                repository = container.repository,
            )

            is SessionState.LoggedIn -> MainScreen(
                session = current,
                repository = container.repository,
                calendarRepository = container.calendarRepository,
                scheduleCache = container.scheduleCache,
                settingsStore = container.settingsStore,
                reminderManager = container.reminderManager,
                onLogout = {
                    scope.launch {
                        container.reminderManager.cancelAll()
                        container.scheduleCache.clear()
                        container.repository.logout()
                    }
                },
            )
        }
    }

    update?.let { pending ->
        UpdateDialog(
            update = pending,
            onDismiss = {
                update = null
                scope.launch { container.updateStore.dismiss(pending.tag) }
            },
        )
    }
}
