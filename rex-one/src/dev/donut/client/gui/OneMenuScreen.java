package dev.donut.client.gui;

import dev.krypton.client.KryptonClient;
import dev.krypton.core.module.Category;
import dev.krypton.core.module.Module;
import dev.krypton.core.setting.BooleanSetting;
import dev.krypton.core.setting.ColorSetting;
import dev.krypton.core.setting.EnumSetting;
import dev.krypton.core.setting.KeybindSetting;
import dev.krypton.core.setting.NumberSetting;
import dev.krypton.core.setting.RangeSetting;
import dev.krypton.core.setting.Setting;
import dev.krypton.core.setting.StringSetting;
import dev.krypton.gui.ClickGuiScreen;
import dev.krypton.gui.ConfigsScreen;
import dev.krypton.impl.modules.client.KryptonPlus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.class_11905;
import net.minecraft.class_11908;
import net.minecraft.class_11909;
import net.minecraft.class_332;

/**
 * The client's module menu, redesigned. The old one opened six draggable column panels that
 * overlapped one another and ran off the bottom of the screen. This is one window: the
 * categories down the left with a search box, the modules as cards in the middle, and the chosen
 * module's settings on the right, each kind of setting with its own control.
 *
 * <p>It extends the old menu so everything that checks "is the menu open" or returns to it after
 * a pop-up (colour and item pickers) keeps working, and the old panels are one click away
 * ("Classic menu") for the few setting kinds that only have a pop-up editor.
 */
@Environment(EnvType.CLIENT)
public final class OneMenuScreen extends ClickGuiScreen {
   private static final int W = 900;
   private static final int H = 540;
   private static final int SIDE = 200;
   private static final int LIST_X = 200;
   private static final int LIST_W = 384;
   private static final int SET_X = 584;
   private static final int SET_W = W - SET_X;
   private static final int CARD_W = 172;
   private static final int CARD_H = 52;
   private static final int TEXT = 0xFFE9ECF2;
   private static final int MUTED = 0xFF9AA1B2;
   private static final int DIM = 0xFF666E80;
   private static final int WINDOW = 0xFF101219;
   private static final int SIDEBAR = 0xFF0B0D13;
   private static final int CARD = 0xFF171A23;
   private static final int INSET = 0xFF0A0C11;
   private static final int BORDER = 0xFF252A37;
   private static final int GOOD = 0xFF5BD68A;
   private static final int[] TINTS = {0xFFEF4444, 0xFF60A5FA, 0xFFF59E0B, 0xFF34D399, 0xFFA78BFA, 0xFF94A3B8};
   private static boolean hooked;
   /** Module names starred by the player, kept in config/rex-one-favorites.txt. */
   private static Set<String> favorites;

   private final List<Hit> hits = new ArrayList<>();
   private final Map<String, Double> motion = new HashMap<>();
   private final Set<Setting> expanded = new HashSet<>();
   private float scale = 1.0F;
   private float originX;
   private float originY;
   private double mouseX;
   private double mouseY;
   private double seconds;
   private double appear;
   private long lastFrame;
   private boolean closing;
   /** "ALL", "ACTIVE" or a category's enum name. */
   private String view = "ALL";
   private String query = "";
   private boolean searchFocused;
   private Module selected;
   private int moduleScroll;
   private int settingScroll;
   private int moduleContent;
   private int settingContent;
   private Runnable drag;
   private StringSetting editing;
   /** A module waiting for its new key, or a key setting waiting for its key. */
   private Object listening;
   private boolean classic;
   private String tooltip;
   private String lastTooltip;
   private double tooltipHeld;

   public OneMenuScreen() {
      super();
   }

   /** Swaps the client's menu for this one as soon as the client has made its own. Once. */
   public static void install() {
      if (hooked) {
         return;
      }
      hooked = true;
      ClientTickEvents.END_CLIENT_TICK.register(client -> {
         KryptonClient krypton = KryptonClient.instance;
         if (krypton != null && krypton.clickGui != null && !(krypton.clickGui instanceof OneMenuScreen) && client.field_1755 != krypton.clickGui) {
            krypton.clickGui = new OneMenuScreen();
         }
      });
   }

   // ---- Layout and the frame ---------------------------------------------------------------------

   @Override
   protected void method_25426() {
      this.scale = Math.max(0.3F, Math.min(1.0F, Math.min((this.field_22789 - 12.0F) / W, (this.field_22790 - 12.0F) / H)));
      this.originX = (this.field_22789 - W * this.scale) / 2.0F;
      this.originY = (this.field_22790 - H * this.scale) / 2.0F;
      if (this.field_22787 != null) {
         this.field_22787.method_1566().method_2000();
      }
      this.closing = false;
      this.hits.clear();
   }

   @Override
   public void method_25394(class_332 g, int mx, int my, float delta) {
      if (this.classic) {
         super.method_25394(g, mx, my, delta);
         this.hits.clear();
         this.mouseX = mx;
         this.mouseY = my;
         int bw = 128;
         this.pill(g, "modern", "Back to new menu", 10, this.field_22790 - 30, bw, 20, true, () -> this.classic = false);
         return;
      }
      long now = System.nanoTime();
      this.seconds = this.lastFrame == 0L ? 1.0 / 60.0 : Math.min(0.1, (now - this.lastFrame) / 1.0E9);
      this.lastFrame = now;
      this.appear = Math.clamp(this.appear + this.seconds / (this.closing ? -0.12 : 0.16), 0.0, 1.0);
      if (this.closing && this.appear <= 0.0) {
         this.closing = false;
         this.appear = 0.0;
         this.lastFrame = 0L;
         super.method_25419();
         return;
      }
      if (KryptonClient.instance != null && KryptonClient.instance.previousScreen != null) {
         KryptonClient.instance.previousScreen.method_25394(g, 0, 0, delta);
      }
      this.mouseX = (mx - this.originX) / this.scale;
      this.mouseY = (my - this.originY) / this.scale;
      this.hits.clear();
      this.tooltip = null;
      double eased = smooth(this.appear);
      double previous = UiPaint.pushOpacity(eased);
      g.method_51448().pushMatrix();
      try {
         if (KryptonPlus.background == null || KryptonPlus.background.get()) {
            g.method_25294(0, 0, this.field_22789, this.field_22790, UiPaint.withOpacity(0xA0050608));
         }
         float pop = (float)(0.97 + 0.03 * eased);
         g.method_51448().translate(this.originX + W * this.scale * (1 - pop) / 2.0F, this.originY + H * this.scale * (1 - pop) / 2.0F + (float)((1.0 - eased) * 8.0));
         g.method_51448().scale(this.scale * pop, this.scale * pop);
         UiPaint.shadow(g, 0, 0, W, H, 16, 0xC0000000);
         UiPaint.round(g, 0, 0, W, H, 16, BORDER);
         UiPaint.round(g, 1, 1, W - 2, H - 2, 15, WINDOW);
         this.drawSidebar(g);
         this.drawModules(g);
         this.drawSettings(g);
         // A tooltip waits for the pointer to rest, so sweeping across cards doesn't flash them.
         if (this.tooltip != null && this.tooltip.equals(this.lastTooltip)) {
            this.tooltipHeld += this.seconds;
         } else {
            this.tooltipHeld = 0.0;
         }
         this.lastTooltip = this.tooltip;
         if (this.tooltip != null && this.tooltipHeld > 0.45) {
            double fade = UiPaint.pushOpacity(Math.min(1.0, (this.tooltipHeld - 0.45) / 0.12) * smooth(this.appear));
            try {
               this.drawTooltip(g, this.tooltip);
            } finally {
               UiPaint.popOpacity(fade);
            }
         }
      } finally {
         g.method_51448().popMatrix();
         UiPaint.popOpacity(previous);
      }
   }

