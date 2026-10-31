package rex.path;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_2338;
import net.minecraft.class_310;

/**
 * One walk: the world model, the search and the follower, with the thinking and the walking
 * overlapped. Anything that wants to send the player somewhere owns one of these; the
 * {@link rex.RexPath} module is a thin shell around one, and Overlord uses its own so the two
 * never fight over the movement input.
 */
@Environment(EnvType.CLIENT)
public final class Traveller {
   @Environment(EnvType.CLIENT)
   public enum Result {
      IDLE,
      /** Searching, with nothing to walk yet. */
      THINKING,
      WALKING,
      /** A screen is open. */
      PAUSED,
      ARRIVED,
      /** No route, and no part of one worth walking. */
      UNREACHABLE
   }

   /** Everything the caller gets to choose, in one place. */
   @Environment(EnvType.CLIENT)
   public record Tuning(PathFinder.Options options, boolean pauseOnScreen, int lookAhead, long microsPerTick, int nodesPerTick, boolean sprint) {
      public static Tuning standard() {
         return new Tuning(PathFinder.Options.standard(), true, 12, 3000L, 4000, true);
      }
   }

   public final Terrain terrain = new Terrain();
   private final PathFinder finder = new PathFinder();
   private final Follower follower = new Follower();
   private Tuning tuning = Tuning.standard();
   private Goal goal;
   private int searches;
   private boolean ahead;
   public String status = "Idle";

   public Goal goal() {
      return this.goal;
   }

   public Path path() {
      return this.follower.path();
   }

   public int index() {
      return this.follower.index();
   }

   public int expanded() {
      return this.finder.expanded();
   }

   public long micros() {
      return this.finder.micros();
   }

   public int cached() {
      return this.terrain.size();
   }

   public int searches() {
      return this.searches;
   }

   public boolean busy() {
      return this.goal != null;
   }

   /** Sends the walk somewhere, from wherever the player is now. */
   public void go(class_310 client, Goal goal) {
      this.stop(client);
      this.goal = goal;
      this.searches = 0;
      this.ahead = false;
      this.status = "Thinking";
      // The first search is started by the first tick, which is where the caller's own settings
      // arrive; starting it here would plan with the defaults instead.
   }

   public void stop(class_310 client) {
      this.goal = null;
      this.finder.cancel();
      this.follower.stop(client);
      this.status = "Idle";
   }

   /** Hands the movement input back without forgetting the walk. */
   public void release() {
      this.follower.release();
   }

   /** A block changed: anything planned through it has to be checked again. */
   public void changed(class_2338 pos) {
      this.terrain.changed(pos);
   }

   private void plan(class_310 client, boolean fromPlayer) {
      class_2338 feet = client.field_1724.method_24515();
      long from = fromPlayer || this.follower.path() == null
         ? class_2338.method_10064(feet.method_10263(), feet.method_10264(), feet.method_10260())
         : this.follower.path().last();
      this.searches++;
      this.ahead = !fromPlayer;
      this.finder.begin(
         this.terrain, class_2338.method_10061(from), class_2338.method_10071(from), class_2338.method_10083(from), this.goal, this.tuning.options()
      );
   }

   public Result tick(class_310 client, Tuning tuning, long tick) {
      this.tuning = tuning;
      if (this.goal == null) {
         this.status = "Idle";
         return Result.IDLE;
      }
      if (client.field_1724 == null || client.field_1687 == null) {
         this.follower.release();
         this.status = "Waiting for a world";
         return Result.THINKING;
      }
      this.terrain.attach(client.field_1687);

      if (this.finder.state() == PathFinder.State.SEARCHING) {
         PathFinder.State after = this.finder.step(tuning.microsPerTick() * 1000L, tuning.nodesPerTick());
         if (after == PathFinder.State.FOUND || after == PathFinder.State.PARTIAL) {
            Path found = this.finder.result();
            if (found != null && found.length() > 1) {
               this.follower.follow(found);
            } else if (!this.ahead) {
               this.status = "Nowhere to go from here";
               return Result.UNREACHABLE;
            }
            this.ahead = false;
         } else if (after == PathFinder.State.FAILED) {
            if (this.ahead) {
               this.ahead = false;
            } else {
               this.status = "No way through to " + this.goal.describe();
               return Result.UNREACHABLE;
            }
         }
      }

      if (this.follower.path() == null) {
         if (this.finder.state() != PathFinder.State.SEARCHING) {
            this.plan(client, true);
         }
         this.status = "Thinking: " + this.finder.expanded() + " cells";
         return Result.THINKING;
      }

      this.follower.sprint = tuning.sprint();
      Follower.Result walked = this.follower.tick(client, this.terrain, tuning.pauseOnScreen(), tick);
      this.status = this.follower.status;
      switch (walked) {
         case PAUSED -> {
            return Result.PAUSED;
         }
         case WALKING -> {
            Path path = this.follower.path();
            // Think about the next stretch before this one runs out, so the walk never stops.
            if (!path.complete && this.finder.state() != PathFinder.State.SEARCHING && path.length() - this.follower.index() <= tuning.lookAhead()) {
               this.plan(client, false);
            }
            return Result.WALKING;
         }
         case STUCK -> {
            this.terrain.forget();
            this.plan(client, true);
            this.status = "Lost the route; thinking again";
            return Result.THINKING;
         }
         default -> {
            class_2338 feet = client.field_1724.method_24515();
            if (this.goal.reached(feet.method_10263(), feet.method_10264(), feet.method_10260())) {
               this.status = "Arrived at " + this.goal.describe();
               return Result.ARRIVED;
            }
            this.terrain.forget();
            this.plan(client, true);
            this.status = "Next stretch";
            return Result.THINKING;
         }
      }
   }
}
