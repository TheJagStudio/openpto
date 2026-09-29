export type ServiceHealth = 'UP' | 'DOWN';

export interface ServiceStatus {
  name: string;
  url: string;
  status: ServiceHealth;
  latencyMs: number | null;
}

/** `GET /gateway/status`. */
export interface GatewayStatus {
  services: ServiceStatus[];
}
