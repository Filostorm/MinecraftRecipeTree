package com.recipetree.neiexport1710;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.IIcon;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Exact compatibility lease for three malformed ProjectBlue control-panel materials.
 *
 * <p>ProjectBlue bypasses ForgeMultipart's safe, already-stitched face-icon cache and calls the
 * source block directly. One pinned source returns {@code null}; two index past their icon arrays.
 * NEI catches those owner-renderer failures and draws fire, which is a false item image. For only
 * the three audited canonical stack identities, this lease temporarily replaces the public
 * {@code ControlPanelMaterial.block} reference with an unregistered block proxy backed by the
 * exact six icons already loaded by that material's {@code BlockMicroMaterial.icont}. ProjectBlue
 * still renders its own panel geometry. The original material and Forge renderer registration are
 * restored and verified after every call.</p>
 */
final class ProjectBlueControlPanelIconRenderer {
    static final String CONTRACT =
            "projectblue-control-panel-fmp-cached-face-icons-owner-renderer-v1";
    static final String ITEM_REGISTRY_ID = "ProjectBlue:controlPanel";
    static final String ITEM_CLASS = "gcewing.projectblue.ControlPanelItem";
    static final String OWNER_RENDERER_CLASS =
            "gcewing.projectblue.ControlPanelItemRenderer";
    static final String CONTROL_PANEL_MATERIAL_CLASS =
            "gcewing.projectblue.ControlPanelMaterial";
    static final String MICRO_MATERIAL_REGISTRY_CLASS =
            "codechicken.microblock.MicroMaterialRegistry";
    static final String BLOCK_MICRO_MATERIAL_CLASS =
            "codechicken.microblock.BlockMicroMaterial";
    static final String MULTI_ICON_TRANSFORMATION_CLASS =
            "codechicken.lib.render.uv.MultiIconTransformation";
    static final String MISSING_ICON_NAME = "missingno";
    static final int EXPECTED_TARGETS = 3;

    static final MaterialPin[] PINS = {
        new MaterialPin(
                "item|ProjectBlue:controlPanel|meta=5|nbt="
                        + "5e9d813c721cd0529491e5f7304b191e21a9a88fcd71c7005b4c0172bc45e6bb",
                "5e9d813c721cd0529491e5f7304b191e21a9a88fcd71c7005b4c0172bc45e6bb",
                5, "Automagy:blockNetherRune_5", "Automagy:blockNetherRune",
                "tuhljin.automagy.blocks.BlockNetherRune", 0x3f, null),
        new MaterialPin(
                "item|ProjectBlue:controlPanel|meta=15|nbt="
                        + "87d2a35ccc7ada13accee33eeeb54132dd49e4b0908fc4d100ab6cb850752313",
                "87d2a35ccc7ada13accee33eeeb54132dd49e4b0908fc4d100ab6cb850752313",
                15, "gregtech:gt.blockcasings6_15", "gregtech:gt.blockcasings6",
                "gregtech.common.blocks.BlockCasings6", 0x03,
                "gregtech:iconsets/MACHINE_CASING_TANK_0"),
        new MaterialPin(
                "item|ProjectBlue:controlPanel|meta=15|nbt="
                        + "b45727dc3e966a026a487cbf0836be93d6ab82d6b0c2faef16840168d0c6c4b5",
                "b45727dc3e966a026a487cbf0836be93d6ab82d6b0c2faef16840168d0c6c4b5",
                15, "gregtech:gt.blockcasingsNH_15", "gregtech:gt.blockcasingsNH",
                "gregtech.common.blocks.BlockCasingsNH", 0x3f, null)
    };

    interface RendererBinding {
        IItemRenderer get(Item item);

        void set(Item item, IItemRenderer renderer);
    }

    interface TargetResolver {
        boolean isTarget(ItemStack stack);
    }

    interface OwnerInvocation {
        void invoke(IItemRenderer owner, IItemRenderer.ItemRenderType type,
                    ItemStack stack, Object[] data) throws Throwable;
    }

    interface ScopedMutation {
        void install() throws Throwable;

        void invoke() throws Throwable;

        void restore() throws Throwable;

        void verifyRestored() throws Throwable;
    }

    static final class MaterialPin {
        final String canonicalKey;
        final String nbtSha256;
        final int metadata;
        final String materialName;
        final String sourceRegistryId;
        final String sourceBlockClass;
        final int missingSideMask;
        final String nonMissingIconName;

