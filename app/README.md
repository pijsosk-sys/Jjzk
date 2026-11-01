# Roblox Publish

A small Android app that sends a Roblox place file straight to Roblox. No Studio, no Creator
Dashboard, no ID numbers to find.

Built because Roblox Studio under Boddy cannot reach Roblox at all — its web layer never starts
(`WebBrowserManager: Skipping preInitializeWebBrowser`, `localhost:13469` refused), so Publish,
Ctrl+S and every Luau route end at `Feature not available in offline mode`. Open Cloud is the only
way out, and doing that by hand on a phone means hunting a place id out of a truncated address bar.

## What it does

1. Type your Roblox username → it lists the experiences that account owns
2. Pick one → it finds that experience's starting place itself
3. Pick a `.rbxl` with the system file picker
4. Paste an API key (the one thing the website still has to give you)
5. Publish — and then it **checks the experience actually changed**

That last step is the point. A `200` from Roblox means it accepted the file, not that players will
see it: a `Saved` version and a publish to a place nothing loads both answer `200` and change
nothing. The app reads the experience's `updated` time before and after and polls for a minute, so
it can say *live* or *accepted but nothing moved* instead of leaving you to find out in game.

## The endpoints

Only the last one needs the key.

| call | purpose |
|---|---|
| `POST users.roblox.com/v1/usernames/users` | username → user id |
| `GET games.roblox.com/v2/users/{id}/games` | that account's experiences |
| `GET games.roblox.com/v1/games?universeIds=` | the experience's `rootPlaceId` and `updated` |
| `POST apis.roblox.com/universes/v1/{universe}/places/{place}/versions?versionType=Published` | the upload |

The publish path was settled by asking the live API with a deliberately bad key: `universes/v1`
answers `401`, while the `cloud/v2` path some documentation gives answers `404`.

A browser cannot do any of this — none of those endpoints send `Access-Control-Allow-Origin` and
`OPTIONS` returns 405, so a web page is not an option. Hence a native app.

## The key

`create.roblox.com/dashboard/credentials` → your key → Access Permissions → add the system
**universe-places** → turn its toggle on → add your experience → tick **write** → no IP restriction,
no expiry. The app has a button that opens that page.

Missing any one of those gives `403 Scope not authorized`, which the app explains rather than
printing the bare status.

## Building

No Gradle, no Android Studio:

```
R8_JAR=/path/to/r8.jar bash app/build.sh
```

- `android.jar` from the Debian package `android-sdk-platform-23`
- `aapt2` from `aapt`, plus `zipalign` and `apksigner`
- `d8` from Google's Maven (`com.android.tools:r8`), run on the JVM

Compiled against API 23 and targeting 34: it uses nothing newer than 23, and Android 15 refuses to
install anything targeting below 24. The UI is built in code, so there are no layout XMLs to keep in
step. No lambdas — API 23's `android.jar` has no `java.lang.invoke.MethodHandles`, so `javac` cannot
compile them against it.

`app/build/` is not committed: the build generates a throwaway signing keystore inside it.

## Not verified

**It has never been run.** There is no emulator or device here. It compiles, the APK is structurally
sound and v1/v2/v3 signatures verify, and every endpoint it calls was checked by hand against the
live API — but no screen has been drawn and no request has gone out from inside the app. Treat the
first launch as the real test.

It is signed with a throwaway key generated at build time, so it installs alongside Boddy rather
than over it.
