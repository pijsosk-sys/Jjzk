package rex;

import dev.krypton.core.event.ClientTickEvent;
import dev.krypton.core.event.EventHandler;
import dev.krypton.core.module.Category;
import dev.krypton.core.module.Module;
import dev.krypton.core.setting.BooleanSetting;
import dev.krypton.core.setting.EnumSetting;
import dev.krypton.core.setting.NumberSetting;
import dev.krypton.core.setting.Setting;
import dev.krypton.core.setting.StringSetting;
import java.util.List;

/**
 * Four races and a baseline, aimed at the layers that still have a seam: plugin menus that handle a
 * click more than once, plugin menus that do not check which window the click came from, economy
 * commands that read an inventory before they empty it, and the save that runs when a connection
 * dies. It works on whatever is in your hand, reports a count before and after each run, and leaves
 * the verdict to that count.
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
      .describe("Throw 0 drops one item, 1 drops the stack.");
   public final StringSetting command = new StringSetting("Command", "/sell all")
      .describe("Sent by COMMAND_RACE. Also settable in chat: .one dupe cmd <text>");
   public final NumberSetting patience = new NumberSetting("Arm Timeout", 0.0, 60.0, 15.0, 5.0)
      .describe("Seconds to wait for the item to be there. Zero refuses instead of waiting.");
   public final BooleanSetting stopOnGain = new BooleanSetting("Stop On Gain", true)
      .describe("Ends the remaining runs the moment a run counts more than it started with.");
   public final BooleanSetting report = new BooleanSetting("Report", true);

   private static final DuperPattern[] SWEEP = {
      DuperPattern.GUI_RACE, DuperPattern.STALE_WINDOW, DuperPattern.COMMAND_RACE, DuperPattern.HELD_THROW
   };
   private static final int WAITING = 0;
   private static final int RUNNING = 1;
   private static final int SETTLING = 2;

   private int phase = WAITING;
   private int remaining;
   private int waiting;
   private int run;
   private int sent;
   private int before;
   private int sweep;
   private int resolved = -1;
   private int syncId;
   private int stale = -1;
   private Object item;
   private Object menu;
   private String blocked = "";

   public Duper() {
      super("Duper", "Plugin menu, command and logout races on the item in your hand.", -1, Category.MISC);
      this.addSettings(
         new Setting[]{
            this.pattern, this.target, this.slot, this.clicks, this.bursts, this.gap, this.runs,
            this.settle, this.button, this.command, this.patience, this.stopOnGain, this.report
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
      this.item = null;
      this.menu = null;
      this.blocked = "";
      DuperPattern chosen = (DuperPattern)this.pattern.get();
      Game.message("Duper " + chosen + ": " + chosen.what);
      Game.message("  " + chosen.how);
      Game.message(
         "  " + this.plan() + ", " + this.bursts.getInt() + " x " + this.clicks.getInt() + " per run, gap " + this.gap.getInt()
            + ", settle " + this.settle.getInt() + ", target " + (this.target.get() == DuperTarget.HELD ? "held item" : "slot " + this.slot.getInt())
            + ". Toggle off to stop."
      );
   }

   private String plan() {
      return this.pattern.get() == DuperPattern.AUTO ? SWEEP.length + " runs, one per pattern" : this.runs.getInt() + " run(s)";
   }

   @Override
   public void onDisable() {
      super.onDisable();
      if (this.sent > 0 && this.report.get()) {
         Game.message("Duper stopped: " + this.sent + " clicks over " + this.run + " run(s).");
      }
      this.phase = WAITING;
      this.remaining = 0;
      this.sent = 0;
      this.menu = null;
      this.item = null;
   }

   /** The pattern this run uses: the setting, or the next one in the sweep. */
   private DuperPattern current() {
      DuperPattern chosen = (DuperPattern)this.pattern.get();
      return chosen == DuperPattern.AUTO ? SWEEP[Math.min(this.sweep, SWEEP.length - 1)] : chosen;
   }

   private int plannedRuns() {
      return this.pattern.get() == DuperPattern.AUTO ? SWEEP.length : this.runs.getInt();
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
      } catch (LinkageError | Exception failure) {
         Game.message("Duper: " + message(failure));
         this.setEnabled(false);
      }
   }

   private void awaitConditions() throws Exception {
      if (this.pattern.get() == DuperPattern.AUTO && this.current() == DuperPattern.COMMAND_RACE && this.command.get().strip().isEmpty()) {
         Game.message("Duper: skipping COMMAND_RACE, no command set (.one dupe cmd <text>).");
         this.step();
         return;
      }
      String missing = this.resolve();
      if (missing != null) {
         this.waiting++;
         if (!missing.equals(this.blocked)) {
            this.blocked = missing;
            Game.message("Duper waiting: " + missing);
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
      this.remaining = this.bursts.getInt();
      this.phase = RUNNING;
      if (this.report.get()) {
         Game.message(
            "Duper run " + this.run + "/" + this.plannedRuns() + " " + this.current() + ": slot " + this.resolved + " of menu " + this.syncId
               + ", " + this.before + " carried."
         );
      }
   }

   /**
    * Finds the menu, the slot and the item for this run, remembering the last plugin menu id so a
    * replaced window can still be clicked. Null when everything needed is there.
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
      if (pattern.clicks && open == own && pattern != DuperPattern.HELD_THROW) {
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
      return null;
   }

   /** Resolves the slot: the held item wherever it sits in this menu, or the slot you named. */
   private Object find(Object player) throws Exception {
      int size = ((List<?>)Bridge.field(this.menu, "field_7761")).size();
      if (this.target.get() == DuperTarget.SLOT) {
         this.resolved = Math.min(this.slot.getInt(), size - 1);
         return this.filled(this.resolved);
      }
      Object held = Bridge.call(Bridge.call(player, "method_31548"), "method_7391");
      if (held == null || (Boolean)Bridge.call(held, "method_7960")) {
         return null;
      }
      Object wanted = Bridge.call(held, "method_7909");
      int selected = ((Number)Bridge.call(Bridge.call(player, "method_31548"), "method_67532")).intValue();
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
      Object inventory = Bridge.call(player, "method_31548");
      for (int index = 0; index < 36; index++) {
         Object stack = Game.invoke(inventory, "method_5438", new String[]{"int"}, index);
         count += this.sizeOf(stack);
      }
      Object own = Bridge.field(player, "field_7498");
      if (this.menu != own) {
         int size = ((List<?>)Bridge.field(this.menu, "field_7761")).size();
         for (int index = 0; index < Math.max(0, size - 36); index++) {
            count += this.sizeOf(this.filled(index));
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
      if (this.current().clicks && !this.current().stale && Bridge.field(player, "field_7512") != this.menu) {
         Game.message("Duper: the menu changed under it, counting what there is.");
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
            this.waiting = this.gap.getInt();
            return;
         }
      }
      this.waiting = this.settle.getInt();
      this.phase = SETTLING;
   }

   private void finish() throws Exception {
      if (this.waiting > 0) {
         this.waiting--;
         return;
      }
      int after = this.total();
      int change = after - this.before;
      if (this.report.get()) {
         Game.message(
            "Duper run " + this.run + " " + this.current() + ": " + this.before + " -> " + after + " (" + (change >= 0 ? "+" : "") + change
               + ") after " + this.sent + " clicks."
         );
      }
      if (change > 0 && this.stopOnGain.get()) {
         Game.message("Duper: " + this.current() + " moved the count up. Stopping; check the container before you trust it.");
         this.setEnabled(false);
         return;
      }
      this.step();
   }

   /** Moves to the next pattern in a sweep, or the next run, or stops. */
   private void step() {
      this.sweep++;
      boolean done = this.pattern.get() == DuperPattern.AUTO ? this.sweep >= SWEEP.length : this.run >= this.runs.getInt();
      if (done) {
         if (this.pattern.get() == DuperPattern.AUTO) {
            Game.message("Duper: no pattern changed the count here. This server does not leave these seams open.");
         }
         this.setEnabled(false);
         return;
      }
      this.phase = WAITING;
      this.waiting = 0;
   }

   private void burst(Object client, Object player) throws Exception {
      int slot = this.resolved;
      int count = this.clicks.getInt();
      switch (this.current()) {
         case HELD_THROW:
            for (int index = 0; index < count; index++) {
               this.click(client, player, this.syncId, slot, this.button.getInt(), "field_7795");
            }
            break;
         case GUI_RACE:
            for (int index = 0; index < count; index++) {
               this.click(client, player, this.syncId, slot, 0, "field_7790");
            }
            break;
         case STALE_WINDOW:
            for (int index = 0; index < count; index++) {
               this.click(client, player, this.stale, slot, 0, "field_7790");
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
         default:
            break;
      }
   }

   /** No logout packet: the channel goes down under the server, so the drop races the save. */
   private void kill(Object client) {
      try {
         Object handler = Bridge.call(client, "method_1562");
         Object connection = Bridge.call(handler, "method_48296");
         Object channel = Bridge.field(connection, "field_11651");
         Game.invoke(channel, "close", new String[0]);
      } catch (LinkageError | Exception hardFailed) {
         try {
            Object handler = Bridge.call(client, "method_1562");
            Bridge.call(handler, "method_47658");
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
         if (rest.strip().isEmpty()) {
            Game.message("Duper command: " + duper.command.get());
         } else {
            duper.command.set(rest.strip());
            Game.message("Duper command: " + duper.command.get());
         }
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
            + (duper.target.get() == DuperTarget.HELD ? "held item" : "slot " + duper.slot.getInt());
      }
      String phase = duper.phase == WAITING
         ? "waiting" + (duper.blocked.isEmpty() ? "" : " (" + duper.blocked + ")")
         : duper.phase == RUNNING ? "run " + duper.run + ", " + duper.remaining + " bursts left" : "settling";
      return "Duper " + duper.current() + ": " + phase + ", " + duper.sent + " sent";
   }
}
