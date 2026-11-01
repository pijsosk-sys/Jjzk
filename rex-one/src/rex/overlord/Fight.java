package rex.overlord;

import dev.donut.client.autobuild.BuilderLook;
import dev.donut.client.autobuild.BuilderNavigator;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_1268;
import net.minecraft.class_1297;
import net.minecraft.class_1588;
import net.minecraft.class_310;
import net.minecraft.class_746;

/**
 * Staying alive while the rest of Overlord works: dealing with anything hostile that comes near,
 * and eating before the hunger bar costs health. Both take over the tick, so the progression never
 * walks on while a zombie is chewing on it.
 *
 * <p>It walks up to what it is fighting. The old version engaged anything inside a box three and a
 * half blocks wide, which reaches further than a sword does, so it would stand in front of a
 * zombie swinging at nothing: not moving, not hitting, until something else killed it. Now the
 * sword's own reach decides whether to swing or to close the gap, and a mob that cannot be got at
 * in twenty seconds is left alone for half a minute so the rest of the work carries on.
 */
@Environment(EnvType.CLIENT)
public final class Fight {
   /** Food Overlord may end up carrying, best first. */
   private static final List<String> FOOD = List.of(
      "cooked_beef", "cooked_porkchop", "cooked_chicken", "cooked_mutton", "bread", "baked_potato", "apple", "beef", "porkchop", "chicken"
   );
   /** Vanilla entity reach: closer than this and a swing lands. */
   private static final double ATTACK = 3.0;
   /** Hostiles this close are dealt with before anything else. */
   private static final double ENGAGE = 7.0;
   /** Once in melee it stays in melee out to here, so it does not flutter across the boundary. */
   private static final double DISENGAGE = 4.2;
   /** Swords, best first. */
   private static final List<String> SWORDS = List.of("netherite_sword", "diamond_sword", "iron_sword", "stone_sword", "wooden_sword", "golden_sword");

   /** Its own movement input: circling, backing off and jumping for a critical all need one. */
   private final Steering steering = new Steering();
   private class_746 owner;
   private net.minecraft.class_744 saved;
   private int strafeFlip;
   private int strafe = 1;
   private net.minecraft.class_243 closing;
   private boolean inMelee;

   private boolean eating;
   private class_1297 threat;
   private int threatTicks;
   /** Mobs that could not be got at, and the tick they may be looked at again. */
   private final java.util.Map<Integer, Long> ignored = new java.util.HashMap<>();
   public String status = "Clear";

   /** What it is fighting, for the marker drawn in the world. */
   public class_1297 threat() {
      return this.threat != null && this.threat.method_5805() ? this.threat : null;
   }

   public void stop(class_310 client) {
      if (this.eating && client.field_1690 != null) {
         client.field_1690.field_1904.method_23481(false);
      }
      this.eating = false;
      this.letGo();
   }

   private void letGo() {
      if (this.owner != null && this.owner.field_3913 == this.steering && this.saved != null) {
         this.owner.field_3913 = this.saved;
      }
      this.owner = null;
      this.saved = null;
      this.steering.stop();
   }

   private void hold(class_746 player) {
      if (this.owner != player) {
         this.letGo();
         this.owner = player;
         this.saved = player.field_3913;
         player.field_3913 = this.steering;
      }
      this.steering.stop();
   }

