package com.recipetree.reiexport118.compat;

import com.mojang.blaze3d.systems.RenderSystem;
import com.recipetree.reiexport118.ReiExportMod;
import com.recipetree.reiexport118.mixin.TextureAtlasSpritesAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Canonicalizes the native block atlas once and freezes only its animation ticker for one export.
 *
 * <p>This operates on Minecraft's active GPU atlas. It does not synthesize pixels, resample
 * textures, replace REI widgets, or alter the 16x16 catalog / 2x layout capture contracts.</p>
 */
public final class Mm2BlockAtlasCanonicalization {
    private static final Object LOCK = new Object();
    private static volatile Active active;

    private Mm2BlockAtlasCanonicalization() {
    }

    public static Scope beginIfApplicable() {
        if (!Mm2DeterminismCompatibility.isLifecycleArmed()) {
            return Scope.inactive();
        }
        if (!Mm2BlockAtlasCanonicalizationCompatibility.isArmed()) {
            throw new IllegalStateException(
                    "MM2 export reached block-atlas canonicalization before exact preflight arm");
        }
        if (!RenderSystem.isOnRenderThread()) {
            throw new IllegalStateException(
                    "MM2 block-atlas canonicalization must begin on the render thread; actual="
                            + Thread.currentThread().getName());
        }

        synchronized (LOCK) {
            if (active != null) {
                throw new IllegalStateException(
                        "Nested MM2 block-atlas canonicalization scope: activeOwner="
                                + active.owner().getName());
            }
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getModelManager() == null) {
            throw new IllegalStateException(
                    "Minecraft model manager is unavailable at block-atlas canonicalization");
        }
        TextureAtlas atlas = minecraft.getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS);
        if (atlas == null || !TextureAtlas.LOCATION_BLOCKS.equals(atlas.location())) {
            throw new IllegalStateException(
                    "Resolved block-atlas identity drift: expected=" + TextureAtlas.LOCATION_BLOCKS
                            + ", actual=" + (atlas == null ? null : atlas.location()));
        }
        if (!(atlas instanceof TextureAtlasSpritesAccessor accessor)) {
            throw new IllegalStateException(
                    "Exact TextureAtlas sprite-map accessor was not applied; no reflection or "
                            + "partial canonicalization fallback was attempted");
        }

