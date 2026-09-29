import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideCheck, lucideCircleAlert, lucideLandmark, lucideX } from '@ng-icons/lucide';

import { ZardAlertComponent } from '@/shared/components/alert';
import { ZardButtonComponent } from '@/shared/components/button';
import { ZardCardImports } from '@/shared/components/card';
import { ZardInputComponent } from '@/shared/components/input';

import { AuthApi } from '../../core/api/auth.api';
import { AuthStore } from '../../core/auth/auth.store';
import { fieldErrors, toProblem } from '../../core/http/problem';
import { PASSWORD_RULE, type ProblemDetail } from '../../core/models';
import { Notifier } from '../../core/notify/notifier.service';
import { safeReturnUrl } from './return-url';

type Field = 'displayName' | 'email' | 'password';

@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterLink, NgIcon, ZardButtonComponent, ZardInputComponent, ZardAlertComponent, ...ZardCardImports],
  viewProviders: [provideIcons({ lucideLandmark, lucideCheck, lucideX, lucideCircleAlert })],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex items-center justify-center px-4 py-12">
      <z-card class="w-full max-w-md">
        <z-card-header class="text-center">
          <span class="bg-primary text-primary-foreground mx-auto mb-2 grid size-10 place-items-center rounded-lg">
            <ng-icon name="lucideLandmark" class="size-5" aria-hidden="true" />
          </span>
          <z-card-title class="text-xl"><h1>Create your free account</h1></z-card-title>
          <z-card-description>No payment details. Get API keys and higher rate limits instantly.</z-card-description>
        </z-card-header>
        <z-card-content>
          <form [formGroup]="form" class="space-y-4" novalidate (ngSubmit)="submit()">
            @if (problem(); as p) {
              <z-alert zType="destructive" zIcon="lucideCircleAlert" zRole="alert" [zTitle]="p.title ?? 'Registration failed'" [zDescription]="p.detail ?? ''" />
            }
            <div class="space-y-1.5">
              <label for="reg-name" class="text-sm font-medium">Display name</label>
              <input id="reg-name" z-input autocomplete="name" formControlName="displayName" [attr.aria-invalid]="!!error('displayName')" aria-describedby="reg-name-err" />
              @if (error('displayName'); as e) {
                <p id="reg-name-err" class="text-destructive text-xs">{{ e }}</p>
              }
            </div>
            <div class="space-y-1.5">
              <label for="reg-email" class="text-sm font-medium">Email</label>
              <input id="reg-email" z-input type="email" autocomplete="email" formControlName="email" [attr.aria-invalid]="!!error('email')" aria-describedby="reg-email-err" />
              @if (error('email'); as e) {
                <p id="reg-email-err" class="text-destructive text-xs">{{ e }}</p>
              }
            </div>
            <div class="space-y-1.5">
              <label for="reg-password" class="text-sm font-medium">Password</label>
              <input
                id="reg-password"
                z-input
                type="password"
                autocomplete="new-password"
                formControlName="password"
                [attr.aria-invalid]="!!error('password')"
                aria-describedby="reg-password-rules"
              />
              <ul id="reg-password-rules" class="space-y-0.5 text-xs" aria-live="polite">
                @for (r of rules(); track r.label) {
                  <li class="flex items-center gap-1.5" [class]="r.ok ? 'text-emerald-700 dark:text-emerald-400' : 'text-muted-foreground'">
                    <ng-icon [name]="r.ok ? 'lucideCheck' : 'lucideX'" class="size-3.5" aria-hidden="true" />
                    {{ r.label }}<span class="sr-only">{{ r.ok ? ' — met' : ' — not met' }}</span>
                  </li>
                }
              </ul>
              @if (serverErrors()['password']; as e) {
                <p class="text-destructive text-xs">{{ e }}</p>
              }
            </div>
            <button type="submit" z-button zSize="lg" class="w-full" [zLoading]="busy()" [zDisabled]="busy()">Create account</button>
          </form>
        </z-card-content>
        <z-card-footer class="justify-center text-sm">
          <span class="text-muted-foreground">Already have an account?</span>
          <a routerLink="/login" [queryParams]="returnUrl() ? { returnUrl: returnUrl() } : {}" class="text-primary ml-1 font-medium hover:underline">Sign in</a>
        </z-card-footer>
      </z-card>
    </div>
  `,
})
export class RegisterPage {
  private readonly api = inject(AuthApi);
  private readonly auth = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly notifier = inject(Notifier);
  private readonly fb = inject(FormBuilder).nonNullable;

  readonly returnUrl = input<string>();

  /** Mirrors backend validation: password ≥ 10 chars with at least one letter and one digit. */
  protected readonly form = this.fb.group({
    displayName: ['', [Validators.required, Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    password: ['', [Validators.required, Validators.minLength(PASSWORD_RULE.minLength), Validators.pattern(PASSWORD_RULE.pattern)]],
  });
  protected readonly busy = signal(false);
  protected readonly problem = signal<ProblemDetail | null>(null);
  protected readonly serverErrors = signal<Record<string, string>>({});
  private readonly submitted = signal(false);
  private readonly password = toSignal(this.form.controls.password.valueChanges, { initialValue: '' });

  protected readonly rules = computed(() => {
    const pw = this.password();
    return [
      { label: `At least ${PASSWORD_RULE.minLength} characters`, ok: pw.length >= PASSWORD_RULE.minLength },
      { label: 'Contains a letter', ok: /[A-Za-z]/.test(pw) },
      { label: 'Contains a digit', ok: /\d/.test(pw) },
    ];
  });

  protected error(name: Field): string | null {
    const server = this.serverErrors()[name];
    if (server) return server;
    const c = this.form.controls[name];
    if (!c.invalid || !(c.touched || this.submitted())) return null;
    if (c.hasError('required')) return 'Required.';
    if (c.hasError('email')) return 'Enter a valid email address.';
    if (c.hasError('maxlength')) return 'Too long.';
    if (c.hasError('minlength') || c.hasError('pattern')) return 'Password does not meet the rules below.';
    return 'Invalid value.';
  }

  protected submit(): void {
    this.submitted.set(true);
    this.problem.set(null);
    this.serverErrors.set({});
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    const v = this.form.getRawValue();
    this.api.register({ email: v.email.trim(), password: v.password, displayName: v.displayName.trim() }).subscribe({
      next: (res) => {
        this.auth.signIn(res);
        this.notifier.success('Account created', { description: 'You are signed in. Create an API key to get started.' });
        const target = safeReturnUrl(this.returnUrl());
        void this.router.navigateByUrl(target === '/' ? '/developers' : target);
      },
      error: (err: unknown) => {
        this.busy.set(false);
        const p = toProblem(err);
        this.serverErrors.set(fieldErrors(p));
        this.problem.set(p.status === 409 ? { ...p, title: 'Email already registered', detail: 'Sign in instead, or use a different email.' } : p);
      },
    });
  }
}
