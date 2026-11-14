package dev.donut.client.gui;

import dev.donut.client.autobuild.AutoBuilderController;
import dev.donut.client.config.ClientConfig;
import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

@Environment(EnvType.CLIENT)
public final class AutoBuilderHud {
   private AutoBuilderHud() {
   }

   public static void render(DrawContext g, TextRenderer font, int height, ClientConfig config) {
      AutoBuilderController builder = AutoBuilderController.get();
      if (builder.schematic() != null
         && (builder.previewVisible() || builder.status() == AutoBuilderController.Status.RUNNING || builder.status() == AutoBuilderController.Status.PAUSED)) {
         int y = 12;
         String status = "AutoBuilder  " + builder.status().name().toLowerCase(Locale.ROOT) + "  " + (int)Math.floor(builder.progress() * 100.0) + "%  "
            + builder.confirmedBlocks() + "/" + builder.totalBlocks();
         String reason = builder.reason();

         while (UiPaint.width(font, reason) > 300 && reason.length() > 4) {
            reason = reason.substring(0, reason.length() - 4) + "...";
         }

         int w = Math.max(210, Math.max(UiPaint.width(font, status), UiPaint.width(font, reason)) + 20);
         UiPaint.round(g, 9, y, w, 45, 7, -636481257);
         UiPaint.text(g, font, status, 19, y + 8, -986378);
         UiPaint.smallText(g, font, reason, 19.0F, y + 22, -4934456);
         UiPaint.round(g, 19, y + 36, w - 20, 2, 1, -13619136);
         UiPaint.round(g, 19, y + 36, (int)((w - 20) * Math.clamp(builder.progress(), 0.0, 1.0)), 2, 1, 0xFF000000 | config.accentColor);
      }
   }
}
