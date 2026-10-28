package dev.qarasky.unofficialkimep.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.qarasky.unofficialkimep.data.CalendarRepository
import dev.qarasky.unofficialkimep.data.calendar.CalendarImporter
import dev.qarasky.unofficialkimep.data.calendar.ImportReport
import dev.qarasky.unofficialkimep.data.model.CalendarYear
import dev.qarasky.unofficialkimep.data.notify.ReminderManager
import dev.qarasky.unofficialkimep.vm.CalendarImportViewModel
import dev.qarasky.unofficialkimep.vm.ImportUiState

/** Shows which academic calendars are loaded and checks KIMEP for a newer one. */
@Composable
fun AcademicCalendarSection(
    importer: CalendarImporter,
    repository: CalendarRepository,
    reminderManager: ReminderManager,
) {
    val viewModel: CalendarImportViewModel = viewModel(
        factory = CalendarImportViewModel.factory(importer, repository, reminderManager),
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Academic calendars", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Midterm weeks, holidays and Study Break come from KIMEP's calendar. The app checks " +
                    "for the new year's PDF once a day from June, and reads it on this phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            viewModel.years.forEach { year ->
                Text(
                    text = "${year.id.replace("-", "–")} · ${describeSource(year)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (year.isManual) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            val checking = viewModel.state == ImportUiState.Checking
            Button(onClick = viewModel::check, enabled = !checking) {
                if (checking) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("Checking…", modifier = Modifier.padding(start = 8.dp))
                } else {
                    Text("Check for new calendar")
                }
            }
            (viewModel.state as? ImportUiState.Done)?.let { done ->
                val failed = done.report.pageFailed || done.report.failed.isNotEmpty()
                describe(done.report, viewModel.years).forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

private fun describeSource(year: CalendarYear): String = when (year.source) {
    CalendarYear.SOURCE_IMPORTED -> "read from KIMEP's PDF on this phone"
    CalendarYear.SOURCE_MANUAL -> "typed in by hand from a scanned PDF; double-check it"
    else -> "from KIMEP's PDF"
}

private fun describe(report: ImportReport, years: List<CalendarYear>): List<String> {
    fun pretty(id: String) = id.replace("-", "–")
    if (report.pageFailed) {
        return listOf("Couldn't reach KIMEP's calendar page. Check your connection and try again.")
    }
    val lines = mutableListOf<String>()
    report.imported.forEach { lines += "Imported ${pretty(it)} from KIMEP's PDF." }
    report.unchanged.forEach { lines += "${pretty(it)} is already up to date." }
    report.unreadable.forEach { id ->
        val manual = years.firstOrNull { it.id == id }?.isManual == true
        lines += "${pretty(id)} is published only as a scanned image, which can't be read on a phone." +
            if (manual) " The app uses a hand-typed copy; check it against KIMEP's PDF." else ""
    }
    report.failed.forEach { (id, why) -> lines += "${pretty(id)}: $why." }
    if (lines.isEmpty()) lines += "No calendars were found on KIMEP's page."
    return lines
}
