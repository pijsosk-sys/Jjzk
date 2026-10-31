package rex.overlord;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_1703;
import net.minecraft.class_1707;
import net.minecraft.class_1713;
import net.minecraft.class_1714;
import net.minecraft.class_1718;
import net.minecraft.class_1720;
import net.minecraft.class_1723;
import net.minecraft.class_1735;
import net.minecraft.class_1799;
import net.minecraft.class_310;
import net.minecraft.class_4862;
import net.minecraft.class_746;

/**
 * Every click Overlord sends into an open window, one at a time with a gap between them and an
 * expected result at the end. A crafting grid filled in one tick looked like a macro and, worse,
 * raced the server: the second click landed before the answer to the first.
 */
@Environment(EnvType.CLIENT)
public final class Work {
   private enum Mode {
      CRAFT,
      SMITH,
      ENCHANT
   }

   private record Click(int slot, int button, class_1713 action) {
   }

   private final ArrayDeque<Click> queue = new ArrayDeque<>();
   private class_1703 locked;
   private Mode mode;
   private String expected = "";
   private int outputSlot;
   private long next;
   private int waited;
   /** Set when a transaction could not finish; the brain reports it and starts over. */
   public String error = "";
   public String status = "";

   public boolean busy() {
      return !this.queue.isEmpty() || this.locked != null;
   }

   public void clear() {
      this.queue.clear();
      this.locked = null;
      this.mode = null;
      this.expected = "";
      this.waited = 0;
   }

   public static class_1703 handler(class_746 player) {
      return player.field_7512;
   }

   public static class_1799 stack(class_1703 handler, int slot) {
      return handler.method_7611(slot).method_7677();
   }

   /** Closes whatever window is open, so the next window opens clean. */
   public static void close(class_310 client) {
      if (client.field_1755 != null && client.field_1724 != null) {
         client.field_1724.method_7346();
      }
   }

   private void click(class_310 client, class_1703 handler, int slot, int button, class_1713 action) {
      client.field_1761.method_2906(handler.field_7763, slot, button, action, client.field_1724);
   }

   /** Slots of the open window that belong to the player's own inventory. */
   private static List<Integer> playerSlots(class_746 player, class_1703 handler) {
      List<Integer> result = new ArrayList<>();
      for (int i = 0; i < handler.field_7761.size(); i++) {
         if (handler.method_7611(i).field_7871 == player.method_31548()) {
            result.add(i);
         }
      }
      return result;
   }

   /** The window slot holding this item, searching the player's inventory part only. */
   public static int source(class_746 player, class_1703 handler, String id) {
      for (int slot : playerSlots(player, handler)) {
         if (Ids.of(stack(handler, slot)).equals(id)) {
            return slot;
         }
      }
      return -1;
   }

   private boolean matches(class_1799 stack) {
      String id = Ids.of(stack);
      return id.equals(this.expected) || this.expected.equals("planks") && id.endsWith("_planks");
   }

   /**
    * Runs the queued clicks. True while a transaction is in flight, so the brain waits instead of
    * deciding something else; false once the result has been taken out.
    */
   public boolean pump(class_310 client, long tick) {
      if (!this.busy()) {
         return false;
      }
      class_746 player = client.field_1724;
      if (player == null || client.field_1761 == null) {
         this.clear();
         return false;
      }
      if (handler(player) != this.locked) {
         this.clear();
         this.error = "The window closed in the middle of a transaction";
         return false;
      }
      if (tick < this.next) {
         return true;
      }
      this.next = tick + 2;

      if (!this.queue.isEmpty()) {
         Click click = this.queue.removeFirst();
         this.click(client, this.locked, click.slot(), click.button(), click.action());
         return true;
      }

      class_1799 output = stack(this.locked, this.outputSlot);
      if (this.mode == Mode.ENCHANT) {
         if (this.matches(output) && output.method_7942()) {
            this.click(client, this.locked, 0, 0, class_1713.field_7794);
            this.clear();
            return true;
         }
         int[] levels = ((class_1718)this.locked).field_7808;
         int lapis = Ids.of(stack(this.locked, 1)).equals("lapis_lazuli") ? stack(this.locked, 1).method_7947() : 0;
         this.status = "Enchanting: table offers " + levels[2] + ", player has " + player.field_7520;
         if (levels[2] >= 30 && player.field_7520 >= levels[2] && lapis >= 3 && this.waited % 10 == 0) {
            client.field_1761.method_2900(this.locked.field_7763, 2);
         }
      } else if (this.matches(output)) {
         this.click(client, this.locked, this.outputSlot, 0, class_1713.field_7794);
         this.clear();
         return true;
      }

      if (++this.waited > 100) {
         this.clear();
         this.error = "The server never produced the expected " + Ids.pretty(this.expected);
      }
      return true;
   }

   /** Empties the grid back into the bag before filling it, one stack per tick. */
   private boolean clean(class_310 client, class_1703 handler, int first, int last) {
      if (!handler.method_34255().method_7960()) {
         this.error = "Something is stuck on the cursor; close the window and start again";
         return false;
      }
      for (int slot = first; slot <= last; slot++) {
         if (!stack(handler, slot).method_7960()) {
            this.click(client, handler, slot, 0, class_1713.field_7794);
            return false;
         }
      }
      return true;
   }

