package rexsmoke;

import dev.donut.client.autobuild.AutoBuilderController;
import dev.donut.client.autobuild.BuildTempo;
import dev.donut.client.autobuild.CreativeBuildMode;
import dev.donut.client.autobuild.MaterialTracker;
import dev.donut.client.autobuild.PlacementTransform;
import dev.donut.client.autobuild.Schematic;
import dev.donut.client.autobuild.UnifiedSchematics;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Test driver, active only with -Drexsmoke.schem=FILE. Runs Auto Build in a real singleplayer world,
 * then compares every blueprint block with the integrated server's world.
 *
 *   -Drexsmoke.presets=SAFE,BALANCED,FAST,TURBO   one build per preset (default FAST)
 *   -Drexsmoke.creative=true                      creative with REX Creative Mode instead of survival
 *   -Drexsmoke.maxSeconds=N                       time cap per build (default 600)
 *   -Drexsmoke.origin=x,y,z                       where the blueprint goes (default 8,-60,8)
 *   -Drexsmoke.paste=true                         do not build; paste the blueprint server-side
 *   -Drexsmoke.farmMinutes=N                      afterwards run the farm for N game minutes at high speed
 *                                                 and count what reaches the chests
 */
public final class Smoke {
   private static final String FILE = System.getProperty("rexsmoke.schem");
   private static final boolean CREATIVE = Boolean.getBoolean("rexsmoke.creative");
   private static final boolean PASTE = Boolean.getBoolean("rexsmoke.paste");
   private static final int MAX_TICKS = Integer.getInteger("rexsmoke.maxSeconds", 600) * 20;
   private static final int FARM_MINUTES = Integer.getInteger("rexsmoke.farmMinutes", 0);
   private static final int RANDOM_TICK_SPEED = Integer.getInteger("rexsmoke.randomTickSpeed", 60);
   private static final List<String> PRESETS = List.of(System.getProperty("rexsmoke.presets", "FAST").split(","));
   private static BlockPos origin;
   private static Schematic schematic;
   private static PlacementTransform transform;
   private static int stage;
   private static int wait;
   private static int preset;
   private static int ticks;
   private static int resumes;
   private static int lastConfirmed;
   private static int lastChange;
   private static int samePause;
   private static String lastPause = "";
   private static final Map<String, Integer> reasons = new LinkedHashMap<>();
   private static final List<String> pauses = new ArrayList<>();
   private static long started;
   private static boolean failed;

   private Smoke() {
   }

   public static void tick(MinecraftClient client) {
      if (FILE == null || failed) {
         return;
      }

      try {
         step(client);
      } catch (Throwable error) {
         failed = true;
         error.printStackTrace();
         log(client, "ERROR " + error);
         client.scheduleStop();
      }
   }

