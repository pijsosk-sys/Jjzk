# REX One: faster Auto Build and a new builder screen

Sources and tooling for REX One `1.0.0-one.14` (Minecraft 1.21.11, Fabric). The build takes the
released `1.0.0-one.13` jar and replaces four classes:

| Class | What changed |
| --- | --- |
| `dev.donut.client.autobuild.AutoBuilderController` | pipelined placement, settling set, real-tick timing, live stats |
| `dev.donut.client.gui.AutoBuilderScreen` | redesigned builder screen |
| `rex.BuildTempo` | Safe / Balanced / Fast / Custom presets, new pacing rows |
| `rex.BuildPatience` | auto-resume also covers lost confirmations |

The controller and screen shipped only as bytecode, so they were decompiled first. The first
commit on this branch holds that decompiled baseline, so `git diff` shows exactly what changed.

## Why Auto Build was slow

1. **It re-picked the block it had just placed.** A placed block stays out of "correct" until the
   four-tick stability check passes. Until then it stayed in the work set, and because it is the
   closest block to the player, target selection kept choosing it and doing nothing. Every
   block cost several dead ticks. Placed blocks now wait in a separate *settling* set that is
   re-checked each tick, so selection moves straight on.
2. **Trust Prediction never worked.** Turning it on made `finishPending` pause the build with
   "No server confirmation". It now finishes on the client's prediction, keeps the position
   unconfirmed until the server answers, and re-queues the block if the server sends back
   something else. *Max In Flight* caps how many unanswered placements may be outstanding.
3. **Delays were counted in steps, not ticks.** With *Steps Per Tick* above one, cooldowns ran
   out within a single tick, and walking stalls were counted several times per tick, so
   "Cannot reach" fired about three times too early. Every delay is now in real client ticks.
4. **A fixed two-tick pause after every block.** This is now *Place Delay*: 0 in Balanced/Fast,
   2 in Safe.
5. **It walked back and forth.** Target choice looked only at the first few hundred entries of
   the work list, in list order, not by distance. It now weighs thousands of candidates. When the
   nearest block can't be placed from where the player stands, it takes a reachable block of the
   same material instead of walking.
6. **It waited for the player to stop drifting.** After every step, placement waited until the
   aim matched a fixed point exactly. It now also uses the rotation the server already has, when
   a ray along it clicks a face that places exactly the wanted block.
7. **Found in game: an endless walk/jump loop.** Mid-jump onto a block, the target came into
   reach, the builder held still to place, the player dropped back out of reach, and it repeated
   forever. It no longer arms a placement in mid-air.

## Measured in game

The bundled 15,460-block kelp farm, built in a real Minecraft 1.21.11 singleplayer world. Every
block was checked on the server afterwards.

| Jar / preset | Blocks placed in 2 min | Rate | Wrong blocks |
| --- | --- | --- | --- |
| original one.13 jar | 183 | 1.5 /s | 0 |
| one.14 Balanced | 614 | 5.1 /s | 0 |
| one.14 Fast | 846 | 7.0 /s | 0 |

A long run of the same farm on Fast placed 7,524 blocks (49%) in 22 minutes, all exact,
before stalling on a 17-step walk to the upper levels (`screenshots/kelp-behemoth-22min*.png`).

Each pair of runs shared a 4-core machine with software rendering, so these figures are for
comparing runs with each other; real hardware will be faster. What's left is mostly walking
between spots (about 40%).

Not solved here: the walking pathfinder can't climb tall narrow builds (the compact kelp tower)
or get out of a closed box, in either jar.

## Why it still won't mess up the build

These hold in every preset:

- A placement is only sent once the server already has the rotation it was aimed with.
- A block only counts once the server's own block update agrees and it has stayed put for the
  stability window. A server refusal puts it straight back in the queue, and three refusals at
  one spot pause the build with the permission message.
- Anything unanswered for the *Ack Window* pauses the build, as before. Auto Resume retries.
- The final pass re-checks the whole structure before reporting Complete.
- *Place Interval* gives a hard blocks-per-second cap for servers that limit placement rate.

## Presets

