import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideCheck, lucideLoaderCircle, lucideX } from '@ng-icons/lucide';

import { humanize } from '../../core/format';
import { type IngestJobResponse, PIPELINE_STAGES, type PipelineStage } from '../../core/models';

export type StepState = 'done' | 'active' | 'failed' | 'pending';

export interface Step {
  stage: PipelineStage;
  state: StepState;
  at: string | null;
  message: string | null;
}

const DONE = new Set(['DONE', 'SUCCEEDED', 'SUCCESS', 'COMPLETED', 'OK', 'PARTIAL']);
const FAILED = new Set(['FAILED', 'ERROR']);
const ACTIVE = new Set(['RUNNING', 'IN_PROGRESS', 'STARTED', 'ACTIVE']);

/** Derives the 4-step UPLOADED → PARSED → TRANSFORMED → LOADED view from a job. */
export function toSteps(job: IngestJobResponse): Step[] {
  const reached = new Map((job.stages ?? []).map((s) => [s.stage, s]));
  const terminal = ['COMPLETED', 'PARTIAL', 'FAILED'].includes(job.status);
  let blocked = false;
  return PIPELINE_STAGES.map((stage, i) => {
    const info = reached.get(stage);
    const status = info?.status?.toUpperCase() ?? '';
    let state: StepState;
    if (blocked) state = 'pending';
    else if (FAILED.has(status)) state = 'failed';
    else if (ACTIVE.has(status)) state = 'active';
    else if (info && (DONE.has(status) || !status)) state = 'done';
    else if (!info && !terminal && (i === 0 || reached.has(PIPELINE_STAGES[i - 1]!))) state = 'active';
    else if (!info && job.status === 'FAILED') state = 'failed';
    else state = 'pending';
    if (state === 'failed' || state === 'active') blocked = true;
    return { stage, state, at: info?.at ?? null, message: info?.message ?? null };
  });
}

/** Horizontal (md+) / vertical (mobile) pipeline stepper. */
@Component({
  selector: 'app-stage-stepper',
  imports: [DatePipe, NgIcon],
  viewProviders: [provideIcons({ lucideCheck, lucideX, lucideLoaderCircle })],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ol class="grid gap-4 md:grid-cols-4" aria-label="Pipeline stages">
      @for (s of steps(); track s.stage; let last = $last) {
        <li class="relative flex gap-3 md:flex-col" [attr.aria-current]="s.state === 'active' ? 'step' : null">
          @if (!last) {
            <span
              class="absolute top-4 left-4 hidden h-0.5 w-[calc(100%-1rem)] translate-x-4 md:block"
              [class]="s.state === 'done' ? 'bg-primary' : 'bg-border'"
              aria-hidden="true"
            ></span>
          }
          <span
            class="relative z-10 grid size-8 shrink-0 place-items-center rounded-full border-2 text-xs font-semibold"
            [class]="circle(s.state)"
            aria-hidden="true"
          >
            @switch (s.state) {
              @case ('done') { <ng-icon name="lucideCheck" class="size-4" /> }
              @case ('failed') { <ng-icon name="lucideX" class="size-4" /> }
              @case ('active') { <ng-icon name="lucideLoaderCircle" class="size-4 animate-spin" /> }
              @default { {{ $index + 1 }} }
            }
          </span>
          <div class="min-w-0">
            <p class="text-sm font-medium">{{ label(s.stage) }} <span class="sr-only">— {{ s.state }}</span></p>
            <p class="text-muted-foreground text-xs">
              @if (s.at) {
                {{ s.at | date: 'h:mm:ss a' }}
              } @else {
                {{ s.state === 'active' ? 'In progress…' : s.state === 'pending' ? 'Waiting' : '' }}
              }
            </p>
            @if (s.message) {
              <p class="mt-0.5 text-xs" [class.text-destructive]="s.state === 'failed'">{{ s.message }}</p>
            }
          </div>
        </li>
      }
    </ol>
  `,
})
export class StageStepperComponent {
  readonly job = input.required<IngestJobResponse>();
  protected readonly steps = computed(() => toSteps(this.job()));

  protected label(stage: PipelineStage): string {
    return humanize(stage);
  }

  protected circle(state: StepState): string {
    switch (state) {
      case 'done':
        return 'border-primary bg-primary text-primary-foreground';
      case 'failed':
        return 'border-red-600 bg-red-600 text-white';
      case 'active':
        return 'border-primary bg-background text-primary';
      default:
        return 'border-border bg-background text-muted-foreground';
    }
  }
}
