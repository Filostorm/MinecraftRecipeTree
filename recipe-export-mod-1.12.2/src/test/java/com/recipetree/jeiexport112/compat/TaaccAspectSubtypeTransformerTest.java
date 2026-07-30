package com.recipetree.jeiexport112.compat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

public final class TaaccAspectSubtypeTransformerTest {
    private static final String FIXTURE_ENV = "JEIEXPORT_TAACC_FIXTURE";
    private static final String TARGET_ENTRY =
            TaaccAspectSubtypeTransformer.TARGET_INTERNAL + ".class";
    private static final String NBT_COMPOUND = "net/minecraft/nbt/NBTTagCompound";
    private String previousProperty;

    @Before
    public void reset() {
        previousProperty = System.getProperty(TaaccAspectSubtypeConfiguration.ENABLE_PROPERTY);
        System.setProperty(TaaccAspectSubtypeConfiguration.ENABLE_PROPERTY, "true");
        TaaccAspectSubtypeTransformer.resetAppliedCountForTests();
    }

    @After
    public void restore() {
        TaaccAspectSubtypeTransformer.resetAppliedCountForTests();
        if (previousProperty == null) {
            System.clearProperty(TaaccAspectSubtypeConfiguration.ENABLE_PROPERTY);
        } else {
            System.setProperty(
                    TaaccAspectSubtypeConfiguration.ENABLE_PROPERTY, previousProperty
            );
        }
    }

    @Test
    public void replacesOnlyTheNullUnsafeNativeGetStringCall() {
        byte[] transformed = transform(exactSyntheticClass());

        assertEquals(1, TaaccAspectSubtypeTransformer.appliedCount());
        assertEquals(1, countCalls(
                transformed,
                "net/minecraft/item/ItemStack",
                "func_77978_p",
                "()Lnet/minecraft/nbt/NBTTagCompound;"
        ));
        assertEquals(0, countCalls(
                transformed,
                NBT_COMPOUND,
                "func_74779_i",
                "(Ljava/lang/String;)Ljava/lang/String;"
        ));
        assertEquals(1, countCalls(
                transformed,
                TaaccAspectSubtypeTransformer.GUARD_INTERNAL,
                TaaccAspectSubtypeTransformer.GUARD_METHOD,
                TaaccAspectSubtypeTransformer.GUARD_DESC
        ));
        assertEquals(11, executableSplitter(transformed).size());
    }

    @Test
    public void patchesTheExactAuditedTaaccArtifact() throws IOException {
        String fixturePath = System.getenv(FIXTURE_ENV);
        Assume.assumeTrue(
                "Set " + FIXTURE_ENV + " to test the audited TAACC 0.0.3 artifact",
                fixturePath != null && !fixturePath.trim().isEmpty()
        );
        File fixture = new File(fixturePath);
        try (JarFile jar = new JarFile(fixture)) {
            JarEntry entry = jar.getJarEntry(TARGET_ENTRY);
            assertNotNull("Missing " + TARGET_ENTRY + " in " + fixture, entry);
            byte[] transformed;
            try (InputStream input = jar.getInputStream(entry)) {
                transformed = transform(readAll(input));
            }
            assertEquals(1, countCalls(
                    transformed,
                    TaaccAspectSubtypeTransformer.GUARD_INTERNAL,
                    TaaccAspectSubtypeTransformer.GUARD_METHOD,
                    TaaccAspectSubtypeTransformer.GUARD_DESC
            ));
            assertEquals(0, countCalls(
                    transformed,
                    NBT_COMPOUND,
                    "func_74779_i",
                    "(Ljava/lang/String;)Ljava/lang/String;"
            ));
        }
    }

