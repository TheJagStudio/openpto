import { InjectionToken } from '@angular/core';
import type { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

import { type MonoTypeOperatorFunction, debounceTime, identity } from 'rxjs';

/** Debounce for live recalculation. Tests provide 0 to recompute synchronously. */
export const CALC_DEBOUNCE_MS = new InjectionToken<number>('CALC_DEBOUNCE_MS', {
  providedIn: 'root',
  factory: () => 300,
});

export function debounceIf<T>(ms: number): MonoTypeOperatorFunction<T> {
  return ms > 0 ? debounceTime<T>(ms) : identity;
}

/** Group validator: independentClaims ≤ totalClaims (mirrors the backend rule). */
export const independentNotAboveTotal: ValidatorFn = (group: AbstractControl): ValidationErrors | null => {
  const total = group.get('totalClaims')?.value;
  const independent = group.get('independentClaims')?.value;
  return typeof total === 'number' && typeof independent === 'number' && independent > total
    ? { independentAboveTotal: true }
    : null;
};
