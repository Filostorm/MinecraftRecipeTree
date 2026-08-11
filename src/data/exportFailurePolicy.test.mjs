import assert from 'node:assert/strict';
import test from 'node:test';

import {
  findingsForNonReportableExportFailures,
  isReportableExportFailureMessage,
} from './exportFailurePolicy.ts';

test('recognizes the historical compatibility message classes for every mod', () => {
  assert.equal(
    isReportableExportFailureMessage(
      'mob aether_redux:blightbunny_spawner rendered fully transparent and was omitted',
    ),
    false,
  );
  assert.equal(
    isReportableExportFailureMessage(
      'blockdrops aether_redux:boss_doorway_carved_base: no standard candidate tool satisfies requiresCorrectToolForDrops; probing with a netherite pickaxe',
    ),
    false,
  );
  assert.equal(
    isReportableExportFailureMessage(
      'mob aether:whirlwind rendered fully transparent and was omitted',
    ),
    false,
  );
  assert.equal(
    isReportableExportFailureMessage(
      'blockdrops v_slab_compat:twigs/rhyolite_vertical_slab: no standard candidate tool satisfies requiresCorrectToolForDrops; probing with a netherite pickaxe',
    ),
    false,
  );
  assert.equal(
    isReportableExportFailureMessage(
      'mob example:broken_renderer failed with an IllegalStateException',
    ),
    true,
  );
  assert.equal(
    isReportableExportFailureMessage(
      'blockdrops example:machine: loot-table evaluation failed',
    ),
    true,
  );
});

test('replaces the unavailable report action with a compatibility explanation', () => {
  const findings = findingsForNonReportableExportFailures([
    '2 recipes could not be exported. After a successful import, use “Share exporter errors” if you want to report them.',
    'An unrelated finding remains.',
  ], 2);

  assert.deepEqual(findings, [
    'An unrelated finding remains.',
    '2 expected exporter compatibility cases were logged by an older exporter. No error report is needed.',
  ]);
  assert.equal(Object.isFrozen(findings), true);
});
