package rex;

import dev.donut.client.autobuild.AutoBuilderController;
import dev.donut.client.autobuild.Schematic;
import dev.krypton.core.module.Category;
import dev.krypton.core.module.Module;
import dev.krypton.core.setting.EnumSetting;
import dev.krypton.core.setting.NumberSetting;
import dev.krypton.core.setting.Setting;

/**
 * Shows or hides the loaded schematic's ghost blocks in the world. Bind a key to it in the menu to
 * flip the ghost without opening the builder. This row is the one switch: the builder screen's
 * Ghost button toggles it, and loading a new schematic turns it on.
 */
public final class SchematicGhost extends Module {
   public enum Layers {
      ALL("All layers"),
      WORKING("Working layer"),
      UP_TO("Up to working"),
      MANUAL("One layer");

      public final String label;

      Layers(String label) {
         this.label = label;
      }
   }

   public final EnumSetting<Layers> layers = new EnumSetting<>("Layers", Layers.ALL, Layers.class)
      .describe("All, only the layer being built, everything up to it, or the one layer picked with Layer.");
   public final NumberSetting layer = new NumberSetting("Layer", 0.0, 383.0, 0.0, 1.0)
      .describe("For One layer: which layer, counted from the bottom of the schematic.");

   public static SchematicGhost instance;
   private static Schematic seen;

   public SchematicGhost() {
      super("Schematic Ghost", "Shows the loaded schematic as ghost blocks in the world.", -1, Category.RENDER);
      this.layer.visibleWhen(() -> this.layers.get() == Layers.MANUAL);
      this.addSettings(new Setting[]{this.layers, this.layer});
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

   public static Layers mode() {
      SchematicGhost ghost = instance;
      return ghost == null ? Layers.ALL : (Layers)ghost.layers.get();
   }

   public static void cycleMode() {
      SchematicGhost ghost = instance;
      if (ghost != null) {
         ghost.layers.next();
         ghost.layers.changed();
      }
   }

   public static int manualLayer() {
      SchematicGhost ghost = instance;
      return ghost == null ? 0 : ghost.layer.getInt();
   }

   public static void shiftLayer(int by) {
      SchematicGhost ghost = instance;
      if (ghost != null) {
         ghost.layer.set(Math.max(0.0, Math.min(383.0, ghost.layer.getDouble() + by)));
         ghost.layer.changed();
      }
   }

   /** The world Y range of ghosts to draw, or null for all of them. */
   public static int[] layerRange() {
      AutoBuilderController builder = AutoBuilderController.get();
      return switch (mode()) {
         case ALL -> null;
         case WORKING -> builder.workingLayer() == Integer.MIN_VALUE ? null : new int[]{builder.workingLayer(), builder.workingLayer()};
         case UP_TO -> builder.workingLayer() == Integer.MIN_VALUE ? null : new int[]{Integer.MIN_VALUE, builder.workingLayer()};
         case MANUAL -> {
            int y = builder.transform().origin().method_10264() + manualLayer();
            yield new int[]{y, y};
         }
      };
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