        MaterialPin(String canonicalKey, String nbtSha256, int metadata,
                    String materialName, String sourceRegistryId,
                    String sourceBlockClass, int missingSideMask,
                    String nonMissingIconName) {
            this.canonicalKey = canonicalKey;
            this.nbtSha256 = nbtSha256;
            this.metadata = metadata;
            this.materialName = materialName;
            this.sourceRegistryId = sourceRegistryId;
            this.sourceBlockClass = sourceBlockClass;
            this.missingSideMask = missingSideMask;
            this.nonMissingIconName = nonMissingIconName;
        }
    }

    private final Item item;
    private final RendererBinding rendererBinding;
    private final IItemRenderer ownerRenderer;
    private final CountingItemRenderer adapterRenderer;
    private final Thread renderThread;
    private final boolean requireMinecraftClientThread;
    private boolean leaseActive;

    ProjectBlueControlPanelIconRenderer(
            Item item, RendererBinding rendererBinding, IItemRenderer ownerRenderer,
            CountingItemRenderer adapterRenderer) {
        this(item, rendererBinding, ownerRenderer, adapterRenderer, false);
    }

    private ProjectBlueControlPanelIconRenderer(
            Item item, RendererBinding rendererBinding, IItemRenderer ownerRenderer,
            CountingItemRenderer adapterRenderer, boolean requireMinecraftClientThread) {
        if (item == null || rendererBinding == null || ownerRenderer == null
                || adapterRenderer == null) {
            throw new IllegalArgumentException(
                    "ITEM_ICON_RENDER: ProjectBlue renderer lease dependencies are required");
        }
        this.item = item;
        this.rendererBinding = rendererBinding;
        this.ownerRenderer = ownerRenderer;
        this.adapterRenderer = adapterRenderer;
        this.renderThread = Thread.currentThread();
        this.requireMinecraftClientThread = requireMinecraftClientThread;
    }

