package dev.donut.client.autobuild;

import dev.donut.client.freecam.FreecamController;
import dev.donut.client.mixin.BlockItemAccessor;
import dev.donut.client.mixin.BuilderClientLevelAccessor;
import dev.donut.client.mixin.BuilderLocalPlayerAccessor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_1268;
import net.minecraft.class_2310;
import net.minecraft.class_2457;
import net.minecraft.class_2544;
import net.minecraft.class_2246;
import net.minecraft.class_238;
import net.minecraft.class_2478;
import net.minecraft.class_7743;
import net.minecraft.class_3965;
import net.minecraft.class_3959.class_3960;
import net.minecraft.class_3959;
import net.minecraft.class_3959.class_242;
import net.minecraft.class_239.class_240;
import net.minecraft.class_1750;
import net.minecraft.class_1269;
import net.minecraft.class_1713;
import net.minecraft.class_1747;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_2244;
import net.minecraft.class_2320;
import net.minecraft.class_2323;
import net.minecraft.class_2338;
import net.minecraft.class_2346;
import net.minecraft.class_2350;
import net.minecraft.class_2397;
import net.minecraft.class_243;
import net.minecraft.class_2482;
import net.minecraft.class_2510;
import net.minecraft.class_265;
import net.minecraft.class_2680;
import net.minecraft.class_2741;
import net.minecraft.class_2742;
import net.minecraft.class_2756;
import net.minecraft.class_2760;
import net.minecraft.class_2769;
import net.minecraft.class_2771;
import net.minecraft.class_304;
import net.minecraft.class_310;
import net.minecraft.class_3532;
import net.minecraft.class_5544;
import net.minecraft.class_638;
import net.minecraft.class_746;
import net.minecraft.class_2350.class_2353;
import rex.BuildRestock;
import rex.BuildTempo;
import rex.UnifiedAutoBuild;

@Environment(EnvType.CLIENT)
public final class AutoBuilderController {
   private static final AutoBuilderController INSTANCE = new AutoBuilderController();
   private static final int NEARBY = 12;
   private final List<Target> nearby = new ArrayList<>();
   private final List<Double> nearbyScores = new ArrayList<>();
   private final BuilderNavigator navigator = new BuilderNavigator();
   private Schematic schematic;
   private PlacementTransform transform = new PlacementTransform(class_2338.field_10980, 0, false);
   private boolean previewVisible;
   private Status status = Status.IDLE;
   private String reason = "Choose a schematic.";
   private class_638 session;
   private List<Target> targets = List.of();
   private Target[] byIndex = new Target[0];
   private final NavigableSet<Target> work = new TreeSet<>(
      Comparator.<Target>comparingInt(t -> t.pos.method_10264())
         .thenComparingInt(Target::index)
   );
   private final Map<class_2338, Integer> indices = new HashMap<>();
   private final BitSet correct = new BitSet();
   private final Map<class_2338, Integer> failures = new HashMap<>();
   private final Set<class_2338> deferred = new HashSet<>();
   private final Map<class_2338, String> deferReasons = new HashMap<>();
   private final Set<class_2338> supportBlocks = new HashSet<>();
   private final Map<class_2338, Integer> unconfirmed = new HashMap<>();
   // Placed blocks that match but have not yet been stable for the gate's window, or are still awaiting the server.
   // They leave the work set so target selection moves on instead of re-picking the block it just placed.
   private final Map<class_2338, Target> settling = new HashMap<>();
   private final ArrayDeque<Settle> settleQueue = new ArrayDeque<>();
   // Placements finished on the client's prediction, by position, with the tick they were sent.
   private final Map<class_2338, Integer> inflight = new HashMap<>();
   private int placedTick = Integer.MIN_VALUE / 2;
   // Confirmed-plus-settling counts for the last five seconds of running ticks, for the rate figure.
   private final int[] history = new int[101];
   private int historyTicks;
   private int placements;
   private class_638 predictionLevel;
   private Target current;
   private Prepared prepared;
   private Pending pending;
   private int tick;
   private int scanIndex;
   private int cooldown;
   private int noProgress;
   private int revalidateIndex;
   private int finalCheckIndex;
   private int originalSlot = -1;
   private boolean scanned;
   private boolean yielded;
   private PlacementPlanner.Plan bucketPlan;
   private int signTick = Integer.MIN_VALUE / 2;
   private Target pillarFor;
   private int pillarGoal;
   private int pillarBase;
   private int pillarFails;
   private boolean pillarPlaced;
   private String lastFailure = "";

   private AutoBuilderController() {
   }

   public static AutoBuilderController get() {
      return INSTANCE;
   }

   public Schematic schematic() {
      return this.schematic;
   }

   public PlacementTransform transform() {
      return this.transform;
   }

   public boolean previewVisible() {
      return this.previewVisible;
   }

   public void setPreviewVisible(boolean value) {
      this.previewVisible = value && this.schematic != null;
   }

   public Status status() {
      return this.status;
   }

   public String reason() {
      return this.reason;
   }

   public boolean isRunning() {
      return this.status == Status.RUNNING;
   }

   public int confirmedBlocks() {
      return this.correct.cardinality();
   }

   public double progress() {
      return this.targets.isEmpty() ? 0.0 : (double)this.correct.cardinality() / this.targets.size();
   }

   public int supportsPlaced() {
      return this.supportBlocks.size();
   }

   /** The lowest layer with blocks still to place, or Integer.MIN_VALUE when there is none. */
   public int workingLayer() {
      return this.work.isEmpty() ? Integer.MIN_VALUE : this.work.first().pos.method_10264();
   }

   public int totalBlocks() {
      return this.targets.size();
   }

   /** Placed blocks that match the blueprint and are waiting out the stability window or the server. */
   public int settlingBlocks() {
      return this.settling.size();
   }

   /** Placements finished on prediction that the server has not acknowledged yet. */
   public int inflightPlacements() {
      return this.inflight.size() + (this.pending == null ? 0 : 1);
   }

   /** Placement packets sent since the build started. */
   public int placementsSent() {
      return this.placements;
   }

   /** Every skipped block and why, for the screen's Skipped figure. */
   public String skippedSummary() {
      StringBuilder out = new StringBuilder(this.deferred.size() + " blocks are skipped for now. They are retried after the next block "
         + "that places, and on Resume.\n");
      int listed = 0;
      for (class_2338 pos : this.deferred) {
         if (listed++ == 40) {
            out.append("...and ").append(this.deferred.size() - 40).append(" more.\n");
            break;
         }
         out.append(coordinates(pos)).append(": ").append(this.deferReasons.getOrDefault(pos, "no reason recorded")).append('\n');
      }
      return out.toString();
   }

   public int deferredBlocks() {
      return this.deferred.size();
   }

   /** Blocks per second over the last five seconds of building, counting blocks still settling. */
   public double blocksPerSecond() {
      int span = Math.min(this.historyTicks - 1, this.history.length - 1);
      if (span < 20) {
         return 0.0;
      }

      int now = this.history[(this.historyTicks - 1) % this.history.length];
      int then = this.history[(this.historyTicks - 1 - span) % this.history.length];
      return Math.max(0, now - then) * 20.0 / span;
   }

   /** Seconds left at the current rate, or -1 when there is no rate to go by. */
   public int secondsRemaining() {
      double rate = this.blocksPerSecond();
      int left = this.targets.size() - this.correct.cardinality() - this.settling.size();
      return rate <= 0.05 ? -1 : (int)Math.ceil(Math.max(0, left) / rate);
   }

   private void sample() {
      this.history[this.historyTicks % this.history.length] = this.correct.cardinality() + this.settling.size();
      this.historyTicks++;
   }

   // ---- For the shulker restock ------------------------------------------------------------------

   public void restockHold(class_310 client, boolean sneak) {
      this.navigator.hold(client, sneak);
   }

   public boolean isBlueprint(class_2338 pos) {
      return this.indices.containsKey(pos);
   }

   /** Every item the blocks still to build need, water buckets included. */
   public Set<class_1792> remainingItems() {
      Set<class_1792> items = new HashSet<>();
      for (Target target : this.targets) {
         if (!this.correct.get(target.index)) {
            items.add(waterSource(target.state) || kelp(target.state) ? class_1802.field_8705 : MaterialTracker.requirement(target.state).item());
            if (kelp(target.state)) {
               items.add(class_1802.field_17532);
            }
         }
      }
      items.remove(class_1802.field_8162);
      return items;
   }

   /** What the builder thinks of one blueprint position, for diagnostics. */
   public String explain(class_2338 pos) {
      Integer index = this.indices.get(pos);
      if (index == null) {
         return "not in blueprint";
      }
      Target target = this.byIndex[index];
      return (this.correct.get(index) ? "correct " : "")
         + (this.work.contains(target) ? "queued " : "")
         + (this.settling.containsKey(pos) ? "settling " : "")
         + (this.deferred.contains(pos) ? "deferred " : "")
         + (this.unconfirmed.containsKey(pos) ? "unconfirmed " : "")
         + (this.failures.containsKey(pos) ? "failures=" + this.failures.get(pos) + " " : "")
         + (target.equals(this.current) ? "current " : "")
         + (this.deferReasons.containsKey(pos) ? "| last skipped: " + this.deferReasons.get(pos) : "");
   }

