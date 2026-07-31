package com.recipetree.reiexport118.mixin;

import com.recipetree.reiexport118.compat.FlowerBlockContractAccess;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.level.block.FlowerBlock;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.function.Supplier;

@Mixin(FlowerBlock.class)
public abstract class FlowerBlockContractMixin implements FlowerBlockContractAccess {
    @Shadow(remap = false, aliases = "f_53508_")
    @Final
    private MobEffect suspiciousStewEffect;

    @Shadow(remap = false, aliases = "f_53509_")
    @Final
    private int effectDuration;

    @Shadow(remap = false)
    @Final
    @Mutable
    private Supplier<MobEffect> suspiciousStewEffectSupplier;

    @Override
    @Unique
    public MobEffect reiexport$getLegacySuspiciousStewEffect() {
        return suspiciousStewEffect;
    }

    @Override
    @Unique
    public int reiexport$getStoredEffectDuration() {
        return effectDuration;
    }

    @Override
    @Unique
    public void reiexport$setSuspiciousStewEffectSupplier(Supplier<MobEffect> supplier) {
        suspiciousStewEffectSupplier = supplier;
    }
}
