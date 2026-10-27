package rex;

import dev.krypton.core.event.ClientTickEvent;
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
import java.util.Comparator;
import java.util.List;
import net.minecraft.class_332;

/**
 * What the builder is doing and what it still needs, without opening anything. The material figures
 * come from the builder's own scanner, driven a few thousand cells per tick inside a time budget
 * rather than in one pass, so a large blueprint costs frames instead of a freeze. It draws over the
 * build window too, which is where you want it when you are picking a schematic.
 */
public final class BuildHud extends Module {
   public static BuildHud instance;

   public final EnumSetting<HudAnchor> anchor = new EnumSetting<>("Anchor", HudAnchor.TOP_RIGHT, HudAnchor.class);
   public final NumberSetting offsetX = new NumberSetting("X", 0.0, 400.0, 6.0, 1.0);
   public final NumberSetting offsetY = new NumberSetting("Y", 0.0, 400.0, 6.0, 1.0);
   public final NumberSetting width = new NumberSetting("Width", 110.0, 400.0, 176.0, 2.0);
   public final NumberSetting materials = new NumberSetting("Material Rows", 1.0, 16.0, 3.0, 1.0);
   public final BooleanSetting detail = new BooleanSetting("Detail", false)
      .describe("Adds the reason line, supports standing, the tempo in force and the scan state.");
   public final NumberSetting rescan = new NumberSetting("Rescan", 1.0, 60.0, 4.0, 1.0)
      .describe("Seconds between material scans.");
   public final NumberSetting cells = new NumberSetting("Cells Per Tick", 256.0, 32768.0, 4096.0, 256.0)
      .describe("Blueprint cells the scanner may walk each tick.");
   public final NumberSetting micros = new NumberSetting("Microseconds", 50.0, 4000.0, 600.0, 50.0)
      .describe("Time the scanner may take each tick, whichever limit it hits first.");
   public final BooleanSetting shortFirst = new BooleanSetting("Shortages First", true);
   public final BooleanSetting idle = new BooleanSetting("Show Without A Build", false)
      .describe("Keeps the panel up when no schematic is loaded.");
   public final BooleanSetting overScreens = new BooleanSetting("Over Screens", true);
   public final ColorSetting background = new ColorSetting("Background", 18, 11, 26, 225);
   public final ColorSetting accent = new ColorSetting("Accent", 120, 180, 255, 255);
   public final ColorSetting text = new ColorSetting("Text", 242, 226, 208, 255);
   public final ColorSetting shortage = new ColorSetting("Shortage", 255, 120, 110, 255);

   private Object scan;
   private Object snapshot;
   private String status = "";
   private long ticks;
   private long scanned = -1000L;
   private double progress;

   public BuildHud() {
      super("Build HUD", "Auto Build status and the materials it still needs.", -1, Category.RENDER);
      this.addSettings(
         new Setting[]{
            this.anchor, this.offsetX, this.offsetY, this.width, this.detail, this.materials, this.rescan, this.cells, this.micros,
            this.shortFirst, this.idle, this.overScreens, this.background, this.accent, this.text, this.shortage
         }
      );
      this.enabled = true;
      instance = this;
   }

   /** The last finished material scan, for anything else that wants the figures. */
   public static Object cached() {
      BuildHud hud = instance;
      return hud == null ? null : hud.snapshot;
   }

   public static void overlay(class_332 graphics, float delta) {
      BuildHud hud = instance;
      if (hud != null && hud.enabled && hud.overScreens.get()) {
         hud.draw(graphics);
      }
   }

   @EventHandler
   public void onBuildHudRender(Render2DEvent event) {
      try {
         if (Bridge.field(Bridge.client(), "field_1755") != null && this.overScreens.get()) {
            return;
         }
      } catch (LinkageError | Exception unknown) {
      }
      this.draw(event.sqx);
   }

