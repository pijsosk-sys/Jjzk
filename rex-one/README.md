# REX One: faster Auto Build, climbing, water, a new builder screen and a shader

Sources and tooling for REX One `1.0.0-one.20` (Minecraft 1.21.11, Fabric). The build takes the
released `1.0.0-one.13` jar, replaces or adds these classes, and adds the shader files:

| Class | What changed |
| --- | --- |
| `dev.donut.client.autobuild.AutoBuilderController` | pipelined placement, settling set, real-tick timing, pillaring, water and kelp, sign editor, live stats |
| `dev.donut.client.autobuild.BuilderNavigator`, `BuilderPathfinder` | walks closer so it stops less |
| `dev.donut.client.gui.AutoBuilderScreen` | redesigned builder screen |
| `dev.donut.client.render.SchematicGhosts` | ghost layer view |
| `rex.BuildTempo` | presets, pacing, pillaring, restock and safety settings |
| `rex.BuildPatience` | auto-resume also covers lost confirmations and players walking off |
| `rex.Huds`, `rex.Modules` | panels stay off the builder screen; registers the new modules near the top of Render |
| `rex.BuildRestock` (new) | shulker box restock |
| `rex.BuildMemory` (new) | remembers the loaded build per server |
| `rex.SchematicGhost` (new module) | ghost on/off (bindable) and layer view |
| `rex.TuffShader` (new module) | the Tuff Shader |

Classes that shipped only as bytecode were decompiled first, and each was committed unchanged
before being edited, so `git log -p` shows exactly what changed.

## New in one.20: Start always starts, three new farms, farms and houses finish, more natural

