# MedicinReminder — GitHub Copilot Implementation Prompt

## Context

This is a Kotlin + Jetpack Compose Android medicine reminder app built for older users.
Current stack: Room, WorkManager, CameraX, ML Kit OCR, Retrofit, OkHttp, Coil, TextToSpeech, AlarmManager, Play Billing.
The app is offline-first. Room database version: 3 with fallbackToDestructiveMigration().
The goal is to improve usability for elders, fix core bugs, add smart grouped reminders,
improve OCR validation, add monetization, and polish the full experience.

---

## SECTION 1 — Grouped Time-of-Day Reminder System

### Goal
Replace individual per-medicine notifications with grouped smart alerts by time period.
Time periods: Morning, Afternoon, Evening, Night.

### Implementation Steps

**1. Define time period windows in a TimeOfDay enum or sealed class:**
```
Morning   → 05:00–11:59
Afternoon → 12:00–16:59
Evening   → 17:00–20:59
Night     → 21:00–04:59
```

**2. When scheduling alarms (ReminderScheduler):**
- Group all medicines that share the same time period for the day.
- Fire one AlarmManager exact alarm per period (e.g. 8:00 AM for Morning).
- Pass all medicine IDs for that period as a comma-separated String extra in the Intent.
- Each period gets its own unique notification channel: `channel_morning`, `channel_afternoon`, `channel_evening`, `channel_night`.

**3. ReminderReceiver must:**
- Receive the grouped Intent.
- Query Room for all medicine details for the given IDs.
- Build a single NotificationCompat notification per group with:
  - Title: "Morning Medicines — Time to take your doses"
  - BigTextStyle body: one line per medicine, e.g. "• Metformin 500mg — After food"
  - Large icon: pill/clock drawable
  - Action button: "Mark All Taken" → PendingIntent to a BroadcastReceiver that batch-logs doses
  - Action button: "Open App" → PendingIntent to MainActivity with the time period as an extra
- Speak aloud with TextToSpeech: "Good morning. Time to take your Morning medicines: Metformin, Aspirin."

**4. When user taps the notification, open MainActivity:**
- Navigate directly to the Today tab.
- Scroll to and highlight medicines for that time period.
- Show a modal bottom sheet (ModalBottomSheet) listing medicines for that period with:
  - Medicine image (Coil, left aligned, 56dp x 56dp rounded)
  - Medicine name (bold, 18sp)
  - Dosage (16sp, secondary color)
  - Notes/food relation (14sp, muted)
  - Individual "Mark Taken" button per row
  - "Mark All Taken" button at the bottom

**5. Missed dose re-notification:**
- After the first grouped notification fires, schedule a follow-up WorkManager OneTimeWorkRequest with a 10-minute delay.
- In the worker, query DoseLogEntity to check if any medicine in the group is still not marked taken.
- If any are untaken, re-fire the grouped notification with title: "Reminder — You have untaken medicines"
- Repeat this cycle up to 3 times (configurable per-medicine setting, see Section 5).
- Stop re-notifying once all medicines in the group are marked taken.

**6. Notification repeat (alarm-style):**
- Within the first notification itself, also schedule a second identical notification 1 minute later using AlarmManager.
- This gives the alarm-clock double-ring feel for elders.
- Cancel the second notification if the user has already marked a dose taken.

---

## SECTION 2 — Today Tab UI Improvements

### Medicine Image Display
- In the medicine card on the Today tab, show the medicine image on the LEFT side.
- Use Coil AsyncImage with a 64dp x 64dp size, `ContentScale.Crop`, and `clip(RoundedCornerShape(8.dp))`.
- If no image is available, show a placeholder drawable (pill icon from vector assets).
- Card layout: Row → [Image 64dp] [Spacer 12dp] [Column with name, dosage, schedule summary].

### Dose Period Grouping in Today Tab
- Group medicine cards under sticky period headers: "Morning", "Afternoon", "Evening", "Night".
- Each header shows the scheduled time and a count badge: e.g. "Morning · 8:00 AM · 3 medicines".
- Medicines with no doses for today are placed in an "Other / As Needed" section at the bottom.
- Use LazyColumn with stickyHeader for each group.

