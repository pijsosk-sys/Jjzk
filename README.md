# Overlord Baritone

An autonomous progression process for [Baritone](https://github.com/cabaletta/baritone).
Starting from an empty inventory, it walks a fixed stage ladder — wood → stone →
iron → diamond → nether → netherite → enchanting — and drives Baritone to gather,
craft, smelt and enchant until the player is wearing a fully enchanted netherite
loadout with a netherite sword and pickaxe.

## How it works

The mod registers a single `IBaritoneProcess` (`ProgressionProcess`) with a
priority above Baritone's built-in mine process. On each tick it consults the
current `Stage` and acts:

- **Mining stages** delegate to Baritone's own `mineByName(...)` and return
  `PathingCommandType.DEFER`, letting the mine process steer movement. The stage
  advances once the inventory target is met.
- **Crafting / smelting / enchanting stages** act on whatever screen handler is
  open (crafting table, furnace, smithing table, enchanting table) through the
  client interaction manager, and advance on the resulting inventory state.

The ladder is defined in `stages/Stage.java`; each entry carries its exit
condition as prose. `firstUnmetStage()` lets the process resume sensibly after a
death or relog by jumping to the first stage whose output is not yet held.

## Requirements

- Minecraft **1.20.4**, Fabric Loader ≥ 0.15
- Fabric API
- **Baritone** installed as a Fabric mod (this project depends only on its API)

## Build

```bash
./gradlew build
```

The jar lands in `build/libs/`. Drop it in `.minecraft/mods/` alongside Fabric
API and Baritone.

## Use

```
/overlord start     # begin from the first unmet stage
/overlord status    # print the current stage and its goal
/overlord stop      # halt and release Baritone
```

## Combat, speed and camouflage

- **Fights mobs.** `combat/CombatModule` scans for hostiles (and provoked
  neutrals) inside engage range, eases the view onto the closest through the
  humanizer, and swings only when vanilla reach, rotation and the attack-cooldown
  bar all allow it. While a mob is in melee the progression process pauses pathing
  and holds ground to fight instead of walking away. Other players are never
  auto-attacked.
- **Thinks fast.** On attach the process raises Baritone's planning lookahead and
  turns on sprint, parkour and water-sprint, so pathing stays snappy — while
  break speed is held at the legit value.
- **Human movement and breaking.** `util/Humanizer` eases every combat rotation
  with per-tick jitter and a small constant tremor instead of snapping, and hands
  out jittered swing/break delays so nothing lands on a fixed cadence. Baritone's
  `smoothLook`, `randomLooking` and `antiCheatCompatibility` are enabled so mining
  and walking don't read as a straight-line machine.
- **Spectator camouflage.** `evasion/SpectatorWatch` reads the tab-list game mode
  every tick, so a spectator is spotted even while invisible. When one is present
  the bot drops its obvious tells: it stops blatant straight-line grinding, mostly
  idles with human head-drift while watched up close, and hardens the
  human-looking look settings — so a watching player can't easily tell it's
  Baritone. It restores normal behaviour once the spectator leaves. `/overlord
  status` shows `CAMOUFLAGE` while this is active.

## Notes and limits

- Interaction-heavy stages (placing/opening a table, furnace, portal, enchanting
  table) assume reasonably flat ground under the player: the process aims straight
  down before issuing the use. Rough terrain may need a manual nudge.
- Recipe ids and screen-handler slot indices target Yarn 1.20.4. Porting to
  another version means re-checking `CraftingHelper`, `SmeltingHelper` and
  `EnchantHelper` against that version's mappings.
- Nether-portal lighting and bed/TNT ancient-debris mining are scaffolded as
  stages but depend on Baritone's build/selection features for the heavy lifting;
  tune block amounts in `ProgressionProcess` for your world's ore density.

## Layout

```
src/main/java/com/roothack/overlord/
  OverlordMod.java              entry point, Baritone attach + command reg
  stages/Stage.java             the progression ladder
  process/ProgressionProcess.java   the tick-driven state machine
  command/OverlordCommand.java  /overlord start|stop|status
  util/
    InventoryHelper.java        inventory counting
    CraftingHelper.java         recipe-book crafting
    SmeltingHelper.java         furnace loading/collection
    EnchantHelper.java          enchanting-table operation
    BlockPlacer.java            hotbar select + place/open utility blocks
```
