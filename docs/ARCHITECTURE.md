# Android architecture

Single-module Kotlin app (`kimep-android/app`), Jetpack Compose + Material 3, no DI
framework (a small hand-rolled container) and no navigation library (state-driven
screens). Package root: `dev.qarasky.unofficialkimep`.

## Package layout

```
dev.qarasky.unofficialkimep
├── KimepApp           Application; creates notification channels, owns AppContainer
├── MainActivity       edge-to-edge host, requests POST_NOTIFICATIONS
├── di/AppContainer    wires api + stores + repositories (singletons)
├── data/
│   ├── KimepApi               Ktor HTTP client, one method per endpoint
│   ├── KimepRepository        remote calls -> Result<T>, writes session
│   ├── CalendarRepository     loads assets/calendar.json (cached in memory)
│   ├── ApiDate                ASP.NET date/time decoding
│   ├── Errors                 Throwable -> user-facing message
│   ├── SessionStore           DataStore: session GUID + profile
│   ├── SettingsStore          DataStore: reminder toggles + scheduled ids
│   ├── ScheduleCache          DataStore: last good timetable (JSON)
│   ├── AcademicCalendar       progress deadlines -> early + official midterm windows
│   ├── MidtermStore           DataStore: midterm dates the user marked per course
│   ├── calendar/              on-device calendar import + holidays (see below)
│   ├── model/                 @Serializable DTOs
│   ├── export/                timetable -> system calendar / .ics file
│   └── notify/                reminder scheduling + notifications
├── ui/                Compose screens (see below)
└── vm/                ViewModels, one per screen
```

## Data flow

```
Compose screen ──collects──> ViewModel.uiState (Compose state)
        │                         │
        │ user actions            │ viewModelScope
        v                         v
   ViewModel ──────────────> KimepRepository ──> KimepApi ──> https://www.kimep.kz/ext/mobile
                                   │
                                   ├──> ScheduleCache  (stale-while-revalidate)
                                   ├──> SessionStore   (session GUID)
                                   └──> ReminderManager (AlarmManager)
```

Screens are dumb: they render `uiState` and forward intents. ViewModels own coroutines and
expose an immutable `data class` state via `mutableStateOf`. Repositories return
`Result<T>` and never touch Compose.

## API layer

`KimepApi` is a thin Ktor client (`OkHttp` engine, kotlinx.serialization). Every call is a
JSON `POST` whose body carries the session GUID as `"id"`; there is no `Authorization`
header. See `docs/KIMEP_Mobile_API.md`.

```kotlin
suspend fun schedule(id: String): List<ClassMeeting> = post("schedule/personal", IdRequest(id))
```

`post` is an `inline reified` helper that throws `ApiException(status, …)` on non-2xx.

## Storage

| Store | DataStore file | Contents |
|---|---|---|
| `SessionStore` | `kimep_session` | session GUID, student id, name, program, expiry |
| `SettingsStore` | `kimep_settings` | reminder toggles, ids of scheduled alarms |
| `ScheduleCache` | `kimep_cache` | last good timetable + finals (JSON), updated-at |

`SessionStore.state` emits a `Loading` placeholder first (`onStart`). Consumers that need
the real value must use `first { it !is SessionState.Loading }` — taking `.first()` returns
the placeholder.

### Stale-while-revalidate

`ScheduleViewModel.init` reads `ScheduleCache`, renders it immediately, then calls the
network. The fresh response overwrites the cache and the UI. A failed refresh keeps the
cached data on screen (only a cold, empty load shows an error).

## Reminders

| Class | Role |
|---|---|
| `ReminderManager` | single entry point; reads settings + session, fetches the schedule, (re)schedules everything |
| `ReminderScheduler` | computes occurrences and sets `AlarmManager` exact alarms |
| `ReminderReceiver` | `BroadcastReceiver` that posts the notification |
| `BootReceiver` | rebuilds alarms after reboot / app update |
| `Notifications` | channel definitions |

Request codes are deterministic — `meetingId * 1000 + weekIndex * 2 + leadKind`
(`leadKind` 0 = 1 hour, 1 = 10 minutes) — so previously scheduled alarms can be cancelled
without persisting each one. Only the meeting *ids* are stored (in `SettingsStore`).
Finals use a separate code base. Alarms are exact when
`AlarmManager.canScheduleExactAlarms()`; otherwise they fall back to inexact.

`ReminderManager.refresh()` is called on app start (via the schedule load), after a
schedule refresh, after a settings change, and after reboot.

## Calendar export

