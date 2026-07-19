import type {CatalogItem} from '../types';

export const ITEM_ICON_FAILURE_SAMPLE_LIMIT = 8;
export const ABSENT_ITEM_ICON_SAMPLE_LIMIT = 8;

const MAX_DIAGNOSTIC_URI_LENGTH = 512;
const MAX_DIAGNOSTIC_TEXT_LENGTH = 192;

export interface ItemIconLoadFailure {
  uri: string;
  itemKey?: string;
  label: string;
  detail: unknown;
}

export interface ItemIconFailureSample {
  uri: string;
  itemKey?: string;
  label: string;
  detail: string;
}

export interface ItemIconFailureSummary {
  datasetIdentity: string;
  cumulativeFailures: number;
  sampledFailures: readonly ItemIconFailureSample[];
  sampleLimit: number;
  failureEventsOmitted: number;
}

export interface AbsentItemIconSummary {
  catalogItems: number;
  itemsWithoutIconUrl: number;
  sampleKeys: readonly string[];
  sampleLimit: number;
  sampleKeysOmitted: number;
}

type FailureLogger = (message: string, summary: ItemIconFailureSummary) => void;

function boundedText(value: string, maximumLength: number): string {
  if (value.length <= maximumLength) return value;
  return `${value.slice(0, maximumLength - 1)}…`;
}

function failureDetail(value: unknown): string {
  if (typeof value === 'string' && value.trim()) {
    return boundedText(value.trim(), MAX_DIAGNOSTIC_TEXT_LENGTH);
  }
  if (value instanceof Error && value.message.trim()) {
    return boundedText(value.message.trim(), MAX_DIAGNOSTIC_TEXT_LENGTH);
  }
  return 'The platform did not provide an HTTP or decoder diagnostic.';
}

/** Emit at 1, 2, 4, 8, ... failures so diagnostics grow logarithmically. */
export function isItemIconFailureLogThreshold(cumulativeFailures: number): boolean {
  if (!Number.isSafeInteger(cumulativeFailures) || cumulativeFailures < 1) return false;
  return Number.isInteger(Math.log2(cumulativeFailures));
}

/** A failure suppresses only the exact URI that produced it; a changed URI must be retried. */
export function hasItemIconUriFailed(failedUri: string | null, currentUri: string): boolean {
  return failedUri === currentUri;
}

/**
 * A dataset-scoped, constant-memory failure reporter. It retains only a small URI sample and
 * emits O(log n) console entries even when a broken asset pack affects thousands of icons.
 */
export class BoundedItemIconFailureReporter {
  private cumulativeFailures = 0;
  private readonly sampledFailures: ItemIconFailureSample[] = [];
  private readonly datasetIdentity: string;
  private readonly logError: FailureLogger;

  constructor(
    datasetIdentity: string,
    logError: FailureLogger = (message, summary) => console.error(message, summary),
  ) {
    this.datasetIdentity = datasetIdentity;
    this.logError = logError;
  }

  report(failure: ItemIconLoadFailure): void {
    this.cumulativeFailures += 1;
    const sampledUri = boundedText(failure.uri, MAX_DIAGNOSTIC_URI_LENGTH);
    if (
      this.sampledFailures.length < ITEM_ICON_FAILURE_SAMPLE_LIMIT &&
      !this.sampledFailures.some(sample => sample.uri === sampledUri)
    ) {
      this.sampledFailures.push({
        uri: sampledUri,
        ...(failure.itemKey
          ? {itemKey: boundedText(failure.itemKey, MAX_DIAGNOSTIC_TEXT_LENGTH)}
          : {}),
        label: boundedText(failure.label, MAX_DIAGNOSTIC_TEXT_LENGTH),
        detail: failureDetail(failure.detail),
      });
    }
    if (!isItemIconFailureLogThreshold(this.cumulativeFailures)) return;

    this.logError(
      'Item icon HTTP fetch or image decode failed; rendering a named accessible fallback.',
      {
        datasetIdentity: this.datasetIdentity,
        cumulativeFailures: this.cumulativeFailures,
        sampledFailures: this.sampledFailures.map(sample => ({...sample})),
        sampleLimit: ITEM_ICON_FAILURE_SAMPLE_LIMIT,
        failureEventsOmitted: Math.max(
          0,
          this.cumulativeFailures - this.sampledFailures.length,
        ),
      },
    );
  }
}

/**
 * Collects intentionally absent catalog icon URLs during the existing item-index pass. This
 * avoids a warning from every fallback component and keeps the retained sample constant.
 */
export class BoundedAbsentItemIconCollector {
  private catalogItems = 0;
  private itemsWithoutIconUrl = 0;
  private readonly sampleKeys: string[] = [];

  observe(item: CatalogItem): void {
    this.catalogItems += 1;
    if (typeof item.icon === 'string' && item.icon !== '') return;
    this.itemsWithoutIconUrl += 1;
    if (this.sampleKeys.length < ABSENT_ITEM_ICON_SAMPLE_LIMIT) {
      this.sampleKeys.push(boundedText(item.k, MAX_DIAGNOSTIC_TEXT_LENGTH));
    }
  }

  summary(): AbsentItemIconSummary | null {
    if (this.itemsWithoutIconUrl === 0) return null;
    return {
      catalogItems: this.catalogItems,
      itemsWithoutIconUrl: this.itemsWithoutIconUrl,
      sampleKeys: [...this.sampleKeys],
      sampleLimit: ABSENT_ITEM_ICON_SAMPLE_LIMIT,
      sampleKeysOmitted: this.itemsWithoutIconUrl - this.sampleKeys.length,
    };
  }
}
