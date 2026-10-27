package rex;

import dev.krypton.core.event.ClientTickEvent;
import dev.krypton.core.event.EventHandler;
import dev.krypton.core.event.PacketReceiveEvent;
import dev.krypton.core.module.Category;
import dev.krypton.core.module.Module;
import dev.krypton.core.setting.BooleanSetting;
import dev.krypton.core.setting.EnumSetting;
import dev.krypton.core.setting.NumberSetting;
import dev.krypton.core.setting.Setting;
import dev.krypton.core.setting.StringSetting;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Eight races and a baseline, aimed at the layers that still have a seam: plugin menus that handle a
 * click more than once, plugin menus that never check which window a click came from, economy
 * commands that read an inventory before they empty it, and the save that runs when a connection
 * dies. It works on the one stack in your hand, counts it before and after every run, and leaves the
 * verdict to that count. Duper HUD shows all of it while it runs.
 */
public final class Duper extends Module {
   public static Duper instance;

   public final EnumSetting<DuperPattern> pattern = new EnumSetting<>("Pattern", DuperPattern.AUTO, DuperPattern.class);
   public final EnumSetting<DuperTarget> target = new EnumSetting<>("Target", DuperTarget.HELD, DuperTarget.class);
   public final NumberSetting slot = new NumberSetting("Slot", 0.0, 89.0, 0.0, 1.0)
      .describe("Menu slot index, used when Target is SLOT.");
   public final NumberSetting clicks = new NumberSetting("Clicks", 1.0, 32.0, 6.0, 1.0)
      .describe("Clicks or commands per burst, all inside one client tick.");
   public final NumberSetting bursts = new NumberSetting("Bursts", 1.0, 64.0, 3.0, 1.0);
   public final NumberSetting gap = new NumberSetting("Burst Gap", 0.0, 20.0, 2.0, 1.0)
      .describe("Ticks between bursts. Zero sends every burst in the same tick.");
   public final NumberSetting runs = new NumberSetting("Runs", 1.0, 20.0, 1.0, 1.0)
      .describe("Whole cycles, each counted and reported on its own. AUTO uses one run per pattern.");
   public final NumberSetting settle = new NumberSetting("Settle", 2.0, 100.0, 20.0, 1.0)
      .describe("Ticks to wait for the server before counting.");
   public final NumberSetting button = new NumberSetting("Button", 0.0, 8.0, 1.0, 1.0)
      .describe("Hotbar slot for the swap race; otherwise the throw button when Keep Stack is off.");
   public final BooleanSetting keepStack = new BooleanSetting("Keep Stack", true)
      .describe("Throws one item per click instead of the whole stack, so a failed probe costs one item.");
   public final StringSetting command = new StringSetting("Command", "/sell all")
      .describe("Sent by COMMAND_RACE. Also settable in chat: .one dupe cmd <text>");
   public final NumberSetting patience = new NumberSetting("Arm Timeout", 0.0, 60.0, 15.0, 5.0)
      .describe("Seconds to wait for the item to be there. Zero refuses instead of waiting.");
   public final NumberSetting jitter = new NumberSetting("Jitter", 0.0, 10.0, 0.0, 1.0)
      .describe("Randomises the gap between bursts by up to this many ticks, so a fixed-interval rate limit sees no pattern.");
   public final NumberSetting radius = new NumberSetting("Ground Radius", 0.0, 32.0, 10.0, 1.0)
      .describe("Counts dropped items of the same kind within this distance, so a drop pattern can be measured at all.");
   public final EnumSetting<DuperOnGain> onGain = new EnumSetting<>("On Gain", DuperOnGain.REPEAT, DuperOnGain.class)
      .describe("STOP ends the sweep, REPEAT keeps running the pattern that gained until a run does not, SWEEP_ON carries on through the rest.");
   public final BooleanSetting report = new BooleanSetting("Report", true)
      .describe("Chat lines as well as the HUD.");

