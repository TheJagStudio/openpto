import { ChangeDetectionStrategy, Component, inject, type TemplateRef, viewChild } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { NgIcon, provideIcons } from '@ng-icons/core';
import {
  lucideActivity,
  lucideCalculator,
  lucideCode,
  lucideDatabase,
  lucideFileText,
  lucideKey,
  lucideLandmark,
  lucideLogIn,
  lucideLogOut,
  lucideMenu,
  lucideShield,
  lucideStamp,
  lucideUser,
} from '@ng-icons/lucide';

import { ZardAvatarComponent } from '@/shared/components/avatar';
import { ZardButtonComponent } from '@/shared/components/button';
import { ZardDropdownImports } from '@/shared/components/dropdown';
import { ZardSheetService } from '@/shared/components/sheet';
import { ZardSonnerComponent } from '@/shared/components/sonner';

import { AuthStore } from '../core/auth/auth.store';
import { Notifier } from '../core/notify/notifier.service';
import { PRIMARY_NAV } from './nav';
import { ThemeToggleComponent } from './theme-toggle.component';

@Component({
  selector: 'app-shell',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    NgIcon,
    ZardButtonComponent,
    ZardAvatarComponent,
    ZardSonnerComponent,
    ThemeToggleComponent,
    ...ZardDropdownImports,
  ],
  viewProviders: [
    provideIcons({
      lucideMenu,
      lucideFileText,
      lucideStamp,
      lucideCalculator,
      lucideDatabase,
      lucideCode,
      lucideActivity,
      lucideLogIn,
      lucideLogOut,
      lucideUser,
      lucideKey,
      lucideShield,
      lucideLandmark,
    }),
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './shell.component.html',
  host: { class: 'flex min-h-dvh flex-col' },
})
export class ShellComponent {
  protected readonly auth = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly sheet = inject(ZardSheetService);
  private readonly notifier = inject(Notifier);
  protected readonly nav = PRIMARY_NAV;
  protected readonly year = new Date().getFullYear();
  private readonly mobileNav = viewChild.required<TemplateRef<unknown>>('mobileNav');

  protected openMobileNav(): void {
    this.sheet.create({
      zContent: this.mobileNav(),
      zSide: 'left',
      zTitle: 'OpenPTO',
      zDescription: 'Open patent & trademark data',
      zHideFooter: true,
      zCustomClasses: 'w-72 max-w-[85vw]',
    });
  }

  protected go(path: string): void {
    void this.router.navigateByUrl(path);
  }

  protected logout(): void {
    this.auth.logout();
    this.notifier.info('Signed out');
    void this.router.navigateByUrl('/');
  }

  protected loginQuery(): Record<string, string> {
    const url = this.router.url;
    return url.startsWith('/login') || url.startsWith('/register') || url === '/' ? {} : { returnUrl: url };
  }
}
