import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';

import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideCalculator, lucideFileX, lucidePrinter } from '@ng-icons/lucide';

import { ZardButtonComponent } from '@/shared/components/button';
import { ZardCardImports } from '@/shared/components/card';
import { ZardEmptyComponent } from '@/shared/components/empty';
import { ZardSkeletonComponent } from '@/shared/components/skeleton';

import { FeesApi } from '../../core/api/fees.api';
import { humanize } from '../../core/format';
import { toProblem } from '../../core/http/problem';
import { BreadcrumbsComponent } from '../../ui/breadcrumbs.component';
import { CopyButtonComponent } from '../../ui/copy-button.component';
import { ErrorStateComponent } from '../../ui/error-state.component';
import { QuoteBreakdownComponent } from './quote-breakdown.component';

/** Read-only, print-friendly view of a saved quote (`/fees/quotes/:id`). */
@Component({
  selector: 'app-saved-quote',
  imports: [
    RouterLink,
    DatePipe,
    NgIcon,
    ZardButtonComponent,
    ZardSkeletonComponent,
    ZardEmptyComponent,
    ...ZardCardImports,
    BreadcrumbsComponent,
    CopyButtonComponent,
    ErrorStateComponent,
    QuoteBreakdownComponent,
  ],
  viewProviders: [provideIcons({ lucidePrinter, lucideCalculator, lucideFileX })],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="mx-auto max-w-4xl space-y-6 px-4 py-8 sm:px-6 print:max-w-none print:p-0">
      <app-breadcrumbs [items]="[{ label: 'Fees', link: '/fees' }, { label: 'Saved quote' }]" />

      @if (quote.isLoading()) {
        <z-skeleton class="h-10 w-72" />
        <z-skeleton class="h-80 w-full" />
      } @else if (notFound()) {
        <z-empty class="rounded-xl border border-dashed py-16" zIcon="lucideFileX" zTitle="Quote not found" zDescription="This link may be mistyped, or the quote was removed.">
          <a z-button routerLink="/fees"><ng-icon name="lucideCalculator" aria-hidden="true" /> New estimate</a>
        </z-empty>
      } @else if (quote.error()) {
        <app-error-state [error]="quote.error()" (retry)="quote.reload()" />
      } @else if (quote.hasValue()) {
        @let q = quote.value();
        <header class="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
          <div class="space-y-1">
            <p class="text-primary text-xs font-semibold tracking-wider uppercase">
              OpenPTO fee quote{{ q.kind ? ' · ' + humanize(q.kind) : '' }}
            </p>
            <h1 class="text-2xl font-semibold tracking-tight">{{ q.label || 'Fee estimate' }}</h1>
            <p class="text-muted-foreground text-sm">
              Quote <span class="font-mono">{{ q.id }}</span> · saved {{ q.createdAt | date: 'medium' }}
            </p>
          </div>
          <div class="flex gap-2 print:hidden">
            <app-copy-button [value]="shareUrl()" label="Copy link" type="outline" />
            <button type="button" z-button zType="outline" zSize="sm" (click)="print()">
              <ng-icon name="lucidePrinter" aria-hidden="true" /> Print
            </button>
          </div>
        </header>

        <z-card class="print:border-0 print:shadow-none">
          <z-card-content class="pt-4">
            <app-quote-breakdown [quote]="q" />
          </z-card-content>
        </z-card>

        <p class="text-muted-foreground text-xs">
          Totals were computed server-side by the OpenPTO fee service using schedule {{ q.scheduleCode }}. Estimates are
          illustrative and not legal advice; confirm amounts with the USPTO before filing.
        </p>
      }
    </div>
  `,
})
export class SavedQuotePage {
  private readonly api = inject(FeesApi);
  readonly id = input.required<string>();

  protected readonly humanize = humanize;
  protected readonly quote = rxResource({ params: () => this.id(), stream: ({ params }) => this.api.quote(params) });
  protected readonly notFound = computed(() => toProblem(this.quote.error()).status === 404);
  protected readonly shareUrl = computed(() => (typeof location !== 'undefined' ? location.href : ''));

  protected print(): void {
    window.print();
  }
}
