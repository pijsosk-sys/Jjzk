package dev.donut.client.autobuild;

import dev.donut.client.config.AutoBuilderSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** The Creative Mode toggle: fetch missing materials from the creative inventory while building. */
public final class CreativeBuildMode implements Runnable {
   private static AutoBuilderSettings activeSettings;
   private final AutoBuilderSettings settings;
   private final Runnable save;

   public CreativeBuildMode(AutoBuilderSettings var1, Runnable var2) {
      this.settings = var1;
      this.save = var2;
   }

   public static void configure(AutoBuilderSettings var0) {
      activeSettings = var0;
   }

   public static AutoBuilderSettings settings() {
      return activeSettings;
   }

   public static BuildTempo tempo() {
      return activeSettings == null ? BuildTempo.FAST : BuildTempo.parse(activeSettings.tempo);
   }

   public static String label(AutoBuilderSettings var0) {
      return "Creative Mode: " + (var0.creativeMode ? "ON" : "OFF");
   }

   @Override
   public void run() {
      this.settings.creativeMode = !this.settings.creativeMode;
      configure(this.settings);
      this.save.run();
   }

   /** Creative Mode is on and the server really gives this player creative access. */
   public static boolean canFetch(MinecraftClient client) {
      return activeSettings != null
         && activeSettings.creativeMode
         && client != null
         && client.player != null
         && client.world != null
         && client.interactionManager != null
         && client.player.getAbilities().creativeMode
         && client.interactionManager.getCurrentGameMode().isCreative()
         && client.player.currentScreenHandler == client.player.playerScreenHandler;
   }

   /**
    * @param found the slot the item was found in, or negative
    * @return {@code found}, or the slot a fresh stack was fetched into
    */
   public static int ensureSlot(MinecraftClient client, Item item, int found) {
      if (found >= 0 || item == null || !canFetch(client)) {
         return found;
      }

      ItemStack stack = item.getDefaultStack();
      if (stack.isEmpty() || stack.getMaxCount() < 1) {
         return found;
      }

      PlayerInventory inventory = client.player.getInventory();
      for (int slot = 0; slot < 36; slot++) {
         if (inventory.getStack(slot).isEmpty()) {
            ItemStack full = stack.copyWithCount(stack.getMaxCount());
            inventory.setStack(slot, full);
            client.interactionManager.clickCreativeStack(full, slot < 9 ? slot + 36 : slot);
            return slot;
         }
      }

      return found;
   }
}
