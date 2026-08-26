export type GraphRenderErrorKind =
  | 'bundle'
  | 'assets'
  | 'data'
  | 'layout'
  | 'resources'
  | 'unknown';

export interface GraphRenderRecovery {
  kind: GraphRenderErrorKind;
  title: string;
  message: string;
  detail?: string;
  reloadPage?: boolean;
}

function searchableError(error: unknown): string {
  if (error instanceof Error) return `${error.name} ${error.message}`.toLowerCase();
  return String(error).toLowerCase();
}

function diagnosticDetail(error: unknown): string {
  const raw = error instanceof Error ? `${error.name}: ${error.message}` : String(error);
  return raw.replace(/\s+/gu, ' ').trim().slice(0, 240) || 'Unknown rendering error';
}

export function graphRenderRecovery(error: unknown): GraphRenderRecovery {
  const detail = searchableError(error);
  if (
    /dynamically imported module|failed to fetch.*module|chunkloaderror|loading chunk|importing a module script failed/u.test(
      detail,
    )
  ) {
    return {
      kind: 'bundle',
      title: 'Recipe Tree was updated',
      message:
        'This tab still has an older part of the site open. Reload to use the current Recipe Tree version; your saved trees will stay available.',
      detail: diagnosticDetail(error),
      reloadPage: true,
    };
  }
  if (
    error instanceof RangeError ||
    /out of memory|allocation|maximum call stack|too many|exceeds .*limit|safe integer|canvas.*(?:size|dimension|context)/u.test(
      detail,
    )
  ) {
    return {
      kind: 'resources',
      title: 'This tree exceeded the browser’s drawing limit',
      message:
        'The tree is still saved. Close other large tabs or return to Browse and open a smaller output before trying again.',
    };
  }
  if (
    /itemicon|pixel grid|icon|sprite|recipe preview|image data|image dimensions/u.test(detail)
  ) {
    return {
      kind: 'assets',
      title: 'An item preview could not be drawn',
      message:
        'One of this tree’s item, recipe, or mob images could not be rendered. The rest of the modpack is still available.',
    };
  }
  if (
    /layout|radial|contour|apportionment|graph fit|non-finite bounds|edge endpoint|node diameter|angular/u.test(
      detail,
    )
  ) {
    return {
      kind: 'layout',
      title: 'This tree’s layout could not be calculated',
      message:
        'Recipe Tree could not place one or more branches. Try again, or return to Browse and open a smaller branch.',
    };
  }
  if (
    /saved graph|graph selection|recipe reference|ingredient selection|source contract|starting item|item key|unavailable in .*pack|could not be reconstructed/u.test(
      detail,
    )
  ) {
    return {
      kind: 'data',
      title: 'This tree no longer matches the modpack',
      message:
        'One or more saved items, recipes, or sources are unavailable in the loaded pack version. Return to Browse and rebuild this branch.',
    };
  }
  return {
    kind: 'unknown',
    title: 'Recipe tree couldn’t open',
    message:
      'Something went wrong while drawing this tree. Your modpack and saved trees are still available.',
    detail: diagnosticDetail(error),
  };
}
