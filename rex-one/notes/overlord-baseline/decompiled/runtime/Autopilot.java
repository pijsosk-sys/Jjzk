package com.roothack.overlord.runtime;

import com.roothack.overlord.stages.Stage;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Predicate;

public final class Autopilot {
   public final InventoryOps ops = new InventoryOps();
   public final Structures structures = new Structures(this);
   public String status = "Stopped";
   public Stage stage = Stage.GATHER_WOOD;
   public boolean running;
   public boolean done;
   public int delay;
   private Object baritone;
   private Object command;
   private Object useKey;
   private long ticks;
   private long lastChange;
   private Map<String, Integer> stock = Map.of();
   private Map<String, Integer> previous = Map.of();
   private String mineKey = "";
   private String lastError = "";
   private int retries;
   private Structures.P exploreTarget;
   private int explorations;
   private Object world;

   public Autopilot() {
   }

   public void attach(Object var1) {
      this.baritone = var1;
   }

   public void start() {
      this.release();
      this.delay = 0;
      this.exploreTarget = null;
      this.running = true;
      this.done = false;
      this.lastChange = this.ticks;
      this.retries = 0;
      this.lastError = "";
      this.ops.clear();
      this.status = "Planning from inventory";
   }

   public void stop() {
      this.running = false;
      this.release();
      this.ops.clear();

      try {
         this.cancelMine();
         Access.call(Access.call(this.baritone, "getCustomGoalProcess"), "onLostControl");
      } catch (Exception var2) {
      }

      this.status = "Stopped";
   }

   public void suspendForDefense() {
      this.release();
   }

   private void release() {
      if (this.useKey != null) {
         try {
            Access.call(this.useKey, "method_23481", false);
         } catch (Exception var2) {
         }
      }

      this.useKey = null;
   }

   private void cancelMine() throws Exception {
      if (this.baritone != null) {
         Access.call(Access.call(this.baritone, "getMineProcess"), "cancel");
      }

      this.mineKey = "";
   }

   public void go(int var1, int var2, int var3) throws Exception {
      this.cancelMine();
      Access.close();
      this.command = Access.command("SET_GOAL_AND_PATH", Access.make("baritone.api.pathing.goals.GoalBlock", var1, var2, var3));
   }

   public void near(int var1, int var2, int var3) throws Exception {
      this.cancelMine();
      Access.close();
      this.command = Access.command("SET_GOAL_AND_PATH", Access.make("baritone.api.pathing.goals.GoalNear", var1, var2, var3, 2));
   }

   public void explore() throws Exception {
      Structures.P var1 = Structures.feet();
      if (this.exploreTarget == null || Math.hypot((double)(var1.x() - this.exploreTarget.x()), (double)(var1.z() - this.exploreTarget.z())) < 4.0) {
         double var2 = (double)(this.explorations++) * 2.399963229728653;
         int var4 = 32 + (int)Math.sqrt((double)this.explorations) * 24;
         this.exploreTarget = var1.add((int)(Math.cos(var2) * (double)var4), 0, (int)(Math.sin(var2) * (double)var4));
      }

      this.go(this.exploreTarget.x(), this.exploreTarget.y(), this.exploreTarget.z());
   }

   private void mine(int var1, Integer var2, String... var3) throws Exception {
      Access.close();
      Object var4 = Access.call(this.baritone, "getMineProcess");
      String var5 = Arrays.toString((Object[])var3);
      boolean var6 = var5.equals(this.mineKey) && Boolean.TRUE.equals(Access.call(var4, "isActive"));
      if (!var6 && var2 != null && Math.abs(Structures.feet().y() - var2) > 3) {
         this.cancelMine();
         this.command = Access.command("SET_GOAL_AND_PATH", Access.make("baritone.api.pathing.goals.GoalYLevel", var2));
         this.status = "Reaching mining layer " + var2;
      } else {
         if (!var6) {
            Access.call(var4, "mineByName", var1, var3);
            this.mineKey = var5;
         }

         this.status = "Gathering " + Arrays.toString((Object[])var3);
      }
   }

   private int count(String var1) {
      return Recipes.count(this.stock, var1);
   }

   private boolean havePick(String var1) {
      return this.count(var1 + "_pickaxe") > 0;
   }