   private static final DuperPattern[] SWEEP = {
      DuperPattern.DROP_PACKET,
      DuperPattern.DROP_CYCLE,
      DuperPattern.DROP_DESYNC,
      DuperPattern.GUI_RACE,
      DuperPattern.SHIFT_SPAM,
      DuperPattern.DOUBLE_PICKUP,
      DuperPattern.SWAP_RACE,
      DuperPattern.STALE_WINDOW,
      DuperPattern.DROP_SPAM,
      DuperPattern.COMMAND_RACE,
      DuperPattern.HELD_THROW
   };
   public static final int WAITING = 0;
   public static final int RUNNING = 1;
   public static final int SETTLING = 2;

   private final Deque<String> log = new ArrayDeque<>();
   private final Map<DuperPattern, Integer> results = new LinkedHashMap<>();

   private int phase = WAITING;
   private int remaining;
   private int waiting;
   private int run;
   private int sent;
   private int before;
   private int live;
   private int confirmed;
   private int sweep;
   private boolean resynced;
   private boolean serverSpoke;
   private int resolved = -1;
   private int syncId;
   private int stale = -1;
   private Object item;
   private Object menu;
   private String itemName = "nothing";
   private String blocked = "";

   public Duper() {
      super("Duper", "Plugin menu, command and logout races on the one stack in your hand.", -1, Category.MISC);
      this.addSettings(
         new Setting[]{
            this.pattern, this.target, this.slot, this.clicks, this.bursts, this.gap, this.runs, this.settle,
            this.button, this.keepStack, this.command, this.patience, this.jitter, this.radius, this.onGain, this.report
         }
      );
      instance = this;
   }

   @Override
   public void onEnable() {
      super.onEnable();
      this.phase = WAITING;
      this.run = 0;
      this.sent = 0;
      this.waiting = 0;
      this.remaining = 0;
      this.sweep = 0;
      this.resolved = -1;
      this.live = 0;
      this.confirmed = 0;
      this.before = 0;
      this.resynced = false;
      this.serverSpoke = false;
      this.item = null;
      this.menu = null;
      this.itemName = "nothing";
      this.blocked = "";
      this.log.clear();
      this.results.clear();
      DuperPattern chosen = (DuperPattern)this.pattern.get();
      this.note(chosen + ": " + chosen.what);
      if (this.report.get()) {
         Game.message("Duper " + chosen + ": " + chosen.what);
         Game.message("  " + chosen.how);
         Game.message(
            "  " + this.plan() + ", " + this.bursts.getInt() + " x " + this.clicks.getInt() + " per run, gap " + this.gap.getInt()
               + ", settle " + this.settle.getInt() + ", target "
               + (this.target.get() == DuperTarget.HELD ? "held stack" : "slot " + this.slot.getInt()) + ". Toggle off to stop."
         );
      }
   }

   @Override
   public void onDisable() {
      super.onDisable();
      if (this.sent > 0 && this.report.get()) {
         Game.message("Duper stopped: " + this.sent + " sent over " + this.run + " run(s).");
      }
      this.note("stopped after " + this.run + " run(s)");
      this.phase = WAITING;
      this.remaining = 0;
      this.menu = null;
      this.item = null;
   }

   private String plan() {
      return this.pattern.get() == DuperPattern.AUTO ? SWEEP.length + " runs, one per pattern" : this.runs.getInt() + " run(s)";
   }

   private void note(String line) {
      this.log.addLast(line);
      while (this.log.size() > 8) {
         this.log.removeFirst();
      }
   }

   /** The pattern this run uses: the setting, or the next one in the sweep. */
   public DuperPattern current() {
      DuperPattern chosen = (DuperPattern)this.pattern.get();
      return chosen == DuperPattern.AUTO ? SWEEP[Math.min(this.sweep, SWEEP.length - 1)] : chosen;
   }

   private int plannedRuns() {
      return this.pattern.get() == DuperPattern.AUTO ? SWEEP.length : this.runs.getInt();
   }

