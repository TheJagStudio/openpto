import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpEventType } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';

import { NgIcon, provideIcons } from '@ng-icons/core';
import {
  lucideCloudUpload,
  lucideDatabase,
  lucideDownload,
  lucideFileArchive,
  lucideFileCode,
  lucidePlay,
  lucideRefreshCw,
  lucideX,
} from '@ng-icons/lucide';

import { ZardButtonComponent } from '@/shared/components/button';
import { ZardCardImports } from '@/shared/components/card';
import { ZardEmptyComponent } from '@/shared/components/empty';
import { ZardProgressComponent } from '@/shared/components/progress';
import { ZardSelectImports } from '@/shared/components/select';
import { ZardSkeletonComponent } from '@/shared/components/skeleton';
import { ZardTableImports } from '@/shared/components/table';

import { environment } from '../../../environments/environment';
import { IngestApi } from '../../core/api/ingest.api';
import { formatBytes, formatDuration, humanize } from '../../core/format';
import {
  INGEST_LIMITS,
  INGEST_STATUSES,
  type IngestJobResponse,
  type IngestSample,
  type IngestStatus,
  TERMINAL_INGEST_STATUSES,
} from '../../core/models';
import { Notifier } from '../../core/notify/notifier.service';
import { ErrorStateComponent } from '../../ui/error-state.component';
import { PageHeaderComponent } from '../../ui/page-header.component';
import { PagerComponent } from '../../ui/pager.component';
import { StatusBadgeComponent } from '../../ui/status-badge.component';
import { checkFiles, type RejectedFile } from './upload-validation';

@Component({
  selector: 'app-pipeline',
  imports: [
    RouterLink,
    DatePipe,
    DecimalPipe,
    NgIcon,
    ZardButtonComponent,
    ZardProgressComponent,
    ZardSkeletonComponent,
    ZardEmptyComponent,
    ...ZardCardImports,
    ...ZardTableImports,
    ...ZardSelectImports,
    PageHeaderComponent,
    PagerComponent,
    StatusBadgeComponent,
    ErrorStateComponent,
  ],
  viewProviders: [
    provideIcons({ lucideCloudUpload, lucideFileCode, lucideFileArchive, lucideX, lucideDownload, lucidePlay, lucideRefreshCw, lucideDatabase }),
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './pipeline.page.html',
})
export class PipelinePage {
  private readonly api = inject(IngestApi);
  private readonly notifier = inject(Notifier);

  protected readonly limits = INGEST_LIMITS;
  protected readonly statuses = INGEST_STATUSES;
  protected readonly formatBytes = formatBytes;
  protected readonly formatDuration = formatDuration;
  protected readonly humanize = humanize;

  // ---- upload ----
  protected readonly selected = signal<File[]>([]);
  protected readonly rejected = signal<RejectedFile[]>([]);
  protected readonly dragging = signal(false);
  protected readonly uploading = signal(false);
  protected readonly progress = signal(0);
  protected readonly trying = signal<string | null>(null);
  protected readonly totalSize = computed(() => this.selected().reduce((n, f) => n + f.size, 0));

  // ---- jobs ----
  protected readonly page = signal(0);
  protected readonly statusFilter = signal<IngestStatus | ''>('');
  protected readonly jobs = rxResource({
    params: () => ({ page: this.page(), size: 10, status: this.statusFilter() || undefined, sort: 'createdAt,desc' }),
    stream: ({ params }) => this.api.jobs(params),
  });
  protected readonly hasActive = computed(
    () => this.jobs.hasValue() && this.jobs.value().content.some((j) => !TERMINAL_INGEST_STATUSES.includes(j.status)),
  );

  protected readonly samples = rxResource({ stream: () => this.api.samples() });

  constructor() {
    // Auto-poll while any visible job is still running.
    effect((onCleanup) => {
      if (!this.hasActive() || this.jobs.isLoading()) return;
      const timer = setTimeout(() => this.jobs.reload(), environment.jobPollMs);
      onCleanup(() => clearTimeout(timer));
    });
  }

  protected onDragOver(event: DragEvent): void {
    event.preventDefault();
    if (event.dataTransfer) event.dataTransfer.dropEffect = 'copy';
    this.dragging.set(true);
  }

  protected onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(false);
    this.addFiles(Array.from(event.dataTransfer?.files ?? []));
  }

  protected onPick(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.addFiles(Array.from(input.files ?? []));
    input.value = '';
  }

  addFiles(files: File[]): void {
    const { accepted, rejected } = checkFiles(files, this.selected().length);
    this.selected.update((cur) => [...cur, ...accepted]);
    this.rejected.set(rejected);
  }

  protected remove(index: number): void {
    this.selected.update((cur) => cur.filter((_, i) => i !== index));
  }

  protected upload(files: File[] = this.selected()): void {
    if (!files.length || this.uploading()) return;
    this.uploading.set(true);
    this.progress.set(0);
    this.api.upload(files).subscribe({
      next: (event) => {
        if (event.type === HttpEventType.UploadProgress) {
          this.progress.set(event.total ? Math.round((event.loaded / event.total) * 100) : 0);
        } else if (event.type === HttpEventType.Response) {
          const created = event.body ?? [];
          this.notifier.success(`${created.length} ${created.length === 1 ? 'job' : 'jobs'} queued`, {
            description: 'Parsing starts immediately — this list refreshes automatically.',
          });
          this.finishUpload();
        }
      },
      error: () => this.finishUpload(false),
    });
  }

  private finishUpload(success = true): void {
    this.uploading.set(false);
    this.trying.set(null);
    if (success) {
      this.selected.set([]);
      this.rejected.set([]);
      this.progress.set(100);
      this.page.set(0);
      this.jobs.reload();
    }
  }

  /** Downloads a public sample and pushes it through the pipeline. */
  protected trySample(sample: IngestSample): void {
    this.trying.set(sample.name);
    this.api.sample(sample.name).subscribe({
      next: (blob) => {
        const type = sample.name.toLowerCase().endsWith('.zip') ? 'application/zip' : 'application/xml';
        this.upload([new File([blob], sample.name, { type })]);
      },
      error: () => this.trying.set(null),
    });
  }

  protected sampleUrl(name: string): string {
    return this.api.sampleUrl(name);
  }

  protected setStatus(value: string | string[]): void {
    const v = Array.isArray(value) ? (value[0] ?? '') : value;
    this.statusFilter.set(v as IngestStatus | '');
    this.page.set(0);
  }

  protected goToPage(page: number): void {
    this.page.set(page);
  }

  protected progressOf(job: IngestJobResponse): number {
    return job.recordsTotal ? Math.round(((job.recordsLoaded + job.recordsFailed) / job.recordsTotal) * 100) : 0;
  }
}
