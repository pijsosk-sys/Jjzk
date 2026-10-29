# Handoff: start a new chat from here

Everything is on branch `ccr-20343ee0-s6lunp` of `pijsosk-sys/Jjzk`, in `rex-one/`. To pick up in a
new chat, tell it: *"Read rex-one/HANDOFF.md, rex-one/README.md and
rex-one/HOW-I-RAN-MINECRAFT.md, then continue."*

## What exists now (version 1.0.0-one.20)

**Jar:** `rex-one/out/REX-One-1.21.11.jar`. Drop it into `mods/` in place of the old jar; it
needs Fabric Loader 0.18.4+, Fabric API and Baritone, as before.

**Auto Build:**
- **one.20:** Start always starts: blocks with no material are set aside and built when the item
  turns up, with a full missing list at the end (`setAsideMissing`, `checkMissing`,
  `missingSummary`). If you carry none of the materials, the screen says so. Grown plants and
  stair shapes count as built (`MaterialTracker.growthOnly`). Sight lines use the block outline
  (cactus and soul sand were unreachable). Doors and gates are opened on the way through and shut
  at the end (`shutDoors`, `BuilderNavigator.clickDoor`), and no support goes beside a cactus or
  door (`keepClear`). Slabs and stairs count as ground (`feetCell`, `solidFooting`). Three new
  bundled farms: sweet berry, cactus, nether wart (`test/make_farms.py`). Natural look: easing,
  turn wobble, random aim among near-best points, and walk drift. The test house completes for
  the first time.
- **one.19:** no stutter on placement (pathfinder collision checks cached and direct, pillar spot
  search sorted and limited to reachable spots: average tick 3.8 -> 0.8 ms), Fast turns 120°/tick
  (9.6 blocks/s on the big farm), and the materials list no longer counts flowing fluids or shows
  stale or creative shortages. The driver logs `builder ticks: ...` per run.
- **one.18:** big farms no longer lag (ghost renderer 29% -> 8% of a frame, cached 3D preview),
  lava sources are poured with lava buckets (after every solid block), an Options row and a
  layer progress strip on the builder screen, and the screen no longer pauses the build.
- **one.17:** finishes the bundled cabin on Fast (it stalled at half), removes its pillars and
  supports at the end (*Remove Supports*), pillars next to the block it needs, drops 2-3 blocks to
  get down, only walks to spots it can really place from, sprints, and aims smoothly
  (*Smooth Aim*, *Turn Speed*). It asks the real placement planner whether a spot works before
  walking there, pours water only after the walls around it, builds direction-sensitive blocks
  first, and breaks and re-places its own block if one comes out facing the wrong way. Old
  schematics (pre-1.17 cauldrons, `chain`) load. See README.
- **Custom speed editor (one.16):** clicking Custom on the builder screen opens an editor for
  every Custom number (drag bars, -/+), Start from Safe/Balanced/Fast, Reset, and the
  every-preset switches. *Edit custom speed...* reopens it.
- **Speed presets:** Safe / Balanced / Fast / Custom. On the big kelp farm Fast does ~7 blocks/s
  against 1.5/s for the original jar, with zero wrong blocks.
- **Pillar Up:** jumps and places dirt or cobblestone under itself to reach high blocks.
- **Water and kelp:** water placed with buckets, kelp planted. The compact kelp farm completes
  85/85.
- **Signs:** the sign editor gets "Done" pressed automatically for signs it placed.
- **Blocks that face the player** (signs, stairs, banners): finds a working aim angle.
- **Walk Closer:** stops closer to blocks, so it stops less often.
- **Shulker Restock:** a setting, off by default. It refills from a carried shulker box.
- **Safety stops:** low health or hunger (on), players nearby (off).
- **Remembers** the loaded schematic and position per server.

