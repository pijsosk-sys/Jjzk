import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Every class in the built jar that calls into a replaced class must still find the method or
 * field it names, with the same descriptor. A mismatch here is a NoSuchMethodError in game.
 * Usage: LinkCheck <jar> <replaced class>...
 */
public final class LinkCheck {
   public static void main(String[] args) throws Exception {
      Map<String, ClassNode> classes = new HashMap<>();
      try (ZipFile jar = new ZipFile(args[0])) {
         for (Enumeration<? extends ZipEntry> e = jar.entries(); e.hasMoreElements(); ) {
            ZipEntry entry = e.nextElement();
            if (entry.getName().endsWith(".class") && !entry.getName().startsWith("META-INF/")) {
               try (InputStream in = jar.getInputStream(entry)) {
                  ClassNode node = new ClassNode();
                  new ClassReader(in.readAllBytes()).accept(node, ClassReader.SKIP_FRAMES);
                  classes.put(node.name, node);
               }
            }
         }
      }

      Set<String> replaced = new HashSet<>();
      for (int i = 1; i < args.length; i++) {
         replaced.add(args[i]);
      }

      List<String> problems = new ArrayList<>();
      int checked = 0;
      for (ClassNode node : classes.values()) {
         for (MethodNode method : node.methods) {
            for (AbstractInsnNode insn : method.instructions) {
               if (insn instanceof MethodInsnNode call && covered(replaced, call.owner)) {
                  checked++;
                  if (!hasMethod(classes, call.owner, call.name, call.desc)) {
                     problems.add(node.name + "." + method.name + " -> " + call.owner + "." + call.name + call.desc);
                  }
               } else if (insn instanceof FieldInsnNode field && covered(replaced, field.owner)) {
                  checked++;
                  if (!hasField(classes, field.owner, field.name, field.desc)) {
                     problems.add(node.name + "." + method.name + " -> " + field.owner + "." + field.name + ":" + field.desc);
                  }
               } else if (insn instanceof InvokeDynamicInsnNode indy) {
                  for (Object arg : indy.bsmArgs) {
                     if (arg instanceof Handle handle && covered(replaced, handle.getOwner())) {
                        checked++;
                        boolean ok = handle.getTag() <= 4
                           ? hasField(classes, handle.getOwner(), handle.getName(), handle.getDesc())
                           : hasMethod(classes, handle.getOwner(), handle.getName(), handle.getDesc());
                        if (!ok) {
                           problems.add(node.name + "." + method.name + " -> " + handle.getOwner() + "." + handle.getName() + handle.getDesc());
                        }
                     }
                  }
               }
            }
         }
      }

      for (String name : replaced) {
         if (!classes.containsKey(name)) {
            problems.add("missing class " + name);
         }
      }

      if (!problems.isEmpty()) {
         problems.forEach(problem -> System.out.println("UNRESOLVED " + problem));
         System.exit(1);
      }

      System.out.println("link check: " + checked + " references into " + replaced.size() + " replaced classes resolve");
   }

   private static boolean covered(Set<String> replaced, String owner) {
      int inner = owner.indexOf('$');
      return replaced.contains(inner < 0 ? owner : owner.substring(0, inner));
   }

   private static boolean hasMethod(Map<String, ClassNode> classes, String owner, String name, String desc) {
      for (ClassNode node = classes.get(owner); node != null; node = classes.get(node.superName)) {
         for (MethodNode method : node.methods) {
            if (method.name.equals(name) && method.desc.equals(desc)) {
               return true;
            }
         }
         // Inherited from outside the jar (Object, Record, Enum, Minecraft): accept.
         if (!classes.containsKey(node.superName)) {
            return node.superName != null && !node.superName.startsWith("dev/") && !node.superName.startsWith("rex/");
         }
      }
      return false;
   }

   private static boolean hasField(Map<String, ClassNode> classes, String owner, String name, String desc) {
      for (ClassNode node = classes.get(owner); node != null; node = classes.get(node.superName)) {
         for (FieldNode field : node.fields) {
            if (field.name.equals(name) && field.desc.equals(desc)) {
               return true;
            }
         }
         if (!classes.containsKey(node.superName)) {
            return node.superName != null && !node.superName.startsWith("dev/") && !node.superName.startsWith("rex/");
         }
      }
      return false;
   }
}
