package dev.donut.client.autobuild;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;

public final class UnifiedSchematics {
   private static final String[] DEFAULTS = new String[]{
      "starter-house.schem", "sample.schem", "kelp-blue-behemoth.litematic", "auto-regear-hendog.litematic", "kelp-compact-auto.schem",
      // DonutSMP farm and spawner bases, made by test/make_donut.py
      "donut-cactus-farm-small.schem", "donut-spawner-base-small.schem", "donut-spawner-base-big.schem",
      // Showcase builds, made by test/make_builds.py
      "castle-gate.schem", "sky-bridge.schem", "pixel-art.schem", "garden.schem", "storage-room.schem", "modern-house.schem"
   };

   /** Simple farms bundled up to one.22, with the SHA-256 of the copy that was installed. */
   private static final String[][] RETIRED = new String[][]{
      {"sugar-cane-farm.schem", "09a2433a9f0e112e26e6c4db1d39dc559dca81397e2e4c99b50549059d43e839"},
      {"sweet-berry-farm.schem", "aee80a3d207aa1bc00aac017d5d67c255b392aea07836448427c2e1b586bb77a"},
      {"cactus-farm.schem", "d3cb079be615054202275e95ce2722695e36c74be145258c61886bbefedebda7"},
      {"nether-wart-farm.schem", "07638c88e4c9e32da17bcbf4a925a94ee155ff1bbe92e19c19ef349eebcf550b"}
   };

   private UnifiedSchematics() {
   }

   /** Removes a retired farm only if it is still the untouched copy this mod installed; edited ones stay. */
   private static void retire(Path var0, String var1, String var2) {
      Path var3 = var0.resolve(var1);
      try {
         if (Files.isRegularFile(var3, LinkOption.NOFOLLOW_LINKS) && Files.size(var3) < 65536) {
            byte[] var4 = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(var3));
            if (HexFormat.of().formatHex(var4).equals(var2)) {
               Files.delete(var3);
            }
         }
      } catch (IOException | NoSuchAlgorithmException var5) {
      }
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

            for (String[] var14 : RETIRED) {
               retire(var1, var14[0], var14[1]);
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
