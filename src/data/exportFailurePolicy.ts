const TRANSPARENT_MOB_OMISSION =
  /^mob [a-z0-9_.-]+:[a-z0-9_./-]+ rendered fully transparent and was omitted$/iu;
const UNKNOWN_CORRECT_TOOL_FALLBACK =
  /^blockdrops [a-z0-9_.-]+:[a-z0-9_./-]+: no standard candidate tool satisfies requiresCorrectToolForDrops; probing with a netherite pickaxe$/iu;

/**
 * Older 1.20.1 exporters classified two successful omission/fallback outcomes as failures. Newer
 * exporters log both as warnings, regardless of which mod registered the entity or block.
 */
export function isReportableExportFailureMessage(message: string): boolean {
  return !TRANSPARENT_MOB_OMISSION.test(message) &&
    !UNKNOWN_CORRECT_TOOL_FALLBACK.test(message);
}

export function findingsForNonReportableExportFailures(
  findings: readonly string[],
  failureCount: number,
): readonly string[] {
  const retained = findings.filter(finding => !finding.includes('Share exporter errors'));
  const noun = failureCount === 1 ? 'case was' : 'cases were';
  return Object.freeze([
    ...retained,
    `${failureCount.toLocaleString()} expected exporter compatibility ${noun} logged by an older exporter. No error report is needed.`,
  ]);
}
