package rex;

/**
 * What a duper can attack. The plain click race is the last entry and has been dead on vanilla since
 * 1.17, when every click started carrying the menu's revision and a mismatch made the server resend
 * the whole menu. The rest aim at the layers that never got that fix: plugin menus, plugin economy
 * handlers, the player-action path that carries no revision at all, and the save that runs when a
 * connection dies.
 */
public enum DuperPattern {
   AUTO(
      false,
      false,
      false,
      "Runs every pattern in turn and names the one that moved the count.",
      "Open the menu you want to test, hold the stack, toggle. One run per pattern, each counted on "
         + "its own; LOGOUT is left out because it ends the session."
   ),
   DROP_PACKET(
      false,
      false,
      true,
      "Raw player-action drops: no window, no revision, nothing to resync.",
      "Works with a container open or closed. The menu-click path carries the revision that killed "
         + "the old races; this path never had one, and the server takes it whatever menu it thinks "
         + "you have open. Stand where the drops land."
   ),
   DROP_CYCLE(
      false,
      false,
      true,
      "Switches the held slot and drops, several times inside one tick.",
      "Fill a few hotbar slots with the same item. Each pair is a slot switch and a drop in the same "
         + "tick, so a handler that caches the held slot for the tick charges them all to one slot. "
         + "The real slot is sent again at the end of the burst."
   ),
   DROP_DESYNC(
      true,
      false,
      true,
      "A menu throw and a raw drop for the same stack in one tick.",
      "Open the container the stack sits in. The throw goes through the revision path and the drop "
         + "goes around it, so the same item is asked for twice by two routes that do not check each "
         + "other."
   ),
   GUI_RACE(
      true,
      true,
      false,
      "Several left clicks on one plugin-menu slot inside a single tick.",
      "Open a shop, auction, crate or backpack menu. A handler that is not debounced runs once per "
         + "click against the item it saw first."
   ),
   SHIFT_SPAM(
      true,
      true,
      false,
      "Several shift-clicks on one slot inside a single tick.",
      "Open the plugin menu that moves items on shift-click: a sell slot, a deposit slot, a backpack. "
         + "Each click asks for the same move again."
   ),
   DOUBLE_PICKUP(
      true,
      true,
      false,
      "Picks the slot up and collects every match onto the cursor, repeatedly.",
      "Open a container holding more than one stack of the item. The collect-all pass walks the whole "
         + "menu, so a plugin that rebuilds slots underneath it can hand the same stack out twice."
   ),
   SWAP_RACE(
      true,
      true,
      true,
      "Swaps the slot into a hotbar slot and throws it in the same tick.",
      "Open a container with the stack in reach and keep the hotbar slot that Button names empty."
   ),
   STALE_WINDOW(
      true,
      false,
      false,
      "Keeps clicking the window id the plugin has already replaced.",
      "Open the plugin menu, let it do whatever reopens it — confirm, next page, buy — and this keeps "
         + "clicking the id it had before. A plugin that does not check the window id handles those "
         + "clicks against the menu it threw away."
   ),
   DROP_SPAM(
      true,
      false,
      true,
      "Throws from your own inventory window while another menu is open.",
      "The clicks name window zero while the server believes you are in the menu you opened. Stand "
         + "where the drops land."
   ),
   COMMAND_RACE(
      false,
      false,
      false,
      "Sends one command several times inside a single tick.",
      "Set it with .one dupe cmd <text>, for example /sell all or /ah sell 1. An economy handler that "
         + "reads your inventory before it takes the items pays for every copy."
   ),
   LOGOUT(
      false,
      false,
      true,
      "Drops the stack, then kills the socket in the same tick.",
      "Stand where the drop lands, hold the stack, toggle. The connection dies with no logout packet, "
         + "so the drop races the save. Reconnect and compare: nothing can be counted for you while gone."
   ),
   HELD_THROW(
      true,
      false,
      true,
      "Throws the stack repeatedly against one menu revision.",
      "The vanilla click race. 1.17 added the revision check that kills it, so expect nothing on a "
         + "current server; it is the baseline the others are measured against."
   );

   /** Sends menu clicks rather than commands or player actions. */
   public final boolean clicks;
   /** Needs a real container menu, not the bare player inventory. */
   public final boolean container;
   /** Puts items on the ground, so the count has to include what is lying there. */
   public final boolean drops;
   public final String what;
   public final String how;

   DuperPattern(boolean clicks, boolean container, boolean drops, String what, String how) {
      this.clicks = clicks;
      this.container = container;
      this.drops = drops;
      this.what = what;
      this.how = how;
   }
}
