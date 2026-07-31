package com.recipetree.reiexport118.mixin;

import com.recipetree.reiexport118.compat.Mm2SpiritShaderGameTimeContract;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
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

final class BufferUploaderSpiritShaderClockMixinContractTest {
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
            "com/recipetree/reiexport118/mixin/"
                    + "BufferUploaderSpiritShaderClockMixin.class";
    private static final String DETERMINISM =
            "com/recipetree/reiexport118/compat/Mm2SpiritEntityRenderDeterminism";

    @Test
    void pinsTheExactUnpatchedProductionBufferUploader() throws IOException {
        assertEquals("1.18.2", Mm2SpiritShaderGameTimeContract.MINECRAFT_VERSION);
        assertEquals("40.2.17", Mm2SpiritShaderGameTimeContract.FORGE_VERSION);
        assertPlainFile(PRODUCTION_MINECRAFT_SRG_CLIENT);
        assertPlainFile(FORGE_BINARY_PATCH_OVERLAY);

        byte[] productionBytes;
        try (JarFile minecraft = new JarFile(PRODUCTION_MINECRAFT_SRG_CLIENT.toFile());
             JarFile forgeOverlay = new JarFile(FORGE_BINARY_PATCH_OVERLAY.toFile())) {
            assertNull(forgeOverlay.getJarEntry(
                    Mm2SpiritShaderGameTimeContract.BUFFER_UPLOADER.resource()));
            productionBytes = readEntry(
                    minecraft,
                    Mm2SpiritShaderGameTimeContract.BUFFER_UPLOADER.resource());
        }
        assertEquals(
                Mm2SpiritShaderGameTimeContract.BUFFER_UPLOADER.sha256(),
                sha256(productionBytes));
        assertEquals(
                Mm2SpiritShaderGameTimeContract.PRODUCTION_RESOURCE_STAGE,
                Mm2SpiritShaderGameTimeContract.BUFFER_UPLOADER.resourceStage());
    }

    @Test
    void exactDrawMethodHasOneShaderGameTimeUniformSeam() throws IOException {
        ClassNode target = readPinnedProductionTarget();
        MethodNode draw = method(
                target,
                "m_166838_",
                "(Ljava/nio/ByteBuffer;Lcom/mojang/blaze3d/vertex/VertexFormat$Mode;"
                        + "Lcom/mojang/blaze3d/vertex/VertexFormat;I"
                        + "Lcom/mojang/blaze3d/vertex/VertexFormat$IndexType;IZ)V");
        assertTrue((draw.access & Opcodes.ACC_PRIVATE) != 0);
        assertTrue((draw.access & Opcodes.ACC_STATIC) != 0);
        assertEquals(2, fieldCount(
                draw,
                Opcodes.GETFIELD,
                "net/minecraft/client/renderer/ShaderInstance",
                "f_173319_",
                "Lcom/mojang/blaze3d/shaders/Uniform;"));
        assertEquals(1, callCount(
                draw,
                Opcodes.INVOKESTATIC,
                "com/mojang/blaze3d/systems/RenderSystem",
                "m_157201_",
                "()F"));
        assertEquals(4, callCount(
                draw,
                Opcodes.INVOKEVIRTUAL,
                "com/mojang/blaze3d/shaders/Uniform",
                "m_5985_",
                "(F)V"));
    }

    @Test
    void compiledMixinRedirectsOnlyTheExactSrgSeam() throws IOException {
        ClassNode mixin = readResource(MIXIN_RESOURCE);
        AnnotationNode mixinAnnotation = annotation(
                mixin.invisibleAnnotations,
                "Lorg/spongepowered/asm/mixin/Mixin;");
        assertEquals(false, annotationValue(mixinAnnotation, "remap"));
        assertEquals(
                List.of(Type.getObjectType(
                        Mm2SpiritShaderGameTimeContract.BUFFER_UPLOADER
                                .className().replace('.', '/'))),
                annotationValue(mixinAnnotation, "value"));

        MethodNode handler = methodByName(
                mixin,
                "reiexport$canonicalSpiritShaderGameTime");
        assertTrue((handler.access & Opcodes.ACC_PRIVATE) != 0);
        assertTrue((handler.access & Opcodes.ACC_STATIC) != 0);
        assertEquals("()F", handler.desc);

        AnnotationNode redirect = annotation(
                allAnnotations(handler),
                "Lorg/spongepowered/asm/mixin/injection/Redirect;");
        assertEquals(
                List.of(Mm2SpiritShaderGameTimeContract.DRAW_WITH_SHADER_METHOD),
                annotationValue(redirect, "method"));
        assertEquals(1, annotationValue(redirect, "require"));
        assertEquals(false, annotationValue(redirect, "remap"));
        AnnotationNode at = assertInstanceOf(
                AnnotationNode.class,
                annotationValue(redirect, "at"));
        assertEquals("INVOKE", annotationValue(at, "value"));
        assertEquals(
                Mm2SpiritShaderGameTimeContract.SHADER_GAME_TIME_INVOKE,
                annotationValue(at, "target"));
        assertEquals(0, annotationValue(at, "ordinal"));
        assertEquals(false, annotationValue(at, "remap"));

        assertEquals(1, callCount(handler, Opcodes.INVOKESTATIC,
                "com/mojang/blaze3d/systems/RenderSystem",
                "getShaderGameTime", "()F"));
        assertEquals(1, callCount(handler, Opcodes.INVOKESTATIC,
                DETERMINISM, "isCaptureActive", "()Z"));
        assertEquals(1, callCount(handler, Opcodes.INVOKESTATIC,
                DETERMINISM,
                "corruptedShaderGameTime",
                "(FLjava/lang/String;)F"));
    }

    private static void assertPlainFile(Path path) {
        assertFalse(Files.isSymbolicLink(path), path.toString());
        assertTrue(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS), path.toString());
    }

    private static ClassNode readPinnedProductionTarget() throws IOException {
        byte[] bytes;
        try (JarFile archive = new JarFile(PRODUCTION_MINECRAFT_SRG_CLIENT.toFile())) {
            bytes = readEntry(
                    archive,
                    Mm2SpiritShaderGameTimeContract.BUFFER_UPLOADER.resource());
        }
        assertEquals(
                Mm2SpiritShaderGameTimeContract.BUFFER_UPLOADER.sha256(),
                sha256(bytes));
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
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
        try (InputStream input = BufferUploaderSpiritShaderClockMixinContractTest.class
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

    private static MethodNode methodByName(ClassNode owner, String name) {
        List<MethodNode> methods = owner.methods.stream()
                .filter(candidate -> name.equals(candidate.name))
                .toList();
        assertEquals(1, methods.size(), owner.name + "." + name);
        return methods.get(0);
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

    private static AnnotationNode annotation(
            List<AnnotationNode> annotations,
            String descriptor
    ) {
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

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
