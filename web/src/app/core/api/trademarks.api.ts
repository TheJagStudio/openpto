import { HttpClient, HttpContext } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

import { map, type Observable } from 'rxjs';

import { API_BASE_URL, SKIP_ERROR_TOAST, toHttpParams, toPage } from '../http/http-utils';
import type { Page, TrademarkDetail, TrademarkSearchParams, TrademarkSummary } from '../models';

@Injectable({ providedIn: 'root' })
export class TrademarksApi {
  private readonly http = inject(HttpClient);
  private readonly root = inject(API_BASE_URL);
  private readonly base = `${this.root}/api/v1/trademarks`;

  search(params: TrademarkSearchParams = {}): Observable<Page<TrademarkSummary>> {
    return this.http
      .get<Page<TrademarkSummary>>(this.base, { params: toHttpParams(params) })
      .pipe(map((page) => toPage(page)));
  }

  get(serialNumber: string): Observable<TrademarkDetail> {
    return this.http.get<TrademarkDetail>(`${this.base}/${encodeURIComponent(serialNumber)}`, {
      context: new HttpContext().set(SKIP_ERROR_TOAST, true),
    });
  }

  datasetUrl(format: 'json' | 'csv', params: TrademarkSearchParams = {}): string {
    const { page: _p, size: _s, ...filters } = params;
    const qs = toHttpParams(filters).toString();
    return `${this.root}/api/v1/datasets/trademarks.${format}${qs ? `?${qs}` : ''}`;
  }
}
