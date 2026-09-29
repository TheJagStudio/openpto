import type { Routes } from '@angular/router';

import { adminGuard, authGuard, guestGuard } from './core/auth/guards';

/** Every feature is lazy-loaded; titles feed OpenPtoTitleStrategy ("Title · OpenPTO"). */
export const routes: Routes = [
  { path: '', title: '', loadComponent: () => import('./features/landing/landing.page').then((m) => m.LandingPage) },
  {
    path: 'patents',
    children: [
      {
        path: '',
        title: 'Patent search',
        loadComponent: () => import('./features/patents/patent-search.page').then((m) => m.PatentSearchPage),
      },
      {
        path: ':patentNumber',
        title: 'Patent',
        loadComponent: () => import('./features/patents/patent-detail.page').then((m) => m.PatentDetailPage),
      },
    ],
  },
  {
    path: 'trademarks',
    children: [
      {
        path: '',
        title: 'Trademark search',
        loadComponent: () => import('./features/trademarks/trademark-search.page').then((m) => m.TrademarkSearchPage),
      },
      {
        path: ':serialNumber',
        title: 'Trademark',
        loadComponent: () => import('./features/trademarks/trademark-detail.page').then((m) => m.TrademarkDetailPage),
      },
    ],
  },
  {
    path: 'fees',
    children: [
      {
        path: '',
        title: 'Fee calculator',
        loadComponent: () => import('./features/fees/fee-calculator.page').then((m) => m.FeeCalculatorPage),
      },
      {
        path: 'schedule',
        title: 'Fee schedule',
        loadComponent: () => import('./features/fees/fee-schedule.page').then((m) => m.FeeSchedulePage),
      },
      {
        path: 'quotes/:id',
        title: 'Saved quote',
        loadComponent: () => import('./features/fees/saved-quote.page').then((m) => m.SavedQuotePage),
      },
    ],
  },
  {
    path: 'pipeline',
    canActivate: [authGuard],
    children: [
      {
        path: '',
        title: 'Data pipeline',
        loadComponent: () => import('./features/pipeline/pipeline.page').then((m) => m.PipelinePage),
      },
      {
        path: 'jobs/:id',
        title: 'Ingest job',
        loadComponent: () => import('./features/pipeline/job-detail.page').then((m) => m.JobDetailPage),
      },
    ],
  },
  {
    path: 'developers',
    title: 'Developer portal',
    loadComponent: () => import('./features/developers/developers.page').then((m) => m.DevelopersPage),
  },
  {
    path: 'status',
    title: 'System status',
    loadComponent: () => import('./features/status/status.page').then((m) => m.StatusPage),
  },
  {
    path: 'admin',
    title: 'Admin',
    canActivate: [adminGuard],
    loadComponent: () => import('./features/admin/admin.page').then((m) => m.AdminPage),
  },
  {
    path: 'login',
    title: 'Sign in',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login.page').then((m) => m.LoginPage),
  },
  {
    path: 'register',
    title: 'Create account',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/register.page').then((m) => m.RegisterPage),
  },
  {
    path: '**',
    title: 'Page not found',
    loadComponent: () => import('./features/not-found/not-found.page').then((m) => m.NotFoundPage),
  },
];
