package rex;

import dev.krypton.core.module.Category;
import dev.krypton.core.module.Module;
import dev.krypton.core.setting.BooleanSetting;
import dev.krypton.core.setting.EnumSetting;
import dev.krypton.core.setting.NumberSetting;
import dev.krypton.core.setting.Setting;

/**
 * Pacing for the unified Auto Build. The controller asks for every number here, so switching this
 * module off restores the original cadence: one placement step per client tick, a two-tick settle
 * after each block, a sixty-tick acknowledgement window, one candidate layer and no automatic retry.
 *
 * <p>A preset fixes every pacing number at once; Custom hands them to the individual rows. Every
 * preset keeps the safety nets: the rotation must have reached the server before a placement is
 * sent, placed blocks must stay put for the stability window before they count, the server's block
 * updates override any prediction, and the final pass re-checks the whole structure.
 */
public final class BuildTempo extends Module {
   public static BuildTempo instance;

   public enum Preset {
      /** Waits for the server on every block. For strict servers and high packet loss. */
      SAFE(2, 1, 60, 2, 1, 4096, 64, false, 0, 2, 1, 2),
      /** Trusts the client's prediction one tick after sending, two placements unconfirmed at most. */
      BALANCED(3, 1, 60, 1, 2, 16384, 64, true, 1, 0, 2, 0),
      /** One block per tick, the protocol floor for a placement that needs its own rotation. */
      FAST(4, 0, 80, 1, 2, 16384, 96, true, 0, 0, 4, 0),
      CUSTOM(0, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0);

      final int steps, arm, ack, swap, layers, candidates, revalidate;
      final boolean trust;
      final int trustDelay, settle, inflight, interval;

      Preset(int steps, int arm, int ack, int swap, int layers, int candidates, int revalidate, boolean trust, int trustDelay, int settle,
            int inflight, int interval) {
         this.steps = steps;
         this.arm = arm;
         this.ack = ack;
         this.swap = swap;
         this.layers = layers;
         this.candidates = candidates;
         this.revalidate = revalidate;
         this.trust = trust;
         this.trustDelay = trustDelay;
         this.settle = settle;
         this.inflight = inflight;
         this.interval = interval;
      }

      public String label() {
         String name = this.name();
         return name.charAt(0) + name.substring(1).toLowerCase(java.util.Locale.ROOT);
      }
   }

   public final EnumSetting<Preset> preset = new EnumSetting<>("Preset", Preset.BALANCED, Preset.class)
      .describe("Safe waits for the server on every block, Balanced trusts the client's prediction with two placements "
         + "unconfirmed, Fast places a block every tick. Custom uses the rows below.");
   public final NumberSetting steps = new NumberSetting("Steps Per Tick", 1.0, 6.0, 3.0, 1.0)
      .describe("Decisions per client tick while nothing is armed or in flight. Walking and placements still take a tick each.");
   public final NumberSetting arm = new NumberSetting("Arm Delay", 0.0, 4.0, 1.0, 1.0)
      .describe("Extra ticks between aiming and the placement packet. The rotation always has to reach the server first.");
   public final NumberSetting ack = new NumberSetting("Ack Window", 10.0, 200.0, 60.0, 1.0)
      .describe("Ticks to wait for the server before a placement counts as unanswered.");
   public final NumberSetting swap = new NumberSetting("Swap Cooldown", 0.0, 4.0, 1.0, 1.0)
      .describe("Ticks after pulling material into the hotbar.");
   public final NumberSetting settle = new NumberSetting("Place Delay", 0.0, 6.0, 0.0, 1.0)
      .describe("Ticks to wait after a block is placed before choosing the next. Was two.");
   public final NumberSetting interval = new NumberSetting("Place Interval", 0.0, 10.0, 0.0, 1.0)
      .describe("Minimum ticks between two placement packets. A hard cap on blocks per second for servers that limit it.");
   public final NumberSetting layers = new NumberSetting("Layer Window", 1.0, 4.0, 2.0, 1.0)
      .describe("How many blueprint layers may be placed at once. Fewer walk-backs, looser bottom-up order.");
   public final NumberSetting candidates = new NumberSetting("Candidates", 64.0, 32768.0, 16384.0, 64.0)
      .describe("Targets examined when choosing the next block. The nearest of them is built first; too few and it walks back and forth across a wide layer.");
   public final BooleanSetting trust = new BooleanSetting("Trust Prediction", true)
      .describe("Move on once the client has predicted the placement instead of waiting for the server's acknowledgement. "
         + "The block only counts once the server agrees and it has stayed put; a refusal re-queues it.");
   public final NumberSetting trustDelay = new NumberSetting("Prediction Delay", 0.0, 20.0, 1.0, 1.0)
      .describe("Ticks after sending before the prediction is trusted. Zero aims the next block in the same tick.");
   public final NumberSetting inflight = new NumberSetting("Max In Flight", 1.0, 8.0, 2.0, 1.0)
      .describe("Predicted placements the server may leave unanswered before the builder waits for it.");
   public final NumberSetting revalidate = new NumberSetting("Revalidate", 0.0, 512.0, 64.0, 16.0)
      .describe("Blueprint positions re-checked against the world each tick.");
   public final BooleanSetting pillar = new BooleanSetting("Pillar Up", true)
      .describe("When a block is too high to walk up to, jump and place dirt, cobblestone, cobbled deepslate, netherrack or "
         + "stone under yourself until it is in reach. The pillar stays unless Remove Supports is on.");
   public final BooleanSetting resume = new BooleanSetting("Auto Resume", true)
      .describe("Resumes after unloaded chunks, lost routes and deferred blocks.");
   public final NumberSetting delay = new NumberSetting("Retry Delay", 10.0, 200.0, 40.0, 10.0);
   public final NumberSetting retries = new NumberSetting("Max Retries", 0.0, 64.0, 32.0, 1.0)
      .describe("Retries without confirmed progress before it stops asking.");

