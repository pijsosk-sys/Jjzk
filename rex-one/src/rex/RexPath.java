package rex;

import dev.donut.client.render.HighlightRenderer;
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
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.class_238;
import net.minecraft.class_2338;
import net.minecraft.class_239;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_3965;
import rex.path.Goal;
import rex.path.Path;
import rex.path.PathFinder;
import rex.path.Traveller;

/**
 * REX's own pathfinder: A* over the loaded blocks, thought out a slice per tick, walked with the
 * player's own movement keys, and drawn in the world so you can see where it is going.
 *
 * <p>It is not a wrapper around anything. Baritone is bundled in this jar and is not used here;
 * the search is {@link rex.path.PathFinder}, the world model {@link rex.path.Terrain} and the walk
 * {@link rex.path.Follower}. A search that runs out of budget hands back the best part of the way
 * and the next search starts from the end of it while that part is being walked, so a walk across
 * a thousand blocks starts moving at once instead of standing still and thinking.
 *
 * <p>Opening any screen stops it dead and closing the screen carries on.
 */
public final class RexPath extends Module {
   public static RexPath instance;

   public final BooleanSetting pauseOnScreen = new BooleanSetting("Pause On Screen", true)
      .describe("Opening any screen puts the walk on hold until it is closed.");
   public final BooleanSetting allowBreak = new BooleanSetting("Break Through", true)
      .describe("Mine blocks that are in the way, when going round them is dearer.");
   public final BooleanSetting allowPlace = new BooleanSetting("Bridge Gaps", false)
      .describe("Throw cobblestone or dirt into a gap instead of walking round it.");
   public final BooleanSetting allowSwim = new BooleanSetting("Swim", true);
   public final BooleanSetting sprint = new BooleanSetting("Sprint", true);
   public final NumberSetting maxFall = new NumberSetting("Max Drop", 1.0, 20.0, 4.0, 1.0)
      .describe("How far it is willing to walk off an edge.");
   public final NumberSetting microsPerTick = new NumberSetting("Microseconds Per Tick", 200.0, 20000.0, 3000.0, 100.0)
      .describe("Thinking time each tick. Higher finds long routes sooner and costs frames.");
   public final NumberSetting nodesPerTick = new NumberSetting("Cells Per Tick", 200.0, 40000.0, 4000.0, 100.0);
   public final NumberSetting maxNodes = new NumberSetting("Search Limit", 10000.0, 2000000.0, 300000.0, 10000.0)
      .describe("Cells one search may look at before it settles for the best part of the way.");
   public final NumberSetting lookAhead = new NumberSetting("Plan Ahead", 2.0, 60.0, 12.0, 1.0)
      .describe("Cells left on the current stretch when the next search starts.");
   public final BooleanSetting showPath = new BooleanSetting("Show Path", true);
   public final BooleanSetting throughWalls = new BooleanSetting("Through Walls", true);
   public final BooleanSetting hud = new BooleanSetting("HUD", true);
   // Overlord's panel sits top left and the build HUD top right, so this one goes bottom right.
   public final EnumSetting<HudAnchor> anchor = new EnumSetting<>("Anchor", HudAnchor.BOTTOM_RIGHT, HudAnchor.class);
   public final NumberSetting offsetX = new NumberSetting("X", 0.0, 400.0, 6.0, 1.0);
   public final NumberSetting offsetY = new NumberSetting("Y", 0.0, 400.0, 34.0, 1.0);
   public final NumberSetting width = new NumberSetting("Width", 120.0, 400.0, 230.0, 2.0);
   public final ColorSetting pathColor = new ColorSetting("Path", 120, 200, 255, 190);
   public final ColorSetting nextColor = new ColorSetting("Next Step", 255, 235, 120, 230);
   public final ColorSetting goalColor = new ColorSetting("Goal", 120, 255, 160, 230);
   public final ColorSetting background = new ColorSetting("Background", 16, 18, 25, 225);
   public final ColorSetting accent = new ColorSetting("Accent", 120, 200, 255, 255);
   public final ColorSetting text = new ColorSetting("Text", 233, 236, 242, 255);

   private final Traveller traveller = new Traveller();
   /** Where to go once the module is on; a goal can be given before it is enabled. */
   private Goal pending;
   private String state = "Idle";
   private long ticks;
   private static boolean rendering;

