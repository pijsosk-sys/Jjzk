package dev.donut.client.autobuild;

import dev.donut.client.autobuild.Schematic.Block;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_1661;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_2244;
import net.minecraft.class_2246;
import net.minecraft.class_2248;
import net.minecraft.class_2320;
import net.minecraft.class_2323;
import net.minecraft.class_2338;
import net.minecraft.class_2362;
import net.minecraft.class_2482;
import net.minecraft.class_2680;
import net.minecraft.class_2741;
import net.minecraft.class_2742;
import net.minecraft.class_2756;
import net.minecraft.class_2758;
import net.minecraft.class_2769;
import net.minecraft.class_2771;
import net.minecraft.class_2806;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_5545;
import net.minecraft.class_5546;
import net.minecraft.class_5553;
import net.minecraft.class_5556;
import net.minecraft.class_638;
import net.minecraft.class_746;
import net.minecraft.class_7923;

@Environment(EnvType.CLIENT)
public final class MaterialTracker {
   private static final class_2758[] COUNTS = new class_2758[]{
      class_2741.field_12536, class_2741.field_27220, class_2741.field_12543, class_2741.field_12509, class_2741.field_42835, class_2741.field_55829
   };

   private MaterialTracker() {
   }

   /**
    * Exact, except where the game decides the rest, as the builder sees it: any water source fills a
    * water or bubble-column cell, any lava source a lava cell, and kelp counts at any age.
    */
   public static boolean matches(class_2680 desired, class_2680 actual) {
      if (desired.equals(actual)) {
         return true;
      }
      if (actual == null) {
         return false;
      }
      if (waterSource(desired)) {
         return waterSource(actual);
      }
      if (desired.method_27852(class_2246.field_10164) && desired.method_26227().method_15771()) {
         return actual.method_27852(class_2246.field_10164) && actual.method_26227().method_15771();
      }
      if (kelp(desired)) {
         return kelp(actual);
      }
      if (passage(desired) && desired.method_26204() == actual.method_26204()) {
         // Opened on the way through; the builder shuts it again once the build is done.
         return desired.method_47968(net.minecraft.class_2741.field_12537, false).method_47968(net.minecraft.class_2741.field_12484, false)
            .equals(actual.method_47968(net.minecraft.class_2741.field_12537, false).method_47968(net.minecraft.class_2741.field_12484, false));
      }
      return growthOnly(desired, actual);
   }

   /**
    * The same block differing only in how far it has grown: a farm schematic saves its bushes,
    * crops and cane fully grown, a freshly placed one starts at the beginning, and the game grows
    * it the rest of the way. Treated as different, a berry farm never finished. Stair shapes are
    * the same kind of thing: the game picks them from the neighbouring stairs.
    */
   public static boolean growthOnly(class_2680 desired, class_2680 actual) {
      if (actual == null || desired.method_26204() != actual.method_26204()) {
         return false;
      }
      for (class_2769<?> property : desired.method_28501()) {
         if (!desired.method_11654(property).equals(actual.method_11654(property))) {
            String name = property.method_11899();
            // A stair's shape follows its neighbours: corner stairs always come out as corners.
            boolean stairShape = name.equals("shape") && desired.method_26204() instanceof net.minecraft.class_2510;
            if (!name.equals("age") && !name.equals("stage") && !name.equals("moisture") && !stairShape) {
               return false;
            }
         }
      }
      return true;
   }

   /** A door or fence gate: something a player walks through. */
   private static boolean passage(class_2680 state) {
      return state.method_26204() instanceof net.minecraft.class_2323 || state.method_26204() instanceof net.minecraft.class_2349;
   }

   private static boolean waterSource(class_2680 state) {
      return state.method_27852(class_2246.field_10382) && state.method_26227().method_15771() || state.method_27852(class_2246.field_10422);
   }

   private static boolean kelp(class_2680 state) {
      return state.method_27852(class_2246.field_9993) || state.method_27852(class_2246.field_10463);
   }

   /**
    * Flowing water and lava: they form on their own from a source and are never placed, so they
    * need nothing. Counted as needing buckets, a farm listed dozens it could never use.
    */
   public static boolean flowing(class_2680 state) {
      return (state.method_27852(class_2246.field_10382) || state.method_27852(class_2246.field_10164)) && !state.method_26227().method_15771();
   }

