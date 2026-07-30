package com.recipetree.neiexport1710;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic lease around Galacticraft 3.3.13-GTNH's public flag item renderer.
 *
 * <p>The owner renderer resolves its cloth data from the current player's Space Race. The
 * exporter player intentionally has no race, so the normal inventory path returns null, sends a
 * server request, and only draws geometry which is outside the 16x16 capture at the observed
 * frame. This adapter keeps the registered owner renderer, model, texture, transformations, and
 * Forge invocation path. For the exact flag item it temporarily supplies Galacticraft's canonical
 * default 48x32 cloth, pins the renderer's dummy entity and client total time to a deterministic
 * safe frame, and restores every touched value after the owner call.</p>
 */
final class GalacticraftFlagIconRenderer {
    static final String CONTRACT =
            "galacticraft-flag-owner-renderer-canonical-default-deterministic-frame-v1";
    static final String OWNER_RENDERER_CLASS =
            "micdoodle8.mods.galacticraft.core.client.render.item.ItemRendererFlag";
    static final String ENTITY_FLAG_CLASS =
            "micdoodle8.mods.galacticraft.core.entities.EntityFlag";
    static final String FLAG_DATA_CLASS =
            "micdoodle8.mods.galacticraft.core.wrappers.FlagData";
    static final String SPACE_RACE_MANAGER_CLASS =
            "micdoodle8.mods.galacticraft.core.dimension.SpaceRaceManager";
    static final String CLIENT_PROXY_CLASS =
            "micdoodle8.mods.galacticraft.core.proxy.ClientProxyCore";
    static final int CANONICAL_FLAG_WIDTH = 48;
    static final int CANONICAL_FLAG_HEIGHT = 32;
    static final byte CANONICAL_COLOR_BYTE = 127;
    static final long DETERMINISTIC_TOTAL_TIME = 1L;
    static final int DETERMINISTIC_ENTITY_ID = 0;

    interface RendererBinding {
        IItemRenderer get(Item item);

        void set(Item item, IItemRenderer renderer);
    }

    interface OwnerInvocation {
        void invoke(
                IItemRenderer owner,
                IItemRenderer.ItemRenderType type,
                ItemStack stack,
                Object[] data) throws Throwable;
    }

    interface ScopedMutation {
        void install() throws Throwable;

        void invoke() throws Throwable;

        void restore() throws Throwable;

        void verifyRestored() throws Throwable;
    }

    interface MatrixStateAccess {
        int matrixMode();

        int modelViewDepth();

        void matrixMode(int mode);

        void popMatrix();
    }

    interface LedgerAccess {
        Object spaceRaceSet() throws Throwable;

        void spaceRaceSet(Object value) throws Throwable;

        Object flagRequestList() throws Throwable;

        void flagRequestList(Object value) throws Throwable;
    }

