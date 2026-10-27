package rex;

import dev.donut.client.config.ClientConfig;
import dev.donut.client.fly.FlyController;
import dev.krypton.core.module.Category;

/** Donut's flight, including its ability packets and save handling. */
public final class DonutFly extends DonutFeature {
   public DonutFly() {
      super("Fly", "Donut flight: server abilities, save handling, no keybind of its own.", Category.MISC);
   }

   @Override
   protected boolean read(ClientConfig config) {
      return FlyController.enabled();
   }

   @Override
   protected void write(ClientConfig config, boolean value) {
      FlyController controller = FlyController.getInstance();
      if (controller == null) {
         throw new IllegalStateException("flight is still initializing");
      }
      controller.setEnabled(value);
   }
}
