package com.recipetree.jeiexport112.compat;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Exact export-only repair for Tinkers' Complement 1.12.2-0.4.3's invalid fluid blacklist entry.
 *
 * <p>The original method body remains an unchanged branch. A four-instruction prefix asks the
 * exact-version runtime guard whether the supplied fluid has a serializable inverse registry name;
 * only an impossible null-name entry returns before either blacklist mutation.</p>
 */
public final class TinkersComplementFluidBlacklistTransformer implements IClassTransformer {
    static final String TARGET_DOTTED = "knightminer.tcomplement.plugin.jei.JEIPlugin";
    static final String TARGET_INTERNAL = "knightminer/tcomplement/plugin/jei/JEIPlugin";
    static final String TARGET_METHOD = "blacklistFluid";
    static final String TARGET_METHOD_DESC =
            "(Lmezz/jei/api/ingredients/IIngredientBlacklist;" +
                    "Lnet/minecraftforge/fluids/Fluid;)V";
    static final String GUARD_INTERNAL =
            "com/recipetree/jeiexport112/compat/TinkersComplementFluidBlacklistGuard";
    static final String GUARD_METHOD = "shouldRunNativeBlacklist";
    static final String GUARD_DESC = "(Lnet/minecraftforge/fluids/Fluid;)Z";
    static final String AUDITED_JAR_SHA256 =
            "09f3ff16c8204d6ed065c9ed1a717f56c824e45c12e6eda451aae3523262656c";
    static final String AUDITED_CLASS_SHA256 =
            "124861ec684552a78c9b4e8b398326005e38bf151e385b4d4985c8b9fc55f54b";

