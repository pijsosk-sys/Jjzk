package dev.donut.client.autobuild;

import dev.donut.client.freecam.FreecamController;
import dev.donut.client.mixin.BuilderClientLevelAccessor;
import dev.donut.client.mixin.BuilderLocalPlayerAccessor;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CandleBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FallingBlock;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import rex.UnifiedAutoBuild;

/**
 * Builds the loaded schematic.
 * <p>
 * Each tick the controller looks at every unfinished blueprint block within arm's reach and places as
 * many as the Build Tempo allows; only when nothing can be placed from where the player stands does it
 * walk. Work goes layer by layer from the bottom: first the blocks of a layer, then its water, then the
 * plants that grow in that water, so water is only poured once its container exists.
 * <p>
 * A block counts as finished when the server's copy of the world has shown it unchanged for four ticks
 * with no placement of ours still unanswered.
 */
@Environment(EnvType.CLIENT)
public final class AutoBuilderController {
   private static final AutoBuilderController INSTANCE = new AutoBuilderController();
   private static final List<Item> SCAFFOLD_ITEMS = List.of(Items.DIRT, Items.COBBLESTONE, Items.COBBLED_DEEPSLATE, Items.STONE, Items.NETHERRACK);
   private static final int STABLE_TICKS = 4;
   private static final boolean DEBUG = Boolean.getBoolean("rex.autobuild.debug");

   private void debug(String text) {
      if (DEBUG) {
         System.out.println("[autobuild t=" + this.tick + "] " + text);
      }
   }
   private final BuilderNavigator navigator = new BuilderNavigator();
   private Schematic schematic;
   private PlacementTransform transform = new PlacementTransform(BlockPos.ORIGIN, 0, false);
   private boolean previewVisible;
   private AutoBuilderController.Status status = AutoBuilderController.Status.IDLE;
   private String reason = "Choose a schematic.";
   private ClientWorld session;
   private AutoBuilderController.Target[] targets = new AutoBuilderController.Target[0];
   private final NavigableSet<AutoBuilderController.Target> work = new TreeSet<>(
      Comparator.<AutoBuilderController.Target>comparingInt(t -> t.order).thenComparingInt(t -> t.index)
   );
   private final Map<BlockPos, Integer> indices = new HashMap<>();
   private final Map<Long, int[]> grid = new HashMap<>();
   private final BitSet correct = new BitSet();
   private int[] matchSince = new int[0];
   private final Set<Integer> watch = new LinkedHashSet<>();
   private final Map<BlockPos, AutoBuilderController.Flight> flights = new HashMap<>();
   private final Map<BlockPos, Integer> failures = new HashMap<>();
   private final Set<Integer> deferred = new HashSet<>();
   private final Map<Integer, Integer> unreachableUntil = new HashMap<>();
   private final Set<BlockPos> supportBlocks = new HashSet<>();
   private final int[] hotbarUsed = new int[9];
   private AutoBuilderController.Target focus;
   private AutoBuilderController.Target goal;
   private BlockPos waterHole;
   private int tick;
   private int scanIndex;
   private int revalidateIndex;
   private int finalCheckIndex;
   private int originalSlot = -1;
   private int cooldown;
   private int budget;
   private int lastProgressTick;
   private int lastPlacementTick;
   private int arrivedTicks;
   private int widen;
   private int deferredRetries;
   private int waterCheckTick = -1000;
   private boolean waterPending;
   private boolean scanned;
   private boolean wantSneak;
   private String lastFailure = "";
   private int turnTick = -100;
   private float previousSentYaw = Float.NaN;
   private float previousSentPitch = Float.NaN;
   private int placedTotal;
   private int rateWindowStart;
   private int rateWindowCount;
   private double rate;

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

   public AutoBuilderController.Status status() {
      return this.status;
   }

   public String reason() {
      return this.reason;
   }

   public boolean isRunning() {
      return this.status == AutoBuilderController.Status.RUNNING;
   }

   public int confirmedBlocks() {
      return this.correct.cardinality();
   }

   public int totalBlocks() {
      return this.targets.length;
   }

   /** Placements sent since Start, including scaffolding and water. */
   public int placementsSent() {
      return this.placedTotal;
   }

   public double progress() {
      return this.targets.length == 0 ? 0.0 : (double)this.correct.cardinality() / this.targets.length;
   }

   public int supportsPlaced() {
      return this.supportBlocks.size();
   }

   public static BuildTempo tempo() {
      return CreativeBuildMode.tempo();
   }

   public void setSchematic(Schematic value) {
      this.cancel();
      this.schematic = value;
      this.rebuildTargets();
      this.previewVisible = value != null;
      this.status = value == null ? AutoBuilderController.Status.IDLE : AutoBuilderController.Status.READY;
      this.reason = value == null ? "Choose a schematic." : "Position the preview, then Start.";
   }

   public void setTransform(PlacementTransform value) {
      if (!this.transform.equals(value)) {
         this.cancel();
         this.transform = Objects.requireNonNull(value);
         this.rebuildTargets();
         this.status = this.schematic == null ? AutoBuilderController.Status.IDLE : AutoBuilderController.Status.READY;
         this.reason = "Position updated.";
      }
   }

   private void rebuildTargets() {
      this.indices.clear();
      this.grid.clear();
      this.work.clear();
      this.supportBlocks.clear();
      this.targets = new AutoBuilderController.Target[0];
      this.matchSince = new int[0];
      this.resetProgress();
      if (this.schematic == null) {
         return;
      }

      List<AutoBuilderController.Target> values = new ArrayList<>(this.schematic.blocks().size());
      for (Schematic.Block block : this.schematic.blocks()) {
         if (MaterialTracker.isDerived(block.state())) {
            continue;
         }

         BlockPos pos = this.transform.apply(block.pos());
         BlockState state = this.transform.apply(block.state());
         int phase = isWater(state) ? 1 : (isWaterPlant(state) ? 2 : 0);
         values.add(new AutoBuilderController.Target(pos, state, values.size(), false, pos.getY() * 4 + phase));
      }

      this.targets = values.toArray(new AutoBuilderController.Target[0]);
      this.matchSince = new int[this.targets.length];
      java.util.Arrays.fill(this.matchSince, -1);
      Map<Long, List<Integer>> cells = new HashMap<>();
      for (AutoBuilderController.Target target : this.targets) {
         this.indices.put(target.pos, target.index);
         cells.computeIfAbsent(cellKey(target.pos.getX() >> 3, target.pos.getY() >> 3, target.pos.getZ() >> 3), k -> new ArrayList<>()).add(target.index);
      }

      for (Map.Entry<Long, List<Integer>> entry : cells.entrySet()) {
         this.grid.put(entry.getKey(), entry.getValue().stream().mapToInt(Integer::intValue).toArray());
      }

      this.work.addAll(values);
   }

   private static long cellKey(int x, int y, int z) {
      return (long)(x & 2097151) << 42 | (long)(y & 2097151) << 21 | (long)(z & 2097151);
   }

   private void resetProgress() {
      this.correct.clear();
      this.watch.clear();
      this.flights.clear();
      this.failures.clear();
      this.deferred.clear();
      this.unreachableUntil.clear();
      java.util.Arrays.fill(this.matchSince, -1);
      this.scanIndex = 0;
      this.scanned = false;
      this.revalidateIndex = 0;
      this.finalCheckIndex = 0;
      this.focus = null;
      this.goal = null;
      this.waterHole = null;
      this.cooldown = 0;
      this.widen = 0;
      this.deferredRetries = 0;
      this.lastFailure = "";
      this.wantSneak = false;
   }