   /** Only the classic panels draw through the old menu's own painter. */
   @Override
   public void qpp(class_332 context, int mouseX, int mouseY, float delta) {
      if (this.classic) {
         super.qpp(context, mouseX, mouseY, delta);
      }
   }

   private int accent() {
      try {
         ColorSetting color = KryptonPlus.accentColor;
         if (color != null) {
            return 0xFF000000 | color.y() << 16 | color.qq() << 8 | color.d();
         }
      } catch (RuntimeException ignored) {
      }
      return 0xFF8B5CF6;
   }

   // ---- Sidebar -----------------------------------------------------------------------------------

   private void drawSidebar(class_332 g) {
      int accent = this.accent();
      UiPaint.round(g, 1, 1, SIDE, H - 2, 15, SIDEBAR);
      g.method_25294(SIDE - 16, 1, SIDE, H - 1, UiPaint.withOpacity(SIDEBAR));
      g.method_25294(SIDE, 14, SIDE + 1, H - 14, UiPaint.withOpacity(BORDER));
      UiPaint.cube(g, 26, 28, 13, accent, 0.95, 0.25);
      UiPaint.strong(g, this.field_22793, "REX One", 48, 20, TEXT);
      List<Module> all = modules();
      long on = all.stream().filter(Module::isEnabled).count();
      UiPaint.smallText(g, this.field_22793, all.size() + " modules  ·  " + on + " on", 48, 33, MUTED);

      int sx = 14;
      int sy = 54;
      double focus = this.animate("searchFocus", this.searchFocused);
      UiPaint.round(g, sx, sy, SIDE - 28, 26, 7, UiPaint.blend(INSET, accent, 0.04 + focus * 0.06));
      UiPaint.outline(g, sx, sy, SIDE - 28, 26, 7, UiPaint.blend(BORDER, accent, focus * 0.8));
      UiPaint.search(g, sx + 9, sy + 8, MUTED);
      boolean caret = this.searchFocused && System.currentTimeMillis() / 500 % 2 == 0;
      String shown = this.query.isEmpty() && !this.searchFocused ? "Search modules" : this.ellipsize(this.query, SIDE - 66) + (caret ? "_" : "");
      UiPaint.text(g, this.field_22793, shown, sx + 26, sy + 9, this.query.isEmpty() && !this.searchFocused ? DIM : TEXT);
      this.hit("search", sx, sy, SIDE - 28, 26, () -> this.searchFocused = true);

      int y = 94;
      y = this.navRow(g, "ALL", "All modules", all.size(), 0xFF64748B, "*", y);
      y = this.navRow(g, "ACTIVE", "Active", (int)on, GOOD, "+", y);
      y = this.navRow(g, "FAV", "Favorites", (int)all.stream().filter(OneMenuScreen::favorite).count(), 0xFFFACC15, "*", y);
      g.method_25294(sx + 4, y + 4, SIDE - 18, y + 5, UiPaint.withOpacity(BORDER));
      y += 12;
      Category[] categories = Category.values();
      for (int i = 0; i < categories.length; i++) {
         Category category = categories[i];
         List<Module> in = byCategory(category);
         y = this.navRow(g, category.name(), pretty(category), in.size(), TINTS[i % TINTS.length], pretty(category).substring(0, 1), y);
      }

      int by = H - 110;
      this.sideButton(g, "builder", "Auto Build", by, true, () -> {
         try {
            menuClosed();
            rex.UnifiedAutoBuild.open();
         } catch (Exception failure) {
            this.tooltip = "Auto Build could not open: " + failure.getMessage();
         }
      });
      this.sideButton(g, "configs", "Configs", by + 30, false, () -> {
         menuClosed();
         this.field_22787.method_1507(new ConfigsScreen());
      });
      this.sideButton(g, "classic", "Classic menu", by + 60, false, () -> this.classic = true);
      if (this.inside(14, by + 60, SIDE - 28, 24)) {
         this.tooltip = "The old column panels, for pop-up settings such as item and block lists.";
      }
   }

