# How I ran Minecraft to test REX One

This explains how the real Minecraft 1.21.11 client was started with REX One, put into a world
and made to run Auto Build, all on a Linux machine with no monitor, no graphics card and no
Minecraft account, and how to do the same yourself.

There's no emulator or fake game here. It is Mojang's own client jar, started the way the Fabric
launcher starts it.

## The short version

```sh
cd rex-one
./tools/setup.sh                      # Minecraft 1.21.11 client + libraries (once)
./build.sh /path/to/REX-One-1.21.11.jar   # build the jar into out/
./test/runtime/setup.sh               # launcher libraries, Fabric API, assets (once)
./test/runtime/make-world.sh          # a flat test world (once)
python3 test/make_house.py house.schem
./test/runtime/run.sh out/REX-One-1.21.11.jar house.schem -Drexsmoke.presets=FAST
cat .runtime/game/rexsmoke-results.txt
```

Screenshots land in `.runtime/game/screenshots/`.

You need Linux with Java 21, python3, curl, unzip/zip, `Xvfb` (`xvfb-run`) and Mesa's OpenGL
drivers (`libgl1-mesa-dri`). On Ubuntu that's
`apt install openjdk-21-jdk xvfb libgl1-mesa-dri zip unzip curl python3`.

## Step by step

### 1. Get the game

`tools/setup.sh` reads Mojang's public version manifest
(`piston-meta.mojang.com/mc/game/version_manifest_v2.json`), finds 1.21.11, and downloads:

- `client.jar`, the game itself, from `piston-data.mojang.com`;
- every library the game lists (LWJGL, Netty, Brigadier, DataFixerUpper, JOML, and so on).

It also makes `client-intermediary.jar`: the same game with Fabric's stable "intermediary" names
(`class_310` and so on) instead of Mojang's obfuscated ones (`a`, `b`...). It does this with
Fabric's `tiny-remapper` and the intermediary mappings from `maven.fabricmc.net`. REX One is
written against those names, so the build compiles the new code against this jar.

No account is needed for this. These files are public downloads.

### 2. Get Fabric

`test/runtime/setup.sh` asks Fabric's meta server how to launch loader 0.18.4 on 1.21.11
(`meta.fabricmc.net/v2/versions/loader/1.21.11/0.18.4/profile/json`). That's the same profile
the Fabric installer writes into the official launcher. It downloads what that lists: Fabric
Loader, Sponge Mixin, ASM and the intermediary mappings. It also fetches:

- the LWJGL **natives** for Linux (the window, OpenGL and input code), which the game's own
  library list marks per operating system;
- **Fabric API** 0.141.4, which REX One needs;
- Fabric's **client game test API**, used only once, to create the world (step 4);
- the **game assets** (fonts, language files, the built-in resource packs) from
  `resources.download.minecraft.net`. Sounds and music are skipped because nobody is listening.

The assets matter. Without them the game started but crashed during its first resource reload.

### 3. Start it without a screen

`run.sh` starts Java the way a launcher does:

```
java -cp <Fabric libraries>:<game libraries>:client.jar \
  net.fabricmc.loader.impl.launch.knot.KnotClient \
  --version 1.21.11 --gameDir <dir> --assetsDir <assets> --assetIndex 29 \
  --accessToken 0 --username Smoke --quickPlaySingleplayer smoke
```

- `KnotClient` is Fabric's launcher class. It finds the game on the classpath, loads every jar
  in `<gameDir>/mods`, applies their mixins and starts Minecraft.
- `--accessToken 0 --username Smoke` runs in offline mode. That's enough for singleplayer; the
  log shows harmless "couldn't connect to Realms" errors.
- `--quickPlaySingleplayer smoke` opens the world named `smoke` straight from the title screen.
- It runs inside `xvfb-run`. Xvfb is a virtual X display that exists only in memory, so the game
  gets a real window and an OpenGL context with no monitor. `LIBGL_ALWAYS_SOFTWARE=1` makes Mesa
  draw on the CPU (llvmpipe, OpenGL 4.5). That's slow, roughly 10–20 fps here, but it is real
  rendering, so screenshots show exactly what you would see.
