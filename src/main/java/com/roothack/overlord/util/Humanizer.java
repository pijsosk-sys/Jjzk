package com.roothack.overlord.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

/**
 * Makes the bot's looking and timing read like a person rather than a machine.
 *
 * <p>Two tells give automation away: instantaneous snap rotations and perfectly
 * periodic actions. This class removes both. Rotations are eased toward their
 * target a fraction per tick with a little per-tick jitter, so the view arcs the
 * way a hand on a mouse arcs; action timers are drawn from a jittered
 * distribution so no two breaks or swings land on the same cadence.
 */
public final class Humanizer {

    private final Random rng = new Random();

    // Easing: fraction of the remaining angle covered each tick, plus noise.
    private double turnSpeed = 0.28;      // base responsiveness
    private double turnJitter = 0.08;     // +/- randomisation of the fraction
    private double aimNoiseDeg = 0.6;     // small constant tremor on the target

    /** Ease the client yaw/pitch toward a target, one tick's worth. */
    public void smoothLookTo(MinecraftClient mc, float targetYaw, float targetPitch) {
        if (mc.player == null) return;

        float curYaw = mc.player.getYaw();
        float curPitch = mc.player.getPitch();

        float dYaw = MathHelper.wrapDegrees(targetYaw - curYaw);
        float dPitch = targetPitch - curPitch;

        double frac = turnSpeed + (rng.nextDouble() - 0.5) * 2 * turnJitter;
        frac = MathHelper.clamp(frac, 0.08, 0.6);

        float noiseY = (float) ((rng.nextDouble() - 0.5) * 2 * aimNoiseDeg);
        float noiseP = (float) ((rng.nextDouble() - 0.5) * aimNoiseDeg);

        float newYaw = curYaw + (float) (dYaw * frac) + noiseY;
        float newPitch = MathHelper.clamp(
                curPitch + (float) (dPitch * frac) + noiseP, -90f, 90f);

        mc.player.setYaw(newYaw);
        mc.player.setPitch(newPitch);
    }

    /** True once the view is within {@code toleranceDeg} of the target. */
    public boolean isAimedAt(MinecraftClient mc, float targetYaw, float targetPitch, float toleranceDeg) {
        if (mc.player == null) return false;
        float dy = Math.abs(MathHelper.wrapDegrees(targetYaw - mc.player.getYaw()));
        float dp = Math.abs(targetPitch - mc.player.getPitch());
        return dy <= toleranceDeg && dp <= toleranceDeg;
    }

    /** Yaw/pitch (degrees) from the player's eyes to a world point. */
    public static float[] anglesTo(MinecraftClient mc, Vec3d point) {
        Vec3d eye = mc.player.getEyePos();
        double dx = point.x - eye.x;
        double dy = point.y - eye.y;
        double dz = point.z - eye.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, dist));
        return new float[]{yaw, pitch};
    }

    /** Aim point for an entity: its body centre, not the feet. */
    public static Vec3d aimPointFor(Entity e) {
        return new Vec3d(e.getX(), e.getY() + e.getHeight() * 0.6, e.getZ());
    }

    /**
     * A jittered countdown in ticks around {@code baseTicks}. Used so swings and
     * block-break confirmations never fall on a fixed period.
     */
    public int nextDelay(int baseTicks, double spreadFraction) {
        double spread = baseTicks * spreadFraction;
        int d = (int) Math.round(baseTicks + (rng.nextDouble() - 0.5) * 2 * spread);
        return Math.max(1, d);
    }

    /** Occasional idle micro-motion so a still bot still drifts like a person. */
    public void idleTwitch(MinecraftClient mc, double chance) {
        if (mc.player == null || rng.nextDouble() > chance) return;
        float dy = (float) ((rng.nextDouble() - 0.5) * 6.0);
        float dp = (float) ((rng.nextDouble() - 0.5) * 3.0);
        mc.player.setYaw(mc.player.getYaw() + dy);
        mc.player.setPitch(MathHelper.clamp(mc.player.getPitch() + dp, -90f, 90f));
    }

    public Random rng() {
        return rng;
    }
}