    @Test
    public void unrelatedClassReturnsTheIdenticalByteArray() {
        byte[] source = exactSyntheticClass();
        assertSame(
                source,
                new TaaccAspectSubtypeTransformer().transform(
                        "example.Unrelated", "example.Unrelated", source
                )
        );
        assertEquals(0, TaaccAspectSubtypeTransformer.appliedCount());
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsNullTargetBytes() {
        new TaaccAspectSubtypeTransformer().transform(
                TaaccAspectSubtypeTransformer.TARGET_DOTTED,
                TaaccAspectSubtypeTransformer.TARGET_DOTTED,
                null
        );
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsClassFileVersionDrift() {
        ClassNode node = readClass(exactSyntheticClass());
        node.version = Opcodes.V1_7;
        transform(writeClass(node));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsOwnerDrift() {
        ClassNode node = readClass(exactSyntheticClass());
        node.name = "example/ChangedTaaccPlugin";
        transform(writeClass(node));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsMethodAccessDrift() {
        ClassNode node = readClass(exactSyntheticClass());
        splitter(node).access = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
        transform(writeClass(node));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsAspectConstantDrift() {
        ClassNode node = readClass(exactSyntheticClass());
        for (AbstractInsnNode instruction : executable(splitter(node))) {
            if (instruction instanceof LdcInsnNode &&
                    "Aspect".equals(((LdcInsnNode) instruction).cst)) {
                ((LdcInsnNode) instruction).cst = "ChangedAspect";
            }
        }
        transform(writeClass(node));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsNativeCallOwnerDrift() {
        ClassNode node = readClass(exactSyntheticClass());
        for (AbstractInsnNode instruction : executable(splitter(node))) {
            if (instruction instanceof MethodInsnNode &&
                    "func_74779_i".equals(((MethodInsnNode) instruction).name)) {
                ((MethodInsnNode) instruction).owner = "example/ChangedCompound";
            }
        }
        transform(writeClass(node));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsExtraExecutableInstruction() {
        ClassNode node = readClass(exactSyntheticClass());
        splitter(node).instructions.insert(new InsnNode(Opcodes.NOP));
        transform(writeClass(node));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsChangedJumpTarget() {
        ClassNode node = readClass(exactSyntheticClass());
        MethodNode method = splitter(node);
        List<AbstractInsnNode> code = executable(method);
        ((JumpInsnNode) code.get(6)).label = new LabelNode();
        method.instructions.add(((JumpInsnNode) code.get(6)).label);
        transform(writeClass(node));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsDuplicateTransformation() {
        byte[] source = exactSyntheticClass();
        transform(source);
        transform(source);
    }

    private static byte[] transform(byte[] source) {
        return new TaaccAspectSubtypeTransformer().transform(
                TaaccAspectSubtypeTransformer.TARGET_DOTTED,
                TaaccAspectSubtypeTransformer.TARGET_DOTTED,
                source
        );
    }

    private static byte[] exactSyntheticClass() {
        ClassNode node = new ClassNode();
        node.version = Opcodes.V1_8;
        node.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER;
        node.name = TaaccAspectSubtypeTransformer.TARGET_INTERNAL;
        node.superName = "java/lang/Object";
        node.sourceFile = "TaaccJeiPlugin.java";
        node.interfaces.add("mezz/jei/api/IModPlugin");
        node.invisibleAnnotations = new ArrayList<AnnotationNode>();
        node.invisibleAnnotations.add(new AnnotationNode("Lmezz/jei/api/JEIPlugin;"));

        MethodNode constructor = new MethodNode(
                Opcodes.ACC_PUBLIC, "<init>", "()V", null, null
        );
        constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        constructor.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false
        ));
        constructor.instructions.add(new InsnNode(Opcodes.RETURN));
        constructor.maxStack = 1;
        constructor.maxLocals = 1;
        node.methods.add(constructor);

        MethodNode splitter = new MethodNode(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                TaaccAspectSubtypeTransformer.TARGET_METHOD,
                TaaccAspectSubtypeTransformer.TARGET_METHOD_DESC,
                null,
                null
        );
        LabelNode nonNull = new LabelNode();
        LabelNode result = new LabelNode();
        splitter.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        splitter.instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "net/minecraft/item/ItemStack",
                "func_77978_p",
                "()Lnet/minecraft/nbt/NBTTagCompound;",
                false
        ));
        splitter.instructions.add(new LdcInsnNode("Aspect"));
        splitter.instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                NBT_COMPOUND,
                "func_74779_i",
                "(Ljava/lang/String;)Ljava/lang/String;",
                false
        ));
        splitter.instructions.add(new VarInsnNode(Opcodes.ASTORE, 1));
        splitter.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        splitter.instructions.add(new JumpInsnNode(Opcodes.IFNONNULL, nonNull));
        splitter.instructions.add(new LdcInsnNode(""));
        splitter.instructions.add(new JumpInsnNode(Opcodes.GOTO, result));
        splitter.instructions.add(nonNull);
        splitter.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        splitter.instructions.add(result);
        splitter.instructions.add(new InsnNode(Opcodes.ARETURN));
        splitter.maxStack = 2;
        splitter.maxLocals = 2;
        node.methods.add(splitter);

        MethodNode registration = new MethodNode(
                Opcodes.ACC_PUBLIC,
                "registerItemSubtypes",
                "(Lmezz/jei/api/ISubtypeRegistry;)V",
                null,
                null
        );
        registration.instructions.add(new InsnNode(Opcodes.RETURN));
        registration.maxStack = 0;
        registration.maxLocals = 2;
        node.methods.add(registration);
        return writeClass(node);
    }

    private static MethodNode splitter(ClassNode node) {
        for (MethodNode method : node.methods) {
            if (TaaccAspectSubtypeTransformer.TARGET_METHOD.equals(method.name) &&
                    TaaccAspectSubtypeTransformer.TARGET_METHOD_DESC.equals(method.desc)) {
                return method;
            }
        }
        throw new AssertionError("Missing synthetic splitter");
    }

    private static List<AbstractInsnNode> executableSplitter(byte[] bytes) {
        return executable(splitter(readClass(bytes)));
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
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                 instruction != null; instruction = instruction.getNext()) {
                if (!(instruction instanceof MethodInsnNode)) {
                    continue;
                }
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (owner.equals(call.owner) && name.equals(call.name) && desc.equals(call.desc)) {
                    count++;
                }
            }
        }
        return count;
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

    private static byte[] readAll(InputStream input) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }
}
