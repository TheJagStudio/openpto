import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { provideIcons } from '@ng-icons/core';
import { lucideTriangleAlert } from '@ng-icons/lucide';
import { catchError, distinctUntilChanged, filter, map, of, startWith, switchMap, tap } from 'rxjs';

import { ZardAlertComponent } from '@/shared/components/alert';
import { ZardCardImports } from '@/shared/components/card';
import { ZardInputComponent } from '@/shared/components/input';
import { ZardSelectImports } from '@/shared/components/select';
import { ZardSkeletonComponent } from '@/shared/components/skeleton';
import { ZardSwitchComponent } from '@/shared/components/switch';

import { FeesApi } from '../../core/api/fees.api';
import { humanize } from '../../core/format';
import { toProblem } from '../../core/http/problem';
import {
  type FeeQuote,
  type ProblemDetail,
  TRADEMARK_FILING_TYPES,
  type TrademarkFeeRequest,
  type TrademarkFilingType,
} from '../../core/models';
import { CALC_DEBOUNCE_MS, debounceIf } from './calc-tokens';
import { QuoteBreakdownComponent } from './quote-breakdown.component';
import { SaveQuoteButtonComponent } from './save-quote-button.component';

const FILING_LABELS: Record<TrademarkFilingType, string> = {
  APPLICATION: 'New application (base filing)',
  STATEMENT_OF_USE: 'Statement of use',
  EXTENSION_SOU: 'Extension of time to file SOU',
  SECTION_8: 'Section 8 declaration of use',
  SECTION_15: 'Section 15 incontestability',
  SECTION_9_RENEWAL: 'Section 9 renewal',
  SECTION_8_AND_9: 'Combined §8 & §9 renewal',
};