   /** Patterns that aim at a window other than the open one, so a menu change is not a reason to stop. */
   private boolean tolerant(DuperPattern pattern) {
      return pattern == DuperPattern.STALE_WINDOW || pattern == DuperPattern.DROP_SPAM;
   }

   private static final String[] INVENTORY_PACKETS = {
      "net.minecraft.class_2649", "net.minecraft.class_2653", "net.minecraft.class_9835", "net.minecraft.class_9834"
   };

   /**
    * The client predicts its own inventory, so a count taken straight after a click can show a copy
    * that only the client believes in. These are the packets in which the server states what you
    * actually have; the count taken on the tick after one of them lands is the server's own view.
    */
   @EventHandler
   public void onDuperPacket(PacketReceiveEvent event) {
      Object packet = event.packet;
      if (packet == null) {
         return;
      }
      for (String name : INVENTORY_PACKETS) {
         try {
            if (Game.type(name).isInstance(packet)) {
               this.resynced = true;
               this.serverSpoke = true;
               return;
            }
         } catch (LinkageError | Exception missing) {
         }
      }
   }

   @EventHandler
   public void onDuperTick(ClientTickEvent event) {
      try {
         switch (this.phase) {
            case WAITING:
               this.awaitConditions();
               break;
            case RUNNING:
               this.advance();
               break;
            case SETTLING:
               this.finish();
               break;
            default:
               break;
         }
         if (this.item != null && this.menu != null) {
            this.live = this.total();
            if (this.resynced) {
               this.resynced = false;
               this.confirmed = this.live;
            }
         }
      } catch (LinkageError | Exception failure) {
         Game.message("Duper: " + message(failure));
         this.note("stopped: " + message(failure));
         this.setEnabled(false);
      }
   }

   private void awaitConditions() throws Exception {
      if (this.pattern.get() == DuperPattern.AUTO && this.current() == DuperPattern.COMMAND_RACE && this.command.get().strip().isEmpty()) {
         this.note("skipped COMMAND_RACE, no command set");
         this.step();
         return;
      }
      String missing = this.resolve();
      if (missing != null) {
         this.waiting++;
         if (!missing.equals(this.blocked)) {
            this.blocked = missing;
            this.note("waiting: " + missing);
            if (this.report.get()) {
               Game.message("Duper waiting: " + missing);
            }
         }
         if (this.patience.getInt() == 0 || this.waiting > this.patience.getInt() * 20) {
            Game.message("Duper: gave up waiting (" + missing + ").");
            this.setEnabled(false);
         }
         return;
      }
      this.blocked = "";
      this.waiting = 0;
      this.run++;
      this.before = this.total();
      this.live = this.before;
      this.confirmed = this.before;
      this.serverSpoke = false;
      this.remaining = this.bursts.getInt();
      this.phase = RUNNING;
      this.note("run " + this.run + " " + this.current() + " on slot " + this.resolved + ", " + this.before + " carried");
      if (this.report.get()) {
         Game.message(
            "Duper run " + this.run + "/" + this.plannedRuns() + " " + this.current() + ": slot " + this.resolved + " of window "
               + this.window() + ", " + this.before + " carried."
         );
      }
   }

   /**
    * Finds the menu, the slot and the item for this run, remembering the last plugin window id so a
    * replaced one can still be clicked. Null when everything needed is there.
    */
   private String resolve() throws Exception {
      Object client = Bridge.client();
      Object player = Bridge.field(client, "field_1724");
      if (player == null || Bridge.field(client, "field_1687") == null) {
         return "join a world";
      }
      Object open = Bridge.field(player, "field_7512");
      Object own = Bridge.field(player, "field_7498");
      if (open == null) {
         return "no menu at all, which should not happen";
      }
      if (open != this.menu && this.syncId > 0) {
         this.stale = this.syncId;
      }
      this.menu = open;
      this.syncId = ((Number)Bridge.field(open, "field_7763")).intValue();
      if (this.syncId > 0) {
         this.stale = this.syncId;
      }
      DuperPattern pattern = this.current();
      if (pattern.container && open == own) {
         return "open the plugin menu you want to race";
      }
      if (pattern == DuperPattern.STALE_WINDOW && this.stale <= 0) {
         return "open a plugin menu once so there is a window id to go stale";
      }
      if (pattern == DuperPattern.COMMAND_RACE && this.command.get().strip().isEmpty()) {
         return "set a command: .one dupe cmd /sell all";
      }
      Object stack = this.find(player);
      if (stack == null) {
         return this.target.get() == DuperTarget.HELD ? "hold the stack you want copied" : "put a stack in slot " + this.slot.getInt();
      }
      this.item = Bridge.call(stack, "method_7909");
      this.itemName = String.valueOf(Bridge.call(Bridge.call(stack, "method_7964"), "getString"));
      return null;
   }

