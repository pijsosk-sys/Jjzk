package com.roothack.overlord.util;

import net.minecraft.class_1268;
import net.minecraft.class_1269;
import net.minecraft.class_1661;
import net.minecraft.class_1713;
import net.minecraft.class_1792;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_239;
import net.minecraft.class_243;
import net.minecraft.class_310;
import net.minecraft.class_3965;
import net.minecraft.class_239.class_240;

public final class BlockPlacer {
   private BlockPlacer() {
   }

   public static boolean selectInHotbar(class_310 mc, class_1792 item) {
      if (mc.field_1724 == null) {
         return false;
      } else {
         class_1661 inv = mc.field_1724.method_31548();

         for (int i = 0; i < 9; i++) {
            if (inv.method_5438(i).method_31574(item)) {
               inv.method_61496(i);
               return true;
            }
         }

         int slot = InventoryHelper.slotOf(mc, item);
         if (slot >= 0 && mc.field_1761 != null) {
            mc.field_1761.method_2906(mc.field_1724.field_7498.field_7763, slot, 0, class_1713.field_7791, mc.field_1724);
            inv.method_61496(0);
            return inv.method_5438(0).method_31574(item);
         } else {
            return false;
         }
      }
   }

   public static boolean placeOnLookedAt(class_310 mc) {
      if (mc.field_1724 != null && mc.field_1761 != null) {
         class_239 hit = mc.field_1765;
         if (hit instanceof class_3965 blockHit && hit.method_17783() == class_240.field_1332) {
            class_1269 result = mc.field_1761.method_2896(mc.field_1724, class_1268.field_5808, blockHit);
            mc.field_1724.method_6104(class_1268.field_5808);
            return result.method_23665();
         }

         return false;
      } else {
         return false;
      }
   }

   public static class_3965 downwardHit(class_310 mc) {
      class_2338 pos = mc.field_1724.method_24515().method_10074();
      class_243 center = class_243.method_24953(pos).method_1031(0.0, 0.5, 0.0);
      return new class_3965(center, class_2350.field_11036, pos, false);
   }

   public static boolean openLookedAt(class_310 mc) {
      return placeOnLookedAt(mc);
   }
}
