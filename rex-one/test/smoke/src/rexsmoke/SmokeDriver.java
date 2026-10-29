package rexsmoke;

import dev.donut.client.autobuild.AutoBuilderController;
import dev.donut.client.autobuild.CreativeBuildMode;
import dev.donut.client.config.AutoBuilderSettings;
import dev.donut.client.autobuild.MaterialTracker;
import dev.donut.client.autobuild.PlacementTransform;
import dev.donut.client.autobuild.Schematic;
import dev.donut.client.autobuild.UnifiedSchematics;
import dev.donut.client.config.ClientConfig;
import dev.donut.client.gui.AutoBuilderScreen;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.class_1132;
import net.minecraft.class_2338;
import net.minecraft.class_2680;
import net.minecraft.class_310;
import net.minecraft.class_318;
import net.minecraft.class_3218;

/**
 * Drives Auto Build in a real singleplayer world, normal ticking, once per Build Tempo preset plus
 * once with Build Tempo off (the original cadence), and compares every block on the server with
 * the blueprint. Active only with -Drexsmoke.schem=<file>; join a flat world with
 * --quickPlaySingleplayer. Results go to rexsmoke-results.txt; the game quits when done.
 */
public final class SmokeDriver implements ClientModInitializer {
   private static final class_2338 ORIGIN = new class_2338(0, -60, 0);
   private static final boolean CREATIVE = Boolean.getBoolean("rexsmoke.creative");
   private static final boolean RESTOCK = Boolean.getBoolean("rexsmoke.restock");
   private static final int MAX_SECONDS = Integer.getInteger("rexsmoke.maxSeconds", 300);
   private static final int STALL_TICKS = 20 * Integer.getInteger("rexsmoke.stallSeconds", 20);
   private static final String[] SURVIVAL = {
      "gamemode survival @a", "effect give @a minecraft:resistance infinite 255 true", "effect give @a minecraft:saturation infinite 255 true", "clear @a",
      "give @a minecraft:oak_planks 128", "give @a minecraft:stone_bricks 192", "give @a minecraft:oak_log 64", "give @a minecraft:glass 64",
      "give @a minecraft:oak_door 4", "give @a minecraft:stone_brick_stairs 64", "give @a minecraft:smooth_stone_slab 128", "give @a minecraft:lantern 4",
      "give @a minecraft:dirt 64", "give @a minecraft:oak_sign 16", "tp @a 4.5 -60 -3.5 0 20"};
   private final List<String> lines = new ArrayList<>();
   private final List<Object> modes = new ArrayList<>();
   private Schematic schematic;
   private int mode = -1;
   private int phase;
   private int wait;
   private int ticks;
   private int lastConfirmed;
   private int still;
   private volatile int[] verified;
   private final java.util.Map<String, Integer> reasons = new java.util.HashMap<>();
   private final java.util.ArrayDeque<String> transitions = new java.util.ArrayDeque<>();
   private String lastReason = "";
   private static final String[] SHOTS = {"OFF", "SOFT", "TUFF", "VIVID", "OFF"};
   private int shot;
   private String viewShot;
   private int afterView;
   private boolean shotArmed;

   @Override
   public void onInitializeClient() {
      if (System.getProperty("rexsmoke.schem") != null) {
         ClientTickEvents.END_CLIENT_TICK.register(this::tick);
      }
   }

   private void tick(class_310 client) {
      try {
         this.step(client);
      } catch (Throwable error) {
         this.log("FAILED: " + error);
         error.printStackTrace();
         this.finish(client);
      }
   }

