package rex;

import dev.krypton.util.Color;
import net.minecraft.class_332;

/**
 * The drawing every panel in this build shares, in the game's own fill and text calls so nothing
 * touches renderer state: a panel drawn at the tail of a screen's render must leave that screen's
 * projection and clip exactly as it found them.
 */
public final class HudPanel {
   public static final int PAD = 7;

   private HudPanel() {
   }

   public static int argb(Color color) {
      return (clamp(color.alpha) << 24) | (clamp(color.red) << 16) | (clamp(color.green) << 8) | clamp(color.blue);
   }

   private static int clamp(int channel) {
      return Math.min(255, Math.max(0, channel));
   }

   public static Color fade(Color color, int alpha) {
      return new Color(color.red, color.green, color.blue, alpha);
   }

   public static Object font() throws Exception {
      return Bridge.field(Bridge.client(), "field_1772");
   }

   public static int lineHeight(Object font) throws Exception {
      return ((Number)Bridge.field(font, "field_2000")).intValue();
   }

   public static int width(Object font, String line) throws Exception {
      return ((Number)Game.invoke(font, "method_1727", new String[]{"java.lang.String"}, line)).intValue();
   }

   public static void fill(class_332 graphics, int x1, int y1, int x2, int y2, int colour) throws Exception {
      Game.invoke(graphics, "method_25294", new String[]{"int", "int", "int", "int", "int"}, x1, y1, x2, y2, colour);
   }

   public static void string(class_332 graphics, Object font, String line, int x, int y, int colour) throws Exception {
      Game.invoke(
         graphics,
         "method_51433",
         new String[]{"net.minecraft.class_327", "java.lang.String", "int", "int", "int", "boolean"},
         font,
         line,
         x,
         y,
         colour,
         true
      );
   }

   /** Body, accent bar along the top, hairline border. */
   public static void frame(class_332 graphics, int x, int y, int width, int height, int back, int tint, int faint) throws Exception {
      fill(graphics, x, y, x + width, y + height, back);
      fill(graphics, x, y, x + width, y + 2, tint);
      fill(graphics, x, y + height - 1, x + width, y + height, faint);
      fill(graphics, x, y, x + 1, y + height, faint);
      fill(graphics, x + width - 1, y, x + width, y + height, faint);
   }

   /** Label left, value right, on one row. */
   public static void row(class_332 graphics, Object font, String label, String value, int x, int y, int width, int labelColour, int valueColour)
      throws Exception {
      string(graphics, font, label, x + PAD, y, labelColour);
      String shown = clip(font, value, width - PAD * 2 - width(font, label) - 6);
      string(graphics, font, shown, x + width - PAD - width(font, shown), y, valueColour);
   }

   public static void bar(class_332 graphics, int x, int y, int width, double done, int track, int fillColour) throws Exception {
      fill(graphics, x, y, x + width, y + 3, track);
      fill(graphics, x, y, x + (int)(width * Math.max(0.0, Math.min(1.0, done))), y + 3, fillColour);
   }

   public static String clip(Object font, String line, int room) throws Exception {
      if (room <= 8 || width(font, line) <= room) {
         return line;
      }
      String cut = line;
      while (cut.length() > 2 && width(font, cut + "...") > room) {
         cut = cut.substring(0, cut.length() - 1);
      }
      return cut + "...";
   }

   /** Top-left corner for an anchor, in the game's scaled coordinates. */
   public static int[] origin(HudAnchor corner, int offsetX, int offsetY, int width, int height) throws Exception {
      Object window = Bridge.call(Bridge.client(), "method_22683");
      int screenWidth = ((Number)Bridge.call(window, "method_4486")).intValue();
      int screenHeight = ((Number)Bridge.call(window, "method_4502")).intValue();
      int x = offsetX;
      int y = offsetY;
      if (corner == HudAnchor.TOP_RIGHT || corner == HudAnchor.BOTTOM_RIGHT) {
         x = screenWidth - width - offsetX;
      }
      if (corner == HudAnchor.BOTTOM_LEFT || corner == HudAnchor.BOTTOM_RIGHT) {
         y = screenHeight - height - offsetY;
      }
      return new int[]{Math.max(0, x), Math.max(0, y)};
   }
}
