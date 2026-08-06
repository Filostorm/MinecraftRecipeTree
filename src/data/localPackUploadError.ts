function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

function safeErrorDetail(error: unknown): string {
  const detail = errorMessage(error)
    .replace(/[\u0000-\u001f\u007f-\u009f]+/gu, ' ')
    .replace(/\s+/gu, ' ')
    .trim();
  if (detail.length === 0) return 'The importer did not provide an error reason.';
  return detail.length <= 600 ? detail : `${detail.slice(0, 597)}…`;
}

export function localPackUploadErrorMessage(error: unknown): string {
  const message = safeErrorDetail(error);
  if (message.startsWith('The browser could not read ')) return message;
  if (message.includes('empty')) {
    return 'This ZIP is empty. Run the exporter again and choose the new ZIP.';
  }
  if (message.startsWith('The ZIP entry ') && message.includes('cannot be opened safely:')) {
    return message;
  }
  if (
    message.startsWith('Install the full ') ||
    message.startsWith('Re-add the full ') ||
    message.startsWith('The update ZIP') ||
    message.startsWith('The installed full export') ||
    message.startsWith('delta.json')
  ) {
    return message;
  }
  if (message.startsWith('No exporter manifest.json was found')) {
    return 'This ZIP does not contain exporter manifest.json at its root or inside one top-level folder.';
  }
  if (message.includes('manifest.json')) {
    return `The exporter manifest is invalid: ${message}`;
  }
  if (message.includes('exporter information')) {
    return `The exporter information could not be read: ${message}`;
  }
  if (
    message.includes('too many files') ||
    message.includes('too large') ||
    message.includes('browser storage')
  ) {
    return message;
  }
  if (message.includes('not a readable ZIP') || message.includes('could not be opened')) {
    return `This file is not a readable ZIP: ${message}`;
  }
  if (message.startsWith('The ZIP is missing ')) return message;
  console.error('The exporter ZIP could not be prepared for the viewer.', error);
  return `The pack could not be added: ${message}`;
}
