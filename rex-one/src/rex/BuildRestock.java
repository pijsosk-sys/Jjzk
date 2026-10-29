package rex;

import dev.donut.client.autobuild.AutoBuilderController;
import dev.donut.client.mixin.BuilderLocalPlayerAccessor;
import java.util.Set;
import net.minecraft.class_1268;
import net.minecraft.class_1661;
import net.minecraft.class_1713;
import net.minecraft.class_1733;
import net.minecraft.class_1747;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_243;
import net.minecraft.class_2480;
import net.minecraft.class_310;
import net.minecraft.class_3532;
import net.minecraft.class_3965;
import net.minecraft.class_746;
import net.minecraft.class_9288;
import net.minecraft.class_9334;

/**
 * Restocking from a carried shulker box (Build Tempo, Shulker Restock). When the builder runs out
 * of a block and a shulker box in the inventory holds it: place the box next to the player, open
 * it, shift-click out what the build still needs, close it, break it and pick it back up. Every
 * click waits for the server to have the player's rotation first, the same rule as placing.
 */
public final class BuildRestock {
   private enum Phase {
      PLACE,
      PLACED,
      OPEN,
      SCREEN,
      TAKE,
      BREAK,
      PICKUP
   }

   private static Phase phase;
   private static class_2338 spot;
   private static class_1792 wanted;
   private static Set<class_1792> needed;
   private static int ticks;
   private static int cooldown;
   private static boolean mining;
   private static int taken;

   private BuildRestock() {
   }

   public static boolean active() {
      return phase != null;
   }

   public static String status() {
      return phase == null ? "" : "Restocking " + wanted.method_63680().getString() + " from a shulker box";
   }

   /** Starts a restock for the item if the setting is on and a carried shulker holds it. */
   public static boolean start(class_310 client, AutoBuilderController builder, class_1792 item) {
      if (!BuildTempo.restock() || shulkerSlot(client.field_1724.method_31548(), item) < 0) {
         return false;
      }
      class_2338 free = freeSpot(client, builder);
      if (free == null) {
         return false;
      }
      wanted = item;
      needed = builder.remainingItems();
      needed.add(item);
      spot = free;
      phase = Phase.PLACE;
      ticks = 0;
      cooldown = 0;
      mining = false;
      taken = 0;
      return true;
   }

   /** One tick of the restock. Returns a pause reason if it had to give up, otherwise null. */
   public static String step(class_310 client, AutoBuilderController builder) {
      class_746 player = client.field_1724;
      ticks++;
      if (ticks > (phase == Phase.BREAK ? 400 : 160)) {
         String failed = "Restock stopped while " + phase.name().toLowerCase() + " at " + spot.method_23854() + ". Collect the shulker box, then Resume.";
         stop(client);
         return failed;
      }
      if (cooldown > 0) {
         cooldown--;
         return null;
      }

      BuilderLocalPlayerAccessor sent = (BuilderLocalPlayerAccessor)player;
      switch (phase) {
         case PLACE -> {
            class_1661 inventory = player.method_31548();
            int slot = shulkerSlot(inventory, wanted);
            if (slot < 0) {
               stop(client);
               return "The shulker box with " + wanted.method_63680().getString() + " is gone. Resume to try again.";
            }
            if (slot >= 9) {
               client.field_1761.method_2906(player.field_7498.field_7763, slot, inventory.method_67532(), class_1713.field_7791, player);
               cooldown = 2;
               return null;
            }
            inventory.method_61496(slot);
            builder.restockHold(client, true);
            class_2338 below = spot.method_10074();
            class_243 point = new class_243(below.method_10263() + 0.5, below.method_10264() + 1.0, below.method_10260() + 0.5);
            if (aimed(client, sent, point, true)) {
               client.field_1761.method_2896(player, class_1268.field_5808, new class_3965(point, class_2350.field_11036, below, false));
               player.method_6104(class_1268.field_5808);
               phase = Phase.PLACED;
               ticks = 0;
            }
         }
         case PLACED -> {
            if (client.field_1687.method_8320(spot).method_26204() instanceof class_2480) {
               phase = Phase.OPEN;
               ticks = 0;
            }
         }
         case OPEN -> {
            builder.restockHold(client, false);
            class_243 point = new class_243(spot.method_10263() + 0.5, spot.method_10264() + 1.0, spot.method_10260() + 0.5);
            if (aimed(client, sent, point, false)) {
               client.field_1761.method_2896(player, class_1268.field_5808, new class_3965(point, class_2350.field_11036, spot, false));
               player.method_6104(class_1268.field_5808);
               phase = Phase.SCREEN;
               ticks = 0;
            }
         }
         case SCREEN -> {
            if (player.field_7512 instanceof class_1733) {
               phase = Phase.TAKE;
               ticks = 0;
            }
         }
         case TAKE -> {
            if (!(player.field_7512 instanceof class_1733 menu)) {
               phase = Phase.BREAK;
               ticks = 0;
               return null;
            }
            int clicks = 0;
            for (int i = 0; i < 27 && clicks < 2; i++) {
               class_1799 stack = menu.field_7761.get(i).method_7677();
               if (!stack.method_7960() && needed.contains(stack.method_7909()) && hasRoom(player.method_31548(), stack)) {
                  taken += stack.method_7947();
                  client.field_1761.method_2906(menu.field_7763, i, 0, class_1713.field_7794, player);
                  clicks++;
               }
            }
            if (clicks == 0) {
               player.method_7346();
               phase = Phase.BREAK;
               ticks = 0;
               cooldown = 2;
            }
         }
         case BREAK -> {
            if (!(client.field_1687.method_8320(spot).method_26204() instanceof class_2480)) {
               phase = Phase.PICKUP;
               ticks = 0;
               return null;
            }
            builder.restockHold(client, false);
            class_243 point = new class_243(spot.method_10263() + 0.5, spot.method_10264() + 1.0, spot.method_10260() + 0.5);
            if (aimed(client, sent, point, false)) {
               if (!mining) {
                  client.field_1761.method_2910(spot, class_2350.field_11036);
                  mining = true;
               } else {
                  client.field_1761.method_2902(spot, class_2350.field_11036);
               }
               player.method_6104(class_1268.field_5808);
            }
         }
         case PICKUP -> {
            if (ticks > 30 || shulkerCount(player.method_31548()) > 0 && ticks > 5) {
               Game.message("Auto Build: restocked " + taken + " items from a shulker box.");
               stop(client);
            }
         }
      }
      return null;
   }

