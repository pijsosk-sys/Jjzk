package rexpath;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.class_1132;
import net.minecraft.class_2338;
import net.minecraft.class_243;
import net.minecraft.class_310;
import net.minecraft.class_318;
import net.minecraft.class_408;
import rex.RexPath;

/**
 * Walks REX Pathfinder through a set of shapes built out of commands and says, for each, whether
 * the player actually ended up there. Enabled with -Drexpath.run=true;
 * -Drexpath.scenarios=wall,break picks a subset. Results in rexpath-results.txt.
 */
public final class PathDriver implements ClientModInitializer {
   private static final boolean TRACE = Boolean.getBoolean("rexpath.trace");

   private record Scenario(String name, String what, int seconds, List<String> setup, int[] goal, boolean screenTest) {
   }

   private final List<String> lines = new ArrayList<>();
   private final List<Scenario> queue = new ArrayList<>();
   private int index = -1;
   private int phase;
   private int wait;
   private int ticks;
   private String last = "";
   private double closest = Double.MAX_VALUE;
   private class_243 frozenAt;
   private int screenOpenedAt;
   private String screenVerdict = "";

   /** A wide stone floor with open air over it, in survival, nothing carried. */
   private static List<String> base() {
      return new ArrayList<>(List.of(
         "gamemode survival @a",
         "clear @a",
         "effect clear @a",
         "effect give @a minecraft:saturation infinite 10 true",
         "effect give @a minecraft:resistance infinite 255 true",
         "time set noon",
         "gamerule doMobSpawning false",
         "gamerule randomTickSpeed 0",
         // Kept under /fill's 32,768 block limit, or the command quietly does nothing at all.
         "fill -16 -63 -16 100 -61 16 minecraft:stone",
         "fill -16 -60 -16 100 -55 16 minecraft:air",
         "tp @a 0.5 -60 0.5 90 0"
      ));
   }

   private static List<String> with(String... extra) {
      List<String> all = base();
      all.addAll(List.of(extra));
      return all;
   }

   private void build() {
      List<Scenario> all = new ArrayList<>();

      all.add(new Scenario("flat", "60 blocks across open ground", 120, base(), new int[]{60, -60, 0}, false));

      // An obsidian wall it is not allowed to break, with the only way round at the far edge.
      all.add(new Scenario("wall", "round a wall it cannot break", 180,
         with("fill 20 -60 -16 20 -57 13 minecraft:obsidian"),
         new int[]{40, -60, 0}, false));

      // Sealed in a stone box: the only way out is through a block.
      all.add(new Scenario("break", "out of a sealed stone box", 180,
         with("give @a minecraft:iron_pickaxe 1",
              "fill -3 -60 -3 3 -57 3 minecraft:stone hollow",
              "fill -2 -60 -2 2 -57 2 minecraft:air",
              "tp @a 0.5 -60 0.5 90 0"),
         new int[]{20, -60, 0}, false));

      // A staircase of single steps: eight jumps up.
      List<String> climb = base();
      for (int i = 0; i < 8; i++) {
         climb.add("setblock " + (2 + i) + " " + (-61 + i) + " 0 minecraft:stone");
         climb.add("setblock " + (2 + i) + " " + (-61 + i) + " 1 minecraft:stone");
      }
      all.add(new Scenario("climb", "eight steps up a staircase", 180, climb, new int[]{9, -53, 0}, false));

      // A cliff: the far half of the floor is lower, so it has to walk off the edge.
      all.add(new Scenario("drop", "off a cliff onto lower ground", 180,
         with("fill 12 -62 -16 100 -61 16 minecraft:air"),
         new int[]{30, -62, 0}, false));

      // The one the walk has to stop for: a screen opens halfway.
      all.add(new Scenario("screen", "stops while a screen is open, carries on after", 240, base(), new int[]{80, -60, 0}, true));

      String only = System.getProperty("rexpath.scenarios", "").toLowerCase(Locale.ROOT);
      for (Scenario scenario : all) {
         if (only.isEmpty() || List.of(only.split(",")).contains(scenario.name())) {
            this.queue.add(scenario);
         }
      }
   }

