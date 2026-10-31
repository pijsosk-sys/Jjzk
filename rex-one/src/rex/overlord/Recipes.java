package rex.overlord;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * The recipes Overlord needs, as grid cells rather than as ingredient lists, because the clicks it
 * sends have to land in the right square. Cell 0 is the top left of a 3x3; a recipe whose cells all
 * fit the top-left 2x2 can be made in the player's own inventory with no table.
 */
@Environment(EnvType.CLIENT)
public final class Recipes {
   /** Every piece Overlord finishes with, in armour-slot order first. */
   public static final List<String> GEAR = List.of("helmet", "chestplate", "leggings", "boots", "sword", "pickaxe");
   /** Items that count as present when one stands nearby instead of sitting in the bag. */
   public static final Set<String> STATIONS = Set.of("crafting_table", "furnace", "smithing_table", "enchanting_table");

   @Environment(EnvType.CLIENT)
   public record Recipe(String output, int count, Map<Integer, String> cells) {
      public Map<String, Integer> needs() {
         Map<String, Integer> out = new LinkedHashMap<>();
         this.cells.values().forEach(id -> out.merge(id, 1, Integer::sum));
         return out;
      }

      /** Fits the 2x2 the player carries around. */
      public boolean small() {
         return this.cells.keySet().stream().allMatch(i -> i % 3 < 2 && i / 3 < 2);
      }
   }

   private static final Map<String, Recipe> ALL = new LinkedHashMap<>();

   private static void add(String output, int count, Object... pairs) {
      Map<Integer, String> cells = new LinkedHashMap<>();
      for (int i = 0; i < pairs.length; i += 2) {
         cells.put((Integer)pairs[i], (String)pairs[i + 1]);
      }
      ALL.put(output, new Recipe(output, count, Collections.unmodifiableMap(cells)));
   }

   private static void shape(String output, int count, String material, int... cells) {
      Object[] pairs = new Object[cells.length * 2];
      for (int i = 0; i < cells.length; i++) {
         pairs[i * 2] = cells[i];
         pairs[i * 2 + 1] = material;
      }
      add(output, count, pairs);
   }

   static {
      shape("planks", 4, "logs", 0);
      shape("stick", 4, "planks", 0, 3);
      shape("crafting_table", 1, "planks", 0, 1, 3, 4);
      shape("furnace", 1, "cobblestone", 0, 1, 2, 3, 5, 6, 7, 8);
      add("torch", 4, 0, "coal", 3, "stick");

      for (String tier : List.of("wooden", "stone", "iron", "diamond")) {
         String m = switch (tier) {
            case "wooden" -> "planks";
            case "stone" -> "cobblestone";
            case "iron" -> "iron_ingot";
            default -> "diamond";
         };
         add(tier + "_pickaxe", 1, 0, m, 1, m, 2, m, 4, "stick", 7, "stick");
         add(tier + "_sword", 1, 1, m, 4, m, 7, "stick");
         add(tier + "_shovel", 1, 1, m, 4, "stick", 7, "stick");
         if (tier.equals("iron") || tier.equals("diamond")) {
            shape(tier + "_helmet", 1, m, 0, 1, 2, 3, 5);
            shape(tier + "_chestplate", 1, m, 0, 2, 3, 4, 5, 6, 7, 8);
            shape(tier + "_leggings", 1, m, 0, 1, 2, 3, 5, 6, 8);
            shape(tier + "_boots", 1, m, 3, 5, 6, 8);
         }
      }

      add("bucket", 1, 0, "iron_ingot", 2, "iron_ingot", 4, "iron_ingot");
      add("flint_and_steel", 1, 0, "iron_ingot", 4, "flint");
      add("smithing_table", 1, 0, "iron_ingot", 1, "iron_ingot", 3, "planks", 4, "planks", 6, "planks", 7, "planks");
      shape("paper", 3, "sugar_cane", 0, 1, 2);
      add("book", 1, 0, "paper", 1, "paper", 3, "paper", 4, "leather");
      add("bookshelf", 1, 0, "planks", 1, "planks", 2, "planks", 3, "book", 4, "book", 5, "book", 6, "planks", 7, "planks", 8, "planks");
      add("enchanting_table", 1, 1, "book", 3, "diamond", 4, "obsidian", 5, "diamond", 6, "obsidian", 7, "obsidian", 8, "obsidian");
      add("netherite_ingot", 1, 0, "netherite_scrap", 1, "netherite_scrap", 2, "netherite_scrap", 3, "netherite_scrap",
         4, "gold_ingot", 5, "gold_ingot", 6, "gold_ingot", 7, "gold_ingot");
      // Duplicating the upgrade template: seven diamonds, one netherrack and the template itself.
      add("netherite_upgrade_smithing_template", 2, 0, "diamond", 1, "netherite_upgrade_smithing_template", 2, "diamond",
         3, "diamond", 4, "netherrack", 5, "diamond", 6, "diamond", 7, "diamond", 8, "diamond");
   }

   private Recipes() {
   }

   public static Recipe get(String id) {
      return ALL.get(id);
   }

   public static java.util.Collection<Recipe> all() {
      return ALL.values();
   }

   /** The whole job: every piece crafted in netherite and enchanted. */
   public static boolean complete(Map<String, Integer> stock, Set<String> enchanted) {
      return GEAR.stream().allMatch(g -> stock.getOrDefault("netherite_" + g, 0) > 0 && enchanted.contains("netherite_" + g));
   }

   /** Cell index 0-8 to the screen-handler slot of a 3x3 table or the player's own 2x2. */
   public static int gridSlot(int index, boolean table) {
      if (index < 0 || index > 8 || !table && (index % 3 >= 2 || index / 3 >= 2)) {
         throw new IllegalArgumentException("recipe cell " + index + " does not fit this grid");
      }
      return 1 + (table ? index : index / 3 * 2 + index % 3);
   }
}
