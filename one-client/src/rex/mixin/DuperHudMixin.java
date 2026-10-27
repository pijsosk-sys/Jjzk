package rex.mixin;

import net.minecraft.class_332;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The in-game HUD draws under an open screen, while the duper works with a container open and the
 * builder with its own window open, so the panels are drawn again after the screen and its
 * tooltips. Not required: if this ever misses, they simply stay behind the screen's backdrop.
 */
@Mixin(
   targets = {"net.minecraft.class_437"},
   priority = 2000,
   remap = false
)
public abstract class DuperHudMixin {
   @Inject(
      method = {"method_47413(Lnet/minecraft/class_332;IIF)V"},
      at = {@At("TAIL")},
      require = 0,
      remap = false
   )
   private void rex$duperOverlay(class_332 graphics, int mouseX, int mouseY, float delta, CallbackInfo info) {
      rex.Huds.overlay(graphics, delta);
   }
}
