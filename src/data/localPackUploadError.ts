function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

export function localPackUploadErrorMessage(error: unknown): string {
  const message = errorMessage(error);
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
  if (message.includes('manifest.json') || message.includes('exporter information')) {
    return 'We could not find the pack information in this ZIP. Run the exporter again and choose the new ZIP.';
  }
  if (
    message.includes('too many files') ||
    message.includes('too large') ||
    message.includes('browser storage')
  ) {
    return message;
  }
  if (message.includes('not a readable ZIP') || message.includes('could not be opened')) {
    return 'This file is not a readable ZIP. Choose the ZIP made by the exporter.';
  }
  if (message.startsWith('The ZIP is missing ')) return message;
  console.error('The exporter ZIP could not be prepared for the viewer.', error);
  return 'We could not read this export. Run the exporter again and try the new ZIP.';
}
