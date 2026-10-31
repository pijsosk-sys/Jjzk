package com.roothack.overlord.stages;

public enum Stage {
   GATHER_WOOD("Punch trees until 4 logs are stocked"),
   CRAFT_BASICS("Craft a crafting table, wooden pickaxe and sticks"),
   GATHER_STONE("Mine cobblestone for a full stone tool set and a furnace"),
   CRAFT_STONE("Craft stone pickaxe, stone sword and a furnace"),
   GATHER_IRON("Strip-mine until 7 raw iron are held"),
   SMELT_IRON("Smelt raw iron into ingots"),
   CRAFT_IRON("Craft iron pickaxe, iron sword and iron armour set"),
   GATHER_DIAMONDS("Branch-mine at y=-54 until 10 diamonds are held"),
   CRAFT_DIAMOND("Craft diamond pickaxe, sword and a full diamond armour set"),
   BUILD_PORTAL("Gather obsidian and open a nether portal"),
   ENTER_NETHER("Step through the portal into the nether"),
   GATHER_ANCIENT_DEBRIS("Mine 4 ancient debris at y=15 using beds or TNT"),
   SMELT_NETHERITE("Smelt ancient debris into netherite scrap"),
   CRAFT_NETHERITE("Craft netherite ingots and upgrade every diamond piece"),
   GATHER_XP("Fight mobs / mine ores until 30 levels are banked"),
   SETUP_ENCHANTING("Place an enchanting table ringed by 15 bookshelves"),
   ENCHANT_GEAR("Enchant every netherite piece to its best tier"),
   DONE("Fully enchanted netherite loadout reached");

   public final String description;

   private Stage(String description) {
      this.description = description;
   }

   public Stage next() {
      int i = this.ordinal();
      return i + 1 < values().length ? values()[i + 1] : DONE;
   }
}