   private void clearSettling() {
      this.settling.clear();
      this.settleQueue.clear();
   }

   public void setSchematic(Schematic value) {
      StabilityGate.clear(this);
      this.cancel();
      this.schematic = value;
      this.rebuildTargets();
      this.previewVisible = value != null;
      this.status = value == null
         ? Status.IDLE
         : Status.READY;
      this.reason = value == null ? "Choose a schematic." : "Position the preview, then Start.";
   }

   public void setTransform(PlacementTransform value) {
      StabilityGate.clear(this);
      if (!this.transform.equals(value)) {
         this.cancel();
         this.transform = Objects.requireNonNull(value);
         this.rebuildTargets();
         this.status = this.schematic == null
            ? Status.IDLE
            : Status.READY;
         this.reason = "Position updated.";
      }
   }

   private void rebuildTargets() {
      this.indices.clear();
      this.correct.clear();
      this.failures.clear();
      this.deferred.clear();
      this.supportBlocks.clear();
      this.clearSettling();
      this.inflight.clear();
      this.scanIndex = 0;
      this.scanned = false;
      this.work.clear();
      this.revalidateIndex = 0;
      this.finalCheckIndex = 0;
      if (this.schematic == null) {
         this.targets = List.of();
         this.byIndex = new Target[0];
      } else {
         List<Target> values = new ArrayList<>(this.schematic.blocks().size());
         List<Schematic.Block> blocks = this.schematic.blocks();

         for (int index = 0; index < blocks.size(); index++) {
            Schematic.Block block = blocks.get(index);
            class_2680 state = this.transform.apply(block.state());
            if (derived(state)) {
               continue;
            }
            class_2338 pos = this.transform.apply(block.pos());
            values.add(new Target(pos, state, index, false));
            this.indices.put(pos, index);
         }

         values.sort(
            Comparator.<Target>comparingInt(t -> t.pos.method_10264())
               .thenComparingInt(
                  t -> MaterialTracker.isSecondary(t.state) ? 2 : (t.state.method_26204() instanceof class_2346 ? 1 : 0)
               )
         );
         this.targets = List.copyOf(values);
         this.byIndex = new Target[blocks.size()];

         for (Target target : values) {
            this.byIndex[target.index] = target;
         }

         this.work.addAll(values);
      }
   }

   public void start() {
      UnifiedAutoBuild.acquire();
      StabilityGate.clear(this);
      class_310 client = class_310.method_1551();
      if (this.schematic == null || this.targets.isEmpty()) {
         this.reason = "Load a schematic containing blocks.";
      } else if (this.canRun(client)) {
         if (this.predictionLevel != client.field_1687) {
            this.unconfirmed.clear();
            this.predictionLevel = client.field_1687;
         }

         this.correct.clear();
         this.failures.clear();
         this.deferred.clear();
         this.clearSettling();
         this.inflight.clear();
         this.scanIndex = 0;
         this.scanned = false;
         this.work.clear();
         this.work.addAll(this.targets);
         this.revalidateIndex = 0;
         this.finalCheckIndex = 0;
         this.session = client.field_1687;
         this.tick = 0;
         this.cooldown = 0;
         this.placements = 0;
         this.historyTicks = 0;
         this.placedTick = Integer.MIN_VALUE / 2;
         this.noProgress = 0;
         this.lastFailure = "";
         this.current = null;
         this.pending = null;
         this.prepared = null;
         this.pillarFor = null;
         this.originalSlot = client.field_1724.method_31548().method_67532();
         this.status = Status.RUNNING;
         this.previewVisible = true;
         this.reason = "Checking loaded blocks...";
      }
   }

   public void pause() {
      this.pauseWith("Paused by you.");
   }

   private void pauseWith(String message) {
      if (this.status != Status.RUNNING
         && this.status != Status.PAUSED) {
         this.reason = message;
      } else {
         this.status = Status.PAUSED;
         this.reason = message;
         this.prepared = null;
         this.pillarFor = null;
         this.navigator.release();
         this.restoreSlot();
      }
   }

   public void resume() {
      UnifiedAutoBuild.acquire();
      if (this.status != Status.PAUSED) {
         this.start();
      } else {
         class_310 client = class_310.method_1551();
         if (this.canRun(client)) {
            if (this.session != client.field_1687) {
               this.start();
            } else {
               this.failures.clear();
               this.deferred.clear();
               this.lastFailure = "";
               this.noProgress = 0;
               this.current = null;
               this.prepared = null;
               this.navigator.reset();
               this.work.clear();
               this.clearSettling();
               this.inflight.clear();
               this.cooldown = 0;
               this.historyTicks = 0;

               for (Target target : this.targets) {
                  if (!this.correct.get(target.index)) {
                     this.work.add(target);
                  }
               }

               this.originalSlot = client.field_1724.method_31548().method_67532();
               this.status = Status.RUNNING;
               this.reason = "Resuming...";
            }
         }
      }
   }

   private boolean canRun(class_310 client) {
      if (client.field_1687 == null || client.field_1724 == null || client.field_1761 == null) {
         this.reason = "Join a world before starting.";
         return false;
      } else if (FreecamController.enabled()) {
         this.reason = "Disable Freecam before resuming AutoBuilder.";
         return false;
      } else if (client.field_1761.method_2928()) {
         this.reason = "Spectator mode cannot place blocks.";
         return false;
      } else if (client.field_1724.method_5765()) {
         this.reason = "Leave the vehicle before starting AutoBuilder.";
         return false;
      } else if (client.field_1755 != null) {
         this.reason = "Close the open menu before resuming.";
         return false;
      } else {
         return true;
      }
   }

   public void cancel() {
      StabilityGate.clear(this);
      this.navigator.reset();
      this.restoreSlot();
      this.current = null;
      this.prepared = null;
      this.pending = null;
      this.session = null;
      this.failures.clear();
      this.deferred.clear();
      this.clearSettling();
      this.inflight.clear();
      this.pillarFor = null;
      this.cooldown = 0;
      this.status = this.schematic == null
         ? Status.IDLE
         : Status.READY;
      this.reason = "Build cancelled.";
   }

   public void disconnect() {
      StabilityGate.clear(this);
      if (this.isRunning() || this.status == Status.PAUSED) {
         this.pauseWith("Disconnected. Rejoin, check the position, then Resume.");
      }

      this.navigator.reset();
      this.restoreSlot();
      this.session = null;
      this.pending = null;
      this.prepared = null;
      this.current = null;
      this.correct.clear();
      this.clearSettling();
      this.inflight.clear();
      this.scanIndex = 0;
      this.scanned = false;
      this.unconfirmed.clear();
      this.predictionLevel = null;
      this.previewVisible = false;
   }

   private void restoreSlot() {
      class_746 player = class_310.method_1551().field_1724;
      if (player != null && this.originalSlot >= 0) {
         player.method_31548().method_61496(this.originalSlot);
      }

      this.originalSlot = -1;
   }

