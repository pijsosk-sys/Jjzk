package rex;

import dev.krypton.core.event.ClientTickEvent;
import dev.krypton.core.event.EventHandler;
import dev.krypton.core.module.Category;
import dev.krypton.core.module.Module;
import dev.krypton.core.setting.BooleanSetting;
import dev.krypton.core.setting.EnumSetting;
import dev.krypton.core.setting.NumberSetting;
import dev.krypton.core.setting.Setting;

/**
 * Bridging that behaves like a hand on a mouse: it turns toward the face it is going to click one
 * tick before it clicks it, so the rotation the server sees is the rotation the placement was made
 * with; the turn itself goes through Aim Tune, so it is eased, noised and snapped to the mouse grid
 * rather than a jump to a perfect angle. It aims at a point on the face rather than its centre,
 * keeps the pitch inside a human band, leaves sprint alone unless told otherwise, and puts the
 * hotbar slot back when it stops.
 */
public final class ScaffoldPlus extends Module {
   public static ScaffoldPlus instance;

   public final EnumSetting<ScaffoldMode> mode = new EnumSetting<>("Mode", ScaffoldMode.NORMAL, ScaffoldMode.class)
      .describe("NORMAL bridges where you walk, TELLY only places while you are off the ground, TOWER places under you.");
   public final NumberSetting extend = new NumberSetting("Extend", 0.0, 2.0, 1.0, 1.0)
      .describe("Blocks ahead of your feet it is allowed to fill.");
   public final NumberSetting yawStep = new NumberSetting("Yaw Step", 5.0, 180.0, 60.0, 5.0)
      .describe("Degrees of yaw it may cover in one tick before easing.");
   public final NumberSetting pitchStep = new NumberSetting("Pitch Step", 5.0, 90.0, 40.0, 5.0);
   public final NumberSetting minPitch = new NumberSetting("Min Pitch", 30.0, 90.0, 65.0, 1.0)
      .describe("Keeps the look inside a band a person could hold while bridging.");
   public final NumberSetting maxPitch = new NumberSetting("Max Pitch", 30.0, 90.0, 85.0, 1.0);
   public final NumberSetting yawOffset = new NumberSetting("Yaw Offset", -45.0, 45.0, 0.0, 1.0)
      .describe("Turns the aim off the face normal, for diagonal bridging.");
   public final NumberSetting delay = new NumberSetting("Place Delay", 0.0, 10.0, 0.0, 1.0)
      .describe("Ticks between placements.");
   public final NumberSetting spot = new NumberSetting("Face Spread", 0.0, 0.4, 0.2, 0.05)
      .describe("How far off the middle of the face each click lands.");
   public final BooleanSetting rotate = new BooleanSetting("Rotate", true)
      .describe("Off places without turning, which no rotation check will believe.");
   public final BooleanSetting swing = new BooleanSetting("Swing", true);
   public final BooleanSetting restore = new BooleanSetting("Restore Slot", true);
   public final BooleanSetting keepSprint = new BooleanSetting("Keep Sprint", true)
      .describe("Leaves sprinting alone instead of dropping it to place.");
   public final BooleanSetting safeOnly = new BooleanSetting("Solid Blocks Only", true)
      .describe("Refuses sand, gravel, concrete powder and anything else that falls.");

   private static final String[] FALLING = {"sand", "gravel", "concrete_powder", "anvil", "dragon_egg", "scaffolding"};

   private int previousSlot = -1;
   private int cooldown;
   private long ticks;
   private long armedTick = -1L;
   private Object armedSupport;
   private int[] armedTarget;
   private double wantedYaw;
   private double wantedPitch;
   private String state = "idle";

   public ScaffoldPlus() {
      super("Scaffold Plus", "Bridging that turns before it places, eased and grid-snapped.", -1, Category.MISC);
      this.addSettings(
         new Setting[]{
            this.mode, this.extend, this.yawStep, this.pitchStep, this.minPitch, this.maxPitch, this.yawOffset,
            this.delay, this.spot, this.rotate, this.swing, this.restore, this.keepSprint, this.safeOnly
         }
      );
      instance = this;
   }

