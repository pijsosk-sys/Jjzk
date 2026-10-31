package com.roothack.overlord.util;

import net.minecraft.class_1703;
import net.minecraft.class_1713;
import net.minecraft.class_1792;
import net.minecraft.class_310;
import net.minecraft.class_4862;

public final class SmithingHelper {
   public static final int TEMPLATE_SLOT = 0;
   public static final int BASE_SLOT = 1;
   public static final int ADDITION_SLOT = 2;
   public static final int RESULT_SLOT = 3;

   private SmithingHelper() {
   }

   public static boolean upgrade(class_310 mc, class_1792 template, class_1792 base, class_1792 addition) {
      if (mc.field_1724 != null && mc.field_1761 != null) {
         if (mc.field_1724.field_7512 instanceof class_4862 handler) {
            int var6 = handler.field_7763;
            if (!place(mc, var6, template, 0)) {
               return false;
            } else if (!place(mc, var6, base, 1)) {
               return false;
            } else if (!place(mc, var6, addition, 2)) {
               return false;
            } else {
               mc.field_1761.method_2906(var6, 3, 0, class_1713.field_7794, mc.field_1724);
               return true;
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
         mc.field_1761.method_2906(syncId, target, 1, class_1713.field_7790, mc.field_1724);
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
