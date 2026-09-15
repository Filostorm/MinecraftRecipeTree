import assert from 'node:assert/strict';
import test from 'node:test';
import {auditItemIcons} from './itemIconAudit.ts';
const item = (id, rest = {}) => ({k: `item|${id}`, id, n: id, m: id.split(':')[0], ...rest});
const emptyWarning = key => `UPSTREAM_NATIVE_ICON_UNAVAILABLE ingredient ${key}: rendered image is fully transparent; both the exact 16x16 HEI draw and bounded native overscan draw produced no visible pixel; omitting the PNG and JSON icon reference so the viewer uses its explicitly named fallback`;

test('only exact zero-pixel evidence accounts for a native omission', () => {
  const items = [item('test:invisible'), item('test:other'), item('test:good', {icon:'good.png'})];
  const result = auditItemIcons(items, [emptyWarning(items[0].k)]);
  assert.equal(result.byReason['native-render-empty'], 1);
  assert.equal(result.byReason.unexplained, 1);
  assert.equal(auditItemIcons(items, ['UPSTREAM_NATIVE_ICON_UNAVAILABLE ingredient item|test:other']).byReason.unexplained, 2);
});
test('EMC is accounted for only if its table icon actually exists', () => {
  const emc = item('projecte:emc', {k: 'emc|projecte:emc', t: 'emc'});
  assert.equal(auditItemIcons([emc], []).byReason.unexplained, 1);
  assert.equal(auditItemIcons([emc, item('projecte:transmutation_table', {icon:'table.png'})], []).byReason['emc-table-icon'], 1);
});
test('structured-only publications remain explicit and malformed evidence fails', () => {
  assert.equal(auditItemIcons([item('test:one')], [], true).byReason['structured-data-only'], 1);
  assert.throws(() => auditItemIcons([], null), /warnings array/);
});
test('REI omissions match the exact type and id', () => {
  const warning = 'UPSTREAM_NATIVE_ICON_UNAVAILABLE id=test:one type=minecraft:item valueClass=Stack itemClass=Item blockClass=Block visiblePixels=0 contract=audited; exact native 16x16 render has zero visible pixels; omitted PNG/icon field; named UI fallback used';
  const result = auditItemIcons([item('test:one'), item('test:one', {t:'fluid', k:'fluid|test:one'})], [warning]);
  assert.equal(result.byReason['native-render-empty'], 1);
  assert.equal(result.byReason.unexplained, 1);
});
test('REI custom catalog types use a slash while warning evidence uses a colon', () => {
  const warning = 'UPSTREAM_NATIVE_ICON_UNAVAILABLE id=reliquary:cure type=jeed:jei_plugin_jei_compat_mobeffectinstance valueClass=Effect itemClass=null blockClass=null visiblePixels=0 contract=audited; exact native 16x16 render has zero visible pixels; omitted PNG/icon field; named UI fallback used';
  const result = auditItemIcons([
    item('reliquary:cure', {t: 'jeed/jei_plugin_jei_compat_mobeffectinstance'}),
    item('reliquary:cure'),
  ], [warning]);
  assert.equal(result.byReason['native-render-empty'], 1);
  assert.equal(result.byReason.unexplained, 1);
});
