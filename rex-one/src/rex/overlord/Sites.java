package rex.overlord;

import dev.donut.client.autobuild.BuilderNavigator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_1703;
import net.minecraft.class_2338;
import net.minecraft.class_310;
import net.minecraft.class_3965;
import net.minecraft.class_746;

/**
 * Places and uses the things Overlord needs standing in the world: the crafting table, the
 * furnace, the smithing table, a nether portal and the enchanting table with its ring of
 * bookshelves. Each call does one step and says whether the step it was asked for is finished.
 */
@Environment(EnvType.CLIENT)
public final class Sites {
   private final Map<String, class_2338> stations = new HashMap<>();
   private final Map<String, class_2338> portals = new HashMap<>();
   private final Set<class_2338> visitedChests = new HashSet<>();
   private final Scan portalScan = new Scan();
   private final Scan chestScan = new Scan();
   private class_2338 frame;
   private String frameDimension = "";
   private class_2338 enchantSite;
   private class_2338 chest;
   private int chestTicks;
   public String status = "";

   public static String dimension(class_310 client) {
      return client.field_1687.method_27983().method_29177().toString();
   }

   public static boolean inNether(class_310 client) {
      return dimension(client).endsWith("the_nether");
   }

   public void worldChanged() {
      this.stations.clear();
      this.frame = null;
      this.frameDimension = "";
      this.enchantSite = null;
      this.chest = null;
   }

   public static String block(class_310 client, class_2338 pos) {
      return Ids.of(client.field_1687.method_8320(pos));
   }

   public static boolean air(class_310 client, class_2338 pos) {
      return client.field_1687.method_8320(pos).method_45474();
   }

   /**
    * Right-clicks the block at target, or places the held item into it. Walks closer when no face
    * is reachable. True only on the tick the click actually went out.
    */
   public boolean use(class_310 client, BuilderNavigator navigator, class_2338 target, boolean placing, String item, String what) {
      class_746 player = client.field_1724;
      class_2338 feet = player.method_24515();
      // A block cannot go where the player is standing. The server simply refuses it, and the ring
      // of bookshelves stopped on whichever one the walk had parked on.
      if (placing && (target.equals(feet) || target.equals(feet.method_10084()))) {
         navigator.moveTo(client, target);
         navigator.rejectCurrent(client);
         this.status = what + ": standing where it goes, stepping aside";
         return false;
      }
      class_3965 hit = Reach.hitFor(client, target, placing);
      if (hit == null) {
         BuilderNavigator.Result walk = navigator.moveTo(client, target);
         if (walk == BuilderNavigator.Result.ARRIVED) {
            // The navigator is happy where it stands but no face of the block can be seen from
            // here. Give this spot up, or the two of them argue for ever.
            navigator.rejectCurrent(client);
            this.status = what + ": moving for a clear line to " + coords(target);
         } else {
            this.status = what + (walk == BuilderNavigator.Result.NO_PATH ? ": no way to " : ": walking to ") + coords(target);
         }
         return false;
      }
      navigator.release();
      if (item != null && !Stock.select(client, item)) {
         this.status = what + ": taking out " + Ids.pretty(item);
         return false;
      }
      if (!Reach.aimAt(client, hit)) {
         this.status = what + ": lining up on " + coords(target);
         return false;
      }
      boolean done = Reach.use(client, hit);
      this.status = what + (done ? ": done" : ": the server refused that click");
      return done;
   }

   public static String coords(class_2338 pos) {
      return pos.method_10263() + " " + pos.method_10264() + " " + pos.method_10260();
   }

   /** A remembered station of this kind still stands nearby. */
   public boolean available(class_310 client, String id) {
      class_2338 pos = this.stations.get(dimension(client) + id);
      return pos != null && block(client, pos).equals(id);
   }

   /**
    * Makes sure the window of this station is open: finds one nearby, places one if there is none,
    * then clicks it. True once the right screen handler is in front of the player.
    */
   public boolean open(class_310 client, BuilderNavigator navigator, String id, Class<? extends class_1703> handler) {
      class_746 player = client.field_1724;
      if (handler.isInstance(Work.handler(player))) {
         return true;
      }
      if (client.field_1755 != null) {
         Work.close(client);
         this.status = "Closing the wrong window";
         return false;
      }
      String key = dimension(client) + id;
      class_2338 known = this.stations.get(key);
      if (known != null && !block(client, known).equals(id)) {
         this.stations.remove(key);
         known = null;
      }
      if (known == null) {
         known = this.nearby(client, id, 5);
         if (known != null) {
            this.stations.put(key, known);
         }
      }
      if (known != null) {
         this.use(client, navigator, known, false, null, "Opening the " + Ids.pretty(id));
         return false;
      }
      class_2338 spot = this.freeSpot(client);
      if (spot == null) {
         this.status = "No clear ground to put a " + Ids.pretty(id) + " down";
         return false;
      }
      this.use(client, navigator, spot, true, id, "Putting a " + Ids.pretty(id) + " down");
      if (block(client, spot).equals(id)) {
         this.stations.put(key, spot);
      }
      return false;
   }

