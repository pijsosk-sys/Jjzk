# START HERE: for a new chat

Paste this into the new chat:

> Read `rex-one/START-HERE.md`, then `rex-one/HANDOFF.md`, `rex-one/README.md`,
> `rex-one/SECURITY.md` and `rex-one/HOW-I-RAN-MINECRAFT.md`, then continue.

## What this project is

**REX One** is a Minecraft **1.21.11 Fabric** client mod. Repo `pijsosk-sys/Jjzk`; everything is in
`rex-one/`. The user plays on **DonutSMP**. Most of the work has gone into **Auto Build**, which builds
`.schem`/`.litematic` schematics by itself. The user also asked for a cleaner UI and a safety check.

The current version is **1.0.0-one.23**. The jar is `rex-one/out/REX-One-1.21.11.jar`; send it to the user
after every release.

## How to act (the user's expectations)

- **The user writes short and casual** ("make it better", "fix all bugs", "do it fast"). Read the intent and
  just do the work end to end. Don't ask lots of questions. If they say "let's talk first", answer and
  don't change anything.
- **Test everything in the real game** (headless test harness, below) before claiming it works. Report
  results honestly, including what still fails. Never claim a fix you didn't verify. One example: a font
  crash "fix" was reverted because it couldn't be shown to work.
- **Make new test builds** instead of reusing the same ones. The user complained about that. Use
  `test/make_builds.py` and `test/make_farms.py`, and add more.
- **At the end of each round:** bump `VERSION` in `build.sh`, add a "New in one.N" section at the top of
  README.md, update HANDOFF.md, commit, push, and send the jar with `SendUserFile`. Include
  screenshots when the UI changes.
- Give the user a short status line when work runs long.
- **Safety and fairness:** don't tune the builder specifically to dodge DonutSMP's anticheat. Do tell the
  user that servers may ban building mods. Natural-looking movement and lag-proofing are fine.

## Git rules

- Work and push **only** on the branch the session names (one.23 is on `ccr-bc57286a-j89fgy`; one.22 and
  older on `ccr-20343ee0-s6lunp`). Bring the latest of these into the session's branch first.
- **No pull requests** unless the user asks.
- End every commit message with:
  ```
  Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
  Claude-Session: <the session's link>
  ```
- Never put model names in commits or files.
- A class that only exists as bytecode is first decompiled (Vineflower, `.cache/vineflower.jar`) and
  committed **unchanged** as a baseline. Only then is it edited, so `git log -p` shows exactly what
  changed.
- Leave no untracked files: a stop hook checks for them.

## Running Minecraft (how the real game is tested)

There's no emulator. It's Mojang's real 1.21.11 client with Fabric, run headless in the cloud
container: `xvfb-run` provides a virtual screen and Mesa llvmpipe renders in software. It joins a
flat singleplayer world, and the test mod (`test/smoke/src/rexsmoke/SmokeDriver.java`) drives Auto
Build, then compares every block on the server with the blueprint. No Minecraft account is
needed: it uses offline username "Smoke". Full story in `HOW-I-RAN-MINECRAFT.md`.

**On a fresh container, do this once.** `.cache/`, `.runtime/` and `.work/` are gitignored, so a new chat
starts without them:

```sh
cd rex-one
# needs Java 21, python3, curl, zip/unzip, xvfb-run, Mesa GL (apt install openjdk-21-jdk xvfb libgl1-mesa-dri zip unzip curl python3)
./tools/setup.sh                 # Minecraft 1.21.11 client + libraries + intermediary remap -> .cache/
./test/runtime/setup.sh          # Fabric loader, LWJGL natives, Fabric API, game assets -> .runtime/
./test/runtime/make-world.sh     # the flat test world -> .runtime/world
# Vineflower (decompiler) for bytecode-only classes, if needed:
#   curl -sSfL -o .cache/vineflower.jar https://repo1.maven.org/maven2/org/vineflower/vineflower/1.10.1/vineflower-1.10.1.jar
```

**The base jar.** `build.sh` needs an earlier REX One jar to build on. The old scratchpad copy
(`base/one15.jar`) is gone in a new chat, so copy the committed jar first. The build writes to
`out/`, so it must not read from there too:

```sh
cp out/REX-One-1.21.11.jar /tmp/base.jar && ./build.sh /tmp/base.jar
```

**Running a test:**

```sh
python3 test/make_builds.py /tmp/builds              # or make_house.py / make_farms.py
GAME_DIR=/tmp/g1 test/runtime/run.sh out/REX-One-1.21.11.jar /tmp/builds/castle-gate.schem \
  -Drexsmoke.presets=FAST,SERVER -Drexsmoke.creative=true -Drexsmoke.clean=true -Drexsmoke.maxSeconds=240
cat /tmp/g1/rexsmoke-results.txt                     # result per preset, wrong blocks, last states
ls /tmp/g1/screenshots/                              # pictures of the build and the screens
```

- A run takes 1–5 minutes. The game quits by itself when done.
- Each result line looks like:
  `FAST COMPLETED 93.6s ... server: 191/191 match ... | Complete: ...`
  - `PAUSED` or `RUNNING` instead of `COMPLETED` means it stopped or ran out of time; the
    "last builder states" lines show where.
  - `builder ticks: average/worst ms` is the lag measurement.
- `-Drexsmoke.stallSeconds=20` (the default) ends a run after 20 s without progress, and
  `maxSeconds` is the hard limit.