   private int navRow(class_332 g, String id, String label, int count, int tint, String letter, int y) {
      int accent = this.accent();
      int x = 12;
      int w = SIDE - 24;
      boolean chosen = this.query.isEmpty() && this.view.equals(id);
      double hover = this.animate("nav" + id, this.inside(x, y, w, 28));
      double pick = this.animate("navPick" + id, chosen);
      UiPaint.round(g, x, y, w, 28, 7, UiPaint.alpha(UiPaint.blend(SIDEBAR, accent, 0.22 * pick + 0.07 * hover), 1.0));
      if (pick > 0.02) {
         UiPaint.round(g, x, y + 7, 3, 14, 1, UiPaint.alpha(accent, pick));
      }
      UiPaint.round(g, x + 10, y + 5, 18, 18, 5, UiPaint.alpha(tint, 0.22 + 0.2 * pick));
      UiPaint.text(g, this.field_22793, letter, x + 19 - UiPaint.width(this.field_22793, letter) / 2, y + 10, tint);
      UiPaint.text(g, this.field_22793, label, x + 36, y + 10, chosen ? TEXT : MUTED);
      String number = Integer.toString(count);
      int nw = UiPaint.width(this.field_22793, number) + 10;
      UiPaint.round(g, x + w - nw - 6, y + 7, nw, 14, 7, UiPaint.blend(INSET, accent, 0.1 * pick));
      UiPaint.text(g, this.field_22793, number, x + w - nw - 1, y + 10, chosen ? TEXT : DIM);
      this.hit("nav" + id, x, y, w, 28, () -> {
         this.view = id;
         this.query = "";
         this.searchFocused = false;
         this.moduleScroll = 0;
      });
      return y + 31;
   }

   private void sideButton(class_332 g, String id, String label, int y, boolean primary, Runnable action) {
      this.pill(g, id, label, 14, y, SIDE - 28, 24, primary, action);
   }

   // ---- Module cards ------------------------------------------------------------------------------

   private List<Module> shown() {
      String q = this.query.toLowerCase(Locale.ROOT).trim();
      List<Module> out = new ArrayList<>();
      for (Module module : modules()) {
         if (!q.isEmpty()) {
            String name = String.valueOf(module.getName()).toLowerCase(Locale.ROOT);
            String about = String.valueOf(module.getDescription()).toLowerCase(Locale.ROOT);
            if (name.contains(q) || about.contains(q)) {
               out.add(module);
            }
         } else if (this.view.equals("ALL") || this.view.equals("ACTIVE") && module.isEnabled() || this.view.equals("FAV") && favorite(module)
            || module.getCategory() != null && module.getCategory().name().equals(this.view)) {
            out.add(module);
         }
      }
      // Names first by match quality when searching, else alphabetical: the old panels listed
      // registration order, which put related modules far apart.
      out.sort((a, b) -> {
         if (!q.isEmpty()) {
            boolean an = String.valueOf(a.getName()).toLowerCase(Locale.ROOT).startsWith(q);
            boolean bn = String.valueOf(b.getName()).toLowerCase(Locale.ROOT).startsWith(q);
            if (an != bn) {
               return an ? -1 : 1;
            }
         }
         return String.valueOf(a.getName()).compareToIgnoreCase(String.valueOf(b.getName()));
      });
      return out;
   }

   private void drawModules(class_332 g) {
      int accent = this.accent();
      List<Module> list = this.shown();
      String title = !this.query.isEmpty() ? "Results for \"" + this.query + "\"" : this.view.equals("ALL") ? "All modules"
         : this.view.equals("ACTIVE") ? "Active modules" : this.view.equals("FAV") ? "Favorites" : pretty(Category.valueOf(this.view));
      UiPaint.strong(g, this.field_22793, this.ellipsize(title, LIST_W - 40), LIST_X + 18, 20, TEXT);
      long on = list.stream().filter(Module::isEnabled).count();
      UiPaint.smallText(g, this.field_22793, list.size() + (list.size() == 1 ? " module" : " modules") + "  ·  " + on + " on", LIST_X + 18, 33, MUTED);
      UiPaint.right(g, this.field_22793, "Right-click toggles", LIST_X + LIST_W - 16, 22, DIM);

      int top = 52;
      int bottom = H - 14;
      int rows = (list.size() + 1) / 2;
      this.moduleContent = rows * (CARD_H + 8);
      this.moduleScroll = Math.clamp(this.moduleScroll, 0, Math.max(0, this.moduleContent - (bottom - top)));
      if (list.isEmpty()) {
         String none = !this.query.isEmpty() ? "No module matches \"" + this.query + "\"." : this.view.equals("FAV") ? "Star a module to keep it here." : "Nothing here yet.";
         UiPaint.text(g, this.field_22793, none, LIST_X + (LIST_W - UiPaint.width(this.field_22793, none)) / 2, 140, DIM);
         return;
      }
      g.method_44379(LIST_X + 8, top, LIST_X + LIST_W - 4, bottom);
      try {
         for (int i = 0; i < list.size(); i++) {
            Module module = list.get(i);
            int x = LIST_X + 16 + i % 2 * (CARD_W + 8);
            int y = top + i / 2 * (CARD_H + 8) - this.moduleScroll;
            if (y + CARD_H < top || y > bottom) {
               continue;
            }
            this.card(g, module, x, y, accent, top, bottom);
         }
      } finally {
         g.method_44380();
      }
      this.scrollbar(g, LIST_X + LIST_W - 6, top, bottom - top, this.moduleContent, this.moduleScroll);
   }

   private void card(class_332 g, Module module, int x, int y, int accent, int top, int bottom) {
      String key = "card" + System.identityHashCode(module);
      boolean on = module.isEnabled();
      boolean chosen = module == this.selected;
      double hover = this.animate(key, this.inside(x, y, CARD_W, CARD_H) && this.mouseY >= top && this.mouseY < bottom);
      double lit = this.animate(key + "on", on);
      UiPaint.round(g, x, y, CARD_W, CARD_H, 9, UiPaint.blend(CARD, accent, 0.05 * hover + 0.1 * lit));
      UiPaint.outline(g, x, y, CARD_W, CARD_H, 9, UiPaint.blend(BORDER, accent, chosen ? 0.9 : 0.25 * hover + 0.35 * lit));
      if (lit > 0.02) {
         UiPaint.round(g, x + 1, y + 10, 3, CARD_H - 20, 1, UiPaint.alpha(accent, lit));
      }
      UiPaint.strong(g, this.field_22793, this.ellipsize(module.getName(), CARD_W - 54), x + 11, y + 10, TEXT);
      String about = module.getDescription() == null ? "" : module.getDescription();
      UiPaint.smallText(g, this.field_22793, this.ellipsizeSmall(about.isEmpty() ? pretty(module.getCategory()) : about, CARD_W - 20), x + 11, y + 25, MUTED);
      String bind = keyName(module.getKeyCode());
      if (!bind.isEmpty()) {
         int bw = UiPaint.smallWidth(this.field_22793, bind) + 8;
         UiPaint.round(g, x + 11, y + 36, bw, 11, 3, UiPaint.blend(INSET, accent, 0.12));
         UiPaint.smallText(g, this.field_22793, bind, x + 15, y + 38, MUTED);
      }
      this.toggle(g, key + "switch", x + CARD_W - 38, y + 9, on, accent);
      boolean starred = favorite(module);
      double starHover = this.animate(key + "star", this.inside(x + CARD_W - 22, y + 32, 16, 16));
      if (starred || hover > 0.05) {
         UiPaint.star(g, (float)(x + CARD_W - 14), (float)(y + 40), 5.0F, starred ? 0xFFFACC15 : UiPaint.alpha(UiPaint.blend(DIM, TEXT, starHover), Math.max(hover, starHover)));
      }
      if (this.mouseY >= top && this.mouseY < bottom) {
         // The card first: the last area added wins a click, so the switch and the star go on top.
         this.hit(key, x, y, CARD_W, CARD_H, () -> this.select(module));
         this.hit(key + "star", x + CARD_W - 22, y + 32, 16, 16, () -> toggleFavorite(module));
         this.hit(key + "switch", x + CARD_W - 42, y + 5, 36, 20, module::toggle);
         if (this.inside(x, y, CARD_W, CARD_H) && UiPaint.width(this.field_22793, about) > CARD_W - 20) {
            this.tooltip = about;
         }
      }
   }