   /** Drives the builder's own scanner a slice at a time. */
   @EventHandler
   public void onBuildHudTick(ClientTickEvent event) {
      this.ticks++;
      try {
         Object client = Bridge.client();
         if (Bridge.field(client, "field_1724") == null || Bridge.field(client, "field_1687") == null) {
            this.scan = null;
            this.snapshot = null;
            return;
         }
         Object controller = controller();
         this.announce(controller);
         Object schematic = Bridge.call(controller, "schematic");
         if (schematic == null) {
            this.scan = null;
            this.snapshot = null;
            return;
         }
         Object transform = Bridge.call(controller, "transform");
         if (this.scan != null
            && !(Boolean)Game.invoke(
               this.scan,
               "matches",
               new String[]{"net.minecraft.class_310", "dev.donut.client.autobuild.Schematic", "dev.donut.client.autobuild.PlacementTransform"},
               client,
               schematic,
               transform
            )) {
            this.scan = null;
         }
         if (this.scan == null && this.ticks - this.scanned >= (long)this.rescan.getInt() * 20L) {
            this.scan = Game.invoke(
               Game.type("dev.donut.client.autobuild.MaterialTracker"),
               "beginSnapshot",
               new String[]{"net.minecraft.class_310", "dev.donut.client.autobuild.Schematic", "dev.donut.client.autobuild.PlacementTransform"},
               client,
               schematic,
               transform
            );
            this.progress = 0.0;
         }
         if (this.scan == null) {
            return;
         }
         boolean done = (Boolean)Game.invoke(
            this.scan,
            "advance",
            new String[]{"net.minecraft.class_310", "int", "long"},
            client,
            this.cells.getInt(),
            (long)this.micros.getInt() * 1000L
         );
         this.progress = ((Number)Bridge.call(this.scan, "progress")).doubleValue();
         if (done) {
            this.snapshot = Bridge.call(this.scan, "result");
            this.scan = null;
            this.scanned = this.ticks;
         } else if ((Boolean)Bridge.call(this.scan, "cancelled")) {
            this.scan = null;
         }
      } catch (LinkageError | Exception failure) {
         this.scan = null;
         System.err.println("[ONE] Build HUD scan: " + failure);
      }
   }

   /** One line when a build starts, naming what it is short of before it walks into it. */
   private void announce(Object controller) throws Exception {
      String now = String.valueOf(Bridge.call(controller, "status"));
      if (now.equals(this.status)) {
         return;
      }
      boolean starting = now.equals("RUNNING") && !this.status.equals("PAUSED");
      this.status = now;
      if (!starting) {
         return;
      }
      List<String[]> needs = this.needs();
      StringBuilder line = new StringBuilder();
      int short_ = 0;
      for (String[] entry : needs) {
         if (!entry[2].equals("short")) {
            continue;
         }
         short_++;
         if (line.length() < 90) {
            line.append(line.length() == 0 ? "" : ", ").append(entry[1]);
         }
      }
      if (short_ == 0) {
         Game.message(this.snapshot == null ? "Auto Build started; materials not scanned yet." : "Auto Build started; you are carrying everything it needs.");
      } else {
         Game.message("Auto Build started, short " + short_ + " item" + (short_ == 1 ? "" : "s") + ": " + line);
      }
   }

   private static Object controller() throws Exception {
      return Game.invoke(Game.type("dev.donut.client.autobuild.AutoBuilderController"), "get", new String[0]);
   }

   private void draw(class_332 graphics) {
      try {
         Object client = Bridge.client();
         if (Bridge.field(client, "field_1724") == null) {
            return;
         }
         Object controller = controller();
         Object schematic = Bridge.call(controller, "schematic");
         if (schematic == null && !this.idle.get()) {
            return;
         }
         this.panel(graphics, HudPanel.font(), controller, schematic);
      } catch (LinkageError | Exception failure) {
         System.err.println("[ONE] Build HUD: " + failure);
      }
   }

