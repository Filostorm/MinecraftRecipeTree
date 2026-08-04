import {type DatasetRuntime, methodNotAllowed} from './datasetRuntime.ts';

const BETA_DATA_PROXY_PATHS = [
  '/api/datasets',
  '/api/modpacks',
] as const;
const BETA_DATA_PROXY_PREFIXES = [
  '/dataset/publications/',
  '/dataset/preview-sets/',
] as const;
const FORWARDED_REQUEST_HEADERS = [
  'accept',
  'if-match',
  'if-modified-since',
  'if-none-match',
  'if-unmodified-since',
  'range',
] as const;

type UpstreamFetch = (request: Request) => Promise<Response>;

function betaDataOrigin(value: string): URL {
  let origin: URL;
  try {
    origin = new URL(value);
  } catch {
    throw new Error('BETA_DATA_ORIGIN must be an absolute HTTPS origin.');
  }
  if (
    origin.protocol !== 'https:' ||
    origin.username !== '' ||
    origin.password !== '' ||
    origin.pathname !== '/' ||
    origin.search !== '' ||
    origin.hash !== ''
  ) {
    throw new Error('BETA_DATA_ORIGIN must be an absolute HTTPS origin without credentials or a path.');
  }
  return origin;
}

function isBetaDataPath(pathname: string): boolean {
  return (
    BETA_DATA_PROXY_PATHS.some(path => pathname === path) ||
    BETA_DATA_PROXY_PREFIXES.some(prefix => pathname.startsWith(prefix))
  );
}

function forwardedHeaders(request: Request): Headers {
  const headers = new Headers();
  for (const name of FORWARDED_REQUEST_HEADERS) {
    const value = request.headers.get(name);
    if (value !== null) headers.set(name, value);
  }
  return headers;
}

/**
 * Gives the isolated beta Site read-only access to the public production dataset corpus.
 * Authentication, cookies, mutation methods, administration, and feedback are never forwarded.
 */
export async function proxyBetaDatasetRequest(
  request: Request,
  runtime: DatasetRuntime,
  url: URL,
  fetchUpstream: UpstreamFetch = fetch,
): Promise<Response | null> {
  if (!runtime.BETA_DATA_ORIGIN || !isBetaDataPath(url.pathname)) return null;
  if (request.method !== 'GET' && request.method !== 'HEAD') {
    console.warn('A beta data proxy mutation was refused.', {
      method: request.method,
      pathname: url.pathname,
    });
    return methodNotAllowed('GET, HEAD');
  }

  const origin = betaDataOrigin(runtime.BETA_DATA_ORIGIN);
  const upstreamUrl = new URL(`${url.pathname}${url.search}`, origin);
  let upstreamResponse: Response;
  try {
    upstreamResponse = await fetchUpstream(
      new Request(upstreamUrl, {
        method: request.method,
        headers: forwardedHeaders(request),
        redirect: 'manual',
      }),
    );
  } catch (error) {
    console.error('The beta data proxy could not reach its configured production origin.', {
      origin: origin.origin,
      pathname: url.pathname,
      error,
    });
    throw error;
  }

  if (!upstreamResponse.ok && upstreamResponse.status !== 304) {
    console.error('The beta data proxy received an unsuccessful production response.', {
      origin: origin.origin,
      pathname: url.pathname,
      status: upstreamResponse.status,
    });
  }
  const responseHeaders = new Headers(upstreamResponse.headers);
  responseHeaders.delete('set-cookie');
  responseHeaders.delete('www-authenticate');
  responseHeaders.set('X-MRT-Beta-Data-Origin', origin.origin);
  if (request.method === 'GET' && BETA_DATA_PROXY_PATHS.some(path => url.pathname === path)) {
    // Catalog responses are small. Reframe them at the beta edge so an upstream Worker's
    // compression/framing headers cannot leave a browser fetch waiting on a transformed stream.
    const body = await upstreamResponse.arrayBuffer();
    responseHeaders.delete('content-encoding');
    responseHeaders.set('content-length', String(body.byteLength));
    return new Response(body, {
      status: upstreamResponse.status,
      statusText: upstreamResponse.statusText,
      headers: responseHeaders,
    });
  }
  return new Response(request.method === 'HEAD' ? null : upstreamResponse.body, {
    status: upstreamResponse.status,
    statusText: upstreamResponse.statusText,
    headers: responseHeaders,
  });
}
