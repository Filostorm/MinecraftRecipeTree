package com.recipetree.reiexport118.mixin;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OffscreenCullBaselineContractTest {
    private static final String ROOT = "com/recipetree/reiexport118/";

    @Test
    void guiBaselineRestoresMinecraftsCullEnabledDriverInvariant() throws IOException {
        ClassNode renderer = readResource(ROOT + "OffscreenRenderer.class");
        MethodNode baseline = method(renderer, "establishGuiBaseline", "()V");
        MethodNode restore = method(renderer, "restore", "()V");

        assertEquals(1, callCount(
                baseline,
                Opcodes.INVOKESTATIC,
                "com/mojang/blaze3d/systems/RenderSystem",
                "enableCull",
                "()V"));
        assertEquals(0, callCount(
                baseline,
                Opcodes.INVOKESTATIC,
                "com/mojang/blaze3d/systems/RenderSystem",
                "disableCull",
                "()V"));
        assertEquals(1, callCount(
                baseline,
                Opcodes.INVOKESTATIC,
                "org/lwjgl/opengl/GL11",
                "glEnable",
                "(I)V"));
        assertEquals(1, callCount(
                baseline,
                Opcodes.INVOKESTATIC,
                "org/lwjgl/opengl/GL11",
                "glCullFace",
                "(I)V"));
        assertEquals(1, callCount(
                baseline,
                Opcodes.INVOKESTATIC,
                "org/lwjgl/opengl/GL11",
                "glFrontFace",
                "(I)V"));
        assertTrue(integerConstantCount(baseline, 2884) >= 1, "GL_CULL_FACE");
        assertTrue(integerConstantCount(baseline, 1029) >= 1, "GL_BACK");
        assertTrue(integerConstantCount(baseline, 2304) >= 1, "GL_CW");
        assertEquals(0, integerConstantCount(baseline, 2305), "GL_CCW");
        assertEquals(1, callCount(
                restore,
                Opcodes.INVOKESTATIC,
                "org/lwjgl/opengl/GL11",
                "glFrontFace",
                "(I)V"));
        assertTrue(integerConstantCount(restore, 2305) >= 1, "restore GL_CCW");
        assertEquals(0, integerConstantCount(restore, 2304), "restore must not retain GL_CW");
        assertTrue(callIndex(
                restore,
                Opcodes.INVOKESTATIC,
                "com/mojang/blaze3d/systems/RenderSystem",
                "setProjectionMatrix",
                "(Lcom/mojang/math/Matrix4f;)V") < callIndex(
                restore,
                Opcodes.INVOKESTATIC,
                "org/lwjgl/opengl/GL11",
                "glFrontFace",
                "(I)V"));
    }

    @Test
    void claimedExportRunsTheFailClosedTranslucentCullCalibrationOnce()
            throws IOException {
        ClassNode coordinator = readResource(ROOT + "ExportCoordinator.class");
        assertEquals(1, classCallCount(
                coordinator,
                Opcodes.INVOKEVIRTUAL,
                ROOT + "OffscreenRenderer",
                "validateTranslucentCullBaseline",
                "()V"));

        ClassNode renderer = readResource(ROOT + "OffscreenRenderer.class");
        MethodNode calibrationDraw = renderer.methods.stream()
                .filter(candidate -> callCount(
                        candidate,
                        Opcodes.INVOKESTATIC,
                        "com/mojang/blaze3d/systems/RenderSystem",
                        "disableCull",
                        "()V") == 1)
                .filter(candidate -> callCount(
                        candidate,
                        Opcodes.INVOKESTATIC,
                        renderer.name,
                        "drawCullCalibrationQuads",
                        "(Lcom/mojang/blaze3d/vertex/PoseStack;IIZ)V") == 2)
                .findFirst()
                .orElseThrow();
        assertEquals(1, callCount(
                calibrationDraw,
                Opcodes.INVOKESTATIC,
                "org/lwjgl/opengl/GL11",
                "glDisable",
                "(I)V"));
        assertEquals(1, callCount(
                calibrationDraw,
                Opcodes.INVOKESTATIC,
                "com/mojang/blaze3d/systems/RenderSystem",
                "disableCull",
                "()V"));
        assertEquals(1, callCount(
                calibrationDraw,
                Opcodes.INVOKESTATIC,
                renderer.name,
                "establishGuiBaseline",
                "()V"));
        assertEquals(2, callCount(
                calibrationDraw,
                Opcodes.INVOKESTATIC,
                renderer.name,
                "drawCullCalibrationQuads",
                "(Lcom/mojang/blaze3d/vertex/PoseStack;IIZ)V"));
    }

    private static ClassNode readResource(String path) throws IOException {
        try (InputStream input = OffscreenCullBaselineContractTest.class
                .getClassLoader().getResourceAsStream(path)) {
            assertNotNull(input, path);
            ClassNode node = new ClassNode();
            new ClassReader(input.readAllBytes()).accept(node, 0);
            return node;
        }
    }

    private static MethodNode method(ClassNode owner, String name, String descriptor) {
        List<MethodNode> methods = owner.methods.stream()
                .filter(candidate -> name.equals(candidate.name))
                .filter(candidate -> descriptor.equals(candidate.desc))
                .toList();
        assertEquals(1, methods.size(), owner.name + "." + name + descriptor);
        return methods.get(0);
    }

    private static int callCount(
            MethodNode method,
            int opcode,
            String owner,
            String name,
            String descriptor
    ) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && call.getOpcode() == opcode
                    && owner.equals(call.owner)
                    && name.equals(call.name)
                    && descriptor.equals(call.desc)) {
                count++;
            }
        }
        return count;
    }

    private static int callIndex(
            MethodNode method,
            int opcode,
            String owner,
            String name,
            String descriptor
    ) {
        int index = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && call.getOpcode() == opcode
                    && owner.equals(call.owner)
                    && name.equals(call.name)
                    && descriptor.equals(call.desc)) {
                return index;
            }
            index++;
        }
        throw new AssertionError("Missing call " + owner + "." + name + descriptor);
    }

    private static int classCallCount(
            ClassNode owner,
            int opcode,
            String targetOwner,
            String name,
            String descriptor
    ) {
        return owner.methods.stream()
                .mapToInt(method -> callCount(
                        method, opcode, targetOwner, name, descriptor))
                .sum();
    }

    private static int integerConstantCount(MethodNode method, int value) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof LdcInsnNode constant
                    && Integer.valueOf(value).equals(constant.cst)) {
                count++;
            }
            if (instruction instanceof IntInsnNode constant
                    && constant.operand == value) {
                count++;
            }
        }
        return count;
    }
}
