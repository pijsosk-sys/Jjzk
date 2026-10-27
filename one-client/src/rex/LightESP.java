package rex;

import dev.krypton.core.event.EventHandler;
import dev.krypton.core.event.Render3DEvent;
import dev.krypton.core.event.WorldTickEvent;
import dev.krypton.core.module.Category;
import dev.krypton.core.module.Module;
import dev.krypton.core.setting.BooleanSetting;
import dev.krypton.core.setting.EnumSetting;
import dev.krypton.core.setting.NumberSetting;
import dev.krypton.core.setting.Setting;
import dev.krypton.render.RenderMode;
import dev.krypton.util.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Player boxes built from a tick snapshot instead of a per-frame entity walk: hitbox size, team
 * colour and tab-list state are resolved on the scan tick, so each frame reads three coordinates
 * and three interpolation fields per player and draws.
 */
public final class LightESP extends Module {
   public final NumberSetting range = new NumberSetting("Range", 8.0, 256.0, 96.0, 8.0);
   public final NumberSetting scanTicks = new NumberSetting("Scan Interval", 1.0, 20.0, 4.0, 1.0)
      .describe("Ticks between target scans. Higher is cheaper.");
   public final NumberSetting limit = new NumberSetting("Max Targets", 4.0, 128.0, 48.0, 4.0);
   public final NumberSetting alpha = new NumberSetting("Alpha", 0.0, 255.0, 110.0, 1.0);
   public final EnumSetting<RenderMode> mode = new EnumSetting<>("Mode", RenderMode.Both, RenderMode.class);
   public final BooleanSetting tracers = new BooleanSetting("Tracers", false);
   public final BooleanSetting listed = new BooleanSetting("Tab List Only", true)
      .describe("Skips holograms, shop stands and NPC bodies that hold no tab-list entry.");
   public final BooleanSetting invisible = new BooleanSetting("Invisible", true);
   public final BooleanSetting health = new BooleanSetting("Health Tint", true)
      .describe("Red at low health, orange at full.");
   public final BooleanSetting friends = new BooleanSetting("Friends", true);

   private static final double CLAMP = 64.0;
   private List<LightESP.Target> snapshot = List.of();
   private Object world;
   private long ticks;
   private int alphaUsed = -1;
   private boolean listedBroken;

   public LightESP() {
      super("Light ESP", "Snapshot player boxes with a tab-list filter for DonutSMP.", -1, Category.RENDER);
      this.addSettings(
         new Setting[]{this.range, this.scanTicks, this.limit, this.alpha, this.mode, this.tracers, this.listed, this.invisible, this.health, this.friends}
      );
   }

   @Override
   public void onEnable() {
      super.onEnable();
      this.snapshot = List.of();
      this.world = null;
      this.ticks = 0L;
      this.alphaUsed = -1;
   }

   @Override
   public void onDisable() {
      super.onDisable();
      this.snapshot = List.of();
      this.world = null;
   }

   @EventHandler
   public void onLightTick(WorldTickEvent event) {
      try {
         Object client = Bridge.client();
         Object world = Bridge.field(client, "field_1687");
         Object self = Bridge.field(client, "field_1724");
         if (world == null || self == null) {
            this.snapshot = List.of();
            this.world = null;
            return;
         }
         if (world != this.world) {
            this.world = world;
            this.snapshot = List.of();
            this.ticks = 0L;
         }
         if (this.ticks++ % (long)this.scanTicks.getInt() == 0L || this.alphaUsed != this.alpha.getInt()) {
            this.scan(client, world, self);
         }
      } catch (Exception failure) {
         System.err.println("[ONE] Light ESP scan: " + failure);
         this.snapshot = List.of();
      }
   }

   private void scan(Object client, Object world, Object self) throws Exception {
      int alpha = this.alpha.getInt();
      this.alphaUsed = alpha;
      Object friends = this.friend(null);
      int cap = this.limit.getInt();
      double reach = this.range.getDouble();
      List<LightESP.Target> found = new ArrayList<>(Math.min(cap, 16));
      for (Object entity : (Iterable<?>)Bridge.call(world, "method_18456")) {
         if (found.size() >= cap) {
            break;
         }
         if (entity == self
            || !(Boolean)Bridge.call(entity, "method_5805")
            || (Boolean)Bridge.call(entity, "method_7325")
            || (Boolean)Bridge.call(entity, "method_29504")
            || !this.invisible.get() && (Boolean)Bridge.call(entity, "method_5767")
            || !EspRender.near(self, entity, reach)
            || !this.inTabList(client, entity)) {
            continue;
         }
         boolean mate = friends != null && (Boolean)Game.invoke(friends, "vo", new String[]{"net.minecraft.class_1657"}, entity);
         if (mate && !this.friends.get()) {
            continue;
         }
         Object box = Bridge.call(entity, "method_5829");
         double width = CombatAccess.num(box, "field_1320") - CombatAccess.num(box, "field_1323");
         double height = CombatAccess.num(box, "field_1325") - CombatAccess.num(box, "field_1322");
         found.add(new LightESP.Target(entity, width / 2.0, height, this.tone(entity, mate, alpha)));
      }
      this.snapshot = found;
   }

