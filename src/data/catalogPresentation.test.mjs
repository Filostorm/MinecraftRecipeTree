import assert from 'node:assert/strict';
import test from 'node:test';
import {
  catalogTypePresentation,
  isItemCatalogEligible,
} from './catalogPresentation.ts';

const item = (overrides = {}) => ({
  k: 'item|minecraft:stone',
  id: 'minecraft:stone',
  n: 'Stone',
  m: 'minecraft',
  ...overrides,
});

test('excludes synthetic Immersive Technology multiblock render models from the item browser', () => {
  assert.equal(
    isItemCatalogEligible(item({
      t: 'custom_mctmods.immersivetechnology.common.util.compat.jei.genericmultiblockingredient_e2d9e4dd',
    })),
    false,
  );
  assert.equal(
    isItemCatalogEligible(item({
      k: 'item|immersivetech:metal_multiblock:3',
      id: 'immersivetech:metal_multiblock',
      n: 'Steam Turbine',
      m: 'immersivetech',
    })),
    true,
  );
});

test('uses concise labels for Multiblock Madness custom ingredient types', () => {
  assert.deepEqual(
    catalogTypePresentation('custom_thaumcraft.api.aspects.aspectlist_0409b2e6'),
    {label: 'Aspect', recognized: true},
  );
  assert.deepEqual(
    catalogTypePresentation('custom_mekanism.api.gas.gasstack_3b5b153e'),
    {label: 'Gas', recognized: true},
  );
  assert.deepEqual(
    catalogTypePresentation(
      'custom_hellfirepvp.modularmachinery.common.integration.ingredient.hybridfluid_2ad9a7d4',
    ),
    {label: 'Fluid', recognized: true},
  );
  assert.deepEqual(
    catalogTypePresentation('custom_lach_01298.qmd.particle.particlestack_81a31f1d'),
    {label: 'Particle', recognized: true},
  );
});

test('keeps unknown exporter types readable and marks them for diagnostics', () => {
  assert.deepEqual(
    catalogTypePresentation('custom_example.internal.ingredient_1234abcd'),
    {label: 'Custom ingredient', recognized: false},
  );
  assert.deepEqual(
    catalogTypePresentation('future_resource'),
    {label: 'future resource', recognized: false},
  );
});
