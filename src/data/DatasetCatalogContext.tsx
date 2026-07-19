import React, {createContext, useCallback, useContext, useEffect, useMemo, useState} from 'react';
import {Platform} from 'react-native';
import {
  type DatasetDescriptor,
  type DatasetSource,
  datasetSource,
  readRequestedDatasetSlug,
  requireDatasetCatalog,
  searchWithDatasetSlug,
  selectDataset,
} from './datasetCatalog';

const PRODUCTION_ORIGIN = 'https://minecraftrecipetree.craftsmannsoftware.com';

interface DatasetRequestConfiguration {
  catalogUrl: string;
  assetOrigin: string;
}

export type DatasetCatalogState =
  | {status: 'loading'}
  | {status: 'error'; message: string; datasets: readonly DatasetDescriptor[]}
  | {
      status: 'ready';
      datasets: readonly DatasetDescriptor[];
      selected: DatasetDescriptor;
      source: DatasetSource;
    };

interface DatasetCatalogContextValue {
  state: DatasetCatalogState;
  select(slug: string): void;
}

function absoluteHttpUrl(value: string, label: string): URL {
  let url: URL;
  try {
    url = new URL(value);
  } catch {
    throw new Error(`${label} must be an absolute HTTP(S) URL.`);
  }
  if (
    (url.protocol !== 'http:' && url.protocol !== 'https:') ||
    url.username !== '' ||
    url.password !== ''
  ) {
    throw new Error(`${label} must be an absolute HTTP(S) URL without credentials.`);
  }
  return url;
}

function requestConfiguration(): DatasetRequestConfiguration {
  const configuredCatalog = process.env.EXPO_PUBLIC_DATASET_CATALOG_URL;
  const configuredOrigin = process.env.EXPO_PUBLIC_DATASET_ORIGIN;

  if (Platform.OS === 'web') {
    if (!configuredCatalog) {
      if (configuredOrigin) {
        throw new Error(
          'EXPO_PUBLIC_DATASET_ORIGIN cannot be set without EXPO_PUBLIC_DATASET_CATALOG_URL on web.',
        );
      }
      return {catalogUrl: '/api/datasets', assetOrigin: ''};
    }
    if (configuredCatalog.startsWith('/')) {
      if (configuredCatalog.startsWith('//')) {
        throw new Error('EXPO_PUBLIC_DATASET_CATALOG_URL cannot be protocol-relative.');
      }
      return {catalogUrl: configuredCatalog, assetOrigin: configuredOrigin ?? ''};
    }
    const catalog = absoluteHttpUrl(configuredCatalog, 'EXPO_PUBLIC_DATASET_CATALOG_URL');
    return {
      catalogUrl: catalog.href,
      assetOrigin: configuredOrigin ?? catalog.origin,
    };
  }

  // Native clients have no same-origin Worker. They use the public catalog by default, while
  // development deployments can point both catalog and immutable asset routes at another host.
  const catalog = absoluteHttpUrl(
    configuredCatalog ?? `${PRODUCTION_ORIGIN}/api/datasets`,
    'EXPO_PUBLIC_DATASET_CATALOG_URL',
  );
  return {
    catalogUrl: catalog.href,
    assetOrigin: configuredOrigin ?? catalog.origin,
  };
}

function currentWebRequestSlug(): string | null {
  if (Platform.OS !== 'web') return null;
  if (typeof window === 'undefined') {
    throw new Error('The web dataset catalog cannot read the pack query before window is available.');
  }
  return readRequestedDatasetSlug(window.location.search);
}

