package rex.overlord;

import dev.donut.client.autobuild.BuilderLook;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_1268;
import net.minecraft.class_1269;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_239;
import net.minecraft.class_243;
import net.minecraft.class_310;
import net.minecraft.class_3959;
import net.minecraft.class_3965;
import net.minecraft.class_746;

/**
 * Finding a face of a block the player can actually see and click from where they stand, aiming at
 * it the way the builder aims, and then clicking it. Everything Overlord does to the world goes
 * through a real ray cast first, so it never sends a click the server would refuse.
 */
@Environment(EnvType.CLIENT)
public final class Reach {
   /** Survival block reach in 1.21, less a margin so a server with the same figure agrees. */
   public static final double REACH = 4.4;

   private Reach() {
   }

   public static class_243 centre(class_2338 pos) {
      return new class_243(pos.method_10263() + 0.5, pos.method_10264() + 0.5, pos.method_10260() + 0.5);
   }

   public static class_3965 trace(class_310 client, class_243 from, class_243 to) {
      return client.field_1687.method_17742(
         new class_3959(from, to, class_3959.class_3960.field_17559, class_3959.class_242.field_1348, client.field_1724)
      );
   }

   /**
    * A hit that interacts with the target block (placing false) or places a block into the target
    * cell (placing true), or null when no face of it can be reached and seen from here.
    */
   public static class_3965 hitFor(class_310 client, class_2338 target, boolean placing) {
      class_746 player = client.field_1724;
      class_243 eye = player.method_33571();
      for (class_2350 direction : class_2350.values()) {
         class_2338 support = placing ? target.method_10093(direction) : target;
         class_2350 face = placing ? direction.method_10153() : direction;
         if (client.field_1687.method_8320(support).method_45474()) {
            continue; // nothing solid to click
         }
         class_243 point = centre(support)
            .method_1031(face.method_10148() * 0.499, face.method_10164() * 0.499, face.method_10165() * 0.499);
         if (eye.method_1025(point) > REACH * REACH) {
            continue;
         }
         // A hair further in than the face, not further out: a ray that stops short of the surface
         // misses, and then no face of a block was ever reachable and nothing was ever broken.
         class_243 just = point.method_1031(face.method_10148() * -0.01, face.method_10164() * -0.01, face.method_10165() * -0.01);
         class_3965 hit = trace(client, eye, just);
         if (hit == null || hit.method_17783() != class_239.class_240.field_1332) {
            continue;
         }
         if (hit.method_17777().equals(support) && hit.method_17780() == face) {
            return hit;
         }
      }
      return null;
   }

   /** Turns the view onto the hit point; true once the view is there. */
   public static boolean aimAt(class_310 client, class_3965 hit) {
      class_243 point = hit.method_17784();
      float[] look = BuilderLook.anglesTo(client.field_1724, point.method_10216(), point.method_10214(), point.method_10215());
      return BuilderLook.toward(client.field_1724, look[0], look[1]);
   }

   /** Right-clicks the face. True when the server accepted it. */
   public static boolean use(class_310 client, class_3965 hit) {
      class_1269 result = client.field_1761.method_2896(client.field_1724, class_1268.field_5808, hit);
      if (result.method_23665()) {
         client.field_1724.method_6104(class_1268.field_5808);
         return true;
      }
      return false;
   }
}
