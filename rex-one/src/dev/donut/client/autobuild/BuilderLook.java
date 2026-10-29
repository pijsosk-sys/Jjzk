package dev.donut.client.autobuild;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.class_310;
import net.minecraft.class_3532;
import net.minecraft.class_746;
import rex.BuildTempo;

/**
 * Every view change the builder makes goes through here, so building looks like a player doing it.
 *
 * <p>Two things made it look robotic. It snapped straight to each new angle, and it did so at the
 * start of the client tick, before the player ticked: the player's tick then copied the new angle
 * into its "last tick" rotation, so the camera had nothing to blend from and jumped twenty times
 * a second. Now a turn is capped at Build Tempo's Turn Speed per tick (small corrections still
 * land in one tick), and at the end of the tick the "last tick" rotation is put back to where the
 * view was, so the frames in between glide from the old angle to the new one the way mouse look
 * does. The server still gets the new angle in this tick's movement packet, so nothing is slower.
 */
@Environment(EnvType.CLIENT)
public final class BuilderLook {
   private static boolean turned;
   private static float fromYaw;
   private static float fromPitch;
   private static boolean hooked;
   private static final java.util.Random RANDOM = new java.util.Random();
   /** Turns under this many degrees finish in one tick. */
   private static final double SNAP = 25.0;
   private static double wobble = 1.0;

   private BuilderLook() {
   }

   /** Registers the end-of-tick hook once. */
   public static void install() {
      if (!hooked) {
         hooked = true;
         ClientTickEvents.END_CLIENT_TICK.register(BuilderLook::endTick);
      }
   }

   /**
    * Turns the view toward the given angles by at most the turn speed. Returns true once the view
    * is exactly there. The yaw is moved by the wrapped difference, so it stays continuous with the
    * player's own yaw and the camera never spins the long way round.
    */
   public static boolean toward(class_746 player, float yaw, float pitch) {
      install();
      float currentYaw = player.method_36454();
      float currentPitch = player.method_36455();
      float dy = class_3532.method_15393(yaw - currentYaw);
      float dp = class_3532.method_15363(pitch, -90.0F, 90.0F) - currentPitch;
      if (dy == 0.0F && dp == 0.0F) {
         return true;
      }

      if (!turned && BuildTempo.smoothAim()) {
         turned = true;
         fromYaw = currentYaw;
         fromPitch = currentPitch;
      }

      double distance = Math.sqrt(dy * dy + dp * dp);
      // A hand is never perfectly even: the turn limit wanders a little from tick to tick.
      if (BuildTempo.smoothAim()) {
         wobble += (RANDOM.nextDouble() - 0.5) * 0.12;
         wobble = Math.max(0.85, Math.min(1.1, wobble + (1.0 - wobble) * 0.15));
      }
      double limit = BuildTempo.turnSpeed() * (BuildTempo.smoothAim() ? wobble : 1.0);
      if (distance <= Math.min(limit, SNAP)) {
         player.method_36456(currentYaw + dy);
         player.method_36457(currentPitch + dp);
         return true;
      }

      // Big turns ease in toward the end instead of arriving at full speed.
      double step = Math.min(limit, Math.max(SNAP * 0.5, distance * BuildTempo.easing()));
      if (step >= distance) {
         player.method_36456(currentYaw + dy);
         player.method_36457(currentPitch + dp);
         return true;
      }
      double scale = step / distance;
      player.method_36456(currentYaw + (float)(dy * scale));
      player.method_36457(currentPitch + (float)(dp * scale));
      return false;
   }

   /** Angles from the player's eye to a point. */
   public static float[] anglesTo(class_746 player, double x, double y, double z) {
      double dx = x - player.method_23317();
      double dy = y - player.method_23320();
      double dz = z - player.method_23321();
      float yaw = (float)Math.toDegrees(Math.atan2(-dx, dz));
      float pitch = (float)Math.toDegrees(Math.atan2(-dy, Math.hypot(dx, dz)));
      return new float[]{yaw, pitch};
   }

   private static void endTick(class_310 client) {
      if (turned && client.field_1724 != null) {
         client.field_1724.field_5982 = fromYaw;
         client.field_1724.field_6004 = fromPitch;
      }
      turned = false;
   }
}
