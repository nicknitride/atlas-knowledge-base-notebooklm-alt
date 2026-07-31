export interface SourcesVisibilityInput {
  citationCount: number;
  sourcesForcedOpen: boolean;
  sourcesUserCollapsed: boolean;
}

/** Canonical formula from contracts/ui-shell.md */
export function computeSourcesVisible({
  citationCount,
  sourcesForcedOpen,
  sourcesUserCollapsed,
}: SourcesVisibilityInput): boolean {
  const sourcesAutoEligible = citationCount > 0;
  return (
    sourcesForcedOpen || (sourcesAutoEligible && !sourcesUserCollapsed)
  );
}
