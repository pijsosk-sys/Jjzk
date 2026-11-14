import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Some mixins in the base jar store a single-valued {@code at = @At(...)} as a one-element array.
 * Mixin tolerates that but MixinExtras (bundled with Fabric Loader) does not and the game crashes
 * while loading. This rewrites those annotations to the single value they are declared as.
 */
public final class FixMixins {
    private static final Set<String> SINGLE_AT = Set.of(
        "Lorg/spongepowered/asm/mixin/injection/Redirect;",
        "Lorg/spongepowered/asm/mixin/injection/ModifyArg;",
        "Lorg/spongepowered/asm/mixin/injection/ModifyArgs;",
        "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;");

    public static void main(String[] args) throws IOException {
        int fixed = 0;
        try (Stream<Path> files = Files.walk(Path.of(args[0]))) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".class"))::iterator) {
                ClassNode node = new ClassNode();
                try { new ClassReader(Files.readAllBytes(file)).accept(node, 0); } catch (RuntimeException e) { continue; }
                boolean changed = false;
                for (MethodNode method : node.methods) {
                    for (List<AnnotationNode> list : List.of(
                            method.visibleAnnotations == null ? List.<AnnotationNode>of() : method.visibleAnnotations,
                            method.invisibleAnnotations == null ? List.<AnnotationNode>of() : method.invisibleAnnotations)) {
                        for (AnnotationNode annotation : list) {
                            if (!SINGLE_AT.contains(annotation.desc) || annotation.values == null) continue;
                            for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
                                if ("at".equals(annotation.values.get(i)) && annotation.values.get(i + 1) instanceof List<?> many && many.size() == 1) {
                                    annotation.values.set(i + 1, many.get(0));
                                    changed = true;
                                    fixed++;
                                }
                            }
                        }
                    }
                }
                if (changed) {
                    ClassWriter writer = new ClassWriter(0);
                    node.accept(writer);
                    Files.write(file, writer.toByteArray());
                }
            }
        }
        System.out.println("FixMixins: rewrote " + fixed + " annotations");
    }
}
