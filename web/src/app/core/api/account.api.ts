import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

import { map, type Observable } from 'rxjs';

import { API_BASE_URL, toHttpParams, toPage } from '../http/http-utils';
import type { AdminUser, ApiKeyResponse, Page, PageRequest, UsageResponse } from '../models';

@Injectable({ providedIn: 'root' })
export class AccountApi {
  private readonly http = inject(HttpClient);
  private readonly root = inject(API_BASE_URL);
  private readonly base = `${this.root}/api/v1/account`;

  apiKeys(): Observable<ApiKeyResponse[]> {
    return this.http.get<ApiKeyResponse[]>(`${this.base}/api-keys`);
  }

  /** Response carries the plaintext `key` exactly once. */
  createApiKey(name: string): Observable<ApiKeyResponse> {
    return this.http.post<ApiKeyResponse>(`${this.base}/api-keys`, { name });
  }

  rotateApiKey(id: string): Observable<ApiKeyResponse> {
    return this.http.post<ApiKeyResponse>(`${this.base}/api-keys/${encodeURIComponent(id)}/rotate`, null);
  }

  revokeApiKey(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/api-keys/${encodeURIComponent(id)}`);
  }

  usage(days = 30): Observable<UsageResponse> {
    return this.http.get<UsageResponse>(`${this.base}/usage`, { params: toHttpParams({ days }) }).pipe(
      map((u) => ({ totalRequests: u?.totalRequests ?? 0, daily: u?.daily ?? [], byKey: u?.byKey ?? [] })),
    );
  }

  /** ADMIN only. */
  adminUsers(query: PageRequest = {}): Observable<Page<AdminUser>> {
    return this.http
      .get<Page<AdminUser>>(`${this.root}/api/v1/admin/users`, { params: toHttpParams(query) })
      .pipe(map((page) => toPage(page)));
  }
}
