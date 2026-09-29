import { HttpEventType, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { SKIP_AUTH_REDIRECT, SKIP_ERROR_TOAST } from '../http/http-utils';
import type { PatentFilingRequest } from '../models';
import { AccountApi } from './account.api';
import { AuthApi } from './auth.api';
import { FeesApi } from './fees.api';
import { IngestApi } from './ingest.api';
import { StatusApi } from './status.api';

function setup() {
  TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
  return TestBed.inject(HttpTestingController);
}

describe('FeesApi', () => {
  let api: FeesApi;
  let http: HttpTestingController;
  beforeEach(() => {
    http = setup();
    api = TestBed.inject(FeesApi);
  });
  afterEach(() => http.verify());

  it('schedule_passesCategory_andAcceptsCurrent', () => {
    api.schedule('current', 'TRADEMARK').subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/fees/schedules/current');
    expect(req.request.params.get('category')).toBe('TRADEMARK');
    req.flush({ items: [] });
  });

  it('patentFiling_postsRequestBody_inline', () => {
    const body: PatentFilingRequest = {
      applicationType: 'UTILITY',
      entitySize: 'SMALL',
      totalClaims: 25,
      independentClaims: 4,
      multipleDependentClaims: false,
      specificationSheets: 120,
      filedElectronically: true,
      lateFilingSurcharge: false,
      extensionMonths: 0,
      continuedExamination: 'NONE',
      prioritizedExamination: false,
    };
    api.patentFiling(body).subscribe();
    const req = http.expectOne('/api/v1/fees/patent/filing');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    expect(req.request.context.get(SKIP_ERROR_TOAST)).toBe(true);
    req.flush({});
  });

  it('saveQuote_and_quote_useQuotesResource', () => {
    api.saveQuote({ kind: 'TRADEMARK', request: { filingType: 'APPLICATION', numberOfClasses: 2, insufficientInformation: false, freeFormTextIds: false, extraCharacterBlocks: 0, inGracePeriod: false } }).subscribe();
    const post = http.expectOne('/api/v1/fees/quotes');
    expect(post.request.method).toBe('POST');
    expect(post.request.body.kind).toBe('TRADEMARK');
    post.flush({ id: 'q1' });

    api.quote('q1').subscribe();
    http.expectOne('/api/v1/fees/quotes/q1').flush({});
  });

  it('maintenance_postsToMaintenanceEndpoint', () => {
    api.maintenance({ entitySize: 'MICRO', grantDate: '2020-01-07' }).subscribe();
    const req = http.expectOne('/api/v1/fees/patent/maintenance');
    expect(req.request.body).toEqual({ entitySize: 'MICRO', grantDate: '2020-01-07' });
    req.flush({ windows: [], patentExpired: false, scheduleCode: 'FY2025' });
  });
});

describe('IngestApi', () => {
  let api: IngestApi;
  let http: HttpTestingController;
  beforeEach(() => {
    http = setup();
    api = TestBed.inject(IngestApi);
  });
  afterEach(() => http.verify());

  it('upload_sendsMultipartFilesField_withProgressEvents', () => {
    const files = [new File(['<a/>'], 'a.xml'), new File(['<b/>'], 'b.xml')];
    const types: HttpEventType[] = [];
    api.upload(files).subscribe((e) => types.push(e.type));

    const req = http.expectOne('/api/v1/ingest/uploads');
    expect(req.request.method).toBe('POST');
    expect(req.request.reportProgress).toBe(true);
    const form = req.request.body as FormData;
    expect(form.getAll('files').length).toBe(2);
    req.event({ type: HttpEventType.UploadProgress, loaded: 5, total: 10 });
    req.flush([]);
    expect(types).toContain(HttpEventType.UploadProgress);
    expect(types).toContain(HttpEventType.Response);
  });

  it('jobs_sendsStatusAndPaging_andMapsPage', () => {
    let total = -1;
    api.jobs({ status: 'FAILED', page: 1, size: 10 }).subscribe((p) => (total = p.totalPages));
    const req = http.expectOne((r) => r.url === '/api/v1/ingest/jobs');
    expect(req.request.params.get('status')).toBe('FAILED');
    expect(req.request.params.get('page')).toBe('1');
    req.flush({ content: [], page: 1, size: 10, totalElements: 31 });
    expect(total).toBe(4);
  });

  it('retry_delete_and_sample_hitTheRightUrls', () => {
    api.retry('j-1').subscribe();
    expect(http.expectOne('/api/v1/ingest/jobs/j-1/retry').request.method).toBe('POST');
    api.delete('j-1').subscribe();
    expect(http.expectOne('/api/v1/ingest/jobs/j-1').request.method).toBe('DELETE');
    api.sample('grant.xml').subscribe();
    expect(http.expectOne('/api/v1/ingest/samples/grant.xml').request.responseType).toBe('blob');
  });
});

describe('AccountApi / AuthApi / StatusApi', () => {
  let http: HttpTestingController;
  beforeEach(() => (http = setup()));
  afterEach(() => http.verify());

  it('apiKeys_crud', () => {
    const api = TestBed.inject(AccountApi);
    api.createApiKey('ci').subscribe();
    const create = http.expectOne('/api/v1/account/api-keys');
    expect(create.request.body).toEqual({ name: 'ci' });
    create.flush({});
    api.rotateApiKey('k1').subscribe();
    http.expectOne('/api/v1/account/api-keys/k1/rotate').flush({});
    api.revokeApiKey('k1').subscribe();
    expect(http.expectOne('/api/v1/account/api-keys/k1').request.method).toBe('DELETE');
  });

  it('usage_sendsDays_andDefaultsArrays', () => {
    let daily: unknown;
    TestBed.inject(AccountApi).usage(30).subscribe((u) => (daily = u.daily));
    const req = http.expectOne((r) => r.url === '/api/v1/account/usage');
    expect(req.request.params.get('days')).toBe('30');
    req.flush({ totalRequests: 0 });
    expect(daily).toEqual([]);
  });

  it('adminUsers_isPaged', () => {
    TestBed.inject(AccountApi).adminUsers({ page: 1, size: 20 }).subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/admin/users');
    expect(req.request.params.get('page')).toBe('1');
    req.flush({ content: [], page: 1, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('login_optsOutOfToastAndRedirect', () => {
    TestBed.inject(AuthApi).login({ email: 'a@b.c', password: 'x' }).subscribe();
    const req = http.expectOne('/api/v1/auth/login');
    expect(req.request.context.get(SKIP_ERROR_TOAST)).toBe(true);
    expect(req.request.context.get(SKIP_AUTH_REDIRECT)).toBe(true);
    req.flush({});
  });

  it('status_and_stats_urls', () => {
    const api = TestBed.inject(StatusApi);
    api.gatewayStatus().subscribe();
    http.expectOne('/gateway/status').flush({ services: [] });
    api.stats().subscribe();
    http.expectOne('/api/v1/stats').flush({});
  });
});
