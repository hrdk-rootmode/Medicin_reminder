Purchase validation (Firebase Cloud Function) — Setup Guide

Goal: validate in-app purchases server-side using a Firebase Cloud Function which calls the Google Play Developer API.

1) Firebase project & Firestore
- Create a Firebase project at https://console.firebase.google.com
- Enable Firestore (Native mode) and Cloud Functions.

Firebase & Google services setup checklist (step-by-step)

1. Create a new Firebase project
  - Go to https://console.firebase.google.com and click "Add project".
  - Enter a project name and follow the prompts.
  - When asked to enable Google Analytics for the project, **you may choose ON or OFF**:
    - If you want analytics, enable Google Analytics and pick the Analytics account.
    - If you prefer no analytics, choose **Disable Google Analytics**. You can change this later in Project Settings → Integrations.

2. Enable Firestore, Functions, and Authentication
  - In the Firebase Console, open "Firestore Database" and create a database in Native mode.
  - Open "Functions" and note the region you plan to use (e.g. `us-central1`).
  - (Optional but recommended) Enable "Authentication" if you plan to tie entitlements to a user account. You can use anonymous auth for quick testing.

3. Enable required Google Cloud APIs
  - In Google Cloud Console (select the same project), enable these APIs:
    - **Google Play Android Developer API** (for purchase validation)
    - **Cloud Functions API**
    - **Firestore API** (should be enabled by Firebase)
    - **Secret Manager API** (recommended for service account keys)
    - **Cloud Resource Manager API** (usually enabled)
    - **Vertex AI API** (only if you plan to use Google Gemini / Vertex AI)

  You can enable APIs in the Cloud Console or with `gcloud`:

  ```bash
  gcloud services enable androidpublisher.googleapis.com cloudfunctions.googleapis.com secretmanager.googleapis.com firestore.googleapis.com aiplatform.googleapis.com
  ```

4. Create and configure a Play Console service account
  - In Play Console: Settings → Developer account → API access → Link project (if not linked).
  - Create a new service account and grant it access to Orders & Purchases (or full access for testing).
  - Download the JSON key for the service account.
  - Secure the JSON key; **do not commit** it to source control.
  - For Cloud Functions, store the key securely: either use Firebase environment config or Secret Manager. Example (Secret Manager):

  ```bash
  gcloud secrets create play-service-account --data-file=service-account.json
  gcloud secrets add-iam-policy-binding play-service-account --member="serviceAccount:$(gcloud projects describe $(gcloud config get-value project) --format='value(projectNumber)')@cloudservices.gserviceaccount.com" --role="roles/secretmanager.secretAccessor"
  ```

5. (Optional) Enable Vertex AI / Gemini access
  - If you plan to use Google Gemini / Vertex AI for AI summarization, enable the Vertex AI API in Cloud Console.
  - Set up billing and quotas for Vertex AI; create a service account with access and store its key in Secret Manager.
  - Note: using Gemini/Vertex AI may incur costs; implement caching and rate limits in Cloud Functions.

6. Deploy the Cloud Function securely
  - Use Firebase CLI to deploy. Prefer supplying credentials through environment config or Secret Manager, not by copying JSON into the functions folder.
  - Example using local service account (quick test only):

    1. Place `service-account.json` in `functions/validatePurchase` (do not commit).
    2. Run:

      ```bash
      cd functions/validatePurchase
      npm install
      firebase deploy --only functions:validatePurchase
      ```

    3. For production, configure the function to read the service account JSON from Secret Manager or Firebase functions config.

7. Set `PURCHASE_VALIDATION_URL` in the app
  - After deploying, copy the HTTPS function URL and put it in the app `local.properties`:

    ```properties
    PURCHASE_VALIDATION_URL=https://<REGION>-<PROJECT>.cloudfunctions.net/validatePurchase
    ```

  - Rebuild the app so `BuildConfig.PURCHASE_VALIDATION_URL` is populated.

8. Play Console: create test product and testers
  - In Play Console → Monetize → Products → In-app products, create a product id such as `lifetime_premium`.
  - Add license-testing / internal testers and upload an internal test build (Internal testing track).

