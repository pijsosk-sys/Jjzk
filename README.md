# Horizon Mobile

An Android app that runs [Horizon](https://playhorizon.dev) on a phone or tablet.
Horizon is the open-source (MIT) Rust rewrite of Minecraft: Java Edition 26.3
([horizon-rust/horizon](https://github.com/horizon-rust/horizon)). Horizon has no
official Android build yet: its roadmap lists touch controls, a mobile UI,
packaging and loading your own game files as the missing pieces. This repository
adds them.

> Unofficial. Not made by, approved by or associated with Mojang, Microsoft or the
> Horizon project. Like Horizon itself, it contains no Minecraft code, assets or
> data: you need your own copy of Java Edition 26.3.

## What's here

| Path | What it is |
| --- | --- |
| `HORIZON_COMMIT` | The Horizon commit this app is built from |
| `patches/horizon-android.patch` | The changes Horizon needs on Android (below) |
| `native/` | The Rust crate built as `libhorizon.so`: `entry.rs` (`android_main`) and `touch.rs` (the touch controls) |
| `android/` | The app: a launcher screen (Java, no dependencies) and the game's `NativeActivity` |
| `scripts/build-native.sh` | Checks out Horizon, applies the patch and builds the native library |
| `.github/workflows/android.yml` | Builds the APK on every push (download it from the run's artifacts) |

### The Horizon patch

- **Event loop.** On Android, winit builds its loop from the activity's
  `AndroidApp`. The game's arguments come from the app instead of argv.
- **Background and foreground.** The window surface is dropped when the app is
  suspended and recreated on resume. A world in progress opens the pause menu.
- **View control.** There's no mouse to grab, so touch turns the view through
  the grabbed-pointer path, the same one Horizon's offscreen agents use.
- **Clipboard.** `arboard` doesn't support Android, so text you copy stays inside
  the game.
- **Touch hooks.** Touch events go to `touch.rs`, the on-screen stick feeds the
  pad's analog move vector, and the controls draw over the HUD.

### Touch controls

- **In game:**
  - Left side: a floating stick to walk.
  - Anywhere else: drag to look, tap to use or place, hold to mine or attack.
  - Hotbar: tap a slot to select it.
  - Buttons: jump `^`, sneak `v` and sprint `>>` (both toggles), attack `X`,
    inventory `...`, drop `Q`, chat `T`, camera `F5` and pause `II`.
- **In menus and inventories:** one finger is the mouse and a second finger
  scrolls. Two buttons give `<` (back) and `Aa` (keyboard). The keyboard also opens
  by itself on text fields.

## Getting the game files onto the phone

Horizon prepares its game files from your copy of Minecraft by running Mojang's
Java code, so this step happens on a computer:

1. Install the Horizon launcher from [playhorizon.dev](https://playhorizon.dev).
   Sign in with the Microsoft account that owns Java Edition and press **Play**
   once. That prepares the files.
2. Zip the prepared folder, the one that holds `horizon-resources.json`:
   `horizon-launcher/versions/<version>/.local/horizon-client/26.3` in your data
   folder. That's `%APPDATA%` on Windows, `~/Library/Application Support` on
   macOS and `~/.local/share` on Linux. Use a launcher version close to
   `HORIZON_COMMIT` (late October 2026), because the file format can change
   between versions.
3. Copy the zip to the phone, open Horizon Mobile and tap **Import game files**.
   Worlds work the same way: zip a world folder (the one with `level.dat`) and tap
   **Import a world**.

On the phone you play offline: single player, LAN, and servers in offline mode.
Signing in to online-mode servers isn't supported yet.

## Building

The easiest way: push to GitHub and download `horizon-mobile-apk` from the
**Android APK** workflow run.

Locally (Linux or macOS):

```sh
rustup target add aarch64-linux-android
cargo install cargo-ndk
export ANDROID_HOME=~/Android/Sdk          # with the NDK installed (sdkmanager "ndk;28.2.13676358")
scripts/build-native.sh --release arm64-v8a
cd android && gradle assembleRelease      # Gradle 8.14, JDK 17
adb install app/build/outputs/apk/release/app-release.apk
```

You need a 64-bit ARM phone on Android 8.0 or newer, with Vulkan or OpenGL ES 3.
Horizon is a full Java Edition client, so expect it to want a recent, fast phone
with plenty of memory. Start with a low render distance (Options, or
`--render-distance` / `8` in **Launch options**).

## Status

This is a first version. It's built by CI but hasn't been tested on a device yet.
Logs are in the game folder: the launcher's **Show the last log** opens
`logs/latest.log`. Known gaps:

- No Microsoft sign-in on the phone, so no online-mode servers.
- No game controllers. Horizon reads them through gilrs, which has no Android
  backend.
- The text-to-speech narrator is silent.
- No system clipboard.

## Updating Horizon

Change `HORIZON_COMMIT`. If the patch no longer applies, update it: build once,
fix `build/horizon`, then run
`git -C build/horizon add -N crates/horizon-client/src/android.rs && git -C build/horizon diff > patches/horizon-android.patch`.
Also keep `native/Cargo.toml`'s dependencies in step with `crates/horizon-client/Cargo.toml`.
