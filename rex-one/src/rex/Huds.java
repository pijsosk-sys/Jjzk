package rex;

import net.minecraft.class_332;

/** Every panel that wants to survive an open screen, drawn after that screen and its tooltips. */
public final class Huds {
   private Huds() {
   }

   public static void overlay(class_332 graphics, float delta) {
      // The Auto Build screen shows the same figures itself, and the menu fills the screen: panels
      // on top of either only cover them.
      net.minecraft.class_437 screen = net.minecraft.class_310.method_1551().field_1755;
      if (screen instanceof dev.donut.client.gui.AutoBuilderScreen || screen instanceof dev.donut.client.gui.OneMenuScreen) {
         return;
      }
      try {
         DuperHud.overlay(graphics, delta);
      } catch (Throwable escaped) {
         System.err.println("[ONE] duper panel: " + escaped);
      }
      try {
         BuildHud.overlay(graphics, delta);
      } catch (Throwable escaped) {
         System.err.println("[ONE] build panel: " + escaped);
      }
   }
}
