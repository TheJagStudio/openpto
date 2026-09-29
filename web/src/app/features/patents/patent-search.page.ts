import { DatePipe, DecimalPipe, NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, DestroyRef, effect, inject, signal, untracked } from '@angular/core';
import { rxResource, takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { NgIcon, provideIcons } from '@ng-icons/core';
import {
  lucideDownload,
  lucideFileText,
  lucideSearch,
  lucideSlidersHorizontal,
  lucideX,
} from '@ng-icons/lucide';
import { debounceTime, distinctUntilChanged, skip } from 'rxjs';

import { ZardBadgeComponent } from '@/shared/components/badge';
import { ZardButtonComponent } from '@/shared/components/button';
import { ZardCardImports } from '@/shared/components/card';
import { ZardEmptyComponent } from '@/shared/components/empty';
import { ZardInputComponent } from '@/shared/components/input';
import { ZardSelectImports } from '@/shared/components/select';
import { ZardSkeletonComponent } from '@/shared/components/skeleton';
import { ZardTableImports } from '@/shared/components/table';

import { PatentsApi } from '../../core/api/patents.api';
import { humanize } from '../../core/format';
import { PATENT_STATUSES, PATENT_TYPES, type PatentSearchParams, type ValueCount } from '../../core/models';
import { ErrorStateComponent } from '../../ui/error-state.component';
import { PageHeaderComponent } from '../../ui/page-header.component';
import { PagerComponent } from '../../ui/pager.component';
import { StatusBadgeComponent } from '../../ui/status-badge.component';
import { PATENT_FILTER_KEYS, type PatentFilterKey, parsePatentQuery, patchFor } from './patent-query';

const FILTER_LABELS: Record<PatentFilterKey, string> = {
  type: 'Type',
  status: 'Status',
  cpc: 'CPC',
  assignee: 'Assignee',
  inventor: 'Inventor',
  filedFrom: 'Filed from',
  filedTo: 'Filed to',
  grantedFrom: 'Granted from',
  grantedTo: 'Granted to',
};

const CPC_SECTIONS: Record<string, string> = {
  A: 'Human necessities',
  B: 'Operations & transport',
  C: 'Chemistry & metallurgy',
  D: 'Textiles & paper',
  E: 'Fixed constructions',
  F: 'Mechanical engineering',
  G: 'Physics',
  H: 'Electricity',
  Y: 'Emerging technologies',
};

@Component({
  selector: 'app-patent-search',
  imports: [
    RouterLink,
    NgIcon,
    DatePipe,
    DecimalPipe,
    NgTemplateOutlet,
    ZardButtonComponent,
    ZardBadgeComponent,
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
  viewProviders: [provideIcons({ lucideSearch, lucideX, lucideSlidersHorizontal, lucideDownload, lucideFileText })],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './patent-search.page.html',
})
export class PatentSearchPage {
  private readonly api = inject(PatentsApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly types = PATENT_TYPES;
  protected readonly statuses = PATENT_STATUSES;
  protected readonly humanize = humanize;
  protected readonly cpcLabel = (section: string): string =>
    CPC_SECTIONS[section] ? `${section} — ${CPC_SECTIONS[section]}` : section;
  protected readonly sortOptions = [
    { value: '', label: 'Best match' },
    { value: 'grantDate,desc', label: 'Newest granted' },
    { value: 'grantDate,asc', label: 'Oldest granted' },
    { value: 'filingDate,desc', label: 'Newest filed' },
    { value: 'patentNumber,asc', label: 'Patent number' },
  ];

  private readonly queryParamMap = toSignal(this.route.queryParamMap, { requireSync: true });

  /** Single source of truth: the URL. Everything else derives from it. */
  readonly criteria = computed(() => parsePatentQuery(this.queryParamMap()));

  /** Facets ignore paging/sort, so they only reload when a filter changes. */
  private readonly facetCriteria = computed(
    () => {
      const { page: _p, size: _s, sort: _o, ...filters } = this.criteria();
      return filters;
    },
    { equal: (a, b) => JSON.stringify(a) === JSON.stringify(b) },
  );

  protected readonly results = rxResource({
    params: () => this.criteria(),
    stream: ({ params }) => this.api.search(params),
  });

  protected readonly facets = rxResource({
    params: () => this.facetCriteria(),
    stream: ({ params }) => this.api.facets(params),
  });

  /** Search box text; pushed to the URL after a 350 ms pause. */
  protected readonly qInput = signal(this.criteria().q ?? '');
  protected readonly filtersOpen = signal(false);

  protected readonly activeFilters = computed(() => {
    const c = this.criteria();
    return PATENT_FILTER_KEYS.filter((k) => c[k] !== undefined).map((k) => ({
      key: k,
      label: FILTER_LABELS[k],
      value: k === 'type' || k === 'status' ? humanize(String(c[k])) : String(c[k]),
    }));
  });

  protected readonly exportJson = computed(() => this.api.datasetUrl('json', this.criteria()));
  protected readonly exportCsv = computed(() => this.api.datasetUrl('csv', this.criteria()));

  constructor() {
    // URL → input (back/forward, facet clicks, shared links).
    effect(() => {
      const q = this.criteria().q ?? '';
      untracked(() => {
        if (q !== this.qInput().trim()) this.qInput.set(q);
      });
    });

    // input → URL (debounced). skip(1): the initial value came from the URL already.
    toObservable(this.qInput)
      .pipe(skip(1), debounceTime(350), distinctUntilChanged(), takeUntilDestroyed(inject(DestroyRef)))
      .subscribe((q) => {
        if (q.trim() !== (this.criteria().q ?? '')) this.update({ q: q.trim() || null }, true);
      });
  }

  /** Merges a change into the URL. Typing replaces history; explicit actions push. */
  update(changes: Partial<Record<keyof PatentSearchParams, string | number | null>>, replaceUrl = false): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: patchFor(changes),
      queryParamsHandling: 'merge',
      replaceUrl,
    });
  }

  protected submitSearch(event: Event): void {
    event.preventDefault();
    this.update({ q: this.qInput().trim() || null });
  }

  protected onSelect(key: 'type' | 'status' | 'sort', value: string | string[]): void {
    const v = Array.isArray(value) ? (value[0] ?? '') : value;
    this.update({ [key]: v || null });
  }

  protected onText(key: PatentFilterKey, event: Event): void {
    const value = (event.target as HTMLInputElement).value.trim();
    if ((this.criteria()[key] ?? '') !== value) this.update({ [key]: value || null });
  }

  protected goToPage(page: number): void {
    this.update({ page: page || null });
    if (typeof window !== 'undefined') window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  protected clearFilter(key: PatentFilterKey): void {
    this.update({ [key]: null });
  }

  protected clearAll(): void {
    this.qInput.set('');
    void this.router.navigate([], { relativeTo: this.route, queryParams: {} });
  }

  protected toggleFacet(key: 'type' | 'status' | 'cpc' | 'assignee', value: string): void {
    this.update({ [key]: this.criteria()[key] === value ? null : value });
  }

  protected isFacetActive(key: 'type' | 'status' | 'cpc' | 'assignee', value: string): boolean {
    return this.criteria()[key] === value;
  }

  protected toggleYear(year: string): void {
    const from = `${year}-01-01`;
    const active = this.criteria().grantedFrom === from;
    this.update({ grantedFrom: active ? null : from, grantedTo: active ? null : `${year}-12-31` });
  }

  protected isYearActive(year: string): boolean {
    return this.criteria().grantedFrom === `${year}-01-01` && this.criteria().grantedTo === `${year}-12-31`;
  }

  protected maxCount(rows: ValueCount<string>[]): number {
    return Math.max(1, ...rows.map((r) => r.count));
  }
}
