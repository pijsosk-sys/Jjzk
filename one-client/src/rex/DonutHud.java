package rex;

import dev.donut.client.config.ClientConfig;
import dev.krypton.core.module.Category;

/** The legacy Donut module list and builder HUD. Off by default so Krypton's HUD stands alone. */
public final class DonutHud extends DonutFeature {
   public DonutHud() {
      super("Donut HUD", "The legacy Donut module list and Auto Build progress lines.", Category.RENDER);
   }

   @Override
   protected boolean read(ClientConfig config) {
      return config.showHud;
   }

   @Override
   protected void write(ClientConfig config, boolean value) {
      config.showHud = value;
   }
}
