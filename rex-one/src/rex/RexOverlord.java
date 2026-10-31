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
import net.minecraft.class_310;
import net.minecraft.class_332;
import rex.overlord.Overlord;

/**
 * Survival progression toward enchanted netherite armour, a sword and a pickaxe, run from the
 * client's own navigator and miner.
 *
 * <p>What this used to be: a thin bridge that asked Baritone for a pathing process and, when
 * Baritone was not installed, printed "bundled pathfinder did not load" and switched itself off.
 * Nothing was bundled, so on a normal install the module did nothing at all. The progression now
 * walks with the Auto Build navigator and mines with {@link rex.overlord.Mine}, so it runs on
 * Fabric API alone; Baritone may be installed alongside it and is simply not used.
 */
public final class RexOverlord extends Module {
   public static RexOverlord instance;

   public final BooleanSetting hud = new BooleanSetting("Progression HUD", true)
      .describe("A small panel with the stage, what it is doing and what it has broken.");
   public final EnumSetting<HudAnchor> anchor = new EnumSetting<>("Anchor", HudAnchor.TOP_LEFT, HudAnchor.class);
   public final NumberSetting offsetX = new NumberSetting("X", 0.0, 400.0, 6.0, 1.0);
   public final NumberSetting offsetY = new NumberSetting("Y", 0.0, 400.0, 6.0, 1.0);
   public final NumberSetting width = new NumberSetting("Width", 120.0, 400.0, 220.0, 2.0);
   public final BooleanSetting announce = new BooleanSetting("Chat Updates", true)
      .describe("Says in chat when the stage changes.");
   public final ColorSetting background = new ColorSetting("Background", 22, 12, 31, 205);
   public final ColorSetting accent = new ColorSetting("Accent", 255, 171, 69, 255);
   public final ColorSetting text = new ColorSetting("Text", 231, 216, 239, 255);

   /** The engine, kept across enable and disable so a restart replans rather than forgetting. */
   private final Overlord engine = new Overlord();
   private String announced = "";

   public RexOverlord() {
      super(
         "REX Overlord",
         "Plays survival toward enchanted netherite gear: wood, stone, iron, diamonds, a portal, debris, then the enchanting table. No Baritone needed.",
         -1,
         Category.MISC
      );
      instance = this;
      this.addSettings(
         new Setting[]{this.hud, this.anchor, this.offsetX, this.offsetY, this.width, this.announce, this.background, this.accent, this.text}
      );
   }

   public String status() {
      return this.engine.status;
   }

   public Overlord engine() {
      return this.engine;
   }

   @Override
   public void onEnable() {
      super.onEnable();
      class_310 client = class_310.method_1551();
      if (client.field_1724 == null || client.field_1687 == null) {
         Game.message("REX Overlord: join a world first.");
         this.setEnabled(false);
         return;
      }
      // Two things driving the same player fight over the movement input.
      try {
         AutoBuild builder = dev.krypton.client.KryptonClient.instance.modules().get(AutoBuild.class);
         if (builder != null && builder.isEnabled()) {
            builder.setEnabled(false);
         }
         if (BoatFly.instance != null && BoatFly.instance.isEnabled()) {
            BoatFly.instance.setEnabled(false);
         }
      } catch (Exception | LinkageError ignored) {
      }
      this.announced = "";
      this.engine.start(client);
      Game.message("REX Overlord started. .overlord stop stops it, .overlord status reports.");
   }

   @Override
   public void onDisable() {
      try {
         this.engine.stop(class_310.method_1551());
      } catch (Exception | LinkageError failure) {
         System.err.println("[ONE] Overlord stop: " + failure);
      }
      super.onDisable();
   }

   @EventHandler
   public void onOverlordTick(ClientTickEvent event) {
      try {
         class_310 client = class_310.method_1551();
         this.engine.tick(client);
         if (this.announce.get() && !this.engine.stage.name().equals(this.announced)) {
            this.announced = this.engine.stage.name();
            Game.message("Overlord: " + this.engine.stage.label() + " - " + this.engine.stage.description);
         }
         if (!this.engine.running) {
            if (!this.engine.stopped.isEmpty()) {
               Game.message("REX Overlord stopped: " + this.engine.stopped);
            }
            this.setEnabled(false);
         }
      } catch (Exception | LinkageError failure) {
         Game.message("REX Overlord failed: " + failure);
         System.err.println("[ONE] Overlord tick: " + failure);
         failure.printStackTrace();
         this.setEnabled(false);
      }
   }

   @EventHandler
   public void onOverlordRender(Render2DEvent event) {
      if (!this.hud.get()) {
         return;
      }
      try {
         this.draw(event.sqx);
      } catch (Exception | LinkageError failure) {
         System.err.println("[ONE] Overlord HUD: " + failure);
      }
   }

   private void draw(class_332 graphics) throws Exception {
      if (Bridge.field(Bridge.client(), "field_1724") == null) {
         return;
      }
      Object font = HudPanel.font();
      int line = HudPanel.lineHeight(font);
      int step = line + 2;
      int panelWidth = this.width.getInt();
      String[] rows = {
         "Stage", this.engine.stage.label(),
         "Doing", this.engine.status,
         "Broken", String.valueOf(this.engine.mined())
      };
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
      HudPanel.string(graphics, font, "REX OVERLORD", x + HudPanel.PAD + 2, y + 3, tint);
      int row = y + line + 6;
      for (int i = 0; i < rows.length; i += 2) {
         HudPanel.row(graphics, font, rows[i], HudPanel.clip(font, rows[i + 1], panelWidth - HudPanel.PAD * 2 - 44), x, row, panelWidth, dim, label);
         row += step;
      }
   }

   /** Chat entry point. Returns true when the text was an Overlord command. */
   public static boolean command(String text) {
      // The jar's command router calls this for every line typed, so the pathfinder's own
      // commands are picked off here rather than in a second copy of the router.
      if (RexPath.command(text)) {
         return true;
      }
      OverlordInput.Command input = OverlordInput.parse(text);
      if (input == null) {
         return false;
      }
      try {
         if (instance == null) {
            var manager = dev.krypton.client.KryptonClient.instance.modules();
            RexOverlord existing = manager.get(RexOverlord.class);
            if (existing != null) {
               instance = existing;
            } else {
               var register = manager.getClass().getDeclaredMethod("register", Module.class);
               register.setAccessible(true);
               register.invoke(manager, new RexOverlord());
            }
         }
         if (instance == null) {
            Game.message("REX Overlord could not be registered");
            return true;
         }
         switch (input.action()) {
            case "start", "resume", "go" -> {
               if (instance.isEnabled()) {
                  Game.message("REX Overlord is already running: " + instance.status());
               } else {
                  instance.setEnabled(true);
               }
            }
            case "stop", "pause" -> {
               instance.setEnabled(false);
               Game.message("REX Overlord stopped");
            }
            case "status" -> {
               Overlord engine = instance.engine();
               Game.message("REX Overlord: " + (instance.isEnabled() ? "ON" : "OFF") + " | " + engine.stage.label() + " | " + engine.status);
               Game.message("  " + engine.stage.description + " | blocks broken: " + engine.mined());
            }
            case "hud" -> {
               instance.hud.set(!instance.hud.get());
               Game.message("Overlord HUD " + (instance.hud.get() ? "on" : "off"));
            }
            default -> Game.message("REX Overlord: .overlord start | stop | status | hud");
         }
      } catch (Exception | LinkageError failure) {
         Game.message("REX Overlord command failed: " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
      }
      return true;
   }
}
