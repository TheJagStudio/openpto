import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';

import { safeReturnUrl } from '../features/auth/return-url';
import { claimTree } from '../features/patents/claims';
import { toSteps } from '../features/pipeline/stage-stepper.component';
import { checkFiles } from '../features/pipeline/upload-validation';
import { niceCeil } from '../ui/bar-chart.component';
import { tokenizeJson } from '../ui/json-viewer.component';
import { pageWindow } from '../ui/pager.component';
import { formatBytes, formatDuration, humanize } from './format';
import { toHttpParams, toPage } from './http/http-utils';
import { fieldErrors, retryAfterSeconds, toProblem } from './http/problem';
import type { IngestJobResponse } from './models';

describe('problem helpers', () => {
  it('toProblem reads a ProblemDetail body and falls back to the X-Request-Id header', () => {
    const err = new HttpErrorResponse({
      status: 400,
      error: { title: 'Validation failed', status: 400, errors: [{ field: 'email', message: 'must be valid' }] },
      headers: new HttpHeaders({ 'X-Request-Id': 'abc' }),
    });
    const p = toProblem(err);
    expect(p.title).toBe('Validation failed');
    expect(p.requestId).toBe('abc');
    expect(fieldErrors(p)).toEqual({ email: 'must be valid' });
  });

  it('toProblem handles network errors and wrapped resource errors', () => {
    expect(toProblem(new HttpErrorResponse({ status: 0 })).title).toBe('Service unreachable');
    const wrapped = new Error('wrapped', { cause: new HttpErrorResponse({ status: 404, error: { title: 'Not Found', status: 404 } }) });
    expect(toProblem(wrapped).status).toBe(404);
  });

  it('retryAfterSeconds supports seconds and HTTP dates', () => {
    expect(retryAfterSeconds('30')).toBe(30);
    expect(retryAfterSeconds(null)).toBeNull();
    const now = Date.parse('2026-01-01T00:00:00Z');
    expect(retryAfterSeconds('Thu, 01 Jan 2026 00:01:00 GMT', now)).toBe(60);
  });
});

describe('http utils', () => {
  it('toHttpParams drops empty values', () => {
    expect(toHttpParams({ a: 'x', b: '', c: null, d: undefined, e: 0, f: false }).toString()).toBe('a=x&e=0&f=false');
  });

  it('toPage clamps and fills defaults', () => {
    expect(toPage(null)).toEqual({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });
});

describe('formatting', () => {
  it('humanize keeps acronyms', () => {
    expect(humanize('LIVE_PENDING')).toBe('Live pending');
    expect(humanize('EXTENSION_SOU')).toBe('Extension SOU');
    expect(humanize('US_PATENT_GRANT')).toBe('US patent grant');
  });
  it('bytes and durations', () => {
    expect(formatBytes(512)).toBe('512 B');
    expect(formatBytes(50 * 1024 * 1024)).toBe('50 MB');
    expect(formatDuration(1500)).toBe('1.5 s');
  });
});

describe('ui helpers', () => {
  it('pageWindow shows first/last with ellipses', () => {
    expect(pageWindow(1, 3)).toEqual([1, 2, 3]);
    expect(pageWindow(10, 20)).toEqual([1, null, 9, 10, 11, null, 20]);
  });

  it('niceCeil rounds axis maxima', () => {
    expect(niceCeil(0)).toBe(1);
    expect(niceCeil(87)).toBe(100);
    expect(niceCeil(230)).toBe(250);
  });

  it('tokenizeJson classifies keys, strings, numbers, booleans and null', () => {
    const kinds = tokenizeJson('{"a": "x", "n": 1.5, "b": true, "z": null}')
      .filter((t) => t.kind !== 'punct' && t.kind !== 'space')
      .map((t) => `${t.kind}:${t.text}`);
    expect(kinds).toEqual(['key:"a"', 'string:"x"', 'key:"n"', 'number:1.5', 'key:"b"', 'boolean:true', 'key:"z"', 'null:null']);
  });

  it('tokenizeJson never produces markup (text only)', () => {
    const text = tokenizeJson(JSON.stringify({ x: '<img src=x onerror=alert(1)>' })).map((t) => t.text).join('');
    expect(text).toContain('<img');
  });
});

describe('feature helpers', () => {
  it('claimTree indents dependent claims by chain depth', () => {
    const rows = claimTree([
      { number: 3, text: 'c', independent: false, dependsOn: 2 },
      { number: 1, text: 'a', independent: true, dependsOn: null },
      { number: 2, text: 'b', independent: false, dependsOn: 1 },
    ]);
    expect(rows.map((r) => [r.number, r.depth])).toEqual([[1, 0], [2, 1], [3, 2]]);
  });

  it('checkFiles enforces extension, size and count', () => {
    const big = new File(['x'], 'big.xml');
    Object.defineProperty(big, 'size', { value: 60 * 1024 * 1024 });
    const { accepted, rejected } = checkFiles([new File(['<a/>'], 'ok.xml'), new File(['x'], 'bad.pdf'), big], 9);
    expect(accepted.map((f) => f.name)).toEqual(['ok.xml']);
    expect(rejected.map((r) => r.name)).toEqual(['bad.pdf', 'big.xml']);
    expect(checkFiles([new File(['<a/>'], 'x.zip')], 10).rejected[0]!.reason).toContain('At most 10');
  });

  it('safeReturnUrl blocks open redirects', () => {
    expect(safeReturnUrl('/patents?q=x')).toBe('/patents?q=x');
    expect(safeReturnUrl('//evil.com')).toBe('/');
    expect(safeReturnUrl('https://evil.com')).toBe('/');
    expect(safeReturnUrl('/login')).toBe('/');
  });

  it('toSteps marks done/active/pending from job stages', () => {
    const job = {
      status: 'LOADING',
      stages: [
        { stage: 'UPLOADED', status: 'DONE', at: '2026-01-01T00:00:00Z', message: null },
        { stage: 'PARSED', status: 'DONE', at: '2026-01-01T00:00:01Z', message: null },
        { stage: 'TRANSFORMED', status: 'DONE', at: '2026-01-01T00:00:02Z', message: null },
      ],
    } as unknown as IngestJobResponse;
    expect(toSteps(job).map((s) => s.state)).toEqual(['done', 'done', 'done', 'active']);

    const failed = { status: 'FAILED', stages: [{ stage: 'UPLOADED', status: 'DONE', at: null, message: null }, { stage: 'PARSED', status: 'FAILED', at: null, message: 'bad xml' }] } as unknown as IngestJobResponse;
    expect(toSteps(failed).map((s) => s.state)).toEqual(['done', 'failed', 'pending', 'pending']);
  });
});
