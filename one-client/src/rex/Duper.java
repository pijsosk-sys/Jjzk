package rex;

import dev.krypton.core.event.ClientTickEvent;
import dev.krypton.core.event.EventHandler;
import dev.krypton.core.module.Category;
import dev.krypton.core.module.Module;
import dev.krypton.core.setting.BooleanSetting;
import dev.krypton.core.setting.EnumSetting;
import dev.krypton.core.setting.NumberSetting;
import dev.krypton.core.setting.Setting;
import java.util.List;

/**
 * Sends container clicks faster than the server can answer them, in the orders that have historically
 * desynchronised an open menu. Toggling it on says what it is about to do and what it is waiting for,
 * waits for the menu and the stack, runs a bounded number of bursts, then counts that item across the
 * whole menu again and reports the difference. Whether a server accepts any of it is what the count
 * is for.
 */
public final class Duper extends Module {
   public static Duper instance;

   public final EnumSetting<DuperPattern> pattern = new EnumSetting<>("Pattern", DuperPattern.THROW_RACE, DuperPattern.class);
   public final NumberSetting clicks = new NumberSetting("Clicks", 1.0, 16.0, 4.0, 1.0)
      .describe("Clicks per burst, all inside one client tick.");
   public final NumberSetting bursts = new NumberSetting("Bursts", 1.0, 64.0, 4.0, 1.0);
   public final NumberSetting gap = new NumberSetting("Burst Gap", 0.0, 20.0, 1.0, 1.0)
      .describe("Ticks between bursts. Zero sends every burst in the same tick.");
   public final NumberSetting runs = new NumberSetting("Runs", 1.0, 20.0, 1.0, 1.0)
      .describe("Whole cycles, each with its own count and report.");
   public final NumberSetting settle = new NumberSetting("Settle", 2.0, 60.0, 10.0, 1.0)
      .describe("Ticks to wait for the server before counting.");
   public final NumberSetting slot = new NumberSetting("Slot", -1.0, 89.0, -1.0, 1.0)
      .describe("Menu slot index, or -1 for the held hotbar slot in whatever menu is open.");
   public final NumberSetting button = new NumberSetting("Button", 0.0, 8.0, 1.0, 1.0)
      .describe("Click button: throw 0 drops one item and 1 drops the stack; swap uses it as the hotbar slot.");
   public final NumberSetting patience = new NumberSetting("Arm Timeout", 0.0, 60.0, 15.0, 5.0)
      .describe("Seconds to wait for a container and a full slot. Zero refuses instead of waiting.");
   public final BooleanSetting container = new BooleanSetting("Container Only", true)
      .describe("Refuses to run against the bare player inventory.");
   public final BooleanSetting stopOnGain = new BooleanSetting("Stop On Gain", true)
      .describe("Ends the remaining runs as soon as a run counts more of the item than it started with.");
   public final BooleanSetting report = new BooleanSetting("Report", true);

   private static final int WAITING = 0;
   private static final int RUNNING = 1;
   private static final int SETTLING = 2;

   private int phase = WAITING;
   private int remaining;
   private int waiting;
   private int run;
   private int sent;
   private int before;
   private int resolved = -1;
   private Object item;
   private Object menu;
   private String blocked = "";