   private boolean overworld() throws Exception {
      return this.structures.portal(false);
   }

   private boolean nether() throws Exception {
      return this.structures.portal(true);
   }

   public Object tick(boolean var1, boolean var2) throws Exception {
      this.ticks++;
      this.command = Access.command("DEFER", null);
      if (!this.running) {
         return Access.command("CANCEL_AND_SET_GOAL", null);
      } else {
         Object var3 = Access.player();
         Object var4 = Access.world();
         if (var3 != null && var4 != null) {
            if (this.world != var4) {
               this.world = var4;
               this.ops.clear();
               this.lastChange = this.ticks;
               this.structures.rememberPortal();
            }

            if (!(Boolean)Access.call(var3, "method_5805")) {
               this.stop();
               this.status = "Stopped after death; #overlord start replans from recovered inventory";
               return Access.command("CANCEL_AND_SET_GOAL", null);
            } else {
               if (var1) {
                  this.cancelMine();
                  this.exploreTarget = null;
                  if (++this.retries > 3) {
                     this.stop();
                     this.status = "Path repeatedly failed; move to accessible terrain and #overlord start";
                     Access.message(this.status);
                     return Access.command("REQUEST_PAUSE", null);
                  }
               }

               if (this.ops.pump(this.ticks)) {
                  this.status = "Waiting for workstation result";
                  return Access.command("REQUEST_PAUSE", null);
               } else if (this.delay > 0) {
                  this.delay--;
                  return Access.command("REQUEST_PAUSE", null);
               } else {
                  this.stock = Access.counts();
                  if (!this.stock.equals(this.previous)) {
                     this.previous = new HashMap<>(this.stock);
                     this.lastChange = this.ticks;
                     this.retries = 0;
                  }

                  if (this.ticks - this.lastChange > 2400L) {
                     this.cancelMine();
                     this.lastChange = this.ticks;
                     this.exploreTarget = null;
                     if (++this.retries > 3) {
                        this.stop();
                        this.status = "No progress after three recovery attempts; #overlord start resumes from inventory";
                        Access.message(this.status);
                        return Access.command("REQUEST_PAUSE", null);
                     } else {
                        this.explore();
                        return this.command;
                     }
                  } else if (this.food()) {
                     return Access.command("REQUEST_PAUSE", null);
                  } else {
                     if (!this.havePick("netherite") && !this.havePick("diamond") && !this.havePick("iron") && !this.havePick("stone")) {
                        this.stage = Stage.CRAFT_BASICS;
                        if (!this.ensure("wooden_pickaxe", 1, 0)) {
                           return this.command;
                        }
                     }

                     if (!this.havePick("netherite") && !this.havePick("diamond") && !this.havePick("iron")) {
                        this.stage = Stage.CRAFT_STONE;
                        if (!this.ensure("stone_pickaxe", 1, 0)) {
                           return this.command;
                        }
                     }

                     if (!this.havePick("netherite") && !this.havePick("diamond")) {
                        this.stage = Stage.CRAFT_IRON;
                        if (!this.ensure("iron_pickaxe", 1, 0) || !this.ensure("iron_sword", 1, 0)) {
                           return this.command;
                        }

                        for (String var6 : List.of("helmet", "chestplate", "leggings", "boots")) {
                           if (this.count("diamond_" + var6) == 0 && this.count("netherite_" + var6) == 0 && !this.ensure("iron_" + var6, 1, 0)) {
                              return this.command;
                           }
                        }
                     }

                     this.stage = Stage.CRAFT_DIAMOND;

                     for (String var13 : Recipes.GEAR) {
                        if (this.count("netherite_" + var13) == 0 && !this.ensure("diamond_" + var13, 1, 0)) {
                           return this.command;
                        }
                     }

                     int var12 = (int)Recipes.GEAR.stream().filter(var1x -> this.count("netherite_" + var1x) == 0).count();
                     if (var12 > 0) {
                        this.stage = Stage.CRAFT_NETHERITE;
                        if (!this.ensure("netherite_upgrade_smithing_template", var12, 0)
                           || !this.ensure("netherite_ingot", var12, 0)
                           || !this.ensure("smithing_table", 1, 0)) {
                           return this.command;
                        }

                        if (!this.structures.station("smithing_table", "class_4862")) {
                           return this.command;
                        }

                        for (String var7 : Recipes.GEAR) {
                           if (this.count("netherite_" + var7) == 0) {
                              this.ops.smith("diamond_" + var7);
                              this.status = "Upgrading " + var7;
                              return Access.command("REQUEST_PAUSE", null);
                           }
                        }
                     }

                     HashSet var15 = new HashSet();

                     for (String var8 : Recipes.GEAR) {
                        if (Access.enchanted("netherite_" + var8)) {
                           var15.add("netherite_" + var8);
                        }
                     }

                     if (!Recipes.complete(this.stock, var15)) {
                        this.stage = Stage.SETUP_ENCHANTING;
                        if (!this.overworld() || !this.ensure("lapis_lazuli", 3, 0)) {
                           return this.command;
                        }

                        int var17 = ((Number)Access.field(var3, "field_7520")).intValue();
                        if (var17 < 30) {
                           this.stage = Stage.GATHER_XP;
                           this.mine(this.count("coal") + 16, 48, "coal_ore");
                           this.status = "Collecting XP for the next level-30 enchantment";
                           return this.command;
                        }

                        if (Structures.feet().y() < 60) {
                           this.cancelMine();
                           this.command = Access.command("SET_GOAL_AND_PATH", Access.make("baritone.api.pathing.goals.GoalYLevel", 64));
                           this.status = "Reaching the surface for the enchanting area";
                           return this.command;
                        }

                        if (!this.structures.enchanting()) {
                           return this.command;
                        }

                        this.stage = Stage.ENCHANT_GEAR;

                        for (String var9 : Recipes.GEAR) {
                           String var10 = "netherite_" + var9;
                           if (!var15.contains(var10)) {
                              this.ops.enchant(var10);
                              this.status = "Enchanting " + var10;
                              return Access.command("REQUEST_PAUSE", null);
                           }
                        }
                     }

                     this.stage = Stage.DONE;
                     this.equip();
                     this.done = true;
                     this.running = false;
                     this.cancelMine();
                     Access.message("Complete: enchanted netherite armor, sword and pickaxe verified in inventory/equipment.");
                     return Access.command("CANCEL_AND_SET_GOAL", null);
                  }
               }
            }
         } else {
            this.release();
            return Access.command("REQUEST_PAUSE", null);
         }
      }
   }

