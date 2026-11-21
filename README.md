# Currency

An ad-free currency converter for Android (built for a Pixel 6).

- USD is pinned at the top, with eight swappable currency slots underneath.
- Tap any row and type: every other row updates instantly.
- Tap a currency's flag or code to replace it from a searchable list of ~165 currencies.
- Long-press a row and drag to reorder.
- Optional exchange-fee setting (top right) to see what an ATM, card or booth really gives you.
- Rates refresh about once a day from open.er-api.com (backup: fawazahmed0/currency-api).
  The last rates are saved, so it works offline.

## Build

Needs JDK 17+ and the Android SDK (platform 35).

    ./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk
    ./gradlew testDebugUnitTest   # add -Proborazzi.test.record=true to save screenshots to app/build/shots

Builds are signed with the non-secret key in `keystore/` so each new build installs over the last one.

Rates By [Exchange Rate API](https://www.exchangerate-api.com).