**Look:**
- **Tuff Graphics** (Render module, bindable): a one-pass shader, no Iris needed, with Soft /
  Tuff / Vivid styles. A normal module only: second row of the Render panel (one.16 moved it up
  from below the panel's bottom edge and took it off the builder screen).
- **Schematic Ghost** (Render module, bindable): ghost on/off, plus layer view.
- **Builder screen:** no HUD clutter on top, plus Ghost, Layers, Skipped list and Copy
  shopping list.

**Bug fixes in the original jar:** a startup crash (11 mixins with the wrong `@At` format), and the
bundled big kelp farm refusing to load (saved by a newer Minecraft).

## How to build and test

```sh
cd rex-one
./build.sh /path/to/original/REX-One-1.21.11.jar     # -> out/REX-One-1.21.11.jar
# (a previously built REX One jar, e.g. one.15, also works as the base: the build replaces its own classes)
./test/runtime/setup.sh && ./test/runtime/make-world.sh   # once
python3 test/make_house.py tower.schem tower          # house | tower | signs
./test/runtime/run.sh out/REX-One-1.21.11.jar tower.schem -Drexsmoke.presets=FAST -Drexsmoke.creative=true
cat .runtime/game/rexsmoke-results.txt                # result, time breakdown, last states
```

Driver options:
- `-Drexsmoke.presets=SAFE,BALANCED,FAST,OFF` picks the runs.
- `-Drexsmoke.creative=true` uses creative.
- `-Drexsmoke.restock=true` tests the shulker restock.
- `-Drexsmoke.shader=true` takes shader screenshots.
- `-Drexsmoke.thirdPerson=true` films from behind; `-Drexsmoke.clean=true` clears pop-ups.
- `test/runtime/record.sh out.mp4 <run.sh args>` screen-records a run with ffmpeg.
- `python3 test/make_house.py pools.schem pools` makes the lava and water basins test.
- Profiling: add `-XX:StartFlightRecording=filename=x.jfr,settings=profile` to run.sh, then
  `jfr print --stack-depth 80 --events jdk.ExecutionSample x.jfr`; the render thread is "main".
- `-Drexsmoke.ui=true` screenshots the module menu, the builder screen and the Custom editor, then quits.
- `-Drexsmoke.maxSeconds=N` and `-Drexsmoke.stallSeconds=N` set the time limits.
- `GAME_DIR=...` runs two games side by side.
- `-Drexsmoke.starts=true` presses the builder screen's real Start on every library file and logs
  what happened (and the screen's message). Add `creative=true` for creative.
- `python3 test/make_farms.py` regenerates the bundled farm schematics.
- Run at most two games at once: four starve the CPU (10 fps) and runs time out.

The full story of how the real game runs headless is in `HOW-I-RAN-MINECRAFT.md`.

## Where the code is

- `src/dev/donut/client/autobuild/AutoBuilderController.java` is the builder. Look for `step`,
  `pillarStep`, `planWater`, `planSampled`, `planAlongSent`, `matches`, `settle`.
- `src/dev/donut/client/autobuild/BuilderNavigator.java` walks (`walkReach`).
- `src/dev/donut/client/gui/AutoBuilderScreen.java` is the screen.
- `src/rex/BuildTempo.java` holds the settings and presets; `BuildRestock`, `BuildMemory`,
  `SchematicGhost` and `TuffShader` are the new modules and helpers.
- `resources/assets/rex/shaders/post/tuff.fsh` and `post_effect/tuff*.json` are the shader.
- Names are Fabric intermediary (`class_310` = MinecraftClient and so on). The decompiled
  baselines are each in their own commit; `git log -p` shows every change.

## Known gaps / next ideas

- Rare: Balanced on the compact kelp farm stops two blocks short about one run in twelve (a
  piston in a cramped water spot; one.19 does it too). Also rare: an observer comes out facing the
  wrong way and is re-placed three times, then set aside.
- Iron doors can't be opened by hand, so the builder treats them as walls.
- The starter house (3,201 blocks, many materials) builds but slowly.
- Not tested on a real server or against an anticheat; start with **Balanced** there.
- Ideas: refill water buckets in survival, build in strips to cut walking further, more farm
  schematics (crops need tilled farmland, which needs a hoe step the builder doesn't have yet).
