import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideCircleAlert, lucideEye, lucideEyeOff, lucideLandmark } from '@ng-icons/lucide';

import { ZardAlertComponent } from '@/shared/components/alert';
import { ZardButtonComponent } from '@/shared/components/button';
import { ZardCardImports } from '@/shared/components/card';
import { ZardInputComponent } from '@/shared/components/input';

import { AuthApi } from '../../core/api/auth.api';
import { AuthStore } from '../../core/auth/auth.store';
import { toProblem } from '../../core/http/problem';
import type { ProblemDetail } from '../../core/models';
import { Notifier } from '../../core/notify/notifier.service';
import { safeReturnUrl } from './return-url';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink, NgIcon, ZardButtonComponent, ZardInputComponent, ZardAlertComponent, ...ZardCardImports],
  viewProviders: [provideIcons({ lucideLandmark, lucideEye, lucideEyeOff, lucideCircleAlert })],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex min-h-[calc(100dvh-3.5rem-12rem)] items-center justify-center px-4 py-12">
      <z-card class="w-full max-w-sm">
        <z-card-header class="text-center">
          <span class="bg-primary text-primary-foreground mx-auto mb-2 grid size-10 place-items-center rounded-lg">
            <ng-icon name="lucideLandmark" class="size-5" aria-hidden="true" />
          </span>
          <z-card-title class="text-xl"><h1>Sign in to OpenPTO</h1></z-card-title>
          <z-card-description>Manage API keys, upload data and save quotes.</z-card-description>
        </z-card-header>
        <z-card-content>
          <form [formGroup]="form" class="space-y-4" novalidate (ngSubmit)="submit()">
            @if (problem(); as p) {
              <z-alert zType="destructive" zIcon="lucideCircleAlert" zRole="alert" [zTitle]="p.title ?? 'Sign-in failed'" [zDescription]="p.detail ?? ''" />
            }
            <div class="space-y-1.5">
              <label for="login-email" class="text-sm font-medium">Email</label>
              <input
                id="login-email"
                z-input
                type="email"
                autocomplete="username"
                formControlName="email"
                [attr.aria-invalid]="showError('email')"
                aria-describedby="login-email-err"
              />
              @if (showError('email')) {
                <p id="login-email-err" class="text-destructive text-xs">Enter a valid email address.</p>
              }
            </div>
            <div class="space-y-1.5">
              <label for="login-password" class="text-sm font-medium">Password</label>
              <div class="relative">
                <input
                  id="login-password"
                  z-input
                  [type]="reveal() ? 'text' : 'password'"
                  autocomplete="current-password"
                  formControlName="password"
                  class="pr-10"
                  [attr.aria-invalid]="showError('password')"
                  aria-describedby="login-password-err"
                />
                <button
                  type="button"
                  class="text-muted-foreground hover:text-foreground focus-visible:ring-ring/50 absolute top-1/2 right-1 grid size-7 -translate-y-1/2 place-items-center rounded-md outline-none focus-visible:ring-2"
                  [attr.aria-label]="reveal() ? 'Hide password' : 'Show password'"
                  [attr.aria-pressed]="reveal()"
                  (click)="reveal.set(!reveal())"
                >
                  <ng-icon [name]="reveal() ? 'lucideEyeOff' : 'lucideEye'" aria-hidden="true" />
                </button>
              </div>
              @if (showError('password')) {
                <p id="login-password-err" class="text-destructive text-xs">Password is required.</p>
              }
            </div>
            <button type="submit" z-button class="w-full" zSize="lg" [zLoading]="busy()" [zDisabled]="busy()">Sign in</button>
          </form>
        </z-card-content>
        <z-card-footer class="justify-center text-sm">
          <span class="text-muted-foreground">New here?</span>
          <a routerLink="/register" [queryParams]="returnUrl() ? { returnUrl: returnUrl() } : {}" class="text-primary ml-1 font-medium hover:underline">Create an account</a>
        </z-card-footer>
      </z-card>
    </div>
  `,
})
export class LoginPage {
  private readonly api = inject(AuthApi);
  private readonly auth = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly notifier = inject(Notifier);
  private readonly fb = inject(FormBuilder).nonNullable;

  readonly returnUrl = input<string>();

  protected readonly form = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });
  protected readonly busy = signal(false);
  protected readonly reveal = signal(false);
  protected readonly problem = signal<ProblemDetail | null>(null);
  private readonly submitted = signal(false);
  protected readonly target = computed(() => safeReturnUrl(this.returnUrl()));

  protected showError(name: 'email' | 'password'): boolean {
    const c = this.form.controls[name];
    return c.invalid && (c.touched || this.submitted());
  }

  protected submit(): void {
    this.submitted.set(true);
    this.problem.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.api.login(this.form.getRawValue()).subscribe({
      next: (res) => {
        this.auth.signIn(res);
        this.notifier.success(`Welcome back, ${res.user.displayName}`);
        void this.router.navigateByUrl(this.target());
      },
      error: (err: unknown) => {
        this.busy.set(false);
        const p = toProblem(err);
        this.problem.set(
          p.status === 401
            ? { ...p, title: 'Incorrect email or password', detail: p.detail ?? 'Check your details and try again.' }
            : p.status === 423 || p.status === 429
              ? { ...p, title: 'Too many attempts', detail: p.detail ?? 'Sign-in is temporarily locked. Try again in 15 minutes.' }
              : p,
        );
      },
    });
  }
}