   /**
    * One pipeline step. The first step of a client tick advances the clock and does the per-tick
    * bookkeeping; later steps in the same tick only make further decisions while nothing is armed,
    * in flight or cooling down, so every delay below is measured in real client ticks.
    */
   private void step(class_310 client, boolean first) {
      if (this.isRunning()) {
         if (first) {
            this.tick++;
            this.sample();
            if (this.cooldown > 0) {
               this.cooldown--;
            }

            for (class_304 key : List.of(client.field_1690.field_1886, client.field_1690.field_1904, client.field_1690.field_1869, client.field_1690.field_1831)) {
               key.method_23481(false);

               while (key.method_1436()) {
               }
            }
         }

         if (client.field_1687 == null || client.field_1724 == null || client.field_1761 == null || client.field_1687 != this.session) {
            this.disconnect();
         } else if (FreecamController.enabled()) {
            this.pauseWith("Freecam is active. Disable it, then Resume.");
         } else if (BuildRestock.active()) {
            // The shulker box's own screen is open while restocking; it is not a reason to pause.
            this.yielded = true;
            if (first) {
               String failed = BuildRestock.step(client, this);
               this.reason = failed == null ? BuildRestock.status() : failed;
               if (failed != null) {
                  this.pauseWith(failed);
               }
            }
         } else if (client.field_1755 instanceof class_7743 editor && this.placedSignRecently()) {
            // Placing a sign opens its editor, which would pause the build: press Done for it.
            editor.method_25419();
            this.reason = "Closed the sign editor";
         } else if (client.field_1755 != null || client.method_1493()) {
            this.pauseWith("An open menu paused construction. Close it, then Resume.");
         } else if (!client.field_1724.method_5805() || client.field_1724.method_31481()) {
            this.pauseWith("Player unavailable. Resume after respawning.");
         } else if (client.field_1724.method_5765()) {
            this.pauseWith("Leave the vehicle, then Resume.");
         } else if (client.field_1724.method_6115()) {
            this.pauseWith("Finish using the held item, then Resume.");
         } else {
            if (first) {
               String danger = BuildTempo.danger(client);
               if (danger != null) {
                  this.pauseWith(danger);
                  return;
               }

               if (this.scanned) {
                  this.revalidate(client, BuildTempo.revalidate());
               }

               this.settle(client);
               if (!this.isRunning() || !this.checkInflight()) {
                  return;
               }
            }

            if (this.pending != null) {
               this.navigator.hold(client, false);
               if (this.pending.acknowledged || this.tick - this.pending.sentTick > BuildTempo.ack()) {
                  this.finishPending(client);
               } else if (BuildTempo.earlyFinish(this.waited())) {
                  this.finishPredicted(client);
               }
            } else if (this.prepared != null) {
               this.navigator.hold(client, shouldSneak(client, this.prepared.target, this.prepared.plan));
               aim(client, this.prepared.plan);
               if (this.tick >= this.prepared.readyTick && this.tick - this.placedTick >= BuildTempo.interval()) {
                  this.sendPrepared(client);
                  if (this.pending != null && BuildTempo.earlyFinish(0)) {
                     // The client applied its prediction inside the interaction call, so with no
                     // prediction delay the next block can be aimed this same tick.
                     this.finishPredicted(client);
                  }
               }
            } else if (this.cooldown > 0) {
               this.yielded = true;
            } else {
               if (!this.scanned) {
                  for (int end = Math.min(this.targets.size(), this.scanIndex + 2048); this.scanIndex < end; this.scanIndex++) {
                     Target target = this.targets.get(this.scanIndex);
                     if (client.field_1687.method_31606(target.pos)) {
                        this.pauseWith("Outside world build height at " + coordinates(target.pos) + ". Reposition the schematic.");
                        return;
                     }

                     if (!client.field_1687.method_8621().method_11952(target.pos)) {
                        this.pauseWith("Outside the world border at " + coordinates(target.pos) + ". Reposition the schematic.");
                        return;
                     }

                     if (!this.unconfirmed.containsKey(target.pos)
                        && MaterialTracker.isLoaded(client.field_1687, target.pos)
                        && matches(target.state, client.field_1687.method_8320(target.pos))) {
                        this.markCorrect(target);
                     }
                  }

                  if (this.scanIndex < this.targets.size()) {
                     return;
                  }

                  this.scanned = true;
               }

               if (this.correct.cardinality() != this.targets.size()) {
                  this.finalCheckIndex = 0;
                  if (this.pillarFor != null) {
                     this.pillarStep(client);
                     return;
                  }

                  if (this.current == null && this.inflight.size() >= BuildTempo.inflight()) {
                     this.reason = "Waiting for the server (" + this.inflight.size() + " placements in flight)";
                     this.yielded = true;
                     return;
                  }

                  if (this.current == null) {
                     this.current = this.chooseTarget(client);
                  }

                  if (this.current == null && (!this.settling.isEmpty() || !this.inflight.isEmpty())) {
                     this.reason = "Confirming " + (this.settling.size() + this.inflight.size()) + " placed blocks...";
                     this.yielded = true;
                  } else if (this.current == null) {
                     this.pauseWith(
                        this.lastFailure.isEmpty() ? "No placeable blocks remain. Check unloaded areas, supports and occupied positions." : this.lastFailure
                     );
                  } else if (client.field_1687.method_31606(this.current.pos)) {
                     this.pauseWith("Outside world build height at " + coordinates(this.current.pos) + ". Reposition the schematic.");
                  } else if (!client.field_1687.method_8621().method_11952(this.current.pos)) {
                     this.pauseWith("Outside the world border at " + coordinates(this.current.pos) + ". Reposition the schematic.");
                  } else if (!MaterialTracker.isLoaded(client.field_1687, this.current.pos)) {
                     this.defer("Area is unloaded at " + coordinates(this.current.pos) + ". Move closer, then Resume.");
                  } else if (this.unconfirmed.containsKey(this.current.pos)) {
                     // A paired half or an earlier predicted placement: let it settle instead of pausing on it.
                     if (this.current.support) {
                        this.current = null;
                        this.yielded = true;
                     } else {
                        this.park(this.current);
                        this.current = null;
                     }
                  } else {
                     class_2680 actual = client.field_1687.method_8320(this.current.pos);
                     if (matches(this.current.state, actual)) {
                        this.markCorrect(this.current);
                        this.current = null;
                     } else if (MaterialTracker.isSecondary(this.current.state)) {
                        this.defer("Paired block is missing at " + coordinates(this.current.pos) + ". Check its door, bed or plant base.");
                     } else {
                        String manual = manualStateReason(this.current.state, actual);
                        if (manual != null) {
                           this.defer(manual + " at " + coordinates(this.current.pos) + ". Complete it manually, then Resume.");
                        } else if (!PlacementPlanner.canAdd(this.current.state, actual)
                           && !PlacementPlanner.needsConfiguration(this.current.state, actual)) {
                           this.defer("Occupied or incorrect block at " + coordinates(this.current.pos) + ". Correct it manually, then Resume.");
                        } else {
                           MaterialTracker.Requirement requirement = MaterialTracker.requirement(
                              this.current.state
                           );
                           boolean water = needsWater(this.current.state, actual);
                           class_1792 needed = water ? class_1802.field_8705 : requirement.item();
                           if (!water && (requirement.item() == class_1802.field_8162 || !(requirement.item() instanceof class_1747))) {
                              this.defer(
                                 "This block requires manual placement: "
                                    + this.current.state.method_26204().method_9518().getString()
                                    + " at "
                                    + coordinates(this.current.pos)
                              );
                           } else if (this.current.state.method_26184(client.field_1687, this.current.pos) && hasNeighborSupport(client, this.current.pos)) {
                              boolean configuring = PlacementPlanner.needsConfiguration(this.current.state, actual);
                              int slot = configuring ? client.field_1724.method_31548().method_67532() : findSlot(client, needed);
                              if (slot < 0 && BuildRestock.start(client, this, needed)) {
                                 this.reason = BuildRestock.status();
                                 this.yielded = true;
                              } else if (slot < 0) {
                                 this.pauseWith("Missing " + needed.method_63680().getString() + ". Add materials, then Resume.");
                              } else if (slot >= 9) {
                                 int selected = client.field_1724.method_31548().method_67532();
                                 client.field_1761
                                    .method_2906(client.field_1724.field_7498.field_7763, slot, selected, class_1713.field_7791, client.field_1724);
                                 this.cooldown = BuildTempo.swap();
                                 this.reason = "Moving material into the hotbar...";
                              } else if (!client.field_1724.method_24828() && !client.field_1724.method_31549().field_7479 && this.navigator.routeLength() > 0) {
                                 // Mid-step: the target can come into reach at the top of a jump, but holding
                                 // still there drops the player back out of reach, so it walked, jumped and
                                 // re-armed forever. Let the step finish and plan from solid ground.
                                 this.reason = "Walking to " + coordinates(this.current.pos) + " / " + this.navigator.routeLength() + " steps";
                                 this.yielded = true;
                              } else {
                                 client.field_1724.method_31548().method_61496(slot);
                                 PlacementPlanner.Plan plan = water
                                    ? planWater(client, this.current)
                                    : PlacementPlanner.plan(client, this.current.pos, this.current.state, client.field_1724.method_6047());
                                 if (plan == null && !configuring && !water) {
                                    plan = planSampled(client, this.current);
                                 }
                                 this.bucketPlan = water ? plan : null;
                                 Target alternative;
                                 if (plan != null) {
                                    this.navigator.hold(client, shouldSneak(client, this.current, plan));
                                    aim(client, plan);
                                    this.prepared = new Prepared(
                                       this.current, plan, this.tick + BuildTempo.arm()
                                    );
                                    this.reason = (
                                          this.current.support
                                             ? "Placing a temporary support"
                                             : (water ? "Pouring water" : (plan.configure() ? "Setting block state" : "Placing block"))
                                       )
                                       + " at "
                                       + coordinates(this.current.pos);
                                    this.noProgress = 0;
                                 } else if (!water && (alternative = this.reachableAlternative(client, requirement.item())) != null) {
                                    this.current = alternative;
                                 } else {
                                    Target lateral = this.lateralSupport(client, this.current);
                                    if (lateral != null) {
                                       this.current = lateral;
                                    } else {
                                       Target access = this.accessSupport(client, this.current);
                                       if (access != null) {
                                          this.current = access;
                                       } else {
                                          // Walking is one decision per tick; extra steps would count stalls several times over.
                                          this.yielded = true;
                                          this.navigator.walkReach(BuildTempo.walkReach());
                                          BuilderNavigator.Result movement = this.navigator.moveTo(client, this.current.pos);
                                          if (movement == BuilderNavigator.Result.ARRIVED) {
                                             this.navigator.rejectCurrent(client);
                                             this.noProgress++;
                                          } else {
                                             boolean blocked = movement == BuilderNavigator.Result.NO_PATH
                                                || movement == BuilderNavigator.Result.STUCK
                                                || this.noProgress > 80;
                                             if (blocked && this.canPillar(client, this.current)) {
                                                this.startPillar(client, this.current);
                                                return;
                                             }

                                             if (movement == BuilderNavigator.Result.NO_PATH) {
                                                this.defer("No walking route to " + coordinates(this.current.pos) + ". Provide a path or stairs, then Resume.");
                                                return;
                                             }

                                             if (movement == BuilderNavigator.Result.STUCK) {
                                                this.noProgress += 30;
                                             } else {
                                                this.noProgress++;
                                             }
                                          }

                                          this.reason = "Walking to " + coordinates(this.current.pos) + " / " + this.navigator.routeLength() + " steps";
                                          if (this.noProgress > 300) {
                                             this.defer(
                                                "Cannot reach a valid placement face or orientation at "
                                                   + coordinates(this.current.pos)
                                                   + ". Provide access, then Resume."
                                             );
                                          }
                                       }
                                    }
                                 }
                              }
                           } else {
                              Target support = this.necessarySupport(client, this.current);
                              if (support != null) {
                                 this.current = support;
                              } else {
                                 this.defer("Missing support at " + coordinates(this.current.pos) + ". Provide solid supports or access, then Resume.");
                              }
                           }
                        }
                     }
                  }
               } else {
                  this.navigator.release();
                  this.reason = "Verifying the completed structure...";
                  this.yielded = true;

                  for (int end = Math.min(this.targets.size(), this.finalCheckIndex + 2048); this.finalCheckIndex < end; this.finalCheckIndex++) {
                     Target targetx = this.targets.get(this.finalCheckIndex);
                     if (this.unconfirmed.containsKey(targetx.pos)
                        || !MaterialTracker.isLoaded(client.field_1687, targetx.pos)
                        || !matches(targetx.state, client.field_1687.method_8320(targetx.pos))) {
                        this.correct.clear(targetx.index);
                        this.settling.remove(targetx.pos);
                        this.work.add(targetx);
                        this.finalCheckIndex = 0;
                        return;
                     }
                  }

                  if (this.finalCheckIndex == this.targets.size()) {
                     this.complete();
                  }
               }
            }
         }
      }
   }

