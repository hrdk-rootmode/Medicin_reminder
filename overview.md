# MedicinReminder Overview

## Project Summary
MedicinReminder is an Android medicine reminder app built for older users and people who need a simple, readable way to manage medicines, alarm times, and dose completion. The app uses Kotlin, Jetpack Compose, Room, WorkManager, CameraX, ML Kit OCR, Retrofit, OkHttp, Coil, and TextToSpeech.

The app is designed as an offline-first product:
- Local trusted medicine information is bundled in `medicine_info.json`.
- OpenFDA data is fetched online and cached locally in Room.
- If the device is offline, the app still works using local and cached data.
- Reminder alarms, dose logs, and medicine records all persist on-device.

## What This Project Does
The app helps a user:
- add a medicine manually or by scanning a package
- schedule one or more daily reminder times
- hear the reminder title spoken aloud
- track whether each dose was taken today
- archive and restore medicines instead of permanently deleting them
- view trusted medicine guidance from local data or OpenFDA

## Build and Gradle Info

### Root Gradle
- Kotlin DSL project
- Top-level plugins are declared in [build.gradle.kts](build.gradle.kts)
- KSP is configured at the project level

### App Module Gradle
The Android app module is defined in [app/build.gradle.kts](app/build.gradle.kts).

Current build configuration:
- `applicationId`: `com.example.medicinreminder`
- `namespace`: `com.example.medicinreminder`
- `minSdk`: 29
- `targetSdk`: 36
- `compileSdk`: 36, minor API level 1
- `versionCode`: 1
- `versionName`: 1.0
- Java compatibility: 11
- Compose enabled: yes

Main dependencies used by the app:
- Jetpack Compose UI and Material 3
- Room runtime and Room KTX
- KSP compiler for Room
- Navigation Compose
- Coil for image loading
- CameraX for taking and previewing medicine photos
- ML Kit text recognition for OCR
- DataStore preferences
- WorkManager for background sync and scheduled jobs
- Retrofit and OkHttp for OpenFDA network calls
- TextToSpeech via Android platform APIs
- Play Billing for premium entitlement handling

### Version Catalog
Library versions and aliases are managed in [gradle/libs.versions.toml](gradle/libs.versions.toml).

Notable version groups:
- Android Gradle Plugin 9.0.1
- Kotlin 2.0.21
- Room 2.6.1
- Compose BOM 2024.09.00
- Navigation 2.8.0
- WorkManager 2.9.1
- Retrofit 2.11.0
- OkHttp 4.12.0

## Permissions and Why They Are Used
The app manifest is in [app/src/main/AndroidManifest.xml](app/src/main/AndroidManifest.xml).

Permissions currently used:

- `android.permission.CAMERA`
  - Needed for live camera scanning of medicine packs.
  - Used by the CameraX-based scan flow on the Add Medicine tab.

- `android.permission.INTERNET`
  - Needed to call the OpenFDA API.
  - Used for online medicine lookups and background sync.

- `android.permission.ACCESS_NETWORK_STATE`
  - Needed to check whether the device is online before syncing.
  - Used to avoid failed background requests when offline.

- `android.permission.POST_NOTIFICATIONS`
  - Needed to show medicine reminder notifications.
  - Used when alarms fire and the reminder receiver posts alerts.

- `android.permission.RECEIVE_BOOT_COMPLETED`
  - Needed to restore reminder behavior after the device reboots.
  - Used by the boot receiver so alarms can continue after restart.

- `android.permission.SCHEDULE_EXACT_ALARM`
  - Needed for accurate medicine reminder timing.
  - Used because the app relies on exact alarm scheduling for dose reminders.

Hardware feature declaration:
- `android.hardware.camera.any`
  - Declares that the device should have a camera feature for scan support.

## Main Tabs
The app currently has 3 navigation tabs.

### 1. Today
Purpose: show the medicines and doses relevant to the current day.

What it shows:
- Medicine cards with title, dosage, notes, image, and current schedule summary.
- Active and archived medicines together, with archived medicines faded.
- A top summary showing the current active reminder count.
- A medicine detail dialog when a card is tapped.

Dose table behavior:
- The header row uses dose periods as columns: Morning, Afternoon, Evening, Night.
- The rows show:
  - Enabled state
  - Time in 12-hour format with AM/PM
  - Taken checkbox for today’s tracking
- Disabled medicines are still visible but use lower opacity so they are easy to spot.
- Disabled medicines can be re-enabled from the detail dialog.

Today tab responsibilities:
- daily medicine overview
- dose tracking
- medicine enable/disable actions
- day-based reminder inspection

### 2. Add Medicine
Purpose: add a new medicine quickly and safely.

Supported actions:
- Camera scan
- Gallery image import
- OCR text extraction with ML Kit
- Manual editing of the detected medicine fields
- Custom spoken reminder title
- Repeat-day selection
- Dose schedule selection with Morning / Afternoon / Evening / Night rows
- Time selection per row
- Food relation selection
- Optional end date

