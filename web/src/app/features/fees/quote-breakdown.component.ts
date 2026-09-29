import { CurrencyPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideInfo, lucideTriangleAlert } from '@ng-icons/lucide';

import { ZardAlertComponent } from '@/shared/components/alert';
import { ZardBadgeComponent } from '@/shared/components/badge';
import { ZardTableImports } from '@/shared/components/table';

import { humanize } from '../../core/format';
import type { FeeQuote } from '../../core/models';

/** Itemized fee table: line items, subtotals by group, total, notes and warnings. */
@Component({
  selector: 'app-quote-breakdown',
  imports: [CurrencyPipe, NgIcon, ZardAlertComponent, ZardBadgeComponent, ...ZardTableImports],
  viewProviders: [provideIcons({ lucideInfo, lucideTriangleAlert })],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let q = quote();
    <div class="space-y-4">
      <div class="flex flex-wrap items-center gap-2 text-sm">
        <z-badge zType="outline">{{ q.scheduleCode }}</z-badge>
        <span class="text-muted-foreground">{{ q.scheduleName }}</span>
        @if (q.entitySize) {
          <z-badge zType="secondary">{{ humanize(q.entitySize) }} entity</z-badge>
        }
      </div>

      @for (w of q.warnings; track $index) {
        <z-alert zType="destructive" zIcon="lucideTriangleAlert" [zTitle]="w" zRole="alert" />
      }

      <div class="overflow-x-auto rounded-lg border">
        <table z-table data-testid="line-items">
          <caption class="sr-only">Itemized fees</caption>
          <thead z-table-header>
            <tr z-table-row>
              <th z-table-head scope="col" class="w-24">Code</th>
              <th z-table-head scope="col">Description</th>
              <th z-table-head scope="col" class="w-16 text-right">Qty</th>
              <th z-table-head scope="col" class="w-28 text-right">Unit</th>
              <th z-table-head scope="col" class="w-28 text-right">Amount</th>
            </tr>
          </thead>
          <tbody z-table-body>
            @for (li of q.lineItems; track $index) {
              <tr z-table-row data-testid="line-item">
                <td z-table-cell class="font-mono text-xs">{{ li.feeCode }}</td>
                <td z-table-cell class="whitespace-normal">{{ li.description }}</td>
                <td z-table-cell class="tabular text-right">{{ li.quantity }}</td>
                <td z-table-cell class="tabular text-right">{{ li.unitAmount | currency: q.currency }}</td>
                <td z-table-cell class="tabular text-right font-medium">{{ li.amount | currency: q.currency }}</td>
              </tr>
            } @empty {
              <tr z-table-row>
                <td z-table-cell colspan="5" class="text-muted-foreground py-6 text-center">No fees apply to this request.</td>
              </tr>
            }
          </tbody>
          <tfoot z-table-footer>
            @for (s of q.subtotals; track s.group) {
              <tr z-table-row>
                <td z-table-cell colspan="4" class="text-muted-foreground text-right">{{ humanize(s.group) }} subtotal</td>
                <td z-table-cell class="tabular text-right">{{ s.amount | currency: q.currency }}</td>
              </tr>
            }
            <tr z-table-row>
              <th z-table-cell colspan="4" scope="row" class="text-right text-base font-semibold">Total</th>
              <td z-table-cell class="tabular text-right text-base font-semibold" data-testid="quote-total">
                {{ q.total | currency: q.currency }}
              </td>
            </tr>
          </tfoot>
        </table>
      </div>

      @if (q.notes.length) {
        <ul class="text-muted-foreground space-y-1 text-sm">
          @for (n of q.notes; track $index) {
            <li class="flex gap-2">
              <ng-icon name="lucideInfo" class="mt-0.5 size-4 shrink-0" aria-hidden="true" />
              <span>{{ n }}</span>
            </li>
          }
        </ul>
      }
    </div>
  `,
})
export class QuoteBreakdownComponent {
  readonly quote = input.required<FeeQuote>();
  protected readonly humanize = humanize;
}
