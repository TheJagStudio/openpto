import { ChangeDetectionStrategy, Component, input, signal } from '@angular/core';

import { CopyButtonComponent } from './copy-button.component';

export interface CodeSample {
  id: string;
  label: string;
  code: string;
}

/**
 * Tabbed code snippets with copy. Implements the WAI-ARIA tabs pattern with roving tabindex
 * and arrow-key navigation (styled like ZardUI's tab list).
 */
@Component({
  selector: 'app-code-tabs',
  imports: [CopyButtonComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="bg-card overflow-hidden rounded-xl border">
      <div class="bg-muted/40 flex items-center justify-between gap-2 border-b px-2">
        <div role="tablist" [attr.aria-label]="ariaLabel()" class="flex overflow-x-auto overflow-y-hidden [scrollbar-width:none]" (keydown)="onKey($event)">
          @for (s of samples(); track s.id; let i = $index) {
            <button
              type="button"
              role="tab"
              [id]="idPrefix() + '-tab-' + s.id"
              [attr.aria-selected]="active() === i"
              [attr.aria-controls]="idPrefix() + '-panel'"
              [attr.tabindex]="active() === i ? 0 : -1"
              class="focus-visible:ring-ring/50 -mb-px border-b-2 px-3 py-2.5 text-sm font-medium whitespace-nowrap transition-colors outline-none focus-visible:ring-2"
              [class]="active() === i ? 'border-primary text-foreground' : 'text-muted-foreground hover:text-foreground border-transparent'"
              (click)="active.set(i)"
            >
              {{ s.label }}
            </button>
          }
        </div>
        @if (current(); as c) {
          <app-copy-button [value]="c.code" [tooltip]="'Copy ' + c.label + ' snippet'" />
        }
      </div>
      @if (current(); as c) {
        <pre
          role="tabpanel"
          [id]="idPrefix() + '-panel'"
          [attr.aria-labelledby]="idPrefix() + '-tab-' + c.id"
          tabindex="0"
          class="overflow-x-auto p-4 font-mono text-xs leading-relaxed sm:text-[13px]"
        ><code>{{ c.code }}</code></pre>
      }
    </div>
  `,
})
export class CodeTabsComponent {
  readonly samples = input.required<CodeSample[]>();
  readonly ariaLabel = input('Code samples');
  readonly idPrefix = input('code');
  protected readonly active = signal(0);

  protected current(): CodeSample | undefined {
    return this.samples()[this.active()] ?? this.samples()[0];
  }

  protected onKey(event: KeyboardEvent): void {
    const n = this.samples().length;
    let next = this.active();
    if (event.key === 'ArrowRight') next = (next + 1) % n;
    else if (event.key === 'ArrowLeft') next = (next - 1 + n) % n;
    else if (event.key === 'Home') next = 0;
    else if (event.key === 'End') next = n - 1;
    else return;
    event.preventDefault();
    this.active.set(next);
    const target = event.currentTarget as HTMLElement;
    (target.querySelectorAll<HTMLElement>('[role="tab"]')[next])?.focus();
  }
}
