package dev.qarasky.unofficialkimep.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Full-page privacy notice, shown under Settings with the app bar's back arrow. */
@Composable
fun PrivacyNoticeScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        PrivacyNoticeBody()
    }
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
            title = "Calendar export",
            lines = listOf(
                "Exporting your schedule is optional and only starts when you tap a button in Settings → Calendar.",
                "Adding to a calendar asks for Android's Calendar permission. The app uses it to list your calendars and to add, and later replace, the class and exam events it created. It does not read or change your other events.",
                "Once events are in a calendar such as Google Calendar, that calendar app and its provider handle them under their own policies.",
                "The Proton Calendar option creates an .ics file in the app's temporary cache and hands it only to the app you pick.",
            ),
        )
        NoticeSection(
            title = "Academic calendar",
            lines = listOf(
                "The app ships with KIMEP's academic calendar. From June it also checks kimep.kz, at most once a day and only while it is missing the current or next year, and you can check manually in Settings → Academic calendar.",
                "A new calendar PDF is downloaded from kimep.kz and read on your phone. Nothing about you is sent: the requests carry no student ID, session or grades.",
                "Midterm dates you mark for a course are stored only on this device.",
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
                "Logging out clears your saved session, timetable and marked midterm dates, and cancels reminders. Other local preferences remain.",
                "You can revoke the Calendar permission in Android Settings at any time. Events already added stay in your calendar until you delete them there.",
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
