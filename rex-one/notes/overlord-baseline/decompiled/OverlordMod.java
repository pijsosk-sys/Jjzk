package com.roothack.overlord;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import com.roothack.overlord.command.ChatTrigger;
import com.roothack.overlord.command.OverlordCommand;
import com.roothack.overlord.process.ProgressionProcess;
import com.roothack.overlord.ui.Banner;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OverlordMod implements ClientModInitializer {
   public static final String MOD_ID = "overlord_baritone";
   private static final Logger LOG = LoggerFactory.getLogger("overlord_baritone");
   private final ProgressionProcess process = new ProgressionProcess();
   private boolean attached = false;

   public OverlordMod() {
   }

   public void onInitializeClient() {
      ClientCommandRegistrationCallback.EVENT
         .register((ClientCommandRegistrationCallback)(dispatcher, access) -> OverlordCommand.register(dispatcher, this.process));
      ChatTrigger.register(this.process);
      HudRenderCallback.EVENT.register((HudRenderCallback)(ctx, tickCounter) -> Banner.render(ctx));
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)mc -> {
         if (!this.attached) {
            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            if (baritone == null) {
               return;
            }

            this.process.attach(baritone);
            baritone.getPathingControlManager().registerProcess(this.process);
            this.attached = true;
            LOG.info("Overlord progression process attached to Baritone.");
         }

         this.process.defendTick();
      });
   }
}
