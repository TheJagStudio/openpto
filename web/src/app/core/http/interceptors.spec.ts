import { HttpClient, HttpContext, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import { AuthStore } from '../auth/auth.store';
import type { AuthResponse } from '../models';
import { Notifier } from '../notify/notifier.service';
import { SKIP_ERROR_TOAST } from './http-utils';
import { authInterceptor, errorInterceptor, requestIdInterceptor } from './interceptors';

const session: AuthResponse = {
  accessToken: 'jwt-token',
  tokenType: 'Bearer',
  expiresIn: 3600,
  user: { id: 'u1', email: 'ada@example.org', displayName: 'Ada', roles: ['USER'], createdAt: '2025-01-01T00:00:00Z' },
};

describe('HTTP interceptors', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let auth: AuthStore;
  let notifier: { success: ReturnType<typeof vi.fn>; info: ReturnType<typeof vi.fn>; warning: ReturnType<typeof vi.fn>; error: ReturnType<typeof vi.fn>; problem: ReturnType<typeof vi.fn> };
  let router: { url: string; navigate: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    localStorage.clear();
    notifier = { success: vi.fn(), info: vi.fn(), warning: vi.fn(), error: vi.fn(), problem: vi.fn() };
    router = { url: '/developers?tab=keys', navigate: vi.fn().mockResolvedValue(true) };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([requestIdInterceptor, authInterceptor, errorInterceptor])),
        provideHttpClientTesting(),
        { provide: Notifier, useValue: notifier },
        { provide: Router, useValue: router },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthStore);
  });

  afterEach(() => {
    backend.verify();
    auth.logout();
  });

  it('adds Authorization: Bearer when a valid session exists', () => {
    auth.signIn(session);
    http.get('/api/v1/auth/me').subscribe();
    const req = backend.expectOne('/api/v1/auth/me');
    expect(req.request.headers.get('Authorization')).toBe('Bearer jwt-token');
    req.flush({});
  });

  it('sends no Authorization header when signed out', () => {
    http.get('/api/v1/patents').subscribe();
    const req = backend.expectOne('/api/v1/patents');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('never leaks the token or request id to third-party origins', () => {
    auth.signIn(session);
    http.get('https://evil.example.com/x').subscribe();
    const req = backend.expectOne('https://evil.example.com/x');
    expect(req.request.headers.has('Authorization')).toBe(false);
    expect(req.request.headers.has('X-Request-Id')).toBe(false);
    req.flush({});
  });

  it('adds a unique X-Request-Id to API calls', () => {
    http.get('/api/v1/stats').subscribe();
    http.get('/api/v1/stats').subscribe();
    const [a, b] = backend.match('/api/v1/stats');
    const idA = a!.request.headers.get('X-Request-Id');
    const idB = b!.request.headers.get('X-Request-Id');
    expect(idA).toBeTruthy();
    expect(idA).not.toBe(idB);
    a!.flush({});
    b!.flush({});
  });

  it('401 → logs out and redirects to /login with returnUrl', () => {
    auth.signIn(session);
    let failed = false;
    http.get('/api/v1/account/api-keys').subscribe({ error: () => (failed = true) });
    backend
      .expectOne('/api/v1/account/api-keys')
      .flush({ title: 'Unauthorized', status: 401 }, { status: 401, statusText: 'Unauthorized' });

    expect(failed).toBe(true);
    expect(auth.isAuthenticated()).toBe(false);
    expect(router.navigate).toHaveBeenCalledWith(['/login'], { queryParams: { returnUrl: '/developers?tab=keys' } });
    expect(notifier.warning).toHaveBeenCalledWith('Your session has ended', expect.anything());
  });

  it('429 → warning toast showing the Retry-After delay', () => {
    http.get('/api/v1/patents').subscribe({ error: () => undefined });
    backend
      .expectOne('/api/v1/patents')
      .flush({ title: 'Too Many Requests', status: 429 }, { status: 429, statusText: 'Too Many Requests', headers: { 'Retry-After': '42' } });

    expect(notifier.warning).toHaveBeenCalledWith(
      'Rate limit reached',
      expect.objectContaining({ description: 'Too many requests. Try again in 42 seconds.' }),
    );
    expect(notifier.problem).not.toHaveBeenCalled();
  });

  it('other errors → ProblemDetail toast including requestId', () => {
    http.get('/api/v1/fees/schedules').subscribe({ error: () => undefined });
    backend.expectOne('/api/v1/fees/schedules').flush(
      { title: 'Service unavailable', detail: 'fee-service is down', status: 503, requestId: 'req-123' },
      { status: 503, statusText: 'Service Unavailable' },
    );
    expect(notifier.problem).toHaveBeenCalledWith(
      expect.objectContaining({ title: 'Service unavailable', detail: 'fee-service is down', requestId: 'req-123' }),
    );
  });

  it('SKIP_ERROR_TOAST suppresses the toast but still errors', () => {
    let failed = false;
    http.get('/api/v1/patents/X', { context: new HttpContext().set(SKIP_ERROR_TOAST, true) }).subscribe({ error: () => (failed = true) });
    backend.expectOne('/api/v1/patents/X').flush({ title: 'Not Found', status: 404 }, { status: 404, statusText: 'Not Found' });
    expect(failed).toBe(true);
    expect(notifier.problem).not.toHaveBeenCalled();
  });
});
