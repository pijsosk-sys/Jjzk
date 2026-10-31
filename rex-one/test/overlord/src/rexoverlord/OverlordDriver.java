package rexoverlord;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.class_1132;
import net.minecraft.class_310;
import net.minecraft.class_318;
import net.minecraft.class_746;
import rex.RexOverlord;
import rex.overlord.Overlord;
import rex.overlord.Stock;

/**
 * Runs REX Overlord in the real game, one scenario at a time, and says for each whether the thing
 * it was asked for actually turned up in the inventory. Enabled with -Drexoverlord.run=true;
 * -Drexoverlord.scenarios=tools,mine picks a subset. Results land in rexoverlord-results.txt.
 */
public final class OverlordDriver implements ClientModInitializer {
   private record Scenario(String name, String goalText, int seconds, List<String> setup, Predicate<class_746> goal) {
   }

   private static final boolean TRACE = Boolean.getBoolean("rexoverlord.trace");

   private final List<String> lines = new ArrayList<>();
   private final List<Scenario> queue = new ArrayList<>();
   private int index = -1;
   private int phase;
   private int wait;
   private int ticks;
   private final Map<String, Integer> stages = new LinkedHashMap<>();
   private String lastStage = "";
   private String worstStatus = "";

   private static int have(class_746 player, String id) {
      return Stock.count(Stock.counts(player), id);
   }

   private static boolean allGear(class_746 player, String prefix) {
      for (String piece : List.of("helmet", "chestplate", "leggings", "boots", "sword", "pickaxe")) {
         if (have(player, prefix + piece) == 0) {
            return false;
         }
      }
      return true;
   }

   private static boolean allEnchanted(class_746 player) {
      for (String piece : List.of("helmet", "chestplate", "leggings", "boots", "sword", "pickaxe")) {
         if (!Stock.enchanted(player, "netherite_" + piece)) {
            return false;
         }
      }
      return true;
   }

   /** Commands every scenario starts from: survival, nothing carried, a stone floor to mine. */
   private static List<String> base() {
      return new ArrayList<>(List.of(
         "gamemode survival @a",
         "clear @a",
         "xp set @a 0 levels",
         "effect clear @a",
         "effect give @a minecraft:saturation infinite 10 true",
         "effect give @a minecraft:resistance infinite 255 true",
         "time set noon",
         "gamerule doMobSpawning false",
         "gamerule randomTickSpeed 0",
         // A stone layer under the grass, so cobblestone and ore can be mined at all.
         "fill -24 -62 -24 24 -61 24 minecraft:stone",
         "fill -24 -60 -24 24 -56 24 minecraft:air",
         "tp @a 0.5 -60 0.5 90 0"
      ));
   }

   private static List<String> with(String... extra) {
      List<String> commands = base();
      commands.addAll(List.of(extra));
      return commands;
   }

   private void build() {
      List<Scenario> all = new ArrayList<>();

      all.add(new Scenario("tools", "a stone pickaxe made from scratch", 420,
         with("fill 6 -60 0 6 -55 0 minecraft:oak_log", "fill 6 -60 4 6 -55 4 minecraft:oak_log",
              "fill -6 -60 0 -6 -55 0 minecraft:oak_log"),
         player -> have(player, "stone_pickaxe") > 0));

      all.add(new Scenario("mine", "raw iron dug out of a vein", 420,
         with("give @a minecraft:stone_pickaxe 1", "give @a minecraft:oak_planks 16", "give @a minecraft:stick 16",
              "give @a minecraft:crafting_table 1",
              "fill 10 -62 0 12 -62 2 minecraft:iron_ore", "fill -11 -61 3 -9 -61 5 minecraft:iron_ore"),
         player -> have(player, "raw_iron") > 0 || have(player, "iron_ingot") > 0));

      all.add(new Scenario("smelt", "iron ingots out of a furnace", 240,
         with("give @a minecraft:raw_iron 8", "give @a minecraft:coal 8", "give @a minecraft:furnace 1",
              "give @a minecraft:stone_pickaxe 1", "give @a minecraft:oak_planks 16", "give @a minecraft:stick 16",
              "give @a minecraft:crafting_table 1"),
         player -> have(player, "iron_ingot") > 0));

      all.add(new Scenario("irongear", "the full iron set crafted", 420,
         with("give @a minecraft:iron_ingot 40", "give @a minecraft:stick 16", "give @a minecraft:oak_planks 16",
              "give @a minecraft:crafting_table 1", "give @a minecraft:stone_pickaxe 1"),
         player -> have(player, "iron_pickaxe") > 0 && have(player, "iron_sword") > 0 && have(player, "iron_chestplate") > 0
            && have(player, "iron_helmet") > 0 && have(player, "iron_leggings") > 0 && have(player, "iron_boots") > 0));

      all.add(new Scenario("diamondgear", "the full diamond set crafted", 420,
         with("give @a minecraft:diamond 40", "give @a minecraft:stick 16", "give @a minecraft:oak_planks 16",
              "give @a minecraft:crafting_table 1", "give @a minecraft:diamond_pickaxe 1"),
         player -> allGear(player, "diamond_")));

      all.add(new Scenario("netherite", "every diamond piece upgraded", 420,
         with("give @a minecraft:diamond_helmet 1", "give @a minecraft:diamond_chestplate 1", "give @a minecraft:diamond_leggings 1",
              "give @a minecraft:diamond_boots 1", "give @a minecraft:diamond_sword 1", "give @a minecraft:diamond_pickaxe 1",
              "give @a minecraft:netherite_upgrade_smithing_template 6", "give @a minecraft:netherite_ingot 6",
              "give @a minecraft:smithing_table 1"),
         player -> allGear(player, "netherite_")));

      all.add(new Scenario("enchant", "all six netherite pieces enchanted", 600,
         with("give @a minecraft:netherite_helmet 1", "give @a minecraft:netherite_chestplate 1", "give @a minecraft:netherite_leggings 1",
              "give @a minecraft:netherite_boots 1", "give @a minecraft:netherite_sword 1", "give @a minecraft:netherite_pickaxe 1",
              "give @a minecraft:enchanting_table 1", "give @a minecraft:bookshelf 16", "give @a minecraft:lapis_lazuli 64",
              "xp set @a 60 levels"),
         OverlordDriver::allEnchanted));

      all.add(new Scenario("eat", "food eaten before the bar runs out", 180,
         with("give @a minecraft:cooked_beef 8", "give @a minecraft:stone_pickaxe 1", "effect clear @a minecraft:saturation",
              "effect give @a minecraft:hunger 30 20 true"),
         player -> have(player, "cooked_beef") < 8));

      all.add(new Scenario("fight", "a zombie beaten off", 180,
         with("give @a minecraft:iron_sword 1", "give @a minecraft:stone_pickaxe 1", "give @a minecraft:oak_planks 16",
              // Midnight, or the zombie burns up in the sun and the fight is never tested.
              "time set midnight", "summon minecraft:zombie 2 -60 0 {PersistenceRequired:1b}"),
         player -> player.method_73183()
            .method_8333(player, player.method_5829().method_1014(8.0), entity -> entity instanceof net.minecraft.class_1588 && entity.method_5805())
            .isEmpty()));

      String only = System.getProperty("rexoverlord.scenarios", "").toLowerCase(Locale.ROOT);
      for (Scenario scenario : all) {
         if (only.isEmpty() || List.of(only.split(",")).contains(scenario.name())) {
            this.queue.add(scenario);
         }
      }
   }

