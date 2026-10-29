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
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;

public final class UnifiedSchematics {
   private static final String[] DEFAULTS = new String[]{
      "starter-house.schem", "sample.schem", "sugar-cane-farm.schem", "kelp-blue-behemoth.litematic", "auto-regear-hendog.litematic", "kelp-compact-auto.schem",
      // Farms that need no tilled soil, made by test/make_farms.py
      "sweet-berry-farm.schem", "cactus-farm.schem", "nether-wart-farm.schem"
   };

   private UnifiedSchematics() {
   }

   private static void install(Path var0, String var1) throws IOException {
      Path var2 = var0.resolve(var1);
      if (!Files.exists(var2, LinkOption.NOFOLLOW_LINKS)) {
         try (InputStream var3 = UnifiedSchematics.class.getResourceAsStream("/assets/rex/schematics/" + var1)) {
            if (var3 == null) {
               throw new IOException("Missing bundled schematic: " + var1);
            }

            try {
               Files.copy(var3, var2);
            } catch (FileAlreadyExistsException var7) {
            }
         }
      }
   }

   public static boolean supported(Path var0) {
      String var1 = var0.getFileName().toString().toLowerCase(Locale.ROOT);
      return Files.isRegularFile(var0) && (var1.endsWith(".litematic") || var1.endsWith(".schem"));
   }

   public static List<Path> list(Path var0) {
      try {
         BundledSchematics.createDirectories(var0, new FileAttribute[0]);
         Path var1 = var0.getParent();
         if (var1 == null) {
            throw new IOException("Schematic folder has no parent.");
         } else {
            for (String var5 : DEFAULTS) {
               install(var1, var5);
            }

            List<Path> var13;
            try (
               Stream<Path> var11 = Files.list(var0);
               Stream<Path> var12 = Files.list(var1);
            ) {
               var13 = Stream.<Path>concat(var11, var12)
                  .filter(UnifiedSchematics::supported)
                  .map(var0x -> var0x.toAbsolutePath().normalize())
                  .distinct()
                  .sorted(Comparator.<Path, String>comparing(var0x -> var0x.getFileName().toString().toLowerCase(Locale.ROOT)).thenComparing(Path::toString))
                  .toList();
            }

            return var13;
         }
      } catch (IOException var10) {
         throw new CompletionException(var10);
      }
   }

   public static Schematic load(Path var0) throws IOException {
      String var1 = var0.getFileName().toString().toLowerCase(Locale.ROOT);
      if (var1.endsWith(".litematic")) {
         return LitematicLoader.load(var0);
      } else if (!var1.endsWith(".schem")) {
         throw new IOException("Choose a .schem or .litematic file.");
      } else {
         byte[] var2 = SpongeToLitematic.convert(var0);
         Path var3 = Files.createTempFile("donut-rex-import-", ".litematic");

         Schematic var4;
         try {
            Files.write(var3, var2);
            var4 = LitematicLoader.load(var3);
         } finally {
            Files.deleteIfExists(var3);
         }

         return var4;
      }
   }
}
