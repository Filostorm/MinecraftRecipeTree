package com.recipetree.reiexport118.mixin;

import com.recipetree.reiexport118.compat.Mm2DeterminismContract;
import com.recipetree.reiexport118.compat.RelicsStatRandomDeterminism;
import it.hurts.sskirillss.relics.items.relics.base.IRelicItem;
import it.hurts.sskirillss.relics.items.relics.base.data.leveling.StatData;
import it.hurts.sskirillss.relics.utils.MathUtils;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/**
 * Materializes the inherited Relics stat randomizer on its one concrete item base class.
 *
 * <p>Mixin 0.8.5 does not support injectors on interface mixins. Relics' audited
 * {@code randomizeStat} implementation is a default interface method and every concrete Relics
 * item inherits through {@code RelicItem}, so this ordinary method merge creates the single JVM
 * dispatch override without modifying the interface or its other behavior.</p>
 */
@Pseudo
@Mixin(targets = Mm2DeterminismContract.RELIC_ITEM_CLASS, remap = false)
public abstract class RelicsStatRandomMixin {
    public void randomizeStat(
            ItemStack stack,
            String abilityId,
            String statId
    ) {
        IRelicItem relic = (IRelicItem) (Object) this;
        StatData entry = relic.getStatData(abilityId, statId);
        double result = MathUtils.round(MathUtils.randomBetween(
                RelicsStatRandomDeterminism.randomFor(
                        (Item) (Object) this, stack, abilityId, statId),
                entry.getInitialValue().getKey(),
                entry.getInitialValue().getValue()), 5);
        relic.setAbilityValue(stack, abilityId, statId, result);
    }
}
