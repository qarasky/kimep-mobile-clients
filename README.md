# KIMEP Mobile Clients

Unofficial clients for **KIMEP University**'s student portal. The Android app is here
today; an updated iOS client is planned in the same repository.

The apps talk to the university's **old mobile user-facing APIs** — the same ones the
deprecated store app used — and reproduce the useful parts (schedule, grades, academic
calendar) with a native interface, offline caching and class reminders.

[![Release](https://img.shields.io/github/v/release/qarasky/kimep-mobile-clients?label=release)](https://github.com/qarasky/kimep-mobile-clients/releases/latest)
[![Android CI](https://github.com/qarasky/kimep-mobile-clients/actions/workflows/android.yml/badge.svg)](https://github.com/qarasky/kimep-mobile-clients/actions/workflows/android.yml)
[![Website](https://img.shields.io/badge/website-download-1D4E89)](https://qarasky.github.io/kimep-mobile-clients/)

**➡️ [Download the latest APK](https://qarasky.github.io/kimep-mobile-clients/)** — a plain
download page on GitHub Pages, or grab it straight from
[Releases](https://github.com/qarasky/kimep-mobile-clients/releases).

### Screenshots

Captured on a real phone with a fictional student taking six catalog courses (18 credits).
These screenshots show the v1.0.1 interface. The demo build is private and is not offered
as a download.

<p align="center">
  <img src="docs/screenshots/updated/dark/grades.png" width="210" alt="GPA, graduation progress and credit-based year standing" />
  &nbsp;
  <img src="docs/screenshots/updated/dark/grade-calculator.png" width="210" alt="Goal-first grade calculator and linked remaining-score sliders" />
  &nbsp;
  <img src="docs/screenshots/updated/dark/schedule.png" width="210" alt="Sample schedule with real catalog courses" />
</p>
<p align="center">
  <img src="docs/screenshots/updated/dark/course-details.png" width="210" alt="Metadata-only course sheet" />
  &nbsp;
  <img src="docs/screenshots/updated/dark/settings-privacy-updates.png" width="210" alt="Privacy notice and manual update button in Settings" />
  &nbsp;
  <img src="docs/screenshots/updated/dark/update-check.png" width="210" alt="Successful manual update check" />
</p>

See [all updated screenshots and the demo setup](docs/screenshots/updated/README.md).

## Features

- **Login** with Student ID and password; the session is stored on device and refreshed
  automatically.
- **Schedule** — weekly timetable grouped by weekday, with the **current day highlighted**,
  a **midterm‑week banner** derived from the academic calendar, and pull‑to‑refresh.
  Cached locally so it renders instantly on launch and updates in the background
  (stale‑while‑revalidate).
- **Finals** — a Classes/Finals switch for the exam timetable (populated once the
  university publishes it).
- **Grades** — cumulative GPA and credits, current‑term assessment scores, and the full
  transcript grouped by semester with colour‑coded grade badges. Graduation progress uses
  a 146-credit target; year standing is credit-based, not years enrolled.
- **Grade goals** — choose a target, see the required average and feasibility, and explore
  linked remaining-score plans.
- **Manual updates** — Settings → App updates → Check for updates, alongside the automatic
  daily check.
- **Academic calendar** — the official PDF parsed into structured data: browse both
  academic years and all semesters, with the ongoing event marked **Now**, the next one
  **Next**, and past events dimmed. Opens scrolled to the current event.
- **Reminders** — notifications **1 hour** and **10 minutes** before each class, plus a
  final‑exam reminder. Each is individually togglable.
- **Privacy** — no usage tracking in v1.0.1; the privacy notice is available
  in Settings. See **[docs/PRIVACY.md](docs/PRIVACY.md)** for data handling and older builds.
- **Material You** dynamic colour, light/dark theme, edge‑to‑edge.

## Tech stack

Kotlin · Jetpack Compose · Material 3 · Ktor (OkHttp engine) · kotlinx.serialization ·
DataStore · AlarmManager · Coil. AGP 9 with built‑in Kotlin, `minSdk 26`, `targetSdk 37`.

See **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)** for how the app is put together.

## Repository layout

```
app-website/            download page (GitHub Pages: qarasky.github.io/kimep-mobile-clients)
kimep-android/          Android app (Gradle project)
  app/src/main/java/dev/qarasky/unofficialkimep/
    data/               models, API client, repositories, caches, notifications
    di/                 hand-rolled dependency container
    ui/                 Compose screens + theme
    vm/                 view models
  app/src/main/assets/calendar.json   parsed academic calendar
  keystore.properties   release signing (git-ignored)
docs/
  KIMEP_Mobile_API.md   reverse-engineered API reference
  CAPTURE_SETUP.md      how the API was captured from a stock iPhone
  ARCHITECTURE.md       Android app architecture
  RELEASING.md          how to cut a release (CI signs automatically)
  DEVICE_SUPPORT.md     supported OS/ABIs/form factors and known limitations
  PRIVACY.md            data handling, local storage, and older-build privacy notes
tools/
  parse_calendar.py     KIMEP calendar PDF -> calendar.json
capture.py              mitmproxy addon used during reverse engineering
dnsmasq.conf            DNS redirect used for the reverse-proxy phase
```

The download page is deployed by `.github/workflows/pages.yml`, which snapshots the latest
releases into `releases.json` at deploy time and publishes `app-website/` to GitHub Pages.
An `ios/` client is planned; the repository name reflects that.

## Building (Android)

```bash
cd kimep-android
./gradlew :app:assembleDebug        # debug APK
./gradlew :app:assembleRelease      # release APK
```

Requires JDK 17+ and an Android SDK with API 37 (`local.properties` → `sdk.dir`, or the
`ANDROID_SDK_ROOT` environment variable).

Release builds are signed from `kimep-android/keystore.properties` (git‑ignored):

```properties
storeFile=keystore/kimep-release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

If that file is missing, the release build falls back to the debug signing config so a
fresh clone still compiles.

## Releases & CI/CD

`.github/workflows/android.yml` runs on pushes to `main`, pull requests,
`workflow_dispatch`, and `v*` tags:

1. Sets up JDK 21 and the Android SDK (`platforms;android-37.0`, `build-tools;36.0.0`).
2. Decodes the release keystore from repository secrets into `keystore.properties`.
3. Builds debug and release, **verifies the release signature**, and uploads both APKs as
   build artifacts.
4. On a `v*` tag, publishes a GitHub release with the signed APK. Tags containing
   `beta` / `alpha` / `rc` are marked as pre-releases.

Required repository secrets:

| Secret | Purpose |
|---|---|
| `KEYSTORE_BASE64` | base64 of `kimep-release.jks` |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | key alias |
| `KEY_PASSWORD` | key password |

If the secrets are absent (for example on a fork), signing is skipped and the release
build falls back to debug signing instead of failing.

To cut a release, bump `versionCode`/`versionName` in
`kimep-android/app/build.gradle.kts`, then push a tag. Full steps:
**[docs/RELEASING.md](docs/RELEASING.md)**.

## How the API was reverse engineered

The iOS app was captured on a stock (non‑jailbroken) iPhone: mitmproxy for the proxy
phase, then a **DNS redirect + reverse proxy** once the Flutter client turned out to
ignore the iOS system proxy. The full, reproducible procedure — including pitfalls and
teardown — is in **[docs/CAPTURE_SETUP.md](docs/CAPTURE_SETUP.md)**.

The resulting endpoint reference is in
**[docs/KIMEP_Mobile_API.md](docs/KIMEP_Mobile_API.md)**.

## Academic calendar pipeline

KIMEP publishes the calendar as PDFs (KAZ/RUS/ENG). `tools/parse_calendar.py` downloads the
English ones and reconstructs the four‑column table from word coordinates
(`pdftotext -bbox`), emitting `calendar.json` that ships as an app asset. Re‑run it each
academic year:

```bash
python3 tools/parse_calendar.py     # needs poppler (pdftotext)
```

## Privacy

v1.0.1 contains no usage analytics, tracking client or first-run privacy
prompt. **Settings → Privacy → Privacy notice** explains connections to KIMEP and GitHub,
on-device storage, and how to clear your data. Account requests use KIMEP's HTTPS API;
passwords are not saved by the app.

**Released v1.0.0 and earlier builds may still send anonymous usage statistics.** Those
builds retain their Settings → Privacy opt-out. Removing tracking from the source does
not change an already installed APK. Full details: **[docs/PRIVACY.md](docs/PRIVACY.md)**.

## Disclaimer

Unofficial and not affiliated with KIMEP University. Personal educational project that
uses the university's old mobile user-facing APIs. No credentials or captured personal
data are committed to this repository.

## Built with AI assistance

In the interest of transparency: the current MVP was created with the help of AI —
**DeepSeek V4.1 Flash**, running in the **OpenCode** harness under an **OpenCode GO**
subscription — alongside manual review and testing on a real device.
