import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, type TemplateRef, viewChild } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';

import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideArrowLeft, lucideBraces, lucideDatabase, lucideDownload, lucideFileX } from '@ng-icons/lucide';

import { ZardBadgeComponent } from '@/shared/components/badge';
import { ZardButtonComponent } from '@/shared/components/button';
import { ZardCardImports } from '@/shared/components/card';
import { ZardDialogService } from '@/shared/components/dialog';
import { ZardEmptyComponent } from '@/shared/components/empty';
import { ZardSkeletonComponent } from '@/shared/components/skeleton';
import { ZardTooltipImports } from '@/shared/components/tooltip';

import { PatentsApi } from '../../core/api/patents.api';
import { humanize } from '../../core/format';
import { toProblem } from '../../core/http/problem';
import type { Party } from '../../core/models';
import { OpenPtoTitleStrategy } from '../../core/title.strategy';
import { BreadcrumbsComponent } from '../../ui/breadcrumbs.component';
import { CopyButtonComponent } from '../../ui/copy-button.component';
import { saveJson } from '../../ui/download';
import { ErrorStateComponent } from '../../ui/error-state.component';
import { JsonViewerComponent } from '../../ui/json-viewer.component';
import { StatusBadgeComponent } from '../../ui/status-badge.component';
import { claimTree } from './claims';

@Component({
  selector: 'app-patent-detail',
  imports: [
    RouterLink,
    DatePipe,
    NgIcon,
    ZardButtonComponent,
    ZardBadgeComponent,
    ZardSkeletonComponent,
    ZardEmptyComponent,
    ...ZardCardImports,
    BreadcrumbsComponent,
    ...ZardTooltipImports,
    StatusBadgeComponent,
    ErrorStateComponent,
    JsonViewerComponent,
    CopyButtonComponent,
  ],
  viewProviders: [provideIcons({ lucideArrowLeft, lucideBraces, lucideDownload, lucideFileX, lucideDatabase })],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './patent-detail.page.html',
})
export class PatentDetailPage {
  private readonly api = inject(PatentsApi);
  private readonly dialog = inject(ZardDialogService);
  private readonly titles = inject(OpenPtoTitleStrategy);

  /** Bound from the route (`withComponentInputBinding`). */
  readonly patentNumber = input.required<string>();

  protected readonly patent = rxResource({
    params: () => this.patentNumber(),
    stream: ({ params }) => this.api.get(params),
  });

  protected readonly notFound = computed(() => toProblem(this.patent.error()).status === 404);
  protected readonly claims = computed(() => (this.patent.hasValue() ? claimTree(this.patent.value().claims ?? []) : []));
  protected readonly independentCount = computed(() => this.claims().filter((c) => c.independent).length);
  protected readonly json = computed(() => (this.patent.hasValue() ? JSON.stringify(this.patent.value(), null, 2) : ''));
  protected readonly humanize = humanize;

  private readonly jsonTpl = viewChild<TemplateRef<unknown>>('jsonTpl');

  constructor() {
    effect(() => {
      if (this.patent.hasValue()) {
        const p = this.patent.value();
        this.titles.setPageTitle(`${p.patentNumber} — ${p.title}`);
      }
    });
  }

  protected place(p: Party): string {
    return [p.city, p.state, p.country].filter(Boolean).join(', ');
  }

  protected openJson(): void {
    const tpl = this.jsonTpl();
    if (!tpl) return;
    this.dialog.create({
      zTitle: `${this.patentNumber()} — JSON`,
      zDescription: 'The exact document returned by GET /api/v1/patents/{patentNumber}.',
      zContent: tpl,
      zOkText: null,
      zCancelText: 'Close',
      zCustomClasses: 'sm:max-w-4xl max-h-[90dvh] overflow-y-auto [&_main]:min-w-0',
    });
  }

  protected downloadJson(): void {
    if (this.patent.hasValue()) saveJson(this.patent.value(), `${this.patentNumber()}.json`);
  }
}
