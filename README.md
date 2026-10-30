# Data Wiper

Android app that deletes folders under `/storage/emulated/0/Android/data` and keeps a
**log menu that says exactly why a deletion failed**.

Ready-to-install APK: [`release/DataWiper-1.0.apk`](release/DataWiper-1.0.apk)
(min Android 7.0, target Android 14, signed with the debug-style key in `keystore/`).

## What it does

* Lists every folder in `Android/data` with its package name, the installed app's name,
  size and file count — the same list the screenshot in the request shows.
* Lets you tick folders (All / None shortcuts) and delete them in one run.
* For each folder it tries three routes, in order, and stops at the first that works:
  1. **File API** — plain `java.io.File` recursive delete.
  2. **Storage Access Framework** — `DocumentsContract.deleteDocument` through a folder grant.
  3. **Root** — `su -c rm -rf` (only when "Use root if available" is ticked).
* After a reported success it **verifies** the folder is really gone, because some
  FUSE/sdcardfs layers report a delete that did not happen.
* Every attempt — successful or not — goes into the log with the concrete reason.

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
| 13+ | The system folder picker also refuses to grant `Android/data`, so only **root** (or adb/MTP from a computer) can remove these folders. |

The app says this up front on the main screen, in the "Why does it fail?" menu item, and
in every relevant log entry — it does not pretend to work around the restriction.

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
  MainActivity.java   folder list, permissions, delete run
  LogActivity.java    the log menu
  Deleter.java        the three delete routes + reason reporting
  Scanner.java        folder listing with File -> SAF -> root -> package-manager fallback
  SafAccess.java      tree grants, document uris, picker intent
  Shell.java          su runner with timeout and stderr capture
  LogStore.java       in-memory log mirrored to disk
```

`MainActivity.java` in the repository root is an older, unrelated file and is not part of
this app's source set.
