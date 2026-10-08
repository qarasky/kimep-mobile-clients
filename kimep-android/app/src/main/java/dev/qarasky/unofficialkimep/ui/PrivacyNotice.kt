package dev.qarasky.unofficialkimep.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PrivacyNoticeDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.PrivacyTip, contentDescription = null) },
        title = { Text("Privacy notice") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                PrivacyNoticeBody()
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun PrivacyNoticeBody() {
    Column {
        NoticeSection(
            title = "No usage tracking",
            lines = listOf(
                "This build does not send analytics, track screens or taps, or generate a usage-tracking ID.",
                "There is no analytics service or developer-operated account server in this build.",
            ),
        )
        NoticeSection(
            title = "Connecting to KIMEP",
            lines = listOf(
                "Your student ID and password are sent directly to KIMEP's services to sign in. Your password is not saved by the app.",
                "Your session is used to fetch your profile, grades and timetable from KIMEP.",
                "Requests to KIMEP use HTTPS. The university controls its services and how they handle your account data.",
            ),
        )
        NoticeSection(
            title = "On your device",
            lines = listOf(
                "Your session and basic profile, cached timetable, reminder preferences and calculator settings are stored in the app's private storage.",
                "Reminders are scheduled locally on your device.",
                "Android may include stored app data in system backups, depending on your device settings.",
            ),
        )
        NoticeSection(
            title = "Update checks",
            lines = listOf(
                "The app automatically checks GitHub for new releases at most once a day. You can also check manually in Settings → App updates. These requests do not include your student ID, session or grades.",
                "KIMEP and GitHub can see normal connection information, such as your IP address, when you use their services.",
            ),
        )
        NoticeSection(
            title = "Your control",
            lines = listOf(
                "Logging out clears your saved session and timetable and cancels reminders. Other local preferences remain.",
                "To remove all local app data, use Android Settings → Apps → this app → Storage → Clear storage.",
                "This is an unofficial app and is not affiliated with KIMEP University.",
            ),
        )
    }
}

@Composable
private fun NoticeSection(title: String, lines: List<String>) {
    Column(Modifier.padding(bottom = 16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))
        lines.forEach { line ->
            Row(Modifier.padding(bottom = 2.dp)) {
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
