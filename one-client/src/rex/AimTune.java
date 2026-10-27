package rex;

import dev.krypton.core.module.Category;
import dev.krypton.core.module.Module;
import dev.krypton.core.setting.BooleanSetting;
import dev.krypton.core.setting.NumberSetting;
import dev.krypton.core.setting.Setting;

/**
 * Shape of every stepped rotation this client sends — KillAura, the scaffold and anything else that
 * goes through AimStep.
 *
 * <p>Server-side checks reconstruct what the mouse must have done: a delta that is not a whole
 * number of mouse steps for any sensitivity cannot come from a mouse, a rotation that covers the
 * same angle every tick cannot come from a hand, and a line that ends exactly on a hitbox centre
 * never happens twice. So the step is eased rather than constant, carries a fraction of a degree of
 * noise, and is snapped to the sensitivity grid the game itself would produce. Switch this module
 * off and AimStep is the plain clamped step it was.
 */
public final class AimTune extends Module {
   public static AimTune instance;

   public final BooleanSetting snap = new BooleanSetting("Grid Snap", true)
      .describe("Rounds each rotation delta to a whole number of mouse steps for the current sensitivity.");
   public final NumberSetting sensitivity = new NumberSetting("Sensitivity", 0.0, 2.0, 0.0, 0.01)
      .describe("Zero reads the game's own mouse sensitivity. Set it only to pin the grid to another value.");
   public final NumberSetting ease = new NumberSetting("Ease", 0.2, 1.0, 0.6, 0.05)
      .describe("Fraction of what is left to cover each tick. One is the old constant step.");
   public final NumberSetting spread = new NumberSetting("Ease Spread", 0.0, 0.5, 0.15, 0.01)
      .describe("Random share added to the ease each tick, so no two ticks move the same amount.");
   public final NumberSetting noise = new NumberSetting("Noise", 0.0, 0.5, 0.08, 0.01)
      .describe("Degrees of random offset per tick, applied before the snap.");
   public final NumberSetting floor = new NumberSetting("Minimum Step", 0.0, 5.0, 0.6, 0.1)
      .describe("Smallest move per tick, so easing cannot crawl to a halt far from the target.");

   public AimTune() {
      super("Aim Tune", "Eased, noised, grid-snapped rotation for everything that aims.", -1, Category.COMBAT);
      this.addSettings(new Setting[]{this.snap, this.sensitivity, this.ease, this.spread, this.noise, this.floor});
      this.enabled = true;
      instance = this;
   }

   static boolean on() {
      AimTune tune = instance;
      return tune != null && tune.enabled;
   }

   static double ease() {
      return on() ? instance.ease.getDouble() : 1.0;
   }

   static double spread() {
      return on() ? instance.spread.getDouble() : 0.0;
   }

   static double noise() {
      return on() ? instance.noise.getDouble() : 0.0;
   }

   static double floorStep() {
      return on() ? instance.floor.getDouble() : 0.0;
   }

   /** Degrees per mouse step, or zero when the grid is unknown or snapping is off. */
   static double grid() {
      if (!on() || !instance.snap.get()) {
         return 0.0;
      }
      double value = instance.sensitivity.getDouble();
      if (value <= 0.0) {
         value = gameSensitivity();
      }
      if (!(value > 0.0)) {
         return 0.0;
      }
      double factor = value * 0.6 + 0.2;
      return factor * factor * factor * 8.0 * 0.15;
   }

   private static double gameSensitivity() {
      try {
         Object options = Bridge.field(Bridge.client(), "field_1690");
         Object option = Bridge.call(options, "method_42495");
         return ((Number)Bridge.call(option, "method_41753")).doubleValue();
      } catch (LinkageError | Exception unavailable) {
         return 0.5;
      }
   }
}
