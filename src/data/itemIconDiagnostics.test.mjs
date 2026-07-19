import assert from 'node:assert/strict';
import test from 'node:test';
import {
  ABSENT_ITEM_ICON_SAMPLE_LIMIT,
  BoundedAbsentItemIconCollector,
  BoundedItemIconFailureReporter,
  ITEM_ICON_FAILURE_SAMPLE_LIMIT,
  hasItemIconUriFailed,
  isItemIconFailureLogThreshold,
} from './itemIconDiagnostics.ts';

const DATASET = 'a'.repeat(64);

test('item icon failures log at first and power-of-two cumulative thresholds', () => {
  const reports = [];
  const reporter = new BoundedItemIconFailureReporter(DATASET, (message, summary) => {
    reports.push({message, summary});
  });

  for (let index = 1; index <= 10; index += 1) {
    reporter.report({
      uri: `/dataset/assets/${index}.webp`,
      itemKey: `item|example:${index}`,
      label: `Example ${index}`,
      detail: `HTTP ${index === 1 ? 404 : 500}`,
    });
  }

  assert.deepEqual(
    reports.map(report => report.summary.cumulativeFailures),
    [1, 2, 4, 8],
  );
  assert.match(reports[0].message, /HTTP fetch or image decode failed/);
  assert.equal(reports.at(-1).summary.datasetIdentity, DATASET);
});

test('item icon failure reporter retains a bounded unique URI sample', () => {
  const reports = [];
  const reporter = new BoundedItemIconFailureReporter(DATASET, (_message, summary) => {
    reports.push(summary);
  });

  for (let index = 0; index < 16; index += 1) {
    reporter.report({
      uri: `/dataset/assets/${index}.webp`,
      itemKey: `item|example:${index}`,
      label: `Example ${index}`,
      detail: new Error('decoder rejected the image'),
    });
  }
  reporter.report({
    uri: '/dataset/assets/0.webp',
    itemKey: 'item|example:0',
    label: 'Example 0',
    detail: 'duplicate component failure',
  });

  const finalPowerOfTwoReport = reports.at(-1);
  assert.equal(finalPowerOfTwoReport.cumulativeFailures, 16);
  assert.equal(finalPowerOfTwoReport.sampledFailures.length, ITEM_ICON_FAILURE_SAMPLE_LIMIT);
  assert.equal(
    new Set(finalPowerOfTwoReport.sampledFailures.map(sample => sample.uri)).size,
    ITEM_ICON_FAILURE_SAMPLE_LIMIT,
  );
  assert.equal(finalPowerOfTwoReport.failureEventsOmitted, 8);
});

test('threshold helper rejects non-positive and non-power-of-two values', () => {
  for (const count of [1, 2, 4, 8, 1024]) assert.equal(isItemIconFailureLogThreshold(count), true);
  for (const count of [-1, 0, 3, 5, 12, 1.5]) {
    assert.equal(isItemIconFailureLogThreshold(count), false);
  }
});

test('item icon failure state applies only to the exact URI so changed assets retry', () => {
  assert.equal(hasItemIconUriFailed('/dataset/assets/old.webp', '/dataset/assets/old.webp'), true);
  assert.equal(hasItemIconUriFailed('/dataset/assets/old.webp', '/dataset/assets/new.webp'), false);
  assert.equal(hasItemIconUriFailed(null, '/dataset/assets/new.webp'), false);
});

test('absent catalog icon collector produces one bounded aggregate snapshot', () => {
  const collector = new BoundedAbsentItemIconCollector();
  collector.observe({
    k: 'item|example:present',
    id: 'example:present',
    n: 'Present',
    m: 'example',
    icon: 'assets/s/0-0-0.webp',
  });
  for (let index = 0; index < 12; index += 1) {
    collector.observe({
      k: `item|example:missing_${index}`,
      id: `example:missing_${index}`,
      n: `Missing ${index}`,
      m: 'example',
    });
  }

  const summary = collector.summary();
  assert.ok(summary);
  assert.equal(summary.catalogItems, 13);
  assert.equal(summary.itemsWithoutIconUrl, 12);
  assert.equal(summary.sampleKeys.length, ABSENT_ITEM_ICON_SAMPLE_LIMIT);
  assert.equal(summary.sampleKeysOmitted, 4);
});

test('absent catalog icon collector stays silent when every item has an icon URL', () => {
  const collector = new BoundedAbsentItemIconCollector();
  collector.observe({
    k: 'item|example:present',
    id: 'example:present',
    n: 'Present',
    m: 'example',
    icon: 'assets/s/0-0-0.webp',
  });
  assert.equal(collector.summary(), null);
});