   private void step(class_310 client) throws Exception {
      if (Boolean.getBoolean("rexsmoke.clean")) {
         client.method_1566().method_2000(); // recipe and tutorial pop-ups, for recordings
      }
      if (this.wait > 0) {
         this.wait--;
         return;
      }
      class_1132 server = client.method_1576();
      switch (this.phase) {
         case 0 -> {
            if (client.field_1724 == null || client.field_1687 == null || client.field_1755 != null || server == null) {
               return;
            }
            this.schematic = UnifiedSchematics.load(Path.of(System.getProperty("rexsmoke.schem")));
            this.log("schematic " + this.schematic.name() + ": " + this.schematic.blocks().size() + " blocks");
            String only = System.getProperty("rexsmoke.presets", "");
            for (Object preset : presets()) {
               if (only.isEmpty() || preset == null || only.toUpperCase(Locale.ROOT).contains(preset.toString())) {
                  this.modes.add(preset);
               }
            }
            if (hasPresets() && (System.getProperty("rexsmoke.presets", "").isEmpty() || System.getProperty("rexsmoke.presets").toUpperCase(Locale.ROOT).contains("OFF"))) {
               this.modes.add("OFF");
            }
            this.phase = Boolean.getBoolean("rexsmoke.ui") ? 20 : 1;
            this.wait = 60;
         }
         case 20 -> {
            // -Drexsmoke.ui=true: the module menu and the Custom speed editor, screenshotted, then quit.
            Object krypton = Class.forName("dev.krypton.client.KryptonClient").getField("instance").get(null);
            Object manager = krypton.getClass().getField("moduleManager").get(krypton);
            for (Object module : (List<?>)manager.getClass().getMethod("all").invoke(manager)) {
               Object category = module.getClass().getMethod("getCategory").invoke(module);
               if (String.valueOf(category).equals("RENDER")) {
                  this.log("render module: " + module.getClass().getMethod("getName").invoke(module));
               }
            }
            client.method_1507((net.minecraft.class_437)Class.forName("dev.krypton.gui.ClickGuiScreen").getConstructor().newInstance());
            this.phase = 21;
            this.wait = 60;
         }
         case 21 -> {
            this.log("fps in the world (module menu open): " + client.method_47599());
            class_318.method_22690(client.field_1697, "rexsmoke-menu.png", client.method_1522(), 1, message -> {});
            AutoBuilderController.get().setSchematic(this.schematic);
            AutoBuilderController.get().setTransform(new PlacementTransform(ORIGIN, 0, false));
            choose(Class.forName("rex.BuildTempo$Preset").getField("FAST").get(null));
            client.method_1507(new AutoBuilderScreen(null, new ClientConfig(), () -> {}));
            this.phase = 22;
            this.wait = 60;
         }
         case 22 -> {
            this.log("fps with the builder screen open: " + client.method_47599());
            class_318.method_22690(client.field_1697, "rexsmoke-builder-screen.png", client.method_1522(), 1, message -> {});
            choose(Class.forName("rex.BuildTempo$Preset").getField("CUSTOM").get(null));
            Method open = AutoBuilderScreen.class.getDeclaredMethod("openTempo");
            open.setAccessible(true);
            open.invoke(client.field_1755);
            this.phase = 23;
            this.wait = 60;
         }
         case 23 -> {
            class_318.method_22690(client.field_1697, "rexsmoke-custom.png", client.method_1522(), 1, message -> {});
            Field progress = AutoBuilderScreen.class.getDeclaredField("modalClosing");
            progress.setAccessible(true);
            progress.set(client.field_1755, true);
            this.phase = 24;
            this.wait = 40;
         }
         case 24 -> {
            class_318.method_22690(client.field_1697, "rexsmoke-builder-custom.png", client.method_1522(), 1, message -> {});
            this.log("tempo: " + Class.forName("rex.BuildTempo").getMethod("describe").invoke(null));
            this.finish(client);
         }
         case 1 -> {
            if (++this.mode >= this.modes.size()) {
               this.phase = 4;
               return;
            }
            class_2338 size = this.schematic.dimensions();
            List<String> commands = new ArrayList<>();
            for (int y = -60; y <= -60 + size.method_10264() + 4; y++) {
               commands.add("fill -3 " + y + " -3 " + (size.method_10263() + 3) + " " + y + " " + (size.method_10260() + 3) + " minecraft:air");
            }
            if (RESTOCK) {
               // Survival with a short supply and a shulker box holding the rest: only restocking finishes it.
               commands.addAll(List.of("gamemode survival @a", "clear @a", "give @a minecraft:stone_bricks 32", "give @a minecraft:dirt 64",
                  "give @a minecraft:shulker_box[container=[{slot:0,item:{id:\"minecraft:stone_bricks\",count:64}},"
                     + "{slot:1,item:{id:\"minecraft:stone_bricks\",count:64}},{slot:2,item:{id:\"minecraft:stone_bricks\",count:64}},"
                     + "{slot:3,item:{id:\"minecraft:stone_bricks\",count:64}}]] 1",
                  "effect give @a minecraft:resistance infinite 255 true", "effect give @a minecraft:saturation infinite 255 true",
                  "tp @a " + (size.method_10263() / 2.0) + " -60 -3.5 0 20"));
               Object tempo = Class.forName("rex.BuildTempo").getField("instance").get(null);
               Object setting = tempo.getClass().getField("restock").get(tempo);
               setting.getClass().getMethod("set", boolean.class).invoke(setting, true);
            } else if (CREATIVE) {
               commands.add("gamemode creative @a");
               commands.add("clear @a");
               commands.add("tp @a " + (size.method_10263() / 2.0) + " -60 -3.5 0 20");
               AutoBuilderSettings settings = new AutoBuilderSettings();
               settings.creativeMode = true;
               CreativeBuildMode.configure(settings);
            } else {
               commands.addAll(List.of(SURVIVAL));
            }
            commands.add("time set noon");
            for (String command : commands) {
               server.execute(() -> server.method_3734().method_44252(server.method_3739(), command));
            }
            this.phase = 2;
            this.wait = 40;
         }
         case 2 -> {
            AutoBuilderController builder = AutoBuilderController.get();
            builder.setSchematic(this.schematic);
            builder.setTransform(new PlacementTransform(ORIGIN, 0, false));
            choose(this.modes.get(this.mode));
            if (Boolean.getBoolean("rexsmoke.thirdPerson")) {
               // For recordings: the camera behind the player shows the builder at work.
               client.field_1690.method_31043(net.minecraft.class_5498.field_26665);
            }
            builder.start();
            this.ticks = 0;
            this.still = 0;
            this.lastConfirmed = -1;
            this.phase = 3;
         }
         case 3 -> {
            AutoBuilderController builder = AutoBuilderController.get();
            this.ticks++;
            String reason = builder.reason();
            if (!reason.equals(this.lastReason)) {
               this.lastReason = reason;
               this.transitions.addLast(this.ticks + " " + reason + " | player " + client.field_1724.method_24515().method_23854());
               System.out.println("[rexsmoke-trace] " + this.ticks + " " + reason + " | player " + client.field_1724.method_24515().method_23854());
               if (this.transitions.size() > 30) {
                  this.transitions.removeFirst();
               }
            }
            if (this.ticks % 40 == 0) {
               // A heartbeat, so a long stretch of one reason (a stall) still shows where the player was.
               net.minecraft.class_746 p = client.field_1724;
               this.transitions.addLast(String.format(Locale.ROOT, "%d   ... %s | at %.2f %.2f %.2f yaw %.0f pitch %.0f ground %s fps %d",
                  this.ticks, reason, p.method_23317(), p.method_23318(), p.method_23321(), p.method_36454(), p.method_36455(), p.method_24828(), client.method_47599()));
               if (this.transitions.size() > 30) {
                  this.transitions.removeFirst();
               }
            }
            int cut = reason.indexOf(" at ");
            this.reasons.merge(cut > 0 ? reason.substring(0, cut) : reason.replaceAll("[0-9]+", "#"), 1, Integer::sum);
            // Removing temporary supports adds no blueprint blocks, but it is progress all the same.
            int confirmed = builder.confirmedBlocks() * 4096 + builder.supportsPlaced();
            if (confirmed != this.lastConfirmed) {
               this.lastConfirmed = confirmed;
               this.still = 0;
            } else {
               this.still++;
            }
            boolean done = builder.status() == AutoBuilderController.Status.COMPLETED || this.still > STALL_TICKS || this.ticks > 20 * MAX_SECONDS;
            if (!done) {
               return;
            }
            if (this.verified == null) {
               server.execute(() -> this.verified = this.verify(server.method_30002()));
               return;
            }
            int[] result = this.verified;
            this.verified = null;
            double seconds = this.ticks / 20.0;
            Object current = this.modes.get(this.mode);
            this.log(String.format(Locale.ROOT, "%-9s %-9s %6.1fs  %5.1f placed/s  placements sent %s  server: %d/%d match (%d exact), %d wrong  | %s",
               current == null ? "default" : current, builder.status(), seconds, result[1] / seconds, sent(builder), result[1],
               this.schematic.blocks().size(), result[0], result[2], builder.reason()));
            this.reasons.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(6)
               .forEach(e -> this.log(String.format(Locale.ROOT, "    %5.1f%%  %s", e.getValue() * 100.0 / this.ticks, e.getKey())));
            this.reasons.clear();
            if (builder.status() != AutoBuilderController.Status.COMPLETED) {
               this.log("  last builder states:");
               this.transitions.forEach(line -> this.log("    " + line));
            }
            this.transitions.clear();
            this.lastReason = "";
            String name = "rexsmoke-" + (current == null ? "default" : current.toString().toLowerCase(Locale.ROOT)) + ".png";
            class_318.method_22690(client.field_1697, name, client.method_1522(), 1, message -> {});
            if (builder.status() == AutoBuilderController.Status.COMPLETED) {
               // Step back for a picture of the finished build from outside.
               class_2338 size = this.schematic.dimensions();
               double distance = Math.max(size.method_10263(), size.method_10260()) * 1.1 + 6;
               String view = String.format(Locale.ROOT, "tp @a %.1f %.1f %.1f -35 25", size.method_10263() / 2.0 - distance * 0.55,
                  -60 + size.method_10264() * 0.7 + 3, size.method_10260() / 2.0 - distance * 0.8);
               server.execute(() -> server.method_3734().method_44252(server.method_3739(), view));
               this.viewShot = "rexsmoke-" + (current == null ? "default" : current.toString().toLowerCase(Locale.ROOT)) + "-view.png";
            }
            if (this.mode == this.modes.size() - 2 || this.modes.size() == 1) {
               // Leave the last preset run on screen for the builder screen shot.
               this.phase = 5;
               this.wait = 10;
            } else {
               builder.cancel();
               this.phase = 1;
               this.wait = 10;
            }
            if (this.viewShot != null) {
               this.afterView = this.phase;
               this.phase = 30;
               this.wait = 40;
            }
         }
         case 30 -> {
            class_318.method_22690(client.field_1697, this.viewShot, client.method_1522(), 1, message -> {});
            this.viewShot = null;
            this.phase = this.afterView;
            this.wait = 5;
         }
         case 5 -> {
            client.method_1507(new AutoBuilderScreen(null, new ClientConfig(), () -> {}));
            this.phase = 6;
            this.wait = 60;
         }
         case 6 -> {
            class_318.method_22690(client.field_1697, "rexsmoke-builder-screen.png", client.method_1522(), 1, message -> {});
            this.phase = 7;
            this.wait = 5;
         }
         case 7 -> {
            client.method_1507(null);
            AutoBuilderController.get().cancel();
            this.phase = 1;
            this.wait = 10;
         }
         case 4 -> {
            if (!Boolean.getBoolean("rexsmoke.shader") || this.shot >= SHOTS.length) {
               this.finish(client);
               return;
            }
            // Same view, shader off and in each style.
            String style = SHOTS[this.shot];
            if (!this.shotArmed) {
               setShader(style);
               this.shotArmed = true;
               this.wait = 20;
               return;
            }
            class_318.method_22690(client.field_1697, "rexsmoke-shader-" + style.toLowerCase(Locale.ROOT) + ".png", client.method_1522(), 1, message -> {});
            this.shotArmed = false;
            this.shot++;
            this.wait = 5;
         }
         default -> {
         }
      }
   }