---

## SECTION 3 — OCR Validation and Camera Improvements

### Problem
Current OCR accepts all captured images and passes unvalidated text to medicine fields.
Non-medicine captures (notebooks, faces, random objects) are not rejected.

### Fix — Add OCR Confidence Validation

**Step 1 — After ML Kit text recognition returns, validate the result:**
```kotlin
fun isLikelyMedicineText(recognizedText: String): Boolean {
    val medicineKeywords = listOf(
        "mg", "ml", "tablet", "capsule", "syrup", "dose", "dosage",
        "twice", "once", "daily", "oral", "topical", "injection",
        "batch", "mfg", "exp", "rx", "dr.", "pharma", "generic"
    )
    val lower = recognizedText.lowercase()
    val matchCount = medicineKeywords.count { lower.contains(it) }
    val hasNumbers = recognizedText.any { it.isDigit() }
    return matchCount >= 2 && hasNumbers
}
```

**Step 2 — If validation fails:**
- Do NOT auto-fill any fields.
- Show a Snackbar or AlertDialog: "This doesn't look like a medicine label. Try scanning the front or back of the medicine pack."
- Offer two options: "Retry Scan" and "Enter Manually".

**Step 3 — Improve OCR field extraction:**
- Extract medicine name: first line of text or the longest capitalized token not containing numbers.
- Extract dosage: regex `\d+\s?(mg|ml|mcg|g|iu)` (case-insensitive).
- Extract batch/expiry: regex `(EXP|Expiry|MFG)[\s:]*(\d{2}[\/\-]\d{2,4})`.
- Only populate a field if confidence is high — leave others blank for manual entry.
- Show extracted fields with a light yellow highlight so the user knows what was auto-filled.

**Step 4 — Camera preview quality:**
- Add `ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST` to CameraX.
- Enable auto-focus via `FocusMeteringAction` on the center of the preview before capture.
- Show a green border overlay when the preview has stable focus (use `Camera.CameraInfo.focusState`).

---

## SECTION 4 — Medicine Description Auto-Fetch from OpenFDA

### Goal
Automatically fetch and display medicine guidance after OCR or manual name entry.

### Implementation

**1. In the Add Medicine screen, add a LaunchedEffect on the medicine name field:**
- Trigger a search after the user stops typing for 600ms (debounce with `kotlinx.coroutines.delay`).
- Call the OpenFDA API: `GET https://api.fda.gov/drug/label.json?search=openfda.generic_name:"<name>"&limit=1`
- API key: append `&api_key=YOUR_KEY`.

**2. Parse the OpenFDA response and extract:**
- `purpose` → shown as "What it's for"
- `warnings` → shown as "Warnings"
- `dosage_and_administration` → shown as "How to take"
- `adverse_reactions` → shown as "Side effects"
- `storage_and_handling` → shown as "Storage"

**3. Display in a collapsible Card below the form:**
- Title: "Medicine Information (from OpenFDA)"
- Each field is an expandable row with a leading icon and bold label.
- Cache the response in RemoteMedicineEntity keyed by normalized medicine name.
- If offline, query the cache first, then fall back to local `medicine_info.json` fuzzy match.

**4. Fuzzy match improvement for local JSON:**
```kotlin
fun fuzzyMatch(query: String, candidates: List<String>): String? {
    val tokens = query.lowercase().split(" ", "-")
    return candidates.maxByOrNull { candidate ->
        tokens.count { token -> candidate.lowercase().contains(token) }
    }?.takeIf { candidate ->
        tokens.any { token -> candidate.lowercase().contains(token) }
    }
}
```

---

## SECTION 5 — Per-Medicine Alert Repeat Setting

### Goal
Let users control how many times the spoken/alarm reminder repeats for each medicine.

### Implementation

**1. Add a field to ReminderScheduleEntity:**
```kotlin
@ColumnInfo(name = "alert_repeat_count")
val alertRepeatCount: Int = 2  // default: 2 repeats
```
Write and test a Room migration for this new column:
```kotlin
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE reminder_schedule ADD COLUMN alert_repeat_count INTEGER NOT NULL DEFAULT 2")
    }
}
```
Replace `fallbackToDestructiveMigration()` with `.addMigrations(MIGRATION_3_4)` in the database builder.