   public RexPath() {
      super(
         "REX Pathfinder",
         "Walks to anywhere it can reach: own A* search, a drawn route, breaking and bridging. .goto x y z",
         -1,
         Category.MISC
      );
      instance = this;
      this.addSettings(
         new Setting[]{
            this.pauseOnScreen, this.allowBreak, this.allowPlace, this.allowSwim, this.sprint, this.maxFall,
            this.microsPerTick, this.nodesPerTick, this.maxNodes, this.lookAhead,
            this.showPath, this.throughWalls, this.hud, this.anchor, this.offsetX, this.offsetY, this.width,
            this.pathColor, this.nextColor, this.goalColor, this.background, this.accent, this.text
         }
      );
      install();
   }

   /** Hooks the route drawing into the client's one world renderer, once. */
   private static void install() {
      if (!rendering) {
         rendering = true;
         HighlightRenderer.overlay(RexPath::boxes);
      }
   }

   public String state() {
      return this.state;
   }

   public Goal goal() {
      return this.traveller.goal();
   }

   private Traveller.Tuning tuning() {
      return new Traveller.Tuning(
         new PathFinder.Options(this.allowBreak.get(), this.allowPlace.get(), this.allowSwim.get(), this.maxFall.getInt(), 1.35, this.maxNodes.getInt()),
         this.pauseOnScreen.get(),
         this.lookAhead.getInt(),
         this.microsPerTick.getLong(),
         this.nodesPerTick.getInt(),
         this.sprint.get()
      );
   }

   /** Sends the walk somewhere. The module turns itself on. */
   public void send(Goal goal) {
      this.pending = goal;
      if (!this.isEnabled()) {
         this.setEnabled(true);
      } else {
         this.traveller.go(class_310.method_1551(), goal);
         this.state = this.traveller.status;
      }
   }

   public void clear() {
      this.pending = null;
      this.traveller.stop(class_310.method_1551());
      this.state = "Idle";
   }

   @Override
   public void onEnable() {
      super.onEnable();
      class_310 client = class_310.method_1551();
      if (client.field_1724 == null) {
         Game.message("REX Pathfinder: join a world first.");
         this.setEnabled(false);
         return;
      }
      if (this.pending != null) {
         this.traveller.go(client, this.pending);
         this.state = this.traveller.status;
      } else {
         this.state = "No goal. .goto x y z";
      }
   }

   @Override
   public void onDisable() {
      try {
         this.traveller.stop(class_310.method_1551());
      } catch (Exception | LinkageError failure) {
         System.err.println("[ONE] Pathfinder stop: " + failure);
      }
      super.onDisable();
   }

   @EventHandler
   public void onPathTick(ClientTickEvent event) {
      this.ticks++;
      class_310 client = class_310.method_1551();
      try {
         if (!this.traveller.busy()) {
            this.state = "No goal. .goto x y z";
            this.traveller.release();
            return;
         }
         Traveller.Result result = this.traveller.tick(client, this.tuning(), this.ticks);
         this.state = this.traveller.status;
         if (result == Traveller.Result.ARRIVED) {
            Game.message("REX Pathfinder: arrived at " + this.traveller.goal().describe() + " after " + this.traveller.searches()
               + " search" + (this.traveller.searches() == 1 ? "" : "es") + ".");
            this.clear();
            this.setEnabled(false);
         } else if (result == Traveller.Result.UNREACHABLE) {
            this.give(client, this.traveller.status);
         }
      } catch (Exception | LinkageError failure) {
         Game.message("REX Pathfinder failed: " + failure);
         System.err.println("[ONE] Pathfinder: " + failure);
         failure.printStackTrace();
         this.clear();
         this.setEnabled(false);
      }
   }

   private void give(class_310 client, String why) {
      Game.message("REX Pathfinder: " + why);
      this.state = why;
      this.clear();
      this.setEnabled(false);
   }

   // ------------------------------------------------------------------ drawing

