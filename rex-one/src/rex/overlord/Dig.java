package rex.overlord;

import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_2338;
import net.minecraft.class_310;
import net.minecraft.class_3965;

/**
 * Breaking one block. The pickaxe is chosen before the first swing, because obsidian and ancient
 * debris simply do not break with the wrong one and the old code would hold the button on them for
 * ever without saying why.
 */
@Environment(EnvType.CLIENT)
public final class Dig {
   @Environment(EnvType.CLIENT)
   public enum Result {
      BREAKING,
      DONE,
      BLOCKED
   }

   private static final List<String> PICKAXES = List.of("netherite_pickaxe", "diamond_pickaxe", "iron_pickaxe", "stone_pickaxe", "wooden_pickaxe");
   private static final List<String> AXES = List.of("netherite_axe", "diamond_axe", "iron_axe", "stone_axe", "wooden_axe");
   private static final List<String> SHOVELS = List.of("netherite_shovel", "diamond_shovel", "iron_shovel", "stone_shovel", "wooden_shovel");

   private class_2338 target;
   private int ticks;
   public String status = "";

   public void stop(class_310 client) {
      if (this.target != null && client.field_1761 != null) {
         client.field_1761.method_2925();
      }
      this.target = null;
      this.ticks = 0;
   }

   /** The tool this block wants, as far as Overlord ever carries tools. */
   private static List<String> toolsFor(String block) {
      if (block.endsWith("_log") || block.endsWith("_stem") || block.endsWith("_planks") || block.endsWith("_wood")) {
         return AXES;
      }
      if (block.equals("dirt") || block.equals("grass_block") || block.equals("gravel") || block.equals("sand") || block.equals("soul_sand")) {
         return SHOVELS;
      }
      return PICKAXES;
   }

   /** Pickaxe tiers, as the game grades them: wood 1, stone 2, iron 3, diamond and netherite 4. */
   private static int tierOf(String block) {
      return switch (block) {
         case "obsidian", "crying_obsidian", "ancient_debris" -> 4;
         case "diamond_ore", "deepslate_diamond_ore", "emerald_ore", "deepslate_emerald_ore", "gold_ore", "deepslate_gold_ore",
              "redstone_ore", "deepslate_redstone_ore", "nether_gold_ore", "gilded_blackstone" -> 3;
         case "iron_ore", "deepslate_iron_ore", "lapis_ore", "deepslate_lapis_ore", "copper_ore", "deepslate_copper_ore" -> 2;
         default -> 0;
      };
   }

   /** True when something carried is good enough to get a drop out of this block. */
   public static boolean canBreak(java.util.Map<String, Integer> stock, String block) {
      int needed = tierOf(block);
      if (needed == 0) {
         return true;
      }
      int best = 0;
      if (Stock.count(stock, "wooden_pickaxe") > 0 || Stock.count(stock, "golden_pickaxe") > 0) {
         best = 1;
      }
      if (Stock.count(stock, "stone_pickaxe") > 0) {
         best = Math.max(best, 2);
      }
      if (Stock.count(stock, "iron_pickaxe") > 0) {
         best = Math.max(best, 3);
      }
      if (Stock.count(stock, "diamond_pickaxe") > 0 || Stock.count(stock, "netherite_pickaxe") > 0) {
         best = Math.max(best, 4);
      }
      return best >= needed;
   }

   private static void holdTool(class_310 client, String block) {
      for (String tool : toolsFor(block)) {
         if (Stock.find(client.field_1724, tool) >= 0) {
            Stock.select(client, tool);
            return;
         }
      }
   }

   /**
    * Keeps breaking the block at pos. DONE once it is gone, BLOCKED when no face of it can be
    * reached from where the player stands, so the caller walks closer.
    */
   public Result tick(class_310 client, class_2338 pos, long tick) {
      if (client.field_1687.method_8320(pos).method_45474()) {
         this.stop(client);
         return Result.DONE;
      }
      if (!pos.equals(this.target)) {
         if (this.target != null) {
            client.field_1761.method_2925();
         }
         this.target = pos.method_10062();
         this.ticks = 0;
      }
      String block = Ids.of(client.field_1687.method_8320(pos));
      class_3965 hit = Reach.hitFor(client, pos, false);
      if (hit == null) {
         this.status = "Cannot see " + Ids.pretty(block) + " from here";
         return Result.BLOCKED;
      }
      holdTool(client, block);
      if (!Reach.aimAt(client, hit)) {
         this.status = "Lining up on " + Ids.pretty(block);
         return Result.BREAKING;
      }
      // The per-tick call vanilla makes while the attack key is held. method_2910 only *starts* a
      // break, so calling that every tick restarted it for ever and nothing ever came out.
      if (client.field_1761.method_2902(pos, hit.method_17780())) {
         client.field_1687.method_74254(pos, hit.method_17780());
         client.field_1724.method_6104(net.minecraft.class_1268.field_5808);
      }
      this.status = "Breaking " + Ids.pretty(block);
      // Nothing has given way in fifteen seconds: either the tool is wrong or the server refuses.
      return ++this.ticks > 300 ? Result.BLOCKED : Result.BREAKING;
   }
}
