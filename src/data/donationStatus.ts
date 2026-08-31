export interface DonationMark {
  id: 'supabase' | 'cloudflare' | 'github-actions';
  label: string;
  monthlyCents: number;
  cumulativeCents: number;
}

export interface DonationLeaderboardEntry {
  donorKey: string;
  displayName: string;
  totalCents: number;
}

export type DonationStatus =
  | {enabled: false; error: string}
  | {
      enabled: true;
      currency: 'usd';
      month: {startsAt: number; endsAt: number};
      totalCents: number;
      goalCents: number;
      marks: DonationMark[];
      leaderboard: DonationLeaderboardEntry[];
      anonymous: {donorCount: number; totalCents: number};
    };

export function donationRecord(value: unknown, label: string): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error(`${label} is not an object.`);
  }
  return value as Record<string, unknown>;
}

function integer(value: unknown, label: string, minimum = 0): number {
  if (!Number.isSafeInteger(value) || (value as number) < minimum) {
    throw new Error(`${label} is invalid.`);
  }
  return value as number;
}

export function parseDonationStatus(value: unknown): DonationStatus {
  const root = donationRecord(value, 'Donation status');
  if (root.enabled === false) {
    if (Object.keys(root).sort().join(',') !== 'enabled,error' || typeof root.error !== 'string') {
      throw new Error('Disabled donation status has an invalid shape.');
    }
    return {enabled: false, error: root.error};
  }
  if (
    root.enabled !== true ||
    root.currency !== 'usd' ||
    !Array.isArray(root.marks) ||
    root.marks.length !== 3 ||
    !Array.isArray(root.leaderboard) ||
    root.leaderboard.length > 50
  ) {
    throw new Error('Donation status has an invalid shape.');
  }
  const month = donationRecord(root.month, 'Donation month');
  const startsAt = integer(month.startsAt, 'Donation month start');
  const endsAt = integer(month.endsAt, 'Donation month end');
  if (endsAt <= startsAt) throw new Error('Donation month bounds are invalid.');
  const markIds = new Set<string>();
  const marks = root.marks.map((value, index) => {
    const mark = donationRecord(value, `Donation mark ${index}`);
    if (
      (mark.id !== 'supabase' && mark.id !== 'cloudflare' && mark.id !== 'github-actions') ||
      markIds.has(mark.id) ||
      typeof mark.label !== 'string' ||
      mark.label.length < 1 ||
      mark.label.length > 40
    ) {
      throw new Error(`Donation mark ${index} is invalid.`);
    }
    markIds.add(mark.id);
    const id = mark.id as DonationMark['id'];
    return {
      id,
      label: mark.label,
      monthlyCents: integer(mark.monthlyCents, `Donation mark ${index} monthly amount`, 1),
      cumulativeCents: integer(mark.cumulativeCents, `Donation mark ${index} cumulative amount`, 1),
    };
  });
  const donorKeys = new Set<string>();
  const leaderboard = root.leaderboard.map((value, index) => {
    const entry = donationRecord(value, `Donation leaderboard row ${index}`);
    if (
      typeof entry.donorKey !== 'string' ||
      entry.donorKey.length > 96 ||
      donorKeys.has(entry.donorKey) ||
      typeof entry.displayName !== 'string' ||
      entry.displayName.length < 1 ||
      entry.displayName.length > 60
    ) {
      throw new Error(`Donation leaderboard row ${index} is invalid.`);
    }
    donorKeys.add(entry.donorKey);
    return {
      donorKey: entry.donorKey,
      displayName: entry.displayName,
      totalCents: integer(entry.totalCents, `Donation leaderboard row ${index} amount`, 1),
    };
  });
  const anonymous = donationRecord(root.anonymous, 'Anonymous donation summary');
  return {
    enabled: true,
    currency: 'usd',
    month: {startsAt, endsAt},
    totalCents: integer(root.totalCents, 'Donation total'),
    goalCents: integer(root.goalCents, 'Donation goal', 1),
    marks,
    leaderboard,
    anonymous: {
      donorCount: integer(anonymous.donorCount, 'Anonymous donor count'),
      totalCents: integer(anonymous.totalCents, 'Anonymous donation total'),
    },
  };
}
