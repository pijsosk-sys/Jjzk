package dev.donut.client.autobuild;

import dev.donut.client.mixin.BlockItemAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.AbstractPressurePlateBlock;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.CakeBlock;
import net.minecraft.block.ComparatorBlock;
import net.minecraft.block.CraftingTableBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.FluidFillable;
import net.minecraft.block.GrindstoneBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.LoomBlock;
import net.minecraft.block.NoteBlock;
import net.minecraft.block.RepeaterBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StonecutterBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

/**
 * Works out where to click so that a block comes out in the requested state. Every plan is a face the
 * player can really see from the given eye position, with the camera angles that look straight at it.
 */
@Environment(EnvType.CLIENT)
public final class PlacementPlanner {
   private static final Set<String> DERIVED = Set.of(
      "powered", "power", "lit", "locked", "in_wall", "snowy", "distance", "north", "east", "south", "west", "up", "down", "waterlogged"
   );
   private static final Direction[] SUPPORT_ORDER = new Direction[]{
      Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.DOWN
   };

   /** The rotation the server is certain to have: unchanged in the last two movement updates. Null while turning. */
   private static Float settledYaw;
   private static Float settledPitch;

   private PlacementPlanner() {
   }

   public static void settledRotation(Float yaw, Float pitch) {
      settledYaw = yaw;
      settledPitch = pitch;
   }

   public static boolean isSettled(float yaw, float pitch) {
      return settledYaw != null && Math.abs(net.minecraft.util.math.MathHelper.wrapDegrees(settledYaw - yaw)) < 0.001F && Math.abs(settledPitch - pitch) < 0.001F;
   }

   public static boolean compatible(BlockState desired, BlockState predicted) {
      if (predicted == null) {
         return false;
      } else if (MaterialTracker.matches(desired, predicted)) {
         return true;
      } else if (desired.getBlock() != predicted.getBlock()) {
         return false;
      } else {
         for (Property<?> property : desired.getProperties()) {
            if (!DERIVED.contains(property.getName())
               && property != Properties.STAIR_SHAPE
               && property != Properties.CHEST_TYPE
               && !isConfigurable(desired, property)
               && (property != SlabBlock.TYPE || desired.get(SlabBlock.TYPE) != SlabType.DOUBLE)
               && (!isStackCount(property) || (Integer)predicted.get(property) > (Integer)desired.get(property))
               && !desired.get(property).equals(predicted.get(property))) {
               return false;
            }
         }

         return true;
      }
   }

   private static boolean isStackCount(Property<?> property) {
      return property == Properties.LAYERS
         || property == Properties.CANDLES
         || property == Properties.PICKLES
         || property == Properties.EGGS
         || property == Properties.FLOWER_AMOUNT
         || property == Properties.SEGMENT_AMOUNT;
   }

   private static boolean isConfigurable(BlockState state, Property<?> property) {
      return state.getBlock() instanceof RepeaterBlock && property == Properties.DELAY
         || state.getBlock() instanceof ComparatorBlock && property == Properties.COMPARATOR_MODE
         || state.getBlock() instanceof NoteBlock && property == Properties.NOTE;
   }

   public static boolean needsConfiguration(BlockState expected, BlockState actual) {
      if (expected.getBlock() != actual.getBlock()) {
         return false;
      } else {
         for (Property<?> property : expected.getProperties()) {
            if (isConfigurable(expected, property) && !expected.get(property).equals(actual.get(property))) {
               return true;
            }
         }

         return false;
      }
   }

   public static boolean canAdd(BlockState expected, BlockState actual) {
      if (actual.isReplaceable()) {
         return true;
      } else if (expected.getBlock() != actual.getBlock()) {
         return false;
      } else if (!expected.contains(SlabBlock.TYPE)) {
         for (Property<?> property : expected.getProperties()) {
            if (isStackCount(property) && (Integer)expected.get(property) > (Integer)actual.get(property)) {
               return true;
            }
         }

         return false;
      } else {
         return expected.get(SlabBlock.TYPE) == SlabType.DOUBLE && actual.get(SlabBlock.TYPE) != SlabType.DOUBLE;
      }
   }