   public void start() {
      UnifiedAutoBuild.acquire();
      MinecraftClient client = MinecraftClient.getInstance();
      if (this.schematic == null || this.targets.length == 0) {
         this.reason = "Load a schematic containing blocks.";
      } else if (this.canRun(client)) {
         this.resetProgress();
         this.work.clear();
         this.work.addAll(List.of(this.targets));
         this.session = client.world;
         this.tick = 0;
         this.lastProgressTick = 0;
         this.lastPlacementTick = -100;
         this.placedTotal = 0;
         this.rateWindowStart = 0;
         this.rateWindowCount = 0;
         this.rate = 0.0;
         this.navigator.reset();
         this.originalSlot = client.player.getInventory().getSelectedSlot();
         this.status = AutoBuilderController.Status.RUNNING;
         this.previewVisible = true;
         this.reason = "Checking loaded blocks...";
         this.stopFlying(client);
      }
   }

   public void pause() {
      this.pauseWith("Paused by you.");
   }

   private void pauseWith(String message) {
      this.debug("PAUSE " + message);
      if (this.status != AutoBuilderController.Status.RUNNING && this.status != AutoBuilderController.Status.PAUSED) {
         this.reason = message;
      } else {
         this.status = AutoBuilderController.Status.PAUSED;
         this.reason = message;
         this.focus = null;
         this.goal = null;
         this.navigator.release();
         this.restoreSlot();
      }
   }

   public void resume() {
      UnifiedAutoBuild.acquire();
      if (this.status != AutoBuilderController.Status.PAUSED) {
         this.start();
      } else {
         MinecraftClient client = MinecraftClient.getInstance();
         if (this.canRun(client)) {
            if (this.session != client.world) {
               this.start();
            } else {
               this.failures.clear();
               this.deferred.clear();
               this.unreachableUntil.clear();
               this.flights.clear();
               this.lastFailure = "";
               this.focus = null;
               this.goal = null;
               this.widen = 0;
               this.deferredRetries = 0;
               this.lastProgressTick = this.tick;
               this.navigator.reset();
               this.work.clear();

               for (AutoBuilderController.Target target : this.targets) {
                  if (!this.correct.get(target.index)) {
                     this.work.add(target);
                  }
               }

               this.originalSlot = client.player.getInventory().getSelectedSlot();
               this.status = AutoBuilderController.Status.RUNNING;
               this.reason = "Resuming...";
               this.stopFlying(client);
            }
         }
      }
   }

   private void stopFlying(MinecraftClient client) {
      if (client.player.getAbilities().flying) {
         client.player.getAbilities().flying = false;
         client.player.sendAbilitiesUpdate();
      }
   }

   private boolean canRun(MinecraftClient client) {
      if (client.world == null || client.player == null || client.interactionManager == null) {
         this.reason = "Join a world before starting.";
         return false;
      } else if (FreecamController.enabled()) {
         this.reason = "Disable Freecam before resuming AutoBuilder.";
         return false;
      } else if (client.interactionManager.isFlyingLocked()) {
         this.reason = "Spectator mode cannot place blocks.";
         return false;
      } else if (client.player.hasVehicle()) {
         this.reason = "Leave the vehicle before starting AutoBuilder.";
         return false;
      } else if (client.currentScreen != null) {
         this.reason = "Close the open menu before resuming.";
         return false;
      } else {
         return true;
      }
   }

   public void cancel() {
      this.navigator.reset();
      this.restoreSlot();
      this.session = null;
      this.resetProgress();
      this.work.clear();
      this.work.addAll(List.of(this.targets));
      this.status = this.schematic == null ? AutoBuilderController.Status.IDLE : AutoBuilderController.Status.READY;
      this.reason = "Build cancelled.";
   }

   public void disconnect() {
      if (this.isRunning() || this.status == AutoBuilderController.Status.PAUSED) {
         this.pauseWith("Disconnected. Rejoin, check the position, then Resume.");
      }

      this.navigator.reset();
      this.restoreSlot();
      this.session = null;
      this.focus = null;
      this.goal = null;
      this.flights.clear();
      this.watch.clear();
      this.correct.clear();
      java.util.Arrays.fill(this.matchSince, -1);
      this.scanIndex = 0;
      this.scanned = false;
      this.previewVisible = false;
   }

   private void restoreSlot() {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null && this.originalSlot >= 0) {
         player.getInventory().setSelectedSlot(this.originalSlot);
      }

