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

## Runtimes (build 2)

- **Box64 (default since build 2):** Debian's `box64` runs the regular x86-64 Wine from WineHQ
  (`wine-stable` 11.0, unpacked to `~/.local/share/roblox-studio/wine-x64`). x86-64 `libunwind8`
  comes from Debian multiarch (`dpkg --add-architecture amd64`). It uses Winlator's conservative
  Box64 settings. Studio 0.745 was checked on this exact Wine on an x86-64 PC: it reaches the
  sign-in screen. Box64 itself can only be tested on the phone.
- **Hangover (build 1, "old way" in the Studio menu):** Studio crashed at start with
  `virtual_setup_exception stack overflow`: exception dispatch through ARM64EC thunks recurses
  (FEX-Emu/FEX#6041, open).
- Each runtime has its own Wine prefix (`prefix` for Hangover, `prefix-box64` for Box64).
- `WINEDLLOVERRIDES=mscoree=;mshtml=`: without it, Wine 11 stops at an "install wine-mono?" dialog
  when it creates the prefix.

## Build log

| Build | Change | Result on the phone |
| --- | --- | --- |
| 1 | Studio-only app, Copy log | Hangover: Studio crashed at start (stack overflow in exception dispatch) |
| 2 | Box64 + x86-64 Wine 11.0 by default; no Mono prompt; runtime switch in the Studio menu | `apt-get update` failed: a Debian mirror mid-sync (trixie-security amd64 list), so Box64 wasn't installed |
| 3 | apt retries the list update, then installs from the lists that did download | not tested yet |

## Building

```sh
./scripts/fetch-proot.sh                      # needs patchelf
echo "sdk.dir=/root/android-sdk" > local.properties
GITHUB_RUN_NUMBER=<n> ./gradlew assembleRelease   # n = build number; raise it every build
```

The APK is in `app/build/outputs/apk/release/app-release.apk`. The last one sent is in `dist/`.
