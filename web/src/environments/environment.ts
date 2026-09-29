import type { Environment } from './environment.model';

/**
 * Production configuration. The SPA is served from the same origin as the gateway
 * (CloudFront → S3 for the app, `/api/*` behaviour → API Gateway), so every call is relative.
 */
export const environment: Environment = {
  production: true,
  apiBaseUrl: '',
  appName: 'OpenPTO',
  swaggerUiUrl: '/swagger-ui.html',
  jobPollMs: 3000,
  statusPollMs: 15000,
};
