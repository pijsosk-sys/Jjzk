package rexsmoke.mixin;

import rexsmoke.Smoke;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
abstract class TickMixin {
   // method_1574 is MinecraftClient.tick in Fabric's intermediary names.
   @Inject(method = "method_1574", at = @At("TAIL"), remap = false)
   private void rexsmoke$tick(CallbackInfo ci) {
      Smoke.tick((MinecraftClient)(Object)this);
   }
}
