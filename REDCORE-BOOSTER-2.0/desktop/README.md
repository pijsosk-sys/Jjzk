# REDCORE Booster for PC — work in progress (NOT built yet)

Started in this delivery but not completed because the session ran out of time. Nothing here is wired into
the Gradle build (`settings.gradle.kts` does not include `:desktop`), so it cannot break the Android build.

Done so far (Compose Multiplatform Desktop + OSHI, reusing `:core`):
- `Ui.kt` — REDCORE theme, fonts and components (same black/red identity as the phone app).
- `PcSystem.kt` — live CPU/core load, clocks, RAM, temperature/fans (only when the PC exposes them), battery, network, device report.
- `PcGames.kt` — Steam (all library folders) and Epic game detection, manual games, per-game modes, launching.
- `build.gradle.kts` — native installers (MSI/EXE/DEB/DMG) and a cross-platform `portableJar` task.

Still to do: `Main.kt`/`App.kt` (windows, screens, mini HUD), PC boost backend (Windows power plan / Game Mode,
Linux power profile, all journaled with `:core`'s `RestoreJournal`), session watcher, then add `include(":desktop")`
and the `org.jetbrains.compose` plugin (1.7.x) to the root build and build/test it.
