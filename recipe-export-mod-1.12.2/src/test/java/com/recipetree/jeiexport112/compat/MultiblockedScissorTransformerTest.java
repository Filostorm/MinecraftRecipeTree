package com.recipetree.jeiexport112.compat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.junit.Assume;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public final class MultiblockedScissorTransformerTest {
    private static final String DOTTED =
            "com.cleanroommc.multiblocked.client.util.RenderUtils";
    private static final String INTERNAL =
            "com/cleanroommc/multiblocked/client/util/RenderUtils";
    private static final String ENTRY = INTERNAL + ".class";
    private static final String GL11 = "org/lwjgl/opengl/GL11";
    private static final String BRIDGE =
            "com/recipetree/jeiexport112/compat/MultiblockedScissorBridge";
    private static final String FIXTURE_ENV = "JEIEXPORT_MULTIBLOCKED_SHADER_FIXTURE";

    @Test
    public void redirectsOnlyTheExactValidatedScissorCall() {
        byte[] transformed = transform(exactClass());

        assertEquals(0, countCalls(transformed, GL11, "glScissor"));
        assertEquals(1, countCalls(transformed, BRIDGE, "glScissor"));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsAnyExecutableOpcodeDrift() {
        ClassNode classNode = readClass(exactClass());
        MethodNode target = findMethod(classNode, "applyScissor", "(IIII)V");
        target.instructions.insertBefore(target.instructions.getLast(),
                new InsnNode(Opcodes.NOP));
        transform(writeClass(classNode));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsClassSchemaDrift() {
        ClassNode classNode = readClass(exactClass());
        classNode.version = Opcodes.V1_7;
        transform(writeClass(classNode));
    }

    @Test
    public void redirectsTheAuditedMultiblocked080Artifact() throws IOException {
        String fixturePath = System.getenv(FIXTURE_ENV);
        Assume.assumeTrue(
                "Set " + FIXTURE_ENV + " to test the audited multiblocked-0.8.0 artifact",
                fixturePath != null && !fixturePath.trim().isEmpty()
        );
        try (JarFile jar = new JarFile(new File(fixturePath))) {
            JarEntry entry = jar.getJarEntry(ENTRY);
            assertNotNull("Missing " + ENTRY, entry);
            byte[] source;
            try (InputStream input = jar.getInputStream(entry)) {
                source = readAll(input);
            }
            byte[] transformed = transform(source);
            assertEquals(0, countCalls(transformed, GL11, "glScissor"));
            assertEquals(1, countCalls(transformed, BRIDGE, "glScissor"));
        }
    }

    private static byte[] exactClass() {
        ClassNode node = new ClassNode();
        node.version = Opcodes.V1_8;
        node.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER;
        node.name = INTERNAL;
        node.superName = "java/lang/Object";
        node.sourceFile = "RenderUtils.java";
        node.fields.add(new FieldNode(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                "scissorFrameStack", "Ljava/util/Stack;", "Ljava/util/Stack<[I>;", null));

        addShell(node, Opcodes.ACC_PUBLIC, "<init>", "()V");
        addShell(node, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "useScissor",
                "(IIIILjava/lang/Runnable;)V");
        addShell(node, Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                "peekFirstScissorOrFullScreen", "()[I");
        addShell(node, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "pushScissorFrame", "(IIII)V");
        addShell(node, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "popScissorFrame", "()V");
        node.methods.add(exactApplyScissor());
        addShell(node, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "renderBlockOverLay",
                "(Lnet/minecraft/util/math/BlockPos;FFFF)V");
        addShell(node, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "renderCubeFace",
                "(Lnet/minecraft/client/renderer/BufferBuilder;DDDDDDFFFF)V");
        addShell(node, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "useLightMap",
                "(FFLjava/lang/Runnable;)V");
        addShell(node, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "moveToFace",
                "(DDDLnet/minecraft/util/EnumFacing;)V");
        addShell(node, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "rotateToFace",
                "(Lnet/minecraft/util/EnumFacing;Lnet/minecraft/util/EnumFacing;)V");
        addShell(node, Opcodes.ACC_STATIC, "<clinit>", "()V");
        return writeClass(node);
    }

    private static MethodNode exactApplyScissor() {
        MethodNode method = new MethodNode(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                "applyScissor", "(IIII)V", null, null);
        method.instructions.add(call(Opcodes.INVOKESTATIC,
                "net/minecraft/client/Minecraft", "func_71410_x",
                "()Lnet/minecraft/client/Minecraft;"));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD,
                "net/minecraft/client/Minecraft", "field_71456_v",
                "Lnet/minecraft/client/gui/GuiIngame;"));
        method.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST,
                "net/minecraftforge/client/GuiIngameForge"));
        method.instructions.add(call(Opcodes.INVOKEVIRTUAL,
                "net/minecraftforge/client/GuiIngameForge", "getResolution",
                "()Lnet/minecraft/client/gui/ScaledResolution;"));
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 4));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 4));
        method.instructions.add(call(Opcodes.INVOKEVIRTUAL,
                "net/minecraft/client/gui/ScaledResolution", "func_78325_e", "()I"));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 5));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 4));
        method.instructions.add(call(Opcodes.INVOKEVIRTUAL,
                "net/minecraft/client/gui/ScaledResolution", "func_78328_b", "()I"));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.ISUB));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 3));
        method.instructions.add(new InsnNode(Opcodes.ISUB));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 6));
        int[] variables = {0, 5, 6, 5, 2, 5, 3, 5};
        for (int index = 0; index < variables.length; index += 2) {
            method.instructions.add(new VarInsnNode(Opcodes.ILOAD, variables[index]));
            method.instructions.add(new VarInsnNode(Opcodes.ILOAD, variables[index + 1]));
            method.instructions.add(new InsnNode(Opcodes.IMUL));
        }
        method.instructions.add(call(Opcodes.INVOKESTATIC, GL11, "glScissor", "(IIII)V"));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxStack = 5;
        method.maxLocals = 7;
        return method;
    }

    private static void addShell(ClassNode node, int access, String name, String descriptor) {
        MethodNode method = new MethodNode(access, name, descriptor, null, null);
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxStack = 0;
        method.maxLocals = 16;
        node.methods.add(method);
    }

    private static MethodInsnNode call(int opcode, String owner, String name, String desc) {
        return new MethodInsnNode(opcode, owner, name, desc, false);
    }

    private static byte[] transform(byte[] source) {
        return new ExportGraphicsTransformer().transform(DOTTED, DOTTED, source);
    }

    private static int countCalls(byte[] bytes, String owner, String name) {
        int count = 0;
        for (MethodNode method : readClass(bytes).methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                 instruction != null; instruction = instruction.getNext()) {
                if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    if (owner.equals(call.owner) && name.equals(call.name)) {
                        count++;
                    }
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

    private static MethodNode findMethod(ClassNode node, String name, String descriptor) {
        for (MethodNode method : node.methods) {
            if (name.equals(method.name) && descriptor.equals(method.desc)) {
                return method;
            }
        }
        throw new AssertionError("Missing " + name + descriptor);
    }

    private static byte[] writeClass(ClassNode node) {
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
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
}
