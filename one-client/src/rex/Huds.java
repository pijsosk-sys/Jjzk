package rex;

import net.minecraft.class_332;

/** Every panel that wants to survive an open screen, drawn after that screen and its tooltips. */
public final class Huds {
   private Huds() {
   }

   public static void overlay(class_332 graphics, float delta) {
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