- `options.txt` is written first with `onboardAccessibility:false`. On a fresh game directory
  Minecraft first shows a "Welcome / Narrator?" screen, which would block quick play. I found that
  by screenshotting the virtual display mid-run.

### 4. Make a world

`make-world.sh` starts the game once with vanilla plus Fabric only, and a tiny test mod
(`test/worldgen`). That mod uses Fabric's client game test API (`-Dfabric.client.gametest`) to
create Fabric's standard flat test world: seed 1, peaceful, superflat, ground at y = −61. The
save is kept as `.runtime/world` and copied into every run.

The game test framework steps the client and the server in lockstep. With REX One loaded, the
integrated server never got past "Loading 0 persistent chunks" in that mode. It's the same with
the original jar and with no build changes at all; in normal play it loads in about 3 seconds.
That's why the framework is used only to make the world, and the real test runs with normal
ticking.

### 5. Drive the builder

`test/smoke` is a small extra Fabric mod (`rexsmoke`). It's only active when
`-Drexsmoke.schem=<file>` is passed. Once the player is in the world it:

1. clears the build area, and either gives survival materials or switches to creative with REX's
   Creative Mode on;
2. loads the schematic with REX's own loader, sets it at 0, −60, 0, picks a Build Tempo preset,
   and presses Start, exactly what the Start button does;
3. watches every tick: it counts how long the builder spends in each state (walking, aligning,
   placing, waiting for the server) and stops at Complete, after 20 seconds with no progress, or
   after `-Drexsmoke.maxSeconds`;
4. **checks every block on the server.** For each blueprint block it reads the integrated
   server's world, not the client's guess, and counts exact matches and wrong blocks;
5. takes a screenshot of the world and of the Auto Build screen, then moves on to the next preset
   or quits.

Options: `-Drexsmoke.presets=SAFE,BALANCED,FAST,OFF` picks the runs (`OFF` turns Build Tempo
off, giving the original pacing). `-Drexsmoke.creative=true` uses creative and Creative Mode.
`-Drexsmoke.maxSeconds=N` sets the time cap.

### 6. Read the results

`.runtime/game/rexsmoke-results.txt` has one line per run, for example:

```
FAST  RUNNING  120.1s  7.0 placed/s  placements sent 846  server: 846/15460 match (846 exact), ...
     49.1%  Placing block
     39.7%  Walking to #, -#, # / # steps
     10.2%  Aligning for placement
```

The full game log is whatever the launch printed; the scripts send it to stdout.

## How problems were found this way

- **Launch crash.** The first launch crashed in the integrated loader: eleven mixins stored a
  single `@At` as an array, and MixinExtras rejects that. Your original jar crashed the same way,
  so it wasn't the new code. `build.sh` now fixes those annotations.
- **The walk/jump/aim loop.** The builder froze on "Aligning for placement". Temporary debug
  prints showed the player's height going up and down every 12 ticks. It was jumping onto a
  block, arming the placement mid-air and dropping back out of reach. Fixed, and the reason
  table proves it.
- **Where the time goes.** The reason table showed walking at 57% and alignment at 27%. That led
  to the candidate-scan and "place along the rotation the server already has" changes, which
  took the kelp farm from 1.5 to 7 blocks/s.
- **To see the screen at any moment**, while the game is running:

  ```sh
  DISPLAY=:<display> XAUTHORITY=/tmp/xvfb-run.*/Xauthority import -window root screen.png
  ```

  `xvfb-run -a` picks the display number; read it from the java process's environment.

## Limits

- Singleplayer only. A real server adds network delay and possibly an anticheat; Balanced is
  the safe first try there.
- Software rendering is slow, so timings compare runs with each other, not with your PC.
- Joining a real online server would need your Minecraft account login, which this setup doesn't
  do on purpose.
