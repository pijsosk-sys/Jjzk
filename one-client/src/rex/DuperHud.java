package rex;

import dev.krypton.core.event.EventHandler;
import dev.krypton.core.event.Render2DEvent;
import dev.krypton.core.module.Category;
import dev.krypton.core.module.Module;
import dev.krypton.core.setting.BooleanSetting;
import dev.krypton.core.setting.ColorSetting;
import dev.krypton.core.setting.EnumSetting;
import dev.krypton.core.setting.NumberSetting;
import dev.krypton.core.setting.Setting;
import dev.krypton.render.GuiPainter;
import dev.krypton.util.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.class_332;

/**
 * Everything the duper is doing, on one panel: the pattern and what it is for, the phase and what it
 * is waiting for, the stack it picked and where, the count as it moves, every pattern already tried
 * with its result, and the last few things that happened. It draws over an open container, because
 * that is where the duper works.
 */
public final class DuperHud extends Module {
   public static DuperHud instance;

   public final EnumSetting<HudAnchor> anchor = new EnumSetting<>("Anchor", HudAnchor.TOP_LEFT, HudAnchor.class);
   public final NumberSetting offsetX = new NumberSetting("X", 0.0, 400.0, 8.0, 1.0);
   public final NumberSetting offsetY = new NumberSetting("Y", 0.0, 400.0, 8.0, 1.0);
   public final NumberSetting width = new NumberSetting("Width", 150.0, 420.0, 250.0, 10.0);
   public final NumberSetting logLines = new NumberSetting("Log Lines", 0.0, 8.0, 5.0, 1.0);
   public final BooleanSetting showIdle = new BooleanSetting("Show When Idle", true)
      .describe("Keeps the panel up with the current pattern while the duper is off.");
   public final BooleanSetting overContainers = new BooleanSetting("Over Containers", true)
      .describe("Draws the panel again after an open screen so a container cannot hide it.");
   public final ColorSetting background = new ColorSetting("Background", 18, 11, 26, 225);
   public final ColorSetting accent = new ColorSetting("Accent", 255, 140, 55, 255);
   public final ColorSetting text = new ColorSetting("Text", 242, 226, 208, 255);
   public final ColorSetting good = new ColorSetting("Gain", 110, 225, 130, 255);

   private static final int PAD = 9;

   public DuperHud() {
      super("Duper HUD", "Live panel for the duper: pattern, phase, slot, count and results.", -1, Category.RENDER);
      this.addSettings(
         new Setting[]{
            this.anchor, this.offsetX, this.offsetY, this.width, this.logLines, this.showIdle,
            this.overContainers, this.background, this.accent, this.text, this.good
         }
      );
      this.enabled = true;
      instance = this;
   }

   /** Called from the screen mixin so an open container cannot cover the panel. */
   public static void overlay(class_332 graphics, float delta) {
      try {
         DuperHud hud = instance;
         if (hud != null && hud.enabled && hud.overContainers.get()) {
            hud.draw(graphics);
         }
      } catch (Throwable escaped) {
         // Nothing may travel from here into the screen's own render pass.
         System.err.println("[ONE] Duper HUD overlay: " + escaped);
      }
   }

   @EventHandler
   public void onDuperHud(Render2DEvent event) {
      try {
         if (Bridge.field(Bridge.client(), "field_1755") != null && this.overContainers.get()) {
            return;
         }
      } catch (LinkageError | Exception unknown) {
      }
      this.draw(event.sqx);
   }

   private void draw(class_332 graphics) {
      Duper duper = Duper.instance;
      if (duper == null || !duper.enabled && !this.showIdle.get()) {
         return;
      }
      try {
         if (Bridge.field(Bridge.client(), "field_1724") == null) {
            return;
         }
      } catch (LinkageError | Exception unknown) {
         return;
      }
      GuiPainter painter = GuiPainter.wca;
      if (painter == null || painter.at) {
         // No painter yet, or something else holds it and a second begin would throw.
         return;
      }
      painter.tne(graphics);
      try {
         this.panel(painter, duper);
      } catch (LinkageError | Exception failure) {
         System.err.println("[ONE] Duper HUD: " + failure);
      } finally {
         painter.edi();
      }
   }