   private void select(Module module) {
      if (this.selected != module) {
         this.selected = module;
         this.settingScroll = 0;
         this.editing = null;
         this.listening = null;
      }
   }

   // ---- Settings ----------------------------------------------------------------------------------

   private void drawSettings(class_332 g) {
      int accent = this.accent();
      UiPaint.round(g, SET_X, 1, SET_W - 1, H - 2, 15, UiPaint.blend(WINDOW, 0xFF000000, 0.25));
      g.method_25294(SET_X, 1, SET_X + 16, H - 1, UiPaint.withOpacity(UiPaint.blend(WINDOW, 0xFF000000, 0.25)));
      g.method_25294(SET_X, 14, SET_X + 1, H - 14, UiPaint.withOpacity(BORDER));
      Module module = this.selected;
      int x = SET_X + 18;
      int w = SET_W - 36;
      if (module == null) {
         UiPaint.cube(g, SET_X + SET_W / 2, 200, 22, UiPaint.alpha(accent, 0.6), 0.95, 0.25);
         String pick = "Pick a module";
         UiPaint.strong(g, this.field_22793, pick, SET_X + (SET_W - UiPaint.width(this.field_22793, pick)) / 2, 240, TEXT);
         for (int i = 0; i < 3; i++) {
            String line = new String[]{"Click a card to see its settings.", "Right-click or the switch toggles it.", "Start typing to search."}[i];
            UiPaint.text(g, this.field_22793, line, SET_X + (SET_W - UiPaint.width(this.field_22793, line)) / 2, 258 + i * 14, MUTED);
         }
         return;
      }
      UiPaint.strong(g, this.field_22793, this.ellipsize(module.getName(), w - 50), x, 20, TEXT);
      UiPaint.smallText(g, this.field_22793, pretty(module.getCategory()) + (module.isEnabled() ? "  ·  on" : "  ·  off"), x, 33, module.isEnabled() ? GOOD : MUTED);
      this.toggle(g, "selSwitch", x + w - 30, 18, module.isEnabled(), accent);
      this.hit("selSwitch", x + w - 34, 14, 36, 20, module::toggle);

      int y = 52;
      String about = module.getDescription() == null ? "" : module.getDescription();
      for (String line : this.wrap(about, w)) {
         UiPaint.text(g, this.field_22793, line, x, y, MUTED);
         y += 12;
      }
      y += 6;
      // the module's own key
      boolean waiting = this.listening == module;
      String bind = waiting ? "Press a key..." : keyName(module.getKeyCode()).isEmpty() ? "None" : keyName(module.getKeyCode());
      UiPaint.text(g, this.field_22793, "Keybind", x, y + 7, TEXT);
      this.chip(g, "bind", bind, x + w - 110, y, 110, waiting, () -> this.listening = this.listening == module ? null : module);
      y += 28;
      this.pill(g, "reset", "Reset settings", x, y, w, 20, false, () -> {
         for (Setting setting : module.getSettings()) {
            setting.reset();
            setting.changed();
         }
      });
      y += 30;
      g.method_25294(x, y, x + w, y + 1, UiPaint.withOpacity(BORDER));
      y += 10;

      int top = y;
      int bottom = H - 14;
      g.method_44379(SET_X + 6, top, SET_X + SET_W - 4, bottom);
      int start = y - this.settingScroll;
      int cy = start;
      try {
         for (Setting setting : module.getSettings()) {
            if (!setting.isVisible() || setting instanceof KeybindSetting bindSetting && bindSetting.isModuleBind()) {
               continue;
            }
            cy = this.settingRow(g, setting, x, cy, w, accent, top, bottom);
         }
         if (cy == start) {
            UiPaint.text(g, this.field_22793, "This module has no settings.", x, cy + 4, DIM);
            cy += 20;
         }
      } finally {
         g.method_44380();
      }
      this.settingContent = cy - start;
      this.settingScroll = Math.clamp(this.settingScroll, 0, Math.max(0, this.settingContent - (bottom - top)));
      this.scrollbar(g, SET_X + SET_W - 8, top, bottom - top, this.settingContent, this.settingScroll);
   }

