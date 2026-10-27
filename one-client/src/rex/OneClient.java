package rex;

import dev.donut.client.config.ClientConfig;
import dev.krypton.core.module.Category;
import dev.krypton.core.module.Module;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.api.ClientModInitializer;

/**
 * One entry point for what used to be six, and Krypton as the only client surface: every Donut
 * keybind is unbound, its title-screen button is gone, its HUD is a module that starts off, and the
 * features it alone had are Krypton module rows. Right Shift opens the one menu; the rest is chat.
 */
public final class OneClient implements ClientModInitializer {
   public static final String VERSION = "1.0.0-one.4";
   private static final String[] PARTS = {
      "dev.donut.client.DonutClient",
      "dev.krypton.client.KryptonClient",
      "rex.HalloweenResources",
      "rex.RexBootstrap",
      "rex.XrayBootstrap",
      "rex.LayerBuild"
   };
   private static final Map<String, String> parts = new LinkedHashMap<>();
   private static boolean quietened;

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
      quieten();
      System.out.println("[ONE] REX One " + VERSION + ": " + ready() + "/" + PARTS.length + " subsystems; Right Shift opens the menu");
   }

   /** The legacy HUD draws the same information Krypton's does, so it starts off, once. */
   private static void quieten() {
      ClientConfig config = donutConfig();
      if (config != null && config.showHud && !quietened) {
         quietened = true;
         config.showHud = false;
         saveDonutConfig();
      }
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

   /** No Donut keybind survives the merge; its features are module rows and chat commands now. */
   public static int binding(String action, int key) {
      return -1;
   }

   public static ClientConfig donutConfig() {
      try {
         return (ClientConfig)stat("rex.UnifiedAutoBuild", "config");
      } catch (LinkageError | Exception unavailable) {
         return null;
      }
   }

   public static void saveDonutConfig() {
      try {
         Runnable save = (Runnable)stat("rex.UnifiedAutoBuild", "save");
         if (save != null) {
            save.run();
         }
      } catch (LinkageError | Exception unavailable) {
      }
   }

   private static Object stat(String owner, String name) throws Exception {
      Field field = Game.type(owner).getDeclaredField(name);
      field.setAccessible(true);
      return field.get(null);
   }

   public static boolean command(String line) {
      String[] words = line.strip().split("\\s+", 4);
      if (words.length == 0 || !words[0].equalsIgnoreCase(".one")) {
         return false;
      }
      String action = words.length > 1 ? words[1].toLowerCase(Locale.ROOT) : "status";
      String argument = words.length > 2 ? words[2] : "";
      String extra = words.length > 3 ? words[3] : "";
      try {
         switch (action) {
            case "status":
               Game.message("REX One " + VERSION + " | " + ready() + "/" + PARTS.length + " subsystems | " + BuildTempo.describe());
               Game.message("  " + Duper.status());
               for (Map.Entry<String, String> part : parts.entrySet()) {
                  if (!part.getValue().equals("ok")) {
                     Game.message("  " + part.getKey() + ": " + part.getValue());
                  }
               }
               Game.message("  " + Modules.count() + " modules registered");
               for (Map.Entry<String, String> broken : Modules.failures().entrySet()) {
                  Game.message("  " + broken.getKey() + ": " + broken.getValue());
               }
               break;
            case "donut":
            case "settings":
               openDonutScreen();
               break;
            case "list":
               list((argument + " " + extra).strip());
               break;
            case "dupe":
            case "duper":
               Duper.chat(argument, extra);
               break;
            case "mats":
            case "materials":
               materials();
               break;
            case "toggle":
            case "on":
            case "off":
               switchModule(action, (argument + " " + extra).strip());
               break;
            case "config":
               config(argument, extra);
               break;
            case "keys":
               Game.message("Right Shift: the menu. Nothing else is bound.");
               Game.message(".one list [category|text] | toggle <module> | on/off <module> | mats | dupe [cmd <text>] | config save <id> [name] | config load <id> | donut | status");
               Game.message(".autobuild / .build: setup | start | pause | resume | stop | status | list | creative on/off");
               break;
            default:
               Game.message(".one status | list | toggle <module> | on/off <module> | mats | dupe | config | donut | keys");
         }
      } catch (LinkageError | Exception failure) {
         Game.message("One: " + failure.getMessage());
      }
      return true;
   }

   private static List<Module> modules() throws Exception {
      Object instance = Game.type("dev.krypton.client.KryptonClient").getField("instance").get(null);
      Object manager = Bridge.call(instance, "modules");
      return new ArrayList<>((List<Module>)Bridge.call(manager, "all"));
   }

   private static void list(String wanted) throws Exception {
      Category category = null;
      for (Category candidate : Category.values()) {
         if (!wanted.isEmpty() && candidate.name().equalsIgnoreCase(wanted)) {
            category = candidate;
         }
      }
      String filter = category == null ? wanted.toLowerCase(Locale.ROOT) : "";
      StringBuilder line = new StringBuilder();
      int count = 0;
      for (Module module : modules()) {
         if (category != null && module.category != category) {
            continue;
         }
         if (!filter.isEmpty() && !module.name.toLowerCase(Locale.ROOT).contains(filter)) {
            continue;
         }
         if (line.length() > 180) {
            Game.message(line.toString());
            line.setLength(0);
         }
         line.append(line.length() == 0 ? "" : ", ").append(module.enabled ? "[" + module.name + "]" : module.name);
         count++;
      }
      if (line.length() > 0) {
         Game.message(line.toString());
      }
      if (count == 0) {
         Game.message("Nothing matched. Categories: COMBAT MISC DONUT BASEFINDING RENDER CLIENT");
         return;
      }
      Game.message(
         count + " modules" + (category == null ? "" : " in " + category.name()) + (filter.isEmpty() ? "" : " matching " + filter)
            + "; enabled ones are bracketed."
      );
   }

   private static void switchModule(String action, String wanted) throws Exception {
      if (wanted.isEmpty()) {
         Game.message("Name a module: .one " + action + " <module>");
         return;
      }
      Module found = null;
      for (Module module : modules()) {
         if (module.name.equalsIgnoreCase(wanted)) {
            found = module;
            break;
         }
         if (found == null && module.name.toLowerCase(Locale.ROOT).startsWith(wanted.toLowerCase(Locale.ROOT))) {
            found = module;
         }
      }
      if (found == null) {
         Game.message("No module called " + wanted + ". Try .one list");
         return;
      }
      boolean value = action.equals("toggle") ? !found.enabled : action.equals("on");
      ModuleLifecycle.set(found, value);
      Game.message(found.name + ": " + (value ? "on" : "off"));
   }

   private static void config(String action, String rest) throws Exception {
      Object instance = Game.type("dev.krypton.client.KryptonClient").getField("instance").get(null);
      Object manager = Game.type("dev.krypton.client.KryptonClient").getField("configManager").get(instance);
      String[] words = rest.strip().split("\\s+", 2);
      switch (action.toLowerCase(Locale.ROOT)) {
         case "save":
            Game.invoke(manager, "save", new String[]{"int", "java.lang.String"}, number(words[0]), words.length > 1 ? words[1] : null);
            break;
         case "load":
            Game.invoke(manager, "load", new String[]{"int"}, number(words[0]));
            break;
         case "list":
            Bridge.call(manager, "listMine");
            break;
         case "flush":
            Bridge.call(manager, "saveActive");
            Game.message("Settings written.");
            break;
         default:
            Game.message(".one config save <id> [name] | load <id> | list | flush");
      }
   }

   private static int number(String text) {
      try {
         return Integer.parseInt(text.strip());
      } catch (NumberFormatException wrong) {
         throw new IllegalArgumentException("give a config id, a whole number");
      }
   }

   /** What the loaded blueprint still needs, before it stops mid-build on the first missing stack. */
   private static void materials() throws Exception {
      Object controller = Game.invoke(Game.type("dev.donut.client.autobuild.AutoBuilderController"), "get", new String[0]);
      Object schematic = Bridge.call(controller, "schematic");
      if (schematic == null) {
         throw new IllegalStateException("Load a schematic first: .autobuild setup");
      }
      if (Bridge.field(Bridge.client(), "field_1687") == null) {
         throw new IllegalStateException("Join a world first.");
      }
      Object snapshot = Game.invoke(
         Game.type("dev.donut.client.autobuild.MaterialTracker"),
         "snapshot",
         new String[]{"net.minecraft.class_310", "dev.donut.client.autobuild.Schematic", "dev.donut.client.autobuild.PlacementTransform"},
         Bridge.client(),
         schematic,
         Bridge.call(controller, "transform")
      );
      Game.message(
         Bridge.call(schematic, "name") + ": " + number(snapshot, "correctBlocks") + "/" + number(snapshot, "totalBlocks") + " placed, "
            + number(snapshot, "remainingBlocks") + " to go, " + number(snapshot, "conflicts") + " conflicts, "
            + number(snapshot, "unknownBlocks") + " unplaceable"
      );
      int shown = 0;
      int short_ = 0;
      for (Object row : (List<?>)Bridge.call(snapshot, "rows")) {
         int missing = number(row, "missing");
         if (missing <= 0) {
            continue;
         }
         short_++;
         if (shown < 12) {
            shown++;
            Game.message(
               "  need " + missing + " more " + Bridge.call(row, "name") + " (have " + (number(row, "hotbar") + number(row, "inventory"))
                  + ", want " + number(row, "remaining") + ")"
            );
         }
      }
      if (short_ == 0) {
         Game.message("  every remaining block is covered by what you are carrying.");
      } else if (short_ > shown) {
         Game.message("  and " + (short_ - shown) + " more items short.");
      }
   }

   private static int number(Object record, String component) throws Exception {
      return ((Number)Bridge.call(record, component)).intValue();
   }

   private static void openDonutScreen() throws Exception {
      Object client = Bridge.client();
      if (Bridge.field(client, "field_1724") == null) {
         throw new IllegalStateException("Join a world first.");
      }
      ClientConfig config = donutConfig();
      Object save = stat("rex.UnifiedAutoBuild", "save");
      if (config == null || save == null) {
         throw new IllegalStateException("Donut settings are still initializing.");
      }
      Constructor<?> screen = Game.type("dev.donut.client.gui.DonutScreen").getConstructor(ClientConfig.class, Runnable.class);
      Game.invoke(client, "method_1507", new String[]{"net.minecraft.class_437"}, screen.newInstance(config, save));
   }
}
