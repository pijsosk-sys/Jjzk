package com.roothack.overlord.combat;

import java.util.List;
import java.util.Objects;

public final class MobDefense {
   private final MobDefense.View view;
   private Object world;
   private Object target;
   private long tick;
   private long lastScan = -100L;
   private long lastAttack = -100L;
   private long lastEquip = -100L;
   private boolean active;
   private int attacks;
   public String status = "Mob defence ready";

   public MobDefense() {
      this(new MinecraftDefense());
   }

   public MobDefense(MobDefense.View var1) {
      this.view = Objects.requireNonNull(var1);
   }

   public boolean tick() throws Exception {
      this.tick++;
      if (!this.view.ready()) {
         this.active = false;
         this.target = null;
         this.status = "Mob defence idle (world/menu/player)";
         return false;
      } else {
         Object var1 = this.view.world();
         if (this.world != var1) {
            this.view.release();
            this.world = var1;
            this.target = null;
            this.lastScan = -100L;
            this.lastAttack = -100L;
         }

         if (this.target != null && (!this.view.valid(this.target) || !this.view.visible(this.target) || this.view.distance(this.target) > 8.0)) {
            this.target = null;
         }

         if (this.tick - this.lastScan >= 3L) {
            this.lastScan = this.tick;
            Object var2 = null;
            double var3 = 8.0;

            for (Object var6 : this.view.nearby(8.0)) {
               if (this.view.valid(var6) && this.view.visible(var6)) {
                  double var7 = this.view.distance(var6);
                  if (Double.isFinite(var7) && var7 >= 0.0 && var7 < var3) {
                     var3 = var7;
                     var2 = var6;
                  }
               }
            }

            if (this.target == null || var2 != null && this.view.distance(this.target) > var3 + 0.75) {
               this.target = var2;
            }
         }

         this.active = this.target != null;
         if (!this.active) {
            this.view.release();
            this.status = "No visible hostile mobs nearby";
            return false;
         } else {
            String var9 = this.view.name(this.target);
            double var10 = this.view.reach();
            double var11 = Double.isFinite(var10) && var10 > 0.0 ? Math.min(3.0, var10) : 0.0;
            if (var11 == 0.0) {
               this.status = "No valid melee interaction range";
               return true;
            } else if (this.view.distance(this.target) > var11) {
               this.status = "Approaching " + var9;
               return true;
            } else {
               if (this.view.equip()) {
                  this.lastEquip = this.tick;
               }

               if (!this.view.aim(this.target, var11)) {
                  this.status = "Aiming at " + var9;
                  return true;
               } else if (this.tick - this.lastEquip >= 2L && this.tick - this.lastAttack >= 2L && !(this.view.cooldown() < 0.95)) {
                  if (this.view.valid(this.target) && this.view.visible(this.target) && this.view.distance(this.target) <= var11) {
                     this.view.attack(this.target);
                     this.lastAttack = this.tick;
                     this.attacks++;
                     this.status = "Fighting " + var9 + " | attacks=" + this.attacks;
                  }

                  return true;
               } else {
                  this.status = "Defending against " + var9 + " (weapon cooldown)";
                  return true;
               }
            }
         }
      }
   }

   public Object pathingCommand() throws Exception {
      return this.active && this.target != null && this.view.valid(this.target) ? this.view.approach(this.target) : null;
   }

   public void stop() {
      try {
         this.view.release();
      } catch (Exception var2) {
      }

      this.active = false;
      this.target = this.world = null;
      this.lastScan = this.lastAttack = this.lastEquip = -100L;
      this.status = "Mob defence stopped";
   }

   public interface View {
      boolean ready() throws Exception;

      Object world() throws Exception;

      List<?> nearby(double var1) throws Exception;

      boolean valid(Object var1) throws Exception;

      boolean visible(Object var1) throws Exception;

      double distance(Object var1) throws Exception;

      double reach() throws Exception;

      double cooldown() throws Exception;

      boolean equip() throws Exception;

      boolean aim(Object var1, double var2) throws Exception;

      void attack(Object var1) throws Exception;

      Object approach(Object var1) throws Exception;

      String name(Object var1) throws Exception;

      void release() throws Exception;
   }
}
