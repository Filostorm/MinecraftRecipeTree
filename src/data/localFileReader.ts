const LOCAL_FILE_READ_TIMEOUT_MS = 30_000;

export type LocalFileReadProgress = (loadedBytes: number) => void;

export interface LocalFileSliceSource {
  readonly name: string;
  slice(start: number, end: number): Blob;
}

function readTimeoutMessage(filename: string): string {
  return `The browser could not read ${filename} from this device. Move the ZIP to a local folder and try again.`;
}

/**
 * Reads a bounded slice from a user-selected file without relying exclusively
 * on a single Blob.arrayBuffer() promise. FileReader supplies progress events
 * and an abortable timeout when a browser file source does not settle promptly.
 */
export async function readLocalFileSlice(
  file: LocalFileSliceSource,
  start: number,
  end: number,
  onProgress?: LocalFileReadProgress,
): Promise<Uint8Array> {
  const slice = file.slice(start, end);
  if (typeof FileReader === 'undefined') {
    const bytes = new Uint8Array(await slice.arrayBuffer());
    onProgress?.(bytes.byteLength);
    return bytes;
  }

  return await new Promise<Uint8Array>((resolve, reject) => {
    const reader = new FileReader();
    let settled = false;
    let timedOut = false;
    const finish = (callback: () => void) => {
      if (settled) return;
      settled = true;
      globalThis.clearTimeout(timeout);
      callback();
    };
    const timeout = globalThis.setTimeout(() => {
      timedOut = true;
      reader.abort();
      finish(() => reject(new Error(readTimeoutMessage(file.name))));
    }, LOCAL_FILE_READ_TIMEOUT_MS);

    reader.onprogress = event => {
      if (!settled) onProgress?.(Math.min(slice.size, event.loaded));
    };
    reader.onload = () => {
      finish(() => {
        if (!(reader.result instanceof ArrayBuffer)) {
          reject(new Error(readTimeoutMessage(file.name)));
          return;
        }
        const bytes = new Uint8Array(reader.result);
        onProgress?.(bytes.byteLength);
        resolve(bytes);
      });
    };
    reader.onerror = () => {
      finish(() => reject(new Error(readTimeoutMessage(file.name))));
    };
    reader.onabort = () => {
      if (timedOut) return;
      finish(() => reject(new Error(readTimeoutMessage(file.name))));
    };

    try {
      reader.readAsArrayBuffer(slice);
    } catch {
      finish(() => reject(new Error(readTimeoutMessage(file.name))));
    }
  });
}
