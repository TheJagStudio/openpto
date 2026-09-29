import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { of } from 'rxjs';

import { FeesApi } from '../../core/api/fees.api';
import type { EntitySize, FeeQuote, PatentFilingRequest } from '../../core/models';
import { CALC_DEBOUNCE_MS } from './calc-tokens';
import { PatentFilingCalculatorComponent } from './patent-filing-calculator.component';

const RATE: Record<EntitySize, number> = { LARGE: 1, SMALL: 0.4, MICRO: 0.2 };

/** Deterministic fake of the fee service: filing fee + $100 per claim over 20. */
function fakeQuote(req: PatentFilingRequest): FeeQuote {
  const r = RATE[req.entitySize];
  const excess = Math.max(0, req.totalClaims - 20);
  const lineItems = [
    { feeCode: '1011', description: 'Basic filing fee - Utility', quantity: 1, unitAmount: 350 * r, amount: 350 * r },
    ...(excess
      ? [{ feeCode: '1202', description: 'Claims in excess of 20', quantity: excess, unitAmount: 100 * r, amount: excess * 100 * r }]
      : []),
  ];
  const total = lineItems.reduce((s, li) => s + li.amount, 0);
  return {
    scheduleCode: 'FY2025',
    scheduleName: 'Fee schedule effective 2025-01-19',
    entitySize: req.entitySize,
    lineItems,
    subtotals: [{ group: 'FILING', amount: total }],
    total,
    currency: 'USD',
    notes: ['Illustrative amounts'],
    warnings: [],
  };
}

describe('PatentFilingCalculatorComponent', () => {
  let patentFiling: ReturnType<typeof vi.fn>;

  async function create() {
    const fixture = TestBed.createComponent(PatentFilingCalculatorComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    patentFiling = vi.fn((req: PatentFilingRequest) => of(fakeQuote(req)));
    TestBed.configureTestingModule({
      imports: [PatentFilingCalculatorComponent],
      providers: [
        provideHttpClient(),
        provideRouter([]),
        { provide: CALC_DEBOUNCE_MS, useValue: 0 },
        { provide: FeesApi, useValue: { patentFiling, saveQuote: vi.fn() } },
      ],
    });
  });

  it('calculates on load for all three entity sizes and renders the line items', async () => {
    const fixture = await create();
    const el = fixture.nativeElement as HTMLElement;

    expect(patentFiling).toHaveBeenCalledTimes(3);
    const sizes = patentFiling.mock.calls.map(([r]) => (r as PatentFilingRequest).entitySize).sort();
    expect(sizes).toEqual(['LARGE', 'MICRO', 'SMALL']);

    const rows = el.querySelectorAll('[data-testid="line-item"]');
    expect(rows.length).toBe(1);
    expect(rows[0]!.textContent).toContain('Basic filing fee - Utility');
    expect(el.querySelector('[data-testid="quote-total"]')!.textContent).toContain('$350.00');
    expect(el.querySelector('[data-testid="compare-SMALL"]')!.textContent).toContain('$140');
    expect(el.querySelector('[data-testid="compare-MICRO"]')!.textContent).toContain('$70');
  });

  it('recomputes when the form changes and shows the new line items', async () => {
    const fixture = await create();
    patentFiling.mockClear();

    fixture.componentInstance.form.controls.totalClaims.setValue(25);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(patentFiling).toHaveBeenCalledTimes(3);
    expect((patentFiling.mock.calls[0]![0] as PatentFilingRequest).totalClaims).toBe(25);
    const el = fixture.nativeElement as HTMLElement;
    const rows = el.querySelectorAll('[data-testid="line-item"]');
    expect(rows.length).toBe(2);
    expect(rows[1]!.textContent).toContain('Claims in excess of 20');
    expect(el.querySelector('[data-testid="quote-total"]')!.textContent).toContain('$850.00');
  });

  it('switching entity size reuses the comparison without refetching', async () => {
    const fixture = await create();
    patentFiling.mockClear();

    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('[data-testid="compare-MICRO"]')!.click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(patentFiling).not.toHaveBeenCalled();
    expect(fixture.componentInstance.quote()?.entitySize).toBe('MICRO');
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-testid="quote-total"]')!.textContent).toContain('$70.00');
  });

  it('does not call the service while the form is invalid (independent > total)', async () => {
    const fixture = await create();
    patentFiling.mockClear();

    fixture.componentInstance.form.patchValue({ totalClaims: 2, independentClaims: 5 });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(patentFiling).not.toHaveBeenCalled();
    expect(fixture.componentInstance.quote()).toBeNull();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Cannot exceed total claims.');
  });
});
