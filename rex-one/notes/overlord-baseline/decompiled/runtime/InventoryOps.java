package com.roothack.overlord.runtime;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

public final class InventoryOps {
   private final ArrayDeque<InventoryOps.Click> queue = new ArrayDeque<>();
   private Object locked;
   private long next;
   private int wait;
   private String output = "";
   private String mode = "";
   public String status = "";

   public InventoryOps() {
   }

   public boolean busy() {
      return !this.queue.isEmpty() || !this.output.isEmpty();
   }

   public void clear() {
      this.queue.clear();
      this.output = "";
      this.locked = null;
   }

   public Object handler() throws Exception {
      return Access.field(Access.player(), "field_7512");
   }

   public Object stack(Object var1, int var2) throws Exception {
      return Access.call(Access.call(var1, "method_7611", var2), "method_7677");
   }

   private boolean empty(Object var1) throws Exception {
      return (Boolean)Access.call(var1, "method_7960");
   }

   public int source(Object var1, String var2) throws Exception {
      List var3 = (List)Access.field(var1, "field_7761");

      for (int var4 = 0; var4 < var3.size(); var4++) {
         Object var5 = var3.get(var4);
         if (Access.field(var5, "field_7871") == Access.inv() && Access.id(Access.call(var5, "method_7677")).equals(var2)) {
            return var4;
         }
      }

      return -1;
   }

   public void click(Object var1, int var2, int var3, String var4) throws Exception {
      Access.call(
         Access.field(Access.mc(), "field_1761"),
         "method_2906",
         ((Number)Access.field(var1, "field_7763")).intValue(),
         var2,
         var3,
         Access.field(Access.type("net.minecraft.class_1713"), var4),
         Access.player()
      );
   }

   public boolean pump(long var1) throws Exception {
      if (!this.busy()) {
         return false;
      } else if (this.handler() != this.locked) {
         this.clear();
         throw new IllegalStateException("Workstation closed during inventory transaction");
      } else if (var1 < this.next) {
         return true;
      } else {
         this.next = var1 + 2L;
         if (!this.queue.isEmpty()) {
            InventoryOps.Click var8 = this.queue.removeFirst();
            this.click(this.locked, var8.slot, var8.button, var8.action);
            return true;
         } else {
            int var3 = this.mode.equals("smith") ? 3 : 0;
            Object var4 = this.stack(this.locked, var3);
            boolean var5 = Access.id(var4).equals(this.output) || this.output.equals("planks") && Access.id(var4).endsWith("_planks");
            if (this.mode.equals("enchant")) {
               if (var5 && (Boolean)Access.call(var4, "method_7942")) {
                  this.click(this.locked, 0, 0, "field_7794");
                  this.clear();
                  return true;
               }

               int[] var6 = (int[])Access.field(this.locked, "field_7808");
               int var7 = ((Number)Access.field(Access.player(), "field_7520")).intValue();
               if (var6[2] >= 30 && var7 >= var6[2] && Access.num(this.stack(this.locked, 1), "method_7947") >= 3 && this.wait % 10 == 0) {
                  Access.call(Access.field(Access.mc(), "field_1761"), "method_2900", ((Number)Access.field(this.locked, "field_7763")).intValue(), 2);
               }
            } else if (var5) {
               this.click(this.locked, var3, 0, "field_7794");
               this.clear();
               return true;
            }

            if (++this.wait > 100) {
               this.clear();
               throw new IllegalStateException("Server did not return the expected crafting/enchanting result");
            } else {
               return true;
            }
         }
      }
   }

   private boolean clean(Object var1, int var2, int var3) throws Exception {
      if (!this.empty(Access.call(var1, "method_34255"))) {
         throw new IllegalStateException("Clear the cursor stack before automation");
      } else {
         for (int var4 = var2; var4 <= var3; var4++) {
            if (!this.empty(this.stack(var1, var4))) {
               this.click(var1, var4, 0, "field_7794");
               return false;
            }
         }

         return true;
      }
   }

   private boolean prepare(Object var1, Map<Integer, String> var2, String var3, String var4) throws Exception {
      HashMap var5 = new HashMap();
      HashMap var6 = new HashMap();
      List var7 = (List)Access.field(var1, "field_7761");

      for (int var8 = 0; var8 < var7.size(); var8++) {
         Object var9 = var7.get(var8);
         if (Access.field(var9, "field_7871") == Access.inv()) {
            Object var10 = Access.call(var9, "method_7677");
            var5.computeIfAbsent(Access.id(var10), var0 -> new ArrayList<>()).add(var8);
            var6.put(var8, Access.num(var10, "method_7947"));
         }
      }

      ArrayDeque var14 = new ArrayDeque();

      for (Entry var16 : var2.entrySet()) {
         int var11 = -1;

         for (int var13 : var5.getOrDefault(var16.getValue(), List.of())) {
            if ((Integer)var6.get(var13) > 0) {
               var11 = var13;
               var6.put(var13, (Integer)var6.get(var13) - 1);
               break;
            }
         }

         if (var11 < 0) {
            return false;
         }

         var14.add(new InventoryOps.Click(var11, 0, "field_7790"));
         var14.add(new InventoryOps.Click((Integer)var16.getKey(), 1, "field_7790"));
         var14.add(new InventoryOps.Click(var11, 0, "field_7790"));
      }

      this.locked = var1;
      this.queue.addAll(var14);
      this.output = var3;
      this.mode = var4;
      this.wait = 0;
      return true;
   }