   private int settingRow(class_332 g, Setting setting, int x, int y, int w, int accent, int top, int bottom) {
      String id = "set" + System.identityHashCode(setting);
      boolean visible = y + 40 > top && y < bottom;
      boolean reachable = this.mouseY >= top && this.mouseY < bottom;
      String name = this.ellipsize(setting.getName(), w - 130);
      if (visible && this.inside(x, y, w, 20) && reachable && setting.getDescription() != null && !setting.getDescription().isEmpty()) {
         this.tooltip = setting.getDescription();
      }
      if (setting instanceof BooleanSetting toggle) {
         if (visible) {
            UiPaint.text(g, this.field_22793, name, x, y + 6, TEXT);
            this.toggle(g, id, x + w - 30, y + 2, toggle.get(), accent);
            if (reachable) {
               this.hit(id, x, y, w, 20, () -> {
                  toggle.toggle();
                  toggle.changed();
               });
            }
         }
         return y + 26;
      }
      if (setting instanceof NumberSetting number) {
         if (visible) {
            UiPaint.text(g, this.field_22793, name, x, y + 2, TEXT);
            UiPaint.right(g, this.field_22793, format(number.getDouble(), number.getStep()), x + w, y + 2, MUTED);
            double t = (number.getDouble() - number.getMin()) / Math.max(1.0E-9, number.getMax() - number.getMin());
            this.slider(g, id, x, y + 16, w, t, accent, reachable, value -> {
               double raw = number.getMin() + value * (number.getMax() - number.getMin());
               double step = number.getStep() <= 0 ? 0.01 : number.getStep();
               number.set(Math.clamp(Math.round(raw / step) * step, number.getMin(), number.getMax()));
               number.changed();
            });
         }
         return y + 34;
      }
      if (setting instanceof RangeSetting range) {
         if (visible) {
            UiPaint.text(g, this.field_22793, name, x, y + 2, TEXT);
            UiPaint.right(g, this.field_22793, format(range.getMinValue(), range.getStep()) + " - " + format(range.getMaxValue(), range.getStep()), x + w, y + 2, MUTED);
            double span = Math.max(1.0E-9, range.getMax() - range.getMin());
            double a = (range.getMinValue() - range.getMin()) / span;
            double b = (range.getMaxValue() - range.getMin()) / span;
            UiPaint.round(g, x, y + 19, w, 4, 2, INSET);
            UiPaint.round(g, (int)(x + a * w), y + 19, Math.max(2, (int)((b - a) * w)), 4, 2, accent);
            UiPaint.round(g, (int)(x + a * w) - 4, y + 17, 8, 8, 4, TEXT);
            UiPaint.round(g, (int)(x + b * w) - 4, y + 17, 8, 8, 4, TEXT);
            if (reachable) {
               this.hit(id, x - 4, y + 12, w + 8, 16, () -> {
                  double at = Math.clamp((this.mouseX - x) / w, 0.0, 1.0);
                  boolean low = Math.abs(at - a) <= Math.abs(at - b);
                  this.drag = () -> {
                     double value = range.getMin() + Math.clamp((this.mouseX - x) / w, 0.0, 1.0) * span;
                     double step = range.getStep() <= 0 ? 0.01 : range.getStep();
                     value = Math.round(value / step) * step;
                     if (low) {
                        range.setMinValue(Math.min(value, range.getMaxValue()));
                     } else {
                        range.setMaxValue(Math.max(value, range.getMinValue()));
                     }
                     range.changed();
                  };
                  this.drag.run();
               });
            }
         }
         return y + 34;
      }
      if (setting instanceof EnumSetting<?> choice) {
         if (visible) {
            UiPaint.text(g, this.field_22793, name, x, y + 6, TEXT);
            String value = pretty(String.valueOf(choice.get()));
            int cw = Math.min(150, Math.max(90, UiPaint.width(this.field_22793, value) + 34));
            int cx = x + w - cw;
            double hover = this.animate(id, reachable && this.inside(cx, y, cw, 20));
            UiPaint.round(g, cx, y, cw, 20, 6, UiPaint.blend(INSET, accent, 0.08 + hover * 0.12));
            UiPaint.outline(g, cx, y, cw, 20, 6, UiPaint.blend(BORDER, accent, hover * 0.6));
            UiPaint.text(g, this.field_22793, "<", cx + 6, y + 6, MUTED);
            UiPaint.text(g, this.field_22793, ">", cx + cw - 11, y + 6, MUTED);
            String shown = this.ellipsize(value, cw - 30);
            UiPaint.text(g, this.field_22793, shown, cx + (cw - UiPaint.width(this.field_22793, shown)) / 2, y + 6, TEXT);
            if (reachable) {
               this.hit(id + "prev", cx, y, cw / 2, 20, () -> {
                  choice.previous();
                  choice.changed();
               });
               this.hit(id + "next", cx + cw / 2, y, cw - cw / 2, 20, () -> {
                  choice.next();
                  choice.changed();
               });
            }
         }
         return y + 26;
      }
      if (setting instanceof StringSetting text) {
         if (visible) {
            UiPaint.text(g, this.field_22793, name, x, y + 2, TEXT);
            boolean typing = this.editing == text;
            UiPaint.round(g, x, y + 14, w, 20, 6, UiPaint.blend(INSET, accent, typing ? 0.08 : 0.02));
            UiPaint.outline(g, x, y + 14, w, 20, 6, UiPaint.blend(BORDER, accent, typing ? 0.9 : 0.0));
            String value = text.get() == null ? "" : text.get();
            boolean caret = typing && System.currentTimeMillis() / 500 % 2 == 0;
            String shown = value.isEmpty() && !typing ? "Click to type" : this.ellipsizeLeft(value, w - 16) + (caret ? "_" : "");
            UiPaint.text(g, this.field_22793, shown, x + 7, y + 20, value.isEmpty() && !typing ? DIM : TEXT);
            if (reachable) {
               this.hit(id, x, y + 14, w, 20, () -> this.editing = text);
            }
         }
         return y + 40;
      }
      if (setting instanceof KeybindSetting key) {
         if (visible) {
            UiPaint.text(g, this.field_22793, name, x, y + 6, TEXT);
            boolean waiting = this.listening == key;
            String label = waiting ? "Press a key..." : keyName(key.getKey()).isEmpty() ? "None" : keyName(key.getKey());
            this.chip(g, id, label, x + w - 110, y, 110, waiting && reachable, () -> this.listening = this.listening == key ? null : key);
         }
         return y + 26;
      }
      if (setting instanceof ColorSetting color) {
         boolean open = this.expanded.contains(color);
         if (visible) {
            UiPaint.text(g, this.field_22793, name, x, y + 6, TEXT);
            int swatch = color.t() << 24 | color.y() << 16 | color.qq() << 8 | color.d();
            UiPaint.round(g, x + w - 46, y + 2, 16, 16, 4, 0xFF000000 | swatch);
            UiPaint.outline(g, x + w - 46, y + 2, 16, 16, 4, BORDER);
            UiPaint.text(g, this.field_22793, open ? "-" : "+", x + w - 18, y + 6, MUTED);
            if (reachable) {
               this.hit(id, x, y, w, 20, () -> {
                  if (!this.expanded.remove(color)) {
                     this.expanded.add(color);
                  }
               });
            }
         }
         int next = y + 26;
         if (open) {
            String[] channels = {"Red", "Green", "Blue", "Alpha"};
            int[] tints = {0xFFEF4444, 0xFF22C55E, 0xFF3B82F6, 0xFFCBD5E1};
            for (int c = 0; c < 4; c++) {
               int channel = c;
               int value = new int[]{color.y(), color.qq(), color.d(), color.t()}[c];
               if (next + 26 > top && next < bottom) {
                  UiPaint.smallText(g, this.field_22793, channels[c], x + 8, next + 2, MUTED);
                  UiPaint.smallText(g, this.field_22793, Integer.toString(value), x + w - UiPaint.smallWidth(this.field_22793, Integer.toString(value)), next + 2, MUTED);
                  this.slider(g, id + "c" + c, x + 8, next + 13, w - 8, value / 255.0, tints[c], reachable, t -> {
                     int[] rgba = {color.y(), color.qq(), color.d(), color.t()};
                     rgba[channel] = (int)Math.round(t * 255.0);
                     color.c(rgba[0], rgba[1], rgba[2], rgba[3]);
                  });
               }
               next += 26;
            }
         }
         return next;
      }
      // Lists of items, blocks, entities and macros only have the old pop-up editors.
      if (visible) {
         UiPaint.text(g, this.field_22793, name, x, y + 6, TEXT);
         this.chip(g, id, "Edit in classic", x + w - 110, y, 110, false, () -> this.classic = true);
      }
      return y + 26;
   }

