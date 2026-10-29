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
   /** -Drexsmoke.farmSeconds=N: after a completed build, let it grow N seconds and count the harvest. */
   private static final int FARM_SECONDS = Integer.getInteger("rexsmoke.farmSeconds", 0);
   private static final int FARM_TICK_SPEED = Integer.getInteger("rexsmoke.farmTickSpeed", 300);
   private boolean farmed;
   private int farmWait;
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
   private List<java.nio.file.Path> library;
   private String startingFile = "";
   private String pendingShot;
   private int pendingShotAt;
   private int afterView;
   private boolean shotArmed;

   @Override
   public void onInitializeClient() {
      if (System.getProperty("rexsmoke.schem") != null) {
         ClientTickEvents.END_CLIENT_TICK.register(this::tick);
         LagSim.register();
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
      if (this.pendingShot != null && --this.pendingShotAt <= 0) {
         class_318.method_22690(client.field_1697, this.pendingShot, client.method_1522(), 1, message -> {});
         this.pendingShot = null;
      }
      LagSim.install(client);
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
            this.phase = Boolean.getBoolean("rexsmoke.starts") ? 40 : Boolean.getBoolean("rexsmoke.ui") ? 20 : 1;
            this.wait = 60;
         }
         case 40 -> {
            // -Drexsmoke.starts=true: every schematic in the library, started with the screen's own
            // Start button, as a player does; logs what the builder is doing two seconds later.
            if (this.library == null) {
               java.nio.file.Path folder = client.field_1697.toPath().resolve("schematics").resolve("donut");
               this.library = new ArrayList<>(UnifiedSchematics.list(folder));
               this.log("library: " + this.library.size() + " files");
               if (CREATIVE) {
                  server.execute(() -> server.method_3734().method_44252(server.method_3739(), "gamemode creative @a"));
                  AutoBuilderSettings settings = new AutoBuilderSettings();
                  settings.creativeMode = true;
                  CreativeBuildMode.configure(settings);
               } else {
                  // Survival: a stack of a few common blocks, so builds using them start and the
                  // rest show why they cannot.
                  for (String item : new String[]{"cobblestone", "spruce_planks", "sand"}) {
                     server.execute(() -> server.method_3734().method_44252(server.method_3739(), "give @a " + item + " 64"));
                  }
               }
            }
            if (this.library.isEmpty()) {
               this.finish(client);
               return;
            }
            java.nio.file.Path file = this.library.remove(0);
            this.startingFile = file.getFileName().toString();
            try {
               Schematic loaded = UnifiedSchematics.load(file);
               AutoBuilderController.get().cancel();
               AutoBuilderController.get().setSchematic(loaded);
               AutoBuilderController.get().setTransform(new PlacementTransform(client.field_1724.method_24515().method_10069(3, 0, 3), 0, false));
            } catch (Exception failure) {
               this.log(String.format(Locale.ROOT, "%-34s does not load: %s", this.startingFile, failure.getMessage()));
               this.wait = 5;
               return;
            }
            client.method_1507(new AutoBuilderScreen(null, new ClientConfig(), () -> {}));
            this.phase = 41;
            this.wait = 20;
         }
         case 41 -> {
            Field hitsField = AutoBuilderScreen.class.getDeclaredField("hits");
            hitsField.setAccessible(true);
            boolean pressed = false;
            for (Object hit : (List<?>)hitsField.get(client.field_1755)) {
               Method id = hit.getClass().getDeclaredMethod("id");
               id.setAccessible(true);
               if ("start".equals(id.invoke(hit))) {
                  Method action = hit.getClass().getDeclaredMethod("action");
                  action.setAccessible(true);
                  ((Runnable)action.invoke(hit)).run();
                  pressed = true;
               }
            }
            if (!pressed) {
               this.log(String.format(Locale.ROOT, "%-34s Start button not clickable (disabled)", this.startingFile));
               client.method_1507(null);
               this.phase = 40;
               this.wait = 5;
               return;
            }
            this.phase = 42;
            this.wait = 40;
         }
         case 42 -> {
            AutoBuilderController builder = AutoBuilderController.get();
            this.log(String.format(Locale.ROOT, "%-34s %-9s %d/%d  %s", this.startingFile, builder.status(), builder.confirmedBlocks(), builder.totalBlocks(), builder.reason()));
            if (client.field_1755 instanceof AutoBuilderScreen) {
               Field message = AutoBuilderScreen.class.getDeclaredField("message");
               message.setAccessible(true);
               this.log(String.format(Locale.ROOT, "%-34s screen says: %s", "", message.get(client.field_1755)));
            }
            builder.cancel();
            client.method_1507(null);
            this.phase = 40;
            this.wait = 10;
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
            // The client's own menu instance, which this build swaps for the redesigned one.
            Object menu = krypton.getClass().getField("clickGui").get(krypton);
            this.log("menu screen: " + menu.getClass().getName());
            client.method_1507((net.minecraft.class_437)menu);
            this.phase = 25;
            this.wait = 60;
         }
         case 25 -> {
            class_318.method_22690(client.field_1697, "rexsmoke-menu.png", client.method_1522(), 1, message -> {});
            // a module with plenty of settings, picked as a click on its card would
            Object krypton = Class.forName("dev.krypton.client.KryptonClient").getField("instance").get(null);
            Object manager = krypton.getClass().getField("moduleManager").get(krypton);
            Object pick = null;
            int most = -1;
            for (Object module : (List<?>)manager.getClass().getMethod("all").invoke(manager)) {
               int count = ((List<?>)module.getClass().getMethod("getSettings").invoke(module)).size();
               if (count > most) {
                  most = count;
                  pick = module;
               }
            }
            if (client.field_1755 != null && client.field_1755.getClass().getSimpleName().equals("OneMenuScreen")) {
               Method select = client.field_1755.getClass().getDeclaredMethod("select", Class.forName("dev.krypton.core.module.Module"));
               select.setAccessible(true);
               select.invoke(client.field_1755, pick);
               Field view = client.field_1755.getClass().getDeclaredField("view");
               view.setAccessible(true);
               view.set(client.field_1755, String.valueOf(pick.getClass().getMethod("getCategory").invoke(pick)));
            }
            this.phase = 26;
            this.wait = 40;
         }
         case 26 -> {
            class_318.method_22690(client.field_1697, "rexsmoke-menu-settings.png", client.method_1522(), 1, message -> {});
            net.minecraft.class_437 screen = client.field_1755;
            if (screen != null && screen.getClass().getSimpleName().equals("OneMenuScreen")) {
               // Real clicks, as a player makes them: the first card's switch, then its star, in the
               // view the last frame drew (the click areas are rebuilt every frame).
               Method shown = screen.getClass().getDeclaredMethod("shown");
               shown.setAccessible(true);
               // A card whose module switches freely (Auto Build, for one, will not turn on with no build).
               List<?> cards = (List<?>)shown.invoke(screen);
               Object first = cards.get(0);
               int index = 0;
               for (int i = 0; i < Math.min(8, cards.size()); i++) {
                  Object module = cards.get(i);
                  boolean was = (Boolean)module.getClass().getMethod("isEnabled").invoke(module);
                  module.getClass().getMethod("toggle").invoke(module);
                  boolean moved = (Boolean)module.getClass().getMethod("isEnabled").invoke(module) != was;
                  if (moved) {
                     module.getClass().getMethod("toggle").invoke(module);
                     first = module;
                     index = i;
                     break;
                  }
               }
               Method enabled = first.getClass().getMethod("isEnabled");
               double sx = 200 + 16 + index % 2 * 180 + 172 - 24;
               double sy = 52 + index / 2 * 60 + 15;
               boolean before = (Boolean)enabled.invoke(first);
               this.clickMenu(client, screen, sx, sy);
               boolean after = (Boolean)enabled.invoke(first);
               this.clickMenu(client, screen, sx, sy); // and back
               this.log(String.format(Locale.ROOT, "menu click on the switch of %s: %s -> %s, back to %s",
                  first.getClass().getMethod("getName").invoke(first), before, after, enabled.invoke(first)));
               this.clickMenu(client, screen, 200 + 16 + 172 - 14, 52 + 40);
               Method favorite = screen.getClass().getDeclaredMethod("favorite", Class.forName("dev.krypton.core.module.Module"));
               favorite.setAccessible(true);
               this.log("menu click on the star: favorite now " + favorite.invoke(null, first));
               this.clickMenu(client, screen, 200 + 16 + 172 - 14, 52 + 40); // and back
               // typing anywhere searches
               for (char c : "esp".toCharArray()) {
                  screen.method_25400(new net.minecraft.class_11905(c, 0));
               }
               Field query = screen.getClass().getDeclaredField("query");
               query.setAccessible(true);
               this.log("typed \"esp\": query is \"" + query.get(screen) + "\", " + ((List<?>)shown.invoke(screen)).size() + " modules shown");
            }
            this.phase = 21;
            this.wait = 40;
         }
         case 21 -> {
            this.log("fps in the world (module menu open): " + client.method_47599());
            class_318.method_22690(client.field_1697, "rexsmoke-menu-search.png", client.method_1522(), 1, message -> {});
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
            LagSim.schematic = this.schematic;
            LagSim.running = true;
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
            LagSim.running = false;
            if (this.verified == null) {
               server.execute(() -> this.verified = this.verify(server.method_30002()));
               return;
            }
            if (FARM_SECONDS > 0 && !this.farmed && builder.status() == AutoBuilderController.Status.COMPLETED) {
               // A farm check: step away (a player picks up the drops), let plants grow fast, then
               // count what reached the farm's containers and what is lying around.
               this.farmed = true;
               this.farmWait = FARM_SECONDS * 20;
               server.execute(() -> {
                  server.method_3734().method_44252(server.method_3739(), "tp @a -30 -59 -30");
                  server.method_3734().method_44252(server.method_3739(), "gamerule random_tick_speed " + FARM_TICK_SPEED);
                  server.method_3734().method_44252(server.method_3739(), "gamerule randomTickSpeed " + FARM_TICK_SPEED);
               });
               this.phase = 50;
               return;
            }
            this.farmed = false;
            int[] result = this.verified;
            this.verified = null;
            double seconds = this.ticks / 20.0;
            Object current = this.modes.get(this.mode);
            this.log(String.format(Locale.ROOT, "%-9s %-9s %6.1fs  %5.1f placed/s  placements sent %s  server: %d/%d match (%d exact), %d wrong  | %s",
               current == null ? "default" : current, builder.status(), seconds, result[1] / seconds, sent(builder), result[1],
               this.schematic.blocks().size(), result[0], result[2], builder.reason()));
            if (LagSim.active()) {
               this.log("  lag: " + LagSim.describe());
               LagSim.rollbacks = 0;
               LagSim.setbacks = 0;
            }
            try {
               this.log("  " + AutoBuilderController.class.getMethod("tickReport").invoke(builder));
            } catch (ReflectiveOperationException none) {
            }
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
            // A moment later, so the frame shows the HUD after the build's last change.
            this.pendingShot = name;
            this.pendingShotAt = 4;
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
         case 50 -> {
            if (--this.farmWait > 0) {
               return;
            }
            server.execute(() -> {
               this.log(this.farmReport(server.method_30002()));
               server.method_3734().method_44252(server.method_3739(), "gamerule random_tick_speed 3");
               server.method_3734().method_44252(server.method_3739(), "gamerule randomTickSpeed 3");
               server.method_3734().method_44252(server.method_3739(), "tp @a " + (ORIGIN.method_10263() - 2) + " -59 " + (ORIGIN.method_10260() - 2));
            });
            this.phase = 3;
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

   /** A left click at a point of the menu's own 900x540 canvas, through the screen's real input path. */
   private void clickMenu(class_310 client, net.minecraft.class_437 screen, double vx, double vy) throws Exception {
      Field scale = screen.getClass().getDeclaredField("scale");
      Field ox = screen.getClass().getDeclaredField("originX");
      Field oy = screen.getClass().getDeclaredField("originY");
      scale.setAccessible(true);
      ox.setAccessible(true);
      oy.setAccessible(true);
      double x = (Float)ox.get(screen) + vx * (Float)scale.get(screen);
      double y = (Float)oy.get(screen) + vy * (Float)scale.get(screen);
      // The hit areas are rebuilt every frame; draw one first so they exist at this point.
      screen.method_25402(new net.minecraft.class_11909(x, y, new net.minecraft.class_11910(0, 0)), false);
   }

   /** Items in the containers inside the build, and items lying loose in and around it. */
   private String farmReport(class_3218 world) {
      class_2338 size = this.schematic.dimensions();
      java.util.Map<String, Integer> stored = new java.util.TreeMap<>();
      for (class_2338 pos : class_2338.method_10097(ORIGIN, ORIGIN.method_10081(size))) {
         if (world.method_8321(pos) instanceof net.minecraft.class_1263 inventory) {
            for (int i = 0; i < inventory.method_5439(); i++) {
               net.minecraft.class_1799 stack = inventory.method_5438(i);
               if (!stack.method_7960()) {
                  stored.merge(stack.method_7909().toString(), stack.method_7947(), Integer::sum);
               }
            }
         }
      }
      java.util.Map<String, Integer> loose = new java.util.TreeMap<>();
      net.minecraft.class_238 box = new net.minecraft.class_238(ORIGIN.method_10263() - 4, -64, ORIGIN.method_10260() - 4,
         ORIGIN.method_10263() + size.method_10263() + 4, -60 + size.method_10264() + 4, ORIGIN.method_10260() + size.method_10260() + 4);
      for (net.minecraft.class_1542 item : world.method_18023(net.minecraft.class_5575.method_31795(net.minecraft.class_1542.class), box, e -> true)) {
         loose.merge(item.method_6983().method_7909().toString(), item.method_6983().method_7947(), Integer::sum);
      }
      return "  farm: after " + FARM_SECONDS + " s at random tick speed " + FARM_TICK_SPEED + ": in containers " + stored + ", lying loose " + loose;
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
         if (!desired.method_26227().method_15769() && !desired.method_26227().method_15771() && desired.method_26204() instanceof net.minecraft.class_2404) {
            // Flowing water or lava: never placed, it runs there on its own, given time.
            matching++;
            continue;
         }
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
