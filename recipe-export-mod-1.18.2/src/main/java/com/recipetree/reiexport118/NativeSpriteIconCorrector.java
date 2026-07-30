package com.recipetree.reiexport118;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.datafixers.util.Pair;
import com.recipetree.reiexport118.compat.NativeSpriteIconCompatibility;
import com.recipetree.reiexport118.compat.NativeSpriteIconContract;
import dev.architectury.fluid.FluidStack;
import dev.architectury.hooks.fluid.FluidStackHooks;
import me.shedaniel.rei.api.common.entry.EntryStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Reconstructs only the exact audited fluid/gas icons from their active runtime sprites after
 * native REI rendering. The caller retains the native result unless the runtime sprite restores
 * lost alpha coverage or an animated native capture differs from its first declared frame.
 */
final class NativeSpriteIconCorrector implements AutoCloseable {
    record Correction(
            NativeImage image,
            NativeSpriteIconContract.CorrectionEvidence evidence
    ) {
    }

    private final Map<SpriteKey, SourceFrame> sourceFrameCache = new HashMap<>();
    private final Map<TintedFrameKey, int[]> tintedFrameCache = new HashMap<>();
    private Field ingredientRendererField;
    private GasAccess gasAccess;
    private boolean closed;

    boolean mayCompare(ResourceLocation typeId) {
        requireOpen();
        if (!NativeSpriteIconCompatibility.isArmed()) {
            return false;
        }
        String type = typeId.toString();
        return NativeSpriteIconContract.STANDARD_FLUID_TYPE_ID.equals(type)
                || NativeSpriteIconContract.MEKANISM_GAS_TYPE_ID.equals(type);
    }

