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
 * Staying alive while the rest of Overlord works: hitting back at anything hostile that gets
 * within arm's reach, and eating before the hunger bar costs health. Both take over the tick, so
 * the progression never walks on while a zombie is chewing on it.
 */
@Environment(EnvType.CLIENT)
public final class Fight {
   /** Food Overlord may end up carrying, best first. */
   private static final List<String> FOOD = List.of(
      "cooked_beef", "cooked_porkchop", "cooked_chicken", "cooked_mutton", "bread", "baked_potato", "apple", "beef", "porkchop", "chicken"
   );
   /** Anything closer than this is a fight, not a detour. */
   private static final double FIGHT = 3.6;

   private boolean eating;
   public String status = "Clear";

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
      class_1297 threat = this.nearest(client, player);
      if (threat == null) {
         this.status = "Clear";
         return false;
      }
      navigator.release();
      float[] look = BuilderLook.anglesTo(player, threat.method_23317(), threat.method_23320() - 0.2, threat.method_23321());
      this.status = "Fighting " + threat.method_5477().getString();
      if (!BuilderLook.toward(player, look[0], look[1])) {
         return true;
      }
      if (player.method_7261(0.0F) >= 0.95F) {
         client.field_1761.method_2918(player, threat);
         player.method_6104(class_1268.field_5808);
      }
      return true;
   }

   private class_1297 nearest(class_310 client, class_746 player) {
      List<class_1297> found = client.field_1687.method_8333(
         player, player.method_5829().method_1014(FIGHT), entity -> entity instanceof class_1588 && entity.method_5805()
      );
      class_1297 best = null;
      double bestDistance = Double.MAX_VALUE;
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