   private Target chooseTarget(class_310 client) {
      Target best = null;
      double bestScore = Double.POSITIVE_INFINITY;
      if (this.work.isEmpty()) {
         return null;
      } else {
         int minimumY = this.work.first().pos.method_10264();
         int examined = 0;
         this.nearby.clear();
         this.nearbyScores.clear();

         for (Target target : this.work) {
            if (++examined > BuildTempo.candidates() || target.pos.method_10264() > minimumY + BuildTempo.layers()) {
               break;
            }

            double score = client.field_1724.method_73189().method_1025(class_243.method_24953(target.pos))
               + (target.pos.method_10264() - minimumY) * 64
               + (MaterialTracker.isSecondary(target.state) ? 512 : 0);
            if (score < bestScore) {
               bestScore = score;
               best = target;
            }

            // Keep the closest few as fallbacks for when the best one cannot be placed from here.
            if (this.nearby.size() < NEARBY || score < this.nearbyScores.get(this.nearbyScores.size() - 1)) {
               int at = 0;
               while (at < this.nearbyScores.size() && this.nearbyScores.get(at) <= score) {
                  at++;
               }
               this.nearby.add(at, target);
               this.nearbyScores.add(at, score);
               if (this.nearby.size() > NEARBY) {
                  this.nearby.remove(NEARBY);
                  this.nearbyScores.remove(NEARBY);
               }
            }
         }

         return best;
      }
   }

   /**
    * A nearby target of the same material that can be placed from where the player stands, so an
    * awkward block does not send the builder walking while others are within reach.
    */
   private Target reachableAlternative(class_310 client, class_1792 item) {
      class_1799 hand = client.field_1724.method_6047();
      for (Target candidate : this.nearby) {
         // Never build above the block being replaced: rising walls next to the player carry it up and
         // leave the rest of the lower layer behind and below it.
         if (candidate == this.current
            || candidate.pos.method_10264() > this.current.pos.method_10264()
            || candidate.support
            || !this.work.contains(candidate)
            || this.unconfirmed.containsKey(candidate.pos)
            || MaterialTracker.isSecondary(candidate.state)
            || !MaterialTracker.isLoaded(client.field_1687, candidate.pos)
            || MaterialTracker.requirement(candidate.state).item() != item) {
            continue;
         }

         class_2680 actual = client.field_1687.method_8320(candidate.pos);
         if (!actual.method_45474()
            || manualStateReason(candidate.state, actual) != null
            || !candidate.state.method_26184(client.field_1687, candidate.pos)
            || !hasNeighborSupport(client, candidate.pos)) {
            continue;
         }

         if (PlacementPlanner.plan(client, candidate.pos, candidate.state, hand) != null) {
            return candidate;
         }
      }

      return null;
   }

   /**
    * The rotation the server already has, if a ray along it from the player's eye clicks a face
    * that places exactly the wanted block at the target. Placing on that hit needs no further
    * aiming, so a player still drifting after a step does not have to wait to come to rest.
    */
   private static PlacementPlanner.Plan planAlongSent(class_310 client, Target target, float yaw, float pitch) {
      class_746 player = client.field_1724;
      class_1799 stack = player.method_6047();
      if (!(stack.method_7909() instanceof class_1747 item) || !client.field_1687.method_8320(target.pos).method_45474()) {
         return null;
      }

      double yawRad = Math.toRadians(yaw);
      double pitchRad = Math.toRadians(pitch);
      class_243 look = new class_243(-Math.sin(yawRad) * Math.cos(pitchRad), -Math.sin(pitchRad), Math.cos(yawRad) * Math.cos(pitchRad));
      class_243 eye = player.method_33571();
      double reach = player.method_55754() - 0.05;
      class_3965 hit = client.field_1687.method_17742(new class_3959(eye, eye.method_1019(look.method_1021(reach)), class_3960.field_17559, class_242.field_1348, player));
      if (hit.method_17783() != class_240.field_1332 || hit.method_17781()) {
         return null;
      }

      float oldYaw = player.method_36454();
      float oldPitch = player.method_36455();
      try {
         player.method_36456(yaw);
         player.method_36457(pitch);
         class_1750 context = item.method_16356(new class_1750(player, class_1268.field_5808, stack, hit));
         if (context != null && context.method_8037().equals(target.pos) && context.method_7716()) {
            class_2680 predicted = ((BlockItemAccessor)item).donut$getPlacementState(context);
            if (compatible(target.state, predicted)) {
               return new PlacementPlanner.Plan(hit, predicted, yaw, pitch, false);
            }
         }
         return null;
      } finally {
         player.method_36456(oldYaw);
         player.method_36457(oldPitch);
      }
   }

   /**
    * The planner tries three points per face. Blocks whose state follows the player's look (signs,
    * banners, skulls, stairs) can need a particular angle, so this samples a grid across every
    * face that could hold the block and keeps the first point that predicts the wanted state.
    */
   private static PlacementPlanner.Plan planSampled(class_310 client, Target target) {
      class_746 player = client.field_1724;
      class_1799 stack = player.method_6047();
      if (!(stack.method_7909() instanceof class_1747 item) || !client.field_1687.method_8320(target.pos).method_45474()) {
         return null;
      }

      class_243 eye = player.method_33571();
      double reach = player.method_55754() - 0.05;
      float oldYaw = player.method_36454();
      float oldPitch = player.method_36455();
      try {
         for (class_2350 face : class_2350.values()) {
            class_2338 support = target.pos.method_10093(face.method_10153());
            if (!MaterialTracker.isLoaded(client.field_1687, support)) {
               continue;
            }
            class_2680 supportState = client.field_1687.method_8320(support);
            if (supportState.method_26215() || supportState.method_45474()) {
               continue;
            }
            class_265 shape = supportState.method_26218(client.field_1687, support);
            if (shape.method_1110()) {
               continue;
            }
            class_238 box = shape.method_1107();
            for (int i = 0; i < 5; i++) {
               for (int j = 0; j < 5; j++) {
                  double u = 0.1 + 0.2 * i;
                  double v = 0.1 + 0.2 * j;
                  // Spread u and v across the two axes that lie in this face.
                  class_2350.class_2351 axis = face.method_10166();
                  double fx = axis == class_2350.class_2351.field_11048 ? 0.5 : u;
                  double fy = axis == class_2350.class_2351.field_11052 ? 0.5 : (axis == class_2350.class_2351.field_11048 ? u : v);
                  double fz = axis == class_2350.class_2351.field_11051 ? 0.5 : v;
                  double x = box.field_1323 + (box.field_1320 - box.field_1323) * fx;
                  double y = box.field_1322 + (box.field_1325 - box.field_1322) * fy;
                  double z = box.field_1321 + (box.field_1324 - box.field_1321) * fz;
                  switch (face) {
                     case field_11036 -> y = box.field_1325;
                     case field_11033 -> y = box.field_1322;
                     case field_11034 -> x = box.field_1320;
                     case field_11039 -> x = box.field_1323;
                     case field_11035 -> z = box.field_1324;
                     case field_11043 -> z = box.field_1321;
                  }
                  class_243 point = new class_243(support.method_10263() + x, support.method_10264() + y, support.method_10260() + z);
                  if (eye.method_1025(point) > reach * reach) {
                     continue;
                  }
                  class_243 inside = point.method_1020(class_243.method_24954(face.method_62675()).method_1021(0.002));
                  class_3965 visible = client.field_1687.method_17742(new class_3959(eye, inside, class_3960.field_17559, class_242.field_1348, player));
                  if (visible.method_17783() != class_240.field_1332 || !visible.method_17777().equals(support) || visible.method_17780() != face) {
                     continue;
                  }
                  class_243 delta = point.method_1020(eye);
                  float yaw = (float)Math.toDegrees(Math.atan2(-delta.field_1352, delta.field_1350));
                  float pitch = (float)Math.toDegrees(Math.atan2(-delta.field_1351, Math.hypot(delta.field_1352, delta.field_1350)));
                  player.method_36456(yaw);
                  player.method_36457(pitch);
                  class_3965 hit = new class_3965(point, face, support, false);
                  class_1750 context = item.method_16356(new class_1750(player, class_1268.field_5808, stack, hit));
                  if (context != null && context.method_8037().equals(target.pos) && context.method_7716()) {
                     class_2680 predicted = ((BlockItemAccessor)item).donut$getPlacementState(context);
                     if (compatible(target.state, predicted)) {
                        return new PlacementPlanner.Plan(hit, predicted, yaw, pitch, false);
                     }
                  }
               }
            }
         }
         return null;
      } finally {
         player.method_36456(oldYaw);
         player.method_36457(oldPitch);
      }
   }