   @Override
   public void onEnable() {
      super.onEnable();
      this.previousSlot = -1;
      this.cooldown = 0;
      this.armedTick = -1L;
      this.armedSupport = null;
      this.armedTarget = null;
      this.state = "waiting for a block";
   }

   @Override
   public void onDisable() {
      super.onDisable();
      this.release();
      this.state = "idle";
   }

   private void release() {
      try {
         if (this.restore.get() && this.previousSlot >= 0) {
            Object player = Bridge.field(Bridge.client(), "field_1724");
            if (player != null) {
               Game.invoke(Bridge.call(player, "method_31548"), "method_61496", new String[]{"int"}, this.previousSlot);
            }
         }
      } catch (LinkageError | Exception ignored) {
      }
      this.previousSlot = -1;
      this.armedSupport = null;
      this.armedTarget = null;
   }

   public String state() {
      return this.state;
   }

   @EventHandler
   public void onScaffoldTick(ClientTickEvent event) {
      try {
         this.ticks++;
         Object client = Bridge.client();
         Object player = Bridge.field(client, "field_1724");
         Object world = Bridge.field(client, "field_1687");
         if (player == null || world == null || Bridge.field(client, "field_1755") != null) {
            this.state = "no world";
            return;
         }
         if (this.cooldown > 0) {
            this.cooldown--;
         }
         int slot = this.blockSlot(player);
         if (slot < 0) {
            this.state = "no blocks in the hotbar";
            this.armedTarget = null;
            return;
         }
         int[] target = this.target(player, world);
         if (target == null) {
            this.state = "nothing to fill";
            this.armedTarget = null;
            return;
         }
         Object support = null;
         int[] face = null;
         for (int[] side : SIDES) {
            int[] neighbour = {target[0] + side[0], target[1] + side[1], target[2] + side[2]};
            Object state = Game.state(world, neighbour[0], neighbour[1], neighbour[2]);
            if (!Game.replaceable(state)) {
               support = neighbour;
               face = new int[]{-side[0], -side[1], -side[2]};
               break;
            }
         }
         if (support == null) {
            this.state = "no face to click";
            this.armedTarget = null;
            return;
         }
         int[] neighbour = (int[])support;
         double[] hit = this.hitPoint(neighbour, face);
         double[] look = this.look(player, hit);
         if (this.rotate.get()) {
            this.aim(player, look);
         }
         boolean aimed = !this.rotate.get() || this.armedTarget != null && sameBlock(this.armedTarget, target) && this.armedTick < this.ticks;
         if (!aimed) {
            this.armedTarget = target;
            this.armedTick = this.ticks;
            this.state = "turning";
            return;
         }
         if (this.cooldown > 0) {
            this.state = "holding";
            return;
         }
         this.place(client, player, slot, neighbour, face, hit);
         this.cooldown = this.delay.getInt();
         this.armedTarget = null;
         this.state = "placed " + target[0] + " " + target[1] + " " + target[2];
      } catch (LinkageError | Exception failure) {
         this.state = "stopped: " + failure;
         Game.message("Scaffold Plus: " + failure);
         this.setEnabled(false);
      }
   }

   private static final int[][] SIDES = {{0, -1, 0}, {-1, 0, 0}, {1, 0, 0}, {0, 0, -1}, {0, 0, 1}, {0, 1, 0}};

   private static boolean sameBlock(int[] left, int[] right) {
      return left[0] == right[0] && left[1] == right[1] && left[2] == right[2];
   }

