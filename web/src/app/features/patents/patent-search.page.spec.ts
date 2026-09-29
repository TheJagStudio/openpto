import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { convertToParamMap, provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { of } from 'rxjs';

import { PatentsApi } from '../../core/api/patents.api';
import type { Page, PatentSearchParams, PatentSummary } from '../../core/models';
import { parsePatentQuery, patchFor } from './patent-query';
import { PatentSearchPage } from './patent-search.page';

const PATENT: PatentSummary = {
  patentNumber: 'US11234567B2',
  applicationNumber: '17/123,456',
  title: 'Solid-state battery cathode',
  type: 'UTILITY',
  status: 'GRANTED',
  filingDate: '2021-03-04',
  grantDate: '2023-06-13',
  primaryCpc: 'H01M10/0562',
  assignees: ['Volta Labs Inc.'],
  inventors: ['Ada Lovelace'],
  abstractSnippet: 'A cathode…',
  source: 'SEED',
};

const page = (content: PatentSummary[]): Page<PatentSummary> => ({ content, page: 0, size: 20, totalElements: content.length, totalPages: 1 });

describe('parsePatentQuery', () => {
  it('parses valid params and drops invalid ones', () => {
    const parsed = parsePatentQuery(
      convertToParamMap({ q: ' battery ', type: 'design', status: 'BOGUS', cpc: 'h01m', filedFrom: '2020-01-01', filedTo: 'yesterday', page: '3', size: '500' }),
    );
    expect(parsed).toEqual({ q: 'battery', type: 'DESIGN', cpc: 'H01M', filedFrom: '2020-01-01', page: 3, size: 100 });
  });

  it('defaults paging for an empty URL', () => {
    expect(parsePatentQuery(convertToParamMap({}))).toEqual({ page: 0, size: 20 });
  });

  it('patchFor resets the page on filter changes and nulls blanks', () => {
    expect(patchFor({ status: 'GRANTED', cpc: '' })).toEqual({ status: 'GRANTED', cpc: null, page: null });
    expect(patchFor({ page: 4 })).toEqual({ page: 4 });
  });
});

describe('PatentSearchPage (query params ↔ state)', () => {
  let search: ReturnType<typeof vi.fn>;
  let facets: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    search = vi.fn(() => of(page([PATENT])));
    facets = vi.fn(() => of({ types: [{ value: 'UTILITY', count: 1 }], statuses: [], years: [], cpcSections: [], topAssignees: [] }));
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideRouter([{ path: 'patents', component: PatentSearchPage }]),
        { provide: PatentsApi, useValue: { search, facets, datasetUrl: () => '/export' } },
      ],
    });
  });

  it('loads results from the URL query params', async () => {
    const harness = await RouterTestingHarness.create();
    const cmp = await harness.navigateByUrl('/patents?q=battery&type=UTILITY&page=2&sort=grantDate,desc', PatentSearchPage);
    await harness.fixture.whenStable();
    harness.detectChanges();

    expect(cmp.criteria()).toEqual({ q: 'battery', type: 'UTILITY', page: 2, size: 20, sort: 'grantDate,desc' });
    expect(search).toHaveBeenLastCalledWith({ q: 'battery', type: 'UTILITY', page: 2, size: 20, sort: 'grantDate,desc' });
    // Facets ignore paging/sort.
    expect(facets).toHaveBeenLastCalledWith({ q: 'battery', type: 'UTILITY' });

    const text = (harness.routeNativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Solid-state battery cathode');
    expect(text).toContain('US11234567B2');
    expect((harness.routeNativeElement as HTMLElement).querySelector<HTMLInputElement>('#patent-q')!.value).toBe('battery');
  });

  it('writes filter changes back to the URL (merging, resetting page) and re-queries', async () => {
    const harness = await RouterTestingHarness.create();
    const cmp = await harness.navigateByUrl('/patents?q=battery&page=3', PatentSearchPage);
    await harness.fixture.whenStable();

    cmp.update({ status: 'GRANTED' });
    await harness.fixture.whenStable();
    harness.detectChanges();

    const url = TestBed.inject(Router).url;
    expect(url).toContain('q=battery');
    expect(url).toContain('status=GRANTED');
    expect(url).not.toContain('page=');
    const last = search.mock.lastCall?.[0] as PatentSearchParams;
    expect(last).toEqual({ q: 'battery', status: 'GRANTED', page: 0, size: 20 });
  });

  it('back/forward navigation updates the state from the URL', async () => {
    const harness = await RouterTestingHarness.create();
    const cmp = await harness.navigateByUrl('/patents?q=solar', PatentSearchPage);
    await harness.navigateByUrl('/patents?q=wind&cpc=F03D');
    await harness.fixture.whenStable();

    expect(cmp.criteria()).toEqual({ q: 'wind', cpc: 'F03D', page: 0, size: 20 });
    expect(search).toHaveBeenLastCalledWith({ q: 'wind', cpc: 'F03D', page: 0, size: 20 });
  });
});