        Map<ResourceLocation, TextureAtlasSprite> sprites = accessor.reiexport$getTexturesByName();
        if (sprites == null || sprites.isEmpty()) {
            throw new IllegalStateException("Active block atlas exposes no stitched sprites");
        }
        List<Map.Entry<ResourceLocation, TextureAtlasSprite>> animated = new ArrayList<>();
        for (Map.Entry<ResourceLocation, TextureAtlasSprite> entry : sprites.entrySet()) {
            ResourceLocation id = entry.getKey();
            TextureAtlasSprite sprite = entry.getValue();
            if (id == null || sprite == null) {
                throw new IllegalStateException(
                        "Active block atlas contains a null sprite mapping: id=" + id
                                + ", sprite=" + sprite);
            }
            if (!id.equals(sprite.getName())) {
                throw new IllegalStateException(
                        "Active block atlas key/name drift: key=" + id
                                + ", spriteName=" + sprite.getName());
            }
            if (sprite.getAnimationTicker() != null) {
                if (sprite.getFrameCount() <= 1) {
                    throw new IllegalStateException(
                            "Animated block-atlas sprite exposes fewer than two frames: " + id
                                    + " frames=" + sprite.getFrameCount());
                }
                animated.add(entry);
            }
        }
        animated.sort(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)));
        if (animated.isEmpty()) {
            throw new IllegalStateException(
                    "Exact MM2 block atlas contains no animated sprites to canonicalize");
        }

        ResourceLocation memoryEssenceId = new ResourceLocation(
                Mm2BlockAtlasCanonicalizationContract.MEMORY_ESSENCE_SPRITE_ID);
        TextureAtlasSprite memoryEssence = sprites.get(memoryEssenceId);
        if (memoryEssence == null || memoryEssence.getAnimationTicker() == null
                || memoryEssence.getFrameCount()
                != Mm2BlockAtlasCanonicalizationContract.MEMORY_ESSENCE_FRAME_COUNT) {
            throw new IllegalStateException(
                    "MM2 Memory Essence animation contract drift: sprite="
                            + memoryEssenceId
                            + ", present=" + (memoryEssence != null)
                            + ", animated=" + (memoryEssence != null
                            && memoryEssence.getAnimationTicker() != null)
                            + ", expectedFrames="
                            + Mm2BlockAtlasCanonicalizationContract.MEMORY_ESSENCE_FRAME_COUNT
                            + ", actualFrames="
                            + (memoryEssence == null ? null : memoryEssence.getFrameCount()));
        }

        atlas.bind();
        for (Map.Entry<ResourceLocation, TextureAtlasSprite> entry : animated) {
            entry.getValue().uploadFirstFrame();
        }

        Active opened = new Active(
                atlas,
                Thread.currentThread(),
                animated.size(),
                sha256Ids(animated));
        synchronized (LOCK) {
            if (active != null) {
                throw new IllegalStateException(
                        "MM2 block-atlas canonicalization scope appeared during render-thread open");
            }
            active = opened;
        }
        ReiExportMod.LOGGER.warn(
                "[reiexport] Canonicalized and froze exact MM2 native block atlas for one "
                        + "export job: atlas={}, stitchedSprites={}, animatedSprites={}, "
                        + "animatedIdsSha256={}, memoryEssenceFrames={}; every animated sprite "
                        + "was uploaded once through Minecraft uploadFirstFrame(), with no "
                        + "replacement art, scaling, interpolation, or per-widget upload loop",
                atlas.location(),
                sprites.size(),
                opened.animatedSprites(),
                opened.animatedIdsSha256(),
                memoryEssence.getFrameCount());
        return new Scope(opened, true);
    }

    /** Called only by the exact byte-pinned TextureAtlas injection. */
    public static boolean suppressCycleIfScoped(TextureAtlas atlas) {
        Active current = active;
        if (current == null) {
            return false;
        }
        if (!Mm2BlockAtlasCanonicalizationCompatibility.isArmed()) {
            throw new IllegalStateException(
                    "MM2 block-atlas tick guard remained active after compatibility disarm");
        }
        if (Thread.currentThread() != current.owner()) {
            throw new IllegalStateException(
                    "MM2 block-atlas tick guard crossed threads: owner="
                            + current.owner().getName() + ", actual="
                            + Thread.currentThread().getName());
        }
        if (atlas != current.atlas()) {
            if (atlas != null && TextureAtlas.LOCATION_BLOCKS.equals(atlas.location())) {
                throw new IllegalStateException(
                        "Minecraft replaced the block-atlas object during an active deterministic "
                                + "export; refusing an uncanonicalized atlas identity");
            }
            return false;
        }
        current.suppressedTicks++;
        return true;
    }

    private static String sha256Ids(
            List<Map.Entry<ResourceLocation, TextureAtlasSprite>> animated
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Map.Entry<ResourceLocation, TextureAtlasSprite> entry : animated) {
                digest.update(entry.getKey().toString().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '\n');
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static final class Active {
        private final TextureAtlas atlas;
        private final Thread owner;
        private final int animatedSprites;
        private final String animatedIdsSha256;
        private long suppressedTicks;

        private Active(
                TextureAtlas atlas,
                Thread owner,
                int animatedSprites,
                String animatedIdsSha256
        ) {
            this.atlas = atlas;
            this.owner = owner;
            this.animatedSprites = animatedSprites;
            this.animatedIdsSha256 = animatedIdsSha256;
        }

        private TextureAtlas atlas() {
            return atlas;
        }

        private Thread owner() {
            return owner;
        }

        private int animatedSprites() {
            return animatedSprites;
        }

        private String animatedIdsSha256() {
            return animatedIdsSha256;
        }
    }

    public static final class Scope implements AutoCloseable {
        private final Active state;
        private final boolean applicable;
        private boolean closed;

        private Scope(Active state, boolean applicable) {
            this.state = state;
            this.applicable = applicable;
        }

        private static Scope inactive() {
            return new Scope(null, false);
        }

        @Override
        public void close() {
            if (closed) {
                throw new IllegalStateException(
                        "MM2 block-atlas canonicalization scope closed twice");
            }
            closed = true;
            if (!applicable) {
                return;
            }
            if (Thread.currentThread() != state.owner()) {
                throw new IllegalStateException(
                        "MM2 block-atlas canonicalization scope closed on the wrong thread: owner="
                                + state.owner().getName() + ", closer="
                                + Thread.currentThread().getName());
            }
            synchronized (LOCK) {
                if (active != state) {
                    throw new IllegalStateException(
                            "MM2 block-atlas canonicalization scope ownership drift");
                }
                active = null;
            }
            ReiExportMod.LOGGER.info(
                    "[reiexport] Released exact MM2 native block-atlas freeze: atlas={}, "
                            + "animatedSprites={}, animatedIdsSha256={}, suppressedAnimationTicks={}; "
                            + "subsequent gameplay atlas ticks retain upstream behavior",
                    state.atlas().location(),
                    state.animatedSprites(),
                    state.animatedIdsSha256(),
                    state.suppressedTicks);
        }
    }
}