**2. Add a settings icon (⚙️ IconButton) on each medicine card in the My Medicines tab:**
- Tapping it opens a ModalBottomSheet titled "Alert Settings for [Medicine Name]".
- Content:
  - Slider: "Repeat alert" with values 1–5, label "Repeat [N] times"
  - Toggle: "Spoken reminder" (TextToSpeech on/off per medicine)
  - Save button

**3. In ReminderReceiver:**
- Read the `alertRepeatCount` for the medicine.
- Use AlarmManager to schedule N follow-up pings, each 1 minute apart.
- Cancel remaining pings via a PendingIntent with a unique requestCode when the user marks the dose taken.

---

## SECTION 6 — Edit Existing Medicine

### Goal
Allow the user to edit all fields of an already-saved medicine.

### Implementation

**1. In the medicine detail dialog (Today tab and My Medicines tab):**
- Add an "Edit" TextButton or IconButton (pencil icon) in the dialog's top-right corner.

**2. Tapping Edit:**
- Dismiss the detail dialog.
- Navigate to the Add Medicine screen pre-populated with the selected medicine's data.
- Pass the medicineId as a navigation argument.

**3. In AddMedicineViewModel:**
- Add a `loadMedicine(id: Long)` function that fetches from Room and populates all state fields.
- On save, update the existing record instead of inserting a new one (`dao.update(entity)`).
- Re-schedule alarms for the updated medicine (cancel old alarms first, then schedule new ones).

**4. The Edit flow must preserve:**
- Existing image (show thumbnail, allow replacement via camera or gallery)
- Dose schedule rows
- Existing DoseLogEntity history (do not delete logs on edit)

---

## SECTION 7 — Monetization (Freemium + One-Time Purchase)

### Model
- Free tier: first 30 days unlimited, then up to 5 active medicines with full notifications.
- Medicines 6+ are not deleted — they stay in the database but notifications are disabled and they show a "Premium required" badge.
- One-time lifetime purchase unlocks unlimited medicines and all features permanently.

