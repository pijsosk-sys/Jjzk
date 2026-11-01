package rex.overlord;

import dev.donut.client.autobuild.BuilderLook;
import dev.donut.client.autobuild.BuilderNavigator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_1268;
import net.minecraft.class_1297;
import net.minecraft.class_1430;
import net.minecraft.class_1542;
import net.minecraft.class_1703;
import net.minecraft.class_1707;
import net.minecraft.class_1713;
import net.minecraft.class_1714;
import net.minecraft.class_1718;
import net.minecraft.class_1720;
import net.minecraft.class_1723;
import net.minecraft.class_2338;
import net.minecraft.class_310;
import net.minecraft.class_4862;
import net.minecraft.class_746;

/**
 * The progression itself: look at what is carried, work out the first thing missing, and do one
 * step toward it. Everything is decided from the inventory on every tick, so dying, logging out or
 * being handed a stack of diamonds all just change what the next step is.
 *
 * <p>Nothing here calls into Baritone. The old build asked Baritone for every walk and every ore
 * and did nothing whatsoever without it; walking is REX's own builder navigator and mining is
 * {@link Mine}.
 */
@Environment(EnvType.CLIENT)
public final class Overlord {
   /** Ore and mob routes that are only worth trying in their own dimension. */
   private static final Set<String> LOGS = Set.of(
      "oak_log", "birch_log", "spruce_log", "jungle_log", "acacia_log", "dark_oak_log", "cherry_log", "mangrove_log", "pale_oak_log"
   );

   private final BuilderNavigator navigator = new BuilderNavigator();
   {
      // Overlord replans far more often than the builder does, so it buys shorter searches.
      this.navigator.searchBudget(600, 1000);
   }
   /** Long hops go through REX's own pathfinder; the navigator handles the last few blocks. */
   private final rex.path.Traveller traveller = new rex.path.Traveller();
   private final Work work = new Work();
   private final Mine mine = new Mine();
   private final Sites sites = new Sites();
   private final Fight fight = new Fight();

   public Stage stage = Stage.PLAN;
   public String status = "Stopped";
   public boolean running;
   public boolean done;
   /** Set when Overlord gave up; the module shows it and switches itself off. */
   public String stopped = "";

   private class_310 client;
   private Map<String, Integer> stock = Map.of();
   private Map<String, Integer> previous = Map.of();
   private long ticks;
   private long lastChange;
   private int delay;
   private int retries;
   private Object world;
   private class_2338 wanderTarget;
   private int wanders;
   private boolean acted;
   private boolean mining;
   private boolean travelled;
   private class_1297 prey;
   /**
    * The piece on a workstation right now. A worn piece cannot be reached from a smithing or
    * enchanting screen, which only show the bag and the hotbar, so it has to stay off until the
    * work on it is finished.
    */
   private String offLimits = "";
   private class_1297 pickup;
   private int pickupTicks;
   private final Set<Integer> ignoredDrops = new HashSet<>();

   public void start(class_310 client) {
      this.client = client;
      this.running = true;
      this.done = false;
      this.stopped = "";
      this.stage = Stage.PLAN;
      this.status = "Reading the inventory";
      this.delay = 0;
      this.retries = 0;
      this.wanderTarget = null;
      this.wanders = 0;
      this.previous = Map.of();
      this.lastChange = this.ticks;
      this.work.clear();
      this.work.error = "";
      this.mine.reset(client);
      this.sites.worldChanged();
      this.navigator.reset();
      this.traveller.stop(client);
      this.pickup = null;
      this.ignoredDrops.clear();
   }

   public void stop(class_310 client) {
      this.running = false;
      this.mine.reset(client);
      this.fight.stop(client);
      this.work.clear();
      this.navigator.reset();
      this.navigator.release();
      this.traveller.stop(client);
      if (this.stopped.isEmpty()) {
         this.status = "Stopped";
      }
   }

   public String hudLine() {
      return this.stage.label() + ": " + this.status;
   }

   public int mined() {
      return this.mine.broken;
   }