@Component({
  selector: 'app-trademark-fee-calculator',
  imports: [
    ReactiveFormsModule,
    ZardInputComponent,
    ZardSwitchComponent,
    ZardAlertComponent,
    ZardSkeletonComponent,
    ...ZardSelectImports,
    ...ZardCardImports,
    QuoteBreakdownComponent,
    SaveQuoteButtonComponent,
  ],
  viewProviders: [provideIcons({ lucideTriangleAlert })],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="grid gap-6 lg:grid-cols-[22rem_1fr]">
      <form [formGroup]="form" class="space-y-5" aria-label="Trademark fee request" (submit)="$event.preventDefault()">
        <z-card>
          <z-card-content class="space-y-4 pt-4">
            <div class="space-y-1.5">
              <span class="text-sm font-medium">Filing type</span>
              <z-select formControlName="filingType" zAriaLabel="Filing type">
                @for (t of filingTypes; track t) {
                  <z-select-item [zValue]="t">{{ labels[t] }}</z-select-item>
                }
              </z-select>
            </div>
            <div class="grid grid-cols-2 gap-3">
              <div class="space-y-1.5">
                <label for="tf-classes" class="text-sm font-medium">Classes</label>
                <input id="tf-classes" z-input type="number" min="1" max="45" formControlName="numberOfClasses" aria-describedby="tf-classes-err" />
                <p id="tf-classes-err" class="text-destructive text-xs" aria-live="polite">
                  @if (form.controls.numberOfClasses.invalid) {
                    Between 1 and 45.
                  }
                </p>
              </div>
              <div class="space-y-1.5">
                <label for="tf-blocks" class="text-sm font-medium">Extra text blocks</label>
                <input id="tf-blocks" z-input type="number" min="0" max="100" formControlName="extraCharacterBlocks" aria-describedby="tf-blocks-err" />
                <p id="tf-blocks-err" class="text-destructive text-xs" aria-live="polite">
                  @if (form.controls.extraCharacterBlocks.invalid) {
                    Between 0 and 100.
                  }
                </p>
              </div>
            </div>
            <div class="space-y-1.5">
              <label for="tf-date" class="text-sm font-medium">Filing date <span class="text-muted-foreground font-normal">(optional)</span></label>
              <input id="tf-date" z-input type="date" formControlName="filingDate" />
            </div>
            <div class="flex flex-col gap-3 border-t pt-4">
              <z-switch formControlName="insufficientInformation" zId="tf-insuff">Insufficient information surcharge</z-switch>
              <z-switch formControlName="freeFormTextIds" zId="tf-free">Free-form goods/services text</z-switch>
              <z-switch formControlName="inGracePeriod" zId="tf-grace">Filed in grace period</z-switch>
            </div>
          </z-card-content>
        </z-card>
      </form>

      <section class="min-w-0 space-y-4" aria-live="polite" [attr.aria-busy]="loading()" aria-label="Trademark fee estimate">
        @if (problem(); as p) {
          <z-alert zType="destructive" zIcon="lucideTriangleAlert" zRole="alert" [zTitle]="p.title ?? 'Could not calculate'" [zDescription]="p.detail ?? ''" />
        }
        @if (quote(); as q) {
          <z-card [class.opacity-60]="loading()">
            <z-card-header>
              <z-card-title>Itemized estimate</z-card-title>
              <z-card-description>{{ labels[value().filingType] }} · {{ value().numberOfClasses }} class(es)</z-card-description>
              <div z-card-action class="print:hidden">
                <app-save-quote-button kind="TRADEMARK" [request]="request()" [disabled]="loading()" />
              </div>
            </z-card-header>
            <z-card-content>
              <app-quote-breakdown [quote]="q" />
            </z-card-content>
          </z-card>
        } @else if (loading()) {
          <z-skeleton class="h-72 w-full" />
        }
      </section>
    </div>
  `,
})
export class TrademarkFeeCalculatorComponent {
  private readonly fees = inject(FeesApi);
  private readonly fb = inject(FormBuilder).nonNullable;
  private readonly debounceMs = inject(CALC_DEBOUNCE_MS);

  protected readonly filingTypes = TRADEMARK_FILING_TYPES;
  protected readonly labels = FILING_LABELS;
  protected readonly humanize = humanize;

  readonly form = this.fb.group({
    filingType: this.fb.control<TrademarkFilingType>('APPLICATION'),
    numberOfClasses: this.fb.control(1, [Validators.required, Validators.min(1), Validators.max(45)]),
    insufficientInformation: this.fb.control(false),
    freeFormTextIds: this.fb.control(false),
    extraCharacterBlocks: this.fb.control(0, [Validators.required, Validators.min(0), Validators.max(100)]),
    inGracePeriod: this.fb.control(false),
    filingDate: this.fb.control(''),
  });

  protected readonly loading = signal(false);
  protected readonly problem = signal<ProblemDetail | null>(null);
  protected readonly quote = signal<FeeQuote | null>(null);
  protected readonly value = toSignal(this.form.valueChanges.pipe(map(() => this.form.getRawValue())), {
    initialValue: this.form.getRawValue(),
  });
  protected readonly request = computed<TrademarkFeeRequest | null>(() => (this.value() && this.form.valid ? this.toRequest() : null));

  constructor() {
    this.form.valueChanges
      .pipe(
        startWith(null),
        map(() => this.form.getRawValue()),
        distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),
        debounceIf(this.debounceMs),
        filter(() => {
          if (this.form.invalid) this.quote.set(null);
          return this.form.valid;
        }),
        tap(() => {
          this.loading.set(true);
          this.problem.set(null);
        }),
        switchMap(() =>
          this.fees.trademark(this.toRequest()).pipe(
            catchError((e: unknown) => {
              this.problem.set(toProblem(e));
              return of(null);
            }),
          ),
        ),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe((q) => {
        this.loading.set(false);
        this.quote.set(q);
      });
  }

  private toRequest(): TrademarkFeeRequest {
    const v = this.form.getRawValue();
    return {
      filingType: v.filingType,
      numberOfClasses: v.numberOfClasses,
      insufficientInformation: v.insufficientInformation,
      freeFormTextIds: v.freeFormTextIds,
      extraCharacterBlocks: v.extraCharacterBlocks,
      inGracePeriod: v.inGracePeriod,
      ...(v.filingDate ? { filingDate: v.filingDate } : {}),
    };
  }
}
