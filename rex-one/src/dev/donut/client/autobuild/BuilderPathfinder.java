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

@Environment(EnvType.CLIENT)
public final class BuilderPathfinder {
   private BuilderPathfinder() {
   }

   public static List<BuilderPathfinder.Cell> find(
      BuilderPathfinder.Cell start,
      BuilderPathfinder.Cell target,
      BuilderPathfinder.Terrain terrain,
      Predicate<BuilderPathfinder.Cell> goal,
      int maxVisited,
      int maxDistance
   ) {
      PriorityQueue<BuilderPathfinder.Entry> open = new PriorityQueue<>(
         Comparator.comparingDouble(BuilderPathfinder.Entry::score)
      );
      Map<BuilderPathfinder.Cell, Double> costs = new HashMap<>();
      Map<BuilderPathfinder.Cell, BuilderPathfinder.Cell> previous = new HashMap<>();
      Set<BuilderPathfinder.Cell> closed = new HashSet<>();
      costs.put(start, 0.0);
      open.add(new BuilderPathfinder.Entry(start, start.distance(target)));
      int[][] axes = new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

      while (!open.isEmpty() && closed.size() < maxVisited) {
         BuilderPathfinder.Cell current = open.remove().cell;
         if (closed.add(current)) {
            if (goal.test(current)) {
               LinkedList<BuilderPathfinder.Cell> route = new LinkedList<>();

               for (BuilderPathfinder.Cell cursor = current; !cursor.equals(start); cursor = previous.get(cursor)) {
                  route.addFirst(cursor);
               }

               return List.copyOf(route);
            }

            for (int[] axis : axes) {
               for (int dy : new int[]{0, 1, -1}) {
                  BuilderPathfinder.Cell next = current.offset(axis[0], dy, axis[1]);
                  if (Math.abs(next.x - start.x) <= maxDistance
                     && Math.abs(next.z - start.z) <= maxDistance
                     && Math.abs(next.y - start.y) <= 24
                     && !closed.contains(next)
                     && terrain.standable(next)
                     && (dy <= 0 || terrain.headroom(current.offset(0, 1, 0)))
                     && (dy >= 0 || terrain.headroom(next.offset(0, 1, 0)))) {
                     double cost = costs.get(current) + (dy == 0 ? 1.0 : 1.45);
                     if (!(cost >= costs.getOrDefault(next, Double.POSITIVE_INFINITY))) {
                        costs.put(next, cost);
                        previous.put(next, current);
                        open.add(new BuilderPathfinder.Entry(next, cost + Math.max(0.0, next.distance(target) - 4.0)));
                     }
                  }
               }
            }
         }
      }

      return List.of();
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

   @Environment(EnvType.CLIENT)
   private record Entry(BuilderPathfinder.Cell cell, double score) {
   }

   @Environment(EnvType.CLIENT)
   public interface Terrain {
      boolean standable(BuilderPathfinder.Cell var1);

      boolean headroom(BuilderPathfinder.Cell var1);
   }
}
