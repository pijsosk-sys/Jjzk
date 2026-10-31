package com.roothack.overlord.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.class_1703;
import net.minecraft.class_1713;
import net.minecraft.class_1792;
import net.minecraft.class_1802;
import net.minecraft.class_310;

public final class CraftingHelper {
   private CraftingHelper() {
   }

   public static Map<Integer, class_1792> pickaxe(class_1792 mat) {
      Map<Integer, class_1792> m = new LinkedHashMap<>();
      m.put(0, mat);
      m.put(1, mat);
      m.put(2, mat);
      m.put(4, class_1802.field_8600);
      m.put(7, class_1802.field_8600);
      return m;
   }

   public static Map<Integer, class_1792> sword(class_1792 mat) {
      Map<Integer, class_1792> m = new LinkedHashMap<>();
      m.put(1, mat);
      m.put(4, mat);
      m.put(7, class_1802.field_8600);
      return m;
   }

   public static Map<Integer, class_1792> helmet(class_1792 mat) {
      return ring(mat, 0, 1, 2, 3, 5);
   }

   public static Map<Integer, class_1792> chestplate(class_1792 mat) {
      return ring(mat, 0, 2, 3, 4, 5, 6, 7, 8);
   }

   public static Map<Integer, class_1792> leggings(class_1792 mat) {
      return ring(mat, 0, 1, 2, 3, 5, 6, 8);
   }

   public static Map<Integer, class_1792> boots(class_1792 mat) {
      return ring(mat, 3, 5, 6, 8);
   }

   public static Map<Integer, class_1792> furnace() {
      return ring(class_1802.field_20412, 0, 1, 2, 3, 5, 6, 7, 8);
   }

   public static Map<Integer, class_1792> craftingTable(class_1792 planks) {
      return ring(planks, 0, 1, 3, 4);
   }

   public static Map<Integer, class_1792> sticks(class_1792 planks) {
      return ring(planks, 0, 3);
   }

   public static Map<Integer, class_1792> planks(class_1792 log) {
      return ring(log, 0);
   }

   private static Map<Integer, class_1792> ring(class_1792 mat, int... slots) {
      Map<Integer, class_1792> m = new LinkedHashMap<>();

      for (int s : slots) {
         m.put(s, mat);
      }

      return m;
   }

   public static boolean craftShaped(class_310 mc, Map<Integer, class_1792> layout, int gridBase, int resultSlot) {
      if (mc.field_1724 != null && mc.field_1761 != null) {
         class_1703 handler = mc.field_1724.field_7512;
         if (handler == null) {
            return false;
         } else {
            int syncId = handler.field_7763;

            for (Entry<Integer, class_1792> cell : layout.entrySet()) {
               int target = gridBase + cell.getKey();
               int src = findInHandler(handler, cell.getValue());
               if (src < 0) {
                  return false;
               }

               mc.field_1761.method_2906(syncId, src, 0, class_1713.field_7790, mc.field_1724);
               mc.field_1761.method_2906(syncId, target, 1, class_1713.field_7790, mc.field_1724);
               mc.field_1761.method_2906(syncId, src, 0, class_1713.field_7790, mc.field_1724);
            }

            mc.field_1761.method_2906(syncId, resultSlot, 0, class_1713.field_7794, mc.field_1724);
            return true;
         }
      } else {
         return false;
      }
   }

   private static int findInHandler(class_1703 handler, class_1792 item) {
      for (int i = 0; i < handler.field_7761.size(); i++) {
         if (handler.method_7611(i).method_7677().method_31574(item)) {
            return i;
         }
      }

      return -1;
   }
}
