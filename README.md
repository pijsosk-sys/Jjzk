# Data Wiper

Android app that deletes folders under `/storage/emulated/0/Android/data` and keeps a
**log menu that says exactly why a deletion failed**.

Ready-to-install APK: [`release/DataWiper-1.3.apk`](release/DataWiper-1.3.apk)
(min Android 7.0, target Android 14, signed with the debug-style key in `keystore/`).

## What it does

* Lists every folder in `Android/data` with its package name, the installed app's name,
  size and file count — the same list the screenshot in the request shows.
* **Search box** filters the list live by package name or app name, so `instagram`,
  `mojang` or `com.micro` narrows hundreds of folders down to the ones you want.
  All / None then act on the filtered list only; selections survive changing the search.
* **Long-press a folder to inspect it** — a read-only probe that deletes nothing (see below).
* When `Android/data` cannot be listed at all, the list falls back to installed apps and
  says so in a banner. Folders left behind by **uninstalled** apps cannot appear in that
  fallback, so typing a folder name into the search box offers a
  *"Use … as a folder name"* button that targets it directly.
* **Menu → Copy adb commands** writes `adb shell rm -rf "…"` for everything selected to
  the clipboard — the route that still works from a computer on any Android version.
* Lets you tick folders (All / None shortcuts) and delete them in one run.
* For each folder it tries three routes, in order, and stops at the first that works:
  1. **File API** — plain `java.io.File` recursive delete.
  2. **Storage Access Framework** — `DocumentsContract.deleteDocument` through a folder grant.
  3. **Shizuku** — `rm -rf` run as the shell user (uid 2000), which is the one non-root
     identity that can still reach `Android/data` on Android 11+.
  4. **Root** — `su -c rm -rf` (only when "Use root if available" is ticked).
* After a reported success it **verifies** the folder is really gone, because some
  FUSE/sdcardfs layers report a delete that did not happen.
* Every attempt — successful or not — goes into the log with the concrete reason.

## Inspecting a folder before deleting it

Long-press any row. The app investigates that one folder without touching it and
reports what each route can actually see:

* **File API** — exists, is a directory, readable, writable, parent writable, child
  count, total size, the first 25 entries by name, and a verdict on whether a delete
  would work.
* **SAF** — the derived document id, which grant (if any) covers it, whether the
  document is really found under that grant, its children, and a verdict.
* **Root** — whether `su` answers, `ls -la` of the folder and `du -sh` of its size
  (only when "Use root if available" is ticked).

It also says whether the owning app is still installed, so leftover data from uninstalled
apps is easy to spot. The full report is copied into the log, so you can compare several
folders afterwards.

## The log menu

Tap **Log** (or the menu item). Each row is colour-coded `OK` / `FAIL` / `WARN` / `INFO`.
Tap a row for the full dump, which contains:

* folder, full path, app name, Android version, device model;
* each route tried, whether it succeeded, and the reason it refused, for example
  * `File.exists() returned false` + the scoped-storage explanation,
  * `File.list() returned null` (EACCES — directory not readable),
  * the first 15 individual paths that refused deletion, each with its own diagnosis
    (parent not writable, no write permission, still contains N entries, …),
  * `no persisted tree permission covers primary:Android/data`,
  * `SecurityException` text straight from the document provider,
  * `document provider returned false`,
  * `no su binary (device is not rooted)` or the su exit code plus stderr.

Buttons: **Failures only** filter, **Copy all**, **Export** (writes
`wipe-log-export.txt` into the app's external files dir) and **Clear**.
The log is also mirrored to `files/wipe-log.txt` as it is written, so it survives a crash.

## Important: why deletes fail on modern Android

This is not a bug in the app, it is the platform:

| Android | Can it delete `Android/data`? |
|---|---|
| 10 and older | Yes, with storage permission. |
| 11 – 12 | File API is blocked. "All files access" does **not** lift it. A folder grant on `Android/data` still works on most builds. |
| 13+ | The system folder picker also refuses to grant `Android/data`. Only the **shell user** (Shizuku), **root**, or adb from a computer can remove these folders. |

The app says this up front on the main screen, in the "Why does it fail?" menu item, and
in every relevant log entry — it does not pretend to work around the restriction.

On Android 13+ the three things that still work are:

1. **Shizuku** — built into this app. Press **Set up Shizuku**, allow the prompt, and the
   app runs its deletes as the shell user. On a phone without root, Shizuku is started by
   pairing it once over Wireless debugging in Developer options, and it has to be started
   again after every reboot.
2. **root** — tick "Use root if available"; the app offers this by itself when nothing
   could be listed.
3. **a computer** — menu → Copy adb commands, then paste them with the phone connected.

The status card on the main screen shows which of these is currently usable, and the
"Shizuku" line says exactly what is missing: not installed, not running, too old, or
permission not granted.

Deleting an app's data folder removes logins, offline maps, downloaded media and game
saves for that app. There is no undo.

## Building

```bash
export ANDROID_HOME=/path/to/android-sdk     # needs platform 34 + build-tools 34.0.0
./gradlew assembleRelease
# -> app/build/outputs/apk/release/app-release.apk
```

## Layout

```
app/src/main/java/com/wipe/datacleaner/
  MainActivity.java   folder list, search, permissions, delete run
  LogActivity.java    the log menu
  Deleter.java        the three delete routes + reason reporting
  Probe.java          read-only inspection of a single folder
  ShizukuRoute.java   Shizuku state, permission and shell execution
  Scanner.java        folder listing with File -> SAF -> root -> package-manager fallback
  SafAccess.java      tree grants, document uris, picker intent
  Shell.java          su runner with timeout and stderr capture
  LogStore.java       in-memory log mirrored to disk
```

`MainActivity.java` in the repository root is an older, unrelated file and is not part of
this app's source set.
