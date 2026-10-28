# Handoff: start a new chat from here

Everything is on branch `claude/yo-b2792p` of `pijsosk-sys/Jjzk`, in `rex-one/`. To pick up in a
new chat, tell it: *"Read rex-one/HANDOFF.md, rex-one/README.md and
rex-one/HOW-I-RAN-MINECRAFT.md, then continue."*

## What exists now (version 1.0.0-one.16)

**Jar:** `rex-one/out/REX-One-1.21.11.jar`. Drop it into `mods/` in place of the old jar; it
needs Fabric Loader 0.18.4+, Fabric API and Baritone, as before.

**Auto Build:**
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
- `-Drexsmoke.ui=true` screenshots the module menu, the builder screen and the Custom editor, then quits.
- `-Drexsmoke.maxSeconds=N` and `-Drexsmoke.stallSeconds=N` set the time limits.
- `GAME_DIR=...` runs two games side by side.

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

- A player boxed inside a closed room with a planned roof can't pillar out (the test house).
- The big kelp farm: the last full run reached about half before the test cut it off on a long
  walk. The run started with every fix was still going when this was written.
- Not tested on a real server or against an anticheat; start with **Balanced** there.
- Ideas not done yet: remove temporary pillars at the end, refill water buckets in survival,
  build in strips to cut walking further.
