package com.recipetree.jeiexport112.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Exact export-only repair for TAACC 0.0.3's null-NBT subtype interpreter.
 *
 * <p>The transform is deliberately stack-neutral: it replaces only NBTTagCompound.getString with
 * a static null-aware delegate that has the same two inputs and one output. Every surrounding
 * TAACC instruction and stack-map frame remains native.</p>
 */
public final class TaaccAspectSubtypeTransformer implements IClassTransformer {
    static final String TARGET_DOTTED =
            "nekizalb.mods.TAACC.compat.jei.TaaccJeiPlugin";
    static final String TARGET_INTERNAL =
            "nekizalb/mods/TAACC/compat/jei/TaaccJeiPlugin";
    static final String TARGET_METHOD = "AspectTagSplitter";
    static final String TARGET_METHOD_DESC =
            "(Lnet/minecraft/item/ItemStack;)Ljava/lang/String;";
    static final String GUARD_INTERNAL =
            "com/recipetree/jeiexport112/compat/TaaccAspectSubtypeGuard";
    static final String GUARD_METHOD = "getStringOrEmpty";
    static final String GUARD_DESC =
            "(Lnet/minecraft/nbt/NBTTagCompound;Ljava/lang/String;)Ljava/lang/String;";

    private static final String ITEM_STACK = "net/minecraft/item/ItemStack";
    private static final String NBT_COMPOUND = "net/minecraft/nbt/NBTTagCompound";
    private static final AtomicInteger APPLIED_COUNT = new AtomicInteger();

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!TARGET_DOTTED.equals(transformedName)) {
            return basicClass;
        }
        if (basicClass == null) {
            throw drift("LaunchWrapper supplied null class bytes");
        }

        ClassNode classNode = new ClassNode();
        new ClassReader(basicClass).accept(classNode, 0);
        validateClass(classNode);
        MethodNode target = findAndValidateMethodTable(classNode);
        MethodInsnNode nativeGetString = validateExactMethodBody(target);

        nativeGetString.setOpcode(Opcodes.INVOKESTATIC);
        nativeGetString.owner = GUARD_INTERNAL;
        nativeGetString.name = GUARD_METHOD;
        nativeGetString.desc = GUARD_DESC;
        nativeGetString.itf = false;

        int applied = APPLIED_COUNT.incrementAndGet();
        if (applied != 1) {
            throw drift("target transform applied " + applied + " times; expected exactly once");
        }

        ClassWriter writer = new ClassWriter(0);
        classNode.accept(writer);
        System.out.println(
                "[jeiexport] Applied exact TAACC 0.0.3 Aspect subtype repair once: " +
                        "only null NBTTagCompound handling is delegated; present compounds retain " +
                        "NBTTagCompound.getString(\"Aspect\") semantics."
        );
        return writer.toByteArray();
    }

    public static void assertAppliedExactlyOnce() {
        int applied = APPLIED_COUNT.get();
        if (applied != 1) {
            throw new IllegalStateException(
                    "[jeiexport] TAACC compatibility readiness gate failed: transformer applied " +
                            applied + " times, expected exactly once. Refusing to export a possibly " +
                            "degraded HEI registry."
            );
        }
    }

    static int appliedCount() {
        return APPLIED_COUNT.get();
    }

    static void resetAppliedCountForTests() {
        APPLIED_COUNT.set(0);
    }

    private static void validateClass(ClassNode classNode) {
        if (!TARGET_INTERNAL.equals(classNode.name)) {
            throw drift("class owner is " + classNode.name + ", expected " + TARGET_INTERNAL);
        }
        if (classNode.version != Opcodes.V1_8) {
            throw drift("class-file version is " + classNode.version + ", expected Java 8 / 52");
        }
        if (classNode.access != (Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER)) {
            throw drift("class access is 0x" + Integer.toHexString(classNode.access) +
                    ", expected ACC_PUBLIC|ACC_SUPER");
        }
        if (!"java/lang/Object".equals(classNode.superName)) {
            throw drift("superclass is " + classNode.superName + ", expected java/lang/Object");
        }
        if (classNode.interfaces.size() != 1 ||
                !"mezz/jei/api/IModPlugin".equals(classNode.interfaces.get(0))) {
            throw drift("interface table changed from exact mezz/jei/api/IModPlugin shape");
        }
        if (!classNode.fields.isEmpty()) {
            throw drift("field table changed; expected zero fields, got " + classNode.fields.size());
        }
        if (!"TaaccJeiPlugin.java".equals(classNode.sourceFile)) {
            throw drift("SourceFile is " + classNode.sourceFile + ", expected TaaccJeiPlugin.java");
        }
        if (!hasExactJeiPluginAnnotation(classNode)) {
            throw drift("class annotation table changed from the exact invisible @JEIPlugin shape");
        }
    }

    private static boolean hasExactJeiPluginAnnotation(ClassNode classNode) {
        if (classNode.visibleAnnotations != null && !classNode.visibleAnnotations.isEmpty()) {
            return false;
        }
        List<AnnotationNode> annotations = classNode.invisibleAnnotations;
        return annotations != null && annotations.size() == 1 &&
                "Lmezz/jei/api/JEIPlugin;".equals(annotations.get(0).desc) &&
                (annotations.get(0).values == null || annotations.get(0).values.isEmpty());
    }

    private static MethodNode findAndValidateMethodTable(ClassNode classNode) {
        if (classNode.methods.size() != 3) {
            throw drift("method table changed; expected 3 methods, got " + classNode.methods.size());
        }
        MethodNode constructor = null;
        MethodNode splitter = null;
        MethodNode registration = null;
        for (MethodNode method : classNode.methods) {
            if ("<init>".equals(method.name) && "()V".equals(method.desc)) {
                constructor = unique(constructor, method, "constructor");
            } else if (TARGET_METHOD.equals(method.name) && TARGET_METHOD_DESC.equals(method.desc)) {
                splitter = unique(splitter, method, "AspectTagSplitter");
            } else if ("registerItemSubtypes".equals(method.name) &&
                    "(Lmezz/jei/api/ISubtypeRegistry;)V".equals(method.desc)) {
                registration = unique(registration, method, "registerItemSubtypes");
            } else {
                throw drift("unexpected method " + method.name + method.desc);
            }
        }
        requireAccess(constructor, Opcodes.ACC_PUBLIC, "constructor");
        requireAccess(splitter, Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "AspectTagSplitter");
        requireAccess(registration, Opcodes.ACC_PUBLIC, "registerItemSubtypes");
        return splitter;
    }

    private static MethodNode unique(MethodNode previous, MethodNode current, String label) {
        if (previous != null) {
            throw drift("duplicate " + label + " method");
        }
        return current;
    }

    private static void requireAccess(MethodNode method, int expected, String label) {
        if (method == null) {
            throw drift("missing " + label + " method");
        }
        if (method.access != expected) {
            throw drift(label + " access is 0x" + Integer.toHexString(method.access) +
                    ", expected 0x" + Integer.toHexString(expected));
        }
        if (method.signature != null || (method.exceptions != null && !method.exceptions.isEmpty())) {
            throw drift(label + " acquired a generic signature or declared exception");
        }
    }

    private static MethodInsnNode validateExactMethodBody(MethodNode method) {
        if (method.maxStack != 2 || method.maxLocals != 2) {
            throw drift("AspectTagSplitter max stack/locals changed from exact 2/2 shape");
        }
        if (method.tryCatchBlocks != null && !method.tryCatchBlocks.isEmpty()) {
            throw drift("AspectTagSplitter acquired a try/catch block");
        }

        List<AbstractInsnNode> code = executableInstructions(method);
        if (code.size() != 11) {
            throw drift("AspectTagSplitter has " + code.size() +
                    " executable instructions; expected exactly 11");
        }
        requireVar(code.get(0), Opcodes.ALOAD, 0, 1);
        requireCall(code.get(1), Opcodes.INVOKEVIRTUAL, ITEM_STACK, "func_77978_p",
                "()Lnet/minecraft/nbt/NBTTagCompound;", false, 2);
        requireConstant(code.get(2), "Aspect", 3);
        MethodInsnNode nativeGetString = requireCall(
                code.get(3), Opcodes.INVOKEVIRTUAL, NBT_COMPOUND, "func_74779_i",
                "(Ljava/lang/String;)Ljava/lang/String;", false, 4
        );
        requireVar(code.get(4), Opcodes.ASTORE, 1, 5);
        requireVar(code.get(5), Opcodes.ALOAD, 1, 6);
        requireJump(code.get(6), Opcodes.IFNONNULL, code.get(9), 7);
        requireConstant(code.get(7), "", 8);
        requireJump(code.get(8), Opcodes.GOTO, code.get(10), 9);
        requireVar(code.get(9), Opcodes.ALOAD, 1, 10);
        requireOpcode(code.get(10), Opcodes.ARETURN, 11);
        return nativeGetString;
    }

    private static List<AbstractInsnNode> executableInstructions(MethodNode method) {
        List<AbstractInsnNode> result = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (instruction.getOpcode() >= 0) {
                result.add(instruction);
            }
        }
        return result;
    }

    private static void requireVar(AbstractInsnNode instruction, int opcode, int variable,
                                   int position) {
        requireOpcode(instruction, opcode, position);
        if (!(instruction instanceof VarInsnNode) ||
                ((VarInsnNode) instruction).var != variable) {
            throw drift("instruction " + position + " changed variable index; expected " + variable);
        }
    }

    private static MethodInsnNode requireCall(AbstractInsnNode instruction, int opcode,
                                              String owner, String name, String desc,
                                              boolean itf, int position) {
        requireOpcode(instruction, opcode, position);
        if (!(instruction instanceof MethodInsnNode)) {
            throw drift("instruction " + position + " is not the expected method invocation");
        }
        MethodInsnNode call = (MethodInsnNode) instruction;
        if (!owner.equals(call.owner) || !name.equals(call.name) || !desc.equals(call.desc) ||
                call.itf != itf) {
            throw drift("instruction " + position + " call changed; got " + call.owner + "." +
                    call.name + call.desc);
        }
        return call;
    }

    private static void requireConstant(AbstractInsnNode instruction, String expected,
                                        int position) {
        requireOpcode(instruction, Opcodes.LDC, position);
        if (!(instruction instanceof LdcInsnNode) ||
                !expected.equals(((LdcInsnNode) instruction).cst)) {
            throw drift("instruction " + position + " constant changed from " +
                    (expected.isEmpty() ? "the empty string" : expected));
        }
    }

    private static void requireJump(AbstractInsnNode instruction, int opcode,
                                    AbstractInsnNode expectedTarget, int position) {
        requireOpcode(instruction, opcode, position);
        if (!(instruction instanceof JumpInsnNode)) {
            throw drift("instruction " + position + " is not the expected jump");
        }
        AbstractInsnNode actualTarget = ((JumpInsnNode) instruction).label;
        while (actualTarget != null && actualTarget.getOpcode() < 0) {
            actualTarget = actualTarget.getNext();
        }
        if (actualTarget != expectedTarget) {
            throw drift("instruction " + position + " jump target changed");
        }
    }

    private static void requireOpcode(AbstractInsnNode instruction, int expected, int position) {
        if (instruction.getOpcode() != expected) {
            throw drift("instruction " + position + " opcode is " + instruction.getOpcode() +
                    ", expected " + expected);
        }
    }

    private static IllegalStateException drift(String detail) {
        return new IllegalStateException(
                "[jeiexport] TAACC 0.0.3 Aspect subtype transform invariant failed: " + detail +
                        ". Audited target class SHA-256=" +
                        "75bc34c5adb18372a8c49bbc28dca51daf58cdb9aca7db1857cbb7b3f85dd0b1; " +
                        "refusing partial activation."
        );
    }
}
