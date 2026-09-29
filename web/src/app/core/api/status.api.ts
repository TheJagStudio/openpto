import { HttpClient, HttpContext } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

import type { Observable } from 'rxjs';

import { API_BASE_URL, SKIP_ERROR_TOAST } from '../http/http-utils';
import type { GatewayStatus, OdpStats } from '../models';

@Injectable({ providedIn: 'root' })
export class StatusApi {
  private readonly http = inject(HttpClient);
  private readonly root = inject(API_BASE_URL);

  /** Polled — failures are shown on the page, not as a toast storm. */
  gatewayStatus(): Observable<GatewayStatus> {
    return this.http.get<GatewayStatus>(`${this.root}/gateway/status`, {
      context: new HttpContext().set(SKIP_ERROR_TOAST, true),
    });
  }

  /** Public portal statistics (`GET /api/v1/stats`). Landing page degrades gracefully. */
  stats(): Observable<OdpStats> {
    return this.http.get<OdpStats>(`${this.root}/api/v1/stats`, {
      context: new HttpContext().set(SKIP_ERROR_TOAST, true),
    });
  }
}
