import assert from 'node:assert/strict';
import test from 'node:test';

const {recipeTreeUserIdentity} = await import('./userIdentity.ts');

function user(overrides = {}) {
  return {
    id: 'user-1',
    email: 'builder@example.com',
    app_metadata: {},
    user_metadata: {},
    identities: [],
    ...overrides,
  };
}

test('prefers the Discord identity display name over generic account metadata', () => {
  assert.deepEqual(
    recipeTreeUserIdentity(user({
      app_metadata: {provider: 'discord'},
      user_metadata: {full_name: 'Generic Name'},
      identities: [{provider: 'discord', identity_data: {global_name: 'Discord Builder'}}],
    })),
    {
      id: 'user-1',
      displayName: 'Discord Builder',
      email: 'builder@example.com',
      provider: 'discord',
    },
  );
});

test('uses email account metadata when no Discord identity is present', () => {
  assert.equal(
    recipeTreeUserIdentity(user({
      app_metadata: {provider: 'email'},
      user_metadata: {full_name: 'Email Builder'},
    })).displayName,
    'Email Builder',
  );
});

test('a user-selected display name overrides the connected Discord name', () => {
  assert.equal(
    recipeTreeUserIdentity(user({
      app_metadata: {provider: 'discord'},
      user_metadata: {display_name: 'Tree Architect'},
      identities: [{provider: 'discord', identity_data: {global_name: 'Discord Builder'}}],
    })).displayName,
    'Tree Architect',
  );
});
