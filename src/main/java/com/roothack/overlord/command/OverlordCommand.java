package com.roothack.overlord.command;

import com.mojang.brigadier.CommandDispatcher;
import com.roothack.overlord.process.ProgressionProcess;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * Client command surface: {@code /overlord start|stop|status}. Registered
 * against the Fabric client command dispatcher at mod init.
 */
public final class OverlordCommand {

    private OverlordCommand() {}

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher,
                                ProgressionProcess process) {
        dispatcher.register(literal("overlord")
            .then(literal("start").executes(ctx -> {
                process.start();
                feedback(ctx.getSource(), "Overlord engaged — " + process.currentStage().name());
                return 1;
            }))
            .then(literal("stop").executes(ctx -> {
                process.stop();
                feedback(ctx.getSource(), "Overlord halted.");
                return 1;
            }))
            .then(literal("status").executes(ctx -> {
                feedback(ctx.getSource(), "Stage: " + process.currentStage().name()
                        + " — " + process.currentStage().description
                        + (process.isCamouflage() ? " | CAMOUFLAGE (spectator watching)" : "")
                        + (process.isEnabled() ? " | running" : " | idle"));
                return 1;
            }))
        );
    }

    private static void feedback(FabricClientCommandSource source, String msg) {
        source.sendFeedback(Text.literal("[Overlord] " + msg));
    }
}