   /** Where the miner's own time goes, in microseconds. */
   public String mineCost() {
      return this.mine.cost();
   }

   /** What Overlord costs the client, in microseconds a tick: a rolling average and the worst. */
   public long micros() {
      return (long)this.averageNanos / 1000L;
   }

   public long worstMicros() {
      return this.worstNanos / 1000L;
   }

   public void resetCost() {
      this.mine.resetCost();
      this.averageNanos = 0.0;
      this.worstNanos = 0L;
      this.worstPart = "-";
      this.worstPartNanos = 0L;
      this.warmUp = 0L;
   }

   private double averageNanos;
   private long worstNanos;
   /** Which part of a tick the worst spike was in, so a stutter can be traced to something. */
   private String worstPart = "-";
   private long worstPartNanos;
   private long warmUp;

   public String worstPart() {
      return this.worstPart + " " + this.worstPartNanos / 1000L + "us";
   }

   /** Times one part of the tick and keeps the worst, which is the one worth looking at. */
   private <T> T timed(String part, java.util.function.Supplier<T> work) {
      long started = System.nanoTime();
      try {
         return work.get();
      } finally {
         long took = System.nanoTime() - started;
         if (this.warmUp > 40L && took > this.worstPartNanos) {
            this.worstPartNanos = took;
            this.worstPart = part;
         }
      }
   }

   /** What Overlord is working on this tick: a block to break, to place, or a mob. */
   @Environment(EnvType.CLIENT)
   public record Focus(class_2338 block, class_1297 mob, Kind kind) {
   }

   @Environment(EnvType.CLIENT)
   public enum Kind {
      BREAK,
      PLACE,
      USE,
      FIGHT,
      WALK
   }

   private Focus focus;

   /** Null when it is not working on anything in particular. */
   public Focus focus() {
      return this.running ? this.focus : null;
   }

   private void setFocus() {
      if (this.fight.threat() != null) {
         this.focus = new Focus(null, this.fight.threat(), Kind.FIGHT);
         return;
      }
      class_2338 mining = this.mine.focus();
      if (this.mining && mining != null) {
         this.focus = new Focus(mining, null, Kind.BREAK);
         return;
      }
      if (this.sites.focus != null && this.acted) {
         this.focus = new Focus(this.sites.focus, null, this.sites.focusPlacing ? Kind.PLACE : Kind.USE);
         return;
      }
      if (this.pickup != null) {
         this.focus = new Focus(this.pickup.method_24515(), null, Kind.WALK);
         return;
      }
      this.focus = null;
   }

   /**
    * A widening spiral of walks, for when what is needed is simply not here. This is the one hop
    * that is genuinely long, so it goes through the pathfinder rather than the builder navigator,
    * which only ever looked a hundred blocks ahead.
    */
   public void wander() {
      class_2338 feet = this.client.field_1724.method_24515();
      if (this.wanderTarget == null || feet.method_19455(this.wanderTarget) < 6) {
         double angle = this.wanders++ * 2.399963229728653;
         int radius = 24 + (int)Math.sqrt(this.wanders) * 16;
         this.wanderTarget = feet.method_10069((int)(Math.cos(angle) * radius), 0, (int)(Math.sin(angle) * radius));
         this.traveller.stop(this.client);
      }
      this.acted = true;
      this.travelled = true;
      this.navigator.release();
      if (this.traveller.goal() == null) {
         this.traveller.go(
            this.client,
            new rex.path.Goal.Near(this.wanderTarget.method_10263(), this.wanderTarget.method_10264(), this.wanderTarget.method_10260(), 4.0)
         );
      }
      rex.path.Traveller.Result result = this.traveller.tick(this.client, TRAVEL, this.ticks);
      this.status = "Exploring: " + this.traveller.status;
      if (result == rex.path.Traveller.Result.ARRIVED || result == rex.path.Traveller.Result.UNREACHABLE) {
         this.wanderTarget = null;
         this.traveller.stop(this.client);
      }
   }

