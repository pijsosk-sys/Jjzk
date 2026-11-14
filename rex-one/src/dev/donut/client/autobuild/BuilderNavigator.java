package dev.donut.client.autobuild;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

/** Walks the player to a standing position chosen by the caller's goal test. */
@Environment(EnvType.CLIENT)
public final class BuilderNavigator {
   private ClientPlayerEntity owner;
   private Input saved;
   private final BuilderNavigator.SteeringInput steering = new BuilderNavigator.SteeringInput();
   private BuilderPathfinder.Route route = new BuilderPathfinder.Route(java.util.List.of(), Set.of(), false);
   private int index;
   private int stalled;
   private Vec3d lastPosition;
   private Object key;
   private BlockPos scaffold;
   private final Set<BuilderPathfinder.Cell> rejectedGoals = new HashSet<>();
   private final Map<BuilderPathfinder.Cell, Boolean> goalCache = new HashMap<>();

   public void hold(MinecraftClient client, boolean sneak) {
      if (client.player != null) {
         this.own(client);
         this.steering.forward = false;
         this.steering.jump = false;
         this.steering.sprint = false;
         this.steering.sneak = sneak;
         this.steering.tick();
      }
   }

   private void own(MinecraftClient client) {
      if (this.owner != client.player || client.player.input != this.steering) {
         this.release();
         this.owner = client.player;
         this.saved = this.owner.input;
         this.owner.input = this.steering;
      }
   }

   public void release() {
      if (this.owner != null && this.owner.input == this.steering && this.saved != null) {
         this.owner.input = this.saved;
      }

      this.owner = null;
      this.saved = null;
      this.steering.forward = false;
      this.steering.jump = false;
      this.steering.sneak = false;
      this.steering.sprint = false;
      this.replan();
   }

   public void replan() {
      this.route = new BuilderPathfinder.Route(java.util.List.of(), Set.of(), false);
      this.index = 0;
      this.stalled = 0;
      this.lastPosition = null;
      this.scaffold = null;
      this.goalCache.clear();
   }

   public void reset() {
      this.release();
      this.key = null;
      this.rejectedGoals.clear();
   }

   public void rejectCurrent(MinecraftClient client) {
      if (client.player != null) {
         this.rejectedGoals.add(cell(client.player.getBlockPos()));
      }

      this.replan();
   }

   public int routeLength() {
      return Math.max(0, this.route.cells().size() - this.index);
   }

   /** The block that has to be placed before the walk can continue, after {@link Result#NEED_SCAFFOLD}. */
   public BlockPos scaffold() {
      return this.scaffold;
   }

   /**
    * @param key         identifies the errand; a new key forgets rejected standing positions
    * @param near        roughly where the errand is, used to steer the search
    * @param goal        true for a standing cell from which the errand can be done
    * @param scaffoldAt  true when a scaffold block may be put at this position (second argument: the cell it is reached from)
    */
   public BuilderNavigator.Result moveTo(
      MinecraftClient client, Object key, BlockPos near, Predicate<BuilderPathfinder.Cell> goal, BiPredicate<BlockPos, BuilderPathfinder.Cell> scaffoldAt
   ) {
      if (client.world == null || client.player == null) {
         return BuilderNavigator.Result.NO_PATH;
      }

      ClientPlayerEntity player = client.player;
      this.own(client);
      this.steering.sneak = false;
      if (!key.equals(this.key)) {
         this.key = key;
         this.rejectedGoals.clear();
         this.replan();
      }

      Predicate<BuilderPathfinder.Cell> accept = at -> !this.rejectedGoals.contains(at) && this.goalCache.computeIfAbsent(at, goal::test);
      BuilderPathfinder.Cell feet = cell(player.getBlockPos());
      // Re-test where the player stands: the world may have changed since the route was planned.
      if (player.isOnGround() && !this.rejectedGoals.contains(feet) && goal.test(feet)) {
         this.steering.forward = false;
         this.steering.jump = false;
         this.steering.sprint = false;
         this.steering.tick();
         return BuilderNavigator.Result.ARRIVED;
      }

      if (!this.route.found() || this.index >= this.route.cells().size()) {
         this.goalCache.clear();
         BuilderPathfinder.Terrain terrain = new BuilderPathfinder.Terrain() {
            private final Map<BuilderPathfinder.Cell, Boolean> stand = new HashMap<>();
            private final Map<BuilderPathfinder.Cell, Boolean> room = new HashMap<>();

            @Override
            public boolean standable(BuilderPathfinder.Cell at) {
               return this.stand.computeIfAbsent(at, c -> BuilderNavigator.solidFloor(client, pos(c).down()) && this.headroom(c));
            }

            @Override
            public boolean headroom(BuilderPathfinder.Cell at) {
               return this.room.computeIfAbsent(at, c -> BuilderNavigator.bodyFits(client, c));
            }

            @Override
            public boolean scaffoldable(BuilderPathfinder.Cell from, BuilderPathfinder.Cell next) {
               return this.headroom(next) && scaffoldAt.test(pos(next).down(), from);
            }
         };
         this.route = BuilderPathfinder.find(feet, cell(near), terrain, accept, 9000, 96);
         this.index = 0;
         this.stalled = 0;
         if (!this.route.found()) {
            this.steering.forward = false;
            this.steering.jump = false;
            this.steering.tick();
            return BuilderNavigator.Result.NO_PATH;
         }

         if (this.route.cells().isEmpty()) {
            return BuilderNavigator.Result.ARRIVED;
         }
      }

      BuilderPathfinder.Cell next = this.route.cells().get(this.index);
      Vec3d aim = new Vec3d(next.x() + 0.5, next.y(), next.z() + 0.5);
      Vec3d at = player.getEntityPos();
      double flat = (at.x - aim.x) * (at.x - aim.x) + (at.z - aim.z) * (at.z - aim.z);
      if (flat < 0.09 && Math.abs(at.y - aim.y) < 0.6) {
         this.index++;
         if (this.index >= this.route.cells().size()) {
            this.replan();
            this.steering.forward = false;
            this.steering.tick();
            return BuilderNavigator.Result.MOVING;
         }

         next = this.route.cells().get(this.index);
         aim = new Vec3d(next.x() + 0.5, next.y(), next.z() + 0.5);
      }

      if (this.route.scaffolds().contains(next) && !solidFloor(client, pos(next).down())) {
         this.scaffold = pos(next).down();
         this.steering.forward = false;
         this.steering.jump = false;
         this.steering.sprint = false;
         this.steering.tick();
         return BuilderNavigator.Result.NEED_SCAFFOLD;
      }

      this.scaffold = null;
      Vec3d delta = aim.subtract(at);
      player.setYaw((float)Math.toDegrees(Math.atan2(-delta.x, delta.z)));
      player.setPitch(10.0F);
      boolean climbing = next.y() > at.y + 0.25;
      this.steering.forward = true;
      this.steering.jump = climbing && (player.isOnGround() || player.isTouchingWater());
      this.steering.sprint = !climbing && this.routeLength() > 3 && next.y() >= at.y - 0.25;
      this.steering.tick();
      if (this.lastPosition != null && at.squaredDistanceTo(this.lastPosition) < 4.0E-4) {
         this.stalled++;
      } else {
         this.stalled = 0;
      }

      this.lastPosition = at;
      if (this.stalled > 30) {
         this.replan();
         return BuilderNavigator.Result.STUCK;
      }

      return BuilderNavigator.Result.MOVING;
   }