   private int[] verify(class_3218 world) {
      PlacementTransform transform = new PlacementTransform(ORIGIN, 0, false);
      int exact = 0;
      int matching = 0;
      int wrong = 0;
      for (Schematic.Block block : this.schematic.blocks()) {
         class_2680 desired = transform.apply(block.state());
         class_2338 pos = transform.apply(block.pos());
         class_2680 actual = world.method_8320(pos);
         if (actual.equals(desired)) {
            exact++;
         }
         if (AutoBuilderController.matches(desired, actual)) {
            matching++;
         } else {
            wrong++;
            if (wrong <= 8) {
               this.log("  wrong at " + pos.method_23854() + ": want " + desired + " got " + actual + " | builder: " + explain(pos));
            }
         }
      }
      return new int[]{exact, matching, wrong};
   }

   private static String explain(class_2338 pos) {
      try {
         return String.valueOf(AutoBuilderController.class.getMethod("explain", class_2338.class).invoke(AutoBuilderController.get(), pos));
      } catch (ReflectiveOperationException none) {
         return "n/a";
      }
   }

   private void finish(class_310 client) {
      try {
         Files.write(Path.of("rexsmoke-results.txt"), this.lines);
      } catch (Exception ignored) {
      }
      this.phase = 99;
      client.method_1592();
   }

