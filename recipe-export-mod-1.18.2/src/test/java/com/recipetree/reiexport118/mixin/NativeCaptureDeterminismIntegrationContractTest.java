package com.recipetree.reiexport118.mixin;

import com.recipetree.reiexport118.compat.IndustrialForegoingOreTagOrderContract;
import com.recipetree.reiexport118.compat.Mm2DeterminismContract;
import com.recipetree.reiexport118.compat.Mm2OffscreenGlintClockContract;
import com.recipetree.reiexport118.compat.Mm2SpiritShaderGameTimeContract;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NativeCaptureDeterminismIntegrationContractTest {
    private static final String ROOT = "com/recipetree/reiexport118/";
    private static final String COMPAT = ROOT + "compat/";

    @TempDir
    Path temporaryDirectory;

    @Test
    void offscreenScopeOwnsTheCompleteNativeDrawAndDeferredFlush() throws IOException {
        ClassNode renderer = readResource(ROOT + "OffscreenRenderer.class");
        MethodNode capture = method(renderer, "capture",
                "(IIILcom/recipetree/reiexport118/compat/"
                        + "LowDragFboViewportCompatibility$CaptureMode;Ljava/lang/String;ZI"
                        + "Ljava/util/function/Supplier;"
                        + "Ljava/util/function/Consumer;)"
                        + "Lcom/mojang/blaze3d/platform/NativeImage;");
        assertEquals(1, callCount(capture, Opcodes.INVOKESTATIC,
                COMPAT + "Mm2UnattendedUiScope",
                "requireCaptureBaseline", "(Ljava/lang/String;)V"));
        assertEquals(1, callCount(capture, Opcodes.INVOKESTATIC,
                COMPAT + "Mm2LightmapReadiness",
                "requireCaptureBaseline", "(Ljava/lang/String;)V"));
        assertEquals(1, callCount(capture, Opcodes.INVOKEINTERFACE,
                "java/util/function/Supplier", "get", "()Ljava/lang/Object;"));
        assertEquals(1, callCount(capture, Opcodes.INVOKESTATIC,
                COMPAT + "Mm2OffscreenGlintClock",
                "beginOffscreenCapture",
                "(Ljava/lang/String;)Lcom/recipetree/reiexport118/compat/"
                        + "Mm2OffscreenGlintClock$CaptureScope;"));
        assertEquals(1, callCount(capture, Opcodes.INVOKESTATIC,
                COMPAT + "Mm2SpiritEntityRenderDeterminism",
                "beginNativeCapture",
                "(Ljava/lang/String;)Lcom/recipetree/reiexport118/compat/"
                        + "Mm2SpiritEntityRenderDeterminism$CaptureScope;"));
        assertEquals(1, callCount(capture, Opcodes.INVOKEVIRTUAL,
                renderer.name,
                "captureOwnedTarget",
                "(IIILcom/recipetree/reiexport118/compat/"
                        + "LowDragFboViewportCompatibility$CaptureMode;Ljava/lang/String;ZI"
                        + "Ljava/util/function/Consumer;)"
                        + "Lcom/mojang/blaze3d/platform/NativeImage;"));
        int uiBaseline = callIndex(capture, COMPAT + "Mm2UnattendedUiScope",
                "requireCaptureBaseline", "(Ljava/lang/String;)V");
        int lightmapBaseline = callIndex(capture, COMPAT + "Mm2LightmapReadiness",
                "requireCaptureBaseline", "(Ljava/lang/String;)V");
        int nativeContext = callIndex(capture,
                "java/util/function/Supplier", "get", "()Ljava/lang/Object;");
        int ownedCapture = callIndex(capture, renderer.name, "captureOwnedTarget",
                "(IIILcom/recipetree/reiexport118/compat/"
                        + "LowDragFboViewportCompatibility$CaptureMode;Ljava/lang/String;ZI"
                        + "Ljava/util/function/Consumer;)"
                        + "Lcom/mojang/blaze3d/platform/NativeImage;");
        assertTrue(uiBaseline < lightmapBaseline);
        assertTrue(lightmapBaseline < nativeContext);
        assertTrue(nativeContext < ownedCapture);

        MethodNode owned = methodByName(renderer, "captureOwnedTarget");
        assertEquals(1, callCount(owned, Opcodes.INVOKEVIRTUAL,
                "net/minecraft/client/renderer/MultiBufferSource$BufferSource",
                "endBatch", "()V"));
        assertEquals(0, callCount(owned, Opcodes.INVOKESTATIC,
                COMPAT + "Mm2OffscreenGlintClock", "beginOffscreenCapture",
                "(Ljava/lang/String;)Lcom/recipetree/reiexport118/compat/"
                        + "Mm2OffscreenGlintClock$CaptureScope;"));
    }

    @Test
    void vanillaPotionPositiveControlIsDeclaredBeforeItsNativeRender() throws IOException {
        ClassNode catalog = readResource(ROOT + "ItemCatalog.class");
        MethodNode lambda = catalog.methods.stream()
                .filter(candidate -> callCount(candidate, Opcodes.INVOKESTATIC,
                        COMPAT + "Mm2OffscreenGlintClock",
                        "requireKnownSampleInterception",
                        "(Ljava/lang/String;)V") == 1)
                .findFirst()
                .orElseThrow();
        assertEquals(1, callCount(lambda, Opcodes.INVOKEVIRTUAL,
                "net/minecraft/world/item/ItemStack", "hasFoil", "()Z"));
        assertEquals(1, callCount(lambda, Opcodes.INVOKESTATIC,
                ROOT + "Naming", "hash128",
                "(Ljava/lang/String;)Ljava/lang/String;"));
        assertTrue(callIndex(lambda, COMPAT + "Mm2OffscreenGlintClock",
                "requireKnownSampleInterception", "(Ljava/lang/String;)V")
                < callIndex(lambda,
                "me/shedaniel/rei/api/common/entry/EntryStack",
                "render",
                "(Lcom/mojang/blaze3d/vertex/PoseStack;"
                        + "Lme/shedaniel/math/Rectangle;IIF)V"));
    }

    @Test
    void eachExportOwnsABaselineAndAllPublicationAudits() throws IOException {
        ClassNode job = readResource(ROOT + "ExportJob.class");
        MethodNode constructor = methodByName(job, "<init>");
        MethodNode finish = method(job, "finish", "()V");
        assertEquals(1, callCount(constructor, Opcodes.INVOKESTATIC,
                COMPAT + "Mm2OffscreenGlintClock", "auditSnapshot",
                "()Lcom/recipetree/reiexport118/compat/"
                        + "Mm2OffscreenGlintClock$AuditSnapshot;"));
        assertEquals(1, callCount(constructor, Opcodes.INVOKESTATIC,
                COMPAT + "Mm2SpiritEntityRenderDeterminism", "auditSnapshot",
                "()Lcom/recipetree/reiexport118/compat/"
                        + "Mm2SpiritEntityRenderDeterminism$AuditSnapshot;"));
        assertEquals(1L, job.fields.stream()
                .filter(field -> "spiritAuditBaseline".equals(field.name))
                .filter(field -> ("Lcom/recipetree/reiexport118/compat/"
                        + "Mm2SpiritEntityRenderDeterminism$AuditSnapshot;")
                        .equals(field.desc))
                .count());
        assertEquals(1, callCount(finish, Opcodes.INVOKESTATIC,
                COMPAT + "IndustrialForegoingOreTagOrderCompatibility",
                "requireObservedBeforePublication", "()V"));
        assertEquals(1, callCount(finish, Opcodes.INVOKESTATIC,
                COMPAT + "IndustrialForegoingRecipeListOrderCompatibility",
                "requireObservedBeforePublication", "()V"));
        assertEquals(1, callCount(finish, Opcodes.INVOKESTATIC,
                COMPAT + "Mm2OffscreenGlintClock",
                "requireKnownSampleInterceptionSince",
                "(Lcom/recipetree/reiexport118/compat/"
                        + "Mm2OffscreenGlintClock$AuditSnapshot;Ljava/lang/String;)V"));
        assertEquals(1, callCount(finish, Opcodes.INVOKESTATIC,
                COMPAT + "Mm2SpiritEntityRenderDeterminism",
                "requireObservedSince",
                "(Lcom/recipetree/reiexport118/compat/"
                        + "Mm2SpiritEntityRenderDeterminism$AuditSnapshot;"
                        + "Ljava/lang/String;)V"));
        assertEquals(1, callCount(finish, Opcodes.INVOKESTATIC,
                COMPAT + "Mm2LightmapReadiness",
                "requireHealthyBeforePublication", "()V"));
        assertEquals(1, callCount(finish, Opcodes.INVOKESTATIC,
                COMPAT + "Mm2UnattendedUiScope",
                "requireHealthyBeforePublication", "()V"));
        assertEquals(1, callCount(finish, Opcodes.INVOKESTATIC,
                COMPAT + "Mm2UnattendedUiScope",
                "releaseIfActive", "(Ljava/lang/String;)V"));
    }

    @Test
    void unattendedUiScopeArmsBeforeWorldBootstrapAndAuditsTheAtomicClaim()
            throws IOException {
        ClassNode bootstrap = readResource(ROOT + "WorldBootstrap.class");
        MethodNode bootstrapTick = method(bootstrap, "tick", "()V");
        assertEquals(1, callCount(bootstrapTick, Opcodes.INVOKESTATIC,
                COMPAT + "Mm2UnattendedUiScope",
                "armForExactRequest", "(Ljava/nio/file/Path;)V"));

        ClassNode coordinator = readResource(ROOT + "ExportCoordinator.class");
        MethodNode coordinatorTick = method(coordinator, "tick", "()V");
        assertEquals(1, callCount(coordinatorTick, Opcodes.INVOKESTATIC,
                COMPAT + "Mm2UnattendedUiScope",
                "armForExactRequest", "(Ljava/nio/file/Path;)V"));
        assertEquals(1, callCount(coordinatorTick, Opcodes.INVOKESTATIC,
                COMPAT + "Mm2UnattendedUiScope",
                "requireReadyForClaim", "()V"));
    }

    @Test
    void auditedPinsPluginRoutingAndRequiredMixinConfigAreSeparatedButComplete()
            throws Exception {
        assertTrue(Mm2DeterminismContract.CLASS_PINS.contains(
                Mm2DeterminismContract.IF_JEI_CUSTOM_PLUGIN));
        assertTrue(Mm2DeterminismContract.CLASS_PINS.contains(
                Mm2DeterminismContract.SPIRIT_JEI_ENTITY_RENDERER));

        verifyPluginAudit(
                "com.recipetree.reiexport118.mixin.IndustrialForegoingOreTagOrderMixin",
                IndustrialForegoingOreTagOrderContract.TARGET_CLASS,
                IndustrialForegoingOreTagOrderContract.TARGET_RESOURCE,
                IndustrialForegoingOreTagOrderContract.TARGET_CLASS_SHA256,
                null);
        verifyPluginAudit(
                "com.recipetree.reiexport118.mixin.SpiritJeiEntityRendererMixin",
                Mm2DeterminismContract.SPIRIT_JEI_ENTITY_RENDERER.className(),
                Mm2DeterminismContract.SPIRIT_JEI_ENTITY_RENDERER.resource(),
                Mm2DeterminismContract.SPIRIT_JEI_ENTITY_RENDERER.sha256(),
                null);
        verifyPluginAudit(
                "com.recipetree.reiexport118.mixin.RenderStateShardGlintClockMixin",
                Mm2OffscreenGlintClockContract.RENDER_STATE_SHARD.className(),
                Mm2OffscreenGlintClockContract.RENDER_STATE_SHARD.resource(),
                Mm2OffscreenGlintClockContract.RENDER_STATE_SHARD.sha256(),
                Mm2OffscreenGlintClockContract.PRODUCTION_RESOURCE_STAGE);
        verifyPluginAudit(
                "com.recipetree.reiexport118.mixin.BufferUploaderSpiritShaderClockMixin",
                Mm2SpiritShaderGameTimeContract.BUFFER_UPLOADER.className(),
                Mm2SpiritShaderGameTimeContract.BUFFER_UPLOADER.resource(),
                Mm2SpiritShaderGameTimeContract.BUFFER_UPLOADER.sha256(),
                Mm2SpiritShaderGameTimeContract.PRODUCTION_RESOURCE_STAGE);

        Path absent = Files.createDirectory(temporaryDirectory.resolve("absent"));
        ReiExportMixinConfigPlugin disabled = new ReiExportMixinConfigPlugin(absent);
        assertFalse(disabled.shouldApplyMixin(
                Mm2OffscreenGlintClockContract.RENDER_STATE_SHARD.className(),
                "com.recipetree.reiexport118.mixin.RenderStateShardGlintClockMixin"));

        String config;
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("reiexport.mixins.json")) {
            assertNotNull(input);
            config = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertEquals(1, occurrences(config, "\"IndustrialForegoingOreTagOrderMixin\""));
        assertEquals(1, occurrences(config, "\"RenderStateShardGlintClockMixin\""));
        assertEquals(1, occurrences(config, "\"BufferUploaderSpiritShaderClockMixin\""));
        assertEquals(1, occurrences(config, "\"SpiritJeiEntityRendererMixin\""));
    }

    private static void verifyPluginAudit(
            String mixin,
            String expectedClass,
            String expectedResource,
            String expectedHash,
            String expectedStage
    ) throws Exception {
        Method routing = ReiExportMixinConfigPlugin.class
                .getDeclaredMethod("auditedTarget", String.class);
        routing.setAccessible(true);
        Object target = routing.invoke(null, mixin);
        assertNotNull(target, mixin);
        assertEquals(expectedClass, recordValue(target, "className"));
        assertEquals(expectedResource, recordValue(target, "resource"));
        assertEquals(expectedHash, recordValue(target, "sha256"));
        assertEquals(true, recordValue(target, "requiredTarget"));
        assertEquals(expectedStage, recordValue(target, "resourceStage"));
    }

    private static Object recordValue(Object record, String methodName) throws Exception {
        Method method = record.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        return method.invoke(record);
    }

    private static ClassNode readResource(String path) throws IOException {
        try (InputStream input = NativeCaptureDeterminismIntegrationContractTest.class
                .getClassLoader().getResourceAsStream(path)) {
            assertNotNull(input, path);
            ClassNode node = new ClassNode();
            new ClassReader(input.readAllBytes()).accept(node,
                    ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
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

    private static int callIndex(
            MethodNode method, String owner, String name, String descriptor) {
        int index = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && owner.equals(call.owner)
                    && name.equals(call.name)
                    && descriptor.equals(call.desc)) {
                return index;
            }
            index++;
        }
        throw new AssertionError("missing call " + owner + "." + name + descriptor);
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