   private static void step(MinecraftClient client) throws Exception {
      if (client.player == null || client.world == null || client.getServer() == null) {
         return;
      }

      if (wait > 0) {
         wait--;
         return;
      }

      AutoBuilderController builder = AutoBuilderController.get();
      switch (stage) {
         case 0 -> {
            String[] at = System.getProperty("rexsmoke.origin", "8,-60,8").split(",");
            origin = new BlockPos(Integer.parseInt(at[0].trim()), Integer.parseInt(at[1].trim()), Integer.parseInt(at[2].trim()));
            schematic = UnifiedSchematics.load(Path.of(FILE));
            transform = new PlacementTransform(origin, 0, false);
            System.out.println("[rexsmoke] loaded " + schematic.name() + " " + schematic.dimensions() + " blocks=" + schematic.blocks().size());
            wait = 100;
            stage = 1;
         }
         case 1 -> {
            BlockPos d = schematic.dimensions();
            command(client, "gamerule spawn_mobs false");
            command(client, "time set noon");
            command(client, "weather clear");
            for (int y = origin.getY() + d.getY() + 2; y >= origin.getY(); y -= 8) {
               int low = Math.max(origin.getY(), y - 7);
               command(client, "fill " + (origin.getX() - 8) + " " + low + " " + (origin.getZ() - 8) + " " + (origin.getX() + d.getX() + 8) + " " + y + " " + (origin.getZ() + d.getZ() + 8) + " air");
            }

            command(client, "kill @e[type=item]");
            command(client, "clear Smoke");
            command(client, "gamemode " + (CREATIVE ? "creative" : "survival") + " Smoke");
            command(client, "tp Smoke " + (origin.getX() - 2.5) + " " + origin.getY() + " " + (origin.getZ() - 2.5) + " -45 20");
            if (PASTE) {
               paste(client);
               wait = 40;
               stage = 5;
               return;
            }

            wait = 40;
            stage = 2;
         }
         case 2 -> {
            client.setScreen(null);
            if (CreativeBuildMode.settings() == null) {
               throw new IllegalStateException("builder settings not bound");
            }

            CreativeBuildMode.settings().creativeMode = CREATIVE;
            CreativeBuildMode.settings().tempo = BuildTempo.parse(PRESETS.get(preset)).name();
            builder.setSchematic(schematic);
            builder.setTransform(transform);
            if (!CREATIVE) {
               supply(client, null);
               wait = 10;
            }

            stage = 3;
         }
         case 3 -> {
            builder.start();
            if (!builder.isRunning()) {
               throw new IllegalStateException("did not start: " + builder.reason());
            }

            reasons.clear();
            pauses.clear();
            ticks = 0;
            resumes = 0;
            samePause = 0;
            lastPause = "";
            lastConfirmed = 0;
            lastChange = 0;
            started = System.nanoTime();
            stage = 4;
         }
         case 4 -> {
            ticks++;
            if (Boolean.getBoolean("rexsmoke.rot")) {
               ServerPlayerEntity sp = client.getServer().getPlayerManager().getPlayer("Smoke");
               System.out.println("[rot] tick " + ticks + " client yaw=" + client.player.getYaw() + " pitch=" + client.player.getPitch() + " server yaw=" + sp.getYaw() + " pitch=" + sp.getPitch() + " | " + builder.reason());
            }
            String reason = builder.reason();
            reasons.merge(reason.replaceAll("\\(.*?/s\\)", "").replaceAll("-?\\d+", "#").trim(), 1, Integer::sum);
            if (builder.confirmedBlocks() != lastConfirmed) {
               lastConfirmed = builder.confirmedBlocks();
               lastChange = ticks;
            }

            if (ticks % 200 == 0) {
               System.out.println("[rexsmoke] t=" + ticks / 20 + "s " + builder.status() + " " + builder.confirmedBlocks() + "/" + builder.totalBlocks() + " sent=" + builder.placementsSent() + " | " + reason);
            }

            boolean done = builder.status() == AutoBuilderController.Status.COMPLETED;
            boolean stalled = ticks - lastChange > 1200;
            if (builder.status() == AutoBuilderController.Status.PAUSED && !done) {
               pauses.add(ticks / 20 + "s: " + reason);
               samePause = reason.equals(lastPause) ? samePause + 1 : 0;
               lastPause = reason;
               if (samePause >= 3 || resumes >= 400) {
                  stalled = true;
               } else {
                  resumes++;
                  if (!CREATIVE) {
                     supply(client, reason);
                  }

                  client.setScreen(null);
                  wait = 10;
                  stage = 41;
                  return;
               }
            }

            if (done || stalled || ticks >= MAX_TICKS) {
               report(client, builder, done ? "COMPLETED" : (stalled ? "STALLED" : "TIMEOUT"));
               screenshot(client);
               builder.cancel();
               wait = 20;
               preset++;
               stage = preset < PRESETS.size() ? 1 : 5;
            }
         }
         case 41 -> {
            builder.resume();
            stage = 4;
         }
         case 5 -> {
            if (FARM_MINUTES <= 0) {
               stage = 9;
               return;
            }

            command(client, "tp Smoke " + (origin.getX() + schematic.dimensions().getX() / 2.0) + " " + (origin.getY() + schematic.dimensions().getY() + 1) + " " + (origin.getZ() + schematic.dimensions().getZ() / 2.0));
            command(client, "gamemode spectator Smoke");
            command(client, "gamerule random_tick_speed " + RANDOM_TICK_SPEED);
            command(client, "tick sprint " + FARM_MINUTES * 1200);
            wait = 20;
            stage = 6;
         }
         case 6 -> {
            if (client.getServer().getTickManager().isSprinting()) {
               wait = 20;
               return;
            }

            farmReport(client);
            command(client, "gamerule random_tick_speed 3");
            screenshot(client);
            wait = 20;
            stage = 9;
         }
         default -> {
            System.out.println("[rexsmoke] finished");
            failed = true;
            client.scheduleStop();
         }
      }
   }

