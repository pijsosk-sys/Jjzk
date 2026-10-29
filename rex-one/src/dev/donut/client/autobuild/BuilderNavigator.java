package dev.donut.client.autobuild;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_10185;
import net.minecraft.class_2246;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_238;
import net.minecraft.class_265;
import net.minecraft.class_241;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_310;
import net.minecraft.class_3959;
import net.minecraft.class_3965;
import net.minecraft.class_744;
import net.minecraft.class_746;
import net.minecraft.class_239.class_240;
import net.minecraft.class_3959.class_242;
import net.minecraft.class_3959.class_3960;
import rex.BuildTempo;

@Environment(EnvType.CLIENT)
public final class BuilderNavigator {
   private class_746 owner;
   private class_744 saved;
   private final BuilderNavigator.SteeringInput steering = new BuilderNavigator.SteeringInput();
   private List<BuilderPathfinder.Cell> route = List.of();
   private int index;
   private int stalled;
   private class_243 lastPosition;
   private class_2338 target;
   private final Set<BuilderPathfinder.Cell> rejectedGoals = new HashSet<>();
   // How close to walk to a block before stopping. Closer than the full reach leaves more of the
   // next blocks in reach too; if no spot that close exists the full reach is used for that block.
   private double walkReach = 4.25;
   private boolean fullReach;
   private boolean standing;
   private java.util.function.Predicate<class_2338> placeable;
   private int progressIndex = -1;
   private double progressGap = Double.POSITIVE_INFINITY;

   /**
    * The final say on whether the block can be placed with the feet on a cell, from the builder,
    * which runs its real planner as if the player stood there. Face visibility alone let it walk
    * to spots where a piston or stair would come out facing the wrong way.
    */
   public void placeableFrom(java.util.function.Predicate<class_2338> check) {
      this.placeable = check;
   }

   public void walkReach(double reach) {
      if (reach != this.walkReach) {
         this.walkReach = reach;
         this.replan();
      }
   }

   public void hold(class_310 client, boolean sneak) {
      if (client.field_1724 != null) {
         if (this.owner != client.field_1724) {
            this.release();
            this.owner = client.field_1724;
            this.saved = this.owner.field_3913;
            this.owner.field_3913 = this.steering;
         }

         this.steering.stop();
         this.steering.sneak = sneak;
         this.steering.method_3129();
      }
   }

   /**
    * Sneaks toward the middle of the block the player stands on, steering relative to the view.
    * Returns true once within a few hundredths of it. Pillaring needs this: a player standing off
    * centre overlaps the neighbouring column and hits their head on it when jumping.
    */
   public boolean centre(class_310 client) {
      this.hold(client, true);
      class_746 player = client.field_1724;
      // The middle of the block actually underfoot: at an edge the player's block position can be
      // an open cell beside the one holding them up, and sneaking will not step off toward it.
      double px = player.method_23317();
      double pz = player.method_23321();
      double cx = Math.floor(px) + 0.5;
      double cz = Math.floor(pz) + 0.5;
      double best = Double.POSITIVE_INFINITY;
      int below = (int)Math.floor(player.method_23318() - 0.01);
      for (double ox : new double[]{-0.3, 0.0, 0.3}) {
         for (double oz : new double[]{-0.3, 0.0, 0.3}) {
            class_2338 ground = class_2338.method_49638(new class_243(px + ox, below, pz + oz));
            if (!client.field_1687.method_8320(ground).method_26220(client.field_1687, ground).method_1110()) {
               double gx = ground.method_10263() + 0.5;
               double gz = ground.method_10260() + 0.5;
               double d = Math.hypot(gx - px, gz - pz);
               if (d < best) {
                  best = d;
                  cx = gx;
                  cz = gz;
               }
            }
         }
      }
      double dx = cx - px;
      double dz = cz - pz;
      double gap = Math.hypot(dx, dz);
      if (gap < 0.12) {
         return true;
      }
      double yaw = Math.toRadians(player.method_36454());
      double sin = Math.sin(yaw);
      double cos = Math.cos(yaw);
      this.steering.side = (float)((dx * cos + dz * sin) / gap);
      this.steering.ahead = (float)((dz * cos - dx * sin) / gap);
      this.steering.method_3129();
      return false;
   }

