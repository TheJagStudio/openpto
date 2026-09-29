import { TestBed } from '@angular/core/testing';
import { type ActivatedRouteSnapshot, provideRouter, Router, type RouterStateSnapshot, UrlTree } from '@angular/router';

import type { AuthResponse, Role } from '../models';
import { Notifier } from '../notify/notifier.service';
import { AUTH_STORAGE_KEY, AuthStore, NOW } from './auth.store';
import { adminGuard, authGuard, guestGuard } from './guards';

function response(roles: Role[] = ['USER'], expiresIn = 3600): AuthResponse {
  return {
    accessToken: 'tok',
    tokenType: 'Bearer',
    expiresIn,
    user: { id: 'u1', email: 'grace.hopper@example.org', displayName: 'Grace Hopper', roles, createdAt: '2025-01-01T00:00:00Z' },
  };
}

describe('AuthStore', () => {
  let now = 1_000_000;

  function configure() {
    TestBed.configureTestingModule({ providers: [{ provide: NOW, useValue: () => now }] });
    return TestBed.inject(AuthStore);
  }

  beforeEach(() => {
    localStorage.clear();
    now = 1_000_000;
  });

  it('signIn stores the session, exposes signals and persists to localStorage', () => {
    const store = configure();
    store.signIn(response(['USER', 'ADMIN']));

    expect(store.isAuthenticated()).toBe(true);
    expect(store.isAdmin()).toBe(true);
    expect(store.initials()).toBe('GH');
    expect(store.token()).toBe('tok');
    const saved = JSON.parse(localStorage.getItem(AUTH_STORAGE_KEY)!);
    expect(saved.expiresAt).toBe(1_000_000 + 3_600_000);
  });

  it('restores a valid persisted session on startup', () => {
    localStorage.setItem(
      AUTH_STORAGE_KEY,
      JSON.stringify({ accessToken: 'persisted', expiresAt: now + 60_000, user: response().user }),
    );
    const store = configure();
    expect(store.token()).toBe('persisted');
    expect(store.user()?.displayName).toBe('Grace Hopper');
  });

  it('discards an expired or corrupted persisted session', () => {
    localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify({ accessToken: 'old', expiresAt: now - 1, user: response().user }));
    const store = configure();
    expect(store.isAuthenticated()).toBe(false);
    expect(localStorage.getItem(AUTH_STORAGE_KEY)).toBeNull();

    TestBed.resetTestingModule();
    localStorage.setItem(AUTH_STORAGE_KEY, '{not json');
    expect(configure().isAuthenticated()).toBe(false);
  });

  it('token() logs out once the token has expired', () => {
    const store = configure();
    store.signIn(response(['USER'], 60));
    now += 61_000;
    expect(store.token()).toBeNull();
    expect(store.isAuthenticated()).toBe(false);
  });

  it('logout clears state and storage', () => {
    const store = configure();
    store.signIn(response());
    store.logout();
    expect(store.user()).toBeNull();
    expect(localStorage.getItem(AUTH_STORAGE_KEY)).toBeNull();
  });
});

describe('route guards', () => {
  const state = { url: '/pipeline/jobs/42' } as RouterStateSnapshot;
  const route = {} as ActivatedRouteSnapshot;
  let notifier: { warning: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    localStorage.clear();
    notifier = { warning: vi.fn() };
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: Notifier, useValue: notifier }] });
  });

  afterEach(() => TestBed.inject(AuthStore).logout());

  const run = (guard: typeof authGuard) => TestBed.runInInjectionContext(() => guard(route, state));
  const serialize = (result: unknown) => TestBed.inject(Router).serializeUrl(result as UrlTree);

  it('authGuard redirects anonymous users to /login with returnUrl', () => {
    const result = run(authGuard);
    expect(result).toBeInstanceOf(UrlTree);
    expect(serialize(result)).toBe('/login?returnUrl=%2Fpipeline%2Fjobs%2F42');
  });

  it('authGuard allows signed-in users', () => {
    TestBed.inject(AuthStore).signIn(response());
    expect(run(authGuard)).toBe(true);
  });

  it('adminGuard sends non-admins home with a warning, and admins through', () => {
    const store = TestBed.inject(AuthStore);
    store.signIn(response(['USER']));
    expect(serialize(run(adminGuard))).toBe('/');
    expect(notifier.warning).toHaveBeenCalled();

    store.signIn(response(['USER', 'ADMIN']));
    expect(run(adminGuard)).toBe(true);
  });

  it('adminGuard sends anonymous users to login', () => {
    expect(serialize(run(adminGuard))).toBe('/login?returnUrl=%2Fpipeline%2Fjobs%2F42');
  });

  it('guestGuard keeps signed-in users off the login page', () => {
    expect(run(guestGuard)).toBe(true);
    TestBed.inject(AuthStore).signIn(response());
    expect(serialize(run(guestGuard))).toBe('/');
  });
});
