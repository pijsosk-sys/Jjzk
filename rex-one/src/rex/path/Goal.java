package rex.path;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_2338;

/**
 * Where the pathfinder is trying to get to. A goal answers two questions: is this cell good
 * enough, and how far does it still look. The heuristic must never overstate the distance left or
 * the search can walk past the short way round.
 */
@Environment(EnvType.CLIENT)
public interface Goal {
   boolean reached(int x, int y, int z);

   double heuristic(int x, int y, int z);

   /** What to show in the HUD and on screen. */
   String describe();

   /** The block the marker is drawn on, or null for goals with no single spot. */
   class_2338 marker();

   /**
    * Cheap octile distance, with height priced the way the moves price it: a block up is a jump
    * and costs about 1.9, a block down is a free step off an edge. Pricing height the same as flat
    * ground looks harmless and is not: a staircase eight blocks high then costs the search
    * twenty-odd thousand cells of flat ground before it will climb anything.
    *
    * @param dx dz the flat distance left, either sign
    * @param climb how much higher the goal is than the cell; negative means the goal is below
    */
   static double octile(int dx, int climb, int dz) {
      int ax = Math.abs(dx);
      int az = Math.abs(dz);
      int diagonal = Math.min(ax, az);
      int straight = Math.max(ax, az) - diagonal;
      return diagonal * 1.4142135623730951 + straight + (climb > 0 ? climb * 1.85 : -climb * 0.5);
   }

   @Environment(EnvType.CLIENT)
   record Block(int x, int y, int z) implements Goal {
      @Override
      public boolean reached(int px, int py, int pz) {
         return px == this.x && py == this.y && pz == this.z;
      }

      @Override
      public double heuristic(int px, int py, int pz) {
         return octile(px - this.x, this.y - py, pz - this.z);
      }

      @Override
      public String describe() {
         return this.x + " " + this.y + " " + this.z;
      }

      @Override
      public class_2338 marker() {
         return new class_2338(this.x, this.y, this.z);
      }
   }

   /** Anywhere within a radius of a spot: what you want for "walk up to that chest". */
   @Environment(EnvType.CLIENT)
   record Near(int x, int y, int z, double radius) implements Goal {
      @Override
      public boolean reached(int px, int py, int pz) {
         double dx = px - this.x;
         double dy = py - this.y;
         double dz = pz - this.z;
         return dx * dx + dy * dy + dz * dz <= this.radius * this.radius;
      }

      @Override
      public double heuristic(int px, int py, int pz) {
         return Math.max(0.0, octile(px - this.x, this.y - py, pz - this.z) - this.radius);
      }

      @Override
      public String describe() {
         return this.x + " " + this.y + " " + this.z + " (within " + (int)this.radius + ")";
      }

      @Override
      public class_2338 marker() {
         return new class_2338(this.x, this.y, this.z);
      }
   }

   /** A column: any height at these coordinates. */
   @Environment(EnvType.CLIENT)
   record Column(int x, int z) implements Goal {
      @Override
      public boolean reached(int px, int py, int pz) {
         return px == this.x && pz == this.z;
      }

      @Override
      public double heuristic(int px, int py, int pz) {
         return octile(px - this.x, 0, pz - this.z);
      }

      @Override
      public String describe() {
         return this.x + " ~ " + this.z;
      }

      @Override
      public class_2338 marker() {
         return null;
      }
   }

   /** A height, wherever that is: how you get down to the diamond layer. */
   @Environment(EnvType.CLIENT)
   record Height(int y) implements Goal {
      @Override
      public boolean reached(int px, int py, int pz) {
         return py == this.y;
      }

      @Override
      public double heuristic(int px, int py, int pz) {
         return this.y > py ? (this.y - py) * 1.85 : (py - this.y) * 0.5;
      }

      @Override
      public String describe() {
         return "y=" + this.y;
      }

      @Override
      public class_2338 marker() {
         return null;
      }
   }
}