   /** The route, as boxes the one world renderer draws. */
   private static List<HighlightRenderer.Overlay> boxes() {
      RexPath path = instance;
      if (path == null || !path.isEnabled() || !path.showPath.get()) {
         return List.of();
      }
      Path route = path.traveller.path();
      if (route == null) {
         return List.of();
      }
      boolean through = path.throughWalls.get();
      List<HighlightRenderer.Overlay> out = new ArrayList<>();
      int from = Math.max(0, path.traveller.index() - 1);
      int to = Math.min(route.length(), from + 320);
      int line = HudPanel.argb(path.pathColor.m());
      int next = HudPanel.argb(path.nextColor.m());
      for (int i = from; i < to; i++) {
         class_2338 cell = route.cell(i);
         boolean current = i == path.traveller.index();
         // A low slab in the middle of the cell: it reads as a trail of footsteps rather than a
         // wall of cubes, and it does not hide the block you are about to break.
         double inset = current ? 0.22 : 0.33;
         class_238 box = new class_238(
            cell.method_10263() + inset, cell.method_10264() + 0.02, cell.method_10260() + inset,
            cell.method_10263() + 1 - inset, cell.method_10264() + (current ? 0.42 : 0.14), cell.method_10260() + 1 - inset
         );
         int colour = current ? next : line;
         out.add(new HighlightRenderer.Overlay(box, (colour & 0xFFFFFF) | (colour >>> 24) / 4 << 24, colour, current ? 2.5F : 1.3F, through));
      }
      class_2338 marker = path.traveller.goal() == null ? null : path.traveller.goal().marker();
      if (marker != null) {
         int colour = HudPanel.argb(path.goalColor.m());
         out.add(
            new HighlightRenderer.Overlay(
               new class_238(marker.method_10263(), marker.method_10264(), marker.method_10260(),
                  marker.method_10263() + 1, marker.method_10264() + 1, marker.method_10260() + 1),
               (colour & 0xFFFFFF) | (colour >>> 24) / 5 << 24, colour, 2.0F, through
            )
         );
      }
      return out;
   }

   @EventHandler
   public void onPathRender(Render2DEvent event) {
      if (!this.hud.get()) {
         return;
      }
      try {
         this.draw(event.sqx);
      } catch (Exception | LinkageError failure) {
         System.err.println("[ONE] Pathfinder HUD: " + failure);
      }
   }

   private void draw(class_332 graphics) throws Exception {
      if (Bridge.field(Bridge.client(), "field_1724") == null) {
         return;
      }
      Path route = this.traveller.path();
      String left = route == null ? "-" : (route.length() - this.traveller.index()) + " cells" + (route.complete ? "" : " (part way)");
      String[] rows = {
         "Goal", this.traveller.goal() == null ? "none" : this.traveller.goal().describe(),
         "Doing", this.state,
         "Left", left,
         "Search", this.traveller.expanded() + " cells, " + this.traveller.micros() + " us",
         "Cache", this.traveller.cached() + " blocks"
      };
      Object font = HudPanel.font();
      int line = HudPanel.lineHeight(font);
      int step = line + 2;
      int panelWidth = this.width.getInt();
      int height = line + 6 + rows.length / 2 * step + 3;
      int[] origin = HudPanel.origin((HudAnchor)this.anchor.get(), this.offsetX.getInt(), this.offsetY.getInt(), panelWidth, height);
      int x = origin[0];
      int y = origin[1];
      Color base = this.text.m();
      int tint = HudPanel.argb(this.accent.m());
      int label = HudPanel.argb(base);
      int dim = HudPanel.argb(HudPanel.fade(base, 150));
      int faint = HudPanel.argb(HudPanel.fade(base, 38));
      HudPanel.card(graphics, x, y, panelWidth, height, HudPanel.argb(this.background.m()), tint, faint);
      HudPanel.string(graphics, font, "REX PATHFINDER", x + HudPanel.PAD + 2, y + 3, tint);
      int row = y + line + 6;
      for (int i = 0; i < rows.length; i += 2) {
         HudPanel.row(graphics, font, rows[i], HudPanel.clip(font, rows[i + 1], panelWidth - HudPanel.PAD * 2 - 44), x, row, panelWidth, dim, label);
         row += step;
      }
   }

   // ------------------------------------------------------------------ chat

