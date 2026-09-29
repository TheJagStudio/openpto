import type { IsoInstant, PageRequest, Uuid, ValueCount } from './common.models';

export const INGEST_STATUSES = [
  'QUEUED',
  'PARSING',
  'TRANSFORMED',
  'LOADING',
  'COMPLETED',
  'PARTIAL',
  'FAILED',
] as const;
export type IngestStatus = (typeof INGEST_STATUSES)[number];

/** Statuses after which a job no longer changes on its own. */
export const TERMINAL_INGEST_STATUSES: readonly IngestStatus[] = ['COMPLETED', 'PARTIAL', 'FAILED'];

export type DocumentFormat =
  | 'US_PATENT_GRANT'
  | 'US_PATENT_APPLICATION'
  | 'PATDOC_LEGACY'
  | 'TRADEMARK_DAILY'
  | 'UNKNOWN';

export const PIPELINE_STAGES = ['UPLOADED', 'PARSED', 'TRANSFORMED', 'LOADED'] as const;
export type PipelineStage = (typeof PIPELINE_STAGES)[number];

export interface IngestStageInfo {
  stage: PipelineStage;
  /** Stage status as reported by the service (e.g. `DONE`, `FAILED`, `RUNNING`, `PENDING`). */
  status: string;
  at: IsoInstant | null;
  message: string | null;
}

export interface IngestError {
  index: number;
  identifier: string | null;
  message: string;
}

export interface IngestJobResponse {
  id: Uuid;
  fileName: string;
  sizeBytes: number;
  status: IngestStatus;
  documentFormat: DocumentFormat;
  recordsTotal: number;
  recordsLoaded: number;
  recordsFailed: number;
  errors: IngestError[];
  stages: IngestStageInfo[];
  rawObjectKey: string | null;
  jsonObjectKey: string | null;
  ownerId: Uuid | null;
  createdAt: IsoInstant;
  startedAt: IsoInstant | null;
  finishedAt: IsoInstant | null;
  durationMs: number | null;
}

export interface IngestJobQuery extends PageRequest {
  status?: IngestStatus;
}

export interface IngestSample {
  name: string;
  description: string;
  format: DocumentFormat | string;
  sizeBytes: number;
}

export interface IngestStats {
  jobs: number;
  byStatus: ValueCount<IngestStatus>[];
  recordsLoaded: number;
  lastJobAt: IsoInstant | null;
}

/** Upload limits enforced by the ingest service (mirrored client-side). */
export const INGEST_LIMITS = {
  maxFiles: 10,
  maxBytes: 50 * 1024 * 1024,
  extensions: ['.xml', '.zip'],
} as const;

/** One JSON record produced by the transform step (shape depends on the document format). */
export type TransformedRecord = Record<string, unknown>;