   /** The air block worth filling: under the feet, or one step along the way you are moving. */
   private int[] target(Object player, Object world) throws Exception {
      Object feet = Bridge.call(player, "method_24515");
      int x = ((Number)Bridge.call(feet, "method_10263")).intValue();
      int y = ((Number)Bridge.call(feet, "method_10264")).intValue();
      int z = ((Number)Bridge.call(feet, "method_10260")).intValue();
      if (this.mode.get() == ScaffoldMode.TOWER) {
         return this.open(world, x, y - 1, z) ? new int[]{x, y - 1, z} : null;
      }
      if (this.mode.get() == ScaffoldMode.TELLY && this.onGround(player)) {
         return null;
      }
      if (this.open(world, x, y - 1, z)) {
         return new int[]{x, y - 1, z};
      }
      Object motion = Bridge.call(player, "method_18798");
      double moveX = ((Number)Bridge.field(motion, "field_1352")).doubleValue();
      double moveZ = ((Number)Bridge.field(motion, "field_1350")).doubleValue();
      if (Math.hypot(moveX, moveZ) < 0.04) {
         return null;
      }
      int stepX = Math.abs(moveX) > Math.abs(moveZ) ? (int)Math.signum(moveX) : 0;
      int stepZ = stepX == 0 ? (int)Math.signum(moveZ) : 0;
      for (int step = 1; step <= Math.max(1, this.extend.getInt()); step++) {
         int tx = x + stepX * step;
         int tz = z + stepZ * step;
         if (this.open(world, tx, y - 1, tz)) {
            return new int[]{tx, y - 1, tz};
         }
      }
      return null;
   }

   private boolean open(Object world, int x, int y, int z) throws Exception {
      return Game.replaceable(Game.state(world, x, y, z));
   }

   private boolean onGround(Object player) {
      try {
         return (Boolean)Bridge.call(player, "method_24828");
      } catch (LinkageError | Exception unknown) {
         return true;
      }
   }

   /** A point on the face that is not its centre, so two clicks never land the same way. */
   private double[] hitPoint(int[] neighbour, int[] face) {
      double spread = this.spot.getDouble();
      double jitterA = (Math.random() * 2.0 - 1.0) * spread;
      double jitterB = (Math.random() * 2.0 - 1.0) * spread;
      double x = neighbour[0] + 0.5 + face[0] * 0.5 + (face[0] == 0 ? jitterA : 0.0);
      double y = neighbour[1] + 0.5 + face[1] * 0.5 + (face[1] == 0 ? jitterB : 0.0);
      double z = neighbour[2] + 0.5 + face[2] * 0.5 + (face[2] == 0 ? (face[0] == 0 ? jitterB : jitterA) : 0.0);
      return new double[]{x, y, z};
   }

   /** Yaw and pitch that look at the hit point, with the pitch kept inside the configured band. */
   private double[] look(Object player, double[] hit) throws Exception {
      Object eye = Bridge.call(player, "method_33571");
      double eyeX = ((Number)Bridge.field(eye, "field_1352")).doubleValue();
      double eyeY = ((Number)Bridge.field(eye, "field_1351")).doubleValue();
      double eyeZ = ((Number)Bridge.field(eye, "field_1350")).doubleValue();
      double dx = hit[0] - eyeX;
      double dy = hit[1] - eyeY;
      double dz = hit[2] - eyeZ;
      double yaw = Math.toDegrees(Math.atan2(dz, dx)) - 90.0 + this.yawOffset.getDouble();
      double pitch = -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
      double low = Math.min(this.minPitch.getDouble(), this.maxPitch.getDouble());
      double high = Math.max(this.minPitch.getDouble(), this.maxPitch.getDouble());
      pitch = Math.max(low, Math.min(high, pitch));
      return new double[]{yaw, pitch};
   }

