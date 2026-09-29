import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';

import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideCircleCheck, lucideCircleX, lucideRefreshCw, lucideServer } from '@ng-icons/lucide';

import { ZardButtonComponent } from '@/shared/components/button';
import { ZardCardImports } from '@/shared/components/card';
import { ZardSkeletonComponent } from '@/shared/components/skeleton';
import { ZardSwitchComponent } from '@/shared/components/switch';

import { environment } from '../../../environments/environment';
import { StatusApi } from '../../core/api/status.api';
import { humanize } from '../../core/format';
import type { ServiceStatus } from '../../core/models';
import { ErrorStateComponent } from '../../ui/error-state.component';
import { PageHeaderComponent } from '../../ui/page-header.component';
import { StatusBadgeComponent } from '../../ui/status-badge.component';

const DESCRIPTIONS: Record<string, string> = {
  gateway: 'API gateway — keys, rate limits, routing',
  odp: 'Open Data service — patents, trademarks, accounts',
  'odp-service': 'Open Data service — patents, trademarks, accounts',
  fees: 'Fee calculation service',
  'fee-service': 'Fee calculation service',
  ingest: 'Ingest service — XML → JSON pipeline',
  'ingest-service': 'Ingest service — XML → JSON pipeline',
};

@Component({
  selector: 'app-status',
  imports: [
    DatePipe,
    NgIcon,
    ZardButtonComponent,
    ZardSkeletonComponent,
    ZardSwitchComponent,
    ...ZardCardImports,
    PageHeaderComponent,
    StatusBadgeComponent,
    ErrorStateComponent,
  ],
  viewProviders: [provideIcons({ lucideRefreshCw, lucideServer, lucideCircleCheck, lucideCircleX })],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="mx-auto max-w-5xl space-y-6 px-4 py-8 sm:px-6">
      <app-page-header eyebrow="System" heading="Service status" description="Live health of every OpenPTO component, measured by the gateway.">
        <div actions class="flex items-center gap-3">
          <z-switch zId="auto-refresh" [zChecked]="autoRefresh()" (zCheckedChange)="autoRefresh.set($event)">Auto-refresh</z-switch>
          <button type="button" z-button zType="outline" zSize="sm" (click)="refresh()">
            <ng-icon name="lucideRefreshCw" [class.animate-spin]="status.isLoading()" aria-hidden="true" /> Refresh
          </button>
        </div>
      </app-page-header>

      <div
        class="flex items-center gap-3 rounded-xl border p-4"
        [class]="overall() === 'UP' ? 'border-emerald-600/30 bg-emerald-50 dark:bg-emerald-500/10' : overall() === 'DOWN' ? 'border-red-600/30 bg-red-50 dark:bg-red-500/10' : ''"
        role="status"
        aria-live="polite"
      >
        @if (overall() === 'UP') {
          <ng-icon name="lucideCircleCheck" class="size-6 text-emerald-600 dark:text-emerald-400" aria-hidden="true" />
          <div>
            <p class="font-medium">All systems operational</p>
            <p class="text-muted-foreground text-xs">Checked {{ checkedAt() | date: 'mediumTime' }}</p>
          </div>
        } @else if (overall() === 'DOWN') {
          <ng-icon name="lucideCircleX" class="size-6 text-red-600 dark:text-red-400" aria-hidden="true" />
          <div>
            <p class="font-medium">{{ downCount() }} {{ downCount() === 1 ? 'service is' : 'services are' }} unavailable</p>
            <p class="text-muted-foreground text-xs">Checked {{ checkedAt() | date: 'mediumTime' }}</p>
          </div>
        } @else {
          <z-skeleton class="size-6 rounded-full" />
          <p class="text-muted-foreground text-sm">Checking services…</p>
        }
      </div>

      @if (status.error() && !status.hasValue()) {
        <app-error-state [error]="status.error()" fallback="The gateway itself did not respond." (retry)="refresh()" />
      }

      <div class="grid gap-4 sm:grid-cols-2">
        @if (status.hasValue()) {
          @for (s of status.value().services; track s.name) {
            <z-card>
              <z-card-header>
                <div class="flex items-start justify-between gap-2">
                  <div class="flex items-center gap-2">
                    <ng-icon name="lucideServer" class="text-muted-foreground size-4" aria-hidden="true" />
                    <z-card-title class="text-base">{{ humanize(s.name) }}</z-card-title>
                  </div>
                  <app-status-badge [status]="s.status" [label]="s.status === 'UP' ? 'Operational' : 'Down'" />
                </div>
                <z-card-description>{{ describe(s) }}</z-card-description>
              </z-card-header>
              <z-card-content>
                <dl class="grid grid-cols-2 gap-2 text-sm">
                  <div>
                    <dt class="text-muted-foreground text-xs">Latency</dt>
                    <dd class="tabular font-medium" [class]="latencyClass(s)">{{ s.latencyMs === null ? '—' : s.latencyMs + ' ms' }}</dd>
                  </div>
                  <div class="min-w-0">
                    <dt class="text-muted-foreground text-xs">Endpoint</dt>
                    <dd class="truncate font-mono text-xs" [title]="s.url">{{ s.url }}</dd>
                  </div>
                </dl>
                @if (s.latencyMs !== null) {
                  <div class="bg-muted mt-3 h-1.5 overflow-hidden rounded-full" aria-hidden="true">
                    <div class="h-full rounded-full" [class]="barClass(s)" [style.width.%]="Math.min(100, (s.latencyMs / 500) * 100)"></div>
                  </div>
                }
              </z-card-content>
            </z-card>
          }
        } @else if (status.isLoading()) {
          @for (i of [1, 2, 3, 4]; track i) {
            <z-skeleton class="h-36 w-full rounded-xl" />
          }
        }
      </div>
      <p class="text-muted-foreground text-xs">Refreshes every {{ pollSeconds }} seconds while auto-refresh is on.</p>
    </div>
  `,
})
export class StatusPage {
  private readonly api = inject(StatusApi);

  protected readonly Math = Math;
  protected readonly humanize = humanize;
  protected readonly pollSeconds = environment.statusPollMs / 1000;
  protected readonly autoRefresh = signal(true);
  protected readonly checkedAt = signal<Date | null>(null);

  protected readonly status = rxResource({ stream: () => this.api.gatewayStatus() });
  protected readonly overall = computed(() => {
    if (!this.status.hasValue()) return this.status.error() ? 'DOWN' : null;
    return this.status.value().services.every((s) => s.status === 'UP') ? 'UP' : 'DOWN';
  });
  protected readonly downCount = computed(() =>
    this.status.hasValue() ? this.status.value().services.filter((s) => s.status !== 'UP').length : 1,
  );

  constructor() {
    effect(() => {
      if (!this.status.isLoading()) this.checkedAt.set(new Date());
    });
    effect((onCleanup) => {
      if (!this.autoRefresh() || this.status.isLoading()) return;
      const timer = setTimeout(() => this.status.reload(), environment.statusPollMs);
      onCleanup(() => clearTimeout(timer));
    });
  }

  protected refresh(): void {
    this.status.reload();
  }

  protected describe(s: ServiceStatus): string {
    return DESCRIPTIONS[s.name.toLowerCase()] ?? s.url;
  }

  protected latencyClass(s: ServiceStatus): string {
    if (s.latencyMs === null) return '';
    return s.latencyMs < 150 ? 'text-emerald-700 dark:text-emerald-400' : s.latencyMs < 400 ? 'text-amber-700 dark:text-amber-300' : 'text-red-700 dark:text-red-400';
  }

  protected barClass(s: ServiceStatus): string {
    const ms = s.latencyMs ?? 0;
    return ms < 150 ? 'bg-emerald-500' : ms < 400 ? 'bg-amber-500' : 'bg-red-500';
  }
}
