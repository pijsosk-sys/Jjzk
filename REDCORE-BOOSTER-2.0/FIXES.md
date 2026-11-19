# REDCORE Booster 2.0 — Fixes

## Architecture
- New **`:core`** Gradle module (pure Kotlin, no Android): `ForegroundTracker`, `SessionMachine`, write-ahead `RestoreJournal`, `FpsMath`, network/format/CPU/thermal math. Fully unit-tested; shared with the PC version.

## "It doesn't do anything" → real, verified, visible
- Every change is journaled, written, **read back**, and reported as **before → after** (e.g. "43 % (auto) → 100 % locked", "60 Hz → Locked to 120 Hz. Display confirms 120 Hz").
- **Active now** panel (Home + Performance) re-reads each effect from the system every 2 s with a ✓ when it is still in force.
- **Boost power** meter: shows exactly which grant unlocks which effect, one-tap fixes, ADB command copy + step-by-step guide.
- Samsung One UI refresh rate (`refresh_rate_mode`) + AOSP keys.
- Honest Android 14+ background-app handling; low-latency Wi-Fi no longer claimed.
- "Allow restricted settings" guidance for APK installs (Android 13+), App-info shortcut.
- New **Unrestricted battery** permission entry (Samsung sleeping apps).
- Sessions started with Play now end — and restore — when you leave the game (watch mode, needs Usage access).

## Issue 1 — Detection
Explicit foreground model (Unknown / ScreenOff / Locked / NoActivity / REDCORE / launcher / other app / game), incremental event queries with overlap de-duplication, grace periods (launcher 4 s, other app 10 s, screen off 120 s), game switch ends A before B without settings flicker, user-ended sessions not restarted, access revoked ⇒ end + notify, 10 s polling with screen off, AUTO + WATCH modes, boot receiver resumes it.

## Issue 2 — Overlay
`OverlayWindows` registry records a window only after Android accepts it, reports rejections, prevents duplicates, disposes Compose content; `running` = main window really attached; hang-up dims only after its window exists (and is journaled); AppOps watcher closes everything if overlay permission is revoked; rotation re-snaps; keep-awake on the always-present ticker window; synchronous `EdgeGuard` with a real strip count.

## Issue 3 — Restore
Write-ahead journal persisted with `commit()` **before** each write; only changed settings are journaled; restore only when the current value is still REDCORE's (user changes kept and reported); raw values (no lossy %); `USER_ROTATION` + missing refresh keys restored; per-setting results; failures stay pending and are retried when the permission returns; crash/kill/reboot recovery on next start and at boot; `restoreOnExit=false` discards the journal; mutex-serialised; mode switches put back settings the new mode no longer uses; idempotent start; journal excluded from cloud backup.

## Issue 4 — FPS
Hard timeouts with a separate output reader; exact package-boundary layer matching; the layer presenting the most frames wins; rate from frames' own span (correct above 128 FPS); stale data ⇒ no number with a reason; display commit rate labelled "SCREEN …/s", never FPS, never in reports; adaptive 1 s / 3 s sampling, paused with the screen off.

## Other
Network (DNS resolved once, first-byte timing, steady-state throughput, complete-POST upload, HTTP status, metered confirm, screen-scoped polling), storage error vs empty + partial-access note, recording (no double consent, FGS failure handled, corrupt files deleted, no main-thread blocking), stuck trigger finger, monitor counter reset, tolerant library parsing + de-dup, DataStore corruption fallback, package install/remove refresh, async icons, °F everywhere, notification-enabled check, denied-twice permissions open App info, overlay mode chip keeps game overrides, Re-apply keeps the game, battery drain accumulated only while unplugged, EQ reports when another app controls effects, `./gradlew test` fixed, lint 0 errors / 0 warnings (GradleDependency bumps set to informational), version 2.0 (code 20000).
