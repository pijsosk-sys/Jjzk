package com.roothack.overlord.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

/**
 * A transient on-screen banner drawn near the top-centre of the HUD, used to
 * announce state changes (engaged, halted, camouflage) so the trigger visibly
 * "pops up". Holds a single message with an expiry; fades out over its last
 * half-second.
 */
public final class Banner {

    private static String text = "";
    private static long expiresAtMs = 0L;
    private static int argbBase = 0xFFFFFF;

    private Banner() {}

    public static void show(String message, int rgb, long millis) {
        text = message;
        argbBase = rgb & 0xFFFFFF;
        expiresAtMs = System.currentTimeMillis() + millis;
    }

    public static void render(DrawContext ctx) {
        long now = System.currentTimeMillis();
        if (now >= expiresAtMs || text.isEmpty()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        long remaining = expiresAtMs - now;
        float alpha = remaining >= 500 ? 1f : MathHelper.clamp(remaining / 500f, 0f, 1f);
        int a = (int) (alpha * 255) & 0xFF;
        int color = (a << 24) | argbBase;

        int sw = ctx.getScaledWindowWidth();
        var font = mc.textRenderer;
        Text t = Text.literal(text);
        int tw = font.getWidth(t);
        int x = (sw - tw) / 2;
        int y = 24;

        // Backing plate for readability, then the text.
        ctx.fill(x - 6, y - 4, x + tw + 6, y + font.fontHeight + 4, (a << 24));
        ctx.drawTextWithShadow(font, t, x, y, color);
    }
}
