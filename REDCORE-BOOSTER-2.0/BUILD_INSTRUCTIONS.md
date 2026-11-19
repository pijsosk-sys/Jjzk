# Build & install — REDCORE Booster 2.0

Requirements: JDK 17+ and Android SDK platform 35 (Android Studio Ladybug or newer). Create `local.properties` with `sdk.dir=/path/to/Android/sdk` if not using Android Studio.

```bash
./gradlew test             # :core logic tests + app Robolectric/Compose/screenshot tests (debug variant)
./gradlew lint
./gradlew assembleDebug    # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease  # minified; UNSIGNED unless you configure a key (below)
./gradlew recordRoborazziDebug   # re-render every screen into ./screenshots
```

## Ready-made APKs (folder `apk/`)
| File | Use |
|---|---|
| `REDCORE-Booster-2.0-release-testkey.apk` | **Install this on phones.** Optimized build signed with the project's public *test* key. |
| `REDCORE-Booster-2.0-debug.apk` | Debug build (bigger, slower), same test key. |
| `REDCORE-Booster-2.0-release-unsigned.apk` | Sign with your own key for publishing. |

### Upgrading from 1.3.1 — read this
1.3.1 was signed with a different machine's *Android Debug* key (SHA-256 `672c8d00…ba938aa`). Android only upgrades an app when the signing certificate matches, so **2.0 cannot install over 1.3.1**: uninstall 1.3.1 first (its settings are lost), or rebuild 2.0 with that original `debug.keystore`. Future 2.x test builds made from this project *will* upgrade each other (shared test key `app/keystore/redcore-test.keystore`, SHA-256 `4B:17:FD:AC…63:F5`).

### Your own release key (required for a public/production release)
Never publish with the test key — it is public. Create `keystore.properties` in the project root (git-ignored):
```
storeFile=release.jks
storePassword=…
keyAlias=…
keyPassword=…
```
or set `REDCORE_KEYSTORE`, `REDCORE_KEYSTORE_PASSWORD`, `REDCORE_KEY_ALIAS`, `REDCORE_KEY_PASSWORD`. Then `./gradlew assembleRelease` produces a signed `app-release.apk`. Test-signed release for sideloading: `./gradlew assembleRelease -Predcore.testSignRelease=true`.

## After installing (all phones, incl. Samsung Galaxy S24 Ultra)
1. Open REDCORE → grant the recommended permissions (Display over other apps, Usage access, Do Not Disturb, Modify system settings, Notifications, Unrestricted battery).
2. **APK installs on Android 13+:** Accessibility (screenshot, L1/R1) and Notification access (barrage) are blocked until *Settings › Apps › REDCORE › ⋮ › Allow restricted settings*.
3. **Biggest boost (optional, once):** enable USB/Wireless debugging and run
   `adb shell pm grant com.redcore.booster android.permission.WRITE_SECURE_SETTINGS && adb shell pm grant com.redcore.booster android.permission.DUMP`
   This unlocks the 120 Hz lock (incl. Samsung Motion smoothness), faster animations and the real FPS counter.

Version: `versionName 2.0`, `versionCode 20000`. Application ID unchanged: `com.redcore.booster`.

## PC version
See `desktop/README.md` — started but **not finished/built** in this delivery.
