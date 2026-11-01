package rex.overlord;

import dev.donut.client.autobuild.BuilderNavigator;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_2338;
import net.minecraft.class_310;
import net.minecraft.class_746;

/**
 * Getting ore out of the ground without a pathfinding mod. Anything already in a loaded chunk is
 * walked to and broken; when nothing is in sight it digs its own staircase to the layer the ore
 * lives on and strip-mines along it, rescanning as it goes. That is the whole of what the old
 * build handed to Baritone's mine process, which is why it did nothing at all when Baritone was
 * not installed.
 */
@Environment(EnvType.CLIENT)
public final class Mine {
   private static final int[][] HEADINGS = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
   private static final Set<String> HAZARDS = Set.of("lava", "water", "flowing_lava", "flowing_water");
   private static final Set<String> UNBREAKABLE = Set.of("bedrock", "barrier", "end_portal_frame", "reinforced_deepslate");
   /**
    * Things a tunnel goes round rather than through. The strip mine used to drive straight through
    * whatever was in front of it, which on one run meant the smithing table Overlord had just put
    * down: it then had to craft another, which needed iron, which started another tunnel. On a
    * server it would be somebody's chests.
    */
   private static final Set<String> KEEP = Set.of(
      "crafting_table", "furnace", "blast_furnace", "smoker", "smithing_table", "enchanting_table", "anvil", "chipped_anvil",
      "damaged_anvil", "brewing_stand", "cartography_table", "fletching_table", "grindstone", "loom", "stonecutter", "lectern",
      "beacon", "bookshelf", "chiseled_bookshelf", "chest", "trapped_chest", "ender_chest", "barrel", "hopper", "dispenser",
      "dropper", "furnace_minecart", "jukebox", "note_block", "respawn_anchor", "bell", "campfire", "shulker_box"
   );

   /** True for anything the miner must leave standing. */
   private static boolean keep(String block) {
      return KEEP.contains(block) || block.endsWith("_shulker_box") || block.endsWith("_bed") || block.endsWith("_banner");
   }
   /**
    * Cells the scanner may look at each tick, and the time it may take doing it. Twenty-four
    * thousand a tick with a registry lookup and a String for each one was most of what Overlord
    * cost the client: a couple of milliseconds every tick and spikes past forty.
    */
   private static final int BUDGET = 24000;
   private static final long NANOS = 1_500_000L;
   /** Ticks between passes when nothing has changed. */
   private static final int RESCAN_GAP = 10;

   private final Scan scan = new Scan();
   private final Dig dig = new Dig();
   /**
    * Walking to ore goes through the pathfinder, never the builder navigator. The navigator
    * searches in one unbudgeted call and asks the world about every cell as it goes, which in
    * real terrain measured seventy milliseconds inside a single tick. The pathfinder spreads the
    * same search over several ticks against a cached world, and can break through or climb on the
    * way.
    */
   private final rex.path.Traveller traveller = new rex.path.Traveller();
   // Not paused by an open screen: Overlord opens its own crafting table and furnace windows, so
   // a walk that waits for every screen to close waits for one Overlord itself put up.
   private static final rex.path.Traveller.Tuning TRAVEL = new rex.path.Traveller.Tuning(
      new rex.path.PathFinder.Options(true, false, true, 4, 1.6, 40000, 96), false, 10, 1500L, 2500, true
   );
   private Set<String> blocks = Set.of();
   private Set<net.minecraft.class_2248> wantedBlocks = Set.of();
   private long lastScan;
   private class_2338 scannedFrom = class_2338.field_10980;
   private Integer layer;
   /** Dig downward when nothing is in sight: what you do when the stuff is under the grass. */
   private boolean descend;
   private int radius = 40;
   private class_2338 current;
   private int arrivals;
   private int walkTicks;
   private final Set<class_2338> rejected = new HashSet<>();
   private int heading;
   private int headingTries;
   private int tunnelled;
   public String status = "";
   /** Where the miner's own time goes, for tracking down a stutter. */
   private final java.util.Map<String, Long> worst = new java.util.LinkedHashMap<>();

   /** Forgets the worst times, so a measurement covers one run and not every run so far. */
   public void resetCost() {
      this.worst.clear();
      this.marks = 0L;
   }

   public String cost() {
      StringBuilder out = new StringBuilder();
      this.worst.forEach((part, nanos) -> out.append(out.isEmpty() ? "" : " ").append(part).append('=').append(nanos / 1000L));
      return out.toString();
   }

   private long marks;

   private void mark(String part, long started) {
      long took = System.nanoTime() - started;
      // The first couple of seconds are the just-in-time compiler, not a stutter anyone sees.
      if (++this.marks > 120L && took > this.worst.getOrDefault(part, 0L)) {
         this.worst.put(part, took);
      }
   }
   /** Blocks broken since the last target change, for the HUD. */
   public int broken;

