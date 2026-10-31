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
   /** Swords, best first. */
   private static final List<String> SWORDS = List.of("netherite_sword", "diamond_sword", "iron_sword", "stone_sword", "wooden_sword", "golden_sword");

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
         this.status = "Clear";
         return false;
      }
      double distance = Math.sqrt(player.method_5858(this.threat));
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
      if (distance > ATTACK) {
         // Walk up to it. Standing still just out of reach, swinging at nothing, was the old
         // behaviour: the box test said "within range" while the server said "too far".
         BuilderNavigator.Result walk = navigator.moveTo(client, this.threat.method_24515());
         if (walk == BuilderNavigator.Result.NO_PATH || walk == BuilderNavigator.Result.STUCK) {
            this.ignored.put(this.threat.method_5628(), tick + 400L);
            this.threat = null;
            this.status = "No way to reach " + name;
            return false;
         }
         this.status = "Closing on " + name + " (" + String.format(java.util.Locale.ROOT, "%.1f", distance) + " blocks)";
         return true;
      }
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
      float[] look = BuilderLook.anglesTo(player, this.threat.method_23317(), this.threat.method_23320() - 0.2, this.threat.method_23321());
      this.status = "Fighting " + name;
      if (!BuilderLook.toward(player, look[0], look[1])) {
         return true;
      }
      if (player.method_7261(0.0F) >= 0.95F) {
         client.field_1761.method_2918(player, this.threat);
         player.method_6104(class_1268.field_5808);
      }
      return true;
   }

   /** The nearest hostile worth dealing with, sticking to the one already being fought. */
   private class_1297 pick(class_310 client, class_746 player, long tick) {
      this.ignored.values().removeIf(until -> until < tick);
      if (this.threat != null && this.threat.method_5805() && player.method_5858(this.threat) <= ENGAGE * ENGAGE
         && !this.ignored.containsKey(this.threat.method_5628())) {
         return this.threat;
      }
      this.threatTicks = 0;
      return this.nearest(client, player);
   }

   private class_1297 nearest(class_310 client, class_746 player) {
      List<class_1297> found = client.field_1687.method_8333(
         player,
         player.method_5829().method_1014(ENGAGE),
         entity -> entity instanceof class_1588 && entity.method_5805() && !this.ignored.containsKey(entity.method_5628())
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