   /** True when this tick belongs to eating or fighting rather than to the progression. */
   public boolean tick(class_310 client, BuilderNavigator navigator, Map<String, Integer> stock, long tick) {
      class_746 player = client.field_1724;
      if (player == null || client.field_1687 == null) {
         this.stop(client);
         return false;
      }
      if (this.eat(client, player, stock)) {
         return true;
      }
      this.threat = this.pick(client, player, tick);
      if (this.threat == null) {
         this.threatTicks = 0;
         this.inMelee = false;
         this.letGo();
         this.status = "Clear";
         return false;
      }
      double distance = Math.sqrt(player.method_5858(this.threat));
      // Hysteresis: having closed to sword range, stay there rather than flutter in and out of
      // the approach every time knockback pushes the gap over three blocks.
      double engageAt = this.inMelee ? DISENGAGE : ATTACK;
      // A mob that cannot be got at in twenty seconds is left alone for a while, or the whole
      // progression stands in front of a zombie behind a wall for ever.
      if (++this.threatTicks > 400) {
         this.ignored.put(this.threat.method_5628(), tick + 600L);
         this.threat = null;
         this.threatTicks = 0;
         this.status = "Leaving that one alone";
         return false;
      }
      String name = this.threat.method_5477().getString();
      if (distance > engageAt) {
         this.inMelee = false;
         // Straight at it, steered by hand. Standing still just out of reach, swinging at
         // nothing, was the old behaviour: the box test said "within range" while the server said
         // "too far". A route search is not wanted here either - a mob seven blocks away is in
         // sight, and asking the navigator for a path to it cost sixty milliseconds in a tick.
         navigator.release();
         this.hold(player);
         float[] toward = BuilderLook.anglesTo(player, this.threat.method_23317(), this.threat.method_23320() - 0.2, this.threat.method_23321());
         BuilderLook.toward(player, toward[0], toward[1]);
         this.steering.ahead = 1.0F;
         this.steering.sprint = distance > 4.0;
         // Hop over the step or the fence it is standing behind, rather than lean on it.
         this.steering.jump = this.closing != null && player.method_73189().method_1025(this.closing) < 0.02 && player.method_24828();
         this.closing = player.method_73189();
         this.steering.method_3129();
         this.status = "Closing on " + name + " (" + String.format(java.util.Locale.ROOT, "%.1f", distance) + " blocks)";
         return true;
      }
      this.inMelee = true;
      navigator.release();
      for (String sword : SWORDS) {
         if (Stock.count(stock, sword) > 0) {
            if (!Stock.select(client, sword)) {
               this.status = "Drawing the " + Ids.pretty(sword);
               return true;
            }
            break;
         }
      }
      this.hold(player);
      float[] look = BuilderLook.anglesTo(player, this.threat.method_23317(), this.threat.method_23320() - 0.2, this.threat.method_23321());
      boolean aimed = BuilderLook.toward(player, look[0], look[1]);

      boolean creeper = this.threat instanceof net.minecraft.class_1548;
      boolean hurt = player.method_6032() <= 7.0F;
      double want = creeper ? 2.9 : 2.1;
      boolean charged = player.method_7261(0.0F) >= 0.95F;

      // Circle it rather than stand in front of it, and change hands every so often so it is not
      // a straight line a skeleton can lead. Standing still trading blows is how the old one died.
      if (++this.strafeFlip > 26) {
         this.strafeFlip = 0;
         this.strafe = -this.strafe;
      }
      this.steering.side = this.strafe * (creeper || hurt ? 0.6F : 1.0F);
      // Back off from a creeper between swings, and whenever hurt; otherwise close to sword range.
      double gap = distance - want;
      this.steering.ahead = (float)Math.max(-1.0, Math.min(1.0, hurt ? -1.0 : gap * 0.8));
      if (creeper && charged) {
         this.steering.ahead = 0.4F;
      }
      // A swing while falling is a critical: half as many swings for the same zombie.
      this.steering.jump = !creeper && !hurt && charged && player.method_24828() && distance < 3.0;
      this.steering.sprint = false;
      this.steering.method_3129();

      this.status = (hurt ? "Backing off from " : creeper ? "Keeping clear of " : "Fighting ") + name;
      if (!aimed) {
         return true;
      }
      // Hit on the way down, which is what makes it a critical.
      if (charged && distance <= (creeper ? 3.2 : 3.0) && (creeper || player.method_18798().field_1351 < -0.08 || !player.method_24828())) {
         client.field_1761.method_2918(player, this.threat);
         player.method_6104(class_1268.field_5808);
      }
      return true;
   }

   @Environment(EnvType.CLIENT)
   private static final class Steering extends net.minecraft.class_744 {
      float ahead;
      float side;
      boolean jump;
      boolean sprint;

