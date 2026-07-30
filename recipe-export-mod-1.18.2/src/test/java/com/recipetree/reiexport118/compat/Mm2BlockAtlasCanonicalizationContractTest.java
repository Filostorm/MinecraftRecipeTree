package com.recipetree.reiexport118.compat;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class Mm2BlockAtlasCanonicalizationContractTest {
    private static final Path PRODUCTION_FORGE_CLIENT = Path.of(
            "..",
            "minecraft-1.18.2-runtime",
            "libraries",
            "net/minecraftforge/forge/1.18.2-40.2.17",
            "forge-1.18.2-40.2.17-client.jar");
    private static final Path UPSTREAM_SRG_CLIENT = Path.of(
            "..",
            "minecraft-1.18.2-runtime",
            "libraries",
            "net/minecraft/client/1.18.2-20220404.173914",
            "client-1.18.2-20220404.173914-srg.jar");
    private static final Path PNEUMATICCRAFT = Path.of(
            "..",
            "export-instances",
            "multiblock-madness-2",
            "mods",
            "pneumaticcraft-repressurized-1.18.2-3.6.4-45.jar");
    private static final String MEMORY_TEXTURE =
            "assets/pneumaticcraft/textures/block/fluid/memory_essence_still.png";
    private static final String MEMORY_METADATA = MEMORY_TEXTURE + ".mcmeta";
    private static final String MEMORY_SOURCE_SHA256 =
            "4cc57d87eddea0faa274e72843e06457ac218f5c1c2d06aede3ed3df0a317e5b";

    @Test
    void pinsTheForgeBinaryPatchedProductionResourceStageNotItsUpstreamSrgInput()
            throws IOException {
        assertEquals(
                "Forge 40.2.17 binary-patched client JAR resource before Mixin application",
                Mm2BlockAtlasCanonicalizationContract.PRODUCTION_RESOURCE_STAGE);
        assertFalse(Files.isSymbolicLink(PRODUCTION_FORGE_CLIENT));
        assertTrue(Files.isRegularFile(PRODUCTION_FORGE_CLIENT, LinkOption.NOFOLLOW_LINKS));
        assertFalse(Files.isSymbolicLink(UPSTREAM_SRG_CLIENT));
        assertTrue(Files.isRegularFile(UPSTREAM_SRG_CLIENT, LinkOption.NOFOLLOW_LINKS));
        try (JarFile production = new JarFile(PRODUCTION_FORGE_CLIENT.toFile());
             JarFile upstream = new JarFile(UPSTREAM_SRG_CLIENT.toFile())) {
            for (Mm2BlockAtlasCanonicalizationContract.CoreClassPin pin
                    : Mm2BlockAtlasCanonicalizationContract.CORE_CLASS_PINS) {
                byte[] productionBytes = readEntry(production, pin.resource());
                byte[] upstreamBytes = readEntry(upstream, pin.resource());
                assertEquals(pin.sha256(), sha256(productionBytes), pin.resource());
                assertNotEquals(pin.sha256(), sha256(upstreamBytes),
                        "the upstream SRG input is not Mixin's production resource stage");
            }
        }
    }

    @Test
    void exactProductionTextureAtlasTicksEveryAnimatedSpriteThroughOneCycleSeam()
            throws IOException {
        ClassNode atlas = readPinned(Mm2BlockAtlasCanonicalizationContract.TEXTURE_ATLAS);
        MethodNode cycle = method(atlas, "m_118270_", "()V");
        assertEquals(1, callCount(cycle, Opcodes.INVOKEVIRTUAL, atlas.name,
                "m_117966_", "()V"), "atlas bind before animated uploads");
        assertEquals(1, fieldCount(cycle, Opcodes.GETFIELD, atlas.name,
                "f_118262_", "Ljava/util/List;"), "animated ticker inventory read");
        assertEquals(1, callCount(cycle, Opcodes.INVOKEINTERFACE,
                "net/minecraft/client/renderer/texture/Tickable",
                "m_7673_", "()V"), "per-sprite animation tick");

        MethodNode tick = method(atlas, "m_7673_", "()V");
        assertEquals(1, callCount(tick, Opcodes.INVOKESTATIC,
                "com/mojang/blaze3d/systems/RenderSystem",
                "m_69586_", "()Z"));
        assertEquals(1, callCount(tick, Opcodes.INVOKEVIRTUAL, atlas.name,
                "m_118270_", "()V"), "render-thread cycle dispatch");
        assertEquals(1, callCount(tick, Opcodes.INVOKESTATIC,
                "com/mojang/blaze3d/systems/RenderSystem",
                "m_69879_", "(Lcom/mojang/blaze3d/pipeline/RenderCall;)V"),
                "off-thread dispatch is recorded onto the render thread");
    }

    @Test
    void vanillaFirstFrameApiSelectsTheFirstDeclaredFrameAndNativeUploadPath()
            throws IOException {
        ClassNode sprite = readPinned(
                Mm2BlockAtlasCanonicalizationContract.TEXTURE_ATLAS_SPRITE);
        MethodNode firstFrame = method(sprite, "m_118416_", "()V");
        assertEquals(1, callCount(firstFrame, Opcodes.INVOKEVIRTUAL,
                "net/minecraft/client/renderer/texture/TextureAtlasSprite$AnimatedTexture",
                "m_174758_", "()V"));
        assertEquals(1, callCount(firstFrame, Opcodes.INVOKEVIRTUAL, sprite.name,
                "m_118375_",
                "(II[Lcom/mojang/blaze3d/platform/NativeImage;)V"),
                "static sprites retain the same native upload implementation");

        ClassNode animated = readPinned(
                Mm2BlockAtlasCanonicalizationContract.ANIMATED_TEXTURE);
        MethodNode animatedFirst = method(animated, "m_174758_", "()V");
        assertEquals(1, fieldCount(animatedFirst, Opcodes.GETFIELD, animated.name,
                "f_174750_", "Ljava/util/List;"), "declared animation sequence read");
        assertEquals(1, callCount(animatedFirst, Opcodes.INVOKEINTERFACE,
                "java/util/List", "get", "(I)Ljava/lang/Object;"));
        assertEquals(1, fieldCount(animatedFirst, Opcodes.GETFIELD,
                "net/minecraft/client/renderer/texture/TextureAtlasSprite$FrameInfo",
                "f_174771_", "I"), "first declared entry's physical frame index");
        assertEquals(1, callCount(animatedFirst, Opcodes.INVOKEVIRTUAL, animated.name,
                "m_174767_", "(I)V"), "native frame upload");
        assertEquals(1, opcodeCount(animatedFirst, Opcodes.ICONST_0),
                "the animation sequence is indexed only at position zero");
    }

    @Test
    void memoryEssenceFramesAccountForBothObservedMiniExportDifferencesExactly()
            throws IOException {
        assertFalse(Files.isSymbolicLink(PNEUMATICCRAFT));
        assertTrue(Files.isRegularFile(PNEUMATICCRAFT, LinkOption.NOFOLLOW_LINKS));
        byte[] png;
        String metadata;
        try (JarFile archive = new JarFile(PNEUMATICCRAFT.toFile())) {
            png = readEntry(archive, MEMORY_TEXTURE);
            metadata = new String(readEntry(archive, MEMORY_METADATA));
        }
        assertEquals(MEMORY_SOURCE_SHA256, sha256(png));
        assertTrue(metadata.contains("\"frametime\": 2"));

        BufferedImage sheet = ImageIO.read(new ByteArrayInputStream(png));
        assertNotNull(sheet);
        assertEquals(16, sheet.getWidth());
        assertEquals(512, sheet.getHeight());
        assertEquals(32, sheet.getHeight() / 16);

        int[] miniG = tintedFrame(sheet, 8, 0x00d0ff00);
        int[] miniF = tintedFrame(sheet, 9, 0x00d0ff00);
        assertEquals(237, differingPixels(miniF, miniG));
        assertEquals(948, differingPixels(miniF, miniG) * 2 * 2,
                "each native 16x16 difference expands to a 2x2 recipe-preview pixel block");
    }

    private static int[] tintedFrame(BufferedImage sheet, int frame, int tintRgb) {
        int[] result = new int[16 * 16];
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int argb = sheet.getRGB(x, frame * 16 + y);
                int alpha = argb >>> 24 & 0xff;
                int red = multiply(argb >>> 16 & 0xff, tintRgb >>> 16 & 0xff);
                int green = multiply(argb >>> 8 & 0xff, tintRgb >>> 8 & 0xff);
                int blue = multiply(argb & 0xff, tintRgb & 0xff);
                result[y * 16 + x] = alpha << 24 | red << 16 | green << 8 | blue;
            }
        }
        return result;
    }

    private static int multiply(int source, int tint) {
        return (source * tint + 127) / 255;
    }

    private static int differingPixels(int[] left, int[] right) {
        int count = 0;
        for (int index = 0; index < left.length; index++) {
            if (left[index] != right[index]) {
                count++;
            }
        }
        return count;
    }

    private static byte[] readEntry(JarFile archive, String path) throws IOException {
        JarEntry entry = archive.getJarEntry(path);
        assertNotNull(entry, path);
        try (InputStream input = archive.getInputStream(entry)) {
            return input.readAllBytes();
        }
    }

    private static ClassNode readPinned(
            Mm2BlockAtlasCanonicalizationContract.CoreClassPin pin
    ) throws IOException {
        assertFalse(Files.isSymbolicLink(PRODUCTION_FORGE_CLIENT));
        assertTrue(Files.isRegularFile(PRODUCTION_FORGE_CLIENT, LinkOption.NOFOLLOW_LINKS));
        byte[] bytecode;
        try (JarFile archive = new JarFile(PRODUCTION_FORGE_CLIENT.toFile())) {
            bytecode = readEntry(archive, pin.resource());
        }
        assertEquals(pin.sha256(), sha256(bytecode), pin.resource());
        ClassNode node = new ClassNode();
        new ClassReader(bytecode).accept(node, 0);
        assertEquals(pin.className().replace('.', '/'), node.name);
        return node;
    }

    private static MethodNode method(ClassNode owner, String name, String descriptor) {
        List<MethodNode> methods = owner.methods.stream()
                .filter(method -> name.equals(method.name))
                .filter(method -> descriptor.equals(method.desc))
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

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