Important behavior:
- There is no separate “main reminder time” field anymore.
- Dose rows define the schedule.
- Only checked dose rows create reminder alarms.
- If nothing is checked, the app falls back to the default reminder time.
- OCR output is intentionally conservative so incorrect auto-fill is less likely.

### 3. My Medicines
Purpose: show the full medicine library in one place.

What it shows:
- All medicines, both active and disabled.
- Disabled medicines are visually dimmed and labeled Disabled.
- The user can tap any medicine to open details.
- The detail dialog lets the user edit, save, disable, or enable the medicine.

My Medicines is the management tab:
- It is the place to review the stored library.
- It is the place to restore disabled items.
- It is the place to inspect schedules and trusted medicine info.

## Core Features

### Medicine Tracking
- Create medicine records manually or by scan.
- Store title, reminder title, dosage, notes, and image path.
- Archive and restore medicines without deleting history.

### Reminder Scheduling
- Multiple dose times per medicine.
- Daily and custom repeat rules.
- Alarm scheduling with Android `AlarmManager` through a reminder scheduler.
- Reminder speech using TextToSpeech.

### Dose Logging
- Mark today’s dose as taken or skipped.
- Keep a history of dose actions.
- Support older users and forgetful users with a clear taken/not taken UI.

### Medicine Information
- Local trusted dataset in `app/src/main/assets/medicine_info.json`.
- Online OpenFDA sync with local Room caching.
- Search fallback order:
  1. remote cached data
  2. local trusted JSON
  3. fuzzy token match on local data

### Offline-First Sync
- Background sync is handled with WorkManager.
- A periodic sync is scheduled.
- An immediate sync can run on launch, but the app is hardened so startup does not depend on it.
- Cached data remains available offline.

## Data and Storage
The app uses Room for local persistence.

Main entities:
- `MedicineEntity` - medicine details and archive status
- `ReminderScheduleEntity` - reminder timing and repeat rules
- `DoseLogEntity` - taken/skipped/missed dose history
- `UserEntitlementEntity` - premium/free entitlement state
- `RemoteMedicineEntity` - cached OpenFDA medicine data

Database notes:
- Current Room database version: 3
- Current migration strategy: `fallbackToDestructiveMigration()`
- This is simple for development, but it can clear local data on schema upgrade

## Architecture Notes
Important app entry points:
- `MainActivity` creates the dependency container and launches the Compose UI.
- `AppContainer` wires repositories, DAOs, and billing.
- `AppNavigation` defines the 3 tab routes.

Background and runtime services:
- `ReminderReceiver` handles reminder alerts and speech.
- `BootReceiver` restores reminder behavior after reboot.
- `MedicineSyncWorker` syncs OpenFDA data into local storage.
- `MedicineSyncScheduler` enqueues periodic and one-time sync jobs.

## UI and Responsive Support
The UI is built with Jetpack Compose, so it scales better than fixed XML layouts.

Current responsiveness goals:
- phones of small, medium, and large size
- large-screen Android tablets
- foldables and split-screen usage where supported by the OS
- portrait and landscape layouts

How the UI is currently structured:
- screen content is built with `Scaffold`, `LazyColumn`, `Row`, and `Column`
- cards and lists expand to the available width
- text-based labels keep the UI readable for older users
- the dose table is designed to compress into a simple grid of columns
- archive state is shown with opacity and color instead of hiding content

What still matters for responsive quality:
- check spacing on very small phones
- check line wrapping on tablets and landscape mode
- check touch target size for older users
- check that bottom navigation stays usable on wider screens

This is an Android app, so it is not an iPad/iOS app. The responsive support here means Android phones, tablets, and larger Android screens.

## App Flow
1. The app opens on the Today tab.
2. The app can schedule medicine sync in the background.
3. The user can add or scan a medicine.
4. Dose rows create reminder alarms.
5. Alarms trigger notification and spoken reminders.
6. The Today tab shows dose status and taken tracking.
7. My Medicines shows the full library and enables recovery of disabled medicines.

## Current Feature Summary
- Today tab shows medicine status, schedule details, and taken tracking.
- Add Medicine tab supports scan, OCR, manual edit, and reminder setup.
- My Medicines tab shows active and disabled medicines together.
- OpenFDA sync supports online info with local caching.
- Local JSON fallback keeps the app usable without network access.
- 12-hour time display with AM/PM is used for dose times.

## Open Questions and Follow-Up Ideas
Useful topics to discuss with another agent or future update pass:
- replace destructive migration with a real Room migration path
- improve OCR matching for weak scans and partial medicine names
- make OpenFDA sync save richer label data instead of minimal cached summaries
- add a proper UI for sync status and last update time
- improve tablet layouts with larger two-pane or wider-card presentations
- add a history screen for dose logs and reminders taken today
- review whether immediate sync should run at launch or only after first screen render

## Notes for Review or Handoff
- Build currently passes.
- The app is designed for readability, simple actions, and older users.
- Disabled medicines stay visible instead of disappearing.
- Dose periods are shown as Morning / Afternoon / Evening / Night.
- Reminder times are displayed in 12-hour format with AM/PM.
