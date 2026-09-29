import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, DestroyRef, effect, inject, signal, untracked } from '@angular/core';
import { rxResource, takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, type ParamMap, type Params, Router, RouterLink } from '@angular/router';

import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideDownload, lucideSearch, lucideStamp, lucideX } from '@ng-icons/lucide';
import { debounceTime, distinctUntilChanged, skip } from 'rxjs';

import { ZardButtonComponent } from '@/shared/components/button';
import { ZardCardImports } from '@/shared/components/card';
import { ZardEmptyComponent } from '@/shared/components/empty';
import { ZardInputComponent } from '@/shared/components/input';
import { ZardSelectImports } from '@/shared/components/select';
import { ZardSkeletonComponent } from '@/shared/components/skeleton';
import { ZardTableImports } from '@/shared/components/table';

import { TrademarksApi } from '../../core/api/trademarks.api';
import { humanize } from '../../core/format';
import { TRADEMARK_STATUSES, type TrademarkSearchParams, type TrademarkStatus } from '../../core/models';
import { ErrorStateComponent } from '../../ui/error-state.component';
import { PageHeaderComponent } from '../../ui/page-header.component';
import { PagerComponent } from '../../ui/pager.component';
import { StatusBadgeComponent } from '../../ui/status-badge.component';

const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;

/** URL → typed trademark criteria (invalid values dropped, paging clamped). */
export function parseTrademarkQuery(p: ParamMap): TrademarkSearchParams {
  const status = p.get('status')?.toUpperCase();
  const cls = Number.parseInt(p.get('niceClass') ?? '', 10);
  const page = Number.parseInt(p.get('page') ?? '', 10);
  const size = Number.parseInt(p.get('size') ?? '', 10);
  const out: TrademarkSearchParams = {
    q: p.get('q')?.trim() || undefined,
    status: status && (TRADEMARK_STATUSES as readonly string[]).includes(status) ? (status as TrademarkStatus) : undefined,
    niceClass: cls >= 1 && cls <= 45 ? cls : undefined,
    owner: p.get('owner')?.trim() || undefined,
    filedFrom: DATE_RE.test(p.get('filedFrom') ?? '') ? (p.get('filedFrom') ?? undefined) : undefined,
    filedTo: DATE_RE.test(p.get('filedTo') ?? '') ? (p.get('filedTo') ?? undefined) : undefined,
    page: Number.isFinite(page) && page > 0 ? page : 0,
    size: Number.isFinite(size) ? Math.min(100, Math.max(1, size)) : 20,
    sort: p.get('sort')?.trim() || undefined,
  };
  return Object.fromEntries(Object.entries(out).filter(([, v]) => v !== undefined)) as TrademarkSearchParams;
}

@Component({
  selector: 'app-trademark-search',
  imports: [
    RouterLink,
    NgIcon,
    DatePipe,
    DecimalPipe,
    ZardButtonComponent,
    ZardInputComponent,
    ZardSkeletonComponent,
    ZardEmptyComponent,
    ...ZardSelectImports,
    ...ZardTableImports,
    ...ZardCardImports,
    PageHeaderComponent,
    PagerComponent,
    StatusBadgeComponent,
    ErrorStateComponent,
  ],
  viewProviders: [provideIcons({ lucideSearch, lucideX, lucideDownload, lucideStamp })],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './trademark-search.page.html',
})
export class TrademarkSearchPage {
  private readonly api = inject(TrademarksApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly statuses = TRADEMARK_STATUSES;
  protected readonly classes = Array.from({ length: 45 }, (_, i) => i + 1);
  protected readonly humanize = humanize;
  protected readonly sortOptions = [
    { value: '', label: 'Best match' },
    { value: 'filingDate,desc', label: 'Newest filed' },
    { value: 'registrationDate,desc', label: 'Newest registered' },
    { value: 'serialNumber,asc', label: 'Serial number' },
  ];

  private readonly params = toSignal(this.route.queryParamMap, { requireSync: true });
  readonly criteria = computed(() => parseTrademarkQuery(this.params()));

  protected readonly results = rxResource({
    params: () => this.criteria(),
    stream: ({ params }) => this.api.search(params),
  });

  protected readonly qInput = signal(this.criteria().q ?? '');
  protected readonly hasFilters = computed(() => {
    const { page: _p, size: _s, sort: _o, ...f } = this.criteria();
    return Object.keys(f).length > 0;
  });
  protected readonly exportCsv = computed(() => this.api.datasetUrl('csv', this.criteria()));

  constructor() {
    effect(() => {
      const q = this.criteria().q ?? '';
      untracked(() => {
        if (q !== this.qInput().trim()) this.qInput.set(q);
      });
    });
    toObservable(this.qInput)
      .pipe(skip(1), debounceTime(350), distinctUntilChanged(), takeUntilDestroyed(inject(DestroyRef)))
      .subscribe((q) => {
        if (q.trim() !== (this.criteria().q ?? '')) this.update({ q: q.trim() || null }, true);
      });
  }

  update(changes: Params, replaceUrl = false): void {
    const patch: Params = { ...changes };
    for (const k of Object.keys(patch)) if (patch[k] === '') patch[k] = null;
    if (!('page' in changes)) patch['page'] = null;
    void this.router.navigate([], { relativeTo: this.route, queryParams: patch, queryParamsHandling: 'merge', replaceUrl });
  }

  protected submit(event: Event): void {
    event.preventDefault();
    this.update({ q: this.qInput().trim() || null });
  }

  protected onSelect(key: 'status' | 'niceClass' | 'sort', value: string | string[]): void {
    const v = Array.isArray(value) ? (value[0] ?? '') : value;
    this.update({ [key]: v || null });
  }

  protected onText(key: 'owner' | 'filedFrom' | 'filedTo', event: Event): void {
    const v = (event.target as HTMLInputElement).value.trim();
    if ((this.criteria()[key] ?? '') !== v) this.update({ [key]: v || null });
  }

  protected clearAll(): void {
    this.qInput.set('');
    void this.router.navigate([], { relativeTo: this.route, queryParams: {} });
  }

  protected goToPage(page: number): void {
    this.update({ page: page || null });
    if (typeof window !== 'undefined') window.scrollTo({ top: 0, behavior: 'smooth' });
  }
}