    static ProjectBlueControlPanelIconRenderer create(ItemStack target) throws Exception {
        if (!isPinnedTarget(target)) {
            throw new IllegalArgumentException(
                    "ITEM_ICON_RENDER: ProjectBlue adapter requires one of its three exact "
                            + "canonical control-panel stacks");
        }
        final Item item = target.getItem();
        requirePinnedItem(item);

        Field registryField = MinecraftForgeClient.class.getDeclaredField(
                "customItemRenderers");
        if (!Modifier.isStatic(registryField.getModifiers())
                || !Map.class.isAssignableFrom(registryField.getType())) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: Forge custom item renderer registry shape drifted");
        }
        registryField.setAccessible(true);
        Object rawRegistry = registryField.get(null);
        if (!(rawRegistry instanceof IdentityHashMap)) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: Forge custom item renderer registry is not an "
                            + "IdentityHashMap; got " + describe(rawRegistry));
        }
        @SuppressWarnings("unchecked")
        Map<Item, IItemRenderer> registry =
                (Map<Item, IItemRenderer>) (Map<?, ?>) rawRegistry;
        ForgeRendererBinding binding = new ForgeRendererBinding(registry);
        IItemRenderer owner = binding.get(item);
        String ownerClass = owner == null ? "<null>" : owner.getClass().getName();
        if (!OWNER_RENDERER_CLASS.equals(ownerClass)) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: pinned ProjectBlue owner renderer mismatch; expected "
                            + OWNER_RENDERER_CLASS + ", got " + ownerClass);
        }

        final RuntimeOwnerInvocation invocation;
        try {
            invocation = RuntimeOwnerInvocation.create(item);
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            if (error instanceof Exception) {
                throw (Exception) error;
            }
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: ProjectBlue/FMP runtime preflight failed", error);
        }
        for (MaterialPin pin : PINS) {
            ItemStack pinnedStack = stackFor(item, pin);
            if (!owner.handleRenderType(pinnedStack, IItemRenderer.ItemRenderType.INVENTORY)
                    || MinecraftForgeClient.getItemRenderer(
                            pinnedStack, IItemRenderer.ItemRenderType.INVENTORY) != owner) {
                throw new IllegalStateException(
                        "ITEM_ICON_RENDER: ProjectBlue owner no longer owns Forge INVENTORY for "
                                + pin.canonicalKey);
            }
        }
        CountingItemRenderer adapter = new CountingItemRenderer(
                owner, new TargetResolver() {
                    @Override
                    public boolean isTarget(ItemStack stack) {
                        return isPinnedTarget(stack);
                    }
                }, invocation);
        return new ProjectBlueControlPanelIconRenderer(
                item, binding, owner, adapter, true);
    }

    void drawExactlyOnce(OffscreenRenderer.DrawCall ownerInventoryDraw) throws Exception {
        long invocations = drawAndCount(ownerInventoryDraw);
        if (invocations != 1L) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: ProjectBlue owner inventory path invoked the exact "
                            + "material adapter " + invocations + " times instead of once");
        }
    }

    synchronized long drawAndCount(OffscreenRenderer.DrawCall ownerDraw) throws Exception {
        if (ownerDraw == null) {
            throw new IllegalArgumentException(
                    "ITEM_ICON_RENDER: ProjectBlue owner draw is required");
        }
        if (Thread.currentThread() != renderThread) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: ProjectBlue renderer lease left its pinned render thread");
        }
        if (requireMinecraftClientThread
                && (Minecraft.getMinecraft() == null
                || !Minecraft.getMinecraft().func_152345_ab())) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: ProjectBlue renderer lease is not on Minecraft's client "
                            + "thread");
        }
        if (leaseActive) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: nested ProjectBlue renderer leases are forbidden");
        }
        if (rendererBinding.get(item) != ownerRenderer) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: ProjectBlue owner renderer binding drifted before draw");
        }

        long attemptsBefore = adapterRenderer.attempts;
        long successesBefore = adapterRenderer.successes;
        long failuresBefore = adapterRenderer.failures;
        Throwable failure = null;
        leaseActive = true;
        try {
            rendererBinding.set(item, adapterRenderer);
            if (rendererBinding.get(item) != adapterRenderer) {
                throw new IllegalStateException(
                        "ITEM_ICON_RENDER: ProjectBlue adapter registration was not exact");
            }
            ownerDraw.draw();
        } catch (Throwable error) {
            failure = error;
        } finally {
            try {
                if (rendererBinding.get(item) != adapterRenderer) {
                    failure = merge(failure, new IllegalStateException(
                            "ITEM_ICON_RENDER: ProjectBlue renderer binding changed during "
                                    + "its lease"));
                }
                rendererBinding.set(item, ownerRenderer);
                if (rendererBinding.get(item) != ownerRenderer) {
                    failure = merge(failure, new IllegalStateException(
                            "ITEM_ICON_RENDER: ProjectBlue owner renderer restoration was not "
                                    + "exact"));
                }
            } catch (Throwable restore) {
                failure = merge(failure, restore);
            }
            leaseActive = false;
        }

        long attempts = adapterRenderer.attempts - attemptsBefore;
        long successes = adapterRenderer.successes - successesBefore;
        long failures = adapterRenderer.failures - failuresBefore;
        if (attempts < 0L || successes < 0L || failures < 0L
                || attempts != successes + failures) {
            failure = merge(failure, new IllegalStateException(
                    "ITEM_ICON_RENDER: ProjectBlue adapter telemetry drifted; attempts="
                            + attempts + ", successes=" + successes + ", failures=" + failures));
        }
        if (failures != 0L) {
            FatalErrors.rethrowIfFatal(adapterRenderer.lastFailure);
            failure = merge(failure, new IllegalStateException(
                    "ITEM_ICON_RENDER: ProjectBlue adapter failed " + failures
                            + " time(s) inside an owner path that may swallow exceptions",
                    adapterRenderer.lastFailure));
        }
        rethrow(failure);
        return successes;
    }

    static boolean isPinnedTarget(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return false;
        }
        GameRegistry.UniqueIdentifier identifier =
                GameRegistry.findUniqueIdentifierFor(stack.getItem());
        if (identifier == null) {
            return false;
        }
        String registryId = identifier.modId + ":" + identifier.name;
        MaterialPin pin = pinForExactIdentity(
                registryId, stack.getItem().getClass().getName(), stack.stackSize,
                stack.getItemDamage(), stack.getTagCompound());
        if (pin == null) {
            return false;
        }
        requirePinnedItem(stack.getItem());
        return true;
    }

    static boolean isPinnedCanonicalKey(String key) {
        if (key == null) {
            return false;
        }
        for (MaterialPin pin : PINS) {
            if (pin.canonicalKey.equals(key)) {
                return true;
            }
        }
        return false;
    }

    static boolean containsPinnedCanonicalKey(Iterable<String> keys) {
        if (keys == null) {
            return false;
        }
        for (String key : keys) {
            if (isPinnedCanonicalKey(key)) {
                return true;
            }
        }
        return false;
    }

    static MaterialPin pinForExactIdentity(
            String registryId, String itemClass, int stackSize, int metadata,
            NBTTagCompound tag) {
        if (!ITEM_REGISTRY_ID.equals(registryId)) {
            return null;
        }
        if (!ITEM_CLASS.equals(itemClass)) {
            throw new IllegalArgumentException(
                    "ITEM_ICON_RENDER: ProjectBlue controlPanel runtime class drifted; expected "
                            + ITEM_CLASS + ", got " + itemClass);
        }
        if (tag == null || !tag.hasKey("mat", 8)
                || tag.func_150296_c().size() != 1) {
            return null;
        }
        String materialName = tag.getString("mat");
        String digest = Naming.sha256(NbtCanonicalizer.canonical(tag));
        for (MaterialPin pin : PINS) {
            if (pin.metadata == metadata && pin.materialName.equals(materialName)
                    && pin.nbtSha256.equals(digest)) {
                if (stackSize != 1) {
                    throw new IllegalArgumentException(
                            "ITEM_ICON_RENDER: pinned ProjectBlue control panel requires amount "
                                    + "1; got " + stackSize + " for " + pin.canonicalKey);
                }
                return pin;
            }
        }
        return null;
    }

    static void runRestoring(ScopedMutation mutation) throws Throwable {
        Throwable failure = null;
        try {
            mutation.install();
            mutation.invoke();
        } catch (Throwable error) {
            failure = error;
        } finally {
            try {
                mutation.restore();
            } catch (Throwable restore) {
                failure = merge(failure, restore);
            }
            try {
                mutation.verifyRestored();
            } catch (Throwable verification) {
                failure = merge(failure, verification);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    static final class CountingItemRenderer implements IItemRenderer {
        private final IItemRenderer owner;
        private final TargetResolver resolver;
        private final OwnerInvocation invocation;
        long attempts;
        long successes;
        long failures;
        Throwable lastFailure;

        CountingItemRenderer(
                IItemRenderer owner, TargetResolver resolver, OwnerInvocation invocation) {
            if (owner == null || resolver == null || invocation == null) {
                throw new IllegalArgumentException(
                        "ITEM_ICON_RENDER: ProjectBlue adapter dependencies are required");
            }
            this.owner = owner;
            this.resolver = resolver;
            this.invocation = invocation;
        }

        @Override
        public boolean handleRenderType(ItemStack item, ItemRenderType type) {
            return owner.handleRenderType(item, type);
        }

        @Override
        public boolean shouldUseRenderHelper(
                ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
            return owner.shouldUseRenderHelper(type, item, helper);
        }

        @Override
        public void renderItem(ItemRenderType type, ItemStack stack, Object... data) {
            if (!resolver.isTarget(stack)) {
                owner.renderItem(type, stack, data);
                return;
            }
            attempts++;
            Throwable failure = null;
            try {
                invocation.invoke(owner, type, stack, data);
            } catch (Throwable error) {
                failure = error;
            }
            if (failure != null) {
                failures++;
                lastFailure = failure;
                FatalErrors.rethrowIfFatal(failure);
                throw new IllegalStateException(
                        "ITEM_ICON_RENDER: ProjectBlue exact owner renderer failed", failure);
            }
            successes++;
        }
    }

    private static final class RuntimeOwnerInvocation implements OwnerInvocation {
        private final Item item;
        private final Method forStack;
        private final Field panelName;
        private final Field panelBlock;
        private final Field panelMetadata;
        private final RuntimeMaterial[] materials;
        private boolean invocationActive;

        private RuntimeOwnerInvocation(
                Item item, Method forStack, Field panelName, Field panelBlock,
                Field panelMetadata, RuntimeMaterial[] materials) {
            this.item = item;
            this.forStack = forStack;
            this.panelName = panelName;
            this.panelBlock = panelBlock;
            this.panelMetadata = panelMetadata;
            this.materials = materials;
        }

        static RuntimeOwnerInvocation create(Item item) throws Throwable {
            Class<?> panelClass = Class.forName(CONTROL_PANEL_MATERIAL_CLASS);
            Method forStack = panelClass.getMethod("forStack", ItemStack.class);
            requirePublicStatic(forStack, panelClass, "ProjectBlue ControlPanelMaterial.forStack");
            if (forStack.getReturnType() != panelClass) {
                throw new IllegalStateException(
                        "ITEM_ICON_RENDER: ControlPanelMaterial.forStack return type drifted");
            }
            Field panelName = requirePublicInstanceField(panelClass, "name", String.class, false);
            Field panelBlock = requirePublicInstanceField(panelClass, "block", Block.class, true);
            Field panelMetadata = requirePublicInstanceField(
                    panelClass, "metadata", Integer.TYPE, false);

            Class<?> registryClass = Class.forName(MICRO_MATERIAL_REGISTRY_CLASS);
            Method getMaterial = registryClass.getMethod("getMaterial", String.class);
            requirePublicStatic(getMaterial, registryClass, "MicroMaterialRegistry.getMaterial");
            Class<?> blockMaterialClass = Class.forName(BLOCK_MICRO_MATERIAL_CLASS);
            Method materialBlock = blockMaterialClass.getMethod("block");
            Method materialMeta = blockMaterialClass.getMethod("meta");
            Method materialIcons = blockMaterialClass.getMethod("icont");
            Method materialItem = blockMaterialClass.getMethod("getItem");
            Class<?> multiIconClass = Class.forName(MULTI_ICON_TRANSFORMATION_CLASS);
            Field iconsField = requirePublicInstanceField(
                    multiIconClass, "icons", IIcon[].class, false);

            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft == null || minecraft.getTextureMapBlocks() == null) {
                throw new IllegalStateException(
                        "ITEM_ICON_RENDER: block atlas is unavailable during ProjectBlue "
                                + "material preflight");
            }
            IIcon missing = minecraft.getTextureMapBlocks().getAtlasSprite(MISSING_ICON_NAME);
            if (missing == null || !MISSING_ICON_NAME.equals(missing.getIconName())) {
                throw new IllegalStateException(
                        "ITEM_ICON_RENDER: canonical block-atlas missing sprite is unavailable");
            }

            RuntimeMaterial[] materials = new RuntimeMaterial[PINS.length];
            for (int index = 0; index < PINS.length; index++) {
                MaterialPin pin = PINS[index];
                ItemStack stack = stackFor(item, pin);
                Object panel = ProjectBlueControlPanelIconRenderer.invoke(
                        forStack, null, stack);
                if (panel == null || panel.getClass() != panelClass) {
                    throw new IllegalStateException(
                            "ITEM_ICON_RENDER: ProjectBlue material cache did not return its "
                                    + "exact runtime class for " + pin.materialName);
                }
                Object micro = ProjectBlueControlPanelIconRenderer.invoke(
                        getMaterial, null, pin.materialName);
                if (micro == null || micro.getClass() != blockMaterialClass) {
                    throw new IllegalStateException(
                            "ITEM_ICON_RENDER: pinned ForgeMultipart material class drifted for "
                                    + pin.materialName + "; got " + describe(micro));
                }
                Block sourceBlock = (Block) ProjectBlueControlPanelIconRenderer.invoke(
                        materialBlock, micro);
                int sourceMeta = ((Number) ProjectBlueControlPanelIconRenderer.invoke(
                        materialMeta, micro)).intValue();
                Block registeredBlock = findBlock(pin.sourceRegistryId);
                if (sourceBlock == null || sourceBlock != registeredBlock
                        || !pin.sourceBlockClass.equals(sourceBlock.getClass().getName())
                        || sourceMeta != pin.metadata) {
                    throw new IllegalStateException(
                            "ITEM_ICON_RENDER: pinned ForgeMultipart source topology drifted for "
                                    + pin.materialName + "; block=" + describe(sourceBlock)
                                    + ", meta=" + sourceMeta);
                }
                ItemStack materialStack = (ItemStack)
                        ProjectBlueControlPanelIconRenderer.invoke(materialItem, micro);
                if (materialStack == null || !(materialStack.getItem() instanceof ItemBlock)
                        || Block.getBlockFromItem(materialStack.getItem()) != sourceBlock
                        || materialStack.stackSize != 1
                        || materialStack.getItemDamage() != pin.metadata
                        || materialStack.hasTagCompound()) {
                    throw new IllegalStateException(
                            "ITEM_ICON_RENDER: pinned ForgeMultipart material item drifted for "
                                    + pin.materialName);
                }
                if (!pin.materialName.equals(panelName.get(panel))
                        || panelBlock.get(panel) != sourceBlock
                        || panelMetadata.getInt(panel) != pin.metadata) {
                    throw new IllegalStateException(
                            "ITEM_ICON_RENDER: ProjectBlue cached material topology drifted for "
                                    + pin.materialName);
                }
                Object transform = ProjectBlueControlPanelIconRenderer.invoke(
                        materialIcons, micro);
                if (transform == null || transform.getClass() != multiIconClass) {
                    throw new IllegalStateException(
                            "ITEM_ICON_RENDER: ForgeMultipart icon transformation drifted for "
                                    + pin.materialName + "; got " + describe(transform));
                }
                Object rawIcons = iconsField.get(transform);
                if (!(rawIcons instanceof IIcon[])) {
                    throw new IllegalStateException(
                            "ITEM_ICON_RENDER: ForgeMultipart cached icon array is unavailable for "
                                    + pin.materialName);
                }
                IIcon[] icons = (IIcon[]) rawIcons;
                if (icons.length != 6) {
                    throw new IllegalStateException(
                            "ITEM_ICON_RENDER: ForgeMultipart cached icon cardinality drifted for "
                                    + pin.materialName + "; expected 6, got " + icons.length);
                }
                IIcon[] pinnedIcons = icons.clone();
                for (int side = 0; side < pinnedIcons.length; side++) {
                    IIcon icon = pinnedIcons[side];
                    boolean expectsMissing = (pin.missingSideMask & (1 << side)) != 0;
                    String expectedIconName = expectsMissing
                            ? MISSING_ICON_NAME : pin.nonMissingIconName;
                    if (expectedIconName == null) {
                        throw new IllegalStateException(
                                "ITEM_ICON_RENDER: incomplete pinned ForgeMultipart icon "
                                        + "topology for " + pin.materialName + " side=" + side);
                    }
                    IIcon expectedIcon = minecraft.getTextureMapBlocks()
                            .getAtlasSprite(expectedIconName);
                    if (icon == null || icon != expectedIcon
                            || !expectedIconName.equals(icon.getIconName())) {
                        throw new IllegalStateException(
                                "ITEM_ICON_RENDER: pinned malformed material's ForgeMultipart "
                                        + "safe icon topology drifted; "
                                        + "material=" + pin.materialName + ", side=" + side
                                        + ", expected=" + expectedIconName + ", icon="
                                        + describeIcon(icon));
                    }
                }
                materials[index] = new RuntimeMaterial(
                        pin, panel, sourceBlock, transform, iconsField, icons, pinnedIcons);
            }
            return new RuntimeOwnerInvocation(
                    item, forStack, panelName, panelBlock, panelMetadata, materials);
        }

        @Override
        public synchronized void invoke(
                final IItemRenderer owner, final IItemRenderer.ItemRenderType type,
                final ItemStack stack, final Object[] data) throws Throwable {
            if (invocationActive) {
                throw new IllegalStateException(
                        "ITEM_ICON_RENDER: nested ProjectBlue owner invocations are forbidden");
            }
            if (type != IItemRenderer.ItemRenderType.INVENTORY) {
                throw new IllegalArgumentException(
                        "ITEM_ICON_RENDER: ProjectBlue exact adapter received " + type
                                + " instead of INVENTORY");
            }
            MaterialPin pin = requirePin(stack);
            final RuntimeMaterial runtime = materials[indexOf(pin)];
            runtime.requireUnchanged(forStack, panelName, panelBlock, panelMetadata, stack);
            invocationActive = true;
            try {
                runRestoring(new ScopedMutation() {
                    @Override
                    public void install() throws Throwable {
                        panelBlock.set(runtime.panel, runtime.proxy);
                        if (panelBlock.get(runtime.panel) != runtime.proxy
                                || panelMetadata.getInt(runtime.panel)
                                != runtime.pin.metadata
                                || !runtime.pin.materialName.equals(
                                        panelName.get(runtime.panel))) {
                            throw new IllegalStateException(
                                    "ITEM_ICON_RENDER: ProjectBlue icon proxy installation was "
                                            + "not exact for " + runtime.pin.materialName);
                        }
                    }

                    @Override
                    public void invoke() {
                        owner.renderItem(type, stack, data);
                    }

                    @Override
                    public void restore() throws Throwable {
                        Throwable drift = null;
                        if (panelBlock.get(runtime.panel) != runtime.proxy) {
                            drift = new IllegalStateException(
                                    "ITEM_ICON_RENDER: ProjectBlue owner changed the installed "
                                            + "material block proxy; repaired before abort");
                        }
                        if (panelName.get(runtime.panel) == null
                                || !runtime.pin.materialName.equals(
                                        panelName.get(runtime.panel))) {
                            drift = merge(drift, new IllegalStateException(
                                    "ITEM_ICON_RENDER: ProjectBlue owner mutated material name; "
                                            + "repaired before abort"));
                        }
                        if (panelMetadata.getInt(runtime.panel) != runtime.pin.metadata) {
                            drift = merge(drift, new IllegalStateException(
                                    "ITEM_ICON_RENDER: ProjectBlue owner mutated material metadata; "
                                            + "repaired before abort"));
                        }
                        panelName.set(runtime.panel, runtime.pin.materialName);
                        panelMetadata.setInt(runtime.panel, runtime.pin.metadata);
                        panelBlock.set(runtime.panel, runtime.sourceBlock);
                        if (drift != null) {
                            throw drift;
                        }
                    }

                    @Override
                    public void verifyRestored() throws Throwable {
                        runtime.requireUnchanged(
                                forStack, panelName, panelBlock, panelMetadata, stack);
                    }
                });
            } finally {
                invocationActive = false;
            }
        }
    }

    private static final class RuntimeMaterial {
        final MaterialPin pin;
        final Object panel;
        final Block sourceBlock;
        final Object transform;
        final Field iconsField;
        final IIcon[] sourceIcons;
        final IIcon[] pinnedIcons;
        final Block proxy;

        RuntimeMaterial(MaterialPin pin, Object panel, Block sourceBlock,
                        Object transform, Field iconsField, IIcon[] sourceIcons,
                        IIcon[] pinnedIcons) {
            this.pin = pin;
            this.panel = panel;
            this.sourceBlock = sourceBlock;
            this.transform = transform;
            this.iconsField = iconsField;
            this.sourceIcons = sourceIcons;
            this.pinnedIcons = pinnedIcons;
            this.proxy = new FaceIconProxy(pin, pinnedIcons);
        }

        void requireUnchanged(
                Method forStack, Field panelName, Field panelBlock,
                Field panelMetadata, ItemStack stack) throws Throwable {
            Object currentPanel = invoke(forStack, null, stack);
            if (currentPanel != panel
                    || !pin.materialName.equals(panelName.get(panel))
                    || panelBlock.get(panel) != sourceBlock
                    || panelMetadata.getInt(panel) != pin.metadata) {
                throw new IllegalStateException(
                        "ITEM_ICON_RENDER: ProjectBlue material cache/state drifted for "
                                + pin.materialName);
            }
            Object currentIcons = iconsField.get(transform);
            if (currentIcons != sourceIcons || sourceIcons.length != pinnedIcons.length) {
                throw new IllegalStateException(
                        "ITEM_ICON_RENDER: ForgeMultipart cached icon array identity drifted for "
                                + pin.materialName);
            }
            for (int side = 0; side < pinnedIcons.length; side++) {
                if (sourceIcons[side] != pinnedIcons[side]) {
                    throw new IllegalStateException(
                            "ITEM_ICON_RENDER: ForgeMultipart cached icon entry drifted for "
                                    + pin.materialName + " side=" + side);
                }
            }
        }
    }

    private static final class FaceIconProxy extends Block {
        private final MaterialPin pin;
        private final IIcon[] icons;

        FaceIconProxy(MaterialPin pin, IIcon[] icons) {
            super(Material.rock);
            this.pin = pin;
            this.icons = icons.clone();
        }

        @Override
        public IIcon getIcon(int side, int metadata) {
            // The three canonical tags contain only `mat`, so their owner render has no control
            // cover whose separate path requests metadata zero. Observing that access here is
            // target-shape drift and must abort instead of broadening this compatibility policy.
            if (side < 0 || side >= icons.length || metadata != pin.metadata) {
                throw new IllegalArgumentException(
                        "ITEM_ICON_RENDER: ProjectBlue owner requested an unmodeled proxy face; "
                                + "material=" + pin.materialName + ", side=" + side
                                + ", metadata=" + metadata);
            }
            return icons[side];
        }
    }

    private static final class ForgeRendererBinding implements RendererBinding {
        private final Map<Item, IItemRenderer> registry;

        ForgeRendererBinding(Map<Item, IItemRenderer> registry) {
            this.registry = registry;
        }

        @Override
        public IItemRenderer get(Item item) {
            return registry.get(item);
        }

        @Override
        public void set(Item item, IItemRenderer renderer) {
            MinecraftForgeClient.registerItemRenderer(item, renderer);
            if (registry.get(item) != renderer) {
                throw new IllegalStateException(
                        "ITEM_ICON_RENDER: Forge did not retain the exact ProjectBlue renderer "
                                + "binding");
            }
        }
    }

    private static MaterialPin requirePin(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            throw new IllegalArgumentException(
                    "ITEM_ICON_RENDER: null ProjectBlue target stack");
        }
        GameRegistry.UniqueIdentifier identifier =
                GameRegistry.findUniqueIdentifierFor(stack.getItem());
        String registryId = identifier == null
                ? "<unregistered>" : identifier.modId + ":" + identifier.name;
        MaterialPin pin = pinForExactIdentity(
                registryId, stack.getItem().getClass().getName(), stack.stackSize,
                stack.getItemDamage(), stack.getTagCompound());
        if (pin == null) {
            throw new IllegalArgumentException(
                    "ITEM_ICON_RENDER: ProjectBlue invocation is outside the three pinned "
                            + "canonical identities");
        }
        if (stack.getItem() != GameRegistry.findItem("ProjectBlue", "controlPanel")) {
            throw new IllegalArgumentException(
                    "ITEM_ICON_RENDER: ProjectBlue target item registry identity drifted");
        }
        return pin;
    }

    private static int indexOf(MaterialPin pin) {
        for (int index = 0; index < PINS.length; index++) {
            if (PINS[index] == pin) {
                return index;
            }
        }
        throw new IllegalArgumentException(
                "ITEM_ICON_RENDER: unrecognized ProjectBlue material pin");
    }

    private static void requirePinnedItem(Item item) {
        Item registered = GameRegistry.findItem("ProjectBlue", "controlPanel");
        if (item == null || item != registered || !ITEM_CLASS.equals(item.getClass().getName())) {
            throw new IllegalArgumentException(
                    "ITEM_ICON_RENDER: ProjectBlue controlPanel item topology drifted; got "
                            + describe(item));
        }
    }

    private static ItemStack stackFor(Item item, MaterialPin pin) {
        ItemStack stack = new ItemStack(item, 1, pin.metadata);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("mat", pin.materialName);
        stack.setTagCompound(tag);
        MaterialPin observed = pinForExactIdentity(
                ITEM_REGISTRY_ID, ITEM_CLASS, 1, pin.metadata, tag);
        if (observed != pin) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: ProjectBlue pinned NBT digest constant drifted for "
                            + pin.materialName);
        }
        return stack;
    }

    private static Block findBlock(String registryId) {
        int separator = registryId.indexOf(':');
        if (separator <= 0 || separator == registryId.length() - 1) {
            throw new IllegalArgumentException(
                    "ITEM_ICON_RENDER: invalid pinned block registry id " + registryId);
        }
        Block block = GameRegistry.findBlock(
                registryId.substring(0, separator), registryId.substring(separator + 1));
        if (block == null) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: pinned source block is absent: " + registryId);
        }
        return block;
    }

    private static Field requirePublicInstanceField(
            Class<?> owner, String name, Class<?> type, boolean mutable) throws Exception {
        Field field = owner.getField(name);
        int modifiers = field.getModifiers();
        if (field.getDeclaringClass() != owner || field.getType() != type
                || !Modifier.isPublic(modifiers) || Modifier.isStatic(modifiers)
                || (mutable && Modifier.isFinal(modifiers))) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: pinned public field topology drifted: "
                            + owner.getName() + "." + name);
        }
        return field;
    }

    private static void requirePublicStatic(
            Method method, Class<?> owner, String label) {
        int modifiers = method.getModifiers();
        if (method.getDeclaringClass() != owner || !Modifier.isPublic(modifiers)
                || !Modifier.isStatic(modifiers)) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: pinned static method topology drifted: " + label);
        }
    }

    private static Object invoke(Method method, Object receiver, Object... arguments)
            throws Throwable {
        try {
            return method.invoke(receiver, arguments);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            throw cause == null ? error : cause;
        }
    }

    private static String describeIcon(IIcon icon) {
        return icon == null ? "<null>"
                : icon.getClass().getName() + ":" + icon.getIconName();
    }

    private static String describe(Object value) {
        return value == null ? "<null>" : value.getClass().getName();
    }

    private static Throwable merge(Throwable primary, Throwable additional) {
        if (primary == null) {
            return additional;
        }
        if (additional != null && additional != primary) {
            primary.addSuppressed(additional);
        }
        return primary;
    }

    private static void rethrow(Throwable failure) throws Exception {
        if (failure == null) {
            return;
        }
        FatalErrors.rethrowIfFatal(failure);
        if (failure instanceof Exception) {
            throw (Exception) failure;
        }
        throw new IllegalStateException(
                "ITEM_ICON_RENDER: ProjectBlue adapter failed", failure);
    }
}
