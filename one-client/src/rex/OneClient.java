package rex;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.api.ClientModInitializer;

/** The six former entry points start here, in their old order, each isolated from the others. */
public final class OneClient implements ClientModInitializer {
   public static final String VERSION = "1.0.0-one.1";
   private static final String[] PARTS = {
      "dev.donut.client.DonutClient",
      "dev.krypton.client.KryptonClient",
      "rex.HalloweenResources",
      "rex.RexBootstrap",
      "rex.XrayBootstrap",
      "rex.LayerBuild"
   };
   private static final Map<String, String> parts = new LinkedHashMap<>();

   @Override
   public void onInitializeClient() {
      for (String name : PARTS) {
         try {
            Object part = Class.forName(name).getDeclaredConstructor().newInstance();
            part.getClass().getMethod("onInitializeClient").invoke(part);
            parts.put(name, "ok");
            System.out.println("[ONE] " + name + " ready");
         } catch (Throwable failure) {
            Throwable cause = failure instanceof InvocationTargetException && failure.getCause() != null ? failure.getCause() : failure;
            parts.put(name, cause.toString());
            System.err.println("[ONE] " + name + " failed: " + cause);
         }
      }
      System.out.println("[ONE] REX One " + VERSION + ": " + ready() + "/" + PARTS.length + " subsystems; Right Shift opens the menu");
   }

   private static int ready() {
      int count = 0;
      for (String state : parts.values()) {
         if (state.equals("ok")) {
            count++;
         }
      }
      return count;
   }

   /** One menu key. Right Shift opens the module menu; the legacy Donut screen moves to .one donut. */
   public static int binding(String action, int key) {
      if (action.equals("open_gui")) {
         return -1;
      }
      return action.equals("open_autobuilder") ? 79 : key;
   }

   public static boolean command(String line) {
      String[] words = line.strip().split("\\s+", 2);
      if (words.length == 0 || !words[0].equalsIgnoreCase(".one")) {
         return false;
      }
      String action = words.length > 1 ? words[1].toLowerCase(Locale.ROOT) : "status";
      try {
         switch (action) {
            case "status":
               Game.message("REX One " + VERSION + " | " + ready() + "/" + PARTS.length + " subsystems | " + BuildTempo.describe());
               for (Map.Entry<String, String> part : parts.entrySet()) {
                  Game.message("  " + part.getKey() + ": " + part.getValue());
               }
               break;
            case "donut":
            case "settings":
               openDonutScreen();
               break;
            case "keys":
               Game.message("Right Shift: menu | O: Auto Build | .one donut: Donut settings | .autobuild / .build: builder commands");
               break;
            default:
               Game.message(".one status | donut | keys");
         }
      } catch (LinkageError | Exception failure) {
         Game.message("One: " + failure);
      }
      return true;
   }

   private static void openDonutScreen() throws Exception {
      Object client = Bridge.client();
      if (Bridge.field(client, "field_1724") == null) {
         throw new IllegalStateException("Join a world first.");
      }
      Class<?> unified = Game.type("rex.UnifiedAutoBuild");
      Object config = read(unified, "config");
      Object save = read(unified, "save");
      if (config == null || save == null) {
         throw new IllegalStateException("Donut settings are still initializing.");
      }
      Constructor<?> screen = Game.type("dev.donut.client.gui.DonutScreen").getConstructor(config.getClass(), Runnable.class);
      Game.invoke(client, "method_1507", new String[]{"net.minecraft.class_437"}, screen.newInstance(config, save));
   }

   private static Object read(Class<?> owner, String name) throws Exception {
      Field field = owner.getDeclaredField(name);
      field.setAccessible(true);
      return field.get(null);
   }
}
