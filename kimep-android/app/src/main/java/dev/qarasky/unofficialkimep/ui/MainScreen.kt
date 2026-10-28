package dev.qarasky.unofficialkimep.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import dev.qarasky.unofficialkimep.data.CalendarRepository
import dev.qarasky.unofficialkimep.data.KimepRepository
import dev.qarasky.unofficialkimep.data.ScheduleCache
import dev.qarasky.unofficialkimep.data.SessionState
import dev.qarasky.unofficialkimep.data.MidtermStore
import dev.qarasky.unofficialkimep.data.SettingsStore
import dev.qarasky.unofficialkimep.data.calendar.CalendarImporter
import dev.qarasky.unofficialkimep.data.export.CalendarExporter
import dev.qarasky.unofficialkimep.data.notify.ReminderManager

private data class Destination(val label: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    session: SessionState.LoggedIn,
    repository: KimepRepository,
    calendarRepository: CalendarRepository,
    scheduleCache: ScheduleCache,
    settingsStore: SettingsStore,
    reminderManager: ReminderManager,
    calendarExporter: CalendarExporter,
    midtermStore: MidtermStore,
    calendarImporter: CalendarImporter,
    onLogout: () -> Unit,
) {
    val destinations = listOf(
        Destination("Schedule", Icons.Filled.CalendarMonth),
        Destination("Grades", Icons.Filled.Assessment),
        Destination("Calendar", Icons.AutoMirrored.Filled.EventNote),
        Destination("Profile", Icons.Filled.Person),
    )

    var index by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showPrivacy by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = showSettings) {
        if (showPrivacy) showPrivacy = false else showSettings = false
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = when {
                            showPrivacy -> "Privacy notice"
                            showSettings -> "Settings"
                            else -> destinations[index].label
                        },
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    if (showSettings) {
                        IconButton(onClick = { if (showPrivacy) showPrivacy = false else showSettings = false }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (!showSettings) {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            if (!showSettings) {
                NavigationBar {
                    destinations.forEachIndexed { i, destination ->
                        NavigationBarItem(
                            selected = index == i,
                            onClick = { index = i },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (showSettings && showPrivacy) {
                PrivacyNoticeScreen()
            } else if (showSettings) {
                SettingsScreen(
                    settingsStore = settingsStore,
                    reminderManager = reminderManager,
                    calendarExporter = calendarExporter,
                    calendarImporter = calendarImporter,
                    calendarRepository = calendarRepository,
                    sessionId = session.id,
                    onOpenPrivacy = { showPrivacy = true },
                )
            } else {
                when (index) {
                    0 -> ScheduleScreen(
                        repository = repository,
                        scheduleCache = scheduleCache,
                        calendarRepository = calendarRepository,
                        reminderManager = reminderManager,
                        midtermStore = midtermStore,
                        sessionId = session.id,
                    )

                    1 -> GradesScreen(
                        repository = repository,
                        sessionId = session.id,
                    )

                    2 -> CalendarScreen(
                        repository = calendarRepository,
                    )

                    else -> ProfileScreen(
                        session = session,
                        repository = repository,
                        onLogout = onLogout,
                    )
                }
            }
        }
    }
}
