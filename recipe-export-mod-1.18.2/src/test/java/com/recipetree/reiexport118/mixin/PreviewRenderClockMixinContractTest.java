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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PreviewRenderClockMixinContractTest {
    private static final String MIXIN_PACKAGE =
            "com/recipetree/reiexport118/mixin/";
    private static final String CLOCK =
            "com/recipetree/reiexport118/compat/Mm2PreviewRenderClock";

    @TempDir
    Path temporaryDirectory;

    @Test
    void createMixingCallPathTerminatesAtThePinnedTickAndPartialTickClock() throws IOException {
        ClassNode holder = readPinned(Mm2DeterminismContract.CREATE_ANIMATION_TICK_HOLDER);
        MethodNode renderTime = method(holder, "getRenderTime", "()F");
        assertEquals(1, callCount(renderTime, Opcodes.INVOKESTATIC, holder.name,
                "getTicks", "()I"));
        assertEquals(1, callCount(renderTime, Opcodes.INVOKESTATIC, holder.name,
                "getPartialTicks", "()F"));

        ClassNode kinetics = readPinned(Mm2DeterminismContract.CREATE_ANIMATED_KINETICS);
        MethodNode angle = method(kinetics, "getCurrentAngle", "()F");
        assertEquals(1, callCount(angle, Opcodes.INVOKESTATIC, holder.name,
                "getRenderTime", "()F"));

        ClassNode mixer = readPinned(Mm2DeterminismContract.CREATE_ANIMATED_MIXER);
        MethodNode draw = method(mixer, "draw",
                "(Lcom/mojang/blaze3d/vertex/PoseStack;II)V");
        assertEquals(1, callCount(draw, Opcodes.INVOKESTATIC, holder.name,
                "getRenderTime", "()F"));
        assertEquals(2, callCount(draw, Opcodes.INVOKESTATIC, mixer.name,
                "getCurrentAngle", "()F"));

        ClassNode category = readPinned(Mm2DeterminismContract.CREATE_MIXING_CATEGORY);
        assertEquals(1, classCallCount(category, Opcodes.INVOKEVIRTUAL, mixer.name,
                "draw", "(Lcom/mojang/blaze3d/vertex/PoseStack;II)V"));
    }

    @Test
    void mekanismInjectionCallPathTerminatesAtThePinnedJeiWallClock() throws IOException {
        ClassNode category = readPinned(
                Mm2DeterminismContract.MEKANISM_CHEMICAL_INJECTION_CATEGORY);
        assertEquals(1, classCallCount(category, Opcodes.INVOKEVIRTUAL,
                category.name, "addSimpleProgress",
                "(Lmekanism/client/gui/element/progress/ProgressType;II)"
                        + "Lmekanism/client/gui/element/progress/GuiProgress;"));

        ClassNode base = readPinned(Mm2DeterminismContract.MEKANISM_BASE_RECIPE_CATEGORY);
        MethodNode timerFactory = method(base, "getSimpleProgressTimer",
                "()Lmekanism/client/gui/element/progress/IProgressInfoHandler;");
        assertEquals(1, callCount(timerFactory, Opcodes.INVOKEINTERFACE,
                "mezz/jei/api/helpers/IGuiHelper", "createTickTimer",
                "(IIZ)Lmezz/jei/api/gui/ITickTimer;"));
        MethodNode progress = method(base, "lambda$getSimpleProgressTimer$4", "()D");
        assertEquals(1, callCount(progress, Opcodes.INVOKEINTERFACE,
                "mezz/jei/api/gui/ITickTimer", "getValue", "()I"));

        ClassNode timer = readPinned(Mm2DeterminismContract.JEI_GUI_HELPER_TICK_TIMER);
        assertEquals(1, classCallCount(timer, Opcodes.INVOKESTATIC,
                "java/lang/System", "currentTimeMillis", "()J"));
        assertEquals(1, opcodeCount(method(timer, "getValue", "()I"), Opcodes.LREM));
    }

    @Test
    void multiblockedCallPathUsesThePinnedLowDragJeiProgressSupplier() throws IOException {
        ClassNode display = readPinned(Mm2DeterminismContract.MULTIBLOCKED_RECIPE_DISPLAY);
        MethodNode factory = methodByName(display, "lambda$new$0");
        assertEquals(2, fieldCount(factory, Opcodes.GETSTATIC,
                "com/lowdragmc/lowdraglib/gui/widget/ProgressWidget",
                "JEIProgress", "Ljava/util/function/DoubleSupplier;"));

        ClassNode progressWidget = readPinned(Mm2DeterminismContract.LOW_DRAG_PROGRESS_WIDGET);
        MethodNode supplier = method(progressWidget, "lambda$static$0", "()D");
        assertEquals(1, callCount(supplier, Opcodes.INVOKESTATIC,
                "java/lang/System", "currentTimeMillis", "()J"));
        assertEquals(1, opcodeCount(supplier, Opcodes.LREM));
        assertEquals(1, classCallCount(progressWidget, Opcodes.INVOKEINTERFACE,
                "java/util/function/DoubleSupplier", "getAsDouble", "()D")
                - callCount(method(progressWidget, "initWidget", "()V"),
                Opcodes.INVOKEINTERFACE, "java/util/function/DoubleSupplier",
                "getAsDouble", "()D")
                - callCount(method(progressWidget, "detectAndSendChanges", "()V"),
                Opcodes.INVOKEINTERFACE, "java/util/function/DoubleSupplier",
                "getAsDouble", "()D"));
        assertEquals(1, callCount(method(progressWidget, "drawInBackground",
                        "(Lcom/mojang/blaze3d/vertex/PoseStack;IIF)V"),
                Opcodes.INVOKEINTERFACE, "java/util/function/DoubleSupplier",
                "getAsDouble", "()D"));
    }

    @Test
    void compiledMixinsPreserveUpstreamClocksOutsideCaptureAndMatchTargetStaticness()
            throws IOException {
        ClassNode create = readResource(MIXIN_PACKAGE + "CreateAnimationTickHolderMixin.class");
        assertMixinTarget(create,
                Mm2DeterminismContract.CREATE_ANIMATION_TICK_HOLDER.className());
        MethodNode createHandler = methodByName(create,
                "reiexport$canonicalRecipeRenderTime");
        assertTrue((createHandler.access & Opcodes.ACC_STATIC) != 0,
                "static target method requires a static injection handler");
        AnnotationNode inject = annotation(annotations(createHandler),
                "Lorg/spongepowered/asm/mixin/injection/Inject;");
        assertEquals(List.of("getRenderTime()F"), value(inject, "method"));
        assertEquals(true, value(inject, "cancellable"));
        assertEquals(1, value(inject, "require"));
        assertEquals(1, callCount(createHandler, Opcodes.INVOKESTATIC, CLOCK,
                "isCaptureActive", "()Z"));
        assertEquals(1, callCount(createHandler, Opcodes.INVOKESTATIC, CLOCK,
                "createRenderTime", "()F"));

        ClassNode jei = readResource(MIXIN_PACKAGE + "JeiCompatTickTimerMixin.class");
        assertMixinTarget(jei, Mm2DeterminismContract.JEI_GUI_HELPER_TICK_TIMER.className());
        MethodNode jeiHandler = methodByName(jei,
                "reiexport$canonicalRecipeWallMillis");
        assertFalse((jeiHandler.access & Opcodes.ACC_STATIC) != 0,
                "instance target method requires an instance redirect handler");
        assertWallClockRedirect(jeiHandler, "getValue()I", "JEI_COMPAT_TICK_TIMER");

        ClassNode lowDrag = readResource(MIXIN_PACKAGE + "LowDragProgressWidgetMixin.class");
        assertMixinTarget(lowDrag, Mm2DeterminismContract.LOW_DRAG_PROGRESS_WIDGET.className());
        MethodNode lowDragHandler = methodByName(lowDrag,
                "reiexport$canonicalRecipeWallMillis");
        assertTrue((lowDragHandler.access & Opcodes.ACC_STATIC) != 0,
                "static synthetic target method requires a static redirect handler");
        assertWallClockRedirect(lowDragHandler, "lambda$static$0()D",
                "LOW_DRAG_PROGRESS");
    }

    @Test
    void pinsMixinsAndModsToOnlyTheExactMm2Request() throws Exception {
        assertEquals("0.5.1.i", Mm2DeterminismContract.CREATE.version());
        assertEquals("1.18.2-1.0.10", Mm2DeterminismContract.MULTIBLOCKED.version());
        assertTrue(Mm2DeterminismContract.LIFECYCLE_SIGNATURE.contains(
                Mm2DeterminismContract.CREATE));
        assertTrue(Mm2DeterminismContract.LIFECYCLE_SIGNATURE.contains(
                Mm2DeterminismContract.MULTIBLOCKED));
        assertTrue(Mm2DeterminismContract.CLASS_PINS.contains(
                Mm2DeterminismContract.LOW_DRAG_PROGRESS_WIDGET));
        assertTrue(Mm2DeterminismContract.CLASS_PINS.contains(
                Mm2DeterminismContract.JEI_GUI_HELPER_TICK_TIMER));

        Path absent = Files.createDirectory(temporaryDirectory.resolve("absent"));
        ReiExportMixinConfigPlugin disabled = new ReiExportMixinConfigPlugin(absent);
        assertFalse(disabled.shouldApplyMixin(
                Mm2DeterminismContract.CREATE_ANIMATION_TICK_HOLDER.className(),
                "com.recipetree.reiexport118.mixin.CreateAnimationTickHolderMixin"));

        Path exact = Files.createDirectory(temporaryDirectory.resolve("exact"));
        Files.writeString(exact.resolve(Mm2ExportRequestScope.REQUEST_NAME),
                "{\"profile\":\"" + Mm2ExportRequestScope.PROFILE
                        + "\",\"packName\":\"" + Mm2ExportRequestScope.PACK_NAME + "\"}");
        ReiExportMixinConfigPlugin enabled = new ReiExportMixinConfigPlugin(exact);
        verifyEnabled(enabled, Mm2DeterminismContract.CREATE_ANIMATION_TICK_HOLDER,
                "CreateAnimationTickHolderMixin");
        verifyEnabled(enabled, Mm2DeterminismContract.JEI_GUI_HELPER_TICK_TIMER,
                "JeiCompatTickTimerMixin");
        verifyEnabled(enabled, Mm2DeterminismContract.LOW_DRAG_PROGRESS_WIDGET,
                "LowDragProgressWidgetMixin");

        String config;
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("reiexport.mixins.json")) {
            assertNotNull(input);
            config = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertEquals(1, occurrences(config, "\"CreateAnimationTickHolderMixin\""));
        assertEquals(1, occurrences(config, "\"JeiCompatTickTimerMixin\""));
        assertEquals(1, occurrences(config, "\"LowDragProgressWidgetMixin\""));
    }

    private static void assertWallClockRedirect(
            MethodNode handler,
            String targetMethod,
            String sourceField
    ) {
        AnnotationNode redirect = annotation(annotations(handler),
                "Lorg/spongepowered/asm/mixin/injection/Redirect;");
        assertEquals(List.of(targetMethod), value(redirect, "method"));
        assertEquals(1, value(redirect, "require"));
        assertEquals(false, value(redirect, "remap"));
        AnnotationNode at = (AnnotationNode) value(redirect, "at");
        assertEquals("INVOKE", value(at, "value"));
        assertEquals("Ljava/lang/System;currentTimeMillis()J", value(at, "target"));
        assertEquals(1, callCount(handler, Opcodes.INVOKESTATIC,
                "java/lang/System", "currentTimeMillis", "()J"));
        assertEquals(1, callCount(handler, Opcodes.INVOKESTATIC, CLOCK,
                "isCaptureActive", "()Z"));
        assertEquals(1, fieldCount(handler, Opcodes.GETSTATIC,
                "com/recipetree/reiexport118/compat/Mm2PreviewRenderClock$Source",
                sourceField,
                "Lcom/recipetree/reiexport118/compat/Mm2PreviewRenderClock$Source;"));
        assertEquals(1, callCount(handler, Opcodes.INVOKESTATIC, CLOCK,
                "wallMillis",
                "(Lcom/recipetree/reiexport118/compat/Mm2PreviewRenderClock$Source;)J"));
    }

    private static void assertMixinTarget(ClassNode mixin, String expected) {
        AnnotationNode annotation = annotation(mixin.invisibleAnnotations,
                "Lorg/spongepowered/asm/mixin/Mixin;");
        assertEquals(List.of(expected), value(annotation, "targets"));
        assertEquals(false, value(annotation, "remap"));
    }

    private static void verifyEnabled(
            ReiExportMixinConfigPlugin plugin,
            Mm2DeterminismContract.ClassPin pin,
            String simpleMixinName
    ) {
        String mixin = "com.recipetree.reiexport118.mixin." + simpleMixinName;
        assertTrue(plugin.shouldApplyMixin(pin.className(), mixin));
        assertThrows(IllegalStateException.class, () ->
                plugin.shouldApplyMixin("drifted.Target", mixin));
    }

    private static ClassNode readPinned(Mm2DeterminismContract.ClassPin pin)
            throws IOException {
        List<byte[]> exact = new ArrayList<>();
        List<String> observed = new ArrayList<>();
        for (URL url : Collections.list(
                PreviewRenderClockMixinContractTest.class.getClassLoader()
                        .getResources(pin.resource()))) {
            try (InputStream input = url.openStream()) {
                byte[] bytecode = input.readAllBytes();
                String hash = sha256(bytecode);
                observed.add(url + " sha256=" + hash);
                if (pin.sha256().equals(hash)) {
                    exact.add(bytecode);
                }
            }
        }
        assertFalse(observed.isEmpty(), "missing pinned resource " + pin.resource());
        assertEquals(observed.size(), exact.size(),
                "every visible resource must match the pin; observed=" + observed);
        return read(exact.get(0));
    }

    private static ClassNode readResource(String resource) throws IOException {
        try (InputStream input = PreviewRenderClockMixinContractTest.class
                .getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, resource);
            return read(input.readAllBytes());
        }
    }

    private static ClassNode read(byte[] bytecode) {
        ClassNode node = new ClassNode();
        new ClassReader(bytecode).accept(node,
                ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return node;
    }

    private static MethodNode method(ClassNode owner, String name, String descriptor) {
        List<MethodNode> matches = owner.methods.stream()
                .filter(candidate -> name.equals(candidate.name))
                .filter(candidate -> descriptor.equals(candidate.desc))
                .toList();
        assertEquals(1, matches.size(), owner.name + "." + name + descriptor);
        return matches.get(0);
    }

    private static MethodNode methodByName(ClassNode owner, String name) {
        List<MethodNode> matches = owner.methods.stream()
                .filter(candidate -> name.equals(candidate.name))
                .toList();
        assertEquals(1, matches.size(), owner.name + "." + name);
        return matches.get(0);
    }

    private static int classCallCount(
            ClassNode owner,
            int opcode,
            String callOwner,
            String name,
            String descriptor
    ) {
        return owner.methods.stream().mapToInt(method ->
                callCount(method, opcode, callOwner, name, descriptor)).sum();
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

    private static int fieldCount(
            MethodNode method,
            int opcode,
            String owner,
            String name,
            String descriptor
    ) {
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

    private static int opcodeCount(MethodNode method, int opcode) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction.getOpcode() == opcode) {
                count++;
            }
        }
        return count;
    }

    private static List<AnnotationNode> annotations(MethodNode method) {
        List<AnnotationNode> annotations = new ArrayList<>();
        if (method.visibleAnnotations != null) {
            annotations.addAll(method.visibleAnnotations);
        }
        if (method.invisibleAnnotations != null) {
            annotations.addAll(method.invisibleAnnotations);
        }
        return annotations;
    }

    private static AnnotationNode annotation(List<AnnotationNode> annotations, String descriptor) {
        assertNotNull(annotations, descriptor);
        List<AnnotationNode> matches = annotations.stream()
                .filter(candidate -> descriptor.equals(candidate.desc))
                .toList();
        assertEquals(1, matches.size(), descriptor);
        return matches.get(0);
    }

    private static Object value(AnnotationNode annotation, String name) {
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

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static int occurrences(String value, String needle) {
        int count = 0;
        for (int index = value.indexOf(needle); index >= 0;
             index = value.indexOf(needle, index + needle.length())) {
            count++;
        }
        return count;
    }
}
