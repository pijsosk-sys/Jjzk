package com.roothack.overlord.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.screen.AbstractFurnaceScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Loads an already-open furnace with fuel and input, then pulls finished output.
 * Slot layout of {@link AbstractFurnaceScreenHandler}: 0 = input, 1 = fuel,
 * 2 = output. The process walks Baritone to the furnace and opens it; this
 * helper only shuffles the stacks.
 */
public final class SmeltingHelper {

    public static final int INPUT_SLOT = 0;
    public static final int FUEL_SLOT = 1;
    public static final int OUTPUT_SLOT = 2;

    private SmeltingHelper() {}

    /** Place fuel and input into the open furnace. */
    public static boolean load(MinecraftClient mc, Item fuel, Item input) {
        if (mc.player == null || mc.interactionManager == null) return false;
        if (!(mc.player.currentScreenHandler instanceof AbstractFurnaceScreenHandler handler)) {
            return false;
        }
        int syncId = handler.syncId;

        boolean placedFuel = moveInto(mc, syncId, fuel, FUEL_SLOT);
        boolean placedInput = moveInto(mc, syncId, input, INPUT_SLOT);
        return placedFuel && placedInput;
    }

    /** Shift-click the smelted output back into the inventory. */
    public static void collect(MinecraftClient mc) {
        if (mc.player == null || mc.interactionManager == null) return;
        if (!(mc.player.currentScreenHandler instanceof AbstractFurnaceScreenHandler handler)) {
            return;
        }
        mc.interactionManager.clickSlot(
                handler.syncId, OUTPUT_SLOT, 0, SlotActionType.QUICK_MOVE, mc.player);
    }

    /** Pick the item up from the inventory and drop it on the target slot. */
    private static boolean moveInto(MinecraftClient mc, int syncId, Item item, int targetSlot) {
        int invSlot = handlerSlotOf(mc, item);
        if (invSlot < 0) return false;
        // PICKUP from source, PICKUP onto target. Leaves surplus on the cursor,
        // so a trailing PICKUP returns it to the origin slot.
        mc.interactionManager.clickSlot(syncId, invSlot, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(syncId, targetSlot, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(syncId, invSlot, 0, SlotActionType.PICKUP, mc.player);
        return true;
    }

    /** Index of a held item within the furnace handler's slot space, or -1. */
    private static int handlerSlotOf(MinecraftClient mc, Item item) {
        var handler = mc.player.currentScreenHandler;
        for (int i = 0; i < handler.slots.size(); i++) {
            var stack = handler.getSlot(i).getStack();
            if (stack.isOf(item)) return i;
        }
        return -1;
    }
}
