package com.roothack.overlord;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;

import com.roothack.overlord.command.OverlordCommand;
import com.roothack.overlord.process.ProgressionProcess;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point. Binds the {@link ProgressionProcess} to the primary Baritone
 * instance's pathing control manager and exposes the {@code /overlord} command.
 *
 * <p>Baritone is loaded as its own Fabric mod; this mod only depends on its API.
 * Attachment to the control manager is deferred to the first client tick so
 * Baritone has finished constructing its primary instance before we attach.
 */
public class OverlordMod implements ClientModInitializer {

    public static final String MOD_ID = "overlord_baritone";
    private static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    private final ProgressionProcess process = new ProgressionProcess();
    private boolean attached = false;

    @Override
    public void onInitializeClient() {
        // Command is available from startup; it refuses cleanly until Baritone
        // is attached.
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) ->
                OverlordCommand.register(dispatcher, process));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (!attached) {
                IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
                if (baritone == null) return;

                process.attach(baritone);
                baritone.getPathingControlManager().registerProcess(process);
                attached = true;
                LOG.info("Overlord progression process attached to Baritone.");
            }

            // Fighting and spectator-camouflage run every client tick while
            // engaged, not only on Baritone's process ticks, so mobs are met
            // instantly and a watcher never catches an idle, robotic pause.
            process.defendTick();
        });
    }
}