      void stop() {
         this.ahead = 0.0F;
         this.side = 0.0F;
         this.jump = false;
         this.sprint = false;
      }

      private boolean moving() {
         return this.ahead != 0.0F || this.side != 0.0F;
      }

      @Override
      public void method_3129() {
         this.field_54155 = new net.minecraft.class_10185(
            this.ahead > 0.3F, this.ahead < -0.3F, this.side > 0.3F, this.side < -0.3F, this.jump, false, this.sprint
         );
         this.field_55868 = this.moving() ? new net.minecraft.class_241(this.side, this.ahead) : net.minecraft.class_241.field_1340;
      }

      @Override
      public void method_64054() {
         if (this.moving()) {
            super.method_64054();
         }
      }
   }

   /** The nearest hostile worth dealing with, sticking to the one already being fought. */
   private class_1297 pick(class_310 client, class_746 player, long tick) {
      this.ignored.values().removeIf(until -> until < tick);
      if (this.threat != null && this.threat.method_5805() && player.method_5858(this.threat) <= ENGAGE * ENGAGE
         && !this.ignored.containsKey(this.threat.method_5628())
         && (reachable(client, player, this.threat) || player.method_5858(this.threat) < 6.25)) {
         return this.threat;
      }
      this.threatTicks = 0;
      return this.nearest(client, player);
   }

   /**
    * Can it be got at at all? A zombie in a cave under the floor is inside the seven blocks that
    * count as near, and the old code would walk at it for ever: close to three blocks, drop into
    * melee, get pushed back out, close again. A whole run went on that. Anything behind a solid
    * block is left alone unless it is close enough to be actually hitting us.
    */
   private static boolean reachable(class_310 client, class_746 player, class_1297 mob) {
      net.minecraft.class_243 eye = player.method_33571();
      net.minecraft.class_243 at = mob.method_5829().method_1005();
      net.minecraft.class_3965 hit = Reach.trace(client, eye, at);
      return hit == null
         || hit.method_17783() != net.minecraft.class_239.class_240.field_1332
         || hit.method_17784().method_1025(eye) >= at.method_1025(eye) - 0.6;
   }

   private class_1297 nearest(class_310 client, class_746 player) {
      List<class_1297> found = client.field_1687.method_8333(
         player,
         player.method_5829().method_1014(ENGAGE),
         entity -> entity instanceof class_1588
            && entity.method_5805()
            && !this.ignored.containsKey(entity.method_5628())
            && (reachable(client, player, entity) || player.method_5858(entity) < 6.25)
      );
      class_1297 best = null;
      double bestDistance = ENGAGE * ENGAGE;
      for (class_1297 entity : found) {
         double distance = player.method_5858(entity);
         if (distance < bestDistance) {
            bestDistance = distance;
            best = entity;
         }
      }
      return best;
   }

   /** Holds the use key on food until the bar is full again. */
   private boolean eat(class_310 client, class_746 player, Map<String, Integer> stock) {
      int hunger = player.method_7344().method_7586();
      if (hunger >= 18) {
         this.stop(client);
         return false;
      }
      for (String food : FOOD) {
         if (Stock.count(stock, food) <= 0) {
            continue;
         }
         if (!Stock.select(client, food)) {
            this.status = "Taking out " + Ids.pretty(food);
            return true;
         }
         if (client.field_1755 != null) {
            Work.close(client);
            return true;
         }
         this.eating = true;
         client.field_1690.field_1904.method_23481(true);
         this.status = "Eating " + Ids.pretty(food) + " (" + hunger + "/20)";
         return true;
      }
      this.stop(client);
      return false;
   }

   /** True when there is nothing to eat and the bar is low enough to matter. */
   public static boolean starving(class_746 player, Map<String, Integer> stock) {
      if (player.method_7344().method_7586() > 6) {
         return false;
      }
      return FOOD.stream().noneMatch(food -> Stock.count(stock, food) > 0);
   }
}