   public BuildTempo() {
      super("Build Tempo", "Placement pacing and automatic retry for Auto Build.", -1, Category.MISC);
      for (Setting knob : new Setting[]{
            this.steps, this.arm, this.ack, this.swap, this.settle, this.interval, this.layers, this.candidates, this.trust, this.trustDelay,
            this.inflight, this.revalidate}) {
         knob.visibleWhen(() -> this.preset.get() == Preset.CUSTOM);
      }
      this.addSettings(
         new Setting[]{
            this.preset, this.steps, this.arm, this.ack, this.swap, this.settle, this.interval, this.layers, this.candidates, this.trust,
            this.trustDelay, this.inflight, this.revalidate, this.pillar, this.resume, this.delay, this.retries
         }
      );
      this.enabled = true;
      instance = this;
   }

   private Preset chosen() {
      return (Preset)this.preset.get();
   }

   private static boolean on() {
      BuildTempo tempo = instance;
      return tempo != null && tempo.enabled;
   }

   /** The preset in force, or null when this module is off. */
   public static Preset current() {
      return on() ? instance.chosen() : null;
   }

   public static void choose(Preset value) {
      BuildTempo tempo = instance;
      if (tempo != null) {
         tempo.preset.set(value);
         tempo.preset.changed();
         if (!tempo.enabled) {
            tempo.setEnabled(true);
         }
      }
   }

   private static Preset fixed() {
      Preset value = instance.chosen();
      return value == Preset.CUSTOM ? null : value;
   }

   /** Called once per client tick before the controller runs. */
   public static void before(Object controller) {
      BuildPatience.tick(controller);
      if (controller instanceof dev.donut.client.autobuild.AutoBuilderController builder) {
         try {
            SchematicGhost.sync(builder);
         } catch (LinkageError | RuntimeException ignored) {
         }
      }
      DonutFeature.syncAll();
   }

   public static int steps() {
      if (!on()) {
         return 1;
      }
      Preset p = fixed();
      return p != null ? p.steps : instance.steps.getInt();
   }

   public static int arm() {
      if (!on()) {
         return 2;
      }
      Preset p = fixed();
      return p != null ? p.arm : instance.arm.getInt();
   }

   public static int ack() {
      if (!on()) {
         return 60;
      }
      Preset p = fixed();
      return p != null ? p.ack : instance.ack.getInt();
   }

   public static int swap() {
      if (!on()) {
         return 2;
      }
      Preset p = fixed();
      return p != null ? p.swap : instance.swap.getInt();
   }

   /** Ticks after a confirmed placement before the next target is chosen. */
   public static int settleDelay() {
      if (!on()) {
         return 2;
      }
      Preset p = fixed();
      return p != null ? p.settle : instance.settle.getInt();
   }

   /** Minimum ticks between placement packets. */
   public static int interval() {
      if (!on()) {
         return 0;
      }
      Preset p = fixed();
      return p != null ? p.interval : instance.interval.getInt();
   }

   public static int layers() {
      if (!on()) {
         return 1;
      }
      Preset p = fixed();
      return p != null ? p.layers : instance.layers.getInt();
   }

   public static int candidates() {
      if (!on()) {
         return 256;
      }
      Preset p = fixed();
      return p != null ? p.candidates : instance.candidates.getInt();
   }

   public static int revalidate() {
      if (!on()) {
         return 256;
      }
      Preset p = fixed();
      return p != null ? p.revalidate : instance.revalidate.getInt();
   }

   /** Predicted placements allowed to wait on the server at once. */
   public static int inflight() {
      if (!on()) {
         return 1;
      }
      Preset p = fixed();
      return Math.max(1, p != null ? p.inflight : instance.inflight.getInt());
   }

   /** Pillaring is the one climbing method; off restores the original walk-only builder. */
   public static boolean pillar() {
      return on() && instance.pillar.get();
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

   private static boolean trusting() {
      if (!on()) {
         return false;
      }
      Preset p = fixed();
      return p != null ? p.trust : instance.trust.get();
   }

   /** Called from the controller with the ticks since the placement in flight was sent. */
   public static boolean earlyFinish(int waited) {
      if (!trusting()) {
         return false;
      }
      Preset p = fixed();
      return waited >= (p != null ? p.trustDelay : instance.trustDelay.getInt());
   }

   /** The best case in blocks per second for the pacing in force, ignoring walking and the server. */
   public static double ceiling() {
      int perBlock;
      if (trusting()) {
         Preset p = fixed();
         int trustDelay = p != null ? p.trustDelay : instance.trustDelay.getInt();
         perBlock = Math.max(1, arm()) + trustDelay;
      } else {
         // aim, send, at least one tick for the acknowledgement, then the settle delay
         perBlock = Math.max(1, arm()) + 1 + settleDelay();
      }
      perBlock = Math.max(perBlock, interval());
      return 20.0 / Math.max(1, perBlock);
   }

   public static String describe() {
      if (!on()) {
         return "Build Tempo off (original cadence)";
      }
      Preset p = instance.chosen();
      return "Build Tempo " + p.label() + ": "
         + steps()
         + " steps/tick, arm "
         + arm()
         + ", ack "
         + ack()
         + ", layers "
         + layers()
         + (trusting() ? ", prediction, " + inflight() + " in flight" : ", waits for the server")
         + (interval() > 0 ? ", interval " + interval() : "")
         + (instance.resume.get() ? ", auto resume" : "")
         + String.format(java.util.Locale.ROOT, ", up to %.1f blocks/s", ceiling());
   }
}
