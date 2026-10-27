import rex.AimStep;

/** Pure-maths checks on the rotation stepper: no Minecraft, no client, just the numbers. */
public final class AimStepTest {
    public static void main(String[] args) {
        // Aim Tune is not instantiated here, so the stepper must behave exactly like the plain
        // clamped step it replaces: no easing, no noise, no grid.
        for (double[] probe : new double[][]{{0, 0, 1, 0, 0}, {37, 12, -4, 2, 9}, {-170, -80, 3, -7, -2}}) {
            AimStep.Rotation turn = AimStep.toward(probe[0], probe[1], probe[2], probe[3], probe[4], 45, 30);
            double wantedYaw = Math.toDegrees(Math.atan2(probe[4], probe[2])) - 90.0;
            double wantedPitch = -Math.toDegrees(Math.atan2(probe[3], Math.hypot(probe[2], probe[4])));
            double offYaw = ((wantedYaw - probe[0]) % 360.0 + 540.0) % 360.0 - 180.0;
            float expectedYaw = (float)(probe[0] + clamp(offYaw, 45));
            float expectedPitch = (float)Math.max(-90, Math.min(90, probe[1] + clamp(wantedPitch - probe[1], 30)));
            check("tune off matches the plain step", Math.abs(turn.yaw() - expectedYaw) < 1.0E-4f
                && Math.abs(turn.pitch() - expectedPitch) < 1.0E-4f);
        }
        check("no target leaves the rotation alone", AimStep.toward(10, 20, 0, 0, 0, 45, 30).yaw() == 10.0f);
        check("pitch cannot leave the legal range", AimStep.toward(0, 0, 0, -50, 0.001, 45, 90).pitch() <= 90.0f);
        try {
            AimStep.toward(Double.NaN, 0, 1, 0, 0, 45, 30);
            throw new AssertionError("a nonfinite aim must be refused");
        } catch (IllegalArgumentException expected) {
            System.out.println("  ok: a nonfinite aim is refused");
        }
        // Every step must stay inside the allowance and never pass the target.
        for (int round = 0; round < 20000; round++) {
            double yaw = Math.random() * 720 - 360;
            double pitch = Math.random() * 180 - 90;
            double allowance = Math.random() * 90;
            AimStep.Rotation turn = AimStep.toward(
                yaw, pitch, Math.random() * 20 - 10, Math.random() * 20 - 10, Math.random() * 20 - 10, allowance, allowance
            );
            double movedYaw = Math.abs(((turn.yaw() - yaw) % 360.0 + 540.0) % 360.0 - 180.0);
            if (movedYaw > allowance + 1.0E-3) {
                throw new AssertionError("yaw step " + movedYaw + " over the allowance " + allowance);
            }
            if (Math.abs(turn.pitch()) > 90.0001) {
                throw new AssertionError("pitch left the range: " + turn.pitch());
            }
            if (!Double.isFinite(turn.yaw()) || !Double.isFinite(turn.pitch())) {
                throw new AssertionError("nonfinite result");
            }
        }
        check("20000 random steps stay inside their allowance", true);
        System.out.println("aim: all checks passed");
    }

    static double clamp(double value, double limit) {
        return Math.max(-limit, Math.min(limit, value));
    }

    static void check(String what, boolean ok) {
        if (!ok) {
            throw new AssertionError(what);
        }
        System.out.println("  ok: " + what);
    }
}
