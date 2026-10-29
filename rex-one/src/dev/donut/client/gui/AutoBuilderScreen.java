package dev.donut.client.gui;

import dev.donut.client.autobuild.AutoBuilderController;
import dev.donut.client.autobuild.MaterialTracker;
import dev.donut.client.autobuild.PlacementTransform;
import dev.donut.client.autobuild.Schematic;
import dev.donut.client.autobuild.UnifiedSchematics;
import dev.donut.client.autobuild.CreativeBuildMode;
import rex.BuildTempo;
import rex.BuildMemory;
import rex.SchematicGhost;
import dev.donut.client.config.ClientConfig;
import dev.donut.client.input.ModuleKeybinds;
import dev.krypton.core.setting.BooleanSetting;
import dev.krypton.core.setting.NumberSetting;
import dev.krypton.core.setting.Setting;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_11905;
import net.minecraft.class_11908;
import net.minecraft.class_11909;
import net.minecraft.class_156;
import net.minecraft.class_2338;
import net.minecraft.class_2561;
import net.minecraft.class_332;
import net.minecraft.class_437;
import net.minecraft.class_638;
import net.minecraft.class_746;

@Environment(EnvType.CLIENT)
public final class AutoBuilderScreen extends class_437 {
   private static final int W = 840;
   private static final int H = 510;
   private static final int TEXT = -1250061;
   private static final int MUTED = -6052942;
   private static final int DIM = -9671552;
   private static final int PANEL = -15724265;
   private static final int CARD = -15657700;
   private static final int BORDER = -14079434;
   private static final int INSET = -16184044;
   private static final int GOOD = 0xFF5BD68A;
   private static final int WARN = -14741;
   private static final int ROW = 30;
   private static final int LIST_TOP = 150;
   private static final int LIST_BOTTOM = 488;
   private final class_437 parent;
   private final ClientConfig config;
   private final Runnable save;
   private final AutoBuilderController builder = AutoBuilderController.get();
   private final SchematicModelPreview model = new SchematicModelPreview();
   private final List<Hit> hits = new ArrayList<>();
   private final Map<String, Double> hoverAnimations = new HashMap<>();
   private final String[] coordinates = new String[]{"0", "0", "0"};
   private List<Path> files = List.of();
   private Path folder;
   private Path selectedFile;
   private MaterialTracker.Snapshot materials;
   private MaterialTracker.Scan materialScan;
   private Schematic materialsSchematic;
   private PlacementTransform materialsTransform;
   private class_638 materialsWorld;
   private class_746 materialsPlayer;
   private boolean loading;
   private boolean refreshing;
   private boolean initialized;
   private boolean closing;
   private boolean materialsOpen;
   private boolean tempoOpen;
   private Runnable dragKnob;
   private int[][] layers;
   private long layersAt;
   private String tooltip;
   private String dragId;
   private boolean modalClosing;
   private boolean draggingPreview;
   private boolean replaceText;
   private long lastFrame;
   private long materialRefresh;
   private int requestId;
   private int fileScroll;
   private int materialScroll;
   private int keyboardFocus = -1;
   private int coordinateFocus = -1;
   private float scale = 1.0F;
   private float originX;
   private float originY;
   private double mouseX;
   private double mouseY;
   private double progress;
   private double modalProgress;
   private double seconds;
   private String message = "";
   private String searchQuery = "";
   private String details = "";
   private boolean searchFocused;
   private boolean libraryFocused;
   private int copied;
   private String libraryQuery = "";
   private double shownProgress;

   public AutoBuilderScreen(class_437 parent, ClientConfig config, Runnable save) {
      super(class_2561.method_43470("AutoBuilder"));
      this.parent = parent;
      this.config = config;
      this.save = save;
   }

   protected void method_25426() {
      this.scale = Math.max(0.1F, Math.min(1.0F, Math.min((this.field_22789 - 16.0F) / 840.0F, (this.field_22790 - 16.0F) / 510.0F)));
      this.originX = (this.field_22789 - 840.0F * this.scale) / 2.0F;
      this.originY = (this.field_22790 - 510.0F * this.scale) / 2.0F;
      // Pop-ups such as tutorial hints draw over every screen; this one has no room for them.
      this.field_22787.method_1566().method_2000();
      if (!this.initialized) {
         this.initialized = true;
         this.folder = this.field_22787.field_1697.toPath().resolve("schematics").resolve("donut");
         this.syncCoordinates();
         this.refreshFiles();
      }

      this.hits.clear();
   }

   public void method_25394(class_332 g, int mx, int my, float delta) {
      long now = System.nanoTime();
      this.seconds = this.lastFrame == 0L ? 0.016666666666666666 : Math.min(0.1, (now - this.lastFrame) / 1.0E9);
      this.lastFrame = now;
      this.progress = Math.clamp(this.progress + this.seconds / (this.closing ? -0.16 : 0.2), 0.0, 1.0);
      this.modalProgress = Math.clamp(
         this.modalProgress + this.seconds / ((this.materialsOpen || this.tempoOpen || !this.details.isEmpty()) && !this.modalClosing ? 0.18 : -0.14), 0.0, 1.0
      );
      this.shownProgress += (this.builder.progress() - this.shownProgress) * (1.0 - Math.exp(-this.seconds * 8.0));
      this.mouseX = (mx - this.originX) / this.scale;
      this.mouseY = (my - this.originY) / this.scale;
      this.hits.clear();
      this.tooltip = null;
      if (!this.closing || this.progress != 0.0) {
         double previous = UiPaint.pushOpacity(smooth(this.progress));
         g.method_51448().pushMatrix();

         try {
            g.method_25294(0, 0, this.field_22789, this.field_22790, UiPaint.withOpacity(2013595921));
            g.method_51448().translate(this.originX, this.originY + (float)((1.0 - smooth(this.progress)) * 10.0));
            g.method_51448().scale(this.scale, this.scale);
            UiPaint.shadow(g, 0, 0, W, H, 14, Integer.MIN_VALUE);
            UiPaint.round(g, 0, 0, W, H, 14, BORDER);
            UiPaint.round(g, 1, 1, W - 2, H - 2, 13, PANEL);
            this.drawHeader(g);
            this.drawLibrary(g);
            this.drawPreview(g);
            this.drawPositioning(g);
            this.drawBuild(g);
            if (this.materialsOpen || this.tempoOpen || !this.details.isEmpty()) {
               this.hits.clear();
               double modalOpacity = UiPaint.pushOpacity(smooth(this.modalProgress));

               try {
                  g.method_25294(0, 0, W, H, UiPaint.withOpacity(-1341781745));
                  if (this.materialsOpen) {
                     this.drawMaterials(g);
                  } else if (this.tempoOpen) {
                     this.drawTempo(g);
                  } else {
                     this.drawDetails(g);
                  }
               } finally {
                  UiPaint.popOpacity(modalOpacity);
               }
            }

            if (this.tooltip != null && !this.materialsOpen && !this.tempoOpen && this.details.isEmpty()) {
               this.drawTooltip(g, this.tooltip);
            }

            if (this.keyboardFocus >= 0 && this.keyboardFocus < this.hits.size()) {
               Hit h = this.hits.get(this.keyboardFocus);
               UiPaint.outline(g, h.x - 1, h.y - 1, h.w + 2, h.h + 2, 5, UiPaint.alpha(this.config.accentColor, 0.85));
            }
         } finally {
            g.method_51448().popMatrix();
            UiPaint.popOpacity(previous);
         }
      }
   }

   private void drawHeader(class_332 g) {
      UiPaint.round(g, 1, 1, W - 2, 51, 13, UiPaint.blend(PANEL, this.config.accentColor, 0.06));
      UiPaint.round(g, 1, 40, W - 2, 12, 0, UiPaint.blend(PANEL, this.config.accentColor, 0.06));
      UiPaint.cube(g, 22, 16, 17, UiPaint.alpha(this.config.accentColor, 1.0), 0.95, 0.25);
      UiPaint.strong(g, this.field_22793, "Auto Build", 52, 15, TEXT);
      UiPaint.text(g, this.field_22793, "REX One  /  schematics/donut", 52, 31, DIM);
      String status = statusLabel(this.builder.status());
      int pillWidth = UiPaint.width(this.field_22793, status) + 26;
      int pillX = 52 + UiPaint.width(this.field_22793, "Auto Build") + 12;
      int color = this.statusColor();
      UiPaint.round(g, pillX, 12, pillWidth, 16, 8, UiPaint.blend(CARD, color, 0.22));
      UiPaint.round(g, pillX + 7, 18, 4, 4, 2, color);
      UiPaint.text(g, this.field_22793, status, pillX + 16, 16, color);
      BuildTempo.Preset preset = BuildTempo.current();
      String tempo = preset == null ? "Original pacing" : preset.label() + " tempo";
      UiPaint.right(g, this.field_22793, tempo, 750, 21, MUTED);
      this.button(g, "close", "Close", 764, 14, 60, true, false, this::method_25419);
      UiPaint.round(g, 1, 52, W - 2, 1, 0, BORDER);
   }

