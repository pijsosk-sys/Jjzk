# Boddy

Your own Linux desktop on a Samsung Galaxy S24 Ultra (or any 64-bit Android 8+ phone).
**No root needed.** It's a normal app; uninstall it and your phone is exactly as before.

What you get:

- A Debian Linux desktop (XFCE) running full-screen, with no PC accessories needed
- **Firefox**
- **Claude**: claude.ai in Firefox, plus **Claude Code** in the terminal
- **Antigravity**: one-tap helper that installs Google's Linux ARM64 build
- **Install Apps** (Synaptic) to download anything a normal Linux PC can run

## Using it with just your fingers

| Do this | Get this |
|---|---|
| Drag one finger | Move the mouse pointer |
| Tap | Left click |
| Tap with two fingers | Right click |
| Drag two fingers | Scroll |
| Press and hold, then drag | Click-and-drag (select text, move windows) |
| ⌨ button | Phone keyboard: type straight into Linux |
| "Mouse" button | Switch between touchpad and direct-touch modes |

The bottom bar also has Esc, Tab, Ctrl, Alt, Shift, arrows and F-keys.
Tap Ctrl or Alt, then a key, for shortcuts like Ctrl+C.
A Bluetooth keyboard and mouse, or Samsung DeX on a monitor, also work.

## Getting the APK

Every push builds the app with GitHub Actions. Open the repo's **Actions** tab,
pick the latest run, and download **Boddy-apk** from Artifacts. Unzip it on your
phone and open the `.apk` (allow "install unknown apps" when asked).

First launch downloads about 1 GB and takes 10–20 minutes. Stay on Wi-Fi.

## How it works

- `scripts/fetch-proot.sh` bundles [proot](https://proot-me.github.io/) (from Termux's
  packages) into the APK. proot lets Linux programs run in the app's own storage
  without real root.
- On first run the app downloads the same Debian image Termux's `proot-distro` uses,
  unpacks it, and runs `app/src/main/assets/boddy/setup.sh` to install the desktop.
- The desktop runs in a VNC server (TigerVNC) inside Linux. The app's own viewer
  (`vnc/VncView.kt`) shows it and turns touches into mouse and keyboard input.

## Building locally

```sh
./scripts/fetch-proot.sh   # needs curl, dpkg-deb, patchelf
./gradlew assembleRelease
```
