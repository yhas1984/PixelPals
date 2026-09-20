# Data safety review for Google Play In-App Review

PixelPals 2.5.2 adds Google Play In-App Review 2.0.2. The app requests the official Google Play review flow after a positive in-app moment; it does not receive the rating, review text or completion result.

The eligibility state is stored only on the device in Android `SharedPreferences`:

- last request timestamp;
- last app version requested;
- number of review requests;
- a short-lived pending event containing the local pet identifier and event timestamp.

PixelPals does not send this state to its own servers and does not add Firebase, advertising analytics or personal tracking. Release builds continue to use `NoOpAnalyticsTracker`; acquisition, first-open and rating metrics are read only from aggregate Google Play Console reports.

Before saving the release in Play Console, review the current Google Play SDK Index entry for `com.google.android.play:review` and confirm the Data safety form remains consistent with the declarations made for Google Play services and the existing advertising and billing SDKs.
