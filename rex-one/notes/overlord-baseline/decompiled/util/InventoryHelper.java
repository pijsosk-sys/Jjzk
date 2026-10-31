package com.roothack.overlord.util;

import net.minecraft.class_1661;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_310;

public final class InventoryHelper {
   private InventoryHelper() {
   }

   public static int count(class_310 mc, class_1792 item) {
      if (mc.field_1724 == null) {
         return 0;
      } else {
         int total = 0;
         class_1661 inv = mc.field_1724.method_31548();

         for (int i = 0; i < inv.method_5439(); i++) {
            class_1799 s = inv.method_5438(i);
            if (s.method_31574(item)) {
               total += s.method_7947();
            }
         }

         return total;
      }
   }

   public static boolean has(class_310 mc, class_1792 item, int amount) {
      return count(mc, item) >= amount;
   }

   public static int slotOf(class_310 mc, class_1792 item) {
      if (mc.field_1724 == null) {
         return -1;
      } else {
         class_1661 inv = mc.field_1724.method_31548();

         for (int i = 0; i < inv.method_5439(); i++) {
            if (inv.method_5438(i).method_31574(item)) {
               return i;
            }
         }

         return -1;
      }
   }

   public static boolean hasEmptySlot(class_310 mc) {
      return mc.field_1724 == null ? false : mc.field_1724.method_31548().method_7376() >= 0;
   }
}
