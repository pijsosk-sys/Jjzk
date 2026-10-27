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
