package dev.donut.client.autobuild;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;

/** The schematic library: bundled blueprints plus whatever the player drops into the folder. */
public final class UnifiedSchematics {
   private static final String[] DEFAULTS = new String[]{
      "kelp-mega-farm.schem",
      "kelp-farm.schem",
      "cobblestone-farm-8-module.litematic",
      "starter-house.schem",
      "kelp-blue-behemoth.litematic",
      "auto-regear-hendog.litematic"
   };
   /** Small demo farms shipped by earlier versions, by file name and exact size; removed when still untouched. */
   private static final Map<String, Long> RETIRED = Map.of("kelp-compact-auto.schem", 440L, "sugar-cane-farm.schem", 230L, "sample.schem", 695L);

   private UnifiedSchematics() {
   }

   private static void install(Path folder, String name) throws IOException {
      Path file = folder.resolve(name);
      if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
         try (InputStream in = UnifiedSchematics.class.getResourceAsStream("/assets/rex/schematics/" + name)) {
            if (in == null) {
               throw new IOException("Missing bundled schematic: " + name);
            }

            try {
               Files.copy(in, file);
            } catch (FileAlreadyExistsException ignored) {
            }
         }
      }
   }

   private static void retire(Path folder) {
      for (Map.Entry<String, Long> old : RETIRED.entrySet()) {
         Path file = folder.resolve(old.getKey());
         try {
            if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && Files.size(file) == old.getValue()) {
               Files.delete(file);
            }
         } catch (IOException ignored) {
         }
      }
   }

   public static boolean supported(Path file) {
      String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
      return Files.isRegularFile(file) && (name.endsWith(".litematic") || name.endsWith(".schem"));
   }

   public static List<Path> list(Path folder) {
      try {
         BundledSchematics.createDirectories(folder, new FileAttribute[0]);
         Path parent = folder.getParent();
         if (parent == null) {
            throw new IOException("Schematic folder has no parent.");
         }

         retire(parent);
         for (String name : DEFAULTS) {
            install(parent, name);
         }

         try (Stream<Path> own = Files.list(folder); Stream<Path> shared = Files.list(parent)) {
            return Stream.concat(own, shared)
               .filter(UnifiedSchematics::supported)
               .map(file -> file.toAbsolutePath().normalize())
               .distinct()
               .sorted(Comparator.<Path, String>comparing(file -> file.getFileName().toString().toLowerCase(Locale.ROOT)).thenComparing(Path::toString))
               .toList();
         }
      } catch (IOException error) {
         throw new CompletionException(error);
      }
   }

   public static Schematic load(Path file) throws IOException {
      String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
      if (name.endsWith(".litematic")) {
         return LitematicLoader.load(file);
      } else if (!name.endsWith(".schem")) {
         throw new IOException("Choose a .schem or .litematic file.");
      } else {
         byte[] converted = SpongeToLitematic.convert(file);
         Path temp = Files.createTempFile("donut-rex-import-", ".litematic");

         try {
            Files.write(temp, converted);
            return LitematicLoader.load(temp);
         } finally {
            Files.deleteIfExists(temp);
         }
      }
   }
}