    static int requireModelViewMatrixState(MatrixStateAccess state) {
        int mode = state.matrixMode();
        int depth = state.modelViewDepth();
        if (mode != GL11.GL_MODELVIEW) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: Galacticraft flag owner renderer requires GL_MODELVIEW; "
                            + "observed matrix mode=" + mode);
        }
        if (depth <= 0) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: Galacticraft flag owner renderer observed invalid "
                            + "modelview stack depth=" + depth);
        }
        return depth;
    }

    static void restoreModelViewMatrixState(
            MatrixStateAccess state, int expectedDepth) throws Throwable {
        if (expectedDepth <= 0) {
            throw new IllegalArgumentException(
                    "ITEM_ICON_RENDER: expected modelview depth must be positive");
        }
        Throwable failure = null;
        int observedMode = state.matrixMode();
        int observedDepth = state.modelViewDepth();
        if (observedMode != GL11.GL_MODELVIEW) {
            failure = merge(failure, new IllegalStateException(
                    "ITEM_ICON_RENDER: Galacticraft flag owner renderer changed matrix mode; "
                            + "expected GL_MODELVIEW, got " + observedMode));
            try {
                state.matrixMode(GL11.GL_MODELVIEW);
            } catch (Throwable repair) {
                failure = merge(failure, repair);
            }
        }
        if (observedDepth < expectedDepth) {
            failure = merge(failure, new IllegalStateException(
                    "ITEM_ICON_RENDER: Galacticraft flag owner renderer underflowed the "
                            + "modelview stack; expected depth=" + expectedDepth
                            + ", got " + observedDepth));
        } else if (observedDepth > expectedDepth) {
            failure = merge(failure, new IllegalStateException(
                    "ITEM_ICON_RENDER: Galacticraft flag owner renderer leaked "
                            + (observedDepth - expectedDepth)
                            + " modelview matrix frame(s); repaired before abort"));
            if (state.matrixMode() == GL11.GL_MODELVIEW) {
                int depth = observedDepth;
                while (depth > expectedDepth) {
                    state.popMatrix();
                    int repairedDepth = state.modelViewDepth();
                    if (repairedDepth != depth - 1) {
                        failure = merge(failure, new IllegalStateException(
                                "ITEM_ICON_RENDER: Galacticraft flag modelview repair did not "
                                        + "pop exactly one frame; before=" + depth
                                        + ", after=" + repairedDepth));
                        break;
                    }
                    depth = repairedDepth;
                }
            }
        }
        int restoredMode = state.matrixMode();
        int restoredDepth = state.modelViewDepth();
        if (restoredMode != GL11.GL_MODELVIEW || restoredDepth != expectedDepth) {
            failure = merge(failure, new IllegalStateException(
                    "ITEM_ICON_RENDER: Galacticraft flag modelview restoration was not exact; "
                            + "mode=" + restoredMode + ", depth=" + restoredDepth
                            + ", expectedDepth=" + expectedDepth));
        }
        if (failure != null) {
            throw failure;
        }
    }

    static LedgerSnapshot snapshotLedgers(LedgerAccess access) throws Throwable {
        Object rawRaces = access.spaceRaceSet();
        Object rawRequests = access.flagRequestList();
        if (!(rawRaces instanceof Set) || !(rawRequests instanceof List)) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: Galacticraft client ledgers are unavailable");
        }
        @SuppressWarnings("unchecked")
        Set<Object> races = (Set<Object>) rawRaces;
        @SuppressWarnings("unchecked")
        List<Object> requests = (List<Object>) rawRequests;
        return new LedgerSnapshot(
                races, new HashSet<Object>(races),
                requests, new ArrayList<Object>(requests));
    }

    static void restoreLedgers(
            LedgerAccess access, LedgerSnapshot snapshot) throws Throwable {
        Object currentRaces = access.spaceRaceSet();
        Object currentRequests = access.flagRequestList();
        boolean raceReferenceDrift = currentRaces != snapshot.spaceRaceSet;
        boolean raceContentDrift = !snapshot.spaceRaces.equals(currentRaces);
        boolean requestReferenceDrift = currentRequests != snapshot.requestList;
        boolean requestContentDrift = !snapshot.requests.equals(currentRequests);
        if (!raceReferenceDrift && !raceContentDrift
                && !requestReferenceDrift && !requestContentDrift) {
            return;
        }

        IllegalStateException mutation = new IllegalStateException(
                "ITEM_ICON_RENDER: Galacticraft flag owner mutated protected client ledgers; "
                        + "spaceRaceReference=" + raceReferenceDrift
                        + ", spaceRaceContents=" + raceContentDrift
                        + ", flagRequestReference=" + requestReferenceDrift
                        + ", flagRequestContents=" + requestContentDrift
                        + "; state repaired before abort");
        Throwable failure = mutation;
        if (!snapshot.spaceRaces.equals(snapshot.spaceRaceSet)) {
            try {
                snapshot.spaceRaceSet.clear();
                snapshot.spaceRaceSet.addAll(snapshot.spaceRaces);
            } catch (Throwable repair) {
                failure = merge(failure, repair);
            }
        }
        if (raceReferenceDrift) {
            try {
                access.spaceRaceSet(snapshot.spaceRaceSet);
            } catch (Throwable repair) {
                failure = merge(failure, repair);
            }
        }
        if (!snapshot.requests.equals(snapshot.requestList)) {
            try {
                snapshot.requestList.clear();
                snapshot.requestList.addAll(snapshot.requests);
            } catch (Throwable repair) {
                failure = merge(failure, repair);
            }
        }
        if (requestReferenceDrift) {
            try {
                access.flagRequestList(snapshot.requestList);
            } catch (Throwable repair) {
                failure = merge(failure, repair);
            }
        }
        try {
            verifyLedgers(access, snapshot);
        } catch (Throwable verification) {
            failure = merge(failure, verification);
        }
        throw failure;
    }

    static void verifyLedgers(
            LedgerAccess access, LedgerSnapshot snapshot) throws Throwable {
        Object currentRaces = access.spaceRaceSet();
        Object currentRequests = access.flagRequestList();
        if (currentRaces != snapshot.spaceRaceSet
                || !snapshot.spaceRaces.equals(currentRaces)
                || currentRequests != snapshot.requestList
                || !snapshot.requests.equals(currentRequests)) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: Galacticraft protected client ledger restoration was "
                            + "not exact");
        }
    }

    private final Item item;
    private final RendererBinding rendererBinding;
    private final IItemRenderer ownerRenderer;
    private final CountingItemRenderer adapterRenderer;
    private final Thread renderThread;
    private final boolean requireMinecraftClientThread;
    private boolean leaseActive;

    GalacticraftFlagIconRenderer(
            Item item,
            RendererBinding rendererBinding,
            IItemRenderer ownerRenderer,
            CountingItemRenderer adapterRenderer) {
        this(item, rendererBinding, ownerRenderer, adapterRenderer, false);
    }

    private GalacticraftFlagIconRenderer(
            Item item,
            RendererBinding rendererBinding,
            IItemRenderer ownerRenderer,
            CountingItemRenderer adapterRenderer,
            boolean requireMinecraftClientThread) {
        this.item = item;
        this.rendererBinding = rendererBinding;
        this.ownerRenderer = ownerRenderer;
        this.adapterRenderer = adapterRenderer;
        this.renderThread = Thread.currentThread();
        this.requireMinecraftClientThread = requireMinecraftClientThread;
    }

    static GalacticraftFlagIconRenderer create(ItemStack flagStack) throws Exception {
        if (!StackIdentity.isPinnedGalacticraftFlagIconTarget(flagStack)) {
            throw new IllegalArgumentException(
                    "ITEM_ICON_RENDER: Galacticraft flag adapter requires its exact pinned "
                            + "catalog stack");
        }
        final Item item = flagStack.getItem();
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
                            + "IdentityHashMap; got "
                            + (rawRegistry == null
                                    ? "<null>" : rawRegistry.getClass().getName()));
        }
        @SuppressWarnings("unchecked")
        Map<Item, IItemRenderer> registry =
                (Map<Item, IItemRenderer>) (Map<?, ?>) rawRegistry;
        ForgeRendererBinding binding = new ForgeRendererBinding(registry);
        IItemRenderer ownerRenderer = binding.get(item);
        String observedOwner = ownerRenderer == null
                ? "<null>" : ownerRenderer.getClass().getName();
        if (!OWNER_RENDERER_CLASS.equals(observedOwner)) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: pinned Galacticraft flag renderer mismatch; expected "
                            + OWNER_RENDERER_CLASS + ", got " + observedOwner);
        }
        if (!ownerRenderer.handleRenderType(
                flagStack, IItemRenderer.ItemRenderType.INVENTORY)
                || MinecraftForgeClient.getItemRenderer(
                        flagStack, IItemRenderer.ItemRenderType.INVENTORY) != ownerRenderer) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: pinned Galacticraft flag owner renderer does not own "
                            + "the Forge INVENTORY path");
        }

        RuntimeOwnerInvocation runtime = RuntimeOwnerInvocation.create(ownerRenderer);
        CountingItemRenderer adapter = new CountingItemRenderer(ownerRenderer, runtime);
        return new GalacticraftFlagIconRenderer(
                item, binding, ownerRenderer, adapter, true);
    }

    void drawExactlyOnce(OffscreenRenderer.DrawCall ownerInventoryDraw) throws Exception {
        long invocations = drawAndCount(ownerInventoryDraw);
        if (invocations != 1L) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: Galacticraft flag owner inventory renderer invoked the "
                            + "deterministic-frame adapter " + invocations
                            + " times instead of exactly once");
        }
    }

    synchronized long drawAndCount(OffscreenRenderer.DrawCall ownerDraw) throws Exception {
        if (ownerDraw == null) {
            throw new IllegalArgumentException(
                    "ITEM_ICON_RENDER: Galacticraft flag owner draw is required");
        }
        if (Thread.currentThread() != renderThread) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: Galacticraft flag renderer lease left its pinned "
                            + "client thread");
        }
        if (requireMinecraftClientThread
                && (Minecraft.getMinecraft() == null
                || !Minecraft.getMinecraft().func_152345_ab())) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: Galacticraft flag renderer lease is not on Minecraft's "
                            + "client thread");
        }
        if (leaseActive) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: nested Galacticraft flag renderer leases are forbidden");
        }
        if (rendererBinding.get(item) != ownerRenderer) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: Galacticraft flag owner renderer binding drifted before "
                            + "draw");
        }

        long attemptsBefore = adapterRenderer.attempts;
        long successesBefore = adapterRenderer.successes;
        long failuresBefore = adapterRenderer.failures;
        leaseActive = true;
        Throwable failure = null;
        try {
            rendererBinding.set(item, adapterRenderer);
            if (rendererBinding.get(item) != adapterRenderer) {
                throw new IllegalStateException(
                        "ITEM_ICON_RENDER: Galacticraft flag adapter registration was not exact");
            }
            ownerDraw.draw();
        } catch (Throwable error) {
            failure = error;
        } finally {
            try {
                rendererBinding.set(item, ownerRenderer);
                if (rendererBinding.get(item) != ownerRenderer) {
                    failure = merge(failure, new IllegalStateException(
                            "ITEM_ICON_RENDER: Galacticraft flag owner renderer restore was "
                                    + "not exact"));
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
                    "ITEM_ICON_RENDER: Galacticraft flag adapter telemetry drifted; attempts="
                            + attempts + ", successes=" + successes
                            + ", failures=" + failures));
        }
        if (failures != 0L) {
            FatalErrors.rethrowIfFatal(adapterRenderer.lastFailure);
            failure = merge(failure, new IllegalStateException(
                    "ITEM_ICON_RENDER: Galacticraft flag adapter failed " + failures
                            + " time(s) inside an owner render path that may swallow exceptions",
                    adapterRenderer.lastFailure));
        }
        rethrow(failure);
        return successes;
    }

    static boolean pinnedOwnerRefreshPredicate(long totalWorldTime) {
        return Math.floorMod((int) totalWorldTime, 100) == 0;
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
        private final OwnerInvocation invocation;
        long attempts;
        long successes;
        long failures;
        Throwable lastFailure;

        CountingItemRenderer(IItemRenderer owner, OwnerInvocation invocation) {
            if (owner == null || invocation == null) {
                throw new IllegalArgumentException(
                        "ITEM_ICON_RENDER: Galacticraft flag owner and invocation are required");
            }
            this.owner = owner;
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
        public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
            attempts++;
            Throwable failure = null;
            try {
                invocation.invoke(owner, type, item, data);
            } catch (Throwable error) {
                failure = error;
            }
            if (failure != null) {
                failures++;
                lastFailure = failure;
                FatalErrors.rethrowIfFatal(failure);
                throw new IllegalStateException(
                        "ITEM_ICON_RENDER: Galacticraft flag owner renderer failed", failure);
            }
            successes++;
        }
    }

    private static final class ForgeRendererBinding implements RendererBinding {
        private final Map<Item, IItemRenderer> registry;

        private ForgeRendererBinding(Map<Item, IItemRenderer> registry) {
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
                        "ITEM_ICON_RENDER: Forge custom item renderer registration did not "
                                + "retain the exact requested binding");
            }
        }
    }

    private static final class RuntimeOwnerInvocation implements OwnerInvocation {
        private final Minecraft minecraft;
        private final WorldClient world;
        private final Entity player;
        private final String playerName;
        private final Entity dummy;
        private final Method getOwner;
        private final Method setOwner;
        private final Method getType;
        private final Method setType;
        private final Field flagDataField;
        private final Object canonicalFlagData;
        private final Method flagDataGetWidth;
        private final Method flagDataGetHeight;
        private final Field flagDataColor;
        private final Method getSpaceRaceFromPlayer;
        private final Field spaceRacesField;
        private final Field flagRequestsSentField;
        private boolean invocationActive;
        private long glAttributePushes;
        private long glAttributePops;

        private RuntimeOwnerInvocation(
                Minecraft minecraft,
                WorldClient world,
                Entity player,
                String playerName,
                Entity dummy,
                Method getOwner,
                Method setOwner,
                Method getType,
                Method setType,
                Field flagDataField,
                Object canonicalFlagData,
                Method flagDataGetWidth,
                Method flagDataGetHeight,
                Field flagDataColor,
                Method getSpaceRaceFromPlayer,
                Field spaceRacesField,
                Field flagRequestsSentField) {
            this.minecraft = minecraft;
            this.world = world;
            this.player = player;
            this.playerName = playerName;
            this.dummy = dummy;
            this.getOwner = getOwner;
            this.setOwner = setOwner;
            this.getType = getType;
            this.setType = setType;
            this.flagDataField = flagDataField;
            this.canonicalFlagData = canonicalFlagData;
            this.flagDataGetWidth = flagDataGetWidth;
            this.flagDataGetHeight = flagDataGetHeight;
            this.flagDataColor = flagDataColor;
            this.getSpaceRaceFromPlayer = getSpaceRaceFromPlayer;
            this.spaceRacesField = spaceRacesField;
            this.flagRequestsSentField = flagRequestsSentField;
        }

        static RuntimeOwnerInvocation create(IItemRenderer ownerRenderer) throws Exception {
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft == null || minecraft.theWorld == null || minecraft.thePlayer == null) {
                throw new IllegalStateException(
                        "ITEM_ICON_RENDER: Galacticraft flag adapter requires an active client "
                                + "world and player");
            }
            WorldClient world = minecraft.theWorld;
            Entity player = minecraft.thePlayer;
            String playerName = minecraft.thePlayer.getGameProfile().getName();
            if (playerName == null || playerName.trim().isEmpty()) {
                throw new IllegalStateException(
                        "ITEM_ICON_RENDER: Galacticraft flag adapter player name is blank");
            }

            Class<?> entityFlagClass = Class.forName(ENTITY_FLAG_CLASS);
            if (!Entity.class.isAssignableFrom(entityFlagClass)) {
                throw new IllegalStateException(
                        "ITEM_ICON_RENDER: pinned Galacticraft flag dummy is not an Entity");
            }
            Field dummyField = ownerRenderer.getClass().getDeclaredField("entityFlagDummy");
            if (!Modifier.isFinal(dummyField.getModifiers())
                    || dummyField.getType() != entityFlagClass) {
                throw new IllegalStateException(
                        "ITEM_ICON_RENDER: pinned Galacticraft entityFlagDummy field drifted");
            }
            dummyField.setAccessible(true);
            Object rawDummy = dummyField.get(ownerRenderer);
            if (rawDummy == null || rawDummy.getClass() != entityFlagClass) {
                throw new IllegalStateException(
                        "ITEM_ICON_RENDER: pinned Galacticraft entityFlagDummy instance drifted");
            }
            Entity dummy = (Entity) rawDummy;

            Method getOwner = exactMethod(entityFlagClass, "getOwner");
            Method setOwner = exactMethod(entityFlagClass, "setOwner", String.class);
            Method getType = exactMethod(entityFlagClass, "getType");
            Method setType = exactMethod(entityFlagClass, "setType", Integer.TYPE);

            Class<?> flagDataClass = Class.forName(FLAG_DATA_CLASS);
            Field flagDataField = entityFlagClass.getField("flagData");
            if (flagDataField.getType() != flagDataClass
                    || Modifier.isStatic(flagDataField.getModifiers())) {
                throw new IllegalStateException(
                        "ITEM_ICON_RENDER: pinned Galacticraft EntityFlag.flagData field drifted");
            }
            Constructor<?> flagDataConstructor =
                    flagDataClass.getConstructor(Integer.TYPE, Integer.TYPE);
            Object canonicalFlagData = flagDataConstructor.newInstance(
                    CANONICAL_FLAG_WIDTH, CANONICAL_FLAG_HEIGHT);
            Method getWidth = exactMethod(flagDataClass, "getWidth");
            Method getHeight = exactMethod(flagDataClass, "getHeight");
            Field color = flagDataClass.getDeclaredField("color");
            if (color.getType() != byte[][][].class
                    || !Modifier.isFinal(color.getModifiers())) {
                throw new IllegalStateException(
                        "ITEM_ICON_RENDER: pinned Galacticraft FlagData.color field drifted");
            }
            color.setAccessible(true);

            Class<?> spaceRaceManager = Class.forName(SPACE_RACE_MANAGER_CLASS);
            Method getSpaceRaceFromPlayer = exactMethod(
                    spaceRaceManager, "getSpaceRaceFromPlayer", String.class);
            if (!Modifier.isStatic(getSpaceRaceFromPlayer.getModifiers())) {
                throw new IllegalStateException(
                        "ITEM_ICON_RENDER: Galacticraft Space Race lookup is no longer static");
            }
            Field spaceRaces = spaceRaceManager.getDeclaredField("spaceRaces");
            if (!Modifier.isStatic(spaceRaces.getModifiers())
                    || !Modifier.isFinal(spaceRaces.getModifiers())
                    || !Set.class.isAssignableFrom(spaceRaces.getType())) {
                throw new IllegalStateException(
                        "ITEM_ICON_RENDER: Galacticraft Space Race set is not a static final Set");
            }
            spaceRaces.setAccessible(true);

            Class<?> clientProxy = Class.forName(CLIENT_PROXY_CLASS);
            Field flagRequestsSent = clientProxy.getField("flagRequestsSent");
            if (!Modifier.isStatic(flagRequestsSent.getModifiers())
                    || Modifier.isFinal(flagRequestsSent.getModifiers())
                    || !List.class.isAssignableFrom(flagRequestsSent.getType())) {
                throw new IllegalStateException(
                        "ITEM_ICON_RENDER: Galacticraft flag request ledger is not a static "
                                + "non-final List");
            }

            RuntimeOwnerInvocation result = new RuntimeOwnerInvocation(
                    minecraft, world, player, playerName, dummy,
                    getOwner, setOwner, getType, setType,
                    flagDataField, canonicalFlagData, getWidth, getHeight, color,
                    getSpaceRaceFromPlayer, spaceRaces, flagRequestsSent);
            try {
                result.verifyCanonicalFlagData();
                result.requirePinnedClientState();
            } catch (Throwable error) {
                FatalErrors.rethrowIfFatal(error);
                if (error instanceof Exception) {
                    throw (Exception) error;
                }
                throw new IllegalStateException(
                        "ITEM_ICON_RENDER: Galacticraft flag runtime preflight failed", error);
            }
            return result;
        }

        @Override
        public synchronized void invoke(
                final IItemRenderer owner,
                final IItemRenderer.ItemRenderType type,
                final ItemStack stack,
                final Object[] data) throws Throwable {
            if (invocationActive) {
                throw new IllegalStateException(
                        "ITEM_ICON_RENDER: nested Galacticraft flag owner invocations are "
                                + "forbidden");
            }
            if (type != IItemRenderer.ItemRenderType.INVENTORY) {
                throw new IllegalArgumentException(
                        "ITEM_ICON_RENDER: Galacticraft flag export adapter received render "
                                + "type " + type + " instead of INVENTORY");
            }
            if (!StackIdentity.isPinnedGalacticraftFlagRenderTarget(stack)) {
                throw new IllegalArgumentException(
                        "ITEM_ICON_RENDER: Galacticraft flag renderer wrapper was invoked for "
                                + "an item outside its pinned target");
            }
            requirePinnedClientState();
            final Snapshot snapshot = snapshot();
            final MatrixStateAccess matrixState = new GlMatrixStateAccess();
            final int modelViewDepth = requireModelViewMatrixState(matrixState);
            invocationActive = true;
            try {
                runRestoring(new ScopedMutation() {
                    @Override
                    public void install() throws Throwable {
                        world.func_82738_a(DETERMINISTIC_TOTAL_TIME);
                        dummy.worldObj = world;
                        dummy.ticksExisted = (int) DETERMINISTIC_TOTAL_TIME;
                        dummy.setEntityId(DETERMINISTIC_ENTITY_ID);
                        invokeReflective(setType, dummy, stack.getItemDamage());
                        invokeReflective(setOwner, dummy, playerName);
                        flagDataField.set(dummy, canonicalFlagData);
                        requireInstalled(stack.getItemDamage());
                    }

                    @Override
                    public void invoke() throws Throwable {
                        Throwable failure = null;
                        boolean attributesPushed = false;
                        long pushesBefore = glAttributePushes;
                        long popsBefore = glAttributePops;
                        try {
                            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
                            attributesPushed = true;
                            glAttributePushes++;
                            owner.renderItem(type, stack, data);
                        } catch (Throwable error) {
                            failure = error;
                        } finally {
                            if (attributesPushed) {
                                try {
                                    GL11.glPopAttrib();
                                    glAttributePops++;
                                } catch (Throwable restore) {
                                    failure = merge(failure, restore);
                                }
                            }
                        }
                        if (glAttributePushes - pushesBefore != 1L
                                || glAttributePops - popsBefore != 1L) {
                            failure = merge(failure, new IllegalStateException(
                                    "ITEM_ICON_RENDER: Galacticraft flag GL attribute lease "
                                            + "did not push and pop exactly once; pushes="
                                            + (glAttributePushes - pushesBefore) + ", pops="
                                            + (glAttributePops - popsBefore)));
                        }
                        try {
                            restoreModelViewMatrixState(matrixState, modelViewDepth);
                        } catch (Throwable matrixRestore) {
                            failure = merge(failure, matrixRestore);
                        }
                        if (failure != null) {
                            throw failure;
                        }
                    }

                    @Override
                    public void restore() throws Throwable {
                        restoreSnapshot(snapshot);
                    }

                    @Override
                    public void verifyRestored() throws Throwable {
                        verifySnapshot(snapshot);
                    }
                });
            } finally {
                invocationActive = false;
            }
        }

        private Snapshot snapshot() throws Throwable {
            requirePinnedClientState();
            LedgerSnapshot ledgers = snapshotLedgers(ledgerAccess());
            Object playerRace = invokeReflective(
                    getSpaceRaceFromPlayer, null, playerName);
            if (playerRace != null) {
                throw new IllegalStateException(
                        "ITEM_ICON_RENDER: pinned exporter player unexpectedly belongs to a "
                                + "Galacticraft Space Race");
            }
            return new Snapshot(
                    world.getTotalWorldTime(), dummy.worldObj, dummy.ticksExisted,
                    dummy.getEntityId(), ((Number) invokeReflective(getType, dummy)).intValue(),
                    (String) invokeReflective(getOwner, dummy), flagDataField.get(dummy),
                    ledgers);
        }

        private void requireInstalled(int metadata) throws Throwable {
            if (world.getTotalWorldTime() != DETERMINISTIC_TOTAL_TIME
                    || pinnedOwnerRefreshPredicate(world.getTotalWorldTime())
                    || dummy.worldObj != world
                    || dummy.ticksExisted != (int) DETERMINISTIC_TOTAL_TIME
                    || dummy.getEntityId() != DETERMINISTIC_ENTITY_ID
                    || ((Number) invokeReflective(getType, dummy)).intValue() != metadata
                    || !playerName.equals(invokeReflective(getOwner, dummy))
                    || flagDataField.get(dummy) != canonicalFlagData) {
                throw new IllegalStateException(
                        "ITEM_ICON_RENDER: Galacticraft flag deterministic owner state was not "
                                + "installed exactly");
            }
            verifyCanonicalFlagData();
        }

        private void restoreSnapshot(Snapshot snapshot) throws Throwable {
            Throwable failure = null;
            try {
                world.func_82738_a(snapshot.totalWorldTime);
            } catch (Throwable error) {
                failure = merge(failure, error);
            }
            try {
                dummy.worldObj = snapshot.dummyWorld;
            } catch (Throwable error) {
                failure = merge(failure, error);
            }
            try {
                dummy.ticksExisted = snapshot.ticksExisted;
            } catch (Throwable error) {
                failure = merge(failure, error);
            }
            try {
                dummy.setEntityId(snapshot.entityId);
            } catch (Throwable error) {
                failure = merge(failure, error);
            }
            try {
                invokeReflective(setType, dummy, snapshot.type);
            } catch (Throwable error) {
                failure = merge(failure, error);
            }
            try {
                invokeReflective(setOwner, dummy, snapshot.owner);
            } catch (Throwable error) {
                failure = merge(failure, error);
            }
            try {
                flagDataField.set(dummy, snapshot.flagData);
            } catch (Throwable error) {
                failure = merge(failure, error);
            }
            try {
                restoreLedgers(ledgerAccess(), snapshot.ledgers);
            } catch (Throwable error) {
                failure = merge(failure, error);
            }
            if (failure != null) {
                throw failure;
            }
        }

        private void verifySnapshot(Snapshot snapshot) throws Throwable {
            if (world.getTotalWorldTime() != snapshot.totalWorldTime
                    || dummy.worldObj != snapshot.dummyWorld
                    || dummy.ticksExisted != snapshot.ticksExisted
                    || dummy.getEntityId() != snapshot.entityId
                    || ((Number) invokeReflective(getType, dummy)).intValue() != snapshot.type
                    || !same(snapshot.owner, invokeReflective(getOwner, dummy))
                    || flagDataField.get(dummy) != snapshot.flagData) {
                throw new IllegalStateException(
                        "ITEM_ICON_RENDER: Galacticraft flag dummy/world restoration was not "
                                + "exact");
            }
            verifyLedgers(ledgerAccess(), snapshot.ledgers);
            if (invokeReflective(getSpaceRaceFromPlayer, null, playerName) != null) {
                throw new IllegalStateException(
                        "ITEM_ICON_RENDER: exporter player Space Race lookup changed during flag "
                                + "rendering");
            }
            verifyCanonicalFlagData();
            requirePinnedClientState();
        }

        private void requirePinnedClientState() throws Throwable {
            if (Minecraft.getMinecraft() != minecraft
                    || minecraft.theWorld != world
                    || minecraft.thePlayer != player
                    || !playerName.equals(minecraft.thePlayer.getGameProfile().getName())) {
                throw new IllegalStateException(
                        "ITEM_ICON_RENDER: Galacticraft flag adapter client world/player binding "
                                + "drifted");
            }
            if (invokeReflective(getSpaceRaceFromPlayer, null, playerName) != null) {
                throw new IllegalStateException(
                        "ITEM_ICON_RENDER: pinned exporter player unexpectedly belongs to a "
                                + "Galacticraft Space Race");
            }
            Object races = spaceRacesField.get(null);
            Object requests = flagRequestsSentField.get(null);
            if (!(races instanceof Set) || !(requests instanceof List)) {
                throw new IllegalStateException(
                        "ITEM_ICON_RENDER: Galacticraft Space Race/request state is unavailable");
            }
        }

        private LedgerAccess ledgerAccess() {
            return new ReflectiveLedgerAccess(spaceRacesField, flagRequestsSentField);
        }

        private void verifyCanonicalFlagData() throws Throwable {
            int width = ((Number) invokeReflective(
                    flagDataGetWidth, canonicalFlagData)).intValue();
            int height = ((Number) invokeReflective(
                    flagDataGetHeight, canonicalFlagData)).intValue();
            Object rawColor = flagDataColor.get(canonicalFlagData);
            if (width != CANONICAL_FLAG_WIDTH
                    || height != CANONICAL_FLAG_HEIGHT
                    || !(rawColor instanceof byte[][][])) {
                throw new IllegalStateException(
                        "ITEM_ICON_RENDER: canonical Galacticraft default FlagData shape drifted");
            }
            byte[][][] color = (byte[][][]) rawColor;
            if (color.length != CANONICAL_FLAG_WIDTH) {
                throw new IllegalStateException(
                        "ITEM_ICON_RENDER: canonical Galacticraft flag color width drifted");
            }
            for (int x = 0; x < CANONICAL_FLAG_WIDTH; x++) {
                if (color[x] == null || color[x].length != CANONICAL_FLAG_HEIGHT) {
                    throw new IllegalStateException(
                            "ITEM_ICON_RENDER: canonical Galacticraft flag color height drifted "
                                    + "at x=" + x);
                }
                for (int y = 0; y < CANONICAL_FLAG_HEIGHT; y++) {
                    if (color[x][y] == null || color[x][y].length != 3) {
                        throw new IllegalStateException(
                                "ITEM_ICON_RENDER: canonical Galacticraft flag RGB shape drifted "
                                        + "at " + x + "," + y);
                    }
                    for (int channel = 0; channel < 3; channel++) {
                        if (color[x][y][channel] != CANONICAL_COLOR_BYTE) {
                            throw new IllegalStateException(
                                    "ITEM_ICON_RENDER: canonical Galacticraft default flag pixel "
                                            + "drifted at " + x + "," + y + "," + channel);
                        }
                    }
                }
            }
        }
    }

    private static final class GlMatrixStateAccess implements MatrixStateAccess {
        @Override
        public int matrixMode() {
            return GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        }

        @Override
        public int modelViewDepth() {
            return GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH);
        }

        @Override
        public void matrixMode(int mode) {
            GL11.glMatrixMode(mode);
        }

        @Override
        public void popMatrix() {
            GL11.glPopMatrix();
        }
    }

    private static final class ReflectiveLedgerAccess implements LedgerAccess {
        private final Field spaceRaces;
        private final Field flagRequests;

        private ReflectiveLedgerAccess(Field spaceRaces, Field flagRequests) {
            this.spaceRaces = spaceRaces;
            this.flagRequests = flagRequests;
        }

        @Override
        public Object spaceRaceSet() throws IllegalAccessException {
            return spaceRaces.get(null);
        }

        @Override
        public void spaceRaceSet(Object value) throws IllegalAccessException {
            spaceRaces.set(null, value);
        }

        @Override
        public Object flagRequestList() throws IllegalAccessException {
            return flagRequests.get(null);
        }

        @Override
        public void flagRequestList(Object value) throws IllegalAccessException {
            flagRequests.set(null, value);
        }
    }

    static final class LedgerSnapshot {
        final Set<Object> spaceRaceSet;
        final Set<Object> spaceRaces;
        final List<Object> requestList;
        final List<Object> requests;

        private LedgerSnapshot(
                Set<Object> spaceRaceSet,
                Set<Object> spaceRaces,
                List<Object> requestList,
                List<Object> requests) {
            this.spaceRaceSet = spaceRaceSet;
            this.spaceRaces = spaceRaces;
            this.requestList = requestList;
            this.requests = requests;
        }
    }

    private static final class Snapshot {
        final long totalWorldTime;
        final World dummyWorld;
        final int ticksExisted;
        final int entityId;
        final int type;
        final String owner;
        final Object flagData;
        final LedgerSnapshot ledgers;

        private Snapshot(
                long totalWorldTime,
                World dummyWorld,
                int ticksExisted,
                int entityId,
                int type,
                String owner,
                Object flagData,
                LedgerSnapshot ledgers) {
            this.totalWorldTime = totalWorldTime;
            this.dummyWorld = dummyWorld;
            this.ticksExisted = ticksExisted;
            this.entityId = entityId;
            this.type = type;
            this.owner = owner;
            this.flagData = flagData;
            this.ledgers = ledgers;
        }
    }

    private static Method exactMethod(
            Class<?> owner, String name, Class<?>... parameterTypes) throws Exception {
        Method method = owner.getMethod(name, parameterTypes);
        if (method.isBridge() || method.isSynthetic()) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: pinned reflective method is bridge/synthetic: "
                            + owner.getName() + "." + name);
        }
        return method;
    }

    private static Object invokeReflective(
            Method method, Object target, Object... arguments) throws Throwable {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            throw cause == null ? error : cause;
        }
    }

    private static boolean same(Object first, Object second) {
        return first == null ? second == null : first.equals(second);
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
                "ITEM_ICON_RENDER: Galacticraft flag adapter failed", failure);
    }
}
