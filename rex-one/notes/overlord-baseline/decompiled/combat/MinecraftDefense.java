package com.roothack.overlord.combat;

import com.roothack.overlord.runtime.Access;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import rex.CombatAccess;
import rex.CombatTools;
import rex.InteractionFaces;
import rex.Reach;
import rex.CombatTools.Weapon;
import rex.CombatTools.WeaponPreference;

public final class MinecraftDefense implements MobDefense.View {
   private Object equippedPlayer;
   private int original = -1;
   private int managed = -1;

   public MinecraftDefense() {
   }

   private Object player() throws Exception {
      return Access.player();
   }

   @Override
   public Object world() throws Exception {
      return Access.world();
   }

   @Override
   public boolean ready() throws Exception {
      Object var1 = this.player();
      return var1 != null
         && this.world() != null
         && Access.field(Access.mc(), "field_1761") != null
         && Access.field(Access.mc(), "field_1755") == null
         && (Boolean)Access.call(var1, "method_5805");
   }

   @Override
   public List<?> nearby(double var1) throws Exception {
      return (List<?>)Access.call(
         this.world(), "method_8333", this.player(), Access.call(Access.call(this.player(), "method_5829"), "method_1014", var1), (Predicate<Object>)var1x -> {
            try {
               return this.valid(var1x);
            } catch (Exception var3) {
               return false;
            }
         }
      );
   }

   @Override
   public boolean valid(Object var1) throws Exception {
      return var1 != null && Access.type("net.minecraft.class_1569").isInstance(var1) && (Boolean)Access.call(var1, "method_5805");
   }

   @Override
   public boolean visible(Object var1) throws Exception {
      return (Boolean)Access.call(this.player(), "method_6057", var1);
   }

   @Override
   public double distance(Object var1) throws Exception {
      Object var2 = Access.call(this.player(), "method_33571");
      Object var3 = Access.call(var1, "method_5829");
      double var4 = this.num(var2, "field_1352");
      double var6 = this.num(var2, "field_1351");
      double var8 = this.num(var2, "field_1350");
      return CombatTools.distance(
         CombatTools.closest(
            var4,
            var6,
            var8,
            this.num(var3, "field_1323"),
            this.num(var3, "field_1322"),
            this.num(var3, "field_1321"),
            this.num(var3, "field_1320"),
            this.num(var3, "field_1325"),
            this.num(var3, "field_1324")
         ),
         var4,
         var6,
         var8
      );
   }

   private double num(Object var1, String var2) throws Exception {
      return ((Number)Access.field(var1, var2)).doubleValue();
   }

   @Override
   public double reach() throws Exception {
      return Reach.vanillaEntityRange(this.player());
   }

   @Override
   public double cooldown() throws Exception {
      return Access.real(this.player(), "method_7261", 0.0F);
   }

   @Override
   public boolean equip() throws Exception {
      Object var1 = this.player();
      Object var2 = Access.call(var1, "method_31548");
      int var3 = Access.num(var2, "method_67532");
      ArrayList var4 = new ArrayList();

      for (int var5 = 0; var5 < 9; var5++) {
         Object var6 = Access.call(var2, "method_5438", var5);
         String var7 = "minecraft:" + Access.id(var6);
         if (CombatTools.weapon(var7)) {
            var4.add(new Weapon(var5, var7));
         }
      }

      Optional var8 = CombatTools.chooseWeapon(var4, WeaponPreference.SMART, false, var3);
      if (!var8.isEmpty() && ((Weapon)var8.get()).slot() != var3) {
         if (this.equippedPlayer != var1 || this.original < 0) {
            this.equippedPlayer = var1;
            this.original = var3;
         }

         this.managed = ((Weapon)var8.get()).slot();
         Access.call(var2, "method_61496", this.managed);
         return true;
      } else {
         return false;
      }
   }

   @Override
   public boolean aim(Object var1, double var2) throws Exception {
      Object var4 = this.player();
      Object var5 = Access.call(var4, "method_33571");
      Object var6 = Access.call(var1, "method_5829");
      double var7 = (this.num(var6, "field_1323") + this.num(var6, "field_1320")) * 0.5;
      double var9 = (this.num(var6, "field_1322") + this.num(var6, "field_1325")) * 0.5;
      double var11 = (this.num(var6, "field_1321") + this.num(var6, "field_1324")) * 0.5;
      double var13 = var7 - this.num(var5, "field_1352");
      double var15 = var9 - this.num(var5, "field_1351");
      double var17 = var11 - this.num(var5, "field_1350");
      Access.call(var4, "method_36456", (float)(Math.toDegrees(Math.atan2(var17, var13)) - 90.0));
      Access.call(var4, "method_36457", (float)(-Math.toDegrees(Math.atan2(var15, Math.hypot(var13, var17)))));
      InteractionFaces.syncLook(Access.mc(), var4);
      return CombatAccess.rayEntity(var4, var1, var2);
   }

   @Override
   public void attack(Object var1) throws Exception {
      Object var2 = this.player();
      Access.call(Access.field(Access.mc(), "field_1761"), "method_2918", var2, var1);
      Access.call(var2, "method_6104", Access.field(Access.type("net.minecraft.class_1268"), "field_5808"));
   }

   @Override
   public Object approach(Object var1) throws Exception {
      double var2 = Math.max(0.0, Math.min(3.0, this.reach()));
      return this.distance(var1) <= var2
         ? Access.command("REQUEST_PAUSE", null)
         : Access.command(
            "SET_GOAL_AND_PATH",
            Access.make(
               "baritone.api.pathing.goals.GoalNear",
               (int)Math.floor(Access.real(var1, "method_23317")),
               (int)Math.floor(Access.real(var1, "method_23318")),
               (int)Math.floor(Access.real(var1, "method_23321")),
               1
            )
         );
   }

   @Override
   public String name(Object var1) {
      try {
         return String.valueOf(Access.call(Access.call(var1, "method_5477"), "getString"));
      } catch (Exception var3) {
         return var1.getClass().getSimpleName();
      }
   }

   @Override
   public void release() throws Exception {
      Object var1 = this.player();
      if (var1 != null && var1 == this.equippedPlayer && this.original >= 0 && Access.num(Access.call(var1, "method_31548"), "method_67532") == this.managed) {
         Access.call(Access.call(var1, "method_31548"), "method_61496", this.original);
      }

      this.equippedPlayer = null;
      this.original = this.managed = -1;
   }
}
