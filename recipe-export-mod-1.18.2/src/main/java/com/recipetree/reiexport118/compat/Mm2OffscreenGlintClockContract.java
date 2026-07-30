package com.recipetree.reiexport118.compat;

/** Exact production-bytecode contract for Minecraft's 1.18.2 glint texture clock. */
public final class Mm2OffscreenGlintClockContract {
    public record CoreClassPin(
            String className,
            String resource,
            String sha256,
            String resourceStage
    ) {
    }

    public static final String MINECRAFT_VERSION = "1.18.2";
    public static final String FORGE_VERSION = "40.2.17";

    /**
     * Forge's binary-patch overlay does not contain this unmodified class. ModLauncher therefore
     * resolves the resource from the pinned Minecraft SRG client JAR immediately beneath the
     * Forge 40.2.17 overlay, before Mixin transforms it.
     */
    public static final String PRODUCTION_RESOURCE_STAGE =
            "Minecraft 1.18.2 SRG client JAR beneath the Forge 40.2.17 binary-patch overlay, "
                    + "before Mixin application";

    public static final CoreClassPin RENDER_STATE_SHARD = new CoreClassPin(
            "net.minecraft.client.renderer.RenderStateShard",
            "net/minecraft/client/renderer/RenderStateShard.class",
            "2694d5f486d0aadaafae69de3fdea1419a9dbf12c1743242fb29420348852d7b",
            PRODUCTION_RESOURCE_STAGE);

    /** Production SRG selectors; the mixin deliberately disables annotation remapping. */
    public static final String SETUP_GLINT_TEXTURING_METHOD = "m_110186_(F)V";
    public static final String UTIL_GET_MILLIS_INVOKE =
            "Lnet/minecraft/Util;m_137550_()J";

    /** Native constants in the audited setup method, retained here for bytecode assertions. */
    public static final long GLINT_TIME_MULTIPLIER = 8L;
    public static final long GLINT_X_PERIOD_MILLIS = 110_000L;
    public static final long GLINT_Y_PERIOD_MILLIS = 30_000L;

    private Mm2OffscreenGlintClockContract() {
    }
}
