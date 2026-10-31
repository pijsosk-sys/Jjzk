package rex.overlord;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_2248;
import net.minecraft.class_2680;
import net.minecraft.class_2960;
import net.minecraft.class_7923;

/**
 * Registry names without the namespace, so the rest of Overlord can talk in plain strings
 * ("diamond_pickaxe", "deepslate_iron_ore") and still be checked by the compiler where it counts.
 */
@Environment(EnvType.CLIENT)
public final class Ids {
   private Ids() {
   }

   public static String of(class_1799 stack) {
      return stack == null || stack.method_7960() ? "air" : of(stack.method_7909());
   }

   public static String of(class_1792 item) {
      return class_7923.field_41178.method_10221(item).method_12832();
   }

   public static String of(class_2680 state) {
      return state == null ? "air" : of(state.method_26204());
   }

   public static String of(class_2248 block) {
      return class_7923.field_41175.method_10221(block).method_12832();
   }

   /** The item with this name, or null when 1.21.11 has no such item. */
   public static class_1792 item(String id) {
      class_2960 name = class_2960.method_60655("minecraft", id);
      return class_7923.field_41178.method_10250(name) ? class_7923.field_41178.method_63535(name) : null;
   }

   /** The block with this name, or null when 1.21.11 has no such block. */
   public static class_2248 block(String id) {
      class_2960 name = class_2960.method_60655("minecraft", id);
      return class_7923.field_41175.method_10250(name) ? class_7923.field_41175.method_63535(name) : null;
   }

   /** "diamond_pickaxe" -> "diamond pickaxe", for messages people read. */
   public static String pretty(String id) {
      return id.replace('_', ' ');
   }
}
