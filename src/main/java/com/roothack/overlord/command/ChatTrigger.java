package com.roothack.overlord.command;

import com.roothack.overlord.process.ProgressionProcess;
import com.roothack.overlord.ui.Banner;

import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;

/**
 * Hooks outgoing chat so the bot is driven with hashtag commands typed straight
 * into the chat box: {@code #start}, {@code #stop}, {@code #status}. The message
 * is swallowed before it reaches the server (never sent publicly) and the action
 * runs with a toast + on-screen banner so it visibly pops up.
 */
public final class ChatTrigger {

    private ChatTrigger() {}

    public static void register(ProgressionProcess process) {
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            String msg = message.trim();
            String lower = msg.toLowerCase();

            if (lower.equals("#start")) {
                if (!process.isAttached()) {
                    popup("Overlord", "Baritone not ready yet — try again in a moment.", 0xFF5555);
                } else {
                    process.start();
                    popup("Overlord ENGAGED", "Stage: " + process.currentStage().name(), 0x55FF55);
                }
                return false; // swallow, do not send to server
            }
            if (lower.equals("#stop")) {
                process.stop();
                popup("Overlord HALTED", "Baritone released.", 0xFFAA00);
                return false;
            }
            if (lower.equals("#status")) {
                String body = process.currentStage().name()
                        + (process.isCamouflage() ? " | CAMOUFLAGE" : "")
                        + (process.isEnabled() ? " | running" : " | idle");
                popup("Overlord", body, 0x55FFFF);
                return false;
            }
            return true; // anything else goes through normally
        });
    }

    private static void popup(String title, String body, int rgb) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        // Toast in the corner...
        SystemToast.add(mc.getToastManager(), SystemToast.Type.PERIODIC_NOTIFICATION,
                Text.literal(title), Text.literal(body));
        // ...and a centred banner that fades.
        Banner.show(title + " — " + body, rgb, 3000);
    }
}
