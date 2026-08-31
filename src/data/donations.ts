import {Platform} from 'react-native';
import {accountFetch} from '../account/supabaseClient';
import {
  donationRecord,
  parseDonationStatus,
  type DonationStatus,
} from './donationStatus';

export {
  parseDonationStatus,
  type DonationLeaderboardEntry,
  type DonationMark,
  type DonationStatus,
} from './donationStatus';

const DONATIONS_ENDPOINT = Platform.OS === 'web'
  ? '/api/donations'
  : 'https://minecraftrecipetree.craftsmannsoftware.com/api/donations';

export async function loadDonationStatus(): Promise<DonationStatus> {
  const response = await fetch(DONATIONS_ENDPOINT, {
    headers: {Accept: 'application/json'},
    cache: 'no-store',
  });
  const value = await response.json() as unknown;
  if (!response.ok) {
    const error = donationRecord(value, 'Donation error').error;
    throw new Error(typeof error === 'string' ? error : `Donations returned HTTP ${response.status}.`);
  }
  return parseDonationStatus(value);
}

function currentReturnUrl(): string {
  if (Platform.OS === 'web' && typeof window !== 'undefined') return window.location.href;
  return 'https://minecraftrecipetree.craftsmannsoftware.com/';
}

export async function createDonationCheckout(input: {
  amountCents: number;
  cadence: 'one_time' | 'monthly';
  publicName: string | null;
}): Promise<string> {
  const response = await accountFetch(`${DONATIONS_ENDPOINT}/checkout`, {
    method: 'POST',
    headers: {'Content-Type': 'application/json', Accept: 'application/json'},
    credentials: 'include',
    body: JSON.stringify({...input, returnUrl: currentReturnUrl()}),
  });
  const value = donationRecord(await response.json(), 'Donation Checkout response');
  if (!response.ok) {
    throw new Error(
      typeof value.error === 'string'
        ? value.error
        : `Donation Checkout returned HTTP ${response.status}.`,
    );
  }
  if (
    Object.keys(value).length !== 1 ||
    typeof value.checkoutUrl !== 'string' ||
    !value.checkoutUrl.startsWith('https://checkout.stripe.com/')
  ) {
    throw new Error('Donation Checkout returned an invalid hosted URL.');
  }
  return value.checkoutUrl;
}
