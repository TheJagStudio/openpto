import { inject, Injectable } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { type RouterStateSnapshot, TitleStrategy } from '@angular/router';

import { environment } from '../../environments/environment';

/** "Page · OpenPTO" for every route with a `title`, plain app name otherwise. */
@Injectable({ providedIn: 'root' })
export class OpenPtoTitleStrategy extends TitleStrategy {
  private readonly title = inject(Title);

  override updateTitle(snapshot: RouterStateSnapshot): void {
    const page = this.buildTitle(snapshot);
    this.title.setTitle(page ? `${page} · ${environment.appName}` : `${environment.appName} — open patent & trademark data`);
  }

  /** For pages whose title depends on loaded data (patent number, mark text…). */
  setPageTitle(page: string): void {
    this.title.setTitle(`${page} · ${environment.appName}`);
  }
}
