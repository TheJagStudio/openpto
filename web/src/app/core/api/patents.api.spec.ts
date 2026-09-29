import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { SKIP_ERROR_TOAST } from '../http/http-utils';
import type { Page, PatentSummary } from '../models';
import { PatentsApi } from './patents.api';
import { TrademarksApi } from './trademarks.api';

describe('PatentsApi', () => {
  let api: PatentsApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(PatentsApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('search_withFilters_sendsOnlyNonEmptyParams', () => {
    api.search({ q: ' battery ', type: 'UTILITY', cpc: '', assignee: undefined, page: 2, size: 50, sort: 'grantDate,desc' }).subscribe();

    const req = http.expectOne((r) => r.url === '/api/v1/patents');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('q')).toBe('battery');
    expect(req.request.params.get('type')).toBe('UTILITY');
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('50');
    expect(req.request.params.get('sort')).toBe('grantDate,desc');
    expect(req.request.params.has('cpc')).toBe(false);
    expect(req.request.params.has('assignee')).toBe(false);
    req.flush({ content: [], page: 2, size: 50, totalElements: 0, totalPages: 0 });
  });

  it('search_mapsPageEnvelope_andDefaultsMissingFields', () => {
    let result: Page<PatentSummary> | undefined;
    api.search().subscribe((p) => (result = p));

    const summary = { patentNumber: 'US11234567B2', title: 'Widget' } as PatentSummary;
    http.expectOne('/api/v1/patents').flush({ content: [summary], totalElements: 45, size: 20 });

    expect(result).toEqual({ content: [summary], page: 0, size: 20, totalElements: 45, totalPages: 3 });
  });

  it('facets_dropsPagingAndSort_andNormalizesBuckets', () => {
    let types: unknown;
    api.facets({ q: 'solar', page: 3, size: 10, sort: 'relevance' }).subscribe((f) => (types = f.types));

    const req = http.expectOne((r) => r.url === '/api/v1/patents/facets');
    expect(req.request.params.get('q')).toBe('solar');
    expect(req.request.params.keys()).toEqual(['q']);
    req.flush({ statuses: [{ value: 'GRANTED', count: 3 }] });
    expect(types).toEqual([]);
  });

  it('get_encodesNumber_andOptsOutOfGlobalToast', () => {
    api.get('US 123/4').subscribe();
    const req = http.expectOne('/api/v1/patents/US%20123%2F4');
    expect(req.request.context.get(SKIP_ERROR_TOAST)).toBe(true);
    req.flush({});
  });

  it('datasetUrl_buildsExportLinkWithFiltersOnly', () => {
    expect(api.datasetUrl('csv', { q: 'ai', page: 4, size: 10 })).toBe('/api/v1/datasets/patents.csv?q=ai');
    expect(api.datasetUrl('json')).toBe('/api/v1/datasets/patents.json');
  });
});

describe('TrademarksApi', () => {
  let api: TrademarksApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(TrademarksApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('search_sendsNiceClassAndStatus', () => {
    api.search({ q: 'coffee', niceClass: 30, status: 'LIVE_REGISTERED' }).subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/trademarks');
    expect(req.request.params.get('niceClass')).toBe('30');
    expect(req.request.params.get('status')).toBe('LIVE_REGISTERED');
    req.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('get_requestsDetailBySerial', () => {
    api.get('97123456').subscribe();
    http.expectOne('/api/v1/trademarks/97123456').flush({});
  });
});
