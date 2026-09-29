import { inject } from '@angular/core';
import { type CanActivateFn, Router } from '@angular/router';

import { Notifier } from '../notify/notifier.service';
import { AuthStore } from './auth.store';

/** Requires a valid session; otherwise redirects to /login?returnUrl=<target>. */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthStore);
  if (auth.token()) return true;
  return inject(Router).createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

/** Requires the ADMIN role; anonymous users go to login, signed-in non-admins go home. */
export const adminGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthStore);
  const router = inject(Router);
  if (!auth.token()) {
    return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
  }
  if (auth.isAdmin()) return true;
  inject(Notifier).warning('Administrators only', { description: 'Your account does not have access to that page.' });
  return router.createUrlTree(['/']);
};

/** Keeps signed-in users away from /login and /register. */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthStore);
  return auth.token() ? inject(Router).createUrlTree(['/']) : true;
};
