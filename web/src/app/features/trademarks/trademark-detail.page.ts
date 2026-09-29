import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';

import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideArrowLeft, lucideDownload, lucideFileX } from '@ng-icons/lucide';

import { ZardBadgeComponent } from '@/shared/components/badge';
import { ZardButtonComponent } from '@/shared/components/button';
import { ZardCardImports } from '@/shared/components/card';
import { ZardEmptyComponent } from '@/shared/components/empty';
import { ZardSkeletonComponent } from '@/shared/components/skeleton';

import { TrademarksApi } from '../../core/api/trademarks.api';
import { humanize } from '../../core/format';
import { toProblem } from '../../core/http/problem';
import type { FilingBasis, GoodsAndServices } from '../../core/models';
import { toneOf } from '../../core/status-tone';
import { OpenPtoTitleStrategy } from '../../core/title.strategy';
import { BreadcrumbsComponent } from '../../ui/breadcrumbs.component';
import { CopyButtonComponent } from '../../ui/copy-button.component';
import { saveJson } from '../../ui/download';
import { ErrorStateComponent } from '../../ui/error-state.component';
import { StatusBadgeComponent } from '../../ui/status-badge.component';

const BASIS: Record<FilingBasis, string> = {
  '1A': 'Section 1(a) — use in commerce',
  '1B': 'Section 1(b) — intent to use',
  '44D': 'Section 44(d) — foreign priority',
  '44E': 'Section 44(e) — foreign registration',
  '66A': 'Section 66(a) — Madrid Protocol',
};

@Component({
  selector: 'app-trademark-detail',
  imports: [
    RouterLink,
    DatePipe,
    NgIcon,
    ZardButtonComponent,
    ZardBadgeComponent,
    ZardSkeletonComponent,
    ZardEmptyComponent,
    ...ZardCardImports,
    BreadcrumbsComponent,
    StatusBadgeComponent,
    ErrorStateComponent,
    CopyButtonComponent,
  ],
  viewProviders: [provideIcons({ lucideArrowLeft, lucideFileX, lucideDownload })],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './trademark-detail.page.html',
})
export class TrademarkDetailPage {
  private readonly api = inject(TrademarksApi);
  private readonly titles = inject(OpenPtoTitleStrategy);

  readonly serialNumber = input.required<string>();

  protected readonly tm = rxResource({
    params: () => this.serialNumber(),
    stream: ({ params }) => this.api.get(params),
  });

  protected readonly notFound = computed(() => toProblem(this.tm.error()).status === 404);
  protected readonly humanize = humanize;
  protected readonly isLive = computed(() => (this.tm.hasValue() ? this.tm.value().status.startsWith('LIVE') : false));
  protected readonly bannerBorder = computed(() => {
    const tone = this.tm.hasValue() ? toneOf(this.tm.value().status) : 'neutral';
    return tone === 'success'
      ? 'border-emerald-600/30'
      : tone === 'info'
        ? 'border-sky-600/30'
        : tone === 'danger'
          ? 'border-red-600/30'
          : '';
  });

  /** Goods & services grouped by Nice class, ascending. */
  protected readonly goodsByClass = computed(() => {
    if (!this.tm.hasValue()) return [];
    const groups = new Map<number, GoodsAndServices[]>();
    for (const g of this.tm.value().goodsAndServices ?? []) {
      groups.set(g.niceClass, [...(groups.get(g.niceClass) ?? []), g]);
    }
    return [...groups.entries()].sort(([a], [b]) => a - b).map(([niceClass, items]) => ({ niceClass, items }));
  });

  /** Prosecution history, newest first. */
  protected readonly events = computed(() =>
    this.tm.hasValue() ? [...(this.tm.value().events ?? [])].sort((a, b) => b.date.localeCompare(a.date)) : [],
  );

  constructor() {
    effect(() => {
      if (this.tm.hasValue()) this.titles.setPageTitle(`${this.tm.value().markText} (${this.tm.value().serialNumber})`);
    });
  }

  protected basis(b: FilingBasis | null): string {
    return b ? BASIS[b] ?? b : '—';
  }

  protected download(): void {
    if (this.tm.hasValue()) saveJson(this.tm.value(), `trademark-${this.serialNumber()}.json`);
  }
}
