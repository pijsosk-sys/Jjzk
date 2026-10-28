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

   public void hold(class_310 client, boolean sneak) {
      if (client.field_1724 != null) {
         if (this.owner != client.field_1724) {
            this.release();
            this.owner = client.field_1724;
            this.saved = this.owner.field_3913;
            this.owner.field_3913 = this.steering;
         }

         this.steering.forward = false;
         this.steering.jump = false;
         this.steering.sneak = sneak;
         this.steering.method_3129();
      }
   }

   public void release() {
      if (this.owner != null && this.owner.field_3913 == this.steering && this.saved != null) {
         this.owner.field_3913 = this.saved;
      }

      this.owner = null;
      this.saved = null;
      this.steering.forward = false;
      this.steering.jump = false;
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
      if (client.field_1687 != null && client.field_1724 != null) {
         this.hold(client, false);
         if (!destination.equals(this.target)) {
            this.target = destination.method_10062();
            this.rejectedGoals.clear();
            this.replan();
         }

         BuilderPathfinder.Cell feet = cell(client.field_1724.method_24515());
         if (this.goal(client, feet, destination)) {
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
               this.route = BuilderPathfinder.find(
                  feet, cell(destination), terrain, at -> this.goal(client, at, destination), 6000, 96
               );
               this.index = 0;
               if (this.route.isEmpty()) {
                  return BuilderNavigator.Result.NO_PATH;
               }
            }

            BuilderPathfinder.Cell next = this.route.get(this.index);
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
            client.field_1724.method_36456((float)Math.toDegrees(Math.atan2(-delta.field_1352, delta.field_1350)));
            client.field_1724.method_36457(10.0F);
            this.steering.forward = true;
            this.steering.jump = next.y() > client.field_1724.method_23318() + 0.25 && client.field_1724.method_24828();
            this.steering.method_3129();
            if (this.lastPosition != null && client.field_1724.method_73189().method_1025(this.lastPosition) < 4.0E-4) {
               this.stalled++;
            } else {
               this.stalled = 0;
            }

            this.lastPosition = client.field_1724.method_73189();
            if (this.stalled > 30) {
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

            double reach = Math.min(4.25, client.field_1724.method_55754() - 0.25);
            if (eye.method_1025(class_243.method_24953(destination)) > reach * reach) {
               return false;
            } else {
               class_3965 hit = client.field_1687
                  .method_17742(new class_3959(eye, class_243.method_24953(destination), class_3960.field_17558, class_242.field_1348, client.field_1724));
               return hit.method_17783() == class_240.field_1333 || hit.method_17777().equals(destination);
            }
         }
      }
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
      boolean forward;
      boolean jump;
      boolean sneak;

      public void method_3129() {
         this.field_54155 = new class_10185(this.forward, false, false, false, this.jump, this.sneak, false);
         this.field_55868 = this.forward ? new class_241(0.0F, 1.0F) : class_241.field_1340;
      }

      public void method_64054() {
         if (this.forward) {
            super.method_64054();
         }
      }
   }
}