   private static RexPath module() {
      if (instance == null) {
         var manager = dev.krypton.client.KryptonClient.instance.modules();
         RexPath existing = manager.get(RexPath.class);
         if (existing != null) {
            instance = existing;
         } else {
            try {
               var register = manager.getClass().getDeclaredMethod("register", Module.class);
               register.setAccessible(true);
               register.invoke(manager, new RexPath());
            } catch (ReflectiveOperationException failure) {
               Game.message("REX Pathfinder could not be registered: " + failure);
            }
         }
      }
      return instance;
   }

   /** Returns true when the text was a pathfinder command. */
   public static boolean command(String text) {
      if (text == null) {
         return false;
      }
      String[] words = text.strip().split("\\s+");
      if (words.length == 0) {
         return false;
      }
      String head = words[0].toLowerCase(Locale.ROOT);
      if (!head.equals(".goto") && !head.equals(".path") && !head.equals("#goto")) {
         return false;
      }
      RexPath module = module();
      if (module == null) {
         return true;
      }
      try {
         class_310 client = class_310.method_1551();
         String first = words.length > 1 ? words[1].toLowerCase(Locale.ROOT) : "status";
         switch (first) {
            case "stop", "cancel", "off" -> {
               module.clear();
               module.setEnabled(false);
               Game.message("REX Pathfinder stopped.");
            }
            case "status" -> Game.message("REX Pathfinder: " + (module.isEnabled() ? "ON" : "OFF") + " | goal "
               + (module.goal() == null ? "none" : module.goal().describe()) + " | " + module.state);
            case "here" -> {
               if (client.field_1765 instanceof class_3965 hit && client.field_1765.method_17783() == class_239.class_240.field_1332) {
                  class_2338 above = hit.method_17777().method_10084();
                  module.send(new Goal.Block(above.method_10263(), above.method_10264(), above.method_10260()));
                  Game.message("REX Pathfinder: walking to the block you are looking at.");
               } else {
                  Game.message("Look at a block first.");
               }
            }
            case "y", "level" -> {
               if (words.length < 3) {
                  Game.message(".goto y <height>");
               } else {
                  module.send(new Goal.Height(Integer.parseInt(words[2])));
                  Game.message("REX Pathfinder: heading for y=" + words[2] + ".");
               }
            }
            default -> {
               if (words.length == 4) {
                  module.send(new Goal.Block(Integer.parseInt(words[1]), Integer.parseInt(words[2]), Integer.parseInt(words[3])));
                  Game.message("REX Pathfinder: walking to " + words[1] + " " + words[2] + " " + words[3] + ".");
               } else if (words.length == 3) {
                  module.send(new Goal.Column(Integer.parseInt(words[1]), Integer.parseInt(words[2])));
                  Game.message("REX Pathfinder: walking to column " + words[1] + " " + words[2] + ".");
               } else if (words.length == 2) {
                  block(module, client, first);
               } else {
                  help();
               }
            }
         }
      } catch (NumberFormatException wrong) {
         help();
      } catch (Exception | LinkageError failure) {
         Game.message("REX Pathfinder: " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
      }
      return true;
   }

   /** .goto diamond_ore: the nearest one in the chunks the client has. */
   private static void block(RexPath module, class_310 client, String id) {
      if (rex.overlord.Ids.block(id) == null) {
         Game.message("No block called " + id + ".");
         help();
         return;
      }
      rex.overlord.Scan scan = new rex.overlord.Scan();
      class_2338 feet = client.field_1724.method_24515();
      scan.restart(feet, Set.of(id), 64, Math.max(client.field_1687.method_31607(), feet.method_10264() - 40), Math.min(client.field_1687.method_31600() - 1, feet.method_10264() + 40));
      while (scan.running()) {
         scan.step(client, 400000);
      }
      List<class_2338> found = scan.results();
      if (found.isEmpty()) {
         Game.message("No " + id + " in the blocks loaded around you.");
         return;
      }
      class_2338 target = found.get(0).method_10084();
      module.send(new Goal.Near(target.method_10263(), target.method_10264(), target.method_10260(), 1.5));
      Game.message("REX Pathfinder: nearest " + id + " at " + found.get(0).method_10263() + " " + found.get(0).method_10264() + " " + found.get(0).method_10260() + ".");
   }

   private static void help() {
      Game.message(".goto <x> <y> <z> | <x> <z> | y <height> | here | <block id> | stop | status");
   }
}
