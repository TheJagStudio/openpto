/**
 * Maps every domain status to a semantic tone. The UI renders tones consistently
 * (`<app-status-badge>`), so "green = good" means the same thing on every page.
 */
export type Tone = 'success' | 'info' | 'warning' | 'danger' | 'neutral' | 'progress';

const TONES: Record<string, Tone> = {
  // patents
  GRANTED: 'success',
  PENDING: 'info',
  ABANDONED: 'danger',
  EXPIRED: 'neutral',
  // trademarks
  LIVE_REGISTERED: 'success',
  LIVE_PENDING: 'info',
  DEAD_ABANDONED: 'danger',
  DEAD_CANCELLED: 'neutral',
  // ingest
  QUEUED: 'neutral',
  PARSING: 'progress',
  TRANSFORMED: 'progress',
  LOADING: 'progress',
  COMPLETED: 'success',
  PARTIAL: 'warning',
  FAILED: 'danger',
  // maintenance windows
  NOT_YET_OPEN: 'neutral',
  OPEN: 'success',
  GRACE_PERIOD: 'warning',
  PAID_WINDOW_PASSED: 'neutral',
  // services
  UP: 'success',
  DOWN: 'danger',
  // stages
  DONE: 'success',
  SUCCEEDED: 'success',
  RUNNING: 'progress',
  SKIPPED: 'neutral',
};

export function toneOf(status: string | null | undefined): Tone {
  return (status && TONES[status.toUpperCase()]) || 'neutral';
}

/** Tailwind classes per tone — tuned for AA contrast in light and dark themes. */
export const TONE_CLASSES: Record<Tone, string> = {
  success: 'bg-emerald-50 text-emerald-800 ring-emerald-600/20 dark:bg-emerald-500/10 dark:text-emerald-300 dark:ring-emerald-400/25',
  info: 'bg-sky-50 text-sky-800 ring-sky-600/20 dark:bg-sky-500/10 dark:text-sky-300 dark:ring-sky-400/25',
  warning: 'bg-amber-50 text-amber-900 ring-amber-600/25 dark:bg-amber-500/10 dark:text-amber-300 dark:ring-amber-400/25',
  danger: 'bg-red-50 text-red-800 ring-red-600/20 dark:bg-red-500/10 dark:text-red-300 dark:ring-red-400/25',
  neutral: 'bg-muted text-muted-foreground ring-border',
  progress: 'bg-violet-50 text-violet-800 ring-violet-600/20 dark:bg-violet-500/10 dark:text-violet-300 dark:ring-violet-400/25',
};

export const TONE_DOT: Record<Tone, string> = {
  success: 'bg-emerald-500',
  info: 'bg-sky-500',
  warning: 'bg-amber-500',
  danger: 'bg-red-500',
  neutral: 'bg-muted-foreground/60',
  progress: 'bg-violet-500 animate-pulse',
};
