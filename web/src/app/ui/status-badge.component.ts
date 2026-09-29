import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { ZardBadgeComponent } from '@/shared/components/badge';

import { humanize } from '../core/format';
import { TONE_CLASSES, TONE_DOT, toneOf, type Tone } from '../core/status-tone';

/** ZardUI badge with a semantic colour + dot for any domain status (patent, mark, job, window…). */
@Component({
  selector: 'app-status-badge',
  imports: [ZardBadgeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <z-badge zType="outline" [class]="classes()" [attr.data-tone]="tone()">
      <span class="size-1.5 rounded-full" [class]="dot()" aria-hidden="true"></span>
      {{ text() }}
    </z-badge>
  `,
})
export class StatusBadgeComponent {
  readonly status = input.required<string | null | undefined>();
  /** Override the displayed text (defaults to the humanized status). */
  readonly label = input<string>();
  /** Override the tone (defaults to the status mapping). */
  readonly toneOverride = input<Tone | undefined>(undefined, { alias: 'tone' });

  protected readonly tone = computed(() => this.toneOverride() ?? toneOf(this.status()));
  protected readonly text = computed(() => this.label() ?? humanize(this.status()));
  protected readonly classes = computed(() => `border-transparent ring-1 ring-inset ${TONE_CLASSES[this.tone()]}`);
  protected readonly dot = computed(() => TONE_DOT[this.tone()]);
}