9. Testing and verification
  - Install the internal test build via the Play Store (internal testing link).
  - Complete a test purchase. The client will post the `purchaseToken` to the Cloud Function; the function validates with Play Developer API and writes entitlement to Firestore (if `uid` provided).

Quick checklist (before deploy)
 - [ ] Firebase project created
 - [ ] Firestore enabled (Native)
 - [ ] Cloud Functions enabled
 - [ ] Play Developer API enabled
 - [ ] Service account created and key secured (Secret Manager recommended)
 - [ ] Vertex AI enabled (if using Gemini)
 - [ ] `PURCHASE_VALIDATION_URL` set in `local.properties` after deploy

Security setup for subscription protection
- Before subscription setup:
  - Enable server-side validation first. Do not rely on client-only premium flags.
  - Use Play Console test accounts and internal testing.
  - Keep the app on a normal, unmodified device for testing.
  - Plan a single source of truth for entitlement (Cloud Function + Firestore or Play Integrity backed verification).
- After subscription setup:
  - Keep server validation required for every purchase or restore flow.
  - Do not grant premium from local DB edits alone; always re-check with the server.
  - Block rooted/debuggable/emulator-like devices in the app.
  - Disable app backup if you want the strictest anti-bypass behavior.
  - Revoke premium on server if the purchase is refunded, canceled, or invalidated.

What this security protects against
- Rooted-phone DB edits
- Backup/restore abuse
- Simple app patching of local premium flags
- Debug build misuse on real users

What it cannot fully stop
- A determined attacker with a fully patched APK or a custom ROM can still try to bypass checks.
- The best practical protection is: server-side validation + Play Integrity + device blocking + Play Console test discipline.

Notes on Google Analytics
 - When creating a Firebase project you are prompted to enable Google Analytics. If you do not want analytics data collected, choose **Disable Google Analytics** during project creation.
 - To disable Analytics after enabling it, go to Firebase Console → Project Settings → Integrations and unlink Analytics, then remove analytics SDK usage in your app if present.

Links & references
 - Firebase Console: https://console.firebase.google.com
 - Google Cloud APIs: https://console.cloud.google.com/apis/library
 - Play Developer API docs: https://developers.google.com/android-publisher

2) Service account for Play Developer API
- In Google Cloud Console, create a service account and grant it the role that can access the Play Developer API (Project > Service accounts).
- In Play Console, go to Settings > Developer account > API access and link your Google Cloud project. Then create a new service account and grant it the necessary permissions (Order & Purchases). Download the JSON key.

3) Prepare Cloud Function code
- Put the downloaded JSON in `functions/validatePurchase/service-account.json` for local testing (do not commit).
- From the `functions/validatePurchase` folder run:
  ```bash
  npm install
  firebase deploy --only functions:validatePurchase
  ```
- Grab the HTTPS function URL from the deploy output.

4) Configure the Android app
- Add the function URL to `local.properties` in the project root:
  ```properties
  PURCHASE_VALIDATION_URL=https://us-central1-<project>.cloudfunctions.net/validatePurchase
  ```
- Rebuild the app so `BuildConfig.PURCHASE_VALIDATION_URL` is populated.

5) Play Console products
- Create an in-app product (e.g., `lifetime_premium`) in your Play Console for the app's package name.
- Create test accounts (license testing) and add them.

6) Testing flow
- Upload an internal test build to Play Console (Internal testing track), add testers.
- Install via Play internal testing on device.
- Perform a test purchase. The device purchase token is returned; the client will POST it to the function URL and the function will validate and write to Firestore.

7) Production notes
- For production, do NOT store service account JSON in the functions folder. Use Firebase environment config or Secret Manager.
- Secure Firestore with rules allowing only Cloud Functions to create entitlements or require UID ownership checks.
- Consider binding entitlements to authenticated users (Firebase Auth) to avoid device-level abuse.

If you want, I can add the Firebase client SDK to the app and implement listening to `entitlements/{uid}` documents so the client updates entitlement state in real time. This requires the app to use Firebase Auth (anonymous is acceptable for initial testing).
