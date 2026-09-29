import type { IsoDate, PageRequest, Uuid } from './common.models';
import type { RecordSource } from './patent.models';

export const TRADEMARK_STATUSES = ['LIVE_PENDING', 'LIVE_REGISTERED', 'DEAD_ABANDONED', 'DEAD_CANCELLED'] as const;
export type TrademarkStatus = (typeof TRADEMARK_STATUSES)[number];

export type MarkType = 'STANDARD_CHARACTER' | 'DESIGN' | 'SOUND';

export type FilingBasis = '1A' | '1B' | '44D' | '44E' | '66A';

export const TRADEMARK_SORTS = ['relevance', 'filingDate', 'registrationDate', 'serialNumber'] as const;
export type TrademarkSortField = (typeof TRADEMARK_SORTS)[number];

export interface TrademarkSummary {
  /** e.g. `97123456`. */
  serialNumber: string;
  registrationNumber: string | null;
  markText: string;
  markType: MarkType;
  status: TrademarkStatus;
  filingDate: IsoDate | null;
  registrationDate: IsoDate | null;
  owner: string | null;
  niceClasses: number[];
}

export interface GoodsAndServices {
  niceClass: number;
  description: string;
}

export interface ProsecutionEvent {
  date: IsoDate;
  code: string;
  description: string;
}

export interface TrademarkDetail extends TrademarkSummary {
  goodsAndServices: GoodsAndServices[];
  ownerAddress: string | null;
  attorney: string | null;
  filingBasis: FilingBasis | null;
  events: ProsecutionEvent[];
  statusDate: IsoDate | null;
  source: RecordSource;
  ingestJobId: Uuid | null;
}

export interface TrademarkSearchParams extends PageRequest {
  q?: string;
  status?: TrademarkStatus;
  /** 1–45. */
  niceClass?: number;
  owner?: string;
  filedFrom?: IsoDate;
  filedTo?: IsoDate;
}