   public static boolean isLoaded(class_638 level, class_2338 pos) {
      return level != null && level.method_2935().method_2857(pos.method_10263() >> 4, pos.method_10260() >> 4, class_2806.field_12803, false) != null;
   }

   public static boolean isSecondary(class_2680 state) {
      class_2248 block = state.method_26204();
      return (block instanceof class_2323 || block instanceof class_2320) && state.method_11654(class_2741.field_12533) == class_2756.field_12609
         || block instanceof class_2244 && state.method_11654(class_2244.field_9967) == class_2742.field_12560;
   }

   public static MaterialTracker.Requirement requirement(class_2680 state) {
      class_1792 item = placementItem(state);
      int count = !isSecondary(state) && !state.method_26215() && !flowing(state) ? 1 : 0;
      if (count != 0) {
         if (state.method_26204() instanceof class_2482 && state.method_11654(class_2482.field_11501) == class_2771.field_12682) {
            count = 2;
         }

         for (class_2758 property : COUNTS) {
            if (state.method_28498(property)) {
               count = state.method_11654(property);
            }
         }
      }

      return new MaterialTracker.Requirement(item, count);
   }

   public static MaterialTracker.Requirement remaining(class_2680 desired, class_2680 actual) {
      MaterialTracker.Requirement all = requirement(desired);
      if (matches(desired, actual)) {
         return new MaterialTracker.Requirement(all.item(), 0);
      } else if (all.item() != class_1802.field_8638
         || !(actual.method_26204() instanceof class_5546) && !(actual.method_26204() instanceof class_5556) && !(actual.method_26204() instanceof class_5553)) {
         if (!(desired.method_26204() instanceof class_5545)
            || !(actual.method_26204() instanceof class_5545)
               && (!actual.method_27852(class_2246.field_10183) || actual.method_11654(class_2741.field_12505) != 0)) {
            if (desired.method_26204() instanceof class_2362 && actual.method_26204() instanceof class_2362) {
               return new MaterialTracker.Requirement(all.item(), 0);
            } else if (desired.method_26204() != actual.method_26204()) {
               return all;
            } else {
               boolean onlyConfiguration = true;
               String blockName = class_7923.field_41175.method_10221(desired.method_26204()).method_12832();

               for (class_2769<?> property : desired.method_28501()) {
                  if (!desired.method_11654(property).equals(actual.method_11654(property))) {
                     String name = property.method_11899();
                     if ((!blockName.equals("repeater") || !name.equals("delay"))
                        && (!blockName.equals("comparator") || !name.equals("mode"))
                        && (!blockName.equals("note_block") || !name.equals("note"))
                        && property != class_2741.field_12508) {
                        onlyConfiguration = false;
                     }
                  }
               }

               if (onlyConfiguration) {
                  return new MaterialTracker.Requirement(all.item(), 0);
               } else if (desired.method_26204() instanceof class_2482
                  && desired.method_11654(class_2482.field_11501) == class_2771.field_12682
                  && actual.method_11654(class_2482.field_11501) != class_2771.field_12682) {
                  return new MaterialTracker.Requirement(all.item(), 1);
               } else {
                  for (class_2758 count : COUNTS) {
                     if (desired.method_28498(count)) {
                        boolean compatible = true;

                        for (class_2769<?> propertyx : desired.method_28501()) {
                           if (propertyx != count && !desired.method_11654(propertyx).equals(actual.method_11654(propertyx))) {
                              compatible = false;
                           }
                        }

                        if (compatible && actual.method_11654(count) < desired.method_11654(count)) {
                           return new MaterialTracker.Requirement(all.item(), desired.method_11654(count) - actual.method_11654(count));
                        }
                     }
                  }

                  return all;
               }
            }
         } else {
            return new MaterialTracker.Requirement(all.item(), 0);
         }
      } else {
         return new MaterialTracker.Requirement(all.item(), 0);
      }
   }

   public static MaterialTracker.Snapshot snapshot(class_310 client, Schematic schematic, PlacementTransform transform) {
      MaterialTracker.Scan scan = beginSnapshot(client, schematic, transform);

      while (!scan.advance(client, Integer.MAX_VALUE, Long.MAX_VALUE)) {
         if (scan.cancelled()) {
            throw new IllegalStateException("World changed during material scan");
         }
      }

      return scan.result();
   }

   public static MaterialTracker.Scan beginSnapshot(class_310 client, Schematic schematic, PlacementTransform transform) {
      return new MaterialTracker.Scan(client, schematic, transform);
   }