   @Override
   public void onInitializeClient() {
      if (Boolean.getBoolean("rexpath.run")) {
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
            RexPath.command(".goto stop");
            this.run(server, this.queue.get(this.index).setup());
            this.ticks = 0;
            this.last = "";
            this.closest = Double.MAX_VALUE;
            this.frozenAt = null;
            this.screenOpenedAt = 0;
            this.screenVerdict = "";
            this.phase = 2;
            this.wait = 60;
         }
         case 2 -> {
            int[] goal = this.queue.get(this.index).goal();
            RexPath.command(".goto " + goal[0] + " " + goal[1] + " " + goal[2]);
            this.phase = 3;
            this.wait = 5;
         }
         case 3 -> {
            Scenario scenario = this.queue.get(this.index);
            this.ticks++;
            RexPath module = RexPath.instance;
            String state = module == null ? "no module" : module.state();
            if (TRACE && this.ticks % 20 == 0) {
               System.out.println(String.format(Locale.ROOT, "[path-trace] %-8s %6.1fs %s", scenario.name(), this.ticks / 20.0, state));
            }
            this.last = state;
            class_2338 feet = client.field_1724.method_24515();
            double distance = Math.sqrt(feet.method_10262(new class_2338(scenario.goal()[0], scenario.goal()[1], scenario.goal()[2])));
            this.closest = Math.min(this.closest, distance);

            if (scenario.screenTest()) {
               this.screenStep(client);
            }

            boolean arrived = distance <= 1.5;
            boolean out = this.ticks > scenario.seconds() * 20;
            boolean quit = module != null && !module.isEnabled() && !arrived && this.ticks > 60;
            if (arrived || out || quit) {
               String verdict = arrived && (!scenario.screenTest() || this.screenVerdict.equals("held")) ? "PASS" : out ? "TIMEOUT" : arrived ? "PARTIAL" : "GAVE-UP";
               this.lines.add(String.format(
                  Locale.ROOT,
                  "%-8s %-8s %6.1fs  %s | closest %.1f | %s%s",
                  scenario.name(), verdict, this.ticks / 20.0, scenario.what(), this.closest, this.last,
                  scenario.screenTest() ? " | screen: " + (this.screenVerdict.isEmpty() ? "never opened" : this.screenVerdict) : ""
               ));
               RexPath.command(".goto stop");
               this.shot(client, scenario.name());
               this.phase = 1;
               this.wait = 40;
            }
         }
      }
   }

   /** Opens chat midway, checks nothing moves, then closes it again. */
   private void screenStep(class_310 client) {
      if (this.screenOpenedAt == 0 && this.ticks > 60) {
         this.screenOpenedAt = this.ticks;
         this.frozenAt = client.field_1724.method_73189();
         client.method_1507(new class_408("", false));
         return;
      }
      if (this.screenOpenedAt > 0 && this.screenVerdict.isEmpty()) {
         int open = this.ticks - this.screenOpenedAt;
         double moved = client.field_1724.method_73189().method_1022(this.frozenAt);
         if (open > 40) {
            this.screenVerdict = moved < 1.0 ? "held" : String.format(Locale.ROOT, "MOVED %.2f blocks", moved);
            client.method_1507(null);
         }
      }
   }

   private void shot(class_310 client, String name) {
      try {
         class_318.method_22690(client.field_1697, "path-" + name + ".png", client.method_1522(), 1, message -> {});
      } catch (Throwable ignored) {
      }
   }

   private void finish(class_310 client) {
      try {
         Files.write(Path.of("rexpath-results.txt"), this.lines);
      } catch (Exception failure) {
         failure.printStackTrace();
      }
      this.lines.forEach(line -> System.out.println("[path-test] " + line));
      client.method_1592();
   }
}