   private void drawLibrary(class_332 g) {
      int x = 14;
      int y = 64;
      int w = 216;
      int h = 432;
      this.card(g, x, y, w, h);
      UiPaint.text(g, this.field_22793, "LIBRARY", x + 12, y + 12, DIM);
      List<Path> shown = this.filteredFiles();
      String count = shown.size() == this.files.size()
         ? this.files.size() + (this.files.size() == 1 ? " file" : " files")
         : shown.size() + " of " + this.files.size();
      UiPaint.right(g, this.field_22793, count, x + w - 12, y + 12, DIM);
      UiPaint.round(g, x + 10, y + 28, w - 20, 22, 5, this.libraryFocused ? BORDER : INSET);
      UiPaint.search(g, x + 18, y + 34, MUTED);
      String query = this.libraryQuery.isEmpty() && !this.libraryFocused
         ? "Search schematics..."
         : this.libraryQuery + (this.libraryFocused && System.currentTimeMillis() % 1000L < 500L ? "|" : "");
      UiPaint.text(g, this.field_22793, this.ellipsize(query, w - 50), x + 34, y + 35, this.libraryQuery.isEmpty() ? DIM : TEXT);
      this.hit("librarySearch", x + 10, y + 28, w - 20, 22, () -> {
         this.libraryFocused = true;
         this.replaceText = false;
      });
      this.button(g, "folder", "Open Folder", x + 10, y + 56, 118, true, false, () -> {
         try {
            Files.createDirectories(this.folder);
            class_156.method_668().method_60932(this.folder);
         } catch (Exception error) {
            this.message = "Cannot open folder: " + errorMessage(error);
         }
      });
      this.button(g, "refresh", this.refreshing ? "..." : "Refresh", x + 134, y + 56, w - 144, !this.refreshing, false, this::refreshFiles);
      int top = LIST_TOP;
      int bottom = LIST_BOTTOM;
      g.method_44379(x + 8, top, x + w - 8, bottom);

      try {
         if (this.files.isEmpty()) {
            UiPaint.text(g, this.field_22793, "Add .schem or .litematic", x + 16, top + 14, MUTED);
            UiPaint.text(g, this.field_22793, "files, then Refresh.", x + 16, top + 30, DIM);
         } else if (shown.isEmpty()) {
            UiPaint.text(g, this.field_22793, "No schematic matches.", x + 16, top + 14, MUTED);
         }

         for (int i = 0; i < shown.size(); i++) {
            int ry = top + i * ROW - this.fileScroll;
            if (ry + ROW - 3 >= top && ry <= bottom) {
               Path file = shown.get(i);
               boolean selected = file.equals(this.selectedFile);
               boolean hover = this.inside(x + 10, ry, w - 22, ROW - 3) && this.mouseY >= top && this.mouseY < bottom;
               UiPaint.round(g, x + 10, ry, w - 22, ROW - 3, 5, UiPaint.blend(CARD, this.config.accentColor, selected ? 0.26 : (hover ? 0.1 : 0.0)));
               if (selected) {
                  UiPaint.round(g, x + 10, ry + 5, 3, ROW - 13, 1, UiPaint.alpha(this.config.accentColor, 1.0));
               }

               String name = file.getFileName().toString();
               int dot = name.lastIndexOf('.');
               String base = dot > 0 ? name.substring(0, dot) : name;
               String kind = dot > 0 ? name.substring(dot + 1).toUpperCase(Locale.ROOT) : "";
               UiPaint.text(g, this.field_22793, this.ellipsize(base, w - 78), x + 20, ry + 9, selected ? TEXT : MUTED);
               UiPaint.right(g, this.field_22793, this.ellipsize(kind, 44), x + w - 18, ry + 9, DIM);
               this.hit("file" + i, x + 10, Math.max(top, ry), w - 22, Math.min(bottom, ry + ROW - 3) - Math.max(top, ry), () -> this.load(file));
            }
         }
      } finally {
         g.method_44380();
      }

      this.scrollbar(g, x + w - 9, top, bottom - top, shown.size() * ROW, this.fileScroll);
   }

   private void drawPreview(class_332 g) {
      int x = 240;
      int y = 64;
      int w = 376;
      int h = 214;
      this.card(g, x, y, w, h);
      Schematic schematic = this.builder.schematic();
      String title = this.loading ? "Loading and validating..." : (schematic == null ? "No schematic loaded" : schematic.name());
      UiPaint.strong(g, this.field_22793, this.ellipsize(title, w - 100), x + 12, y + 12, TEXT);
      this.button(g, "previous", "<", x + w - 76, y + 7, 30, !this.files.isEmpty() && !this.loading, false, () -> this.nextFile(-1));
      this.button(g, "next", ">", x + w - 42, y + 7, 30, !this.files.isEmpty() && !this.loading, false, () -> this.nextFile(1));
      int vx = x + 10;
      int vy = y + 36;
      int vw = w - 20;
      int vh = 148;
      UiPaint.round(g, vx, vy, vw, vh, 7, INSET);
      g.method_44379(vx + 2, vy + 2, vx + vw - 2, vy + vh - 2);

      try {
         for (int gx = vx + 12; gx < vx + vw; gx += 24) {
            UiPaint.line(g, gx, vy + 2, gx, vy + vh - 2, 338642013);
         }

         for (int gy = vy + 12; gy < vy + vh; gy += 24) {
            UiPaint.line(g, vx + 2, gy, vx + vw - 2, gy, 338642013);
         }

         this.model.setSchematic(schematic);
         this.model.render(g, vx + 4, vy + 4, vw - 8, vh - 8);
         if (schematic == null) {
            UiPaint.text(g, this.field_22793, "Pick a schematic from the library", vx + (vw - UiPaint.width(this.field_22793, "Pick a schematic from the library")) / 2, vy + 62, MUTED);
            String formats = "Sponge .schem and multi-region .litematic";
            UiPaint.text(g, this.field_22793, formats, vx + (vw - UiPaint.width(this.field_22793, formats)) / 2, vy + 80, DIM);
         }
      } finally {
         g.method_44380();
      }

      this.hit("model", vx, vy, vw, vh, () -> this.draggingPreview = true);
      if (schematic != null) {
         this.drawLayers(g, vx + vw - 12, vy + 6, 6, vh - 12);
      }
      if (schematic != null) {
         class_2338 d = schematic.dimensions();
         UiPaint.text(
            g,
            this.field_22793,
            d.method_10263() + " x " + d.method_10264() + " x " + d.method_10260() + "  /  " + number(schematic.blocks().size()) + " blocks  /  "
               + schematic.regionCount() + (schematic.regionCount() == 1 ? " region" : " regions"),
            x + 12,
            y + h - 20,
            MUTED
         );
         this.button(g, "resetView", "Reset view", x + w - 92, y + h - 27, 80, true, false, this.model::reset);
      } else {
         UiPaint.text(g, this.field_22793, "Drag to rotate, scroll to zoom", x + 12, y + h - 20, DIM);
      }
   }