   private static boolean canCompleteInPlace(class_2680 desired, class_2680 actual) {
      return remaining(desired, actual).count() < requirement(desired).count();
   }

   private static class_2338 pairedPosition(class_2338 pos, class_2680 state) {
      if (isSecondary(state)) {
         return null;
      } else if ((state.method_26204() instanceof class_2323 || state.method_26204() instanceof class_2320) && state.method_28498(class_2741.field_12533)) {
         return pos.method_10084();
      } else {
         return state.method_26204() instanceof class_2244 ? pos.method_10093(state.method_11654(class_2244.field_11177)) : null;
      }
   }

   public static boolean isPaired(class_2680 state) {
      return state.method_26204() instanceof class_2323 || state.method_26204() instanceof class_2320 || state.method_26204() instanceof class_2244;
   }

   public static class_2338 primaryPosition(class_2338 pos, class_2680 secondary) {
      return secondary.method_26204() instanceof class_2244
         ? pos.method_10093(secondary.method_11654(class_2244.field_11177).method_10153())
         : pos.method_10074();
   }

   private static class_1792 placementItem(class_2680 state) {
      class_2248 block = state.method_26204();
      String name = class_7923.field_41175.method_10221(block).method_12832();

      return switch (name) {
         case "redstone_wire" -> class_1802.field_8725;
         case "tripwire" -> class_1802.field_8276;
         case "water", "bubble_column" -> class_1802.field_8705;
         case "lava" -> class_1802.field_8187;
         case "powder_snow" -> class_1802.field_27876;
         case "water_cauldron", "lava_cauldron", "powder_snow_cauldron" -> class_1802.field_8638;
         case "wheat" -> class_1802.field_8317;
         case "carrots" -> class_1802.field_8179;
         case "potatoes" -> class_1802.field_8567;
         case "beetroots" -> class_1802.field_8309;
         case "cocoa" -> class_1802.field_8116;
         case "sweet_berry_bush" -> class_1802.field_16998;
         case "cave_vines", "cave_vines_plant" -> class_1802.field_28659;
         case "kelp_plant" -> class_1802.field_17532;
         case "bamboo_sapling" -> class_1802.field_8648;
         case "pumpkin_stem", "attached_pumpkin_stem" -> class_1802.field_46249;
         case "melon_stem", "attached_melon_stem" -> class_1802.field_46250;
         case "pitcher_crop" -> class_1802.field_43195;
         case "torchflower_crop" -> class_1802.field_42711;
         default -> block instanceof class_5545 ? class_1802.field_17534 : (block instanceof class_2362 ? class_1802.field_8074 : block.method_8389());
      };
   }

   private static String displayName(class_1792 item, class_2680 desired) {
      return item == class_1802.field_8162 ? "Blocks with no placement item" : item.method_63680().getString();
   }

   private static List<MaterialTracker.Requirement> supplemental(class_2680 state) {
      String name = class_7923.field_41175.method_10221(state.method_26204()).method_12832();
      if (state.method_26204() instanceof class_5545) {
         String candle = name.substring(0, name.length() - "_cake".length());
         return List.of(new MaterialTracker.Requirement(class_7923.field_41178.method_63535(class_2960.method_60656(candle)), 1));
      } else if (state.method_26204() instanceof class_2362 pot && !pot.method_16231().method_9564().method_26215()) {
         return List.of(requirement(pot.method_16231().method_9564()));
      } else if (!name.equals("water_cauldron") && (!state.method_28498(class_2741.field_12508) || !state.method_11654(class_2741.field_12508))) {
         if (name.equals("lava_cauldron")) {
            return List.of(new MaterialTracker.Requirement(class_1802.field_8187, 1));
         } else {
            return name.equals("powder_snow_cauldron") ? List.of(new MaterialTracker.Requirement(class_1802.field_27876, 1)) : List.of();
         }
      } else {
         return List.of(new MaterialTracker.Requirement(class_1802.field_8705, 1));
      }
   }

   private static boolean hasSupplement(class_2680 desired, class_2680 actual, class_1792 item) {
      if (matches(desired, actual)) {
         return true;
      } else {
         return item == class_1802.field_8705 && actual.method_28498(class_2741.field_12508)
            ? actual.method_26204() == desired.method_26204() && actual.method_11654(class_2741.field_12508)
            : actual.method_26204() == desired.method_26204() && (desired.method_26204() instanceof class_5545 || desired.method_26204() instanceof class_2362);
      }
   }