    Correction correctAfterNativeCapture(
            EntryStack<?> stack,
            ResourceLocation typeId,
            ResourceLocation identifier,
            NativeImage nativeCapture,
            int iconScale,
            int nativeVisiblePixels
    ) {
        requireOpen();
        if (!mayCompare(typeId)) {
            return null;
        }

        String type = typeId.toString();
        NativeSpriteIconContract.requireCaptureContract(
                iconScale,
                nativeCapture.getWidth(),
                nativeCapture.getHeight(),
                nativeVisiblePixels
        );
        int nativeLogicalVisiblePixels = countLogicalVisiblePixels(nativeCapture, iconScale);
        Object renderer = stack.getRenderer();
        String outerRendererClass = className(renderer);
        Object innerRenderer = NativeSpriteIconContract.JEI_RENDERER_WRAPPER_CLASS.equals(
                outerRendererClass
        ) ? extractIngredientRenderer(renderer) : null;
        String innerRendererClass = innerRenderer == null ? null : innerRenderer.getClass().getName();
        Object value = stack.getValue();
        String valueClass = className(value);
        NativeSpriteIconContract.Kind kind = NativeSpriteIconContract.requireRendererContract(
                type,
                valueClass,
                outerRendererClass,
                innerRendererClass
        );

        ResolvedSprite resolved = switch (kind) {
            case STANDARD_FLUID -> resolveStandardFluid(value, identifier);
            case MEKANISM_GAS -> resolveMekanismGas(value, identifier);
            case NONE -> throw new IllegalStateException(
                    "Known native sprite entry unexpectedly classified as NONE: " + type);
        };
        validateRuntimeSprite(resolved.sprite(), resolved.spriteId());
        if (nativeLogicalVisiblePixels == NativeSpriteIconContract.ICON_SIZE
                * NativeSpriteIconContract.ICON_SIZE
                && resolved.sprite().getFrameCount() == 1) {
            // A fully covered static native icon needs neither source-resource I/O nor a copy.
            return null;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ResourceManager resourceManager = minecraft.getResourceManager();
        SpriteKey spriteKey = new SpriteKey(resourceManager, resolved.sprite());
        SourceFrame sourceFrame = sourceFrameCache.get(spriteKey);
        if (sourceFrame == null) {
            sourceFrame = loadAndValidateSourceFrame(resourceManager, resolved.sprite());
            sourceFrameCache.put(spriteKey, sourceFrame);
        }
        TintedFrameKey tintedKey = new TintedFrameKey(spriteKey, resolved.tint());
        int[] tintedPixels = tintedFrameCache.get(tintedKey);
        if (tintedPixels == null) {
            tintedPixels = NativeSpriteIconContract.tintFramePreservingAlpha(
                    sourceFrame.sourcePixels(),
                    resolved.tint()
            );
            tintedFrameCache.put(tintedKey, tintedPixels);
        } else {
            NativeSpriteIconContract.requireVisible(tintedPixels);
        }

        int correctedSize = NativeSpriteIconContract.ICON_SIZE * iconScale;
        NativeImage corrected = new NativeImage(correctedSize, correctedSize, false);
        try {
            for (int y = 0; y < correctedSize; y++) {
                for (int x = 0; x < correctedSize; x++) {
                    corrected.setPixelRGBA(
                            x,
                            y,
                            tintedPixels[(y / iconScale) * NativeSpriteIconContract.ICON_SIZE
                                    + (x / iconScale)]
                    );
                }
            }
            if (!ExportContext.hasVisiblePixel(corrected)) {
                throw new IllegalStateException(
                        "Corrected native sprite NativeImage is fully transparent; refusing publication");
            }
            int correctedVisiblePixels = countVisiblePixels(tintedPixels);
            int differingPixels = countDifferingPixels(nativeCapture, tintedPixels, iconScale);
            NativeSpriteIconContract.CorrectionReason reason =
                    NativeSpriteIconContract.correctionReason(
                    nativeLogicalVisiblePixels,
                    correctedVisiblePixels,
                    differingPixels,
                    sourceFrame.frameSelection().runtimeFrameCount()
            );
            if (reason == null) {
                corrected.close();
                return null;
            }

            NativeSpriteIconContract.CorrectionEvidence evidence =
                    new NativeSpriteIconContract.CorrectionEvidence(
                            kind,
                            reason,
                            identifier.toString(),
                            type,
                            outerRendererClass,
                            innerRendererClass,
                            resolved.spriteId().toString(),
                            resolved.tint(),
                            sourceFrame.textureResource().toString(),
                            sourceFrame.resourceSource(),
                            sourceFrame.sourceSha256(),
                            sourceFrame.frameSelection(),
                            nativeLogicalVisiblePixels,
                            correctedVisiblePixels,
                            differingPixels
                    );
            return new Correction(corrected, evidence);
        } catch (RuntimeException | Error throwable) {
            corrected.close();
            throw throwable;
        }
    }

    private static ResolvedSprite resolveStandardFluid(
            Object value,
            ResourceLocation identifier
    ) {
        FluidStack fluidStack = (FluidStack) value;
        if (fluidStack.isEmpty()) {
            throw new IllegalStateException("Standard fluid correction received an empty FluidStack");
        }
        ResourceLocation registryId = ForgeRegistries.FLUIDS.getKey(fluidStack.getFluid());
        if (!identifier.equals(registryId)) {
            throw new IllegalStateException("Standard fluid identifier drift: entry=" + identifier
                    + ", registry=" + registryId);
        }
        TextureAtlasSprite sprite = FluidStackHooks.getStillTexture(fluidStack);
        if (sprite == null) {
            throw new IllegalStateException("Standard fluid still-texture hook returned null for "
                    + identifier);
        }
        int tint = FluidStackHooks.getColor(fluidStack);
        return new ResolvedSprite(sprite, sprite.getName(), tint);
    }

    private ResolvedSprite resolveMekanismGas(Object value, ResourceLocation identifier) {
        GasAccess access = gasAccess();
        if (value.getClass() != access.gasStackClass()) {
            throw new IllegalStateException("Mekanism gas runtime value class drift: expected="
                    + access.gasStackClass().getName() + ", actual=" + value.getClass().getName());
        }
        if ((boolean) invoke(access.isEmpty(), value)) {
            throw new IllegalStateException("Mekanism gas correction received an empty GasStack");
        }
        ResourceLocation registryId = requireResourceLocation(
                invoke(access.getTypeRegistryName(), value),
                "GasStack.getTypeRegistryName"
        );
        if (!identifier.equals(registryId)) {
            throw new IllegalStateException("Mekanism gas identifier drift: entry=" + identifier
                    + ", registry=" + registryId);
        }

        Object chemical = invoke(access.getType(), value);
        if (chemical == null || !access.chemicalClass().isInstance(chemical)) {
            throw new IllegalStateException("Mekanism GasStack.getType class drift: expected subtype of "
                    + access.chemicalClass().getName() + ", actual=" + className(chemical));
        }
        ResourceLocation spriteId = requireResourceLocation(
                invoke(access.getIcon(), chemical),
                "Chemical.getIcon"
        );
        Object tintValue = invoke(access.getTint(), chemical);
        if (!(tintValue instanceof Integer)) {
            throw new IllegalStateException("Mekanism Chemical.getTint return drift: actual="
                    + className(tintValue));
        }
        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(spriteId);
        return new ResolvedSprite(sprite, spriteId, (Integer) tintValue);
    }

    private SourceFrame loadAndValidateSourceFrame(
            ResourceManager resourceManager,
            TextureAtlasSprite sprite
    ) {
        ResourceLocation spriteId = sprite.getName();
        ResourceLocation textureResource = new ResourceLocation(
                spriteId.getNamespace(),
                "textures/" + spriteId.getPath() + ".png"
        );
        byte[] pngBytes;
        String resourceSource;
        AnimationMetadataSection metadata;
        try (Resource resource = resourceManager.getResource(textureResource);
             InputStream input = resource.getInputStream()) {
            resourceSource = resource.getSourceName();
            metadata = resource.getMetadata(AnimationMetadataSection.SERIALIZER);
            if (metadata == null) {
                metadata = AnimationMetadataSection.EMPTY;
            }
            pngBytes = input.readAllBytes();
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Reading active runtime sprite resource failed: "
                    + textureResource + ", exception=" + exception.getClass().getName()
                    + ": " + exception.getMessage(), exception);
        }

        String sourceSha256 = sha256(pngBytes);
        try (NativeImage source = NativeImage.read(new ByteArrayInputStream(pngBytes))) {
            Pair<Integer, Integer> frameSize;
            try {
                frameSize = metadata.getFrameSize(source.getWidth(), source.getHeight());
            } catch (RuntimeException exception) {
                throw new IllegalStateException("Malformed animation metadata for "
                        + textureResource + ": " + exception.getMessage(), exception);
            }

            List<NativeSpriteIconContract.DeclaredFrame> declaredFrames = new ArrayList<>();
            metadata.forEachFrame((index, duration) -> declaredFrames.add(
                    new NativeSpriteIconContract.DeclaredFrame(index, duration)
            ));
            NativeSpriteIconContract.FrameSelection selection =
                    NativeSpriteIconContract.requireFrameSelection(
                            source.getWidth(),
                            source.getHeight(),
                            frameSize.getFirst(),
                            frameSize.getSecond(),
                            metadata.isInterpolatedFrames(),
                            metadata.getDefaultFrameTime(),
                            declaredFrames,
                            sprite.getFrameCount()
                    );

            int columns = source.getWidth() / NativeSpriteIconContract.ICON_SIZE;
            int physicalFrame = selection.firstPhysicalFrame();
            int sourceX = physicalFrame % columns * NativeSpriteIconContract.ICON_SIZE;
            int sourceY = physicalFrame / columns * NativeSpriteIconContract.ICON_SIZE;
            int[] sourcePixels = new int[NativeSpriteIconContract.ICON_SIZE
                    * NativeSpriteIconContract.ICON_SIZE];
            for (int y = 0; y < NativeSpriteIconContract.ICON_SIZE; y++) {
                for (int x = 0; x < NativeSpriteIconContract.ICON_SIZE; x++) {
                    int sourcePixel = source.getPixelRGBA(sourceX + x, sourceY + y);
                    // TextureAtlasSprite expects the physical sheet-frame index here, not the
                    // position within an explicit animation sequence.
                    int runtimePixel = sprite.getPixelRGBA(physicalFrame, x, y);
                    if (sourcePixel != runtimePixel) {
                        throw new IllegalStateException("Runtime atlas/source frame mismatch for "
                                + spriteId + " at " + x + "," + y
                                + "; active resource=" + textureResource
                                + ", firstPhysicalFrame=" + physicalFrame);
                    }
                    sourcePixels[y * NativeSpriteIconContract.ICON_SIZE + x] = sourcePixel;
                }
            }
            NativeSpriteIconContract.requireVisible(sourcePixels);
            return new SourceFrame(
                    sourcePixels,
                    countVisiblePixels(sourcePixels),
                    textureResource,
                    resourceSource,
                    sourceSha256,
                    selection
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Decoding active runtime sprite PNG failed: "
                    + textureResource + ", sourceSha256=" + sourceSha256, exception);
        }
    }

