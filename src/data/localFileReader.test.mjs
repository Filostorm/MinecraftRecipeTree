import assert from 'node:assert/strict';
import {File} from 'node:buffer';
import test from 'node:test';

const {readLocalFileSlice} = await import('./localFileReader.ts');

test('reads a bounded local file slice without FileReader', async () => {
  const file = new File([new Uint8Array([1, 2, 3, 4, 5])], 'pack.zip');
  const progress = [];
  const bytes = await readLocalFileSlice(file, 1, 4, loaded => progress.push(loaded));
  assert.deepEqual([...bytes], [2, 3, 4]);
  assert.deepEqual(progress, [3]);
});

test('uses FileReader progress events when the browser provides it', async () => {
  const originalFileReader = Object.getOwnPropertyDescriptor(globalThis, 'FileReader');
  class TestFileReader {
    result = null;
    onabort = null;
    onerror = null;
    onload = null;
    onprogress = null;

    abort() {
      this.onabort?.();
    }

    async readAsArrayBuffer(blob) {
      this.onprogress?.({loaded: 2});
      this.result = await blob.arrayBuffer();
      this.onload?.();
    }
  }
  Object.defineProperty(globalThis, 'FileReader', {
    configurable: true,
    value: TestFileReader,
  });
  try {
    const file = new File([new Uint8Array([5, 6, 7, 8])], 'pack.zip');
    const progress = [];
    const bytes = await readLocalFileSlice(file, 0, file.size, loaded => progress.push(loaded));
    assert.deepEqual([...bytes], [5, 6, 7, 8]);
    assert.deepEqual(progress, [2, 4]);
  } finally {
    if (originalFileReader) {
      Object.defineProperty(globalThis, 'FileReader', originalFileReader);
    } else {
      delete globalThis.FileReader;
    }
  }
});