   private void panel(GuiPainter painter, Duper duper) throws Exception {
      List<String[]> rows = this.rows(duper);
      List<String> log = this.log(duper);
      double line = Math.max(9.0, painter.sqz());
      double step = line + 4.0;
      double panelWidth = this.width.getDouble();
      double header = line + 14.0;
      double body = rows.size() * step + (duper.enabled ? step : 0.0);
      double extra = log.isEmpty() ? 0.0 : step * 0.6 + log.size() * (line + 2.0);
      double panelHeight = header + body + extra + PAD;

      double[] origin = this.origin(panelWidth, panelHeight);
      double x = origin[0];
      double y = origin[1];
      Color back = this.background.m();
      Color tint = this.accent.m();
      Color label = this.text.m();
      Color dim = new Color(label.red, label.green, label.blue, 150);

      painter.fhp(back, x, y, x + panelWidth, y + panelHeight, 5.0);
      painter.nat(x, y, panelWidth, 2.0, tint);
      painter.sxz("DUPER", x + PAD, y + 7.0, label, true);
      String head = duper.enabled ? String.valueOf(duper.current()) : String.valueOf(duper.pattern.get());
      painter.sxz(head, x + panelWidth - PAD - painter.yuj(head), y + 7.0, tint, true);

      double row = y + header;
      for (String[] entry : rows) {
         painter.sxz(entry[0], x + PAD, row, dim, true);
         Color colour = entry.length > 2 && entry[2].equals("gain") ? this.good.m() : label;
         painter.sxz(entry[1], x + panelWidth - PAD - painter.yuj(entry[1]), row, colour, true);
         row += step;
      }

      if (duper.enabled) {
         double inner = panelWidth - PAD * 2;
         double done = duper.burstsPlanned() <= 0 ? 0.0 : 1.0 - (double)duper.burstsLeft() / duper.burstsPlanned();
         painter.nat(x + PAD, row + 2.0, inner, 3.0, new Color(label.red, label.green, label.blue, 45));
         painter.nat(x + PAD, row + 2.0, inner * Math.max(0.0, Math.min(1.0, done)), 3.0, tint);
         row += step;
      }

      if (!log.isEmpty()) {
         row += step * 0.6 - 2.0;
         painter.nat(x + PAD, row - 4.0, panelWidth - PAD * 2, 1.0, new Color(label.red, label.green, label.blue, 40));
         for (String entry : log) {
            painter.sxz(this.clip(painter, entry, panelWidth - PAD * 2), x + PAD, row, dim, true);
            row += line + 2.0;
         }
      }
   }

   /** Label and value per row, with "gain" on anything that went up. */
   private List<String[]> rows(Duper duper) {
      List<String[]> rows = new ArrayList<>();
      if (!duper.enabled) {
         rows.add(new String[]{"state", "idle"});
         rows.add(new String[]{"target", duper.target.get() == DuperTarget.HELD ? "held stack" : "slot " + duper.slot.getInt()});
         rows.add(new String[]{"plan", duper.clicks.getInt() + " x " + duper.bursts.getInt() + " clicks"});
         rows.add(new String[]{"what", ((DuperPattern)duper.pattern.get()).what});
         return rows;
      }
      int change = duper.live() - duper.before();
      rows.add(new String[]{"phase", duper.phase() == Duper.WAITING ? "waiting" : duper.phase() == Duper.RUNNING ? "running" : "settling"});
      if (duper.phase() == Duper.WAITING && !duper.blocked().isEmpty()) {
         rows.add(new String[]{"needs", duper.blocked()});
      }
      rows.add(new String[]{"run", duper.run() + " / " + duper.planned()});
      rows.add(new String[]{"item", duper.itemName()});
      rows.add(new String[]{"slot", duper.slotUsed() + "  window " + duper.windowUsed()});
      rows.add(new String[]{"count", duper.before() + " -> " + duper.live(), change > 0 ? "gain" : ""});
      rows.add(new String[]{"change", (change >= 0 ? "+" : "") + change, change > 0 ? "gain" : ""});
      rows.add(new String[]{"sent", String.valueOf(duper.sent())});
      if (duper.phase() == Duper.SETTLING) {
         rows.add(new String[]{"settling", duper.ticksLeft() + " ticks"});
      }
      for (Map.Entry<DuperPattern, Integer> result : duper.results().entrySet()) {
         int delta = result.getValue();
         rows.add(new String[]{result.getKey().name().toLowerCase(Locale.ROOT), (delta >= 0 ? "+" : "") + delta, delta > 0 ? "gain" : ""});
      }
      return rows;
   }

   private List<String> log(Duper duper) {
      int wanted = this.logLines.getInt();
      if (wanted <= 0) {
         return List.of();
      }
      List<String> lines = duper.lines();
      return lines.size() <= wanted ? lines : lines.subList(lines.size() - wanted, lines.size());
   }

   private String clip(GuiPainter painter, String line, double room) {
      String cut = line;
      while (cut.length() > 4 && painter.yuj(cut) > room) {
         cut = cut.substring(0, cut.length() - 2);
      }
      return cut.equals(line) ? line : cut + "...";
   }

   /** Top-left corner for the chosen anchor, in the same scaled space the painter draws in. */
   private double[] origin(double panelWidth, double panelHeight) throws Exception {
      Object window = Bridge.call(Bridge.client(), "method_22683");
      double screenWidth = ((Number)Bridge.call(window, "method_4486")).doubleValue();
      double screenHeight = ((Number)Bridge.call(window, "method_4502")).doubleValue();
      double x = this.offsetX.getDouble();
      double y = this.offsetY.getDouble();
      HudAnchor corner = (HudAnchor)this.anchor.get();
      if (corner == HudAnchor.TOP_RIGHT || corner == HudAnchor.BOTTOM_RIGHT) {
         x = screenWidth - panelWidth - x;
      }
      if (corner == HudAnchor.BOTTOM_LEFT || corner == HudAnchor.BOTTOM_RIGHT) {
         y = screenHeight - panelHeight - y;
      }
      return new double[]{Math.max(0.0, x), Math.max(0.0, y)};
   }
}
