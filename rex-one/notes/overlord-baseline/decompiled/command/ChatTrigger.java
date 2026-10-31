package com.roothack.overlord.command;

import com.roothack.overlord.process.ProgressionProcess;
import com.roothack.overlord.ui.Banner;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.AllowChat;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_370;
import net.minecraft.class_370.class_9037;

public final class ChatTrigger {
   private ChatTrigger() {
   }

   public static void register(ProgressionProcess process) {
      ClientSendMessageEvents.ALLOW_CHAT.register((AllowChat)message -> {
         String msg = message.trim();
         String lower = msg.toLowerCase();
         if (lower.equals("#start")) {
            if (!process.isAttached()) {
               popup("Overlord", "Baritone not ready yet — try again in a moment.", 16733525);
            } else {
               process.start();
               popup("Overlord ENGAGED", "Stage: " + process.currentStage().name(), 5635925);
            }

            return false;
         } else if (lower.equals("#stop")) {
            process.stop();
            popup("Overlord HALTED", "Baritone released.", 16755200);
            return false;
         } else if (lower.equals("#status")) {
            String body = process.currentStage().name() + (process.isCamouflage() ? " | CAMOUFLAGE" : "") + (process.isEnabled() ? " | running" : " | idle");
            popup("Overlord", body, 5636095);
            return false;
         } else {
            return true;
         }
      });
   }

   private static void popup(String title, String body, int rgb) {
      class_310 mc = class_310.method_1551();
      if (mc != null) {
         class_370.method_27024(mc.method_1566(), class_9037.field_47588, class_2561.method_43470(title), class_2561.method_43470(body));
         Banner.show(title + " — " + body, rgb, 3000L);
      }
   }
}
