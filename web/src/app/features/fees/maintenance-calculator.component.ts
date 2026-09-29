import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { provideIcons } from '@ng-icons/core';
import { lucideCalendar, lucideTriangleAlert } from '@ng-icons/lucide';
import { catchError, distinctUntilChanged, filter, map, of, startWith, switchMap, tap } from 'rxjs';

import { ZardAlertComponent } from '@/shared/components/alert';
import { ZardCardImports } from '@/shared/components/card';
import { ZardInputComponent } from '@/shared/components/input';
import { ZardRadioGroupImports } from '@/shared/components/radio-group';
import { ZardSkeletonComponent } from '@/shared/components/skeleton';

import { FeesApi } from '../../core/api/fees.api';
import { humanize } from '../../core/format';
import { toProblem } from '../../core/http/problem';
import {
  ENTITY_SIZES,
  type EntitySize,
  type MaintenanceRequest,
  type MaintenanceSchedule,
  type ProblemDetail,
} from '../../core/models';
import { StatusBadgeComponent } from '../../ui/status-badge.component';
import { CALC_DEBOUNCE_MS, debounceIf } from './calc-tokens';
import { SaveQuoteButtonComponent } from './save-quote-button.component';

@Component({
  selector: 'app-maintenance-calculator',
  imports: [
    ReactiveFormsModule,
    CurrencyPipe,
    DatePipe,
    ZardInputComponent,
    ZardAlertComponent,
    ZardSkeletonComponent,
    ...ZardCardImports,
    ...ZardRadioGroupImports,
    StatusBadgeComponent,
    SaveQuoteButtonComponent,
  ],
  viewProviders: [provideIcons({ lucideCalendar, lucideTriangleAlert })],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="grid gap-6 lg:grid-cols-[22rem_1fr]">
      <form [formGroup]="form" class="space-y-5" aria-label="Maintenance fee request" (submit)="$event.preventDefault()">
        <z-card>
          <z-card-content class="space-y-4 pt-4">
            <div class="space-y-1.5">
              <label for="mf-grant" class="text-sm font-medium">Grant date</label>
              <input id="mf-grant" z-input type="date" formControlName="grantDate" required aria-describedby="mf-grant-hint" />
              <p id="mf-grant-hint" class="text-muted-foreground text-xs">Fees are due 3.5, 7.5 and 11.5 years after grant.</p>
            </div>
            <div class="space-y-1.5">
              <label for="mf-asof" class="text-sm font-medium">As of <span class="text-muted-foreground font-normal">(optional)</span></label>
              <input id="mf-asof" z-input type="date" formControlName="asOfDate" />
            </div>
            <fieldset class="space-y-2">
              <legend class="text-sm font-medium">Entity size</legend>
              <z-radio-group formControlName="entitySize" name="mf-entity" class="grid-cols-3">
                @for (s of entitySizes; track s) {
                  <div class="flex items-center gap-2"><z-radio [value]="s" [zId]="'mf-entity-' + s" /><label [for]="'mf-entity-' + s" class="text-sm">{{ humanize(s) }}</label></div>
                }
              </z-radio-group>
            </fieldset>
          </z-card-content>
        </z-card>
      </form>

      <section class="min-w-0 space-y-4" aria-live="polite" [attr.aria-busy]="loading()" aria-label="Maintenance fee windows">
        @if (problem(); as p) {
          <z-alert zType="destructive" zIcon="lucideTriangleAlert" zRole="alert" [zTitle]="p.title ?? 'Could not calculate'" [zDescription]="p.detail ?? ''" />
        }
        @if (!form.valid && !problem()) {
          <z-alert zIcon="lucideCalendar" zTitle="Enter a grant date" zDescription="The three maintenance windows and their current status will appear here." />
        }
        @if (result(); as r) {
          <z-card>
            <z-card-header>
              <z-card-title>Maintenance windows</z-card-title>
              <z-card-description>
                Schedule {{ r.scheduleCode }}
                @if (r.patentExpired) {
                  · <span class="text-destructive font-medium">Patent expired for non-payment</span>
                }
              </z-card-description>
              <div z-card-action class="print:hidden">
                <app-save-quote-button kind="MAINTENANCE" [request]="request()" [disabled]="loading()" />
              </div>
            </z-card-header>
            <z-card-content>
              <ol class="relative space-y-4 border-l pl-6" [class.opacity-60]="loading()">
                @for (w of r.windows; track w.stage) {
                  <li class="relative">
                    <span
                      class="ring-background absolute top-1.5 -left-[29px] size-3 rounded-full ring-4"
                      [class]="w.status === 'OPEN' ? 'bg-emerald-500' : w.status === 'GRACE_PERIOD' ? 'bg-amber-500' : w.status === 'EXPIRED' ? 'bg-red-500' : 'bg-muted-foreground/50'"
                      aria-hidden="true"
                    ></span>
                    <div class="bg-card rounded-lg border p-4">
                      <div class="flex flex-wrap items-center justify-between gap-2">
                        <h3 class="font-semibold">{{ w.stage }}-year fee</h3>
                        <app-status-badge [status]="w.status" />
                      </div>
                      <dl class="mt-3 grid grid-cols-2 gap-3 text-sm sm:grid-cols-4">
                        <div><dt class="text-muted-foreground text-xs">Window opens</dt><dd>{{ w.windowOpens | date: 'mediumDate' }}</dd></div>
                        <div><dt class="text-muted-foreground text-xs">Due (no surcharge)</dt><dd>{{ w.dueDate | date: 'mediumDate' }}</dd></div>
                        <div><dt class="text-muted-foreground text-xs">Grace ends</dt><dd>{{ w.graceEnds | date: 'mediumDate' }}</dd></div>
                        <div>
                          <dt class="text-muted-foreground text-xs">If paid as of date</dt>
                          <dd class="tabular font-semibold">{{ w.totalIfPaidOnAsOfDate | currency: 'USD' }}</dd>
                        </div>
                      </dl>
                      <p class="text-muted-foreground mt-2 text-xs">
                        Fee {{ w.fee | currency: 'USD' }}
                        @if (w.surcharge) {
                          + surcharge {{ w.surcharge | currency: 'USD' }}
                        }
                      </p>
                    </div>
                  </li>
                }
              </ol>
            </z-card-content>
          </z-card>
        } @else if (loading()) {
          <z-skeleton class="h-72 w-full" />
        }
      </section>
    </div>
  `,
})
export class MaintenanceCalculatorComponent {
  private readonly fees = inject(FeesApi);
  private readonly fb = inject(FormBuilder).nonNullable;
  private readonly debounceMs = inject(CALC_DEBOUNCE_MS);

  protected readonly entitySizes = ENTITY_SIZES;
  protected readonly humanize = humanize;

  readonly form = this.fb.group({
    grantDate: this.fb.control('', Validators.required),
    asOfDate: this.fb.control(''),
    entitySize: this.fb.control<EntitySize>('LARGE'),
  });

  protected readonly loading = signal(false);
  protected readonly problem = signal<ProblemDetail | null>(null);
  protected readonly result = signal<MaintenanceSchedule | null>(null);
  private readonly value = toSignal(this.form.valueChanges.pipe(map(() => this.form.getRawValue())), {
    initialValue: this.form.getRawValue(),
  });
  protected readonly request = computed<MaintenanceRequest | null>(() => {
    const v = this.value();
    return v.grantDate ? this.toRequest() : null;
  });

  constructor() {
    this.form.valueChanges
      .pipe(
        startWith(null),
        map(() => this.form.getRawValue()),
        distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),
        debounceIf(this.debounceMs),
        filter(() => {
          if (this.form.invalid) this.result.set(null);
          return this.form.valid;
        }),
        tap(() => {
          this.loading.set(true);
          this.problem.set(null);
        }),
        switchMap(() =>
          this.fees.maintenance(this.toRequest()).pipe(
            catchError((e: unknown) => {
              this.problem.set(toProblem(e));
              return of(null);
            }),
          ),
        ),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe((r) => {
        this.loading.set(false);
        this.result.set(r);
      });
  }

  private toRequest(): MaintenanceRequest {
    const v = this.form.getRawValue();
    return { entitySize: v.entitySize, grantDate: v.grantDate, ...(v.asOfDate ? { asOfDate: v.asOfDate } : {}) };
  }
}
