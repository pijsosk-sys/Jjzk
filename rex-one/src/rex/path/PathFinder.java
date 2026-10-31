package rex.path;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.Arrays;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_2338;

/**
 * A* over the blocks the client has, run a slice at a time.
 *
 * <p>Two things make it usable in a game rather than in a benchmark. It never runs to completion
 * inside one tick: {@link #step} is given a time and a node budget and keeps its open set between
 * ticks, so a long search costs a few frames of thinking instead of a freeze. And when the budget
 * really is gone it does not give up: it returns the route to the most promising cell it reached,
 * which is walked while the next search runs from the end of it. That is what makes a walk across
 * a thousand blocks start moving immediately.
 */
@Environment(EnvType.CLIENT)
public final class PathFinder {
   @Environment(EnvType.CLIENT)
   public enum State {
      IDLE,
      SEARCHING,
      /** The goal was reached. */
      FOUND,
      /** The budget ran out; what came back is the best part-way route. */
      PARTIAL,
      /** Nowhere to go from here. */
      FAILED
   }

   @Environment(EnvType.CLIENT)
   public record Options(boolean allowBreak, boolean allowPlace, boolean allowSwim, int maxFall, double weight, int maxNodes) {
      public static Options standard() {
         return new Options(true, false, true, 4, 1.35, 300000);
      }
   }

   private static final int[][] CARDINALS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
   private static final int[][] DIAGONALS = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
   private static final double DIAGONAL_COST = 1.4142135623730951;

   private Terrain terrain;
   private Goal goal;
   private Options options = Options.standard();

   private long[] cell = new long[1024];
   private double[] cost = new double[1024];
   private double[] estimate = new double[1024];
   private int[] parent = new int[1024];
   private byte[] move = new byte[1024];
   private boolean[] closed = new boolean[1024];
   private int nodes;

   private int[] heap = new int[1024];
   private int heapSize;

   private final Long2IntOpenHashMap known = new Long2IntOpenHashMap();
   private int best;
   private double bestHeuristic;
   private State state = State.IDLE;
   private Path result;
   private int expanded;
   private long nanos;

   public PathFinder() {
      this.known.defaultReturnValue(-1);
   }

   public State state() {
      return this.state;
   }

   public Path result() {
      return this.result;
   }

   public int expanded() {
      return this.expanded;
   }

   public long micros() {
      return this.nanos / 1000L;
   }

   public Goal goal() {
      return this.goal;
   }

   public void cancel() {
      this.state = State.IDLE;
      this.result = null;
      this.reset();
   }

   private void reset() {
      this.nodes = 0;
      this.heapSize = 0;
      this.known.clear();
      this.expanded = 0;
      this.nanos = 0L;
   }

   /** Starts a fresh search from a cell. */
   public void begin(Terrain terrain, int x, int y, int z, Goal goal, Options options) {
      this.terrain = terrain;
      this.goal = goal;
      this.options = options;
      this.reset();
      this.result = null;
      int start = this.node(class_2338.method_10064(x, y, z));
      this.cost[start] = 0.0;
      this.estimate[start] = goal.heuristic(x, y, z) * options.weight();
      this.parent[start] = -1;
      this.move[start] = Path.WALK;
      this.best = start;
      this.bestHeuristic = goal.heuristic(x, y, z);
      this.push(start);
      this.state = State.SEARCHING;
   }