   /** Resolves the slot: the held stack wherever it sits in this menu, or the slot you named. */
   private Object find(Object player) throws Exception {
      int size = ((List<?>)Bridge.field(this.menu, "field_7761")).size();
      if (this.target.get() == DuperTarget.SLOT) {
         this.resolved = Math.min(this.slot.getInt(), size - 1);
         return this.filled(this.resolved);
      }
      Object inventory = Bridge.call(player, "method_31548");
      Object held = Bridge.call(inventory, "method_7391");
      if (held == null || (Boolean)Bridge.call(held, "method_7960")) {
         return null;
      }
      Object wanted = Bridge.call(held, "method_7909");
      int selected = ((Number)Bridge.call(inventory, "method_67532")).intValue();
      int hotbar = size - 9 + Math.max(0, Math.min(8, selected));
      Object stack = this.filled(hotbar);
      if (stack != null && Bridge.call(stack, "method_7909") == wanted) {
         this.resolved = hotbar;
         return stack;
      }
      for (int index = 0; index < size; index++) {
         stack = this.filled(index);
         if (stack != null && Bridge.call(stack, "method_7909") == wanted) {
            this.resolved = index;
            return stack;
         }
      }
      return null;
   }

   private Object filled(int slot) {
      try {
         Object stack = Bridge.call(Game.invoke(this.menu, "method_7611", new String[]{"int"}, slot), "method_7677");
         return stack != null && !(Boolean)Bridge.call(stack, "method_7960") ? stack : null;
      } catch (LinkageError | Exception unknown) {
         return null;
      }
   }

   /** Everything of that item you are carrying, plus the open container's own slots. */
   private int total() throws Exception {
      int count = 0;
      Object player = Bridge.field(Bridge.client(), "field_1724");
      if (player == null) {
         return this.live;
      }
      Object inventory = Bridge.call(player, "method_31548");
      for (int index = 0; index < 36; index++) {
         count += this.sizeOf(Game.invoke(inventory, "method_5438", new String[]{"int"}, index));
      }
      Object own = Bridge.field(player, "field_7498");
      if (this.menu != own) {
         int size = ((List<?>)Bridge.field(this.menu, "field_7761")).size();
         for (int index = 0; index < Math.max(0, size - 36); index++) {
            count += this.sizeOf(this.filled(index));
         }
      }
      return count + this.ground(player);
   }

   /**
    * What is lying on the ground nearby. A drop pattern takes the stack out of the inventory on
    * purpose, so without this every drop run would read as a loss and a real gain would be invisible.
    */
   private int ground(Object player) throws Exception {
      if (!this.current().drops || this.radius.getDouble() <= 0.0) {
         return 0;
      }
      Object world = Bridge.field(Bridge.client(), "field_1687");
      if (world == null) {
         return 0;
      }
      Class<?> items = Game.type("net.minecraft.class_1542");
      double reach = this.radius.getDouble();
      int count = 0;
      int seen = 0;
      for (Object entity : (Iterable<?>)Bridge.call(world, "method_18112")) {
         if (++seen > 2048) {
            break;
         }
         if (items.isInstance(entity) && EspRender.near(player, entity, reach)) {
            count += this.sizeOf(Bridge.call(entity, "method_6983"));
         }
      }
      return count;
   }