   public boolean ensure(String var1, int var2, int var3) throws Exception {
      if (this.count(var1) >= var2) {
         return true;
      } else if (Set.of("crafting_table", "furnace", "smithing_table").contains(var1) && this.structures.available(var1)) {
         return true;
      } else if (var3 > 24) {
         throw new IllegalStateException("Ingredient cycle: " + var1);
      } else {
         this.status = "Need " + var2 + " " + var1;
         if (!var1.equals("netherite_upgrade_smithing_template") || this.count(var1) != 0) {
            Recipes.Recipe var4 = Recipes.get(var1);
            if (var4 != null) {
               Map var9 = var4.needs();

               for (Entry var13 : var9.entrySet()) {
                  if (!this.ensure((String)var13.getKey(), (Integer)var13.getValue(), var3 + 1)) {
                     return false;
                  }
               }

               if (!var4.small()) {
                  if (!this.ensure("crafting_table", 1, var3 + 1) || !this.structures.station("crafting_table", "class_1714")) {
                     return false;
                  }
               } else if (!Access.type("net.minecraft.class_1714").isInstance(this.ops.handler())
                  && !Access.type("net.minecraft.class_1723").isInstance(this.ops.handler())) {
                  Access.close();
                  return false;
               }

               this.cancelMine();
               this.ops.craft(var4, this.stock);
               this.delay = 2;
               return false;
            } else {
               String var5 = switch (var1) {
                  case "iron_ingot" -> "raw_iron";
                  case "gold_ingot" -> "raw_gold";
                  case "netherite_scrap" -> "ancient_debris";
                  case "cooked_beef" -> "beef";
                  default -> null;
               };
               if (var5 == null) {
                  switch (var1) {
                     case "logs":
                        if (!this.overworld()) {
                           return false;
                        }

                        this.stage = Stage.GATHER_WOOD;
                        this.mine(
                           Math.max(var2, 8),
                           null,
                           "oak_log",
                           "birch_log",
                           "spruce_log",
                           "jungle_log",
                           "acacia_log",
                           "dark_oak_log",
                           "cherry_log",
                           "mangrove_log"
                        );
                        break;
                     case "cobblestone":
                        if (!this.havePick("stone")
                           && !this.havePick("iron")
                           && !this.havePick("diamond")
                           && !this.havePick("netherite")
                           && !this.ensure("wooden_pickaxe", 1, var3 + 1)) {
                           return false;
                        }

                        this.stage = Stage.GATHER_STONE;
                        this.mine(Math.max(var2, 32), null, "stone", "cobblestone");
                        break;
                     case "coal":
                        if (!this.havePick("stone")
                           && !this.havePick("iron")
                           && !this.havePick("diamond")
                           && !this.havePick("netherite")
                           && !this.ensure("wooden_pickaxe", 1, var3 + 1)) {
                           return false;
                        }

                        if (!this.overworld()) {
                           return false;
                        }

                        this.mine(Math.max(var2, 16), 48, "coal_ore");
                        break;
                     case "raw_iron":
                        if (!this.havePick("iron") && !this.havePick("diamond") && !this.havePick("netherite") && !this.ensure("stone_pickaxe", 1, var3 + 1)) {
                           return false;
                        }

                        if (!this.overworld()) {
                           return false;
                        }

                        this.stage = Stage.GATHER_IRON;
                        this.mine(Math.max(var2, 8), 16, "iron_ore", "deepslate_iron_ore");
                        break;
                     case "diamond":
                     case "raw_gold":
                     case "lapis_lazuli":
                        if (!this.havePick("diamond") && !this.havePick("netherite") && !this.ensure("iron_pickaxe", 1, var3 + 1)) {
                           return false;
                        }

                        if (!this.overworld()) {
                           return false;
                        }

                        String[] var8 = var1.equals("diamond")
                           ? new String[]{"diamond_ore", "deepslate_diamond_ore"}
                           : (var1.equals("raw_gold") ? new String[]{"gold_ore", "deepslate_gold_ore"} : new String[]{"lapis_ore", "deepslate_lapis_ore"});
                        this.stage = Stage.GATHER_DIAMONDS;
                        this.mine(var2, var1.equals("diamond") ? -54 : -16, var8);
                        break;
                     case "obsidian":
                        if (!this.havePick("netherite") && !this.ensure("diamond_pickaxe", 1, var3 + 1)) {
                           return false;
                        }

                        this.stage = Stage.BUILD_PORTAL;
                        this.mine(var2, null, "obsidian");
                        break;
                     case "ancient_debris":
                        if (!this.havePick("netherite") && !this.ensure("diamond_pickaxe", 1, var3 + 1)) {
                           return false;
                        }

                        if (!this.nether()) {
                           return false;
                        }

                        this.stage = Stage.GATHER_ANCIENT_DEBRIS;
                        this.mine(var2, 15, "ancient_debris");
                        break;
                     case "netherrack":
                        if (!this.nether()) {
                           return false;
                        }

                        this.mine(Math.max(var2, 8), null, "netherrack");
                        break;
                     case "flint":
                        if (!this.overworld()) {
                           return false;
                        }

                        this.mine(var2, null, "gravel");
                        break;
                     case "sugar_cane":
                        if (!this.overworld()) {
                           return false;
                        }

                        this.mine(var2, null, "sugar_cane");
                        break;
                     case "leather":
                     case "beef":
                        if (!this.overworld()) {
                           return false;
                        }

                        this.hunt();
                        break;
                     default:
                        throw new IllegalStateException("No acquisition route for " + var1);
                  }

                  return false;
               } else {
                  Object var6 = this.ops.handler();
                  if (!Access.type("net.minecraft.class_1720").isInstance(var6)
                     || (Boolean)Access.call(this.ops.stack(var6, 2), "method_7960") && !Access.id(this.ops.stack(var6, 0)).equals(var5)) {
                     int var11 = var2 - this.count(var1);
                     if (!this.ensure(var5, var11, var3 + 1)
                        || !this.ensure("furnace", 1, var3 + 1)
                        || !this.ensure("coal", Math.max(1, (var11 + 7) / 8), var3 + 1)) {
                        return false;
                     } else if (!this.structures.station("furnace", "class_1720")) {
                        return false;
                     } else {
                        this.cancelMine();
                        this.ops.smelt(var5);
                        this.delay = 4;
                        return false;
                     }
                  } else {
                     this.cancelMine();
                     this.ops.smelt(var5);
                     this.delay = 4;
                     return false;
                  }
               }
            }
         } else if (!this.nether()) {
            return false;
         } else {
            this.stage = Stage.GATHER_ANCIENT_DEBRIS;
            this.structures.template();
            return false;
         }
      }
   }

