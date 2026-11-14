package dev.donut.client.autobuild;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.Predicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * A* over standing positions. Besides walking and one-block steps it can drop up to three blocks and,
 * where the terrain allows it, ask for a scaffold block to stand on.
 */
@Environment(EnvType.CLIENT)
public final class BuilderPathfinder {
   private BuilderPathfinder() {
   }

   public static BuilderPathfinder.Route find(
      BuilderPathfinder.Cell start,
      BuilderPathfinder.Cell target,
      BuilderPathfinder.Terrain terrain,
      Predicate<BuilderPathfinder.Cell> goal,
      int maxVisited,
      int maxDistance
   ) {
      PriorityQueue<BuilderPathfinder.Entry> open = new PriorityQueue<>(Comparator.comparingDouble(BuilderPathfinder.Entry::score));
      Map<BuilderPathfinder.Cell, Double> costs = new HashMap<>();
      Map<BuilderPathfinder.Cell, BuilderPathfinder.Cell> previous = new HashMap<>();
      Set<BuilderPathfinder.Cell> closed = new HashSet<>();
      Set<BuilderPathfinder.Cell> scaffolded = new HashSet<>();
      costs.put(start, 0.0);
      open.add(new BuilderPathfinder.Entry(start, start.distance(target)));
      int[][] axes = new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
      int[] rises = new int[]{0, 1, -1, -2, -3};

      while (!open.isEmpty() && closed.size() < maxVisited) {
         BuilderPathfinder.Cell current = open.remove().cell;
         if (!closed.add(current)) {
            continue;
         }

         if (goal.test(current)) {
            LinkedList<BuilderPathfinder.Cell> route = new LinkedList<>();
            Set<BuilderPathfinder.Cell> needed = new HashSet<>();

            for (BuilderPathfinder.Cell cursor = current; !cursor.equals(start); cursor = previous.get(cursor)) {
               route.addFirst(cursor);
               if (scaffolded.contains(cursor)) {
                  needed.add(cursor);
               }
            }

            return new BuilderPathfinder.Route(List.copyOf(route), needed, true);
         }

         for (int[] axis : axes) {
            for (int dy : rises) {
               BuilderPathfinder.Cell next = current.offset(axis[0], dy, axis[1]);
               if (Math.abs(next.x - start.x) > maxDistance
                  || Math.abs(next.z - start.z) > maxDistance
                  || Math.abs(next.y - start.y) > 48
                  || closed.contains(next)) {
                  continue;
               }

               if (dy > 0 && !terrain.headroom(current.offset(0, 1, 0))) {
                  continue;
               }

               boolean clear = true;
               for (int k = 1; k <= -dy && clear; k++) {
                  clear = terrain.headroom(next.offset(0, k, 0));
               }

               if (!clear) {
                  continue;
               }

               double step = dy == 0 ? 1.0 : (dy == 1 || dy == -1 ? 1.45 : 1.45 - 2.5 * (dy + 1));
               boolean scaffold = false;
               if (!terrain.standable(next)) {
                  if (dy < -1 || !terrain.scaffoldable(current, next)) {
                     continue;
                  }

                  scaffold = true;
                  step += 6.0;
               }

               double cost = costs.get(current) + step;
               if (cost < costs.getOrDefault(next, Double.POSITIVE_INFINITY)) {
                  costs.put(next, cost);
                  previous.put(next, current);
                  if (scaffold) {
                     scaffolded.add(next);
                  } else {
                     scaffolded.remove(next);
                  }

                  open.add(new BuilderPathfinder.Entry(next, cost + Math.max(0.0, next.distance(target) - 4.0)));
               }
            }
         }
      }

      return new BuilderPathfinder.Route(List.of(), Set.of(), false);
   }

   @Environment(EnvType.CLIENT)
   public record Cell(int x, int y, int z) {
      public BuilderPathfinder.Cell offset(int dx, int dy, int dz) {
         return new BuilderPathfinder.Cell(this.x + dx, this.y + dy, this.z + dz);
      }

      public double distance(BuilderPathfinder.Cell other) {
         return Math.sqrt(
            (double)(this.x - other.x) * (this.x - other.x) + (double)(this.y - other.y) * (this.y - other.y) + (double)(this.z - other.z) * (this.z - other.z)
         );
      }
   }

   /** @param scaffolds cells of the route whose floor has to be placed before stepping on them */
   @Environment(EnvType.CLIENT)
   public record Route(List<BuilderPathfinder.Cell> cells, Set<BuilderPathfinder.Cell> scaffolds, boolean found) {
   }

   @Environment(EnvType.CLIENT)
   private record Entry(BuilderPathfinder.Cell cell, double score) {
   }

   @Environment(EnvType.CLIENT)
   public interface Terrain {
      /** A player can stand with its feet in this cell. */
      boolean standable(BuilderPathfinder.Cell cell);

      /** The player's body fits with its feet in this cell, whatever is underneath. */
      boolean headroom(BuilderPathfinder.Cell cell);

      /** The floor under {@code next} is missing but could be placed while standing at {@code from}. */
      boolean scaffoldable(BuilderPathfinder.Cell from, BuilderPathfinder.Cell next);
   }
}
