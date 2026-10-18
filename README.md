# NoRootESP

No-root Android ESP overlay. Captures the screen via `MediaProjection`, runs a
YOLO TFLite detector on each frame, and draws boxes + tracers on a
transparent system overlay. No memory hooking, no `/proc/<pid>/mem`, no JNI
patching — works on retail Android with just the two user-granted
permissions.

## Why the old app didn't work

The previous `MainActivity.java` was a stub for a native memory-hack
("nativeEnableAimbot"), which needs:
- a `ptrace`/`process_vm_readv` loop on the Fortnite PID — blocked by SELinux
  on unrooted Android,
- a rebuilt `libnativehack.so` with current game offsets,
- and a way to inject it into the game process.

None of that works without root (or Magisk + Zygisk). This project replaces
it with a vision-based ESP that reads the screen the same way a screen
recorder does, so it needs no root.

## Build

1. Open the folder in Android Studio Giraffe+ (AGP 8.5).
2. Place a Fortnite-trained YOLO TFLite model at
   `app/src/main/assets/model.tflite` (see `MODEL_HERE.txt`).
3. Run on device (Android 8.0+). Grant **Display over other apps**, then
   accept the screen-capture prompt.
4. Launch Fortnite. The overlay draws boxes on detected enemies with a
   tracer from the screen center.

## Tuning

- Confidence slider in the app (default 0.35).
- `inputSize` in `Detector.kt` — raise for accuracy, lower for FPS.
- `iouThreshold` for NMS tightness.

## Files

- `MainActivity.kt` — UI, permission flow, projection launcher.
- `ProjectionService.kt` — foreground service holding `MediaProjection` +
  `ImageReader`, pumps frames into the detector.
- `Detector.kt` — TFLite YOLOv5/v8 inference with GPU delegate fallback.
- `OverlayService.kt` — transparent full-screen overlay, draws boxes + tracers.
