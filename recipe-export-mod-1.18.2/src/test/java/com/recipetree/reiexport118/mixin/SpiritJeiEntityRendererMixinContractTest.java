package com.recipetree.reiexport118.mixin;

import com.recipetree.reiexport118.compat.Mm2DeterminismContract;
import com.recipetree.reiexport118.compat.Mm2ExportRequestScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SpiritJeiEntityRendererMixinContractTest {
    private static final String TARGET =
            "me/codexadrian/spirit/compat/jei/ingredients/EntityRenderer";
    private static final String MIXIN =
            "com/recipetree/reiexport118/mixin/SpiritJeiEntityRendererMixin";
    private static final String RENDER_DESCRIPTOR =
            "(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/Entity;"
                    + "Lnet/minecraft/world/level/Level;FFFF)V";
    private static final String DETERMINISM =
            "com/recipetree/reiexport118/compat/Mm2SpiritEntityRenderDeterminism";
    private static final String SPIRIT_JEI_PLUGIN =
            "me/codexadrian/spirit/compat/jei/SpiritPlugin";

    @TempDir
    Path temporaryDirectory;

    @Test
    void pinnedSpiritRendererContainsExactlyTheTwoAuditedProcessAgeSeams()
            throws IOException {
        ClassNode target = readResource(
                Mm2DeterminismContract.SPIRIT_JEI_ENTITY_RENDERER.resource());
        MethodNode render = method(target, "renderEntity", RENDER_DESCRIPTOR);
        assertEquals(1, fieldCount(render, Opcodes.GETFIELD,
                "net/minecraft/client/player/LocalPlayer", "f_19797_", "I"));
        assertEquals(1, fieldCount(render, Opcodes.PUTFIELD,
                "net/minecraft/world/entity/Entity", "f_19797_", "I"));
        assertEquals(1, callCount(render, Opcodes.INVOKEVIRTUAL,
                "net/minecraft/client/Minecraft", "m_91296_", "()F"));
    }

    @Test
    void exactSpiritJeiPluginRegistersThePinnedRendererForItsIngredientType()
            throws IOException {
        ClassNode plugin = readResource(SPIRIT_JEI_PLUGIN + ".class");
        MethodNode register = method(
                plugin,
                "registerIngredients",
                "(Lmezz/jei/api/registration/IModIngredientRegistration;)V");
        assertEquals(1, typeCount(register, Opcodes.NEW, TARGET));
        assertEquals(1, callCount(register, Opcodes.INVOKESPECIAL,
                TARGET, "<init>", "()V"));
        assertEquals(1, callCount(register, Opcodes.INVOKEINTERFACE,
                "mezz/jei/api/registration/IModIngredientRegistration",
                "register",
                "(Lmezz/jei/api/ingredients/IIngredientType;Ljava/util/Collection;"
                        + "Lmezz/jei/api/ingredients/IIngredientHelper;"
                        + "Lmezz/jei/api/ingredients/IIngredientRenderer;)V"));
    }

    @Test
    void compiledMixinRedirectsEachExactSeamOnceAndPreservesUpstreamPaths()
            throws IOException {
        ClassNode mixin = readResource(MIXIN + ".class");
        AnnotationNode target = annotation(mixin.invisibleAnnotations,
                "Lorg/spongepowered/asm/mixin/Mixin;");
        assertEquals(List.of(Mm2DeterminismContract.SPIRIT_JEI_ENTITY_RENDERER
                .className()), value(target, "targets"));
        assertEquals(false, value(target, "remap"));

        MethodNode tick = methodByName(mixin,
                "reiexport$canonicalEntityTickCount");
        assertTrue((tick.access & Opcodes.ACC_STATIC) != 0);
        assertRedirect(tick, "FIELD",
                "Lnet/minecraft/client/player/LocalPlayer;f_19797_:I");
        assertEquals(1, fieldCount(tick, Opcodes.GETFIELD,
                "net/minecraft/client/player/LocalPlayer", "tickCount", "I"));
        assertEquals(1, callCount(tick, Opcodes.INVOKESTATIC, DETERMINISM,
                "entityTickCount", "(I)I"));

        MethodNode frame = methodByName(mixin, "reiexport$canonicalFrameTime");
        assertTrue((frame.access & Opcodes.ACC_STATIC) != 0);
        assertRedirect(frame, "INVOKE",
                "Lnet/minecraft/client/Minecraft;m_91296_()F");
        assertEquals(1, callCount(frame, Opcodes.INVOKEVIRTUAL,
                "net/minecraft/client/Minecraft", "getFrameTime", "()F"));
        assertEquals(1, callCount(frame, Opcodes.INVOKESTATIC, DETERMINISM,
                "frameTime", "(F)F"));
    }

    @Test
    void exactClassPinMixinSelectionAndRequiredConfigAreAllRequestScoped()
            throws Exception {
        assertTrue(Mm2DeterminismContract.LIFECYCLE_SIGNATURE.contains(
                Mm2DeterminismContract.SPIRIT));
        assertTrue(Mm2DeterminismContract.CLASS_PINS.contains(
                Mm2DeterminismContract.SPIRIT_JEI_ENTITY_RENDERER));

        Path absent = Files.createDirectory(temporaryDirectory.resolve("absent"));
        ReiExportMixinConfigPlugin disabled = new ReiExportMixinConfigPlugin(absent);
        assertFalse(disabled.shouldApplyMixin(
                Mm2DeterminismContract.SPIRIT_JEI_ENTITY_RENDERER.className(),
                MIXIN.replace('/', '.')));

        Path exact = Files.createDirectory(temporaryDirectory.resolve("exact"));
        Files.writeString(exact.resolve(Mm2ExportRequestScope.REQUEST_NAME),
                "{\"profile\":\"" + Mm2ExportRequestScope.PROFILE
                        + "\",\"packName\":\"" + Mm2ExportRequestScope.PACK_NAME + "\"}");
        ReiExportMixinConfigPlugin enabled = new ReiExportMixinConfigPlugin(exact);
        assertTrue(enabled.shouldApplyMixin(
                Mm2DeterminismContract.SPIRIT_JEI_ENTITY_RENDERER.className(),
                MIXIN.replace('/', '.')));
        assertThrows(IllegalStateException.class, () -> enabled.shouldApplyMixin(
                "drifted.Target", MIXIN.replace('/', '.')));

        String config;
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("reiexport.mixins.json")) {
            assertNotNull(input);
            config = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertEquals(1, occurrences(config, "\"SpiritJeiEntityRendererMixin\""));
    }

    private static void assertRedirect(MethodNode handler, String value, String target) {
        AnnotationNode redirect = annotation(allAnnotations(handler),
                "Lorg/spongepowered/asm/mixin/injection/Redirect;");
        assertEquals(List.of("renderEntity" + RENDER_DESCRIPTOR),
                annotationValue(redirect, "method"));
        assertEquals(1, annotationValue(redirect, "require"));
        assertEquals(false, annotationValue(redirect, "remap"));
        AnnotationNode at = (AnnotationNode) annotationValue(redirect, "at");
        assertEquals(value, annotationValue(at, "value"));
        assertEquals(target, annotationValue(at, "target"));
    }

    private static ClassNode readResource(String path) throws IOException {
        try (InputStream input = SpiritJeiEntityRendererMixinContractTest.class
                .getClassLoader().getResourceAsStream(path)) {
            assertNotNull(input, path);
            ClassNode node = new ClassNode();
            new ClassReader(input.readAllBytes()).accept(node,
                    ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
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

    private static MethodNode methodByName(ClassNode owner, String name) {
        List<MethodNode> methods = owner.methods.stream()
                .filter(candidate -> name.equals(candidate.name))
                .toList();
        assertEquals(1, methods.size(), owner.name + "." + name);
        return methods.get(0);
    }

    private static int fieldCount(
            MethodNode method, int opcode, String owner, String name, String descriptor) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode field
                    && field.getOpcode() == opcode
                    && owner.equals(field.owner)
                    && name.equals(field.name)
                    && descriptor.equals(field.desc)) {
                count++;
            }
        }
        return count;
    }

    private static int callCount(
            MethodNode method, int opcode, String owner, String name, String descriptor) {
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

    private static int typeCount(MethodNode method, int opcode, String descriptor) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof TypeInsnNode type
                    && type.getOpcode() == opcode
                    && descriptor.equals(type.desc)) {
                count++;
            }
        }
        return count;
    }

    private static AnnotationNode annotation(
            List<AnnotationNode> annotations, String descriptor) {
        return annotations.stream()
                .filter(candidate -> descriptor.equals(candidate.desc))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing annotation " + descriptor));
    }

    private static List<AnnotationNode> allAnnotations(MethodNode method) {
        List<AnnotationNode> annotations = new ArrayList<>();
        if (method.visibleAnnotations != null) {
            annotations.addAll(method.visibleAnnotations);
        }
        if (method.invisibleAnnotations != null) {
            annotations.addAll(method.invisibleAnnotations);
        }
        return annotations;
    }

    private static Object value(AnnotationNode annotation, String name) {
        return annotationValue(annotation, name);
    }

    private static Object annotationValue(AnnotationNode annotation, String name) {
        if (annotation.values == null) {
            return null;
        }
        for (int index = 0; index < annotation.values.size(); index += 2) {
            if (name.equals(annotation.values.get(index))) {
                return annotation.values.get(index + 1);
            }
        }
        return null;
    }

    private static int occurrences(String source, String target) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(target, index)) >= 0) {
            count++;
            index += target.length();
        }
        return count;
    }
}
