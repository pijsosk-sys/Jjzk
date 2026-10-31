package rex.overlord;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/** The route Overlord walks, in the order it walks it. The HUD shows the current one. */
@Environment(EnvType.CLIENT)
public enum Stage {
   PLAN("Reading the inventory and deciding what comes next"),
   WOOD("Breaking trees for logs"),
   BASICS("Crafting a table, a wooden pickaxe and sticks"),
   STONE("Mining cobblestone for stone tools and a furnace"),
   IRON("Mining raw iron"),
   SMELT("Smelting ore into ingots"),
   IRON_GEAR("Crafting the iron pickaxe, sword and armour"),
   DIAMONDS("Mining diamonds"),
   DIAMOND_GEAR("Crafting the diamond pickaxe, sword and armour"),
   PORTAL("Gathering obsidian and lighting a nether portal"),
   NETHER("Going through the portal"),
   DEBRIS("Mining ancient debris"),
   NETHERITE("Smelting scrap and upgrading every diamond piece"),
   XP("Collecting the levels an enchantment costs"),
   ENCHANT_SETUP("Placing the enchanting table and its bookshelves"),
   ENCHANT("Enchanting each netherite piece"),
   DONE("Enchanted netherite armour, sword and pickaxe are in hand");

   public final String description;

   Stage(String description) {
      this.description = description;
   }

   /** Short label for the HUD. */
   public String label() {
      return this.name().charAt(0) + this.name().substring(1).toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
   }
}
