package com.recipetree.jeiexport112.compat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class TinkersComplementFluidBlacklistTransformerTest {
    private static final String FIXTURE_ENV = "JEIEXPORT_TCOMPLEMENT_FIXTURE";
    private static final String TARGET_ENTRY =
            TinkersComplementFluidBlacklistTransformer.TARGET_INTERNAL + ".class";
    private static final String FLUID_STACK = "net/minecraftforge/fluids/FluidStack";
    private static final String FLUID_UTIL = "net/minecraftforge/fluids/FluidUtil";
    private static final String BLACKLIST = "mezz/jei/api/ingredients/IIngredientBlacklist";
    private String previousProperty;

    @Before
    public void reset() {
        previousProperty = System.getProperty(
                TinkersComplementFluidBlacklistConfiguration.ENABLE_PROPERTY
        );
        System.setProperty(
                TinkersComplementFluidBlacklistConfiguration.ENABLE_PROPERTY, "true"
        );
        TinkersComplementFluidBlacklistTransformer.resetAppliedCountForTests();
    }

    @After
    public void restore() {
        TinkersComplementFluidBlacklistTransformer.resetAppliedCountForTests();
        if (previousProperty == null) {
            System.clearProperty(TinkersComplementFluidBlacklistConfiguration.ENABLE_PROPERTY);
        } else {
            System.setProperty(
                    TinkersComplementFluidBlacklistConfiguration.ENABLE_PROPERTY,
                    previousProperty
            );
        }
    }

    @Test
    public void prependsOnlyTheExplicitGuardAndRetainsTheNativeBody() {
        byte[] transformed = transformSynthetic(exactSyntheticClass());
        List<AbstractInsnNode> code = executable(target(readClass(transformed)));

        assertEquals(1, TinkersComplementFluidBlacklistTransformer.appliedCount());
        assertEquals(18, code.size());
        assertVar(code.get(0), Opcodes.ALOAD, 1);
        assertCall(
                code.get(1), Opcodes.INVOKESTATIC,
                TinkersComplementFluidBlacklistTransformer.GUARD_INTERNAL,
                TinkersComplementFluidBlacklistTransformer.GUARD_METHOD,
                TinkersComplementFluidBlacklistTransformer.GUARD_DESC, false
        );
        assertEquals(Opcodes.IFNE, code.get(2).getOpcode());
        assertEquals(Opcodes.RETURN, code.get(3).getOpcode());
        assertEquals(Opcodes.NEW, code.get(4).getOpcode());
        assertEquals(FLUID_STACK, ((TypeInsnNode) code.get(4)).desc);
        assertEquals(Opcodes.RETURN, code.get(17).getOpcode());

        AbstractInsnNode branchTarget = ((JumpInsnNode) code.get(2)).label;
        while (branchTarget != null && branchTarget.getOpcode() < 0) {
            branchTarget = branchTarget.getNext();
        }
        assertSame(code.get(4), branchTarget);

        assertEquals(1, countCalls(
                transformed, FLUID_STACK, "<init>",
                "(Lnet/minecraftforge/fluids/Fluid;I)V"
        ));
        assertEquals(2, countCalls(
                transformed, BLACKLIST, "addIngredientToBlacklist", "(Ljava/lang/Object;)V"
        ));
        assertEquals(1, countCalls(
                transformed, FLUID_UTIL, "getFilledBucket",
                "(Lnet/minecraftforge/fluids/FluidStack;)Lnet/minecraft/item/ItemStack;"
        ));
    }

    @Test
    public void patchesTheExactAuditedArtifactAndVerifiesBothDigests() throws IOException {
        String fixturePath = System.getenv(FIXTURE_ENV);
        Assume.assumeTrue(
                "Set " + FIXTURE_ENV + " to test the audited Tinkers' Complement artifact",
                fixturePath != null && !fixturePath.trim().isEmpty()
        );
        File fixture = new File(fixturePath);
        assertEquals(
                TinkersComplementFluidBlacklistTransformer.AUDITED_JAR_SHA256,
                sha256(readFile(fixture))
        );
        try (JarFile jar = new JarFile(fixture)) {
            JarEntry entry = jar.getJarEntry(TARGET_ENTRY);
            assertNotNull("Missing " + TARGET_ENTRY + " in " + fixture, entry);
            byte[] source;
            try (InputStream input = jar.getInputStream(entry)) {
                source = readAll(input);
            }
            assertEquals(
                    TinkersComplementFluidBlacklistTransformer.AUDITED_CLASS_SHA256,
                    sha256(source)
            );
            byte[] transformed = new TinkersComplementFluidBlacklistTransformer().transform(
                    TinkersComplementFluidBlacklistTransformer.TARGET_DOTTED,
                    TinkersComplementFluidBlacklistTransformer.TARGET_DOTTED,
                    source
            );
            assertEquals(1, countCalls(
                    transformed,
                    TinkersComplementFluidBlacklistTransformer.GUARD_INTERNAL,
                    TinkersComplementFluidBlacklistTransformer.GUARD_METHOD,
                    TinkersComplementFluidBlacklistTransformer.GUARD_DESC
            ));
            assertEquals(1, countCalls(
                    transformed, FLUID_UTIL, "getFilledBucket",
                    "(Lnet/minecraftforge/fluids/FluidStack;)Lnet/minecraft/item/ItemStack;"
            ));
        }
    }

    @Test
    public void unrelatedClassReturnsTheIdenticalByteArray() {
        byte[] source = exactSyntheticClass();
        assertSame(
                source,
                new TinkersComplementFluidBlacklistTransformer().transform(
                        "example.Unrelated", "example.Unrelated", source
                )
        );
        assertEquals(0, TinkersComplementFluidBlacklistTransformer.appliedCount());
    }

    @Test(expected = IllegalStateException.class)
    public void productionRejectsAnUnauditedClassDigest() {
        new TinkersComplementFluidBlacklistTransformer().transform(
                TinkersComplementFluidBlacklistTransformer.TARGET_DOTTED,
                TinkersComplementFluidBlacklistTransformer.TARGET_DOTTED,
                exactSyntheticClass()
        );
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsNullTargetBytes() {
        new TinkersComplementFluidBlacklistTransformer().transform(
                TinkersComplementFluidBlacklistTransformer.TARGET_DOTTED,
                TinkersComplementFluidBlacklistTransformer.TARGET_DOTTED,
                null
        );
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsClassFileVersionDrift() {
        ClassNode node = readClass(exactSyntheticClass());
        node.version = Opcodes.V1_7;
        transformSynthetic(writeClass(node));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsOwnerDrift() {
        ClassNode node = readClass(exactSyntheticClass());
        node.name = "example/ChangedTinkersComplementPlugin";
        transformSynthetic(writeClass(node));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsAnnotationDrift() {
        ClassNode node = readClass(exactSyntheticClass());
        node.invisibleAnnotations.clear();
        transformSynthetic(writeClass(node));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsFieldTableDrift() {
        ClassNode node = readClass(exactSyntheticClass());
        node.fields.get(0).value = "changed.fuel";
        transformSynthetic(writeClass(node));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsMethodTableDrift() {
        ClassNode node = readClass(exactSyntheticClass());
        target(node).access = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
        transformSynthetic(writeClass(node));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsStackOrLocalShapeDrift() {
        ClassNode node = readClass(exactSyntheticClass());
        target(node).maxStack = 5;
        transformSynthetic(writeClass(node));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsAmountOperandDrift() {
        ClassNode node = readClass(exactSyntheticClass());
        for (AbstractInsnNode instruction : executable(target(node))) {
            if (instruction instanceof IntInsnNode) {
                ((IntInsnNode) instruction).operand = 999;
            }
        }
        transformSynthetic(writeClass(node));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsFluidStackConstructorDrift() {
        ClassNode node = readClass(exactSyntheticClass());
        for (AbstractInsnNode instruction : executable(target(node))) {
            if (instruction instanceof MethodInsnNode &&
                    "<init>".equals(((MethodInsnNode) instruction).name)) {
                ((MethodInsnNode) instruction).owner = "example/ChangedFluidStack";
            }
        }
        transformSynthetic(writeClass(node));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsFilledBucketCallDrift() {
        ClassNode node = readClass(exactSyntheticClass());
        for (AbstractInsnNode instruction : executable(target(node))) {
            if (instruction instanceof MethodInsnNode &&
                    "getFilledBucket".equals(((MethodInsnNode) instruction).name)) {
                ((MethodInsnNode) instruction).owner = "example/ChangedFluidUtil";
            }
        }
        transformSynthetic(writeClass(node));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsBlacklistMutationCallDrift() {
        ClassNode node = readClass(exactSyntheticClass());
        for (AbstractInsnNode instruction : executable(target(node))) {
            if (instruction instanceof MethodInsnNode &&
                    "addIngredientToBlacklist".equals(((MethodInsnNode) instruction).name)) {
                ((MethodInsnNode) instruction).name = "changedBlacklistCall";
                break;
            }
        }
        transformSynthetic(writeClass(node));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsExtraExecutableInstruction() {
        ClassNode node = readClass(exactSyntheticClass());
        target(node).instructions.insert(new InsnNode(Opcodes.NOP));
        transformSynthetic(writeClass(node));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsDuplicateTransformation() {
        byte[] source = exactSyntheticClass();
        transformSynthetic(source);
        transformSynthetic(source);
    }

    @Test(expected = IllegalStateException.class)
    public void readinessGateRejectsZeroApplications() {
        TinkersComplementFluidBlacklistTransformer.assertAppliedExactlyOnce();
    }

    private static byte[] transformSynthetic(byte[] source) {
        return TinkersComplementFluidBlacklistTransformer.transformStructurallyForTests(source);
    }

    private static byte[] exactSyntheticClass() {
        ClassNode node = new ClassNode();
        node.version = Opcodes.V1_8;
        node.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER;
        node.name = TinkersComplementFluidBlacklistTransformer.TARGET_INTERNAL;
        node.superName = "java/lang/Object";
        node.sourceFile = "JEIPlugin.java";
        node.interfaces.add("mezz/jei/api/IModPlugin");
        node.invisibleAnnotations = new ArrayList<AnnotationNode>();
        node.invisibleAnnotations.add(new AnnotationNode("Lmezz/jei/api/JEIPlugin;"));

        int constantAccess = Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL;
        node.fields.add(new FieldNode(
                constantAccess, "FURNACE_FUEL", "Ljava/lang/String;", null, "minecraft.fuel"
        ));
        node.fields.add(new FieldNode(
                constantAccess, "TINKERS_SMELTERY", "Ljava/lang/String;", null,
                "tconstruct.smeltery"
        ));
        node.fields.add(new FieldNode(
                constantAccess, "TINKERS_ALLOYING", "Ljava/lang/String;", null,
                "tconstruct.alloy"
        ));
        node.fields.add(new FieldNode(
                constantAccess, "EXNIHILO_HAMMER", "Ljava/lang/String;", null,
                "exnihilocreatio:hammer"
        ));
        node.fields.add(new FieldNode(
                constantAccess, "CHISEL_CHISELING", "Ljava/lang/String;", null,
                "chisel.chiseling"
        ));
        node.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "meltingCategory",
                "Lknightminer/tcomplement/plugin/jei/melter/MeltingRecipeCategory;", null, null
        ));

        node.methods.add(voidMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", 1));
        node.methods.add(voidMethod(
                Opcodes.ACC_PUBLIC, "registerCategories",
                "(Lmezz/jei/api/recipe/IRecipeCategoryRegistration;)V", 2
        ));
        node.methods.add(voidMethod(
                Opcodes.ACC_PUBLIC, "register", "(Lmezz/jei/api/IModRegistry;)V", 2
        ));
        node.methods.add(exactBlacklistMethod());
        node.methods.add(nullReturningMethod(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                "lambda$register$1",
                "(Lmezz/jei/api/IGuiHelper;" +
                        "Lknightminer/tcomplement/library/steelworks/HighOvenFuel;)" +
                        "Lmezz/jei/api/recipe/IRecipeWrapper;",
                2
        ));
        node.methods.add(nullReturningMethod(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                "lambda$register$0",
                "(Lknightminer/tcomplement/plugin/jei/highoven/mix/HighOvenMixWrapper;)" +
                        "Lmezz/jei/api/recipe/IRecipeWrapper;",
                1
        ));
        return writeClass(node);
    }

    private static MethodNode voidMethod(int access, String name, String desc, int locals) {
        MethodNode method = new MethodNode(access, name, desc, null, null);
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxStack = 0;
        method.maxLocals = locals;
        return method;
    }

    private static MethodNode nullReturningMethod(
            int access, String name, String desc, int locals
    ) {
        MethodNode method = new MethodNode(access, name, desc, null, null);
        method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.maxStack = 1;
        method.maxLocals = locals;
        return method;
    }

    private static MethodNode exactBlacklistMethod() {
        MethodNode method = new MethodNode(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                TinkersComplementFluidBlacklistTransformer.TARGET_METHOD,
                TinkersComplementFluidBlacklistTransformer.TARGET_METHOD_DESC,
                null, null
        );
        method.instructions.add(new TypeInsnNode(Opcodes.NEW, FLUID_STACK));
        method.instructions.add(new InsnNode(Opcodes.DUP));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new IntInsnNode(Opcodes.SIPUSH, 1000));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, FLUID_STACK, "<init>",
                "(Lnet/minecraftforge/fluids/Fluid;I)V", false
        ));
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 2));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKEINTERFACE, BLACKLIST, "addIngredientToBlacklist",
                "(Ljava/lang/Object;)V", true
        ));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC, FLUID_UTIL, "getFilledBucket",
                "(Lnet/minecraftforge/fluids/FluidStack;)Lnet/minecraft/item/ItemStack;", false
        ));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKEINTERFACE, BLACKLIST, "addIngredientToBlacklist",
                "(Ljava/lang/Object;)V", true
        ));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxStack = 4;
        method.maxLocals = 3;
        return method;
    }

    private static MethodNode target(ClassNode node) {
        for (MethodNode method : node.methods) {
            if (TinkersComplementFluidBlacklistTransformer.TARGET_METHOD.equals(method.name) &&
                    TinkersComplementFluidBlacklistTransformer.TARGET_METHOD_DESC.equals(method.desc)) {
                return method;
            }
        }
        throw new AssertionError("missing target method");
    }

    private static List<AbstractInsnNode> executable(MethodNode method) {
        List<AbstractInsnNode> result = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (instruction.getOpcode() >= 0) {
                result.add(instruction);
            }
        }
        return result;
    }

    private static int countCalls(byte[] bytes, String owner, String name, String desc) {
        int count = 0;
        ClassNode node = readClass(bytes);
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction : executable(method)) {
                if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    if (owner.equals(call.owner) && name.equals(call.name) &&
                            desc.equals(call.desc)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static void assertVar(AbstractInsnNode instruction, int opcode, int variable) {
        assertEquals(opcode, instruction.getOpcode());
        assertTrue(instruction instanceof VarInsnNode);
        assertEquals(variable, ((VarInsnNode) instruction).var);
    }

    private static void assertCall(
            AbstractInsnNode instruction, int opcode, String owner, String name,
            String desc, boolean itf
    ) {
        assertEquals(opcode, instruction.getOpcode());
        assertTrue(instruction instanceof MethodInsnNode);
        MethodInsnNode call = (MethodInsnNode) instruction;
        assertEquals(owner, call.owner);
        assertEquals(name, call.name);
        assertEquals(desc, call.desc);
        assertEquals(itf, call.itf);
    }

    private static ClassNode readClass(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static byte[] writeClass(ClassNode node) {
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static byte[] readFile(File file) throws IOException {
        try (InputStream input = new java.io.FileInputStream(file)) {
            return readAll(input);
        }
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
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
            throw new AssertionError(exception);
        }
    }
}
