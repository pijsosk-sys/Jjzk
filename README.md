# Boddy

Your own Linux desktop on a Samsung Galaxy S24 Ultra (or any 64-bit Android 8+ phone).
**No root needed.** It's a normal app; uninstall it and your phone is exactly as before.

What you get:

- A Debian Linux desktop (XFCE) running full-screen, with no PC accessories needed
- **Firefox**
- **Claude**: claude.ai in Firefox, plus **Claude Code** in the terminal. Sign in once with
  "Sign in to Claude": the link opens in your phone's own browser, where you're probably
  already logged in.
- **DeepSeek**: DeepSeek chat, plus **DeepSeek Code**, which is Claude Code running on DeepSeek's
  models through [DeepSeek's Anthropic-compatible API](https://api-docs.deepseek.com/guides/agent_integrations/claude_code).
  It asks for your DeepSeek API key once and keeps its settings separate from your Claude sign-in.
- **Gemini CLI, Qwen Code, OpenCode**: free, open-source coding agents, downloaded the first time
  you tap them; they get the same Boddy tools and AI mode as Claude Code
- **Codex**: OpenAI's coding agent; sign in with your ChatGPT account (opens in your phone's browser)
- **Claude can drive Boddy**: Claude Code, DeepSeek Code and Codex know Boddy's tools
  (`boddy-screen`, `boddy-click`, `boddy-type`, `phone-save`, `phone-open`, `phone-app`), so they can
  see and use the desktop, send files to your phone and open phone apps. The first time, Boddy asks
  whether they should **ask every time** or **just do it**; change it under "AI mode" on the home screen.
- **Games**: Open buttons for Minecraft, DroidBridge (Minecraft Java), Roblox and Winlator. JDK 21 is
  installed for Fabric mods; Bedrock add-ons (`.mcaddon`) open straight into Minecraft.
- **Antigravity**: one-tap helper that installs Google's Linux ARM64 build
- **Install Apps** (Synaptic) to download anything a normal Linux PC can run
- The Boddy look: dark theme, our wallpaper, and a dock with your apps
- Sound: Linux audio (YouTube in Firefox, games) plays through the phone speaker; PulseAudio
  sends it to `AudioStream.kt` on 127.0.0.1:4713

## Using it with just your fingers

| Do this | Get this |
|---|---|
| Drag one finger | Move the mouse pointer |
| Tap | Left click |
| Tap with two fingers | Right click |
| Drag two fingers | Scroll |
| Pinch | Zoom in (the view follows the pointer) |
| Press and hold, then drag | Click-and-drag (select text, move windows) |
| ⌨ button | Phone keyboard: type straight into Linux |
| "Mouse" button | Switch between touchpad and direct-touch modes |
| Paste button | Types whatever is on your phone's clipboard |
| Size button | Big, Medium (default) or Small desktop |

The bottom bar also has Esc, Tab, Ctrl, Alt, Shift, arrows and F-keys.
Tap Ctrl or Alt, then a key, for shortcuts like Ctrl+C.
A Bluetooth keyboard and mouse, or Samsung DeX on a monitor, also work.

## Getting the APK

**Direct download:** [Boddy.apk](https://github.com/pijsosk-sys/Jjzk/releases/latest/download/Boddy.apk). Open it on your phone and allow "install unknown apps" when asked.

Every push builds the app with GitHub Actions and publishes it as a release. You can also open the **Actions** tab,
pick the latest run, and download **Boddy-apk** from Artifacts. Unzip it on your
phone and open the `.apk` (allow "install unknown apps" when asked).

First launch downloads the prebuilt Boddy system (under 1 GB, in 4 parallel parts) and unpacks it; nothing has to be installed package by package, so it takes a few minutes. Stay on Wi-Fi.

## How it works

- `scripts/fetch-proot.sh` bundles [proot](https://proot-me.github.io/) (from Termux's
  packages) into the APK. proot lets Linux programs run in the app's own storage
  without real root.
- CI (`scripts/build-rootfs.sh`, under qemu) prebuilds Debian arm64 with everything in
  `assets/boddy/packages.txt` plus Claude Code and Codex, and publishes it as a release tagged by
  a hash of its inputs. On first run the app downloads and unpacks it, then
  `assets/boddy/setup.sh` only configures things and installs whatever is missing.
- The desktop runs in a VNC server (TigerVNC) inside Linux. The app's own viewer
  (`vnc/VncView.kt`) shows it and turns touches into mouse and keyboard input.
- `PhoneBridge.kt` lets Linux open links in the phone's browser (used for sign-in). It
  only listens on 127.0.0.1 and needs a per-session token stored in the app's private storage.
- When `setup.sh` or the theme changes, bump `Installer.SETUP_VERSION`; installed phones then
  show "Update Boddy", which re-runs setup without downloading Linux again.

Staying alive in the background: the home screen shows a "Keep Boddy running" card until
battery optimisation is off for Boddy and (Android 12+) **Developer options → Disable child
process restrictions** is on; both are what make Android kill Boddy when you switch apps. The
service holds wake and Wi-Fi locks, is sticky, and restores the desktop if Android restarts it.
Crashes and recovered errors are saved and shown on the next launch.

## Building locally

```sh
./scripts/fetch-proot.sh   # needs curl, dpkg-deb, patchelf
./gradlew assembleRelease
```
