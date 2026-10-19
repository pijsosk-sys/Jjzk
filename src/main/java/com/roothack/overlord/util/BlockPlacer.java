package com.roothack.overlord.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Places a utility block (crafting table, furnace) from the hotbar onto the
 * block the player is currently looking at, then returns whether the resulting
 * use opened a screen. The process is responsible for aiming at solid ground
 * first (Baritone's look behaviour / a downward pitch); this only selects the
 * item and issues the right-click.
 */
public final class BlockPlacer {

    private BlockPlacer() {}

    /** Move the given item into the held hotbar slot if it is in the inventory. */
    public static boolean selectInHotbar(MinecraftClient mc, Item item) {
        if (mc.player == null) return false;
        var inv = mc.player.getInventory();
        for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).isOf(item)) {
                inv.setSelectedSlot(i);
                return true;
            }
        }
        // Not on the hotbar: pull it down from main inventory via swap.
        int slot = InventoryHelper.slotOf(mc, item);
        if (slot < 0 || mc.interactionManager == null) return false;
        // Hotbar swap uses button 0..8 with SWAP action against slot 0 of hotbar.
        mc.interactionManager.clickSlot(
                mc.player.playerScreenHandler.syncId, slot, 0,
                net.minecraft.screen.slot.SlotActionType.SWAP, mc.player);
        inv.setSelectedSlot(0);
        return inv.getStack(0).isOf(item);
    }

    /**
     * Right-click the block currently under the crosshair. Returns true when a
     * block face was targeted and the interaction was sent.
     */
    public static boolean placeOnLookedAt(MinecraftClient mc) {
        if (mc.player == null || mc.interactionManager == null) return false;
        HitResult hit = mc.crosshairTarget;
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        var result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, blockHit);
        mc.player.swingHand(Hand.MAIN_HAND);
        return result.isAccepted();
    }

    /** Aim straight down at the block the player stands on. */
    public static BlockHitResult downwardHit(MinecraftClient mc) {
        var pos = mc.player.getBlockPos().down();
        var center = Vec3d.ofCenter(pos).add(0, 0.5, 0);
        return new BlockHitResult(center, Direction.UP, pos, false);
    }

    /** Open a utility block already looked at by sending a bare use-block. */
    public static boolean openLookedAt(MinecraftClient mc) {
        return placeOnLookedAt(mc);
    }
}
