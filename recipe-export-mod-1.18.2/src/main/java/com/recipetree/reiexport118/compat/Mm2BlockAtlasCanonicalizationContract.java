package com.recipetree.reiexport118.compat;

import java.util.List;

/** Exact production classpath-resource and sprite contract for atlas canonicalization. */
public final class Mm2BlockAtlasCanonicalizationContract {
    public record CoreClassPin(String className, String resource, String sha256) {
    }

    public static final String MINECRAFT_VERSION = "1.18.2";
    public static final String FORGE_VERSION = "40.2.17";
    /**
     * ModLauncher's production classpath exposes Forge's binary-patched client artifact here,
     * not the upstream Minecraft SRG input used to generate that artifact. Mixin reads the same
     * resource origin before it applies target mixins.
     */
    public static final String PRODUCTION_RESOURCE_STAGE =
            "Forge 40.2.17 binary-patched client JAR resource before Mixin application";

    public static final CoreClassPin TEXTURE_ATLAS = new CoreClassPin(
            "net.minecraft.client.renderer.texture.TextureAtlas",
            "net/minecraft/client/renderer/texture/TextureAtlas.class",
            "19f4bc520e5e5b942a8497a68fba96a9d92f3bc3b46d31cc10c256fdb99413ca");
    public static final CoreClassPin TEXTURE_ATLAS_SPRITE = new CoreClassPin(
            "net.minecraft.client.renderer.texture.TextureAtlasSprite",
            "net/minecraft/client/renderer/texture/TextureAtlasSprite.class",
            "f2d2158e1db4c81ef29b5c87f34a7507e5295fc8b3eca8598bcf789a7c4804ad");
    public static final CoreClassPin ANIMATED_TEXTURE = new CoreClassPin(
            "net.minecraft.client.renderer.texture.TextureAtlasSprite$AnimatedTexture",
            "net/minecraft/client/renderer/texture/TextureAtlasSprite$AnimatedTexture.class",
            "51086ad2dbd2a24628bdef514dd53db51673294bfb77993cfcccfb2d5ec7f869");
    public static final List<CoreClassPin> CORE_CLASS_PINS = List.of(
            TEXTURE_ATLAS,
            TEXTURE_ATLAS_SPRITE,
            ANIMATED_TEXTURE);

    /** Production SRG selectors used with refmap remapping deliberately disabled. */
    public static final String TEXTURE_ATLAS_CYCLE_METHOD = "m_118270_()V";
    public static final String TEXTURE_ATLAS_SPRITES_FIELD = "f_118264_";
    public static final String TEXTURE_ATLAS_SPRITE_FIRST_FRAME_METHOD = "m_118416_()V";
    public static final String ANIMATED_TEXTURE_FIRST_FRAME_METHOD = "m_174758_()V";

    /** Kept as a JDK String because this contract is initialized during early Mixin selection. */
    public static final String MEMORY_ESSENCE_SPRITE_ID =
            "pneumaticcraft:block/fluid/memory_essence_still";
    public static final int MEMORY_ESSENCE_FRAME_COUNT = 32;

    private Mm2BlockAtlasCanonicalizationContract() {
    }
}