    private static final String FLUID_STACK = "net/minecraftforge/fluids/FluidStack";
    private static final String FLUID_UTIL = "net/minecraftforge/fluids/FluidUtil";
    private static final String BLACKLIST = "mezz/jei/api/ingredients/IIngredientBlacklist";
    private static final AtomicInteger APPLIED_COUNT = new AtomicInteger();

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!TARGET_DOTTED.equals(transformedName)) {
            return basicClass;
        }
        if (basicClass == null) {
            throw drift("LaunchWrapper supplied null class bytes");
        }
        String actualSha256 = sha256(basicClass);
        if (!AUDITED_CLASS_SHA256.equals(actualSha256)) {
            throw drift(
                    "target class SHA-256 is " + actualSha256 +
                            ", expected exact audited class " + AUDITED_CLASS_SHA256
            );
        }
        return transformValidatedClass(basicClass);
    }

    /** Structural entry point used only by tests; production always enforces the class digest. */
    static byte[] transformStructurallyForTests(byte[] basicClass) {
        if (basicClass == null) {
            throw drift("test supplied null class bytes");
        }
        return transformValidatedClass(basicClass);
    }

    public static void assertAppliedExactlyOnce() {
        int applied = APPLIED_COUNT.get();
        if (applied != 1) {
            throw new IllegalStateException(
                    "[jeiexport] Tinkers' Complement compatibility readiness gate failed: " +
                            "transformer applied " + applied + " times, expected exactly once. " +
                            "Refusing to export a possibly incomplete HEI registry."
            );
        }
    }

    static int appliedCount() {
        return APPLIED_COUNT.get();
    }

    static void resetAppliedCountForTests() {
        APPLIED_COUNT.set(0);
    }

    private static byte[] transformValidatedClass(byte[] basicClass) {
        ClassNode classNode = new ClassNode();
        new ClassReader(basicClass).accept(classNode, 0);
        validateClass(classNode);
        MethodNode target = findAndValidateMethodTable(classNode);
        validateExactMethodBody(target);

        LabelNode nativeBody = new LabelNode();
        InsnList prefix = new InsnList();
        prefix.add(new VarInsnNode(Opcodes.ALOAD, 1));
        prefix.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC, GUARD_INTERNAL, GUARD_METHOD, GUARD_DESC, false
        ));
        prefix.add(new JumpInsnNode(Opcodes.IFNE, nativeBody));
        prefix.add(new InsnNode(Opcodes.RETURN));
        prefix.add(nativeBody);
        prefix.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        target.instructions.insertBefore(target.instructions.getFirst(), prefix);

        int applied = APPLIED_COUNT.incrementAndGet();
        if (applied != 1) {
            throw drift("target transform applied " + applied + " times; expected exactly once");
        }

        ClassWriter writer = new ClassWriter(0);
        classNode.accept(writer);
        System.out.println(
                "[jeiexport] Applied exact Tinkers' Complement 1.12.2-0.4.3 fluid blacklist " +
                        "repair once: only a null FluidRegistry inverse name skips the impossible " +
                        "entry; every valid-name fluid retains the original blacklistFluid body."
        );
        return writer.toByteArray();
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
        if (!"JEIPlugin.java".equals(classNode.sourceFile)) {
            throw drift("SourceFile is " + classNode.sourceFile + ", expected JEIPlugin.java");
        }
        if (!hasExactJeiPluginAnnotation(classNode)) {
            throw drift("class annotation table changed from the exact invisible @JEIPlugin shape");
        }
        validateFieldTable(classNode);
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

    private static void validateFieldTable(ClassNode classNode) {
        if (classNode.fields.size() != 6) {
            throw drift("field table changed; expected 6 fields, got " + classNode.fields.size());
        }
        int privateConstant = Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL;
        requireField(classNode.fields.get(0), privateConstant, "FURNACE_FUEL",
                "Ljava/lang/String;", "minecraft.fuel");
        requireField(classNode.fields.get(1), privateConstant, "TINKERS_SMELTERY",
                "Ljava/lang/String;", "tconstruct.smeltery");
        requireField(classNode.fields.get(2), privateConstant, "TINKERS_ALLOYING",
                "Ljava/lang/String;", "tconstruct.alloy");
        requireField(classNode.fields.get(3), privateConstant, "EXNIHILO_HAMMER",
                "Ljava/lang/String;", "exnihilocreatio:hammer");
        requireField(classNode.fields.get(4), privateConstant, "CHISEL_CHISELING",
                "Ljava/lang/String;", "chisel.chiseling");
        requireField(
                classNode.fields.get(5), Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "meltingCategory",
                "Lknightminer/tcomplement/plugin/jei/melter/MeltingRecipeCategory;", null
        );
    }

    private static void requireField(
            FieldNode field, int access, String name, String desc, Object value
    ) {
        if (field.access != access || !name.equals(field.name) || !desc.equals(field.desc) ||
                field.signature != null || (value == null ? field.value != null :
                !value.equals(field.value))) {
            throw drift("field table entry changed at " + name);
        }
    }

    private static MethodNode findAndValidateMethodTable(ClassNode classNode) {
        if (classNode.methods.size() != 6) {
            throw drift("method table changed; expected 6 methods, got " + classNode.methods.size());
        }
        requireMethod(classNode.methods.get(0), Opcodes.ACC_PUBLIC, "<init>", "()V");
        requireMethod(
                classNode.methods.get(1), Opcodes.ACC_PUBLIC, "registerCategories",
                "(Lmezz/jei/api/recipe/IRecipeCategoryRegistration;)V"
        );
        requireMethod(
                classNode.methods.get(2), Opcodes.ACC_PUBLIC, "register",
                "(Lmezz/jei/api/IModRegistry;)V"
        );
        MethodNode target = classNode.methods.get(3);
        requireMethod(
                target, Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                TARGET_METHOD, TARGET_METHOD_DESC
        );
        requireMethod(
                classNode.methods.get(4),
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                "lambda$register$1",
                "(Lmezz/jei/api/IGuiHelper;" +
                        "Lknightminer/tcomplement/library/steelworks/HighOvenFuel;)" +
                        "Lmezz/jei/api/recipe/IRecipeWrapper;"
        );
        requireMethod(
                classNode.methods.get(5),
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                "lambda$register$0",
                "(Lknightminer/tcomplement/plugin/jei/highoven/mix/HighOvenMixWrapper;)" +
                        "Lmezz/jei/api/recipe/IRecipeWrapper;"
        );
        return target;
    }

    private static void requireMethod(
            MethodNode method, int access, String name, String desc
    ) {
        if (method.access != access || !name.equals(method.name) || !desc.equals(method.desc) ||
                method.signature != null ||
                (method.exceptions != null && !method.exceptions.isEmpty())) {
            throw drift("method table entry changed at " + name + desc);
        }
    }

    private static void validateExactMethodBody(MethodNode method) {
        if (method.maxStack != 4 || method.maxLocals != 3) {
            throw drift("blacklistFluid max stack/locals changed from exact 4/3 shape");
        }
        if (method.tryCatchBlocks != null && !method.tryCatchBlocks.isEmpty()) {
            throw drift("blacklistFluid acquired a try/catch block");
        }
        List<AbstractInsnNode> code = executableInstructions(method);
        if (code.size() != 14) {
            throw drift("blacklistFluid has " + code.size() +
                    " executable instructions; expected exactly 14");
        }
        requireType(code.get(0), Opcodes.NEW, FLUID_STACK, 1);
        requireOpcode(code.get(1), Opcodes.DUP, 2);
        requireVar(code.get(2), Opcodes.ALOAD, 1, 3);
        requireInt(code.get(3), Opcodes.SIPUSH, 1000, 4);
        requireCall(code.get(4), Opcodes.INVOKESPECIAL, FLUID_STACK, "<init>",
                "(Lnet/minecraftforge/fluids/Fluid;I)V", false, 5);
        requireVar(code.get(5), Opcodes.ASTORE, 2, 6);
        requireVar(code.get(6), Opcodes.ALOAD, 0, 7);
        requireVar(code.get(7), Opcodes.ALOAD, 2, 8);
        requireCall(code.get(8), Opcodes.INVOKEINTERFACE, BLACKLIST,
                "addIngredientToBlacklist", "(Ljava/lang/Object;)V", true, 9);
        requireVar(code.get(9), Opcodes.ALOAD, 0, 10);
        requireVar(code.get(10), Opcodes.ALOAD, 2, 11);
        requireCall(code.get(11), Opcodes.INVOKESTATIC, FLUID_UTIL, "getFilledBucket",
                "(Lnet/minecraftforge/fluids/FluidStack;)Lnet/minecraft/item/ItemStack;",
                false, 12);
        requireCall(code.get(12), Opcodes.INVOKEINTERFACE, BLACKLIST,
                "addIngredientToBlacklist", "(Ljava/lang/Object;)V", true, 13);
        requireOpcode(code.get(13), Opcodes.RETURN, 14);
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

    private static void requireType(
            AbstractInsnNode instruction, int opcode, String desc, int position
    ) {
        requireOpcode(instruction, opcode, position);
        if (!(instruction instanceof TypeInsnNode) ||
                !desc.equals(((TypeInsnNode) instruction).desc)) {
            throw drift("instruction " + position + " type changed from " + desc);
        }
    }

    private static void requireVar(
            AbstractInsnNode instruction, int opcode, int variable, int position
    ) {
        requireOpcode(instruction, opcode, position);
        if (!(instruction instanceof VarInsnNode) ||
                ((VarInsnNode) instruction).var != variable) {
            throw drift("instruction " + position + " changed variable index; expected " + variable);
        }
    }

    private static void requireInt(
            AbstractInsnNode instruction, int opcode, int operand, int position
    ) {
        requireOpcode(instruction, opcode, position);
        if (!(instruction instanceof IntInsnNode) ||
                ((IntInsnNode) instruction).operand != operand) {
            throw drift("instruction " + position + " changed integer operand; expected " + operand);
        }
    }

    private static void requireCall(
            AbstractInsnNode instruction, int opcode, String owner, String name,
            String desc, boolean itf, int position
    ) {
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
    }

    private static void requireOpcode(AbstractInsnNode instruction, int expected, int position) {
        if (instruction.getOpcode() != expected) {
            throw drift("instruction " + position + " opcode is " + instruction.getOpcode() +
                    ", expected " + expected);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder value = new StringBuilder(digest.length * 2);
            for (byte current : digest) {
                value.append(String.format("%02x", current & 0xff));
            }
            return value.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("[jeiexport] JVM is missing SHA-256", exception);
        }
    }

    private static IllegalStateException drift(String detail) {
        return new IllegalStateException(
                "[jeiexport] Tinkers' Complement 1.12.2-0.4.3 fluid blacklist transform " +
                        "invariant failed: " + detail + ". Audited artifact SHA-256=" +
                        AUDITED_JAR_SHA256 + ", target class SHA-256=" + AUDITED_CLASS_SHA256 +
                        "; refusing partial activation."
        );
    }
}