   private void revalidate(class_310 client, int budget) {
      for (int checked = 0; checked < Math.min(budget, this.targets.size()); checked++) {
         if (this.revalidateIndex >= this.targets.size()) {
            this.revalidateIndex = 0;
         }

         Target target = this.targets.get(this.revalidateIndex++);
         if (!this.unconfirmed.containsKey(target.pos) && (this.pending == null || !this.pending.target.pos.equals(target.pos))) {
            boolean matches = MaterialTracker.isLoaded(client.field_1687, target.pos)
               && matches(target.state, client.field_1687.method_8320(target.pos));
            if (matches) {
               this.markCorrect(target);
            } else if (this.correct.get(target.index) || this.settling.containsKey(target.pos)) {
               this.correct.clear(target.index);
               this.unsettle(target);
            }
         }
      }
   }

   private void sendPrepared(class_310 client) {
      Prepared action = this.prepared;
      this.prepared = null;
      if (action.target.equals(this.current)) {
         boolean bucket = action.plan == this.bucketPlan;
         PlacementPlanner.Plan plan = bucket
            ? planWater(client, this.current)
            : PlacementPlanner.plan(client, this.current.pos, this.current.state, client.field_1724.method_6047());
         if (plan == null && !action.plan.configure() && !bucket) {
            plan = planSampled(client, this.current);
         }
         this.bucketPlan = bucket ? plan : null;
         if (plan == null) {
            this.navigator.rejectCurrent(client);
         } else {
            boolean sneak = shouldSneak(client, this.current, plan);
            this.navigator.hold(client, sneak);
            aim(client, plan);
            BuilderLocalPlayerAccessor sent = (BuilderLocalPlayerAccessor)client.field_1724;
            if (!plan.configure()
               && !bucket
               && sent.donut$lastSentInput() != null
               && sent.donut$lastSentInput().comp_3164() == sneak
               && (Math.abs(class_3532.method_15393(sent.donut$lastSentYaw() - plan.yaw())) > 0.001F || Math.abs(sent.donut$lastSentPitch() - plan.pitch()) > 0.001F)) {
               PlacementPlanner.Plan along = planAlongSent(client, this.current, sent.donut$lastSentYaw(), sent.donut$lastSentPitch());
               if (along != null && shouldSneak(client, this.current, along) == sneak) {
                  plan = along;
                  aim(client, plan);
               }
            }

            if (!(Math.abs(class_3532.method_15393(sent.donut$lastSentYaw() - plan.yaw())) > 0.001F)
               && !(Math.abs(sent.donut$lastSentPitch() - plan.pitch()) > 0.001F)
               && sent.donut$lastSentInput() != null
               && sent.donut$lastSentInput().comp_3164() == sneak) {
               this.send(client, this.current, plan);
            } else {
               this.prepared = new Prepared(this.current, plan, this.tick + 1);
               this.reason = (bucket ? "Aligning to pour water at " : "Aligning for placement at ") + coordinates(this.current.pos);
            }
         }
      }
   }

   // ---- Fluids and kelp ----------------------------------------------------------------------

   /** Flowing water and lava form on their own from a source; they are never placed. */
   private static boolean derived(class_2680 state) {
      return (state.method_27852(class_2246.field_10382) || state.method_27852(class_2246.field_10164)) && !state.method_26227().method_15771();
   }

   /** A water source, or a bubble column, which is a water source over soul sand or magma. */
   private static boolean waterSource(class_2680 state) {
      return state.method_27852(class_2246.field_10382) && state.method_26227().method_15771() || state.method_27852(class_2246.field_10422);
   }

   private static boolean kelp(class_2680 state) {
      return state.method_27852(class_2246.field_9993) || state.method_27852(class_2246.field_10463);
   }

   /**
    * Whether a world block satisfies a blueprint block. Exact, except where the game decides the
    * rest: any water source fills a water or bubble-column cell, and kelp counts as built at any
    * age, as kelp or as the kelp plant it turns into once more kelp grows on top.
    */
   public static boolean matches(class_2680 desired, class_2680 actual) {
      if (MaterialTracker.matches(desired, actual)) {
         return true;
      } else if (waterSource(desired)) {
         return waterSource(actual);
      } else if (kelp(desired)) {
         return kelp(actual);
      } else if (desired.method_26204() != actual.method_26204()) {
         return false;
      }

      // Same block: ignore what the world keeps changing by itself.
      boolean connects = desired.method_26204() instanceof class_2457 || desired.method_26204() instanceof class_2310 || desired.method_26204() instanceof class_2544;
      for (class_2769<?> property : desired.method_28501()) {
         String name = property.method_11899();
         if (RUNTIME.contains(name) || connects && CONNECTIONS.contains(name)) {
            continue;
         }
         if (!desired.method_11654(property).equals(actual.method_11654(property))) {
            return false;
         }
      }
      return true;
   }

   /** Properties redstone, light and weather set at run time, not part of what was built. */
   private static final Set<String> RUNTIME = Set.of("power", "powered", "lit", "locked", "in_wall", "snowy", "distance", "triggered", "enabled");
   /** Sides that redstone dust, fences, panes, bars and walls work out from their neighbours. */
   private static final Set<String> CONNECTIONS = Set.of("north", "east", "south", "west", "up");

   private static boolean compatible(class_2680 desired, class_2680 predicted) {
      return PlacementPlanner.compatible(desired, predicted) || predicted != null && kelp(desired) && kelp(predicted);
   }

   /** A water cell with no source yet, or a kelp cell that needs its water before the kelp. */
   private static boolean needsWater(class_2680 desired, class_2680 actual) {
      return waterSource(desired) && !waterSource(actual) || kelp(desired) && !waterSource(actual) && !kelp(actual);
   }

   /**
    * Where to aim a water bucket so the water lands in the target. A bucket clicks the first solid
    * face along the look, ignoring fluids, and fills the cell in front of it; a waterloggable block
    * would take the water itself, so those faces are skipped.
    */
   private static PlacementPlanner.Plan planWater(class_310 client, Target target) {
      class_746 player = client.field_1724;
      class_243 eye = player.method_33571();
      double reach = player.method_55754() - 0.05;
      for (class_2350 face : class_2350.values()) {
         class_2338 support = target.pos.method_10093(face.method_10153());
         if (!MaterialTracker.isLoaded(client.field_1687, support)) {
            continue;
         }
         class_2680 state = client.field_1687.method_8320(support);
         if (state.method_26215() || state.method_45474() || state.method_28498(class_2741.field_12508)) {
            continue;
         }
         class_265 shape = state.method_26218(client.field_1687, support);
         if (shape.method_1110()) {
            continue;
         }
         class_238 box = shape.method_1107();
         for (double u : new double[]{0.5, 0.25, 0.75}) {
            for (double v : new double[]{0.5, 0.25, 0.75}) {
               class_2350.class_2351 axis = face.method_10166();
               double fx = axis == class_2350.class_2351.field_11048 ? 0.5 : u;
               double fy = axis == class_2350.class_2351.field_11052 ? 0.5 : (axis == class_2350.class_2351.field_11048 ? u : v);
               double fz = axis == class_2350.class_2351.field_11051 ? 0.5 : v;
               double x = box.field_1323 + (box.field_1320 - box.field_1323) * fx;
               double y = box.field_1322 + (box.field_1325 - box.field_1322) * fy;
               double z = box.field_1321 + (box.field_1324 - box.field_1321) * fz;
               switch (face) {
                  case field_11036 -> y = box.field_1325;
                  case field_11033 -> y = box.field_1322;
                  case field_11034 -> x = box.field_1320;
                  case field_11039 -> x = box.field_1323;
                  case field_11035 -> z = box.field_1324;
                  case field_11043 -> z = box.field_1321;
               }
               class_243 point = new class_243(support.method_10263() + x, support.method_10264() + y, support.method_10260() + z);
               if (eye.method_1025(point) > reach * reach) {
                  continue;
               }
               class_243 inside = point.method_1020(class_243.method_24954(face.method_62675()).method_1021(0.002));
               class_3965 seen = client.field_1687.method_17742(new class_3959(eye, inside, class_3960.field_17559, class_242.field_1348, player));
               if (seen.method_17783() != class_240.field_1332 || !seen.method_17777().equals(support) || seen.method_17780() != face) {
                  continue;
               }
               class_243 delta = point.method_1020(eye);
               float yaw = (float)Math.toDegrees(Math.atan2(-delta.field_1352, delta.field_1350));
               float pitch = (float)Math.toDegrees(Math.atan2(-delta.field_1351, Math.hypot(delta.field_1352, delta.field_1350)));
               return new PlacementPlanner.Plan(new class_3965(point, face, support, false), class_2246.field_10382.method_9564(), yaw, pitch, false);
            }
         }
      }
      return null;
   }

