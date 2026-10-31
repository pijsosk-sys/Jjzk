package com.roothack.overlord.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Structures {
   private final Autopilot brain;
   private final Map<String, Structures.P> stations = new HashMap<>();
   private Structures.P frame;
   private Structures.P enchant;
   private String frameDimension = "";
   private final Map<String, Structures.P> portals = new HashMap<>();
   private final Set<String> visitedChests = new HashSet<>();
   private int scan;
   private Structures.P chest;
   private int chestTicks;

   public Structures(Autopilot var1) {
      this.brain = var1;
   }

   public static Structures.P feet() throws Exception {
      return new Structures.P(
         (int)Math.floor(Access.real(Access.player(), "method_23317")),
         (int)Math.floor(Access.real(Access.player(), "method_23318")),
         (int)Math.floor(Access.real(Access.player(), "method_23321"))
      );
   }

   private double distance(Structures.P var1) throws Exception {
      return Math.hypot(
         Math.hypot(Access.real(Access.player(), "method_23317") - (double)var1.x - 0.5, Access.real(Access.player(), "method_23321") - (double)var1.z - 0.5),
         Access.real(Access.player(), "method_23318") - (double)var1.y
      );
   }

   private Object ray(double var1, double var3, double var5) throws Exception {
      Object var7 = Access.player();
      Object var8 = Access.call(var7, "method_33571");
      Object var9 = Access.make("net.minecraft.class_243", var1, var3, var5);
      Object var10 = Access.make(
         "net.minecraft.class_3959",
         var8,
         var9,
         Access.field(Access.type("net.minecraft.class_3959$class_3960"), "field_17559"),
         Access.field(Access.type("net.minecraft.class_3959$class_242"), "field_1348"),
         var7
      );
      return Access.call(Access.world(), "method_17742", var10);
   }

   private boolean same(Object var1, Structures.P var2) throws Exception {
      return Access.num(var1, "method_10263") == var2.x && Access.num(var1, "method_10264") == var2.y && Access.num(var1, "method_10260") == var2.z;
   }

   private Object face(Structures.P var1, boolean var2) throws Exception {
      for (int[] var6 : new int[][]{{0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}}) {
         Structures.P var7 = var2 ? var1.add(var6[0], var6[1], var6[2]) : var1;
         if (!Access.air(var7.x, var7.y, var7.z)) {
            double var8 = (double)var7.x + 0.5 - (double)var6[0] * 0.499;
            double var10 = (double)var7.y + 0.5 - (double)var6[1] * 0.499;
            double var12 = (double)var7.z + 0.5 - (double)var6[2] * 0.499;
            Object var14 = Access.call(Access.player(), "method_33571");
            if (!(
               Math.pow(((Number)Access.field(var14, "field_1352")).doubleValue() - var8, 2.0)
                     + Math.pow(((Number)Access.field(var14, "field_1351")).doubleValue() - var10, 2.0)
                     + Math.pow(((Number)Access.field(var14, "field_1350")).doubleValue() - var12, 2.0)
                  > 20.25
            )) {
               Object var15 = this.ray(var8 + (double)var6[0] * 0.002, var10 + (double)var6[1] * 0.002, var12 + (double)var6[2] * 0.002);
               if (Access.type("net.minecraft.class_3965").isInstance(var15) && Access.call(var15, "method_17783").toString().equals("BLOCK")) {
                  Object var16 = Access.call(var15, "method_17777");
                  Object var17 = var2 ? Access.call(var16, "method_10093", Access.call(var15, "method_17780")) : var16;
                  if (this.same(var17, var1)) {
                     return var15;
                  }
               }
            }
         }
      }

      return null;
   }

   public boolean select(String var1) throws Exception {
      Object var2 = Access.inv();
      int var3 = -1;

      for (int var4 = 0; var4 < 36; var4++) {
         if (Access.id(Access.call(var2, "method_5438", var4)).equals(var1)) {
            var3 = var4;
            break;
         }
      }

      if (var3 < 0) {
         return false;
      } else if (var3 >= 9) {
         Access.close();
         Object var7 = Access.field(Access.player(), "field_7498");
         List var5 = (List)Access.field(var7, "field_7761");

         for (int var6 = 0; var6 < var5.size(); var6++) {
            if (Access.field(var5.get(var6), "field_7871") == var2 && ((Number)Access.field(var5.get(var6), "field_7875")).intValue() == var3) {
               this.brain.ops.click(var7, var6, 8, "field_7791");
               Access.call(var2, "method_61496", 8);
               return false;
            }
         }

         return false;
      } else {
         Access.call(var2, "method_61496", var3);
         return true;
      }
   }

   public boolean use(Structures.P var1, boolean var2, String var3) throws Exception {
      Object var4 = this.face(var1, var2);
      if (var4 == null) {
         this.brain.near(var1.x, var1.y, var1.z);
         return false;
      } else if (var3 != null && !this.select(var3)) {
         return false;
      } else {
         Object var5 = Access.call(var4, "method_17784");
         Object var6 = Access.call(Access.player(), "method_33571");
         double var7 = ((Number)Access.field(var5, "field_1352")).doubleValue() - ((Number)Access.field(var6, "field_1352")).doubleValue();
         double var9 = ((Number)Access.field(var5, "field_1351")).doubleValue() - ((Number)Access.field(var6, "field_1351")).doubleValue();
         double var11 = ((Number)Access.field(var5, "field_1350")).doubleValue() - ((Number)Access.field(var6, "field_1350")).doubleValue();
         float var13 = (float)(Math.toDegrees(Math.atan2(var11, var7)) - 90.0);
         float var14 = (float)(-Math.toDegrees(Math.atan2(var9, Math.hypot(var7, var11))));
         Access.call(Access.player(), "method_36456", var13);
         Access.call(Access.player(), "method_36457", var14);
         Object var15 = Access.call(Access.mc(), "method_1562");
         Object var16 = Access.make(
            "net.minecraft.class_2828$class_2831",
            var13,
            var14,
            (Boolean)Access.call(Access.player(), "method_24828"),
            (Boolean)Access.field(Access.player(), "field_5976")
         );
         Access.call(var15, "method_52787", var16);
         Object var17 = Access.call(
            Access.field(Access.mc(), "field_1761"), "method_2896", Access.player(), Access.field(Access.type("net.minecraft.class_1268"), "field_5808"), var4
         );
         this.brain.delay = 8;
         return (Boolean)Access.call(var17, "method_23665");
      }
   }

   public boolean available(String var1) throws Exception {
      Structures.P var2 = this.stations.get(Access.dimension() + var1);
      return var2 != null && Access.block(var2.x, var2.y, var2.z).equals(var1);
   }

   public boolean station(String var1, String var2) throws Exception {
      if (Access.type("net.minecraft." + var2).isInstance(this.brain.ops.handler())) {
         return true;
      } else if (Access.field(Access.mc(), "field_1755") != null) {
         Access.close();
         return false;
      } else {
         String var3 = Access.dimension() + var1;
         Structures.P var4 = this.stations.get(var3);
         if (var4 != null && !Access.block(var4.x, var4.y, var4.z).equals(var1)) {
            this.stations.remove(var3);
            var4 = null;
         }

         if (var4 == null) {
            Structures.P var5 = feet();

            label70:
            for (int var6 = -2; var6 <= 2; var6++) {
               for (int var7 = -4; var7 <= 4; var7++) {
                  for (int var8 = -4; var8 <= 4; var8++) {
                     if (Access.block(var5.x + var7, var5.y + var6, var5.z + var8).equals(var1)) {
                        var4 = var5.add(var7, var6, var8);
                        break label70;
                     }
                  }
               }
            }

            if (var4 != null) {
               this.stations.put(var3, var4);
            }
         }

         if (var4 != null) {
            this.use(var4, false, null);
            return false;
         } else {
            Structures.P var11 = feet();

            for (int[] var9 : new int[][]{{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {2, 1}, {-2, -1}}) {
               Structures.P var10 = var11.add(var9[0], 0, var9[1]);
               if (Access.air(var10.x, var10.y, var10.z) && !Access.air(var10.x, var10.y - 1, var10.z) && Access.air(var10.x, var10.y + 1, var10.z)) {
                  this.use(var10, true, var1);
                  if (Access.block(var10.x, var10.y, var10.z).equals(var1)) {
                     this.stations.put(var3, var10);
                  }

                  return false;
               }
            }

            this.brain.status = "Need flat ground for " + var1;
            return false;
         }
      }
   }

   public void rememberPortal() throws Exception {
      Structures.P var1 = feet();

      for (int var2 = -4; var2 <= 4; var2++) {
         for (int var3 = -4; var3 <= 4; var3++) {
            for (int var4 = -4; var4 <= 4; var4++) {
               if (Access.block(var1.x + var2, var1.y + var3, var1.z + var4).equals("nether_portal")) {
                  this.portals.put(Access.dimension(), var1.add(var2, var3, var4));
                  return;
               }
            }
         }
      }
   }

   public boolean portal(boolean var1) throws Exception {
      boolean var2 = Access.dimension().endsWith("the_nether");
      if (var2 == var1) {
         return true;
      } else {
         if (!this.frameDimension.equals(Access.dimension())) {
            this.frame = null;
            this.frameDimension = Access.dimension();
         }

         Structures.P var3 = this.portals.get(Access.dimension());
         if (var3 != null && Access.block(var3.x, var3.y, var3.z).equals("nether_portal")) {
            this.brain.go(var3.x, var3.y, var3.z);
            return false;
         } else {
            Structures.P var4 = feet();

            for (int var5 = 0; var5 < 256; var5++) {
               int var6 = this.scan++ % 15625;
               int var7 = var6 % 25 - 12;
               int var8 = var6 / 25 % 25 - 12;
               int var9 = var6 / 625 - 12;
               if (Access.block(var4.x + var7, var4.y + var9, var4.z + var8).equals("nether_portal")) {
                  this.portals.put(Access.dimension(), var4.add(var7, var9, var8));
                  this.brain.go(var4.x + var7, var4.y + var9, var4.z + var8);
                  return false;
               }
            }

            if (this.frame == null) {
               if (!this.brain.ensure("obsidian", 14, 0) || !this.brain.ensure("flint_and_steel", 1, 0)) {
                  return false;
               }

               var4 = feet();
               Structures.P var11 = var4.add(3, 0, 0);
               boolean var13 = true;

               for (int var19 = 0; var19 < 4; var19++) {
                  for (int var22 = 0; var22 < 5; var22++) {
                     if (!Access.air(var11.x + var19, var11.y + var22, var11.z)) {
                        var13 = false;
                     }
                  }
               }

               if (!var13) {
                  this.brain.explore();
                  this.brain.status = "Searching flat space for a portal";
                  return false;
               }

               for (int var20 = 0; var20 < 4; var20++) {
                  if (Access.air(var11.x + var20, var11.y - 1, var11.z)) {
                     var13 = false;
                  }
               }

               if (!var13) {
                  this.brain.explore();
                  return false;
               }

               this.frame = var11;
            }

            ArrayList var12 = new ArrayList();

            for (int var14 = 0; var14 < 4; var14++) {
               var12.add(this.frame.add(var14, 0, 0));
            }

            for (int var15 = 1; var15 < 4; var15++) {
               var12.add(this.frame.add(0, var15, 0));
               var12.add(this.frame.add(3, var15, 0));
            }

            for (int var16 = 0; var16 < 4; var16++) {
               var12.add(this.frame.add(var16, 4, 0));
            }

            for (Structures.P var21 : var12) {
               if (!Access.block(var21.x, var21.y, var21.z).equals("obsidian")) {
                  if (!this.brain.ensure("obsidian", 1, 0)) {
                     return false;
                  }

                  this.use(var21, true, "obsidian");
                  return false;
               }
            }

            Structures.P var18 = this.frame.add(1, 1, 0);
            if (Access.block(var18.x, var18.y, var18.z).equals("nether_portal")) {
               this.portals.put(Access.dimension(), var18);
               this.brain.go(var18.x, var18.y, var18.z);
               return false;
            } else if (!this.brain.ensure("flint_and_steel", 1, 0)) {
               return false;
            } else {
               this.use(var18, true, "flint_and_steel");
               return false;
            }
         }
      }
   }

   public boolean template() throws Exception {
      if (this.brain.ops.lootTemplate()) {
         return false;
      } else {
         if (Access.type("net.minecraft.class_1707").isInstance(this.brain.ops.handler())) {
            if (this.chest != null) {
               this.visitedChests.add(Access.dimension() + this.chest);
            }

            Access.close();
            this.chest = null;
         }

         Structures.P var1 = feet();
         if (this.chest != null) {
            if (++this.chestTicks <= 600) {
               this.use(this.chest, false, null);
               return false;
            }

            this.visitedChests.add(Access.dimension() + this.chest);
            this.chest = null;
         }

         for (int var2 = 0; var2 < 256; var2++) {
            int var3 = this.scan++ % 15625;
            Structures.P var4 = var1.add(var3 % 25 - 12, var3 / 625 - 12, var3 / 25 % 25 - 12);
            String var5 = Access.block(var4.x, var4.y, var4.z);
            if ((var5.equals("chest") || var5.equals("trapped_chest")) && !this.visitedChests.contains(Access.dimension() + var4)) {
               this.chest = var4;
               this.chestTicks = 0;
               this.use(var4, false, null);
               return false;
            }
         }

         this.brain.explore();
         this.brain.status = "Exploring the Nether for a bastion upgrade-template chest";
         return false;
      }
   }

   public boolean enchanting() throws Exception {
      if (this.enchant == null) {
         if (!this.brain.ensure("enchanting_table", 1, 0) || !this.brain.ensure("bookshelf", 15, 0)) {
            return false;
         }

         Structures.P var1 = feet();
         Structures.P var2 = var1.add(3, 0, 3);
         boolean var3 = true;

         for (int var4 = -2; var4 <= 2; var4++) {
            for (int var5 = -2; var5 <= 2; var5++) {
               for (int var6 = 0; var6 < 2; var6++) {
                  if (!Access.air(var2.x + var4, var2.y + var6, var2.z + var5)) {
                     var3 = false;
                  }
               }
            }
         }

         if (!var3) {
            this.brain.explore();
            this.brain.status = "Finding an empty 5x5 enchanting area";
            return false;
         }

         this.enchant = var2;
      }

      if (Access.block(this.enchant.x, this.enchant.y, this.enchant.z).equals("enchanting_table")) {
         int var7 = 0;

         for (int var8 = -2; var8 <= 2; var8++) {
            for (int var9 = -2; var9 <= 2; var9++) {
               if ((Math.abs(var8) == 2 || Math.abs(var9) == 2) && var7++ < 15) {
                  Structures.P var10 = this.enchant.add(var8, 0, var9);
                  if (!Access.block(var10.x, var10.y, var10.z).equals("bookshelf")) {
                     if (!this.brain.ensure("bookshelf", 1, 0)) {
                        return false;
                     }

                     this.use(var10, true, "bookshelf");
                     return false;
                  }
               }
            }
         }

         this.stations.put(Access.dimension() + "enchanting_table", this.enchant);
         return this.station("enchanting_table", "class_1718");
      } else if (!this.brain.ensure("enchanting_table", 1, 0)) {
         return false;
      } else {
         this.use(this.enchant, true, "enchanting_table");
         return false;
      }
   }

   public static record P(int x, int y, int z) {
      Structures.P add(int var1, int var2, int var3) {
         return new Structures.P(this.x + var1, this.y + var2, this.z + var3);
      }
   }
}
