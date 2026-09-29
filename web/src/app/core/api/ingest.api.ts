import { HttpClient, type HttpEvent } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

import { map, type Observable } from 'rxjs';

import { API_BASE_URL, toHttpParams, toPage } from '../http/http-utils';
import type {
  IngestJobQuery,
  IngestJobResponse,
  IngestSample,
  IngestStats,
  Page,
  TransformedRecord,
} from '../models';

@Injectable({ providedIn: 'root' })
export class IngestApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(API_BASE_URL)}/api/v1/ingest`;

  /** Multipart upload with progress events (`reportProgress`). Field name: `files`. */
  upload(files: readonly File[]): Observable<HttpEvent<IngestJobResponse[]>> {
    const form = new FormData();
    for (const file of files) form.append('files', file, file.name);
    return this.http.post<IngestJobResponse[]>(`${this.base}/uploads`, form, {
      reportProgress: true,
      observe: 'events',
    });
  }

  jobs(query: IngestJobQuery = {}): Observable<Page<IngestJobResponse>> {
    return this.http
      .get<Page<IngestJobResponse>>(`${this.base}/jobs`, { params: toHttpParams(query) })
      .pipe(map((page) => toPage(page)));
  }

  job(id: string): Observable<IngestJobResponse> {
    return this.http.get<IngestJobResponse>(`${this.base}/jobs/${encodeURIComponent(id)}`);
  }

  jobJson(id: string): Observable<TransformedRecord[]> {
    return this.http.get<TransformedRecord[]>(`${this.base}/jobs/${encodeURIComponent(id)}/json`);
  }

  jobJsonUrl(id: string): string {
    return `${this.base}/jobs/${encodeURIComponent(id)}/json`;
  }

  retry(id: string): Observable<IngestJobResponse | null> {
    return this.http.post<IngestJobResponse | null>(`${this.base}/jobs/${encodeURIComponent(id)}/retry`, null);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/jobs/${encodeURIComponent(id)}`);
  }

  samples(): Observable<IngestSample[]> {
    return this.http.get<IngestSample[]>(`${this.base}/samples`);
  }

  sampleUrl(name: string): string {
    return `${this.base}/samples/${encodeURIComponent(name)}`;
  }

  /** Downloads a sample as a Blob (used by "Try this sample" to re-upload it). */
  sample(name: string): Observable<Blob> {
    return this.http.get(this.sampleUrl(name), { responseType: 'blob' });
  }

  stats(): Observable<IngestStats> {
    return this.http.get<IngestStats>(`${this.base}/stats`);
  }
}
