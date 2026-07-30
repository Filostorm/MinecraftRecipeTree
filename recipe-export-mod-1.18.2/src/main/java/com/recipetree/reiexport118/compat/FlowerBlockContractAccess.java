package com.recipetree.reiexport118.compat;

import net.minecraft.world.effect.MobEffect;

import java.util.function.Supplier;

/**
 * Narrow bridge to Forge 1.18.2's FlowerBlock compatibility fields. Implemented by a vanilla
 * class mixin so the preflight can replace only a statically verified stale supplier.
 */
public interface FlowerBlockContractAccess {
    MobEffect reiexport$getLegacySuspiciousStewEffect();

    int reiexport$getStoredEffectDuration();

    void reiexport$setSuspiciousStewEffectSupplier(Supplier<MobEffect> supplier);
}
