package rex;

/**
 * One tick of rotation toward a point. Same entry point the rest of the client already calls, with
 * the step shaped by Aim Tune: eased instead of constant, a fraction of a degree of noise, and the
 * result snapped to the sensitivity grid so the delta is a whole number of mouse steps. With Aim
 * Tune off this is the plain clamped step it replaces.
 */
public final class AimStep {
   public static AimStep.Rotation toward(
      double yaw, double pitch, double deltaX, double deltaY, double deltaZ, double yawStep, double pitchStep
   ) {
      if (!Double.isFinite(yaw)
         || !Double.isFinite(pitch)
         || !Double.isFinite(deltaX)
         || !Double.isFinite(deltaY)
         || !Double.isFinite(deltaZ)
         || !Double.isFinite(yawStep)
         || !Double.isFinite(pitchStep)) {
         throw new IllegalArgumentException("Nonfinite aim");
      } else if (deltaX == 0.0 && deltaY == 0.0 && deltaZ == 0.0) {
         return new AimStep.Rotation((float)yaw, (float)pitch);
      } else {
         double wantedYaw = Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0;
         double wantedPitch = -Math.toDegrees(Math.atan2(deltaY, Math.hypot(deltaX, deltaZ)));
         double offYaw = ((wantedYaw - yaw) % 360.0 + 540.0) % 360.0 - 180.0;
         double offPitch = wantedPitch - pitch;
         double grid = AimTune.grid();
         double movedYaw = step(offYaw, Math.max(0.0, yawStep), grid);
         double movedPitch = step(offPitch, Math.max(0.0, pitchStep), grid);
         return new AimStep.Rotation(
            (float)(yaw + movedYaw), (float)Math.max(-90.0, Math.min(90.0, pitch + movedPitch))
         );
      }
   }

   /**
    * How far to turn this tick. The travel is what a reconstruction looks at, so that part is eased,
    * noised and snapped to the mouse grid. The last fraction of a degree is not: inside one grid step
    * of the target it moves the whole remainder, because a turn that stops a third of a step short
    * forever is a crosshair that never lands on anything.
    */
   private static double step(double remaining, double allowance, double grid) {
      if (allowance <= 0.0 || remaining == 0.0) {
         return 0.0;
      }
      double size = Math.abs(remaining);
      double finish = Math.max(grid, 0.2);
      if (size <= finish) {
         return Math.abs(remaining) > allowance ? Math.signum(remaining) * allowance : remaining;
      }
      double ease = AimTune.ease();
      if (ease < 1.0) {
         double share = Math.min(1.0, ease + Math.random() * AimTune.spread());
         size = Math.max(size * share, Math.min(size, AimTune.floorStep()));
      }
      double noise = AimTune.noise();
      if (noise > 0.0) {
         // Scaled by how far there is to go, so it never fights the last degree.
         size += (Math.random() * 2.0 - 1.0) * noise * Math.min(1.0, Math.abs(remaining) / 4.0);
      }
      size = Math.max(0.0, Math.min(Math.min(size, allowance), Math.abs(remaining)));
      double moved = Math.signum(remaining) * size;
      if (grid > 0.0) {
         // A whole number of mouse steps, and never more steps than the target or the allowance has
         // room for. Below one step there is nothing on the grid to send, so the remainder goes as
         // it is — which is the same thing the final approach above does.
         double direction = Math.signum(moved);
         double steps = Math.rint(Math.abs(moved) / grid);
         steps = Math.min(steps, Math.floor(Math.abs(remaining) / grid));
         steps = Math.min(steps, Math.floor(allowance / grid));
         moved = steps > 0.0 ? direction * steps * grid : remaining;
      }
      return Math.abs(moved) > Math.abs(remaining) ? remaining : moved;
   }

   public record Rotation(float yaw, float pitch) {
   }
}