   private int sizeOf(Object stack) throws Exception {
      if (stack == null || (Boolean)Bridge.call(stack, "method_7960") || Bridge.call(stack, "method_7909") != this.item) {
         return 0;
      }
      return ((Number)Bridge.call(stack, "method_7947")).intValue();
   }

   private void advance() throws Exception {
      Object client = Bridge.client();
      Object player = Bridge.field(client, "field_1724");
      if (player == null) {
         this.setEnabled(false);
         return;
      }
      DuperPattern pattern = this.current();
      if (pattern.clicks && !this.tolerant(pattern) && Bridge.field(player, "field_7512") != this.menu) {
         this.note("menu changed, counting what there is");
         this.waiting = this.settle.getInt();
         this.phase = SETTLING;
         return;
      }
      if (this.waiting > 0) {
         this.waiting--;
         return;
      }
      while (this.remaining > 0) {
         this.burst(client, player);
         this.remaining--;
         if (this.gap.getInt() > 0 && this.remaining > 0) {
            this.waiting = this.pause();
            return;
         }
      }
      this.waiting = this.settle.getInt();
      this.phase = SETTLING;
   }

   /** The configured gap, moved by up to Jitter ticks either way but never below one. */
   private int pause() {
      int spread = this.jitter.getInt();
      int base = this.gap.getInt();
      return spread <= 0 ? base : Math.max(1, base - spread + (int)(Math.random() * (spread * 2 + 1)));
   }

   private void finish() throws Exception {
      if (this.waiting > 0) {
         this.waiting--;
         return;
      }
      int after = this.total();
      this.live = after;
      int judged = this.serverSpoke ? this.confirmed : after;
      int change = judged - this.before;
      this.results.put(this.current(), change);
      String source = this.serverSpoke ? "server" : "client only";
      this.note(this.current() + ": " + this.before + " -> " + judged + " (" + (change >= 0 ? "+" : "") + change + ", " + source + ")");
      if (this.report.get()) {
         Game.message(
            "Duper run " + this.run + " " + this.current() + ": " + this.before + " -> " + judged + " (" + (change >= 0 ? "+" : "") + change
               + ") after " + this.sent + " sent, counted from the " + source + "."
         );
      }
      if (change > 0) {
         this.gained();
         return;
      }
      this.step();
   }

   /** A run that gained: stop, keep hammering the same pattern, or carry on through the sweep. */
   private void gained() {
      DuperPattern winner = this.current();
      if (!this.serverSpoke) {
         Game.message("Duper: " + winner + " gained on the client's own count. Reopen the container to make the server state it.");
      }
      switch ((DuperOnGain)this.onGain.get()) {
         case REPEAT:
            Game.message("Duper: " + winner + " gained " + this.results.get(winner) + ". Running it again.");
            this.note("gain on " + winner + ", repeating");
            this.phase = WAITING;
            this.waiting = 0;
            return;
         case SWEEP_ON:
            this.note("gain on " + winner + ", carrying on");
            this.step();
            return;
         case STOP:
         default:
            Game.message("Duper: " + winner + " moved the count up. Stopping; check the container before you trust it.");
            this.note("gain on " + winner + ", stopped");
            this.setEnabled(false);
      }
   }

   /** Moves to the next pattern in a sweep, or the next run, or stops. */
   private void step() {
      this.sweep++;
      boolean done = this.pattern.get() == DuperPattern.AUTO ? this.sweep >= SWEEP.length : this.run >= this.runs.getInt();
      if (done) {
         if (this.pattern.get() == DuperPattern.AUTO) {
            Game.message("Duper: no pattern changed the count here. This server does not leave these seams open.");
            this.note("sweep finished, nothing moved");
         }
         this.setEnabled(false);
         return;
      }
      this.phase = WAITING;
      this.waiting = 0;
   }

   private int window() {
      return this.current() == DuperPattern.STALE_WINDOW ? this.stale : this.current() == DuperPattern.DROP_SPAM ? 0 : this.syncId;
   }

