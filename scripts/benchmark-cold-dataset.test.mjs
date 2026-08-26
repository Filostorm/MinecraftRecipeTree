import assert from 'node:assert/strict';
import {EventEmitter} from 'node:events';
import {link, mkdir, mkdtemp, realpath, rename, rm, symlink, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {dirname, resolve} from 'node:path';
import test from 'node:test';
import {
  ACTIVATION_THRESHOLDS,
  MIN_ACTIVATION_BENCHMARK_RUNS,
  MonotonicDeadline,
  assertAllowedApplicationUpstreamResponse,
  assertOwnedChromeCommandLine,
  assertProfileSlugBinding,
  assertRuntimeRootRemovable,
  assertSameBuildTree,
  chromeEnvironment,
  chromeUserDataDirectory,
  classifyActivationGate,
  closeHttpServer,
  computeBootstrapMetrics,
  createChromeSpawnGate,
  digestBuildTree,
  isolatedProcessEnvironment,
  isolatedRuntimePaths,
  parseBenchmarkArguments,
  prepareChromeUserDataDirectory,
  proxyUpstream,
  requireCanonicalProxyUrl,
  requireOwnedPageWebSocketUrl,
  resolveIsolatedOutputTarget,
  stopChrome,
  validateRunTraffic,
} from './benchmark-cold-dataset.mjs';

const MIB = 1024 * 1024;

test('output target is canonically isolated from every immutable input tree', async () => {
  const root = await mkdtemp(resolve(tmpdir(), 'mrt-cold-output-test-'));
  try {
    const paths = {
      dist: resolve(root, 'dist'),
      exportRoot: resolve(root, 'export'),
      publicationBundle: resolve(root, 'publication'),
      previewSidecar: resolve(root, 'preview'),
      reports: resolve(root, 'reports'),
    };
    for (const path of Object.values(paths)) await mkdir(resolve(path, 'nested'), {recursive: true});
    const options = {
      dist: paths.dist,
      exportRoot: paths.exportRoot,
      publication: resolve(paths.publicationBundle, 'publication.json'),
      previewSidecar: paths.previewSidecar,
      output: resolve(paths.reports, 'cold.json'),
    };
    assert.equal(
      await resolveIsolatedOutputTarget(options),
      resolve(await realpath(paths.reports), 'cold.json'),
    );

    for (const [name, protectedRoot] of [
      ['production build', paths.dist],
      ['core export', paths.exportRoot],
      ['core publication bundle', paths.publicationBundle],
      ['preview sidecar', paths.previewSidecar],
    ]) {
      await assert.rejects(
        resolveIsolatedOutputTarget({...options, output: resolve(protectedRoot, 'nested', 'cold.json')}),
        new RegExp(`outside the canonical ${name} root`),
      );
    }

    const alias = resolve(root, 'dist-alias');
    await symlink(paths.dist, alias, 'dir');
    await assert.rejects(
      resolveIsolatedOutputTarget({...options, output: resolve(alias, 'nested', 'cold.json')}),
      /outside the canonical production build root/,
    );
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('runtime environments replace home, XDG, and temporary roots without inheriting credentials', () => {
  const runtimeRoot = resolve(tmpdir(), 'mrt-runtime-environment-test');
  const paths = isolatedRuntimePaths(runtimeRoot);
  const source = {
    PATH: '/safe/bin',
    LANG: 'C.UTF-8',
    HOME: '/operator/home',
    TMPDIR: '/operator/tmp',
    XDG_CONFIG_HOME: '/operator/config',
    CLOUDFLARE_API_TOKEN: 'operator-secret',
    CLOUDFLARE_API_KEY: 'operator-key',
  };
  assert.deepEqual(isolatedProcessEnvironment(runtimeRoot, source), {
    HOME: paths.home,
    USERPROFILE: paths.home,
    APPDATA: paths.appData,
    LOCALAPPDATA: paths.localAppData,
    XDG_CONFIG_HOME: paths.config,
    XDG_CACHE_HOME: paths.cache,
    XDG_DATA_HOME: paths.data,
    XDG_STATE_HOME: paths.state,
    XDG_RUNTIME_DIR: paths.runtime,
    TMPDIR: paths.temporary,
    TMP: paths.temporary,
    TEMP: paths.temporary,
    PATH: '/safe/bin',
    LANG: 'C.UTF-8',
    CI: '1',
    NO_COLOR: '1',
    WRANGLER_WRITE_LOGS: 'false',
    WRANGLER_LOG_PATH: resolve(runtimeRoot, 'wrangler.log'),
    MINIFLARE_REGISTRY_PATH: resolve(runtimeRoot, 'miniflare-registry.json'),
  });
  assert.deepEqual(chromeEnvironment(runtimeRoot, source), {
    HOME: paths.home,
    USERPROFILE: paths.home,
    APPDATA: paths.appData,
    LOCALAPPDATA: paths.localAppData,
    XDG_CONFIG_HOME: paths.config,
    XDG_CACHE_HOME: paths.cache,
    XDG_DATA_HOME: paths.data,
    XDG_STATE_HOME: paths.state,
    XDG_RUNTIME_DIR: paths.runtime,
    TMPDIR: paths.temporary,
    TMP: paths.temporary,
    TEMP: paths.temporary,
    PATH: '/safe/bin',
    LANG: 'C.UTF-8',
    CHROME_LOG_FILE: resolve(runtimeRoot, 'chrome.log'),
  });
});

test('Chrome profile ownership is a precreated plain directory strictly below the isolated runtime', async () => {
  const runtimeRoot = await mkdtemp(resolve(tmpdir(), 'mrt-chrome-profile-test-'));
  try {
    const expected = chromeUserDataDirectory(runtimeRoot);
    assert.equal(expected, resolve(runtimeRoot, 'chrome-user-data'));
    const ownership = await prepareChromeUserDataDirectory(runtimeRoot);
    assert.equal(ownership.runtimeRoot, await realpath(runtimeRoot));
    assert.equal(ownership.userDataDir, await realpath(expected));
    await assert.rejects(
      prepareChromeUserDataDirectory(runtimeRoot),
      error => error?.code === 'EEXIST',
    );
  } finally {
    await rm(runtimeRoot, {recursive: true, force: true});
  }
});

test('CDP endpoint identity binds the exact loopback port and owned Chrome profile', () => {
  const port = 9_222;
  const userDataDir = resolve(tmpdir(), 'mrt-owned-chrome-profile');
  const webSocketUrl = `ws://127.0.0.1:${port}/devtools/page/ABC_def-123`;
  assert.equal(requireOwnedPageWebSocketUrl(webSocketUrl, port), webSocketUrl);
  for (const candidate of [
    `ws://127.0.0.1:${port + 1}/devtools/page/ABC`,
    `ws://localhost:${port}/devtools/page/ABC`,
    `ws://127.0.0.1:${port}/devtools/browser/ABC`,
    `ws://127.0.0.1:${port}/devtools/page/%41BC`,
    `ws://127.0.0.1:${port}/devtools/page/ABC?warm=true`,
  ]) {
    assert.throws(
      () => requireOwnedPageWebSocketUrl(candidate, port),
      /owned loopback endpoint|malformed path encoding/,
    );
  }

  const expectedArguments = [
    `--user-data-dir=${userDataDir}`,
    `--remote-debugging-port=${port}`,
    '--remote-debugging-address=127.0.0.1',
    '--enable-automation',
  ];
  assert.doesNotThrow(() =>
    assertOwnedChromeCommandLine({arguments: expectedArguments}, {userDataDir, port}),
  );
  for (const arguments_ of [
    expectedArguments.filter(argument => !argument.startsWith('--user-data-dir=')),
    expectedArguments.map(argument =>
      argument.startsWith('--user-data-dir=') ? '--user-data-dir=/tmp/competing-profile' : argument,
    ),
    [...expectedArguments, `--remote-debugging-port=${port}`],
  ]) {
    assert.throws(
      () => assertOwnedChromeCommandLine({arguments: arguments_}, {userDataDir, port}),
      /not bound to the owned Chrome command line/,
    );
  }
});

test('Chrome cancellation catches a process that appears after the first kill and removes its owned profile', async () => {
  const runtimeRoot = await mkdtemp(resolve(tmpdir(), 'mrt-chrome-cancel-test-'));
  try {
    const ownership = await prepareChromeUserDataDirectory(runtimeRoot);
    let currentProcess = null;
    let killRequests = 0;
    const launchPromise = new Promise(resolveLaunch => {
      queueMicrotask(() => {
        currentProcess = Object.assign(new EventEmitter(), {exitCode: null, signalCode: null});
        resolveLaunch();
      });
    });
    const chrome = {
      get process() {
        return currentProcess;
      },
      launchPromise,
      ownership,
      kill() {
        killRequests += 1;
        if (currentProcess && currentProcess.exitCode === null) {
          currentProcess.exitCode = 0;
          queueMicrotask(() => currentProcess.emit('close'));
        }
      },
    };
    await stopChrome(chrome);
    assert.equal(killRequests, 2);
    await assert.rejects(realpath(ownership.userDataDir), error => error?.code === 'ENOENT');
  } finally {
    await rm(runtimeRoot, {recursive: true, force: true});
  }
});

test('Chrome cancellation blocks a spawn after timeout and retains the runtime until launch settlement', async () => {
  const runtimeRoot = await mkdtemp(resolve(tmpdir(), 'mrt-chrome-late-launch-test-'));
  try {
    const ownership = await prepareChromeUserDataDirectory(runtimeRoot);
    const activeOwners = new Set();
    let spawnCalls = 0;
    let lateSpawnError = null;
    let settleLaunch;
    const spawnGate = createChromeSpawnGate(() => {
      spawnCalls += 1;
      return Object.assign(new EventEmitter(), {exitCode: null, signalCode: null});
    });
    const launchPromise = new Promise(resolveLaunch => {
      settleLaunch = resolveLaunch;
    });
    const chrome = {
      process: null,
      launchPromise,
      ownership,
      abortLaunch() {
        spawnGate.abort();
      },
      kill() {},
      releaseOwnership() {
        activeOwners.delete(chrome);
      },
    };
    activeOwners.add(chrome);

    const lateContinuation = new Promise(resolveLate => {
      setTimeout(() => {
        try {
          spawnGate.spawn('/not-used');
        } catch (error) {
          lateSpawnError = error;
        }
        settleLaunch();
        resolveLate();
      }, 30);
    });
    await assert.rejects(stopChrome(chrome, 10), /Chrome process\/profile cleanup failed/);
    assert.equal(activeOwners.size, 1);
    assert.equal(await realpath(ownership.userDataDir), ownership.userDataDir);
    assert.throws(
      () => assertRuntimeRootRemovable(runtimeRoot, activeOwners),
      /unreleasedChromeOwners=1/,
    );

    await lateContinuation;
    assert.equal(spawnCalls, 0);
    assert.match(lateSpawnError?.message ?? '', /cancelled before process ownership/);
    await stopChrome(chrome, 100);
    assert.equal(activeOwners.size, 0);
    assert.doesNotThrow(() => assertRuntimeRootRemovable(runtimeRoot, activeOwners));
    assert.throws(
      () => assertRuntimeRootRemovable(
        runtimeRoot,
        activeOwners,
        {exitCode: null, signalCode: null},
      ),
      /liveWrangler=true/,
    );
    await assert.rejects(realpath(ownership.userDataDir), error => error?.code === 'ENOENT');
  } finally {
    await rm(runtimeRoot, {recursive: true, force: true});
  }
});

test('monotonic deadline is one absolute budget rather than a resettable timeout', async () => {
  let now = 100;
  const deadline = new MonotonicDeadline(1_000, () => now);
  assert.equal(deadline.remainingMilliseconds('initial operation'), 1_000);
  now = 650.25;
  assert.equal(deadline.remainingMilliseconds('later operation'), 450);
  await deadline.wait(Promise.resolve('ready'), 'resolved operation');
  await assert.rejects(deadline.pause(451, 'oversized pause'), /cannot fit oversized pause/);
  now = 1_100;
  assert.throws(() => deadline.remainingMilliseconds('expired operation'), /deadline expired/);
});

test('proxy rejects non-origin-form and normalization-changing request targets', () => {
  const host = '127.0.0.1:48123';
  assert.equal(
    requireCanonicalProxyUrl('/api/datasets', host).href,
    `http://${host}/api/datasets`,
  );
  assert.equal(
    requireCanonicalProxyUrl('/dataset/publications/abc/exports/items.json?dataset=abc', host).search,
    '?dataset=abc',
  );
  for (const target of [
    '//evil.example/api/datasets',
    'http://evil.example/api/datasets',
    '/dataset/publications/abc/exports/part/../items.json?dataset=abc',
    '/dataset\\publications\\abc\\exports\\items.json?dataset=abc',
    '/dataset/publications/abc/exports/%2e/items.json?dataset=abc',
    '/%61pi/datasets',
    '/dataset/%70ublications/abc/exports/items.json?dataset=abc',
    '/api/datasets#fragment',
  ]) {
    assert.throws(() => requireCanonicalProxyUrl(target, host), /not canonical|changed during URL parsing/);
  }
});

test('upstream forwarding allows only typed application shell assets and rejects mislabeled JSON', async () => {
  assert.doesNotThrow(() =>
    assertAllowedApplicationUpstreamResponse('/', {'content-type': 'text/html; charset=utf-8'}),
  );
  assert.doesNotThrow(() =>
    assertAllowedApplicationUpstreamResponse(
      '/assets/application-abc123.js',
      {'content-type': 'text/javascript'},
    ),
  );
  assert.doesNotThrow(() =>
    assertAllowedApplicationUpstreamResponse('/.rsc', {'content-type': 'text/x-component'}),
  );
  assert.doesNotThrow(() =>
    assertAllowedApplicationUpstreamResponse('/local-pack-sw.js', {'content-type': 'text/javascript'}),
  );
  for (const [path, mediaType] of [
    ['/bootstrap', 'text/plain'],
    ['/extra.json', 'application/json'],
    ['/other-sw.js', 'text/javascript'],
    ['/publish.rsc', 'text/x-component'],
    ['/assets/bootstrap.bin', 'application/octet-stream'],
    ['/', 'text/plain'],
    ['/', 'application/json'],
    ['/assets/application-abc123.js', 'application/json'],
    ['/assets/application-abc123.js', ['text/javascript', 'application/json']],
  ]) {
    assert.throws(
      () => assertAllowedApplicationUpstreamResponse(path, {'content-type': mediaType}),
      /refuses non-allowlisted|refuses application resource/,
    );
  }
  await assert.rejects(
    proxyUpstream({}, {}, 1, '/bootstrap'),
    /refuses non-allowlisted application resource/,
  );
});

test('HTTP cleanup stops accepts before force-closing and independently bounds a stuck close', async () => {
  const events = [];
  await closeHttpServer({
    close(callback) {
      events.push('stop-accepts');
      queueMicrotask(() => callback());
    },
    closeIdleConnections() {
      events.push('close-idle');
    },
    closeAllConnections() {
      events.push('force-close');
    },
  }, 100);
  assert.deepEqual(events, ['stop-accepts', 'close-idle', 'force-close']);

  const stuckEvents = [];
  await assert.rejects(
    closeHttpServer({
      close() {
        stuckEvents.push('stop-accepts');
      },
      closeAllConnections() {
        stuckEvents.push('force-close');
      },
    }, 10),
    /HTTP server cleanup failed after stopping new accepts/,
  );
  assert.deepEqual(stuckEvents, ['stop-accepts', 'force-close']);
});

test('build digest preserves the framed digest for a stable canonical plain tree', async t => {
  const root = await realpath(await mkdtemp(resolve(tmpdir(), 'mrt-build-digest-stable-test-')));
  t.after(() => rm(root, {recursive: true, force: true}));
  const dist = resolve(root, 'dist');
  await mkdir(resolve(dist, 'nested'), {recursive: true});
  await writeFile(resolve(dist, 'a.txt'), 'alpha\n');
  await writeFile(resolve(dist, 'nested', 'b.txt'), 'beta\n');
  assert.deepEqual(await digestBuildTree(dist), {
    sha256: 'd65ed770f1acd7e1ffe895b3db4ff4db4c64decb756ff82a52e93f8bfbf223ad',
    files: 2,
    bytes: 11,
  });
});

test('build digest rejects hard-linked files', async t => {
  const root = await realpath(await mkdtemp(resolve(tmpdir(), 'mrt-build-digest-hardlink-test-')));
  t.after(() => rm(root, {recursive: true, force: true}));
  const dist = resolve(root, 'dist');
  await mkdir(dist);
  const first = resolve(dist, 'first.js');
  await writeFile(first, 'first\n');
  await link(first, resolve(dist, 'second.js'));
  await assert.rejects(digestBuildTree(dist), /must not be hard-linked/);
});

test('build digest rejects final and intermediate symlinks in the requested dist path', async t => {
  if (process.platform === 'win32') {
    t.skip('Creating filesystem symlinks requires an elevated Windows test environment.');
    return;
  }
  const root = await realpath(await mkdtemp(resolve(tmpdir(), 'mrt-build-digest-symlink-test-')));
  t.after(() => rm(root, {recursive: true, force: true}));
  const actualParent = resolve(root, 'actual-parent');
  const actualDist = resolve(actualParent, 'dist');
  await mkdir(actualDist, {recursive: true});
  await writeFile(resolve(actualDist, 'app.js'), 'app\n');

  const finalAlias = resolve(root, 'dist-alias');
  await symlink(actualDist, finalAlias, 'dir');
  await assert.rejects(digestBuildTree(finalAlias), /no-follow plain directory/);

  const parentAlias = resolve(root, 'parent-alias');
  await symlink(actualParent, parentAlias, 'dir');
  await assert.rejects(
    digestBuildTree(resolve(parentAlias, 'dist')),
    /no-follow plain directory/,
  );
});

test('build digest detects a deterministic file swap after descriptor open', async t => {
  const root = await realpath(await mkdtemp(resolve(tmpdir(), 'mrt-build-digest-swap-test-')));
  t.after(() => rm(root, {recursive: true, force: true}));
  const dist = resolve(root, 'dist');
  await mkdir(dist);
  const target = resolve(dist, 'app.js');
  const replacement = resolve(root, 'replacement.js');
  const retained = resolve(root, 'retained-original.js');
  await writeFile(target, 'original\n');
  await writeFile(replacement, 'replaced\n');
  let swaps = 0;
  await assert.rejects(
    digestBuildTree(dist, {
      async afterFileOpen({path}) {
        assert.equal(path, target);
        swaps += 1;
        await rename(target, retained);
        await rename(replacement, target);
      },
    }),
    /changed while the production build was being digested/,
  );
  assert.equal(swaps, 1);
});

test('build postflight identity and GTNH channel identity fail closed', () => {
  const build = {sha256: 'a'.repeat(64), files: 10, bytes: 20};
  assert.doesNotThrow(() => assertSameBuildTree(build, {...build}));
  assert.throws(() => assertSameBuildTree(build, {...build, bytes: 21}), /changed during cold-browser/);

  assert.doesNotThrow(() => assertProfileSlugBinding('gtnh-1.7.10', 'gt-new-horizons'));
  assert.doesNotThrow(() => assertProfileSlugBinding('generic-jei', 'example-pack'));
  assert.throws(
    () => assertProfileSlugBinding('gtnh-1.7.10', 'gtnh'),
    /requires benchmark slug gt-new-horizons/,
  );
  assert.throws(
    () => assertProfileSlugBinding('generic-jei', 'gt-new-horizons'),
    /requires manifest profile gtnh-1.7.10/,
  );
});

test('CLI rejects an implicit Chrome selection and unsupported options', () => {
  assert.throws(() => parseBenchmarkArguments([]), /Missing required option --slug/);
  assert.throws(
    () => parseBenchmarkArguments([
      '--slug', 'gt-new-horizons', '--dist', 'dist', '--export-root', 'export',
      '--publication', 'publication.json', '--preview-sidecar', 'preview',
      '--output', 'report.json',
    ]),
    /Missing required option --chrome/,
  );
  assert.throws(
    () => parseBenchmarkArguments(['--slug', 'gt-new-horizons', '--unknown', 'value']),
    /Unsupported option --unknown/,
  );
  assert.throws(
    () => parseBenchmarkArguments([
      '--slug', 'gt-new-horizons', '--dist', 'dist', '--export-root', 'export',
      '--publication', 'publication.json', '--preview-sidecar', 'preview',
      '--output', 'report.json', '--chrome', '/chrome',
      '--runs', String(MIN_ACTIVATION_BENCHMARK_RUNS - 1),
    ]),
    new RegExp(`--runs must be at least ${MIN_ACTIVATION_BENCHMARK_RUNS}`),
  );
});

test('activation thresholds use exact eligible/review boundaries and a hard peak ceiling', () => {
  const staticMetrics = {
    combinedDatasetBootstrapBytes: 72 * MIB,
    indexBootstrapBytes: 40 * MIB,
    bootstrapDocumentCount: 12,
  };
  const run = {
    readyMs: 8_000,
    peakHeapBytes: 400 * MIB,
    settledHeapBytes: 300 * MIB,
    proxyTraffic: {bootstrapDocumentCount: 12},
  };
  assert.equal(classifyActivationGate(staticMetrics, [run]).decision, 'current-storage-eligible');
  assert.equal(
    classifyActivationGate({
      ...staticMetrics,
      combinedDatasetBootstrapBytes:
        ACTIVATION_THRESHOLDS.eligible.combinedDatasetBootstrapBytes + 1,
    }, [run]).decision,
    'operator-review-required',
  );
  assert.equal(
    classifyActivationGate({...staticMetrics, bootstrapDocumentCount: 1}, [{
      ...run,
      proxyTraffic: {bootstrapDocumentCount: 13},
    }]).decision,
    'operator-review-required',
  );
  assert.equal(
    classifyActivationGate(staticMetrics, [{
      ...run,
      peakHeapBytes: ACTIVATION_THRESHOLDS.review.peakHeapBytes + 1,
    }]).decision,
    'lazy-index-required',
  );
});

test('bootstrap metrics count item shards but defer reverse-index shards', async () => {
  const root = await mkdtemp(resolve(tmpdir(), 'mrt-bootstrap-test-'));
  try {
    const bodies = new Map([
      ['manifest.json', Buffer.from(JSON.stringify({
        web: {recipeImages: {mode: 'omitted'}},
      }))],
      ['categories.json', Buffer.from('{}')],
      ['mobs.json', Buffer.from('{}')],
      ['blockdrops.json', Buffer.from('{}')],
      ['items.json', Buffer.from(JSON.stringify({
        format: 'mrt-sharded-json-v1',
        kind: 'array',
        count: 1,
        parts: [{path: 'items/part-000.json', start: 0, count: 1, bytes: 3}],
      }))],
      ['items/part-000.json', Buffer.from('[1]')],
      ['index.json', Buffer.from(JSON.stringify({
        format: 'mrt-sharded-json-v1',
        kind: 'object',
        count: 1,
        parts: [{path: 'index/part-000.json', count: 1, bytes: 7}],
      }))],
      ['index/part-000.json', Buffer.from('{"a":1}')],
    ]);
    const records = [];
    for (const [path, bytes] of bodies) {
      const localPath = resolve(root, ...path.split('/'));
      await mkdir(dirname(localPath), {recursive: true});
      await writeFile(localPath, bytes);
      records.push({path, bytes: bytes.length, localPath});
    }
    const metrics = await computeBootstrapMetrics(
      {records},
      {manifestBytes: Buffer.from('{}')},
    );
    assert.equal(metrics.itemsBootstrapBytes, bodies.get('items.json').length + 3);
    assert.equal(metrics.indexBootstrapBytes, bodies.get('index.json').length);
    assert.equal(
      metrics.combinedDatasetBootstrapBytes,
      bodies.get('items.json').length + 3 + bodies.get('index.json').length,
    );
    assert.equal(metrics.bootstrapDocumentCount, 9);
    assert.ok(!metrics.coreBootstrapPaths.includes('index/part-000.json'));
    assert.deepEqual(metrics.previewBootstrapPaths, ['manifest.json']);
    assert.equal(metrics.previewManifestBytes, 2);
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('structured-data-only bootstrap omits the unused preview manifest request', async () => {
  const root = await mkdtemp(resolve(tmpdir(), 'mrt-bootstrap-gtnh-test-'));
  try {
    const bodies = new Map([
      ['manifest.json', Buffer.from(JSON.stringify({
        publicationPolicy: 'gtnh-structured-data-only-v1',
        web: {recipeImages: {mode: 'omitted'}},
      }))],
      ['categories.json', Buffer.from('{}')],
      ['mobs.json', Buffer.from('{}')],
      ['blockdrops.json', Buffer.from('{}')],
      ['items.json', Buffer.from('[]')],
      ['index.json', Buffer.from('{}')],
    ]);
    const records = [];
    for (const [path, bytes] of bodies) {
      const localPath = resolve(root, ...path.split('/'));
      await mkdir(dirname(localPath), {recursive: true});
      await writeFile(localPath, bytes);
      records.push({path, bytes: bytes.length, localPath});
    }
    const metrics = await computeBootstrapMetrics(
      {
        records,
        manifest: {publicationPolicy: 'gtnh-structured-data-only-v1'},
      },
      {manifestBytes: Buffer.from('{"validated":true}')},
    );
    assert.equal(metrics.bootstrapDocumentCount, 7);
    assert.deepEqual(metrics.previewBootstrapPaths, []);
    assert.equal(metrics.previewManifestBytes, 0);

    const stats = {
      requests: 8,
      datasetRequests: 8,
      catalogRequests: 1,
      documentRequests: 8,
      imageRequests: 0,
      documentBodyBytes: 70,
      imageBodyBytes: 0,
      failures: [],
      served: new Map([
        ['catalog:/api/datasets', {kind: 'document', requests: 1, responseBodyBytes: 10}],
        ...metrics.coreBootstrapPaths.map(path => [
          `core:${path}`,
          {
            kind: 'document',
            requests: path === 'manifest.json' ? 2 : 1,
            responseBodyBytes: 10,
          },
        ]),
      ]),
    };
    const evidence = validateRunTraffic(stats, metrics);
    assert.equal(evidence.bootstrapDocumentCount, 7);
    assert.equal(evidence.servedObjects.some(entry => entry.key.startsWith('preview:')), false);
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('traffic validation requires every bootstrap object and manifest confirmation', () => {
  const metrics = {
    coreBootstrapPaths: ['manifest.json', 'items.json'],
    previewBootstrapPaths: ['manifest.json'],
  };
  const stats = {
    requests: 5,
    datasetRequests: 5,
    catalogRequests: 1,
    documentRequests: 5,
    imageRequests: 0,
    documentBodyBytes: 100,
    imageBodyBytes: 0,
    failures: [],
    served: new Map([
      ['catalog:/api/datasets', {kind: 'document', requests: 1, responseBodyBytes: 20}],
      ['core:manifest.json', {kind: 'document', requests: 2, responseBodyBytes: 40}],
      ['core:items.json', {kind: 'document', requests: 1, responseBodyBytes: 30}],
      ['preview:manifest.json', {kind: 'document', requests: 1, responseBodyBytes: 10}],
    ]),
  };
  const evidence = validateRunTraffic(stats, metrics);
  assert.equal(evidence.totalDatasetBodyBytes, 100);
  assert.equal(evidence.bootstrapDocumentCount, 4);
  assert.deepEqual(
    evidence.servedObjects.map(entry => entry.key),
    [
      'catalog:/api/datasets',
      'core:items.json',
      'core:manifest.json',
      'preview:manifest.json',
    ],
  );
  stats.served.delete('core:items.json');
  assert.throws(() => validateRunTraffic(stats, metrics), /omitted required bootstrap request/);
  stats.served.set('core:items.json', {kind: 'document', requests: 1, responseBodyBytes: 30});
  stats.served.set('core:manifest.json', {kind: 'document', requests: 1, responseBodyBytes: 40});
  assert.throws(() => validateRunTraffic(stats, metrics), /manifest confirmation/);
  stats.served.set('core:manifest.json', {kind: 'document', requests: 2, responseBodyBytes: 40});
  stats.served.set('core:recipes/part-000.json', {
    kind: 'document',
    requests: 1,
    responseBodyBytes: 20,
  });
  assert.throws(() => validateRunTraffic(stats, metrics), /unexpected bootstrap dataset document/);
  stats.served.delete('core:recipes/part-000.json');
  stats.served.set('core:assets/s/000-0-12.webp', {
    kind: 'image',
    requests: 1,
    responseBodyBytes: 12,
  });
  assert.equal(
    validateRunTraffic(stats, metrics).servedObjects.find(
      entry => entry.key === 'core:assets/s/000-0-12.webp',
    ).kind,
    'image',
  );
});
