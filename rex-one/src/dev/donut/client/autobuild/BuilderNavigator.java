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
import net.minecraft.class_3532;
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
   private static final java.util.Random DRIFT = new java.util.Random();
   private float driftYaw;
   private float driftPitch;
   private java.util.function.Predicate<class_2338> placeable;
   /** When a door was last clicked: the server needs a moment to answer, or a second click undoes it. */
   private long doorClicked;
   /** Where each destination's last search failed from; cleared whenever the world changes. */
   private final java.util.Map<class_2338, BuilderPathfinder.Cell> failedFrom = new java.util.HashMap<>();
   private class_243 watchPos;
   private int watchTicks;
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

   /** Something was built or broken: routes that failed may exist now. */
   public void worldChanged() {
      this.failedFrom.clear();
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
         this.rejectedGoals.add(feetCell(client.field_1724));
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
            this.watchPos = null;
         }

         BuilderPathfinder.Cell feet = feetCell(client.field_1724);
         java.util.function.Predicate<BuilderPathfinder.Cell> arrived = stand
            ? at -> at.equals(cell(destination))
            : at -> this.goal(client, at, destination);
         if (stand ? feet.equals(cell(destination)) && client.field_1724.method_24828() : this.goal(client, feet, destination)) {
            return BuilderNavigator.Result.ARRIVED;
         } else {
            if (this.route.isEmpty() || this.index >= this.route.size()) {
               BuilderPathfinder.Terrain terrain = terrain(client);
               int maxDrop = maxDrop(client.field_1724);
               // The same search from the same cell fails the same way until something is built or
               // broken: a block retried every tick searched thousands of cells twice each time.
               if (feet.equals(this.failedFrom.get(destination))) {
                  return BuilderNavigator.Result.NO_PATH;
               }
               // Spots within the closer walking reach are near when they exist: a short look first.
               this.route = BuilderPathfinder.find(feet, cell(destination), terrain, arrived, stand || this.fullReach ? 6000 : 2500, 96, maxDrop);
               this.index = 0;
               if (this.route.isEmpty() && !stand && !this.fullReach && this.walkReach < 4.25) {
                  this.fullReach = true;
                  this.route = BuilderPathfinder.find(feet, cell(destination), terrain, arrived, 6000, 96, maxDrop);
               }
               if (this.route.isEmpty() && !stand && this.goal(client, feet, destination)) {
                  // With the full reach the spot the player stands on already works: an empty route
                  // here is "you are there", not "no way there". Read as no way, it sent the player
                  // down a pillar to look for a route, then straight back up the same pillar.
                  return BuilderNavigator.Result.ARRIVED;
               }

               if (this.route.isEmpty()) {
                  this.failedFrom.put(destination.method_10062(), feet);
                  return BuilderNavigator.Result.NO_PATH;
               }
            }

            // A watchdog replanning cannot reset: a route planned again every tick (a cell that never
            // reads as open, a step out of reach) kept the stall count at zero while the player stood
            // still for good, on a bed beside a wall.
            class_243 here = client.field_1724.method_73189();
            if (this.watchPos == null || Math.hypot(here.field_1352 - this.watchPos.field_1352, here.field_1350 - this.watchPos.field_1350) > 0.3
               || Math.abs(here.field_1351 - this.watchPos.field_1351) > 0.5) {
               this.watchPos = here;
               this.watchTicks = 0;
            } else if (++this.watchTicks > 60) {
               this.watchPos = null;
               this.rejectCurrent(client);
               return BuilderNavigator.Result.STUCK;
            }
            BuilderPathfinder.Cell next = this.route.get(this.index);
            if (!stillOpen(client, next) || client.field_1724.method_24828() && next.y() > client.field_1724.method_23318() + 1.2) {
               // Something was built into the route since it was planned (often by this builder),
               // or the player fell off it and the next cell is out of jumping reach. Plan again.
               this.replan();
               return BuilderNavigator.Result.MOVING;
            }
            class_2338 door = this.closedDoorAhead(client);
            if (door != null && this.openDoor(client, door)) {
               this.stalled = 0;
               return BuilderNavigator.Result.MOVING;
            }
            class_243 aim = new class_243(next.x() + 0.5, next.y(), next.z() + 0.5);
            class_243 at = client.field_1724.method_73189();
            // Across the ground only: on a slab the feet are half a block under the cell's floor.
            if (Math.pow(at.field_1352 - aim.field_1352, 2) + Math.pow(at.field_1350 - aim.field_1350, 2) < 0.16 && Math.abs(at.field_1351 - aim.field_1351) < 0.6) {
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
               // Eyes on the ground a couple of steps ahead, as a player walking does: the view tips
               // down for a drop and up for a climb instead of holding one fixed angle.
               BuilderPathfinder.Cell ahead = this.route.get(Math.min(this.route.size() - 1, this.index + 2));
               float[] look = BuilderLook.anglesTo(client.field_1724, ahead.x() + 0.5, ahead.y() + 0.3, ahead.z() + 0.5);
               // A slow wander of a few degrees: nobody walks holding the mouse dead still.
               this.driftYaw = class_3532.method_15363(this.driftYaw + (float)(DRIFT.nextGaussian() * 0.35) - this.driftYaw * 0.04F, -3.5F, 3.5F);
               this.driftPitch = class_3532.method_15363(this.driftPitch + (float)(DRIFT.nextGaussian() * 0.25) - this.driftPitch * 0.05F, -2.5F, 2.5F);
               BuilderLook.toward(
                  client.field_1724,
                  (float)Math.toDegrees(Math.atan2(-delta.field_1352, delta.field_1350)) + this.driftYaw,
                  class_3532.method_15363(look[1] + this.driftPitch, 4.0F, 38.0F)
               );
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
            } else if (client.field_1687.method_8320(destination).method_26218(client.field_1687, destination).method_1110()) {
               // Water and the like: nothing to see, only the way there must be clear.
               class_3965 hit = client.field_1687
                  .method_17742(new class_3959(eye, class_243.method_24953(destination), class_3960.field_17559, class_242.field_1348, client.field_1724));
               return hit.method_17783() == class_240.field_1333 || hit.method_17777().equals(destination);
            } else {
               return visibleFace(client, eye, destination, reach) != null;
            }
         }
      }
   }

   /**
    * A face of the block that a click or a dig from this eye would land on, or null: the block's
    * centre first, then the middle of each face, in reach and in plain sight. The centre alone
    * missed blocks whose middle hides behind a neighbour while a face shows, so it walked up to
    * them and found nothing to break, again and again.
    */
   public static class_2350 visibleFace(class_310 client, class_243 eye, class_2338 pos, double reach) {
      class_243 center = class_243.method_24953(pos);
      for (int i = -1; i < 6; i++) {
         class_243 point = i < 0 ? center : center.method_1019(class_243.method_24954(class_2350.values()[i].method_62675()).method_1021(0.499));
         if (eye.method_1025(point) > reach * reach) {
            continue;
         }
         class_3965 hit = client.field_1687.method_17742(new class_3959(eye, point, class_3960.field_17559, class_242.field_1348, client.field_1724));
         if (hit.method_17783() == class_240.field_1332 && hit.method_17777().equals(pos)) {
            return hit.method_17780();
         }
      }
      return null;
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
            class_3965 hit = client.field_1687.method_17742(new class_3959(eye, inside, class_3960.field_17559, class_242.field_1348, client.field_1724));
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

   /**
    * The walking rules over the live world, with every answer kept for the life of this object.
    */
   private static BuilderPathfinder.Terrain terrain(class_310 client) {
               // Each answer is kept for the length of the search: the pathfinder asks about the same
               // cells again and again (every neighbour test, every drop), and these checks were
               // nearly all the builder's time on a cabin, with ticks of a quarter second.
               java.util.Map<BuilderPathfinder.Cell, Boolean> standCache = new java.util.HashMap<>();
               java.util.Map<BuilderPathfinder.Cell, Boolean> headCache = new java.util.HashMap<>();
               return new BuilderPathfinder.Terrain() {
                  @Override
                  public boolean standable(BuilderPathfinder.Cell at) {
                     Boolean known = standCache.get(at);
                     if (known == null) {
                        known = this.checkStandable(at);
                        standCache.put(at, known);
                     }
                     return known;
                  }

                  private boolean checkStandable(BuilderPathfinder.Cell at) {
                     class_2338 pos = BuilderNavigator.pos(at);
                     if (MaterialTracker.isLoaded(client.field_1687, pos)
                        && MaterialTracker.isLoaded(client.field_1687, pos.method_10086(2))) {
                        class_2680 ground = client.field_1687.method_8320(pos.method_10074());
                        return !ground.method_27852(class_2246.field_10092)
                              && !ground.method_27852(class_2246.field_17350)
                              && !ground.method_27852(class_2246.field_23860)
                              && !ground.method_27852(class_2246.field_10029)
                              && !ground.method_27852(class_2246.field_27879)
                              && solidFooting(client, ground, pos.method_10074())
                           ? this.headroom(at)
                           : false;
                     } else {
                        return false;
                     }
                  }

                  @Override
                  public boolean headroom(BuilderPathfinder.Cell at) {
                     Boolean known = headCache.get(at);
                     if (known == null) {
                        known = this.checkHeadroom(at);
                        headCache.put(at, known);
                     }
                     return known;
                  }

                  private boolean checkHeadroom(BuilderPathfinder.Cell at) {
                     class_2338 pos = BuilderNavigator.pos(at);
                     if (MaterialTracker.isLoaded(client.field_1687, pos)
                        && (client.field_1687.method_8316(pos).method_15769() || client.field_1687.method_8320(pos).method_27852(class_2246.field_10382))
                        && client.field_1687.method_8316(pos.method_10084()).method_15769()) {
                        // Water at the feet with the head dry is a shallow stream, waded through like
                        // a player does: the water floor of a cactus farm cut the builder off from half
                        // the farm. Deeper water (head under) and lava stay off the route.
                        class_2680 state = client.field_1687.method_8320(pos);
                        return !state.method_27852(class_2246.field_10036)
                              && !state.method_27852(class_2246.field_22089)
                              && !state.method_27852(class_2246.field_16999)
                              && !state.method_27852(class_2246.field_27879)
                           ? bodyFits(client, at)
                           : false;
                     } else {
                        return false;
                     }
                  }
               };
   }

   /**
    * Every block position the player can walk to from where they stand, within a budget. One
    * flood answers "can I get there" for many spots at once, where trying each spot with its own
    * search explored the whole budget for every spot that could not be reached.
    */
   public Set<class_2338> reachable(class_310 client, int budget) {
      Set<class_2338> out = new HashSet<>();
      if (client.field_1724 == null || client.field_1687 == null) {
         return out;
      }
      for (BuilderPathfinder.Cell at : BuilderPathfinder.explore(feetCell(client.field_1724), terrain(client), budget, 96, maxDrop(client.field_1724))) {
         out.add(pos(at));
      }
      return out;
   }

   /**
    * Ground the player can stand on: a full block, or one with a top at least half height across the
    * whole square, as slabs, stairs, soul sand, farmland and path blocks have. Full blocks only made
    * the walkways of a nether wart farm into islands, and left a slab roof with nowhere to stand.
    */
   private static boolean solidFooting(class_310 client, class_2680 ground, class_2338 pos) {
      if (ground.method_26234(client.field_1687, pos)) {
         return true;
      }
      class_265 shape = ground.method_26220(client.field_1687, pos);
      if (shape.method_1110()) {
         return false;
      }
      class_238 box = shape.method_1107();
      // Half-height ground only for slabs and stairs, built to be walked on; beds and the like are
      // furniture to go round (walked across, a bed confused where the feet were and the player
      // paced back and forth on it).
      boolean walkway = ground.method_26204() instanceof net.minecraft.class_2482 || ground.method_26204() instanceof net.minecraft.class_2510;
      return box.field_1325 >= (walkway ? 0.5 : 0.8) && box.field_1323 <= 0.0 && box.field_1321 <= 0.0 && box.field_1320 >= 1.0 && box.field_1324 >= 1.0;
   }

   /** Whether a planned route cell still has room for the player's body. */
   private static boolean stillOpen(class_310 client, BuilderPathfinder.Cell at) {
      class_2338 pos = pos(at);
      if (!MaterialTracker.isLoaded(client.field_1687, pos)) {
         return true;
      }
      // The same body test the pathfinder's headroom check uses.
      return bodyFits(client, at);
   }

   /**
    * Whether the player's body (a 0.6-wide, 1.8-tall box in the middle of the cell) clears the
    * collision boxes of the two blocks it would be in. The world's general collision query also
    * walks entities and the world border, and cost far more for the same answer here.
    */
   private static boolean bodyFits(class_310 client, BuilderPathfinder.Cell at) {
      for (int dy = 0; dy <= 1; dy++) {
         class_2338 pos = new class_2338(at.x(), at.y() + dy, at.z());
         class_2680 state = client.field_1687.method_8320(pos);
         if (openable(state)) {
            // Opened on the way through, as a player does.
            continue;
         }
         class_265 shape = state.method_26220(client.field_1687, pos);
         if (shape.method_1110()) {
            continue;
         }
         double bottom = dy == 0 ? 0.01 : 0.0;
         double top = dy == 0 ? 1.0 : 0.8;
         for (class_238 box : shape.method_1090()) {
            if (box.field_1320 > 0.2 && box.field_1323 < 0.8 && box.field_1324 > 0.2 && box.field_1321 < 0.8 && box.field_1325 > bottom && box.field_1322 < top) {
               return false;
            }
            // A door, closed trapdoor or gate: a thin panel right across one edge. The body fits beside
            // it, but nobody walks through it, and the player hopped against a closed door forever.
            boolean acrossX = box.field_1323 <= 0.05 && box.field_1320 >= 0.95;
            boolean acrossZ = box.field_1321 <= 0.05 && box.field_1324 >= 0.95;
            if ((acrossX || acrossZ) && box.field_1325 > bottom && box.field_1322 < top && box.field_1325 - box.field_1322 > 0.5) {
               return false;
            }
         }
      }
      return true;
   }

   /** A door that opens by hand, or a fence gate. */
   private static boolean openable(class_2680 state) {
      return state.method_26204() instanceof net.minecraft.class_2323 && net.minecraft.class_2323.method_24796(state)
         || state.method_26204() instanceof net.minecraft.class_2349;
   }

   private static boolean closed(class_2680 state) {
      return state.method_26204() instanceof net.minecraft.class_2323
         ? !state.method_11654(net.minecraft.class_2323.field_10945)
         : !state.method_11654(net.minecraft.class_2349.field_11026);
   }

   /** A closed door or gate in the next two route cells, or null. */
   private class_2338 closedDoorAhead(class_310 client) {
      for (int i = this.index; i < Math.min(this.route.size(), this.index + 2); i++) {
         BuilderPathfinder.Cell at = this.route.get(i);
         for (int dy = 0; dy <= 1; dy++) {
            class_2338 pos = new class_2338(at.x(), at.y() + dy, at.z());
            class_2680 state = client.field_1687.method_8320(pos);
            if (openable(state) && closed(state)) {
               // A door opens from either half; click the lower one.
               return state.method_26204() instanceof net.minecraft.class_2323 && dy == 1 && openable(client.field_1687.method_8320(pos.method_10074())) ? pos.method_10074() : pos;
            }
         }
      }
      return null;
   }

   /** Turns to the door and opens it. True while that holds up the walk. */
   private boolean openDoor(class_310 client, class_2338 door) {
      class_746 player = client.field_1724;
      class_243 center = class_243.method_24953(door);
      if (player.method_33571().method_1025(center) > 3.5 * 3.5) {
         return false;
      }
      this.steering.side = 0.0F;
      this.steering.ahead = 0.0F;
      this.steering.jump = false;
      this.steering.sprint = false;
      this.steering.method_3129();
      this.clickDoor(client, door);
      return true;
   }

   /** Turns to the door and, once facing it, clicks it. True on the tick it was clicked. */
   public boolean clickDoor(class_310 client, class_2338 door) {
      class_746 player = client.field_1724;
      class_243 center = class_243.method_24953(door);
      float[] look = BuilderLook.anglesTo(player, center.field_1352, center.field_1351, center.field_1350);
      if (!BuilderLook.toward(player, look[0], look[1]) || System.currentTimeMillis() - this.doorClicked < 700L) {
         return false;
      }
      class_243 from = player.method_33571().method_1020(center);
      class_2350 side = Math.abs(from.field_1352) > Math.abs(from.field_1350)
         ? (from.field_1352 > 0 ? class_2350.field_11034 : class_2350.field_11039)
         : (from.field_1350 > 0 ? class_2350.field_11035 : class_2350.field_11043);
      client.field_1761.method_2896(player, net.minecraft.class_1268.field_5808, new class_3965(center, side, door, false));
      player.method_6104(net.minecraft.class_1268.field_5808);
      this.doorClicked = System.currentTimeMillis();
      return true;
   }

   /**
    * The cell the player stands in. On a slab, stair or soul sand the feet are partway into the
    * block below that cell, so on the ground the height is rounded rather than floored.
    */
   private static BuilderPathfinder.Cell feetCell(class_746 player) {
      double y = player.method_24828() ? Math.floor(player.method_23318() + 0.5) : Math.floor(player.method_23318());
      return new BuilderPathfinder.Cell(class_3532.method_15357(player.method_23317()), (int)y, class_3532.method_15357(player.method_23321()));
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
