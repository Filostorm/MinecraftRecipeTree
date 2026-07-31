package com.recipetree.reiexport118.mixin;

import com.recipetree.reiexport118.compat.Mm2OffscreenGlintClockContract;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RenderStateShardGlintClockMixinContractTest {
    private static final Path PRODUCTION_MINECRAFT_SRG_CLIENT = Path.of(
            "..",
            "minecraft-1.18.2-runtime",
            "libraries",
            "net/minecraft/client/1.18.2-20220404.173914",
            "client-1.18.2-20220404.173914-srg.jar");
    private static final Path FORGE_BINARY_PATCH_OVERLAY = Path.of(
            "..",
            "minecraft-1.18.2-runtime",
            "libraries",
            "net/minecraftforge/forge/1.18.2-40.2.17",
            "forge-1.18.2-40.2.17-client.jar");
    private static final String MIXIN_RESOURCE =
            "com/recipetree/reiexport118/mixin/RenderStateShardGlintClockMixin.class";
    private static final String CLOCK =
            "com/recipetree/reiexport118/compat/Mm2OffscreenGlintClock";

    @Test
    void pinsTheActualUnpatchedProductionResourceStage() throws IOException {
        assertEquals("1.18.2", Mm2OffscreenGlintClockContract.MINECRAFT_VERSION);
        assertEquals("40.2.17", Mm2OffscreenGlintClockContract.FORGE_VERSION);
        assertEquals(
                "Minecraft 1.18.2 SRG client JAR beneath the Forge 40.2.17 binary-patch "
                        + "overlay, before Mixin application",
                Mm2OffscreenGlintClockContract.PRODUCTION_RESOURCE_STAGE);
        assertPlainFile(PRODUCTION_MINECRAFT_SRG_CLIENT);
        assertPlainFile(FORGE_BINARY_PATCH_OVERLAY);

        byte[] productionBytes;
        try (JarFile minecraft = new JarFile(PRODUCTION_MINECRAFT_SRG_CLIENT.toFile());
             JarFile forgeOverlay = new JarFile(FORGE_BINARY_PATCH_OVERLAY.toFile())) {
            assertNull(forgeOverlay.getJarEntry(
                    Mm2OffscreenGlintClockContract.RENDER_STATE_SHARD.resource()),
                    "RenderStateShard is intentionally inherited from the Minecraft SRG client");
            productionBytes = readEntry(
                    minecraft,
                    Mm2OffscreenGlintClockContract.RENDER_STATE_SHARD.resource());
        }
        assertEquals(
                Mm2OffscreenGlintClockContract.RENDER_STATE_SHARD.sha256(),
                sha256(productionBytes));
        assertEquals(
                Mm2OffscreenGlintClockContract.PRODUCTION_RESOURCE_STAGE,
                Mm2OffscreenGlintClockContract.RENDER_STATE_SHARD.resourceStage());
    }

    @Test
    void exactVanillaGlintMethodHasOneMonotonicClockSeamAndBothNativePeriods()
            throws IOException {
        ClassNode target = readPinnedProductionTarget();
        MethodNode setup = method(
                target,
                "m_110186_",
                "(F)V");
        assertTrue((setup.access & Opcodes.ACC_PRIVATE) != 0);
        assertTrue((setup.access & Opcodes.ACC_STATIC) != 0);
        assertEquals(1, callCount(
                setup,
                Opcodes.INVOKESTATIC,
                "net/minecraft/Util",
                "m_137550_",
                "()J"));
        assertEquals(1, ldcCount(setup,
                Mm2OffscreenGlintClockContract.GLINT_TIME_MULTIPLIER));
        assertEquals(1, ldcCount(setup,
                Mm2OffscreenGlintClockContract.GLINT_X_PERIOD_MILLIS));
        assertEquals(1, ldcCount(setup,
                Mm2OffscreenGlintClockContract.GLINT_Y_PERIOD_MILLIS));
        assertEquals(1, opcodeCount(setup, Opcodes.LMUL));
        assertEquals(2, opcodeCount(setup, Opcodes.LREM));
        assertEquals(2, classCallCount(
                target,
                Opcodes.INVOKESTATIC,
                target.name,
                "m_110186_",
                "(F)V"),
                "entity and ordinary glint texture states converge on the one clock seam");
    }

    @Test
    void compiledMixinRedirectsOnlyTheExactSrgCallAndPreservesUpstreamOutsideScope()
            throws IOException {
        ClassNode mixin = readResource(MIXIN_RESOURCE);
        AnnotationNode mixinAnnotation = annotation(
                mixin,
                "Lorg/spongepowered/asm/mixin/Mixin;");
        assertEquals(false, value(mixinAnnotation, "remap"));
        assertEquals(
                List.of(Type.getObjectType(
                        Mm2OffscreenGlintClockContract.RENDER_STATE_SHARD
                                .className().replace('.', '/'))),
                value(mixinAnnotation, "value"));

        MethodNode handler = methodByName(
                mixin,
                "reiexport$canonicalOffscreenGlintMillis");
        assertTrue((handler.access & Opcodes.ACC_PRIVATE) != 0);
        assertTrue((handler.access & Opcodes.ACC_STATIC) != 0,
                "the audited target method is static");
        assertEquals("()J", handler.desc);

        AnnotationNode redirect = annotation(
                handler,
                "Lorg/spongepowered/asm/mixin/injection/Redirect;");
        assertEquals(
                List.of(Mm2OffscreenGlintClockContract.SETUP_GLINT_TEXTURING_METHOD),
                value(redirect, "method"));
        assertEquals(1, value(redirect, "require"));
        assertEquals(false, value(redirect, "remap"));
        AnnotationNode at = assertInstanceOf(
                AnnotationNode.class,
                value(redirect, "at"));
        assertEquals("INVOKE", value(at, "value"));
        assertEquals(
                Mm2OffscreenGlintClockContract.UTIL_GET_MILLIS_INVOKE,
                value(at, "target"));
        assertEquals(0, value(at, "ordinal"));
        assertEquals(false, value(at, "remap"));

        assertEquals(1, callCount(handler, Opcodes.INVOKESTATIC, CLOCK,
                "isCaptureActive", "()Z"));
        assertEquals(1, callCount(handler, Opcodes.INVOKESTATIC, CLOCK,
                "canonicalGlintMillis", "()J"));
        assertEquals(1, callCount(handler, Opcodes.INVOKESTATIC,
                "net/minecraft/Util", "getMillis", "()J"),
                "official-mapped compilation retains the literal upstream call outside scope");
        assertEquals(1, conditionalJumpCount(handler));
        assertEquals(0, classCallCount(mixin, Opcodes.INVOKESTATIC,
                "java/lang/System", "currentTimeMillis", "()J"));
    }

    private static void assertPlainFile(Path path) {
        assertFalse(Files.isSymbolicLink(path), path.toString());
        assertTrue(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS), path.toString());
    }

    private static ClassNode readPinnedProductionTarget() throws IOException {
        assertPlainFile(PRODUCTION_MINECRAFT_SRG_CLIENT);
        byte[] bytes;
        try (JarFile archive = new JarFile(PRODUCTION_MINECRAFT_SRG_CLIENT.toFile())) {
            bytes = readEntry(
                    archive,
                    Mm2OffscreenGlintClockContract.RENDER_STATE_SHARD.resource());
        }
        assertEquals(
                Mm2OffscreenGlintClockContract.RENDER_STATE_SHARD.sha256(),
                sha256(bytes));
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        assertEquals(
                Mm2OffscreenGlintClockContract.RENDER_STATE_SHARD
                        .className().replace('.', '/'),
                node.name);
        return node;
    }

    private static byte[] readEntry(JarFile archive, String path) throws IOException {
        JarEntry entry = archive.getJarEntry(path);
        assertNotNull(entry, path);
        try (InputStream input = archive.getInputStream(entry)) {
            return input.readAllBytes();
        }
    }

    private static ClassNode readResource(String path) throws IOException {
        try (InputStream input = RenderStateShardGlintClockMixinContractTest.class
                .getClassLoader().getResourceAsStream(path)) {
            assertNotNull(input, path);
            ClassNode node = new ClassNode();
            new ClassReader(input.readAllBytes()).accept(node, 0);
            return node;
        }
    }

    private static MethodNode method(ClassNode owner, String name, String descriptor) {
        List<MethodNode> methods = owner.methods.stream()
                .filter(method -> name.equals(method.name))
                .filter(method -> descriptor.equals(method.desc))
                .toList();
        assertEquals(1, methods.size(), owner.name + "." + name + descriptor);
        return methods.get(0);
    }

    private static MethodNode methodByName(ClassNode owner, String name) {
        List<MethodNode> methods = owner.methods.stream()
                .filter(method -> name.equals(method.name))
                .toList();
        assertEquals(1, methods.size(), owner.name + "." + name);
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

    private static int ldcCount(MethodNode method, Object constant) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof LdcInsnNode ldc && constant.equals(ldc.cst)) {
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

    private static int conditionalJumpCount(MethodNode method) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof JumpInsnNode jump
                    && jump.getOpcode() != Opcodes.GOTO
                    && jump.getOpcode() != Opcodes.JSR) {
                count++;
            }
        }
        return count;
    }

    private static AnnotationNode annotation(ClassNode type, String descriptor) {
        List<AnnotationNode> annotations = new ArrayList<>();
        if (type.visibleAnnotations != null) {
            annotations.addAll(type.visibleAnnotations);
        }
        if (type.invisibleAnnotations != null) {
            annotations.addAll(type.invisibleAnnotations);
        }
        return annotation(annotations, descriptor);
    }

    private static AnnotationNode annotation(MethodNode method, String descriptor) {
        List<AnnotationNode> annotations = new ArrayList<>();
        if (method.visibleAnnotations != null) {
            annotations.addAll(method.visibleAnnotations);
        }
        if (method.invisibleAnnotations != null) {
            annotations.addAll(method.invisibleAnnotations);
        }
        return annotation(annotations, descriptor);
    }

    private static AnnotationNode annotation(
            List<AnnotationNode> annotations,
            String descriptor
    ) {
        return annotations.stream()
                .filter(annotation -> descriptor.equals(annotation.desc))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing annotation " + descriptor));
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
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
