import {exportJWK, generateKeyPair, SignJWT} from 'jose';

export const TEST_ORIGIN = 'https://minecraftrecipetree.craftsmannsoftware.com';
export const TEST_SUPABASE_URL = 'https://mrt-unit-test.supabase.co';
export const TEST_USER_ID = '123e4567-e89b-42d3-a456-426614174000';

export async function supabaseTestAuthentication() {
  const {privateKey, publicKey} = await generateKeyPair('ES256', {extractable: true});
  const publicJwk = await exportJWK(publicKey);
  const kid = 'mrt-unit-test-key';
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async input => {
    const url = typeof input === 'string'
      ? input
      : input instanceof URL
        ? input.href
        : input.url;
    if (url === `${TEST_SUPABASE_URL}/auth/v1/.well-known/jwks.json`) {
      return Response.json({keys: [{...publicJwk, kid, use: 'sig', alg: 'ES256'}]});
    }
    return originalFetch(input);
  };
  const token = await new SignJWT({
    role: 'authenticated',
    email: 'builder@example.com',
    user_metadata: {full_name: 'Recipe Builder'},
  })
    .setProtectedHeader({alg: 'ES256', kid, typ: 'JWT'})
    .setSubject(TEST_USER_ID)
    .setIssuer(`${TEST_SUPABASE_URL}/auth/v1`)
    .setAudience('authenticated')
    .setIssuedAt()
    .setExpirationTime('5m')
    .sign(privateKey);
  return {
    authorization: `Bearer ${token}`,
    restoreFetch() {
      globalThis.fetch = originalFetch;
    },
    runtime: {
      SUPABASE_URL: TEST_SUPABASE_URL,
    },
  };
}
