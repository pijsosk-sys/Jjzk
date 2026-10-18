# Fortnite ESP (no-root, screen-capture)

A single-screen Android app. One button grants permissions, starts a screen-space
ESP overlay, and launches Fortnite. No root, no bootloader unlock, no process
memory access — it reads the **rendered screen** via `MediaProjection` and finds
enemies with an on-device detector, then draws boxes over everything.

## How it works
`MainActivity` → asks for "Display over other apps" + screen-capture consent →
`CaptureService` mirrors the display into frames → `Detector` (TFLite) finds
enemies in the pixels → `EspView` draws boxes / tracers / distance labels.
UI toggles and sliders drive `Config` live.

## Build
Open the project in **Android Studio** (it creates the Gradle wrapper on first
open) and Run, or from a configured SDK:

```
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

`minSdk 29`, `targetSdk 34`, Kotlin, Material 3.

## The one thing you must supply
`app/src/main/assets/model.tflite` — the enemy detector. The app runs without it
(overlay shows a note) but draws no boxes until it's present. See
`app/src/main/assets/PUT_model.tflite_HERE.txt` for how to train/export one.

## Honest limits
- **Cloud Fortnite has no readable frames of the game world** beyond what's on
  screen; this works on a locally installed build. On the S24 Ultra the local
  APK path is how you get a readable screen.
- **Latency**: you're a frame or two behind — capture + inference cost time.
- **Detection ≠ memory**: accuracy is only as good as the model you train.
- **Anti-cheat**: BattlEye/EAC can detect the overlay window and the
  `MediaProjection` session. Ban risk is real. This is for a device you own.
