const functions = require('firebase-functions');
const admin = require('firebase-admin');
const {google} = require('googleapis');

admin.initializeApp();

// Expect environment variable GOOGLE_SERVICE_ACCOUNT to contain the service account JSON
// or set GOOGLE_APPLICATION_CREDENTIALS when deploying.

exports.validatePurchase = functions.https.onRequest(async (req, res) => {
  try {
    const { packageName, productId, purchaseToken, uid } = req.body;
    if (!packageName || !productId || !purchaseToken) {
      res.status(400).json({ error: 'missing parameter' });
      return;
    }

    // Create authorized androidpublisher client using ADC (service account)
    const auth = new google.auth.GoogleAuth({
      scopes: ['https://www.googleapis.com/auth/androidpublisher']
    });
    const authClient = await auth.getClient();
    const androidpublisher = google.androidpublisher({ version: 'v3', auth: authClient });

    // Call the purchases.products.get API for in-app products
    const result = await androidpublisher.purchases.products.get({
      packageName,
      productId,
      token: purchaseToken
    });

    const purchase = result.data;
    const valid = purchase && (purchase.purchaseState === 0);

    if (valid && uid) {
      // write entitlement to Firestore for this uid
      await admin.firestore().doc(`entitlements/${uid}`).set({
        productId,
        purchaseToken,
        purchaseTimeMillis: purchase.purchaseTimeMillis,
        validatedAt: Date.now()
      }, { merge: true });
    }

    res.json({ valid, purchase });
  } catch (err) {
    console.error('validatePurchase error', err);
    res.status(500).json({ error: err.message });
  }
});