   private void panel(class_332 graphics, Object font, Object controller, Object schematic) throws Exception {
      List<String[]> rows = this.rows(controller, schematic);
      List<String[]> needs = this.needs();
      int line = HudPanel.lineHeight(font);
      int step = line + 2;
      int panelWidth = this.width.getInt();
      int height = line + 8 + rows.size() * step + (schematic == null ? 0 : 6) + (needs.isEmpty() ? 0 : 3 + needs.size() * step) + 4;

      int[] origin = HudPanel.origin((HudAnchor)this.anchor.get(), this.offsetX.getInt(), this.offsetY.getInt(), panelWidth, height);
      int x = origin[0];
      int y = origin[1];
      Color base = this.text.m();
      int tint = HudPanel.argb(this.accent.m());
      int label = HudPanel.argb(base);
      int dim = HudPanel.argb(HudPanel.fade(base, 150));
      int faint = HudPanel.argb(HudPanel.fade(base, 38));
      int alarm = HudPanel.argb(this.shortage.m());

      HudPanel.card(graphics, x, y, panelWidth, height, HudPanel.argb(this.background.m()), tint, faint);
      String status = String.valueOf(Bridge.call(controller, "status"));
      HudPanel.string(graphics, font, "BUILD", x + HudPanel.PAD + 2, y + 4, dim);
      HudPanel.string(graphics, font, status, x + panelWidth - HudPanel.PAD - HudPanel.width(font, status), y + 4, tint);

      int row = y + line + 8;
      for (String[] entry : rows) {
         int colour = entry.length > 2 && entry[2].equals("short") ? alarm : label;
         if (entry[0].isEmpty()) {
            HudPanel.string(graphics, font, HudPanel.clip(font, entry[1], panelWidth - HudPanel.PAD * 2 - 2), x + HudPanel.PAD + 2, row, colour);
         } else {
            HudPanel.row(graphics, font, entry[0], entry[1], x, row, panelWidth, dim, colour);
         }
         row += step;
      }

      if (schematic != null) {
         double done = ((Number)Bridge.call(controller, "progress")).doubleValue();
         HudPanel.bar(graphics, x + HudPanel.PAD + 2, row, panelWidth - HudPanel.PAD * 2 - 2, done, faint, tint);
      }

      if (!needs.isEmpty()) {
         row += 3;
         for (String[] entry : needs) {
            HudPanel.string(
               graphics, font, HudPanel.clip(font, entry[1], panelWidth - HudPanel.PAD * 2 - 2), x + HudPanel.PAD + 2, row,
               entry[2].equals("short") ? alarm : dim
            );
            row += step;
         }
      }
   }

   /** Two lines when it is quiet, three while it builds; Detail adds the rest. */
   private List<String[]> rows(Object controller, Object schematic) throws Exception {
      List<String[]> rows = new ArrayList<>();
      if (schematic == null) {
         rows.add(new String[]{"", "no schematic \u00b7 .autobuild setup"});
         return rows;
      }
      int confirmed = ((Number)Bridge.call(controller, "confirmedBlocks")).intValue();
      int total = ((List<?>)Bridge.call(schematic, "blocks")).size();
      double done = ((Number)Bridge.call(controller, "progress")).doubleValue();
      rows.add(new String[]{"", HudPanel.clip(HudPanel.font(), String.valueOf(Bridge.call(schematic, "name")), this.width.getInt() - 14)});
      rows.add(new String[]{confirmed + " / " + total, Math.round(done * 100.0) + "%"});
      String status = String.valueOf(Bridge.call(controller, "status"));
      if (this.detail.get() || status.equals("PAUSED")) {
         rows.add(new String[]{"", String.valueOf(Bridge.call(controller, "reason"))});
      }
      if (this.detail.get()) {
         int supports = ((Number)Bridge.call(controller, "supportsPlaced")).intValue();
         if (supports > 0) {
            rows.add(new String[]{"supports", String.valueOf(supports)});
         }
         rows.add(new String[]{"", BuildTempo.describe()});
         rows.add(new String[]{"", this.scan != null ? "scanning " + Math.round(this.progress * 100.0) + "%"
            : this.snapshot == null ? "materials not scanned" : "materials current"});
      }
      return rows;
   }

   /** The last finished scan as short lines, what is missing first. */
   private List<String[]> needs() throws Exception {
      Object snapshot = this.snapshot;
      if (snapshot == null) {
         return List.of();
      }
      List<Object> all = new ArrayList<>((List<Object>)Bridge.call(snapshot, "rows"));
      if (this.shortFirst.get()) {
         all.sort(Comparator.comparingInt((Object row) -> -number(row, "missing")).thenComparingInt(row -> -number(row, "remaining")));
      }
      List<String[]> rows = new ArrayList<>();
      int shortages = 0;
      for (Object entry : all) {
         int missing = number(entry, "missing");
         int remaining = number(entry, "remaining");
         if (remaining <= 0 && missing <= 0) {
            continue;
         }
         if (missing > 0) {
            shortages++;
         }
         if (rows.size() >= this.materials.getInt()) {
            continue;
         }
         String name = String.valueOf(Bridge.call(entry, "name"));
         rows.add(new String[]{name, (missing > 0 ? missing : remaining) + "\u00d7 " + name, missing > 0 ? "short" : "ok"});
      }
      if (rows.isEmpty()) {
         rows.add(new String[]{"", "nothing left to place", "ok"});
      } else if (shortages > rows.size()) {
         rows.add(new String[]{"", "and " + (shortages - rows.size()) + " more short", "short"});
      }
      return rows;
   }

   private static int number(Object record, String component) {
      try {
         return ((Number)Bridge.call(record, component)).intValue();
      } catch (LinkageError | Exception unknown) {
         return 0;
      }
   }
}
