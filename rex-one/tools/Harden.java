import java.io.OutputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * A Minecraft client has no business starting other programs on the player's computer. The audit
 * in SECURITY.md found one place that did (Spotify HUD ran PowerShell with the execution policy
 * bypassed, or osascript, every few seconds to read the playing song). This empties every method in
 * the jar that starts a process, so it simply returns, and fails the build if it finds one it
 * cannot empty safely. Nested library jars under META-INF/jars are left alone.
 * Usage: Harden <jar>
 */
public final class Harden {
   public static void main(String[] args) throws Exception {
      List<String> emptied = new ArrayList<>();
      try (FileSystem jar = FileSystems.newFileSystem(URI.create("jar:" + Path.of(args[0]).toUri()), Map.of())) {
         List<Path> classes;
         try (Stream<Path> walk = Files.walk(jar.getPath("/"))) {
            classes = walk.filter(p -> p.toString().endsWith(".class") && !p.toString().startsWith("/META-INF/")).toList();
         }
         for (Path path : classes) {
            byte[] bytes = Files.readAllBytes(path);
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            boolean changed = false;
            for (MethodNode method : node.methods) {
               if (method.instructions == null || !startsProcess(method)) {
                  continue;
               }
               if ((method.access & Opcodes.ACC_ABSTRACT) != 0 || "<init>".equals(method.name) || "<clinit>".equals(method.name)) {
                  throw new IllegalStateException(node.name + "." + method.name + " starts a process and cannot be emptied safely");
               }
               method.instructions = defaultReturn(Type.getReturnType(method.desc));
               method.tryCatchBlocks.clear();
               if (method.localVariables != null) {
                  method.localVariables.clear();
               }
               method.visibleLocalVariableAnnotations = null;
               method.invisibleLocalVariableAnnotations = null;
               changed = true;
               emptied.add(node.name + "." + method.name + method.desc);
            }
            if (changed) {
               ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
               node.accept(writer);
               try (OutputStream out = Files.newOutputStream(path)) {
                  out.write(writer.toByteArray());
               }
            }
         }
      }
      System.out.println("harden: " + (emptied.isEmpty() ? "nothing starts a process" : "emptied " + String.join(", ", emptied)));
   }

   private static boolean startsProcess(MethodNode method) {
      for (AbstractInsnNode insn : method.instructions) {
         if (insn instanceof MethodInsnNode call
            && ("java/lang/ProcessBuilder".equals(call.owner) && "start".equals(call.name)
               || "java/lang/Runtime".equals(call.owner) && "exec".equals(call.name))) {
            return true;
         }
      }
      return false;
   }

   private static InsnList defaultReturn(Type type) {
      InsnList list = new InsnList();
      switch (type.getSort()) {
         case Type.VOID -> list.add(new InsnNode(Opcodes.RETURN));
         case Type.BOOLEAN, Type.CHAR, Type.BYTE, Type.SHORT, Type.INT -> {
            list.add(new InsnNode(Opcodes.ICONST_0));
            list.add(new InsnNode(Opcodes.IRETURN));
         }
         case Type.LONG -> {
            list.add(new InsnNode(Opcodes.LCONST_0));
            list.add(new InsnNode(Opcodes.LRETURN));
         }
         case Type.FLOAT -> {
            list.add(new InsnNode(Opcodes.FCONST_0));
            list.add(new InsnNode(Opcodes.FRETURN));
         }
         case Type.DOUBLE -> {
            list.add(new InsnNode(Opcodes.DCONST_0));
            list.add(new InsnNode(Opcodes.DRETURN));
         }
         default -> {
            list.add(new InsnNode(Opcodes.ACONST_NULL));
            list.add(new InsnNode(Opcodes.ARETURN));
         }
      }
      return list;
   }
}
