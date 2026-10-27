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
 * desynchronised an open menu. Every run is bounded, reports what it sent and what the slot held
 * before and after, and switches the module back off; whether a given server accepts any of it
 * depends on that server's version and patches.
 */
public final class Duper extends Module {
   public static Duper instance;

   public final EnumSetting<DuperPattern> pattern = new EnumSetting<>("Pattern", DuperPattern.THROW_RACE, DuperPattern.class);
   public final NumberSetting clicks = new NumberSetting("Clicks", 1.0, 16.0, 4.0, 1.0)
      .describe("Clicks per burst, all inside one client tick.");
   public final NumberSetting bursts = new NumberSetting("Bursts", 1.0, 64.0, 4.0, 1.0);
   public final NumberSetting gap = new NumberSetting("Burst Gap", 0.0, 20.0, 1.0, 1.0)
      .describe("Ticks between bursts. Zero sends every burst in the same tick.");
   public final NumberSetting slot = new NumberSetting("Slot", -1.0, 89.0, -1.0, 1.0)
      .describe("Menu slot index, or -1 for the held hotbar slot in whatever menu is open.");
   public final NumberSetting button = new NumberSetting("Button", 0.0, 8.0, 1.0, 1.0)
      .describe("Click button: throw 0 drops one item and 1 drops the stack.");
   public final BooleanSetting container = new BooleanSetting("Container Only", true)
      .describe("Refuses to run against the bare player inventory.");
   public final BooleanSetting report = new BooleanSetting("Report", true);

   private int remaining;
   private int waiting;
   private int sent;
   private int before;
   private int resolved = -1;
   private Object menu;

   public Duper() {
      super("Duper", "Bounded container click races. One run per toggle.", -1, Category.MISC);
      this.addSettings(
         new Setting[]{this.pattern, this.clicks, this.bursts, this.gap, this.slot, this.button, this.container, this.report}
      );
      instance = this;
   }

   @Override
   public void onEnable() {
      super.onEnable();
      this.remaining = 0;
      this.sent = 0;
      this.waiting = 0;
      this.resolved = -1;
      this.menu = null;
      try {
         this.arm();
      } catch (LinkageError | Exception refused) {
         Game.message("Duper: " + message(refused));
         this.setEnabled(false);
      }
   }

   @Override
   public void onDisable() {
      super.onDisable();
      if (this.sent > 0 && this.report.get()) {
         Game.message("Duper: " + this.sent + " clicks, slot " + this.resolved + " held " + this.before + " -> " + this.count(this.resolved));
      }
      this.remaining = 0;
      this.sent = 0;
      this.menu = null;
   }

   private void arm() throws Exception {
      Object client = Bridge.client();
      Object player = Bridge.field(client, "field_1724");
      if (player == null || Bridge.field(client, "field_1687") == null) {
         throw new IllegalStateException("join a world first");
      }
      Object open = Bridge.field(player, "field_7512");
      Object own = Bridge.field(player, "field_7498");
      if (open == null) {
         throw new IllegalStateException("no menu is open");
      }
      if (this.container.get() && open == own) {
         throw new IllegalStateException("open a container, or clear Container Only");
      }
      this.menu = open;
      this.resolved = this.target(open, player);
      this.before = this.count(this.resolved);
      if (this.before <= 0) {
         throw new IllegalStateException("slot " + this.resolved + " is empty");
      }
      this.remaining = this.bursts.getInt();
      this.waiting = 0;
      if (this.report.get()) {
         Game.message(
            "Duper: " + this.pattern.get() + ", " + this.remaining + " x " + this.clicks.getInt() + " clicks on slot " + this.resolved + " (" + this.before + ")"
         );
      }
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

   private int count(int slot) {
      try {
         Object stack = Bridge.call(Game.invoke(this.menu, "method_7611", new String[]{"int"}, slot), "method_7677");
         return (Boolean)Bridge.call(stack, "method_7960") ? 0 : ((Number)Bridge.call(stack, "method_7947")).intValue();
      } catch (LinkageError | Exception unknown) {
         return -1;
      }
   }

   @EventHandler
   public void onDuperTick(ClientTickEvent event) {
      if (this.remaining > 0) {
         try {
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
               if (this.gap.getInt() > 0) {
                  this.waiting = this.gap.getInt();
                  break;
               }
            }
            if (this.remaining <= 0) {
               this.setEnabled(false);
            }
         } catch (LinkageError | Exception failure) {
            Game.message("Duper: " + message(failure));
            this.setEnabled(false);
         }
      }
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
      return "Duper " + (duper.enabled ? "running" : "idle") + ": " + duper.pattern.get() + ", " + duper.clicks.getInt() + " clicks x "
         + duper.bursts.getInt() + " bursts, gap " + duper.gap.getInt() + ", slot " + (duper.slot.getInt() < 0 ? "held" : String.valueOf(duper.slot.getInt()));
   }
}
