package rex;

import dev.donut.client.config.ClientConfig;
import dev.krypton.core.module.Category;

/** Donut's storage highlighter: per-type colours, through-walls fill, distance fade. */
public final class DonutStorage extends DonutFeature {
   public DonutStorage() {
      super("Storage Finder", "Donut storage highlighter with per-type colours. Tune it in .one donut.", Category.RENDER);
   }

   @Override
   protected boolean read(ClientConfig config) {
      return config.storage.enabled;
   }

   @Override
   protected void write(ClientConfig config, boolean value) {
      config.storage.enabled = value;
   }
}