   private int throwButton() {
      return this.keepStack.get() ? 0 : 1;
   }

   private void burst(Object client, Object player) throws Exception {
      int slot = this.resolved;
      int count = this.clicks.getInt();
      switch (this.current()) {
         case GUI_RACE:
            for (int index = 0; index < count; index++) {
               this.click(client, player, this.syncId, slot, 0, "field_7790");
            }
            break;
         case SHIFT_SPAM:
            for (int index = 0; index < count; index++) {
               this.click(client, player, this.syncId, slot, 0, "field_7794");
            }
            break;
         case DOUBLE_PICKUP:
            for (int index = 0; index < count; index++) {
               this.click(client, player, this.syncId, slot, 0, "field_7790");
               this.click(client, player, this.syncId, slot, 0, "field_7793");
            }
            break;
         case SWAP_RACE:
            for (int index = 0; index < count; index++) {
               this.click(client, player, this.syncId, slot, Math.min(8, this.button.getInt()), "field_7791");
               this.click(client, player, this.syncId, slot, this.throwButton(), "field_7795");
            }
            break;
         case STALE_WINDOW:
            for (int index = 0; index < count; index++) {
               this.click(client, player, this.stale, slot, 0, "field_7790");
            }
            break;
         case DROP_SPAM:
            int own = 36 + ((Number)Bridge.call(Bridge.call(player, "method_31548"), "method_67532")).intValue();
            for (int index = 0; index < count; index++) {
               this.click(client, player, 0, own, this.throwButton(), "field_7795");
            }
            break;
         case DROP_PACKET:
            for (int index = 0; index < count; index++) {
               this.dropPacket(player, !this.keepStack.get());
            }
            break;
         case DROP_CYCLE:
            Object inventory = Bridge.call(player, "method_31548");
            int held = ((Number)Bridge.call(inventory, "method_67532")).intValue();
            for (int index = 0; index < count; index++) {
               int hotbar = index % 9;
               this.select(player, hotbar);
               this.dropPacket(player, !this.keepStack.get());
            }
            this.select(player, held);
            break;
         case DROP_DESYNC:
            for (int index = 0; index < count; index++) {
               this.click(client, player, this.syncId, slot, this.throwButton(), "field_7795");
               this.dropPacket(player, !this.keepStack.get());
            }
            break;
         case COMMAND_RACE:
            String line = this.command.get().strip();
            Object handler = Bridge.call(client, "method_1562");
            for (int index = 0; index < count; index++) {
               Game.invoke(handler, "method_45730", new String[]{"java.lang.String"}, line.startsWith("/") ? line.substring(1) : line);
               this.sent++;
            }
            break;
         case LOGOUT:
            for (int index = 0; index < count; index++) {
               this.click(client, player, this.syncId, slot, 1, "field_7795");
            }
            Game.message("Duper: dropped " + this.before + " and killing the socket. Reconnect and compare.");
            this.kill(client);
            this.remaining = 1;
            break;
         case HELD_THROW:
         default:
            for (int index = 0; index < count; index++) {
               this.click(client, player, this.syncId, slot, this.throwButton(), "field_7795");
            }
            break;
      }
   }

   /**
    * A player-action drop. This path carries no window and no revision, which is the whole point:
    * there is nothing for the server to compare and resync, and it is taken whatever menu the server
    * believes is open.
    */
   private void dropPacket(Object player, boolean whole) throws Exception {
      Class<?> action = Game.type("net.minecraft.class_2846$class_2847");
      Class<?> pos = Game.type("net.minecraft.class_2338");
      Class<?> facing = Game.type("net.minecraft.class_2350");
      Object packet = Game.type("net.minecraft.class_2846")
         .getConstructor(action, pos, facing)
         .newInstance(
            Bridge.field(action, whole ? "field_12970" : "field_12975"),
            Bridge.field(pos, "field_10980"),
            Bridge.field(facing, "field_11033")
         );
      this.send(player, packet);
      this.sent++;
   }

