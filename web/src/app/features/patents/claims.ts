import type { Claim } from '../../core/models';

export interface ClaimRow extends Claim {
  /** Nesting depth: 0 for independent claims, +1 per dependency hop. */
  depth: number;
}

/**
 * Computes indentation depth for each claim from its `dependsOn` chain
 * (cycle-safe, capped at 4 levels for readability).
 */
export function claimTree(claims: readonly Claim[]): ClaimRow[] {
  const byNumber = new Map(claims.map((c) => [c.number, c]));
  const depthOf = (claim: Claim): number => {
    let depth = 0;
    const seen = new Set<number>([claim.number]);
    let current: Claim | undefined = claim;
    while (current && !current.independent && current.dependsOn !== null && current.dependsOn !== undefined) {
      if (seen.has(current.dependsOn)) break;
      seen.add(current.dependsOn);
      depth++;
      current = byNumber.get(current.dependsOn);
    }
    return Math.min(depth, 4);
  };
  return [...claims].sort((a, b) => a.number - b.number).map((c) => ({ ...c, depth: depthOf(c) }));
}
