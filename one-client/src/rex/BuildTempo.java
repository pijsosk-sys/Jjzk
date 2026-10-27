package rex;

import dev.krypton.core.module.Category;
import dev.krypton.core.module.Module;
import dev.krypton.core.setting.BooleanSetting;
import dev.krypton.core.setting.NumberSetting;
import dev.krypton.core.setting.Setting;

/**
 * Pacing for the unified Auto Build. The controller asks for every number here, so switching this
 * module off restores the original cadence: two-tick arming, one placement step per client tick, a
 * sixty-tick acknowledgement window, one candidate layer and no automatic retry.
 */
public final class BuildTempo extends Module {
   public static BuildTempo instance;

   public final NumberSetting steps = new NumberSetting("Steps Per Tick", 1.0, 6.0, 3.0, 1.0)
      .describe("Pipeline steps per client tick. Walking and in-flight placements still take one step each.");
   public final NumberSetting arm = new NumberSetting("Arm Delay", 0.0, 4.0, 1.0, 1.0)
      .describe("Ticks between aiming and the placement packet. Was two.");
   public final NumberSetting ack = new NumberSetting("Ack Window", 10.0, 200.0, 60.0, 1.0)
      .describe("Ticks to wait for the server before re-checking a placement.");
   public final NumberSetting swap = new NumberSetting("Swap Cooldown", 0.0, 4.0, 1.0, 1.0)
      .describe("Ticks after pulling material into the hotbar. Was two.");
   public final NumberSetting layers = new NumberSetting("Layer Window", 1.0, 4.0, 2.0, 1.0)
      .describe("How many blueprint layers may be placed at once. Fewer walk-backs, looser bottom-up order.");
   public final NumberSetting candidates = new NumberSetting("Candidates", 64.0, 1024.0, 384.0, 64.0)
      .describe("Targets examined when choosing the next block.");
   public final BooleanSetting resume = new BooleanSetting("Auto Resume", true)
      .describe("Resumes after unloaded chunks, lost routes and deferred blocks.");
   public final NumberSetting delay = new NumberSetting("Retry Delay", 10.0, 200.0, 40.0, 10.0);
   public final NumberSetting retries = new NumberSetting("Max Retries", 0.0, 64.0, 32.0, 1.0)
      .describe("Retries without confirmed progress before it stops asking.");

   public BuildTempo() {
      super("Build Tempo", "Placement pacing and automatic retry for Auto Build.", -1, Category.MISC);
      this.addSettings(
         new Setting[]{this.steps, this.arm, this.ack, this.swap, this.layers, this.candidates, this.resume, this.delay, this.retries}
      );
      this.enabled = true;
      instance = this;
   }

   private static boolean on() {
      BuildTempo tempo = instance;
      return tempo != null && tempo.enabled;
   }

   /** Called once per client tick before the controller runs. */
   public static void before(Object controller) {
      BuildPatience.tick(controller);
   }

   public static int steps() {
      return on() ? instance.steps.getInt() : 1;
   }

   public static int arm() {
      return on() ? instance.arm.getInt() : 2;
   }

   public static int ack() {
      return on() ? instance.ack.getInt() : 60;
   }

   public static int swap() {
      return on() ? instance.swap.getInt() : 2;
   }

   public static int layers() {
      return on() ? instance.layers.getInt() : 1;
   }

   public static int candidates() {
      return on() ? instance.candidates.getInt() : 256;
   }

   public static boolean autoResume() {
      return on() && instance.resume.get();
   }

   public static int retryDelay() {
      return on() ? instance.delay.getInt() : 40;
   }

   public static int maxRetries() {
      return on() ? instance.retries.getInt() : 0;
   }

   public static String describe() {
      if (!on()) {
         return "Build Tempo off (original cadence)";
      }
      return "Build Tempo: "
         + instance.steps.getInt()
         + " steps/tick, arm "
         + instance.arm.getInt()
         + ", ack "
         + instance.ack.getInt()
         + ", layers "
         + instance.layers.getInt()
         + (instance.resume.get() ? ", auto resume" : "");
   }
}
