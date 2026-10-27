package rex;

/**
 * What a duper can actually attack. The plain click race belongs to the first entry and has been
 * dead on vanilla since 1.17, when every click started carrying the menu's revision and a mismatch
 * made the server resend the whole menu. The rest aim at the layers that never got that fix:
 * plugin menus, plugin economy handlers and the save that happens when a connection dies.
 */
public enum DuperPattern {
   HELD_THROW(
      true,
      false,
      "Throws the stack repeatedly against one menu revision.",
      "The vanilla click race. 1.17 added the revision check that kills it, so expect nothing on a "
         + "current server; it is here as the baseline the others are measured against."
   ),
   GUI_RACE(
      true,
      false,
      "Clicks one slot of a plugin menu several times inside a single tick.",
      "Open a shop, auction, crate or backpack menu and point Slot at the item, or hold the item and "
         + "leave Target on HELD. A plugin handler that is not debounced runs once per click against "
         + "the item it saw first."
   ),
   STALE_WINDOW(
      true,
      true,
      "Keeps clicking the menu id the plugin has already replaced.",
      "Open the plugin menu, let it do whatever reopens it — confirm, next page, buy — and this keeps "
         + "clicking the id it had before. A plugin that does not check the window id handles those "
         + "clicks against the menu it threw away."
   ),
   COMMAND_RACE(
      false,
      false,
      "Sends one command several times inside a single tick.",
      "Set the command with .one dupe cmd <text>, for example /sell all or /ah sell 1. An economy "
         + "handler that reads your inventory before it takes the items pays for every copy."
   ),
   LOGOUT(
      false,
      false,
      "Drops the stack, then kills the socket in the same tick.",
      "Stand where the drop lands, hold the stack, toggle. The connection dies with no logout packet, "
         + "so the drop races the save. Reconnect and compare: you cannot be counted for while gone."
   ),
   AUTO(
      true,
      false,
      "Runs each pattern above in turn and reports which one moved the count.",
      "Open the menu you want to test, hold the item, toggle. One run per pattern, counted separately; "
         + "LOGOUT is left out because it ends the session."
   );

   public final boolean clicks;
   public final boolean stale;
   public final String what;
   public final String how;

   DuperPattern(boolean clicks, boolean stale, String what, String how) {
      this.clicks = clicks;
      this.stale = stale;
      this.what = what;
      this.how = how;
   }
}
