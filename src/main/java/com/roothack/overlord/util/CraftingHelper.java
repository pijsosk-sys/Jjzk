package com.roothack.overlord.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;

import java.util.Optional;

/**
 * Drives the recipe book of an already-open crafting screen. The process is
 * responsible for walking Baritone to a crafting table and opening it; once a
 * {@code CraftingScreenHandler} (or the 2x2 inventory grid) is on screen, these
 * calls fill the grid from the recipe book and pull the result into the
 * inventory.
 *
 * Signatures target Yarn 1.20.4. {@code clickRecipe} moves ingredients into the
 * grid using the player's inventory; {@code clickSlot} with QUICK_MOVE then
 * shift-clicks the output slot so every craftable unit is produced in one pass.
 */
public final class CraftingHelper {

    private CraftingHelper() {}

    /**
     * @param recipeId e.g. "minecraft:stone_pickaxe"
     * @param outputSlot the result slot of the open handler (0 for both the
     *                   2x2 inventory grid and the 3x3 table)
     * @return true if the recipe existed and was dispatched
     */
    public static boolean craftAll(MinecraftClient mc, String recipeId, int outputSlot) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return false;
        if (mc.player.currentScreenHandler == null) return false;

        Optional<RecipeEntry<?>> entry =
                mc.world.getRecipeManager().get(Identifier.of(recipeId));
        if (entry.isEmpty()) return false;

        int syncId = mc.player.currentScreenHandler.syncId;
        Recipe<?> recipe = entry.get().value();

        // Fill the grid from the recipe book, crafting the full stack (shift).
        mc.interactionManager.clickRecipe(syncId, entry.get(), true);

        // Shift-click the result into the inventory.
        mc.interactionManager.clickSlot(
                syncId, outputSlot, 0, SlotActionType.QUICK_MOVE, mc.player);
        return true;
    }
}
