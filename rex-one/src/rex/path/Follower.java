package rex.path;

import dev.donut.client.autobuild.BuilderLook;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_10185;
import net.minecraft.class_241;
import net.minecraft.class_2338;
import net.minecraft.class_243;
import net.minecraft.class_310;
import net.minecraft.class_3532;
import net.minecraft.class_744;
import net.minecraft.class_746;
import rex.overlord.Dig;
import rex.overlord.Ids;
import rex.overlord.Reach;
import rex.overlord.Stock;

/**
 * Walks a path. The movement goes in through the player's own input object, the same way the
 * builder steers, so the server sees ordinary key presses rather than teleports.
 *
 * <p>Opening any screen hands control straight back: nothing moves, nothing digs, and the walk
 * carries on from where it stopped once the screen is closed.
 */
@Environment(EnvType.CLIENT)
public final class Follower {
   @Environment(EnvType.CLIENT)
   public enum Result {
      /** Still walking. */
      WALKING,
      /** The end of this path. */
      DONE,
      /** Standing still too long, or the world no longer matches the path. */
      STUCK,
      /** A screen is open; the walk is on hold. */
      PAUSED
   }

   /** Blocks it is willing to throw down to bridge a gap, best first. */
   private static final String[] THROWAWAY = {"cobblestone", "cobbled_deepslate", "dirt", "netherrack", "stone", "andesite", "deepslate"};

   private final Steering steering = new Steering();
   private final Dig dig = new Dig();
   private class_746 owner;
   private class_744 saved;
   private Path path;
   private int index;
   private class_243 watch;
   private int watchTicks;
   private int breakTicks;
   public String status = "Idle";
   public boolean sprint = true;

   public void follow(Path path) {
      this.path = path;
      this.index = path == null ? 0 : Math.min(1, path.length() - 1);
      this.watch = null;
      this.watchTicks = 0;
      this.breakTicks = 0;
   }

   public Path path() {
      return this.path;
   }

   public int index() {
      return this.index;
   }

   /** Where the walk has got to, for starting the next search from. */
   public long head() {
      return this.path == null ? 0L : this.path.cells[Math.min(this.index, this.path.length() - 1)];
   }

   public void release() {
      if (this.owner != null && this.owner.field_3913 == this.steering && this.saved != null) {
         this.owner.field_3913 = this.saved;
      }
      this.owner = null;
      this.saved = null;
      this.steering.stop();
   }

   public void stop(class_310 client) {
      this.release();
      this.dig.stop(client);
      this.path = null;
      this.status = "Idle";
   }

   private void hold(class_310 client) {
      class_746 player = client.field_1724;
      if (player != null) {
         if (this.owner != player) {
            this.release();
            this.owner = player;
            this.saved = player.field_3913;
            player.field_3913 = this.steering;
         }
         this.steering.stop();
      }
   }

   public Result tick(class_310 client, Terrain terrain, boolean pauseOnScreen, long tick) {
      class_746 player = client.field_1724;
      if (player == null || client.field_1687 == null || this.path == null) {
         this.release();
         return Result.DONE;
      }
      if (pauseOnScreen && client.field_1755 != null) {
         this.release();
         this.dig.stop(client);
         this.status = "On hold: a screen is open";
         return Result.PAUSED;
      }

      class_243 at = player.method_73189();
      // Step the cursor along every cell already reached, so a sprint across three cells in one
      // tick does not leave the walk aiming at a cell behind the player.
      while (this.index < this.path.length() - 1 && this.reached(player, this.path.cells[this.index])) {
         this.index++;
         this.watchTicks = 0;
      }
      if (this.index >= this.path.length() - 1 && this.reached(player, this.path.last())) {
         this.release();
         this.status = "Arrived";
         return Result.DONE;
      }

      long targetCell = this.path.cells[this.index];
      byte kind = this.path.moves[this.index];
      class_2338 target = class_2338.method_10092(targetCell);

      // Nothing standing still for three seconds is making progress.
      if (this.watch == null || this.watch.method_1025(at) > 0.09) {
         this.watch = at;
         this.watchTicks = 0;
      } else if (++this.watchTicks > 60) {
         this.watch = null;
         this.release();
         this.status = "Stuck at " + target.method_10263() + " " + target.method_10264() + " " + target.method_10260();
         return Result.STUCK;
      }

      if (kind == Path.BREAK && this.clearTheWay(client, target, tick)) {
         return Result.WALKING;
      }
      if (kind == Path.BRIDGE && this.bridge(client, target)) {
         return Result.WALKING;
      }
      this.dig.stop(client);

      this.hold(client);
      this.look(client, player);
      this.steer(player, target, kind, terrain);
      this.status = Path.name(kind) + " to " + target.method_10263() + " " + target.method_10264() + " " + target.method_10260()
         + " (" + (this.path.length() - this.index) + " to go)";
      return Result.WALKING;
   }

   private boolean reached(class_746 player, long cell) {
      class_243 at = player.method_73189();
      double dx = class_2338.method_10061(cell) + 0.5 - at.field_1352;
      double dz = class_2338.method_10083(cell) + 0.5 - at.field_1350;
      double dy = class_2338.method_10071(cell) - at.field_1351;
      return dx * dx + dz * dz < 0.2 && Math.abs(dy) < 0.8;
   }

