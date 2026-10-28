package rex;

import dev.donut.client.autobuild.AutoBuilderController;
import dev.donut.client.autobuild.AutoBuilderController.Status;

/**
 * Deferred blocks leave the work set and the controller then pauses with the reason it kept. This
 * re-queues them once the world has had time to change, which is what unloaded chunks, lost routes
 * and occupied positions need; it never resumes a pause the player has to clear.
 */
public final class BuildPatience {
   private static final String[] RETRYABLE = {
      "unloaded",
      "No placeable blocks remain",
      "No walking route",
      "Cannot reach",
      "Still waiting for server confirmation",
      "No server confirmation",
      "Paired block",
      "Missing support",
      "Occupied or incorrect"
   };

   private static Status previous = Status.IDLE;
   private static int confirmed = -1;
   private static int waiting;
   private static int attempts;
   private static boolean quiet;

   public static void tick(Object raw) {
      if (raw instanceof AutoBuilderController controller) {
         try {
            Status status = controller.status();
            if (status != previous) {
               previous = status;
               waiting = 0;
               if (status != Status.PAUSED) {
                  quiet = false;
               }
            }
            if (status == Status.RUNNING) {
               int blocks = controller.confirmedBlocks();
               if (blocks != confirmed) {
                  confirmed = blocks;
                  attempts = 0;
               }
               return;
            }
            if (status != Status.PAUSED || !BuildTempo.autoResume()) {
               return;
            }
            String reason = controller.reason();
            if (!retryable(reason)) {
               return;
            }
            if (attempts >= BuildTempo.maxRetries()) {
               if (!quiet) {
                  quiet = true;
                  Game.message("Auto Build paused after " + attempts + " retries: " + reason);
               }
               return;
            }
            if (++waiting < BuildTempo.retryDelay() * (1 + attempts / 8)) {
               return;
            }
            waiting = 0;
            attempts++;
            controller.resume();
            if (controller.status() == Status.RUNNING) {
               Game.message("Auto Build retry " + attempts + ": " + reason);
            }
         } catch (LinkageError | RuntimeException ignored) {
         }
      }
   }

   private static boolean retryable(String reason) {
      if (reason == null) {
         return false;
      }
      for (String part : RETRYABLE) {
         if (reason.contains(part)) {
            return true;
         }
      }
      return false;
   }
}
