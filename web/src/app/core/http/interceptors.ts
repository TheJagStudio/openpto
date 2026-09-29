import { HttpErrorResponse, type HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';

import { catchError, throwError } from 'rxjs';

import { AuthStore } from '../auth/auth.store';
import { Notifier } from '../notify/notifier.service';
import { SKIP_AUTH_REDIRECT, SKIP_ERROR_TOAST } from './http-utils';
import { retryAfterSeconds, toProblem } from './problem';

/** True for calls that go to our own gateway (relative or same-origin), never third parties. */
function isOwnApi(url: string): boolean {
  if (url.startsWith('/')) return !url.startsWith('//');
  try {
    return typeof location !== 'undefined' && new URL(url).origin === location.origin;
  } catch {
    return false;
  }
}

function newRequestId(): string {
  const c = globalThis.crypto;
  if (c && typeof c.randomUUID === 'function') return c.randomUUID();
  return `${Date.now().toString(16)}-${Math.random().toString(16).slice(2, 10)}`;
}

/** Adds a correlation id to every API request (the gateway propagates it through all services). */
export const requestIdInterceptor: HttpInterceptorFn = (req, next) => {
  if (!isOwnApi(req.url) || req.headers.has('X-Request-Id')) return next(req);
  return next(req.clone({ setHeaders: { 'X-Request-Id': newRequestId() } }));
};

/** Attaches `Authorization: Bearer <jwt>` while the stored session is valid. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (!isOwnApi(req.url) || req.headers.has('Authorization')) return next(req);
  const token = inject(AuthStore).token();
  if (!token) return next(req);
  return next(req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }));
};

/**
 * Global error handling:
 * - 401 → drop the session and send the user to /login?returnUrl=… (unless opted out)
 * - 429 → toast with the Retry-After delay
 * - anything else → toast with ProblemDetail title/detail + requestId (unless opted out)
 * The error is always re-thrown so callers can still react.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthStore);
  const router = inject(Router);
  const notifier = inject(Notifier);

  return next(req).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse)) return throwError(() => error);
      const problem = toProblem(error);

      if (error.status === 401 && !req.context.get(SKIP_AUTH_REDIRECT)) {
        const hadSession = auth.isAuthenticated();
        auth.logout();
        const current = router.url;
        if (!current.startsWith('/login')) {
          void router.navigate(['/login'], { queryParams: { returnUrl: current } });
        }
        if (hadSession) {
          notifier.warning('Your session has ended', { description: 'Please sign in again to continue.' });
        } else if (!req.context.get(SKIP_ERROR_TOAST)) {
          notifier.info('Sign in required', { description: problem.detail });
        }
        return throwError(() => error);
      }

      if (error.status === 429) {
        const seconds = retryAfterSeconds(error.headers?.get('Retry-After') ?? null);
        notifier.warning('Rate limit reached', {
          description:
            seconds !== null
              ? `Too many requests. Try again in ${seconds} second${seconds === 1 ? '' : 's'}.`
              : 'Too many requests. Please slow down and try again shortly.',
          duration: 8000,
        });
        return throwError(() => error);
      }

      if (!req.context.get(SKIP_ERROR_TOAST)) {
        notifier.problem(problem);
      }
      return throwError(() => error);
    }),
  );
};
