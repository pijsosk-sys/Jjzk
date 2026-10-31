package com.roothack.overlord.ui;

import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_327;
import net.minecraft.class_332;
import net.minecraft.class_3532;

public final class Banner {
   private static String text = "";
   private static long expiresAtMs = 0L;
   private static int argbBase = 16777215;

   private Banner() {
   }

   public static void show(String message, int rgb, long millis) {
      text = message;
      argbBase = rgb & 16777215;
      expiresAtMs = System.currentTimeMillis() + millis;
   }

   public static void render(class_332 ctx) {
      long now = System.currentTimeMillis();
      if (now < expiresAtMs && !text.isEmpty()) {
         class_310 mc = class_310.method_1551();
         long remaining = expiresAtMs - now;
         float alpha = remaining >= 500L ? 1.0F : class_3532.method_15363((float)remaining / 500.0F, 0.0F, 1.0F);
         int a = (int)(alpha * 255.0F) & 0xFF;
         int color = a << 24 | argbBase;
         int sw = ctx.method_51421();
         class_327 font = mc.field_1772;
         class_2561 t = class_2561.method_43470(text);
         int tw = font.method_27525(t);
         int x = (sw - tw) / 2;
         int y = 24;
         ctx.method_25294(x - 6, y - 4, x + tw + 6, y + 9 + 4, a << 24);
         ctx.method_27535(font, t, x, y, color);
      }
   }
}
