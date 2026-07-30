package com.recipetree.reiexport118.mixin;

import com.recipetree.reiexport118.compat.Mm2LightmapReadinessContract;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class Mm2LightmapReadinessMixinContractTest {
    private static final String ROOT = "com/recipetree/reiexport118/";
    private static final String COMPAT = ROOT + "compat/";
    private static final Path PRODUCTION_FORGE_CLIENT = Path.of(
            "..", "minecraft-1.18.2-runtime", "libraries",
            "net/minecraftforge/forge/1.18.2-40.2.17",
            "forge-1.18.2-40.2.17-client.jar");
    private static final Path UPSTREAM_SRG_CLIENT = Path.of(
            "..", "minecraft-1.18.2-runtime", "libraries",
            "net/minecraft/client/1.18.2-20220404.173914",
            "client-1.18.2-20220404.173914-srg.jar");

    @TempDir
    Path temporaryDirectory;

    @Test
    void pinsThePatchedProductionClassAndItsSingleReturnUpdateSeam() throws IOException {
        assertEquals(
                "Forge 40.2.17 binary-patched client JAR resource before Mixin application",
                Mm2LightmapReadinessContract.PRODUCTION_RESOURCE_STAGE);
        assertFalse(Files.isSymbolicLink(PRODUCTION_FORGE_CLIENT));
        assertTrue(Files.isRegularFile(PRODUCTION_FORGE_CLIENT, LinkOption.NOFOLLOW_LINKS));
        assertFalse(Files.isSymbolicLink(UPSTREAM_SRG_CLIENT));
        assertTrue(Files.isRegularFile(UPSTREAM_SRG_CLIENT, LinkOption.NOFOLLOW_LINKS));

        byte[] production;
        byte[] upstream;
        try (JarFile forge = new JarFile(PRODUCTION_FORGE_CLIENT.toFile());
             JarFile vanilla = new JarFile(UPSTREAM_SRG_CLIENT.toFile())) {
            production = readEntry(forge, Mm2LightmapReadinessContract.LIGHT_TEXTURE_RESOURCE);
            upstream = readEntry(vanilla, Mm2LightmapReadinessContract.LIGHT_TEXTURE_RESOURCE);
        }
        assertEquals(Mm2LightmapReadinessContract.LIGHT_TEXTURE_SHA256,
                sha256(production));
        assertNotEquals(Mm2LightmapReadinessContract.LIGHT_TEXTURE_SHA256,
                sha256(upstream));

        ClassNode lightTexture = read(production);
        assertEquals(Mm2LightmapReadinessContract.LIGHT_TEXTURE_CLASS.replace('.', '/'),
                lightTexture.name);
        List<FieldNode> pixels = lightTexture.fields.stream()
                .filter(field -> Mm2LightmapReadinessContract.LIGHT_PIXELS_FIELD
                        .equals(field.name))
                .filter(field -> "Lcom/mojang/blaze3d/platform/NativeImage;"
                        .equals(field.desc))
                .toList();
        assertEquals(1, pixels.size());
        assertTrue((pixels.get(0).access & Opcodes.ACC_PRIVATE) != 0);
        assertTrue((pixels.get(0).access & Opcodes.ACC_FINAL) != 0);

        MethodNode update = method(lightTexture, "m_109881_", "(F)V");
        assertEquals(1, opcodeCount(update, Opcodes.RETURN));
        assertEquals(1, callCount(update, Opcodes.INVOKEVIRTUAL,
                "net/minecraft/client/renderer/texture/DynamicTexture",
                "m_117985_", "()V"));
        MethodNode randomizingTick = method(lightTexture, "m_109880_", "()V");
        assertEquals(4, callCount(randomizingTick, Opcodes.INVOKESTATIC,
                "java/lang/Math", "random", "()D"));
    }

    @Test
    void canonicalAndConstructorPixelsDecodeToTheProvenChannelValues() {
        assertEquals("0xfffcfcfc", String.format("0x%08x",
                Mm2LightmapReadinessContract.EXPECTED_FULL_BRIGHT_ABGR));
        int canonical = Mm2LightmapReadinessContract.EXPECTED_FULL_BRIGHT_ABGR;
        assertEquals(255, canonical >>> 24 & 0xff);
        assertEquals(252, canonical >>> 16 & 0xff);
        assertEquals(252, canonical >>> 8 & 0xff);
        assertEquals(252, canonical & 0xff);
        assertNotEquals(canonical, 0xffffffff);
    }

    @Test
    void compiledMixinsUseTheExactAccessorAndCompletedUploadInjection() throws IOException {
        ClassNode accessor = readResource(ROOT + "mixin/LightTexturePixelsAccessor.class");
        MethodNode getPixels = method(accessor, "reiexport$getLightPixels",
                "()Lcom/mojang/blaze3d/platform/NativeImage;");
        AnnotationNode accessorAnnotation = annotation(annotations(getPixels),
                "Lorg/spongepowered/asm/mixin/gen/Accessor;");
        assertEquals(Mm2LightmapReadinessContract.LIGHT_PIXELS_FIELD,
                value(accessorAnnotation, "value"));
        assertEquals(false, value(accessorAnnotation, "remap"));

        ClassNode updateMixin = readResource(ROOT + "mixin/LightTextureUpdateMixin.class");
        MethodNode handler = method(updateMixin,
                "reiexport$recordCompletedLightmapUpdate",
                "(FLorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V");
        AnnotationNode inject = annotation(annotations(handler),
                "Lorg/spongepowered/asm/mixin/injection/Inject;");
        assertEquals(List.of(Mm2LightmapReadinessContract.UPDATE_LIGHT_TEXTURE_METHOD),
                value(inject, "method"));
        assertEquals(1, value(inject, "require"));
        assertEquals(false, value(inject, "remap"));
        @SuppressWarnings("unchecked")
        List<AnnotationNode> points = (List<AnnotationNode>) value(inject, "at");
        assertEquals(1, points.size());
        AnnotationNode point = points.get(0);
        assertEquals("INVOKE", value(point, "value"));
        assertEquals(Mm2LightmapReadinessContract.DYNAMIC_TEXTURE_UPLOAD_INVOKE,
                value(point, "target"));
        assertEquals(List.of("Lorg/spongepowered/asm/mixin/injection/At$Shift;", "AFTER"),
                List.of((String[]) value(point, "shift")));
        assertEquals(false, value(point, "remap"));
        assertEquals(1, callCount(handler, Opcodes.INVOKESTATIC,
                COMPAT + "Mm2LightmapReadiness",
                "recordCompletedVanillaUpdate",
                "(Lnet/minecraft/client/renderer/LightTexture;)V"));
    }

    @Test
    void pluginRoutesBothMixinsToOneRequiredProductionPinAndRequestScope()
            throws Exception {
        verifyPluginAudit("com.recipetree.reiexport118.mixin.LightTexturePixelsAccessor");
        verifyPluginAudit("com.recipetree.reiexport118.mixin.LightTextureUpdateMixin");

        Path absent = Files.createDirectory(temporaryDirectory.resolve("absent"));
        ReiExportMixinConfigPlugin disabled = new ReiExportMixinConfigPlugin(absent);
        assertFalse(disabled.shouldApplyMixin(
                Mm2LightmapReadinessContract.LIGHT_TEXTURE_CLASS,
                "com.recipetree.reiexport118.mixin.LightTextureUpdateMixin"));

        String config;
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("reiexport.mixins.json")) {
            assertNotNull(input);
            config = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertEquals(1, occurrences(config, "\"LightTexturePixelsAccessor\""));
        assertEquals(1, occurrences(config, "\"LightTextureUpdateMixin\""));
    }

    @Test
    void lifecycleChecksPrecedeClaimAndEveryCaptureAndPublicationOwnAnAudit()
            throws IOException {
        ClassNode coordinator = readResource(ROOT + "ExportCoordinator.class");
        MethodNode tick = method(coordinator, "tick", "()V");
        assertTrue(callIndex(tick, COMPAT + "Mm2LightmapReadiness",
                "pollReadyBeforeClaim", "()Z")
                < callIndex(tick, ROOT + "RegistryCensus", "captureCounts",
                "()Lcom/recipetree/reiexport118/RegistryCensus$Counts;"));
        int claim = callIndex(tick, ROOT + "ExportCoordinator", "claim",
                "(Ljava/nio/file/Path;)"
                        + "Lcom/recipetree/reiexport118/ExportCoordinator$Claim;");
        assertTrue(callIndex(tick, COMPAT + "Mm2UnattendedUiScope",
                "requireReadyForClaim", "()V") < claim);
        assertTrue(callIndex(tick, COMPAT + "Mm2LightmapReadiness",
                "requireReadyForClaim", "()V") < claim);

        ClassNode renderer = readResource(ROOT + "OffscreenRenderer.class");
        assertEquals(1, renderer.methods.stream()
                .mapToInt(method -> callCount(method, Opcodes.INVOKESTATIC,
                        COMPAT + "Mm2LightmapReadiness",
                        "requireCaptureBaseline", "(Ljava/lang/String;)V"))
                .sum());

        ClassNode job = readResource(ROOT + "ExportJob.class");
        MethodNode finish = method(job, "finish", "()V");
        assertEquals(1, callCount(finish, Opcodes.INVOKESTATIC,
                COMPAT + "Mm2LightmapReadiness",
                "requireHealthyBeforePublication", "()V"));
        assertTrue(callIndex(finish, COMPAT + "Mm2LightmapReadiness",
                "requireHealthyBeforePublication", "()V")
                < callIndex(finish, COMPAT + "Mm2UnattendedUiScope",
                "releaseIfActive", "(Ljava/lang/String;)V"));

        ClassNode bootstrap = readResource(ROOT + "WorldBootstrap.class");
        assertEquals(1, callCount(method(bootstrap, "tick", "()V"),
                Opcodes.INVOKESTATIC,
                ROOT + "ExportCoordinator", "releaseUnclaimedScopes",
                "(Ljava/lang/String;)V"));
        MethodNode logout = method(coordinator, "abortForLogout", "()V");
        assertEquals(1, callCount(logout, Opcodes.INVOKESTATIC,
                ROOT + "ExportCoordinator", "releaseUnclaimedScopes",
                "(Ljava/lang/String;)V"));
        assertEquals(1, callCount(logout, Opcodes.INVOKESTATIC,
                ROOT + "ExportCoordinator", "failActiveOrUnclaimed",
                "(Ljava/nio/file/Path;Ljava/lang/Throwable;)V"));

        MethodNode preClaimFailure = method(coordinator, "failActiveOrUnclaimed",
                "(Ljava/nio/file/Path;Ljava/lang/Throwable;)V");
        assertEquals(1, callCount(preClaimFailure, Opcodes.INVOKESTATIC,
                ROOT + "ExportRequest", "read",
                "(Ljava/nio/file/Path;)Lcom/recipetree/reiexport118/ExportRequest;"));
        assertTrue(callCount(preClaimFailure, Opcodes.INVOKEVIRTUAL,
                "net/minecraft/client/Minecraft", "stop", "()V") >= 1);
    }

    @Test
    void uiNormalizationIsEarlyClaimIsMutationFreeAndNoSyntheticLightmapCallExists()
            throws IOException {
        ClassNode ui = readResource(COMPAT + "Mm2UnattendedUiScope.class");
        MethodNode normalize = method(ui,
                "normalizeExactPauseScreenDuringReadiness", "()V");
        assertEquals(1, fieldCount(normalize, Opcodes.PUTFIELD,
                "net/minecraft/client/Minecraft", "screen",
                "Lnet/minecraft/client/gui/screens/Screen;"));
        MethodNode claim = method(ui, "requireReadyForClaim", "()V");
        assertEquals(0, fieldCount(claim, Opcodes.PUTFIELD,
                "net/minecraft/client/Minecraft", "screen",
                "Lnet/minecraft/client/gui/screens/Screen;"));

        ClassNode readiness = readResource(COMPAT + "Mm2LightmapReadiness.class");
        assertEquals(0, readiness.methods.stream().mapToInt(method ->
                callCount(method, Opcodes.INVOKEVIRTUAL,
                        "net/minecraft/client/renderer/LightTexture",
                        "tick", "()V")
                        + callCount(method, Opcodes.INVOKEVIRTUAL,
                        "net/minecraft/client/renderer/LightTexture",
                        "updateLightTexture", "(F)V")).sum());
    }

    private static void verifyPluginAudit(String mixin) throws Exception {
        Method routing = ReiExportMixinConfigPlugin.class
                .getDeclaredMethod("auditedTarget", String.class);
        routing.setAccessible(true);
        Object target = routing.invoke(null, mixin);
        assertNotNull(target);
        assertEquals(Mm2LightmapReadinessContract.LIGHT_TEXTURE_CLASS,
                recordValue(target, "className"));
        assertEquals(Mm2LightmapReadinessContract.LIGHT_TEXTURE_RESOURCE,
                recordValue(target, "resource"));
        assertEquals(Mm2LightmapReadinessContract.LIGHT_TEXTURE_SHA256,
                recordValue(target, "sha256"));
        assertEquals(true, recordValue(target, "requiredTarget"));
        assertEquals(Mm2LightmapReadinessContract.PRODUCTION_RESOURCE_STAGE,
                recordValue(target, "resourceStage"));
    }

    private static Object recordValue(Object record, String name) throws Exception {
        Method method = record.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(record);
    }

    private static byte[] readEntry(JarFile archive, String resource) throws IOException {
        JarEntry entry = archive.getJarEntry(resource);
        assertNotNull(entry, resource);
        try (InputStream input = archive.getInputStream(entry)) {
            return input.readAllBytes();
        }
    }

    private static ClassNode readResource(String resource) throws IOException {
        try (InputStream input = Mm2LightmapReadinessMixinContractTest.class
                .getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, resource);
            return read(input.readAllBytes());
        }
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node,
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

    private static int opcodeCount(MethodNode method, int opcode) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction.getOpcode() == opcode) count++;
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
                    && descriptor.equals(call.desc)) count++;
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
                    && descriptor.equals(call.desc)) return index;
            index++;
        }
        throw new AssertionError("missing call " + owner + "." + name + descriptor);
    }

    private static int fieldCount(
            MethodNode method, int opcode, String owner, String name, String descriptor) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode field
                    && field.getOpcode() == opcode
                    && owner.equals(field.owner)
                    && name.equals(field.name)
                    && descriptor.equals(field.desc)) count++;
        }
        return count;
    }

    private static List<AnnotationNode> annotations(MethodNode method) {
        List<AnnotationNode> result = new ArrayList<>();
        if (method.visibleAnnotations != null) result.addAll(method.visibleAnnotations);
        if (method.invisibleAnnotations != null) result.addAll(method.invisibleAnnotations);
        return result;
    }

    private static AnnotationNode annotation(List<AnnotationNode> values, String descriptor) {
        for (AnnotationNode annotation : values) {
            if (descriptor.equals(annotation.desc)) return annotation;
        }
        throw new AssertionError("missing annotation " + descriptor);
    }

    private static Object value(AnnotationNode annotation, String name) {
        for (int index = 0; annotation.values != null
                && index < annotation.values.size(); index += 2) {
            if (name.equals(annotation.values.get(index))) {
                return annotation.values.get(index + 1);
            }
        }
        throw new AssertionError("missing annotation value " + name);
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
