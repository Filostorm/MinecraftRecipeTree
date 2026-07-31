package com.recipetree.reiexport118.compat;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LowDragSceneCacheBytecodeContractTest {
    private static final String WORLD_RENDERER =
            "com/lowdragmc/lowdraglib/client/scene/WorldSceneRenderer";
    private static final String SCENE_WIDGET =
            "com/lowdragmc/lowdraglib/gui/widget/SceneWidget";
    private static final String PATTERN_WIDGET =
            "com/lowdragmc/multiblocked/api/gui/controller/structure/PatternWidget";

    @Test
    void reiPatternPopulationProvenanceEndsAtTheBenignNeedMarker() throws Exception {
        ClassNode display = exactClass(
                LowDragFboViewportContract.MULTIBLOCK_INFO_DISPLAY_RESOURCE,
                LowDragFboViewportContract.MULTIBLOCK_INFO_DISPLAY_SHA256);
        MethodNode supplier = method(
                display,
                "lambda$new$0",
                "(Lcom/lowdragmc/multiblocked/api/definition/ControllerDefinition;)"
                        + "Lcom/lowdragmc/multiblocked/api/gui/controller/structure/PatternWidget;");
        MethodInsnNode patternConstructor = invocation(
                supplier, PATTERN_WIDGET, "<init>",
                "(Lcom/lowdragmc/multiblocked/api/definition/ControllerDefinition;Z)V");
        assertEquals(Opcodes.ICONST_1, previousOpcode(patternConstructor).getOpcode(),
                "REI must construct PatternWidget(definition, true)");
        invocation(supplier, PATTERN_WIDGET, "reset", "(I)V");

        ClassNode pattern = exactClass(
                LowDragFboViewportContract.PATTERN_WIDGET_RESOURCE,
                LowDragFboViewportContract.PATTERN_WIDGET_SHA256);
        MethodNode reset = method(pattern, "reset", "(I)V");
        invocation(
                reset,
                PATTERN_WIDGET,
                "setupScene",
                "(Lcom/lowdragmc/multiblocked/api/gui/controller/structure/PatternWidget$MBPattern;)V");
        MethodNode setupScene = method(
                pattern,
                "setupScene",
                "(Lcom/lowdragmc/multiblocked/api/gui/controller/structure/PatternWidget$MBPattern;)V");
        invocation(
                setupScene,
                SCENE_WIDGET,
                "setRenderedCore",
                "(Ljava/util/Collection;Lcom/lowdragmc/lowdraglib/client/scene/ISceneRenderHook;)"
                        + "Lcom/lowdragmc/lowdraglib/gui/widget/SceneWidget;");

        ClassNode scene = exactClass(
                LowDragFboViewportContract.SCENE_WIDGET_RESOURCE,
                LowDragFboViewportContract.SCENE_WIDGET_SHA256);
        MethodNode setRenderedCore = method(
                scene,
                "setRenderedCore",
                "(Ljava/util/Collection;Lcom/lowdragmc/lowdraglib/client/scene/ISceneRenderHook;)"
                        + "Lcom/lowdragmc/lowdraglib/gui/widget/SceneWidget;");
        MethodInsnNode invalidate = invocation(
                setRenderedCore, SCENE_WIDGET, "needCompileCache", "()V");
        assertEquals(Opcodes.ALOAD, nextOpcode(invalidate).getOpcode());
        assertEquals(Opcodes.ARETURN, nextOpcode(nextOpcode(invalidate)).getOpcode(),
                "client setRenderedCore must invalidate immediately before its return");

        MethodNode sceneInvalidation = method(scene, "needCompileCache", "()V");
        invocation(
                sceneInvalidation,
                WORLD_RENDERER,
                "needCompileCache",
                "()Lcom/lowdragmc/lowdraglib/client/scene/WorldSceneRenderer;");

        ClassNode world = exactClass(
                LowDragFboViewportContract.WORLD_RENDERER_RESOURCE,
                LowDragFboViewportContract.WORLD_RENDERER_SHA256);
        MethodNode worldInvalidation = method(
                world,
                "needCompileCache",
                "()Lcom/lowdragmc/lowdraglib/client/scene/WorldSceneRenderer;");
        assertTrue(hasFieldRead(
                        worldInvalidation,
                        WORLD_RENDERER + "$CacheState",
                        "NEED",
                        "Lcom/lowdragmc/lowdraglib/client/scene/WorldSceneRenderer$CacheState;"),
                "needCompileCache must select the exact NEED marker");
    }

    @Test
    void disabledUseCacheBranchesAroundTheAsyncCacheRenderer() throws Exception {
        ClassNode world = exactClass(
                LowDragFboViewportContract.WORLD_RENDERER_RESOURCE,
                LowDragFboViewportContract.WORLD_RENDERER_SHA256);
        MethodNode drawWorld = method(world, "drawWorld", "()V");
        FieldInsnNode useCache = fieldRead(drawWorld, WORLD_RENDERER, "useCache", "Z");
        AbstractInsnNode branchNode = nextOpcode(useCache);
        assertTrue(branchNode instanceof JumpInsnNode);
        JumpInsnNode disabledBranch = (JumpInsnNode) branchNode;
        assertEquals(Opcodes.IFEQ, disabledBranch.getOpcode(),
                "useCache=false must jump around renderCacheBuffer");

        MethodInsnNode asyncRenderer = invocation(
                drawWorld,
                WORLD_RENDERER,
                "renderCacheBuffer",
                "(Lnet/minecraft/client/Minecraft;F)V");
        int branchIndex = drawWorld.instructions.indexOf(disabledBranch);
        int asyncIndex = drawWorld.instructions.indexOf(asyncRenderer);
        int synchronousTargetIndex = drawWorld.instructions.indexOf(disabledBranch.label);
        assertTrue(branchIndex < asyncIndex && asyncIndex < synchronousTargetIndex,
                "the false branch must skip the sole asynchronous cache-render call");
        assertTrue(hasFieldReadAfter(
                        drawWorld,
                        disabledBranch.label,
                        WORLD_RENDERER,
                        "renderedBlocksMap",
                        "Ljava/util/Map;"),
                "the false branch must continue into direct rendered-block geometry");
    }

    private static ClassNode exactClass(String resource, String expectedSha256) throws Exception {
        byte[] bytecode = resource(resource);
        assertEquals(expectedSha256, sha256(bytecode), "bytecode pin drift for " + resource);
        ClassNode node = new ClassNode();
        new ClassReader(bytecode).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return node;
    }

    private static byte[] resource(String name) throws IOException {
        ClassLoader loader = LowDragSceneCacheBytecodeContractTest.class.getClassLoader();
        try (InputStream input = loader.getResourceAsStream(name)) {
            assertNotNull(input, "missing exact MM2 test resource " + name);
            return input.readAllBytes();
        }
    }

    private static String sha256(byte[] bytes) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static MethodNode method(ClassNode owner, String name, String descriptor) {
        return owner.methods.stream()
                .filter(method -> name.equals(method.name) && descriptor.equals(method.desc))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "missing method " + owner.name + "." + name + descriptor));
    }

    private static MethodInsnNode invocation(
            MethodNode method,
            String owner,
            String name,
            String descriptor
    ) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode invocation
                    && owner.equals(invocation.owner)
                    && name.equals(invocation.name)
                    && descriptor.equals(invocation.desc)) {
                return invocation;
            }
        }
        throw new AssertionError("missing invocation " + owner + "." + name + descriptor
                + " in " + method.name + method.desc);
    }

    private static FieldInsnNode fieldRead(
            MethodNode method,
            String owner,
            String name,
            String descriptor
    ) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode field
                    && field.getOpcode() == Opcodes.GETFIELD
                    && owner.equals(field.owner)
                    && name.equals(field.name)
                    && descriptor.equals(field.desc)) {
                return field;
            }
        }
        throw new AssertionError("missing field read " + owner + "." + name
                + " in " + method.name + method.desc);
    }

    private static boolean hasFieldRead(
            MethodNode method,
            String owner,
            String name,
            String descriptor
    ) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode field
                    && (field.getOpcode() == Opcodes.GETFIELD
                    || field.getOpcode() == Opcodes.GETSTATIC)
                    && owner.equals(field.owner)
                    && name.equals(field.name)
                    && descriptor.equals(field.desc)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasFieldReadAfter(
            MethodNode method,
            AbstractInsnNode start,
            String owner,
            String name,
            String descriptor
    ) {
        for (AbstractInsnNode instruction = start; instruction != null;
             instruction = instruction.getNext()) {
            if (instruction instanceof FieldInsnNode field
                    && field.getOpcode() == Opcodes.GETFIELD
                    && owner.equals(field.owner)
                    && name.equals(field.name)
                    && descriptor.equals(field.desc)) {
                return true;
            }
        }
        return false;
    }

    private static AbstractInsnNode previousOpcode(AbstractInsnNode instruction) {
        AbstractInsnNode cursor = instruction.getPrevious();
        while (cursor != null && cursor.getOpcode() < 0) {
            cursor = cursor.getPrevious();
        }
        assertNotNull(cursor, "missing preceding opcode");
        return cursor;
    }

    private static AbstractInsnNode nextOpcode(AbstractInsnNode instruction) {
        AbstractInsnNode cursor = instruction.getNext();
        while (cursor != null && cursor.getOpcode() < 0) {
            cursor = cursor.getNext();
        }
        assertNotNull(cursor, "missing following opcode");
        return cursor;
    }
}
