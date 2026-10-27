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
import dev.krypton.util.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.class_332;

/**
 * Everything the duper is doing, on one panel: the pattern and what it is for, the phase and what it
 * is waiting for, the stack it picked and where, the count as it moves, every pattern already tried
 * with its result, and the last few things that happened.
 *
 * <p>It draws with the game's own fill and text calls rather than the client's painter. The painter
 * sets up and tears down renderer state around every batch, which is right where it is meant to run
 * — the HUD pass — but wrong at the tail of a screen's own render: it left the projection and clip
 * changed under the screen that was still drawing, which is what zoomed and cut the chat screen.
 */
public final class DuperHud extends Module {
   public static DuperHud instance;

   public final EnumSetting<HudAnchor> anchor = new EnumSetting<>("Anchor", HudAnchor.TOP_LEFT, HudAnchor.class);
   public final NumberSetting offsetX = new NumberSetting("X", 0.0, 400.0, 6.0, 1.0);
   public final NumberSetting offsetY = new NumberSetting("Y", 0.0, 400.0, 6.0, 1.0);
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

   private static final int PAD = HudPanel.PAD;

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
         Object client = Bridge.client();
         if (Bridge.field(client, "field_1724") == null) {
            return;
         }
         this.panel(graphics, HudPanel.font(), duper);
      } catch (LinkageError | Exception failure) {
         System.err.println("[ONE] Duper HUD: " + failure);
      }
   }

   private void panel(class_332 graphics, Object font, Duper duper) throws Exception {
      List<String[]> rows = this.rows(duper);
      List<String> log = this.log(duper);
      int line = HudPanel.lineHeight(font);
      int step = line + 3;
      int panelWidth = this.width.getInt();
      int header = line + 11;
      int body = rows.size() * step + (duper.enabled ? step : 0);
      int extra = log.isEmpty() ? 0 : 5 + log.size() * (line + 1);
      int panelHeight = header + body + extra + PAD;

      int[] origin = HudPanel.origin((HudAnchor)this.anchor.get(), this.offsetX.getInt(), this.offsetY.getInt(), panelWidth, panelHeight);
      int x = origin[0];
      int y = origin[1];
      Color base = this.text.m();
      int tint = HudPanel.argb(this.accent.m());
      int label = HudPanel.argb(base);
      int dim = HudPanel.argb(HudPanel.fade(base, 155));
      int faint = HudPanel.argb(HudPanel.fade(base, 40));

      HudPanel.frame(graphics, x, y, panelWidth, panelHeight, HudPanel.argb(this.background.m()), tint, faint);
      HudPanel.string(graphics, font, "DUPER", x + PAD, y + 6, label);
      String head = duper.enabled ? String.valueOf(duper.current()) : String.valueOf(duper.pattern.get());
      HudPanel.string(graphics, font, head, x + panelWidth - PAD - HudPanel.width(font, head), y + 6, tint);

      int row = y + header;
      for (String[] entry : rows) {
         int colour = entry.length > 2 && entry[2].equals("gain") ? HudPanel.argb(this.good.m()) : label;
         HudPanel.row(graphics, font, entry[0], entry[1], x, row, panelWidth, dim, colour);
         row += step;
      }

      if (duper.enabled) {
         double done = duper.burstsPlanned() <= 0 ? 0.0 : 1.0 - (double)duper.burstsLeft() / duper.burstsPlanned();
         HudPanel.bar(graphics, x + PAD, row + 2, panelWidth - PAD * 2, done, faint, tint);
         row += step;
      }

      if (!log.isEmpty()) {
         HudPanel.fill(graphics, x + PAD, row, x + panelWidth - PAD, row + 1, faint);
         row += 4;
         for (String entry : log) {
            HudPanel.string(graphics, font, HudPanel.clip(font, entry, panelWidth - PAD * 2), x + PAD, row, dim);
            row += line + 1;
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
      int stated = duper.confirmed() - duper.before();
      rows.add(new String[]{
         duper.serverSpoke() ? "server says" : "server quiet",
         duper.serverSpoke() ? duper.confirmed() + "  (" + (stated >= 0 ? "+" : "") + stated + ")" : "predicted only",
         stated > 0 && duper.serverSpoke() ? "gain" : ""
      });
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

}