   @Environment(EnvType.CLIENT)
   public record Requirement(class_1792 item, int count) {
   }

   @Environment(EnvType.CLIENT)
   public record Row(class_1792 item, String name, int total, int correct, int remaining, int hotbar, int inventory, int missing, int unknown) {
   }

   @Environment(EnvType.CLIENT)
   public static final class Scan {
      private final Schematic schematic;
      private final PlacementTransform transform;
      private final class_638 world;
      private final class_746 player;
      private Map<class_1792, MaterialTracker.Totals> totals = new HashMap<>();
      private Map<class_2338, Block> byPosition = new HashMap<>();
      private int indexPosition;
      private int blockPosition;
      private int correct;
      private int remaining;
      private int unknown;
      private int conflicts;
      private boolean cancelled;
      private MaterialTracker.Snapshot result;

      private Scan(class_310 client, Schematic schematic, PlacementTransform transform) {
         this.schematic = schematic;
         this.transform = transform;
         this.world = client.field_1687;
         this.player = client.field_1724;
         if (schematic == null) {
            this.result = new MaterialTracker.Snapshot(List.of(), 0, 0, 0, 0, 0);
         }
      }

      public MaterialTracker.Snapshot result() {
         return this.result;
      }

      public boolean cancelled() {
         return this.cancelled;
      }

      public int processedCells() {
         return this.indexPosition + this.blockPosition;
      }

      public double progress() {
         return this.schematic != null && !this.schematic.blocks().isEmpty() ? (double)this.processedCells() / (2L * this.schematic.blocks().size()) : 1.0;
      }

      public boolean matches(class_310 client, Schematic value, PlacementTransform placement) {
         return !this.cancelled
            && this.world == client.field_1687
            && this.player == client.field_1724
            && this.schematic == value
            && this.transform.equals(placement);
      }

      public void cancel() {
         this.cancelled = true;
         this.result = null;
         this.byPosition = Map.of();
         this.totals = Map.of();
      }

      public boolean advance(class_310 client, int maxCells, long maxNanos) {
         if (maxCells < 1 || maxNanos < 1L) {
            throw new IllegalArgumentException("Material scan budget must be positive");
         } else if (this.cancelled) {
            return false;
         } else if (this.world != client.field_1687 || this.player != client.field_1724) {
            this.cancel();
            return false;
         } else if (this.result != null) {
            return true;
         } else {
            long started = System.nanoTime();
            int visited = 0;

            int size;
            for (size = this.schematic.blocks().size(); visited < maxCells && System.nanoTime() - started < maxNanos; visited++) {
               if (this.indexPosition < size) {
                  Block block = (Block)this.schematic.blocks().get(this.indexPosition++);
                  if (MaterialTracker.isPaired(block.state())) {
                     this.byPosition.put(block.pos(), block);
                  }
               } else {
                  if (this.blockPosition >= size) {
                     break;
                  }

                  this.accumulate(client, (Block)this.schematic.blocks().get(this.blockPosition++));
               }
            }

            if (this.indexPosition == size && this.blockPosition == size) {
               this.result = this.finish(client);
               this.byPosition = Map.of();
               this.totals = Map.of();
            }

            return this.result != null;
         }
      }

