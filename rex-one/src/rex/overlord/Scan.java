package rex.overlord;

import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_2338;
import net.minecraft.class_310;

/**
 * Looks for blocks in the chunks the client already has, a slice of the box per tick. A single
 * pass over a 97x97 area is over a million cells; doing that in one tick is a visible freeze, so
 * the pass is spread out and the nearest match of the finished pass is what gets used.
 */
@Environment(EnvType.CLIENT)
public final class Scan {
   private Set<String> ids = Set.of();
   private class_2338 origin = class_2338.field_10980;
   private int radius;
   private int low;
   private int high;
   private long cursor;
   private long cells;
   private final java.util.TreeMap<Double, class_2338> found = new java.util.TreeMap<>();
   private java.util.List<class_2338> results = java.util.List.of();
   private boolean complete;
   /** How many of the nearest matches a finished pass keeps, so a second choice exists. */
   private static final int KEEP = 24;

   /** Starts a fresh pass. Nothing else resets the cursor, so a pass always finishes. */
   public void restart(class_2338 origin, Set<String> ids, int radius, int low, int high) {
      this.origin = origin.method_10062();
      this.ids = ids;
      this.radius = radius;
      this.low = low;
      this.high = high;
      this.cursor = 0L;
      int side = radius * 2 + 1;
      this.cells = (long)side * side * (high - low + 1);
      this.found.clear();
      this.complete = false;
   }

   /** Forgets the last pass entirely, so a stale match cannot be used for a new search. */
   public void clear() {
      this.cells = 0L;
      this.cursor = 0L;
      this.complete = false;
      this.found.clear();
      this.results = java.util.List.of();
   }

   public boolean running() {
      return !this.complete && this.cells > 0L;
   }

   /** The nearest match of the last finished pass, nearest first. */
   public java.util.List<class_2338> results() {
      return this.results;
   }

   public double progress() {
      return this.cells == 0L ? 1.0 : (double)this.cursor / this.cells;
   }

   /** Walks up to budget cells. True when the pass has just finished. */
   public boolean step(class_310 client, int budget) {
      if (this.complete || this.cells == 0L || client.field_1687 == null) {
         return false;
      }
      int side = this.radius * 2 + 1;
      long end = Math.min(this.cells, this.cursor + budget);
      class_2338.class_2339 cursorPos = new class_2338.class_2339();
      for (; this.cursor < end; this.cursor++) {
         long index = this.cursor;
         int dx = (int)(index % side) - this.radius;
         int dz = (int)(index / side % side) - this.radius;
         int y = this.low + (int)(index / side / side);
         cursorPos.method_10103(this.origin.method_10263() + dx, y, this.origin.method_10260() + dz);
         if (!this.ids.contains(Ids.of(client.field_1687.method_8320(cursorPos)))) {
            continue;
         }
         double distance = this.origin.method_10262(cursorPos);
         if (this.found.size() < KEEP || distance < this.found.lastKey()) {
            this.found.put(distance, cursorPos.method_10062());
            if (this.found.size() > KEEP) {
               this.found.pollLastEntry();
            }
         }
      }
      if (this.cursor >= this.cells) {
         this.complete = true;
         this.results = java.util.List.copyOf(this.found.values());
         return true;
      }
      return false;
   }
}