   private static void setShader(String style) throws Exception {
      Class<?> type = Class.forName("rex.TuffShader");
      Object module = type.getField("instance").get(null);
      Method enable = module.getClass().getMethod("setEnabled", boolean.class);
      if (style.equals("OFF")) {
         enable.invoke(module, false);
         return;
      }
      Object setting = type.getField("style").get(module);
      Class<?> styles = Class.forName("rex.TuffShader$Style");
      setting.getClass().getMethod("set", Enum.class).invoke(setting, Enum.valueOf((Class)styles, style));
      enable.invoke(module, false);
      enable.invoke(module, true);
   }

   private static boolean hasPresets() {
      try {
         Class.forName("rex.BuildTempo$Preset");
         return true;
      } catch (ClassNotFoundException none) {
         return false;
      }
   }

   private static List<Object> presets() {
      List<Object> values = new ArrayList<>();
      try {
         for (Object value : Class.forName("rex.BuildTempo$Preset").getEnumConstants()) {
            if (!value.toString().equals("CUSTOM")) {
               values.add(value);
            }
         }
      } catch (ClassNotFoundException none) {
         values.add(null);
      }
      return values;
   }

   private static void choose(Object preset) throws Exception {
      Class<?> tempo = Class.forName("rex.BuildTempo");
      Field instance = tempo.getField("instance");
      Object module = instance.get(null);
      Method enable = module.getClass().getMethod("setEnabledSilently", boolean.class);
      if ("OFF".equals(preset)) {
         enable.invoke(module, false);
         return;
      }
      enable.invoke(module, true);
      if (preset != null) {
         tempo.getMethod("choose", preset.getClass()).invoke(null, preset);
      }
   }

   private static String sent(AutoBuilderController builder) {
      try {
         return String.valueOf(AutoBuilderController.class.getMethod("placementsSent").invoke(builder));
      } catch (ReflectiveOperationException none) {
         return "n/a";
      }
   }

   private void log(String line) {
      System.out.println("[rexsmoke] " + line);
      this.lines.add(line);
   }
}