   // ---- Controls ----------------------------------------------------------------------------------

   private void toggle(class_332 g, String id, int x, int y, boolean on, int accent) {
      double t = this.animate(id + "knob", on);
      UiPaint.round(g, x, y, 28, 16, 8, UiPaint.blend(0xFF2A2F3D, accent, t));
      UiPaint.round(g, (int)Math.round(x + 2 + t * 12), y + 2, 12, 12, 6, UiPaint.blend(0xFFB8BFCC, 0xFFFFFFFF, t));
   }

   private void slider(class_332 g, String id, int x, int y, int w, double t, int color, boolean reachable, java.util.function.DoubleConsumer set) {
      t = Math.clamp(t, 0.0, 1.0);
      double hover = this.animate(id, reachable && this.inside(x - 4, y - 6, w + 8, 16));
      UiPaint.round(g, x, y + 2, w, 4, 2, INSET);
      UiPaint.round(g, x, y + 2, Math.max(4, (int)(t * w)), 4, 2, color);
      int knob = (int)(x + t * w);
      int size = (int)Math.round(8 + hover * 2);
      UiPaint.round(g, knob - size / 2, y + 4 - size / 2, size, size, size / 2, TEXT);
      if (reachable) {
         this.hit(id, x - 4, y - 6, w + 8, 16, () -> {
            this.drag = () -> set.accept(Math.clamp((this.mouseX - x) / w, 0.0, 1.0));
            this.drag.run();
         });
      }
   }

   private void chip(class_332 g, String id, String label, int x, int y, int w, boolean active, Runnable action) {
      int accent = this.accent();
      double hover = this.animate(id, this.inside(x, y, w, 20));
      UiPaint.round(g, x, y, w, 20, 6, UiPaint.blend(INSET, accent, (active ? 0.3 : 0.06) + hover * 0.14));
      UiPaint.outline(g, x, y, w, 20, 6, UiPaint.blend(BORDER, accent, active ? 1.0 : hover * 0.6));
      String shown = this.ellipsize(label, w - 10);
      UiPaint.text(g, this.field_22793, shown, x + (w - UiPaint.width(this.field_22793, shown)) / 2, y + 6, active ? TEXT : MUTED);
      this.hit(id, x, y, w, 20, action);
   }

   private void pill(class_332 g, String id, String label, int x, int y, int w, int h, boolean primary, Runnable action) {
      int accent = this.accent();
      double hover = this.animate(id, this.inside(x, y, w, h));
      UiPaint.round(g, x, y, w, h, 7, UiPaint.blend(CARD, accent, (primary ? 0.55 : 0.06) + hover * 0.16));
      UiPaint.outline(g, x, y, w, h, 7, UiPaint.blend(BORDER, accent, (primary ? 0.5 : 0.0) + hover * 0.35));
      String shown = this.ellipsize(label, w - 12);
      UiPaint.text(g, this.field_22793, shown, x + (w - UiPaint.width(this.field_22793, shown)) / 2, y + (h - 7) / 2, TEXT);
      this.hit(id, x, y, w, h, action);
   }

   private void scrollbar(class_332 g, int x, int y, int h, int content, int scroll) {
      if (content > h) {
         int thumb = Math.max(20, h * h / content);
         int offset = (int)((long)(h - thumb) * scroll / Math.max(1, content - h));
         UiPaint.round(g, x, y + offset, 3, thumb, 1, UiPaint.alpha(MUTED, 0.45));
      }
   }

   private void drawTooltip(class_332 g, String text) {
      List<String> lines = this.wrap(text, 240);
      int w = 0;
      for (String line : lines) {
         w = Math.max(w, UiPaint.width(this.field_22793, line));
      }
      int x = (int)Math.min(this.mouseX + 12, W - w - 22);
      int y = (int)Math.min(this.mouseY + 14, H - lines.size() * 11 - 16);
      UiPaint.round(g, x, y, w + 14, lines.size() * 11 + 10, 6, 0xF0161922);
      UiPaint.outline(g, x, y, w + 14, lines.size() * 11 + 10, 6, BORDER);
      for (int i = 0; i < lines.size(); i++) {
         UiPaint.text(g, this.field_22793, lines.get(i), x + 7, y + 6 + i * 11, TEXT);
      }
   }

   // ---- Input -------------------------------------------------------------------------------------

