package com.recipetree.jeiexport112.compat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public final class ExportWorldStartupTransformerTest {
    private static final String BASE_DOTTED = "net.minecraft.server.MinecraftServer";
    private static final String BASE_INTERNAL = "net/minecraft/server/MinecraftServer";
    private static final String INTEGRATED_DOTTED =
            "net.minecraft.server.integrated.IntegratedServer";
    private static final String INTEGRATED_INTERNAL =
            "net/minecraft/server/integrated/IntegratedServer";
    private static final String METHOD_DESC =
            "(Ljava/lang/String;Ljava/lang/String;JLnet/minecraft/world/WorldType;Ljava/lang/String;)V";
    private static final String DIMENSION_MANAGER =
            "net/minecraftforge/common/DimensionManager";
    private static final String REPLACEMENT =
            "com/recipetree/jeiexport112/compat/ExportWorldStartupDimensions";
    private static final String ENUMERATION_DESC = "()[Ljava/lang/Integer;";

    @Test
    public void patchesOnlyBaseServerTargetCall() {
        byte[] source = syntheticClass(BASE_INTERNAL, "loadAllWorlds", 1, true, false);
        byte[] transformed = new ExportWorldStartupTransformer().transform(
                BASE_DOTTED, BASE_DOTTED, source
        );

        assertEquals(1, countCalls(transformed, "loadAllWorlds", REPLACEMENT));
        assertEquals(0, countCalls(transformed, "loadAllWorlds", DIMENSION_MANAGER));
        assertEquals(1, countCalls(transformed, "decoy", DIMENSION_MANAGER));
    }

    @Test
    public void patchesIntegratedServerSrgTargetCall() {
        byte[] transformed = new ExportWorldStartupTransformer().transform(
                INTEGRATED_DOTTED,
                INTEGRATED_DOTTED,
                syntheticClass(INTEGRATED_INTERNAL, "func_71247_a", 1, false, false)
        );

        assertEquals(1, countCalls(transformed, "func_71247_a", REPLACEMENT));
        assertEquals(0, countCalls(transformed, "func_71247_a", DIMENSION_MANAGER));
    }

    @Test
    public void patchesTheCompiledForge112BaseAndIntegratedClasses() throws IOException {
        assertCompiledForgeClassPatches(BASE_DOTTED, BASE_INTERNAL, "loadAllWorlds");
        assertCompiledForgeClassPatches(
                INTEGRATED_DOTTED,
                INTEGRATED_INTERNAL,
                "loadAllWorlds"
        );
    }

    @Test
    public void unrelatedClassIsReturnedWithoutRewriting() {
        byte[] source = syntheticClass("example/Unrelated", "loadAllWorlds", 1, false, false);
        assertSame(
                source,
                new ExportWorldStartupTransformer().transform(
                        "example.Unrelated", "example.Unrelated", source
                )
        );
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsMissingTargetMethod() {
        byte[] source = syntheticClass(BASE_INTERNAL, "otherMethod", 1, false, false);
        new ExportWorldStartupTransformer().transform(BASE_DOTTED, BASE_DOTTED, source);
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsBothDeobfuscatedAndSrgTargetMethods() {
        byte[] source = syntheticClass(BASE_INTERNAL, "loadAllWorlds", 1, false, true);
        new ExportWorldStartupTransformer().transform(BASE_DOTTED, BASE_DOTTED, source);
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsMissingExactCallSite() {
        byte[] source = syntheticClass(BASE_INTERNAL, "loadAllWorlds", 0, false, false);
        new ExportWorldStartupTransformer().transform(BASE_DOTTED, BASE_DOTTED, source);
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsDuplicateExactCallSites() {
        byte[] source = syntheticClass(BASE_INTERNAL, "loadAllWorlds", 2, false, false);
        new ExportWorldStartupTransformer().transform(BASE_DOTTED, BASE_DOTTED, source);
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsClassBytesForDifferentOwner() {
        byte[] source = syntheticClass(INTEGRATED_INTERNAL, "loadAllWorlds", 1, false, false);
        new ExportWorldStartupTransformer().transform(BASE_DOTTED, BASE_DOTTED, source);
    }

    private static byte[] syntheticClass(String owner, String methodName, int targetCalls,
                                         boolean decoyCall, boolean addSrgAlias) {
        ClassNode classNode = new ClassNode();
        classNode.version = Opcodes.V1_8;
        classNode.access = Opcodes.ACC_PUBLIC;
        classNode.name = owner;
        classNode.superName = "java/lang/Object";
        classNode.methods.add(method(methodName, METHOD_DESC, targetCalls));
        if (addSrgAlias) {
            classNode.methods.add(method("func_71247_a", METHOD_DESC, 1));
        }
        if (decoyCall) {
            classNode.methods.add(method("decoy", "()V", 1));
        }
        ClassWriter writer = new ClassWriter(0);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    private static MethodNode method(String name, String descriptor, int calls) {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, name, descriptor, null, null);
        for (int index = 0; index < calls; index++) {
            method.instructions.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    DIMENSION_MANAGER,
                    "getStaticDimensionIDs",
                    ENUMERATION_DESC,
                    false
            ));
            method.instructions.add(new InsnNode(Opcodes.POP));
        }
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxStack = calls == 0 ? 0 : 1;
        method.maxLocals = descriptor.equals("()V") ? 1 : 8;
        return method;
    }

    private static int countCalls(byte[] bytes, String methodName, String callOwner) {
        ClassNode classNode = new ClassNode();
        new ClassReader(bytes).accept(classNode, 0);
        List<MethodInsnNode> matches = new ArrayList<MethodInsnNode>();
        for (MethodNode method : classNode.methods) {
            if (!methodName.equals(method.name)) {
                continue;
            }
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                 instruction != null;
                 instruction = instruction.getNext()) {
                if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    if (callOwner.equals(call.owner) &&
                            "getStaticDimensionIDs".equals(call.name) &&
                            ENUMERATION_DESC.equals(call.desc)) {
                        matches.add(call);
                    }
                }
            }
        }
        return matches.size();
    }

    private static void assertCompiledForgeClassPatches(String dottedName, String internalName,
                                                        String methodName) throws IOException {
        byte[] source = readClasspathResource("/" + internalName + ".class");
        byte[] transformed = new ExportWorldStartupTransformer().transform(
                dottedName, dottedName, source
        );
        assertEquals(1, countCalls(transformed, methodName, REPLACEMENT));
        assertEquals(0, countCalls(transformed, methodName, DIMENSION_MANAGER));
    }

    private static byte[] readClasspathResource(String path) throws IOException {
        InputStream input = ExportWorldStartupTransformerTest.class.getResourceAsStream(path);
        if (input == null) {
            throw new IOException("Missing test classpath resource " + path);
        }
        try (InputStream closeable = input;
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = closeable.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }
}
