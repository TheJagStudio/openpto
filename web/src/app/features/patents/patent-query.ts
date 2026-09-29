import type { ParamMap, Params } from '@angular/router';

import {
  PATENT_STATUSES,
  PATENT_TYPES,
  type PatentSearchParams,
  type PatentStatus,
  type PatentType,
} from '../../core/models';

const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;
export const DEFAULT_PAGE_SIZE = 20;

function oneOf<T extends string>(values: readonly T[], raw: string | null): T | undefined {
  const v = raw?.toUpperCase();
  return v && (values as readonly string[]).includes(v) ? (v as T) : undefined;
}

function text(raw: string | null): string | undefined {
  const v = raw?.trim();
  return v ? v : undefined;
}

function date(raw: string | null): string | undefined {
  return raw && DATE_RE.test(raw) ? raw : undefined;
}

function int(raw: string | null, fallback: number, min: number, max: number): number {
  const n = raw === null ? NaN : Number.parseInt(raw, 10);
  return Number.isFinite(n) ? Math.min(max, Math.max(min, n)) : fallback;
}

/**
 * URL query params → typed search criteria. Invalid values are dropped, paging is clamped
 * (page ≥ 0, 1 ≤ size ≤ 100) so any shared URL produces a valid API call.
 */
export function parsePatentQuery(params: ParamMap): PatentSearchParams {
  const criteria: PatentSearchParams = {
    q: text(params.get('q')),
    type: oneOf<PatentType>(PATENT_TYPES, params.get('type')),
    status: oneOf<PatentStatus>(PATENT_STATUSES, params.get('status')),
    cpc: text(params.get('cpc'))?.toUpperCase(),
    assignee: text(params.get('assignee')),
    inventor: text(params.get('inventor')),
    filedFrom: date(params.get('filedFrom')),
    filedTo: date(params.get('filedTo')),
    grantedFrom: date(params.get('grantedFrom')),
    grantedTo: date(params.get('grantedTo')),
    page: int(params.get('page'), 0, 0, 10_000),
    size: int(params.get('size'), DEFAULT_PAGE_SIZE, 1, 100),
    sort: text(params.get('sort')),
  };
  return Object.fromEntries(Object.entries(criteria).filter(([, v]) => v !== undefined)) as PatentSearchParams;
}

/** Filter keys that narrow results (used for chips / "clear all"). */
export const PATENT_FILTER_KEYS = [
  'type',
  'status',
  'cpc',
  'assignee',
  'inventor',
  'filedFrom',
  'filedTo',
  'grantedFrom',
  'grantedTo',
] as const satisfies readonly (keyof PatentSearchParams)[];

export type PatentFilterKey = (typeof PATENT_FILTER_KEYS)[number];

/** Builds the query-param patch for a filter change; any filter change resets to the first page. */
export function patchFor(changes: Partial<Record<keyof PatentSearchParams, string | number | null>>): Params {
  const patch: Params = {};
  for (const [k, v] of Object.entries(changes)) patch[k] = v === '' || v === undefined ? null : v;
  if (!('page' in changes)) patch['page'] = null;
  return patch;
}
