import { afterNextRender, ChangeDetectionStrategy, Component, inject, input, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';

import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideBookOpen, lucideCalculator, lucideCalendar, lucideStamp } from '@ng-icons/lucide';

import { ZardButtonComponent } from '@/shared/components/button';
import { ZardTabGroupComponent, ZardTabsImports } from '@/shared/components/tabs';

import { PageHeaderComponent } from '../../ui/page-header.component';
import { MaintenanceCalculatorComponent } from './maintenance-calculator.component';
import { PatentFilingCalculatorComponent } from './patent-filing-calculator.component';
import { TrademarkFeeCalculatorComponent } from './trademark-fee-calculator.component';

const TABS = ['patent', 'maintenance', 'trademark'] as const;

@Component({
  selector: 'app-fee-calculator-page',
  imports: [
    RouterLink,
    NgIcon,
    ZardButtonComponent,
    ...ZardTabsImports,
    PageHeaderComponent,
    PatentFilingCalculatorComponent,
    MaintenanceCalculatorComponent,
    TrademarkFeeCalculatorComponent,
  ],
  viewProviders: [provideIcons({ lucideCalculator, lucideCalendar, lucideStamp, lucideBookOpen })],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="mx-auto max-w-7xl space-y-6 px-4 py-8 sm:px-6">
      <app-page-header
        eyebrow="Fee calculator"
        heading="Estimate USPTO fees"
        description="Itemized, entity-aware estimates computed by the decoupled fee service. Amounts are illustrative and modeled on the published schedule."
      >
        <a actions z-button zType="outline" routerLink="/fees/schedule">
          <ng-icon name="lucideBookOpen" aria-hidden="true" /> Full fee schedule
        </a>
      </app-page-header>

      <z-tab-group class="gap-4">
        <z-tab label="Patent filing" zIcon="lucideCalculator">
          <div class="pt-2"><app-patent-filing-calculator /></div>
        </z-tab>
        <z-tab label="Maintenance" zIcon="lucideCalendar">
          <div class="pt-2"><app-maintenance-calculator /></div>
        </z-tab>
        <z-tab label="Trademark" zIcon="lucideStamp">
          <div class="pt-2"><app-trademark-fee-calculator /></div>
        </z-tab>
      </z-tab-group>
    </div>
  `,
})
export class FeeCalculatorPage {
  /** `?tab=patent|maintenance|trademark` deep-links to a calculator. */
  readonly tab = input<string>();
  private readonly tabs = viewChild.required(ZardTabGroupComponent);

  constructor() {
    afterNextRender(() => {
      const index = TABS.indexOf((this.tab() ?? 'patent') as (typeof TABS)[number]);
      if (index > 0) this.tabs().selectTabByIndex(index);
    });
  }
}