   /** How Overlord walks: it may break through, it never bridges, and a screen stops it. */
   // See Mine.TRAVEL: Overlord puts its own windows up, so it does not wait for screens to close.
   private static final rex.path.Traveller.Tuning TRAVEL = new rex.path.Traveller.Tuning(
      new rex.path.PathFinder.Options(true, false, true, 4, 1.6, 60000, 192), false, 12, 2500L, 3000, true
   );

   // ---------------------------------------------------------------- the tick

   public void tick(class_310 client) {
      long started = System.nanoTime();
      try {
         this.run(client);
      } finally {
         long took = System.nanoTime() - started;
         this.warmUp++;
         this.averageNanos += (took - this.averageNanos) * 0.02;
         // The first couple of seconds are the JIT warming up, not a stutter anyone sees.
         if (this.warmUp > 40L && took > this.worstNanos) {
            this.worstNanos = took;
         }
      }
   }

   private void run(class_310 client) {
      this.client = client;
      this.ticks++;
      if (!this.running) {
         return;
      }
      class_746 player = client.field_1724;
      if (player == null || client.field_1687 == null || client.field_1761 == null) {
         this.navigator.release();
         this.status = "Waiting for a world";
         return;
      }
      if (this.world != client.field_1687) {
         this.world = client.field_1687;
         this.work.clear();
         this.mine.reset(client);
         this.sites.worldChanged();
         this.navigator.worldChanged();
         this.navigator.reset();
         this.sites.rememberPortal(client);
         this.lastChange = this.ticks;
      }
      if (!player.method_5805()) {
         this.give("Died. Start again and it replans from whatever survived.");
         return;
      }

      if (this.work.pump(client, this.ticks)) {
         this.status = this.work.status;
         return;
      }
      if (!this.work.error.isEmpty()) {
         this.status = this.work.error;
         this.work.error = "";
         Work.close(client);
         this.delay = 20;
         return;
      }
      if (this.delay > 0) {
         this.delay--;
         return;
      }

      this.stock = Stock.counts(player);
      if (!this.stock.equals(this.previous)) {
         this.previous = new HashMap<>(this.stock);
         this.lastChange = this.ticks;
         this.retries = 0;
      }

      this.acted = false;
      this.mining = false;
      this.travelled = false;

      if (this.timed("fight", () -> this.fight.tick(client, this.navigator, this.stock, this.ticks))) {
         this.mine.reset(client);
         // The fight walks with the navigator, so the pathfinder must let go of the input.
         this.traveller.release();
         this.status = this.fight.status;
         this.setFocus();
         return;
      }

      // Anything broken lands on the floor out of arm's reach, so it has to be walked over. Without
      // this the miner broke a whole vein and carried on with an empty bag.
      if (this.timed("collect", this::collect)) {
         return;
      }

      // Nothing has changed hands in two minutes: shake the situation up before giving in.
      if (this.ticks - this.lastChange > 2400L) {
         this.lastChange = this.ticks;
         this.wanderTarget = null;
         if (++this.retries > 3) {
            this.give("No progress after three attempts. Move somewhere with trees or stone and start again.");
            return;
         }
         this.mine.reset(client);
         this.navigator.reset();
         this.wander();
         this.status = "Stuck; moving somewhere else (attempt " + this.retries + " of 3)";
         return;
      }

      try {
         this.timed("plan", () -> {
            this.plan(player);
            return null;
         });
      } catch (IllegalStateException refused) {
         this.give(refused.getMessage());
         return;
      }

      if (this.mining && !this.acted) {
         this.timed("mine", () -> {
            this.mine.tick(client, this.navigator, this.ticks, this.stock);
            return null;
         });
         this.status = this.mine.status;
      } else if (!this.mining) {
         this.mine.reset(client);
      }
      if (!this.travelled) {
         // Only one thing may hold the movement input at a time.
         this.traveller.release();
      }
      this.setFocus();
   }

