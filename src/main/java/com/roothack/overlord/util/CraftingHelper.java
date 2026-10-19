package com.roothack.overlord.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Crafts by placing ingredients directly into the grid of an open handler and
 * pulling the result, using only slot clicks. This avoids the recipe-book API,
 * which was reworked across 1.21.x and is brittle to compile against; slot
 * clicks are stable.
 *
 * <p>Grid indices are the vanilla crafting layout. For a crafting table the
 * 3x3 input slots are handler indices 1..9 and the result is slot 0. For the
 * 2x2 player inventory grid the inputs are 1..4 and the result is slot 0. Pass
 * {@code gridBase} accordingly (1 for both; the 2x2 simply ignores indices
 * beyond 4).
 */
public final class CraftingHelper {

    private CraftingHelper() {}

    // --- shape builders ---------------------------------------------------

    /** Tool head of 3 material across the top, handle of 2 sticks down. */
    public static Map<Integer, Item> pickaxe(Item mat) {
        Map<Integer, Item> m = new LinkedHashMap<>();
        m.put(0, mat); m.put(1, mat); m.put(2, mat);
        m.put(4, Items.STICK); m.put(7, Items.STICK);
        return m;
    }

    public static Map<Integer, Item> sword(Item mat) {
        Map<Integer, Item> m = new LinkedHashMap<>();
        m.put(1, mat); m.put(4, mat); m.put(7, Items.STICK);
        return m;
    }

    public static Map<Integer, Item> helmet(Item mat) {
        return ring(mat, 0, 1, 2, 3, 5);
    }

    public static Map<Integer, Item> chestplate(Item mat) {
        return ring(mat, 0, 2, 3, 4, 5, 6, 7, 8);
    }

    public static Map<Integer, Item> leggings(Item mat) {
        return ring(mat, 0, 1, 2, 3, 5, 6, 8);
    }

    public static Map<Integer, Item> boots(Item mat) {
        return ring(mat, 3, 5, 6, 8);
    }

    public static Map<Integer, Item> furnace() {
        return ring(Items.COBBLESTONE, 0, 1, 2, 3, 5, 6, 7, 8);
    }

    public static Map<Integer, Item> craftingTable(Item planks) {
        return ring(planks, 0, 1, 3, 4);
    }

    public static Map<Integer, Item> sticks(Item planks) {
        return ring(planks, 0, 3);
    }

    public static Map<Integer, Item> planks(Item log) {
        return ring(log, 0);
    }

    private static Map<Integer, Item> ring(Item mat, int... slots) {
        Map<Integer, Item> m = new LinkedHashMap<>();
        for (int s : slots) m.put(s, mat);
        return m;
    }

    // --- execution --------------------------------------------------------

    /**
     * Place one of each ingredient into the grid, then shift-pull the result.
     *
     * @param layout   grid-index (0..8) -> item
     * @param gridBase handler slot that grid index 0 maps to (1 for tables and
     *                 the 2x2 inventory grid)
     * @param resultSlot handler slot of the output (0)
     * @return true if every ingredient was available and placed
     */
    public static boolean craftShaped(MinecraftClient mc, Map<Integer, Item> layout,
                                      int gridBase, int resultSlot) {
        if (mc.player == null || mc.interactionManager == null) return false;
        ScreenHandler handler = mc.player.currentScreenHandler;
        if (handler == null) return false;
        int syncId = handler.syncId;

        for (Map.Entry<Integer, Item> cell : layout.entrySet()) {
            int target = gridBase + cell.getKey();
            int src = findInHandler(handler, cell.getValue());
            if (src < 0) return false;

            // Pick up the source stack, right-click once onto the target cell to
            // drop a single item, then return the remainder to the source.
            mc.interactionManager.clickSlot(syncId, src, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(syncId, target, 1, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(syncId, src, 0, SlotActionType.PICKUP, mc.player);
        }

        // Pull the crafted result into the inventory.
        mc.interactionManager.clickSlot(syncId, resultSlot, 0, SlotActionType.QUICK_MOVE, mc.player);
        return true;
    }

    private static int findInHandler(ScreenHandler handler, Item item) {
        for (int i = 0; i < handler.slots.size(); i++) {
            if (handler.getSlot(i).getStack().isOf(item)) return i;
        }
        return -1;
    }
}
