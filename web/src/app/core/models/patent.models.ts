import type { IsoDate, IsoInstant, PageRequest, Uuid, ValueCount } from './common.models';

export const PATENT_TYPES = ['UTILITY', 'DESIGN', 'PLANT', 'REISSUE'] as const;
export type PatentType = (typeof PATENT_TYPES)[number];

export const PATENT_STATUSES = ['PENDING', 'GRANTED', 'ABANDONED', 'EXPIRED'] as const;
export type PatentStatus = (typeof PATENT_STATUSES)[number];

export type RecordSource = 'SEED' | 'INGEST';

export const PATENT_SORTS = ['relevance', 'grantDate', 'filingDate', 'patentNumber'] as const;
export type PatentSortField = (typeof PATENT_SORTS)[number];

export interface PatentSummary {
  /** e.g. `US11234567B2` (publications: `US20240123456A1`). */
  patentNumber: string;
  /** e.g. `17/123,456`. */
  applicationNumber: string | null;
  title: string;
  type: PatentType;
  status: PatentStatus;
  filingDate: IsoDate | null;
  grantDate: IsoDate | null;
  primaryCpc: string | null;
  assignees: string[];
  inventors: string[];
  abstractSnippet: string | null;
  source: RecordSource;
}

export interface Claim {
  number: number;
  text: string;
  independent: boolean;
  /** Claim number this one depends on; null for independent claims. */
  dependsOn: number | null;
}

export type CitedBy = 'EXAMINER' | 'APPLICANT';

export interface Citation {
  patentNumber: string;
  citedBy: CitedBy;
}

export interface Party {
  name: string;
  city: string | null;
  state: string | null;
  country: string | null;
}

/** Summary fields + detail. `assignees`/`inventors` are structured here (not plain strings). */
export interface PatentDetail extends Omit<PatentSummary, 'assignees' | 'inventors'> {
  abstract: string | null;
  claims: Claim[];
  cpcCodes: string[];
  citations: Citation[];
  inventors: Party[];
  assignees: Party[];
  priorityDate: IsoDate | null;
  expirationDate: IsoDate | null;
  examiner: string | null;
  artUnit: string | null;
  ingestJobId: Uuid | null;
  updatedAt: IsoInstant | null;
}

/** Filters accepted by `GET /api/v1/patents` and `/facets`. */
export interface PatentSearchParams extends PageRequest {
  q?: string;
  type?: PatentType;
  status?: PatentStatus;
  /** CPC prefix, e.g. `G06F`. */
  cpc?: string;
  assignee?: string;
  inventor?: string;
  filedFrom?: IsoDate;
  filedTo?: IsoDate;
  grantedFrom?: IsoDate;
  grantedTo?: IsoDate;
}

export interface PatentFacets {
  types: ValueCount<PatentType>[];
  statuses: ValueCount<PatentStatus>[];
  years: ValueCount[];
  cpcSections: ValueCount[];
  topAssignees: ValueCount[];
}

export interface YearCount {
  year: number;
  count: number;
}

/** `GET /api/v1/stats`. */
export interface OdpStats {
  patents: number;
  trademarks: number;
  patentsByYear: YearCount[];
  trademarksByStatus: ValueCount[];
  lastIngestAt: IsoInstant | null;
}
