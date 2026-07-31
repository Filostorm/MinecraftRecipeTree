package com.recipetree.jeiexport112.compat;

/** Validates Forge's proxy-tested texture-size cache without issuing any OpenGL calls. */
public final class TextureSizeGuard {
    private static final int MINIMUM_TEXTURE_SIZE = 2_048;
    private static final int FORGE_FIRST_PROXY_SIZE = 16_384;

    private TextureSizeGuard() {
    }

    public static int validateForgeCachedMaximumTextureSize(int cachedMaximum) {
        boolean powerOfTwo = cachedMaximum > 0 &&
                (cachedMaximum & (cachedMaximum - 1)) == 0;
        if (!powerOfTwo || cachedMaximum < MINIMUM_TEXTURE_SIZE ||
                cachedMaximum > FORGE_FIRST_PROXY_SIZE) {
            throw new IllegalStateException(
                    "[jeiexport] Forge SplashProgress returned invalid maximum texture " +
                            "size=" + cachedMaximum + "; expected a proxy-tested power of " +
                            "two in [" + MINIMUM_TEXTURE_SIZE + ", " +
                            FORGE_FIRST_PROXY_SIZE + "]. No OpenGL re-probe, context " +
                            "rebind, retry, or fabricated capability fallback was attempted."
            );
        }

        System.out.println(
                "[jeiexport] Validated Forge SplashProgress maximum texture cache=" +
                        cachedMaximum + ". The exporter preserved Forge's proxy-test/cache " +
                        "lifecycle and issued no additional OpenGL query."
        );
        return cachedMaximum;
    }
}
