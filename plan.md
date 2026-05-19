# Android Medicine Reminder Refactor Prompt

Use this prompt with an Android coding agent or as a build spec for refactoring an existing reminder app into a medicine reminder app.

---

## Prompt

You are a senior Android engineer working inside an **existing incomplete Android reminder project**. Do **not** create a new app from scratch unless absolutely necessary. **Inspect the current project first**, auto-detect the existing build setup, then refactor and extend the current codebase into a **photo-first medicine reminder app**.

### Primary instruction
1. **Inspect and auto-detect the current Android project structure**:
   - detect whether Gradle uses **Groovy** or **Kotlin DSL**
   - detect module names (`app`, feature modules, shared modules, etc.)
   - detect package name / namespace
   - detect whether UI is **Jetpack Compose** or **XML/ViewBinding**
   - detect existing architecture (MVVM, MVP, plain Activities/Fragments)
   - detect existing reminder/notification code, database layer, navigation, billing, auth, and ads setup
2. **Reuse as much existing reminder logic as possible**.
3. **Refactor safely**: preserve working code, migrate incomplete parts, remove dead code, and leave the project in a clean buildable state.
4. If there are broken/incomplete files, **repair them instead of duplicating logic**.
5. Make changes incrementally and keep the app functional after each major step.

---

## Product goal
Transform the current reminder project into a global **Medicine Reminder** app for elders/caregivers with:
- medicine photo capture
- OCR text extraction from medicine packs and prescriptions
- reminder scheduling
- stored medicine image per reminder
- multilingual support
- optional Google login for backup/restore
- one-time lifetime purchase
- 30-day premium trial
- after trial: only **5 active medicine reminders** in free tier
- unlimited active reminders and no ads for lifetime users

---

## Core product rules
- This is a **medicine organizer + reminder** app, **not** a diagnosis app.
- Do **not** present the app as giving medical advice.
- Do **not** scrape random Google results directly.
- If medicine info is shown, label it as:
  - Common uses
  - Common side effects
  - Warnings
  - Storage guidance
  - “Consult your doctor/pharmacist” disclaimer
- Always require user confirmation after OCR extraction.

---

## App structure: 3 main screens
Refactor navigation so the app has **3 main pages**:

### 1. Today
Purpose: daily medicine reminders
- show today’s active medicines
- next dose time
- medicine image thumbnail
- medicine name
- dosage / notes
- taken / skipped actions
- overdue reminders
- active reminder count (important for free tier limit)

### 2. Scan / Add Medicine
Purpose: add medicine quickly
- capture medicine photo using camera
- upload image from gallery
- optional prescription image scan
- OCR extraction using on-device text recognition if possible
- auto-fill medicine title, strength, dosage text, timing hints when possible
- editable confirmation form
- save photo, reminder times, repeat settings, start/end date

### 3. My Medicines / Profile
Purpose: library + account + monetization
- list all saved medicines
- show active vs paused reminders
- show stored photos
- edit/delete/archive medicine
- show trial / free / lifetime status
- login/logout (optional Google login)
- backup/restore
- restore purchase
- premium upgrade
- settings and language selection

---

## Feature requirements

### A. Medicine reminder core
Implement or refactor existing reminder logic to support medicine use cases:
- multiple reminder times per medicine
- repeat patterns: daily / custom weekdays / interval
- before food / after food / note field
- start date and optional end date
- mark taken / skipped
- local notification reminders
- missed reminder tracking
- active/inactive reminder state

### B. OCR / scanning
Use a reliable Android OCR approach, preferably **ML Kit Text Recognition**.
Requirements:
- medicine pack photo scan
- prescription photo scan
- extract visible text
- detect likely medicine name(s)
- detect dosage/frequency hints if available
- populate editable fields
- save original image locally
- do not rely on network OCR if avoidable for MVP

