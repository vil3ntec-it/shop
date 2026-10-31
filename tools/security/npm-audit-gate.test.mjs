/**
 * آزمونِ دروازهٔ npm audit با گزارشِ ساختگی به شکلِ JSONِ نسخهٔ ۲. اجرا: node --test "tools/security/*.test.mjs"
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import { advisories, evaluate, infraError, HIGH_BLOCK_FROM } from './npm-audit-gate.mjs';

const adv = (name, severity, ghsa) => ({ source: 1, name, dependency: name, title: `${name} issue`, url: `https://github.com/advisories/${ghsa}`, severity, range: '<9' });
//  همان شکلِ گزارشِ واقعیِ server پیش از #578: proxy-addr critical، body-parser high، qs moderate
const REPORT = {
  auditReportVersion: 2,
  vulnerabilities: {
    'proxy-addr': { name: 'proxy-addr', severity: 'critical', isDirect: false, via: [adv('proxy-addr', 'critical', 'GHSA-jqcg-44mw-7w3h')], range: '1.1.0 - 2.0.7' },
    'body-parser': { name: 'body-parser', severity: 'high', isDirect: false, via: [adv('body-parser', 'high', 'GHSA-27r3-jxmh-p828')], range: '2.0.0 - 2.3.0' },
    qs: { name: 'qs', severity: 'moderate', isDirect: false, via: [adv('qs', 'moderate', 'GHSA-6pq8-jhm2-2vcw')], range: '6.7.0 - 6.16.0' },
    express: { name: 'express', severity: 'critical', isDirect: true, via: ['proxy-addr', 'body-parser'], range: '*' },
  },
  metadata: { vulnerabilities: { critical: 2, high: 1, moderate: 1, low: 0, info: 0 } },
};
const CLEAN = { auditReportVersion: 2, vulnerabilities: {}, metadata: { vulnerabilities: { critical: 0, high: 0 } } };
const ids = (xs) => xs.map((x) => x.id).sort();

test('مشاوره‌ها یکتا و فقط از via ِ شیء (نه زنجیرهٔ رشته‌ای)', () => {
  assert.deepEqual(ids(advisories(REPORT)), ['GHSA-27r3-jxmh-p828', 'GHSA-6pq8-jhm2-2vcw', 'GHSA-jqcg-44mw-7w3h']);
});

test('critical سد است؛ high پیش از تاریخِ طرح هشدار و از آن روز سد', () => {
  const before = evaluate(REPORT, [], '2026-11-01');
  assert.deepEqual(ids(before.block), ['GHSA-jqcg-44mw-7w3h']);
  assert.deepEqual(ids(before.warn), ['GHSA-27r3-jxmh-p828']);
  assert.deepEqual(ids(before.info), ['GHSA-6pq8-jhm2-2vcw']);
  const after = evaluate(REPORT, [], HIGH_BLOCK_FROM);
  assert.deepEqual(ids(after.block), ['GHSA-27r3-jxmh-p828', 'GHSA-jqcg-44mw-7w3h']);
});

test('استثنا فقط با تاریخِ نگذشته؛ بی تاریخ یا گذشته دوباره سد', () => {
  const ok = evaluate(REPORT, [{ id: 'GHSA-jqcg-44mw-7w3h', until: '2026-11-30' }], '2026-11-01');
  assert.deepEqual(ok.block, []);
  assert.deepEqual(ids(ok.allowed), ['GHSA-jqcg-44mw-7w3h']);
  const gone = evaluate(REPORT, [{ id: 'GHSA-jqcg-44mw-7w3h', until: '2026-10-01' }], '2026-11-01');
  assert.deepEqual(ids(gone.block), ['GHSA-jqcg-44mw-7w3h']);
  assert.deepEqual(ids(gone.expired), ['GHSA-jqcg-44mw-7w3h']);
  const undated = evaluate(REPORT, [{ id: 'GHSA-jqcg-44mw-7w3h' }], '2026-11-01');
  assert.deepEqual(ids(undated.block), ['GHSA-jqcg-44mw-7w3h']);
});

test('گزارشِ پاک چیزی سد نمی‌کند', () => {
  assert.deepEqual(evaluate(CLEAN, [], '2027-01-01').block, []);
  assert.equal(infraError(CLEAN), null);
});

test('خطای رجیستری یافته نیست', () => {
  assert.match(infraError({ error: { code: 'ENOTFOUND', summary: 'registry down' } }), /ENOTFOUND/);
  assert.ok(infraError(null));
  assert.ok(infraError({ message: 'x' }));
  assert.equal(infraError(REPORT), null);
});
