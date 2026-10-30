# Boddy: notes for Claude

Boddy is an Android app (Samsung Galaxy S24 Ultra, no root) that runs a Debian Linux desktop
in the app's own storage and makes it feel like its own OS: a Boddy-branded XFCE desktop,
AI coding agents that can see and drive it, sound, and bridges to the phone's own apps.

## How to work with the owner

- **Explain simply.** The owner isn't a programmer and reads on a phone. Short sentences, plain
  words, small lists. No jargon without a one-line explanation.
- **When they say "talk first" / "don't start yet", don't build.** Lay out the options and your
  recommendation, ask the few questions that matter, and wait for "start" / "go".
- **Be honest about limits and say so early.** Things that can't work: replacing Android or
  installing a real OS (locked bootloader), Windows-only apps like Roblox Studio running well
  (no GPU), Android apps writing into other apps' private folders. Offer the closest thing that
  does work.
- **Don't help with cheats or bots that break a game's rules** (e.g. Roblox automation,
  multiplayer cheats). Helper bots in the owner's own Minecraft worlds are fine.
- **Deliver an APK after each change.** The owner tests by installing the APK; send it with
  SendUserFile when CI finishes (see "Getting the APK"). Say what to tap after installing
  (usually "Update Boddy").
- **Never claim something works on the phone unless the owner confirmed it.** Say "built, not
  tested on a phone yet". When they report a problem, ask for a screenshot of the log
  ("Show details") or of the "Boddy hit a problem last time" dialog.
- Messages are often short or cut off. If a request is ambiguous, ask one quick question
  rather than guessing on something big.

## Layout

| Path | What it is |
|---|---|
| `app/src/main/java/com/boddy/os/` | Android app (Kotlin) |
| `MainActivity.kt` | Home screen: install/update, AI shortcuts, phone apps, keep-alive card, AI mode |
| `DesktopActivity.kt` | Full-screen desktop, extra-keys bar (Size, Paste, Ctrl…), handles phone requests |
| `vnc/RfbClient.kt`, `vnc/VncView.kt` | Built-in VNC client; touchpad mouse, pinch zoom, soft keyboard |
| `BoddyService.kt` | Foreground service: install, desktop process, watchdog, wake/Wi-Fi locks, sticky restore |
| `Installer.kt` | Downloads (4 parallel ranges) and unpacks the system image, runs `setup.sh`; `SETUP_VERSION` |
| `Proot.kt` | Builds the proot command (binds, fake /proc files, phone storage at /root/Phone) |
| `PhoneBridge.kt`, `PhoneActions.kt`, `PhoneApps.kt` | Token-protected localhost:7311 bridge: open links/files/apps, save to Downloads, list apps |
| `PhoneControl.kt` | Optional accessibility helper: phone screenshots, taps/holds/swipes/multi-finger bursts, typing, Stop bubble; driven via the bridge (`screen`, `ui`, `do`, `key`, `type`, `status`, `split`) |
| `AudioStream.kt` | Plays PulseAudio's raw stream from 127.0.0.1:4713 |
| `Settings.kt` | Size, AI mode (`AgentConfig` writes Claude/Codex/OpenCode permissions), last desktop size |
| `KeepAlive.kt`, `CrashLog.kt` | Battery/child-process checks, crash reports shown on next launch |
| `app/src/main/assets/boddy/` | Scripts copied into Linux at `/opt/boddy` (on every desktop start) |
| `setup.sh` | First-boot/update setup (installs only missing packages from `packages.txt`) |
| `start-desktop.sh` | PulseAudio, Xvnc on :1 / port 5901, XFCE |
| `make-launchers.sh`, `apply-theme.sh`, `session-init.sh` | Desktop icons, dock, theme, wallpaper |
| `ai-setup.sh` | The guide every AI agent gets (CLAUDE.md, AGENTS.md, GEMINI.md, QWEN.md inside Linux) |
| `boddy-ctl` | `boddy-screen/click/type/key/scroll`, `phone-save/open/app/apps`, phone control (`phone-screen/ui/tap/hold/swipe/do/key/type/split/status`, saved button spots `phone-spot(s)` in `/root/.boddy/spots`) |
| `claude-code.sh`, `codex.sh`, `deepseek-code.sh`, `free-ai.sh` | AI agent launchers (Gemini, Qwen, OpenCode via free-ai.sh) |
| `roblox-tool`, `roblox.luau`, `roblox-tools-app.sh` | Roblox games as files: `roblox-new`/`roblox-build`/`roblox-serve` via Rojo, extract/apply via Lune |
| `studio`, `studio-app.sh` | Experimental Roblox Studio: x86-64 WineHQ under Box64 by default, Hangover kept for comparison |
| `tests/` | `test_launcher.py` (Studio launcher, and that `studio-app.sh` still matches its output), `test_roblox_tool.sh` (project commands). CI runs both |
| `mcbot`, `mcbot.js`, `mcbot-app.sh` | Minecraft Java helper bot (Mineflayer, HTTP on 7320) |
| `phone-apps-app.sh` | Pick and open any phone app |
| `scripts/fetch-proot.sh` | CI: bundles Termux's proot + libs as `lib*.so` |
| `scripts/build-rootfs.sh` | CI: builds the prebuilt system image under qemu |
| `.github/workflows/build.yml` | `rootfs` job (image, tagged by hash) then `build` job (APK, release, `apk-latest` tag) |