      this.originalSlot = -1;
   }

   // ---------------------------------------------------------------------------------------------
   // The tick
   // ---------------------------------------------------------------------------------------------

   public void tick(MinecraftClient client) {
      if (!this.isRunning()) {
         return;
      }

      this.tick++;

      for (KeyBinding key : List.of(client.options.attackKey, client.options.useKey, client.options.dropKey, client.options.swapHandsKey)) {
         key.setPressed(false);

         while (key.wasPressed()) {
         }
      }

      if (client.world == null || client.player == null || client.interactionManager == null || client.world != this.session) {
         this.disconnect();
         return;
      } else if (FreecamController.enabled()) {
         this.pauseWith("Freecam is active. Disable it, then Resume.");
         return;
      } else if (client.currentScreen != null || client.isPaused()) {
         this.pauseWith("An open menu paused construction. Close it, then Resume.");
         return;
      } else if (!client.player.isAlive() || client.player.isRemoved()) {
         this.pauseWith("Player unavailable. Resume after respawning.");
         return;
      } else if (client.player.hasVehicle()) {
         this.pauseWith("Leave the vehicle, then Resume.");
         return;
      } else if (client.player.isUsingItem()) {
         this.pauseWith("Finish using the held item, then Resume.");
         return;
      }

      // A rotation counts as known to the server once two movement updates in a row carried it.
      BuilderLocalPlayerAccessor sentRotation = (BuilderLocalPlayerAccessor)client.player;
      float sentYaw = sentRotation.donut$lastSentYaw();
      float sentPitch = sentRotation.donut$lastSentPitch();
      boolean steady = sentYaw == this.previousSentYaw && sentPitch == this.previousSentPitch;
      PlacementPlanner.settledRotation(steady ? sentYaw : null, steady ? sentPitch : null);
      this.previousSentYaw = sentYaw;
      this.previousSentPitch = sentPitch;

      if (!this.scanned) {
         this.navigator.hold(client, false);
         this.initialScan(client);
         return;
      }

      this.settleFlights(client);
      if (!this.isRunning()) {
         return;
      }

      this.revalidate(client, 384);
      this.settleWatched(client);
      if (this.tick - this.rateWindowStart >= 40) {
         this.rate = this.rateWindowCount * 20.0 / (this.tick - this.rateWindowStart);
         this.rateWindowStart = this.tick;
         this.rateWindowCount = 0;
      }

      if (this.correct.cardinality() == this.targets.length) {
         this.finalCheck(client);
         return;
      }

      this.finalCheckIndex = 0;
      if (this.cooldown > 0) {
         this.cooldown--;
         this.navigator.hold(client, this.wantSneak);
         return;
      }

      BuildTempo tempo = tempo();
      if (this.tick % tempo.period == 0) {
         this.budget = tempo.burst;
      }

      if (tempo.careful && !this.flights.isEmpty()) {
         this.navigator.hold(client, this.wantSneak);
         this.reason = "Waiting for the server...";
         return;
      }

      if (this.work.isEmpty()) {
         this.outOfWork(client);
         return;
      }

      AutoBuilderController.Outcome outcome = this.placeNearby(client);
      if (!this.isRunning()) {
         return;
      }

      if (outcome == AutoBuilderController.Outcome.PLACED || outcome == AutoBuilderController.Outcome.BUSY) {
         this.navigator.hold(client, this.wantSneak);
         this.arrivedTicks = 0;
      } else if (this.tick - this.lastPlacementTick <= 1 && this.budget <= 0) {
         // Out of budget for this tick: stay put, more may be placeable from here.
         this.navigator.hold(client, this.wantSneak);
      } else {
         this.wantSneak = false;
         this.travel(client);
      }

      if (this.isRunning() && this.tick - this.lastProgressTick > 600) {
         this.pauseWith("No progress for 30 seconds. Last step: " + this.reason + (this.lastFailure.isEmpty() ? "" : " | " + this.lastFailure));
      }
   }

   private void initialScan(MinecraftClient client) {
      for (int end = Math.min(this.targets.length, this.scanIndex + 4096); this.scanIndex < end; this.scanIndex++) {
         AutoBuilderController.Target target = this.targets[this.scanIndex];
         if (client.world.isOutOfHeightLimit(target.pos)) {
            this.pauseWith("Outside world build height at " + coordinates(target.pos) + ". Reposition the schematic.");
            return;
         }

         if (!client.world.getWorldBorder().contains(target.pos)) {
            this.pauseWith("Outside the world border at " + coordinates(target.pos) + ". Reposition the schematic.");
            return;
         }

         if (MaterialTracker.isLoaded(client.world, target.pos) && MaterialTracker.matches(target.state, client.world.getBlockState(target.pos))) {
            // Already there before we touched anything: no need to wait for it to settle.
            this.correct.set(target.index);
            this.work.remove(target);
         }
      }

      if (this.scanIndex >= this.targets.length) {
         this.scanned = true;
         this.lastProgressTick = this.tick;
      }
   }

   /** Rolling comparison of the blueprint with the world, so outside changes are always noticed. */
   private void revalidate(MinecraftClient client, int count) {
      for (int checked = 0; checked < Math.min(count, this.targets.length); checked++) {
         if (this.revalidateIndex >= this.targets.length) {
            this.revalidateIndex = 0;
         }

         AutoBuilderController.Target target = this.targets[this.revalidateIndex++];
         if (this.flights.containsKey(target.pos)) {
            continue;
         }

         boolean matches = MaterialTracker.isLoaded(client.world, target.pos) && MaterialTracker.matches(target.state, client.world.getBlockState(target.pos));
         if (matches) {
            this.observe(target);
         } else {
            this.unmatch(target);
         }
      }
   }

   private void observe(AutoBuilderController.Target target) {
      if (!target.support && !this.correct.get(target.index) && this.matchSince[target.index] < 0) {
         this.matchSince[target.index] = this.tick;
         this.watch.add(target.index);
      }
   }

   private void unmatch(AutoBuilderController.Target target) {
      if (target.support) {
         return;
      }

      this.matchSince[target.index] = -1;
      this.watch.remove(target.index);
      if (this.correct.get(target.index)) {
         this.correct.clear(target.index);
         this.finalCheckIndex = 0;
      }

      if (!this.deferred.contains(target.index)) {
         this.work.add(target);
      }
   }

   private void settleWatched(MinecraftClient client) {
      Iterator<Integer> iterator = this.watch.iterator();
      while (iterator.hasNext()) {
         AutoBuilderController.Target target = this.targets[iterator.next()];
         if (this.flights.containsKey(target.pos)) {
            continue;
         }

         boolean matches = MaterialTracker.isLoaded(client.world, target.pos) && MaterialTracker.matches(target.state, client.world.getBlockState(target.pos));
         if (!matches) {
            this.matchSince[target.index] = -1;
            iterator.remove();
            if (!this.deferred.contains(target.index)) {
               this.work.add(target);
            }
         } else if (this.tick - this.matchSince[target.index] >= STABLE_TICKS) {
            iterator.remove();
            this.correct.set(target.index);
            this.work.remove(target);
            this.deferred.remove(target.index);
            this.failures.remove(target.pos);
            this.lastProgressTick = this.tick;
         }
      }
   }

   private void settleFlights(MinecraftClient client) {
      Iterator<Map.Entry<BlockPos, AutoBuilderController.Flight>> iterator = this.flights.entrySet().iterator();
      while (iterator.hasNext()) {
         Map.Entry<BlockPos, AutoBuilderController.Flight> entry = iterator.next();
         AutoBuilderController.Flight flight = entry.getValue();
         if (!flight.acknowledged) {
            if (this.tick - flight.sentTick > 100) {
               iterator.remove();
               this.pauseWith("No server confirmation at " + coordinates(entry.getKey()) + ". Check the connection, then Resume.");
               return;
            }

            continue;
         }

         if (this.tick <= flight.ackTick) {
            continue;
         }

         iterator.remove();
         AutoBuilderController.Target target = flight.target;
         BlockState actual = client.world.getBlockState(entry.getKey());
         if (target == null) {
            continue;
         }

         if (target.support) {
            if (!actual.isReplaceable()) {
               this.supportBlocks.add(target.pos);
            }

            continue;
         }

         if (MaterialTracker.matches(target.state, actual)) {
            this.failures.remove(target.pos);
            this.matchSince[target.index] = -1;
            this.observe(target);
         } else if (DEBUG && !actual.equals(flight.before) && !isWaterPlant(target.state)) {
            this.debug("MISPLACED " + coordinates(target.pos) + " want " + target.state + " got " + actual);
         } else if (actual.equals(flight.before)) {
            int attempts = this.failures.merge(target.pos, 1, Integer::sum);
            this.navigator.replan();
            if (attempts >= 4) {
               this.failures.remove(target.pos);
               this.defer(target, "Server rejected the placement at " + coordinates(target.pos) + ". Check build permissions and the block's support.");
            }
         }
      }
   }

   private void finalCheck(MinecraftClient client) {
      this.navigator.hold(client, false);
      this.reason = "Verifying the completed structure...";
      if (!this.flights.isEmpty()) {
         return;
      }

      for (int end = Math.min(this.targets.length, this.finalCheckIndex + 4096); this.finalCheckIndex < end; this.finalCheckIndex++) {
         AutoBuilderController.Target target = this.targets[this.finalCheckIndex];
         if (!MaterialTracker.isLoaded(client.world, target.pos) || !MaterialTracker.matches(target.state, client.world.getBlockState(target.pos))) {
            this.unmatch(target);
            this.finalCheckIndex = 0;
            return;
         }
      }

      if (this.finalCheckIndex == this.targets.length) {
         this.complete();
      }
   }

   private void outOfWork(MinecraftClient client) {
      this.navigator.hold(client, false);
      if (!this.watch.isEmpty() || !this.flights.isEmpty()) {
         this.reason = "Waiting for the last blocks to settle...";
         return;
      }

      if (!this.deferred.isEmpty() && this.deferredRetries < 2) {
         this.deferredRetries++;
         for (int index : this.deferred) {
            this.work.add(this.targets[index]);
         }

         this.deferred.clear();
         return;
      }

      int left = this.targets.length - this.correct.cardinality();
      this.pauseWith(
         (this.lastFailure.isEmpty() ? "Some blocks could not be placed." : this.lastFailure) + " " + left + " block" + (left == 1 ? "" : "s") + " left; fix, then Resume."
      );
   }

   // ---------------------------------------------------------------------------------------------
   // Choosing what may be built now
   // ---------------------------------------------------------------------------------------------

   /**
    * What may be worked on. Layers are finished bottom-up; within a layer blocks come first (order 4y),
    * then water (4y+1), then water plants (4y+2). While a layer has only blocks left, the blocks of the
    * layer above may be started too, and more layers open up when nothing below can be reached.
    */
   private boolean eligible(AutoBuilderController.Target target) {
      if (this.correct.get(target.index) || this.deferred.contains(target.index) || this.flights.containsKey(target.pos)) {
         return false;
      }

      int lowest = this.work.first().order;
      if (target.order == lowest) {
         return true;
      }

      if ((lowest & 3) != 0 || (target.order & 3) != 0) {
         return false;
      }

      int ahead = (target.order >> 2) - (lowest >> 2);
      if (ahead < 1 || ahead > 1 + this.widen) {
         return false;
      }

      if (this.widen == 0) {
         // The layer above only once this layer has no water or plants still to come.
         AutoBuilderController.Target later = this.work.ceiling(new AutoBuilderController.Target(BlockPos.ORIGIN, null, -1, false, lowest + 1));
         return later == null || later.order > lowest + 2;
      }

      return true;
   }

   private int ceiling() {
      return this.work.first().order + 4 * (1 + this.widen);
   }

   private boolean inLowestLayer(AutoBuilderController.Target target) {
      return target.order >> 2 == this.work.first().order >> 2;
   }

   /** Building here would roof over a blueprint block that is not there yet. */
   private boolean coversUnfinished(MinecraftClient client, AutoBuilderController.Target target) {
      Integer below = this.indices.get(target.pos.down());
      if (below == null || this.correct.get(below) || this.deferred.contains(below)) {
         return false;
      }

      return !MaterialTracker.matches(this.targets[below].state, client.world.getBlockState(target.pos.down()));
   }

   /** Is any water still missing in the layer being worked on? Planting waits for that. */
   private boolean waterStillNeeded(MinecraftClient client) {
      if (this.tick - this.waterCheckTick < 10) {
         return this.waterPending;
      }

      this.waterCheckTick = this.tick;
      this.waterPending = false;
      int limit = this.work.first().order;
      for (AutoBuilderController.Target target : this.work) {
         if (target.order > limit) {
            break;
         }

         if (this.deferred.contains(target.index) || !MaterialTracker.isLoaded(client.world, target.pos)) {
            continue;
         }

         BlockState actual = client.world.getBlockState(target.pos);
         if (!MaterialTracker.matches(target.state, actual) && this.classify(client, target, actual) == AutoBuilderController.Kind.WATER) {
            this.waterPending = true;
            break;
         }
      }

      return this.waterPending;
   }

   private AutoBuilderController.Kind classify(MinecraftClient client, AutoBuilderController.Target target, BlockState actual) {
      BlockState desired = target.state;
      if (target.support) {
         return actual.isReplaceable() ? AutoBuilderController.Kind.BLOCK : AutoBuilderController.Kind.DONE;
      }

      if (actual.isOf(Blocks.MOVING_PISTON) || actual.isOf(Blocks.PISTON_HEAD)) {
         // A piston is passing through; look again when it has gone.
         return AutoBuilderController.Kind.LATER;
      }

      if (isWater(desired)) {
         if (desired.isOf(Blocks.LAVA)) {
            return AutoBuilderController.Kind.MANUAL;
         }

         return actual.isAir() || actual.isReplaceable() ? AutoBuilderController.Kind.WATER : AutoBuilderController.Kind.MANUAL;
      }

      if (isWaterPlant(desired)) {
         FluidState fluid = actual.getFluidState();
         if (actual.isOf(Blocks.WATER) && fluid.isIn(FluidTags.WATER) && (fluid.isStill() || fluid.contains(Properties.FALLING) && fluid.get(Properties.FALLING))) {
            return this.plantable(client, target) ? AutoBuilderController.Kind.BLOCK : AutoBuilderController.Kind.LATER;
         }

         if (actual.isAir() || actual.isReplaceable()) {
            return AutoBuilderController.Kind.WATER;
         }

         return AutoBuilderController.Kind.MANUAL;
      }

      if (desired.getBlock() == actual.getBlock() && desired.contains(Properties.WATERLOGGED) && actual.contains(Properties.WATERLOGGED)) {
         boolean want = desired.get(Properties.WATERLOGGED);
         boolean have = actual.get(Properties.WATERLOGGED);
         if (want != have && MaterialTracker.matches(desired, actual.with(Properties.WATERLOGGED, want))) {
            return want ? AutoBuilderController.Kind.WATER : AutoBuilderController.Kind.MANUAL;
         }
      }

      if (MaterialTracker.isSecondary(desired)) {
         return AutoBuilderController.Kind.LATER;
      }

      if (manualStateReason(desired) != null) {
         return AutoBuilderController.Kind.MANUAL;
      }

      boolean configuring = PlacementPlanner.needsConfiguration(desired, actual);
      if (!PlacementPlanner.canAdd(desired, actual) && !configuring) {
         return AutoBuilderController.Kind.MANUAL;
      }

      Item item = MaterialTracker.requirement(desired).item();
      if (item == Items.AIR || !(item instanceof BlockItem)) {
         return AutoBuilderController.Kind.MANUAL;
      }

      if (configuring) {
         return AutoBuilderController.Kind.BLOCK;
      }

      if (!desired.canPlaceAt(client.world, target.pos) || !hasNeighborSupport(client, target.pos)) {
         return AutoBuilderController.Kind.LATER;
      }

      if (desired.getBlock() instanceof FallingBlock && client.world.getBlockState(target.pos.down()).isReplaceable()) {
         return AutoBuilderController.Kind.LATER;
      }

      return AutoBuilderController.Kind.BLOCK;
   }

   private boolean plantable(MinecraftClient client, AutoBuilderController.Target target) {
      Block block = target.state.getBlock();
      BlockState below = client.world.getBlockState(target.pos.down());
      if (block == Blocks.KELP || block == Blocks.KELP_PLANT) {
         return below.isOf(Blocks.KELP) || below.isOf(Blocks.KELP_PLANT) || below.isSideSolidFullSquare(client.world, target.pos.down(), Direction.UP) && !below.isOf(Blocks.MAGMA_BLOCK);
      }

      return target.state.canPlaceAt(client.world, target.pos);
   }

   private String manualMessage(MinecraftClient client, AutoBuilderController.Target target, BlockState actual) {
      BlockState desired = target.state;
      String where = " at " + coordinates(target.pos) + ".";
      if (desired.isOf(Blocks.LAVA)) {
         return "Lava has to be placed by hand" + where;
      }

      String manual = manualStateReason(desired);
      if (manual != null && actual.isReplaceable()) {
         return manual + where;
      }

      if (desired.getBlock() == actual.getBlock() && desired.contains(Properties.WATERLOGGED) && !desired.get(Properties.WATERLOGGED) && actual.get(Properties.WATERLOGGED)) {
         return "Remove the water from the block" + where;
      }

      Item item = MaterialTracker.requirement(desired).item();
      if (actual.isReplaceable() && (item == Items.AIR || !(item instanceof BlockItem))) {
         return desired.getBlock().getName().getString() + " has to be placed by hand" + where;
      }

      return "Wrong block in the way (" + actual.getBlock().getName().getString() + ")" + where + " Remove it.";
   }

   private static String manualStateReason(BlockState desired) {
      return desired.getBlock() instanceof CandleBlock && desired.get(Properties.LIT) ? "Lit candles need lighting by hand" : null;
   }

   static boolean isWater(BlockState state) {
      return state.isOf(Blocks.WATER) || state.isOf(Blocks.BUBBLE_COLUMN) || state.isOf(Blocks.LAVA);
   }

   static boolean isWaterPlant(BlockState state) {
      return state.isOf(Blocks.KELP) || state.isOf(Blocks.KELP_PLANT) || state.isOf(Blocks.SEAGRASS) || state.isOf(Blocks.TALL_SEAGRASS);
   }

   // ---------------------------------------------------------------------------------------------
   // Placing
   // ---------------------------------------------------------------------------------------------

   private AutoBuilderController.Outcome placeNearby(MinecraftClient client) {
      ClientPlayerEntity player = client.player;
      Vec3d eye = player.getEyePos();
      double range = player.getBlockInteractionRange() + 1.0;
      List<AutoBuilderController.Target> near = new ArrayList<>();
      int minX = MathHelper.floor(eye.x - range) >> 3;
      int maxX = MathHelper.floor(eye.x + range) >> 3;
      int minY = MathHelper.floor(eye.y - range) >> 3;
      int maxY = MathHelper.floor(eye.y + range) >> 3;
      int minZ = MathHelper.floor(eye.z - range) >> 3;
      int maxZ = MathHelper.floor(eye.z + range) >> 3;

      for (int x = minX; x <= maxX; x++) {
         for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
               int[] cell = this.grid.get(cellKey(x, y, z));
               if (cell == null) {
                  continue;
               }

               for (int index : cell) {
                  AutoBuilderController.Target target = this.targets[index];
                  if (this.eligible(target) && eye.squaredDistanceTo(Vec3d.ofCenter(target.pos)) <= range * range) {
                     near.add(target);
                  }
               }
            }
         }
      }

      if (near.isEmpty()) {
         return AutoBuilderController.Outcome.NONE;
      }

      near.sort(
         Comparator.<AutoBuilderController.Target>comparingInt(t -> t == this.focus ? 0 : 1)
            .thenComparingInt(t -> t.order)
            .thenComparingDouble(t -> eye.squaredDistanceTo(Vec3d.ofCenter(t.pos)))
      );
      // Finish what is within reach of the current layer before starting on the one above.
      boolean lowestWaiting = false;
      for (AutoBuilderController.Target target : near) {
         if (!this.inLowestLayer(target)) {
            break;
         }

         if (!MaterialTracker.matches(target.state, client.world.getBlockState(target.pos))) {
            lowestWaiting = true;
            break;
         }
      }

      boolean placed = false;
      int planned = 0;
      for (AutoBuilderController.Target target : near) {
         if (planned >= 48) {
            break;
         }

         BlockState actual = client.world.getBlockState(target.pos);
         if (MaterialTracker.matches(target.state, actual)) {
            this.observe(target);
            continue;
         }

         if (!this.inLowestLayer(target) && (lowestWaiting || this.coversUnfinished(client, target))) {
            continue;
         }

         AutoBuilderController.Kind kind = this.classify(client, target, actual);
         if (kind == AutoBuilderController.Kind.MANUAL) {
            this.defer(target, this.manualMessage(client, target, actual));
            continue;
         }

         if (kind == AutoBuilderController.Kind.LATER || kind == AutoBuilderController.Kind.DONE) {
            continue;
         }

         if (kind == AutoBuilderController.Kind.BLOCK && isWaterPlant(target.state) && this.waterStillNeeded(client)) {
            continue;
         }

         planned++;
         AutoBuilderController.Outcome outcome = this.attempt(client, target, kind, actual);
         if (!this.isRunning()) {
            return outcome;
         }

         if (outcome == AutoBuilderController.Outcome.BUSY) {
            this.focus = target;
            return placed ? AutoBuilderController.Outcome.PLACED : AutoBuilderController.Outcome.BUSY;
         }

         if (outcome == AutoBuilderController.Outcome.PLACED) {
            placed = true;
            this.focus = null;
            if (tempo().careful) {
               break;
            }
         }
      }

      if (!placed) {
         this.focus = null;
      }

      return placed ? AutoBuilderController.Outcome.PLACED : AutoBuilderController.Outcome.NONE;
   }

   /** Try to do one thing for this target from where the player stands. */
   private AutoBuilderController.Outcome attempt(MinecraftClient client, AutoBuilderController.Target target, AutoBuilderController.Kind kind, BlockState actual) {
      ClientPlayerEntity player = client.player;
      if (kind == AutoBuilderController.Kind.WATER) {
         return this.pour(client, target);
      }

      boolean configuring = !target.support && PlacementPlanner.needsConfiguration(target.state, actual);
      Item item = target.support ? target.state.getBlock().asItem() : MaterialTracker.requirement(target.state).item();
      if (!configuring) {
         int slot = this.hotbarSlot(client, item);
         if (slot == -1) {
            this.pauseWith("Missing " + item.getName().getString() + ". Add materials, then Resume.");
            return AutoBuilderController.Outcome.BUSY;
         } else if (slot == -2) {
            return AutoBuilderController.Outcome.BUSY;
         }

         player.getInventory().setSelectedSlot(slot);
      }

      PlacementPlanner.Plan plan = PlacementPlanner.plan(client, target.pos, target.state, player.getMainHandStack());
      if (plan == null) {
         return AutoBuilderController.Outcome.NONE;
      }

      if (this.budget <= 0) {
         // Placeable from here, but this tick's allowance is used up: stay and wait.
         return AutoBuilderController.Outcome.BUSY;
      }

      if (!this.ready(client, plan)) {
         return AutoBuilderController.Outcome.BUSY;
      }

      if (DEBUG && target.state.contains(Properties.FACING)) {
         this.debug("place " + coordinates(target.pos) + " " + target.state + " predicted " + plan.prediction() + " yaw=" + plan.yaw() + " pitch=" + plan.pitch()
            + " hit=" + plan.hit().getBlockPos().toShortString() + "/" + plan.hit().getSide() + " eye=" + player.getEyePos() + " sneak=" + plan.sneak());
      }

      BlockState before = client.world.getBlockState(target.pos);
      this.look(client, plan);
      ActionResult result = client.interactionManager.interactBlock(player, Hand.MAIN_HAND, plan.hit());
      if (result.isAccepted()) {
         player.swingHand(Hand.MAIN_HAND);
      }

      this.sent(client, target, before);
      BlockPos secondary = secondaryPosition(target);
      if (secondary != null && this.indices.containsKey(secondary)) {
         AutoBuilderController.Flight flight = this.flights.get(target.pos);
         this.flights.put(secondary, new AutoBuilderController.Flight(null, client.world.getBlockState(secondary), this.tick, flight.sequence));
      }

      this.reason = (target.support ? "Placing scaffolding" : (plan.configure() ? "Setting block state" : "Placing blocks")) + " at " + coordinates(target.pos)
         + this.rateSuffix();
      return AutoBuilderController.Outcome.PLACED;
   }

   private String rateSuffix() {
      return this.rate >= 0.5 ? String.format(java.util.Locale.ROOT, "  (%.1f/s)", this.rate) : "";
   }

   /** Sneak and camera are where the plan needs them, as far as the server knows. */
   private boolean ready(MinecraftClient client, PlacementPlanner.Plan plan) {
      BuilderLocalPlayerAccessor sent = (BuilderLocalPlayerAccessor)client.player;
      if (plan.sneak()) {
         this.wantSneak = true;
         if (sent.donut$lastSentInput() == null || !sent.donut$lastSentInput().sneak() || !client.player.isSneaking()) {
            this.navigator.hold(client, true);
            this.reason = "Sneaking to place against a container...";
            return false;
         }
      }

      if (tempo().careful) {
         client.player.setYaw(plan.yaw());
         client.player.setPitch(plan.pitch());
         if (!PlacementPlanner.isSettled(plan.yaw(), plan.pitch())) {
            this.navigator.hold(client, this.wantSneak);
            this.reason = "Aligning for placement...";
            return false;
         }
      } else if (plan.aim()) {
         // Turn once and wait for the server to have it. The plan is re-made every tick and stops asking
         // for a turn as soon as the rotation the server holds gives the right block.
         boolean waiting = this.tick - this.turnTick <= 3 && !PlacementPlanner.isSettled(client.player.getYaw(), client.player.getPitch());
         if (!waiting) {
            client.player.setYaw(plan.yaw());
            client.player.setPitch(plan.pitch());
            this.turnTick = this.tick;
         }

         this.navigator.hold(client, this.wantSneak);
         this.reason = "Turning to place a facing block...";
         return false;
      }

      return true;
   }

   /**
    * Point the camera for a placement. Blocks that ignore the view direction are placed with the
    * rotation the server already has; buckets carry their own rotation in the use packet.
    */
   private void look(MinecraftClient client, PlacementPlanner.Plan plan) {
      if (tempo().careful || plan.aim() || plan.useItem()) {
         client.player.setYaw(plan.yaw());
         client.player.setPitch(plan.pitch());
      }
   }

   private void sent(MinecraftClient client, AutoBuilderController.Target target, BlockState before) {
      int sequence = ((BuilderClientLevelAccessor)client.world).donut$predictionHandler().getSequence();
      this.flights.put(target.pos, new AutoBuilderController.Flight(target, before, this.tick, sequence));
      if (!target.support) {
         this.matchSince[target.index] = -1;
         this.watch.remove(target.index);
      }

      this.budget--;
      this.placedTotal++;
      this.rateWindowCount++;
      this.lastPlacementTick = this.tick;
      this.lastProgressTick = this.tick;
      this.widen = 0;
      if (tempo().careful) {
         this.cooldown = 1;
      }
   }

   /**
    * Hotbar slot holding the item, moving it there if needed.
    * @return the slot, -1 when the player has none, -2 when it was just moved and needs a tick
    */
   private int hotbarSlot(MinecraftClient client, Item item) {
      PlayerInventory inventory = client.player.getInventory();
      int found = -1;
      for (int i = 0; i < 36; i++) {
         if (inventory.getStack(i).isOf(item)) {
            found = i;
            break;
         }
      }

      found = CreativeBuildMode.ensureSlot(client, item, found);
      if (found < 0) {
         return -1;
      }

      if (found < 9) {
         this.hotbarUsed[found] = this.tick;
         return found;
      }

      if (client.player.currentScreenHandler != client.player.playerScreenHandler) {
         return -1;
      }

      int into = 0;
      for (int i = 0; i < 9; i++) {
         if (inventory.getStack(i).isEmpty()) {
            into = i;
            break;
         }

         if (this.hotbarUsed[i] < this.hotbarUsed[into]) {
            into = i;
         }
      }

      client.interactionManager.clickSlot(client.player.playerScreenHandler.syncId, found, into, SlotActionType.SWAP, client.player);
      this.hotbarUsed[into] = this.tick;
      this.reason = "Moving " + item.getName().getString() + " into the hotbar...";
      return -2;
   }

   // ---------------------------------------------------------------------------------------------
   // Water
   // ---------------------------------------------------------------------------------------------

   private AutoBuilderController.Outcome pour(MinecraftClient client, AutoBuilderController.Target target) {
      ClientPlayerEntity player = client.player;
      PlacementPlanner.Plan plan = PlacementPlanner.planWater(client, target.pos, player.getEyePos());
      if (plan == null) {
         return AutoBuilderController.Outcome.NONE;
      }

      int slot = this.hotbarSlot(client, Items.WATER_BUCKET);
      if (slot == -2) {
         return AutoBuilderController.Outcome.BUSY;
      }

      if (slot == -1) {
         return this.refill(client);
      }

      player.getInventory().setSelectedSlot(slot);
      if (this.budget <= 0) {
         // Placeable from here, but this tick's allowance is used up: stay and wait.
         return AutoBuilderController.Outcome.BUSY;
      }

      if (!this.ready(client, plan)) {
         return AutoBuilderController.Outcome.BUSY;
      }

      BlockState before = client.world.getBlockState(target.pos);
      this.look(client, plan);
      client.interactionManager.interactItem(player, Hand.MAIN_HAND);
      player.swingHand(Hand.MAIN_HAND);
      this.sent(client, target, before);
      this.reason = "Pouring water at " + coordinates(target.pos) + this.rateSuffix();
      return AutoBuilderController.Outcome.PLACED;
   }

   /** No full bucket: scoop from a water source that refills itself, or go and find one. */
   private AutoBuilderController.Outcome refill(MinecraftClient client) {
      ClientPlayerEntity player = client.player;
      boolean hasEmpty = false;
      for (int i = 0; i < 36; i++) {
         hasEmpty |= player.getInventory().getStack(i).isOf(Items.BUCKET);
      }

      if (!hasEmpty) {
         this.pauseWith("Missing Water Bucket. Bring three or more water buckets, then Resume.");
         return AutoBuilderController.Outcome.BUSY;
      }

      Vec3d eye = player.getEyePos();
      BlockPos centre = player.getBlockPos();
      BlockPos best = null;
      PlacementPlanner.Plan bestPlan = null;
      double bestDistance = Double.POSITIVE_INFINITY;
      for (BlockPos pos : BlockPos.iterateOutwards(centre, 5, 5, 5)) {
         if (this.flights.containsKey(pos) || !this.scoopable(client, pos)) {
            continue;
         }

         double distance = eye.squaredDistanceTo(Vec3d.ofCenter(pos));
         if (distance < bestDistance) {
            PlacementPlanner.Plan plan = PlacementPlanner.planScoop(client, pos, eye);
            if (plan != null) {
               best = pos.toImmutable();
               bestPlan = plan;
               bestDistance = distance;
            }
         }
      }

      if (best == null) {
         // Nothing to scoop from here: travel() will walk to water.
         this.waterHole = this.findWater(client);
         if (this.waterHole == null) {
            this.pauseWith("Out of water. Make a 2x2 water pool beside the build or bring more water buckets, then Resume.");
            return AutoBuilderController.Outcome.BUSY;
         }

         return AutoBuilderController.Outcome.NONE;
      }

      int slot = this.hotbarSlot(client, Items.BUCKET);
      if (slot < 0) {
         return AutoBuilderController.Outcome.BUSY;
      }

      player.getInventory().setSelectedSlot(slot);
      if (this.budget <= 0) {
         // Placeable from here, but this tick's allowance is used up: stay and wait.
         return AutoBuilderController.Outcome.BUSY;
      }

      if (!this.ready(client, bestPlan)) {
         return AutoBuilderController.Outcome.BUSY;
      }

      BlockState before = client.world.getBlockState(best);
      this.look(client, bestPlan);
      client.interactionManager.interactItem(player, Hand.MAIN_HAND);
      player.swingHand(Hand.MAIN_HAND);
      Integer index = this.indices.get(best);
      int sequence = ((BuilderClientLevelAccessor)client.world).donut$predictionHandler().getSequence();
      this.flights.put(best, new AutoBuilderController.Flight(null, before, this.tick, sequence));
      if (index != null) {
         this.matchSince[index] = -1;
      }

      this.budget--;
      this.lastPlacementTick = this.tick;
      this.lastProgressTick = this.tick;
      this.waterHole = best;
      this.reason = "Refilling the bucket at " + coordinates(best);
      return AutoBuilderController.Outcome.PLACED;
   }

   /** A source that comes back after scooping, or one that is not part of the blueprint. */
   private boolean scoopable(MinecraftClient client, BlockPos pos) {
      BlockState state = client.world.getBlockState(pos);
      if (!state.isOf(Blocks.WATER) || !state.getFluidState().isStill()) {
         return false;
      }

      int sources = 0;
      for (Direction direction : Direction.Type.HORIZONTAL) {
         FluidState side = client.world.getFluidState(pos.offset(direction));
         if (side.isStill() && side.isOf(Fluids.WATER)) {
            sources++;
         }
      }

      BlockPos below = pos.down();
      BlockState floor = client.world.getBlockState(below);
      boolean renews = sources >= 2 && (floor.isSolidBlock(client.world, below) || floor.getFluidState().isStill() && floor.getFluidState().isOf(Fluids.WATER));
      return renews || !this.indices.containsKey(pos) && sources >= 1;
   }

   private BlockPos findWater(MinecraftClient client) {
      if (this.waterHole != null && this.scoopable(client, this.waterHole)) {
         return this.waterHole;
      }

      BlockPos centre = client.player.getBlockPos();
      for (BlockPos pos : BlockPos.iterateOutwards(centre, 24, 8, 24)) {
         if (MaterialTracker.isLoaded(client.world, pos) && this.scoopable(client, pos)) {
            return pos.toImmutable();
         }
      }

      return null;
   }

   // ---------------------------------------------------------------------------------------------
   // Walking
   // ---------------------------------------------------------------------------------------------

   private void travel(MinecraftClient client) {
      ClientPlayerEntity player = client.player;
      if (this.goal != null && !this.stillWanted(client, this.goal)) {
         this.goal = null;
      }

      if (this.goal == null) {
         this.goal = this.chooseGoal(client);
         this.arrivedTicks = 0;
         if (this.goal == null) {
            this.navigator.hold(client, false);
            if (!this.flights.isEmpty() || !this.watch.isEmpty()) {
               this.reason = "Waiting for the server...";
            } else if (this.widen < 6) {
               this.debug("no goal; widen " + this.widen + " lowest=" + this.work.first().order + " " + coordinates(this.work.first().pos) + " unreachable=" + this.unreachableUntil.size());
               this.widen++;
            } else {
               this.giveUpOnLayer(client);
            }

            return;
         }
      }

      AutoBuilderController.Target target = this.goal;
      BlockState actual = client.world.getBlockState(target.pos);
      AutoBuilderController.Kind kind = this.classify(client, target, actual);
      boolean needWater = kind == AutoBuilderController.Kind.WATER && !this.hasItem(client, Items.WATER_BUCKET) && !CreativeBuildMode.canFetch(client);
      BlockPos near;
      java.util.function.Predicate<BuilderPathfinder.Cell> test;
      double reach = player.getBlockInteractionRange();
      if (needWater) {
         AutoBuilderController.Outcome scooped = this.refill(client);
         if (scooped != AutoBuilderController.Outcome.NONE || !this.isRunning()) {
            this.navigator.hold(client, this.wantSneak);
            return;
         }

         BlockPos water = this.findWater(client);
         if (water == null) {
            this.pauseWith("Out of water. Make a 2x2 water pool beside the build or bring more water buckets, then Resume.");
            return;
         }

         near = water;
         test = cell -> {
            Vec3d eye = new Vec3d(cell.x() + 0.5, cell.y() + 1.62, cell.z() + 0.5);
            return eye.squaredDistanceTo(Vec3d.ofCenter(water)) <= reach * reach && PlacementPlanner.planScoop(client, water, eye) != null;
         };
      } else {
         near = target.pos;
         Item item = target.support ? target.state.getBlock().asItem() : MaterialTracker.requirement(target.state).item();
         ItemStack stack = new ItemStack(item);
         test = cell -> {
            if (cell.x() == target.pos.getX() && cell.z() == target.pos.getZ() && cell.y() <= target.pos.getY() && cell.y() + 2 > target.pos.getY()) {
               return false;
            }

            Vec3d eye = new Vec3d(cell.x() + 0.5, cell.y() + 1.62, cell.z() + 0.5);
            if (eye.squaredDistanceTo(Vec3d.ofCenter(target.pos)) > (reach + 1.0) * (reach + 1.0)) {
               return false;
            }

            return kind == AutoBuilderController.Kind.WATER
               ? PlacementPlanner.planWater(client, target.pos, eye) != null
               : PlacementPlanner.plan(client, target.pos, target.state, stack, eye, true) != null;
         };
      }

      Item scaffold = this.scaffoldItem(client);
      BuilderNavigator.Result result = this.navigator.moveTo(
         client, needWater ? "water" : target, near, test, (floor, from) -> scaffold != null && this.canScaffold(client, floor, from)
      );
      switch (result) {
         case ARRIVED -> {
            // The plan worked for a player standing mid-block; shuffle to the middle, then try elsewhere.
            this.navigator.centre(client);
            if (++this.arrivedTicks > 6) {
               this.arrivedTicks = 0;
               this.debug("ARRIVED but cannot place " + coordinates(target.pos) + " " + target.state + " from " + player.getEntityPos());
               this.navigator.rejectCurrent(client);
            }

            this.reason = "Lining up at " + coordinates(target.pos);
         }
         case NO_PATH -> {
            this.debug("NO_PATH to " + coordinates(target.pos) + " " + target.state + " kind=" + kind + " from " + player.getBlockPos().toShortString() + " scaffold=" + scaffold);
            this.unreachableUntil.put(target.index, this.tick + 100);
            this.lastFailure = "No walking route to " + coordinates(target.pos) + ". Add stairs or carry dirt/cobblestone for scaffolding.";
            this.goal = null;
         }
         case NEED_SCAFFOLD -> {
            BlockPos floor = this.navigator.scaffold();
            AutoBuilderController.Target support = new AutoBuilderController.Target(
               floor, ((BlockItem)scaffold).getBlock().getDefaultState(), -1, true, 0
            );
            if (this.flights.containsKey(floor)) {
               this.reason = "Waiting for scaffolding...";
            } else {
               AutoBuilderController.Outcome outcome = this.attempt(client, support, AutoBuilderController.Kind.BLOCK, client.world.getBlockState(floor));
               if (outcome == AutoBuilderController.Outcome.NONE) {
                  if (++this.arrivedTicks > 10) {
                     this.arrivedTicks = 0;
                     this.navigator.rejectCurrent(client);
                  }
               } else if (outcome == AutoBuilderController.Outcome.PLACED) {
                  this.supportBlocks.add(floor);
               }
            }
         }
         case STUCK -> this.reason = "Finding another way to " + coordinates(near);
         default -> {
            this.arrivedTicks = 0;
            this.reason = (needWater ? "Fetching water at " : "Walking to ") + coordinates(near) + " / " + this.navigator.routeLength() + " steps";
         }
      }
   }

   private boolean stillWanted(MinecraftClient client, AutoBuilderController.Target target) {
      if (this.correct.get(target.index) || this.deferred.contains(target.index) || this.flights.containsKey(target.pos)) {
         return false;
      }

      if (this.unreachableUntil.getOrDefault(target.index, 0) > this.tick || !this.eligible(target)) {
         return false;
      }

      BlockState actual = client.world.getBlockState(target.pos);
      if (MaterialTracker.matches(target.state, actual)) {
         return false;
      }

      AutoBuilderController.Kind kind = this.classify(client, target, actual);
      return kind == AutoBuilderController.Kind.WATER || kind == AutoBuilderController.Kind.BLOCK && !(isWaterPlant(target.state) && this.waterStillNeeded(client));
   }

   /** Nearest block that could be placed right now if the player stood in the right spot. */
   private AutoBuilderController.Target chooseGoal(MinecraftClient client) {
      int ceiling = this.ceiling();
      Vec3d at = client.player.getEntityPos();
      List<AutoBuilderController.Target> candidates = new ArrayList<>();
      for (AutoBuilderController.Target target : this.work) {
         if (target.order > ceiling) {
            break;
         }

         if (this.eligible(target) && this.unreachableUntil.getOrDefault(target.index, 0) <= this.tick && MaterialTracker.isLoaded(client.world, target.pos)) {
            candidates.add(target);
         }
      }

      // The current layer always wins over the one above, however far away it is.
      candidates.sort(
         Comparator.<AutoBuilderController.Target>comparingInt(t -> this.inLowestLayer(t) ? 0 : 1)
            .thenComparingDouble(t -> at.squaredDistanceTo(Vec3d.ofCenter(t.pos)))
      );
      int examined = 0;
      for (AutoBuilderController.Target target : candidates) {
         BlockState actual = client.world.getBlockState(target.pos);
         if (MaterialTracker.matches(target.state, actual)) {
            this.observe(target);
            continue;
         }

         if (!this.inLowestLayer(target) && this.coversUnfinished(client, target)) {
            continue;
         }

         AutoBuilderController.Kind kind = this.classify(client, target, actual);
         if (kind == AutoBuilderController.Kind.MANUAL) {
            this.defer(target, this.manualMessage(client, target, actual));
            continue;
         }

         if (kind == AutoBuilderController.Kind.WATER || kind == AutoBuilderController.Kind.BLOCK && !(isWaterPlant(target.state) && this.waterStillNeeded(client))) {
            return target;
         }

         if (++examined > 4000) {
            break;
         }
      }

      return null;
   }

   /** Nothing in the open layers can be placed and nothing is pending: these blocks need the player. */
   private void giveUpOnLayer(MinecraftClient client) {
      int lowest = this.work.first().order;
      List<AutoBuilderController.Target> stuck = new ArrayList<>();
      for (AutoBuilderController.Target target : this.work) {
         if (target.order > lowest) {
            break;
         }

         stuck.add(target);
      }

      if (stuck.isEmpty()) {
         this.widen = 0;
         return;
      }

      AutoBuilderController.Target first = stuck.get(0);
      boolean unreachable = this.unreachableUntil.getOrDefault(first.index, 0) > this.tick;
      String message = unreachable && !this.lastFailure.isEmpty()
         ? this.lastFailure
         : "Nothing to attach " + first.state.getBlock().getName().getString() + " to at " + coordinates(first.pos) + ". Add a support block.";
      for (AutoBuilderController.Target target : stuck) {
         this.deferred.add(target.index);
         this.work.remove(target);
      }

      this.lastFailure = message;
      this.widen = 0;
      this.unreachableUntil.clear();
   }

   private boolean hasItem(MinecraftClient client, Item item) {
      for (int i = 0; i < 36; i++) {
         if (client.player.getInventory().getStack(i).isOf(item)) {
            return true;
         }
      }

      return false;
   }

   private Item scaffoldItem(MinecraftClient client) {
      for (Item candidate : SCAFFOLD_ITEMS) {
         if (this.hasItem(client, candidate)) {
            return candidate;
         }
      }

      return CreativeBuildMode.canFetch(client) ? Items.COBBLESTONE : null;
   }

   /**
    * A scaffold block may go at {@code floor} when the spot is free, is not part of the blueprint, and
    * has something visible to click on: the block underneath, or a wall beside it.
    */
   private boolean canScaffold(MinecraftClient client, BlockPos floor, BuilderPathfinder.Cell from) {
      if (this.indices.containsKey(floor) || !MaterialTracker.isLoaded(client.world, floor)) {
         return false;
      }

      BlockState state = client.world.getBlockState(floor);
      if (!state.isReplaceable() || !state.getFluidState().isEmpty()) {
         return false;
      }

      BlockPos below = floor.down();
      if (client.world.getBlockState(below).isSideSolidFullSquare(client.world, below, Direction.UP)) {
         return true;
      }

      if (floor.getY() != from.y()) {
         return false;
      }

      for (Direction direction : Direction.Type.HORIZONTAL) {
         BlockPos side = floor.offset(direction);
         if (side.getX() == from.x() && side.getZ() == from.z()) {
            continue;
         }

         if (client.world.getBlockState(side).isSideSolidFullSquare(client.world, side, direction.getOpposite())) {
            return true;
         }
      }

      return false;
   }

   private void defer(AutoBuilderController.Target target, String message) {
      this.lastFailure = message;
      if (!target.support) {
         this.deferred.add(target.index);
         this.work.remove(target);
      }

      if (target == this.goal) {
         this.goal = null;
      }

      if (target == this.focus) {
         this.focus = null;
      }
   }

   private void complete() {
      this.status = AutoBuilderController.Status.COMPLETED;
      this.navigator.reset();
      this.restoreSlot();
      this.goal = null;
      this.focus = null;
      this.reason = "Complete: "
         + this.correct.cardinality()
         + " blocks confirmed."
         + (this.supportBlocks.isEmpty() ? "" : " " + this.supportBlocks.size() + " scaffolding blocks remain.");
      if (this.schematic.blocks().stream().anyMatch(block -> block.blockEntity() != null)) {
         this.reason = this.reason + " Container contents are not copied.";
      }
   }

   // ---------------------------------------------------------------------------------------------
   // Server feedback (called from the packet mixin)
   // ---------------------------------------------------------------------------------------------

   public void serverBlockUpdate(BlockPos pos, BlockState state) {
      if (this.session == null) {
         return;
      }

      Integer index = this.indices.get(pos);
      if (index != null && index < this.targets.length) {
         AutoBuilderController.Target target = this.targets[index];
         if (MaterialTracker.matches(target.state, state)) {
            this.observe(target);
         } else if (!this.flights.containsKey(pos)) {
            this.unmatch(target);
         } else {
            this.matchSince[index] = -1;
         }
      }
   }

   public void serverAcknowledged(int sequence) {
      for (AutoBuilderController.Flight flight : this.flights.values()) {
         if (!flight.acknowledged && sequence >= flight.sequence) {
            flight.acknowledged = true;
            flight.ackTick = this.tick;
         }
      }
   }

   public void positionCorrected() {
      if (this.isRunning()) {
         this.navigator.replan();
         this.focus = null;
      }
   }

   // ---------------------------------------------------------------------------------------------
   // Helpers
   // ---------------------------------------------------------------------------------------------

   private static String coordinates(BlockPos pos) {
      return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
   }

   private static BlockPos secondaryPosition(AutoBuilderController.Target target) {
      if (target.support) {
         return null;
      } else if ((target.state.getBlock() instanceof DoorBlock || target.state.getBlock() instanceof TallPlantBlock)
         && target.state.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER) {
         return target.pos.up();
      } else {
         return target.state.getBlock() instanceof BedBlock && target.state.get(BedBlock.PART) == BedPart.FOOT
            ? target.pos.offset(target.state.get(BedBlock.FACING))
            : null;
      }
   }

   private static boolean hasNeighborSupport(MinecraftClient client, BlockPos pos) {
      BlockState actual = client.world.getBlockState(pos);
      if (!actual.isAir() && !actual.isReplaceable()) {
         return true;
      } else {
         for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.offset(direction);
            if (MaterialTracker.isLoaded(client.world, neighbor)) {
               BlockState state = client.world.getBlockState(neighbor);
               if (!state.isReplaceable() && !state.getOutlineShape(client.world, neighbor).isEmpty()) {
                  return true;
               }
            }
         }

         return false;
      }
   }

   public void getMaterials(MinecraftClient client) {
      if (this.schematic != null && client.player != null && client.world != null && client.interactionManager != null) {
         if (client.player.getAbilities().creativeMode && client.interactionManager.getCurrentGameMode().isCreative()) {
            if (client.player.currentScreenHandler != client.player.playerScreenHandler) {
               this.reason = "Close the container before getting materials.";
            } else {
               MaterialTracker.Snapshot snapshot = MaterialTracker.snapshot(client, this.schematic, this.transform);
               int added = 0;
               int omitted = 0;

               for (MaterialTracker.Row row : snapshot.rows()) {
                  if (row.item() != Items.AIR) {
                     int need = Math.max(0, row.remaining() - row.hotbar() - row.inventory());

                     for (int slot = 0; slot < 36 && need > 0; slot++) {
                        ItemStack existing = client.player.getInventory().getStack(slot);
                        if (existing.isEmpty() || existing.isOf(row.item())) {
                           int room = row.item().getDefaultStack().getMaxCount() - (existing.isEmpty() ? 0 : existing.getCount());
                           if (room > 0) {
                              int count = Math.min(room, need);
                              ItemStack stack = existing.isEmpty() ? new ItemStack(row.item(), count) : existing.copyWithCount(existing.getCount() + count);
                              client.player.getInventory().setStack(slot, stack);
                              client.interactionManager.clickCreativeStack(stack, slot < 9 ? slot + 36 : slot);
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

   @Environment(EnvType.CLIENT)
   private static final class Flight {
      final AutoBuilderController.Target target;
      final BlockState before;
      final int sentTick;
      final int sequence;
      boolean acknowledged;
      int ackTick;

      Flight(AutoBuilderController.Target target, BlockState before, int sentTick, int sequence) {
         this.target = target;
         this.before = before;
         this.sentTick = sentTick;
         this.sequence = sequence;
      }
   }

   private static enum Kind {
      /** Place (or configure) with a block item. */
      BLOCK,
      /** Needs a bucket of water. */
      WATER,
      /** Cannot be placed yet; something it depends on is missing. */
      LATER,
      /** The player has to do this one. */
      MANUAL,
      DONE;
   }

   private static enum Outcome {
      PLACED,
      /** A tick was spent preparing (sneaking, turning, moving an item, pausing). */
      BUSY,
      NONE;
   }

   @Environment(EnvType.CLIENT)
   public static enum Status {
      IDLE,
      READY,
      RUNNING,
      PAUSED,
      COMPLETED;
   }

   /** @param order layer and phase: 4 * y + (0 block, 1 water, 2 water plant) */
   @Environment(EnvType.CLIENT)
   private record Target(BlockPos pos, BlockState state, int index, boolean support, int order) {
   }
}