   public boolean craft(Recipes.Recipe var1, Map<String, Integer> var2) throws Exception {
      Object var3 = this.handler();
      boolean var4 = Access.type("net.minecraft.class_1714").isInstance(var3);
      boolean var5 = Access.type("net.minecraft.class_1723").isInstance(var3);
      if ((var4 || var5) && (var4 || var1.small())) {
         if (!this.clean(var3, 1, var4 ? 9 : 4)) {
            return false;
         } else {
            LinkedHashMap var6 = new LinkedHashMap();
            HashMap var7 = new HashMap(var2);

            for (Entry var9 : var1.cells().entrySet()) {
               String var10 = Recipes.actual(var7, (String)var9.getValue());
               if (var7.getOrDefault(var10, 0) <= 0) {
                  return false;
               }

               var6.put(Recipes.gridSlot((Integer)var9.getKey(), var4), var10);
               var7.computeIfPresent(var10, (var0, var1x) -> var1x <= 1 ? null : var1x - 1);
            }

            return this.prepare(var3, var6, var1.output(), "craft");
         }
      } else {
         return false;
      }
   }

   public boolean smith(String var1) throws Exception {
      Object var2 = this.handler();
      return Access.type("net.minecraft.class_4862").isInstance(var2) && this.clean(var2, 0, 2)
         ? this.prepare(var2, Map.of(0, "netherite_upgrade_smithing_template", 1, var1, 2, "netherite_ingot"), var1.replace("diamond_", "netherite_"), "smith")
         : false;
   }

   public boolean enchant(String var1) throws Exception {
      Object var2 = this.handler();
      if (Access.type("net.minecraft.class_1718").isInstance(var2) && this.clean(var2, 0, 1)) {
         LinkedHashMap var3 = new LinkedHashMap();
         var3.put(0, var1);
         int var4 = this.source(var2, "lapis_lazuli");
         if (var4 < 0 || Access.num(this.stack(var2, var4), "method_7947") < 3) {
            return false;
         } else if (!this.prepare(var2, var3, var1, "enchant")) {
            return false;
         } else {
            this.queue.add(new InventoryOps.Click(var4, 0, "field_7790"));

            for (int var5 = 0; var5 < 3; var5++) {
               this.queue.add(new InventoryOps.Click(1, 1, "field_7790"));
            }

            this.queue.add(new InventoryOps.Click(var4, 0, "field_7790"));
            return true;
         }
      } else {
         return false;
      }
   }

   public boolean smelt(String var1) throws Exception {
      Object var2 = this.handler();
      if (!Access.type("net.minecraft.class_1720").isInstance(var2)) {
         return false;
      } else if (!this.empty(this.stack(var2, 2))) {
         this.click(var2, 2, 0, "field_7794");
         return true;
      } else {
         int var3 = this.source(var2, var1);
         if (this.empty(this.stack(var2, 0)) && var3 >= 0) {
            this.click(var2, var3, 0, "field_7794");
            return true;
         } else {
            if (this.empty(this.stack(var2, 1))) {
               var3 = this.source(var2, "coal");
               if (var3 < 0) {
                  var3 = this.source(var2, "charcoal");
               }

               if (var3 >= 0) {
                  this.click(var2, var3, 0, "field_7794");
                  return true;
               }
            }

            return true;
         }
      }
   }

   public boolean lootTemplate() throws Exception {
      Object var1 = this.handler();
      if (!Access.type("net.minecraft.class_1707").isInstance(var1)) {
         return false;
      } else {
         List var2 = (List)Access.field(var1, "field_7761");

         for (int var3 = 0; var3 < var2.size(); var3++) {
            Object var4 = var2.get(var3);
            if (Access.field(var4, "field_7871") != Access.inv()) {
               String var5 = Access.id(Access.call(var4, "method_7677"));
               if (Set.of("netherite_upgrade_smithing_template", "diamond", "gold_ingot", "netherite_ingot", "obsidian").contains(var5)) {
                  this.click(var1, var3, 0, "field_7794");
                  return true;
               }
            }
         }

         return false;
      }
   }

   private static record Click(int slot, int button, String action) {
   }
}
