package com.roothack.overlord.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Operates an already-open enchanting table. Handler slots: 0 = item to
 * enchant, 1 = lapis lazuli. The three enchant options are buttons 0..2;
 * button 2 is the top (level-30) tier. {@code enchantmentPower[2]} on the
 * handler reports the level requirement so the process waits for 30.
 */
public final class EnchantHelper {

    public static final int ITEM_SLOT = 0;
    public static final int LAPIS_SLOT = 1;
    public static final int TOP_OPTION = 2;

    private EnchantHelper() {}

    /** Load one piece of gear plus lapis, then pull the result back out. */
    public static boolean enchantTopTier(MinecraftClient mc, Item gear) {
        if (mc.player == null || mc.interactionManager == null) return false;
        if (!(mc.player.currentScreenHandler instanceof EnchantmentScreenHandler handler)) {
            return false;
        }
        int syncId = handler.syncId;

        if (!place(mc, syncId, gear, ITEM_SLOT)) return false;
        place(mc, syncId, net.minecraft.item.Items.LAPIS_LAZULI, LAPIS_SLOT);

        // Top option must be unlocked (needs 15 bookshelves + 30 levels).
        if (handler.enchantmentPower[TOP_OPTION] < 1) return false;
        if (!handler.isAcceptingInventory()) {
            // Still fine to send; server validates affordability.
        }
        mc.interactionManager.clickButton(syncId, TOP_OPTION);

        // Pull the enchanted item back to the inventory.
        mc.interactionManager.clickSlot(syncId, ITEM_SLOT, 0, SlotActionType.QUICK_MOVE, mc.player);
        return true;
    }

    private static boolean place(MinecraftClient mc, int syncId, Item item, int target) {
        int src = handlerSlotOf(mc, item);
        if (src < 0) return false;
        mc.interactionManager.clickSlot(syncId, src, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(syncId, target, 0, SlotActionType.PICKUP, mc.player);
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
