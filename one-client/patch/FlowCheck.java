import java.nio.file.Files;
import java.nio.file.Path;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.BasicVerifier;

/** Data-flow check over every patched class: stack shapes and branch targets, no class loading. */
public final class FlowCheck {
    public static void main(String[] args) throws Exception {
        int methods = 0;
        for (Path file : Files.walk(Path.of(args[0])).filter(path -> path.toString().endsWith(".class")).toList()) {
            ClassNode node = new ClassNode();
            new ClassReader(Files.readAllBytes(file)).accept(node, 0);
            for (MethodNode method : node.methods) {
                new Analyzer<BasicValue>(new BasicVerifier()).analyze(node.name, method);
                methods++;
            }
            System.out.println("  ok: " + node.name);
        }
        System.out.println("flow: " + methods + " methods analysed");
    }
}
