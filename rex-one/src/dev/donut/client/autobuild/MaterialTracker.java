package dev.donut.client.autobuild;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CandleCakeBlock;
import net.minecraft.block.CauldronBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FlowerPotBlock;
import net.minecraft.block.LavaCauldronBlock;
import net.minecraft.block.LeveledCauldronBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkStatus;

@Environment(EnvType.CLIENT)
public final class MaterialTracker {
   private static final IntProperty[] COUNTS = new IntProperty[]{
      Properties.LAYERS, Properties.CANDLES, Properties.PICKLES, Properties.EGGS, Properties.FLOWER_AMOUNT, Properties.SEGMENT_AMOUNT
   };

   private MaterialTracker() {
   }

   /** Properties the game changes on its own (redstone, growth, neighbour shapes); never a reason to rebuild. */
   private static final java.util.Set<String> VOLATILE = java.util.Set.of(
      "powered", "power", "locked", "triggered", "extended", "enabled", "age", "stage", "distance", "moisture", "occupied", "open",
      "snowy", "in_wall", "attached", "persistent", "bottom", "unstable", "has_record", "has_book", "has_bottle_0", "has_bottle_1",
      "has_bottle_2", "drag", "hatch", "bites", "crafting", "ominous", "can_summon", "shrieking", "bloom", "sculk_sensor_phase",
      "tip", "thickness", "berries", "conditional", "inverted", "signal_fire", "disarmed", "short", "eye", "charges", "dusted", "cracked"
   );
   private static final java.util.Set<String> SIDES = java.util.Set.of("north", "east", "south", "west", "up", "down");

   /** True when {@code actual} is the block the blueprint asks for, ignoring state the world settles by itself. */
   public static boolean matches(BlockState desired, BlockState actual) {
      if (desired.equals(actual)) {
         return true;
      }
      Block want = desired.getBlock();
      Block have = actual.getBlock();
      if (want == Blocks.WATER || want == Blocks.BUBBLE_COLUMN) {
         // A source, the falling water under one, or a water plant living in it.
         net.minecraft.fluid.FluidState fluid = actual.getFluidState();
         if (have == Blocks.WATER || have == Blocks.BUBBLE_COLUMN) {
            return fluid.isStill() || fluid.contains(Properties.FALLING) && fluid.get(Properties.FALLING);
         }

         return (have == Blocks.KELP || have == Blocks.KELP_PLANT || have == Blocks.SEAGRASS || have == Blocks.TALL_SEAGRASS) && fluid.isStill();
      }
      if (want == Blocks.LAVA) {
         return have == Blocks.LAVA && actual.getFluidState().isStill();
      }
      if (family(want) != family(have)) {
         return false;
      }
      if (want != have) {
         return true;
      }
      boolean connecting = want instanceof net.minecraft.block.HorizontalConnectingBlock
         || want instanceof net.minecraft.block.WallBlock
         || want instanceof net.minecraft.block.RedstoneWireBlock
         || want instanceof net.minecraft.block.TripwireBlock
         || want instanceof net.minecraft.block.ChorusPlantBlock
         || want instanceof net.minecraft.block.FireBlock;
      boolean litIsDerived = !(want instanceof net.minecraft.block.CandleBlock) && !(want instanceof net.minecraft.block.CampfireBlock);
      for (Property<?> property : desired.getProperties()) {
         if (desired.get(property).equals(actual.get(property))) {
            continue;
         }
         String name = property.getName();
         if (VOLATILE.contains(name)
            || name.equals("lit") && litIsDerived
            || connecting && SIDES.contains(name)
            || property == Properties.STAIR_SHAPE
            || property == Properties.CHEST_TYPE
            || name.equals("shape") && want instanceof net.minecraft.block.AbstractRailBlock) {
            continue;
         }
         return false;
      }
      return true;
   }

   /** Stem and body of a growing plant count as the same thing: a kelp tip becomes kelp_plant when it grows. */
   private static Block family(Block block) {
      if (block == Blocks.KELP_PLANT) return Blocks.KELP;
      if (block == Blocks.TWISTING_VINES_PLANT) return Blocks.TWISTING_VINES;
      if (block == Blocks.WEEPING_VINES_PLANT) return Blocks.WEEPING_VINES;
      if (block == Blocks.CAVE_VINES_PLANT) return Blocks.CAVE_VINES;
      if (block == Blocks.BAMBOO_SAPLING) return Blocks.BAMBOO;
      return block;
   }

   /** Blueprint entries the builder never places itself: flowing fluid and the moving parts of pistons. */
   public static boolean isDerived(BlockState state) {
      Block block = state.getBlock();
      if (block == Blocks.PISTON_HEAD || block == Blocks.MOVING_PISTON) {
         return true;
      }
      return (block == Blocks.WATER || block == Blocks.LAVA) && !state.getFluidState().isStill();
   }

