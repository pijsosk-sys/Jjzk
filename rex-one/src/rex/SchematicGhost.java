package rex;

import dev.donut.client.autobuild.AutoBuilderController;
import dev.donut.client.autobuild.Schematic;
import dev.krypton.core.module.Category;
import dev.krypton.core.module.Module;

/**
 * Shows or hides the loaded schematic's ghost blocks in the world. Bind a key to it in the menu to
 * flip the ghost without opening the builder. This row is the one switch: the builder screen's
 * Ghost button toggles it, and loading a new schematic turns it on.
 */
public final class SchematicGhost extends Module {
   public static SchematicGhost instance;
   private static Schematic seen;

   public SchematicGhost() {
      super("Schematic Ghost", "Shows the loaded schematic as ghost blocks in the world.", -1, Category.RENDER);
      this.enabled = true;
      instance = this;
   }

   @Override
   public void onEnable() {
      super.onEnable();
      AutoBuilderController.get().setPreviewVisible(true);
   }

   @Override
   public void onDisable() {
      super.onDisable();
      AutoBuilderController.get().setPreviewVisible(false);
   }

   /** True when the ghost is meant to be visible. */
   public static boolean shown() {
      SchematicGhost ghost = instance;
      return ghost == null ? AutoBuilderController.get().previewVisible() : ghost.enabled;
   }

   /** The builder screen's Ghost button. */
   public static void set(boolean visible) {
      SchematicGhost ghost = instance;
      if (ghost == null) {
         AutoBuilderController.get().setPreviewVisible(visible);
      } else if (ghost.enabled != visible) {
         ghost.setEnabled(visible);
      } else {
         AutoBuilderController.get().setPreviewVisible(visible);
      }
   }

   /** Once per client tick, from Build Tempo's hook. */
   static void sync(AutoBuilderController builder) {
      SchematicGhost ghost = instance;
      if (ghost == null) {
         return;
      }
      Schematic schematic = builder.schematic();
      if (schematic != seen) {
         seen = schematic;
         if (schematic != null && !ghost.enabled) {
            ghost.setEnabled(true);
         }
      }
      if (schematic != null && builder.previewVisible() != ghost.enabled) {
         builder.setPreviewVisible(ghost.enabled);
      }
   }
}