   public void release() {
      if (this.owner != null && this.owner.field_3913 == this.steering && this.saved != null) {
         this.owner.field_3913 = this.saved;
      }

      this.owner = null;
      this.saved = null;
      this.steering.stop();
      this.steering.sneak = false;
      this.route = List.of();
      this.index = 0;
      this.stalled = 0;
      this.lastPosition = null;
   }

   public void replan() {
      this.route = List.of();
      this.index = 0;
      this.stalled = 0;
      this.progressIndex = -1;
      this.progressGap = Double.POSITIVE_INFINITY;
      this.lastPosition = null;
   }

   public void reset() {
      this.release();
      this.target = null;
      this.rejectedGoals.clear();
   }

   public void rejectCurrent(class_310 client) {
      if (client.field_1724 != null) {
         this.rejectedGoals.add(cell(client.field_1724.method_24515()));
      }

      this.replan();
   }

   public int routeLength() {
      return Math.max(0, this.route.size() - this.index);
   }

   public BuilderNavigator.Result moveTo(class_310 client, class_2338 destination) {
      return this.travel(client, destination, false);
   }

   /** Walks until the feet are on exactly this cell. */
   public BuilderNavigator.Result standAt(class_310 client, class_2338 feet) {
      return this.travel(client, feet, true);
   }

