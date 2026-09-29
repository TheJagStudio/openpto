import type { IsoDate, IsoInstant } from './common.models';

export const ENTITY_SIZES = ['LARGE', 'SMALL', 'MICRO'] as const;
export type EntitySize = (typeof ENTITY_SIZES)[number];

export const APPLICATION_TYPES = ['UTILITY', 'DESIGN', 'PLANT', 'PROVISIONAL', 'REISSUE'] as const;
export type ApplicationType = (typeof APPLICATION_TYPES)[number];

export const CONTINUED_EXAMINATIONS = ['NONE', 'FIRST', 'SUBSEQUENT'] as const;
export type ContinuedExamination = (typeof CONTINUED_EXAMINATIONS)[number];

export const TRADEMARK_FILING_TYPES = [
  'APPLICATION',
  'STATEMENT_OF_USE',
  'EXTENSION_SOU',
  'SECTION_8',
  'SECTION_15',
  'SECTION_9_RENEWAL',
  'SECTION_8_AND_9',
] as const;
export type TrademarkFilingType = (typeof TRADEMARK_FILING_TYPES)[number];

export type FeeCategory = 'PATENT' | 'TRADEMARK';
export type FeeUnit = 'EACH' | 'PER_CLAIM' | 'PER_CLASS' | 'PER_50_SHEETS' | 'PER_MONTH';

export interface FeeScheduleSummary {
  id: number | string;
  /** e.g. `FY2025`. */
  code: string;
  name: string;
  effectiveFrom: IsoDate;
  effectiveTo: IsoDate | null;
  current: boolean;
}

export interface FeeItem {
  feeCode: string;
  description: string;
  category: FeeCategory;
  group: string;
  largeEntity: number | null;
  smallEntity: number | null;
  microEntity: number | null;
  unit: FeeUnit;
}

export interface FeeSchedule extends FeeScheduleSummary {
  items: FeeItem[];
}

export interface PatentFilingRequest {
  applicationType: ApplicationType;
  entitySize: EntitySize;
  /** 0–500. */
  totalClaims: number;
  /** 0–100, ≤ totalClaims. */
  independentClaims: number;
  multipleDependentClaims: boolean;
  /** 0–10000. */
  specificationSheets: number;
  filedElectronically: boolean;
  lateFilingSurcharge: boolean;
  /** 0–5. */
  extensionMonths: number;
  continuedExamination: ContinuedExamination;
  prioritizedExamination: boolean;
  filingDate?: IsoDate;
}

export interface MaintenanceRequest {
  entitySize: EntitySize;
  grantDate: IsoDate;
  asOfDate?: IsoDate;
}

export type MaintenanceStage = '3.5' | '7.5' | '11.5';
export type MaintenanceWindowStatus = 'NOT_YET_OPEN' | 'OPEN' | 'GRACE_PERIOD' | 'EXPIRED' | 'PAID_WINDOW_PASSED';

export interface MaintenanceWindow {
  stage: MaintenanceStage;
  windowOpens: IsoDate;
  dueDate: IsoDate;
  graceEnds: IsoDate;
  status: MaintenanceWindowStatus;
  fee: number;
  surcharge: number;
  totalIfPaidOnAsOfDate: number;
}

export interface MaintenanceSchedule {
  windows: MaintenanceWindow[];
  patentExpired: boolean;
  scheduleCode: string;
}

export interface TrademarkFeeRequest {
  filingType: TrademarkFilingType;
  /** 1–45. */
  numberOfClasses: number;
  insufficientInformation: boolean;
  freeFormTextIds: boolean;
  /** 0–100. */
  extraCharacterBlocks: number;
  inGracePeriod: boolean;
  filingDate?: IsoDate;
}

export interface LineItem {
  feeCode: string;
  description: string;
  quantity: number;
  unitAmount: number;
  amount: number;
}

export interface Subtotal {
  group: string;
  amount: number;
}

export interface FeeQuote {
  scheduleCode: string;
  scheduleName: string;
  entitySize?: EntitySize | null;
  lineItems: LineItem[];
  subtotals: Subtotal[];
  total: number;
  currency: 'USD';
  notes: string[];
  warnings: string[];
}

export type QuoteKind = 'PATENT_FILING' | 'MAINTENANCE' | 'TRADEMARK';

export type QuoteRequestBody = PatentFilingRequest | MaintenanceRequest | TrademarkFeeRequest;

export interface SaveQuoteRequest {
  kind: QuoteKind;
  request: QuoteRequestBody;
  label?: string;
}

/** `POST /api/v1/fees/quotes` → 201 and `GET /api/v1/fees/quotes/{id}`. */
export interface SavedQuote extends FeeQuote {
  id: string;
  createdAt: IsoInstant;
  /** Optional echo fields — rendered when the backend includes them. */
  kind?: QuoteKind;
  label?: string | null;
  request?: QuoteRequestBody;
}