   private static void command(MinecraftClient client, String command) {
      MinecraftServer server = client.getServer();
      server.submit(() -> server.getCommandManager().parseAndExecute(server.getCommandSource().withSilent(), command)).join();
   }

   /** Survival supplies: buckets, scaffolding, and as much of the remaining material as fits. */
   private static void supply(MinecraftClient client, String pauseReason) {
      MaterialTracker.Snapshot snapshot = MaterialTracker.snapshot(client, schematic, transform);
      List<MaterialTracker.Row> rows = new ArrayList<>(snapshot.rows());
      rows.removeIf(row -> row.remaining() <= 0 || row.item() == Items.AIR);
      rows.sort(Comparator.comparingInt((MaterialTracker.Row row) -> pauseReason != null && pauseReason.contains("Missing " + row.name()) ? 0 : 1)
         .thenComparingInt(row -> -row.remaining()));
      MinecraftServer server = client.getServer();
      server.submit(() -> {
         ServerPlayerEntity player = server.getPlayerManager().getPlayer("Smoke");
         player.getInventory().clear();
         give(player, Items.WATER_BUCKET, 1);
         give(player, Items.WATER_BUCKET, 1);
         give(player, Items.WATER_BUCKET, 1);
         give(player, Items.DIRT, 64);
         int slots = 32;
         for (MaterialTracker.Row row : rows) {
            int max = row.item().getMaxCount();
            int want = Math.min(row.remaining(), max * (rows.indexOf(row) == 0 ? 6 : 3));
            while (want > 0 && slots > 0) {
               int count = Math.min(want, max);
               give(player, row.item(), count);
               want -= count;
               slots--;
            }
         }

         player.playerScreenHandler.sendContentUpdates();
      }).join();
   }

   private static void give(ServerPlayerEntity player, Item item, int count) {
      player.getInventory().insertStack(new ItemStack(item, count));
   }

   private static void paste(MinecraftClient client) {
      MinecraftServer server = client.getServer();
      server.submit(() -> {
         ServerWorld world = server.getOverworld();
         for (int pass = 0; pass < 3; pass++) {
            for (Schematic.Block block : schematic.blocks()) {
               BlockState state = transform.apply(block.state());
               boolean water = state.isOf(Blocks.WATER);
               boolean plant = state.isOf(Blocks.KELP) || state.isOf(Blocks.KELP_PLANT);
               int wanted = water ? 1 : (plant ? 2 : 0);
               if (wanted == pass && !MaterialTracker.isDerived(state)) {
                  world.setBlockState(transform.apply(block.pos()), state, 3);
               }
            }
         }
      }).join();
      log(client, "PASTED " + schematic.name() + " (" + schematic.blocks().size() + " blocks)");
   }

