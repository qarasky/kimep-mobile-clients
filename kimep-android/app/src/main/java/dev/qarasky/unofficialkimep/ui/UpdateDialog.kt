package dev.qarasky.unofficialkimep.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import dev.qarasky.unofficialkimep.data.UpdateCheck

@Composable
fun UpdateDialog(
    update: UpdateCheck.AppUpdate,
    onDismiss: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update available") },
        text = {
            Text("Version ${update.tag} is available on GitHub. You're on an older build.")
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
            ) { Text("Close") }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    runCatching { uriHandler.openUri(update.url) }
                    onDismiss()
                },
            ) { Text("Open") }
        },
    )
}
