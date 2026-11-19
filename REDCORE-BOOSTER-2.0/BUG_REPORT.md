# REDCORE Booster 1.3.1 — Bug Report (source audit)

All issues below were confirmed by reading the 1.3.1 source. Severity: **H** high, **M** medium, **L** low.

## Why the app "didn't do anything" (reported by the user, Galaxy S24 Ultra)
| # | Sev | Finding |
|---|---|---|
| 1 | H | `killBackgroundProcesses()` cannot close other apps on Android 14+, but the app reported "Asked Android to close N apps" (fake success). |
| 2 | H | Low-latency Wi-Fi lock only works for the app **on screen**; during play that is the game, so it never applied, yet was reported as applied. |
| 3 | H | Refresh-rate control only wrote AOSP keys; Samsung One UI uses `Settings.Secure refresh_rate_mode`. Both need the one-time ADB grant, which the app did not explain well. |
| 4 | H | Android 13+ blocks Accessibility and Notification access for APK installs until "Allow restricted settings" — no guidance existed. |
| 5 | H | Sessions started with **Play** never ended automatically unless Auto game boost was on, so settings stayed changed. |
| 6 | M | No read-back verification and no before→after values: users could not see what changed. |

## Issue 1 — Game detection (`GameDetectionService`)
- H: `foregroundPackage(6000) ?: lastFg` kept the last game "in front" when no event arrived: screen off, lock screen, notification shade, a crashed game or revoked access left a **stale session forever**.
- H: switching game A → B started B without ending A (A not logged/reported, B inherited A's start time, settings A changed but B didn't use were never restored).
- H: Usage access revoked → `stopSelf()` without ending the session (DND/brightness stuck).
- M: "Turn off" and `onDestroy` didn't end the session; polling every 2.5 s even with the screen off.

## Issue 2 — Overlay windows (`OverlayService`, `EdgeGuard`)
- H: `composeWindow()` returned the view even when `addView` threw → app believed windows existed.
- H: Screen hang-up dimmed brightness to 1 % **before** adding its window; if the window was rejected the screen stayed at 1 %.
- M: no handling of "Display over other apps" being revoked while playing; keep-screen-on lived on a window that can be 0×0; `EdgeGuard.show()` returned `true` before the async add; rotation left the bubble off-edge.

## Issue 3 — Restore (`BoostController`)
- H: **No crash/reboot recovery.** After a crash the next session saved the *boosted* values as the restore point → the user's originals were lost permanently.
- H: `restore()` restored settings REDCORE never changed (e.g. DND in Eco) and overwrote changes the user made during the game.
- H: `USER_ROTATION` written but never restored; missing `peak/min_refresh_rate` (never set) not restored → refresh stayed locked.
- M: brightness saved as a percentage (lossy); failures swallowed (`runCatching`); restore point cleared even when parts failed (permission revoked); `restoreOnExit=false` left a stale restore point; no locking between UI and service; `applySmooth120(false)` claimed success while doing nothing; mode switch left the old mode's brightness forever.

## Issue 4 — FPS (`FpsProbe`)
- H: `dumpsys` executed with no timeout (could hang the sampler forever) and could deadlock on large output.
- M: layer matched by substring (`com.game` matched `com.game.helper`), first candidate picked; "frames in last second" capped at 128 (under-reports >128 FPS); stale layer reported as 0 FPS; display commit rate shown as "SCR FPS".

## Other bugs
- M: Network ping included DNS lookup time; upload test returned "0 bps" on failure and counted buffered bytes; download timing included TLS setup; no HTTP status check.
- M: Network info polled every 3 s for the whole activity lifetime (even off-screen).
- M: Storage scan errors were shown as "no large files".
- M: `RecordPermissionActivity` asked for consent again after rotation; `startForeground` failures crashed; killed recordings left corrupt files in the gallery.
- M: Hold-mode L1/R1 trigger left a virtual finger pressed if the session ended.
- M: Monitor rx/tx rate after a pause averaged over minutes; library JSON: one bad entry wiped the whole library; DataStore IOException crashed the app.
- M: `./gradlew test` failed (release-variant Compose tests need the debug-only test manifest).
- L: `runBlocking` DataStore reads on the main thread; icons decoded during composition; °F ignored in reports/cool guard/device info; notifications "granted" even when disabled per-app; denied-twice runtime permissions made the Allow button do nothing; Add Games list never refreshed; overlay mode chip silently changed the global default and dropped game overrides; Re-apply turned a game session into a game-less one.