   private static void report(MinecraftClient client, AutoBuilderController builder, String outcome) {
      MinecraftServer server = client.getServer();
      int[] counts = new int[4];
      List<String> wrong = new ArrayList<>();
      server.submit(() -> {
         ServerWorld world = server.getOverworld();
         for (Schematic.Block block : schematic.blocks()) {
            BlockState desired = transform.apply(block.state());
            if (MaterialTracker.isDerived(desired)) {
               continue;
            }

            BlockPos pos = transform.apply(block.pos());
            BlockState actual = world.getBlockState(pos);
            counts[0]++;
            if (actual.equals(desired)) {
               counts[1]++;
               counts[2]++;
            } else if (MaterialTracker.matches(desired, actual)) {
               counts[2]++;
            } else {
               counts[3]++;
               if (wrong.size() < 12) {
                  wrong.add("      " + pos.toShortString() + " want " + desired + " have " + actual);
               }
            }
         }
      }).join();
      double seconds = ticks / 20.0;
      StringBuilder text = new StringBuilder();
      text.append(String.format(Locale.ROOT, "%s %s %s  %.1fs game time (%.1fs real)  %.1f placed/s  placements sent %d  confirmed %d/%d  scaffolding %d%n",
         PRESETS.get(preset), CREATIVE ? "creative" : "survival", outcome, seconds, (System.nanoTime() - started) / 1e9, builder.placementsSent() / Math.max(1.0, seconds),
         builder.placementsSent(), builder.confirmedBlocks(), builder.totalBlocks(), builder.supportsPlaced()));
      text.append(String.format(Locale.ROOT, "   server: %d/%d match (%d exact), %d wrong or missing%n", counts[2], counts[0], counts[1], counts[3]));
      text.append("   last status: ").append(builder.reason()).append('\n');
      reasons.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(8).forEach(entry ->
         text.append(String.format(Locale.ROOT, "   %5.1f%%  %s%n", entry.getValue() * 100.0 / Math.max(1, ticks), entry.getKey())));
      if (!pauses.isEmpty()) {
         text.append("   pauses (").append(pauses.size()).append("):\n");
         pauses.stream().limit(10).forEach(pause -> text.append("      ").append(pause).append('\n'));
      }

      wrong.forEach(line -> text.append(line).append('\n'));
      log(client, text.toString().stripTrailing());
   }

   private static void farmReport(MinecraftClient client) {
      MinecraftServer server = client.getServer();
      BlockPos d = schematic.dimensions();
      Map<String, Integer> stored = new LinkedHashMap<>();
      int[] loose = new int[2];
      server.submit(() -> {
         ServerWorld world = server.getOverworld();
         for (BlockPos pos : BlockPos.iterate(origin, origin.add(d.getX() - 1, d.getY() - 1, d.getZ() - 1))) {
            BlockEntity entity = world.getBlockEntity(pos);
            if (entity instanceof Inventory inventory) {
               for (int slot = 0; slot < inventory.size(); slot++) {
                  ItemStack stack = inventory.getStack(slot);
                  if (!stack.isEmpty()) {
                     String where = world.getBlockState(pos).getBlock().getName().getString();
                     stored.merge(where + ": " + stack.getItem().getName().getString(), stack.getCount(), Integer::sum);
                  }
               }
            }

            BlockState state = world.getBlockState(pos);
            if (state.isOf(Blocks.KELP) || state.isOf(Blocks.KELP_PLANT)) {
               loose[1]++;
            }
         }

         for (Entity entity : world.iterateEntities()) {
            if (entity instanceof ItemEntity item) {
               loose[0] += item.getStack().getCount();
            }
         }
      }).join();
      int total = stored.values().stream().mapToInt(Integer::intValue).sum();
      double hours = FARM_MINUTES / 60.0 * RANDOM_TICK_SPEED / 3.0;
      StringBuilder text = new StringBuilder();
      text.append(String.format(Locale.ROOT, "FARM %d game minutes at random tick speed %d (= %.1f hours of normal play)%n", FARM_MINUTES, RANDOM_TICK_SPEED, hours));
      text.append(String.format(Locale.ROOT, "   collected %d items = %.0f per hour of normal play; %d items lying loose; %d kelp blocks standing%n", total, total / hours, loose[0], loose[1]));
      stored.forEach((key, value) -> text.append("   ").append(value).append("  ").append(key).append('\n'));
      log(client, text.toString().stripTrailing());
   }

   private static void screenshot(MinecraftClient client) {
      ScreenshotRecorder.saveScreenshot(client.runDirectory, client.getFramebuffer(), message -> {
      });
   }

   private static void log(MinecraftClient client, String text) {
      System.out.println("[rexsmoke] " + text);
      try {
         Files.writeString(new File(client.runDirectory, "rexsmoke-results.txt").toPath(), text + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
      } catch (Exception ignored) {
      }
   }
}