### C. Medicine image support
- store one or more photos per medicine (at least one for MVP)
- show medicine image in Today cards and Medicine Library
- keep file references safely using app storage / URI strategy

### D. Medicine info
Create a **safe data provider abstraction**:
- `MedicineInfoRepository`
- can start with mock/local/provider-based implementation
- later can be connected to trusted drug data source or backend
- display: common uses, common side effects, warnings, storage
- multi-language rendering supported
- include disclaimer text in UI

### E. Multi-language
Implement app internationalization cleanly:
- externalize all user-facing strings to resources
- support at least a base multilingual-ready structure
- add a language selector in settings/profile
- architecture should allow future expansion to Hindi + other languages
- medicine info text should be translatable through repository/output layer

### F. Login (optional, not forced)
Implement **optional Google login**:
- app must work without login
- login unlocks backup/restore sync
- use Google Sign-In / Credential Manager and Firebase Auth if suitable
- if not signed in, keep all data local via Room
- if signed in, sync medicine data to cloud storage (Firestore or equivalent)

### G. Monetization model
Implement exactly this model:

#### Trial
- first **30 days**: premium trial active automatically
- during trial:
  - unlimited active medicine reminders
  - no ads
  - premium features enabled

#### Free plan after trial
- only **5 active medicine reminders** allowed
- user can still **store/view all medicines**
- medicines above 5 active reminders are **paused**, not deleted
- user can manually choose which 5 stay active
- show clear upgrade prompt when limit exceeded
- free plan can show light ads on non-critical screens only

#### Lifetime purchase
One-time purchase unlocks:
- remove ads
- unlimited active medicine reminders
- unlimited medicine storage
- unlimited OCR/prescription scan usage (if included in plan)
- all paused reminders can be reactivated

#### Restore purchase
- implement Google Play Billing restore flow
- add “Restore Purchase” button in profile
- premium should survive reinstall through Play account restoration

### H. Ads
If ads are included:
- use AdMob or existing ads integration if already present
- show ads only on non-critical screens:
  - Medicine Library / Profile / Info areas
- do **not** show ads on:
  - dose alarm screen
  - urgent reminder confirmation screen
  - medicine due popup
- if user is in premium trial or lifetime, ads must be disabled

---

## Data/storage requirements
Use or refactor the existing DB layer. If none exists or it is broken, implement **Room**.

### Suggested entities
Refactor as needed, but support at least:

#### MedicineEntity
- id
- title
- normalizedTitle
- dosageText
- notes
- imageUri / localImagePath
- infoLanguage
- createdAt
- updatedAt
- isArchived

#### ReminderScheduleEntity
- id
- medicineId
- timeOfDay
- repeatType
- repeatDays
- startDate
- endDate
- foodRelation
- isActive
- isPausedByPlanLimit

#### DoseLogEntity
- id
- medicineId
- scheduleId
- scheduledAt
- actionTaken (`TAKEN`, `SKIPPED`, `MISSED`)
- actionTime

#### UserEntitlementEntity or local prefs model
- trialStart
- trialEnd
- isPremium
- purchaseToken/cache
- activeReminderLimit
- adsEnabled

#### Optional sync model
- cloudSyncEnabled
- lastSyncAt
- remoteId mappings

Use DataStore or SharedPreferences only for lightweight config and flags; keep medicines/schedules/logs in Room.

---

## Architecture requirements
Respect the current project style first. If the project already follows a pattern, continue it. If the code is too messy, refactor toward:
- MVVM
- repository layer
- use-cases/interactors where appropriate
- clear package separation
- testable reminder scheduling logic

Suggested structure:
- `data/`
- `domain/`
- `ui/`
- `billing/`
- `ocr/`
- `auth/`
- `notifications/`
- `sync/`

But adapt to the existing project instead of forcing a full rewrite.

---

