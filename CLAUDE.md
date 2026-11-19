# Desk Days — project brief for Claude Code

Personal office-attendance tracker for Android (iOS later). It uses a geofence
around the user's office to log time on site. A day with 4h or more inside
counts as an office day, and monthly stats are shown against a 50% target.
It's for personal use and will be published on Google Play for anyone to use.

The screen mockups are in `design/mockups/` (one HTML file per screen; see
"Screens" below). Treat them as visual reference, not code to reuse.

## Decisions already made
- **Platform:** Android first, iOS later.
- **One office per user.** The user sets it during onboarding.
- **Monthly target:** 50% of eligible working days.
- **No backend.** All data stays on the device, with optional backup to the user's own Google Drive.
- **Monetisation:** free, no ads. Optional tips through Google Play Billing (required by Play policy for in-app tips).
- **Location:** Sydney, Australia, so use the NSW public holiday calendar.

## Tech stack
- Kotlin, Jetpack Compose (Material 3), single-activity app
- Room (local database), DataStore (settings)
- Google Play Services Geofencing API with ENTER/EXIT transitions (DWELL optional)
- WorkManager: daily roll-up, daily Drive backup, reminders
- Optional Wi‑Fi check: match the saved office SSID/BSSID to confirm presence
- Google Sign-In plus the Drive REST API, `drive.appdata` scope only (hidden app folder)
- Android Auto Backup turned on as a secondary safety net
- Play Billing Library: consumable one-time products for tips
- Hilt for DI (optional); unit tests for all counting logic
- Consider Kotlin Multiplatform for the counting/stats module so iOS can share it later

## Core logic (keep it pure and unit-tested)
1. Record raw events: `{timestamp, type: ENTER|EXIT, source: GEOFENCE|WIFI|MANUAL}`.
2. Build visits from ENTER→EXIT pairs. If an EXIT is missing at the end of the day, close the visit at the last confirmed in-office signal, or flag it for the user to fix.
3. Merge visits separated by a gap shorter than `mergeGapMinutes` (default 15).
4. Daily minutes = the sum of merged visits clipped to that local day (Australia/Sydney time zone).
5. Day status: OFFICE if minutes ≥ `thresholdMinutes` (default 240). Otherwise REMOTE, or LEAVE/HOLIDAY if marked. A manual override always wins.
6. Monthly:
   - eligible = working days (Mon–Fri by default) − leave − public holidays
   - target = ceil(eligible × 0.5)
   - pct = office days ÷ eligible
   - still needed = max(0, target − office days so far), alongside working days remaining
7. Every value above can be changed in settings.

## Screens (see design/mockups)
1. **Today:** a progress ring (time in office against the 4h goal), an "In office" badge, arrival time, detection source, month progress bar, and a "Fix today manually" button.
2. **Calendar:** month grid coloured office / remote / leave / holiday / today, with a day detail card showing merged visits.
3. **Stats:** month % against the target, a "go in N of the next M days" nudge, a "how it's counted" breakdown, a 6‑month bar chart and CSV export.
4. **Office setup:** map pin plus radius (default 150 m), address, Wi‑Fi confirmation toggle, counting rules, and a link to Backup.
5. **Location disclosure:** the prominent disclosure Google Play requires, shown before the background-location permission request, plus a "log manually" fallback.
6. **Backup & restore:** Drive toggle and account, last backup time, Back up now / Restore, daily on Wi‑Fi only, delete backup.
7. **Support the app:** three consumable tips (Coffee $2.99, Big coffee $5.99, Lunch $9.99) and a thank-you state that gives a supporter badge and an alternate icon. Every feature stays free.

Bottom navigation: Today · Calendar · Stats · Office.
Visual style: background #F5F6F4, text #15181C, accent teal #0F766E; Inter for body text, Space Grotesk for headings; rounded cards (20–24px), touch targets ≥ 44px.

## Google Play requirements to build in from the start
- **Permissions:** ask for foreground location first, then background location ("Allow all the time"), and only after the disclosure screen.
- **Play Console declarations:** complete the background-location permissions declaration form and provide a demo video.
- **Privacy policy:** must mention background location and the optional Drive backup.
- **Data safety form:** location is processed on the device; the optional backup goes to the user's own Drive.
- **Tips:** use Play Billing only. No external payment links inside the app.

## Suggested build order
1. Project scaffold, Room schema, and the pure counting/stats module with tests
2. Onboarding: disclosure, permissions, office setup
3. Geofence registration (re-register on boot and after app updates), plus event logging
4. Today, Calendar and Stats screens
5. Manual edits, leave, NSW holidays, reminders, home-screen widget
6. Drive backup/restore
7. Play Billing tips
8. Release prep: privacy policy, store listing, declarations

---

## Repository layout and status

```
core/   Pure Kotlin (JVM) counting/stats logic + unit tests. No Android deps.
app/    Android app (Compose, Room, DataStore). Depends on :core.
design/mockups/  Screen mockups (visual reference only).
licenses/        OFL licences for the bundled Inter and Space Grotesk fonts.
```

Build order progress:
- [x] 1. Scaffold, Room schema, pure counting/stats module with tests
- [ ] 2. Onboarding: disclosure, permissions, office setup
- [ ] 3. Geofence registration + event logging
- [ ] 4. Today, Calendar, Stats screens (currently placeholders behind the bottom nav)
- [ ] 5. Manual edits, leave, holidays, reminders, widget
- [ ] 6. Drive backup/restore
- [ ] 7. Play Billing tips
- [ ] 8. Release prep

### Commands
- Core tests (no Android SDK needed): `./gradlew -PcoreOnly :core:test`
- App: `./gradlew :app:assembleDebug` (needs the Android SDK, API 36, and Google Maven)

`-PcoreOnly` drops `:app` and the Google Maven repo, so the logic can be tested anywhere.

### Counting decisions made while implementing (all in `core/`)
- Raw events are append-only (`presence_events`); everything else is recomputed, so changing
  a rule re-counts history.
- A visit opens on the first ENTER from any source and stays open while any source is still
  inside (so a Wi‑Fi drop doesn't end a geofence visit). A MANUAL EXIT always closes it.
- Any event during an open visit is a "confirmed in-office signal". If the local day ends
  without an EXIT, the visit closes at the last signal and is flagged `needsReview`. An EXIT
  arriving on a later day is not trusted.
- An open visit that started today runs until "now" (`ongoing`).
- Merged gaps (< `mergeGapMinutes`) count as time on site.
- Status priority: manual override → OFFICE (≥ threshold, even on a holiday) → UNTRACKED
  (before `trackedFrom`) → HOLIDAY → NON_WORKING → PENDING (today/future) → REMOTE.
- `trackedFrom` (set when the office is first saved) excludes earlier days, so the first
  month isn't counted as mostly remote.
- Office days on non-working days count as office days but not as eligible days
  (`countNonWorkingDayAttendance`, default true).
- "Working days remaining" includes today when today isn't an office day yet.
- NSW holidays are computed by rule (incl. weekend additional days); Bank Holiday is excluded.
  One-off proclaimed holidays must be marked manually.
- CSV export escapes leading `= + - @` to prevent spreadsheet formula injection.