   /** Works for at most this long and this many cells. Call again next tick while SEARCHING. */
   public State step(long nanoBudget, int nodeBudget) {
      if (this.state != State.SEARCHING) {
         return this.state;
      }
      long started = System.nanoTime();
      int taken = 0;
      while (this.heapSize > 0) {
         if (++taken > nodeBudget || System.nanoTime() - started > nanoBudget) {
            this.nanos += System.nanoTime() - started;
            return State.SEARCHING;
         }
         int current = this.pop();
         if (this.closed[current]) {
            continue;
         }
         this.closed[current] = true;
         this.expanded++;
         long position = this.cell[current];
         int x = class_2338.method_10061(position);
         int y = class_2338.method_10071(position);
         int z = class_2338.method_10083(position);
         double heuristic = this.goal.heuristic(x, y, z);
         if (heuristic < this.bestHeuristic) {
            this.bestHeuristic = heuristic;
            this.best = current;
         }
         if (this.goal.reached(x, y, z)) {
            this.result = this.build(current, true);
            this.state = State.FOUND;
            this.nanos += System.nanoTime() - started;
            return this.state;
         }
         if (this.expanded >= this.options.maxNodes()) {
            break;
         }
         this.expand(current, x, y, z);
      }
      this.nanos += System.nanoTime() - started;
      // Out of open cells or out of budget: hand back the best part of the way, if it is anywhere.
      this.result = this.best == -1 || this.parent[this.best] == -1 ? null : this.build(this.best, false);
      this.state = this.result == null ? State.FAILED : State.PARTIAL;
      return this.state;
   }

   private Path build(int end, boolean complete) {
      int count = 1;
      for (int at = end; this.parent[at] != -1; at = this.parent[at]) {
         count++;
      }
      long[] cells = new long[count];
      byte[] moves = new byte[count];
      int at = end;
      for (int i = count - 1; i >= 0; i--) {
         cells[i] = this.cell[at];
         moves[i] = this.move[at];
         if (this.parent[at] == -1) {
            break;
         }
         at = this.parent[at];
      }
      return new Path(cells, moves, complete);
   }

   // ------------------------------------------------------------------ moves

   private void expand(int from, int x, int y, int z) {
      Terrain t = this.terrain;
      boolean headroom = t.clear(x, y + 1, z);
      for (int[] d : CARDINALS) {
         int nx = x + d[0];
         int nz = z + d[1];
         if (t.at(nx, y, nz) == Terrain.VOID) {
            continue; // never path into chunks that are not there
         }
         if (t.standable(nx, y, nz)) {
            this.offer(from, nx, y, nz, 1.0, Path.WALK);
         } else {
            byte ahead = t.at(nx, y, nz);
            // Step up one, the way a player walks up a slab or a stair.
            if ((ahead == Terrain.FLOOR || ahead == Terrain.BLOCKED)
               && t.standable(nx, y + 1, nz)
               && t.clear(x, y + 2, z)) {
               this.offer(from, nx, y + 1, nz, 1.9, Path.JUMP);
            } else if (this.options.allowBreak() && (ahead == Terrain.BLOCKED || ahead == Terrain.FLOOR) && t.at(nx, y - 1, nz) == Terrain.FLOOR) {
               double extra = 0.0;
               if (!t.clear(nx, y, nz)) {
                  if (!t.breakable(nx, y, nz)) {
                     continue;
                  }
                  extra += t.hardness(nx, y, nz);
               }
               if (!t.clear(nx, y + 1, nz)) {
                  if (!t.breakable(nx, y + 1, nz)) {
                     continue;
                  }
                  extra += t.hardness(nx, y + 1, nz);
               }
               this.offer(from, nx, y, nz, 1.0 + extra, Path.BREAK);
            }
         }

         // Walk off an edge. One block down is free, further down costs more, and the search
         // refuses to drop into lava however convenient it looks.
         if (t.clear(nx, y, nz) && t.clear(nx, y + 1, nz) && t.at(nx, y - 1, nz) != Terrain.FLOOR) {
            boolean landed = false;
            for (int drop = 1; drop <= this.options.maxFall(); drop++) {
               byte below = t.at(nx, y - drop, nz);
               if (below == Terrain.FLOOR) {
                  this.offer(from, nx, y - drop + 1, nz, 1.0 + 0.5 * (drop - 1), Path.FALL);
                  landed = true;
                  break;
               }
               if (below == Terrain.WATER) {
                  this.offer(from, nx, y - drop, nz, 1.5 + 0.2 * drop, Path.FALL);
                  landed = true;
                  break;
               }
               if (below != Terrain.CLEAR) {
                  break;
               }
            }
            if (!landed && this.options.allowPlace()) {
               this.offer(from, nx, y, nz, 3.0, Path.BRIDGE);
            }
         }

         if (this.options.allowSwim() && t.water(nx, y, nz) && t.clear(nx, y + 1, nz)) {
            this.offer(from, nx, y, nz, 2.2, Path.SWIM);
         }
      }

      // Corners, but never through one: both blocks beside the corner have to be open, or the
      // player clips the edge and stops dead.
      for (int[] d : DIAGONALS) {
         int nx = x + d[0];
         int nz = z + d[1];
         if (t.standable(nx, y, nz)
            && headroom
            && t.clear(nx, y, z)
            && t.clear(nx, y + 1, z)
            && t.clear(x, y, nz)
            && t.clear(x, y + 1, nz)) {
            this.offer(from, nx, y, nz, DIAGONAL_COST, Path.DIAGONAL);
         }
      }

      // Up and down inside water.
      if (this.options.allowSwim() && t.water(x, y, z)) {
         if (t.clear(x, y + 1, z)) {
            this.offer(from, x, y + 1, z, 2.5, Path.SWIM);
         }
         if (t.water(x, y - 1, z)) {
            this.offer(from, x, y - 1, z, 2.5, Path.SWIM);
         }
      }
   }

