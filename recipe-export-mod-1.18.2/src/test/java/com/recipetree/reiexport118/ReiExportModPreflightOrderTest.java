package com.recipetree.reiexport118;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class ReiExportModPreflightOrderTest {
    private static final String COMPATIBILITY =
            "com/recipetree/reiexport118/compat/Mm2DeterminismCompatibility";
    private static final String VALIDATE = "validateBeforeReiRegistration";

    @Test
    void exactMm2BinaryPreflightRunsOnceBeforeClientSetupAndCanDisableRegistration()
            throws IOException {
        ClassNode type;
        try (InputStream input = ReiExportModPreflightOrderTest.class.getClassLoader()
                .getResourceAsStream("com/recipetree/reiexport118/ReiExportMod.class")) {
            assertNotNull(input, "compiled ReiExportMod");
            type = new ClassNode();
            new ClassReader(input).accept(type, 0);
        }

        MethodNode constructor = method(type, "<init>", "()V");
        List<MethodInsnNode> constructorPreflights = calls(constructor, COMPATIBILITY, VALIDATE);
        assertEquals(1, constructorPreflights.size());
        assertEquals("()Z", constructorPreflights.get(0).desc);

        AbstractInsnNode branch = nextExecutable(constructorPreflights.get(0));
        JumpInsnNode enabledBranch = assertInstanceOf(JumpInsnNode.class, branch);
        assertEquals(Opcodes.IFNE, enabledBranch.getOpcode(),
                "a false exact-request preflight must return before listener/event registration");
        assertEquals(Opcodes.RETURN, nextExecutable(enabledBranch).getOpcode());

        MethodNode clientSetup = type.methods.stream()
                .filter(candidate -> "onClientSetup".equals(candidate.name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing client setup callback"));
        assertEquals(0, calls(clientSetup, COMPATIBILITY, VALIDATE).size(),
                "creative exemplars are constructed before FML client setup");

        long total = type.methods.stream()
                .flatMap(candidate -> calls(candidate, COMPATIBILITY, VALIDATE).stream())
                .count();
        assertEquals(1, total, "MM2 compatibility preflight must arm exactly once");
    }

    private static MethodNode method(ClassNode type, String name, String descriptor) {
        return type.methods.stream()
                .filter(candidate -> name.equals(candidate.name) && descriptor.equals(candidate.desc))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing method " + name + descriptor));
    }

    private static List<MethodInsnNode> calls(MethodNode method, String owner, String name) {
        List<MethodInsnNode> matching = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && owner.equals(call.owner)
                    && name.equals(call.name)) {
                matching.add(call);
            }
        }
        return List.copyOf(matching);
    }

    private static AbstractInsnNode nextExecutable(AbstractInsnNode instruction) {
        AbstractInsnNode current = instruction.getNext();
        while (current != null && current.getOpcode() < 0) {
            current = current.getNext();
        }
        if (current == null) {
            throw new AssertionError("instruction has no executable successor");
        }
        return current;
    }
}
