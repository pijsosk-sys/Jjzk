# Boddy Studio launcher

Source of `assets/boddy/studio` and `assets/boddy/studio-app.sh` from the Boddy Studio Compat APK.
Copy `studio` back into the APK's `assets/boddy/` (or to `/opt/boddy/studio` inside the Boddy desktop) to use it.

## `LAUNCH FAILED: ... CERTIFICATE_VERIFY_FAILED: self-signed certificate in certificate chain`

The launcher downloads Studio over HTTPS from `clientsettingscdn.roblox.com` and `setup.rbxcdn.com`.
This error means something on the network is replacing those sites' certificates: usually a VPN, ad-blocker
or antivirus app with HTTPS filtering, a work/school network, or a Wi-Fi sign-in page.
Certificates installed in Android settings are not visible inside Boddy's Debian system.

The launcher now prints the certificate chain it was given, so you can see who issued it. To fix it:

1. Turn off HTTPS filtering in that app, or switch to mobile data / another Wi-Fi, and retry.
2. If you trust the filter, export its root certificate (PEM) and, in the Boddy terminal, run:
   `cp ~/Phone/Download/<name>.pem /usr/local/share/ca-certificates/<name>.crt && update-ca-certificates`
   or set `BODDY_CA_FILE=/path/to/<name>.pem` for the launcher only.

Certificate verification is never turned off.

`Diagnostics` in the Studio menu now also checks HTTPS access to both Roblox hosts.

## Wine `warn:font:` messages

Lines like `Ignoring font "/usr/share/wine/fonts/vgasys.fon"` and `unsupported font format 10d5a4d` are harmless.
Wine's own parser skips old `.fon` bitmap fonts and FreeType loads them instead.
They only showed up because the launcher ran Wine with `WINEDEBUG=warn+all`. It now defaults to `-all,err+all`.
Set `BODDY_WINEDEBUG=warn+all` to get the full log back.

## The Studio app

The desktop icon runs `studio-app.sh`: a menu, then a progress window (download progress, first-time Wine setup) that
closes when Studio opens, instead of a terminal. Cancel stops the launcher and its downloads. Failures and Studio
crashes show a dialog with **Show log**; Diagnostics opens in a window. The menu choice can be passed as an argument,
e.g. `studio-app.sh "Open Studio"`. Dialog text is ASCII only: under Boddy's default C locale, zenity rejects
arguments with other characters.

## Signing in

Studio's built-in login window needs Microsoft WebView2, which does not run here, so it shows
"An error occurred while launching Studio login". Choose **Login via Browser** instead:

1. Firefox opens inside the Boddy desktop. Wine hands the link to `studio --open-url`, which starts Firefox
   with a clean environment (no Wine variables, signal state or raised file limit), Firefox's seccomp sandboxes off
   (they conflict with proot), and a separate low-memory profile in `~/.local/share/roblox-studio/firefox-profile`.
2. Sign in to Roblox there.
3. When Firefox asks to open the `roblox-studio-auth` link, allow it. The launcher hands the link to the running Studio.

The launcher registers itself for `roblox-studio-auth:` links in `~/.local/share/applications` and `~/.config/mimeapps.list`.
Sign in from Firefox in the Boddy desktop, not the phone's browser: Android cannot pass the link back to Studio.

If Firefox closes unexpectedly, a dialog says why (crash, or killed for memory) and its log is saved to
`Downloads/Boddy-Studio-firefox.log`.

## Screen size and typing

- **Cut-off windows:** Boddy's desktop is 1100-1600 px wide with the phone's shape, so it is often only 500-720 px tall,
  less than Studio's windows need. While Studio runs, the launcher enlarges the desktop with `xrandr` to at least
  768 px tall (same shape, so the phone still shows all of it) and restores it when Studio exits.
  `BODDY_STUDIO_HEIGHT` changes the target.
- **Desktop shrinking while typing:** the desktop view is resized when the keyboard opens, and the viewer scaled the
  desktop to fit both width and height, so it shrank to a small picture. `rebuild_apk.py` changes that one
  `Math.min` in `VncView.onDraw` to `Math.max`: the desktop keeps its size and the view follows the mouse cursor
  above the keyboard. Without the keyboard nothing changes.

## Boddy keeps reconnecting

"Reconnecting to the desktop…" means the Linux desktop stopped. With Studio running this is almost always Android:

- **Android 12+ phantom process limit:** Android kills an app's Linux processes beyond 32 in total, or that use a lot
  of CPU in the background, checking every few minutes. The Boddy desktop plus Wine, Studio and Firefox exceeds that.
- **Low memory:** Android's low-memory killer stops Boddy.

`Downloads/Boddy-Studio-monitor.log` gets a line every 15 s while Studio runs (process count and free memory);
after a restart its last line shows which of the two it was.

The launcher keeps the count down (Firefox runs as two processes; Wine's Bluetooth and USB drivers are off), but only
Android settings remove the limit:

- **Android 14 and newer:** Settings → System → Developer options → **Disable child process restrictions**.
- **Android 12 and 13:** once, from a computer with ADB (or Wireless debugging):
  ```
  adb shell "/system/bin/device_config set_sync_disabled_for_tests persistent"
  adb shell "/system/bin/device_config put activity_manager max_phantom_processes 2147483647"
  adb shell settings put global settings_enable_monitor_phantom_procs false
  ```

Also allow Boddy to run in the background (no battery optimization), and close other apps to free memory.

## Storage and offline use

After a successful install the downloaded package archives are deleted, and only the newest previous Studio is kept as a backup.
If the update check fails (offline, or a certificate problem) and Studio is installed, the installed version opens.

## Tests

`python3 -I test_launcher.py`

## Rebuilding the APK

`rebuild_apk.py` swaps in this launcher, updates the embedded `boddy-studio-fix-source.zip` and bumps `versionCode`.
Every other entry, including `classes.dex` and native libraries, is copied unchanged with its original compression.
Android build-tools provide `zipalign` and `apksigner`:

```
python3 rebuild_apk.py Boddy-Studio-Compat.apk unsigned.apk 4
zipalign -p -f 4 unsigned.apk aligned.apk
apksigner sign --ks boddy-studio.jks --ks-key-alias boddy --out Boddy-Studio-Compat-v4.apk aligned.apk
```

The original signing key was not shipped, so a build signed with a new key cannot update an installed copy in place.
Keep the keystore outside the repo, and sign later builds with that same key so they install as updates.
The app copies `assets/boddy/*` into `/opt/boddy` every time the desktop starts, so a new launcher takes effect on the next start.
