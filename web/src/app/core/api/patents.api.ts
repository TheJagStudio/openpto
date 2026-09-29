import { HttpClient, HttpContext } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

import { map, type Observable } from 'rxjs';

import { API_BASE_URL, SKIP_ERROR_TOAST, toHttpParams, toPage } from '../http/http-utils';
import type { Page, PatentDetail, PatentFacets, PatentSearchParams, PatentSummary } from '../models';

@Injectable({ providedIn: 'root' })
export class PatentsApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(API_BASE_URL)}/api/v1/patents`;

  search(params: PatentSearchParams = {}): Observable<Page<PatentSummary>> {
    return this.http
      .get<Page<PatentSummary>>(this.base, { params: toHttpParams(params) })
      .pipe(map((page) => toPage(page)));
  }

  /** Facet counts for the same filters (paging/sort are ignored by the endpoint). */
  facets(params: PatentSearchParams = {}): Observable<PatentFacets> {
    const { page: _p, size: _s, sort: _o, ...filters } = params;
    return this.http.get<PatentFacets>(`${this.base}/facets`, { params: toHttpParams(filters) }).pipe(
      map((f) => ({
        types: f?.types ?? [],
        statuses: f?.statuses ?? [],
        years: f?.years ?? [],
        cpcSections: f?.cpcSections ?? [],
        topAssignees: f?.topAssignees ?? [],
      })),
    );
  }

  /** 404 is rendered inline by the detail page, so it opts out of the global toast. */
  get(patentNumber: string): Observable<PatentDetail> {
    return this.http.get<PatentDetail>(`${this.base}/${encodeURIComponent(patentNumber)}`, {
      context: new HttpContext().set(SKIP_ERROR_TOAST, true),
    });
  }

  /** Bulk export URL (JSON/CSV) honoring the current filters — used as a plain download link. */
  datasetUrl(format: 'json' | 'csv', params: PatentSearchParams = {}): string {
    const { page: _p, size: _s, ...filters } = params;
    const qs = toHttpParams(filters).toString();
    return `${this.base.replace('/patents', '/datasets/patents')}.${format}${qs ? `?${qs}` : ''}`;
  }
}
