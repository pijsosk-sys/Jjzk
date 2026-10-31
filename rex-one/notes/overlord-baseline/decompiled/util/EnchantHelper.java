package com.roothack.overlord.util;

import net.minecraft.class_1703;
import net.minecraft.class_1713;
import net.minecraft.class_1718;
import net.minecraft.class_1792;
import net.minecraft.class_1802;
import net.minecraft.class_310;

public final class EnchantHelper {
   public static final int ITEM_SLOT = 0;
   public static final int LAPIS_SLOT = 1;
   public static final int TOP_OPTION = 2;

   private EnchantHelper() {
   }

   public static boolean enchantTopTier(class_310 mc, class_1792 gear) {
      if (mc.field_1724 != null && mc.field_1761 != null) {
         if (mc.field_1724.field_7512 instanceof class_1718 handler) {
            int var4 = handler.field_7763;
            if (!place(mc, var4, gear, 0)) {
               return false;
            } else {
               place(mc, var4, class_1802.field_8759, 1);
               if (handler.field_7808[2] < 1) {
                  return false;
               } else {
                  mc.field_1761.method_2900(var4, 2);
                  mc.field_1761.method_2906(var4, 0, 0, class_1713.field_7794, mc.field_1724);
                  return true;
               }
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static boolean place(class_310 mc, int syncId, class_1792 item, int target) {
      int src = handlerSlotOf(mc, item);
      if (src < 0) {
         return false;
      } else {
         mc.field_1761.method_2906(syncId, src, 0, class_1713.field_7790, mc.field_1724);
         mc.field_1761.method_2906(syncId, target, 0, class_1713.field_7790, mc.field_1724);
         mc.field_1761.method_2906(syncId, src, 0, class_1713.field_7790, mc.field_1724);
         return true;
      }
   }

   private static int handlerSlotOf(class_310 mc, class_1792 item) {
      class_1703 handler = mc.field_1724.field_7512;

      for (int i = 0; i < handler.field_7761.size(); i++) {
         if (handler.method_7611(i).method_7677().method_31574(item)) {
            return i;
         }
      }

      return -1;
   }
}
