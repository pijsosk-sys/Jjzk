import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;
import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Label;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.tree.*;

/** Bytecode side of the merge: one entry point, one menu key, one command namespace, paced builder. */
public final class OnePatch {
    static final String CONTROLLER = "dev/donut/client/autobuild/AutoBuilderController";
    static final String TEMPO = "rex/BuildTempo";
    static final String MC_CLIENT = "net/minecraft/class_310";
    static final String MODULE = "dev/krypton/core/module/Module";

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[1]);
        try (ZipFile jar = new ZipFile(args[0])) {
            write(output, CONTROLLER, controller(read(jar, CONTROLLER)));
            write(output, "rex/UnifiedAutoBuild", binding(read(jar, "rex/UnifiedAutoBuild")));
            write(output, "dev/krypton/core/managers/ModuleManager", modules(read(jar, "dev/krypton/core/managers/ModuleManager")));
            write(output, "rex/RexCommands", commands(read(jar, "rex/RexCommands")));
            write(output, "dev/donut/client/DonutClient", titleScreen(read(jar, "dev/donut/client/DonutClient")));
        }
    }

    static ClassNode read(ZipFile jar, String name) throws Exception {
        byte[] bytes = jar.getInputStream(jar.getEntry(name + ".class")).readAllBytes();
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    static void write(Path output, String name, ClassNode node) throws Exception {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        Path path = output.resolve(name + ".class");
        Files.createDirectories(path.getParent());
        Files.write(path, writer.toByteArray());
        System.out.println(name + ": patched");
    }

    static MethodNode method(ClassNode node, String name, String descriptor) {
        for (MethodNode candidate : node.methods) {
            if (candidate.name.equals(name) && (descriptor == null || candidate.desc.equals(descriptor))) {
                return candidate;
            }
        }
        throw new IllegalStateException("Missing " + node.name + "." + name);
    }

    static String fieldDescriptor(ClassNode node, String name) {
        for (FieldNode field : node.fields) {
            if (field.name.equals(name)) {
                return field.desc;
            }
        }
        throw new IllegalStateException("Missing field " + node.name + "." + name);
    }

    static MethodInsnNode tempo(String name) {
        return new MethodInsnNode(Opcodes.INVOKESTATIC, TEMPO, name, "()I", false);
    }

    /** Replaces a constant push with a call that returns the same int. */
    static void replace(MethodNode method, AbstractInsnNode constant, String supplier, List<String> changes, String note) {
        method.instructions.set(constant, tempo(supplier));
        changes.add(note);
    }

    static ClassNode controller(ClassNode node) {
        List<String> changes = new ArrayList<>();
        MethodNode tick = method(node, "tick", "(L" + MC_CLIENT + ";)V");
        tick.name = "rex$tickOnce";
        tick.access = (tick.access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC;
        changes.add("one placement step is callable");

        AbstractInsnNode ack = null;
        AbstractInsnNode arm = null;
        AbstractInsnNode swap = null;
        for (AbstractInsnNode instruction : tick.instructions.toArray()) {
            AbstractInsnNode next = instruction.getNext();
            AbstractInsnNode previous = instruction.getPrevious();
            if (instruction.getOpcode() == Opcodes.BIPUSH && ((IntInsnNode)instruction).operand == 60) {
                if (ack != null) {
                    throw new IllegalStateException("Two acknowledgement windows");
                }
                ack = instruction;
            }
            if (instruction.getOpcode() == Opcodes.ICONST_2 && next != null && next.getOpcode() == Opcodes.IADD
                    && previous instanceof FieldInsnNode field && field.name.equals("tick")) {
                if (arm != null) {
                    throw new IllegalStateException("Two arm delays");
                }
                arm = instruction;
            }
            if (instruction.getOpcode() == Opcodes.ICONST_2 && next instanceof FieldInsnNode field
                    && field.getOpcode() == Opcodes.PUTFIELD && field.name.equals("cooldown")) {
                if (swap != null) {
                    throw new IllegalStateException("Two swap cooldowns");
                }
                swap = instruction;
            }
        }
        if (ack == null || arm == null || swap == null) {
            throw new IllegalStateException("Builder cadence constants not found");
        }
        replace(tick, ack, "ack", changes, "acknowledgement window");
        replace(tick, arm, "arm", changes, "arm delay");
        replace(tick, swap, "swap", changes, "hotbar swap cooldown");

        MethodNode choose = method(node, "chooseTarget", null);
        AbstractInsnNode examined = null;
        AbstractInsnNode window = null;
        for (AbstractInsnNode instruction : choose.instructions.toArray()) {
            AbstractInsnNode next = instruction.getNext();
            if (instruction.getOpcode() == Opcodes.SIPUSH && ((IntInsnNode)instruction).operand == 256) {
                if (examined != null) {
                    throw new IllegalStateException("Two candidate caps");
                }
                examined = instruction;
            }
            if (instruction.getOpcode() == Opcodes.ICONST_1 && next != null && next.getOpcode() == Opcodes.IADD) {
                if (window != null) {
                    throw new IllegalStateException("Two layer windows");
                }
                window = instruction;
            }
        }
        if (examined == null || window == null) {
            throw new IllegalStateException("Target selection constants not found");
        }
        replace(choose, examined, "candidates", changes, "candidates examined");
        replace(choose, window, "layers", changes, "layer window");

        node.methods.add(idle(node));
        node.methods.add(driver());
        changes.add("paced dispatcher");
        node.methods.add(waited(node, tick));
        earlyFinish(tick, changes);
        System.out.println(node.name + ": " + String.join(", ", changes));
        if (changes.size() != 8) {
            throw new IllegalStateException("Builder patches: " + changes);
        }
        return node;
    }

    /** Ticks since the placement in flight was sent, or zero when nothing is in flight. */
    static MethodNode waited(ClassNode node, MethodNode tick) {
        FieldInsnNode sent = null;
        for (AbstractInsnNode instruction : tick.instructions.toArray()) {
            if (instruction instanceof FieldInsnNode field && field.name.equals("sentTick")) {
                sent = field;
                break;
            }
        }
        if (sent == null) {
            throw new IllegalStateException("No sentTick read");
        }
        MethodNode waited = new MethodNode(Opcodes.ACC_PUBLIC, "rex$waited", "()I", null, null);
        InsnList code = waited.instructions;
        LabelNode flight = new LabelNode(new Label());
        String pending = fieldDescriptor(node, "pending");
        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, "pending", pending));
        code.add(new JumpInsnNode(Opcodes.IFNONNULL, flight));
        code.add(new InsnNode(Opcodes.ICONST_0));
        code.add(new InsnNode(Opcodes.IRETURN));
        code.add(flight);
        code.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, "tick", "I"));
        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, "pending", pending));
        code.add(new FieldInsnNode(Opcodes.GETFIELD, sent.owner, "sentTick", "I"));
        code.add(new InsnNode(Opcodes.ISUB));
        code.add(new InsnNode(Opcodes.IRETURN));
        return waited;
    }

    /** Finishing on the client's own prediction is an extra way into the same branch. */
    static void earlyFinish(MethodNode tick, List<String> changes) {
        for (AbstractInsnNode instruction : tick.instructions.toArray()) {
            if (!(instruction instanceof FieldInsnNode field) || !field.name.equals("acknowledged")) {
                continue;
            }
            AbstractInsnNode read = field.getPrevious();
            AbstractInsnNode load = read == null ? null : read.getPrevious();
            if (!(field.getNext() instanceof JumpInsnNode branch) || branch.getOpcode() != Opcodes.IFNE
                    || load == null || load.getOpcode() != Opcodes.ALOAD) {
                throw new IllegalStateException("Unexpected acknowledgement branch");
            }
            InsnList head = new InsnList();
            head.add(new VarInsnNode(Opcodes.ALOAD, 0));
            head.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CONTROLLER, "rex$waited", "()I", false));
            head.add(new MethodInsnNode(Opcodes.INVOKESTATIC, TEMPO, "earlyFinish", "(I)Z", false));
            head.add(new JumpInsnNode(Opcodes.IFNE, branch.label));
            tick.instructions.insertBefore(load, head);
            changes.add("optional prediction finish");
            return;
        }
        throw new IllegalStateException("No acknowledgement branch");
    }

    /** True while no placement is armed or waiting, so extra steps only advance the queue. */
    static MethodNode idle(ClassNode node) {
        MethodNode idle = new MethodNode(Opcodes.ACC_PUBLIC, "rex$idle", "()Z", null, null);
        InsnList code = idle.instructions;
        Label busy = new Label();
        LabelNode busyNode = new LabelNode(busy);
        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, "pending", fieldDescriptor(node, "pending")));
        code.add(new JumpInsnNode(Opcodes.IFNONNULL, busyNode));
        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, "prepared", fieldDescriptor(node, "prepared")));
        code.add(new JumpInsnNode(Opcodes.IFNONNULL, busyNode));
        code.add(new InsnNode(Opcodes.ICONST_1));
        code.add(new InsnNode(Opcodes.IRETURN));
        code.add(busyNode);
        code.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        code.add(new InsnNode(Opcodes.ICONST_0));
        code.add(new InsnNode(Opcodes.IRETURN));
        return idle;
    }

    /**
     * The client tick entry point: run the retry check, then as many pipeline steps as the tempo
     * allows while nothing is in flight. Walking and awaiting the server still cost a whole tick.
     */
    static MethodNode driver() {
        MethodNode tick = new MethodNode(Opcodes.ACC_PUBLIC, "tick", "(L" + MC_CLIENT + ";)V", null, null);
        InsnList code = tick.instructions;
        LabelNode loop = new LabelNode(new Label());
        LabelNode done = new LabelNode(new Label());
        Object[] locals = {CONTROLLER, MC_CLIENT, Opcodes.INTEGER, Opcodes.INTEGER};
        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, TEMPO, "before", "(Ljava/lang/Object;)V", false));
        code.add(tempo("steps"));
        code.add(new VarInsnNode(Opcodes.ISTORE, 2));
        code.add(new InsnNode(Opcodes.ICONST_0));
        code.add(new VarInsnNode(Opcodes.ISTORE, 3));
        code.add(loop);
        code.add(new FrameNode(Opcodes.F_FULL, locals.length, locals, 0, new Object[0]));
        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new VarInsnNode(Opcodes.ALOAD, 1));
        code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CONTROLLER, "rex$tickOnce", "(L" + MC_CLIENT + ";)V", false));
        code.add(new IincInsnNode(3, 1));
        code.add(new VarInsnNode(Opcodes.ILOAD, 3));
        code.add(new VarInsnNode(Opcodes.ILOAD, 2));
        code.add(new JumpInsnNode(Opcodes.IF_ICMPGE, done));
        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CONTROLLER, "isRunning", "()Z", false));
        code.add(new JumpInsnNode(Opcodes.IFEQ, done));
        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CONTROLLER, "rex$idle", "()Z", false));
        code.add(new JumpInsnNode(Opcodes.IFEQ, done));
        code.add(new JumpInsnNode(Opcodes.GOTO, loop));
        code.add(done);
        code.add(new FrameNode(Opcodes.F_FULL, locals.length, locals, 0, new Object[0]));
        code.add(new InsnNode(Opcodes.RETURN));
        return tick;
    }

    static ClassNode binding(ClassNode node) {
        MethodNode binding = method(node, "binding", "(Ljava/lang/String;I)I");
        binding.instructions.clear();
        binding.tryCatchBlocks.clear();
        binding.localVariables = null;
        binding.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        binding.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        binding.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "rex/OneClient", "binding", "(Ljava/lang/String;I)I", false));
        binding.instructions.add(new InsnNode(Opcodes.IRETURN));
        System.out.println(node.name + ": one menu key");
        return node;
    }

    /** Rebuilds registerAll as guarded, named registrations and appends this build's own modules. */
    static ClassNode modules(ClassNode node) {
        MethodNode registerAll = method(node, "registerAll", "()V");
        List<String> order = new ArrayList<>();
        for (AbstractInsnNode instruction : registerAll.instructions.toArray()) {
            if (!(instruction instanceof TypeInsnNode created) || created.getOpcode() != Opcodes.NEW) {
                continue;
            }
            AbstractInsnNode init = created.getNext();
            if (init != null && init.getOpcode() == Opcodes.DUP) {
                init = init.getNext();
            }
            if (init instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESPECIAL
                    && call.name.equals("<init>") && call.owner.equals(created.desc)
                    && call.getNext() instanceof MethodInsnNode next && next.name.equals("register")) {
                order.add(created.desc);
            }
        }
        if (order.size() < 90) {
            throw new IllegalStateException("Only found " + order.size() + " modules");
        }
        String[] names = {"rex/LightESP", TEMPO, "rex/Duper", "rex/DonutFly", "rex/DonutStorage", "rex/DonutSpawner", "rex/DonutChunks", "rex/DonutHud"};
        for (String module : names) {
            if (!order.contains(module)) {
                order.add(module);
            }
        }
        registerAll.instructions.clear();
        registerAll.tryCatchBlocks.clear();
        registerAll.localVariables = null;
        for (String module : order) {
            registerAll.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            registerAll.instructions.add(new LdcInsnNode(module.replace('/', '.')));
            registerAll.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "rex/Modules", "add",
                    "(Ldev/krypton/core/managers/ModuleManager;Ljava/lang/String;)V", false));
        }
        registerAll.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "dev/krypton/client/KryptonClient", "instance",
                "Ldev/krypton/client/KryptonClient;"));
        registerAll.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "dev/krypton/client/KryptonClient", "events",
                "()Ldev/krypton/core/managers/EventManager;", false));
        registerAll.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        registerAll.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "dev/krypton/core/managers/EventManager", "register",
                "(Ljava/lang/Object;)V", false));
        registerAll.instructions.add(new InsnNode(Opcodes.RETURN));
        System.out.println(node.name + ": " + order.size() + " modules registered one by one, including " + String.join(", ", names));
        return node;
    }

    /** The Donut button on the title screen goes: one client, one menu. */
    static ClassNode titleScreen(ClassNode node) {
        MethodNode handler = method(node, "lambda$onInitializeClient$3", null);
        boolean adds = false;
        for (AbstractInsnNode instruction : handler.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode call && call.owner.endsWith("screen/v1/Screens")) {
                adds = true;
            }
        }
        if (!adds) {
            throw new IllegalStateException("Not the title-screen handler");
        }
        handler.instructions.clear();
        handler.tryCatchBlocks.clear();
        handler.localVariables = null;
        handler.instructions.add(new InsnNode(Opcodes.RETURN));
        System.out.println(node.name + ": title-screen button removed");
        return node;
    }

    static ClassNode commands(ClassNode node) {
        MethodNode handle = method(node, "handle", "(Ljava/lang/String;)Z");
        LabelNode legacy = new LabelNode(new Label());
        InsnList head = new InsnList();
        head.add(new VarInsnNode(Opcodes.ALOAD, 0));
        head.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "rex/OneClient", "command", "(Ljava/lang/String;)Z", false));
        head.add(new JumpInsnNode(Opcodes.IFEQ, legacy));
        head.add(new InsnNode(Opcodes.ICONST_1));
        head.add(new InsnNode(Opcodes.IRETURN));
        head.add(legacy);
        head.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        handle.instructions.insert(head);
        System.out.println(node.name + ": .one command");
        return node;
    }
}