   private void drawPositioning(class_332 g) {
      int x = 240;
      int y = 286;
      int w = 376;
      int h = 210;
      this.card(g, x, y, w, h);
      boolean available = this.builder.schematic() != null && !this.loading;
      UiPaint.text(g, this.field_22793, "PLACEMENT", x + 12, y + 12, DIM);
      String[] axes = {"X", "Y", "Z"};

      for (int axis = 0; axis < 3; axis++) {
         int index = axis;
         int ax = x + 12 + axis * 120;
         UiPaint.text(g, this.field_22793, axes[axis], ax, y + 37, MUTED);
         this.button(g, "minus" + axis, "-", ax + 12, y + 28, 18, available, false, () -> this.nudge(index, -1));
         UiPaint.round(g, ax + 33, y + 28, 58, 24, 4, this.coordinateFocus == axis ? BORDER : INSET);
         String shown = this.coordinates[axis] + (this.coordinateFocus == axis && System.currentTimeMillis() % 1000L < 500L ? "|" : "");
         UiPaint.text(g, this.field_22793, this.ellipsize(shown, 52), ax + 37, y + 37, available ? TEXT : DIM);
         if (available) {
            this.hit("coordinate" + axis, ax + 33, y + 28, 58, 24, () -> {
               this.coordinateFocus = index;
               this.replaceText = true;
            });
         }

         this.button(g, "plus" + axis, "+", ax + 94, y + 28, 18, available, false, () -> this.nudge(index, 1));
      }

      PlacementTransform t = this.builder.transform();
      int bw = (w - 24 - 12) / 3;
      this.button(g, "atPlayer", "At player", x + 12, y + 58, bw, available && this.field_22787.field_1724 != null, false, () -> {
         PlacementTransform current = this.builder.transform();
         this.builder.setTransform(new PlacementTransform(this.field_22787.field_1724.method_24515(), current.quarterTurns(), current.mirrorX()));
         this.syncCoordinates();
      });
      this.button(g, "rotate", "Rotate " + Math.floorMod(t.quarterTurns(), 4) * 90 + "°", x + 18 + bw, y + 58, bw, available, false, () -> {
         if (this.commitCoordinates()) {
            PlacementTransform current = this.builder.transform();
            this.builder.setTransform(new PlacementTransform(current.origin(), current.quarterTurns() + 1, current.mirrorX()));
            this.invalidateMaterials();
         }
      });
      this.button(g, "mirror", "Mirror " + (t.mirrorX() ? "on" : "off"), x + 24 + bw * 2, y + 58, bw, available, t.mirrorX(), () -> {
         if (this.commitCoordinates()) {
            PlacementTransform current = this.builder.transform();
            this.builder.setTransform(new PlacementTransform(current.origin(), current.quarterTurns(), !current.mirrorX()));
            this.invalidateMaterials();
         }
      });
      int hw = (w - 24 - 6) / 2;
      this.button(g, "preview", "Show in world", x + 12, y + 88, hw, available && this.field_22787.field_1687 != null, false, () -> {
         if (this.commitCoordinates()) {
            SchematicGhost.set(true);
            this.field_22787.method_1507(null);
         }
      });
      boolean ghost = SchematicGhost.shown();
      this.button(g, "ghost", ghost ? "Ghost: shown" : "Ghost: hidden", x + 18 + hw, y + 88, hw, available, ghost, () -> SchematicGhost.set(!SchematicGhost.shown()));
      UiPaint.text(g, this.field_22793, "Ghost opacity", x + 12, y + 129, DIM);
      int opacity = (int)Math.round(this.config.autoBuilder.previewOpacity * 100.0);
      this.button(g, "opacityDown", "-", x + 100, y + 120, 22, true, false, () -> this.setOpacity(-0.05));
      UiPaint.round(g, x + 128, y + 127, 80, 4, 2, INSET);
      UiPaint.round(g, x + 128, y + 127, (int)(80.0 * Math.clamp(opacity / 100.0, 0.0, 1.0)), 4, 2, UiPaint.alpha(this.config.accentColor, 0.9));
      UiPaint.text(g, this.field_22793, opacity + "%", x + 214, y + 129, MUTED);
      this.button(g, "opacityUp", "+", x + 244, y + 120, 22, true, false, () -> this.setOpacity(0.05));
      if (this.builder.schematic() != null && !this.builder.schematic().warnings().isEmpty()) {
         int notices = this.builder.schematic().warnings().size();
         this.button(g, "warnings", notices + (notices == 1 ? " notice" : " notices"), x + w - 94, y + 120, 82, true, false,
            () -> this.openDetails(String.join("\n", this.builder.schematic().warnings())));
      }

      // Which layers of the ghost to draw.
      int ly = y + 150;
      UiPaint.text(g, this.field_22793, "Layers", x + 12, ly + 9, DIM);
      SchematicGhost.Layers mode = SchematicGhost.mode();
      this.button(g, "layers", mode.label, x + 100, ly, 128, available, mode != SchematicGhost.Layers.ALL, SchematicGhost::cycleMode);
      if (mode == SchematicGhost.Layers.MANUAL) {
         this.button(g, "layerDown", "-", x + 236, ly, 22, true, false, () -> SchematicGhost.shiftLayer(-1));
         String shown = "Y " + (this.builder.transform().origin().method_10264() + SchematicGhost.manualLayer());
         UiPaint.text(g, this.field_22793, shown, x + 264, ly + 9, TEXT);
         this.button(g, "layerUp", "+", x + 316, ly, 22, true, false, () -> SchematicGhost.shiftLayer(1));
      } else if (mode != SchematicGhost.Layers.ALL && this.builder.workingLayer() != Integer.MIN_VALUE) {
         UiPaint.text(g, this.field_22793, "Y " + this.builder.workingLayer(), x + 236, ly + 9, MUTED);
      }

      // The switches that matter most while building, one click each.
      int oy = y + 180;
      UiPaint.text(g, this.field_22793, "Options", x + 12, oy + 9, DIM);
      BuildTempo tempo = BuildTempo.instance;
      if (tempo != null) {
         BooleanSetting[] options = {tempo.smooth, tempo.sprint, tempo.removeSupports, tempo.pillar};
         String[] labels = {"Smooth", "Sprint", "Clean up", "Pillar"};
         int ow = (w - 100 - 12 - 3 * 4) / 4;
         for (int i = 0; i < options.length; i++) {
            BooleanSetting option = options[i];
            this.button(g, "option" + i, labels[i], x + 100 + i * (ow + 4), oy, ow, 24, true, option.get(), () -> option.set(!option.get()));
            if (this.inside(x + 100 + i * (ow + 4), oy, ow, 24)) {
               this.tooltip = option.getDescription();
            }
         }
      }
   }

   /**
    * One bar per blueprint layer, bottom at the bottom, filled by how much of it is built. The layer
    * being built is marked; pointing at the strip names the layer and its count.
    */
   private void drawLayers(class_332 g, int x, int y, int w, int h) {
      long now = System.currentTimeMillis();
      if (this.layers == null || now - this.layersAt > 500L) {
         this.layers = this.builder.layerProgress();
         this.layersAt = now;
      }
      int[][] rows = this.layers;
      if (rows.length == 0) {
         return;
      }
      UiPaint.round(g, x - 1, y - 1, w + 2, h + 2, 3, BORDER);
      int working = this.builder.workingLayer();
      double step = (double)h / rows.length;
      for (int i = 0; i < rows.length; i++) {
         int[] row = rows[i];
         int top = y + h - (int)Math.round((i + 1) * step);
         int bottom = y + h - (int)Math.round(i * step);
         int size = Math.max(1, bottom - top - (step >= 4.0 ? 1 : 0));
         double done = row[2] == 0 ? 1.0 : (double)row[1] / row[2];
         int color = done >= 1.0 ? GOOD : UiPaint.blend(INSET, this.config.accentColor, 0.25 + 0.75 * done);
         UiPaint.round(g, x, top, w, size, 0, color);
         if (row[0] == working && this.builder.isRunning()) {
            UiPaint.round(g, x - 4, top, 3, size, 0, TEXT);
         }
         if (this.inside(x - 6, top, w + 8, Math.max(2, bottom - top))) {
            this.tooltip = "Layer Y " + row[0] + ": " + number(row[1]) + " / " + number(row[2]) + " built";
         }
      }
   }