   private boolean food() throws Exception {
      Object var1 = Access.call(Access.player(), "method_7344");
      int var2 = Access.num(var1, "method_7586");
      if (var2 >= 18) {
         this.release();
         return false;
      } else {
         for (String var4 : List.of("cooked_beef", "cooked_porkchop", "cooked_chicken", "bread", "baked_potato", "beef", "porkchop", "apple")) {
            if (this.count(var4) > 0) {
               this.cancelMine();
               if (!this.structures.select(var4)) {
                  return true;
               }

               this.useKey = Access.field(Access.field(Access.mc(), "field_1690"), "field_1904");
               Access.call(this.useKey, "method_23481", true);
               this.status = "Eating before continuing";
               return true;
            }
         }

         this.release();
         if (var2 <= 6) {
            this.status = "Gathering food";
            if (this.count("beef") > 0) {
               this.ensure("cooked_beef", 1, 0);
            } else {
               this.hunt();
            }

            return true;
         } else {
            return false;
         }
      }
   }

   private void hunt() throws Exception {
      Object var1 = Access.player();
      Object var2 = Access.call(Access.call(var1, "method_5829"), "method_1014", 48.0);
      List var3 = (List)Access.call(Access.world(), "method_8333", var1, var2, (Predicate<Object>)var0 -> {
         try {
            return Access.type("net.minecraft.class_1430").isInstance(var0) && (Boolean)Access.call(var0, "method_5805");
         } catch (Exception var2x) {
            return false;
         }
      });
      Object var4 = null;
      double var5 = Double.MAX_VALUE;

      for (Object var8 : var3) {
         double var9 = Access.real(var1, "method_5858", var8);
         if (var9 < var5) {
            var5 = var9;
            var4 = var8;
         }
      }

      if (var4 == null) {
         this.explore();
         this.status = "Exploring for cows and leather";
      } else if (var5 > 6.25) {
         this.near((int)Access.real(var4, "method_23317"), (int)Access.real(var4, "method_23318"), (int)Access.real(var4, "method_23321"));
      } else {
         this.cancelMine();
         String var11 = this.count("netherite_sword") > 0
            ? "netherite_sword"
            : (this.count("diamond_sword") > 0 ? "diamond_sword" : (this.count("iron_sword") > 0 ? "iron_sword" : null));
         if (var11 != null) {
            this.structures.select(var11);
         }

         if (Access.real(var1, "method_7261", 0.0F) >= 0.95) {
            Access.call(Access.field(Access.mc(), "field_1761"), "method_2918", var1, var4);
            Access.call(var1, "method_6104", Access.field(Access.type("net.minecraft.class_1268"), "field_5808"));
            this.delay = 10;
         }
      }
   }

   private void equip() throws Exception {
      Access.close();
      Object var1 = Access.field(Access.player(), "field_7498");

      for (int var2 = 0; var2 < 4; var2++) {
         String var3 = "netherite_" + Recipes.GEAR.get(var2);
         int var4 = 5 + var2;
         Object var5 = this.ops.stack(var1, var4);
         if (!Access.id(var5).equals(var3)) {
            if (!(Boolean)Access.call(var5, "method_7960")) {
               this.ops.click(var1, var4, 0, "field_7794");
            }

            int var6 = this.ops.source(var1, var3);
            if (var6 >= 0) {
               this.ops.click(var1, var6, 0, "field_7794");
            }
         }
      }

      this.structures.select("netherite_sword");
   }
}
