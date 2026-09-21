import type {DatasetDescriptor} from '../data/datasetCatalog';

/** Full offline pack downloads are currently a native feature. */
export function PackDownloadPrompt(_props: {dataset: DatasetDescriptor | null; onComplete(): void}) {return null;}
