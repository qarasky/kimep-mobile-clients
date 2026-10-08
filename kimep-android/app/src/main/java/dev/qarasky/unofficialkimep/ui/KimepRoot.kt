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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.qarasky.unofficialkimep.BuildConfig
import dev.qarasky.unofficialkimep.KimepApp
import dev.qarasky.unofficialkimep.data.SessionState
import dev.qarasky.unofficialkimep.data.UpdateCheck
import dev.qarasky.unofficialkimep.data.analytics.AnalyticsEvents
import dev.qarasky.unofficialkimep.data.analytics.ConsentState
import dev.qarasky.unofficialkimep.ui.components.LoadingState
import kotlinx.coroutines.launch

@Composable
fun KimepRoot() {
    val context = LocalContext.current
    val container = (context.applicationContext as KimepApp).container
    val scope = rememberCoroutineScope()

    val consent by container.analyticsStore.consent
        .collectAsStateWithLifecycle(initialValue = ConsentState.Loading)
    val session by container.sessionStore.state
        .collectAsStateWithLifecycle(initialValue = SessionState.Loading)

    var showNotice by rememberSaveable { mutableStateOf(false) }
    var update: UpdateCheck.AppUpdate? by remember { mutableStateOf(null) }

    // Opt-out model: Analytics.track() drops the event only if consent was denied.
    LaunchedEffect(Unit) {
        container.analytics.track(AnalyticsEvents.APP_OPEN)
    }

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

    // Show the dismissible notice once, until the user makes a choice.
    LaunchedEffect(consent) {
        if (container.analyticsEnabled && consent == ConsentState.Undecided) {
            showNotice = true
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
                analytics = container.analytics,
            )

            is SessionState.LoggedIn -> MainScreen(
                session = current,
                repository = container.repository,
                calendarRepository = container.calendarRepository,
                scheduleCache = container.scheduleCache,
                settingsStore = container.settingsStore,
                reminderManager = container.reminderManager,
                analyticsStore = container.analyticsStore,
                analytics = container.analytics,
                analyticsEnabled = container.analyticsEnabled,
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

    if (showNotice) {
        AnalyticsFirstRunDialog(
            onKeepEnabled = {
                showNotice = false
                scope.launch {
                    container.analyticsStore.setConsent(true)
                    container.analytics.track(
                        AnalyticsEvents.CONSENT,
                        mapOf("decision" to "granted", "source" to "first_run_notice"),
                    )
                }
            },
            onOptOut = {
                showNotice = false
                scope.launch {
                    container.analyticsStore.setConsent(false)
                    container.analyticsStore.clearIdentity()
                }
            },
        )
    }

    update?.let { pending ->
        UpdateDialog(
            update = pending,
            analytics = container.analytics,
            onDismiss = {
                update = null
                scope.launch { container.updateStore.dismiss(pending.tag) }
            },
        )
    }
}
