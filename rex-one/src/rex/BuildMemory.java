package rex;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.donut.client.autobuild.AutoBuilderController;
import dev.donut.client.autobuild.PlacementTransform;
import dev.donut.client.autobuild.Schematic;
import dev.donut.client.autobuild.UnifiedSchematics;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_2338;
import net.minecraft.class_310;
import net.minecraft.class_642;

/**
 * Remembers which schematic was loaded, from which file, and where it was placed, per server, in
 * config/rex-one-build.json. Joining that server again loads it back, ready but not started;
 * the builder's own scan then finds the blocks that are already done.
 */
public final class BuildMemory {
   private static Path loadedFile;
   private static Schematic loadedSchematic;
   private static PlacementTransform savedTransform;
   private static boolean restoreChecked;

   private BuildMemory() {
   }

   private static Path file() {
      return FabricLoader.getInstance().getConfigDir().resolve("rex-one-build.json");
   }

   private static String server(class_310 client) {
      class_642 entry = client.method_1558();
      if (client.method_1576() != null) {
         return "singleplayer";
      }
      return entry == null ? "unknown" : entry.field_3761;
   }

   /** Called by the builder screen when a schematic file finishes loading. */
   public static void remember(Path path, Schematic schematic) {
      loadedFile = path.toAbsolutePath();
      loadedSchematic = schematic;
      savedTransform = null;
   }

   /** Once per client tick, from Build Tempo's hook. */
   static void tick(AutoBuilderController builder) {
      class_310 client = class_310.method_1551();
      if (client.field_1687 == null) {
         restoreChecked = false;
         return;
      }
      if (!restoreChecked) {
         restoreChecked = true;
         if (builder.schematic() == null) {
            restore(client, builder);
         }
      }
      if (loadedSchematic != null && builder.schematic() == loadedSchematic && !builder.transform().equals(savedTransform)) {
         save(client, builder.transform());
      }
   }

   private static void save(class_310 client, PlacementTransform transform) {
      savedTransform = transform;
      JsonObject json = new JsonObject();
      json.addProperty("file", loadedFile.toString());
      json.addProperty("server", server(client));
      json.addProperty("x", transform.origin().method_10263());
      json.addProperty("y", transform.origin().method_10264());
      json.addProperty("z", transform.origin().method_10260());
      json.addProperty("quarterTurns", transform.quarterTurns());
      json.addProperty("mirrorX", transform.mirrorX());
      try {
         Files.writeString(file(), json.toString(), StandardCharsets.UTF_8);
      } catch (Exception failure) {
         System.err.println("[ONE] could not save the build position: " + failure);
      }
   }

   private static void restore(class_310 client, AutoBuilderController builder) {
      try {
         Path saved = file();
         if (!Files.isRegularFile(saved)) {
            return;
         }
         JsonObject json = JsonParser.parseString(Files.readString(saved, StandardCharsets.UTF_8)).getAsJsonObject();
         if (!server(client).equals(json.get("server").getAsString())) {
            return;
         }
         Path path = Path.of(json.get("file").getAsString());
         PlacementTransform transform = new PlacementTransform(
            new class_2338(json.get("x").getAsInt(), json.get("y").getAsInt(), json.get("z").getAsInt()),
            json.get("quarterTurns").getAsInt(),
            json.get("mirrorX").getAsBoolean()
         );
         if (!Files.isRegularFile(path)) {
            return;
         }
         CompletableFuture.supplyAsync(() -> {
            try {
               return UnifiedSchematics.load(path);
            } catch (Exception failure) {
               throw new RuntimeException(failure);
            }
         }).whenComplete((schematic, error) -> client.execute(() -> {
            if (error != null || schematic == null || builder.schematic() != null) {
               return;
            }
            builder.setSchematic(schematic);
            builder.setTransform(transform);
            remember(path, schematic);
            savedTransform = transform;
            Game.message("Auto Build: restored " + schematic.name() + " at " + transform.origin().method_10263() + ", "
               + transform.origin().method_10264() + ", " + transform.origin().method_10260() + ". Open the builder and press Start to continue.");
         }));
      } catch (Exception failure) {
         System.err.println("[ONE] could not restore the build: " + failure);
      }
   }
}