   /** What to look for, and the y the strip mine runs along when nothing is in sight. */
   public void want(Set<String> blocks, Integer layer) {
      this.want(blocks, layer, false);
   }

   /** As above, but digging down rather than along while nothing is in sight. */
   public void want(Set<String> blocks, Integer layer, boolean descend) {
      this.descend = descend;
      if (!blocks.equals(this.blocks)) {
         this.blocks = blocks;
         java.util.Set<net.minecraft.class_2248> resolved = new HashSet<>();
         for (String id : blocks) {
            net.minecraft.class_2248 block = Ids.block(id);
            if (block != null) {
               resolved.add(block);
            }
         }
         this.wantedBlocks = resolved;
         this.current = null;
         this.rejected.clear();
         this.broken = 0;
         this.scan.clear();
      }
      this.layer = layer;
   }

   public void reset(class_310 client) {
      this.dig.stop(client);
      this.traveller.stop(client);
      this.current = null;
      this.rejected.clear();
      this.blocks = Set.of();
   }

   public boolean idle() {
      return this.blocks.isEmpty();
   }

   /** The block it is breaking, or the one it is walking to. */
   public class_2338 focus() {
      return this.dig.breaking() != null ? this.dig.breaking() : this.current;
   }

   /**
    * Always a band around the player, never one around the target layer. Scanning only the layer
    * meant a vein two blocks from the feet went unseen while the tunnel set off for y=16.
    */
   private void rescan(class_746 player, long tick) {
      int here = player.method_24515().method_10264();
      this.lastScan = tick;
      this.scannedFrom = player.method_24515();
      this.scan.restartBlocks(this.wantedBlocks, player.method_24515(), this.radius, Math.max(-64, here - 24), Math.min(320, here + 16));
   }

   /** Another pass is only worth making when something has changed or enough time has passed. */
   private boolean worthRescanning(class_746 player, long tick) {
      return tick - this.lastScan >= RESCAN_GAP || player.method_24515().method_19455(this.scannedFrom) > 8;
   }

   private boolean wanted(class_310 client, class_2338 pos) {
      return this.wantedBlocks.contains(client.field_1687.method_8320(pos).method_26204());
   }

   public void tick(class_310 client, BuilderNavigator navigator, long tick, java.util.Map<String, Integer> stock) {
      class_746 player = client.field_1724;
      if (this.blocks.isEmpty() || player == null) {
         return;
      }
      long mark = System.nanoTime();
      this.scan.step(client, BUDGET, NANOS);
      this.mark("scan", mark);
      if (!this.scan.running() && this.worthRescanning(player, tick)) {
         this.rescan(player, tick);
      }

      if (this.current != null && !this.wanted(client, this.current)) {
         this.current = null;
      }
      if (this.current == null) {
         for (class_2338 candidate : this.scan.results()) {
            if (!this.rejected.contains(candidate) && this.wanted(client, candidate) && Dig.canBreak(stock, Ids.of(client.field_1687.method_8320(candidate)))) {
               this.current = candidate;
               this.arrivals = 0;
               this.walkTicks = 0;
               break;
            }
         }
         // Everything in sight has been given up on at some point. They are worth another look
         // before cutting a tunnel through a forest that is full of the very thing wanted.
         if (this.current == null && !this.rejected.isEmpty()) {
            this.rejected.clear();
            this.status = "Trying the awkward ones again";
            return;
         }
      }

      if (this.current != null) {
         mark = System.nanoTime();
         Dig.Result result = this.dig.tick(client, this.current, tick);
         this.mark("dig", mark);
         if (result == Dig.Result.DONE) {
            this.broken++;
            this.current = null;
            this.rejected.clear();
            this.lastScan = 0L; // the world changed where it matters: look again now
            navigator.worldChanged();
            this.status = "Broke it; looking for the next one";
            return;
         }
         if (result == Dig.Result.BREAKING) {
            this.walkTicks = 0;
            navigator.release();
            this.traveller.release();
            this.status = this.dig.status;
            return;
         }
         this.dig.stop(client);
         // Close by, the builder navigator is both cheaper and better aimed: its goal is "can I
         // click that block from here", which is exactly the question. It is only over distance
         // that its one unbudgeted search turns into a freeze, and that is where the pathfinder
         // takes over.
         navigator.release();
         if (client.field_1755 != null) {
            Work.close(client);
            this.status = "Closing a window before walking";
            return;
         }
         // Within three blocks of the block itself, not of the cell above it: a log near the top
         // of a spruce has no standing room above it, and aiming there meant arriving nowhere.
         rex.path.Goal goal = new rex.path.Goal.Near(
            this.current.method_10263(), this.current.method_10264(), this.current.method_10260(), 3.0
         );
         if (!goal.equals(this.traveller.goal())) {
            this.traveller.go(client, goal);
         }
         rex.path.Traveller.Result walk = this.traveller.tick(client, TRAVEL, tick);
         // Twenty seconds is long enough to get to anything within the scan radius. Without this
         // a block it could walk near but never quite reach went round for ever: arrive, replan,
         // walk, arrive, and the stall watchdog counted it as no progress at all.
         if (++this.walkTicks > 400) {
            this.rejected.add(this.current);
            this.current = null;
            this.traveller.stop(client);
            this.status = "Too long getting to that one; trying another";
            return;
         }
         if (walk == rex.path.Traveller.Result.UNREACHABLE) {
            this.rejected.add(this.current);
            this.current = null;
            this.traveller.stop(client);
            this.status = "No way to that one; trying another";
            return;
         }
         if (walk == rex.path.Traveller.Result.ARRIVED) {
            // There now. The dig runs first next tick and usually just works; only when standing
            // here has not helped three times is the block given up on.
            this.traveller.stop(client);
            if (++this.arrivals > 8) {
               this.rejected.add(this.current);
               this.current = null;
               this.status = "Cannot get at that one; trying another";
            } else {
               this.status = "At the block; lining up";
            }
            return;
         }
         this.status = "Walking to " + Ids.pretty(Ids.of(client.field_1687.method_8320(this.current)))
            + " " + this.current.method_10263() + " " + this.current.method_10264() + " " + this.current.method_10260()
            + ": " + this.traveller.status;
         return;
      }

      this.traveller.release();
      mark = System.nanoTime();
      this.tunnel(client, navigator, tick, stock);
      this.mark("tunnel", mark);
   }

