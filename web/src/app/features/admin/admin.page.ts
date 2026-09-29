import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';

import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideDatabase, lucideRefreshCw, lucideUsers } from '@ng-icons/lucide';

import { ZardAvatarComponent } from '@/shared/components/avatar';
import { ZardBadgeComponent } from '@/shared/components/badge';
import { ZardButtonComponent } from '@/shared/components/button';
import { ZardCardImports } from '@/shared/components/card';
import { ZardEmptyComponent } from '@/shared/components/empty';
import { ZardSkeletonComponent } from '@/shared/components/skeleton';
import { ZardTableImports } from '@/shared/components/table';
import { ZardTabsImports } from '@/shared/components/tabs';

import { AccountApi } from '../../core/api/account.api';
import { IngestApi } from '../../core/api/ingest.api';
import { formatBytes, formatDuration, humanize } from '../../core/format';
import { ErrorStateComponent } from '../../ui/error-state.component';
import { PageHeaderComponent } from '../../ui/page-header.component';
import { PagerComponent } from '../../ui/pager.component';
import { StatusBadgeComponent } from '../../ui/status-badge.component';

@Component({
  selector: 'app-admin',
  imports: [
    RouterLink,
    DatePipe,
    DecimalPipe,
    NgIcon,
    ZardButtonComponent,
    ZardBadgeComponent,
    ZardAvatarComponent,
    ZardSkeletonComponent,
    ZardEmptyComponent,
    ...ZardCardImports,
    ...ZardTableImports,
    ...ZardTabsImports,
    PageHeaderComponent,
    PagerComponent,
    StatusBadgeComponent,
    ErrorStateComponent,
  ],
  viewProviders: [provideIcons({ lucideUsers, lucideDatabase, lucideRefreshCw })],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="mx-auto max-w-7xl space-y-6 px-4 py-8 sm:px-6">
      <app-page-header eyebrow="Administration" heading="Admin console" description="All registered users and every ingest job across the platform." />

      @if (stats.hasValue()) {
        @let s = stats.value();
        <dl class="grid grid-cols-2 gap-px overflow-hidden rounded-xl border bg-border md:grid-cols-4">
          <div class="bg-card p-4"><dt class="text-muted-foreground text-xs">Ingest jobs</dt><dd class="tabular text-2xl font-semibold">{{ s.jobs | number }}</dd></div>
          <div class="bg-card p-4"><dt class="text-muted-foreground text-xs">Records loaded</dt><dd class="tabular text-2xl font-semibold">{{ s.recordsLoaded | number }}</dd></div>
          <div class="bg-card p-4"><dt class="text-muted-foreground text-xs">Users</dt><dd class="tabular text-2xl font-semibold">{{ users.hasValue() ? (users.value().totalElements | number) : '—' }}</dd></div>
          <div class="bg-card p-4"><dt class="text-muted-foreground text-xs">Last job</dt><dd class="text-sm font-semibold">{{ s.lastJobAt ? (s.lastJobAt | date: 'medium') : '—' }}</dd></div>
        </dl>
      }

      <z-tab-group>
        <z-tab label="Users" zIcon="lucideUsers">
          <div class="space-y-3 pt-4">
            @if (users.isLoading() && !users.hasValue()) {
              @for (i of [1, 2, 3, 4, 5]; track i) {
                <z-skeleton class="h-12 w-full" />
              }
            } @else if (users.error()) {
              <app-error-state [error]="users.error()" (retry)="users.reload()" />
            } @else if (users.hasValue()) {
              @let page = users.value();
              <div class="overflow-x-auto rounded-lg border">
                <table z-table class="min-w-[40rem]">
                  <thead z-table-header>
                    <tr z-table-row>
                      <th z-table-head scope="col">User</th>
                      <th z-table-head scope="col">Roles</th>
                      <th z-table-head scope="col" class="text-right">API keys</th>
                      <th z-table-head scope="col">Joined</th>
                    </tr>
                  </thead>
                  <tbody z-table-body>
                    @for (u of page.content; track u.id) {
                      <tr z-table-row>
                        <td z-table-cell>
                          <div class="flex items-center gap-3">
                            <z-avatar zSize="sm" [zFallback]="initials(u.displayName || u.email)" [zAlt]="u.displayName" />
                            <div class="min-w-0">
                              <div class="truncate font-medium">{{ u.displayName }}</div>
                              <div class="text-muted-foreground truncate text-xs">{{ u.email }}</div>
                            </div>
                          </div>
                        </td>
                        <td z-table-cell>
                          <div class="flex gap-1">
                            @for (r of u.roles; track r) {
                              <z-badge [zType]="r === 'ADMIN' ? 'default' : 'outline'">{{ r }}</z-badge>
                            }
                          </div>
                        </td>
                        <td z-table-cell class="tabular text-right">{{ u.activeKeys }} / {{ u.totalKeys }}</td>
                        <td z-table-cell class="text-muted-foreground text-xs">{{ u.createdAt | date: 'mediumDate' }}</td>
                      </tr>
                    } @empty {
                      <tr z-table-row><td z-table-cell colspan="4" class="text-muted-foreground py-8 text-center">No users.</td></tr>
                    }
                  </tbody>
                </table>
              </div>
              <app-pager [page]="page.page" [totalPages]="page.totalPages" [size]="page.size" [totalElements]="page.totalElements" ariaLabel="User pages" (pageChange)="userPage.set($event)" />
            }
          </div>
        </z-tab>

        <z-tab label="All ingest jobs" zIcon="lucideDatabase">
          <div class="space-y-3 pt-4">
            <div class="flex justify-end">
              <button type="button" z-button zType="outline" zSize="sm" (click)="jobs.reload()">
                <ng-icon name="lucideRefreshCw" aria-hidden="true" /> Refresh
              </button>
            </div>
            @if (jobs.isLoading() && !jobs.hasValue()) {
              @for (i of [1, 2, 3, 4, 5]; track i) {
                <z-skeleton class="h-12 w-full" />
              }
            } @else if (jobs.error()) {
              <app-error-state [error]="jobs.error()" (retry)="jobs.reload()" />
            } @else if (jobs.hasValue()) {
              @let page = jobs.value();
              @if (!page.content.length) {
                <z-empty zIcon="lucideDatabase" zTitle="No ingest jobs" zDescription="Nobody has uploaded data yet." />
              } @else {
                <div class="overflow-x-auto rounded-lg border">
                  <table z-table class="min-w-[52rem]">
                    <thead z-table-header>
                      <tr z-table-row>
                        <th z-table-head scope="col">File</th>
                        <th z-table-head scope="col">Owner</th>
                        <th z-table-head scope="col">Status</th>
                        <th z-table-head scope="col" class="text-right">Loaded / total</th>
                        <th z-table-head scope="col">Created</th>
                        <th z-table-head scope="col" class="text-right">Duration</th>
                      </tr>
                    </thead>
                    <tbody z-table-body>
                      @for (j of page.content; track j.id) {
                        <tr z-table-row>
                          <td z-table-cell class="max-w-64">
                            <a [routerLink]="['/pipeline/jobs', j.id]" class="text-primary block truncate hover:underline">{{ j.fileName }}</a>
                            <span class="text-muted-foreground text-xs">{{ humanize(j.documentFormat) }} · {{ formatBytes(j.sizeBytes) }}</span>
                          </td>
                          <td z-table-cell class="max-w-40 truncate font-mono text-xs">{{ j.ownerId ?? '—' }}</td>
                          <td z-table-cell><app-status-badge [status]="j.status" /></td>
                          <td z-table-cell class="tabular text-right text-xs">{{ j.recordsLoaded | number }} / {{ j.recordsTotal | number }}</td>
                          <td z-table-cell class="text-muted-foreground text-xs">{{ j.createdAt | date: 'MMM d, h:mm a' }}</td>
                          <td z-table-cell class="tabular text-muted-foreground text-right text-xs">{{ formatDuration(j.durationMs) }}</td>
                        </tr>
                      }
                    </tbody>
                  </table>
                </div>
                <app-pager [page]="page.page" [totalPages]="page.totalPages" [size]="page.size" [totalElements]="page.totalElements" ariaLabel="Job pages" (pageChange)="jobPage.set($event)" />
              }
            }
          </div>
        </z-tab>
      </z-tab-group>
    </div>
  `,
})
export class AdminPage {
  private readonly account = inject(AccountApi);
  private readonly ingest = inject(IngestApi);

  protected readonly humanize = humanize;
  protected readonly formatBytes = formatBytes;
  protected readonly formatDuration = formatDuration;

  protected readonly userPage = signal(0);
  protected readonly jobPage = signal(0);
  protected readonly users = rxResource({
    params: () => ({ page: this.userPage(), size: 20 }),
    stream: ({ params }) => this.account.adminUsers(params),
  });
  /** ADMIN sees every user's jobs from the same endpoint. */
  protected readonly jobs = rxResource({
    params: () => ({ page: this.jobPage(), size: 20, sort: 'createdAt,desc' }),
    stream: ({ params }) => this.ingest.jobs(params),
  });
  protected readonly stats = rxResource({ stream: () => this.ingest.stats() });

  protected initials(source: string): string {
    const parts = source.split(/[\s@._-]+/).filter(Boolean);
    return ((parts[0]?.[0] ?? '') + (parts[1]?.[0] ?? '')).toUpperCase() || '?';
  }
}