   /** True for a few seconds after this builder placed a sign. */
   private boolean placedSignRecently() {
      return this.tick - this.signTick <= 100;
   }

   /** Sends one placement the server already has the rotation for, and tracks it until it answers. */
   private void send(class_310 client, Target target, PlacementPlanner.Plan plan) {
      if (target.state.method_26204() instanceof class_2478) {
         this.signTick = this.tick;
      }

      class_2680 before = client.field_1687.method_8320(target.pos);
      boolean bucket = plan == this.bucketPlan;
      // A bucket is used, not clicked on a block: the server finds the face along the look itself.
      class_1269 result = bucket
         ? client.field_1761.method_2919(client.field_1724, class_1268.field_5808)
         : client.field_1761.method_2896(client.field_1724, class_1268.field_5808, plan.hit());
      int sequence = ((BuilderClientLevelAccessor)client.field_1687).donut$predictionHandler().method_41942();
      this.unconfirmed.put(target.pos, sequence);
      class_2338 secondary = secondaryPosition(target);
      if (secondary != null) {
         this.unconfirmed.put(secondary, sequence);
      }

      this.pending = new Pending(target, before, plan.prediction(), this.tick, sequence);
      this.pending.bucket = bucket;
      this.bucketPlan = null;
      this.placedTick = this.tick;
      this.placements++;
      if (result.method_23665()) {
         client.field_1724.method_6104(class_1268.field_5808);
      }

      this.reason = "Waiting for the server at " + coordinates(target.pos);
   }

   // ---- Pillaring -------------------------------------------------------------------------------

   private static final List<class_1792> PILLAR_BLOCKS = List.of(
      class_1802.field_8831, class_1802.field_20412, class_1802.field_29025, class_1802.field_8328, class_1802.field_20391
   );

   /** The first pillar block the player has, or null. */
   private static class_1792 pillarMaterial(class_310 client) {
      for (class_1792 candidate : PILLAR_BLOCKS) {
         if (findSlot(client, candidate) >= 0) {
            return candidate;
         }
      }
      return null;
   }

   /**
    * A target too high to walk up to can be reached by pillaring straight up where the player
    * stands, as long as that column is open air the blueprint does not use.
    */
   private boolean canPillar(class_310 client, Target target) {
      if (target.support || !BuildTempo.pillar() || !client.field_1724.method_24828()) {
         return false;
      }

      class_2338 feet = client.field_1724.method_24515();
      int goal = pillarGoal(target, feet.method_10264());
      if (target.pos.method_10264() <= feet.method_10264() || goal - feet.method_10264() > 40 || pillarMaterial(client) == null) {
         return false;
      }

      for (int y = feet.method_10264(); y <= goal + 2; y++) {
         class_2338 cell = new class_2338(feet.method_10263(), y, feet.method_10260());
         if (!MaterialTracker.isLoaded(client.field_1687, cell) || this.indices.containsKey(cell) || !client.field_1687.method_8320(cell).method_45474()) {
            return false;
         }
      }

      return true;
   }

   /** Stand a block below the target, and always climb at least one block. */
   private static int pillarGoal(Target target, int feet) {
      return Math.max(target.pos.method_10264() - 1, feet + 1);
   }

   private void startPillar(class_310 client, Target target) {
      this.pillarFor = target;
      this.pillarGoal = pillarGoal(target, client.field_1724.method_24515().method_10264());
      this.pillarFails = 0;
      this.pillarBase = client.field_1724.method_24515().method_10264();
      this.navigator.reset();
      this.noProgress = 0;
      this.reason = "Pillaring up to " + coordinates(target.pos);
   }

   /**
    * One tick of pillaring: look straight down, jump, and once the feet clear the block below,
    * place a pillar block there along the rotation the server already has.
    */
   private void pillarStep(class_310 client) {
      this.yielded = true;
      class_746 player = client.field_1724;
      int feet = player.method_24515().method_10264();
      // Height only counts standing on the pillar: at the top of a hop the feet are already a block up.
      if (player.method_24828() && feet >= this.pillarGoal || this.pillarFails > 6) {
         if (feet < this.pillarGoal) {
            this.lastFailure = "Could not pillar up at " + coordinates(player.method_24515());
         }
         this.pillarFor = null;
         this.current = null;
         this.navigator.reset();
         return;
      }

      class_1792 material = pillarMaterial(client);
      if (material == null) {
         this.pillarFor = null;
         this.pauseWith("Out of pillar blocks. Carry dirt, cobblestone, cobbled deepslate, netherrack or stone, then Resume.");
         return;
      }

      int slot = findSlot(client, material);
      if (slot >= 9) {
         int selected = player.method_31548().method_67532();
         client.field_1761.method_2906(player.field_7498.field_7763, slot, selected, class_1713.field_7791, player);
         this.cooldown = BuildTempo.swap();
         return;
      }

      player.method_31548().method_61496(slot);
      this.navigator.hold(client, false);
      player.method_36457(90.0F);
      BuilderLocalPlayerAccessor sent = (BuilderLocalPlayerAccessor)player;
      this.reason = "Pillaring up to " + coordinates(this.pillarFor.pos) + " (" + Math.max(0, this.pillarGoal - feet) + " to go)";
      if (player.method_24828()) {
         this.pillarBase = feet;
         this.pillarPlaced = false;
         if (sent.donut$lastSentPitch() > 89.0F && client.field_1687.method_8320(player.method_24515().method_10086(2)).method_45474()) {
            player.method_6043();
         }
         return;
      }

      class_2338 cell = new class_2338(player.method_24515().method_10263(), this.pillarBase, player.method_24515().method_10260());
      if (this.pillarPlaced || player.method_23318() < this.pillarBase + 1.0 || !client.field_1687.method_8320(cell).method_45474()) {
         return;
      }

      class_1747 item = (class_1747)material;
      Target support = new Target(cell, item.method_7711().method_9564(), -1, true);
      PlacementPlanner.Plan plan = planAlongSent(client, support, sent.donut$lastSentYaw(), sent.donut$lastSentPitch());
      this.pillarPlaced = true;
      if (plan == null) {
         this.pillarFails++;
         return;
      }

      this.send(client, support, plan);
   }

   private static void aim(class_310 client, PlacementPlanner.Plan plan) {
      client.field_1724.method_36456(plan.yaw());
      client.field_1724.method_36457(plan.pitch());
   }

   public void serverBlockUpdate(class_2338 pos, class_2680 state) {
      StabilityGate.invalidate(this, pos);
      this.unconfirmed.remove(pos);
      Integer predicted = this.inflight.remove(pos);
      if (this.session != null) {
         Integer index = this.indices.get(pos);
         if (index != null) {
            class_2680 desired = this.transform.apply(this.schematic.blocks().get(index).state());
            if (matches(desired, state)) {
               this.markCorrect(this.byIndex[index]);
               this.deferred.remove(pos);
               if (predicted != null) {
                  this.failures.remove(pos);
               }
            } else {
               this.correct.clear(index);
               this.unsettle(this.byIndex[index]);
               this.finalCheckIndex = 0;
               if (predicted != null) {
                  this.predictionRefused(pos);
               }
            }
         }

         if (this.pending != null && this.pending.target.pos.equals(pos)) {
            this.pending.observed = state;
         }
      }
   }

   public void serverAcknowledged(int sequence) {
      this.unconfirmed.entrySet().removeIf(entry -> entry.getValue() <= sequence);
      this.inflight.keySet().retainAll(this.unconfirmed.keySet());
      if (this.pending != null && sequence >= this.pending.sequence) {
         this.pending.acknowledged = true;
      }
   }

   public void positionCorrected() {
      if (this.isRunning()) {
         this.navigator.replan();
         this.prepared = null;
         this.reason = "Server corrected the position. Replanning...";
      }
   }

   private void finishPending(class_310 client) {
      Pending action = this.pending;
      if (!action.acknowledged && action.observed == null) {
         this.pauseWith("No server confirmation at " + coordinates(action.target.pos) + ". Check the connection, then Resume.");
      } else {
         this.pending = null;
         class_2680 actual = action.observed == null ? client.field_1687.method_8320(action.target.pos) : action.observed;
         boolean changed = action.acknowledged && !actual.equals(action.before);
         if (action.observed != null && !action.observed.equals(action.before)) {
            changed = true;
         }

         if (action.bucket && kelp(action.target.state) && waterSource(actual)) {
            // The water a kelp cell needs is in; the kelp itself is the next placement.
            this.failures.remove(action.target.pos);
            this.current = null;
            this.noProgress = 0;
            this.cooldown = BuildTempo.settleDelay();
            return;
         }

         if (matches(action.target.state, actual) && changed) {
            this.markCorrect(action.target);
            this.failures.remove(action.target.pos);

            for (class_2338 pos : this.deferred) {
               Integer index = this.indices.get(pos);
               if (index != null && !this.correct.get(index)) {
                  this.work.add(this.byIndex[index]);
               }
            }

            this.deferred.clear();
            this.current = null;
            this.noProgress = 0;
            this.cooldown = BuildTempo.settleDelay();
         } else if (!changed
            || actual.method_26204() != action.target.state.method_26204()
            || !PlacementPlanner.canAdd(action.target.state, actual)
               && !PlacementPlanner.needsConfiguration(action.target.state, actual)) {
            int attempts = this.failures.merge(action.target.pos, 1, Integer::sum);
            this.navigator.rejectCurrent(client);
            this.cooldown = 4;
            this.reason = "Server rejected placement at " + coordinates(action.target.pos) + " (" + attempts + "/3). Replanning...";
            if (attempts >= 3) {
               this.pauseWith(
                  "Server rejected or changed the requested state at "
                     + coordinates(action.target.pos)
                     + ". Check build permissions, supports and state compatibility, then Resume."
               );
            }
         } else {
            this.failures.remove(action.target.pos);
            this.cooldown = BuildTempo.settleDelay();
         }
      }
   }

