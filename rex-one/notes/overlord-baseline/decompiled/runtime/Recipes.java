package com.roothack.overlord.runtime;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

public final class Recipes {
   public static final List<String> GEAR = List.of("helmet", "chestplate", "leggings", "boots", "sword", "pickaxe");
   private static final Map<String, Recipes.Recipe> ALL = new LinkedHashMap<>();

   public Recipes() {
   }

   private static void add(String var0, int var1, Object... var2) {
      LinkedHashMap var3 = new LinkedHashMap();

      for (byte var4 = 0; var4 < var2.length; var4 += 2) {
         var3.put((Integer)var2[var4], (String)var2[var4 + 1]);
      }

      ALL.put(var0, new Recipes.Recipe(var0, var1, Collections.unmodifiableMap(var3)));
   }

   private static void shape(String var0, int var1, String var2, int... var3) {
      Object[] var4 = new Object[var3.length * 2];

      for (int var5 = 0; var5 < var3.length; var5++) {
         var4[var5 * 2] = var3[var5];
         var4[var5 * 2 + 1] = var2;
      }

      add(var0, var1, var4);
   }

   public static Recipes.Recipe get(String var0) {
      return ALL.get(var0);
   }

   public static Collection<Recipes.Recipe> all() {
      return ALL.values();
   }

   public static int count(Map<String, Integer> var0, String var1) {
      if (var1.equals("logs")) {
         return var0.entrySet()
            .stream()
            .filter(var0x -> ((String)var0x.getKey()).endsWith("_log") || ((String)var0x.getKey()).endsWith("_stem"))
            .mapToInt(Entry::getValue)
            .sum();
      } else {
         return var1.equals("planks")
            ? var0.entrySet().stream().filter(var0x -> ((String)var0x.getKey()).endsWith("_planks")).mapToInt(Entry::getValue).sum()
            : var0.getOrDefault(var1, 0);
      }
   }

   public static String actual(Map<String, Integer> var0, String var1) {
      return !var1.equals("logs") && !var1.equals("planks")
         ? var1
         : var0.keySet()
            .stream()
            .filter(var1x -> var1.equals("logs") ? var1x.endsWith("_log") || var1x.endsWith("_stem") : var1x.endsWith("_planks"))
            .sorted()
            .findFirst()
            .orElse(var1);
   }

   public static boolean complete(Map<String, Integer> var0, Set<String> var1) {
      return GEAR.stream().allMatch(var2 -> var0.getOrDefault("netherite_" + var2, 0) > 0 && var1.contains("netherite_" + var2));
   }

   public static int gridSlot(int var0, boolean var1) {
      if (var0 >= 0 && var0 <= 8 && (var1 || var0 % 3 < 2 && var0 / 3 < 2)) {
         return 1 + (var1 ? var0 : var0 / 3 * 2 + var0 % 3);
      } else {
         throw new IllegalArgumentException("Recipe does not fit grid");
      }
   }

   static {
      shape("planks", 4, "logs", 0);
      shape("stick", 4, "planks", 0, 3);
      shape("crafting_table", 1, "planks", 0, 1, 3, 4);
      shape("furnace", 1, "cobblestone", 0, 1, 2, 3, 5, 6, 7, 8);

      for (String var1 : List.of("wooden", "stone", "iron", "diamond")) {
         String var2 = switch (var1) {
            case "wooden" -> "planks";
            case "stone" -> "cobblestone";
            case "iron" -> "iron_ingot";
            default -> "diamond";
         };
         add(var1 + "_pickaxe", 1, 0, var2, 1, var2, 2, var2, 4, "stick", 7, "stick");
         add(var1 + "_sword", 1, 1, var2, 4, var2, 7, "stick");
         if (var1.equals("iron") || var1.equals("diamond")) {
            shape(var1 + "_helmet", 1, var2, 0, 1, 2, 3, 5);
            shape(var1 + "_chestplate", 1, var2, 0, 2, 3, 4, 5, 6, 7, 8);
            shape(var1 + "_leggings", 1, var2, 0, 1, 2, 3, 5, 6, 8);
            shape(var1 + "_boots", 1, var2, 3, 5, 6, 8);
         }
      }

      add("bucket", 1, 0, "iron_ingot", 2, "iron_ingot", 4, "iron_ingot");
      add("flint_and_steel", 1, 0, "iron_ingot", 4, "flint");
      add("smithing_table", 1, 0, "iron_ingot", 1, "iron_ingot", 3, "planks", 4, "planks", 6, "planks", 7, "planks");
      shape("paper", 3, "sugar_cane", 0, 1, 2);
      add("book", 1, 0, "paper", 1, "paper", 3, "paper", 4, "leather");
      add("bookshelf", 1, 0, "planks", 1, "planks", 2, "planks", 3, "book", 4, "book", 5, "book", 6, "planks", 7, "planks", 8, "planks");
      add("enchanting_table", 1, 1, "book", 3, "diamond", 4, "obsidian", 5, "diamond", 6, "obsidian", 7, "obsidian", 8, "obsidian");
      add(
         "netherite_ingot",
         1,
         0,
         "netherite_scrap",
         1,
         "netherite_scrap",
         2,
         "netherite_scrap",
         3,
         "netherite_scrap",
         4,
         "gold_ingot",
         5,
         "gold_ingot",
         6,
         "gold_ingot",
         7,
         "gold_ingot"
      );
      add(
         "netherite_upgrade_smithing_template",
         2,
         0,
         "diamond",
         1,
         "netherite_upgrade_smithing_template",
         2,
         "diamond",
         3,
         "diamond",
         4,
         "netherrack",
         5,
         "diamond",
         6,
         "diamond",
         7,
         "diamond",
         8,
         "diamond"
      );
   }

   public static record Recipe(String output, int count, Map<Integer, String> cells) {
      public Map<String, Integer> needs() {
         LinkedHashMap var1 = new LinkedHashMap();
         this.cells.values().forEach(var1x -> var1.merge(var1x, 1, Integer::sum));
         return var1;
      }

      public boolean small() {
         return this.cells.keySet().stream().allMatch(var0 -> var0 % 3 < 2 && var0 / 3 < 2);
      }
   }
}