   @Override
   public boolean method_25402(class_11909 event, boolean doubled) {
      if (this.classic) {
         Hit back = this.at(event.comp_4798(), event.comp_4799());
         if (back != null) {
            back.action.run();
            return true;
         }
         return super.method_25402(event, doubled);
      }
      if (this.closing) {
         return true;
      }
      double x = (event.comp_4798() - this.originX) / this.scale;
      double y = (event.comp_4799() - this.originY) / this.scale;
      this.mouseX = x;
      this.mouseY = y;
      Hit target = this.at(x, y);
      String id = target == null ? "" : target.id;
      if (!id.equals("search")) {
         this.searchFocused = false;
      }
      if (this.editing != null && !id.equals("set" + System.identityHashCode(this.editing))) {
         this.editing = null;
      }
      if (this.listening != null && !id.equals("bind") && !id.startsWith("set")) {
         this.listening = null;
      }
      if (event.method_74245() == 1) {
         // right-click on a card toggles the module
         if (id.startsWith("card")) {
            for (Module module : this.shown()) {
               if (id.equals("card" + System.identityHashCode(module)) || id.equals("card" + System.identityHashCode(module) + "switch")) {
                  module.toggle();
               }
            }
         }
         return true;
      }
      if (event.method_74245() != 0 || target == null) {
         return true;
      }
      if (doubled && id.startsWith("card") && !id.endsWith("switch")) {
         for (Module module : this.shown()) {
            if (id.equals("card" + System.identityHashCode(module))) {
               module.toggle();
            }
         }
         return true;
      }
      target.action.run();
      return true;
   }

   @Override
   public boolean method_25403(class_11909 event, double dx, double dy) {
      if (this.classic) {
         return super.method_25403(event, dx, dy);
      }
      if (this.drag != null) {
         this.mouseX = (event.comp_4798() - this.originX) / this.scale;
         this.mouseY = (event.comp_4799() - this.originY) / this.scale;
         this.drag.run();
      }
      return true;
   }

   @Override
   public boolean method_25406(class_11909 event) {
      if (this.classic) {
         return super.method_25406(event);
      }
      this.drag = null;
      return true;
   }

   @Override
   public boolean method_25401(double x, double y, double horizontal, double vertical) {
      if (this.classic) {
         return super.method_25401(x, y, horizontal, vertical);
      }
      double lx = (x - this.originX) / this.scale;
      int step = (int)Math.round(vertical * 36.0);
      if (lx >= SET_X) {
         this.settingScroll = Math.clamp(this.settingScroll - step, 0, Math.max(0, this.settingContent - 200));
      } else if (lx >= LIST_X) {
         this.moduleScroll = Math.clamp(this.moduleScroll - step, 0, Math.max(0, this.moduleContent));
      }
      return true;
   }

   @Override
   public boolean method_25404(class_11908 event) {
      if (this.classic) {
         if (event.comp_4795() == 256) {
            this.classic = false;
            return true;
         }
         return super.method_25404(event);
      }
      int key = event.comp_4795();
      if (this.listening != null) {
         int bound = key == 256 || key == 259 || key == 261 ? -1 : key;
         if (this.listening instanceof Module module) {
            module.setKeyCode(bound);
            markDirty();
         } else if (this.listening instanceof KeybindSetting setting) {
            setting.setKey(bound);
            setting.changed();
         }
         this.listening = null;
         return true;
      }
      if (this.editing != null) {
         String value = this.editing.get() == null ? "" : this.editing.get();
         if (key == 256 || key == 257 || key == 335) {
            this.editing = null;
         } else if (key == 259 && !value.isEmpty()) {
            this.editing.set(value.substring(0, value.length() - 1));
            this.editing.changed();
         }
         return true;
      }
      if (key == 256) {
         if (this.searchFocused || !this.query.isEmpty()) {
            this.query = "";
            this.searchFocused = false;
         } else {
            this.method_25419();
         }
         return true;
      }
      if (key == 259 && !this.query.isEmpty()) {
         this.query = this.query.substring(0, this.query.length() - 1);
         this.moduleScroll = 0;
         return true;
      }
      if ((key == 257 || key == 335) && !this.query.isEmpty()) {
         List<Module> list = this.shown();
         if (!list.isEmpty()) {
            this.select(list.get(0));
         }
         return true;
      }
      // Not handed to the old panels, which would type into their hidden search box.
      return false;
   }

   @Override
   public boolean method_25400(class_11905 event) {
      if (this.classic) {
         return super.method_25400(event);
      }
      if (!event.method_74227() || this.listening != null) {
         return true;
      }
      String typed = event.method_74226();
      if (this.editing != null) {
         String value = this.editing.get() == null ? "" : this.editing.get();
         if (value.length() < 256) {
            this.editing.set(value + typed);
            this.editing.changed();
         }
         return true;
      }
      // Typing anywhere searches.
      if (this.query.length() < 40 && !(this.query.isEmpty() && typed.isBlank())) {
         this.query = this.query + typed;
         this.searchFocused = true;
         this.moduleScroll = 0;
      }
      return true;
   }

   @Override
   public void method_25419() {
      if (this.classic) {
         this.classic = false;
      }
      this.editing = null;
      this.listening = null;
      this.searchFocused = false;
      if (!this.closing) {
         this.closing = true;
      }
   }

   private Hit at(double x, double y) {
      for (int i = this.hits.size() - 1; i >= 0; i--) {
         Hit hit = this.hits.get(i);
         if (x >= hit.x && x < hit.x + hit.w && y >= hit.y && y < hit.y + hit.h) {
            return hit;
         }
      }
      return null;
   }

   // ---- Helpers -----------------------------------------------------------------------------------

   private static java.nio.file.Path favoritesFile() {
      return net.minecraft.class_310.method_1551().field_1697.toPath().resolve("config").resolve("rex-one-favorites.txt");
   }

   private static Set<String> favorites() {
      if (favorites == null) {
         favorites = new java.util.LinkedHashSet<>();
         try {
            java.nio.file.Path file = favoritesFile();
            if (java.nio.file.Files.exists(file)) {
               for (String line : java.nio.file.Files.readAllLines(file)) {
                  if (!line.isBlank()) {
                     favorites.add(line.trim());
                  }
               }
            }
         } catch (java.io.IOException | RuntimeException ignored) {
         }
      }
      return favorites;
   }

   private static boolean favorite(Module module) {
      return favorites().contains(String.valueOf(module.getName()));
   }

