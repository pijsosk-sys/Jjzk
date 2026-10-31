package com.roothack.overlord.process;

import baritone.api.IBaritone;
import baritone.api.process.IBaritoneProcess;
import baritone.api.process.PathingCommand;
import com.roothack.overlord.combat.MobDefense;
import com.roothack.overlord.runtime.Access;
import com.roothack.overlord.runtime.Autopilot;
import com.roothack.overlord.stages.Stage;

public final class ProgressionProcess implements IBaritoneProcess {
   private IBaritone baritone;
   private final Autopilot brain = new Autopilot();
   private final MobDefense combat = new MobDefense();
   private String combatError = "";

   public ProgressionProcess() {
   }

   public void attach(IBaritone var1) {
      this.baritone = var1;
      this.brain.attach(var1);

      try {
         Object var2 = Access.call(Access.type("baritone.api.BaritoneAPI"), "getSettings");

         for (String var6 : new String[]{"allowSprint", "smoothLook", "antiCheatCompatibility"}) {
            try {
               Object var7 = Access.field(var2, var6);
               var7.getClass().getField("value").set(var7, true);
            } catch (Exception var8) {
            }
         }
      } catch (Exception var9) {
         Access.message("Attachment: " + var9.getMessage());
      }
   }

   public void start() {
      if (this.baritone != null) {
         this.combat.stop();
         this.combatError = "";
         this.brain.start();
      }
   }

   public void stop() {
      this.combat.stop();
      this.brain.stop();
   }

   public boolean isEnabled() {
      return this.brain.running;
   }

   public boolean isAttached() {
      return this.baritone != null;
   }

   public boolean isCamouflage() {
      return false;
   }

   public Stage currentStage() {
      return this.brain.stage;
   }

   public boolean isActive() {
      if (this.brain.running && this.baritone != null) {
         try {
            return Access.player() != null;
         } catch (Exception var2) {
            return false;
         }
      } else {
         return false;
      }
   }

   public boolean defendTick() {
      if (!this.brain.running) {
         return false;
      } else {
         try {
            if (Access.player() != null && Access.world() != null) {
               boolean var1 = this.combat.tick();
               if (var1) {
                  this.brain.suspendForDefense();
               }

               return var1;
            } else {
               this.stop();
               return false;
            }
         } catch (LinkageError | Exception var3) {
            String var2 = var3.toString();
            if (!var2.equals(this.combatError)) {
               this.combatError = var2;
               Access.message("Mob defence failed: " + var2);
            }

            this.combat.stop();
            return false;
         }
      }
   }

   public PathingCommand onTick(boolean var1, boolean var2) {
      try {
         Object var3 = this.combat.pathingCommand();
         return var3 != null ? (PathingCommand)var3 : (PathingCommand)this.brain.tick(var1, var2);
      } catch (LinkageError | Exception var6) {
         this.brain.stop();
         this.brain.status = "Stopped: " + var6.getMessage();
         Access.message(this.brain.status);

         try {
            return (PathingCommand)Access.command("CANCEL_AND_SET_GOAL", null);
         } catch (Exception var5) {
            throw new IllegalStateException(var5);
         }
      }
   }

   public void onLostControl() {
   }

   public double priority() {
      return 10.0;
   }

   public boolean isTemporary() {
      return false;
   }

   public String displayName0() {
      return "Overlord " + this.brain.stage + ": " + this.brain.status + " | " + (this.combatError.isEmpty() ? this.combat.status : this.combatError);
   }
}