   private void drawBuild(class_332 g) {
      int x = 626;
      int y = 64;
      int w = 200;
      int h = 432;
      this.card(g, x, y, w, h);
      boolean available = this.builder.schematic() != null && !this.loading;
      UiPaint.text(g, this.field_22793, "BUILD", x + 12, y + 12, DIM);
      int percent = (int)Math.floor(this.builder.progress() * 100.0);
      g.method_51448().pushMatrix();

      try {
         g.method_51448().translate(x + 12, y + 28);
         g.method_51448().scale(2.0F, 2.0F);
         UiPaint.strong(g, this.field_22793, percent + "%", 0, 0, TEXT);
      } finally {
         g.method_51448().popMatrix();
      }

      UiPaint.right(g, this.field_22793, number(this.builder.confirmedBlocks()) + " / " + number(this.builder.totalBlocks()), x + w - 12, y + 40, MUTED);
      UiPaint.round(g, x + 12, y + 56, w - 24, 6, 3, INSET);
      int filled = (int)Math.round((w - 24) * Math.clamp(this.shownProgress, 0.0, 1.0));
      if (filled > 0) {
         UiPaint.round(g, x + 12, y + 56, Math.max(6, filled), 6, 3, UiPaint.alpha(this.statusColor(), 1.0));
      }

      double rate = this.builder.blocksPerSecond();
      int eta = this.builder.secondsRemaining();
      String[][] stats = {
         {"Rate", rate > 0.0 ? String.format(Locale.ROOT, "%.1f/s", rate) : "-"},
         {"Time left", eta < 0 ? "-" : duration(eta)},
         {"In flight", Integer.toString(this.builder.inflightPlacements())},
         {"Ping", this.builder.connection(this.field_22787)},
         {"Supports", number(this.builder.supportsPlaced())},
         {"Skipped", number(this.builder.deferredBlocks())}
      };

      for (int i = 0; i < stats.length; i++) {
         int sx = x + 12 + (i % 2) * 90;
         int sy = y + 72 + (i / 2) * 32;
         UiPaint.round(g, sx, sy, 86, 28, 5, INSET);
         UiPaint.text(g, this.field_22793, stats[i][0], sx + 7, sy + 5, DIM);
         UiPaint.text(g, this.field_22793, stats[i][1], sx + 7, sy + 16, i == 5 && this.builder.deferredBlocks() > 0 ? WARN : TEXT);
      }

      if (this.builder.deferredBlocks() > 0) {
         this.hit("skipped", x + 102, y + 136, 86, 28, () -> this.openDetails(this.builder.skippedSummary()));
      }

      int sy = y + 174;
      UiPaint.text(g, this.field_22793, "SPEED", x + 12, sy, DIM);
      BuildTempo.Preset current = BuildTempo.current();
      boolean tempo = BuildTempo.instance != null;
      BuildTempo.Preset[] presets = BuildTempo.Preset.values();
      int cell = (w - 24 - 8) / 3;

      for (int i = 0; i < presets.length; i++) {
         BuildTempo.Preset preset = presets[i];
         int px = x + 12 + (i % 3) * (cell + 4);
         int py = sy + 13 + (i / 3) * 24;
         boolean selected = preset == current;
         String id = "preset" + i;
         double hover = this.animate(id, tempo && this.inside(px, py, cell, 20));
         UiPaint.round(g, px, py, cell, 20, 5, UiPaint.blend(INSET, this.config.accentColor, selected ? 0.6 : hover * 0.2));
         if (selected) {
            UiPaint.outline(g, px, py, cell, 20, 5, UiPaint.alpha(this.config.accentColor, 0.9));
         }

         String label = preset.label();
         UiPaint.text(g, this.field_22793, label, px + (cell - UiPaint.width(this.field_22793, label)) / 2, py + 7, selected ? TEXT : (tempo ? MUTED : DIM));
         if (tempo) {
            this.hit(id, px, py, cell, 20, () -> {
               BuildTempo.choose(preset);
               if (preset == BuildTempo.Preset.CUSTOM) {
                  this.openTempo();
               }
            });
         }
      }

      String ceiling = !tempo ? "Build Tempo is unavailable" : String.format(Locale.ROOT, "Up to %.0f blocks/s", BuildTempo.ceiling());
      UiPaint.text(g, this.field_22793, ceiling, x + 12, sy + (current == BuildTempo.Preset.CUSTOM ? 60 : 64), MUTED);
      if (current == BuildTempo.Preset.CUSTOM) {
         this.button(g, "editCustom", "Edit custom speed...", x + 12, sy + 73, w - 24, 17, true, false, this::openTempo);
      } else {
         UiPaint.text(g, this.field_22793, presetNote(current), x + 12, sy + 77, DIM);
      }
      int ay = y + 268;
      String action = this.builder.status() == AutoBuilderController.Status.PAUSED ? "Resume" : (this.builder.isRunning() ? "Pause" : "Start building");
      this.button(g, "start", action, x + 12, ay, w - 24, 28, available && this.field_22787.field_1687 != null, true, () -> {
         if (this.commitCoordinates()) {
            if (this.builder.isRunning()) {
               this.builder.pause();
            } else if (!this.builder.hasAnyMaterial(this.field_22787)) {
               // Closing the screen into an instant "Missing ..." pause looked like Start did nothing.
               this.message = "You carry none of the materials this build needs. Open Materials for the list.";
            } else {
               this.field_22787.method_1507(null);
               if (this.builder.status() == AutoBuilderController.Status.PAUSED) {
                  this.builder.resume();
               } else {
                  this.builder.start();
               }
            }
         }
      });
      int half = (w - 24 - 6) / 2;
      this.button(g, "materials", "Materials", x + 12, ay + 34, half, available, false, () -> {
         if (this.commitCoordinates()) {
            this.materialsOpen = true;
            this.modalClosing = false;
            this.modalProgress = 0.0;
            this.materialScroll = 0;
            this.searchFocused = false;
            this.keyboardFocus = -1;
            this.invalidateMaterials();
            this.refreshMaterials();
         }
      });
      this.button(g, "cancel", "Cancel", x + 18 + half, ay + 34, half, available, false, this.builder::cancel);
      int ry = ay + 66;
      String reason = this.message.isEmpty() ? this.builder.reason() : this.message;
      UiPaint.round(g, x + 12, ry, w - 24, 60, 5, INSET);
      List<String> lines = this.wrap(reason, w - 40);

      for (int i = 0; i < Math.min(4, lines.size()); i++) {
         String line = i == 3 && lines.size() > 4 ? this.ellipsize(lines.get(i) + " ...", w - 40) : lines.get(i);
         UiPaint.text(g, this.field_22793, line, x + 20, ry + 7 + i * 13, this.message.isEmpty() ? MUTED : WARN);
      }

      if (!reason.isEmpty()) {
         this.hit("reason", x + 12, ry, w - 24, 60, () -> this.openDetails(reason));
      }

      this.donut$drawCreativeMode(g, x + 12, y + h - 28, w - 24);
   }

   /** A small wrapped note next to the pointer, kept inside the window. */
   private void drawTooltip(class_332 g, String text) {
      List<String> lines = this.wrap(text, 220);
      int width = 0;
      for (String line : lines) {
         width = Math.max(width, UiPaint.width(this.field_22793, line));
      }
      int w = width + 16;
      int h = lines.size() * 12 + 10;
      int tx = (int)Math.min(W - w - 6, this.mouseX + 12);
      int ty = (int)Math.min(H - h - 6, this.mouseY + 14);
      UiPaint.round(g, tx - 1, ty - 1, w + 2, h + 2, 6, BORDER);
      UiPaint.round(g, tx, ty, w, h, 5, CARD);
      for (int i = 0; i < lines.size(); i++) {
         UiPaint.text(g, this.field_22793, lines.get(i), tx + 8, ty + 6 + i * 12, TEXT);
      }
   }

   private void card(class_332 g, int x, int y, int w, int h) {
      UiPaint.round(g, x, y, w, h, 9, BORDER);
      UiPaint.round(g, x + 1, y + 1, w - 2, h - 2, 8, UiPaint.blend(CARD, this.config.accentColor, 0.035));
   }

   private int statusColor() {
      return switch (this.builder.status()) {
         case RUNNING -> GOOD;
         case PAUSED -> WARN;
         case COMPLETED -> UiPaint.alpha(this.config.accentColor, 1.0);
         case READY -> TEXT;
         default -> DIM;
      };
   }

   private static String statusLabel(AutoBuilderController.Status status) {
      String name = status.name().toLowerCase(Locale.ROOT);
      return Character.toUpperCase(name.charAt(0)) + name.substring(1);
   }

   private static String presetNote(BuildTempo.Preset preset) {
      if (preset == null) {
         return "Build Tempo module is off";
      }

      return switch (preset) {
         case SAFE -> "Waits for the server each block";
         case BALANCED -> "Prediction, 2 unconfirmed";
         case FAST -> "One block per tick";
         case SERVER -> "Paced and patient, for public servers";
         case CUSTOM -> "Your own numbers";
      };
   }