   /** One eased, grid-snapped tick of turn toward the face. The placement waits for the next tick. */
   private void aim(Object player, double[] look) throws Exception {
      double yaw = ((Number)Bridge.call(player, "method_36454")).doubleValue();
      double pitch = ((Number)Bridge.call(player, "method_36455")).doubleValue();
      double wantedYaw = look[0];
      double wantedPitch = look[1];
      double radians = Math.toRadians(wantedPitch);
      double plane = Math.cos(radians);
      double dx = -Math.sin(Math.toRadians(wantedYaw)) * plane;
      double dy = -Math.sin(radians);
      double dz = Math.cos(Math.toRadians(wantedYaw)) * plane;
      AimStep.Rotation turn = AimStep.toward(yaw, pitch, dx, dy, dz, this.yawStep.getDouble(), this.pitchStep.getDouble());
      Game.invoke(player, "method_36456", new String[]{"float"}, turn.yaw());
      Game.invoke(player, "method_36457", new String[]{"float"}, turn.pitch());
      this.wantedYaw = wantedYaw;
      this.wantedPitch = wantedPitch;
   }

   private void place(Object client, Object player, int slot, int[] neighbour, int[] face, double[] hit) throws Exception {
      Object inventory = Bridge.call(player, "method_31548");
      int held = ((Number)Bridge.call(inventory, "method_67532")).intValue();
      if (this.previousSlot < 0) {
         this.previousSlot = held;
      }
      if (held != slot) {
         Game.invoke(inventory, "method_61496", new String[]{"int"}, slot);
      }
      Object direction = Bridge.field(Game.type("net.minecraft.class_2350"), name(face));
      Object point = Game.type("net.minecraft.class_243")
         .getConstructor(double.class, double.class, double.class)
         .newInstance(hit[0], hit[1], hit[2]);
      Object result = Game.type("net.minecraft.class_3965")
         .getConstructor(Game.type("net.minecraft.class_243"), Game.type("net.minecraft.class_2350"), Game.type("net.minecraft.class_2338"), boolean.class)
         .newInstance(point, direction, Game.pos(neighbour[0], neighbour[1], neighbour[2]), false);
      Object hand = Bridge.field(Game.type("net.minecraft.class_1268"), "field_5808");
      Game.invoke(
         Bridge.field(client, "field_1761"),
         "method_2896",
         new String[]{"net.minecraft.class_746", "net.minecraft.class_1268", "net.minecraft.class_3965"},
         player,
         hand,
         result
      );
      if (this.swing.get()) {
         Game.invoke(player, "method_6104", new String[]{"net.minecraft.class_1268"}, hand);
      }
   }

   private static String name(int[] face) {
      if (face[1] < 0) {
         return "field_11033";
      }
      if (face[1] > 0) {
         return "field_11036";
      }
      if (face[2] < 0) {
         return "field_11043";
      }
      if (face[2] > 0) {
         return "field_11035";
      }
      return face[0] > 0 ? "field_11034" : "field_11039";
   }

   /** A hotbar slot holding a block, preferring the one already in hand. */
   private int blockSlot(Object player) throws Exception {
      Object inventory = Bridge.call(player, "method_31548");
      int held = ((Number)Bridge.call(inventory, "method_67532")).intValue();
      if (this.usable(inventory, held)) {
         return held;
      }
      for (int slot = 0; slot < 9; slot++) {
         if (this.usable(inventory, slot)) {
            return slot;
         }
      }
      return -1;
   }

   private boolean usable(Object inventory, int slot) throws Exception {
      Object stack = Game.invoke(inventory, "method_5438", new String[]{"int"}, slot);
      if (stack == null || (Boolean)Bridge.call(stack, "method_7960")) {
         return false;
      }
      Object item = Bridge.call(stack, "method_7909");
      if (!Game.type("net.minecraft.class_1747").isInstance(item)) {
         return false;
      }
      if (!this.safeOnly.get()) {
         return true;
      }
      String id = String.valueOf(Game.id(Bridge.field(Game.type("net.minecraft.class_7923"), "field_41178"), item));
      for (String bad : FALLING) {
         if (id.contains(bad)) {
            return false;
         }
      }
      return true;
   }
}
