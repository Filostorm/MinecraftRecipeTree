import assert from 'node:assert/strict';
import test from 'node:test';

const {validDisplayName, validEmail, validPassword} = await import('./userCredentials.ts');

test('email normalization is strict and predictable', () => {
  assert.equal(validEmail(' Recipe.Builder@Example.COM '), 'recipe.builder@example.com');
  assert.throws(() => validEmail('missing-domain@example'), /valid email/u);
});

test('password validation preserves spaces and enforces the documented length', () => {
  assert.equal(validPassword('  useful password  '), '  useful password  ');
  assert.throws(() => validPassword('short'), /8–128/u);
  assert.throws(() => validPassword(`valid-pass\nword`), /8–128/u);
});

test('usernames are trimmed, compacted, and bounded', () => {
  assert.equal(validDisplayName('  Recipe   Builder  '), 'Recipe Builder');
  assert.throws(() => validDisplayName('x'), /2–32/u);
  assert.throws(() => validDisplayName(`Recipe\nBuilder`), /control characters/u);
});
