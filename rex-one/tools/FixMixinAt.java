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
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Mixin injectors whose {@code at} is a single {@code @At} (Redirect, ModifyArg, ModifyArgs,
 * ModifyVariable) must not carry it as an array. Mixin itself tolerates the array, but MixinExtras,
 * which Fabric Loader bundles, casts the value straight to an annotation and crashes the game while
 * it loads the target class. This rewrites a one-element array to the plain value, in place.
 * Usage: FixMixinAt <jar>
 */
public final class FixMixinAt {
   private static final List<String> SINGLE_AT = List.of(
      "Lorg/spongepowered/asm/mixin/injection/Redirect;",
      "Lorg/spongepowered/asm/mixin/injection/ModifyArg;",
      "Lorg/spongepowered/asm/mixin/injection/ModifyArgs;",
      "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;"
   );

   public static void main(String[] args) throws Exception {
      List<String> fixed = new ArrayList<>();
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
               if (method.visibleAnnotations == null) {
                  continue;
               }
               for (AnnotationNode annotation : method.visibleAnnotations) {
                  if (!SINGLE_AT.contains(annotation.desc) || annotation.values == null) {
                     continue;
                  }
                  for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
                     if ("at".equals(annotation.values.get(i)) && annotation.values.get(i + 1) instanceof List<?> list) {
                        if (list.size() != 1) {
                           throw new IllegalStateException(node.name + "." + method.name + " has " + list.size() + " @At values for a single-@At injector");
                        }
                        annotation.values.set(i + 1, list.get(0));
                        changed = true;
                        fixed.add(node.name + "." + method.name);
                     }
                  }
               }
            }
            if (changed) {
               ClassWriter writer = new ClassWriter(0);
               node.accept(writer);
               try (OutputStream out = Files.newOutputStream(path)) {
                  out.write(writer.toByteArray());
               }
            }
         }
      }
      System.out.println("mixin @At fix: " + (fixed.isEmpty() ? "nothing to fix" : String.join(", ", fixed)));
   }
}