   @Override
   public void onInitializeClient() {
      if (Boolean.getBoolean("rexoverlord.run")) {
         ClientTickEvents.END_CLIENT_TICK.register(this::tick);
      }
   }

   private void tick(class_310 client) {
      try {
         this.step(client);
      } catch (Throwable failure) {
         this.lines.add("DRIVER FAILED: " + failure);
         failure.printStackTrace();
         this.finish(client);
      }
   }

   private void run(class_1132 server, List<String> commands) {
      for (String command : commands) {
         server.execute(() -> server.method_3734().method_44252(server.method_3739(), command));
      }
   }

   private void step(class_310 client) throws Exception {
      if (this.wait > 0) {
         this.wait--;
         return;
      }
      class_1132 server = client.method_1576();
      if (client.field_1724 == null || client.field_1687 == null || server == null) {
         return;
      }
      switch (this.phase) {
         case 0 -> {
            this.build();
            this.lines.add("scenarios: " + this.queue.size());
            this.phase = 1;
            this.wait = 60;
         }
         case 1 -> {
            if (++this.index >= this.queue.size()) {
               this.finish(client);
               return;
            }
            Scenario scenario = this.queue.get(this.index);
            RexOverlord.command("#overlord stop");
            this.run(server, scenario.setup());
            this.stages.clear();
            this.lastStage = "";
            this.worstStatus = "";
            this.ticks = 0;
            this.phase = 2;
            this.wait = 60;
         }
         case 2 -> {
            RexOverlord.command("#overlord start");
            this.phase = 3;
            this.wait = 10;
         }
         case 3 -> {
            Scenario scenario = this.queue.get(this.index);
            Overlord engine = RexOverlord.instance == null ? null : RexOverlord.instance.engine();
            if (engine != null) {
               String stage = engine.stage.name();
               if (!stage.equals(this.lastStage)) {
                  this.lastStage = stage;
                  this.stages.merge(stage, 1, Integer::sum);
               }
               if (!engine.status.isEmpty()) {
                  this.worstStatus = engine.status.length() > 160 ? engine.status.substring(0, 160) + "..." : engine.status;
               }
               if (TRACE && this.ticks % 20 == 0) {
                  System.out.println(String.format(Locale.ROOT, "[overlord-trace] %6.1fs %-14s %s",
                     this.ticks / 20.0, engine.stage.name(), this.worstStatus));
               }
            }
            this.ticks++;
            boolean reached = scenario.goal().test(client.field_1724);
            boolean out = this.ticks > scenario.seconds() * 20;
            boolean gaveUp = engine != null && !engine.stopped.isEmpty();
            if (reached || out || gaveUp) {
               String verdict = reached ? "PASS" : gaveUp ? "GAVE-UP" : "TIMEOUT";
               this.lines.add(String.format(
                  Locale.ROOT,
                  "%-12s %-7s %6.1fs  goal: %s | stage: %s | last: %s%s",
                  scenario.name(), verdict, this.ticks / 20.0, scenario.goalText(),
                  engine == null ? "?" : engine.stage.name(), this.worstStatus,
                  gaveUp ? " | gave up: " + engine.stopped : ""
               ));
               this.lines.add("             stages seen: " + this.stages.keySet());
               RexOverlord.command("#overlord stop");
               this.shot(client, scenario.name());
               this.phase = 1;
               this.wait = 40;
            }
         }
      }
   }

   private void shot(class_310 client, String name) {
      try {
         class_318.method_22690(client.field_1697, "overlord-" + name + ".png", client.method_1522(), 1, message -> {});
      } catch (Throwable ignored) {
      }
   }

   private void finish(class_310 client) {
      try {
         Files.write(Path.of("rexoverlord-results.txt"), this.lines);
      } catch (Exception failure) {
         failure.printStackTrace();
      }
      this.lines.forEach(line -> System.out.println("[overlord-test] " + line));
      client.method_1592();
   }
}
