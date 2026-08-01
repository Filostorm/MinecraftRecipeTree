import {Platform, Share} from 'react-native';
import {downloadBlob} from './treeExports.ts';

type DirectoryPickerOptions = {
  id?: string;
  mode?: 'read' | 'readwrite';
  startIn?: 'desktop' | 'documents' | 'downloads';
};

type WritableFileHandle = {
  createWritable(): Promise<{
    write(data: string): Promise<void>;
    close(): Promise<void>;
  }>;
};

type WritableDirectoryHandle = {
  name: string;
  getDirectoryHandle(name: string, options: {create: true}): Promise<WritableDirectoryHandle>;
  getFileHandle(name: string, options: {create: true}): Promise<WritableFileHandle>;
};

type DirectoryPickerWindow = Window & {
  showDirectoryPicker?: (options?: DirectoryPickerOptions) => Promise<WritableDirectoryHandle>;
};

export async function sharePortableTree(
  filename: string,
  json: string,
): Promise<string> {
  if (Platform.OS !== 'web') {
    const result = await Share.share({
      title: filename,
      message: json,
    });
    return result.action === Share.dismissedAction ? 'Share cancelled.' : 'Tree shared.';
  }

  const file = new File([json], filename, {type: 'application/json'});
  const webNavigator = navigator as Navigator & {
    canShare?: (data: ShareData) => boolean;
  };
  if (webNavigator.share && webNavigator.canShare?.({files: [file]})) {
    try {
      await webNavigator.share({
        title: 'Minecraft recipe tree',
        text: 'Open this tree in Minecraft Recipe Tree.',
        files: [file],
      });
      return 'Tree shared.';
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') return 'Share cancelled.';
      console.warn('Native web sharing failed; downloading the tree instead.', error);
    }
  }

  downloadBlob(filename, file);
  try {
    await navigator.clipboard?.writeText(json);
    return 'Tree downloaded and copied to the clipboard.';
  } catch (error) {
    console.warn('The downloaded tree could not also be copied to the clipboard.', error);
    return 'Tree downloaded.';
  }
}

export function pickPortableTreeFile(): Promise<string | null> {
  if (Platform.OS !== 'web' || typeof document === 'undefined') {
    return Promise.resolve(null);
  }
  return new Promise((resolve, reject) => {
    let settled = false;
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = '.mrtree.json,application/json';
    input.style.display = 'none';
    const finish = (value: string | null) => {
      if (settled) return;
      settled = true;
      input.remove();
      globalThis.removeEventListener('focus', onWindowFocus);
      resolve(value);
    };
    const fail = (error: unknown) => {
      if (settled) return;
      settled = true;
      input.remove();
      globalThis.removeEventListener('focus', onWindowFocus);
      reject(error);
    };
    const onWindowFocus = () => {
      globalThis.setTimeout(() => {
        if (!settled && !input.files?.length) finish(null);
      }, 400);
    };
    input.addEventListener('change', () => {
      const file = input.files?.[0];
      if (!file) {
        finish(null);
        return;
      }
      if (file.size > 1_048_576) {
        fail(new Error('The selected tree is larger than the 1 MiB import limit.'));
        return;
      }
      file.text().then(finish, fail);
    });
    input.addEventListener('cancel', () => finish(null));
    globalThis.addEventListener('focus', onWindowFocus);
    document.body.appendChild(input);
    input.click();
  });
}

/** Writes directly into the folder watched by the in-game import button. */
export async function savePortableTreeToInstance(
  filename: string,
  json: string,
): Promise<string> {
  if (Platform.OS !== 'web' || typeof window === 'undefined') {
    throw new Error('Saving into a Minecraft instance is available on desktop web.');
  }
  const pickerWindow = window as DirectoryPickerWindow;
  if (!pickerWindow.showDirectoryPicker) {
    downloadBlob(filename, new Blob([json], {type: 'application/json'}));
    return 'Folder access is unavailable in this browser, so the tree was downloaded instead.';
  }

  let instance: WritableDirectoryHandle;
  try {
    instance = await pickerWindow.showDirectoryPicker({
      id: 'minecraft-recipe-tree-instance',
      mode: 'readwrite',
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') return 'Folder selection cancelled.';
    throw error;
  }
  const config = await instance.getDirectoryHandle('config', {create: true});
  const shares = await config.getDirectoryHandle('recipe-tree-shares', {create: true});
  const file = await shares.getFileHandle(filename, {create: true});
  const writable = await file.createWritable();
  try {
    await writable.write(json);
  } finally {
    await writable.close();
  }
  return `Saved to ${instance.name}/config/recipe-tree-shares/${filename}. Open the mod and choose Import file.`;
}
