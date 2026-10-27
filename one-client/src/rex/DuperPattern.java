package rex;

/**
 * What a duper can attack. The plain click race belongs to the last entry and has been dead on
 * vanilla since 1.17, when every click started carrying the menu's revision and a mismatch made the
 * server resend the whole menu. The rest aim at the layers that never got that fix: plugin menus,
 * plugin economy handlers and the save that happens when a connection dies.
 */
public enum DuperPattern {
   AUTO(
      false,
      false,
      "Runs every pattern in turn and names the one that moved the count.",
      "Open the menu you want to test, hold the stack, toggle. One run per pattern, each counted on "
         + "its own; LOGOUT is left out because it ends the session."
   ),
   GUI_RACE(
      true,
      true,
      "Several left clicks on one plugin-menu slot inside a single tick.",
      "Open a shop, auction, crate or backpack menu. A handler that is not debounced runs once per "
         + "click against the item it saw first."
   ),
   SHIFT_SPAM(
      true,
      true,
      "Several shift-clicks on one slot inside a single tick.",
      "Open the plugin menu that moves items on shift-click: a sell slot, a deposit slot, a backpack. "
         + "Each click asks for the same move again."
   ),
   DOUBLE_PICKUP(
      true,
      true,
      "Picks the slot up and collects every match onto the cursor, repeatedly.",
      "Open a container holding more than one stack of the item. The collect-all pass walks the whole "
         + "menu, so a plugin that rebuilds slots underneath it can hand the same stack out twice."
   ),
   SWAP_RACE(
      true,
      true,
      "Swaps the slot into a hotbar slot and throws it in the same tick.",
      "Open a container with the stack in reach and keep the hotbar slot that Button names empty."
   ),
   STALE_WINDOW(
      true,
      false,
      "Keeps clicking the window id the plugin has already replaced.",
      "Open the plugin menu, let it do whatever reopens it — confirm, next page, buy — and this keeps "
         + "clicking the id it had before. A plugin that does not check the window id handles those "
         + "clicks against the menu it threw away."
   ),
   DROP_SPAM(
      true,
      false,
      "Throws from your own inventory window while another menu is open.",
      "Works anywhere, container or not: the clicks name window zero while the server believes you are "
         + "in the menu you opened. Stand where the drops land."
   ),
   COMMAND_RACE(
      false,
      false,
      "Sends one command several times inside a single tick.",
      "Set it with .one dupe cmd <text>, for example /sell all or /ah sell 1. An economy handler that "
         + "reads your inventory before it takes the items pays for every copy."
   ),
   LOGOUT(
      false,
      false,
      "Drops the stack, then kills the socket in the same tick.",
      "Stand where the drop lands, hold the stack, toggle. The connection dies with no logout packet, "
         + "so the drop races the save. Reconnect and compare: nothing can be counted for you while gone."
   ),
   HELD_THROW(
      true,
      false,
      "Throws the stack repeatedly against one menu revision.",
      "The vanilla click race. 1.17 added the revision check that kills it, so expect nothing on a "
         + "current server; it is the baseline the others are measured against."
   );

   /** Sends menu clicks rather than commands. */
   public final boolean clicks;
   /** Needs a real container menu, not the bare player inventory. */
   public final boolean container;
   public final String what;
   public final String how;

   DuperPattern(boolean clicks, boolean container, String what, String how) {
      this.clicks = clicks;
      this.container = container;
      this.what = what;
      this.how = how;
   }
}