   /** Right-clicking this block does something of its own unless the player sneaks. */
   public static boolean isInteractive(BlockState state) {
      Block block = state.getBlock();
      return state.hasBlockEntity()
         || block instanceof DoorBlock
         || block instanceof TrapdoorBlock
         || block instanceof FenceGateBlock
         || block instanceof ButtonBlock
         || block instanceof LeverBlock
         || block instanceof RepeaterBlock
         || block instanceof ComparatorBlock
         || block instanceof NoteBlock
         || block instanceof CraftingTableBlock
         || block instanceof AnvilBlock
         || block instanceof BedBlock
         || block instanceof CakeBlock
         || block instanceof GrindstoneBlock
         || block instanceof LoomBlock
         || block instanceof StonecutterBlock
         || block == Blocks.CARTOGRAPHY_TABLE
         || block == Blocks.SMITHING_TABLE
         || block == Blocks.FLETCHING_TABLE
         || block == Blocks.DRAGON_EGG
         || block == Blocks.REDSTONE_ORE
         || block == Blocks.DEEPSLATE_REDSTONE_ORE
         || block == Blocks.RESPAWN_ANCHOR
         || block == Blocks.CAULDRON
         || block == Blocks.WATER_CAULDRON
         || block == Blocks.COMPOSTER
         || block == Blocks.BELL
         || block == Blocks.DAYLIGHT_DETECTOR
         || block == Blocks.FLOWER_POT;
   }

   /** Plan from where the player's eyes are right now. */
   public static PlacementPlanner.Plan plan(MinecraftClient client, BlockPos target, BlockState desired, ItemStack stack) {
      return client.player == null ? null : plan(client, target, desired, stack, client.player.getEyePos(), false);
   }

   /**
    * @param eye       where the eyes are, or would be
    * @param imagined  the player is not really there, so its own body must not count as an obstacle
    */
   public static PlacementPlanner.Plan plan(MinecraftClient client, BlockPos target, BlockState desired, ItemStack stack, Vec3d eye, boolean imagined) {
      if (client.world == null || client.player == null) {
         return null;
      }

      ClientPlayerEntity player = client.player;
      BlockState actual = client.world.getBlockState(target);
      boolean configure = needsConfiguration(desired, actual);
      if (!configure && !(stack.getItem() instanceof BlockItem)) {
         return null;
      }

      List<BlockHitResult> hits = new ArrayList<>();
      if (!actual.isAir() && actual.getBlock() == desired.getBlock() && !actual.isReplaceable()) {
         for (Direction face : Direction.values()) {
            addFace(hits, target, face, actual, client);
         }
      } else {
         for (Direction face : SUPPORT_ORDER) {
            BlockPos support = target.offset(face.getOpposite());
            if (MaterialTracker.isLoaded(client.world, support)) {
               BlockState supportState = client.world.getBlockState(support);
               if (!supportState.isAir() && !supportState.isReplaceable() && !supportState.getOutlineShape(client.world, support).isEmpty()) {
                  addFace(hits, support, face, supportState, client);
               }
            }
         }
      }

      double reach = player.getBlockInteractionRange() - 0.05;
      float oldYaw = player.getYaw();
      float oldPitch = player.getPitch();
      PlacementPlanner.Plan fallback = null;

      try {
         for (BlockHitResult sample : hits) {
            if (eye.squaredDistanceTo(sample.getPos()) > reach * reach) {
               continue;
            }

            BlockHitResult hit = sight(client, eye, sample);
            if (hit == null || eye.squaredDistanceTo(hit.getPos()) > reach * reach) {
               continue;
            }

            Vec3d point = hit.getPos();

            Vec3d delta = point.subtract(eye);
            float yaw = (float)Math.toDegrees(Math.atan2(-delta.x, delta.z));
            float pitch = (float)Math.toDegrees(Math.atan2(-delta.y, Math.hypot(delta.x, delta.z)));
            player.setYaw(yaw);
            player.setPitch(pitch);
            BlockState clicked = client.world.getBlockState(hit.getBlockPos());
            if (configure) {
               BlockState predicted = actual;
               if (actual.getBlock() instanceof RepeaterBlock) {
                  predicted = actual.cycle(Properties.DELAY);
               } else if (actual.getBlock() instanceof ComparatorBlock) {
                  predicted = actual.cycle(Properties.COMPARATOR_MODE);
               } else if (actual.getBlock() instanceof NoteBlock) {
                  predicted = actual.cycle(Properties.NOTE);
               }

               return new PlacementPlanner.Plan(hit, predicted, yaw, pitch, true, false, false, false);
            }

            BlockItem item = (BlockItem)stack.getItem();
            ItemPlacementContext context = item.getPlacementContext(new ItemPlacementContext(player, Hand.MAIN_HAND, stack, hit));
            if (context == null || !context.getBlockPos().equals(target) || !context.canPlace()) {
               continue;
            }

            BlockState predicted;
            if (imagined) {
               predicted = item.getBlock().getPlacementState(context);
               if (predicted != null && !predicted.canPlaceAt(client.world, target)) {
                  predicted = null;
               }
            } else {
               predicted = ((BlockItemAccessor)item).donut$getPlacementState(context);
            }

            if (compatible(desired, predicted)) {
               boolean sneak = isInteractive(clicked);
               boolean aim = true;
               if (!imagined && settledYaw != null) {
                  // Would the block come out right with the rotation the server already has? Then no turn is needed.
                  player.setYaw(settledYaw);
                  player.setPitch(settledPitch);
                  ItemPlacementContext asSeen = item.getPlacementContext(new ItemPlacementContext(player, Hand.MAIN_HAND, stack, hit));
                  aim = asSeen == null
                     || !asSeen.getBlockPos().equals(target)
                     || !asSeen.canPlace()
                     || !compatible(desired, ((BlockItemAccessor)item).donut$getPlacementState(asSeen));
               }

               PlacementPlanner.Plan plan = new PlacementPlanner.Plan(hit, predicted, yaw, pitch, false, sneak, false, aim);
               if (!sneak) {
                  return plan;
               }

               if (fallback == null) {
                  fallback = plan;
               }
            }
         }

         return fallback;
      } finally {
         player.setYaw(oldYaw);
         player.setPitch(oldPitch);
      }
   }