   private BuilderNavigator.Result travel(class_310 client, class_2338 destination, boolean stand) {
      if (client.field_1687 != null && client.field_1724 != null) {
         this.hold(client, false);
         if (!destination.equals(this.target) || stand != this.standing) {
            this.standing = stand;
            this.target = destination.method_10062();
            this.rejectedGoals.clear();
            this.fullReach = false;
            this.replan();
         }

         BuilderPathfinder.Cell feet = cell(client.field_1724.method_24515());
         java.util.function.Predicate<BuilderPathfinder.Cell> arrived = stand
            ? at -> at.equals(cell(destination))
            : at -> this.goal(client, at, destination);
         if (stand ? feet.equals(cell(destination)) && client.field_1724.method_24828() : this.goal(client, feet, destination)) {
            return BuilderNavigator.Result.ARRIVED;
         } else {
            if (this.route.isEmpty() || this.index >= this.route.size()) {
               BuilderPathfinder.Terrain terrain = new BuilderPathfinder.Terrain() {
                  @Override
                  public boolean standable(BuilderPathfinder.Cell at) {
                     class_2338 pos = BuilderNavigator.pos(at);
                     if (MaterialTracker.isLoaded(client.field_1687, pos)
                        && MaterialTracker.isLoaded(client.field_1687, pos.method_10086(2))) {
                        class_2680 ground = client.field_1687.method_8320(pos.method_10074());
                        return !ground.method_27852(class_2246.field_10092)
                              && !ground.method_27852(class_2246.field_17350)
                              && !ground.method_27852(class_2246.field_23860)
                              && !ground.method_27852(class_2246.field_10029)
                              && !ground.method_27852(class_2246.field_27879)
                              && ground.method_26234(client.field_1687, pos.method_10074())
                           ? this.headroom(at)
                           : false;
                     } else {
                        return false;
                     }
                  }

                  @Override
                  public boolean headroom(BuilderPathfinder.Cell at) {
                     class_2338 pos = BuilderNavigator.pos(at);
                     if (MaterialTracker.isLoaded(client.field_1687, pos)
                        && client.field_1687.method_8316(pos).method_15769()
                        && client.field_1687.method_8316(pos.method_10084()).method_15769()) {
                        class_2680 state = client.field_1687.method_8320(pos);
                        return !state.method_27852(class_2246.field_10036)
                              && !state.method_27852(class_2246.field_22089)
                              && !state.method_27852(class_2246.field_16999)
                              && !state.method_27852(class_2246.field_27879)
                           ? client.field_1687
                              .method_8587(
                                 client.field_1724, new class_238(at.x() + 0.2, at.y() + 0.01, at.z() + 0.2, at.x() + 0.8, at.y() + 1.8, at.z() + 0.8)
                              )
                           : false;
                     } else {
                        return false;
                     }
                  }
               };
               int maxDrop = maxDrop(client.field_1724);
               this.route = BuilderPathfinder.find(feet, cell(destination), terrain, arrived, 6000, 96, maxDrop);
               this.index = 0;
               if (this.route.isEmpty() && !stand && !this.fullReach && this.walkReach < 4.25) {
                  this.fullReach = true;
                  this.route = BuilderPathfinder.find(feet, cell(destination), terrain, arrived, 6000, 96, maxDrop);
               }

               if (this.route.isEmpty()) {
                  return BuilderNavigator.Result.NO_PATH;
               }
            }

            BuilderPathfinder.Cell next = this.route.get(this.index);
            if (!stillOpen(client, next) || client.field_1724.method_24828() && next.y() > client.field_1724.method_23318() + 1.2) {
               // Something was built into the route since it was planned (often by this builder),
               // or the player fell off it and the next cell is out of jumping reach. Plan again.
               this.replan();
               return BuilderNavigator.Result.MOVING;
            }
            class_243 aim = new class_243(next.x() + 0.5, next.y(), next.z() + 0.5);
            if (client.field_1724.method_73189().method_1025(aim) < 0.16) {
               this.index++;
               if (this.index >= this.route.size()) {
                  this.replan();
                  return BuilderNavigator.Result.MOVING;
               }

               next = this.route.get(this.index);
               aim = new class_243(next.x() + 0.5, next.y(), next.z() + 0.5);
            }

            class_243 delta = aim.method_1020(client.field_1724.method_73189());
            double flat = Math.hypot(delta.field_1352, delta.field_1350);
            int left = this.route.size() - this.index;
            // Look the way a player would: along the walk while there is a way to go, at the block
            // for the last couple of steps, so the aim is mostly done on arrival.
            if (left > 2 && flat > 1.0E-4) {
               BuilderLook.toward(client.field_1724, (float)Math.toDegrees(Math.atan2(-delta.field_1352, delta.field_1350)), 12.0F);
            } else {
               float[] look = BuilderLook.anglesTo(
                  client.field_1724, destination.method_10263() + 0.5, destination.method_10264() + 0.5, destination.method_10260() + 0.5
               );
               BuilderLook.toward(client.field_1724, look[0], look[1]);
            }
            // Steer toward the next cell relative to wherever the view points now, so the path is
            // followed exactly while the view is still turning.
            double yaw = Math.toRadians(client.field_1724.method_36454());
            double sin = Math.sin(yaw);
            double cos = Math.cos(yaw);
            double dx = flat > 1.0E-4 ? delta.field_1352 / flat : 0.0;
            double dz = flat > 1.0E-4 ? delta.field_1350 / flat : 0.0;
            this.steering.side = (float)(dx * cos + dz * sin);
            this.steering.ahead = (float)(dz * cos - dx * sin);
            this.steering.jump = next.y() > client.field_1724.method_23318() + 0.25 && client.field_1724.method_24828();
            // No sprinting into a drop or a climb: a sprint overshoots a narrow landing.
            boolean level = true;
            for (int i = this.index; i < Math.min(this.route.size(), this.index + 3); i++) {
               level &= this.route.get(i).y() == Math.floor(client.field_1724.method_23318() + 0.5);
            }
            this.steering.sprint = BuildTempo.sprint() && left > 2 && level && this.steering.ahead > 0.85F;
            this.steering.method_3129();
            // Progress means getting closer to the next cell across the ground. Jumping in place
            // against a wall changes the height, and pushing against it jitters sideways; both used
            // to count as movement, so a blocked player hopped there forever.
            class_243 now = client.field_1724.method_73189();
            double gap = Math.hypot(aim.field_1352 - now.field_1352, aim.field_1350 - now.field_1350);
            if (this.index != this.progressIndex || gap < this.progressGap - 0.05) {
               this.progressIndex = this.index;
               this.progressGap = gap;
               this.stalled = 0;
            } else {
               this.stalled++;
            }

            this.lastPosition = client.field_1724.method_73189();
            if (this.stalled > 40) {
               this.rejectCurrent(client);
               return BuilderNavigator.Result.STUCK;
            } else {
               return BuilderNavigator.Result.MOVING;
            }
         }
      } else {
         return BuilderNavigator.Result.NO_PATH;
      }
   }