   /** Tells the server which hotbar slot is held without touching what the client thinks. */
   private void select(Object player, int slot) throws Exception {
      this.send(player, Game.type("net.minecraft.class_2868").getConstructor(int.class).newInstance(slot));
      this.sent++;
   }

   private void send(Object player, Object packet) throws Exception {
      Game.invoke(Bridge.field(player, "field_3944"), "method_52787", new String[]{"net.minecraft.class_2596"}, packet);
   }

   /** No logout packet: the channel goes down under the server, so the drop races the save. */
   private void kill(Object client) {
      try {
         Object connection = Bridge.call(Bridge.call(client, "method_1562"), "method_48296");
         Game.invoke(Bridge.field(connection, "field_11651"), "close", new String[0]);
      } catch (LinkageError | Exception hardFailed) {
         try {
            Bridge.call(Bridge.call(client, "method_1562"), "method_47658");
         } catch (LinkageError | Exception softFailed) {
            Game.message("Duper: could not drop the connection: " + message(hardFailed));
         }
      }
   }

   private void click(Object client, Object player, int window, int slot, int button, String action) throws Exception {
      Object manager = Bridge.field(client, "field_1761");
      Object type = Bridge.field(Game.type("net.minecraft.class_1713"), action);
      Game.invoke(
         manager,
         "method_2906",
         new String[]{"int", "int", "int", "net.minecraft.class_1713", "net.minecraft.class_1657"},
         window,
         slot,
         button,
         type,
         player
      );
      this.sent++;
   }

   private static String message(Throwable failure) {
      return failure.getMessage() == null ? failure.toString() : failure.getMessage();
   }

   /** Chat side of the module: .one dupe [cmd <text>] */
   public static boolean chat(String argument, String rest) {
      Duper duper = instance;
      if (duper == null) {
         Game.message("Duper unavailable");
         return true;
      }
      if (argument.equalsIgnoreCase("cmd") || argument.equalsIgnoreCase("command")) {
         if (!rest.strip().isEmpty()) {
            duper.command.set(rest.strip());
         }
         Game.message("Duper command: " + duper.command.get());
         return true;
      }
      Game.message(status());
      for (DuperPattern pattern : DuperPattern.values()) {
         Game.message("  " + pattern + ": " + pattern.what);
      }
      return true;
   }

   public static String status() {
      Duper duper = instance;
      if (duper == null) {
         return "Duper unavailable";
      }
      if (!duper.enabled) {
         return "Duper idle: " + duper.pattern.get() + ", " + duper.clicks.getInt() + " x " + duper.bursts.getInt() + ", target "
            + (duper.target.get() == DuperTarget.HELD ? "held stack" : "slot " + duper.slot.getInt());
      }
      return "Duper " + duper.current() + ": " + duper.phaseName() + ", " + duper.sent + " sent";
   }

   public String phaseName() {
      return this.phase == WAITING
         ? "waiting" + (this.blocked.isEmpty() ? "" : " (" + this.blocked + ")")
         : this.phase == RUNNING ? "running" : "settling";
   }

   public int phase() {
      return this.phase;
   }

   public int run() {
      return this.run;
   }

   public int planned() {
      return this.plannedRuns();
   }

   public int burstsLeft() {
      return this.remaining;
   }

   public int burstsPlanned() {
      return this.bursts.getInt();
   }

   public int sent() {
      return this.sent;
   }

   public int before() {
      return this.before;
   }

   public int live() {
      return this.live;
   }

   public int confirmed() {
      return this.confirmed;
   }

   public boolean serverSpoke() {
      return this.serverSpoke;
   }

   public int slotUsed() {
      return this.resolved;
   }

   public int windowUsed() {
      return this.window();
   }

   public String itemName() {
      return this.itemName;
   }

   public String blocked() {
      return this.blocked;
   }

   public int ticksLeft() {
      return this.waiting;
   }

   public List<String> lines() {
      return new ArrayList<>(this.log);
   }

   public Map<DuperPattern, Integer> results() {
      return new LinkedHashMap<>(this.results);
   }
}