   private static String duration(int seconds) {
      if (seconds >= 3600) {
         return seconds / 3600 + "h " + seconds % 3600 / 60 + "m";
      } else {
         return seconds >= 60 ? seconds / 60 + "m " + seconds % 60 + "s" : seconds + "s";
      }
   }

   private List<Path> filteredFiles() {
      String query = this.libraryQuery.toLowerCase(Locale.ROOT).trim();
      if (query.isEmpty()) {
         return this.files;
      } else {
         return this.files.stream().filter(file -> file.getFileName().toString().toLowerCase(Locale.ROOT).contains(query)).toList();
      }
   }

   private double animate(String id, boolean active) {
      double value = this.hoverAnimations.getOrDefault(id, 0.0);
      value += ((active ? 1 : 0) - value) * (1.0 - Math.exp(-this.seconds * 18.0));
      this.hoverAnimations.put(id, value);
      return value;
   }

   private void drawMaterials(class_332 g) {
      int x = 29;
      int y = 43;
      int w = 782;
      int h = 418;
      UiPaint.round(g, x - 1, y - 1, w + 2, h + 2, 11, -14079434);
      UiPaint.round(g, x, y, w, h, 10, -15724265);
      UiPaint.strong(g, this.field_22793, "Materials", x + 20, y + 18, -1250061);
      if (this.materialScan != null) {
         UiPaint.text(
            g, this.field_22793, "Updating... " + Math.round(this.materialScan.progress() * 100.0) + "%", x + 116, y + 19, -6052942
         );
      }

      this.button(g, "closeMaterials", "Done", x + w - 78, y + 12, 59, true, false, this::closeModal);
      if (this.materials == null) {
         UiPaint.text(g, this.field_22793, "Reading loaded world and inventory...", x + 20, y + 51, -6052942);
      } else {
         UiPaint.text(
            g,
            this.field_22793,
            number(this.materials.totalBlocks())
               + " blocks  /  "
               + number(this.materials.correctBlocks())
               + " correct  /  "
               + number(this.materials.remainingBlocks())
               + " remaining  /  "
               + number(this.materials.unknownBlocks())
               + " unknown",
            x + 20,
            y + 47,
            -6052942
         );
         UiPaint.round(g, x + 20, y + 69, 355, 26, 5, this.searchFocused ? -14079434 : -16184044);
         UiPaint.search(g, x + 30, y + 77, -6052942);
         String query = this.searchQuery.isEmpty() && !this.searchFocused
            ? "Search materials..."
            : this.searchQuery + (this.searchFocused && System.currentTimeMillis() % 1000L < 500L ? "|" : "");
         UiPaint.text(g, this.field_22793, this.ellipsize(query, 313), x + 47, y + 78, this.searchQuery.isEmpty() ? -9671552 : -1250061);
         this.hit("materialSearch", x + 20, y + 69, 355, 26, () -> {
            this.searchFocused = true;
            this.replaceText = false;
         });
         boolean creative = this.field_22787.field_1724 != null && this.field_22787.field_1724.method_31549().field_7477;
         this.button(g, "getMaterials", "Get Materials", x + w - 163, y + 69, 143, creative, false, () -> {
            this.builder.getMaterials(this.field_22787);
            this.invalidateMaterials();
            this.refreshMaterials();
         });
         if (!creative) {
            UiPaint.text(g, this.field_22793, "Creative only", x + w - 264, y + 78, -9671552);
         }

         this.button(g, "copyMaterials", this.copied > 0 ? "Copied" : "Copy list", x + 388, y + 69, 104, !this.materials.rows().isEmpty(), false, () -> {
            this.field_22787.field_1774.method_1455(this.shoppingList());
            this.copied = 40;
         });

         int[] cols = new int[]{334, 397, 468, 531, 594, 663, 737};
         String[] labels = new String[]{"Total", "Correct", "Remain", "Hotbar", "Inv.", "Missing", "Unknown"};
         UiPaint.text(g, this.field_22793, "Material", x + 20, y + 116, -9671552);

         for (int i = 0; i < cols.length; i++) {
            UiPaint.right(g, this.field_22793, labels[i], cols[i] + 40, y + 116, -9671552);
         }

         UiPaint.round(g, x + 18, y + 133, w - 36, 1, 0, -14079434);
         List<MaterialTracker.Row> rows = this.filteredRows();
         this.materialScroll = Math.clamp((long)this.materialScroll, 0, Math.max(0, rows.size() * 28 - 192));
         g.method_44379(x + 18, y + 140, x + w - 17, y + 332);

         try {
            for (int i = 0; i < rows.size(); i++) {
               int ry = y + 140 + i * 28 - this.materialScroll;
               if (ry + 27 >= y + 140 && ry < y + 332) {
                  MaterialTracker.Row row = rows.get(i);
                  if (i % 2 == 0) {
                     UiPaint.round(g, x + 18, ry, w - 36, 27, 3, -15657700);
                  }

                  g.method_51427(row.item().method_7854(), x + 23, ry + 5);
                  UiPaint.text(g, this.field_22793, this.ellipsize(row.name(), 233), x + 48, ry + 10, -1250061);
                  int[] values = new int[]{row.total(), row.correct(), row.remaining(), row.hotbar(), row.inventory(), row.missing(), row.unknown()};

                  for (int col = 0; col < values.length; col++) {
                     UiPaint.right(
                        g,
                        this.field_22793,
                        number(values[col]),
                        cols[col] + 40,
                        ry + 10,
                        col == 5 && values[col] > 0 ? -30825 : (col == 6 && values[col] > 0 ? -14741 : -6052942)
                     );
                  }
               }
            }

            if (rows.isEmpty()) {
               UiPaint.text(g, this.field_22793, "No matching materials.", x + 25, y + 155, -6052942);
            }
         } finally {
            g.method_44380();
         }

         this.scrollbar(g, x + w - 13, y + 140, 192, rows.size() * 28, this.materialScroll);
         UiPaint.text(
            g, this.field_22793, "Counts are items. Inventory excludes hotbar. Unknown = unloaded blocks.", x + 20, y + 347, -9671552
         );
         String lower = this.materials.conflicts() > 0
            ? this.materials.conflicts() + " occupied positions require manual correction."
            : "Double slabs count twice. Paired blocks count their placement item once.";
         UiPaint.text(g, this.field_22793, lower, x + 20, y + 365, this.materials.conflicts() > 0 ? -14741 : -9671552);
         UiPaint.text(g, this.field_22793, this.ellipsize(this.builder.reason(), 738), x + 20, y + 391, -6052942);
      }
   }

