package dev.donut.client.gui;

import dev.donut.client.autobuild.AutoBuilderController;
import dev.donut.client.autobuild.MaterialTracker;
import dev.donut.client.autobuild.PlacementTransform;
import dev.donut.client.autobuild.Schematic;
import dev.donut.client.autobuild.UnifiedSchematics;
import dev.donut.client.autobuild.CreativeBuildMode;
import dev.donut.client.config.ClientConfig;
import dev.donut.client.input.ModuleKeybinds;
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
         this.modalProgress + this.seconds / ((this.materialsOpen || !this.details.isEmpty()) && !this.modalClosing ? 0.18 : -0.14), 0.0, 1.0
      );
      this.mouseX = (mx - this.originX) / this.scale;
      this.mouseY = (my - this.originY) / this.scale;
      this.hits.clear();
      if (!this.closing || this.progress != 0.0) {
         double previous = UiPaint.pushOpacity(smooth(this.progress));
         g.method_51448().pushMatrix();

         try {
            g.method_25294(0, 0, this.field_22789, this.field_22790, UiPaint.withOpacity(2013595921));
            g.method_51448().translate(this.originX, this.originY);
            g.method_51448().scale(this.scale, this.scale);
            UiPaint.shadow(g, 0, 0, 840, 510, 12, Integer.MIN_VALUE);
            UiPaint.round(g, 0, 0, 840, 510, 12, -14079434);
            UiPaint.round(g, 1, 1, 838, 508, 11, -15724265);
            UiPaint.strong(g, this.field_22793, "AutoBuilder", 21, 20, -1250061);
            UiPaint.text(g, this.field_22793, "DONUT + REX LIBRARY", 21, 39, -9671552);
            this.button(g, "close", "Close", 765, 16, 56, true, false, this::method_25419);
            this.drawLibrary(g);
            this.drawPreview(g);
            this.drawPositioning(g);
            this.drawActions(g);
            if (this.materialsOpen || !this.details.isEmpty()) {
               this.hits.clear();
               double modalOpacity = UiPaint.pushOpacity(smooth(this.modalProgress));

               try {
                  g.method_25294(0, 0, 840, 510, UiPaint.withOpacity(-1341781745));
                  if (this.materialsOpen) {
                     this.drawMaterials(g);
                  } else {
                     this.drawDetails(g);
                  }
               } finally {
                  UiPaint.popOpacity(modalOpacity);
               }
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

   private void drawLibrary(class_332 g) {
      UiPaint.round(g, 14, 62, 218, 382, 8, UiPaint.blend(-15724265, this.config.accentColor, 0.07));
      UiPaint.text(g, this.field_22793, "schematics/donut/", 25, 76, -6052942);
      this.button(g, "folder", "Open Folder", 24, 96, 116, true, false, () -> {
         try {
            Files.createDirectories(this.folder);
            class_156.method_668().method_60932(this.folder);
         } catch (Exception var2x) {
            this.message = "Cannot open folder: " + errorMessage(var2x);
         }
      });
      this.button(g, "refresh", this.refreshing ? "..." : "Refresh", 148, 96, 73, !this.refreshing, false, this::refreshFiles);
      UiPaint.text(g, this.field_22793, this.files.size() + " schematic" + (this.files.size() == 1 ? "" : "s"), 25, 132, -9671552);
      g.method_44379(24, 151, 222, 423);

      try {
         if (this.files.isEmpty()) {
            UiPaint.text(g, this.field_22793, "Add .schem or .litematic files here.", 27, 167, -6052942);
            UiPaint.text(g, this.field_22793, "Then choose Refresh.", 27, 184, -9671552);
         }

         for (int i = 0; i < this.files.size(); i++) {
            int y = 151 + i * 31 - this.fileScroll;
            if (y + 28 >= 151 && y <= 423) {
               Path file = this.files.get(i);
               boolean selected = file.equals(this.selectedFile);
               UiPaint.round(
                  g,
                  24,
                  y,
                  198,
                  28,
                  5,
                  UiPaint.blend(-15657700, this.config.accentColor, selected ? 0.25 : (this.inside(24, y, 198, 28) ? 0.1 : 0.0))
               );
               UiPaint.text(
                  g, this.field_22793, this.ellipsize(file.getFileName().toString(), 176), 33, y + 10, selected ? -1250061 : -6052942
               );
               this.hit("file" + i, 24, Math.max(151, y), 198, Math.min(423, y + 28) - Math.max(151, y), () -> this.load(file));
            }
         }
      } finally {
         g.method_44380();
      }

      this.scrollbar(g, 226, 151, 271, this.files.size() * 31, this.fileScroll);
   }

   private void drawPreview(class_332 g) {
      Schematic schematic = this.builder.schematic();
      String title = this.loading ? "Loading and validating..." : (schematic == null ? "Choose a schematic" : schematic.name());
      UiPaint.strong(g, this.field_22793, this.ellipsize(title, 380), 250, 67, -1250061);
      this.button(g, "previous", "<", 745, 62, 32, !this.files.isEmpty() && !this.loading, false, () -> this.nextFile(-1));
      this.button(g, "next", ">", 785, 62, 32, !this.files.isEmpty() && !this.loading, false, () -> this.nextFile(1));
      UiPaint.round(g, 246, 95, 576, 171, 8, -16184044);
      UiPaint.outline(g, 246, 95, 576, 171, 8, -14079434);
      g.method_44379(250, 100, 818, 259);

      try {
         for (int x = 252; x < 818; x += 24) {
            UiPaint.line(g, x, 99, x, 263, 338642013);
         }

         for (int y = 99; y < 263; y += 24) {
            UiPaint.line(g, 250, y, 818, y, 338642013);
         }

         this.model.setSchematic(schematic);
         this.model.render(g, 252, 102, 564, 154);
         if (schematic == null) {
            UiPaint.text(g, this.field_22793, "Your 3D preview will appear here", 405, 166, -6052942);
            UiPaint.text(g, this.field_22793, "Sponge .schem and multi-region .litematic support", 373, 185, -9671552);
         }
      } finally {
         g.method_44380();
      }

      this.hit("model", 246, 95, 576, 171, () -> this.draggingPreview = true);
      if (schematic != null) {
         class_2338 d = schematic.dimensions();
         UiPaint.text(
            g,
            this.field_22793,
            d.method_10263()
               + " x "
               + d.method_10264()
               + " x "
               + d.method_10260()
               + " blocks  /  "
               + number(schematic.blocks().size())
               + " blocks  /  "
               + schematic.regionCount()
               + " regions",
            253,
            276,
            -6052942
         );
         UiPaint.text(
            g,
            this.field_22793,
            this.model.sampled() ? "Drag to rotate / Scroll to zoom / Surface sampled" : "Drag to rotate / Scroll to zoom",
            253,
            294,
            -9671552
         );
         this.button(g, "resetView", "Reset view", 728, 273, 89, true, false, this.model::reset);
      }
   }

   private void drawPositioning(class_332 g) {
      boolean available = this.builder.schematic() != null && !this.loading;
      UiPaint.text(g, this.field_22793, "WORLD POSITION", 251, 322, -9671552);

      for (int axis = 0; axis < 3; axis++) {
         int index = axis;
         int x = 251 + axis * 191;
         UiPaint.text(g, this.field_22793, new String[]{"X", "Y", "Z"}[axis], x, 347, -6052942);
         this.button(g, "minus" + axis, "-", x + 17, 338, 24, available, false, () -> this.nudge(index, -1));
         UiPaint.round(g, x + 44, 338, 100, 24, 4, this.coordinateFocus == axis ? -14079434 : -16184044);
         String shown = this.coordinates[axis] + (this.coordinateFocus == axis && System.currentTimeMillis() % 1000L < 500L ? "|" : "");
         UiPaint.text(g, this.field_22793, this.ellipsize(shown, 90), x + 50, 347, available ? -1250061 : -9671552);
         if (available) {
            this.hit("coordinate" + axis, x + 44, 338, 100, 24, () -> {
               this.coordinateFocus = index;
               this.replaceText = true;
            });
         }

         this.button(g, "plus" + axis, "+", x + 147, 338, 24, available, false, () -> this.nudge(index, 1));
      }

      PlacementTransform t = this.builder.transform();
      this.button(
         g,
         "atPlayer",
         "At player",
         251,
         376,
         92,
         available && this.field_22787.field_1724 != null,
         false,
         () -> {
            PlacementTransform current = this.builder.transform();
            this.builder
               .setTransform(
                  new PlacementTransform(this.field_22787.field_1724.method_24515(), current.quarterTurns(), current.mirrorX())
               );
            this.syncCoordinates();
         }
      );
      this.button(g, "rotate", "Rotate " + Math.floorMod(t.quarterTurns(), 4) * 90 + " deg", 351, 376, 114, available, false, () -> {
         if (this.commitCoordinates()) {
            PlacementTransform current = this.builder.transform();
            this.builder.setTransform(new PlacementTransform(current.origin(), current.quarterTurns() + 1, current.mirrorX()));
            this.invalidateMaterials();
         }
      });
      this.button(g, "mirror", "Mirror X: " + (t.mirrorX() ? "On" : "Off"), 473, 376, 119, available, t.mirrorX(), () -> {
         if (this.commitCoordinates()) {
            PlacementTransform current = this.builder.transform();
            this.builder.setTransform(new PlacementTransform(current.origin(), current.quarterTurns(), !current.mirrorX()));
            this.invalidateMaterials();
         }
      });
      this.button(g, "preview", "Show in world", 600, 376, 113, available && this.field_22787.field_1687 != null, false, () -> {
         if (this.commitCoordinates()) {
            this.builder.setPreviewVisible(true);
            this.field_22787.method_1507(null);
         }
      });
      this.button(g, "cancelPreview", "Cancel Preview", 721, 376, 96, this.builder.previewVisible(), false, () -> this.builder.setPreviewVisible(false));
      UiPaint.text(g, this.field_22793, "Ghost opacity", 253, 415, -9671552);
      this.button(g, "opacityDown", "-", 347, 406, 24, true, false, () -> this.setOpacity(-0.05));
      UiPaint.text(g, this.field_22793, Math.round(this.config.autoBuilder.previewOpacity * 100.0) + "%", 380, 415, -6052942);
      this.button(g, "opacityUp", "+", 421, 406, 24, true, false, () -> this.setOpacity(0.05));
      if (this.builder.schematic() != null && !this.builder.schematic().warnings().isEmpty()) {
         this.button(
            g,
            "warnings",
            this.builder.schematic().warnings().size() + " file notices",
            668,
            406,
            149,
            true,
            false,
            () -> this.openDetails(String.join("\n", this.builder.schematic().warnings()))
         );
      }
   }

   private void drawActions(class_332 g) {
      UiPaint.round(g, 16, 450, 808, 1, 0, -14079434);
      boolean available = this.builder.schematic() != null && !this.loading;
      String status = this.builder.status().name().toLowerCase(Locale.ROOT);
      status = Character.toUpperCase(status.charAt(0)) + status.substring(1);
      UiPaint.text(g, this.field_22793, status + "  " + Math.round(this.builder.progress() * 100.0) + "%", 24, 464, -1250061);
      UiPaint.round(g, 24, 482, 203, 4, 2, -14079434);
      UiPaint.round(
         g, 24, 482, (int)(203.0 * this.builder.progress()), 4, 2, UiPaint.alpha(this.config.accentColor, 1.0)
      );
      this.button(g, "materials", "Materials", 251, 459, 103, available, false, () -> {
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
      String action = this.builder.status() == AutoBuilderController.Status.PAUSED
         ? "Resume"
         : (this.builder.isRunning() ? "Pause" : "Start");
      this.button(g, "start", action, 364, 459, 99, available && this.field_22787.field_1687 != null, true, () -> {
         if (this.commitCoordinates()) {
            if (this.builder.isRunning()) {
               this.builder.pause();
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
      this.button(g, "cancel", "Cancel", 473, 459, 89, available, false, this.builder::cancel);
      String reason = this.message.isEmpty() ? this.builder.reason() : this.message;
      UiPaint.text(g, this.field_22793, this.ellipsize(reason, 238), 579, 469, this.message.isEmpty() ? -6052942 : -14741);
      if (!reason.isEmpty()) {
         this.hit("reason", 575, 459, 242, 26, () -> this.openDetails(reason));
      }

      UiPaint.text(g, this.field_22793, "Exact states / Server confirmation / REX stability checks", 251, 493, -9671552);
      this.donut$drawCreativeMode(g);
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

   private void drawDetails(class_332 g) {
      UiPaint.round(g, 135, 114, 570, 280, 10, -15724265);
      UiPaint.outline(g, 135, 114, 570, 280, 10, -14079434);
      UiPaint.strong(g, this.field_22793, "AutoBuilder notice", 154, 134, -1250061);
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
      if (!this.files.isEmpty()) {
         int index = this.files.indexOf(this.selectedFile);
         this.load(this.files.get(Math.floorMod(index < 0 ? 0 : index + direction, this.files.size())));
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
      double hover = this.hoverAnimations.getOrDefault(id, 0.0);
      hover += ((enabled && this.inside(x, y, w, 24) ? 1 : 0) - hover) * (1.0 - Math.exp(-this.seconds * 18.0));
      this.hoverAnimations.put(id, hover);
      UiPaint.round(
         g, x, y, w, 24, 5, UiPaint.blend(-15657700, this.config.accentColor, enabled ? (primary ? 0.55 : 0.09) + hover * 0.18 : 0.0)
      );
      UiPaint.outline(
         g, x, y, w, 24, 5, UiPaint.blend(-14079434, this.config.accentColor, enabled ? (primary ? 0.45 : 0.0) + hover * 0.3 : 0.0)
      );
      String shown = this.ellipsize(label, w - 10);
      UiPaint.text(
         g, this.field_22793, shown, x + (w - UiPaint.width(this.field_22793, shown)) / 2, y + 9, enabled ? -1250061 : -9671552
      );
      if (enabled) {
         this.hit(id, x, y, w, 24, action);
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
            this.keyboardFocus = -1;
            if (target != null) {
               target.action.run();
            }

            return true;
         }
      }
   }

   public boolean method_25403(class_11909 event, double dx, double dy) {
      if (this.draggingPreview && !this.materialsOpen && this.details.isEmpty()) {
         this.model.rotate(dx / this.scale, dy / this.scale);
         return true;
      } else {
         return false;
      }
   }

   public boolean method_25406(class_11909 event) {
      this.draggingPreview = false;
      return true;
   }

   public boolean method_25401(double x, double y, double horizontal, double vertical) {
      if (!this.closing && !this.modalClosing) {
         double lx = (x - this.originX) / this.scale;
         double ly = (y - this.originY) / this.scale;
         if (this.materialsOpen) {
            this.materialScroll = Math.clamp((long)(this.materialScroll - (int)(vertical * 28.0)), 0, Math.max(0, this.filteredRows().size() * 28 - 192));
         } else if (!this.details.isEmpty()) {
            this.materialScroll = Math.clamp(
               (long)(this.materialScroll - (int)(vertical * 25.0)), 0, Math.max(0, this.wrap(this.details, 525).size() * 17 - 181)
            );
         } else if (lx >= 246.0 && lx < 822.0 && ly >= 95.0 && ly < 266.0) {
            this.model.zoom(vertical);
         } else if (lx >= 14.0 && lx < 234.0 && ly >= 151.0 && ly <= 423.0) {
            this.fileScroll = Math.clamp((long)(this.fileScroll - (int)(vertical * 31.0)), 0, Math.max(0, this.files.size() * 31 - 272));
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
            } else if (!this.materialsOpen && this.details.isEmpty()) {
               this.method_25419();
            } else {
               this.closeModal();
            }

            return true;
         } else {
            if (this.coordinateFocus >= 0 || this.searchFocused) {
               String value = this.coordinateFocus >= 0 ? this.coordinates[this.coordinateFocus] : this.searchQuery;
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
      if (this.closing && this.progress == 0.0 && this.field_22787.field_1755 == this) {
         this.field_22787.method_1507(this.parent);
      }

      if (this.modalClosing && this.modalProgress == 0.0) {
         this.materialsOpen = false;
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

   private void donut$drawCreativeMode(class_332 var1) {
      UiPaint.round(var1, 24, 489, 203, 16, 4, -15657700);
      UiPaint.outline(var1, 24, 489, 203, 16, 4, this.config.accentColor);
      UiPaint.text(var1, this.field_22793, CreativeBuildMode.label(this.config.autoBuilder), 35, 493, -1250061);
      this.hit("creativeMode", 24, 489, 203, 16, new CreativeBuildMode(this.config.autoBuilder, this.save));
   }

   @Environment(EnvType.CLIENT)
   private record Hit(String id, int x, int y, int w, int h, Runnable action) {
      boolean contains(double mx, double my) {
         return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
      }
   }
}
