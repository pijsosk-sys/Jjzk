package rex;

import dev.donut.client.config.ClientConfig;
import dev.krypton.core.module.Category;
import dev.krypton.core.module.Module;
import java.util.ArrayList;
import java.util.List;

/**
 * A Donut feature wearing a Krypton module. The toggle writes the Donut config and saves it, and
 * every client tick pulls the other direction so a change made in the legacy screen shows up on
 * the module row instead of the two disagreeing.
 */
public abstract class DonutFeature extends Module {
   private static final List<DonutFeature> features = new ArrayList<>();

   protected DonutFeature(String name, String description, Category category) {
      super(name, description, -1, category);
      features.add(this);
   }

   protected abstract boolean read(ClientConfig config) throws Exception;

   protected abstract void write(ClientConfig config, boolean value) throws Exception;

   @Override
   public void onEnable() {
      super.onEnable();
      this.apply(true);
   }

   @Override
   public void onDisable() {
      super.onDisable();
      this.apply(false);
   }

   private void apply(boolean value) {
      try {
         ClientConfig config = OneClient.donutConfig();
         if (config != null && this.read(config) != value) {
            this.write(config, value);
            OneClient.saveDonutConfig();
         }
      } catch (LinkageError | Exception failure) {
         Game.message(this.name + ": " + failure);
      }
   }

   /** Called once per client tick from the builder dispatcher. */
   public static void syncAll() {
      ClientConfig config = OneClient.donutConfig();
      if (config != null) {
         for (DonutFeature feature : features) {
            try {
               boolean live = feature.read(config);
               if (live != feature.enabled) {
                  feature.setEnabledSilently(live);
               }
            } catch (LinkageError | Exception ignored) {
            }
         }
      }
   }
}
