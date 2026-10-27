package rex;

/** Click orders a duper can send. Each one races the server's view of the open menu. */
public enum DuperPattern {
   THROW_RACE(
      "Throws the slot %d times against one local state.",
      "Open the container, hold the stack in the slot you set (-1 means the slot in your hand), "
         + "stand where the drops will land, then toggle. Collect what lands and compare it with the report."
   ),
   MOVE_RACE(
      "Shift-moves and throws the same slot inside one tick.",
      "Open a chest or a shulker with the stack in the slot you set. Leave the container open; "
         + "the moved stack and the thrown stack race each other."
   ),
   CURSOR_SPLIT(
      "Picks the slot up and throws the cursor, repeatedly.",
      "Open any container with the slot full. Do not touch the mouse while it runs: "
         + "your own clicks change the cursor the race depends on."
   ),
   CLOSE_RACE(
      "Shift-moves the slot, then closes the container in the same tick.",
      "Open a chest or a shulker with the stack in the slot. One burst only; reopen the container afterwards to see what landed."
   ),
   SWAP_RACE(
      "Swaps the slot into a hotbar slot and throws it in the same tick.",
      "Open a container with the stack in the slot you set and keep hotbar slot 1 empty; Button picks the hotbar slot, 0 is the first."
   );

   public final String what;
   public final String how;

   DuperPattern(String what, String how) {
      this.what = what;
      this.how = how;
   }
}
