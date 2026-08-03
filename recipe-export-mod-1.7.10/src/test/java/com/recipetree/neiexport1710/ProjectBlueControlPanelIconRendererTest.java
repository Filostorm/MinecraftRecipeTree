package com.recipetree.neiexport1710;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.client.IItemRenderer;
import org.junit.Test;

import java.util.IdentityHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ProjectBlueControlPanelIconRendererTest {
    @Test
    public void exactPinsMatchAllThreeAuditedCanonicalIdentities() {
        assertEquals(3, ProjectBlueControlPanelIconRenderer.PINS.length);
        assertPin(0, 5, "Automagy:blockNetherRune_5",
                "5e9d813c721cd0529491e5f7304b191e21a9a88fcd71c7005b4c0172bc45e6bb");
        assertPin(1, 15, "gregtech:gt.blockcasings6_15",
                "87d2a35ccc7ada13accee33eeeb54132dd49e4b0908fc4d100ab6cb850752313");
        assertPin(2, 15, "gregtech:gt.blockcasingsNH_15",
                "b45727dc3e966a026a487cbf0836be93d6ab82d6b0c2faef16840168d0c6c4b5");
        assertEquals(0x3f, ProjectBlueControlPanelIconRenderer.PINS[0].missingSideMask);
        assertEquals(0x03, ProjectBlueControlPanelIconRenderer.PINS[1].missingSideMask);
        assertEquals("gregtech:iconsets/MACHINE_CASING_TANK_0",
                ProjectBlueControlPanelIconRenderer.PINS[1].nonMissingIconName);
        assertEquals(0x3f, ProjectBlueControlPanelIconRenderer.PINS[2].missingSideMask);
        assertEquals(3, ProjectBlueControlPanelIconRenderer.EXPECTED_TARGETS);
    }

    @Test
    public void matcherRejectsNearMissesAndExtraNbt() {
        NBTTagCompound tag = materialTag("Automagy:blockNetherRune_5");
        assertNull(ProjectBlueControlPanelIconRenderer.pinForExactIdentity(
                "Other:controlPanel", ProjectBlueControlPanelIconRenderer.ITEM_CLASS,
                1, 5, tag));
        assertNull(ProjectBlueControlPanelIconRenderer.pinForExactIdentity(
                ProjectBlueControlPanelIconRenderer.ITEM_REGISTRY_ID,
                ProjectBlueControlPanelIconRenderer.ITEM_CLASS, 1, 6, tag));
        tag.setInteger("extra", 1);
        assertNull(ProjectBlueControlPanelIconRenderer.pinForExactIdentity(
                ProjectBlueControlPanelIconRenderer.ITEM_REGISTRY_ID,
                ProjectBlueControlPanelIconRenderer.ITEM_CLASS, 1, 5, tag));
        assertFalse(ProjectBlueControlPanelIconRenderer.isPinnedCanonicalKey(
                "item|ProjectBlue:controlPanel|meta=5|nbt=wrong"));
    }

    @Test
    public void rendererLeaseCountsOnlyTargetsDelegatesOthersAndRestoresBinding()
            throws Exception {
        final Item item = new Item();
        final ItemStack target = new ItemStack(item, 1, 5);
        final ItemStack other = new ItemStack(item, 1, 0);
        final RecordingRenderer owner = new RecordingRenderer();
        final MapBinding binding = new MapBinding();
        binding.set(item, owner);
        ProjectBlueControlPanelIconRenderer.CountingItemRenderer adapter =
                new ProjectBlueControlPanelIconRenderer.CountingItemRenderer(
                        owner, targetResolver(target), directInvocation());
        ProjectBlueControlPanelIconRenderer lease =
                new ProjectBlueControlPanelIconRenderer(item, binding, owner, adapter);

        long successes = lease.drawAndCount(new OffscreenRenderer.DrawCall() {
            @Override
            public void draw() {
                binding.get(item).renderItem(
                        IItemRenderer.ItemRenderType.INVENTORY, target);
                binding.get(item).renderItem(
                        IItemRenderer.ItemRenderType.INVENTORY, other);
            }
        });

        assertEquals(1L, successes);
        assertEquals(2, owner.calls);
        assertSame(owner, binding.get(item));
    }

    @Test
    public void swallowedTargetFailureEscapesAfterRendererBindingRestore() throws Exception {
        final Item item = new Item();
        final ItemStack target = new ItemStack(item, 1, 5);
        final RecordingRenderer owner = new RecordingRenderer();
        owner.failure = new IllegalStateException("synthetic owner failure");
        final MapBinding binding = new MapBinding();
        binding.set(item, owner);
        ProjectBlueControlPanelIconRenderer.CountingItemRenderer adapter =
                new ProjectBlueControlPanelIconRenderer.CountingItemRenderer(
                        owner, targetResolver(target), directInvocation());
        ProjectBlueControlPanelIconRenderer lease =
                new ProjectBlueControlPanelIconRenderer(item, binding, owner, adapter);

        try {
            lease.drawAndCount(new OffscreenRenderer.DrawCall() {
                @Override
                public void draw() {
                    try {
                        binding.get(item).renderItem(
                                IItemRenderer.ItemRenderType.INVENTORY, target);
                    } catch (RuntimeException swallowedByNei) {
                        // Mirrors NEI's safe item-render context.
                    }
                }
            });
            fail("Expected swallowed owner failure to escape the renderer lease");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("may swallow exceptions"));
        }
        assertSame(owner, binding.get(item));
    }

    @Test
    public void materialMutationProtocolRestoresAndVerifiesAfterFailure() {
        final StringBuilder order = new StringBuilder();
        try {
            ProjectBlueControlPanelIconRenderer.runRestoring(
                    new ProjectBlueControlPanelIconRenderer.ScopedMutation() {
                @Override
                public void install() {
                    order.append('i');
                }

                @Override
                public void invoke() {
                    order.append('d');
                    throw new IllegalStateException("owner failed");
                }

                @Override
                public void restore() {
                    order.append('r');
                }

                @Override
                public void verifyRestored() {
                    order.append('v');
                }
            });
            fail("Expected owner failure");
        } catch (Throwable expected) {
            assertTrue(expected.getMessage().contains("owner failed"));
        }
        assertEquals("idrv", order.toString());
    }

    private static void assertPin(
            int index, int metadata, String material, String digest) {
        NBTTagCompound tag = materialTag(material);
        ProjectBlueControlPanelIconRenderer.MaterialPin pin =
                ProjectBlueControlPanelIconRenderer.pinForExactIdentity(
                        ProjectBlueControlPanelIconRenderer.ITEM_REGISTRY_ID,
                        ProjectBlueControlPanelIconRenderer.ITEM_CLASS,
                        1, metadata, tag);
        assertSame(ProjectBlueControlPanelIconRenderer.PINS[index], pin);
        assertEquals(digest, pin.nbtSha256);
        assertEquals("item|ProjectBlue:controlPanel|meta=" + metadata
                + "|nbt=" + digest, pin.canonicalKey);
        assertTrue(ProjectBlueControlPanelIconRenderer.isPinnedCanonicalKey(
                pin.canonicalKey));
    }

    private static NBTTagCompound materialTag(String material) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("mat", material);
        return tag;
    }

    private static ProjectBlueControlPanelIconRenderer.TargetResolver targetResolver(
            final ItemStack target) {
        return new ProjectBlueControlPanelIconRenderer.TargetResolver() {
            @Override
            public boolean isTarget(ItemStack stack) {
                return stack == target;
            }
        };
    }

    private static ProjectBlueControlPanelIconRenderer.OwnerInvocation directInvocation() {
        return new ProjectBlueControlPanelIconRenderer.OwnerInvocation() {
            @Override
            public void invoke(
                    IItemRenderer owner, IItemRenderer.ItemRenderType type,
                    ItemStack stack, Object[] data) {
                owner.renderItem(type, stack, data);
            }
        };
    }

    private static final class MapBinding
            implements ProjectBlueControlPanelIconRenderer.RendererBinding {
        private final Map<Item, IItemRenderer> renderers =
                new IdentityHashMap<Item, IItemRenderer>();

        @Override
        public IItemRenderer get(Item item) {
            return renderers.get(item);
        }

        @Override
        public void set(Item item, IItemRenderer renderer) {
            renderers.put(item, renderer);
        }
    }

    private static final class RecordingRenderer implements IItemRenderer {
        int calls;
        RuntimeException failure;

        @Override
        public boolean handleRenderType(ItemStack item, ItemRenderType type) {
            return true;
        }

        @Override
        public boolean shouldUseRenderHelper(
                ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
            return false;
        }

        @Override
        public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
            calls++;
            if (failure != null) {
                throw failure;
            }
        }
    }
}
