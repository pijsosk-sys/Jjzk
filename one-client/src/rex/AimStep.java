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
    * How far to turn this tick: a share of what is left rather than the whole allowance, never more
    * than the allowance, never less than the floor while there is distance to cover, plus noise, and
    * finally rounded onto the mouse grid.
    */
   private static double step(double remaining, double allowance, double grid) {
      if (allowance <= 0.0 || remaining == 0.0) {
         return 0.0;
      }
      double size = Math.abs(remaining);
      double ease = AimTune.ease();
      if (ease < 1.0) {
         double share = ease + Math.random() * AimTune.spread();
         size = Math.min(size, Math.max(size * Math.min(1.0, share), Math.min(size, AimTune.floorStep())));
      }
      double noise = AimTune.noise();
      if (noise > 0.0) {
         size += (Math.random() * 2.0 - 1.0) * noise;
      }
      size = Math.max(0.0, Math.min(size, allowance));
      double moved = Math.signum(remaining) * size;
      if (grid > 0.0) {
         double steps = Math.rint(moved / grid);
         if (steps == 0.0 && Math.abs(remaining) >= grid) {
            steps = Math.signum(moved);
         }
         moved = steps * grid;
         if (Math.abs(moved) > allowance) {
            moved = Math.signum(moved) * Math.floor(allowance / grid) * grid;
         }
      }
      return Math.abs(moved) > Math.abs(remaining) ? remaining : moved;
   }

   public record Rotation(float yaw, float pitch) {
   }
}