function writeWebRequestSlug(slug: string): void {
  if (Platform.OS !== 'web') return;
  if (typeof window === 'undefined') {
    throw new Error('The web dataset selector cannot update the pack query without window.');
  }
  const nextSearch = searchWithDatasetSlug(window.location.search, slug);
  const nextUrl = `${window.location.pathname}${nextSearch}${window.location.hash}`;
  window.history.pushState(window.history.state, '', nextUrl);
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

const DatasetCatalogContext = createContext<DatasetCatalogContextValue | null>(null);

export function DatasetCatalogProvider({children}: {children: React.ReactNode}) {
  const [datasets, setDatasets] = useState<readonly DatasetDescriptor[]>([]);
  const [selected, setSelected] = useState<DatasetDescriptor | null>(null);
  const [assetOrigin, setAssetOrigin] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    let alive = true;
    (async () => {
      try {
        const configuration = requestConfiguration();
        const response = await fetch(configuration.catalogUrl, {
          cache: 'no-store',
          signal: controller.signal,
        });
        if (!response.ok) {
          throw new Error(
            `Dataset catalog request failed with HTTP ${response.status} at ${configuration.catalogUrl}.`,
          );
        }
        let value: unknown;
        try {
          value = await response.json();
        } catch (cause) {
          throw new Error(`Dataset catalog returned invalid JSON: ${errorMessage(cause)}`);
        }
        const nextDatasets = requireDatasetCatalog(value);
        const defaultDataset = selectDataset(nextDatasets, null);
        datasetSource(defaultDataset, configuration.assetOrigin);
        if (!alive) return;
        // Retain the validated catalog when only the requested ?pack value is invalid. The error
        // boundary can then offer explicit, user-selected recovery instead of choosing a fallback.
        setDatasets(nextDatasets);
        setAssetOrigin(configuration.assetOrigin);
        const nextSelected = selectDataset(nextDatasets, currentWebRequestSlug());
        datasetSource(nextSelected, configuration.assetOrigin);
        if (!alive) return;
        setSelected(nextSelected);
        setError(null);
        setLoading(false);
      } catch (cause) {
        if (!alive || controller.signal.aborted) return;
        const message = errorMessage(cause);
        console.error('Dataset catalog initialization failed.', cause);
        setError(message);
        setLoading(false);
      }
    })();
    return () => {
      alive = false;
      controller.abort();
    };
  }, []);

  const select = useCallback(
    (slug: string) => {
      try {
        if (datasets.length === 0) {
          throw new Error('Dataset selection was requested before a valid catalog was loaded.');
        }
        const nextSelected = selectDataset(datasets, slug);
        datasetSource(nextSelected, assetOrigin);
        writeWebRequestSlug(nextSelected.slug);
        setSelected(nextSelected);
        setError(null);
      } catch (cause) {
        const message = errorMessage(cause);
        console.error('Dataset selection failed.', {slug, cause});
        setSelected(null);
        setError(message);
      }
    },
    [assetOrigin, datasets],
  );

  useEffect(() => {
    if (Platform.OS !== 'web' || datasets.length === 0) return;
    const handlePopState = () => {
      try {
        const nextSelected = selectDataset(datasets, currentWebRequestSlug());
        datasetSource(nextSelected, assetOrigin);
        setSelected(nextSelected);
        setError(null);
      } catch (cause) {
        const message = errorMessage(cause);
        console.error('Browser history selected an invalid dataset.', cause);
        setSelected(null);
        setError(message);
      }
    };
    window.addEventListener('popstate', handlePopState);
    return () => window.removeEventListener('popstate', handlePopState);
  }, [assetOrigin, datasets]);

  const state = useMemo<DatasetCatalogState>(() => {
    if (loading) return {status: 'loading'};
    if (error || !selected) {
      return {
        status: 'error',
        message: error ?? 'Dataset selection is unavailable.',
        datasets,
      };
    }
    return {
      status: 'ready',
      datasets,
      selected,
      source: datasetSource(selected, assetOrigin),
    };
  }, [assetOrigin, datasets, error, loading, selected]);

  const value = useMemo<DatasetCatalogContextValue>(() => ({state, select}), [select, state]);
  return <DatasetCatalogContext.Provider value={value}>{children}</DatasetCatalogContext.Provider>;
}

export function useDatasetCatalog(): DatasetCatalogContextValue {
  const value = useContext(DatasetCatalogContext);
  if (!value) throw new Error('useDatasetCatalog must be rendered inside DatasetCatalogProvider.');
  return value;
}
