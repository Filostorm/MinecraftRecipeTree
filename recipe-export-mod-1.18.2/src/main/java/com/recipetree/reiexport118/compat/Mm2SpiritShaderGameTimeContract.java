package com.recipetree.reiexport118.compat;

/** Exact production-bytecode contract for Spirit's corrupted-entity shader clock input. */
public final class Mm2SpiritShaderGameTimeContract {
    public record CoreClassPin(
            String className,
            String resource,
            String sha256,
            String resourceStage
    ) {
    }

    public static final String MINECRAFT_VERSION = "1.18.2";
    public static final String FORGE_VERSION = "40.2.17";
    public static final String PRODUCTION_RESOURCE_STAGE =
            Mm2OffscreenGlintClockContract.PRODUCTION_RESOURCE_STAGE;

    /**
     * Forge's runtime binary-patch overlay has no BufferUploader entry, so ModLauncher resolves
     * this exact class from the Minecraft SRG client JAR beneath that overlay.
     */
    public static final CoreClassPin BUFFER_UPLOADER = new CoreClassPin(
            "com.mojang.blaze3d.vertex.BufferUploader",
            "com/mojang/blaze3d/vertex/BufferUploader.class",
            "5090705cfbb38c4fbfc94cc9f8432d3530d85ff85bb5cc3de219050126607318",
            PRODUCTION_RESOURCE_STAGE);

    /** Production SRG selectors; the mixin deliberately disables annotation remapping. */
    public static final String DRAW_WITH_SHADER_METHOD =
            "m_166838_(Ljava/nio/ByteBuffer;"
                    + "Lcom/mojang/blaze3d/vertex/VertexFormat$Mode;"
                    + "Lcom/mojang/blaze3d/vertex/VertexFormat;I"
                    + "Lcom/mojang/blaze3d/vertex/VertexFormat$IndexType;IZ)V";
    public static final String SHADER_GAME_TIME_INVOKE =
            "Lcom/mojang/blaze3d/systems/RenderSystem;m_157201_()F";

    public static final String CORRUPTED_ENTITY_SHADER =
            "rendertype_entity_corrupted";
    public static final float CANONICAL_SHADER_GAME_TIME = 0.0F;

    private Mm2SpiritShaderGameTimeContract() {
    }
}
