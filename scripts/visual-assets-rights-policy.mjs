import {isDeepStrictEqual} from 'node:util';
import {GTNH_1710_PROFILE} from './export-quality-policy.mjs';

export const GTNH_STRUCTURED_DATA_ONLY_POLICY_ID = 'gtnh-structured-data-only-v1';
export const VISUAL_ASSETS_POLICY_FORMAT = 'mrt-visual-assets-policy-v1';
export const GTNH_RECIPE_IMAGE_OMISSION_REASON =
  'third-party-artwork-rights-not-cleared';

export const GTNH_STRUCTURED_DATA_ONLY_VISUAL_ASSETS = Object.freeze({
  format: VISUAL_ASSETS_POLICY_FORMAT,
  mode: 'structured-data-only',
  policy: GTNH_STRUCTURED_DATA_ONLY_POLICY_ID,
  itemIcons: 0,
  categoryIcons: 0,
  recipePreviews: 0,
  mobSprites: 0,
  packedImageFiles: 0,
});

export function requiresGtnhStructuredDataOnlyPolicy(profile) {
  return profile === GTNH_1710_PROFILE;
}

export function hasExactGtnhStructuredDataOnlyVisualAssets(value) {
  return isDeepStrictEqual(value, GTNH_STRUCTURED_DATA_ONLY_VISUAL_ASSETS);
}
