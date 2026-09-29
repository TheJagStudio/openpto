export interface NavItem {
  label: string;
  path: string;
  icon: string;
  /** Exact-match active state (for `/`). */
  exact?: boolean;
  description: string;
}

export const PRIMARY_NAV: readonly NavItem[] = [
  { label: 'Patents', path: '/patents', icon: 'lucideFileText', description: 'Search granted patents and applications' },
  { label: 'Trademarks', path: '/trademarks', icon: 'lucideStamp', description: 'TSDR-style trademark status' },
  { label: 'Fees', path: '/fees', icon: 'lucideCalculator', description: 'Fee calculator and schedules' },
  { label: 'Data Pipeline', path: '/pipeline', icon: 'lucideDatabase', description: 'Convert legacy XML to JSON' },
  { label: 'Developers', path: '/developers', icon: 'lucideCode', description: 'API keys, usage and docs' },
  { label: 'Status', path: '/status', icon: 'lucideActivity', description: 'Live service health' },
];
