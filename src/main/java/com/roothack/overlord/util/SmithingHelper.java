package com.roothack.overlord.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.screen.SmithingScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Drives an already-open smithing table to apply the netherite upgrade.
 * Handler slots: 0 = upgrade template, 1 = base gear, 2 = netherite ingot,
 * 3 = result. The process opens the table; this loads the three inputs and
 * pulls the upgraded piece.
 */
public final class SmithingHelper {

    public static final int TEMPLATE_SLOT = 0;
    public static final int BASE_SLOT = 1;
    public static final int ADDITION_SLOT = 2;
    public static final int RESULT_SLOT = 3;

    private SmithingHelper() {}

    public static boolean upgrade(MinecraftClient mc, Item template, Item base, Item addition) {
        if (mc.player == null || mc.interactionManager == null) return false;
        if (!(mc.player.currentScreenHandler instanceof SmithingScreenHandler handler)) {
            return false;
        }
        int syncId = handler.syncId;

        if (!place(mc, syncId, template, TEMPLATE_SLOT)) return false;
        if (!place(mc, syncId, base, BASE_SLOT)) return false;
        if (!place(mc, syncId, addition, ADDITION_SLOT)) return false;

        mc.interactionManager.clickSlot(syncId, RESULT_SLOT, 0, SlotActionType.QUICK_MOVE, mc.player);
        return true;
    }

    private static boolean place(MinecraftClient mc, int syncId, Item item, int target) {
        int src = handlerSlotOf(mc, item);
        if (src < 0) return false;
        // One item per input slot: pick up, right-click one onto target, return.
        mc.interactionManager.clickSlot(syncId, src, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(syncId, target, 1, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(syncId, src, 0, SlotActionType.PICKUP, mc.player);
        return true;
    }

    private static int handlerSlotOf(MinecraftClient mc, Item item) {
        var handler = mc.player.currentScreenHandler;
        for (int i = 0; i < handler.slots.size(); i++) {
            if (handler.getSlot(i).getStack().isOf(item)) return i;
        }
        return -1;
    }
}
