import type {SupabaseClient} from '@supabase/supabase-js';
import {Platform} from 'react-native';
import {SUPABASE_PROJECT_URL, SUPABASE_PUBLISHABLE_KEY} from './supabaseConfig';

let clientPromise: Promise<SupabaseClient> | null = null;

export function supabaseAccountClient(): Promise<SupabaseClient> {
  if (Platform.OS !== 'web') {
    return Promise.reject(new Error('Supabase accounts are currently available only on the web.'));
  }
  if (clientPromise) return clientPromise;
  clientPromise = (async () => {
    const {createClient} = await import('@supabase/supabase-js');
    return createClient(SUPABASE_PROJECT_URL, SUPABASE_PUBLISHABLE_KEY, {
      auth: {
        flowType: 'pkce',
        persistSession: true,
        autoRefreshToken: true,
        detectSessionInUrl: true,
        experimental: {
          appendPkceFlowIdToRedirects: true,
        },
      },
    });
  })().catch(error => {
    clientPromise = null;
    console.error('Supabase account client could not be initialized.', error);
    throw error;
  });
  return clientPromise;
}

const AUTH_REDIRECT_PARAMETERS = [
  'code',
  'error',
  'error_code',
  'error_description',
  'sb_flow_id',
] as const;

export function cleanFailedAccountAuthRedirect(): void {
  if (Platform.OS !== 'web' || typeof window === 'undefined') return;
  const url = new URL(window.location.href);
  for (const parameter of AUTH_REDIRECT_PARAMETERS) url.searchParams.delete(parameter);
  const nextUrl = `${url.pathname}${url.search}${url.hash}`;
  window.history.replaceState(window.history.state, '', nextUrl);
}

export async function accountFetch(
  input: RequestInfo | URL,
  init: RequestInit = {},
): Promise<Response> {
  const client = await supabaseAccountClient();
  const {data, error} = await client.auth.getSession();
  if (error) throw new Error(`Supabase session could not be read: ${error.message}`);
  const headers = new Headers(init.headers);
  if (data.session?.access_token) {
    headers.set('Authorization', `Bearer ${data.session.access_token}`);
  }
  return fetch(input, {...init, headers});
}
