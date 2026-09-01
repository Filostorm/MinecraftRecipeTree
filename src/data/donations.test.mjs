import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';

const {parseDonationStatus} = await import('./donationStatus.ts');
const modalSource = await readFile(new URL('../donations/DonationsModal.tsx', import.meta.url), 'utf8');

test('parses a bounded public monthly donation status', () => {
  const value = {
    enabled: true,
    currency: 'usd',
    month: {startsAt: 1_788_000_000_000, endsAt: 1_790_678_400_000},
    totalCents: 1200,
    goalCents: 1800,
    marks: [
      {id: 'supabase', label: 'Supabase', monthlyCents: 2500, cumulativeCents: 2500},
      {id: 'cloudflare', label: 'Cloudflare', monthlyCents: 500, cumulativeCents: 3000},
      {id: 'github-actions', label: 'GitHub Actions', monthlyCents: 400, cumulativeCents: 3400},
    ],
    leaderboard: [{donorKey: 'user:abc', displayName: 'Builder', totalCents: 1000}],
    anonymous: {donorCount: 2, totalCents: 200},
  };
  assert.deepEqual(parseDonationStatus(value), value);
});

test('rejects duplicate public donor identities and malformed service marks', () => {
  const base = {
    enabled: true,
    currency: 'usd',
    month: {startsAt: 1, endsAt: 2},
    totalCents: 100,
    goalCents: 300,
    marks: [
      {id: 'supabase', label: 'Supabase', monthlyCents: 1, cumulativeCents: 1},
      {id: 'cloudflare', label: 'Cloudflare', monthlyCents: 1, cumulativeCents: 2},
      {id: 'github-actions', label: 'GitHub Actions', monthlyCents: 1, cumulativeCents: 3},
    ],
    leaderboard: [
      {donorKey: 'same', displayName: 'One', totalCents: 50},
      {donorKey: 'same', displayName: 'Two', totalCents: 50},
    ],
    anonymous: {donorCount: 0, totalCents: 0},
  };
  assert.throws(() => parseDonationStatus(base), /leaderboard row 1/i);
});

test('the compact donation sheet uses concise service context and public names by default', () => {
  assert.match(modalSource, /useState\(true\)/u);
  assert.match(modalSource, /setShowName\(true\)/u);
  assert.match(modalSource, /<Text style=\{styles\.donateButtonText\}>Donate<\/Text>/u);
  assert.match(modalSource, /'github-actions': 'Automated updates'/u);
  assert.match(modalSource, /cloudflare: 'Hosting & storage'/u);
  assert.match(modalSource, /supabase: 'Accounts & sync'/u);
  assert.doesNotMatch(modalSource, /No public donors yet this month\./u);
  assert.doesNotMatch(modalSource, /dollars\(mark\.cumulativeCents\).*level/u);
  assert.match(modalSource, /maxWidth: 740 \/ interfaceZoom/u);
});

test('donation milestone lines stay anchored to their cumulative meter thresholds', () => {
  assert.match(modalSource, /mark\.cumulativeCents \/ status\.goalCents \* 100/u);
  assert.match(modalSource, /const METER_TRACK_HEIGHT = 300;/u);
  assert.match(modalSource, /meterPanel: \{width: 210, minHeight: 373,/u);
  assert.match(
    modalSource,
    /meterLevel: \{position: 'absolute', right: 0, left: 0, height: 3, flexDirection: 'row', alignItems: 'center'\}/u,
  );
  assert.doesNotMatch(modalSource, /meterLevel: \{[^\n]*translateY/u);
});
