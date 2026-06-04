ValidatePurchase Cloud Function

This function validates Google Play in-app purchases (one-time products) server-side using the Play Developer API and writes an entitlement doc to Firestore.

Prerequisites
- Firebase project with Cloud Functions and Firestore enabled.
- A Google Cloud service account with the **Play Console > Orders and Purchases** permission (or Owner) and the Play Developer API enabled. Download the JSON key.
- Firebase CLI installed and authenticated (`npm i -g firebase-tools` and `firebase login`).

Deploy steps
1. Copy your Play service account JSON to the functions folder or configure ADC. For local deploy testing you can place the JSON at `functions/validatePurchase/service-account.json` (NOT recommended for production commits).

2. From `functions/validatePurchase` run:

```bash
npm install
firebase deploy --only functions:validatePurchase
```

3. After deploy, note the HTTPS URL in the deploy output (or from the Firebase Console). Set that URL to your app's `local.properties` key `PURCHASE_VALIDATION_URL` (see app/README).

Security notes
- Do NOT commit service account keys to source control. Use Firebase environment config or secret manager for production:
  - `firebase functions:config:set play.key="$(cat service-account.json | base64)"`
  - In the function, decode and use the JSON via process.env or functions.config().
- Firestore writes entitlement docs at `entitlements/{uid}`. Secure with Firestore rules so only trusted code (Cloud Functions) can create entitlements or only owners can write.

API contract (POST)
- Request JSON: { packageName, productId, purchaseToken, uid }
- Response JSON: { valid: boolean, purchase: object }

Testing
- Use Play Console internal testing and test accounts to create test purchases. Use the deployed function URL to validate tokens returned by test purchases.

References
- https://firebase.google.com/docs/functions
- https://developers.google.com/play/developer-api
- https://firebase.google.com/docs/firestore/security/rules-structure
