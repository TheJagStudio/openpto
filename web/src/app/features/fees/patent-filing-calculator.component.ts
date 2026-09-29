import { CurrencyPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { provideIcons } from '@ng-icons/core';
import { lucideCalculator, lucideTriangleAlert } from '@ng-icons/lucide';
import { catchError, distinctUntilChanged, filter, forkJoin, map, of, startWith, switchMap, tap } from 'rxjs';

import { ZardAlertComponent } from '@/shared/components/alert';
import { ZardCardImports } from '@/shared/components/card';
import { ZardInputComponent } from '@/shared/components/input';
import { ZardRadioGroupImports } from '@/shared/components/radio-group';
import { ZardSelectImports } from '@/shared/components/select';
import { ZardSkeletonComponent } from '@/shared/components/skeleton';
import { ZardSwitchComponent } from '@/shared/components/switch';

import { FeesApi } from '../../core/api/fees.api';
import { humanize } from '../../core/format';
import { toProblem } from '../../core/http/problem';
import {
  APPLICATION_TYPES,
  CONTINUED_EXAMINATIONS,
  ENTITY_SIZES,
  type EntitySize,
  type FeeQuote,
  type PatentFilingRequest,
  type ProblemDetail,
} from '../../core/models';
import { CALC_DEBOUNCE_MS, debounceIf, independentNotAboveTotal } from './calc-tokens';
import { QuoteBreakdownComponent } from './quote-breakdown.component';
import { SaveQuoteButtonComponent } from './save-quote-button.component';

type Comparison = Record<EntitySize, FeeQuote>;

/** Application types that cannot use Track One prioritized examination. */
const NO_TRACK_ONE = new Set(['DESIGN', 'PLANT', 'PROVISIONAL']);

@Component({
  selector: 'app-patent-filing-calculator',
  imports: [
    ReactiveFormsModule,
    CurrencyPipe,
    ZardInputComponent,
    ZardSwitchComponent,
    ZardAlertComponent,
    ZardSkeletonComponent,
    ...ZardSelectImports,
    ...ZardRadioGroupImports,
    ...ZardCardImports,
    QuoteBreakdownComponent,
    SaveQuoteButtonComponent,
  ],
  viewProviders: [provideIcons({ lucideCalculator, lucideTriangleAlert })],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './patent-filing-calculator.component.html',
})
export class PatentFilingCalculatorComponent {
  private readonly fees = inject(FeesApi);
  private readonly fb = inject(FormBuilder).nonNullable;
  private readonly debounceMs = inject(CALC_DEBOUNCE_MS);

  protected readonly applicationTypes = APPLICATION_TYPES;
  protected readonly entitySizes = ENTITY_SIZES;
  protected readonly continuedExaminations = CONTINUED_EXAMINATIONS;
  protected readonly humanize = humanize;
  protected readonly extensionOptions = [0, 1, 2, 3, 4, 5];

  readonly form = this.fb.group(
    {
      applicationType: this.fb.control<PatentFilingRequest['applicationType']>('UTILITY'),
      entitySize: this.fb.control<EntitySize>('LARGE'),
      totalClaims: this.fb.control(20, [Validators.required, Validators.min(0), Validators.max(500)]),
      independentClaims: this.fb.control(3, [Validators.required, Validators.min(0), Validators.max(100)]),
      multipleDependentClaims: this.fb.control(false),
      specificationSheets: this.fb.control(40, [Validators.required, Validators.min(0), Validators.max(10000)]),
      filedElectronically: this.fb.control(true),
      lateFilingSurcharge: this.fb.control(false),
      /** String because z-select values are strings; converted in toRequest(). */
      extensionMonths: this.fb.control('0'),
      continuedExamination: this.fb.control<PatentFilingRequest['continuedExamination']>('NONE'),
      prioritizedExamination: this.fb.control(false),
      filingDate: this.fb.control(''),
    },
    { validators: independentNotAboveTotal },
  );

  protected readonly loading = signal(false);
  protected readonly problem = signal<ProblemDetail | null>(null);
  protected readonly comparison = signal<Comparison | null>(null);

  private readonly value = toSignal(this.form.valueChanges.pipe(map(() => this.form.getRawValue())), {
    initialValue: this.form.getRawValue(),
  });
  protected readonly entity = computed(() => this.value().entitySize);
  readonly quote = computed(() => this.comparison()?.[this.entity()] ?? null);
  protected readonly trackOneAllowed = computed(() => !NO_TRACK_ONE.has(this.value().applicationType));
  protected readonly trackOneOverLimit = computed(
    () => this.value().prioritizedExamination && (this.value().independentClaims > 4 || this.value().totalClaims > 30),
  );
  protected readonly request = computed<PatentFilingRequest | null>(() =>
    this.form.valid ? this.toRequest(this.value().entitySize) : null,
  );

  constructor() {
    const destroyRef = inject(DestroyRef);

    // Track One is not available for design / plant / provisional applications.
    this.form.controls.applicationType.valueChanges.pipe(takeUntilDestroyed(destroyRef)).subscribe((type) => {
      const ctrl = this.form.controls.prioritizedExamination;
      if (NO_TRACK_ONE.has(type)) {
        ctrl.setValue(false, { emitEvent: false });
        ctrl.disable({ emitEvent: false });
      } else {
        ctrl.enable({ emitEvent: false });
      }
    });

    // Live recalculation: one request per entity size so the comparison is always in sync.
    // Changing only the entity size re-uses the comparison (no refetch).
    this.form.valueChanges
      .pipe(
        startWith(null),
        map(() => this.form.getRawValue()),
        map(({ entitySize: _e, ...rest }) => rest),
        distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),
        debounceIf(this.debounceMs),
        filter(() => {
          if (this.form.invalid) {
            this.comparison.set(null);
            return false;
          }
          return true;
        }),
        tap(() => {
          this.loading.set(true);
          this.problem.set(null);
        }),
        switchMap(() =>
          forkJoin({
            LARGE: this.fees.patentFiling(this.toRequest('LARGE')),
            SMALL: this.fees.patentFiling(this.toRequest('SMALL')),
            MICRO: this.fees.patentFiling(this.toRequest('MICRO')),
          }).pipe(
            catchError((err: unknown) => {
              this.problem.set(toProblem(err));
              return of(null);
            }),
          ),
        ),
        takeUntilDestroyed(destroyRef),
      )
      .subscribe((result) => {
        this.loading.set(false);
        this.comparison.set(result);
      });
  }

  protected savings(size: EntitySize): number | null {
    const c = this.comparison();
    if (!c || !c.LARGE.total) return null;
    return Math.round((1 - c[size].total / c.LARGE.total) * 100);
  }

  protected selectEntity(size: EntitySize): void {
    this.form.controls.entitySize.setValue(size);
  }

  protected fieldError(name: 'totalClaims' | 'independentClaims' | 'specificationSheets'): string | null {
    const c = this.form.controls[name];
    if (!c.errors) {
      return name === 'independentClaims' && this.form.hasError('independentAboveTotal')
        ? 'Cannot exceed total claims.'
        : null;
    }
    if (c.hasError('required')) return 'Required.';
    if (c.hasError('min')) return 'Must be 0 or more.';
    if (c.hasError('max')) return `Maximum is ${c.getError('max').max}.`;
    return 'Invalid value.';
  }

  private toRequest(entitySize: EntitySize): PatentFilingRequest {
    const v = this.form.getRawValue();
    return {
      applicationType: v.applicationType,
      entitySize,
      totalClaims: v.totalClaims,
      independentClaims: v.independentClaims,
      multipleDependentClaims: v.multipleDependentClaims,
      specificationSheets: v.specificationSheets,
      filedElectronically: v.filedElectronically,
      lateFilingSurcharge: v.lateFilingSurcharge,
      extensionMonths: Number(v.extensionMonths) || 0,
      continuedExamination: v.continuedExamination,
      prioritizedExamination: v.prioritizedExamination,
      ...(v.filingDate ? { filingDate: v.filingDate } : {}),
    };
  }
}
