package rex.overlord;

import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_1661;
import net.minecraft.class_1713;
import net.minecraft.class_1799;
import net.minecraft.class_310;
import net.minecraft.class_746;

/**
 * What the player is carrying, and getting a particular item into the hand. Counted off the player
 * screen handler so worn armour and the offhand count too: a chestplate already on the body is not
 * a chestplate still to craft.
 */
@Environment(EnvType.CLIENT)
public final class Stock {
   /** Player screen handler slots: 5-8 armour, 9-35 inventory, 36-44 hotbar, 45 offhand. */
   private static final int ARMOUR = 5;
   private static final int OFFHAND = 45;

   private Stock() {
   }

   public static Map<String, Integer> counts(class_746 player) {
      Map<String, Integer> result = new HashMap<>();
      for (int slot = ARMOUR; slot <= OFFHAND; slot++) {
         class_1799 stack = player.field_7498.method_7611(slot).method_7677();
         if (!stack.method_7960()) {
            result.merge(Ids.of(stack), stack.method_7947(), Integer::sum);
         }
      }
      return result;
   }

   /** Counts with the two wildcards the recipes use: any log, any plank. */
   public static int count(Map<String, Integer> stock, String id) {
      if (id.equals("logs")) {
         return sum(stock, "_log", "_stem");
      }
      if (id.equals("planks")) {
         return sum(stock, "_planks");
      }
      return stock.getOrDefault(id, 0);
   }

   private static int sum(Map<String, Integer> stock, String... suffixes) {
      int total = 0;
      for (Map.Entry<String, Integer> entry : stock.entrySet()) {
         for (String suffix : suffixes) {
            if (entry.getKey().endsWith(suffix)) {
               total += entry.getValue();
               break;
            }
         }
      }
      return total;
   }

   /** The real item a wildcard stands for right now, so a click can be aimed at a slot. */
   public static String actual(Map<String, Integer> stock, String id) {
      if (!id.equals("logs") && !id.equals("planks")) {
         return id;
      }
      return stock.entrySet().stream()
         .filter(e -> e.getValue() > 0)
         .map(Map.Entry::getKey)
         .filter(k -> id.equals("logs") ? k.endsWith("_log") || k.endsWith("_stem") : k.endsWith("_planks"))
         .sorted()
         .findFirst()
         .orElse(id);
   }

   /** Index in the main inventory (0-8 hotbar, 9-35 the rest), or -1. */
   public static int find(class_746 player, String id) {
      class_1661 inventory = player.method_31548();
      for (int i = 0; i < 36; i++) {
         if (Ids.of(inventory.method_5438(i)).equals(id)) {
            return i;
         }
      }
      return -1;
   }

   /** The player screen handler slot holding main inventory index i. */
   public static int handlerSlot(int index) {
      return index < 9 ? 36 + index : index;
   }

   /**
    * Puts the item in the hand. Returns true when it is held now; false means a swap was sent and
    * the next tick will see it, which is how every other click in Overlord is paced.
    */
   public static boolean select(class_310 client, String id) {
      class_746 player = client.field_1724;
      if (player == null || client.field_1761 == null) {
         return false;
      }
      class_1661 inventory = player.method_31548();
      if (Ids.of(inventory.method_5438(inventory.method_67532())).equals(id)) {
         return true;
      }
      int index = find(player, id);
      if (index < 0) {
         return false;
      }
      if (index < 9) {
         inventory.method_61496(index);
         return true;
      }
      // Out of the hotbar: swap it into the slot in hand and let the server catch up.
      client.field_1761.method_2906(player.field_7498.field_7763, handlerSlot(index), inventory.method_67532(), class_1713.field_7791, player);
      return false;
   }

   /** True when a piece of this kind is held or worn and already carries enchantments. */
   public static boolean enchanted(class_746 player, String id) {
      for (int slot = ARMOUR; slot <= OFFHAND; slot++) {
         class_1799 stack = player.field_7498.method_7611(slot).method_7677();
         if (Ids.of(stack).equals(id) && stack.method_7942()) {
            return true;
         }
      }
      return false;
   }
}