**Start did nothing on some schematics.** In survival, the first block it had no material for
paused the whole build at once ("Missing X"), so Start looked dead. Now every block needing a
missing item is set aside, the rest is built, and the set-aside blocks are placed by themselves
as soon as the item turns up in the inventory (checked every second, and a build paused only for
materials resumes). When nothing else is left it pauses with the full list ("Missing materials:
Spruce Planks x144, Cobblestone x27"). If you carry *none* of the build's materials, the screen
says so instead of starting. Tested by pressing the screen's real Start button on every
library file: all 10 start in creative; in survival the ones you carry blocks for start and the
others show that message.

**The sweet berry farm stopped part way.** Farm schematics save plants fully grown (berry bushes
at age 3); a placed bush is age 0 and the builder treated it as wrong forever. Blocks that differ
only in growth (`age`, `stage`, `moisture`) now count as built, in the builder, the HUD and the
materials list. Stair `shape` too: the game picks it from the neighbouring stairs.

**Cactus and nether wart could not be placed at all.** The reach test aimed at a block's outline
but traced the sight line against its collision box. Cactus and soul sand have a collision top
below their outline top, so the line never hit and every spot looked out of reach ("No walking
route"). Sight lines now use the outline, as the real crosshair does. Temporary blocks are no
longer put beside a cactus (it would break), soul sand, slabs and stairs count as ground to stand
on, and its feet are placed on the right cell when standing on them.

**Houses with a door.** A closed door counted as open space, so the player hopped against it
forever (the test house stopped at 116/260, on one.19 too). Now the builder opens wooden doors
and gates on its way through like a player, never leaves a temporary block in a doorway (that
had shut it inside), and shuts the doors again once the build is done. Supports under slabs,
stairs, fences, panes and walls are removed (they never needed them), and a hanging lantern no
longer keeps the block under it.

**New farms** (in the library, made by `test/make_farms.py`):

| Farm | Blocks | Fast (creative) |
| --- | --- | --- |
| `sweet-berry-farm` | 135 | complete, 21.8 s |
| `cactus-farm` | 129 | complete, 27.1 s |
| `nether-wart-farm` | 90 | complete, 14.5 s |

**Looks more like a player.**
- Big turns ease out toward the end (Safe eases the most), and the turn speed wanders a little
  from tick to tick, as a hand does.
- Among the aim points that work, it picks one at random within a few degrees of the smallest
  turn, so it doesn't click the same point of every block.
- While walking, the view drifts a few degrees and looks at the ground ahead, not along a fixed
  line.

**Tested on the final jar (creative):** test house 260/260 on Fast and Safe with every support
removed; Cozy Oak Cabin 284/284; compact kelp farm 85/85 on Fast, Balanced and Safe; tower
224/224; lava and water pools 90/90; the three farms above. One known rare miss: about one run in
twelve, Balanced on the compact kelp farm stopped two blocks short (a piston in a cramped water
spot). The same thing happens on one.19.

## New in one.19: no stutter when it places, a correct materials list, a bit faster

**The stutter.** The builder now times its own ticks (`tickReport()`, logged by the test driver).
On the Cozy Oak Cabin on Fast:

| | one.18 | one.19 |
| --- | --- | --- |
| average builder tick | 3.8 ms | 0.8 ms |
| worst builder tick | 316 ms | 70-100 ms |
| ticks longer than a frame (16 ms) | 67 | about 20 |
| time to finish | 136 s | 94 s |

(This test machine renders on the CPU and runs two games at once; a normal PC is several times
faster per tick.)

- 95% of the builder's time went into the pathfinder asking "does the player fit here" through
  the world's general collision query (which also walks entities and the world border), for the
  same cells again and again. Answers are now kept for the length of a search, and the test is a
  direct look at the two blocks' collision boxes.
- Choosing a pillar spot ran sight lines and the full placement planner for up to ~3,000
  spot-and-height pairs in one tick. They are now sorted by their cheap score and tested in that
  order, stopping at the first that works, and only spots the player can walk to (one flood from
  the player) are considered, so it never starts a doomed walk to an unreachable spot.
- Placements pick, among every face that works, the one needing the smallest turn.
- The pillar-spot test runs at most ten milliseconds a tick and carries on next tick; walking to a
  spot gives up after eight seconds (a route with a step the player cannot make looped forever);
  and pillar or stepping blocks no longer count as progress, which had reset the retry limits so
  a block it could not place from a pillar was climbed for again and again.

**Faster.** Fast turns up to 120° a tick (was 80°): fewer ticks spent aligning, 9.6 blocks/s on
the big kelp farm against 8.8-9.0. The camera still glides between ticks.

**Materials list (HUD and Materials screen).** Flowing water and lava in a schematic were counted
as buckets still needed (the "and 37 more short" on the big farm); they form on their own and now
need nothing. Any water source satisfies a water cell, any lava source a lava cell, kelp counts at
any age, as the builder already judged. The HUD shows "nothing left to place" as soon as a build
completes instead of a scan up to four seconds stale, and in creative nothing is shown as short.

## New in one.18: no more lag on big farms, lava, a better screen

**Lag.** Profiled with Java Flight Recorder on the bundled 15,460-block kelp farm, building on Fast:

| | one.17 | one.18 |
| --- | --- | --- |
| ghost renderer, share of each frame | 29% | 8% |
| world drawing, share of each frame | 33% | 18% |
| frames per second (this test machine renders on the CPU) | 13 | 23 |
| build speed | 9.1 blocks/s | 9.2 blocks/s |

- The schematic ghost worked out every ghost box (a world lookup, shape and colour each) on every
  frame and drew all six faces and twelve edges of up to 8,192 boxes. The boxes are now worked
  out a few times a second, faces shared by two ghost cubes are left out (a solid wall of ghosts
  only draws its surface), only ghosts within 18 blocks get edges, and ghosts reach 48 blocks
  out, 4,096 at most.
- The builder screen's 3D preview projected and depth-sorted every face of up to 6,000 blocks on
  every frame. The sorted faces are now kept until the view changes, hidden faces are left out,
  and big schematics are sampled to 4,000 blocks.
- Target choice reads each block's material, fluid and facing from a table built once per
  schematic instead of working them out for thousands of candidates per choice.

**Lava.** Lava sources are poured with lava buckets, like water. Lava goes in last of all, once
every solid block of the build is in, since poured early it flows into cells meant for blocks
and sets wood, wool and leaves alight. Flowing lava forms on its own and is never placed. A
two-basin test (9 lava and 9 water sources) completes on Fast and Safe, all exact.

**Screen.**
- *Options* row on the Placement card: Smooth aim, Sprint, Clean up (Remove Supports) and Pillar,
  one click each. Pointing at one explains it.
- A layer strip down the side of the 3D preview: one bar per layer, filled as it is built, green
  when done, the layer being built marked. Pointing at it names the layer and its count.
- The builder's own screen no longer pauses the build, so the live figures can be watched.

**Camera.** Walking, the view rests on the ground a couple of steps ahead instead of a fixed
angle, so it tips down for a drop and up for a climb, as a player's does.

## New in one.17: finishes real builds, cleans up after itself, looks human

Tested in the real game on the bundled Cozy Oak Cabin (284 blocks, a gable roof with eaves that
overhang the walls) and the bundled starter house (3,201 blocks).

- **Cozy Oak Cabin on Fast: complete, 284/284 exact, every temporary support removed** (95-130 s
  across runs, including while being screen-recorded). one.16 stalled at 140/284 on Fast and on
  Safe, walking back and forth under the roof.
- **Compact kelp farm: complete, 85/85, 29 s.**
- **Remove Supports** (on): once everything else is built and verified, every pillar and support
  block it placed is broken, top down, so only the schematic is left. Standing on a pillar it digs
  straight down; it walks over to supports out of reach; it picks the quickest hotbar tool (any
  non-weapon in creative). A support that a torch, lantern, sign or other non-full blueprint block
  might hang from is left, and the completion message says why.
- **Pillars go where they help.** It used to pillar wherever it stood, often in the middle of a
  room, out of reach of the block it was for, and then it was stuck on top. Now it picks the
  nearest open column beside the target whose top can actually place it, walks there, then climbs.
  Stuck on one of its own pillars, it digs down a block at a time.
- **It can come back down.** The pathfinder only stepped one block up or down, so once on a roof
  or a pillar it could never reach the ground again. It now drops off edges: up to three blocks
  always (no damage), up to twelve in creative, and up to six in survival only while health is
  well above the low-health pause. Longer drops cost more in route choice, so it prefers stairs.
- **It only walks to spots it can place from.** "In reach" used to mean "close to the block". Now
  a spot counts only if a face the block can be placed against is in reach and actually in sight,
  sampled at five points the way a click and the server's reach check need, and the real
  placement planner agrees it would come out in the right state from there (the player is moved
  to the spot for the check and straight back, within the tick). A piston, stair or upright log
  now gets walked to a spot where it faces the right way, instead of arrive / fail / walk on. If
  no spot passes the planner, any spot with a visible face is used, as before.
- **Water waits for its walls.** Water and kelp go in only once every solid block at or below
  their height is built (skipped ones included), so nothing floods the cells meant for pistons
  and glass.
- **No more hopping in place.** A player blocked mid-route (a block placed into its path, or a
  fall off a narrow landing) jumped forever, since height changes counted as progress. Progress
  now means getting closer to the next step; a route cell that filled up or went out of jumping
  reach is replanned; and it does not sprint into a drop.
- **Pillars start centred.** Standing off-centre, the player overlapped the next column and hit
  its head on it every jump. It now sneaks to the middle of the block it stands on first, and
  gives a pillar up after eight seconds without gaining height.
- **Blocks that need a neighbour first** (upside-down stairs, open trapdoors hanging off a block
  that is not built yet) are put aside after three failed tries and retried when something next
  to them is placed. Every skipped block used to be re-queued after every placement, sending the
  builder back to the same unplaceable blocks after each block.
- **Builds what is in reach first.** When the nearest block needs a walk, any other block it can
  place from where it stands goes first, in any material already in the hotbar.
- **Sprint** (on) on longer walks.
- **Old schematics load.** Pre-1.17 `cauldron[level=N]` becomes a water cauldron, renamed blocks
  (`chain`, `grass`, `grass_path`) map to today's names, and a property a block no longer has is
  ignored instead of refusing the whole file. The bundled starter house did not load before.
- **Fixes its own mistakes.** If a block it is building comes out as the right block in the wrong
  state (a piston facing the wrong way), it breaks it and places it again, twice per spot at most.
  Only the blueprint's own block type is ever broken.
- **Picky blocks first.** Blocks that face a way (pistons, observers, logs, stairs) can be placed
  from far fewer spots, so within the layers being built they go first, while those spots are
  still open.
- An unreachable stepping block no longer pauses the build; that plan is dropped and the block it
  was for finds another way.

### Looks like a player now (Smooth Aim, on)

The builder used to snap the view to each new angle at the start of the client tick, before the
player ticked, so the game had no previous angle to blend from and the camera jumped twenty times
a second. Now every view change goes through `BuilderLook`: a turn is capped at *Turn Speed*
degrees per tick (Safe 30, Balanced 50, Fast 80; small corrections between neighbouring blocks
still land in one tick), and at the end of the tick the previous-tick rotation is put back, so the
frames in between glide from the old angle to the new one the way mouse look does. The server
still gets the new angle in the same tick, so it costs nothing. Walking, it looks along the way
and turns to the block for the last couple of steps; movement is steered relative to the view, so
the path is followed exactly while the camera is still turning. Pillaring tilts down smoothly
instead of snapping to straight down.

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

Still hard: a player boxed inside a closed room with a planned roof over it can't pillar out
(the test house). A standing sign that needs both height and one particular side is skipped
after about 15 seconds and retried later.

## New: finishing big builds

- **Pillar Up.** When the next block is above the player and walking can't get there, it looks
  straight down, jumps and places a pillar block under itself (dirt, cobblestone, cobbled
  deepslate, netherrack or stone), only in open air the blueprint doesn't use. A 14-high tower
  with no stairs now finishes on every preset; before, it stopped at 32 of 224 blocks.
- **Water and kelp.** Water sources and bubble-column cells are filled with a water bucket. The
  bucket is aimed so the first solid face along the look puts the water in the cell. Kelp cells
  get their water first, then the kelp. Flowing water isn't a target, since it comes from the
  sources. The compact kelp farm now completes 85/85.
- **What counts as built.** Kelp at any age; redstone dust, fences, panes and walls whatever
  sides they connect to; run-time states like `power`, `lit` and `powered` are ignored. These
  are things the world sets by itself and the builder could never place exactly.
- **Signs.** Placing a sign opens its editor, which used to pause the build; the builder now
  presses Done for the signs it placed.
- **Blocks that face the player** (signs, banners, skulls, stairs): when the usual aim points
  give the wrong rotation, it tries a 5×5 grid on every face and takes the one that gives the
  right block.
- **Never builds above a gap.** A shortcut that placed a nearby block instead of an awkward one
  could pick a higher layer. The walls rose under the player and stranded the rest of the lower
  layer, so now the substitute is never higher than the block it replaces.
- **Walks closer** (Fast 3, Balanced 3.25, Safe 4.25 blocks; *Walk Closer* in Custom), so more of
  the next blocks are in reach when it stops. It falls back to the full reach where nothing closer
  works.

## New: settings in Build Tempo

| Row | Default | What it does |
| --- | --- | --- |
| Pillar Up | on | the climbing above |
| Shulker Restock | **off** | out of a block, and a carried shulker box has it: place the box beside you, shift-click out what the build still needs, break it, pick it up, carry on |
| Pause On Low Health / Health / Hunger | on, 8, 6 | pause so you can heal or eat |
| Pause Near Players / Player Radius | off, 24 | pause while someone is near; Auto Resume carries on when they leave |
| Walk Closer | 3.25 (Custom) | see above |

The loaded schematic and its position are also saved per server (`config/rex-one-build.json`) and
loaded back when you join, ready to Start. The builder's scan then finds what's already built.

## New: look and screen

- **Tuff Graphics** (Render module, bindable): one full-screen pass
  through Minecraft's own post-effect pipeline that sharpens, adds vibrance, a soft contrast curve,
  warm/cool toning and a vignette. Styles are Soft, Tuff and Vivid. It needs no Iris or OptiFine,
  costs about what the creeper spectator effect does, and doesn't touch the HUD.
  See `screenshots/shader-*.png`. Since one.16 it is a plain module only: second row of the
  Render panel (it used to sit below the bottom of that panel), and no longer on the builder screen.
- **Schematic Ghost** (Render module, bindable): shows or hides the ghost. *Layers* is All /
  Working layer / Up to working / One layer.
- **Builder screen:** HUD panels and pop-ups no longer draw over it, plus a Ghost toggle, Layers,
  and *Skipped*, which lists every skipped block and why when clicked.
  Materials has **Copy list**, which copies what's still missing to the clipboard.

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
| Custom | your own numbers (Custom speed editor) | | |

You can pick a preset from the Speed buttons on the builder screen, or with the *Preset* row of
the Build Tempo module (Misc). Turning the Build Tempo module off restores the original cadence.

**Custom speed editor (one.16).** Clicking *Custom* on the builder screen opens an editor with
every pacing number Custom uses: a bar to click or drag and -/+ buttons per row, Trust Prediction
as a switch, *Start from* Safe / Balanced / Fast to copy a preset's numbers in, *Reset to
defaults*, and the switches that apply on every preset (Pillar Up, Shulker Restock, the two
safety pauses, Auto Resume). Pointing at a row explains it; changes apply at once, even mid-build.
While Custom is selected, *Edit custom speed...* under the Speed buttons reopens it.
See `screenshots/custom-speed.png`.

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
