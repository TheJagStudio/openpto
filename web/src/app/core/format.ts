/** Presentation helpers shared by templates (pure functions, easy to test). */

const ACRONYMS = new Set(['SOU', 'RCE', 'CPC', 'US', 'JSON', 'XML', 'API', 'PATDOC']);

/** `LIVE_PENDING` → `Live pending`, `SECTION_8_AND_9` → `Section 8 and 9`. */
export function humanize(value: string | null | undefined): string {
  if (!value) return '';
  const words = value.split('_').map((w) => (ACRONYMS.has(w) ? w : w.toLowerCase()));
  const first = words[0] ?? '';
  words[0] = ACRONYMS.has(first) ? first : first.charAt(0).toUpperCase() + first.slice(1);
  return words.join(' ');
}

export function formatBytes(bytes: number | null | undefined): string {
  if (bytes === null || bytes === undefined || !Number.isFinite(bytes)) return '—';
  if (bytes < 1024) return `${bytes} B`;
  const units = ['KB', 'MB', 'GB'];
  let value = bytes / 1024;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit++;
  }
  return `${value.toFixed(value < 10 ? 1 : 0)} ${units[unit]}`;
}

export function formatDuration(ms: number | null | undefined): string {
  if (ms === null || ms === undefined || !Number.isFinite(ms)) return '—';
  if (ms < 1000) return `${Math.round(ms)} ms`;
  const s = ms / 1000;
  if (s < 60) return `${s.toFixed(1)} s`;
  const m = Math.floor(s / 60);
  return `${m} min ${Math.round(s % 60)} s`;
}

const usd = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });
export function formatUsd(amount: number | null | undefined): string {
  return amount === null || amount === undefined ? '—' : usd.format(amount);
}

const compact = new Intl.NumberFormat('en-US', { notation: 'compact', maximumFractionDigits: 1 });
export function formatCompact(n: number | null | undefined): string {
  return n === null || n === undefined ? '—' : compact.format(n);
}

/** Today in the user's local calendar as `YYYY-MM-DD`. */
export function todayIso(now: Date = new Date()): string {
  const y = now.getFullYear();
  const m = String(now.getMonth() + 1).padStart(2, '0');
  const d = String(now.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}
