package dev.qarasky.unofficialkimep.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import dev.qarasky.unofficialkimep.data.UpdateCheck
import dev.qarasky.unofficialkimep.data.analytics.Analytics
import dev.qarasky.unofficialkimep.data.analytics.AnalyticsEvents

@Composable
fun UpdateDialog(
    update: UpdateCheck.AppUpdate,
    analytics: Analytics,
    onDismiss: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = {
            analytics.track(AnalyticsEvents.UPDATE_DISMISS)
            onDismiss()
        },
        title = { Text("Update available") },
        text = {
            Text("Version ${update.tag} is available on GitHub. You're on an older build.")
        },
        dismissButton = {
            TextButton(
                onClick = {
                    analytics.track(AnalyticsEvents.UPDATE_DISMISS)
                    onDismiss()
                },
            ) { Text("Close") }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    analytics.track(AnalyticsEvents.UPDATE_OPEN)
                    runCatching { uriHandler.openUri(update.url) }
                    onDismiss()
                },
            ) { Text("Open") }
        },
    )
}
