package dev.donut.client.autobuild;

import dev.donut.client.autobuild.Schematic.Block;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_155;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_2487;
import net.minecraft.class_2499;
import net.minecraft.class_2505;
import net.minecraft.class_2507;
import net.minecraft.class_2520;
import net.minecraft.class_2680;
import net.minecraft.class_2769;
import net.minecraft.class_2960;
import net.minecraft.class_7923;

@Environment(EnvType.CLIENT)
public final class LitematicLoader {
   public static final int MAX_BLOCKS = 1000000;
   public static final int MAX_VOLUME = 4000000;
   public static final long MAX_FILE_BYTES = 33554432L;
   public static final long MAX_NBT_BYTES = 134217728L;
   private static final int MAX_REGIONS = 512;
   private static final int MAX_AXIS = 8192;
   private static final int MAX_SPAN = 65536;

   private LitematicLoader() {
   }

   public static Schematic load(Path file) throws IOException {
      if (!file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".litematic")) {
         throw invalid("Choose a .litematic file");
      } else if (!Files.isRegularFile(file)) {
         throw invalid("Schematic file is missing");
      } else {
         long length = Files.size(file);
         if (length > 0L && length <= 33554432L) {
            try {
               class_2487 root;
               try (BufferedInputStream input = new BufferedInputStream(Files.newInputStream(file))) {
                  input.mark(2);
                  int first = input.read();
                  int second = input.read();
                  input.reset();
                  class_2505 account = new class_2505(134217728L, 64);
                  root = first == 31 && second == 139 ? class_2507.method_10629(input, account) : class_2507.method_10625(new DataInputStream(input), account);
               }

               return read(root, file.getFileName().toString().replaceFirst("(?i)\\.litematic$", ""));
            } catch (IOException var10) {
               throw var10;
            } catch (RuntimeException var11) {
               throw new IOException("Invalid or oversized .litematic: " + concise(var11), var11);
            }
         } else {
            throw invalid("File must be between 1 byte and 32 MiB");
         }
      }
   }

   public static Schematic read(class_2487 root, String fallbackName) throws IOException {
      if (root == null) {
         throw invalid("Schematic has no root compound");
      } else {
         int version = integer(root, "Version");
         if (version >= 4 && version <= 7) {
            int dataVersion = integer(root, "MinecraftDataVersion");
            if (dataVersion < 1451) {
               throw invalid("Pre-1.13 schematics must be converted in Litematica first");
            } else if (dataVersion > class_155.method_16673().comp_4026().comp_4038()) {
               throw invalid("Schematic was saved by a newer Minecraft version");
            } else {
               class_2487 regionTags = compound(root, "Regions");
               if (!regionTags.method_33133() && regionTags.method_10546() <= 512) {
                  Set<String> warnings = new LinkedHashSet<>();
                  if (dataVersion != class_155.method_16673().comp_4026().comp_4038()) {
                     warnings.add("Older Minecraft data: block names and properties are validated; re-save in current Litematica if conversion is needed.");
                  }

                  List<LitematicLoader.Region> regions = new ArrayList<>();
                  long totalVolume = 0L;
                  int minX = Integer.MAX_VALUE;
                  int minY = Integer.MAX_VALUE;
                  int minZ = Integer.MAX_VALUE;
                  int maxX = Integer.MIN_VALUE;
                  int maxY = Integer.MIN_VALUE;
                  int maxZ = Integer.MIN_VALUE;

                  for (String key : regionTags.method_10541().stream().sorted().toList()) {
                     class_2487 tag = compound(regionTags, key);
                     class_2338 position = position(compound(tag, "Position"));
                     class_2338 signedSize = position(compound(tag, "Size"));
                     int sx = axis(signedSize.method_10263());
                     int sy = axis(signedSize.method_10264());
                     int sz = axis(signedSize.method_10260());
                     long volume = (long)sx * sy * sz;
                     totalVolume += volume;
                     if (totalVolume > 4000000L) {
                        throw invalid("Schematic exceeds 4,000,000 region cells");
                     }

                     class_2338 min = new class_2338(
                        minimum(position.method_10263(), signedSize.method_10263()),
                        minimum(position.method_10264(), signedSize.method_10264()),
                        minimum(position.method_10260(), signedSize.method_10260())
                     );
                     minX = Math.min(minX, min.method_10263());
                     minY = Math.min(minY, min.method_10264());
                     minZ = Math.min(minZ, min.method_10260());
                     maxX = Math.max(maxX, Math.addExact(min.method_10263(), sx - 1));
                     maxY = Math.max(maxY, Math.addExact(min.method_10264(), sy - 1));
                     maxZ = Math.max(maxZ, Math.addExact(min.method_10260(), sz - 1));
                     class_2499 paletteNbt = list(tag, "BlockStatePalette");
                     if (paletteNbt.isEmpty() || paletteNbt.size() > 65536) {
                        throw invalid("Invalid palette in region " + key);
                     }

                     List<class_2680> palette = new ArrayList<>(paletteNbt.size());

                     for (class_2520 entry : paletteNbt) {
                        if (!(entry instanceof class_2487 state)) {
                           throw invalid("Non-compound palette entry in " + key);
                        }

                        palette.add(readState(state));
                     }

                     long[] packed = tag.method_10565("BlockStates").orElseThrow(() -> invalid("Missing BlockStates in " + key));
                     int bits = bitsForPalette(palette.size());
                     long needed = (volume * bits + 63L) / 64L;
                     if (packed.length != needed) {
                        throw invalid("Wrong packed BlockStates length in " + key + ": expected " + needed);
                     }

                     Map<class_2338, class_2487> entities = new HashMap<>();
                     if (tag.method_10545("TileEntities")) {
                        class_2499 tileTags = list(tag, "TileEntities");
                        if (tileTags.size() > volume) {
                           throw invalid("More block entities than region cells in " + key);
                        }

                        for (class_2520 entry : tileTags) {
                           if (!(entry instanceof class_2487 entity)) {
                              throw invalid("Invalid block entity in " + key);
                           }

                           class_2338 local = position(entity);
                           if (local.method_10263() < 0
                              || local.method_10263() >= sx
                              || local.method_10264() < 0
                              || local.method_10264() >= sy
                              || local.method_10260() < 0
                              || local.method_10260() >= sz) {
                              throw invalid("Block entity outside region " + key);
                           }

                           if (entities.put(local, entity) != null) {
                              throw invalid("Duplicate block entity in region " + key);
                           }
                        }
                     }

                     if (!entities.isEmpty()) {
                        warnings.add("Stored block-entity data is retained but cannot be restored by ordinary block placement.");
                     }

                     if (!optionalCompoundList(tag, "Entities").isEmpty()) {
                        warnings.add("Entities are not built automatically.");
                     }

                     if (!optionalCompoundList(tag, "PendingBlockTicks").isEmpty() || !optionalCompoundList(tag, "PendingFluidTicks").isEmpty()) {
                        warnings.add("Scheduled redstone and fluid ticks are not restored.");
                     }

                     regions.add(new LitematicLoader.Region(key, min, sx, sy, sz, (int)volume, palette, packed, bits, entities));
                  }

                  class_2338 dimensions = new class_2338(span(minX, maxX), span(minY, maxY), span(minZ, maxZ));
                  class_2338 minimum = new class_2338(minX, minY, minZ);
                  Map<class_2338, Block> blocks = new HashMap<>();
                  int identicalOverlaps = 0;

                  for (LitematicLoader.Region region : regions) {
                     for (int index = 0; index < region.volume; index++) {
                        int id = packedIndex(region.packed, region.bits, index);
                        if (id >= region.palette.size()) {
                           throw invalid("Out-of-range palette index in region " + region.name);
                        }

                        class_2680 state = region.palette.get(id);
                        int x = index % region.sx;
                        int z = index / region.sx % region.sz;
                        int y = index / (region.sx * region.sz);
                        class_2338 localx = new class_2338(x, y, z);
                        class_2487 entity = region.entities.get(localx);
                        if (entity != null) {
                           validateBlockEntity(state, entity, region.name);
                        }

                        if (!state.method_26215()) {
                           class_2338 normalized = region.minimum.method_10081(localx).method_10059(minimum);
                           if (entity != null) {
                              entity = entity.method_10553();
                              entity.method_10569("x", normalized.method_10263());
                              entity.method_10569("y", normalized.method_10264());
                              entity.method_10569("z", normalized.method_10260());
                           }

                           Block placed = new Block(normalized, state, entity);
                           Block previous = blocks.putIfAbsent(normalized, placed);
                           if (previous != null) {
                              if (previous.state() != state || !Objects.equals(previous.blockEntity(), entity)) {
                                 throw invalid("Conflicting overlapping regions at " + normalized.method_23854());
                              }

                              identicalOverlaps++;
                           }

                           if (blocks.size() > 1000000) {
                              throw invalid("Schematic exceeds 1,000,000 non-air blocks");
                           }
                        }
                     }
                  }

                  if (blocks.isEmpty()) {
                     throw invalid("Schematic contains no non-air blocks");
                  } else {
                     if (identicalOverlaps > 0) {
                        warnings.add("Identical overlapping blocks were counted once (" + identicalOverlaps + ").");
                     }

                     for (Block block : blocks.values()) {
                        if (MaterialTracker.isSecondary(block.state())) {
                           Block primary = blocks.get(MaterialTracker.primaryPosition(block.pos(), block.state()));
                           if (primary == null
                              || primary.state().method_26204() != block.state().method_26204()
                              || MaterialTracker.isSecondary(primary.state())) {
                              warnings.add("Some door, bed or plant secondary halves have no base in the schematic and require manual attention.");
                              break;
                           }
                        }
                     }

                     String name = root.method_68568("Metadata").method_68564("Name", fallbackName).strip();
                     if (name.isEmpty()) {
                        name = fallbackName;
                     }

                     if (name.length() > 160) {
                        name = name.substring(0, 160);
                     }

                     List<Block> ordered = new ArrayList<>(blocks.values());
                     ordered.sort(
                        Comparator.<Block>comparingInt(blockx -> blockx.pos().method_10264())
                           .thenComparingInt(blockx -> blockx.pos().method_10260())
                           .thenComparingInt(blockx -> blockx.pos().method_10263())
                     );
                     return new Schematic(name, ordered, dimensions, regions.size(), List.copyOf(warnings));
                  }
               } else {
                  throw invalid("Schematic must have 1–512 regions");
               }
            }
         } else {
            throw invalid("Unsupported Litematica version " + version + "; re-save using a current Litematica version");
         }
      }
   }

   public static int bitsForPalette(int size) {
      if (size >= 1 && size <= 65536) {
         return Math.max(2, 32 - Integer.numberOfLeadingZeros(size - 1));
      } else {
         throw new IllegalArgumentException("Palette size out of range");
      }
   }

   public static int packedIndex(long[] packed, int bits, int index) {
      if (bits >= 2 && bits <= 16 && index >= 0) {
         long start = (long)index * bits;
         int word = (int)(start >>> 6);
         int offset = (int)(start & 63L);
         if (word < packed.length && (offset + bits <= 64 || word + 1 < packed.length)) {
            long value = packed[word] >>> offset;
            if (offset + bits > 64) {
               value |= packed[word + 1] << 64 - offset;
            }

            return (int)(value & (1L << bits) - 1L);
         } else {
            throw new IllegalArgumentException("Packed block index exceeds backing array");
         }
      } else {
         throw new IllegalArgumentException("Invalid packed block index");
      }
   }

   private static class_2680 readState(class_2487 tag) throws IOException {
      String name = tag.method_10558("Name").orElseThrow(() -> invalid("Palette block has no Name"));
      class_2960 id = class_2960.method_12829(name);
      if (id != null && class_7923.field_41175.method_10250(id)) {
         class_2248 block = class_7923.field_41175.method_63535(id);
         class_2680 state = block.method_9564();
         if (tag.method_10545("Properties")) {
            class_2487 properties = compound(tag, "Properties");

            for (String key : properties.method_10541()) {
               class_2769<?> property = block.method_9595().method_11663(key);
               if (property == null) {
                  throw invalid("Unknown property " + key + " on " + name);
               }

               String value = properties.method_10558(key).orElseThrow(() -> invalid("Invalid property value " + key));
               state = setProperty(state, property, value);
            }
         }

         return state;
      } else {
         throw invalid("Unknown block: " + name);
      }
   }

   private static <T extends Comparable<T>> class_2680 setProperty(class_2680 state, class_2769<T> property, String text) throws IOException {
      T value = (T)property.method_11900(text).orElseThrow(() -> invalid("Invalid " + property.method_11899() + "=" + text));
      return state.method_11657(property, value);
   }

   private static int integer(class_2487 tag, String key) throws IOException {
      if (tag.method_10580(key) != null && tag.method_10580(key).method_10711() == 3) {
         return tag.method_10550(key).orElseThrow(() -> invalid("Missing integer " + key));
      } else {
         throw invalid("Missing integer " + key);
      }
   }

   private static class_2487 compound(class_2487 tag, String key) throws IOException {
      return tag.method_10562(key).orElseThrow(() -> invalid("Missing compound " + key));
   }

   private static class_2499 list(class_2487 tag, String key) throws IOException {
      return tag.method_10554(key).orElseThrow(() -> invalid("Missing list " + key));
   }

   private static class_2499 optionalCompoundList(class_2487 tag, String key) throws IOException {
      if (!tag.method_10545(key)) {
         return new class_2499();
      } else {
         class_2499 result = list(tag, key);

         for (class_2520 entry : result) {
            if (!(entry instanceof class_2487)) {
               throw invalid("Non-compound " + key + " entry");
            }
         }

         return result;
      }
   }

   private static void validateBlockEntity(class_2680 state, class_2487 tag, String region) throws IOException {
      if (!state.method_31709()) {
         throw invalid("Block entity attached to a non-entity block in " + region);
      } else {
         String name = tag.method_10558("id").orElseThrow(() -> invalid("Block entity has no id in " + region));
         class_2960 id = class_2960.method_12829(name);
         if (id == null || !class_7923.field_41181.method_10250(id) || !class_7923.field_41181.method_63535(id).method_20526(state)) {
            throw invalid("Invalid block entity " + name + " for " + class_7923.field_41175.method_10221(state.method_26204()));
         }
      }
   }

   private static class_2338 position(class_2487 tag) throws IOException {
      return new class_2338(integer(tag, "x"), integer(tag, "y"), integer(tag, "z"));
   }

   private static int axis(int signed) throws IOException {
      long value = Math.abs((long)signed);
      if (value >= 1L && value <= 8192L) {
         return (int)value;
      } else {
         throw invalid("Each region axis must contain 1–8192 blocks");
      }
   }

   private static int minimum(int position, int signedSize) throws IOException {
      long value = (long)position + Math.min(0, signedSize + 1);
      if (Math.abs(value) > 30000000L) {
         throw invalid("Region coordinates are outside supported bounds");
      } else {
         return (int)value;
      }
   }

   private static int span(int minimum, int maximum) throws IOException {
      long span = (long)maximum - minimum + 1L;
      if (span > 65536L) {
         throw invalid("Schematic bounds exceed 65,536 blocks on one axis");
      } else {
         return (int)span;
      }
   }

   private static IOException invalid(String message) {
      return new IOException(message);
   }

   private static String concise(Exception exception) {
      String message = exception.getMessage();
      return message == null ? exception.getClass().getSimpleName() : message.substring(0, Math.min(message.length(), 240));
   }

   @Environment(EnvType.CLIENT)
   private record Region(
      String name,
      class_2338 minimum,
      int sx,
      int sy,
      int sz,
      int volume,
      List<class_2680> palette,
      long[] packed,
      int bits,
      Map<class_2338, class_2487> entities
   ) {
   }
}
