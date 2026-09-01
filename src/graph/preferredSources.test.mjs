import assert from 'node:assert/strict';
import test from 'node:test';
import {
  loadPreferredSources,
  persistPreferredSources,
} from './preferredSources.ts';

function memoryStorage(initial = {}) {
  const values = new Map(Object.entries(initial));
  return {
    getItem(key) {
      return values.get(key) ?? null;
    },
    setItem(key, value) {
      values.set(key, String(value));
    },
    removeItem(key) {
      values.delete(key);
    },
    clear() {
      values.clear();
    },
    key(index) {
      return [...values.keys()][index] ?? null;
    },
    get length() {
      return values.size;
    },
  };
}

function withStorage(storage, run) {
  const previous = globalThis.localStorage;
  Object.defineProperty(globalThis, 'localStorage', {configurable: true, value: storage});
  try {
    return run();
  } finally {
    Object.defineProperty(globalThis, 'localStorage', {configurable: true, value: previous});
  }
}

const packA = {slug: 'pack-a', publicationId: 'a'.repeat(64)};
const packB = {slug: 'pack-b', publicationId: 'b'.repeat(64)};
const packANext = {slug: 'pack-a', publicationId: 'c'.repeat(64)};
const acceptAll = () => true;

test('preferred sources are isolated by pack and publication', () => {
  withStorage(memoryStorage(), () => {
    persistPreferredSources(packA, {'item|a': {t: 'recipe', ref: [1, 2]}});
    persistPreferredSources(packB, {'item|b': {t: 'recipe', ref: [3, 4]}});
    persistPreferredSources(packANext, {'item|c': {t: 'recipe', ref: [5, 6]}});

    assert.deepEqual(loadPreferredSources(packA, acceptAll), {
      'item|a': {t: 'recipe', ref: [1, 2]},
    });
    assert.deepEqual(loadPreferredSources(packB, acceptAll), {
      'item|b': {t: 'recipe', ref: [3, 4]},
    });
    assert.deepEqual(loadPreferredSources(packANext, acceptAll), {
      'item|c': {t: 'recipe', ref: [5, 6]},
    });
  });
});

test('unscoped browser preferences migrate only when valid for the current publication', () => {
  const storage = memoryStorage({
    'minecraft-recipe-tree.preferred-sources.v2': JSON.stringify({
      'item|current': {t: 'recipe', ref: [1, 2]},
      'item|other-pack': {t: 'recipe', ref: [7, 8]},
    }),
  });
  withStorage(storage, () => {
    const migrated = loadPreferredSources(
      packA,
      (itemKey, source) => itemKey === 'item|current' && source.t === 'recipe' && source.ref[0] === 1,
    );
    assert.deepEqual(migrated, {'item|current': {t: 'recipe', ref: [1, 2]}});
    const root = JSON.parse(storage.getItem('minecraft-recipe-tree.preferred-sources.v3'));
    assert.equal(root.version, 3);
    assert.deepEqual(root.scopes[`${packA.slug}:${packA.publicationId}`], migrated);
  });
});