- Without `-Drexsmoke.creative=true` it runs in survival with a fixed set of materials (fits the test
  house).
- **If it fails at start:**
  - "Aborted" with `liblwjgl_stb` in the log is the rare font crash: just run again.
  - Other errors: check the log you redirected, and `GAME_DIR/logs/latest.log`.
- `test/runtime/record.sh out.mp4 <run.sh args>` makes a video, if the user asks for one.

**How the user runs it on their own PC:**
1. Install **Fabric Loader 0.18.4+** for Minecraft 1.21.11.
2. Put in `mods/`:
   - `REX-One-1.21.11.jar` (the file in `out/`, sent with `SendUserFile`),
   - **Fabric API**,
   - **Baritone** (fabric 1.17.0).
3. Replace the old REX jar; don't keep two.
4. Schematics go in `.minecraft/schematics/` (`donut/` subfolder); the bundled ones install
   themselves.
5. The menu key opens the new menu. Auto Build is in the menu sidebar.

## Building and testing

```sh
cd rex-one
./build.sh <base jar>   # we use scratchpad base/one15.jar; any earlier REX One jar works as the base
# REX_OUT=/tmp/x.jar REX_WORK=/tmp/w ./build.sh <base>   builds somewhere else, so tests using out/ keep running
test/runtime/run.sh out/REX-One-1.21.11.jar some.schem -Drexsmoke.presets=FAST,SERVER -Drexsmoke.creative=true -Drexsmoke.clean=true
cat .runtime/game/rexsmoke-results.txt      # or $GAME_DIR/rexsmoke-results.txt
```

- Set `GAME_DIR=...` to run two games side by side. **Never run more than two**: the CPU starves, runs
  time out, and the font crash shows up.
- `-Drexsmoke.ping=200 -Drexsmoke.jitter=60 -Drexsmoke.rollback=0.03 -Drexsmoke.rubberband=15`
  simulates a laggy server (the lag simulator is in `test/smoke/src/rexsmoke/LagSim.java`).
- `-Drexsmoke.ui=true` takes screenshots of the menu and builder screen and clicks the menu for real.
  `-Drexsmoke.starts=true` presses Start on every library file.
- Profile with `-XX:StartFlightRecording=filename=x.jfr,settings=profile`, then `jfr print`.
- **Test builds:**
  - `test/make_house.py` (house, tower, pools)
  - `test/make_farms.py` (berry, cactus, wart; test only since one.23)
  - `test/make_donut.py` (the bundled DonutSMP farms and bases)
  - `test/make_builds.py` (castle gate, sky bridge, pixel art, spiral tower, garden, storage room,
    modern house)
- Names in the code are Fabric intermediary (`class_310` = MinecraftClient). Look names up in
  `.cache/yarn/mappings/mappings.tiny`.

## What has been done (oldest first)

| Version | Done |
| --- | --- |
| one.16 | Custom speed editor; Tuff Shader as a normal module |
| one.17 | Finishes real builds, removes pillars/supports at the end, smooth human-like aim, sprint |
| one.18 | Fixed the lag on big farms, lava buckets, better builder screen |
| one.19 | No stutter when placing, correct HUD materials list, faster Fast preset |
| one.20 | Start always starts (missing materials set aside), 3 new farms, grown plants count as built, doors, more natural movement |
| one.21 | **Server preset** + lag-proofing: never clicks unconfirmed blocks, repairs wrong blocks, waits after setbacks, slows down when the server refuses blocks, breaks out when walled in |
| one.22 | **Security audit** (SECURITY.md, nothing malicious, Spotify HUD's PowerShell removed by `tools/Harden.java`); **new module menu** `OneMenuScreen` (categories, favourites, search, cards, settings panel); 7 new test builds (6 added to the library) and the fixes they found (pillaring, walled-in holes, watchdog, search budget) |
| one.23 | DonutSMP library: simple farms out, cactus farm and spawner base small/big in (big cactus farm test-only, it stalls) (`test/make_donut.py`); farm harvest check in the test; builder wades shallow water, pours floor water last, bucket aim slack, no pillaring from containers |

## Key code

- `src/dev/donut/client/autobuild/AutoBuilderController.java`: the builder (placing, supports,
  pillaring, cleanup, lag handling, break-out).
- `BuilderNavigator.java` / `BuilderPathfinder.java`: walking, doors, the watchdog, the failed-search
  memory.
- `src/dev/donut/client/gui/OneMenuScreen.java`: the new menu. `AutoBuilderScreen.java`: the builder
  screen.
- `src/rex/BuildTempo.java`: the presets (Safe, Balanced, Fast, Server, Custom).
- `tools/Harden.java`: runs in `build.sh` and empties anything that starts a program.

## Still open / next ideas

- **Offered but not done:** run the game and log every network connection it makes, to prove at
  runtime that it sends nothing anywhere. The user asked "did you check for account stealing". The
  code check says it's clean; this would prove it while running.
- The spiral tower (floating stairs inside a closed tower) still struggles, and it's not in the
  library.
- A rare font crash at launch (`dev.krypton.render.Fonts`, native stb) has only been seen on the test
  machine under load. The cause isn't known.
- Not yet tested on a real server: the user can test on DonutSMP with the Server preset and send the
  builder's messages back.
- **Ideas:**
  - crop farms (need hoeing farmland),
  - refilling water buckets in survival,
  - building in strips to walk less,
  - a faster starter house (3,201 blocks).
