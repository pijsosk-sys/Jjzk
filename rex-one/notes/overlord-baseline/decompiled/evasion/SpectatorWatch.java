package com.roothack.overlord.evasion;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.Settings;
import com.roothack.overlord.util.Humanizer;
import java.util.UUID;
import net.minecraft.class_1657;
import net.minecraft.class_1934;
import net.minecraft.class_310;
import net.minecraft.class_640;

public final class SpectatorWatch {
   private final Humanizer human;
   private double nearBlocks = 24.0;
   private boolean camouflage = false;
   private boolean nearby = false;
   private Boolean savedFreeLook;
   private Double savedRandomLooking;
   private Boolean savedAntiCheat;

   public SpectatorWatch(Humanizer human) {
      this.human = human;
   }

   public boolean isCamouflage() {
      return this.camouflage;
   }

   public boolean spectatorNearby() {
      return this.nearby;
   }

   public void tick(class_310 mc, IBaritone baritone) {
      boolean watched = this.scan(mc);
      if (watched && !this.camouflage) {
         this.enterCamouflage(baritone);
      } else if (!watched && this.camouflage) {
         this.exitCamouflage(baritone);
      }

      this.camouflage = watched;
      if (watched) {
         this.human.idleTwitch(mc, 0.15);
      }
   }

   private boolean scan(class_310 mc) {
      if (mc.method_1562() != null && mc.field_1724 != null) {
         UUID self = mc.field_1724.method_5667();
         this.nearby = false;
         boolean any = false;

         for (class_640 entry : mc.method_1562().method_2880()) {
            if (!entry.method_2966().id().equals(self) && entry.method_2958() == class_1934.field_9219) {
               any = true;
               class_1657 pe = mc.field_1687 != null ? mc.field_1687.method_18470(entry.method_2966().id()) : null;
               if (pe != null && pe.method_5858(mc.field_1724) <= this.nearBlocks * this.nearBlocks) {
                  this.nearby = true;
               }
            }
         }

         return any;
      } else {
         return false;
      }
   }

   private void enterCamouflage(IBaritone baritone) {
      Settings s = BaritoneAPI.getSettings();
      this.savedFreeLook = (Boolean)s.freeLook.value;
      this.savedRandomLooking = (Double)s.randomLooking.value;
      this.savedAntiCheat = (Boolean)s.antiCheatCompatibility.value;
      s.freeLook.value = false;
      s.randomLooking.value = 4.0;
      s.antiCheatCompatibility.value = true;
      s.blockBreakSpeed.value = 100;
   }

   private void exitCamouflage(IBaritone baritone) {
      Settings s = BaritoneAPI.getSettings();
      if (this.savedFreeLook != null) {
         s.freeLook.value = this.savedFreeLook;
      }

      if (this.savedRandomLooking != null) {
         s.randomLooking.value = this.savedRandomLooking;
      }

      if (this.savedAntiCheat != null) {
         s.antiCheatCompatibility.value = this.savedAntiCheat;
      }
   }
}