## Reminder/notification implementation
Refactor existing reminder logic carefully.
Requirements:
- reliable local reminders
- survive app restarts
- reschedule after reboot
- support multiple reminders per medicine
- support taken/skipped logging
- if using AlarmManager/WorkManager, choose the most reliable approach for time-sensitive reminders
- add BootReceiver if needed
- keep notification channels clean and elder-friendly

---

## OCR parsing heuristics
Implement practical parsing, not over-engineered AI.
Examples:
- extract the strongest medicine-like line from OCR result
- detect common dosage forms: `500mg`, `250 mg`, `5ml`, `1 tablet`, etc.
- detect frequency hints: `once daily`, `twice daily`, `morning`, `night`, `after food`
- parse prescription text conservatively
- always show editable confirmation UI before saving

---

## Purchase / plan logic
Implement a simple entitlement manager:
- `isTrialActive`
- `isLifetimeUnlocked`
- `canActivateMoreReminders(currentActiveCount)`
- `maxActiveReminderCount = 5` after trial unless lifetime purchased
- premium trial and lifetime remove ads

When trial expires:
- keep all medicines stored
- if active medicines > 5:
  - keep first allowed set active or let user choose
  - mark extras paused by plan limit
  - show upgrade CTA
- never silently delete user medicine data

---

## Login / sync behavior
Implement optional login only.
Behavior:
- no login required to use app
- local-first behavior
- user may sign in later to back up medicines
- after login, allow sync/restore flow
- handle logout safely without deleting local records unless user explicitly confirms
- premium purchase must restore via Play Billing even without login

---

## Gradle/refactor instructions
Auto-detect and update dependencies based on the current project setup.
Examples only if needed:
- Room
- ML Kit Text Recognition
- CameraX
- DataStore
- Play Billing
- AdMob
- Firebase Auth / Firestore (if optional login is implemented)
- Coil/Glide/Picasso depending on existing image stack

Important:
- preserve compatibility with the project’s existing minSdk/targetSdk unless changes are required
- update Gradle files minimally and correctly for the detected DSL
- remove duplicated or dead dependencies
- keep the project buildable

---

## UI/UX rules
- elder-friendly spacing and readable text
- clear CTA buttons
- avoid clutter
- use strong visual medicine cards with image + name + next time
- free/premium messaging should be simple and honest
- label paused reminders clearly
- profile screen should include:
  - trial status
  - active reminder usage (e.g. `5/5 active`)
  - lifetime purchase CTA
  - restore purchase
  - optional backup/login

---

## Deliverables expected from the implementation agent
1. Inspect current project and explain what was found.
2. Refactor current reminder project instead of replacing it blindly.
3. Implement the medicine reminder features above.
4. Update Gradle/build config correctly.
5. Keep or improve code structure.
6. Leave the app compiling.
7. Summarize changed files and architecture changes.
8. If some external API keys are required, use safe placeholders and document where to configure them.
9. If a true medicine-info API is unavailable, create a pluggable provider interface and a mock/local implementation so the feature is scaffolded safely.

---

## Preferred execution order
1. Inspect project structure and build system
2. Repair broken existing reminder flow
3. Add/normalize local DB models
4. Refactor reminder scheduling
5. Add medicine image support
6. Add OCR scan flow
7. Add plan/trial/lifetime gating
8. Add purchase restore
9. Add optional login and backup sync
10. Add multilingual structure
11. Polish UI and remove dead code

---

## Important non-goals
Do not:
- turn this into a medical diagnosis app
- claim verified medical truth from random web scraping
- hard-delete medicines after trial expiry
- force login for all users
- show ads during critical reminder moments

---

## Output format expected from the implementation agent
Provide:
1. short audit of the current project
2. implementation plan
3. exact code changes
4. Gradle/dependency changes
5. notes for anything still needing external configuration

---

## Extra note for this specific project
This is an **existing reminder app that the user already worked on heavily but left incomplete**. Respect that work. Prefer **refactor + finish** over rewrite.

---

If the project is uploaded, inspect it directly and apply this spec to the real codebase.