   /** Walks onto nearby drops. True when this tick went on fetching one. */
   private boolean collect() {
      if (this.client.field_1755 != null || this.work.busy()) {
         return false;
      }
      class_746 player = this.client.field_1724;
      if (this.pickup != null && (!this.pickup.method_5805() || ++this.pickupTicks > 120)) {
         this.ignoredDrops.add(this.pickup.method_5628());
         this.pickup = null;
      }
      if (this.pickup == null) {
         double best = Double.MAX_VALUE;
         for (class_1297 entity : this.client.field_1687.method_8333(
            player,
            player.method_5829().method_1014(14.0),
            candidate -> candidate instanceof class_1542 && candidate.method_5805() && !this.ignoredDrops.contains(candidate.method_5628())
         )) {
            double distance = player.method_5858(entity);
            if (distance < best) {
               best = distance;
               this.pickup = entity;
               this.pickupTicks = 0;
            }
         }
      }
      if (this.pickup == null) {
         return false;
      }
      if (player.method_5858(this.pickup) < 1.6) {
         return false; // close enough that the game picks it up by itself
      }
      this.mine.reset(this.client);
      this.navigator.release();
      // Through the pathfinder, not the navigator: walking onto a drop is a short hop, but the
      // navigator pays for it with one unbudgeted six-thousand-cell search, which is a visible
      // freeze in the middle of a tick.
      class_2338 at = this.pickup.method_24515();
      rex.path.Goal goal = new rex.path.Goal.Block(at.method_10263(), at.method_10264(), at.method_10260());
      if (!goal.equals(this.traveller.goal())) {
         this.traveller.go(this.client, goal);
      }
      rex.path.Traveller.Result walk = this.traveller.tick(this.client, TRAVEL, this.ticks);
      if (walk == rex.path.Traveller.Result.UNREACHABLE || walk == rex.path.Traveller.Result.ARRIVED) {
         this.ignoredDrops.add(this.pickup.method_5628());
         this.pickup = null;
         this.traveller.stop(this.client);
         return false;
      }
      this.acted = true;
      this.travelled = true;
      this.status = "Picking up " + Ids.pretty(Ids.of(((class_1542)this.pickup).method_6983()));
      return true;
   }

   private void give(String why) {
      this.stopped = why;
      this.status = why;
      this.running = false;
      this.mine.reset(this.client);
      this.fight.stop(this.client);
      this.navigator.release();
   }

   private int count(String id) {
      return Stock.count(this.stock, id);
   }

   private boolean havePick(String... tiers) {
      for (String tier : tiers) {
         if (this.count(tier + "_pickaxe") > 0) {
            return true;
         }
      }
      return false;
   }

   // ---------------------------------------------------------------- the ladder

