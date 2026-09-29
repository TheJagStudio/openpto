import { HttpContextToken, HttpParams } from '@angular/common/http';
import { InjectionToken } from '@angular/core';

import { environment } from '../../../environments/environment';
import type { Page } from '../models';

/** Prefix for every API URL (empty = same origin, which the dev proxy forwards to the gateway). */
export const API_BASE_URL = new InjectionToken<string>('API_BASE_URL', {
  providedIn: 'root',
  factory: () => environment.apiBaseUrl,
});

/**
 * Opt a request out of the global error toast — for callers that render the error inline
 * (form validation, "not found" pages, the live fee calculator…). 401 handling still applies.
 */
export const SKIP_ERROR_TOAST = new HttpContextToken<boolean>(() => false);

/** Opt a request out of the 401 → logout → login redirect (e.g. the login call itself). */
export const SKIP_AUTH_REDIRECT = new HttpContextToken<boolean>(() => false);

type ParamValue = string | number | boolean | null | undefined;

/**
 * Builds `HttpParams` from a flat object, dropping `null`, `undefined` and blank strings so
 * URLs stay clean and shareable.
 */
export function toHttpParams(source: object): HttpParams {
  let params = new HttpParams();
  for (const [key, raw] of Object.entries(source) as [string, ParamValue][]) {
    if (raw === null || raw === undefined) continue;
    const value = typeof raw === 'string' ? raw.trim() : String(raw);
    if (value === '') continue;
    params = params.set(key, value);
  }
  return params;
}

/**
 * Normalizes a page envelope defensively (missing arrays/counters become safe defaults) so
 * templates never have to null-check. Numbers are clamped to non-negative integers.
 */
export function toPage<T>(raw: Partial<Page<T>> | null | undefined): Page<T> {
  const content = Array.isArray(raw?.content) ? raw.content : [];
  const size = Math.max(1, Math.trunc(raw?.size ?? (content.length || 20)));
  const totalElements = Math.max(0, Math.trunc(raw?.totalElements ?? content.length));
  const totalPages = Math.max(0, Math.trunc(raw?.totalPages ?? Math.ceil(totalElements / size)));
  const page = Math.max(0, Math.trunc(raw?.page ?? 0));
  return { content, page, size, totalElements, totalPages };
}
