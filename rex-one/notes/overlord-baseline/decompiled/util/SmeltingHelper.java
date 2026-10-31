package com.roothack.overlord.util;

import net.minecraft.class_1703;
import net.minecraft.class_1713;
import net.minecraft.class_1720;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_310;

public final class SmeltingHelper {
   public static final int INPUT_SLOT = 0;
   public static final int FUEL_SLOT = 1;
   public static final int OUTPUT_SLOT = 2;

   private SmeltingHelper() {
   }

   public static boolean load(class_310 mc, class_1792 fuel, class_1792 input) {
      if (mc.field_1724 != null && mc.field_1761 != null) {
         if (!(mc.field_1724.field_7512 instanceof class_1720 handler)) {
            return false;
         } else {
            int var7 = handler.field_7763;
            boolean placedFuel = moveInto(mc, var7, fuel, 1);
            boolean placedInput = moveInto(mc, var7, input, 0);
            return placedFuel && placedInput;
         }
      } else {
         return false;
      }
   }

   public static void collect(class_310 mc) {
      if (mc.field_1724 != null && mc.field_1761 != null) {
         if (mc.field_1724.field_7512 instanceof class_1720 handler) {
            mc.field_1761.method_2906(handler.field_7763, 2, 0, class_1713.field_7794, mc.field_1724);
         }
      }
   }

   private static boolean moveInto(class_310 mc, int syncId, class_1792 item, int targetSlot) {
      int invSlot = handlerSlotOf(mc, item);
      if (invSlot < 0) {
         return false;
      } else {
         mc.field_1761.method_2906(syncId, invSlot, 0, class_1713.field_7790, mc.field_1724);
         mc.field_1761.method_2906(syncId, targetSlot, 0, class_1713.field_7790, mc.field_1724);
         mc.field_1761.method_2906(syncId, invSlot, 0, class_1713.field_7790, mc.field_1724);
         return true;
      }
   }

   private static int handlerSlotOf(class_310 mc, class_1792 item) {
      class_1703 handler = mc.field_1724.field_7512;

      for (int i = 0; i < handler.field_7761.size(); i++) {
         class_1799 stack = handler.method_7611(i).method_7677();
         if (stack.method_31574(item)) {
            return i;
         }
      }

      return -1;
   }
}