   private void plan(class_746 player) {
      if (this.wearArmour()) {
         this.acted = true;
         return;
      }
      if (Fight.starving(player, this.stock) && Sites.inNether(this.client) == false) {
         this.stage = Stage.WOOD;
         this.status = "Hungry with nothing to eat: hunting";
         this.hunt();
         return;
      }
      if (!this.havePick("netherite", "diamond", "iron", "stone")) {
         this.stage = Stage.BASICS;
         if (!this.ensure("wooden_pickaxe", 1, 0)) {
            return;
         }
      }
      if (!this.havePick("netherite", "diamond", "iron")) {
         this.stage = Stage.STONE;
         if (!this.ensure("stone_pickaxe", 1, 0) || !this.ensure("stone_sword", 1, 0)) {
            return;
         }
      }
      if (!this.havePick("netherite", "diamond")) {
         this.stage = Stage.IRON_GEAR;
         if (!this.ensure("iron_pickaxe", 1, 0) || !this.ensure("iron_sword", 1, 0)) {
            return;
         }
         for (String piece : List.of("helmet", "chestplate", "leggings", "boots")) {
            if (this.count("diamond_" + piece) == 0 && this.count("netherite_" + piece) == 0 && !this.ensure("iron_" + piece, 1, 0)) {
               return;
            }
         }
      }
      this.stage = Stage.DIAMOND_GEAR;
      for (String piece : Recipes.GEAR) {
         if (this.count("netherite_" + piece) == 0 && !this.ensure("diamond_" + piece, 1, 0)) {
            return;
         }
      }

      int upgrades = (int)Recipes.GEAR.stream().filter(piece -> this.count("netherite_" + piece) == 0).count();
      if (upgrades > 0) {
         this.stage = Stage.NETHERITE;
         if (!this.ensure("netherite_upgrade_smithing_template", upgrades, 0)
            || !this.ensure("netherite_ingot", upgrades, 0)
            || !this.ensure("smithing_table", 1, 0)) {
            return;
         }
         if (!this.station("smithing_table", class_4862.class)) {
            return;
         }
         for (String piece : Recipes.GEAR) {
            if (this.count("netherite_" + piece) == 0) {
               this.offLimits = "diamond_" + piece;
               this.acted = true;
               int armourSlot = this.wornSlot("diamond_" + piece);
               if (armourSlot >= 0) {
                  Work.close(this.client);
                  this.client.field_1761
                     .method_2906(this.client.field_1724.field_7498.field_7763, armourSlot, 0, class_1713.field_7794, this.client.field_1724);
                  this.status = "Taking the " + Ids.pretty(piece) + " off to upgrade it";
                  this.delay = 2;
                  return;
               }
               this.work.smith(this.client, "diamond_" + piece);
               this.status = "Upgrading the " + Ids.pretty(piece);
               return;
            }
         }
      }

      Set<String> enchanted = new HashSet<>();
      for (String piece : Recipes.GEAR) {
         if (Stock.enchanted(this.client.field_1724, "netherite_" + piece)) {
            enchanted.add("netherite_" + piece);
         }
      }
      if (!Recipes.complete(this.stock, enchanted)) {
         this.stage = Stage.ENCHANT_SETUP;
         if (!this.overworld() || !this.ensure("lapis_lazuli", 3, 0)) {
            return;
         }
         if (this.client.field_1724.field_7520 < 30) {
            this.stage = Stage.XP;
            this.status = "Collecting experience for a level-30 enchantment (level " + this.client.field_1724.field_7520 + ")";
            this.wantOre(Set.of("coal_ore", "deepslate_coal_ore"), 48);
            return;
         }
         if (!this.sites.enchanting(this.client, this.navigator, this)) {
            this.acted = true;
            this.status = this.sites.status;
            return;
         }
         this.stage = Stage.ENCHANT;
         for (String piece : Recipes.GEAR) {
            String id = "netherite_" + piece;
            if (!enchanted.contains(id)) {
               this.offLimits = id;
               this.acted = true;
               // A worn piece cannot be reached from the enchanting screen, which only shows the
               // bag and the hotbar. Take it off first, enchant it, and it goes back on after.
               int armourSlot = this.wornSlot(id);
               if (armourSlot >= 0) {
                  Work.close(this.client);
                  this.client.field_1761
                     .method_2906(this.client.field_1724.field_7498.field_7763, armourSlot, 0, class_1713.field_7794, this.client.field_1724);
                  this.status = "Taking the " + Ids.pretty(piece) + " off to enchant it";
                  this.delay = 2;
                  return;
               }
               this.work.enchant(this.client, id);
               this.status = "Enchanting the " + Ids.pretty(piece);
               return;
            }
         }
         this.offLimits = "";
         return;
      }

      this.stage = Stage.DONE;
      this.equip();
      if (!this.done) {
         this.done = true;
         this.running = false;
         this.status = "Done: enchanted netherite armour, sword and pickaxe";
         rex.Game.message("Overlord finished: enchanted netherite armour, sword and pickaxe are in the inventory.");
      }
   }

   /** Opens a workstation window, placing one first if there is none about. */
   private boolean station(String id, Class<? extends class_1703> handler) {
      boolean open = this.sites.open(this.client, this.navigator, id, handler);
      if (!open) {
         this.acted = true;
         this.status = this.sites.status;
      }
      return open;
   }

   private boolean overworld() {
      boolean there = this.sites.portal(this.client, this.navigator, this, false);
      if (!there) {
         this.acted = true;
         this.stage = Stage.PORTAL;
         this.status = this.sites.status;
      }
      return there;
   }

