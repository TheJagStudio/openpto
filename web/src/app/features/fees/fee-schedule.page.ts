import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideCalculator, lucidePrinter, lucideSearch } from '@ng-icons/lucide';

import { ZardBadgeComponent } from '@/shared/components/badge';
import { ZardButtonComponent } from '@/shared/components/button';
import { ZardEmptyComponent } from '@/shared/components/empty';
import { ZardInputComponent } from '@/shared/components/input';
import { ZardSelectImports } from '@/shared/components/select';
import { ZardSkeletonComponent } from '@/shared/components/skeleton';
import { ZardTableImports } from '@/shared/components/table';

import { FeesApi } from '../../core/api/fees.api';
import { humanize } from '../../core/format';
import type { FeeCategory, FeeItem, FeeUnit } from '../../core/models';
import { BreadcrumbsComponent } from '../../ui/breadcrumbs.component';
import { ErrorStateComponent } from '../../ui/error-state.component';
import { PageHeaderComponent } from '../../ui/page-header.component';

const UNIT_LABEL: Record<FeeUnit, string> = {
  EACH: '',
  PER_CLAIM: 'per claim',
  PER_CLASS: 'per class',
  PER_50_SHEETS: 'per 50 sheets',
  PER_MONTH: 'per month',
};

@Component({
  selector: 'app-fee-schedule',
  imports: [
    RouterLink,
    CurrencyPipe,
    DatePipe,
    NgIcon,
    ZardButtonComponent,
    ZardBadgeComponent,
    ZardInputComponent,
    ZardSkeletonComponent,
    ZardEmptyComponent,
    ...ZardSelectImports,
    ...ZardTableImports,
    PageHeaderComponent,
    BreadcrumbsComponent,
    ErrorStateComponent,
  ],
  viewProviders: [provideIcons({ lucideSearch, lucidePrinter, lucideCalculator })],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './fee-schedule.page.html',
})
export class FeeSchedulePage {
  private readonly api = inject(FeesApi);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  /** Query params (component input binding): `?code=FY2023&category=TRADEMARK`. */
  readonly code = input<string>();
  readonly category = input<string>();

  protected readonly filter = signal('');
  protected readonly unitLabel = UNIT_LABEL;
  protected readonly humanize = humanize;

  protected readonly selectedCode = computed(() => this.code() || 'current');
  protected readonly selectedCategory = computed<FeeCategory>(() =>
    this.category()?.toUpperCase() === 'TRADEMARK' ? 'TRADEMARK' : 'PATENT',
  );

  protected readonly schedules = rxResource({ stream: () => this.api.schedules() });
  protected readonly schedule = rxResource({
    params: () => ({ code: this.selectedCode(), category: this.selectedCategory() }),
    stream: ({ params }) => this.api.schedule(params.code, params.category),
  });

  /** Items filtered by the search box, grouped like the USPTO page (one table per group). */
  protected readonly groups = computed(() => {
    if (!this.schedule.hasValue()) return [];
    const needle = this.filter().trim().toLowerCase();
    const items = this.schedule
      .value()
      .items.filter((i) => !i.category || i.category === this.selectedCategory())
      .filter((i) => !needle || i.feeCode.toLowerCase().includes(needle) || i.description.toLowerCase().includes(needle));
    const map = new Map<string, FeeItem[]>();
    for (const item of items) map.set(item.group, [...(map.get(item.group) ?? []), item]);
    return [...map.entries()].map(([group, rows]) => ({ group, rows }));
  });
  protected readonly matchCount = computed(() => this.groups().reduce((n, g) => n + g.rows.length, 0));

  protected setParam(key: 'code' | 'category', value: string | string[]): void {
    const v = Array.isArray(value) ? value[0] : value;
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { [key]: v && v !== 'current' ? v : null },
      queryParamsHandling: 'merge',
    });
  }

  protected print(): void {
    window.print();
  }
}
