import type { IsoDate, IsoInstant, Uuid } from './common.models';

export type Role = 'USER' | 'ADMIN';

export interface UserResponse {
  id: Uuid;
  email: string;
  displayName: string;
  roles: Role[];
  createdAt: IsoInstant;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: 'Bearer';
  /** Seconds until the token expires. */
  expiresIn: number;
  user: UserResponse;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  /** Min 10 chars, at least one letter and one digit. */
  password: string;
  displayName: string;
}

export type ApiTier = 'ANONYMOUS' | 'FREE' | 'WEB' | 'ADMIN';

export interface ApiKeyResponse {
  id: Uuid;
  name: string;
  /** First 9 chars of the key, e.g. `opto_ab12`. */
  prefix: string;
  tier: ApiTier;
  createdAt: IsoInstant;
  lastUsedAt: IsoInstant | null;
  revokedAt: IsoInstant | null;
  requestCount: number;
  /** Plaintext key — only present on create/rotate responses. */
  key?: string;
}

export interface CreateApiKeyRequest {
  name: string;
}

export interface DailyUsage {
  date: IsoDate;
  requests: number;
}

export interface KeyUsage {
  keyId: Uuid;
  name: string;
  requests: number;
}

export interface UsageResponse {
  totalRequests: number;
  daily: DailyUsage[];
  byKey: KeyUsage[];
}

/**
 * Row of `GET /api/v1/admin/users` ("page of users with key counts").
 * Matches odp-service `AdminUserResponse`.
 */
export interface AdminUser extends UserResponse {
  enabled: boolean;
  lastLoginAt: string | null;
  lockedUntil: string | null;
  activeKeys: number;
  totalKeys: number;
}

/** Published tier limits (mirrors CONTRACTS.md; enforced by the gateway). */
export interface TierLimit {
  tier: ApiTier;
  identity: string;
  perMinute: number;
  perDay: number;
}

export const TIER_LIMITS: readonly TierLimit[] = [
  { tier: 'ANONYMOUS', identity: 'Client IP (no key)', perMinute: 30, perDay: 1_000 },
  { tier: 'FREE', identity: 'X-API-Key header', perMinute: 120, perDay: 20_000 },
  { tier: 'WEB', identity: 'Signed-in web session (JWT)', perMinute: 300, perDay: 50_000 },
  { tier: 'ADMIN', identity: 'Administrator', perMinute: 1_200, perDay: 1_000_000 },
];

/** Password rule enforced by the backend on register. */
export const PASSWORD_RULE = {
  minLength: 10,
  pattern: /^(?=.*[A-Za-z])(?=.*\d).+$/,
} as const;