   private boolean goal(class_310 client, BuilderPathfinder.Cell at, class_2338 destination) {
      if (this.rejectedGoals.contains(at)) {
         return false;
      } else {
         class_243 eye = new class_243(at.x() + 0.5, at.y() + client.field_1724.method_5751(), at.z() + 0.5);
         if (at.x() == destination.method_10263()
            && at.z() == destination.method_10260()
            && at.y() <= destination.method_10264()
            && at.y() + 1.8 > destination.method_10264()) {
            return false;
         } else {
            if (client.field_1687.method_8320(destination).method_45474() && eye.field_1351 <= destination.method_10264() + 0.01) {
               boolean alternativeFace = false;

               for (class_2350 direction : class_2350.values()) {
                  if (direction != class_2350.field_11033) {
                     class_2338 neighbor = destination.method_10093(direction);
                     if (MaterialTracker.isLoaded(client.field_1687, neighbor)) {
                        class_2680 state = client.field_1687.method_8320(neighbor);
                        if (!state.method_45474() && !state.method_26220(client.field_1687, neighbor).method_1110()) {
                           alternativeFace = true;
                           break;
                        }
                     }
                  }
               }

               if (!alternativeFace) {
                  return false;
               }
            }

            double reach = Math.min(this.fullReach ? 4.25 : this.walkReach, client.field_1724.method_55754() - 0.25);
            if (eye.method_1025(class_243.method_24953(destination)) > reach * reach) {
               return false;
            } else if (client.field_1687.method_8320(destination).method_26215()) {
               // An empty spot is only reachable from here if some face it can be placed against is in
               // reach and in sight. Distance alone sent it to spots that show every such face its back
               // (an eave seen from the roof above it), where it arrived, found nothing and walked on.
               return canClickFrom(client, eye, destination, reach) && (this.placeable == null || this.placeable.test(pos(at)));
            } else {
               class_3965 hit = client.field_1687
                  .method_17742(new class_3959(eye, class_243.method_24953(destination), class_3960.field_17558, class_242.field_1348, client.field_1724));
               return hit.method_17783() == class_240.field_1333 || hit.method_17777().equals(destination);
            }
         }
      }
   }

   private static final double[][] FACE_POINTS = {{0.5, 0.5}, {0.2, 0.2}, {0.8, 0.2}, {0.2, 0.8}, {0.8, 0.8}};

