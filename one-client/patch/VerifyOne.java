import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/**
 * Hosts the patched dispatcher and idle check in a plain class so the JVM verifier accepts or
 * rejects the real instructions, then drives the loop with a counting stand-in for the controller.
 */
public final class VerifyOne {
    static final String SOURCE = "dev/donut/client/autobuild/AutoBuilderController";
    static final String HOST = "TickHostUnderTest";
    static final String CLIENT = "net/minecraft/class_310";

    public static void main(String[] args) throws Exception {
        ClassNode patched = new ClassNode();
        new ClassReader(Files.readAllBytes(Path.of(args[0], SOURCE + ".class"))).accept(patched, 0);
        MethodNode tick = find(patched, "tick");
        MethodNode idle = find(patched, "rex$idle");
        MethodNode waited = find(patched, "rex$waited");
        for (MethodNode method : List.of(tick, idle, waited)) {
            retarget(method);
        }

        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, HOST, null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PUBLIC, "pending", "LPendingStub;", null, null).visitEnd();
        writer.visitField(Opcodes.ACC_PUBLIC, "tick", "I", null, null).visitEnd();
        writer.visitField(Opcodes.ACC_PUBLIC, "prepared", "Ljava/lang/Object;", null, null).visitEnd();
        writer.visitField(Opcodes.ACC_PUBLIC, "steps", "I", null, null).visitEnd();
        writer.visitField(Opcodes.ACC_PUBLIC, "running", "Z", null, null).visitEnd();
        constructor(writer);
        accessor(writer, "isRunning", "()Z", "running", "Z", Opcodes.IRETURN);
        once(writer);
        tick.accept(writer);
        idle.accept(writer);
        waited.accept(writer);
        writer.visitEnd();

        Path out = Path.of(args[1]);
        Files.createDirectories(out);
        Files.write(out.resolve(HOST + ".class"), writer.toByteArray());
        System.out.println("host written");
    }

    static MethodNode find(ClassNode node, String name) {
        for (MethodNode method : node.methods) {
            if (method.name.equals(name)) {
                return method;
            }
        }
        throw new IllegalStateException("Missing " + name);
    }

    /** Same instructions, pointed at the harness instead of Minecraft and the module system. */
    static void retarget(MethodNode method) {
        method.desc = method.desc.replace("L" + CLIENT + ";", "Ljava/lang/Object;");
        method.localVariables = null;
        method.visibleAnnotations = null;
        method.invisibleAnnotations = null;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode call) {
                if (call.owner.equals(SOURCE)) {
                    call.owner = HOST;
                }
                if (call.owner.equals("rex/BuildTempo")) {
                    call.owner = "TempoStub";
                }
                call.desc = call.desc.replace("L" + CLIENT + ";", "Ljava/lang/Object;");
            }
            if (instruction instanceof FieldInsnNode field && field.owner.equals(SOURCE)) {
                field.owner = HOST;
                field.desc = field.name.equals("pending") ? "LPendingStub;" : field.name.equals("tick") ? "I" : "Ljava/lang/Object;";
            }
            if (instruction instanceof FieldInsnNode field && field.owner.endsWith("$Pending")) {
                field.owner = "PendingStub";
            }
            if (instruction instanceof FrameNode frame) {
                replace(frame.local);
                replace(frame.stack);
            }
        }
    }

    static void replace(List<Object> types) {
        if (types != null) {
            for (int index = 0; index < types.size(); index++) {
                if (SOURCE.equals(types.get(index))) {
                    types.set(index, HOST);
                } else if (CLIENT.equals(types.get(index))) {
                    types.set(index, "java/lang/Object");
                }
            }
        }
    }

    static void constructor(ClassWriter writer) {
        var init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(1, 1);
        init.visitEnd();
    }

    static void accessor(ClassWriter writer, String name, String desc, String field, String type, int ret) {
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC, name, desc, null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitFieldInsn(Opcodes.GETFIELD, HOST, field, type);
        method.visitInsn(ret);
        method.visitMaxs(1, 1);
        method.visitEnd();
    }

    /** Counts the steps the dispatcher takes and clears the queue after the first placement. */
    static void once(ClassWriter writer) {
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC, "rex$tickOnce", "(Ljava/lang/Object;)V", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "TempoStub", "step", "(Ljava/lang/Object;)V", false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(2, 2);
        method.visitEnd();
    }
}