   private void offer(int from, int x, int y, int z, double step, byte kind) {
      if (this.terrain.harmful(x, y, z) || this.terrain.harmful(x, y + 1, z)) {
         return;
      }
      long position = class_2338.method_10064(x, y, z);
      double candidate = this.cost[from] + step;
      int existing = this.known.get(position);
      if (existing >= 0) {
         if (this.closed[existing] || candidate >= this.cost[existing]) {
            return;
         }
         this.cost[existing] = candidate;
         this.parent[existing] = from;
         this.move[existing] = kind;
         this.estimate[existing] = candidate + this.goal.heuristic(x, y, z) * this.options.weight();
         this.push(existing);
         return;
      }
      int node = this.node(position);
      this.cost[node] = candidate;
      this.parent[node] = from;
      this.move[node] = kind;
      this.estimate[node] = candidate + this.goal.heuristic(x, y, z) * this.options.weight();
      this.push(node);
   }

   // ------------------------------------------------------------------ store and heap

   private int node(long position) {
      if (this.nodes == this.cell.length) {
         int size = this.nodes * 2;
         this.cell = Arrays.copyOf(this.cell, size);
         this.cost = Arrays.copyOf(this.cost, size);
         this.estimate = Arrays.copyOf(this.estimate, size);
         this.parent = Arrays.copyOf(this.parent, size);
         this.move = Arrays.copyOf(this.move, size);
         this.closed = Arrays.copyOf(this.closed, size);
      }
      int index = this.nodes++;
      this.cell[index] = position;
      this.closed[index] = false;
      this.known.put(position, index);
      return index;
   }

   private void push(int node) {
      if (this.heapSize == this.heap.length) {
         this.heap = Arrays.copyOf(this.heap, this.heapSize * 2);
      }
      int at = this.heapSize++;
      this.heap[at] = node;
      while (at > 0) {
         int up = (at - 1) >>> 1;
         if (this.estimate[this.heap[up]] <= this.estimate[this.heap[at]]) {
            break;
         }
         int swap = this.heap[up];
         this.heap[up] = this.heap[at];
         this.heap[at] = swap;
         at = up;
      }
   }

   private int pop() {
      int top = this.heap[0];
      this.heap[0] = this.heap[--this.heapSize];
      int at = 0;
      while (true) {
         int left = at * 2 + 1;
         if (left >= this.heapSize) {
            break;
         }
         int right = left + 1;
         int small = right < this.heapSize && this.estimate[this.heap[right]] < this.estimate[this.heap[left]] ? right : left;
         if (this.estimate[this.heap[small]] >= this.estimate[this.heap[at]]) {
            break;
         }
         int swap = this.heap[small];
         this.heap[small] = this.heap[at];
         this.heap[at] = swap;
         at = small;
      }
      return top;
   }
}