   private boolean nether() {
      boolean there = this.sites.portal(this.client, this.navigator, this, true);
      if (!there) {
         this.acted = true;
         this.stage = Stage.PORTAL;
         this.status = this.sites.status;
      }
      return there;
   }

   /** Points the miner at a set of blocks; the tick runs it once the ladder has had its say. */
   private void wantOre(Set<String> blocks, Integer layer) {
      this.mine.want(blocks, layer);
      this.mining = true;
   }

   // ---------------------------------------------------------------- acquiring one item

   /**
    * True when the player holds at least target of this item. False means a step toward it was
    * taken (or asked for) this tick and the ladder should stop here.
    */
   public boolean ensure(String id, int target, int depth) {
      if (this.count(id) >= target) {
         return true;
      }
      if (Recipes.STATIONS.contains(id) && this.sites.available(this.client, id)) {
         return true;
      }
      if (depth > 24) {
         throw new IllegalStateException("Recipes loop on " + Ids.pretty(id));
      }
      this.status = "Needs " + target + " " + Ids.pretty(id) + " (has " + this.count(id) + ")";

      if (id.equals("netherite_upgrade_smithing_template") && this.count(id) == 0) {
         if (!this.nether()) {
            return false;
         }
         this.stage = Stage.DEBRIS;
         this.acted = true;
         this.sites.template(this.client, this.navigator, this.work, this);
         this.status = this.sites.status;
         return false;
      }

      Recipes.Recipe recipe = Recipes.get(id);
      if (recipe != null) {
         for (Map.Entry<String, Integer> need : recipe.needs().entrySet()) {
            if (!this.ensure(need.getKey(), need.getValue(), depth + 1)) {
               return false;
            }
         }
         if (!recipe.small()) {
            if (!this.ensure("crafting_table", 1, depth + 1) || !this.station("crafting_table", class_1714.class)) {
               return false;
            }
         } else {
            class_1703 handler = Work.handler(this.client.field_1724);
            if (!(handler instanceof class_1714) && !(handler instanceof class_1723)) {
               Work.close(this.client);
               this.acted = true;
               this.status = "Closing a window to use the 2x2 grid";
               return false;
            }
         }
         this.acted = true;
         this.work.craft(this.client, recipe, this.stock);
         this.status = "Crafting " + Ids.pretty(id);
         this.delay = 2;
         return false;
      }

      String raw = switch (id) {
         case "iron_ingot" -> "raw_iron";
         case "gold_ingot" -> "raw_gold";
         case "netherite_scrap" -> "ancient_debris";
         case "cooked_beef" -> "beef";
         case "cooked_porkchop" -> "porkchop";
         default -> null;
      };
      if (raw != null) {
         class_1703 handler = Work.handler(this.client.field_1724);
         if (handler instanceof class_1720
            && (!Work.stack(handler, 2).method_7960() || Ids.of(Work.stack(handler, 0)).equals(raw))) {
            this.acted = true;
            this.work.smelt(this.client, raw);
            this.status = this.work.status;
            this.delay = 4;
            return false;
         }
         int missing = target - this.count(id);
         if (!this.ensure(raw, missing, depth + 1)
            || !this.ensure("furnace", 1, depth + 1)
            || !this.ensure("coal", Math.max(1, (missing + 7) / 8), depth + 1)) {
            return false;
         }
         if (!this.station("furnace", class_1720.class)) {
            return false;
         }
         this.stage = Stage.SMELT;
         this.acted = true;
         this.work.smelt(this.client, raw);
         this.status = this.work.status;
         this.delay = 4;
         return false;
      }

      switch (id) {
         case "logs" -> {
            if (!this.overworld()) {
               return false;
            }
            this.stage = Stage.WOOD;
            this.wantOre(LOGS, null);
         }
         case "cobblestone" -> {
            this.stage = Stage.STONE;
            this.wantOre(Set.of("stone", "cobblestone", "andesite", "diorite", "granite"), null);
         }
         case "coal" -> {
            if (!this.pickaxeAtLeast("wooden", depth)) {
               return false;
            }
            if (!this.overworld()) {
               return false;
            }
            this.wantOre(Set.of("coal_ore", "deepslate_coal_ore"), 48);
         }
         case "raw_iron" -> {
            if (!this.pickaxeAtLeast("stone", depth)) {
               return false;
            }
            if (!this.overworld()) {
               return false;
            }
            this.stage = Stage.IRON;
            this.wantOre(Set.of("iron_ore", "deepslate_iron_ore"), 16);
         }
         case "diamond" -> {
            if (!this.pickaxeAtLeast("iron", depth)) {
               return false;
            }
            if (!this.overworld()) {
               return false;
            }
            this.stage = Stage.DIAMONDS;
            this.wantOre(Set.of("diamond_ore", "deepslate_diamond_ore"), -54);
         }
         case "raw_gold" -> {
            if (!this.pickaxeAtLeast("iron", depth)) {
               return false;
            }
            this.wantOre(Sites.inNether(this.client) ? Set.of("nether_gold_ore", "gilded_blackstone") : Set.of("gold_ore", "deepslate_gold_ore"),
               Sites.inNether(this.client) ? null : -16);
         }
         case "lapis_lazuli" -> {
            if (!this.pickaxeAtLeast("stone", depth)) {
               return false;
            }
            if (!this.overworld()) {
               return false;
            }
            this.wantOre(Set.of("lapis_ore", "deepslate_lapis_ore"), -1);
         }
         case "obsidian" -> {
            if (!this.pickaxeAtLeast("diamond", depth)) {
               return false;
            }
            this.stage = Stage.PORTAL;
            this.wantOre(Set.of("obsidian"), null);
         }
         case "ancient_debris" -> {
            if (!this.pickaxeAtLeast("diamond", depth)) {
               return false;
            }
            if (!this.nether()) {
               return false;
            }
            this.stage = Stage.DEBRIS;
            this.wantOre(Set.of("ancient_debris"), 15);
         }
         case "netherrack" -> {
            if (!this.nether()) {
               return false;
            }
            this.wantOre(Set.of("netherrack"), null);
         }
         case "flint" -> {
            if (!this.overworld()) {
               return false;
            }
            this.wantOre(Set.of("gravel"), null);
         }
         case "sugar_cane" -> {
            if (!this.overworld()) {
               return false;
            }
            this.wantOre(Set.of("sugar_cane"), null);
         }
         case "leather", "beef" -> {
            if (!this.overworld()) {
               return false;
            }
            this.hunt();
         }
         default -> throw new IllegalStateException("No way to get " + Ids.pretty(id) + " yet");
      }
      return false;
   }