## Rules when changing things

- **Changed anything `setup.sh`/`apply-theme.sh`/`ai-setup.sh` does?** Bump
  `Installer.SETUP_VERSION`, so installed phones show "Update Boddy". Other scripts in
  `assets/boddy` are re-copied on every desktop start.
- **Added a package?** Put it in `assets/boddy/packages.txt`. That changes the image hash, so CI
  rebuilds the system image (about 15–25 minutes under qemu) and existing phones get it via Update.
- **Changed `studio`?** Run `python3 tests/test_launcher.py`; it fails if `studio-app.sh` no longer
  matches the messages the launcher prints (its progress window reads them).
- Tools the owner might not use (Lune, Rojo, Gemini/Qwen/OpenCode, Mineflayer, Box64, Hangover) are
  downloaded on first use, not baked in. The owner doesn't want a bigger download.
- Android can only exec binaries from `nativeLibraryDir`, so native tools ship as `lib*.so`.
- Under proot: no systemd, no kernel modules, no GPU, no Unix-socket-heavy daemons (e.g. Codex
  needs `--no-daemon`). Everything runs as (fake) root.
- Keep crash-safety: background work goes through the service's `CoroutineExceptionHandler`;
  UI-side handlers catch and report with `CrashLog.record(..., fatal = false)`.

## Building and getting the APK

This cloud environment can't download the Android SDK, so **CI is the compiler**: push to the
working branch and the workflow builds. Plain `git` works through the proxy; GitHub web
downloads (releases/artifacts) are blocked, so CI also stores the APK in the `apk-latest` git
tag:

```sh
git fetch -q origin +refs/tags/apk-latest:refs/tags/apk-latest
git log -1 --format=%s apk-latest        # "Boddy APK, build N"
git show apk-latest:Boddy.apk > Boddy.apk  # then SendUserFile
```

Poll for the new build number in a background loop. If it doesn't arrive, read the run with
the GitHub MCP tools (`actions_list`, `get_job_logs`) and fix the failure before telling the
owner. The public download link is
`https://github.com/pijsosk-sys/Jjzk/releases/latest/download/Boddy.apk`.

Locally you can still check scripts with `bash -n`, JS with `node --check`, and JVM-only Kotlin
(like `RfbClient`) in a scratch Gradle project.

## Not yet confirmed on a real phone

Confirmed working by the owner: install, the desktop, touch controls, and Claude Code with
sign-in. Built and compiled but not reported tested: Codex after the `--no-daemon` fix, sound,
Roblox Tools (including the new `roblox-new`/`roblox-build` game commands), Phone Apps, the
Minecraft bot, the Studio launcher (Box64 or Hangover; nothing has yet confirmed Studio's editor
itself runs on the phone), the keep-alive card and watchdog, Gemini/Qwen/OpenCode, the parallel download, and phone control (accessibility
helper, Minecraft playing, `phone-split`). The owner reported a crash
after about 30 minutes of use; the cause wasn't confirmed (likely Android killing background
processes), so ask for the saved crash report if it happens again.
