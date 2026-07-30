package com.recipetree.reiexport118.compat;

/** Exact production-bytecode and pixel-state contract for Minecraft 1.18.2's lightmap. */
public final class Mm2LightmapReadinessContract {
    public static final String MINECRAFT_VERSION = "1.18.2";
    public static final String FORGE_VERSION = "40.2.17";
    public static final String LIGHT_TEXTURE_CLASS =
            "net.minecraft.client.renderer.LightTexture";
    public static final String LIGHT_TEXTURE_RESOURCE =
            "net/minecraft/client/renderer/LightTexture.class";
    public static final String LIGHT_TEXTURE_SHA256 =
            "a8cc85d14063aeeb90d897e361806a3970426a6e8fca56905c0587a36b3d5e50";
    public static final String PRODUCTION_RESOURCE_STAGE =
            "Forge 40.2.17 binary-patched client JAR resource before Mixin application";

    /** Production SRG selectors; both lightmap mixins deliberately disable remapping. */
    public static final String LIGHT_PIXELS_FIELD = "f_109871_";
    public static final String UPDATE_LIGHT_TEXTURE_METHOD = "m_109881_(F)V";
    public static final String DYNAMIC_TEXTURE_UPLOAD_INVOKE =
            "Lnet/minecraft/client/renderer/texture/DynamicTexture;m_117985_()V";

    public static final int FULL_BRIGHT_X = 15;
    public static final int FULL_BRIGHT_Y = 15;
    /** NativeImage stores this symmetric opaque pixel as ABGR 0xfffcfcfc. */
    public static final int EXPECTED_FULL_BRIGHT_ABGR = 0xfffcfcfc;
    public static final int READINESS_TIMEOUT_TICKS = 600;

    private Mm2LightmapReadinessContract() {
    }
}
