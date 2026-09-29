export interface Environment {
  production: boolean;
  /** Prefix for all API calls. Empty = same origin (dev server proxies to the gateway). */
  apiBaseUrl: string;
  appName: string;
  /** Gateway Swagger UI, embedded in the developer portal. */
  swaggerUiUrl: string;
  /** Poll interval (ms) for ingest jobs that are still running. */
  jobPollMs: number;
  /** Poll interval (ms) for the status page. */
  statusPollMs: number;
}