   private class_2338 nearby(class_310 client, String id, int range) {
      class_2338 feet = client.field_1724.method_24515();
      class_2338 best = null;
      double bestDistance = Double.MAX_VALUE;
      for (int dy = -3; dy <= 3; dy++) {
         for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
               class_2338 at = feet.method_10069(dx, dy, dz);
               if (block(client, at).equals(id)) {
                  double distance = feet.method_10262(at);
                  if (distance < bestDistance) {
                     bestDistance = distance;
                     best = at;
                  }
               }
            }
         }
      }
      return best;
   }

   /** An empty cell beside the player with solid ground under it and headroom over it. */
   private class_2338 freeSpot(class_310 client) {
      class_2338 feet = client.field_1724.method_24515();
      for (int[] offset : new int[][]{{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {2, 2}, {-2, -2}, {1, 2}, {-1, -2}, {2, 1}, {-2, -1}}) {
         for (int dy : new int[]{0, -1, 1}) {
            class_2338 at = feet.method_10069(offset[0], dy, offset[1]);
            if (air(client, at) && air(client, at.method_10084()) && !air(client, at.method_10074())) {
               return at;
            }
         }
      }
      return null;
   }

   /** Remembers a portal standing where the player came out, so the way back is known. */
   public void rememberPortal(class_310 client) {
      class_2338 found = this.nearby(client, "nether_portal", 4);
      if (found != null) {
         this.portals.put(dimension(client), found);
      }
   }

   /**
    * Gets the player into the nether (nether true) or back out. Reuses any portal in sight,
    * otherwise builds the ordinary ten-obsidian frame and lights it.
    */
   public boolean portal(class_310 client, BuilderNavigator navigator, Overlord brain, boolean nether) {
      if (inNether(client) == nether) {
         return true;
      }
      if (!this.frameDimension.equals(dimension(client))) {
         this.frame = null;
         this.frameDimension = dimension(client);
      }
      class_2338 known = this.portals.get(dimension(client));
      if (known != null && block(client, known).equals("nether_portal")) {
         navigator.moveTo(client, known);
         this.status = "Walking into the portal at " + coords(known);
         return false;
      }
      this.portalScan.step(client, 16000);
      if (!this.portalScan.running()) {
         if (!this.portalScan.results().isEmpty()) {
            this.portals.put(dimension(client), this.portalScan.results().get(0));
         }
         this.portalScan.restart(client.field_1724.method_24515(), Set.of("nether_portal"), 24, -64, 320);
      }

      if (this.frame == null) {
         if (!brain.ensure("obsidian", 10, 0) || !brain.ensure("flint_and_steel", 1, 0)) {
            return false;
         }
         class_2338 candidate = client.field_1724.method_24515().method_10069(3, 0, 0);
         if (!this.frameFits(client, candidate)) {
            brain.wander();
            this.status = "Looking for space to build a portal";
            return false;
         }
         this.frame = candidate;
      }
      for (class_2338 pos : framePositions(this.frame)) {
         if (!block(client, pos).equals("obsidian")) {
            if (!brain.ensure("obsidian", 1, 0)) {
               return false;
            }
            this.use(client, navigator, pos, true, "obsidian", "Portal frame");
            return false;
         }
      }
      class_2338 inside = this.frame.method_10069(1, 1, 0);
      if (!block(client, inside).equals("nether_portal")) {
         if (!brain.ensure("flint_and_steel", 1, 0)) {
            return false;
         }
         this.use(client, navigator, inside, true, "flint_and_steel", "Lighting the portal");
         return false;
      }
      this.portals.put(dimension(client), inside);
      navigator.moveTo(client, inside);
      this.status = "Stepping into the portal";
      return false;
   }

   /** The ten obsidian blocks of a plain 4x5 frame; the two bottom corners are not needed. */
   private static List<class_2338> framePositions(class_2338 frame) {
      List<class_2338> blocks = new ArrayList<>();
      for (int x = 1; x <= 2; x++) {
         blocks.add(frame.method_10069(x, 0, 0));
         blocks.add(frame.method_10069(x, 4, 0));
      }
      for (int y = 1; y <= 3; y++) {
         blocks.add(frame.method_10069(0, y, 0));
         blocks.add(frame.method_10069(3, y, 0));
      }
      return blocks;
   }

   private boolean frameFits(class_310 client, class_2338 frame) {
      for (int x = 0; x < 4; x++) {
         if (air(client, frame.method_10069(x, -1, 0))) {
            return false; // nothing to stand the frame on
         }
         for (int y = 0; y < 5; y++) {
            if (!air(client, frame.method_10069(x, y, 0))) {
               return false;
            }
         }
      }
      return true;
   }

   /** Looks through chests in the nether for an upgrade template. */
   public boolean template(class_310 client, BuilderNavigator navigator, Work work, Overlord brain) {
      if (work.loot(client)) {
         this.status = work.status;
         return false;
      }
      if (Work.handler(client.field_1724) instanceof net.minecraft.class_1707) {
         if (this.chest != null) {
            this.visitedChests.add(this.chest);
         }
         Work.close(client);
         this.chest = null;
         this.status = "That chest had nothing useful";
         return false;
      }
      if (this.chest != null) {
         if (++this.chestTicks > 600 || !block(client, this.chest).endsWith("chest")) {
            this.visitedChests.add(this.chest);
            this.chest = null;
         } else {
            this.use(client, navigator, this.chest, false, null, "Chest at " + coords(this.chest));
            return false;
         }
      }
      this.chestScan.step(client, 16000);
      if (!this.chestScan.running()) {
         for (class_2338 found : this.chestScan.results()) {
            if (!this.visitedChests.contains(found)) {
               this.chest = found;
               this.chestTicks = 0;
               break;
            }
         }
         this.chestScan.restart(client.field_1724.method_24515(), Set.of("chest", "trapped_chest"), 40, -64, 256);
      }
      if (this.chest == null) {
         brain.wander();
         this.status = "Exploring the nether for a bastion chest with an upgrade template";
      }
      return false;
   }

   /** The enchanting table with fifteen bookshelves two blocks out, which is level 30. */
   public boolean enchanting(class_310 client, BuilderNavigator navigator, Overlord brain) {
      if (this.enchantSite == null) {
         if (!brain.ensure("enchanting_table", 1, 0) || !brain.ensure("bookshelf", 15, 0)) {
            return false;
         }
         class_2338 candidate = client.field_1724.method_24515().method_10069(3, 0, 3);
         if (!this.areaFits(client, candidate)) {
            brain.wander();
            this.status = "Looking for a clear 5x5 for the enchanting table";
            return false;
         }
         this.enchantSite = candidate;
      }
      if (!block(client, this.enchantSite).equals("enchanting_table")) {
         if (!brain.ensure("enchanting_table", 1, 0)) {
            return false;
         }
         this.use(client, navigator, this.enchantSite, true, "enchanting_table", "Enchanting table");
         return false;
      }
      int placed = 0;
      for (int dx = -2; dx <= 2; dx++) {
         for (int dz = -2; dz <= 2; dz++) {
            if (Math.abs(dx) != 2 && Math.abs(dz) != 2 || placed >= 15) {
               continue;
            }
            class_2338 shelf = this.enchantSite.method_10069(dx, 0, dz);
            if (!block(client, shelf).equals("bookshelf")) {
               if (!brain.ensure("bookshelf", 1, 0)) {
                  return false;
               }
               this.use(client, navigator, shelf, true, "bookshelf", "Bookshelf " + (placed + 1) + " of 15");
               return false;
            }
            placed++;
         }
      }
      this.stations.put(dimension(client) + "enchanting_table", this.enchantSite);
      return this.open(client, navigator, "enchanting_table", net.minecraft.class_1718.class);
   }

   private boolean areaFits(class_310 client, class_2338 centre) {
      for (int dx = -2; dx <= 2; dx++) {
         for (int dz = -2; dz <= 2; dz++) {
            if (air(client, centre.method_10069(dx, -1, dz))) {
               return false;
            }
            for (int dy = 0; dy < 2; dy++) {
               if (!air(client, centre.method_10069(dx, dy, dz))) {
                  return false;
               }
            }
         }
      }
      return true;
   }
}