   /** Fill and outline are built once per scan so the render path allocates nothing. */
   private Color[] tone(Object entity, boolean mate, int alpha) throws Exception {
      int red = 255;
      int green = 135;
      int blue = 60;
      if (mate) {
         red = 85;
         green = 210;
         blue = 230;
      } else if (this.health.get()) {
         double current = ((Number)Bridge.call(entity, "method_6032")).doubleValue();
         double maximum = ((Number)Bridge.call(entity, "method_6063")).doubleValue();
         double share = maximum > 0.0 ? Math.max(0.0, Math.min(1.0, current / maximum)) : 1.0;
         green = (int)(40.0 + 95.0 * share);
         blue = (int)(40.0 * share);
      }
      return new Color[]{new Color(red, green, blue, Math.min(48, alpha)), new Color(red, green, blue, Math.min(255, alpha))};
   }

   private boolean inTabList(Object client, Object entity) throws Exception {
      if (!this.listed.get() || this.listedBroken) {
         return true;
      }
      try {
         Object handler = Bridge.call(client, "method_1562");
         if (handler == null) {
            return true;
         }
         return Game.invoke(handler, "method_2871", new String[]{"java.util.UUID"}, Bridge.call(entity, "method_5667")) != null;
      } catch (ReflectiveOperationException unavailable) {
         this.listedBroken = true;
         Game.message("Light ESP: tab-list filter unavailable on this version, drawing every player.");
         return true;
      }
   }

   private Object friend(Object unused) {
      try {
         Object instance = Game.type("dev.krypton.client.KryptonClient").getField("instance").get(null);
         Object modules = Bridge.call(instance, "modules");
         return Game.invoke(modules, "get", new String[]{"java.lang.Class"}, Game.type("dev.krypton.impl.modules.client.Friends"));
      } catch (LinkageError | Exception unavailable) {
         return null;
      }
   }

   @EventHandler
   public void onLightRender(Render3DEvent event) {
      List<LightESP.Target> targets = this.snapshot;
      if (!targets.isEmpty()) {
         RenderMode mode = (RenderMode)this.mode.get();
         boolean tracers = this.tracers.get();
         float delta = Math.max(0.0F, Math.min(1.0F, event.tickDelta));
         try {
            for (LightESP.Target target : targets) {
               double x = ((Number)Bridge.call(target.entity, "method_23317")).doubleValue();
               double y = ((Number)Bridge.call(target.entity, "method_23318")).doubleValue();
               double z = ((Number)Bridge.call(target.entity, "method_23321")).doubleValue();
               x += back(target.entity, "field_6038", x, delta);
               y += back(target.entity, "field_5971", y, delta);
               z += back(target.entity, "field_5989", z, delta);
               if (!Double.isFinite(x + y + z)) {
                  continue;
               }
               double west = x - target.halfWidth;
               double east = x + target.halfWidth;
               double north = z - target.halfWidth;
               double south = z + target.halfWidth;
               double top = y + target.height;
               event.renderer.vqo(west, y, north, east, top, south, target.tone[0], target.tone[1], mode, 0);
               if (tracers) {
                  event.renderer.aen(event.camX, event.camY - 0.15, event.camZ, x, y + target.height * 0.5, z, target.tone[1]);
               }
            }
         } catch (Exception failure) {
            System.err.println("[ONE] Light ESP render: " + failure);
            this.snapshot = List.of();
         }
      }
   }

   private static double back(Object entity, String field, double now, float delta) throws Exception {
      double previous = ((Number)Bridge.field(entity, field)).doubleValue();
      return Math.abs(previous - now) > CLAMP ? 0.0 : (previous - now) * (1.0F - delta);
   }

   private record Target(Object entity, double halfWidth, double height, Color[] tone) {
   }
}
