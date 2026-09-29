import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { type ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import {
  provideRouter,
  TitleStrategy,
  withComponentInputBinding,
  withInMemoryScrolling,
  withRouterConfig,
  withViewTransitions,
} from '@angular/router';

import { provideZard } from '@/shared/core/provider/providezard';

import { routes } from './app.routes';
import { authInterceptor, errorInterceptor, requestIdInterceptor } from './core/http/interceptors';
import { OpenPtoTitleStrategy } from './core/title.strategy';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(
      routes,
      withComponentInputBinding(),
      withInMemoryScrolling({ scrollPositionRestoration: 'top', anchorScrolling: 'enabled' }),
      withRouterConfig({ paramsInheritanceStrategy: 'always' }),
      withViewTransitions({ skipInitialTransition: true }),
    ),
    // Order matters: request id → auth header → error handling (sees the final request).
    provideHttpClient(withFetch(), withInterceptors([requestIdInterceptor, authInterceptor, errorInterceptor])),
    { provide: TitleStrategy, useClass: OpenPtoTitleStrategy },
    provideZard(),
  ],
};
