package rex;

import dev.donut.client.config.ClientConfig;
import dev.krypton.core.module.Category;

/** Donut's growth-based chunk finder. */
public final class DonutChunks extends DonutFeature {
   public DonutChunks() {
      super("Growth Chunks", "Donut chunk finder: kelp, vines, amethyst and bamboo growth evidence.", Category.BASEFINDING);
   }

   @Override
   protected boolean read(ClientConfig config) {
      return config.chunkFinder.enabled;
   }

   @Override
   protected void write(ClientConfig config, boolean value) {
      config.chunkFinder.enabled = value;
   }
}
