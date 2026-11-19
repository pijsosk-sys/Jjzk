# Test results — REDCORE Booster 2.0

Run on 2026-11-19 in a Linux container (OpenJDK 21, Gradle 8.14.3, Android SDK 35, Robolectric). No physical device or emulator was available, so nothing was run on a real phone.

| Command | Result |
|---|---|
| `./gradlew test` | **PASS** — 135 tests, 0 failures (`:core` 48, `:app` debug 87) |
| `./gradlew lint` | **PASS** — 0 errors, 0 warnings (version-bump `GradleDependency` checks set to informational) |
| `./gradlew assembleDebug` | **PASS** — `apk/REDCORE-Booster-2.0-debug.apk` |
| `./gradlew assembleRelease` | **PASS** — minified, unsigned (1.7 MB) |
| `./gradlew assembleRelease -Predcore.testSignRelease=true` | **PASS** — `apk/REDCORE-Booster-2.0-release-testkey.apk` |

Signed APK: `package: name='com.redcore.booster' versionCode='20000' versionName='2.0' platformBuildVersionName='15' platformBuildVersionCode='35' compileSdkVersion='35' compileSdkVersionCodename='15'`
Certificate: `Signer #1 certificate SHA-256 digest: 4b17fdac3e854aa87a1c5d3aa22a912d7c853644cb0f999abf2943b02e5363f5` (project test key — not the 1.3.1 key, see BUILD_INSTRUCTIONS.md).

## Baseline (untouched 1.3.1, same environment)
`testDebugUnitTest` 39 tests pass; `lintDebug` 0 errors / 19 warnings; `./gradlew test` failed (release-variant Compose tests).

## What the tests cover
| Area | Tests |
|---|---|
| Game detection transitions, null/unknown foreground, screen off/lock, split screen, missed pause events | ForegroundTrackerTest (10), SessionMachineTest (13) |
| Session restoration & cleanup, crash/reboot recovery, user changes kept, revoked permission + retry, restore-off, mode/game switch, idempotency, hang-up, honest Android 14 | BoostControllerTest (17), RestoreJournalTest (9) |
| FPS parsing, >128 FPS, stale-data and foreign-clock rejection, exact layer matching | FpsMathTest (8), LogicTest |
| Temperature, CPU, network (ping/jitter/throughput), storage, formatting | MeasurementsTest (8), LogicTest (9) |
| Game profile persistence & overrides, settings persistence, library add/remove/scan/dedup, damaged data | PersistenceTest (8) |
| Permission denial / notifications disabled / DND / restricted settings | PermissionsTest (7) |
| Overlay window state (rejected windows never active, duplicates, failures) | OverlayWindowsTest (6) |
| Overlay UI (FPS vs screen-update labelling) | OverlayUiTest (4, Compose) |
| Navigation | NavStackTest (5) |
| Every screen renders (Roborazzi) | ScreenshotTest (31) |

## Not verified (needs a real phone)
Usage-events timing on a real device, Samsung `refresh_rate_mode` effect, SurfaceFlinger output on Android 14/15, overlay behaviour over real games, MediaProjection recording, call screening.
