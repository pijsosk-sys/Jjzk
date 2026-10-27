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
   public final NumberSetting width = new NumberSetting("Width", 150.0, 420.0, 260.0, 10.0);
   public final NumberSetting materials = new NumberSetting("Material Rows", 1.0, 16.0, 8.0, 1.0);
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
            this.anchor, this.offsetX, this.offsetY, this.width, this.materials, this.rescan, this.cells, this.micros,
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
            line.append(line.length() == 0 ? "" : ", ").append(entry[1].replace(" of ", "/")).append(" ").append(entry[0]);
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
      int step = line + 3;
      int panelWidth = this.width.getInt();
      int header = line + 11;
      int height = header + rows.size() * step + step + (needs.isEmpty() ? 0 : 5 + needs.size() * step) + HudPanel.PAD;

      int[] origin = HudPanel.origin((HudAnchor)this.anchor.get(), this.offsetX.getInt(), this.offsetY.getInt(), panelWidth, height);
      int x = origin[0];
      int y = origin[1];
      Color base = this.text.m();
      int label = HudPanel.argb(base);
      int dim = HudPanel.argb(HudPanel.fade(base, 155));
      int faint = HudPanel.argb(HudPanel.fade(base, 40));
      int tint = HudPanel.argb(this.accent.m());

      HudPanel.frame(graphics, x, y, panelWidth, height, HudPanel.argb(this.background.m()), tint, faint);
      HudPanel.string(graphics, font, "AUTO BUILD", x + HudPanel.PAD, y + 6, label);
      String status = String.valueOf(Bridge.call(controller, "status"));
      HudPanel.string(graphics, font, status, x + panelWidth - HudPanel.PAD - HudPanel.width(font, status), y + 6, tint);

      int row = y + header;
      for (String[] entry : rows) {
         HudPanel.row(graphics, font, entry[0], entry[1], x, row, panelWidth, dim, entry.length > 2 && entry[2].equals("short") ? HudPanel.argb(this.shortage.m()) : label);
         row += step;
      }

      double done = schematic == null ? 0.0 : ((Number)Bridge.call(controller, "progress")).doubleValue();
      HudPanel.bar(graphics, x + HudPanel.PAD, row + 2, panelWidth - HudPanel.PAD * 2, done, faint, tint);
      row += step;

      if (!needs.isEmpty()) {
         HudPanel.fill(graphics, x + HudPanel.PAD, row, x + panelWidth - HudPanel.PAD, row + 1, faint);
         row += 4;
         for (String[] entry : needs) {
            HudPanel.row(
               graphics, font, HudPanel.clip(font, entry[0], panelWidth / 2), entry[1], x, row, panelWidth, dim,
               entry[2].equals("short") ? HudPanel.argb(this.shortage.m()) : label
            );
            row += step;
         }
      }
   }

   private List<String[]> rows(Object controller, Object schematic) throws Exception {
      List<String[]> rows = new ArrayList<>();
      if (schematic == null) {
         rows.add(new String[]{"schematic", "none loaded"});
         rows.add(new String[]{"open it", ".autobuild setup"});
         return rows;
      }
      rows.add(new String[]{"schematic", String.valueOf(Bridge.call(schematic, "name"))});
      int confirmed = ((Number)Bridge.call(controller, "confirmedBlocks")).intValue();
      int total = ((List<?>)Bridge.call(schematic, "blocks")).size();
      rows.add(new String[]{"blocks", confirmed + " / " + total});
      rows.add(new String[]{"reason", String.valueOf(Bridge.call(controller, "reason"))});
      int supports = ((Number)Bridge.call(controller, "supportsPlaced")).intValue();
      if (supports > 0) {
         rows.add(new String[]{"supports", String.valueOf(supports)});
      }
      rows.add(new String[]{"tempo", BuildTempo.describe()});
      if (this.scan != null) {
         rows.add(new String[]{"scanning", Math.round(this.progress * 100.0) + "%"});
      } else if (this.snapshot == null) {
         rows.add(new String[]{"materials", "not scanned yet"});
      }
      return rows;
   }

   /** Material rows from the last finished scan, shortages first. */
   private List<String[]> needs() throws Exception {
      Object snapshot = this.snapshot;
      if (snapshot == null) {
         return List.of();
      }
      List<Object> all = new ArrayList<>((List<Object>)Bridge.call(snapshot, "rows"));
      List<String[]> rows = new ArrayList<>();
      if (this.shortFirst.get()) {
         all.sort(Comparator.comparingInt((Object row) -> -number(row, "missing")).thenComparingInt(row -> -number(row, "remaining")));
      }
      for (Object entry : all) {
         if (rows.size() >= this.materials.getInt()) {
            break;
         }
         int missing = number(entry, "missing");
         int remaining = number(entry, "remaining");
         if (remaining <= 0 && missing <= 0) {
            continue;
         }
         String name = String.valueOf(Bridge.call(entry, "name"));
         if (missing > 0) {
            rows.add(new String[]{name, "short " + missing + " of " + remaining, "short"});
         } else {
            rows.add(new String[]{name, "need " + remaining + ", carried", "ok"});
         }
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
