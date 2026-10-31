# Handoff: start a new chat from here

Everything is on branch `ccr-20343ee0-s6lunp` of `pijsosk-sys/Jjzk`, in `rex-one/`. To pick up in a
new chat, tell it: *"Read rex-one/HANDOFF.md, rex-one/README.md and
rex-one/HOW-I-RAN-MINECRAFT.md, then continue."*

## What exists now (version 1.0.0-one.23)

**Jar:** `rex-one/out/REX-One-1.21.11.jar`. Drop it into `mods/` in place of the old jar; it
needs Fabric Loader 0.18.4+ and Fabric API. Baritone is bundled inside the jar and nothing in
one.23 needs it any more, so a missing or changed Baritone no longer breaks anything.

**Overlord (one.23, rewritten):** `src/rex/overlord/` plus `src/rex/RexOverlord.java`.
- The shipped module did not work: in game it stopped after about twenty seconds with
  `Stopped: baritone.api.pathing.goals.GoalNear`, because every move went through reflection by
  name and `GoalNear` does not take four ints. The whole engine is now typed source compiled
  against the Minecraft jar, with no reflection into the game or into Baritone.
- Walking is `BuilderNavigator`; mining is the new `rex.overlord.Mine` (scan loaded chunks, walk
  to ore, otherwise cut a staircase down and strip-mine). Drops are walked over and collected.
- `Overlord` is the ladder (inventory -> first missing thing -> one step), `Work` the window
  clicks, `Sites` the workstations/portal/enchanting ring, `Fight` defence and eating, `Dig`,
  `Reach`, `Scan`, `Stock`, `Ids`, `Recipes`, `Stage` the rest.
- Chat: `.overlord start | stop | status | hud`. HUD: stage, what it is doing, blocks broken.
- Tested in the real game with a new harness, `test/runtime/run-overlord.sh` plus
  `test/overlord/src/rexoverlord/OverlordDriver.java`: nine scenarios, each checking the
  inventory for what it asked for. Results in README.
- Not reachable in a flat test world, so still unproven in game: the nether, ancient debris and
  the bastion upgrade template.

**Auto Build:**
- **one.22:**
  - **Security audit** (SECURITY.md): nothing malicious. Spotify HUD's PowerShell calls are
    removed by `tools/Harden.java`, which runs in `build.sh` and empties any method that starts a
    process.
  - **New module menu** `dev.donut.client.gui.OneMenuScreen`: it extends the old
    `ClickGuiScreen`, and `Modules.add` swaps it into `KryptonClient.clickGui`. It has a sidebar
    with categories, Active, Favorites (`config/rex-one-favorites.txt`) and search; module cards;
    and a settings panel with native controls. Click areas are rebuilt every frame, and the last
    one added wins a click. Classic mode falls back to the old panels.
  - The builder screen shares its theme (accent from Krypton+).
  - **Startup crash fixed:** the font atlas got slack for stb's overrun (`src/dev/krypton/render/FontAtlas.java`).
  - **Seven new test builds** (`test/make_builds.py`).
  - **Navigation fixes:** standing on a valid spot no longer reads as "no route", and nothing
    reachable from a pillar is left behind. There's a movement watchdog that replanning can't
    reset, failed searches are remembered until the world changes, and extra steps per tick
    are capped at 6 ms or 2,500 search cells. Pillar spots are scored by coverage and searched
    down to 16 below. Supports can sit beside a block, on anything solid. Beds are no longer
    ground. Wall-mounted blocks don't keep the block under them.
- **one.21:** lag-proof, plus a **Server** preset. Placements never click a block the server
  hasn't confirmed (a refused one made the next block land one off). Wrong blocks in spots that
  started empty are broken and re-placed (`startFix`, `startedEmpty`). No placing for one round
  trip after a setback (`correctedTick`). The server-wait windows scale with ping
  (`ackWindow`, `latencyTicks`). It backs off when blocks are refused (`backoff`, `slowDown`).
  When walled in, it breaks out (`startEscape`, `escapeStep`). Faces count as visible from any
  face midpoint (`BuilderNavigator.visibleFace`). Restock waits for container contents. There is
  a Ping tile on the screen. Tested with the new lag simulator (`test/smoke/src/rexsmoke/LagSim.java`).
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
- `-Drexsmoke.ui=true` also clicks the new menu for real (a card's switch, its star, typing a
  search) and logs the outcome; screenshots rexsmoke-menu*.png.
- `python3 test/make_builds.py DIR` writes the seven new test builds.
- Lag simulator: `-Drexsmoke.ping=200 -Drexsmoke.jitter=60` delays packets both ways (in order),
  `-Drexsmoke.rollback=0.03` has the server refuse that share of fresh blueprint blocks,
  `-Drexsmoke.rubberband=15` sets the player back a second every 15 s. Results get a `lag:` line.
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

- Never run on a real server: the lag simulator stands in for one. Some servers ban building mods
  outright; check the rules first. Use the **Server** preset there.
- A doorless room can keep the supports used inside it once the walls close: it breaks out of a
  closed room, but doesn't break back in to fetch them. Seen on the cabin with lag.
- Rare: Balanced on the compact kelp farm can stop two blocks short at a piston in a cramped water
  spot.
- Iron doors can't be opened by hand, so the builder treats them as walls.
- The starter house (3,201 blocks, many materials) builds but slowly.
- Ideas: refill water buckets in survival, build in strips to cut walking further, crop farms
  (they need a hoe step for farmland).