   /**
    * True when, from this eye, a block placed into the empty spot has something to be clicked on:
    * a solid neighbour whose face toward the spot is in reach and in plain sight, sampled at its
    * centre and four points round it, the same test the server's reach check and a real click need.
    */
   public static boolean canClickFrom(class_310 client, class_243 eye, class_2338 destination, double reach) {
      for (class_2350 direction : class_2350.values()) {
         class_2338 neighbor = destination.method_10093(direction);
         if (!MaterialTracker.isLoaded(client.field_1687, neighbor)) {
            continue;
         }
         class_2680 state = client.field_1687.method_8320(neighbor);
         if (state.method_45474()) {
            continue;
         }
         class_265 shape = state.method_26218(client.field_1687, neighbor);
         if (shape.method_1110()) {
            continue;
         }
         class_238 box = shape.method_1107().method_996(neighbor);
         class_2350.class_2351 axis = direction.method_10166();
         boolean positive = direction.method_10171() == class_2350.class_2352.field_11056;
         // The clicked face is the neighbour's side toward the spot; it is only visible from that side.
         double plane = positive ? box.method_1001(axis) : box.method_990(axis);
         double coordinate = eye.method_18043(axis);
         if (positive ? coordinate > plane - 0.02 : coordinate < plane + 0.02) {
            continue;
         }
         class_2350.class_2351 first = axis == class_2350.class_2351.field_11048 ? class_2350.class_2351.field_11052 : class_2350.class_2351.field_11048;
         class_2350.class_2351 second = axis == class_2350.class_2351.field_11051 ? class_2350.class_2351.field_11052 : class_2350.class_2351.field_11051;
         for (double[] uv : FACE_POINTS) {
            double a = box.method_1001(first) + (box.method_990(first) - box.method_1001(first)) * uv[0];
            double b = box.method_1001(second) + (box.method_990(second) - box.method_1001(second)) * uv[1];
            double[] xyz = new double[3];
            xyz[axis.ordinal()] = plane;
            xyz[first.ordinal()] = a;
            xyz[second.ordinal()] = b;
            class_243 point = new class_243(xyz[0], xyz[1], xyz[2]);
            if (eye.method_1025(point) > reach * reach) {
               continue;
            }
            class_243 inside = point.method_1019(class_243.method_24954(direction.method_62675()).method_1021(0.002));
            class_3965 hit = client.field_1687.method_17742(new class_3959(eye, inside, class_3960.field_17558, class_242.field_1348, client.field_1724));
            if (hit.method_17783() == class_240.field_1332 && hit.method_17777().equals(neighbor) && hit.method_17780() == direction.method_10153()) {
               return true;
            }
         }
      }
      return false;
   }

   /**
    * The deepest drop worth taking. Three blocks never hurt; in creative there is no fall damage;
    * in survival up to three more blocks while health stays well clear of the low-health pause
    * (each block past three costs half a heart).
    */
   private static int maxDrop(class_746 player) {
      if (player.method_31549().field_7480 || player.method_31549().field_7477) {
         return 12;
      }
      int spare = (int)Math.floor((player.method_6032() - 12.0F) / 2.0F);
      return 3 + Math.max(0, Math.min(3, spare));
   }

   /** Whether a planned route cell still has room for the player's body. */
   private static boolean stillOpen(class_310 client, BuilderPathfinder.Cell at) {
      class_2338 pos = pos(at);
      if (!MaterialTracker.isLoaded(client.field_1687, pos)) {
         return true;
      }
      // The same body box the pathfinder's headroom test uses.
      return client.field_1687.method_8587(client.field_1724, new class_238(at.x() + 0.2, at.y() + 0.01, at.z() + 0.2, at.x() + 0.8, at.y() + 1.8, at.z() + 0.8));
   }

   private static BuilderPathfinder.Cell cell(class_2338 p) {
      return new BuilderPathfinder.Cell(p.method_10263(), p.method_10264(), p.method_10260());
   }

   private static class_2338 pos(BuilderPathfinder.Cell p) {
      return new class_2338(p.x(), p.y(), p.z());
   }

   @Environment(EnvType.CLIENT)
   public static enum Result {
      MOVING,
      ARRIVED,
      NO_PATH,
      STUCK;
   }

   @Environment(EnvType.CLIENT)
   private static final class SteeringInput extends class_744 {
      /** Movement relative to the view: ahead is forward (+) or back, side is left (+) or right. */
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
      }

      boolean moving() {
         return this.ahead != 0.0F || this.side != 0.0F;
      }

      public void method_3129() {
         this.field_54155 = new class_10185(
            this.ahead > 0.3F, this.ahead < -0.3F, this.side > 0.3F, this.side < -0.3F, this.jump, this.sneak, this.sprint && !this.sneak
         );
         this.field_55868 = this.moving() ? new class_241(this.side, this.ahead) : class_241.field_1340;
      }

      public void method_64054() {
         if (this.moving()) {
            super.method_64054();
         }
      }
   }
}