   private static void toggleFavorite(Module module) {
      String name = String.valueOf(module.getName());
      if (!favorites().remove(name)) {
         favorites().add(name);
      }
      try {
         java.nio.file.Path file = favoritesFile();
         java.nio.file.Files.createDirectories(file.getParent());
         java.nio.file.Files.write(file, favorites());
      } catch (java.io.IOException | RuntimeException ignored) {
      }
   }

   private static List<Module> modules() {
      KryptonClient krypton = KryptonClient.instance;
      return krypton == null || krypton.moduleManager == null ? List.of() : krypton.moduleManager.all();
   }

   private static List<Module> byCategory(Category category) {
      List<Module> out = new ArrayList<>();
      for (Module module : modules()) {
         if (module.getCategory() == category) {
            out.add(module);
         }
      }
      return out;
   }

   /** The menu module is on while the menu shows; leaving for another screen turns it off, as closing does. */
   private static void menuClosed() {
      KryptonClient krypton = KryptonClient.instance;
      if (krypton != null && krypton.moduleManager != null) {
         KryptonPlus menu = krypton.moduleManager.get(KryptonPlus.class);
         if (menu != null) {
            menu.setEnabledSilently(false);
         }
      }
   }

   private static void markDirty() {
      KryptonClient krypton = KryptonClient.instance;
      if (krypton != null && krypton.configManager != null) {
         krypton.configManager.markDirty();
      }
   }

   private static String pretty(Category category) {
      if (category == null) {
         return "Other";
      }
      return category == Category.BASEFINDING ? "Base Finding" : category.name.isEmpty() ? category.name() : category.name;
   }

   /** SOME_VALUE -> Some Value. */
   private static String pretty(String raw) {
      if (raw == null || raw.isEmpty() || !raw.equals(raw.toUpperCase(Locale.ROOT))) {
         return raw;
      }
      StringBuilder out = new StringBuilder();
      for (String part : raw.split("_")) {
         if (!part.isEmpty()) {
            out.append(out.length() == 0 ? "" : " ").append(part.charAt(0)).append(part.substring(1).toLowerCase(Locale.ROOT));
         }
      }
      return out.toString();
   }

   private static String format(double value, double step) {
      if (step >= 1.0 && Math.abs(value - Math.rint(value)) < 1.0E-9) {
         return Long.toString(Math.round(value));
      }
      int decimals = step >= 0.1 ? 1 : step >= 0.01 ? 2 : 3;
      return String.format(Locale.ROOT, "%." + decimals + "f", value);
   }

   /** A short name for a GLFW key code, or "" for none. */
   static String keyName(int key) {
      if (key <= 0) {
         return "";
      }
      if (key >= 65 && key <= 90 || key >= 48 && key <= 57) {
         return String.valueOf((char)key);
      }
      if (key >= 290 && key <= 314) {
         return "F" + (key - 289);
      }
      if (key >= 320 && key <= 329) {
         return "Num " + (key - 320);
      }
      return switch (key) {
         case 32 -> "Space";
         case 39 -> "'";
         case 44 -> ",";
         case 45 -> "-";
         case 46 -> ".";
         case 47 -> "/";
         case 59 -> ";";
         case 61 -> "=";
         case 91 -> "[";
         case 92 -> "\\";
         case 93 -> "]";
         case 96 -> "`";
         case 257 -> "Enter";
         case 258 -> "Tab";
         case 260 -> "Insert";
         case 262 -> "Right";
         case 263 -> "Left";
         case 264 -> "Down";
         case 265 -> "Up";
         case 266 -> "Page Up";
         case 267 -> "Page Down";
         case 268 -> "Home";
         case 269 -> "End";
         case 280 -> "Caps Lock";
         case 340 -> "Left Shift";
         case 341 -> "Left Ctrl";
         case 342 -> "Left Alt";
         case 344 -> "Right Shift";
         case 345 -> "Right Ctrl";
         case 346 -> "Right Alt";
         default -> "Key " + key;
      };
   }

   private double animate(String id, boolean active) {
      double value = this.motion.getOrDefault(id, active ? 1.0 : 0.0);
      value += ((active ? 1 : 0) - value) * (1.0 - Math.exp(-this.seconds * 16.0));
      this.motion.put(id, value);
      return value;
   }

   private void hit(String id, int x, int y, int w, int h, Runnable action) {
      if (!this.closing && w > 0 && h > 0) {
         this.hits.add(new Hit(id, x, y, w, h, action));
      }
   }

   private boolean inside(int x, int y, int w, int h) {
      return this.mouseX >= x && this.mouseX < x + w && this.mouseY >= y && this.mouseY < y + h;
   }

   private String ellipsize(String text, int max) {
      if (text == null) {
         return "";
      }
      if (UiPaint.width(this.field_22793, text) <= max) {
         return text;
      }
      while (!text.isEmpty() && UiPaint.width(this.field_22793, text + "...") > max) {
         text = text.substring(0, text.length() - 1);
      }
      return text + "...";
   }

   private String ellipsizeSmall(String text, int max) {
      if (text == null) {
         return "";
      }
      if (UiPaint.smallWidth(this.field_22793, text) <= max) {
         return text;
      }
      while (!text.isEmpty() && UiPaint.smallWidth(this.field_22793, text + "...") > max) {
         text = text.substring(0, text.length() - 1);
      }
      return text + "...";
   }

   /** The end of a long value, which is the part being typed. */
   private String ellipsizeLeft(String text, int max) {
      if (UiPaint.width(this.field_22793, text) <= max) {
         return text;
      }
      while (!text.isEmpty() && UiPaint.width(this.field_22793, "..." + text) > max) {
         text = text.substring(1);
      }
      return "..." + text;
   }

   private List<String> wrap(String value, int max) {
      List<String> lines = new ArrayList<>();
      if (value == null || value.isEmpty()) {
         return lines;
      }
      for (String paragraph : value.split("\\n")) {
         String line = "";
         for (String word : paragraph.split(" ")) {
            if (!line.isEmpty() && UiPaint.width(this.field_22793, line + " " + word) > max) {
               lines.add(line);
               line = "";
            }
            line = line + (line.isEmpty() ? "" : " ") + word;
         }
         lines.add(line);
      }
      return lines;
   }

   private static double smooth(double value) {
      return value * value * (3.0 - 2.0 * value);
   }

   @Environment(EnvType.CLIENT)
   private record Hit(String id, int x, int y, int w, int h, Runnable action) {
   }
}