   public Duper() {
      super("Duper", "Bounded container click races. It tells you what it needs, then reports what changed.", -1, Category.MISC);
      this.addSettings(
         new Setting[]{
            this.pattern, this.clicks, this.bursts, this.gap, this.runs, this.settle,
            this.slot, this.button, this.patience, this.container, this.stopOnGain, this.report
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
      this.resolved = -1;
      this.item = null;
      this.menu = null;
      this.blocked = "";
      DuperPattern chosen = (DuperPattern)this.pattern.get();
      Game.message("Duper " + chosen + ": " + String.format(chosen.what, this.clicks.getInt()));
      Game.message("  " + chosen.how);
      Game.message(
         "  " + this.runs.getInt() + " run(s) of " + this.bursts.getInt() + " x " + this.clicks.getInt() + " clicks, gap "
            + this.gap.getInt() + ", settle " + this.settle.getInt() + ", slot " + (this.slot.getInt() < 0 ? "held" : String.valueOf(this.slot.getInt()))
            + ". Toggle it off to stop."
      );
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

   /** Says once what it is missing, then starts the moment the menu and the stack are both there. */
   private void awaitConditions() throws Exception {
      String missing = this.missing();
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
         Game.message("Duper run " + this.run + "/" + this.runs.getInt() + ": slot " + this.resolved + ", " + this.before + " held in this menu.");
      }
   }

   /** Null when it can run; otherwise the one thing it is waiting for, in words. */
   private String missing() throws Exception {
      Object client = Bridge.client();
      Object player = Bridge.field(client, "field_1724");
      if (player == null || Bridge.field(client, "field_1687") == null) {
         return "join a world";
      }
      Object open = Bridge.field(player, "field_7512");
      if (open == null) {
         return "open a container";
      }
      if (this.container.get() && open == Bridge.field(player, "field_7498")) {
         return "open a container, or clear Container Only";
      }
      this.menu = open;
      this.resolved = this.target(open, player);
      Object stack = this.stack(this.resolved);
      if (stack == null || (Boolean)Bridge.call(stack, "method_7960")) {
         return "put a stack in slot " + this.resolved;
      }
      this.item = Bridge.call(stack, "method_7909");
      return null;
   }

   private void advance() throws Exception {
      Object client = Bridge.client();
      Object player = Bridge.field(client, "field_1724");
      if (player == null || Bridge.field(player, "field_7512") != this.menu) {
         Game.message("Duper: the menu changed, stopping.");
         this.setEnabled(false);
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

   /** Counts the same item across the whole menu again and says what moved. */
   private void finish() throws Exception {
      if (this.waiting > 0) {
         this.waiting--;
         return;
      }
      int after = this.total();
      int change = after - this.before;
      if (this.report.get()) {
         Game.message(
            "Duper run " + this.run + ": " + this.before + " -> " + after + " (" + (change >= 0 ? "+" : "") + change + ") after " + this.sent + " clicks."
         );
      }
      if (change > 0 && this.stopOnGain.get()) {
         Game.message("Duper: the count went up. Stopping while it is ahead; check the container before you trust it.");
         this.setEnabled(false);
         return;
      }
      if (this.run >= this.runs.getInt()) {
         this.setEnabled(false);
         return;
      }
      this.phase = WAITING;
      this.waiting = 0;
   }

   private int target(Object menu, Object player) throws Exception {
      int wanted = this.slot.getInt();
      if (wanted >= 0) {
         return wanted;
      }
      int size = ((List<?>)Bridge.field(menu, "field_7761")).size();
      int held = ((Number)Bridge.call(Bridge.call(player, "method_31548"), "method_67532")).intValue();
      return size - 9 + Math.max(0, Math.min(8, held));
   }

   private Object stack(int slot) {
      try {
         return Bridge.call(Game.invoke(this.menu, "method_7611", new String[]{"int"}, slot), "method_7677");
      } catch (LinkageError | Exception unknown) {
         return null;
      }
   }

   /** Every slot of the open menu, so a dupe that lands anywhere in it is counted. */
   private int total() throws Exception {
      int count = 0;
      int size = ((List<?>)Bridge.field(this.menu, "field_7761")).size();
      for (int index = 0; index < size; index++) {
         Object stack = this.stack(index);
         if (stack != null && !(Boolean)Bridge.call(stack, "method_7960") && Bridge.call(stack, "method_7909") == this.item) {
            count += ((Number)Bridge.call(stack, "method_7947")).intValue();
         }
      }
      return count;
   }

   private void burst(Object client, Object player) throws Exception {
      int slot = this.resolved;
      int button = this.button.getInt();
      int count = this.clicks.getInt();
      switch ((DuperPattern)this.pattern.get()) {
         case THROW_RACE:
            for (int index = 0; index < count; index++) {
               this.click(client, player, slot, button, "field_7795");
            }
            break;
         case MOVE_RACE:
            for (int index = 0; index < count; index++) {
               this.click(client, player, slot, 0, "field_7794");
               this.click(client, player, slot, button, "field_7795");
            }
            break;
         case CURSOR_SPLIT:
            for (int index = 0; index < count; index++) {
               this.click(client, player, slot, 0, "field_7790");
               this.click(client, player, -999, 0, "field_7790");
            }
            break;
         case SWAP_RACE:
            for (int index = 0; index < count; index++) {
               this.click(client, player, slot, Math.min(8, button), "field_7791");
               this.click(client, player, slot, 1, "field_7795");
            }
            break;
         case CLOSE_RACE:
            for (int index = 0; index < count; index++) {
               this.click(client, player, slot, 0, "field_7794");
            }
            Bridge.call(player, "method_7346");
            this.remaining = 1;
            break;
      }
   }

   private void click(Object client, Object player, int slot, int button, String action) throws Exception {
      Object manager = Bridge.field(client, "field_1761");
      Object type = Bridge.field(Game.type("net.minecraft.class_1713"), action);
      Game.invoke(
         manager,
         "method_2906",
         new String[]{"int", "int", "int", "net.minecraft.class_1713", "net.minecraft.class_1657"},
         ((Number)Bridge.field(this.menu, "field_7763")).intValue(),
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

   public static String status() {
      Duper duper = instance;
      if (duper == null) {
         return "Duper unavailable";
      }
      if (!duper.enabled) {
         return "Duper idle: " + duper.pattern.get() + ", " + duper.clicks.getInt() + " x " + duper.bursts.getInt()
            + ", slot " + (duper.slot.getInt() < 0 ? "held" : String.valueOf(duper.slot.getInt()));
      }
      String phase = duper.phase == WAITING ? "waiting" + (duper.blocked.isEmpty() ? "" : " (" + duper.blocked + ")")
         : duper.phase == RUNNING ? "run " + duper.run + ", " + duper.remaining + " bursts left" : "settling";
      return "Duper " + duper.pattern.get() + ": " + phase + ", " + duper.sent + " clicks sent";
   }
}
