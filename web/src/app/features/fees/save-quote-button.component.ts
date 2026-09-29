import { ChangeDetectionStrategy, Component, inject, input, signal, type TemplateRef, viewChild } from '@angular/core';
import { Router } from '@angular/router';

import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideExternalLink, lucidePrinter, lucideShare2 } from '@ng-icons/lucide';

import { ZardButtonComponent } from '@/shared/components/button';
import { ZardDialogService } from '@/shared/components/dialog';
import { ZardInputComponent } from '@/shared/components/input';

import { FeesApi } from '../../core/api/fees.api';
import type { QuoteKind, QuoteRequestBody, SavedQuote } from '../../core/models';
import { CopyButtonComponent } from '../../ui/copy-button.component';

/**
 * Saves the current calculator request as a server-side quote (the server recomputes totals)
 * and shows the shareable link.
 */
@Component({
  selector: 'app-save-quote-button',
  imports: [ZardButtonComponent, ZardInputComponent, NgIcon, CopyButtonComponent],
  viewProviders: [provideIcons({ lucideShare2, lucideExternalLink, lucidePrinter })],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button type="button" z-button zType="outline" [zLoading]="saving()" [zDisabled]="disabled() || saving()" (click)="save()">
      <ng-icon name="lucideShare2" aria-hidden="true" /> Save &amp; share
    </button>

    <ng-template #sharedTpl let-dialogRef="dialogRef">
      @if (saved(); as q) {
        <div class="space-y-3">
          <label class="text-sm font-medium" for="share-url">Shareable link</label>
          <div class="flex gap-2">
            <input id="share-url" z-input readonly [value]="url(q)" (focus)="$any($event.target).select()" />
            <app-copy-button [value]="url(q)" tooltip="Copy link" type="outline" />
          </div>
          <p class="text-muted-foreground text-xs">Anyone with the link can view this quote. Totals were recomputed by the server.</p>
          <div class="flex justify-end">
            <button type="button" z-button (click)="open(q, dialogRef)">
              <ng-icon name="lucideExternalLink" aria-hidden="true" /> Open quote
            </button>
          </div>
        </div>
      }
    </ng-template>
  `,
})
export class SaveQuoteButtonComponent {
  private readonly fees = inject(FeesApi);
  private readonly dialog = inject(ZardDialogService);
  private readonly router = inject(Router);

  readonly kind = input.required<QuoteKind>();
  readonly request = input.required<QuoteRequestBody | null>();
  readonly label = input<string>();
  readonly disabled = input(false);

  protected readonly saving = signal(false);
  protected readonly saved = signal<SavedQuote | null>(null);
  private readonly sharedTpl = viewChild.required<TemplateRef<unknown>>('sharedTpl');

  protected save(): void {
    const request = this.request();
    if (!request) return;
    this.saving.set(true);
    this.fees.saveQuote({ kind: this.kind(), request, label: this.label() }).subscribe({
      next: (q) => {
        this.saving.set(false);
        this.saved.set(q);
        this.dialog.create({
          zTitle: 'Quote saved',
          zDescription: `Quote ${q.id} — ${q.scheduleCode}`,
          zContent: this.sharedTpl(),
          zOkText: null,
          zCancelText: 'Close',
        });
      },
      error: () => this.saving.set(false),
    });
  }

  protected url(q: SavedQuote): string {
    const origin = typeof location !== 'undefined' ? location.origin : '';
    return `${origin}/fees/quotes/${encodeURIComponent(q.id)}`;
  }

  protected open(q: SavedQuote, ref: { close: () => void }): void {
    ref.close();
    void this.router.navigate(['/fees/quotes', q.id]);
  }
}
