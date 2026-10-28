# Privacy

This describes v1.1.0 and the current Android source. Its privacy notice is accessible from
**Settings → Privacy → Privacy notice** (a full page, not a dialog), not shown as a first-run prompt.

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

## Calendar export

Exporting the schedule is optional and starts only when you tap a button in
**Settings → Calendar**.

- **Add to a calendar** asks for Android's Calendar permission (`READ_CALENDAR` and
  `WRITE_CALENDAR`). The app uses it to list your calendars and to add, and on a later
  export replace, the class and exam events it created (identified by a marker set by the
  app). It does not read or change your other events.
- Once events are in a calendar such as Google Calendar, that calendar app and its provider
  handle them under their own policies.
- **Export for Proton Calendar** writes an `.ics` file to the app's temporary cache and
  hands it only to the app you pick.
- You can revoke the permission in Android Settings at any time. Events already added stay
  in your calendar until you delete them there.

## Academic calendar

The app bundles KIMEP's academic calendar. From June it also looks for the new year's
calendar on kimep.kz, at most once a day and only while the current or next academic year
is missing. **Settings → Academic calendar → Check for new calendar** does the same on
demand. The calendar page and PDF are plain downloads from kimep.kz; the requests do not
include your student ID, session or grades. The PDF is read on the phone and the result
is kept in the app's private storage. KIMEP can see normal connection information, such
as your IP address.

Midterm dates you mark for a course are stored only on the device. They are never sent
anywhere, are added to a calendar export only if you export, and are cleared on logout.

## GitHub update checks

The app automatically checks public GitHub releases at most once per day and offers a link
to an update. Settings → App updates → Check for updates makes an immediate manual check,
even if the daily automatic check has already run or an update was previously dismissed.
The request does not include your student ID, session or grades. Opening download or
repository links uses your browser. KIMEP and GitHub can receive normal connection
information, such as your IP address, when you connect to their services.

## Your control

Logging out clears the saved session, timetable and marked midterm dates and cancels local reminders. Other
preferences remain. To remove all on-device app data, use Android Settings → Apps → this
app → Storage → Clear storage.

## Older releases

**v1.0.0 and earlier APKs may still contain default-on anonymous usage tracking** when
configured at build time. On those builds, use Settings → Privacy to turn off anonymous
statistics; doing so also clears the local anonymous identifier. Source changes do not
disable tracking in an already installed older APK. Upgrade to a tracking-free release
v1.0.1 or later.

This is an unofficial project, not affiliated with or endorsed by KIMEP University.
