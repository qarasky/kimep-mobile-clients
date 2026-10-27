package dev.qarasky.unofficialkimep.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.qarasky.unofficialkimep.data.export.CalendarExporter
import dev.qarasky.unofficialkimep.data.export.DeviceCalendarWriter
import dev.qarasky.unofficialkimep.vm.CalendarExportViewModel
import dev.qarasky.unofficialkimep.vm.ExportState

@Composable
fun CalendarExportSection(
    exporter: CalendarExporter,
    sessionId: String,
) {
    val viewModel: CalendarExportViewModel = viewModel(
        factory = CalendarExportViewModel.factory(exporter, sessionId),
    )
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.values.all { it }) viewModel.showCalendarPicker() else viewModel.permissionDenied()
    }

    viewModel.icsToShare?.let { uri ->
        LaunchedEffect(uri) {
            shareIcs(context, uri)
            viewModel.icsHandled()
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Export full schedule", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Copies your weekly classes and final exams into a calendar. " +
                    "Running it again replaces what this app added before.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            val working = viewModel.state == ExportState.Working
            Button(
                onClick = {
                    if (viewModel.hasCalendarPermission()) {
                        viewModel.showCalendarPicker()
                    } else {
                        permissionLauncher.launch(DeviceCalendarWriter.PERMISSIONS)
                    }
                },
                enabled = !working,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (working) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("Working…", modifier = Modifier.padding(start = 8.dp))
                } else {
                    Text("Add to Google Calendar or other")
                }
            }
            Text(
                "Pick any calendar on this device: Google, or an account synced through Android.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = viewModel::exportIcs,
                enabled = !working,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Export for Proton Calendar (.ics)") }
            Text(
                "Proton Calendar does not appear in Android's calendar list, so it imports a file instead.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            when (val state = viewModel.state) {
                is ExportState.Success -> Text(
                    state.message,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
                is ExportState.Failure -> Text(
                    state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 12.dp),
                )
                else -> Unit
            }
        }
    }

    viewModel.calendars?.let { calendars ->
        AlertDialog(
            onDismissRequest = viewModel::dismissPicker,
            title = { Text("Choose a calendar") },
            text = {
                Column {
                    calendars.forEach { calendar ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { viewModel.addToCalendar(calendar) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                        ) {
                            Spacer(
                                Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color(calendar.color)),
                            )
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(calendar.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    calendar.account,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissPicker) { Text("Cancel") }
            },
        )
    }
}

/** Hands the .ics to whichever app the user picks (Proton Calendar opens it as an import). */
private fun shareIcs(context: Context, uri: Uri) {
    val view = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, "text/calendar")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(Intent.createChooser(view, "Import schedule with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/calendar")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { context.startActivity(Intent.createChooser(send, "Share schedule").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
