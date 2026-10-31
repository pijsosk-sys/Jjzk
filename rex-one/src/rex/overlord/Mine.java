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
   /** Cells the scanner may look at each tick. */
   private static final int BUDGET = 24000;

   private final Scan scan = new Scan();
   private final Dig dig = new Dig();
   private Set<String> blocks = Set.of();
   private Integer layer;
   private int radius = 40;
   private class_2338 current;
   private final Set<class_2338> rejected = new HashSet<>();
   private int heading;
   private int headingTries;
   private int tunnelled;
   public String status = "";
   /** Blocks broken since the last target change, for the HUD. */
   public int broken;

   /** What to look for, and the y the strip mine runs along when nothing is in sight. */
   public void want(Set<String> blocks, Integer layer) {
      if (!blocks.equals(this.blocks)) {
         this.blocks = blocks;
         this.current = null;
         this.rejected.clear();
         this.broken = 0;
         this.scan.clear();
      }
      this.layer = layer;
   }

   public void reset(class_310 client) {
      this.dig.stop(client);
      this.current = null;
      this.rejected.clear();
      this.blocks = Set.of();
   }

   public boolean idle() {
      return this.blocks.isEmpty();
   }

   /**
    * Always a band around the player, never one around the target layer. Scanning only the layer
    * meant a vein two blocks from the feet went unseen while the tunnel set off for y=16.
    */
   private void rescan(class_746 player) {
      int here = player.method_24515().method_10264();
      this.scan.restart(player.method_24515(), this.blocks, this.radius, Math.max(-64, here - 24), Math.min(320, here + 16));
   }

   private boolean wanted(class_310 client, class_2338 pos) {
      return this.blocks.contains(Ids.of(client.field_1687.method_8320(pos)));
   }

   public void tick(class_310 client, BuilderNavigator navigator, long tick, java.util.Map<String, Integer> stock) {
      class_746 player = client.field_1724;
      if (this.blocks.isEmpty() || player == null) {
         return;
      }
      this.scan.step(client, BUDGET);
      if (!this.scan.running()) {
         this.rescan(player);
      }

      if (this.current != null && !this.wanted(client, this.current)) {
         this.current = null;
      }
      if (this.current == null) {
         for (class_2338 candidate : this.scan.results()) {
            if (!this.rejected.contains(candidate) && this.wanted(client, candidate) && Dig.canBreak(stock, Ids.of(client.field_1687.method_8320(candidate)))) {
               this.current = candidate;
               break;
            }
         }
      }

      if (this.current != null) {
         Dig.Result result = this.dig.tick(client, this.current, tick);
         if (result == Dig.Result.DONE) {
            this.broken++;
            this.current = null;
            this.rejected.clear();
            navigator.worldChanged();
            this.status = "Broke it; looking for the next one";
            return;
         }
         if (result == Dig.Result.BREAKING) {
            navigator.release();
            this.status = this.dig.status;
            return;
         }
         this.dig.stop(client);
         BuilderNavigator.Result walk = navigator.moveTo(client, this.current);
         if (walk == BuilderNavigator.Result.NO_PATH || walk == BuilderNavigator.Result.STUCK) {
            this.rejected.add(this.current);
            this.current = null;
            this.status = "No way to that one; trying another";
            return;
         }
         if (walk == BuilderNavigator.Result.ARRIVED) {
            // Standing in the spot the navigator picked, with no face of the block in sight: take
            // another spot rather than stand here breaking nothing.
            navigator.rejectCurrent(client);
         }
         this.status = "Walking to " + Ids.pretty(Ids.of(client.field_1687.method_8320(this.current)))
            + " " + this.current.method_10263() + " " + this.current.method_10264() + " " + this.current.method_10260();
         return;
      }

      this.tunnel(client, navigator, tick, stock);
   }

   /** Digs new ground: a staircase toward the mining layer, then a corridor along it. */
   private void tunnel(class_310 client, BuilderNavigator navigator, long tick, java.util.Map<String, Integer> stock) {
      class_746 player = client.field_1724;
      class_2338 feet = player.method_24515();
      // Only ever downward: a staircase up has nothing to stand on while it is being cut, and the
      // old version happily tried to climb to y=16 through open air and stood there for ever.
      int dy = this.layer != null && feet.method_10264() > this.layer + 1 ? -1 : 0;
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
         if (UNBREAKABLE.contains(block) || !Dig.canBreak(stock, block)) {
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
      BuilderNavigator.Result walk = navigator.standAt(client, destination);
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
