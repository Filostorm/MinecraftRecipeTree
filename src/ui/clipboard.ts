export async function copyText(value: string): Promise<void> {
  if (!globalThis.navigator?.clipboard) {
    throw new Error('Clipboard access is unavailable.');
  }
  await globalThis.navigator.clipboard.writeText(value);
}