    private static void validateRuntimeSprite(
            TextureAtlasSprite sprite,
            ResourceLocation expectedSpriteId
    ) {
        if (sprite == null) {
            throw new IllegalStateException("Runtime sprite lookup returned null for "
                    + expectedSpriteId);
        }
        if (!expectedSpriteId.equals(sprite.getName())) {
            throw new IllegalStateException("Runtime sprite identifier drift: expected="
                    + expectedSpriteId + ", actual=" + sprite.getName());
        }
        if (MissingTextureAtlasSprite.getLocation().equals(sprite.getName())) {
            throw new IllegalStateException("Runtime sprite resolved to the missing-texture sprite: "
                    + expectedSpriteId);
        }
        if (sprite.getWidth() != NativeSpriteIconContract.ICON_SIZE
                || sprite.getHeight() != NativeSpriteIconContract.ICON_SIZE) {
            throw new IllegalStateException("Unsupported runtime sprite frame geometry: sprite="
                    + expectedSpriteId + ", actual=" + sprite.getWidth() + "x"
                    + sprite.getHeight() + ", required=16x16");
        }
    }

    private Object extractIngredientRenderer(Object wrapper) {
        try {
            if (ingredientRendererField == null) {
                Field field = wrapper.getClass().getDeclaredField("ingredientRenderer");
                if (!field.trySetAccessible()) {
                    throw new IllegalStateException(
                            "Cannot access exact JEI renderer wrapper ingredientRenderer field");
                }
                ingredientRendererField = field;
            }
            Object renderer = ingredientRendererField.get(wrapper);
            if (renderer == null) {
                throw new IllegalStateException(
                        "Exact JEI renderer wrapper contains a null ingredientRenderer");
            }
            return renderer;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "Inspecting exact JEI renderer wrapper failed", exception);
        }
    }

    private GasAccess gasAccess() {
        if (gasAccess != null) {
            return gasAccess;
        }
        try {
            ClassLoader loader = NativeSpriteIconCorrector.class.getClassLoader();
            Class<?> stackClass = Class.forName(
                    NativeSpriteIconContract.MEKANISM_GAS_VALUE_CLASS,
                    false,
                    loader
            );
            Class<?> chemicalClass = Class.forName(
                    "mekanism.api.chemical.Chemical",
                    false,
                    loader
            );
            gasAccess = new GasAccess(
                    stackClass,
                    chemicalClass,
                    stackClass.getMethod("isEmpty"),
                    stackClass.getMethod("getTypeRegistryName"),
                    stackClass.getMethod("getType"),
                    chemicalClass.getMethod("getIcon"),
                    chemicalClass.getMethod("getTint")
            );
            return gasAccess;
        } catch (ReflectiveOperationException | LinkageError exception) {
            throw new IllegalStateException(
                    "Resolving exact Mekanism GasStack/Chemical accessors failed", exception);
        }
    }

    private static Object invoke(Method method, Object target) {
        try {
            return method.invoke(target);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Invoking exact runtime method failed: " + method,
                    exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            throw new IllegalStateException("Exact runtime method threw: " + method
                    + ", cause=" + cause, cause);
        }
    }

    private static ResourceLocation requireResourceLocation(Object value, String method) {
        if (!(value instanceof ResourceLocation)) {
            throw new IllegalStateException(method + " return drift: actual=" + className(value));
        }
        return (ResourceLocation) value;
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM does not provide SHA-256", exception);
        }
    }

    private static String className(Object value) {
        return value == null ? "<null>" : value.getClass().getName();
    }

    private static int countVisiblePixels(int[] abgrPixels) {
        int visible = 0;
        for (int pixel : abgrPixels) {
            if ((pixel >>> 24 & 0xff) != 0) {
                visible++;
            }
        }
        return visible;
    }

    private static int countLogicalVisiblePixels(NativeImage nativeCapture, int iconScale) {
        int expectedSize = NativeSpriteIconContract.ICON_SIZE * iconScale;
        if (nativeCapture.getWidth() != expectedSize || nativeCapture.getHeight() != expectedSize) {
            throw new IllegalStateException("Native sprite logical-coverage geometry drift: native="
                    + nativeCapture.getWidth() + "x" + nativeCapture.getHeight()
                    + ", iconScale=" + iconScale);
        }
        int visible = 0;
        for (int y = 0; y < NativeSpriteIconContract.ICON_SIZE; y++) {
            for (int x = 0; x < NativeSpriteIconContract.ICON_SIZE; x++) {
                boolean texelVisible = false;
                for (int dy = 0; dy < iconScale && !texelVisible; dy++) {
                    for (int dx = 0; dx < iconScale; dx++) {
                        int pixel = nativeCapture.getPixelRGBA(x * iconScale + dx, y * iconScale + dy);
                        if ((pixel >>> 24 & 0xff) != 0) {
                            texelVisible = true;
                            break;
                        }
                    }
                }
                if (texelVisible) visible++;
            }
        }
        return visible;
    }

    private static int countDifferingPixels(
            NativeImage nativeCapture,
            int[] canonicalPixels,
            int iconScale
    ) {
        int expectedSize = NativeSpriteIconContract.ICON_SIZE * iconScale;
        if (nativeCapture.getWidth() != expectedSize
                || nativeCapture.getHeight() != expectedSize
                || canonicalPixels.length != NativeSpriteIconContract.ICON_SIZE
                * NativeSpriteIconContract.ICON_SIZE) {
            throw new IllegalStateException(
                    "Native/canonical sprite comparison geometry drift: native="
                            + nativeCapture.getWidth() + "x" + nativeCapture.getHeight()
                            + ", iconScale=" + iconScale
                            + ", canonicalPixels=" + canonicalPixels.length);
        }
        int differing = 0;
        for (int y = 0; y < NativeSpriteIconContract.ICON_SIZE; y++) {
            for (int x = 0; x < NativeSpriteIconContract.ICON_SIZE; x++) {
                int index = y * NativeSpriteIconContract.ICON_SIZE + x;
                int sample = nativeCapture.getPixelRGBA(
                        x * iconScale + iconScale / 2,
                        y * iconScale + iconScale / 2
                );
                if (sample != canonicalPixels[index]) {
                    differing++;
                }
            }
        }
        return differing;
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Native sprite icon corrector is already closed");
        }
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            for (SourceFrame frame : sourceFrameCache.values()) {
                Arrays.fill(frame.sourcePixels(), 0);
            }
            for (int[] frame : tintedFrameCache.values()) {
                Arrays.fill(frame, 0);
            }
            sourceFrameCache.clear();
            tintedFrameCache.clear();
            ingredientRendererField = null;
            gasAccess = null;
        }
    }

    private record ResolvedSprite(
            TextureAtlasSprite sprite,
            ResourceLocation spriteId,
            int tint
    ) {
    }

    private record SourceFrame(
            int[] sourcePixels,
            int visiblePixels,
            ResourceLocation textureResource,
            String resourceSource,
            String sourceSha256,
            NativeSpriteIconContract.FrameSelection frameSelection
    ) {
    }

    private static final class SpriteKey {
        private final ResourceManager resourceManager;
        private final TextureAtlasSprite sprite;

        private SpriteKey(ResourceManager resourceManager, TextureAtlasSprite sprite) {
            this.resourceManager = resourceManager;
            this.sprite = sprite;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof SpriteKey)) {
                return false;
            }
            SpriteKey key = (SpriteKey) other;
            return resourceManager == key.resourceManager && sprite == key.sprite;
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(resourceManager) + System.identityHashCode(sprite);
        }
    }

    private record TintedFrameKey(SpriteKey spriteKey, int tint) {
    }

    private record GasAccess(
            Class<?> gasStackClass,
            Class<?> chemicalClass,
            Method isEmpty,
            Method getTypeRegistryName,
            Method getType,
            Method getIcon,
            Method getTint
    ) {
    }
}
