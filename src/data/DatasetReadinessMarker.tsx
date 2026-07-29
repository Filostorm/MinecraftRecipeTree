import {useEffect} from 'react';
import {Platform} from 'react-native';
import {useLoadState} from './DataContext';

export const DATASET_READY_MARK = 'mrt:dataset-ready';
export const DATASET_STATE_ATTRIBUTE = 'data-mrt-dataset-state';
export const DATASET_PUBLICATION_ATTRIBUTE = 'data-mrt-dataset-publication-id';

/**
 * Exposes a benchmark-only readiness contract after React has committed the loaded dataset.
 * This is deliberately an effect rather than render-time mutation: automation cannot observe a
 * publication as ready until the corresponding UI tree has committed.
 */
export function DatasetReadinessMarker({
  expectedPublicationId,
}: {
  expectedPublicationId: string;
}) {
  const state = useLoadState();

  useEffect(() => {
    if (Platform.OS !== 'web') return;
    if (typeof document === 'undefined' || typeof performance === 'undefined') {
      console.error('Dataset readiness instrumentation requires browser document/performance APIs.');
      return;
    }

    const root = document.documentElement;
    performance.clearMarks(DATASET_READY_MARK);
    root.removeAttribute(DATASET_PUBLICATION_ATTRIBUTE);
    let markerState: string;
    const cleanup = () => {
      performance.clearMarks(DATASET_READY_MARK);
      if (root.getAttribute(DATASET_PUBLICATION_ATTRIBUTE) === expectedPublicationId) {
        root.removeAttribute(DATASET_PUBLICATION_ATTRIBUTE);
      }
      if (root.getAttribute(DATASET_STATE_ATTRIBUTE) === markerState) {
        root.removeAttribute(DATASET_STATE_ATTRIBUTE);
      }
    };

    if (state.status === 'loading') {
      markerState = 'loading';
      root.setAttribute(DATASET_STATE_ATTRIBUTE, markerState);
      return cleanup;
    }
    if (state.status === 'error') {
      markerState = 'error';
      root.setAttribute(DATASET_STATE_ATTRIBUTE, markerState);
      return cleanup;
    }

    const loadedPublicationId = state.data.manifest.publicationId;
    if (
      loadedPublicationId !== expectedPublicationId ||
      state.data.datasetIdentity !== expectedPublicationId ||
      state.data.descriptor.publicationId !== expectedPublicationId
    ) {
      markerState = 'identity-error';
      root.setAttribute(DATASET_STATE_ATTRIBUTE, markerState);
      console.error('Dataset readiness instrumentation rejected a publication identity mismatch.', {
        expectedPublicationId,
        loadedPublicationId,
        datasetIdentity: state.data.datasetIdentity,
        descriptorPublicationId: state.data.descriptor.publicationId,
      });
      return cleanup;
    }

    root.setAttribute(DATASET_PUBLICATION_ATTRIBUTE, expectedPublicationId);
    markerState = 'ready';
    root.setAttribute(DATASET_STATE_ATTRIBUTE, markerState);
    performance.mark(DATASET_READY_MARK, {
      detail: {publicationId: expectedPublicationId},
    });
    return cleanup;
  }, [expectedPublicationId, state]);

  return null;
}
