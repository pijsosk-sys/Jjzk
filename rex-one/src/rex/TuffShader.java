package rex;

import dev.krypton.core.event.ClientTickEvent;
import dev.krypton.core.event.EventHandler;
import dev.krypton.core.module.Category;
import dev.krypton.core.module.Module;
import dev.krypton.core.setting.EnumSetting;
import dev.krypton.core.setting.Setting;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_757;

/**
 * A light colour grade for the world: one full-screen pass (sharpen, vibrance, contrast, split
 * toning, vignette) through Minecraft's own post-effect pipeline, the one spectating a creeper
 * uses. It runs before the HUD and menus are drawn, so text stays untouched, and it costs about
 * as much as that creeper effect does. No Iris or OptiFine needed.
 */
public final class TuffShader extends Module {
   public enum Style {
      SOFT("tuff_soft"),
      TUFF("tuff"),
      VIVID("tuff_vivid");

      final class_2960 id;

      Style(String path) {
         this.id = class_2960.method_60655("rex", path);
      }
   }

   public final EnumSetting<Style> style = new EnumSetting<>("Style", Style.TUFF, Style.class)
      .describe("Soft is barely there, Tuff is punchy, Vivid is loud.");

   public static TuffShader instance;
   private static Method setEffect;
   private static Field effectId;
   private static boolean broken;

   public TuffShader() {
      super("Tuff Shader", "A light colour grade for the world. No shader mod needed.", -1, Category.RENDER);
      this.addSettings(new Setting[]{this.style});
      instance = this;
   }

   private class_2960 wanted() {
      return ((Style)this.style.get()).id;
   }

   @Override
   public void onEnable() {
      super.onEnable();
      this.apply();
   }

   @Override
   public void onDisable() {
      super.onDisable();
      class_757 renderer = class_310.method_1551().field_1773;
      if (renderer != null && isOurs(renderer.method_62906())) {
         renderer.method_62905();
      }
   }

   /** Spectating a mob or a resource reload replaces the effect; put ours back each tick. */
   @EventHandler
   public void onTuffTick(ClientTickEvent event) {
      this.apply();
   }

   private void apply() {
      class_757 renderer = class_310.method_1551().field_1773;
      if (renderer == null || broken) {
         return;
      }
      class_2960 current = renderer.method_62906();
      if (current != null && !isOurs(current)) {
         return; // a vanilla effect (creeper, spider, entity outline) is in charge right now
      }
      if (this.wanted().equals(current)) {
         return;
      }
      try {
         if (setEffect == null) {
            setEffect = class_757.class.getDeclaredMethod("method_62904", class_2960.class);
            setEffect.setAccessible(true);
         }
         setEffect.invoke(renderer, this.wanted());
      } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
         broken = true;
         System.err.println("[ONE] Tuff Shader could not be applied: " + failure);
      }
   }

   private static boolean isOurs(class_2960 id) {
      return id != null && id.method_12836().equals("rex") && id.method_12832().startsWith("tuff");
   }
}
