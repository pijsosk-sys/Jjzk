package com.roothack.overlord.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Read-only queries over the client player's main inventory. Baritone owns
 * movement and mining; these helpers only answer "do we have enough yet?" so
 * the progression process can decide when a stage is finished.
 */
public final class InventoryHelper {

    private InventoryHelper() {}

    public static int count(MinecraftClient mc, Item item) {
        if (mc.player == null) return 0;
        int total = 0;
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.isOf(item)) total += s.getCount();
        }
        return total;
    }

    public static boolean has(MinecraftClient mc, Item item, int amount) {
        return count(mc, item) >= amount;
    }

    /** First inventory slot index holding the item, or -1. */
    public static int slotOf(MinecraftClient mc, Item item) {
        if (mc.player == null) return -1;
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            if (inv.getStack(i).isOf(item)) return i;
        }
        return -1;
    }

    public static boolean hasEmptySlot(MinecraftClient mc) {
        if (mc.player == null) return false;
        return mc.player.getInventory().getEmptySlot() >= 0;
    }
}
