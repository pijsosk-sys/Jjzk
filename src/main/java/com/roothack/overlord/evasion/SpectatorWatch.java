package com.roothack.overlord.evasion;

import com.roothack.overlord.util.Humanizer;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.Settings;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.GameMode;

import java.util.UUID;

/**
 * Watches for other players in spectator mode and, when one is around, hides the
 * tells that mark the client as a Baritone bot.
 *
 * <p>A spectator can fly up to and sit inside the bot, watching it from any
 * angle. The giveaways are the robotic ones: dead-still idling, metronomic
 * mining, instant perfect rotations, and a path that ignores the watcher
 * entirely. When a spectator is detected this class:
 * <ul>
 *   <li>forces Baritone's human-looking look settings on (smooth, free-look,
 *       anti-cheat compatibility, randomised looking);</li>
 *   <li>reports {@code camouflage == true} so the progression process throttles
 *       itself — no more blatant straight-line strip mining while watched;</li>
 *   <li>feeds idle micro-motions through the {@link Humanizer} so a paused bot
 *       still drifts its view like a bored person.</li>
 * </ul>
 *
 * <p>Detection uses the tab-list game mode, which the client always has, so a
 * spectator is spotted even while invisible. A spectator whose entity is loaded
 * close by raises the response to its strictest.
 */
public final class SpectatorWatch {

    private final Humanizer human;

    private double nearBlocks = 24.0;   // "right on top of us" distance
    private boolean camouflage = false;
    private boolean nearby = false;

    // Baritone settings we flip while watched, restored when clear.
    private Boolean savedFreeLook;
    private Double savedRandomLooking;
    private Boolean savedAntiCheat;

    public SpectatorWatch(Humanizer human) {
        this.human = human;
    }

    public boolean isCamouflage() {
        return camouflage;
    }

    public boolean spectatorNearby() {
        return nearby;
    }

    public void tick(MinecraftClient mc, IBaritone baritone) {
        boolean watched = scan(mc);
        if (watched && !camouflage) {
            enterCamouflage(baritone);
        } else if (!watched && camouflage) {
            exitCamouflage(baritone);
        }
        camouflage = watched;

        if (watched) {
            // A paused bot that never moves its head is obvious; a real player
            // fidgets. Keep the view alive.
            human.idleTwitch(mc, 0.15);
        }
    }

    private boolean scan(MinecraftClient mc) {
        if (mc.getNetworkHandler() == null || mc.player == null) return false;
        UUID self = mc.player.getUuid();
        nearby = false;
        boolean any = false;

        for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
            if (entry.getProfile().id().equals(self)) continue;
            if (entry.getGameMode() != GameMode.SPECTATOR) continue;
            any = true;

            PlayerEntity pe = mc.world != null
                    ? mc.world.getPlayerByUuid(entry.getProfile().id()) : null;
            if (pe != null && pe.squaredDistanceTo(mc.player) <= nearBlocks * nearBlocks) {
                nearby = true;
            }
        }
        return any;
    }

    private void enterCamouflage(IBaritone baritone) {
        Settings s = BaritoneAPI.getSettings();
        savedFreeLook = s.freeLook.value;
        savedRandomLooking = s.randomLooking.value;
        savedAntiCheat = s.antiCheatCompatibility.value;

        // Body turns with the view, looking is eased and more random, and
        // movement stays inside anti-cheat-safe envelopes.
        s.freeLook.value = false;
        s.randomLooking.value = 4.0;   // degrees of extra look variance
        s.antiCheatCompatibility.value = true;
        s.blockBreakSpeed.value = 100; // never faster than legit
    }

    private void exitCamouflage(IBaritone baritone) {
        Settings s = BaritoneAPI.getSettings();
        if (savedFreeLook != null) s.freeLook.value = savedFreeLook;
        if (savedRandomLooking != null) s.randomLooking.value = savedRandomLooking;
        if (savedAntiCheat != null) s.antiCheatCompatibility.value = savedAntiCheat;
    }
}