      private void accumulate(class_310 client, Block block) {
         class_2338 pos = this.transform.apply(block.pos());
         class_2680 desired = this.transform.apply(block.state());
         if (MaterialTracker.flowing(desired)) {
            return; // not part of what gets built: the game makes it from a source
         }
         boolean loaded = MaterialTracker.isLoaded(client.field_1687, pos);
         class_2680 actual = loaded ? client.field_1687.method_8320(pos) : null;
         if (!loaded) {
            this.unknown++;
         } else if (MaterialTracker.matches(desired, actual)) {
            this.correct++;
         } else {
            this.remaining++;
            if (!actual.method_45474() && !MaterialTracker.canCompleteInPlace(desired, actual)) {
               this.conflicts++;
            }
         }

         MaterialTracker.Requirement required = MaterialTracker.requirement(desired);
         boolean orphanSecondary = false;
         if (required.count() == 0) {
            if (!MaterialTracker.isSecondary(block.state())) {
               return;
            }

            class_2338 primaryPos = MaterialTracker.primaryPosition(block.pos(), block.state());
            Block primary = this.byPosition.get(primaryPos);
            if (primary != null
               && primary.state().method_26204() == block.state().method_26204()
               && !MaterialTracker.isSecondary(primary.state())
               && block.pos().equals(MaterialTracker.pairedPosition(primary.pos(), primary.state()))) {
               return;
            }

            required = new MaterialTracker.Requirement(required.item(), 1);
            orphanSecondary = true;
         }

         class_2338 otherLocal = MaterialTracker.pairedPosition(block.pos(), block.state());
         boolean pairMatches = true;
         if (otherLocal != null && this.byPosition.containsKey(otherLocal)) {
            class_2338 otherWorld = this.transform.apply(otherLocal);
            boolean otherLoaded = MaterialTracker.isLoaded(client.field_1687, otherWorld);
            loaded &= otherLoaded;
            pairMatches = otherLoaded
               && MaterialTracker.matches(this.transform.apply(this.byPosition.get(otherLocal).state()), client.field_1687.method_8320(otherWorld));
         }

         MaterialTracker.Totals row = this.totals
            .computeIfAbsent(required.item(), item -> new MaterialTracker.Totals(item, MaterialTracker.displayName(item, desired)));
         row.total = row.total + required.count();
         if (!loaded) {
            row.unknown = row.unknown + required.count();
         } else {
            int need = orphanSecondary
               ? (MaterialTracker.matches(desired, actual) ? 0 : 1)
               : (pairMatches ? MaterialTracker.remaining(desired, actual).count() : required.count());
            row.remaining += need;
            row.correct = row.correct + (required.count() - need);
         }

         for (MaterialTracker.Requirement extra : MaterialTracker.supplemental(desired)) {
            MaterialTracker.Totals extraRow = this.totals
               .computeIfAbsent(extra.item(), item -> new MaterialTracker.Totals(item, item.method_63680().getString()));
            extraRow.total = extraRow.total + extra.count();
            if (!loaded) {
               extraRow.unknown = extraRow.unknown + extra.count();
            } else if (MaterialTracker.hasSupplement(desired, actual, extra.item())) {
               extraRow.correct = extraRow.correct + extra.count();
            } else {
               extraRow.remaining = extraRow.remaining + extra.count();
            }
         }
      }

      private MaterialTracker.Snapshot finish(class_310 client) {
         if (client.field_1724 != null) {
            class_1661 inventory = client.field_1724.method_31548();

            for (int slot = 0; slot < 36; slot++) {
               class_1799 stack = inventory.method_5438(slot);
               MaterialTracker.Totals row = this.totals.get(stack.method_7909());
               if (row != null && !stack.method_7960()) {
                  if (slot < 9) {
                     row.hotbar = row.hotbar + stack.method_7947();
                  } else {
                     row.inventory = row.inventory + stack.method_7947();
                  }
               }
            }
         }

         List<MaterialTracker.Row> rows = new ArrayList<>();

         for (MaterialTracker.Totals row : this.totals.values()) {
            rows.add(
               new MaterialTracker.Row(
                  row.item,
                  row.name,
                  row.total,
                  row.correct,
                  row.remaining,
                  row.hotbar,
                  row.inventory,
                  Math.max(0, row.remaining - row.hotbar - row.inventory),
                  row.unknown
               )
            );
         }

         rows.sort(Comparator.comparing(MaterialTracker.Row::name, String.CASE_INSENSITIVE_ORDER));
         return new MaterialTracker.Snapshot(rows, this.schematic.blocks().size(), this.correct, this.remaining, this.unknown, this.conflicts);
      }
   }

   @Environment(EnvType.CLIENT)
   public record Snapshot(List<MaterialTracker.Row> rows, int totalBlocks, int correctBlocks, int remainingBlocks, int unknownBlocks, int conflicts) {
      public Snapshot(List<MaterialTracker.Row> rows, int totalBlocks, int correctBlocks, int remainingBlocks, int unknownBlocks, int conflicts) {
         rows = List.copyOf(rows);
         this.rows = rows;
         this.totalBlocks = totalBlocks;
         this.correctBlocks = correctBlocks;
         this.remainingBlocks = remainingBlocks;
         this.unknownBlocks = unknownBlocks;
         this.conflicts = conflicts;
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class Totals {
      final class_1792 item;
      final String name;
      int total;
      int correct;
      int remaining;
      int hotbar;
      int inventory;
      int unknown;

      Totals(class_1792 item, String name) {
         this.item = item;
         this.name = name;
      }
   }
}
