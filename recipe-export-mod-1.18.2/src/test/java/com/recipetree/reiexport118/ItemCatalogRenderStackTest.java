package com.recipetree.reiexport118;

import me.shedaniel.rei.api.common.entry.EntryStack;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ItemCatalogRenderStackTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void copiesAndAmountNormalizesOnlyTheCatalogItemStack() {
        ItemStack sourceItem = new ItemStack(Items.STONE, 7);
        ItemStack copiedItem = sourceItem.copy();
        EntryStack<?> copiedStack = stack(copiedItem, () -> {
            throw new AssertionError("the copied render stack must not be copied again");
        });
        AtomicInteger copyCalls = new AtomicInteger();
        EntryStack<?> sourceStack = stack(sourceItem, () -> {
            copyCalls.incrementAndGet();
            return copiedStack;
        });

        EntryStack<?> renderStack = ItemCatalog.copyForCatalogIconRender(sourceStack);

        assertSame(copiedStack, renderStack, "the settings-preserving EntryStack.copy() result must be rendered");
        assertEquals(1, copyCalls.get());
        assertEquals(1, copiedItem.getCount(), "catalog icon render amount must be neutral");
        assertEquals(7, sourceItem.getCount(), "recipe/source amount must remain untouched");
    }

    @Test
    void leavesNonItemRenderValuesUnchanged() {
        Object sourceValue = new Object();
        Object copiedValue = new Object();
        EntryStack<?> copiedStack = stack(copiedValue, () -> {
            throw new AssertionError("the copied render stack must not be copied again");
        });
        EntryStack<?> sourceStack = stack(sourceValue, () -> copiedStack);

        EntryStack<?> renderStack = ItemCatalog.copyForCatalogIconRender(sourceStack);

        assertSame(copiedStack, renderStack);
        assertSame(copiedValue, renderStack.getValue(),
                "fluid, gas, and other non-item entry values must retain their native copy semantics");
    }

    @Test
    void rejectsAnAliasedMutableItemCopyInsteadOfMutatingRecipeState() {
        ItemStack sourceItem = new ItemStack(Items.STONE, 4);
        @SuppressWarnings("unchecked")
        EntryStack<?>[] holder = new EntryStack<?>[1];
        holder[0] = stack(sourceItem, () -> holder[0]);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> ItemCatalog.copyForCatalogIconRender(holder[0])
        );

        assertEquals(
                "REI EntryStack.copy() aliased the mutable ItemStack used for catalog rendering.",
                failure.getMessage()
        );
        assertEquals(4, sourceItem.getCount());
    }

    private static EntryStack<?> stack(Object value, Supplier<EntryStack<?>> copy) {
        return (EntryStack<?>) Proxy.newProxyInstance(
                ItemCatalogRenderStackTest.class.getClassLoader(),
                new Class<?>[]{EntryStack.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "copy" -> copy.get();
                    case "getValue" -> value;
                    case "isEmpty" -> false;
                    case "normalize" -> throw new AssertionError(
                            "catalog rendering must preserve EntryStack settings instead of calling normalize()");
                    case "toString" -> "TestEntryStack[" + value + "]";
                    default -> throw new AssertionError("Unexpected EntryStack method: " + method);
                }
        );
    }
}
