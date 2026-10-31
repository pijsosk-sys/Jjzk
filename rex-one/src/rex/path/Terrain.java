package rex.path;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_265;
import net.minecraft.class_2680;
import net.minecraft.class_638;
import rex.overlord.Ids;

/**
 * What a cell is, from the search's point of view, with the answer remembered. A single path
 * search asks about tens of thousands of cells and asks about most of them several times; looking
 * each one up in the chunk again is where a pathfinder's time actually goes.
 */
@Environment(EnvType.CLIENT)
public final class Terrain {
   public static final byte UNKNOWN = 0;
   /** Nothing in the way: air, a torch, a flower. */
   public static final byte CLEAR = 1;
   /** A full enough block to stand on. */
   public static final byte FLOOR = 2;
   /** Solid but not standable, or simply in the way. */
   public static final byte BLOCKED = 3;
   /** Water: you can go through it, slowly. */
   public static final byte WATER = 4;
   /** Lava, fire, cactus, a magma block: never walk into it. */
   public static final byte HARM = 5;
   /** Not loaded: the search must not guess what is there. */
   public static final byte VOID = 6;

   private static final Set<String> HARMFUL = Set.of(
      "lava", "fire", "soul_fire", "campfire", "soul_campfire", "magma_block", "cactus", "sweet_berry_bush",
      "powder_snow", "wither_rose", "lit_campfire", "end_portal", "nether_portal"
   );
   /** Blocks that are solid but that a path should rather not touch. */
   private static final Set<String> AVOID = Set.of("bedrock", "barrier", "obsidian", "crying_obsidian", "ancient_debris", "reinforced_deepslate");

   private final Long2ByteOpenHashMap cache = new Long2ByteOpenHashMap();
   private class_638 world;
   private final class_2338.class_2339 cursor = new class_2338.class_2339();
   /** How many lookups the cache saved, for the HUD. */
   public long hits;
   public long misses;

   public Terrain() {
      this.cache.defaultReturnValue(UNKNOWN);
   }

   public void attach(class_638 world) {
      if (this.world != world) {
         this.world = world;
         this.cache.clear();
      }
   }

   public void forget() {
      this.cache.clear();
   }

   /** Drops what is remembered about one block and its neighbours above and below. */
   public void changed(class_2338 pos) {
      for (int dy = -1; dy <= 1; dy++) {
         this.cache.remove(class_2338.method_10064(pos.method_10263(), pos.method_10264() + dy, pos.method_10260()));
      }
   }

   public int size() {
      return this.cache.size();
   }

   public byte at(int x, int y, int z) {
      long key = class_2338.method_10064(x, y, z);
      byte known = this.cache.get(key);
      if (known != UNKNOWN) {
         this.hits++;
         return known;
      }
      this.misses++;
      byte value = this.classify(x, y, z);
      this.cache.put(key, value);
      return value;
   }

   private byte classify(int x, int y, int z) {
      if (this.world == null || y < this.world.method_31607() || y >= this.world.method_31600()) {
         return VOID;
      }
      this.cursor.method_10103(x, y, z);
      if (!this.world.method_22340(this.cursor)) {
         return VOID;
      }
      class_2680 state = this.world.method_8320(this.cursor);
      String id = Ids.of(state);
      if (HARMFUL.contains(id)) {
         return HARM;
      }
      if (!state.method_26227().method_15769()) {
         return id.contains("lava") ? HARM : WATER;
      }
      class_265 shape = state.method_26220(this.world, this.cursor);
      if (shape.method_1110()) {
         return CLEAR;
      }
      // Tall enough on top to stand on, and filling the cell enough to walk across: a slab, a
      // stair and a full block all count; a fence or a wall does not, because the player cannot
      // step onto one without jumping.
      double top = shape.method_1105(class_2350.class_2351.field_11052);
      double low = shape.method_1091(class_2350.class_2351.field_11052);
      boolean full = shape.method_1105(class_2350.class_2351.field_11048) - shape.method_1091(class_2350.class_2351.field_11048) > 0.9
         && shape.method_1105(class_2350.class_2351.field_11051) - shape.method_1091(class_2350.class_2351.field_11051) > 0.9;
      return low <= 0.01 && top >= 0.49 && top <= 1.01 && full ? FLOOR : BLOCKED;
   }

   /** Nothing solid in the way of the body. */
   public boolean clear(int x, int y, int z) {
      byte at = this.at(x, y, z);
      return at == CLEAR || at == WATER;
   }

   /** A cell the feet can rest in: clear here and overhead, with something to stand on below. */
   public boolean standable(int x, int y, int z) {
      return this.clear(x, y, z) && this.clear(x, y + 1, z) && this.at(x, y - 1, z) == FLOOR;
   }

   public boolean water(int x, int y, int z) {
      return this.at(x, y, z) == WATER;
   }

   public boolean harmful(int x, int y, int z) {
      return this.at(x, y, z) == HARM;
   }

   public boolean known(int x, int y, int z) {
      return this.at(x, y, z) != VOID;
   }

   /** True when the block may be broken to get through: not bedrock, not a liquid, not precious. */
   public boolean breakable(int x, int y, int z) {
      byte at = this.at(x, y, z);
      if (at != BLOCKED && at != FLOOR) {
         return false;
      }
      this.cursor.method_10103(x, y, z);
      String id = Ids.of(this.world.method_8320(this.cursor));
      if (AVOID.contains(id)) {
         return false;
      }
      return this.world.method_8320(this.cursor).method_26214(this.world, this.cursor) >= 0.0F;
   }

   /** Roughly how long this block takes to break, as a path cost. */
   public double hardness(int x, int y, int z) {
      this.cursor.method_10103(x, y, z);
      float hardness = this.world.method_8320(this.cursor).method_26214(this.world, this.cursor);
      return hardness < 0.0F ? 1000.0 : Math.min(40.0, 2.0 + hardness * 3.0);
   }
}