   /**
    * Plan for emptying a water bucket so that {@code target} ends up holding a water source.
    * A bucket is used along the line of sight, so the plan is a visible face next to the target
    * (or the target itself when it is a block that can hold water).
    */
   public static PlacementPlanner.Plan planWater(MinecraftClient client, BlockPos target, Vec3d eye) {
      if (client.world == null || client.player == null) {
         return null;
      }

      BlockState actual = client.world.getBlockState(target);
      List<BlockHitResult> hits = new ArrayList<>();
      boolean fillTarget = actual.getBlock() instanceof FluidFillable && actual.contains(Properties.WATERLOGGED) && !actual.get(Properties.WATERLOGGED);
      if (fillTarget) {
         for (Direction face : Direction.values()) {
            addFace(hits, target, face, actual, client);
         }
      } else if (actual.isAir() || actual.isReplaceable() || !actual.getFluidState().isEmpty() && actual.getBlock() == Blocks.WATER) {
         for (Direction face : SUPPORT_ORDER) {
            BlockPos support = target.offset(face.getOpposite());
            if (MaterialTracker.isLoaded(client.world, support)) {
               BlockState state = client.world.getBlockState(support);
               boolean wouldFillSupport = state.getBlock() instanceof FluidFillable && state.contains(Properties.WATERLOGGED) && !state.get(Properties.WATERLOGGED);
               if (!state.isAir() && !state.isReplaceable() && !wouldFillSupport && !state.getOutlineShape(client.world, support).isEmpty()) {
                  addFace(hits, support, face, state, client);
               }
            }
         }
      } else {
         return null;
      }

      double reach = client.player.getBlockInteractionRange() - 0.3;
      for (BlockHitResult hit : hits) {
         Vec3d point = hit.getPos();
         if (eye.squaredDistanceTo(point) <= reach * reach && visible(client, eye, hit)) {
            Vec3d delta = point.subtract(eye);
            float yaw = (float)Math.toDegrees(Math.atan2(-delta.x, delta.z));
            float pitch = (float)Math.toDegrees(Math.atan2(-delta.y, Math.hypot(delta.x, delta.z)));
            return new PlacementPlanner.Plan(hit, Blocks.WATER.getDefaultState(), yaw, pitch, false, false, true, false);
         }
      }

      return null;
   }