   /** The Custom speed editor: every number Custom uses, plus the switches that apply on every preset. */
   private void drawTempo(class_332 g) {
      int x = 29;
      int y = 43;
      int w = 782;
      int h = 418;
      UiPaint.round(g, x - 1, y - 1, w + 2, h + 2, 11, BORDER);
      UiPaint.round(g, x, y, w, h, 10, PANEL);
      UiPaint.strong(g, this.field_22793, "Custom speed", x + 20, y + 18, TEXT);
      this.button(g, "closeTempo", "Done", x + w - 78, y + 12, 59, true, true, this::closeModal);
      BuildTempo tempo = BuildTempo.instance;
      if (tempo == null) {
         UiPaint.text(g, this.field_22793, "Build Tempo is unavailable.", x + 20, y + 51, MUTED);
         return;
      }

      String summary = BuildTempo.current() == BuildTempo.Preset.CUSTOM
         ? String.format(Locale.ROOT, "Up to %.0f blocks/s with these numbers", BuildTempo.ceiling())
         : "Pick Custom to use these numbers";
      UiPaint.text(g, this.field_22793, summary, x + 124, y + 19, MUTED);
      String hovered = null;
      UiPaint.text(g, this.field_22793, "PACING", x + 20, y + 46, DIM);
      Setting[] rows = tempo.customRows();

      for (int i = 0; i < rows.length; i++) {
         Setting row = rows[i];
         int ry = y + 60 + i * 24;
         if (this.inside(x + 16, ry - 2, 452, 23)) {
            hovered = row.getDescription();
            UiPaint.round(g, x + 16, ry - 2, 452, 23, 4, CARD);
         }

         UiPaint.text(g, this.field_22793, row.getName(), x + 24, ry + 7, TEXT);
         String id = "knob" + i;
         if (row instanceof BooleanSetting toggle) {
            boolean on = toggle.get();
            this.button(g, id, on ? "On" : "Off", x + 196, ry, 248, 20, true, on, () -> toggle.set(!toggle.get()));
         } else if (row instanceof NumberSetting knob) {
            this.knob(g, id, knob, x + 196, ry);
         }
      }

      int rx = x + 492;
      int rw = w - 512;
      UiPaint.text(g, this.field_22793, "START FROM", rx, y + 46, DIM);
      BuildTempo.Preset[] fixed = {BuildTempo.Preset.SAFE, BuildTempo.Preset.BALANCED, BuildTempo.Preset.FAST, BuildTempo.Preset.SERVER};
      int third = (rw - 12) / 4;

      for (int i = 0; i < fixed.length; i++) {
         BuildTempo.Preset from = fixed[i];
         int bx = rx + i * (third + 4);
         if (this.inside(bx, y + 60, third, 20)) {
            hovered = "Copies the " + from.label() + " numbers into the rows on the left, then edit from there.";
         }
         this.button(g, "from" + i, from.label(), bx, y + 60, third, 20, true, false, () -> tempo.copyToCustom(from));
      }

      if (this.inside(rx, y + 86, rw, 20)) {
         hovered = "Puts every pacing row back to its default.";
      }
      this.button(g, "resetCustom", "Reset to defaults", rx, y + 86, rw, 20, true, false, tempo::resetCustom);
      UiPaint.text(g, this.field_22793, "ON EVERY PRESET", rx, y + 124, DIM);
      BooleanSetting[] switches = tempo.behaviourRows();

      for (int i = 0; i < switches.length; i++) {
         BooleanSetting toggle = switches[i];
         int ry = y + 138 + i * 24;
         if (this.inside(rx, ry, rw, 20)) {
            hovered = toggle.getDescription();
         }
         UiPaint.text(g, this.field_22793, toggle.getName(), rx + 4, ry + 7, TEXT);
         boolean on = toggle.get();
         this.button(g, "switch" + i, on ? "On" : "Off", rx + rw - 56, ry, 56, 20, true, on, () -> toggle.set(!toggle.get()));
      }

      int dy = y + 334;
      UiPaint.round(g, rx, dy, rw, 74, 6, INSET);
      String help = hovered == null || hovered.isEmpty() ? "Point at a row to see what it does. Changes apply right away, even mid-build." : hovered;
      List<String> lines = this.wrap(help, rw - 20);

      for (int i = 0; i < Math.min(5, lines.size()); i++) {
         UiPaint.text(g, this.field_22793, lines.get(i), rx + 10, dy + 9 + i * 13, hovered == null ? DIM : MUTED);
      }
   }

   /** One number row: a bar to click or drag, the value, and - / + buttons. */
   private void knob(class_332 g, String id, NumberSetting knob, int x, int y) {
      int bar = 150;
      double span = knob.getMax() - knob.getMin();
      double fill = span <= 0.0 ? 0.0 : Math.clamp((knob.getDouble() - knob.getMin()) / span, 0.0, 1.0);
      boolean hover = this.inside(x, y, bar, 20) || this.dragKnob != null && id.equals(this.dragId);
      UiPaint.round(g, x, y + 8, bar, 4, 2, INSET);
      UiPaint.round(g, x, y + 8, Math.max(4, (int)Math.round(bar * fill)), 4, 2, UiPaint.alpha(this.config.accentColor, hover ? 1.0 : 0.8));
      UiPaint.round(g, x + (int)Math.round((bar - 8) * fill), y + 5, 8, 10, 3, hover ? TEXT : MUTED);
      Runnable fromMouse = () -> knob.set(knob.getMin() + Math.clamp((this.mouseX - x) / bar, 0.0, 1.0) * span);
      this.hit(id, x, y, bar, 20, () -> {
         fromMouse.run();
         this.dragKnob = fromMouse;
         this.dragId = id;
      });
      String value = knob.getStep() < 1.0 ? String.format(Locale.ROOT, "%.2f", knob.getDouble()) : number(knob.getInt());
      UiPaint.right(g, this.field_22793, value, x + bar + 50, y + 7, TEXT);
      // Wide ranges (Candidates) step by about a thirty-second of the range, so +/- stays useful.
      double step = Math.max(knob.getStep(), Math.round(span / 32.0 / knob.getStep()) * knob.getStep());
      this.button(g, id + "-", "-", x + bar + 56, y, 20, 20, knob.getDouble() > knob.getMin(), false, () -> knob.set(knob.getDouble() - step));
      this.button(g, id + "+", "+", x + bar + 78, y, 20, 20, knob.getDouble() < knob.getMax(), false, () -> knob.set(knob.getDouble() + step));
   }

   private void openTempo() {
      this.tempoOpen = true;
      this.modalClosing = false;
      this.modalProgress = 0.0;
      this.keyboardFocus = -1;
   }

   private void drawDetails(class_332 g) {
      UiPaint.round(g, 135, 114, 570, 280, 10, -15724265);
      UiPaint.outline(g, 135, 114, 570, 280, 10, -14079434);
      UiPaint.strong(g, this.field_22793, "Auto Build notice", 154, 134, -1250061);
      List<String> lines = this.wrap(this.details, 525);
      g.method_44379(152, 160, 686, 343);

      for (int i = 0; i < lines.size(); i++) {
         UiPaint.text(g, this.field_22793, lines.get(i), 154, 162 + i * 17 - this.materialScroll, -6052942);
      }

      g.method_44380();
      this.scrollbar(g, 692, 160, 181, lines.size() * 17, this.materialScroll);
      this.button(g, "doneDetails", "Done", 586, 356, 98, true, true, this::closeModal);
      this.button(g, "copyDetails", "Copy details", 154, 356, 125, true, false, () -> this.field_22787.field_1774.method_1455(this.details));
   }

   private void refreshFiles() {
      this.refreshing = true;
      Path directory = this.folder;
      CompletableFuture.<List<Path>>supplyAsync(() -> UnifiedSchematics.list(directory))
         .whenComplete((result, error) -> this.field_22787.execute(() -> {
            this.refreshing = false;
            if (error != null) {
               this.message = "Cannot read schematic folder: " + errorMessage(error);
            } else {
               this.files = result;
               this.fileScroll = 0;
               this.message = this.files.isEmpty() ? "Add .schem or .litematic files using Open Folder." : "";
            }
         }));
   }

   private void load(Path file) {
      if (!this.loading) {
         int request = ++this.requestId;
         this.selectedFile = file;
         this.loading = true;
         this.message = "";
         if (this.builder.isRunning()) {
            this.builder.pause();
         }

         CompletableFuture.<Schematic>supplyAsync(() -> {
            try {
               return UnifiedSchematics.load(file);
            } catch (IOException var2x) {
               throw new CompletionException(var2x);
            }
         }).whenComplete((value, error) -> this.field_22787.execute(() -> {
            if (request == this.requestId) {
               this.loading = false;
               if (error != null) {
                  this.message = "Invalid schematic: " + errorMessage(error);
               } else {
                  this.builder.setSchematic(value);
                  BuildMemory.remember(file, value);
                  if (this.field_22787.field_1724 != null) {
                     this.builder.setTransform(new PlacementTransform(this.field_22787.field_1724.method_24515(), 0, false));
                  }

                  this.syncCoordinates();
                  this.invalidateMaterials();
                  this.model.setSchematic(value);
                  this.message = value.warnings().isEmpty() ? "" : "Loaded with " + value.warnings().size() + " notice(s).";
               }
            }
         }));
      }
   }

   private void nextFile(int direction) {
      List<Path> shown = this.filteredFiles().isEmpty() ? this.files : this.filteredFiles();
      if (!shown.isEmpty()) {
         int index = shown.indexOf(this.selectedFile);
         this.load(shown.get(Math.floorMod(index < 0 ? 0 : index + direction, shown.size())));
      }
   }

   private void syncCoordinates() {
      class_2338 p = this.builder.transform().origin();
      this.coordinates[0] = Integer.toString(p.method_10263());
      this.coordinates[1] = Integer.toString(p.method_10264());
      this.coordinates[2] = Integer.toString(p.method_10260());
      this.invalidateMaterials();
   }