   /** Nudge the player to the middle of the block it stands on. */
   public void centre(MinecraftClient client) {
      ClientPlayerEntity player = client.player;
      this.own(client);
      BlockPos feet = player.getBlockPos();
      Vec3d delta = new Vec3d(feet.getX() + 0.5 - player.getX(), 0.0, feet.getZ() + 0.5 - player.getZ());
      this.steering.jump = false;
      this.steering.sprint = false;
      this.steering.sneak = false;
      this.steering.forward = delta.lengthSquared() > 0.01;
      if (this.steering.forward) {
         player.setYaw((float)Math.toDegrees(Math.atan2(-delta.x, delta.z)));
      }

      this.steering.tick();
   }

   static boolean solidFloor(MinecraftClient client, BlockPos floor) {
      if (!MaterialTracker.isLoaded(client.world, floor)) {
         return false;
      }

      BlockState ground = client.world.getBlockState(floor);
      if (ground.isOf(Blocks.MAGMA_BLOCK)
         || ground.isOf(Blocks.CAMPFIRE)
         || ground.isOf(Blocks.SOUL_CAMPFIRE)
         || ground.isOf(Blocks.CACTUS)
         || ground.isOf(Blocks.POWDER_SNOW)) {
         return false;
      }

      VoxelShape shape = ground.getCollisionShape(client.world, floor);
      if (shape.isEmpty()) {
         return false;
      }

      return shape.getMax(Direction.Axis.Y) > 0.8
         && shape.getMax(Direction.Axis.Y) <= 1.0
         && shape.getMin(Direction.Axis.X) <= 0.35
         && shape.getMax(Direction.Axis.X) >= 0.65
         && shape.getMin(Direction.Axis.Z) <= 0.35
         && shape.getMax(Direction.Axis.Z) >= 0.65;
   }

   static boolean bodyFits(MinecraftClient client, BuilderPathfinder.Cell at) {
      BlockPos pos = pos(at);
      if (!MaterialTracker.isLoaded(client.world, pos) || !client.world.getFluidState(pos).isEmpty() || !client.world.getFluidState(pos.up()).isEmpty()) {
         return false;
      }

      BlockState state = client.world.getBlockState(pos);
      if (state.isOf(Blocks.FIRE) || state.isOf(Blocks.SOUL_FIRE) || state.isOf(Blocks.SWEET_BERRY_BUSH) || state.isOf(Blocks.POWDER_SNOW)) {
         return false;
      }

      return client.world.isSpaceEmpty(client.player, new Box(at.x() + 0.2, at.y() + 0.01, at.z() + 0.2, at.x() + 0.8, at.y() + 1.8, at.z() + 0.8));
   }

   static BuilderPathfinder.Cell cell(BlockPos p) {
      return new BuilderPathfinder.Cell(p.getX(), p.getY(), p.getZ());
   }

   static BlockPos pos(BuilderPathfinder.Cell p) {
      return new BlockPos(p.x(), p.y(), p.z());
   }

   @Environment(EnvType.CLIENT)
   public static enum Result {
      MOVING,
      ARRIVED,
      NO_PATH,
      STUCK,
      NEED_SCAFFOLD;
   }

   @Environment(EnvType.CLIENT)
   private static final class SteeringInput extends Input {
      boolean forward;
      boolean jump;
      boolean sneak;
      boolean sprint;

      @Override
      public void tick() {
         this.playerInput = new PlayerInput(this.forward, false, false, false, this.jump, this.sneak, this.sprint);
         this.movementVector = this.forward ? new Vec2f(0.0F, 1.0F) : Vec2f.ZERO;
      }

      @Override
      public void jump() {
         if (this.forward) {
            super.jump();
         }
      }
   }
}