   public static boolean isLoaded(ClientWorld level, BlockPos pos) {
      return level != null && level.getChunkManager().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false) != null;
   }

   public static boolean isSecondary(BlockState state) {
      Block block = state.getBlock();
      return (block instanceof DoorBlock || block instanceof TallPlantBlock) && state.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER
         || block instanceof BedBlock && state.get(BedBlock.PART) == BedPart.HEAD;
   }

   public static MaterialTracker.Requirement requirement(BlockState state) {
      Item item = placementItem(state);
      int count = !isSecondary(state) && !state.isAir() ? 1 : 0;
      if (count != 0) {
         if (state.getBlock() instanceof SlabBlock && state.get(SlabBlock.TYPE) == SlabType.DOUBLE) {
            count = 2;
         }

         for (IntProperty property : COUNTS) {
            if (state.contains(property)) {
               count = state.get(property);
            }
         }
      }

      return new MaterialTracker.Requirement(item, count);
   }

   public static MaterialTracker.Requirement remaining(BlockState desired, BlockState actual) {
      MaterialTracker.Requirement all = requirement(desired);
      if (matches(desired, actual)) {
         return new MaterialTracker.Requirement(all.item(), 0);
      } else if (all.item() != Items.CAULDRON
         || !(actual.getBlock() instanceof CauldronBlock)
            && !(actual.getBlock() instanceof LeveledCauldronBlock)
            && !(actual.getBlock() instanceof LavaCauldronBlock)) {
         if (!(desired.getBlock() instanceof CandleCakeBlock)
            || !(actual.getBlock() instanceof CandleCakeBlock) && (!actual.isOf(Blocks.CAKE) || actual.get(Properties.BITES) != 0)) {
            if (desired.getBlock() instanceof FlowerPotBlock && actual.getBlock() instanceof FlowerPotBlock) {
               return new MaterialTracker.Requirement(all.item(), 0);
            } else if (desired.getBlock() != actual.getBlock()) {
               return all;
            } else {
               boolean onlyConfiguration = true;
               String blockName = Registries.BLOCK.getId(desired.getBlock()).getPath();

               for (Property<?> property : desired.getProperties()) {
                  if (!desired.get(property).equals(actual.get(property))) {
                     String name = property.getName();
                     if ((!blockName.equals("repeater") || !name.equals("delay"))
                        && (!blockName.equals("comparator") || !name.equals("mode"))
                        && (!blockName.equals("note_block") || !name.equals("note"))
                        && property != Properties.WATERLOGGED) {
                        onlyConfiguration = false;
                     }
                  }
               }

               if (onlyConfiguration) {
                  return new MaterialTracker.Requirement(all.item(), 0);
               } else if (desired.getBlock() instanceof SlabBlock
                  && desired.get(SlabBlock.TYPE) == SlabType.DOUBLE
                  && actual.get(SlabBlock.TYPE) != SlabType.DOUBLE) {
                  return new MaterialTracker.Requirement(all.item(), 1);
               } else {
                  for (IntProperty count : COUNTS) {
                     if (desired.contains(count)) {
                        boolean compatible = true;

                        for (Property<?> propertyx : desired.getProperties()) {
                           if (propertyx != count && !desired.get(propertyx).equals(actual.get(propertyx))) {
                              compatible = false;
                           }
                        }

                        if (compatible && actual.get(count) < desired.get(count)) {
                           return new MaterialTracker.Requirement(all.item(), desired.get(count) - actual.get(count));
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

   public static MaterialTracker.Snapshot snapshot(MinecraftClient client, Schematic schematic, PlacementTransform transform) {
      MaterialTracker.Scan scan = beginSnapshot(client, schematic, transform);

      while (!scan.advance(client, Integer.MAX_VALUE, Long.MAX_VALUE)) {
         if (scan.cancelled()) {
            throw new IllegalStateException("World changed during material scan");
         }
      }

      return scan.result();
   }

   public static MaterialTracker.Scan beginSnapshot(MinecraftClient client, Schematic schematic, PlacementTransform transform) {
      return new MaterialTracker.Scan(client, schematic, transform);
   }

   private static boolean canCompleteInPlace(BlockState desired, BlockState actual) {
      return remaining(desired, actual).count() < requirement(desired).count();
   }

   private static BlockPos pairedPosition(BlockPos pos, BlockState state) {
      if (isSecondary(state)) {
         return null;
      } else if ((state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TallPlantBlock) && state.contains(Properties.DOUBLE_BLOCK_HALF)) {
         return pos.up();
      } else {
         return state.getBlock() instanceof BedBlock ? pos.offset(state.get(BedBlock.FACING)) : null;
      }
   }

   public static boolean isPaired(BlockState state) {
      return state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TallPlantBlock || state.getBlock() instanceof BedBlock;
   }

   public static BlockPos primaryPosition(BlockPos pos, BlockState secondary) {
      return secondary.getBlock() instanceof BedBlock ? pos.offset(secondary.get(BedBlock.FACING).getOpposite()) : pos.down();
   }

   private static Item placementItem(BlockState state) {
      Block block = state.getBlock();
      String name = Registries.BLOCK.getId(block).getPath();

      return switch (name) {
         case "redstone_wire" -> Items.REDSTONE;
         case "tripwire" -> Items.STRING;
         case "water", "bubble_column" -> Items.WATER_BUCKET;
         case "lava" -> Items.LAVA_BUCKET;
         case "powder_snow" -> Items.POWDER_SNOW_BUCKET;
         case "water_cauldron", "lava_cauldron", "powder_snow_cauldron" -> Items.CAULDRON;
         case "wheat" -> Items.WHEAT_SEEDS;
         case "carrots" -> Items.CARROT;
         case "potatoes" -> Items.POTATO;
         case "beetroots" -> Items.BEETROOT_SEEDS;
         case "cocoa" -> Items.COCOA_BEANS;
         case "sweet_berry_bush" -> Items.SWEET_BERRIES;
         case "cave_vines", "cave_vines_plant" -> Items.GLOW_BERRIES;
         case "kelp_plant" -> Items.KELP;
         case "twisting_vines_plant" -> Items.TWISTING_VINES;
         case "weeping_vines_plant" -> Items.WEEPING_VINES;
         case "tall_seagrass" -> Items.SEAGRASS;
         case "bamboo_sapling" -> Items.BAMBOO;
         case "pumpkin_stem", "attached_pumpkin_stem" -> Items.PUMPKIN_SEEDS;
         case "melon_stem", "attached_melon_stem" -> Items.MELON_SEEDS;
         case "pitcher_crop" -> Items.PITCHER_POD;
         case "torchflower_crop" -> Items.TORCHFLOWER_SEEDS;
         default -> block instanceof CandleCakeBlock ? Items.CAKE : (block instanceof FlowerPotBlock ? Items.FLOWER_POT : block.asItem());
      };
   }

   private static String displayName(Item item, BlockState desired) {
      return item == Items.AIR ? "Blocks with no placement item" : item.getName().getString();
   }

   private static List<MaterialTracker.Requirement> supplemental(BlockState state) {
      String name = Registries.BLOCK.getId(state.getBlock()).getPath();
      if (state.getBlock() instanceof CandleCakeBlock) {
         String candle = name.substring(0, name.length() - "_cake".length());
         return List.of(new MaterialTracker.Requirement(Registries.ITEM.get(Identifier.ofVanilla(candle)), 1));
      } else if (state.getBlock() instanceof FlowerPotBlock pot && !pot.getContent().getDefaultState().isAir()) {
         return List.of(requirement(pot.getContent().getDefaultState()));
      } else if (!name.equals("water_cauldron") && (!state.contains(Properties.WATERLOGGED) || !state.get(Properties.WATERLOGGED))) {
         if (name.equals("lava_cauldron")) {
            return List.of(new MaterialTracker.Requirement(Items.LAVA_BUCKET, 1));
         } else {
            return name.equals("powder_snow_cauldron") ? List.of(new MaterialTracker.Requirement(Items.POWDER_SNOW_BUCKET, 1)) : List.of();
         }
      } else {
         return List.of(new MaterialTracker.Requirement(Items.WATER_BUCKET, 1));
      }
   }

   private static boolean hasSupplement(BlockState desired, BlockState actual, Item item) {
      if (matches(desired, actual)) {
         return true;
      } else {
         return item == Items.WATER_BUCKET && actual.contains(Properties.WATERLOGGED)
            ? actual.getBlock() == desired.getBlock() && actual.get(Properties.WATERLOGGED)
            : actual.getBlock() == desired.getBlock() && (desired.getBlock() instanceof CandleCakeBlock || desired.getBlock() instanceof FlowerPotBlock);
      }
   }

   @Environment(EnvType.CLIENT)
   public record Requirement(Item item, int count) {
   }

   @Environment(EnvType.CLIENT)
   public record Row(Item item, String name, int total, int correct, int remaining, int hotbar, int inventory, int missing, int unknown) {
   }

   @Environment(EnvType.CLIENT)
   public static final class Scan {
      private final Schematic schematic;
      private final PlacementTransform transform;
      private final ClientWorld world;
      private final ClientPlayerEntity player;
      private Map<Item, MaterialTracker.Totals> totals = new HashMap<>();
      private Map<BlockPos, Schematic.Block> byPosition = new HashMap<>();
      private int indexPosition;
      private int blockPosition;
      private int correct;
      private int remaining;
      private int unknown;
      private int conflicts;
      private boolean cancelled;
      private MaterialTracker.Snapshot result;

      private Scan(MinecraftClient client, Schematic schematic, PlacementTransform transform) {
         this.schematic = schematic;
         this.transform = transform;
         this.world = client.world;
         this.player = client.player;
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

      public boolean matches(MinecraftClient client, Schematic value, PlacementTransform placement) {
         return !this.cancelled && this.world == client.world && this.player == client.player && this.schematic == value && this.transform.equals(placement);
      }

      public void cancel() {
         this.cancelled = true;
         this.result = null;
         this.byPosition = Map.of();
         this.totals = Map.of();
      }

      public boolean advance(MinecraftClient client, int maxCells, long maxNanos) {
         if (maxCells < 1 || maxNanos < 1L) {
            throw new IllegalArgumentException("Material scan budget must be positive");
         } else if (this.cancelled) {
            return false;
         } else if (this.world != client.world || this.player != client.player) {
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
                  Schematic.Block block = this.schematic.blocks().get(this.indexPosition++);
                  if (MaterialTracker.isPaired(block.state())) {
                     this.byPosition.put(block.pos(), block);
                  }
               } else {
                  if (this.blockPosition >= size) {
                     break;
                  }

                  this.accumulate(client, this.schematic.blocks().get(this.blockPosition++));
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

      private void accumulate(MinecraftClient client, Schematic.Block block) {
         if (MaterialTracker.isDerived(block.state())) {
            return;
         }

         BlockPos pos = this.transform.apply(block.pos());
         BlockState desired = this.transform.apply(block.state());
         boolean loaded = MaterialTracker.isLoaded(client.world, pos);
         BlockState actual = loaded ? client.world.getBlockState(pos) : null;
         if (!loaded) {
            this.unknown++;
         } else if (MaterialTracker.matches(desired, actual)) {
            this.correct++;
         } else {
            this.remaining++;
            if (!actual.isReplaceable() && !MaterialTracker.canCompleteInPlace(desired, actual)) {
               this.conflicts++;
            }
         }

         MaterialTracker.Requirement required = MaterialTracker.requirement(desired);
         if (required.item() == Items.WATER_BUCKET) {
            // One bucket is reused for every water block, so water is not a per-block material.
            return;
         }

         boolean orphanSecondary = false;
         if (required.count() == 0) {
            if (!MaterialTracker.isSecondary(block.state())) {
               return;
            }

            BlockPos primaryPos = MaterialTracker.primaryPosition(block.pos(), block.state());
            Schematic.Block primary = this.byPosition.get(primaryPos);
            if (primary != null
               && primary.state().getBlock() == block.state().getBlock()
               && !MaterialTracker.isSecondary(primary.state())
               && block.pos().equals(MaterialTracker.pairedPosition(primary.pos(), primary.state()))) {
               return;
            }

            required = new MaterialTracker.Requirement(required.item(), 1);
            orphanSecondary = true;
         }

         BlockPos otherLocal = MaterialTracker.pairedPosition(block.pos(), block.state());
         boolean pairMatches = true;
         if (otherLocal != null && this.byPosition.containsKey(otherLocal)) {
            BlockPos otherWorld = this.transform.apply(otherLocal);
            boolean otherLoaded = MaterialTracker.isLoaded(client.world, otherWorld);
            loaded &= otherLoaded;
            pairMatches = otherLoaded
               && MaterialTracker.matches(this.transform.apply(this.byPosition.get(otherLocal).state()), client.world.getBlockState(otherWorld));
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
            MaterialTracker.Totals extraRow = this.totals.computeIfAbsent(extra.item(), item -> new MaterialTracker.Totals(item, item.getName().getString()));
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

      private MaterialTracker.Snapshot finish(MinecraftClient client) {
         if (client.player != null) {
            PlayerInventory inventory = client.player.getInventory();

            for (int slot = 0; slot < 36; slot++) {
               ItemStack stack = inventory.getStack(slot);
               MaterialTracker.Totals row = this.totals.get(stack.getItem());
               if (row != null && !stack.isEmpty()) {
                  if (slot < 9) {
                     row.hotbar = row.hotbar + stack.getCount();
                  } else {
                     row.inventory = row.inventory + stack.getCount();
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
      final Item item;
      final String name;
      int total;
      int correct;
      int remaining;
      int hotbar;
      int inventory;
      int unknown;

      Totals(Item item, String name) {
         this.item = item;
         this.name = name;
      }
   }
}
