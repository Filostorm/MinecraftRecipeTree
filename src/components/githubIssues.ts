export type GitHubIssueKind = 'bug' | 'feedback';

export const GITHUB_REPOSITORY = 'Filostorm/MinecraftRecipeTree';

export interface IssueReportContext {
  packSlug: string;
  packName: string;
  packVersion: string;
  minecraftVersion: string;
  publicationId: string;
  previewAssetSetId: string;
  exportGeneratedAt: string;
  exportFormat: number;
  itemCount: number;
  recipeCount: number;
  categoryCount: number;
  modCount: number;
  activeTab: string;
  openItemKey: string;
  graphRootKey: string;
  graphDirection: string;
  interfaceZoomPercent: number;
  contentZoomPercent: number;
}

export interface IssueReportRuntime {
  page: string;
  platform: string;
  userAgent: string;
  viewport: string;
  language: string;
  online: string;
}

export interface IssueReportPayload {
  kind: GitHubIssueKind;
  title: string;
  message: string;
  contact: string;
  packSlug: string;
  packName: string;
  page: string;
  website: string;
  diagnostics: {
    packVersion: string;
    minecraftVersion: string;
    publicationId: string;
    previewAssetSetId: string;
    exportGeneratedAt: string;
    exportFormat: string;
    itemCount: string;
    recipeCount: string;
    categoryCount: string;
    modCount: string;
    activeTab: string;
    openItemKey: string;
    graphRootKey: string;
    graphDirection: string;
    interfaceZoom: string;
    contentZoom: string;
    platform: string;
    userAgent: string;
    viewport: string;
    language: string;
    online: string;
  };
}

export function buildIssueReportPayload(
  kind: GitHubIssueKind,
  title: string,
  message: string,
  context: IssueReportContext,
  runtime: IssueReportRuntime,
): IssueReportPayload {
  return {
    kind,
    title,
    message,
    contact: '',
    packSlug: context.packSlug,
    packName: context.packName,
    page: runtime.page,
    website: '',
    diagnostics: {
      packVersion: context.packVersion,
      minecraftVersion: context.minecraftVersion,
      publicationId: context.publicationId,
      previewAssetSetId: context.previewAssetSetId,
      exportGeneratedAt: context.exportGeneratedAt,
      exportFormat: String(context.exportFormat),
      itemCount: String(context.itemCount),
      recipeCount: String(context.recipeCount),
      categoryCount: String(context.categoryCount),
      modCount: String(context.modCount),
      activeTab: context.activeTab,
      openItemKey: context.openItemKey,
      graphRootKey: context.graphRootKey,
      graphDirection: context.graphDirection,
      interfaceZoom: `${context.interfaceZoomPercent}%`,
      contentZoom: `${context.contentZoomPercent}%`,
      platform: runtime.platform,
      userAgent: runtime.userAgent,
      viewport: runtime.viewport,
      language: runtime.language,
      online: runtime.online,
    },
  };
}
