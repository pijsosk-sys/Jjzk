package com.roothack.overlord.combat;

import com.roothack.overlord.util.Humanizer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Defends the bot. Each tick it picks the most dangerous hostile in reach,
 * eases the view onto it through the {@link Humanizer}, and attacks only when
 * the vanilla attack-cooldown bar is full and the view is actually on target —
 * so swings respect reach, timing and rotation the way a player's do, not a
 * machine's instant perfect hits.
 *
 * <p>It takes over looking only while a mob is inside engage range; the rest of
 * the time it leaves the view to Baritone's pathing.
 */
public final class CombatModule {

    private final Humanizer human;

    private double engageRange = 5.0;   // start tracking within this
    private double reach = 3.1;         // vanilla melee reach
    private float swingArmedAt = 1.0f;  // cooldown progress required to swing
    private int swingGate = 0;          // humanised ticks between committed swings

    private Entity target;

    public CombatModule(Humanizer human) {
        this.human = human;
    }

    /** @return true if combat is currently steering the view this tick. */
    public boolean tick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            return false;
        }
        if (mc.player.isDead() || mc.player.getHealth() <= 0f) return false;

        target = pickTarget(mc);
        if (target == null) return false;

        Vec3d aim = Humanizer.aimPointFor(target);
        float[] ang = Humanizer.anglesTo(mc, aim);
        human.smoothLookTo(mc, ang[0], ang[1]);

        if (swingGate > 0) swingGate--;

        boolean inReach = mc.player.squaredDistanceTo(target) <= reach * reach;
        boolean aimed = human.isAimedAt(mc, ang[0], ang[1], 6.0f);
        boolean cooled = mc.player.getAttackCooldownProgress(0.0f) >= swingArmedAt;

        if (inReach && aimed && cooled && swingGate == 0) {
            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(Hand.MAIN_HAND);
            // Attacking resets the vanilla cooldown; add a jittered floor so
            // swings never land on a fixed period.
            swingGate = human.nextDelay(3, 0.5);
        }
        return true;
    }

    public Entity currentTarget() {
        return target;
    }

    private Entity pickTarget(MinecraftClient mc) {
        Box area = mc.player.getBoundingBox().expand(engageRange);
        Entity best = null;
        double bestScore = Double.MAX_VALUE;

        for (Entity e : mc.world.getOtherEntities(mc.player, area, this::isHostile)) {
            if (!(e instanceof LivingEntity le) || le.isDead()) continue;
            double d = mc.player.squaredDistanceTo(e);
            // Prefer the closest; break ties toward whatever is looking at us.
            double score = d;
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }

    private boolean isHostile(Entity e) {
        if (e instanceof Monster) return true;
        if (e instanceof HostileEntity) return true;
        // A neutral mob that is currently angry (e.g. provoked wolf, piglin).
        if (e instanceof Angerable a && a.getAngryAt() != null) return true;
        // Never auto-swing at other players.
        return false;
    }
}