   /** Plan for scooping the water source at {@code source} with an empty bucket. */
   public static PlacementPlanner.Plan planScoop(MinecraftClient client, BlockPos source, Vec3d eye) {
      if (client.world == null || client.player == null) {
         return null;
      }

      double reach = client.player.getBlockInteractionRange() - 0.3;
      for (double height : new double[]{0.5, 0.8, 0.2}) {
         Vec3d point = new Vec3d(source.getX() + 0.5, source.getY() + height, source.getZ() + 0.5);
         if (eye.squaredDistanceTo(point) > reach * reach) {
            continue;
         }

         Vec3d delta = point.subtract(eye);
         Vec3d beyond = eye.add(delta.normalize().multiply(delta.length() + 0.4));
         BlockHitResult seen = client.world.raycast(new RaycastContext(eye, beyond, ShapeType.OUTLINE, FluidHandling.SOURCE_ONLY, client.player));
         if (seen.getType() == Type.BLOCK && seen.getBlockPos().equals(source)) {
            float yaw = (float)Math.toDegrees(Math.atan2(-delta.x, delta.z));
            float pitch = (float)Math.toDegrees(Math.atan2(-delta.y, Math.hypot(delta.x, delta.z)));
            return new PlacementPlanner.Plan(seen, Blocks.AIR.getDefaultState(), yaw, pitch, false, false, true, false);
         }
      }

      return null;
   }

   private static boolean visible(MinecraftClient client, Vec3d eye, BlockHitResult hit) {
      return sight(client, eye, hit) != null;
   }

   /**
    * What the player really clicks when aiming at {@code hit}: the first surface on that line, provided
    * it is the same block and the same face. Hollow shapes such as hoppers are hit a little deeper in.
    */
   private static BlockHitResult sight(MinecraftClient client, Vec3d eye, BlockHitResult hit) {
      Vec3d delta = hit.getPos().subtract(eye);
      double length = delta.length();
      if (length < 1.0E-4) {
         return null;
      }

      Vec3d beyond = eye.add(delta.multiply((length + 0.7) / length));
      BlockHitResult seen = client.world.raycast(new RaycastContext(eye, beyond, ShapeType.OUTLINE, FluidHandling.NONE, client.player));
      return seen.getType() == Type.BLOCK && seen.getBlockPos().equals(hit.getBlockPos()) && seen.getSide() == hit.getSide() ? seen : null;
   }

   private static final double[][] SPOTS = new double[][]{{0.5, 0.5}, {0.5, 0.25}, {0.5, 0.75}, {0.25, 0.5}, {0.75, 0.5}, {0.2, 0.2}, {0.8, 0.8}, {0.2, 0.8}, {0.8, 0.2}, {0.5, 0.06}, {0.5, 0.94}, {0.06, 0.5}, {0.94, 0.5}};

   private static void addFace(List<BlockHitResult> hits, BlockPos pos, Direction face, BlockState state, MinecraftClient client) {
      VoxelShape shape = state.getOutlineShape(client.world, pos);
      if (shape.isEmpty()) {
         return;
      }

      Box b = shape.getBoundingBox();
      for (double[] spot : SPOTS) {
         double u = spot[0];
         double v = spot[1];
         double x;
         double y;
         double z;
         switch (face.getAxis()) {
            case Y -> {
               x = b.minX + (b.maxX - b.minX) * u;
               z = b.minZ + (b.maxZ - b.minZ) * v;
               y = face == Direction.UP ? b.maxY : b.minY;
            }
            case X -> {
               z = b.minZ + (b.maxZ - b.minZ) * u;
               y = b.minY + (b.maxY - b.minY) * v;
               x = face == Direction.EAST ? b.maxX : b.minX;
            }
            default -> {
               x = b.minX + (b.maxX - b.minX) * u;
               y = b.minY + (b.maxY - b.minY) * v;
               z = face == Direction.SOUTH ? b.maxZ : b.minZ;
            }
         }

         hits.add(new BlockHitResult(new Vec3d(pos.getX() + x, pos.getY() + y, pos.getZ() + z), face, pos, false));
      }
   }

   /**
    * @param sneak    the clicked block has a right-click action, so the click must be made sneaking
    * @param useItem  the held item is used along the line of sight (buckets) instead of placed on the face
    * @param aim      the result depends on where the player looks, so the server must see this rotation first
    */
   @Environment(EnvType.CLIENT)
   public record Plan(BlockHitResult hit, BlockState prediction, float yaw, float pitch, boolean configure, boolean sneak, boolean useItem, boolean aim) {
   }
}
