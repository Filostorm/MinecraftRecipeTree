import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
import vm from 'node:vm';
import test from 'node:test';
import ts from 'typescript';

function nativeRuntime(relativePath, {storage, failWrite = () => false} = {}) {
  const files = new Map();
  const logs = [];
  const uri = parts => parts.map(part => typeof part === 'string' ? part : part.uri).join('/');
  class Directory {
    constructor(...parts) { this.uri = uri(parts); }
    get exists() { return [...files.keys()].some(key => key.startsWith(`${this.uri}/`)); }
    create() {}
    list() { return [...files.keys()].filter(key => key.startsWith(`${this.uri}/`)).map(key => new File(key)); }
  }
  class File {
    constructor(...parts) { this.uri = uri(parts); this.name = this.uri.split('/').at(-1); }
    get exists() { return files.has(this.uri); }
    create() { if (!this.exists) files.set(this.uri, ''); }
    write(text) { if (failWrite(this.uri)) throw new Error('disk failure'); files.set(this.uri, text); }
    textSync() { return files.get(this.uri); }
    async text() { await new Promise(resolve => setTimeout(resolve, 1)); return this.textSync(); }
    delete() { files.delete(this.uri); }
  }
  const exports = {};
  const context = {exports, TextEncoder, Uint8Array, Date, setTimeout, localStorage: storage,
    console: {error: (...args) => logs.push(args), warn: (...args) => logs.push(args), info: (...args) => logs.push(args)},
    require(name) {
      if (name === 'expo-file-system') return {Directory, File, Paths: {document: 'document', cache: 'cache'}};
      if (name === 'expo-crypto') return {CryptoDigestAlgorithm: {SHA256: 'sha256'}, digest: async (_, bytes) => createHash('sha256').update(bytes).digest()};
      throw new Error(`Unexpected module ${name}`);
    },
  };
  const source = readFileSync(new URL(relativePath, import.meta.url), 'utf8');
  const {outputText} = ts.transpileModule(source, {compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022}});
  vm.runInNewContext(outputText, context);
  return {api: exports, files, logs};
}

test('legacy migration retains the original after a failed write and retries without overwriting newer keys', () => {
  const entries = new Map([['newer', 'current']]);
  let fail = true;
  const runtime = nativeRuntime('../ui/nativeLocalStorage.native.ts', {storage: {
    getItem: key => entries.get(key) ?? null,
    setItem: (key, value) => { if (key === 'second' && fail) throw new Error('full'); entries.set(key, value); },
  }});
  const original = 'document/minecraft-recipe-tree/local-storage.json';
  runtime.files.set(original, JSON.stringify({first: 'one', second: 'two', newer: 'old'}));
  runtime.api.migrateLegacyNativeLocalStorage();
  assert.equal(runtime.files.has(original), true);
  assert.ok(runtime.logs.length > 0);
  fail = false;
  runtime.api.migrateLegacyNativeLocalStorage();
  assert.equal(runtime.files.has(original), false);
  assert.equal(entries.get('newer'), 'current');
  assert.equal(entries.get('second'), 'two');
});

test('unreadable legacy storage is preserved rather than deleted', () => {
  const runtime = nativeRuntime('../ui/nativeLocalStorage.native.ts', {storage: {getItem: () => null}});
  const original = 'document/minecraft-recipe-tree/local-storage.json';
  runtime.files.set(original, '{broken');
  runtime.api.migrateLegacyNativeLocalStorage();
  assert.equal(runtime.files.get(original), '{broken');
});

test('concurrent document writes retain every index entry and remain readable', async () => {
  const {api, files} = nativeRuntime('./publishedDatasetCache.native.ts');
  await Promise.all(Array.from({length: 30}, (_, index) => api.writeCachedPublishedDocument(`https://test/${index}`, `document-${index}`)));
  const index = JSON.parse(files.get('cache/minecraft-recipe-tree/published-dataset-cache/index.json'));
  assert.equal(Object.keys(index.entries).length, 30);
  assert.equal(files.size, 31);
  for (let n = 0; n < 30; n++) assert.equal((await api.readCachedPublishedDocument(`https://test/${n}`)).text, `document-${n}`);
});

test('failed index commits do not poison the queue or leave permanent untracked documents', async () => {
  let fail = true;
  const {api, files, logs} = nativeRuntime('./publishedDatasetCache.native.ts', {failWrite: path => fail && path.endsWith('index.json')});
  await api.writeCachedPublishedDocument('https://test/fail', 'lost');
  fail = false;
  await api.writeCachedPublishedDocument('https://test/good', 'saved');
  assert.equal(files.size, 2);
  assert.equal((await api.readCachedPublishedDocument('https://test/good')).text, 'saved');
  assert.ok(logs.length > 0);
});
