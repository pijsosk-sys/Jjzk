# Studio Launcher

A cut-down copy of the Boddy app (from branch `ccr-9a21146d-10t208`) that only runs the real Windows
Roblox Studio, through Debian + Hangover (Wine with x86 emulation) under proot. Experimental: Studio
has not been seen running on a phone yet. Each round, the owner installs a build, taps **Copy log**
after it fails, and pastes the log back here.

## What changed from Boddy

- App id `com.boddy.studio`, name "Studio Launcher": it installs next to Boddy, not over it.
- `targetSdk 28` (as in the v13 "Boddy Studio Test" build): newer targets forbid executable
  mappings of app files, which Hangover needs.
- A fixed signing key in the repo (`app/studio-launcher.jks`, password `studio-launcher`), so every
  build installs over the last one and keeps the multi-GB Studio download. It's a test key, not
  a secret.
- Home screen: Install → **Launch Roblox Studio**, the Studio menu, desktop only, keep-alive
  fixes, and **Copy log** / **Share log** (`LogReport.kt`). The log combines the app log, the last
  crash, and the Studio launcher, sign-in, monitor and Firefox logs, read straight from the Linux
  files. The crash dialog also offers Copy log.
- Ports moved so it never clashes with a running Boddy: VNC 5911, sound 4813, phone bridge 7411.
- Linux side: only the Studio launcher (`studio`, `studio-app.sh`, taken from the v13 APK), Firefox
  (for Login via Browser), Files and Terminal. The AI agents, Minecraft bot and phone control are gone.
- `VncView`: the desktop keeps its size when the keyboard opens (the v13 dex patch, now in source).
- It uses the same prebuilt Linux image as v13 (`assets/rootfs.txt`).

## Building

```sh
./scripts/fetch-proot.sh                      # needs patchelf
echo "sdk.dir=/root/android-sdk" > local.properties
GITHUB_RUN_NUMBER=<n> ./gradlew assembleRelease   # n = build number; raise it every build
```

The APK is in `app/build/outputs/apk/release/app-release.apk`. The last one sent is in `dist/`.