### Pricing
- **USD: $1.99** one-time lifetime purchase.
- **INR: ₹99** one-time lifetime purchase (use Google Play's local pricing for India).
- No subscriptions. No ads shown to paid users.

### Implementation with Play Billing (already integrated)

**1. Define a single non-consumable in-app product in Google Play Console:**
- Product ID: `premium_lifetime`
- Price: USD 1.99 / INR 99 (set via Play Console pricing templates)

**2. In UserEntitlementEntity / repository:**
- Add field: `isPremium: Boolean`
- On app launch, query BillingClient for purchase state of `premium_lifetime`.
- If purchase found and acknowledged → set `isPremium = true` in Room.

**3. Enforcement logic (in MedicineRepository or ViewModel):**
```kotlin
fun canAddActiveReminder(currentActiveCount: Int, isPremium: Boolean, isWithinTrial: Boolean): Boolean {
    if (isPremium || isWithinTrial) return true
    return currentActiveCount < 5
}
```
- Trial start date: store `firstLaunchDate` in DataStore on first open.
- `isWithinTrial = (today - firstLaunchDate) <= 30 days`.

**4. Paywall UI (show when user tries to add medicine 6+ after trial):**
- BottomSheet or Dialog with:
  - Headline: "You've used your 5 free reminders"
  - Subtext: "Unlock unlimited medicines for a one-time payment. No subscription ever."
  - Price badge: "$1.99 / ₹99 — Lifetime"
  - Primary button: "Unlock Now" → triggers BillingClient.launchBillingFlow()
  - Secondary: "Maybe Later" → dismiss, medicine is saved but notification disabled

**5. Ad integration (free tier only, after trial):**
- Use Google AdMob banner ad at the bottom of the Today tab (free users only).
- Use AdMob Rewarded ad: user can watch an ad to temporarily enable a 6th+ medicine reminder for 24 hours.
- Hide all ads for premium users.
- Ad unit IDs: register in AdMob console and store in `BuildConfig` fields via `gradle.properties`.

**6. Restore Purchase button:**
- Add to app Settings screen: "Restore Purchase" → calls `BillingClient.queryPurchasesAsync()` and re-validates.

---

## SECTION 8 — General UX and Stability Improvements

### Notification Channel Setup
- Create all 4 notification channels (morning, afternoon, evening, night) in `Application.onCreate()`.
- Each channel: IMPORTANCE_HIGH, enable vibration, enable lights.

### BootReceiver
- In `BootReceiver.onReceive()`, re-schedule all active medicines' alarms from Room after reboot.
- Run this as a WorkManager OneTimeWorkRequest to avoid blocking the broadcast.

### Today Tab — Empty State
- If no medicines are scheduled for today, show a centered illustration and text: "No medicines scheduled for today. Tap + to add one."

### Accessibility for Elders
- Minimum touch target size: 48dp x 48dp for all interactive elements.
- Font sizes: medicine name 18sp, dosage 16sp, labels 14sp — do not go below 14sp anywhere.
- High contrast: use Material 3 color scheme with sufficient contrast ratio (≥ 4.5:1).
- All images have `contentDescription` set.
- TextToSpeech spoken text should be slow and clear — set `SpeechRate` to 0.85f.

### Room Migration
- Replace `fallbackToDestructiveMigration()` with explicit migrations.
- Start with `MIGRATION_3_4` for the `alert_repeat_count` column (see Section 5).
- Document each migration with a comment explaining what changed and why.

### Performance
- Preload medicine images with Coil's `preload()` on Today tab composition.
- Use `Flow<List<MedicineEntity>>` from Room DAO (reactive) — avoid one-shot queries in ViewModels.
- Batch all alarm re-scheduling in a single `coroutineScope.launch(Dispatchers.IO)` block.

---

## SECTION 9 — Implementation Order (Recommended)

Work through these in order to avoid dependency issues:

1. Room Migration (Section 5 migration part) — do this first, it unblocks all data model changes.
2. Grouped Notification System (Section 1) — core value, most impactful for elders.
3. Today Tab Image + Grouping UI (Section 2) — immediate visual improvement.
4. OCR Validation Fix (Section 3) — prevents bad data entry.
5. Edit Medicine (Section 6) — basic CRUD completion.
6. Per-Medicine Alert Repeat (Section 5 UI part) — quality of life.
7. OpenFDA Auto-Fetch (Section 4) — enriches medicine info.
8. Monetization (Section 7) — add last, after core UX is stable.

---

## Files Most Likely to Be Modified

- `ReminderScheduler.kt` — grouped alarm scheduling
- `ReminderReceiver.kt` — grouped notification building, TextToSpeech, re-notification
- `BootReceiver.kt` — restore all alarms
- `TodayScreen.kt` / `TodayViewModel.kt` — grouped LazyColumn, image display
- `AddMedicineScreen.kt` / `AddMedicineViewModel.kt` — edit mode, OCR validation, OpenFDA fetch
- `MyMedicinesScreen.kt` — settings icon, edit entry point
- `MedicineDetailDialog.kt` — edit button
- `AppDatabase.kt` — migration, new entities
- `ReminderScheduleEntity.kt` — new alertRepeatCount field
- `NotificationHelper.kt` (create if not exists) — centralize all notification building
- `BillingRepository.kt` — premium check, paywall trigger
- `MainActivity.kt` / `Application.kt` — notification channels, trial date, AdMob init

---

## Notes for Copilot

- Never use `fallbackToDestructiveMigration()` going forward. Always write a proper Migration.
- All alarm scheduling must use `AlarmManager.setExactAndAllowWhileIdle()` for reliability.
- Cancel pending alarms using the same requestCode used to create them (derive from medicineId + periodOrdinal).
- TextToSpeech must be initialized once and reused — do not create a new instance per notification.
- Test on Android 12+ for exact alarm permission requirements (`SCHEDULE_EXACT_ALARM` or `USE_EXACT_ALARM`).
- All Composable previews should use `@PreviewParameter` with sample data for medicines.
- Minimum SDK is 29, so no API gating is needed for most of the above — but check CameraX and ML Kit minimum versions.