import { computed, DestroyRef, inject, Injectable, InjectionToken, signal } from '@angular/core';

import type { AuthResponse, Role, UserResponse } from '../models';

export const AUTH_STORAGE_KEY = 'openpto.auth';

/** Clock abstraction so expiry logic is testable. */
export const NOW = new InjectionToken<() => number>('NOW', {
  providedIn: 'root',
  factory: () => () => Date.now(),
});

/** Storage abstraction (defaults to `localStorage`, `null` when unavailable). */
export const AUTH_STORAGE = new InjectionToken<Storage | null>('AUTH_STORAGE', {
  providedIn: 'root',
  factory: () => {
    try {
      return typeof localStorage === 'undefined' ? null : localStorage;
    } catch {
      return null;
    }
  },
});

export interface AuthSession {
  accessToken: string;
  /** Epoch millis. */
  expiresAt: number;
  user: UserResponse;
}

/** Refuse tokens that expire within this margin so a request never leaves with a dying token. */
const EXPIRY_SKEW_MS = 10_000;

function isSession(value: unknown): value is AuthSession {
  if (typeof value !== 'object' || value === null) return false;
  const v = value as Partial<AuthSession>;
  return (
    typeof v.accessToken === 'string' &&
    typeof v.expiresAt === 'number' &&
    typeof v.user === 'object' &&
    v.user !== null &&
    typeof v.user.email === 'string'
  );
}

/**
 * Signal store for the signed-in session. Persisted to localStorage; an expired session is
 * discarded on load, on every token read, and by a timer at the exact expiry moment.
 */
@Injectable({ providedIn: 'root' })
export class AuthStore {
  private readonly storage = inject(AUTH_STORAGE);
  private readonly now = inject(NOW);
  private readonly session = signal<AuthSession | null>(null);
  private expiryTimer: ReturnType<typeof setTimeout> | null = null;

  readonly user = computed(() => this.session()?.user ?? null);
  readonly isAuthenticated = computed(() => this.session() !== null);
  readonly roles = computed<Role[]>(() => this.session()?.user.roles ?? []);
  readonly isAdmin = computed(() => this.roles().includes('ADMIN'));
  readonly expiresAt = computed(() => this.session()?.expiresAt ?? null);
  readonly initials = computed(() => {
    const u = this.user();
    if (!u) return '';
    const source = u.displayName?.trim() || u.email;
    const parts = source.split(/[\s@._-]+/).filter(Boolean);
    return ((parts[0]?.[0] ?? '') + (parts[1]?.[0] ?? '')).toUpperCase() || '?';
  });

  constructor() {
    this.restore();
    inject(DestroyRef).onDestroy(() => this.clearTimer());
  }

  /** Stores a fresh session from a login/register response. */
  signIn(response: AuthResponse): void {
    const session: AuthSession = {
      accessToken: response.accessToken,
      expiresAt: this.now() + Math.max(0, response.expiresIn) * 1000,
      user: response.user,
    };
    this.session.set(session);
    this.persist(session);
    this.scheduleExpiry(session.expiresAt);
  }

  /** Replaces the cached user profile (e.g. after `GET /auth/me`). */
  updateUser(user: UserResponse): void {
    const current = this.session();
    if (!current) return;
    const next = { ...current, user };
    this.session.set(next);
    this.persist(next);
  }

  logout(): void {
    this.clearTimer();
    this.session.set(null);
    try {
      this.storage?.removeItem(AUTH_STORAGE_KEY);
    } catch {
      /* storage unavailable — nothing to clear */
    }
  }

  /** The bearer token if the session is still valid; logs out (and returns null) when expired. */
  token(): string | null {
    const s = this.session();
    if (!s) return null;
    if (this.isExpired(s)) {
      this.logout();
      return null;
    }
    return s.accessToken;
  }

  hasRole(role: Role): boolean {
    return this.roles().includes(role);
  }

  private isExpired(s: AuthSession): boolean {
    return s.expiresAt - EXPIRY_SKEW_MS <= this.now();
  }

  private restore(): void {
    let raw: string | null = null;
    try {
      raw = this.storage?.getItem(AUTH_STORAGE_KEY) ?? null;
    } catch {
      raw = null;
    }
    if (!raw) return;
    try {
      const parsed: unknown = JSON.parse(raw);
      if (isSession(parsed) && !this.isExpired(parsed)) {
        this.session.set(parsed);
        this.scheduleExpiry(parsed.expiresAt);
        return;
      }
    } catch {
      /* corrupted entry — fall through and remove it */
    }
    this.logout();
  }

  private persist(session: AuthSession): void {
    try {
      this.storage?.setItem(AUTH_STORAGE_KEY, JSON.stringify(session));
    } catch {
      /* quota / private mode — session stays in memory only */
    }
  }

  private scheduleExpiry(expiresAt: number): void {
    this.clearTimer();
    const delay = expiresAt - EXPIRY_SKEW_MS - this.now();
    // setTimeout overflows above ~24.8 days; tokens live 8h, so only guard against nonsense.
    if (delay > 0 && delay < 2 ** 31 - 1) {
      this.expiryTimer = setTimeout(() => this.logout(), delay);
    }
  }

  private clearTimer(): void {
    if (this.expiryTimer !== null) {
      clearTimeout(this.expiryTimer);
      this.expiryTimer = null;
    }
  }
}
