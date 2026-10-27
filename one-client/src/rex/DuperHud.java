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
 * What the duper is doing, in four lines: the pattern, where the run is, the count with the change
 * called out, and a bar. Detail turns the rest back on — the slot and window, every pattern already
 * tried, and the log. It draws with the game's own fill and text calls, so nothing touches renderer
 * state and a panel at the tail of a screen's render leaves that screen as it found it.
 */
public final class DuperHud extends Module {
   public static DuperHud instance;

   public final EnumSetting<HudAnchor> anchor = new EnumSetting<>("Anchor", HudAnchor.TOP_LEFT, HudAnchor.class);
   public final NumberSetting offsetX = new NumberSetting("X", 0.0, 400.0, 4.0, 1.0);
   public final NumberSetting offsetY = new NumberSetting("Y", 0.0, 400.0, 4.0, 1.0);
   public final NumberSetting width = new NumberSetting("Width", 90.0, 400.0, 142.0, 2.0);
   public final BooleanSetting detail = new BooleanSetting("Detail", false)
      .describe("Adds the slot and window, every pattern already tried, and the log.");
   public final NumberSetting logLines = new NumberSetting("Log Lines", 0.0, 8.0, 2.0, 1.0)
      .describe("Only with Detail on.");
   public final BooleanSetting showIdle = new BooleanSetting("Show When Idle", true);
   public final BooleanSetting overContainers = new BooleanSetting("Over Containers", true)
      .describe("Draws the panel again after an open screen so a container cannot hide it.");
   public final ColorSetting background = new ColorSetting("Background", 16, 10, 22, 205);
   public final ColorSetting accent = new ColorSetting("Accent", 255, 140, 55, 255);
   public final ColorSetting text = new ColorSetting("Text", 242, 226, 208, 255);
   public final ColorSetting good = new ColorSetting("Gain", 110, 225, 130, 255);

   public DuperHud() {
      super("Duper HUD", "Four lines of duper state, or everything with Detail on.", -1, Category.RENDER);
      this.addSettings(
         new Setting[]{
            this.anchor, this.offsetX, this.offsetY, this.width, this.detail, this.logLines, this.showIdle,
            this.overContainers, this.background, this.accent, this.text, this.good
         }
      );
      this.enabled = true;
      instance = this;
   }

   /** Called from the screen pass so an open container cannot cover the panel. */
   public static void overlay(class_332 graphics, float delta) {
      DuperHud hud = instance;
      if (hud != null && hud.enabled && hud.overContainers.get()) {
         hud.draw(graphics);
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
         this.panel(graphics, HudPanel.font(), duper);
      } catch (LinkageError | Exception failure) {
         System.err.println("[ONE] Duper HUD: " + failure);
      }
   }