   private boolean commitCoordinates() {
      PlacementTransform t = this.builder.transform();

      try {
         class_2338 pos = new class_2338(Integer.parseInt(this.coordinates[0]), Integer.parseInt(this.coordinates[1]), Integer.parseInt(this.coordinates[2]));
         if (Math.abs((long)pos.method_10263()) > 29999984L || Math.abs((long)pos.method_10260()) > 29999984L || Math.abs((long)pos.method_10264()) > 2048L) {
            throw new NumberFormatException();
         }

         if (!pos.equals(t.origin())) {
            this.builder.setTransform(new PlacementTransform(pos, t.quarterTurns(), t.mirrorX()));
         }

         if (this.message.startsWith("Invalid coordinates.")) {
            this.message = "";
         }

         this.invalidateMaterials();
      } catch (NumberFormatException var3) {
         this.message = "Invalid coordinates. Use whole numbers within world bounds.";
         this.coordinateFocus = -1;
         this.syncCoordinates();
         return false;
      }

      this.coordinateFocus = -1;
      return true;
   }

   private void nudge(int axis, int amount) {
      this.commitCoordinates();
      this.coordinates[axis] = Long.toString((long)Integer.parseInt(this.coordinates[axis]) + amount);
      this.commitCoordinates();
   }

   private void setOpacity(double amount) {
      this.config.autoBuilder.previewOpacity += amount;
      this.config.autoBuilder.sanitize();
      this.save.run();
   }

   private void invalidateMaterials() {
      this.materialRefresh = 0L;
      if (this.materialScan != null) {
         this.materialScan.cancel();
      }

      this.materialScan = null;
      if (this.materialsSchematic != this.builder.schematic()
         || !this.builder.transform().equals(this.materialsTransform)
         || this.field_22787 != null && (this.materialsWorld != this.field_22787.field_1687 || this.materialsPlayer != this.field_22787.field_1724)) {
         this.materials = null;
      }
   }

   private void refreshMaterials() {
      Schematic value = this.builder.schematic();
      PlacementTransform placement = this.builder.transform();
      if (this.materialsSchematic != value
         || !placement.equals(this.materialsTransform)
         || this.materialsWorld != this.field_22787.field_1687
         || this.materialsPlayer != this.field_22787.field_1724) {
         this.invalidateMaterials();
         this.materials = null;
         this.materialsSchematic = value;
         this.materialsTransform = placement;
         this.materialsWorld = this.field_22787.field_1687;
         this.materialsPlayer = this.field_22787.field_1724;
      }

      if (this.materialScan == null) {
         this.materialScan = MaterialTracker.beginSnapshot(this.field_22787, value, placement);
      }
   }

   private List<MaterialTracker.Row> filteredRows() {
      if (this.materials == null) {
         return List.of();
      } else {
         String query = this.searchQuery.toLowerCase(Locale.ROOT).trim();
         return this.materials.rows().stream().filter(row -> row.name().toLowerCase(Locale.ROOT).contains(query)).toList();
      }
   }

   /** What is still missing, one line per item, ready to paste into chat or a note. */
   private String shoppingList() {
      StringBuilder out = new StringBuilder();
      Schematic schematic = this.builder.schematic();
      out.append("Shopping list").append(schematic == null ? "" : " for " + schematic.name()).append('\n');
      int lines = 0;
      for (MaterialTracker.Row row : this.materials.rows()) {
         if (row.missing() > 0) {
            int stacks = (row.missing() + 63) / 64;
            out.append(number(row.missing())).append(" x ").append(row.name()).append("  (").append(stacks).append(stacks == 1 ? " stack" : " stacks")
               .append(", have ").append(number(row.hotbar() + row.inventory())).append(")\n");
            lines++;
         }
      }
      if (lines == 0) {
         out.append("Nothing missing: everything left is in your inventory.\n");
      }
      return out.toString();
   }

   private void openDetails(String value) {
      this.details = value;
      this.modalClosing = false;
      this.modalProgress = 0.0;
      this.materialScroll = 0;
      this.keyboardFocus = -1;
   }

   private void closeModal() {
      this.modalClosing = true;
      this.searchFocused = false;
      this.keyboardFocus = -1;
   }

   private void button(class_332 g, String id, String label, int x, int y, int w, boolean enabled, boolean primary, Runnable action) {
      this.button(g, id, label, x, y, w, 24, enabled, primary, action);
   }

   private void button(class_332 g, String id, String label, int x, int y, int w, int h, boolean enabled, boolean primary, Runnable action) {
      double hover = this.animate(id, enabled && this.inside(x, y, w, h));
      UiPaint.round(g, x, y, w, h, 6, UiPaint.blend(CARD, this.config.accentColor, enabled ? (primary ? 0.55 : 0.09) + hover * 0.18 : 0.0));
      UiPaint.outline(g, x, y, w, h, 6, UiPaint.blend(BORDER, this.config.accentColor, enabled ? (primary ? 0.45 : 0.0) + hover * 0.3 : 0.0));
      String shown = this.ellipsize(label, w - 10);
      UiPaint.text(g, this.field_22793, shown, x + (w - UiPaint.width(this.field_22793, shown)) / 2, y + (h - 6) / 2, enabled ? TEXT : DIM);
      if (enabled) {
         this.hit(id, x, y, w, h, action);
      }
   }

   private void hit(String id, int x, int y, int w, int h, Runnable action) {
      if (!this.closing && !this.modalClosing && h > 0) {
         this.hits.add(new Hit(id, x, y, w, h, action));
      }
   }

   private boolean inside(int x, int y, int w, int h) {
      return this.mouseX >= x && this.mouseX < x + w && this.mouseY >= y && this.mouseY < y + h;
   }

   private void scrollbar(class_332 g, int x, int y, int h, int content, int scroll) {
      if (content > h) {
         int thumb = Math.max(18, h * h / content);
         UiPaint.round(g, x, y, 3, h, 1, -15657700);
         UiPaint.round(
            g, x, y + (int)((double)scroll / (content - h) * (h - thumb)), 3, thumb, 1, UiPaint.alpha(this.config.accentColor, 0.6)
         );
      }
   }

   private String ellipsize(String text, int max) {
      if (text == null) {
         return "";
      } else if (UiPaint.width(this.field_22793, text) <= max) {
         return text;
      } else {
         while (!text.isEmpty() && UiPaint.width(this.field_22793, text + "...") > max) {
            text = removeLast(text);
         }

         return text + "...";
      }
   }

