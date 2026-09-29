import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';

import { NgIcon, provideIcons } from '@ng-icons/core';
import {
  lucideArrowRight,
  lucideCalculator,
  lucideDatabase,
  lucideFileCode,
  lucideSearch,
  lucideShield,
  lucideZap,
} from '@ng-icons/lucide';

import { ZardBadgeComponent } from '@/shared/components/badge';
import { ZardButtonComponent } from '@/shared/components/button';
import { ZardCardImports } from '@/shared/components/card';
import { ZardInputComponent } from '@/shared/components/input';
import { ZardSkeletonComponent } from '@/shared/components/skeleton';

import { StatusApi } from '../../core/api/status.api';
import { humanize } from '../../core/format';
import { BarChartComponent, type BarDatum } from '../../ui/bar-chart.component';
import { CodeTabsComponent, type CodeSample } from '../../ui/code-tabs.component';

@Component({
  selector: 'app-landing',
  imports: [
    RouterLink,
    NgIcon,
    DecimalPipe,
    DatePipe,
    ZardButtonComponent,
    ZardBadgeComponent,
    ZardInputComponent,
    ZardSkeletonComponent,
    ...ZardCardImports,
    BarChartComponent,
    CodeTabsComponent,
  ],
  viewProviders: [
    provideIcons({ lucideArrowRight, lucideSearch, lucideCalculator, lucideDatabase, lucideFileCode, lucideZap, lucideShield }),
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './landing.page.html',
})
export class LandingPage {
  private readonly statusApi = inject(StatusApi);
  private readonly router = inject(Router);

  protected readonly stats = rxResource({ stream: () => this.statusApi.stats() });
  protected readonly query = signal('');

  protected readonly byYear = computed<BarDatum[]>(() => {
    const s = this.stats.hasValue() ? this.stats.value() : undefined;
    return (s?.patentsByYear ?? [])
      .slice()
      .sort((a, b) => a.year - b.year)
      .slice(-15)
      .map((y) => ({ label: String(y.year).slice(-2), title: String(y.year), value: y.count }));
  });

  protected readonly tmStatuses = computed(() => {
    const s = this.stats.hasValue() ? this.stats.value() : undefined;
    const rows = s?.trademarksByStatus ?? [];
    const total = rows.reduce((sum, r) => sum + r.count, 0) || 1;
    return rows.map((r) => ({ label: humanize(r.value), count: r.count, pct: Math.round((r.count / total) * 100) }));
  });

  protected readonly products = [
    {
      icon: 'lucideFileCode',
      title: 'Open Data API',
      body: 'Full-text patent and trademark search with facets, bulk JSON/CSV exports and generous free rate limits.',
      link: '/patents',
      cta: 'Search patents',
    },
    {
      icon: 'lucideDatabase',
      title: 'XML → JSON Pipeline',
      body: 'Upload USPTO bulk XML (grants, publications, legacy PATDOC, trademark dailies) and get clean JSON records.',
      link: '/pipeline',
      cta: 'Open the pipeline',
    },
    {
      icon: 'lucideCalculator',
      title: 'Fee Calculator',
      body: 'Itemized filing, maintenance and trademark fees for large, small and micro entities — shareable quotes.',
      link: '/fees',
      cta: 'Estimate fees',
    },
  ];

  protected readonly snippets: CodeSample[] = [
    {
      id: 'curl',
      label: 'curl',
      code: `curl -s "http://localhost:8080/api/v1/patents?q=neural+network&cpc=G06N&size=5" \\
  -H "X-API-Key: $OPENPTO_KEY" | jq '.content[].title'`,
    },
    {
      id: 'js',
      label: 'JavaScript',
      code: `const res = await fetch('/api/v1/patents?q=battery&status=GRANTED&sort=grantDate,desc', {
  headers: { 'X-API-Key': process.env.OPENPTO_KEY },
});
const { content, totalElements } = await res.json();
console.log(totalElements, content.map((p) => p.patentNumber));`,
    },
    {
      id: 'py',
      label: 'Python',
      code: `import requests

r = requests.get("http://localhost:8080/api/v1/trademarks",
                 params={"q": "coffee", "niceClass": 30},
                 headers={"X-API-Key": OPENPTO_KEY})
for tm in r.json()["content"]:
    print(tm["serialNumber"], tm["markText"], tm["status"])`,
    },
  ];

  protected search(event: Event): void {
    event.preventDefault();
    const q = this.query().trim();
    void this.router.navigate(['/patents'], { queryParams: q ? { q } : {} });
  }
}