   private void panel(class_332 graphics, Object font, Duper duper) throws Exception {
      boolean running = duper.enabled;
      List<String[]> rows = this.rows(duper, running);
      List<String> log = this.log(duper);
      int line = HudPanel.lineHeight(font);
      int step = line + 2;
      int panelWidth = this.width.getInt();
      int height = line + 6 + rows.size() * step + (running ? 6 : 0) + (log.isEmpty() ? 0 : 2 + log.size() * step) + 3;

      int[] origin = HudPanel.origin((HudAnchor)this.anchor.get(), this.offsetX.getInt(), this.offsetY.getInt(), panelWidth, height);
      int x = origin[0];
      int y = origin[1];
      Color base = this.text.m();
      int tint = HudPanel.argb(this.accent.m());
      int label = HudPanel.argb(base);
      int dim = HudPanel.argb(HudPanel.fade(base, 150));
      int faint = HudPanel.argb(HudPanel.fade(base, 38));

      HudPanel.card(graphics, x, y, panelWidth, height, HudPanel.argb(this.background.m()), tint, faint);
      String head = running ? String.valueOf(duper.current()) : String.valueOf(duper.pattern.get());
      String corner = running ? duper.run() + "/" + duper.planned() : "idle";
      HudPanel.string(graphics, font, HudPanel.clip(font, head, panelWidth - 24), x + HudPanel.PAD + 2, y + 3, tint);
      HudPanel.string(graphics, font, corner, x + panelWidth - HudPanel.PAD - HudPanel.width(font, corner), y + 3, dim);

      int row = y + line + 6;
      for (String[] entry : rows) {
         int colour = entry.length > 2 && entry[2].equals("gain") ? HudPanel.argb(this.good.m()) : label;
         if (entry[0].isEmpty()) {
            HudPanel.string(graphics, font, HudPanel.clip(font, entry[1], panelWidth - HudPanel.PAD * 2 - 2), x + HudPanel.PAD + 2, row, colour);
         } else {
            HudPanel.row(graphics, font, entry[0], entry[1], x, row, panelWidth, dim, colour);
         }
         row += step;
      }

      if (running) {
         double done = duper.burstsPlanned() <= 0 ? 0.0 : 1.0 - (double)duper.burstsLeft() / duper.burstsPlanned();
         HudPanel.bar(graphics, x + HudPanel.PAD + 2, row, panelWidth - HudPanel.PAD * 2 - 2, done, faint, tint);
      }

      if (!log.isEmpty()) {
         row += 3;
         for (String entry : log) {
            HudPanel.string(graphics, font, HudPanel.clip(font, entry, panelWidth - HudPanel.PAD * 2 - 2), x + HudPanel.PAD + 2, row, dim);
            row += step;
         }
      }
   }

   /** Four lines while it runs, two while it waits; Detail adds the rest. */
   private List<String[]> rows(Duper duper, boolean running) {
      List<String[]> rows = new ArrayList<>();
      if (!running) {
         rows.add(new String[]{"", HudPanel.join(duper.clicks.getInt() + "x" + duper.bursts.getInt(),
            duper.target.get() == DuperTarget.HELD ? "held" : "slot " + duper.slot.getInt())});
         if (this.detail.get()) {
            rows.add(new String[]{"", ((DuperPattern)duper.pattern.get()).what});
         }
         return rows;
      }
      int change = (duper.serverSpoke() ? duper.confirmed() : duper.live()) - duper.before();
      String phase = duper.phase() == Duper.WAITING ? "waiting" : duper.phase() == Duper.RUNNING ? "running" : "settling";
      rows.add(new String[]{"", HudPanel.join("run " + duper.run() + "/" + duper.planned(), phase, duper.sent() + " sent")});
      if (duper.phase() == Duper.WAITING && !duper.blocked().isEmpty()) {
         rows.add(new String[]{"", duper.blocked()});
      }
      String count = duper.before() + " → " + (duper.serverSpoke() ? duper.confirmed() : duper.live());
      rows.add(new String[]{count, (change >= 0 ? "+" : "") + change + (duper.serverSpoke() ? "" : "?"), change > 0 ? "gain" : ""});
      rows.add(new String[]{"", HudPanel.join(duper.itemName(), duper.serverSpoke() ? "server" : "client only")});
      if (this.detail.get()) {
         rows.add(new String[]{"slot", duper.slotUsed() + " · window " + duper.windowUsed()});
         for (Map.Entry<DuperPattern, Integer> result : duper.results().entrySet()) {
            int delta = result.getValue();
            rows.add(new String[]{result.getKey().name().toLowerCase(Locale.ROOT), (delta >= 0 ? "+" : "") + delta, delta > 0 ? "gain" : ""});
         }
      }
      return rows;
   }

   private List<String> log(Duper duper) {
      int wanted = this.detail.get() ? this.logLines.getInt() : 0;
      if (wanted <= 0) {
         return List.of();
      }
      List<String> lines = duper.lines();
      return lines.size() <= wanted ? lines : lines.subList(lines.size() - wanted, lines.size());
   }
}
