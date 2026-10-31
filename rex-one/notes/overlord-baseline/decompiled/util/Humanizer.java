package com.roothack.overlord.util;

import java.util.Random;
import net.minecraft.class_1297;
import net.minecraft.class_243;
import net.minecraft.class_310;
import net.minecraft.class_3532;

public final class Humanizer {
   private final Random rng = new Random();
   private double turnSpeed = 0.28;
   private double turnJitter = 0.08;
   private double aimNoiseDeg = 0.6;

   public Humanizer() {
   }

   public void smoothLookTo(class_310 mc, float targetYaw, float targetPitch) {
      if (mc.field_1724 != null) {
         float curYaw = mc.field_1724.method_36454();
         float curPitch = mc.field_1724.method_36455();
         float dYaw = class_3532.method_15393(targetYaw - curYaw);
         float dPitch = targetPitch - curPitch;
         double frac = this.turnSpeed + (this.rng.nextDouble() - 0.5) * 2.0 * this.turnJitter;
         frac = class_3532.method_15350(frac, 0.08, 0.6);
         float noiseY = (float)((this.rng.nextDouble() - 0.5) * 2.0 * this.aimNoiseDeg);
         float noiseP = (float)((this.rng.nextDouble() - 0.5) * this.aimNoiseDeg);
         float newYaw = curYaw + (float)((double)dYaw * frac) + noiseY;
         float newPitch = class_3532.method_15363(curPitch + (float)((double)dPitch * frac) + noiseP, -90.0F, 90.0F);
         mc.field_1724.method_36456(newYaw);
         mc.field_1724.method_36457(newPitch);
      }
   }

   public boolean isAimedAt(class_310 mc, float targetYaw, float targetPitch, float toleranceDeg) {
      if (mc.field_1724 == null) {
         return false;
      } else {
         float dy = Math.abs(class_3532.method_15393(targetYaw - mc.field_1724.method_36454()));
         float dp = Math.abs(targetPitch - mc.field_1724.method_36455());
         return dy <= toleranceDeg && dp <= toleranceDeg;
      }
   }

   public static float[] anglesTo(class_310 mc, class_243 point) {
      class_243 eye = mc.field_1724.method_33571();
      double dx = point.field_1352 - eye.field_1352;
      double dy = point.field_1351 - eye.field_1351;
      double dz = point.field_1350 - eye.field_1350;
      double dist = Math.sqrt(dx * dx + dz * dz);
      float yaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
      float pitch = (float)(-Math.toDegrees(Math.atan2(dy, dist)));
      return new float[]{yaw, pitch};
   }

   public static class_243 aimPointFor(class_1297 e) {
      return new class_243(e.method_23317(), e.method_23318() + (double)e.method_17682() * 0.6, e.method_23321());
   }

   public int nextDelay(int baseTicks, double spreadFraction) {
      double spread = (double)baseTicks * spreadFraction;
      int d = (int)Math.round((double)baseTicks + (this.rng.nextDouble() - 0.5) * 2.0 * spread);
      return Math.max(1, d);
   }

   public void idleTwitch(class_310 mc, double chance) {
      if (mc.field_1724 != null && !(this.rng.nextDouble() > chance)) {
         float dy = (float)((this.rng.nextDouble() - 0.5) * 6.0);
         float dp = (float)((this.rng.nextDouble() - 0.5) * 3.0);
         mc.field_1724.method_36456(mc.field_1724.method_36454() + dy);
         mc.field_1724.method_36457(class_3532.method_15363(mc.field_1724.method_36455() + dp, -90.0F, 90.0F));
      }
   }

   public Random rng() {
      return this.rng;
   }
}
