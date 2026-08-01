export const DEFAULT_ROOT_AMOUNT = 1;
export const MAX_ROOT_AMOUNT = 1_000_000_000_000;

export function createDefaultRootProductionPlan(): {
  amount: number;
  windowSeconds: number;
} {
  return {amount: DEFAULT_ROOT_AMOUNT, windowSeconds: 1};
}

export function normalizeRootAmount(requestedAmount: number): number {
  if (!Number.isFinite(requestedAmount)) {
    throw new Error('Root amount must be a finite number.');
  }
  return Math.min(
    MAX_ROOT_AMOUNT,
    Math.max(DEFAULT_ROOT_AMOUNT, Math.floor(requestedAmount)),
  );
}

export function rootAmountWheelStep(deltaY: number): -1 | 0 | 1 {
  if (!Number.isFinite(deltaY)) {
    throw new Error('Root amount wheel delta must be finite.');
  }
  if (deltaY === 0) return 0;
  return deltaY < 0 ? 1 : -1;
}
