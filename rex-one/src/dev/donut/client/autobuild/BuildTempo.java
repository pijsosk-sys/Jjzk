package dev.donut.client.autobuild;

import java.util.Locale;

/**
 * How quickly Auto Build sends placements.
 * <p>
 * SAFE turns the camera for real, waits for the server to see it, and waits for every placement to be
 * acknowledged. The other presets send the rotation together with the placement and do not wait.
 */
public enum BuildTempo {
   SAFE("Safe", 1, 4, true),
   BALANCED("Balanced", 1, 2, false),
   FAST("Fast", 3, 4, false),
   TURBO("Turbo", 4, 1, false);

   public final String label;
   /** Placements allowed every {@link #period} ticks. */
   public final int burst;
   public final int period;
   /** Aim with a real, server-synchronised camera turn and wait for each acknowledgement. */
   public final boolean careful;

   BuildTempo(String label, int burst, int period, boolean careful) {
      this.label = label;
      this.burst = burst;
      this.period = period;
      this.careful = careful;
   }

   public BuildTempo next() {
      return values()[(this.ordinal() + 1) % values().length];
   }

   public String describe() {
      return switch (this) {
         case SAFE -> "about 3 blocks/s, waits for every confirmation";
         case BALANCED -> "about 10 blocks/s";
         case FAST -> "about 15 blocks/s";
         case TURBO -> "up to 80 blocks/s, for singleplayer and creative worlds";
      };
   }

   public static BuildTempo parse(String value) {
      if (value != null) {
         for (BuildTempo tempo : values()) {
            if (tempo.name().equals(value.trim().toUpperCase(Locale.ROOT))) {
               return tempo;
            }
         }
      }
      return FAST;
   }
}