   /**
    * Finishes the placement in flight on the client's own prediction. The position stays
    * unconfirmed until the server acknowledges it, the block waits in the settling set rather
    * than counting as built, and a refusal from the server puts it straight back in the queue.
    * If the client did not even predict a change there is nothing to trust, so it keeps waiting
    * for the server as before.
    */
   private void finishPredicted(class_310 client) {
      Pending action = this.pending;
      if (action.acknowledged || action.observed != null) {
         this.finishPending(client);
         return;
      }

      class_2680 actual = client.field_1687.method_8320(action.target.pos);
      if (action.bucket && kelp(action.target.state)) {
         if (waterSource(actual)) {
            this.pending = null;
            this.current = null;
            this.noProgress = 0;
            this.cooldown = BuildTempo.settleDelay();
         }
         return;
      }

      if (actual.equals(action.before) || !matches(action.target.state, actual)) {
         return;
      }

      this.pending = null;
      if (action.target.support) {
         this.supportBlocks.add(action.target.pos);
      } else {
         this.inflight.put(action.target.pos, action.sentTick);
         this.markCorrect(action.target);
      }

      for (class_2338 pos : this.deferred) {
         Integer index = this.indices.get(pos);
         if (index != null && !this.correct.get(index)) {
            this.work.add(this.byIndex[index]);
         }
      }

      this.deferred.clear();
      this.current = null;
      this.noProgress = 0;
      this.cooldown = BuildTempo.settleDelay();
   }

   /** The server answered a predicted placement with a different block. */
   private void predictionRefused(class_2338 pos) {
      int attempts = this.failures.merge(pos, 1, Integer::sum);
      if (this.isRunning()) {
         this.prepared = null;
         this.cooldown = Math.max(this.cooldown, 4);
         this.reason = "Server rejected placement at " + coordinates(pos) + " (" + attempts + "/3). Replanning...";
         if (attempts >= 3) {
            this.pauseWith(
               "Server rejected or changed the requested state at "
                  + coordinates(pos)
                  + ". Check build permissions, supports and state compatibility, then Resume."
            );
         }
      }
   }

   /**
    * Predicted placements the server never answers would otherwise sit in flight forever. This
    * pauses the build the same way an unacknowledged placement always has.
    */
   private boolean checkInflight() {
      int window = BuildTempo.ack();
      for (Map.Entry<class_2338, Integer> entry : this.inflight.entrySet()) {
         if (this.tick - entry.getValue() > window) {
            class_2338 pos = entry.getKey();
            this.inflight.clear();
            this.pauseWith("No server confirmation at " + coordinates(pos) + ". Check the connection, then Resume.");
            return false;
         }
      }

      return true;
   }

   /** Takes a matching block out of the work set until it has proven stable. */
   private void park(Target target) {
      if (this.settling.putIfAbsent(target.pos, target) == null) {
         this.settleQueue.addLast(new Settle(target, this.tick + 1, this.tick));
      }

      this.work.remove(target);
   }

   /** Returns a block that stopped matching to the work set. */
   private void unsettle(Target target) {
      this.settling.remove(target.pos);
      if (!this.deferred.contains(target.pos)) {
         this.work.add(target);
      }
   }

   /** Re-checks settling blocks that are due; they become correct once the stability gate agrees. */
   private void settle(class_310 client) {
      int budget = 4096;
      while (budget-- > 0 && !this.settleQueue.isEmpty() && this.settleQueue.peekFirst().due() <= this.tick) {
         Settle entry = this.settleQueue.pollFirst();
         Target target = entry.target();
         if (this.settling.get(target.pos) != target) {
            continue;
         }

         if (this.correct.get(target.index)) {
            this.settling.remove(target.pos);
         } else if (this.unconfirmed.containsKey(target.pos)) {
            if (this.tick - entry.since() > BuildTempo.ack() * 2) {
               // The same pause the builder always made for a position the server never confirmed.
               this.pauseWith("Still waiting for server confirmation at " + coordinates(target.pos) + ". Resume after the connection recovers.");
               return;
            }

            this.settleQueue.addLast(new Settle(target, this.tick + 1, entry.since()));
         } else {
            boolean loaded = MaterialTracker.isLoaded(client.field_1687, target.pos);
            boolean matches = loaded && matches(target.state, client.field_1687.method_8320(target.pos));
            if (!matches) {
               StabilityGate.invalidate(this, target.pos);
               this.unsettle(target);
            } else if (StabilityGate.confirm(this, target.pos, this.tick, true, true)) {
               this.settling.remove(target.pos);
               this.correct.set(target.index);
               this.work.remove(target);
            } else {
               this.settleQueue.addLast(new Settle(target, this.tick + 1, entry.since()));
            }
         }
      }
   }

   private void markCorrect(Target target) {
      if (target.support) {
         this.supportBlocks.add(target.pos);
      } else if (!this.correct.get(target.index)) {
         if (StabilityGate.confirm(
            this,
            target.pos,
            this.tick,
            MaterialTracker.isLoaded(this.session, target.pos),
            matches(target.state, this.session.method_8320(target.pos))
         )) {
            this.settling.remove(target.pos);
            this.correct.set(target.index);
            this.work.remove(target);
         } else {
            this.park(target);
         }
      }
   }

   private void defer(String message) {
      this.lastFailure = message;
      if (this.current != null) {
         this.deferReasons.put(this.current.pos, message);
      }
      if (this.current.support) {
         this.pauseWith(message);
         this.current = null;
      } else {
         this.deferred.add(this.current.pos);
         this.work.remove(this.current);
         this.current = null;
         this.noProgress = 0;
         this.navigator.reset();
      }
   }

   private void complete() {
      this.status = Status.COMPLETED;
      this.navigator.reset();
      this.restoreSlot();
      this.current = null;
      this.reason = "Complete: "
         + this.correct.cardinality()
         + " blocks confirmed."
         + (this.supportBlocks.isEmpty() ? "" : " " + this.supportBlocks.size() + " temporary supports remain.");
      if (this.schematic.blocks().stream().anyMatch(block -> block.blockEntity() != null)) {
         this.reason = this.reason + " Stored block-entity data needs server support.";
      }
   }

   private static String coordinates(class_2338 pos) {
      return pos.method_10263() + ", " + pos.method_10264() + ", " + pos.method_10260();
   }

   private static int donut$findInventorySlot(class_310 client, class_1792 item) {
      for (int i = 0; i < 36; i++) {
         if (client.field_1724.method_31548().method_5438(i).method_31574(item)) {
            return i;
         }
      }

      return -1;
   }

   private static class_2338 secondaryPosition(Target target) {
      if ((target.state.method_26204() instanceof class_2323 || target.state.method_26204() instanceof class_2320)
         && target.state.method_11654(class_2741.field_12533) == class_2756.field_12607) {
         return target.pos.method_10084();
      } else {
         return target.state.method_26204() instanceof class_2244 && target.state.method_11654(class_2244.field_9967) == class_2742.field_12557
            ? target.pos.method_10093((class_2350)target.state.method_11654(class_2244.field_11177))
            : null;
      }
   }

   private static boolean shouldSneak(
      class_310 client, Target target, PlacementPlanner.Plan plan
   ) {
      return !plan.configure() && client.field_1687.method_8320(target.pos).method_26204() != target.state.method_26204();
   }

   private static String manualStateReason(class_2680 desired, class_2680 actual) {
      if (!desired.method_28498(class_2741.field_12508)
         || !(Boolean)desired.method_11654(class_2741.field_12508)
         || desired.method_26204() == actual.method_26204() && (Boolean)actual.method_11654(class_2741.field_12508)) {
         for (class_2769<?> property : kelp(desired) ? List.<class_2769<?>>of() : desired.method_28501()) {
            if ((property.method_11899().equals("age") || property.method_11899().equals("stage"))
               && desired.method_11654(property) instanceof Integer age
               && age > 0) {
               return "This grown block state requires growth or server support";
            }
         }

         if (desired.method_26204() instanceof class_5544 && (Boolean)desired.method_11654(class_2741.field_12548)) {
            return "Lit candles require ignition";
         } else {
            return desired.method_26204() instanceof class_2397 && !desired.method_11654(class_2741.field_12514)
               ? "Natural leaf persistence requires server state support"
               : null;
         }
      } else {
         return "Waterlogged blocks require water placement";
      }
   }