   private static void stop(class_310 client) {
      if (client.field_1724 != null && client.field_1724.field_7512 instanceof class_1733) {
         client.field_1724.method_7346();
      }
      phase = null;
      spot = null;
      needed = null;
   }

   /** Points the camera at the point and reports whether the server already has that look. */
   private static boolean aimed(class_310 client, BuilderLocalPlayerAccessor sent, class_243 point, boolean sneak) {
      class_746 player = client.field_1724;
      class_243 delta = point.method_1020(player.method_33571());
      float yaw = (float)Math.toDegrees(Math.atan2(-delta.field_1352, delta.field_1350));
      float pitch = (float)Math.toDegrees(Math.atan2(-delta.field_1351, Math.hypot(delta.field_1352, delta.field_1350)));
      dev.donut.client.autobuild.BuilderLook.toward(player, yaw, pitch);
      return Math.abs(class_3532.method_15393(sent.donut$lastSentYaw() - yaw)) < 0.5F
         && Math.abs(sent.donut$lastSentPitch() - pitch) < 0.5F
         && sent.donut$lastSentInput() != null
         && sent.donut$lastSentInput().comp_3164() == sneak;
   }

   private static boolean isShulker(class_1799 stack) {
      return stack.method_7909() instanceof class_1747 block && block.method_7711() instanceof class_2480;
   }

   private static int shulkerCount(class_1661 inventory) {
      int count = 0;
      for (int i = 0; i < 36; i++) {
         if (isShulker(inventory.method_5438(i))) {
            count++;
         }
      }
      return count;
   }

   /** The inventory slot of a shulker box that holds the item, or -1. */
   private static int shulkerSlot(class_1661 inventory, class_1792 item) {
      for (int i = 0; i < 36; i++) {
         class_1799 stack = inventory.method_5438(i);
         if (isShulker(stack)) {
            class_9288 contents = stack.method_58694(class_9334.field_49622);
            if (contents != null) {
               for (class_1799 inside : contents.method_59714()) {
                  if (inside.method_7909() == item) {
                     return i;
                  }
               }
            }
         }
      }
      return -1;
   }

   private static boolean hasRoom(class_1661 inventory, class_1799 stack) {
      for (int i = 0; i < 36; i++) {
         class_1799 slot = inventory.method_5438(i);
         if (slot.method_7960() || slot.method_7909() == stack.method_7909() && slot.method_7947() < slot.method_7914()) {
            return true;
         }
      }
      return false;
   }

   /**
    * A spot beside the player (diagonals too, so the dropped box lands within pickup reach) on
    * solid ground with air for the lid. A blueprint cell that is still empty is fine: the box is
    * gone again before building carries on.
    */
   private static class_2338 freeSpot(class_310 client, AutoBuilderController builder) {
      class_2338 feet = client.field_1724.method_24515();
      int[][] around = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}, {1, -1}, {1, 1}, {-1, 1}, {-1, -1}};
      for (int[] offset : around) {
         class_2338 cell = feet.method_10069(offset[0], 0, offset[1]);
         class_2338 below = cell.method_10074();
         if (client.field_1687.method_8320(cell).method_26215()
            && client.field_1687.method_8320(cell.method_10084()).method_26215()
            && client.field_1687.method_8320(below).method_26234(client.field_1687, below)) {
            return cell;
         }
      }
      return null;
   }
}
