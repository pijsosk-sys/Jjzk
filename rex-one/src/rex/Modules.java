package rex;

import dev.krypton.core.managers.ModuleManager;
import dev.krypton.core.module.Module;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Guarded module registration. The generated registerAll was one long sequence of constructor calls,
 * so a module that threw in its constructor took every module after it off the menu with no trace.
 * Each row now stands on its own and a failure is reported by name.
 */
public final class Modules {
   private static final Map<String, String> failures = new LinkedHashMap<>();
   private static int registered;
   private static Method register;

   /** This build's own modules, registered after the last row the generated registerAll names. */
   private static final String[] EXTRAS = {"rex.SchematicGhost", "rex.TuffShader"};
   private static final String LAST = "rex.BuildHud";

   public static void add(ModuleManager manager, String className) {
      addOne(manager, className);
      if (LAST.equals(className)) {
         for (String extra : EXTRAS) {
            addOne(manager, extra);
         }
      }
   }

   private static void addOne(ModuleManager manager, String className) {
      try {
         Constructor<?> constructor = Class.forName(className).getDeclaredConstructor();
         constructor.setAccessible(true);
         Module module = (Module)constructor.newInstance();
         if (register == null) {
            register = ModuleManager.class.getDeclaredMethod("register", Module.class);
            register.setAccessible(true);
         }
         register.invoke(manager, module);
         registered++;
         // A module that ships enabled never had onEnable called, so its handlers were never on the
         // bus. The config pass that follows turns it off again if that is what was saved.
         if (module.enabled) {
            module.onEnable();
         }
      } catch (Throwable failure) {
         Throwable cause = failure instanceof InvocationTargetException && failure.getCause() != null ? failure.getCause() : failure;
         failures.put(className, cause.toString());
         System.err.println("[ONE] module " + className + " did not load: " + cause);
      }
   }

   public static int count() {
      return registered;
   }

   public static Map<String, String> failures() {
      return failures;
   }
}
