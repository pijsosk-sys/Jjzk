package com.roothack.overlord.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.roothack.overlord.process.ProgressionProcess;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.class_2561;

public final class OverlordCommand {
   private OverlordCommand() {
   }

   public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, ProgressionProcess process) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommandManager.literal("overlord")
                  .then(ClientCommandManager.literal("start").executes(ctx -> {
                     process.start();
                     feedback((FabricClientCommandSource)ctx.getSource(), "Overlord engaged — " + process.currentStage().name());
                     return 1;
                  })))
               .then(ClientCommandManager.literal("stop").executes(ctx -> {
                  process.stop();
                  feedback((FabricClientCommandSource)ctx.getSource(), "Overlord halted.");
                  return 1;
               })))
            .then(
               ClientCommandManager.literal("status")
                  .executes(
                     ctx -> {
                        feedback(
                           (FabricClientCommandSource)ctx.getSource(),
                           "Stage: "
                              + process.currentStage().name()
                              + " — "
                              + process.currentStage().description
                              + (process.isCamouflage() ? " | CAMOUFLAGE (spectator watching)" : "")
                              + (process.isEnabled() ? " | running" : " | idle")
                        );
                        return 1;
                     }
                  )
            )
      );
   }

   private static void feedback(FabricClientCommandSource source, String msg) {
      source.sendFeedback(class_2561.method_43470("[Overlord] " + msg));
   }
}
