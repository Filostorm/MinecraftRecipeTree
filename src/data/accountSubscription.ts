import {accountFetch} from '../account/supabaseClient';

export interface AccountSubscription {
  amountCents: number;
  nextBillingAt: number;
  status: string;
}

interface SubscriptionResponse {
  subscription: AccountSubscription | null;
  error?: string;
}

async function parseResponse(response: Response): Promise<SubscriptionResponse> {
  const body = await response.json().catch(() => null) as SubscriptionResponse | null;
  if (!response.ok) {
    throw new Error(body?.error ?? 'Monthly donation tier could not be loaded.');
  }
  if (!body || !('subscription' in body)) {
    throw new Error('Monthly donation tier returned an invalid response.');
  }
  return body;
}

export async function loadAccountSubscription(): Promise<AccountSubscription | null> {
  return (await parseResponse(await accountFetch('/api/donations/subscription'))).subscription;
}

export async function updateAccountSubscription(amountCents: number): Promise<AccountSubscription> {
  const response = await accountFetch('/api/donations/subscription', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({amountCents}),
  });
  const subscription = (await parseResponse(response)).subscription;
  if (!subscription) throw new Error('Monthly donation tier disappeared while it was being updated.');
  return subscription;
}
