import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';

import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideBraces, lucideDownload, lucideFileX, lucideRotateCcw, lucideTrash2 } from '@ng-icons/lucide';

import { ZardAlertDialogService } from '@/shared/components/alert-dialog';
import { ZardButtonComponent } from '@/shared/components/button';
import { ZardCardImports } from '@/shared/components/card';
import { ZardEmptyComponent } from '@/shared/components/empty';
import { ZardProgressComponent } from '@/shared/components/progress';
import { ZardSkeletonComponent } from '@/shared/components/skeleton';
import { ZardTableImports } from '@/shared/components/table';

import { environment } from '../../../environments/environment';
import { IngestApi } from '../../core/api/ingest.api';
import { formatBytes, formatDuration, humanize } from '../../core/format';
import { toProblem } from '../../core/http/problem';
import { TERMINAL_INGEST_STATUSES, type TransformedRecord } from '../../core/models';
import { Notifier } from '../../core/notify/notifier.service';
import { BreadcrumbsComponent } from '../../ui/breadcrumbs.component';
import { CopyButtonComponent } from '../../ui/copy-button.component';
import { saveJson } from '../../ui/download';
import { ErrorStateComponent } from '../../ui/error-state.component';
import { JsonViewerComponent } from '../../ui/json-viewer.component';
import { StatusBadgeComponent } from '../../ui/status-badge.component';
import { StageStepperComponent } from './stage-stepper.component';

const PREVIEW_RECORDS = 25;

interface RecordLink {
  label: string;
  title: string;
  link: string[];
}

/** Picks the public identifier out of a transformed record (patent or trademark). */
export function recordLink(r: TransformedRecord): RecordLink | null {
  const title = typeof r['title'] === 'string' ? r['title'] : typeof r['markText'] === 'string' ? r['markText'] : '';
  if (typeof r['patentNumber'] === 'string') return { label: r['patentNumber'], title, link: ['/patents', r['patentNumber']] };
  if (typeof r['serialNumber'] === 'string') return { label: r['serialNumber'], title, link: ['/trademarks', r['serialNumber']] };
  return null;
}

@Component({
  selector: 'app-job-detail',
  imports: [
    RouterLink,
    DatePipe,
    DecimalPipe,
    NgIcon,
    ZardButtonComponent,
    ZardSkeletonComponent,
    ZardEmptyComponent,
    ZardProgressComponent,
    ...ZardCardImports,
    ...ZardTableImports,
    BreadcrumbsComponent,
    StatusBadgeComponent,
    ErrorStateComponent,
    JsonViewerComponent,
    CopyButtonComponent,
    StageStepperComponent,
  ],
  viewProviders: [provideIcons({ lucideRotateCcw, lucideTrash2, lucideDownload, lucideBraces, lucideFileX })],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './job-detail.page.html',
})
export class JobDetailPage {
  private readonly api = inject(IngestApi);
  private readonly confirm = inject(ZardAlertDialogService);
  private readonly notifier = inject(Notifier);
  private readonly router = inject(Router);

  readonly id = input.required<string>();

  protected readonly formatBytes = formatBytes;
  protected readonly formatDuration = formatDuration;
  protected readonly humanize = humanize;

  protected readonly job = rxResource({ params: () => this.id(), stream: ({ params }) => this.api.job(params) });
  protected readonly notFound = computed(() => toProblem(this.job.error()).status === 404);
  protected readonly isTerminal = computed(() => this.job.hasValue() && TERMINAL_INGEST_STATUSES.includes(this.job.value().status));
  protected readonly canRetry = computed(() => this.job.hasValue() && ['FAILED', 'PARTIAL'].includes(this.job.value().status));
  protected readonly loadPct = computed(() => {
    if (!this.job.hasValue()) return 0;
    const j = this.job.value();
    return j.recordsTotal ? Math.round((j.recordsLoaded / j.recordsTotal) * 100) : 0;
  });

  /** JSON output is fetched on demand (it can be large). */
  protected readonly showJson = signal(false);
  protected readonly output = rxResource({
    params: () => (this.showJson() && this.job.hasValue() && this.job.value().jsonObjectKey ? this.id() : undefined),
    stream: ({ params }) => this.api.jobJson(params),
  });
  protected readonly preview = computed(() => (this.output.hasValue() ? this.output.value().slice(0, PREVIEW_RECORDS) : []));
  protected readonly links = computed(() =>
    this.preview()
      .map(recordLink)
      .filter((l): l is RecordLink => l !== null),
  );
  protected readonly previewCount = PREVIEW_RECORDS;
  protected readonly busy = signal(false);

  constructor() {
    // Poll while the job is still moving through the pipeline.
    effect((onCleanup) => {
      if (!this.job.hasValue() || this.isTerminal() || this.job.isLoading()) return;
      const timer = setTimeout(() => this.job.reload(), environment.jobPollMs);
      onCleanup(() => clearTimeout(timer));
    });
  }

  protected loadJson(): void {
    if (this.showJson()) this.output.reload();
    else this.showJson.set(true);
  }

  protected download(): void {
    this.api.jobJson(this.id()).subscribe((records) => {
      const base = this.job.hasValue() ? this.job.value().fileName.replace(/\.(xml|zip)$/i, '') : this.id();
      saveJson(records, `${base}.json`);
    });
  }

  protected retry(): void {
    this.confirm.create({
      zTitle: 'Retry this job?',
      zDescription: 'The pipeline re-runs from the stored raw file. Records that already loaded are upserted, not duplicated.',
      zOkText: 'Retry',
      zCancelText: 'Cancel',
      zOnOk: () => {
        this.busy.set(true);
        this.api.retry(this.id()).subscribe({
          next: () => {
            this.busy.set(false);
            this.notifier.success('Job re-queued');
            this.showJson.set(false);
            this.job.reload();
          },
          error: () => this.busy.set(false),
        });
      },
    });
  }

  protected remove(): void {
    this.confirm.create({
      zTitle: 'Delete this job?',
      zDescription: 'The job record, its raw upload and its JSON output are permanently deleted. Loaded patents and trademarks stay in the dataset.',
      zOkText: 'Delete job',
      zOkDestructive: true,
      zCancelText: 'Cancel',
      zOnOk: () => {
        this.busy.set(true);
        this.api.delete(this.id()).subscribe({
          next: () => {
            this.notifier.success('Job deleted');
            void this.router.navigateByUrl('/pipeline');
          },
          error: () => this.busy.set(false),
        });
      },
    });
  }
}
