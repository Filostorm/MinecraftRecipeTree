import assert from 'node:assert/strict';
import test from 'node:test';
import {MINECRAFT_PRODUCT_DISCLAIMER} from './legalNotices.ts';

test('the public Minecraft product disclaimer retains its exact required text', () => {
  assert.equal(
    MINECRAFT_PRODUCT_DISCLAIMER,
    'NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.',
  );
});