   private static boolean hasNeighborSupport(class_310 client, class_2338 pos) {
      class_2680 actual = client.field_1687.method_8320(pos);
      if (!actual.method_26215()) {
         return true;
      } else {
         for (class_2350 direction : class_2350.values()) {
            class_2338 neighbor = pos.method_10093(direction);
            if (MaterialTracker.isLoaded(client.field_1687, neighbor)) {
               class_2680 state = client.field_1687.method_8320(neighbor);
               if (!state.method_45474() && !state.method_26220(client.field_1687, neighbor).method_1110()) {
                  return true;
               }
            }
         }

         return false;
      }
   }

   private Target necessarySupport(
      class_310 client, Target target
   ) {
      class_2338 below = target.pos.method_10074();
      if (this.indices.containsKey(below)) {
         return null;
      } else {
         class_1792 material = null;

         for (class_1792 candidate : List.of(class_1802.field_8831, class_1802.field_20412, class_1802.field_20391, class_1802.field_8328)) {
            if (findSlot(client, candidate) >= 0) {
               material = candidate;
               break;
            }
         }

         if (!(material instanceof class_1747 item)) {
            return null;
         } else {
            class_2338 var10 = below;

            for (int depth = 0; depth < 12; var10 = var10.method_10074()) {
               if (!MaterialTracker.isLoaded(client.field_1687, var10)
                  || this.indices.containsKey(var10)
                  || !client.field_1687.method_8320(var10).method_45474()) {
                  return null;
               }

               class_2338 foundation = var10.method_10074();
               if (!MaterialTracker.isLoaded(client.field_1687, foundation)) {
                  return null;
               }

               if (client.field_1687.method_8320(foundation).method_26234(client.field_1687, foundation)) {
                  return new Target(var10, item.method_7711().method_9564(), -1, true);
               }

               depth++;
            }

            return null;
         }
      }
   }

   private Target lateralSupport(
      class_310 client, Target target
   ) {
      boolean topSlab = target.state.method_26204() instanceof class_2482 && target.state.method_11654(class_2482.field_11501) == class_2771.field_12679;
      boolean topStair = target.state.method_26204() instanceof class_2510 && target.state.method_11654(class_2510.field_11572) == class_2760.field_12619;
      if (!target.support && (topSlab || topStair)) {
         List<class_2350> directions = new ArrayList<>();
         if (topStair) {
            class_2350 facing = (class_2350)target.state.method_11654(class_2510.field_11571);
            directions.add(facing);
            directions.add(facing.method_10170());
            directions.add(facing.method_10160());
         } else {
            for (class_2350 direction : class_2353.field_11062) {
               directions.add(direction);
            }

            directions.sort(
               Comparator.comparingDouble(
                  directionx -> -client.field_1724.method_73189().method_1025(class_243.method_24953(target.pos.method_10093(directionx)))
               )
            );
         }

         for (class_2350 direction : directions) {
            class_2338 neighbor = target.pos.method_10093(direction);
            if (MaterialTracker.isLoaded(client.field_1687, neighbor)) {
               class_2680 state = client.field_1687.method_8320(neighbor);
               class_265 shape = state.method_26220(client.field_1687, neighbor);
               if (!state.method_45474() && !shape.method_1110() && shape.method_1107().field_1325 > 0.65) {
                  return null;
               }
            }
         }

         class_1792 material = null;

         for (class_1792 candidate : List.of(class_1802.field_8831, class_1802.field_20412, class_1802.field_20391, class_1802.field_8328)) {
            if (findSlot(client, candidate) >= 0) {
               material = candidate;
               break;
            }
         }

         if (material instanceof class_1747 item) {
            for (class_2350 directionx : directions) {
               class_2338 neighbor = target.pos.method_10093(directionx);
               if (!this.indices.containsKey(neighbor)
                  && MaterialTracker.isLoaded(client.field_1687, neighbor)
                  && MaterialTracker.isLoaded(client.field_1687, neighbor.method_10074())
                  && client.field_1687.method_8320(neighbor).method_45474()
                  && client.field_1687.method_8320(neighbor.method_10074()).method_26234(client.field_1687, neighbor.method_10074())) {
                  return new Target(neighbor, item.method_7711().method_9564(), -1, true);
               }
            }

            return null;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private Target accessSupport(
      class_310 client, Target target
   ) {
      if (!target.support && !(target.pos.method_10264() - client.field_1724.method_23318() < 1.6)) {
         class_1792 material = null;

         for (class_1792 candidate : List.of(class_1802.field_8831, class_1802.field_20412, class_1802.field_20391, class_1802.field_8328)) {
            if (findSlot(client, candidate) >= 0) {
               material = candidate;
               break;
            }
         }

         if (material instanceof class_1747 item) {
            Target var14 = null;
            double distance = Double.POSITIVE_INFINITY;

            for (class_2350 direction : class_2353.field_11062) {
               class_2338 step = target.pos.method_10087(2).method_10093(direction);
               if (MaterialTracker.isLoaded(client.field_1687, step)
                  && client.field_1687.method_8320(step).method_26234(client.field_1687, step)) {
                  return null;
               }
            }

            for (class_2350 directionx : class_2353.field_11062) {
               class_2338 step = target.pos.method_10087(2).method_10093(directionx);
               if (!this.indices.containsKey(step)
                  && MaterialTracker.isLoaded(client.field_1687, step)
                  && client.field_1687.method_8320(step).method_45474()
                  && client.field_1687.method_8320(step.method_10074()).method_26234(client.field_1687, step.method_10074())) {
                  double candidatex = client.field_1724.method_73189().method_1025(class_243.method_24953(step));
                  if (candidatex < distance) {
                     distance = candidatex;
                     var14 = new Target(step, item.method_7711().method_9564(), -1, true);
                  }
               }
            }

            return var14;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   public void getMaterials(class_310 client) {
      if (this.schematic != null && client.field_1724 != null && client.field_1687 != null && client.field_1761 != null) {
         if (client.field_1724.method_31549().field_7477 && client.field_1761.method_2920().method_8386()) {
            if (client.field_1724.field_7512 != client.field_1724.field_7498) {
               this.reason = "Close the container before getting materials.";
            } else {
               MaterialTracker.Snapshot snapshot = MaterialTracker.snapshot(
                  client, this.schematic, this.transform
               );
               int added = 0;
               int omitted = 0;

               for (MaterialTracker.Row row : snapshot.rows()) {
                  if (row.item() != class_1802.field_8162) {
                     int need = Math.max(0, row.remaining() - row.hotbar() - row.inventory());

                     for (int slot = 0; slot < 36 && need > 0; slot++) {
                        class_1799 existing = client.field_1724.method_31548().method_5438(slot);
                        if (existing.method_7960() || existing.method_31574(row.item())) {
                           int room = row.item().method_7854().method_7914() - (existing.method_7960() ? 0 : existing.method_7947());
                           if (room > 0) {
                              int count = Math.min(room, need);
                              class_1799 stack = existing.method_7960()
                                 ? new class_1799(row.item(), count)
                                 : existing.method_46651(existing.method_7947() + count);
                              client.field_1724.method_31548().method_5447(slot, stack);
                              client.field_1761.method_2909(stack, slot < 9 ? slot + 36 : slot);
                              need -= count;
                              added += count;
                           }
                        }
                     }

                     omitted += need;
                  }
               }

               this.reason = "Added " + added + " items." + (omitted > 0 ? " Inventory full: " + omitted + " items still missing." : "");
            }
         } else {
            this.reason = "Get Materials requires Creative access.";
         }
      }
   }

   private static int findSlot(class_310 var0, class_1792 var1) {
      return CreativeBuildMode.ensureSlot(var0, var1, donut$findInventorySlot(var0, var1));
   }

   /** True while nothing is armed, in flight or cooling down, and the last step did not use up the tick. */
   private boolean idle() {
      return this.pending == null && this.prepared == null && this.cooldown == 0 && !this.yielded;
   }

   /** The client tick entry point: the retry check, then as many pipeline steps as the tempo allows. */
   public void tick(class_310 client) {
      BuildTempo.before(this);
      int steps = BuildTempo.steps();
      this.yielded = false;
      this.step(client, true);

      for (int done = 1; done < steps && this.isRunning() && this.idle(); done++) {
         this.step(client, false);
      }
   }

   /** Ticks since the placement in flight was sent, or zero when nothing is in flight. */
   private int waited() {
      return this.pending == null ? 0 : this.tick - this.pending.sentTick;
   }

   @Environment(EnvType.CLIENT)
   private static final class Pending {
      final Target target;
      final class_2680 before;
      final class_2680 predicted;
      final int sentTick;
      final int sequence;
      boolean acknowledged;
      boolean bucket;
      class_2680 observed;

      Pending(Target target, class_2680 before, class_2680 predicted, int sentTick, int sequence) {
         this.target = target;
         this.before = before;
         this.predicted = predicted;
         this.sentTick = sentTick;
         this.sequence = sequence;
      }
   }

   @Environment(EnvType.CLIENT)
   private record Prepared(Target target, PlacementPlanner.Plan plan, int readyTick) {
   }

   @Environment(EnvType.CLIENT)
   public static enum Status {
      IDLE,
      READY,
      RUNNING,
      PAUSED,
      COMPLETED;
   }

   @Environment(EnvType.CLIENT)
   private record Settle(Target target, int due, int since) {
   }

   @Environment(EnvType.CLIENT)
   private record Target(class_2338 pos, class_2680 state, int index, boolean support) {
   }
}