   /** Makes sure a pickaxe of at least this tier exists before sending the miner at hard rock. */
   private boolean pickaxeAtLeast(String tier, int depth) {
      List<String> order = List.of("wooden", "stone", "iron", "diamond", "netherite");
      for (String better : order.subList(order.indexOf(tier), order.size())) {
         if (this.count(better + "_pickaxe") > 0) {
            return true;
         }
      }
      return this.ensure(tier + "_pickaxe", 1, depth + 1);
   }

   /** Walks up to the nearest cow and hits it, for beef and leather. */
   private void hunt() {
      class_746 player = this.client.field_1724;
      if (this.prey != null && (!this.prey.method_5805() || player.method_5858(this.prey) > 2304.0)) {
         this.prey = null;
      }
      if (this.prey == null) {
         double best = Double.MAX_VALUE;
         for (class_1297 entity : this.client.field_1687
            .method_8333(player, player.method_5829().method_1014(48.0), candidate -> candidate instanceof class_1430 && candidate.method_5805())) {
            double distance = player.method_5858(entity);
            if (distance < best) {
               best = distance;
               this.prey = entity;
            }
         }
      }
      if (this.prey == null) {
         this.wander();
         this.status = "Looking for cows";
         return;
      }
      this.acted = true;
      double distance = player.method_5858(this.prey);
      if (distance > 9.0) {
         this.navigator.moveTo(this.client, this.prey.method_24515());
         this.status = "Walking up to a cow";
         return;
      }
      this.navigator.release();
      String sword = this.count("netherite_sword") > 0 ? "netherite_sword"
         : this.count("diamond_sword") > 0 ? "diamond_sword"
         : this.count("iron_sword") > 0 ? "iron_sword"
         : this.count("stone_sword") > 0 ? "stone_sword" : null;
      if (sword != null && !Stock.select(this.client, sword)) {
         this.status = "Taking out the " + Ids.pretty(sword);
         return;
      }
      float[] look = BuilderLook.anglesTo(player, this.prey.method_23317(), this.prey.method_23320() - 0.2, this.prey.method_23321());
      if (!BuilderLook.toward(player, look[0], look[1])) {
         this.status = "Lining up on a cow";
         return;
      }
      if (player.method_7261(0.0F) >= 0.95F) {
         this.client.field_1761.method_2918(player, this.prey);
         player.method_6104(class_1268.field_5808);
      }
      this.status = "Hunting a cow";
   }

