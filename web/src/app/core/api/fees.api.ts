import { HttpClient, HttpContext } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

import type { Observable } from 'rxjs';

import { API_BASE_URL, SKIP_ERROR_TOAST, toHttpParams } from '../http/http-utils';
import type {
  FeeCategory,
  FeeQuote,
  FeeSchedule,
  FeeScheduleSummary,
  MaintenanceRequest,
  MaintenanceSchedule,
  PatentFilingRequest,
  SavedQuote,
  SaveQuoteRequest,
  TrademarkFeeRequest,
} from '../models';

/** Calculator calls render validation errors inline (live recalculation), so they skip the toast. */
const inline = () => new HttpContext().set(SKIP_ERROR_TOAST, true);

@Injectable({ providedIn: 'root' })
export class FeesApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(API_BASE_URL)}/api/v1/fees`;

  schedules(): Observable<FeeScheduleSummary[]> {
    return this.http.get<FeeScheduleSummary[]>(`${this.base}/schedules`);
  }

  /** `code` is a schedule code (e.g. `FY2025`) or `current`. */
  schedule(code: string, category?: FeeCategory): Observable<FeeSchedule> {
    return this.http.get<FeeSchedule>(`${this.base}/schedules/${encodeURIComponent(code)}`, {
      params: toHttpParams({ category }),
    });
  }

  patentFiling(request: PatentFilingRequest): Observable<FeeQuote> {
    return this.http.post<FeeQuote>(`${this.base}/patent/filing`, request, { context: inline() });
  }

  maintenance(request: MaintenanceRequest): Observable<MaintenanceSchedule> {
    return this.http.post<MaintenanceSchedule>(`${this.base}/patent/maintenance`, request, { context: inline() });
  }

  trademark(request: TrademarkFeeRequest): Observable<FeeQuote> {
    return this.http.post<FeeQuote>(`${this.base}/trademark`, request, { context: inline() });
  }

  saveQuote(body: SaveQuoteRequest): Observable<SavedQuote> {
    return this.http.post<SavedQuote>(`${this.base}/quotes`, body);
  }

  quote(id: string): Observable<SavedQuote> {
    return this.http.get<SavedQuote>(`${this.base}/quotes/${encodeURIComponent(id)}`, { context: inline() });
  }
}
