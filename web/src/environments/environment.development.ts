import type { Environment } from './environment.model';

/** `ng serve` configuration: relative URLs, proxied to the gateway on :8080 by proxy.conf.json. */
export const environment: Environment = {
  production: false,
  apiBaseUrl: '',
  appName: 'OpenPTO',
  swaggerUiUrl: '/swagger-ui.html',
  jobPollMs: 3000,
  statusPollMs: 15000,
};