   /** The armour slot this piece is worn in, or -1 when it is not being worn. */
   private int wornSlot(String id) {
      for (int piece = 0; piece < 4; piece++) {
         if (Ids.of(this.client.field_1724.field_7498.method_7611(5 + piece).method_7677()).equals(id)) {
            return 5 + piece;
         }
      }
      return -1;
   }

   /** Armour by slot, best first. The helmet slot is first, matching the screen handler. */
   private static final List<List<String>> ARMOUR = List.of(
      List.of("netherite_helmet", "diamond_helmet", "iron_helmet", "chainmail_helmet", "golden_helmet", "leather_helmet", "turtle_helmet"),
      List.of("netherite_chestplate", "diamond_chestplate", "iron_chestplate", "chainmail_chestplate", "golden_chestplate", "leather_chestplate"),
      List.of("netherite_leggings", "diamond_leggings", "iron_leggings", "chainmail_leggings", "golden_leggings", "leather_leggings"),
      List.of("netherite_boots", "diamond_boots", "iron_boots", "chainmail_boots", "golden_boots", "leather_boots")
   );

   /**
    * Wears the best armour it is carrying. Overlord used to put armour on only at the very end,
    * which meant it fought everything on the way there in its shirt: three zombies killed it in
    * under eight seconds with a full iron set sitting in the bag.
    *
    * @return true when it moved something this tick
    */
   private boolean wearArmour() {
      class_746 player = this.client.field_1724;
      if (this.client.field_1755 != null || this.work.busy()) {
         return false;
      }
      for (int piece = 0; piece < ARMOUR.size(); piece++) {
         List<String> order = ARMOUR.get(piece);
         int slot = 5 + piece;
         String worn = Ids.of(player.field_7498.method_7611(slot).method_7677());
         int wornRank = order.indexOf(worn);
         if (wornRank == 0) {
            continue; // already the best there is
         }
         int limit = wornRank < 0 ? order.size() : wornRank;
         for (int rank = 0; rank < limit; rank++) {
            if (order.get(rank).equals(this.offLimits)) {
               continue; // this one is waiting its turn on a workstation
            }
            int index = Stock.find(player, order.get(rank));
            if (index < 0) {
               continue;
            }
            // An empty slot takes the piece straight away; a full one is emptied first, which
            // keeps the cursor out of it entirely.
            int from = worn.equals("air") ? Stock.handlerSlot(index) : slot;
            this.client.field_1761.method_2906(player.field_7498.field_7763, from, 0, class_1713.field_7794, player);
            this.status = "Putting on " + Ids.pretty(order.get(rank));
            this.delay = 2;
            return true;
         }
      }
      return false;
   }

   /** Puts the finished armour on and holds the sword. */
   private void equip() {
      Work.close(this.client);
      if (this.wearArmour()) {
         this.acted = true;
         return;
      }
      Stock.select(this.client, "netherite_sword");
   }

   /** Only used by the tests and the HUD. */
   public boolean chestOpen() {
      return Work.handler(this.client.field_1724) instanceof class_1707;
   }

   public Stage stage() {
      return this.stage;
   }
}