   /** Digs new ground: a staircase toward the mining layer, then a corridor along it. */
   private void tunnel(class_310 client, BuilderNavigator navigator, long tick, java.util.Map<String, Integer> stock) {
      class_746 player = client.field_1724;
      class_2338 feet = player.method_24515();
      // Only ever downward: a staircase up has nothing to stand on while it is being cut, and the
      // old version happily tried to climb to y=16 through open air and stood there for ever.
      int dy = this.layer != null ? (feet.method_10264() > this.layer + 1 ? -1 : 0) : this.descend ? -1 : 0;
      int[] heading = HEADINGS[this.heading];
      class_2338 ahead = feet.method_10069(heading[0], 0, heading[1]);

      // Where the feet end up, and the column that has to be clear for the body to follow.
      class_2338 destination = ahead.method_10069(0, dy, 0);
      class_2338[] column = {destination, destination.method_10069(0, 1, 0), destination.method_10069(0, 2, 0)};

      for (class_2338 cell : column) {
         if (HAZARDS.contains(Ids.of(client.field_1687.method_8320(cell)))
            || HAZARDS.contains(Ids.of(client.field_1687.method_8320(cell.method_10069(heading[0], 0, heading[1]))))) {
            this.turn("lava or water ahead");
            return;
         }
      }
      for (class_2338 cell : column) {
         String block = Ids.of(client.field_1687.method_8320(cell));
         if (block.equals("air")) {
            continue;
         }
         if (UNBREAKABLE.contains(block) || keep(block) || !Dig.canBreak(stock, block)) {
            this.turn(Ids.pretty(block) + " in the way");
            return;
         }
         Dig.Result result = this.dig.tick(client, cell, tick);
         if (result == Dig.Result.BLOCKED) {
            this.dig.stop(client);
            if (navigator.moveTo(client, cell) == BuilderNavigator.Result.NO_PATH) {
               this.turn("cannot reach the next cell");
            }
            this.status = "Working toward the tunnel face";
            return;
         }
         navigator.release();
         this.status = (this.layer == null ? "Tunnelling" : dy == 0 ? "Strip-mining at y=" + feet.method_10264() : "Digging toward y=" + this.layer)
            + ": " + this.dig.status;
         return;
      }

      this.dig.stop(client);
      // One cell forward, along a corridor this very code just cleared. A full route search for
      // that costs more than the step is worth - ninety milliseconds of it, in a forest.
      navigator.searchBudget(150, 220);
      BuilderNavigator.Result walk = navigator.standAt(client, destination);
      navigator.searchBudget(600, 1000);
      if (walk == BuilderNavigator.Result.ARRIVED) {
         this.headingTries = 0;
         // Every so often take a different line, so a strip mine does not become one endless shaft.
         if (++this.tunnelled % 64 == 0) {
            this.heading = (this.heading + 1) % HEADINGS.length;
         }
      } else if (walk == BuilderNavigator.Result.NO_PATH || walk == BuilderNavigator.Result.STUCK) {
         this.turn("cannot step forward");
      }
      this.status = (dy == 0 ? "Strip-mining" : "Digging toward y=" + this.layer) + " at "
         + feet.method_10263() + " " + feet.method_10264() + " " + feet.method_10260();
   }

   private void turn(String why) {
      this.heading = (this.heading + 1) % HEADINGS.length;
      this.status = "Turning: " + why;
      if (++this.headingTries > 8) {
         this.headingTries = 0;
         this.status = "Boxed in; moving the tunnel";
      }
   }
}
