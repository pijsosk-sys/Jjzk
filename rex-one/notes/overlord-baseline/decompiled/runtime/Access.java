package com.roothack.overlord.runtime;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Access {
   private static final Map<String, Method> methods = new ConcurrentHashMap<>();
   private static final Map<String, Class<?>> types = new ConcurrentHashMap<>();

   public Access() {
   }

   public static Class<?> type(String var0) throws Exception {
      Class var1 = types.get(var0);
      if (var1 == null) {
         var1 = Class.forName(var0);
         types.put(var0, var1);
      }

      return var1;
   }

   public static Object field(Object var0, String var1) throws Exception {
      Class var2 = var0 instanceof Class ? (Class)var0 : var0.getClass();

      for (Class var3 = var2; var3 != null; var3 = var3.getSuperclass()) {
         try {
            Field var4 = var3.getDeclaredField(var1);
            var4.setAccessible(true);
            return var4.get(var0 instanceof Class ? null : var0);
         } catch (NoSuchFieldException var5) {
         }
      }

      throw new NoSuchFieldException(var2 + "." + var1);
   }

   public static Object call(Object var0, String var1, Object... var2) throws Exception {
      Class var3 = var0 instanceof Class ? (Class)var0 : var0.getClass();
      String var4 = var3.getName() + var1 + Arrays.toString(Arrays.stream(var2).map(var0x -> var0x == null ? null : var0x.getClass().getName()).toArray());
      Method var5 = methods.get(var4);
      if (var5 == null) {
         label54:
         for (Method var9 : var3.getMethods()) {
            if (var9.getName().equals(var1) && var9.getParameterCount() == var2.length) {
               Class[] var10 = var9.getParameterTypes();

               for (int var11 = 0; var11 < var10.length; var11++) {
                  if (var2[var11] != null && !box(var10[var11]).isInstance(var2[var11])) {
                     continue label54;
                  }
               }

               var5 = var9;
               break;
            }
         }

         if (var5 == null) {
            throw new NoSuchMethodException(var4);
         }

         var5.trySetAccessible();
         methods.put(var4, var5);
      }

      try {
         return var5.invoke(var0 instanceof Class ? null : var0, var2);
      } catch (InvocationTargetException var12) {
         throw new Exception(var1 + ": " + var12.getCause(), var12.getCause());
      }
   }

   private static Class<?> box(Class<?> var0) {
      if (!var0.isPrimitive()) {
         return var0;
      } else if (var0 == int.class) {
         return Integer.class;
      } else if (var0 == boolean.class) {
         return Boolean.class;
      } else if (var0 == float.class) {
         return Float.class;
      } else if (var0 == double.class) {
         return Double.class;
      } else {
         return var0 == long.class ? Long.class : var0;
      }
   }

   public static Object make(String var0, Object... var1) throws Exception {
      label30:
      for (Constructor var5 : type(var0).getConstructors()) {
         if (var5.getParameterCount() == var1.length) {
            Class[] var6 = var5.getParameterTypes();

            for (int var7 = 0; var7 < var6.length; var7++) {
               if (var1[var7] != null && !box(var6[var7]).isInstance(var1[var7])) {
                  continue label30;
               }
            }

            return var5.newInstance(var1);
         }
      }

      throw new NoSuchMethodException(var0);
   }

   public static Object mc() throws Exception {
      return call(type("net.minecraft.class_310"), "method_1551");
   }

   public static Object player() throws Exception {
      return field(mc(), "field_1724");
   }

   public static Object world() throws Exception {
      return field(mc(), "field_1687");
   }

   public static int num(Object var0, String var1) throws Exception {
      return ((Number)call(var0, var1)).intValue();
   }

   public static double real(Object var0, String var1, Object... var2) throws Exception {
      return ((Number)call(var0, var1, var2)).doubleValue();
   }

   public static Object pos(int var0, int var1, int var2) throws Exception {
      return make("net.minecraft.class_2338", var0, var1, var2);
   }

   public static Object state(int var0, int var1, int var2) throws Exception {
      return call(world(), "method_8320", pos(var0, var1, var2));
   }

   public static String id(Object var0) throws Exception {
      return call(var0, "method_7960")
         ? "air"
         : call(field(type("net.minecraft.class_7923"), "field_41178"), "method_10221", call(var0, "method_7909")).toString().replace("minecraft:", "");
   }

   public static String block(int var0, int var1, int var2) throws Exception {
      return call(field(type("net.minecraft.class_7923"), "field_41175"), "method_10221", call(state(var0, var1, var2), "method_26204"))
         .toString()
         .replace("minecraft:", "");
   }

   public static boolean air(int var0, int var1, int var2) throws Exception {
      return (Boolean)call(state(var0, var1, var2), "method_45474");
   }

   public static String dimension() throws Exception {
      return call(call(world(), "method_27983"), "method_29177").toString();
   }

   public static Object inv() throws Exception {
      return call(player(), "method_31548");
   }

   public static Map<String, Integer> counts() throws Exception {
      HashMap var0 = new HashMap();
      Object var1 = inv();

      for (int var2 = 0; var2 < 36; var2++) {
         Object var3 = call(var1, "method_5438", var2);
         if (!(Boolean)call(var3, "method_7960")) {
            var0.merge(id(var3), num(var3, "method_7947"), Integer::sum);
         }
      }

      Object var5 = field(player(), "field_7498");

      for (int var6 = 5; var6 <= 8; var6++) {
         Object var4 = call(call(var5, "method_7611", var6), "method_7677");
         if (!(Boolean)call(var4, "method_7960")) {
            var0.merge(id(var4), num(var4, "method_7947"), Integer::sum);
         }
      }

      return var0;
   }

   public static boolean enchanted(String var0) throws Exception {
      for (int var1 = 0; var1 < 36; var1++) {
         Object var2 = call(inv(), "method_5438", var1);
         if (id(var2).equals(var0) && (Boolean)call(var2, "method_7942")) {
            return true;
         }
      }

      Object var4 = field(player(), "field_7498");

      for (int var5 = 5; var5 <= 8; var5++) {
         Object var3 = call(call(var4, "method_7611", var5), "method_7677");
         if (id(var3).equals(var0) && (Boolean)call(var3, "method_7942")) {
            return true;
         }
      }

      return false;
   }

   public static void close() throws Exception {
      if (field(mc(), "field_1755") != null) {
         call(player(), "method_7346");
      }
   }

   public static void message(String var0) {
      try {
         call(player(), "method_7353", call(type("net.minecraft.class_2561"), "method_43470", "[Overlord] " + var0), false);
      } catch (Exception var2) {
         System.out.println("[Overlord] " + var0);
      }
   }

   public static Object command(String var0, Object var1) throws Exception {
      return make("baritone.api.process.PathingCommand", var1, field(type("baritone.api.process.PathingCommandType"), var0));
   }
}