   /**
    * Plans the clicks: for each cell, pick the stack up, right-click one item into the cell, put
    * the stack back. Three clicks a cell is how a person does it and it keeps counts exact.
    */
   private boolean plan(class_746 player, class_1703 handler, Map<Integer, String> cells, String expected, Mode mode, int outputSlot) {
      Map<String, List<Integer>> pools = new HashMap<>();
      Map<Integer, Integer> left = new HashMap<>();
      for (int slot : playerSlots(player, handler)) {
         class_1799 stack = stack(handler, slot);
         pools.computeIfAbsent(Ids.of(stack), k -> new ArrayList<>()).add(slot);
         left.put(slot, stack.method_7947());
      }
      ArrayDeque<Click> planned = new ArrayDeque<>();
      for (Map.Entry<Integer, String> cell : cells.entrySet()) {
         int from = -1;
         for (int slot : pools.getOrDefault(cell.getValue(), List.of())) {
            if (left.get(slot) > 0) {
               from = slot;
               left.put(slot, left.get(slot) - 1);
               break;
            }
         }
         if (from < 0) {
            return false;
         }
         planned.add(new Click(from, 0, class_1713.field_7790));
         planned.add(new Click(cell.getKey(), 1, class_1713.field_7790));
         planned.add(new Click(from, 0, class_1713.field_7790));
      }
      this.locked = handler;
      this.queue.addAll(planned);
      this.expected = expected;
      this.mode = mode;
      this.outputSlot = outputSlot;
      this.waited = 0;
      return true;
   }

   public boolean craft(class_310 client, Recipes.Recipe recipe, Map<String, Integer> stock) {
      class_746 player = client.field_1724;
      class_1703 handler = handler(player);
      boolean table = handler instanceof class_1714;
      boolean small = handler instanceof class_1723;
      if (!table && !small || !table && !recipe.small()) {
         return false;
      }
      if (!this.clean(client, handler, 1, table ? 9 : 4)) {
         return false;
      }
      Map<Integer, String> cells = new LinkedHashMap<>();
      Map<String, Integer> available = new HashMap<>(stock);
      for (Map.Entry<Integer, String> cell : recipe.cells().entrySet()) {
         String id = Stock.actual(available, cell.getValue());
         if (available.getOrDefault(id, 0) <= 0) {
            return false;
         }
         cells.put(Recipes.gridSlot(cell.getKey(), table), id);
         available.merge(id, -1, Integer::sum);
      }
      this.status = "Crafting " + Ids.pretty(recipe.output());
      return this.plan(player, handler, cells, recipe.output(), Mode.CRAFT, 0);
   }

   public boolean smith(class_310 client, String base) {
      class_746 player = client.field_1724;
      class_1703 handler = handler(player);
      if (!(handler instanceof class_4862) || !this.clean(client, handler, 0, 2)) {
         return false;
      }
      Map<Integer, String> cells = new LinkedHashMap<>();
      cells.put(0, "netherite_upgrade_smithing_template");
      cells.put(1, base);
      cells.put(2, "netherite_ingot");
      this.status = "Upgrading " + Ids.pretty(base);
      return this.plan(player, handler, cells, base.replace("diamond_", "netherite_"), Mode.SMITH, 3);
   }

   public boolean enchant(class_310 client, String id) {
      class_746 player = client.field_1724;
      class_1703 handler = handler(player);
      if (!(handler instanceof class_1718) || !this.clean(client, handler, 0, 1)) {
         return false;
      }
      int lapis = source(player, handler, "lapis_lazuli");
      if (lapis < 0 || stack(handler, lapis).method_7947() < 3) {
         return false;
      }
      Map<Integer, String> cells = new LinkedHashMap<>();
      cells.put(0, id);
      if (!this.plan(player, handler, cells, id, Mode.ENCHANT, 0)) {
         return false;
      }
      // Three lapis into the fee slot, then the rest of the stack back.
      this.queue.add(new Click(lapis, 0, class_1713.field_7790));
      for (int i = 0; i < 3; i++) {
         this.queue.add(new Click(1, 1, class_1713.field_7790));
      }
      this.queue.add(new Click(lapis, 0, class_1713.field_7790));
      this.status = "Enchanting " + Ids.pretty(id);
      return true;
   }

   /** One furnace click per tick: empty the output, load the ore, load the fuel. */
   public boolean smelt(class_310 client, String input) {
      class_746 player = client.field_1724;
      class_1703 handler = handler(player);
      if (!(handler instanceof class_1720)) {
         return false;
      }
      if (!stack(handler, 2).method_7960()) {
         this.click(client, handler, 2, 0, class_1713.field_7794);
         this.status = "Taking the smelted result";
         return true;
      }
      if (stack(handler, 0).method_7960()) {
         int slot = source(player, handler, input);
         if (slot >= 0) {
            this.click(client, handler, slot, 0, class_1713.field_7794);
            this.status = "Loading " + Ids.pretty(input);
            return true;
         }
      }
      if (stack(handler, 1).method_7960()) {
         int fuel = source(player, handler, "coal");
         if (fuel < 0) {
            fuel = source(player, handler, "charcoal");
         }
         if (fuel >= 0) {
            this.click(client, handler, fuel, 0, class_1713.field_7794);
            this.status = "Loading fuel";
            return true;
         }
      }
      this.status = "Waiting for the furnace";
      return true;
   }

   private static final Set<String> LOOT = Set.of("netherite_upgrade_smithing_template", "diamond", "gold_ingot", "netherite_ingot", "obsidian", "ancient_debris");

   /** Takes anything useful out of an open chest. */
   public boolean loot(class_310 client) {
      class_746 player = client.field_1724;
      class_1703 handler = handler(player);
      if (!(handler instanceof class_1707)) {
         return false;
      }
      for (int i = 0; i < handler.field_7761.size(); i++) {
         class_1735 slot = handler.method_7611(i);
         if (slot.field_7871 == player.method_31548()) {
            continue;
         }
         if (LOOT.contains(Ids.of(slot.method_7677()))) {
            this.click(client, handler, i, 0, class_1713.field_7794);
            this.status = "Taking " + Ids.pretty(Ids.of(slot.method_7677())) + " from a chest";
            return true;
         }
      }
      return false;
   }
}