| Preset | Waits for server | In flight | Best case |
| --- | --- | --- | --- |
| Safe | every block | 1 | ~5 blocks/s |
| Balanced (default) | no, 1 tick after sending | 2 | ~10 blocks/s |
| Fast | no, same tick | 4 | ~20 blocks/s (one per tick) |
| Custom | the rows in Build Tempo | | |

You can pick a preset from the Speed buttons on the builder screen, or with the *Preset* row of
the Build Tempo module (Misc). Turning the Build Tempo module off restores the original cadence.

## New builder screen

Three columns:

- **Library:** search box, file-type tags, Open Folder and Refresh.
- **Preview and Placement:** 3D preview, X/Y/Z fields (Up/Down arrows nudge, Shift nudges by
  ten), At player, Rotate, Mirror, Show in world, ghost opacity.
- **Build:** percentage and progress bar, live rate, time left, placements in flight, blocks
  settling, supports and deferred blocks, the speed presets with the rate each allows,
  Start/Pause/Resume, Materials, Cancel, the builder's current reason (click it for the full
  text), and the creative-mode switch.

## Also fixed: the big kelp farm wouldn't load

`assets/rex/schematics/kelp-blue-behemoth.litematic` was saved by a newer Minecraft (data
version 4790), so 1.21.11 (4671) refused it: "Schematic was saved by a newer Minecraft version".
Every block state in it exists in 1.21.11, so the build stamps it 4671. It loads and builds in
game.

## Also fixed: launch crash

Eleven Krypton/REX mixins (`HeightContextMixin`, `CameraMixin`, `MouseMixin`,
`WorldRendererMixin`, `HeldItemRendererMixin`, `TextVisitFactoryMixin`, `BoatMoveMixin`) stored
the single `@At` of a `@Redirect` / `@ModifyArg` / `@ModifyArgs` / `@ModifyVariable` as an
array. MixinExtras 0.5.0, which ships with Fabric Loader 0.18.4, casts that value straight to an
annotation, and the game crashes while loading the target class: `ClassCastException:
ArrayList cannot be cast to AnnotationNode` from `HeightContext`. The released one.13 jar crashes
this way on launch. `tools/FixMixinAt.java` rewrites those values during the build.

## Building

```sh
./build.sh path/to/REX-One-1.21.11.jar      # writes out/REX-One-1.21.11.jar
```

You need JDK 21, python3 and curl. The first run calls `tools/setup.sh`, which downloads the
Minecraft 1.21.11 client, remaps it to Fabric intermediary with tiny-remapper, and fetches its
libraries into `.cache/`. Then the build:

1. compiles `src/` against the base jar plus the remapped client,
2. swaps those classes (and their nested classes) into a copy of the jar, bumps the version in
   `fabric.mod.json`, and refreshes the bundled README and sources,
3. fixes the mixin `@At` arrays,
4. runs `tools/LinkCheck.java`, which fails the build if any class in the jar calls a method or
   field on a replaced class that no longer exists.

## In-game test

The full guide, with commands, is in [HOW-I-RAN-MINECRAFT.md](HOW-I-RAN-MINECRAFT.md).

`test/` holds a real in-game check, run against a real Minecraft client:

1. Download the official 1.21.11 client and its libraries from Mojang, Fabric Loader 0.18.4 and
   Fabric API from Fabric's maven, and the game assets minus sounds.
2. Launch it headless: `KnotClient` (Fabric's launcher) under Xvfb, a virtual screen, with Mesa
   software OpenGL, in offline mode, with `--quickPlaySingleplayer` into a flat world.
3. `smoke/` is a small extra mod that takes over once the world loads. It runs `/gamemode`,
   `/give` and `/fill` on the integrated server, loads the schematic into the real builder,
   picks a preset, presses Start, and waits until it completes or stalls. It then compares every
   block on the server with the blueprint, logs where time went by builder state, takes
   screenshots, and quits.

Details: `make_house.py` writes a 260-block house: planks, logs,
stone bricks, glass, a door, stairs in four facings, top and bottom slabs, and a lantern.
`smoke/` is a small Fabric mod that joins a flat singleplayer world. It builds the house under
each preset and with Build Tempo off, compares every block on the server with the blueprint,
and writes `rexsmoke-results.txt` plus screenshots.
