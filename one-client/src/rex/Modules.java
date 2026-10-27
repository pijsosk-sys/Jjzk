package rex;

import dev.krypton.core.managers.ModuleManager;
import dev.krypton.core.module.Module;
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

   public static void add(ModuleManager manager, String className) {
      try {
         Module module = (Module)Class.forName(className).getDeclaredConstructor().newInstance();
         if (register == null) {
            register = ModuleManager.class.getDeclaredMethod("register", Module.class);
            register.setAccessible(true);
         }
         register.invoke(manager, module);
         registered++;
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
