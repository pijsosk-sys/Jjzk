package dev.donut.client.config;

import dev.donut.client.autobuild.BuildTempo;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class AutoBuilderSettings {
   public boolean favorite = false;
   public double previewOpacity = 0.28;
   public boolean creativeMode;
   /** Name of a {@link BuildTempo}. */
   public String tempo = BuildTempo.FAST.name();

   public void sanitize() {
      this.previewOpacity = Double.isFinite(this.previewOpacity) ? Math.clamp(this.previewOpacity, 0.08, 0.8) : 0.28;
      this.tempo = BuildTempo.parse(this.tempo).name();
   }
}