   /** Breaks whatever sits in the next cell. True while it is still working on it. */
   private boolean clearTheWay(class_310 client, class_2338 target, long tick) {
      this.release();
      for (class_2338 blocking : new class_2338[]{target, target.method_10084()}) {
         if (!client.field_1687.method_8320(blocking).method_26220(client.field_1687, blocking).method_1110()) {
            Dig.Result result = this.dig.tick(client, blocking, tick);
            this.status = "Clearing the way: " + this.dig.status;
            if (result == Dig.Result.BLOCKED && ++this.breakTicks > 40) {
               this.breakTicks = 0;
               return false; // let the walk try, and the watchdog replan if it cannot
            }
            return true;
         }
      }
      this.breakTicks = 0;
      return false;
   }

   /** Throws a block into the gap under the next cell. True while it is still working on it. */
   private boolean bridge(class_310 client, class_2338 target) {
      class_2338 under = target.method_10074();
      if (!client.field_1687.method_8320(under).method_26220(client.field_1687, under).method_1110()) {
         return false;
      }
      this.release();
      String block = null;
      for (String candidate : THROWAWAY) {
         if (Stock.find(client.field_1724, candidate) >= 0) {
            block = candidate;
            break;
         }
      }
      if (block == null) {
         this.status = "Nothing to bridge with: carry cobblestone or dirt";
         return false;
      }
      if (!Stock.select(client, block)) {
         this.status = "Taking out " + Ids.pretty(block);
         return true;
      }
      var hit = Reach.hitFor(client, under, true);
      if (hit == null) {
         this.status = "Cannot reach the gap under " + under.method_10264();
         return false;
      }
      if (!Reach.aimAt(client, hit)) {
         this.status = "Lining up to bridge";
         return true;
      }
      this.status = "Bridging";
      Reach.use(client, hit);
      return true;
   }

   /** Looks along the walk, and at the block when there is one to deal with. */
   private void look(class_310 client, class_746 player) {
      int ahead = Math.min(this.path.length() - 1, this.index + 2);
      class_2338 at = class_2338.method_10092(this.path.cells[ahead]);
      float[] angles = BuilderLook.anglesTo(player, at.method_10263() + 0.5, at.method_10264() + 0.4, at.method_10260() + 0.5);
      BuilderLook.toward(player, angles[0], class_3532.method_15363(angles[1], -35.0F, 45.0F));
   }

   private void steer(class_746 player, class_2338 target, byte kind, Terrain terrain) {
      class_243 at = player.method_73189();
      double dx = target.method_10263() + 0.5 - at.field_1352;
      double dz = target.method_10260() + 0.5 - at.field_1350;
      double flat = Math.hypot(dx, dz);
      if (flat > 1.0E-4) {
         double yaw = Math.toRadians(player.method_36454());
         double sin = Math.sin(yaw);
         double cos = Math.cos(yaw);
         this.steering.side = (float)((dx * cos + dz * sin) / flat);
         this.steering.ahead = (float)((dz * cos - dx * sin) / flat);
      }
      int feetY = class_3532.method_15357(at.field_1351);
      boolean inWater = terrain.water(class_3532.method_15357(at.field_1352), feetY, class_3532.method_15357(at.field_1350));
      // Jump for a step up, and hold jump in water so it swims out instead of sinking.
      this.steering.jump = inWater || kind == Path.JUMP && target.method_10264() > feetY && player.method_24828();
      // Sprint only on a straight clear run: sprinting into a corner overshoots it.
      this.steering.sprint = this.sprint && !inWater && player.method_24828() && this.straightRun() >= 3;
      this.steering.sneak = kind == Path.BRIDGE;
      this.steering.method_3129();
   }

   /** How many plain walking steps come next. */
   private int straightRun() {
      int run = 0;
      for (int i = this.index; i < this.path.length() && run < 4; i++) {
         byte kind = this.path.moves[i];
         if (kind != Path.WALK && kind != Path.DIAGONAL) {
            break;
         }
         run++;
      }
      return run;
   }

   @Environment(EnvType.CLIENT)
   private static final class Steering extends class_744 {
      float ahead;
      float side;
      boolean jump;
      boolean sneak;
      boolean sprint;

      void stop() {
         this.ahead = 0.0F;
         this.side = 0.0F;
         this.jump = false;
         this.sprint = false;
         this.sneak = false;
      }

      boolean moving() {
         return this.ahead != 0.0F || this.side != 0.0F;
      }

      @Override
      public void method_3129() {
         this.field_54155 = new class_10185(
            this.ahead > 0.3F, this.ahead < -0.3F, this.side > 0.3F, this.side < -0.3F, this.jump, this.sneak, this.sprint && !this.sneak
         );
         this.field_55868 = this.moving() ? new class_241(this.side, this.ahead) : class_241.field_1340;
      }

      @Override
      public void method_64054() {
         if (this.moving()) {
            super.method_64054();
         }
      }
   }
}
