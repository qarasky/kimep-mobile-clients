# Privacy

This describes v1.0.1 and the current Android source. Its privacy notice is accessible from
**Settings → Privacy → Privacy notice**, not shown as a first-run prompt.

## No usage tracking

The tracking client, event calls, anonymous ID generation, consent controls and build-time
tracking configuration have been removed. The app does not send usage statistics or
screen/tap events. It has no developer-operated account server.

When upgrading from an older build, the app attempts to delete the retired on-device
tracking preferences, including the anonymous identifier. This is retried on every launch
and does not remove your session, timetable or settings. It does not delete historical
events already received by the old tracking service.

## Connecting to KIMEP

- Your student ID and password are sent directly to KIMEP's HTTPS API to sign in.
- The password is not saved by the app.
- The saved session is used to retrieve your profile, timetable, grades and avatar.
- KIMEP controls these services and their handling of your account data.

## Local storage

The app stores your session and basic profile, cached timetable, reminder preferences,
calculator settings, and update-check preferences in its private storage. Reminders are
scheduled on the device. Grades are held in memory while using the grades screen, not
sent to an analytics service.

Android system backups are enabled; stored app data may be included depending on your
device's backup settings.

## GitHub update checks

The app automatically checks public GitHub releases at most once per day and offers a link
to an update. Settings → App updates → Check for updates makes an immediate manual check,
even if the daily automatic check has already run or an update was previously dismissed.
The request does not include your student ID, session or grades. Opening download or
repository links uses your browser. KIMEP and GitHub can receive normal connection
information, such as your IP address, when you connect to their services.

## Your control

Logging out clears the saved session and timetable and cancels local reminders. Other
preferences remain. To remove all on-device app data, use Android Settings → Apps → this
app → Storage → Clear storage.

## Older releases

**v1.0.0 and earlier APKs may still contain default-on anonymous usage tracking** when
configured at build time. On those builds, use Settings → Privacy to turn off anonymous
statistics; doing so also clears the local anonymous identifier. Source changes do not
disable tracking in an already installed older APK. Upgrade to a tracking-free release
v1.0.1 or later.

This is an unofficial project, not affiliated with or endorsed by KIMEP University.