Settings → Calendar sends the full timetable (weekly classes + final exams) elsewhere.
`ScheduleEvent.from` turns meetings/finals into neutral events (first matching weekday,
duration, weekly repeat until `Date_To`, wall-clock times pinned to KIMEP's UTC+5), and
two writers consume them:

| Class | Role |
|---|---|
| `DeviceCalendarWriter` | lists writable calendars from `CalendarContract` and inserts recurring events (Google Calendar, DAVx5, any synced account). Needs `READ/WRITE_CALENDAR`. Events are tagged via `CUSTOM_APP_PACKAGE`/`CUSTOM_APP_URI` so a re-export replaces them |
| `IcsBuilder` | RFC 5545 output (UTC times, `RRULE:FREQ=WEEKLY`), shared through a `FileProvider` URI |
| `CalendarExporter` | facade: live timetable, falling back to `ScheduleCache` |

Proton Calendar keeps its events outside Android's calendar provider, so it is reached via
the `.ics` path (Proton's Android app imports `.ics` files opened with it).

## Academic calendar

`assets/calendar.json` (built by `tools/parse_calendar.py`) is the baseline. The app can
add newer years at runtime; `CalendarRepository` merges both and the imported copy wins.

| Class (`data/calendar`) | Role |
|---|---|
| `CalendarSource` | finds the English PDF links on KIMEP's calendar page (kimep.kz hosts only) |
| `PdfTextExtractor` | pdfbox-android text extraction with glyph positions (page 1) |
| `CalendarPdfParser` | Kotlin port of the Python parser: words -> rows -> Fall/Spring/Summer columns |
| `CalendarValidator` | rejects implausible results, so a bad parse never replaces good data |
| `CalendarImporter` | runs the above. `autoCheck` is rate-limited (daily, only when a needed year is missing); `checkNow` is the Settings button. Scanned PDFs are remembered as unreadable |
| `ImportedCalendars` | file-backed store for imported years |
| `DaysOff` | holidays ("KIMEP is closed"), dates named in notes, make-up wording and Study Break weeks, as day-off ranges |
| `ScheduleNotices` | banners for the Schedule tab (midterm weeks, upcoming days off, marked midterms) |

Midterms: the progress-grade deadline `D` ends the "Progress Submission" event. The
official midterm week is the Mon–Sun week before Monday(`D`); professors often start a
week earlier, so that week is shown as "midterms may start". Both come from
`AcademicCalendar.midtermWindows`. Days off remove classes from the schedule view,
reminders and exports (single `EXDATE`s on recurring events). Users can also mark a
specific midterm date per course (`MidtermStore`); it appears in the Schedule's Midterms
tab, the notice banners and exports as an all-day event.

Years whose PDF is only a scanned image cannot be read on the phone. Those are typed in
by hand (`tools/calendar_manual/`), flagged `"source": "manual"` and shown with a
"double-check" warning until a readable PDF replaces them.

## Date & time

All API dates are ASP.NET JSON dates: `"\/Date(<epoch millis>)\/"`. Two encodings exist
(`data/ApiDate.kt`):

- **Absolute dates** (`Date_From`, `Date_To`, `ExpiredOn`): epoch ms, interpreted at
  **UTC+5**.
- **Time-of-day** (`Time_From`, `Time_To`): encoded on the OLE base date 1899-12-30 with the
  university's **legacy UTC+6** offset (Kazakhstan moved to UTC+5 in 2024). Reading these
  as +5 shows every lesson an hour early, so times use a fixed +6.

A fixed offset is used deliberately instead of `ZoneId` so 1899 dates aren't affected by
timezone-database history. Alarm times are then built from the decoded local wall-clock
time in the device's default zone.

## UI

State-driven, no Navigation library. `KimepRoot` observes `SessionStore.state` and shows:

```
Loading -> LoadingState
LoggedOut -> LoginScreen
LoggedIn  -> MainScreen (bottom bar: Schedule · Grades · Calendar · Profile)
```

- `MainScreen` owns the top bar, bottom bar and an in-place **Settings** screen (gear
  action), so there's no back-stack to manage.
- `ScheduleScreen` has a Classes/Finals filter chip row, highlights today, and shows a
  midterm banner when `ScheduleUiState.midterm` is set.
- `CalendarScreen` computes `Past/Current/Future` per event, marks **Now**/**Next**, and
  scrolls the list to the first current-or-upcoming item on load.
- Theme: Material 3 with **dynamic colour** on Android 12+, a hand-tuned fallback palette,
  light/dark, edge-to-edge.

## Dependency wiring

`AppContainer` (created lazily by `KimepApp`) holds the singletons. Screens obtain the
container from the Application and pass what they need to `viewModel(factory = …)`
factories. There is no reflection-based DI.

## Adding an endpoint

1. Add a `@Serializable` DTO in `data/model/Models.kt` (match the JSON keys exactly, mark
   unknown fields optional).
2. Add a method to `KimepApi` using the `post` helper.
3. Wrap it in `KimepRepository` as `Result<T>`.
4. Surface it through a ViewModel state and a screen.

The academic calendar is offline data: add fields to `data/model/CalendarModels.kt` and
update `tools/parse_calendar.py` that generates `assets/calendar.json`.

## Known limitations

- `_finalexams` schema is unconfirmed (it returned an empty array during capture); the
  `FinalExam` DTO is intentionally permissive. Align it once the endpoint returns data.
- Release builds keep `isMinifyEnabled = false` (R8 rules for kotlinx.serialization/Ktor are
  not tuned yet).
- The bundled academic calendar is only a fallback: new years are imported on the phone,
  but scanned PDFs still need `tools/calendar_manual/` entries.
