package com.roothack.overlord.combat;

import com.roothack.overlord.util.Humanizer;
import net.minecraft.class_1268;
import net.minecraft.class_1297;
import net.minecraft.class_1309;
import net.minecraft.class_1569;
import net.minecraft.class_1588;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_310;
import net.minecraft.class_5354;

public final class CombatModule {
   private final Humanizer human;
   private double engageRange = 5.0;
   private double reach = 3.1;
   private float swingArmedAt = 1.0F;
   private int swingGate = 0;
   private class_1297 target;

   public CombatModule(Humanizer human) {
      this.human = human;
   }

   public boolean tick(class_310 mc) {
      if (mc.field_1724 != null && mc.field_1687 != null && mc.field_1761 != null) {
         if (!mc.field_1724.method_29504() && !(mc.field_1724.method_6032() <= 0.0F)) {
            this.target = this.pickTarget(mc);
            if (this.target == null) {
               return false;
            } else {
               class_243 aim = Humanizer.aimPointFor(this.target);
               float[] ang = Humanizer.anglesTo(mc, aim);
               this.human.smoothLookTo(mc, ang[0], ang[1]);
               if (this.swingGate > 0) {
                  this.swingGate--;
               }

               boolean inReach = mc.field_1724.method_5858(this.target) <= this.reach * this.reach;
               boolean aimed = this.human.isAimedAt(mc, ang[0], ang[1], 6.0F);
               boolean cooled = mc.field_1724.method_7261(0.0F) >= this.swingArmedAt;
               if (inReach && aimed && cooled && this.swingGate == 0) {
                  mc.field_1761.method_2918(mc.field_1724, this.target);
                  mc.field_1724.method_6104(class_1268.field_5808);
                  this.swingGate = this.human.nextDelay(3, 0.5);
               }

               return true;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public class_1297 currentTarget() {
      return this.target;
   }

   private class_1297 pickTarget(class_310 mc) {
      class_238 area = mc.field_1724.method_5829().method_1014(this.engageRange);
      class_1297 best = null;
      double bestScore = Double.MAX_VALUE;

      for (class_1297 e : mc.field_1687.method_8333(mc.field_1724, area, this::isHostile)) {
         if (e instanceof class_1309) {
            class_1309 le = (class_1309)e;
            if (!le.method_29504()) {
               double d = mc.field_1724.method_5858(e);
               if (d < bestScore) {
                  bestScore = d;
                  best = e;
               }
            }
         }
      }

      return best;
   }

   private boolean isHostile(class_1297 e) {
      if (e instanceof class_1569) {
         return true;
      } else if (e instanceof class_1588) {
         return true;
      } else {
         if (e instanceof class_5354 a && a.method_29508() != null) {
            return true;
         }

         return false;
      }
   }
}
