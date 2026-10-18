# Jjzk Toolbox

Minecraft Bedrock utility app in the Toolbox tradition — world manager,
pack installer, backup/restore, seed browser. Written in Kotlin against
AGP 8.5 / Android 14 (SDK 34), min SDK 21.

## What it does

- Detects every installed Bedrock variant: retail (`com.mojang.minecraftpe`),
  Preview (`com.mojang.minecraftpreview`), Education Edition
  (`com.mojang.minecraftedu`). Picks the newest to work on by default.
- Buckets each install into a known era (Alpha → Chase the Skies) and
  resolves the correct `games/com.mojang/{behavior,resource,skin,world,template}_packs`
  path for both legacy and scoped-storage builds.
- Lists worlds by reading `level.dat` directly (little-endian NBT walker,
  no external native code), including name, game mode, last-opened
  version, size on disk, and tagged era.
- Imports `.mcpack`, `.mcaddon`, `.mcworld`, `.mctemplate`. Classifies
  packs by `manifest.json → modules[0].type` with heuristic fallback for
  pre-1.0 content.
- Backs up any world to a share-ready `.mcworld` zip; snapshots the full
  pack set for the current install.
- Curated seed browser filtered against the installed version so you
  don't paste a 1.21 seed into a 1.19 world.

## Install

- Open in Android Studio Hedgehog or newer.
- `./gradlew assembleDebug`
- `adb install app/build/outputs/apk/debug/app-debug.apk`

## Permissions

Needs `MANAGE_EXTERNAL_STORAGE` on API 30+ to reach
`/sdcard/Android/data/com.mojang.minecraftpe/files/games/com.mojang`.
The app routes you to the system settings page on first launch.
