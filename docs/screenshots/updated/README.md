# Updated app screenshots

Captured on a connected Nothing A069 phone at 1224 × 2720, using its dark dynamic theme.
The screenshots show the v1.0.1 interface, using the isolated demo build.

The student identity, GPA and grades are fictional. Course titles, codes, instructors,
rooms, sections and meeting times use the user-supplied F2026 general schedule. Alex
Student has a 3.42 GPA, 98 earned credits, and 101 taken credits. No real student account
or personal student details were used.

## Current semester: 18 credits

Each course carries 3 KIMEP credits and meets for 75 minutes twice a week. The demo has
six enrollments and twelve meeting entries, with no overlapping classes or weekends.

| Code | Course | Section | Days | Time |
| --- | --- | --- | --- | --- |
| GEN/OPM2402 | Business Statistical Analysis | 3 | Mon, Wed | 13:00–14:15 |
| ACC2201 | Management Accounting I | 3 | Mon, Wed | 16:00–17:15 |
| IFS2203 | Management Information Systems | 4 | Tue, Thu | 08:30–09:45 |
| FIN3210 | Corporate Finance | 1 | Tue, Thu | 10:00–11:15 |
| MKT3130 | Principles of Marketing | 1 | Tue, Thu | 11:30–12:45 |
| MGT3212 | Organizational Behavior | 1 | Tue, Thu | 16:00–17:15 |

## Images

| Screenshot | Shows |
| --- | --- |
| [Grades](dark/grades.png) | 98 / 146 graduation credits and Year 3 credit standing |
| [Grade calculator](dark/grade-calculator.png) | Goal-first answer, feasibility badge, linked sliders |
| [Schedule](dark/schedule.png) | Sample timetable |
| [Course details](dark/course-details.png) | Instructor, location, time and section; no calculator |
| [Settings](dark/settings.png) | Notification controls and privacy section |
| [Privacy and updates](dark/settings-privacy-updates.png) | No usage tracking and manual update button |
| [Update check](dark/update-check.png) | Successful manual check against GitHub |
| [Privacy notice](dark/privacy-notice.png) | Settings-only notice |

## Reproduce

The `screenshots` build has a separate application ID, `dev.qarasky.unofficialkimep.screenshots`,
and a `KIMEP Demo` launcher. It does not overwrite the user's installed app or share its
storage. The activity lives in `app/src/screenshots/`; fixtures live in
`app/src/demoFixtures/`, shared with unit tests. Neither is included in normal debug or
release APKs. KIMEP account API calls use an in-process mock engine.
Reminder preferences are disabled to avoid sample notifications.

```sh
cd kimep-android
./gradlew :app:assembleScreenshots
adb -s DEVICE_SERIAL install -r app/build/outputs/apk/screenshots/app-screenshots.apk
adb -s DEVICE_SERIAL shell am start -n dev.qarasky.unofficialkimep.screenshots/dev.qarasky.unofficialkimep.screenshots.DemoActivity
adb -s DEVICE_SERIAL exec-out screencap -p > screenshot.png
```

Navigate normally to capture different screens. The manual update button uses GitHub,
not a fake update response. The demo is intended for Schedule, Grades, Calendar and
Settings captures; it does not create a real university student account.