   private List<String> wrap(String value, int max) {
      List<String> lines = new ArrayList<>();

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

   private static String number(int value) {
      return String.format(Locale.ROOT, "%,d", value);
   }

   private static String errorMessage(Throwable error) {
      while (error.getCause() != null) {
         error = error.getCause();
      }

      return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
   }

   private static String removeLast(String value) {
      return value.isEmpty() ? value : value.substring(0, value.offsetByCodePoints(value.length(), -1));
   }

   private static double smooth(double value) {
      return value * value * (3.0 - 2.0 * value);
   }

   public boolean method_25402(class_11909 event, boolean doubled) {
      if (this.closing || this.modalClosing) {
         return true;
      } else if (event.method_74245() != 0) {
         return false;
      } else {
         double x = (event.comp_4798() - this.originX) / this.scale;
         double y = (event.comp_4799() - this.originY) / this.scale;
         Hit target = null;

         for (int i = this.hits.size() - 1; i >= 0; i--) {
            if (this.hits.get(i).contains(x, y)) {
               target = this.hits.get(i);
               break;
            }
         }

         if (this.coordinateFocus >= 0 && (target == null || !target.id.equals("coordinate" + this.coordinateFocus)) && !this.commitCoordinates()) {
            return true;
         } else {
            this.searchFocused = target != null && target.id.equals("materialSearch");
            this.libraryFocused = target != null && target.id.equals("librarySearch");
            this.keyboardFocus = -1;
            if (target != null) {
               target.action.run();
            }

            return true;
         }
      }
   }

   public boolean method_25403(class_11909 event, double dx, double dy) {
      if (this.dragKnob != null && this.tempoOpen) {
         this.mouseX = (event.comp_4798() - this.originX) / this.scale;
         this.dragKnob.run();
         return true;
      } else if (this.draggingPreview && !this.materialsOpen && !this.tempoOpen && this.details.isEmpty()) {
         this.model.rotate(dx / this.scale, dy / this.scale);
         return true;
      } else {
         return false;
      }
   }

   public boolean method_25406(class_11909 event) {
      this.draggingPreview = false;
      this.dragKnob = null;
      return true;
   }

   public boolean method_25401(double x, double y, double horizontal, double vertical) {
      if (!this.closing && !this.modalClosing) {
         double lx = (x - this.originX) / this.scale;
         double ly = (y - this.originY) / this.scale;
         if (this.materialsOpen) {
            this.materialScroll = Math.clamp((long)(this.materialScroll - (int)(vertical * 28.0)), 0, Math.max(0, this.filteredRows().size() * 28 - 192));
         } else if (this.tempoOpen) {
            // nothing scrolls in the Custom editor
         } else if (!this.details.isEmpty()) {
            this.materialScroll = Math.clamp(
               (long)(this.materialScroll - (int)(vertical * 25.0)), 0, Math.max(0, this.wrap(this.details, 525).size() * 17 - 181)
            );
         } else if (lx >= 250.0 && lx < 606.0 && ly >= 100.0 && ly < 248.0) {
            this.model.zoom(vertical);
         } else if (lx >= 14.0 && lx < 230.0 && ly >= LIST_TOP && ly <= LIST_BOTTOM) {
            this.fileScroll = Math.clamp(
               (long)(this.fileScroll - (int)(vertical * ROW)), 0, Math.max(0, this.filteredFiles().size() * ROW - (LIST_BOTTOM - LIST_TOP))
            );
         }

         return true;
      } else {
         return true;
      }
   }

   public boolean method_25404(class_11908 event) {
      if (!this.closing && !this.modalClosing) {
         if (event.comp_4795() == 256) {
            if (this.coordinateFocus >= 0) {
               this.coordinateFocus = -1;
               this.syncCoordinates();
            } else if (this.libraryFocused) {
               this.libraryFocused = false;
            } else if (!this.materialsOpen && !this.tempoOpen && this.details.isEmpty()) {
               this.method_25419();
            } else {
               this.closeModal();
            }

            return true;
         } else {
            if (this.coordinateFocus >= 0 && (event.comp_4795() == 265 || event.comp_4795() == 264)) {
               this.nudge(this.coordinateFocus, (event.comp_4795() == 265 ? 1 : -1) * (event.method_74239() ? 10 : 1));
               return true;
            }

            if (this.coordinateFocus >= 0 || this.searchFocused || this.libraryFocused) {
               String value = this.coordinateFocus >= 0 ? this.coordinates[this.coordinateFocus] : (this.libraryFocused ? this.libraryQuery : this.searchQuery);
               if (event.method_74240() && event.comp_4795() == 65) {
                  this.replaceText = true;
                  return true;
               }

               if (event.method_74240() && event.comp_4795() == 86) {
                  String pasted = this.field_22787.field_1774.method_1460().replaceAll("[\\p{Cntrl}]", "");
                  if (this.coordinateFocus >= 0) {
                     pasted = pasted.replaceAll("[^0-9-]", "");
                  }

                  value = (this.replaceText ? "" : value) + pasted;
                  this.setInput(value.substring(0, Math.min(this.coordinateFocus >= 0 ? 11 : 64, value.length())));
                  this.replaceText = false;
                  return true;
               }

               if (event.comp_4795() == 259) {
                  this.setInput(this.replaceText ? "" : removeLast(value));
                  this.replaceText = false;
                  return true;
               }

               if (event.comp_4795() == 257) {
                  if (this.coordinateFocus >= 0) {
                     this.commitCoordinates();
                  } else {
                     this.searchFocused = false;
                     this.libraryFocused = false;
                  }

                  return true;
               }
            }

            if (event.comp_4795() == 258 && !this.hits.isEmpty()) {
               if (this.coordinateFocus >= 0) {
                  this.commitCoordinates();
               }

               this.keyboardFocus = Math.floorMod(this.keyboardFocus + (event.method_74239() ? -1 : 1), this.hits.size());
               this.searchFocused = false;
               this.libraryFocused = false;
               return true;
            } else if ((event.comp_4795() == 257 || event.comp_4795() == 32) && this.keyboardFocus >= 0 && this.keyboardFocus < this.hits.size()) {
               this.hits.get(this.keyboardFocus).action.run();
               return true;
            } else {
               return false;
            }
         }
      } else {
         return true;
      }
   }

   private void setInput(String value) {
      if (this.coordinateFocus >= 0) {
         this.coordinates[this.coordinateFocus] = value;
      } else if (this.libraryFocused) {
         this.libraryQuery = value;
         this.fileScroll = 0;
      } else {
         this.searchQuery = value;
         this.materialScroll = 0;
      }
   }

   public boolean method_25400(class_11905 event) {
      if (this.closing || this.modalClosing || !event.method_74227()) {
         return true;
      } else if (this.coordinateFocus >= 0) {
         int cp = event.comp_4793();
         if (cp >= 48 && cp <= 57 || cp == 45) {
            if (this.replaceText) {
               this.coordinates[this.coordinateFocus] = "";
            }

            this.replaceText = false;
            if (this.coordinates[this.coordinateFocus].length() < 11) {
               this.coordinates[this.coordinateFocus] = this.coordinates[this.coordinateFocus] + event.method_74226();
            }
         }

         return true;
      } else if (this.libraryFocused) {
         if (this.replaceText) {
            this.libraryQuery = "";
         }

         this.replaceText = false;
         if (this.libraryQuery.length() < 64) {
            this.libraryQuery = this.libraryQuery + event.method_74226();
         }

         this.fileScroll = 0;
         return true;
      } else if (this.searchFocused) {
         if (this.replaceText) {
            this.searchQuery = "";
         }

         this.replaceText = false;
         if (this.searchQuery.length() < 64) {
            this.searchQuery = this.searchQuery + event.method_74226();
         }

         this.materialScroll = 0;
         return true;
      } else {
         return false;
      }
   }

   public void method_25393() {
      if (this.copied > 0) {
         this.copied--;
      }
      if (this.closing && this.progress == 0.0 && this.field_22787.field_1755 == this) {
         this.field_22787.method_1507(this.parent);
      }

      if (this.modalClosing && this.modalProgress == 0.0) {
         this.materialsOpen = false;
         this.tempoOpen = false;
         this.dragKnob = null;
         this.details = "";
         this.modalClosing = false;
         this.materialScroll = 0;
      }

      if (this.materialsOpen && !this.modalClosing && !this.closing) {
         if (this.materialsSchematic != this.builder.schematic()
            || !this.builder.transform().equals(this.materialsTransform)
            || this.materialsWorld != this.field_22787.field_1687
            || this.materialsPlayer != this.field_22787.field_1724
            || this.materialScan == null && System.currentTimeMillis() - this.materialRefresh >= 750L) {
            this.refreshMaterials();
         }

         if (this.materialScan != null && this.materialScan.advance(this.field_22787, 4096, 4000000L)) {
            this.materials = this.materialScan.result();
            this.materialScan = null;
            this.materialRefresh = System.currentTimeMillis();
         }
      } else if (this.materialScan != null) {
         this.materialScan.cancel();
         this.materialScan = null;
         this.materialRefresh = 0L;
      }
   }

   public void method_25419() {
      if (!this.closing) {
         this.commitCoordinates();
         this.closing = true;
         this.draggingPreview = false;
         this.keyboardFocus = -1;
      }
   }

   public void method_25432() {
      this.requestId++;
      this.loading = false;
      this.invalidateMaterials();
      ModuleKeybinds.suppressUntilReleased(this.field_22787);
   }

   public boolean method_25421() {
      return false;
   }

   private void donut$drawCreativeMode(class_332 g, int x, int y, int w) {
      double hover = this.animate("creativeMode", this.inside(x, y, w, 20));
      UiPaint.round(g, x, y, w, 20, 5, UiPaint.blend(INSET, this.config.accentColor, 0.05 + hover * 0.12));
      UiPaint.outline(g, x, y, w, 20, 5, UiPaint.alpha(this.config.accentColor, 0.55 + hover * 0.35));
      String label = this.ellipsize(CreativeBuildMode.label(this.config.autoBuilder), w - 12);
      UiPaint.text(g, this.field_22793, label, x + (w - UiPaint.width(this.field_22793, label)) / 2, y + 7, TEXT);
      this.hit("creativeMode", x, y, w, 20, new CreativeBuildMode(this.config.autoBuilder, this.save));
   }

   @Environment(EnvType.CLIENT)
   private record Hit(String id, int x, int y, int w, int h, Runnable action) {
      boolean contains(double mx, double my) {
         return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
      }
   }
}
