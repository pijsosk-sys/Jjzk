package rex;

import dev.donut.client.config.ClientConfig;
import dev.krypton.core.module.Category;

/** Donut's spawner highlighter. */
public final class DonutSpawner extends DonutFeature {
   public DonutSpawner() {
      super("Spawner Finder", "Donut spawner highlighter. Tune it in .one donut.", Category.RENDER);
   }

   @Override
   protected boolean read(ClientConfig config) {
      return config.spawner.enabled;
   }

   @Override
   protected void write(ClientConfig config, boolean value) {
      config.spawner.enabled = value;
   }
}
