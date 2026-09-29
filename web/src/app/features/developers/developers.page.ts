import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal, type TemplateRef, viewChild } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { DomSanitizer } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';

import { NgIcon, provideIcons } from '@ng-icons/core';
import {
  lucideBookOpen,
  lucideExternalLink,
  lucideKeyRound,
  lucidePlus,
  lucideRefreshCw,
  lucideTrash2,
  lucideTriangleAlert,
} from '@ng-icons/lucide';

import { ZardAlertComponent } from '@/shared/components/alert';
import { ZardAlertDialogService } from '@/shared/components/alert-dialog';
import { ZardBadgeComponent } from '@/shared/components/badge';
import { ZardButtonComponent } from '@/shared/components/button';
import { ZardCardImports } from '@/shared/components/card';
import { ZardDialogService } from '@/shared/components/dialog';
import { ZardEmptyComponent } from '@/shared/components/empty';
import { ZardInputComponent } from '@/shared/components/input';
import { ZardSkeletonComponent } from '@/shared/components/skeleton';
import { ZardTableImports } from '@/shared/components/table';
import { ZardTooltipImports } from '@/shared/components/tooltip';

import { environment } from '../../../environments/environment';
import { AccountApi } from '../../core/api/account.api';
import { AuthStore } from '../../core/auth/auth.store';
import { type ApiKeyResponse, TIER_LIMITS } from '../../core/models';
import { Notifier } from '../../core/notify/notifier.service';
import { BarChartComponent, type BarDatum } from '../../ui/bar-chart.component';
import { CodeTabsComponent } from '../../ui/code-tabs.component';
import { CopyButtonComponent } from '../../ui/copy-button.component';
import { ErrorStateComponent } from '../../ui/error-state.component';
import { PageHeaderComponent } from '../../ui/page-header.component';
import { apiSnippets } from './snippets';

const MAX_ACTIVE_KEYS = 5;

@Component({
  selector: 'app-developers',
  imports: [
    RouterLink,
    DatePipe,
    DecimalPipe,
    NgIcon,
    ZardButtonComponent,
    ZardBadgeComponent,
    ZardInputComponent,
    ZardAlertComponent,
    ZardSkeletonComponent,
    ZardEmptyComponent,
    ...ZardCardImports,
    ...ZardTableImports,
    ...ZardTooltipImports,
    PageHeaderComponent,
    BarChartComponent,
    CodeTabsComponent,
    CopyButtonComponent,
    ErrorStateComponent,
  ],
  viewProviders: [
    provideIcons({ lucidePlus, lucideRefreshCw, lucideTrash2, lucideKeyRound, lucideTriangleAlert, lucideExternalLink, lucideBookOpen }),
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './developers.page.html',
})
export class DevelopersPage {
  private readonly account = inject(AccountApi);
  private readonly dialog = inject(ZardDialogService);
  private readonly confirm = inject(ZardAlertDialogService);
  private readonly notifier = inject(Notifier);
  private readonly sanitizer = inject(DomSanitizer);
  protected readonly auth = inject(AuthStore);

  protected readonly tiers = TIER_LIMITS;
  protected readonly maxKeys = MAX_ACTIVE_KEYS;
  protected readonly swaggerUrl = environment.swaggerUiUrl;
  protected readonly swaggerSafe = this.sanitizer.bypassSecurityTrustResourceUrl(environment.swaggerUiUrl);
  protected readonly showDocs = signal(false);

  protected readonly keys = rxResource({
    params: () => (this.auth.isAuthenticated() ? true : undefined),
    stream: () => this.account.apiKeys(),
  });
  protected readonly usage = rxResource({
    params: () => (this.auth.isAuthenticated() ? 30 : undefined),
    stream: ({ params }) => this.account.usage(params),
  });

  protected readonly activeKeys = computed(() => (this.keys.hasValue() ? this.keys.value().filter((k) => !k.revokedAt) : []));
  protected readonly canCreate = computed(() => this.activeKeys().length < MAX_ACTIVE_KEYS);

  protected readonly daily = computed<BarDatum[]>(() => {
    if (!this.usage.hasValue()) return [];
    const byDate = new Map(this.usage.value().daily.map((d) => [d.date, d.requests]));
    // Fill gaps so the chart always shows 30 consecutive days.
    const out: BarDatum[] = [];
    const today = new Date();
    for (let i = 29; i >= 0; i--) {
      const d = new Date(today.getFullYear(), today.getMonth(), today.getDate() - i);
      const iso = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
      out.push({ label: String(d.getDate()), title: iso, value: byDate.get(iso) ?? 0 });
    }
    return out;
  });

  protected readonly snippets = computed(() => {
    const prefix = this.activeKeys()[0]?.prefix;
    const placeholder = prefix ? `${prefix}…` : 'opto_YOUR_API_KEY';
    return apiSnippets(typeof location !== 'undefined' ? location.origin : 'http://localhost:8080', placeholder);
  });

  // ---- dialogs ----
  protected readonly newKeyName = signal('');
  protected readonly creating = signal(false);
  protected readonly revealed = signal<ApiKeyResponse | null>(null);
  private readonly createTpl = viewChild.required<TemplateRef<unknown>>('createTpl');
  private readonly revealTpl = viewChild.required<TemplateRef<unknown>>('revealTpl');

  protected openCreate(): void {
    this.newKeyName.set('');
    this.dialog.create({
      zTitle: 'Create API key',
      zDescription: 'Give the key a name you will recognise, e.g. the app or script that uses it.',
      zContent: this.createTpl(),
      zHideFooter: true,
    });
  }

  protected create(event: Event, ref: { close: () => void }): void {
    event.preventDefault();
    const name = this.newKeyName().trim();
    if (!name || this.creating()) return;
    this.creating.set(true);
    this.account.createApiKey(name).subscribe({
      next: (key) => {
        this.creating.set(false);
        ref.close();
        this.keys.reload();
        this.reveal(key, 'API key created');
      },
      error: () => this.creating.set(false),
    });
  }

  protected rotate(key: ApiKeyResponse): void {
    this.confirm.create({
      zTitle: `Rotate “${key.name}”?`,
      zDescription: 'A new secret is issued immediately. Anything still using the old secret will start receiving 401 errors.',
      zOkText: 'Rotate key',
      zCancelText: 'Cancel',
      zOnOk: () => {
        this.account.rotateApiKey(key.id).subscribe((rotated) => {
          this.keys.reload();
          this.reveal(rotated, 'API key rotated');
        });
      },
    });
  }

  protected revoke(key: ApiKeyResponse): void {
    this.confirm.create({
      zTitle: `Revoke “${key.name}”?`,
      zDescription: 'Requests using this key will be rejected. This cannot be undone.',
      zOkText: 'Revoke key',
      zOkDestructive: true,
      zCancelText: 'Cancel',
      zOnOk: () => {
        this.account.revokeApiKey(key.id).subscribe(() => {
          this.notifier.success('API key revoked', { description: key.name });
          this.keys.reload();
        });
      },
    });
  }

  private reveal(key: ApiKeyResponse, title: string): void {
    this.revealed.set(key);
    this.dialog.create({
      zTitle: title,
      zDescription: 'Copy your key now. For your security it will not be shown again.',
      zContent: this.revealTpl(),
      zOkText: 'I have saved it',
      zCancelText: null,
      zClosable: false,
      zMaskClosable: false,
      zOnOk: () => {
        this.revealed.set(null);
      },
    });
  }
}
