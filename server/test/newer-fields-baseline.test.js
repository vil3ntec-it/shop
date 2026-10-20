'use strict';
/**
 * A00 — نگهبانِ فهرستِ منجمدِ `PRE_A00` (`src/lib/newer-fields.js`).
 *
 * `PRE_A00` مدل‌های دفترِ ساخت‌هایی است که پیش از A00 (#341) منتشر شده‌اند و دیگر عوض
 * نمی‌شوند. ساختِ A00 به بعد با `caps: ["a00"]` از تور می‌گذرد، پس فیلدی که پس از A00 به
 * مدل بیاید **هیچ** ردیفی این‌جا لازم ندارد (#286، #315، #318، #340 بی ویرایشِ این فهرست
 * مرج می‌شوند).
 *
 *   ۱) اثرِ انگشتِ فهرست همان است که با #341 منجمد شد. تنها دلیلِ عوض کردنش اصلاحِ کفِ
 *      یک فیلدِ پیش از A00 است — آن‌وقت همین‌جا هم با دلیل عوض شود.
 *   ۲) روزِ انجماد هر فیلدِ مدلِ دفتر (`parseModels` ِ `test/e2e/lib/native.mjs`) در فهرست
 *      بود؛ فیلدِ امروزِ مدل که در فهرست نیست باید تازه‌تر از A00 باشد (فقط چاپ می‌شود).
 *   ۳) جدول‌ها همگامِ v1 ِ دکان‌اند و کف‌ها نسخهٔ معتبرِ هر دو خط.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const N = require('../src/lib/newer-fields');
const compat = require('../src/lib/app-compat');
const { tablesOf } = require('../src/lib/sync-v1');

/** اثرِ انگشتِ `PRE_A00` در روزِ انجماد (#341) — ۱۴ جدول، ۱۴۰ فیلد */
const FROZEN = 'a21ce9290b47a382e42c9979956b2dae191ce3af83f32462178d432bbacf756e';

const canonical = (o) => JSON.stringify(o, (k, v) => (v && typeof v === 'object' && !Array.isArray(v)
  ? Object.fromEntries(Object.keys(v).sort().map((x) => [x, v[x]])) : v));

test('PRE_A00 منجمد است', () => {
  const fields = Object.values(N.PRE_A00).reduce((s, t) => s + Object.keys(t).length, 0);
  assert.deepEqual([Object.keys(N.PRE_A00).length, fields], [14, 140]);
  assert.equal(crypto.createHash('sha256').update(canonical(N.PRE_A00)).digest('hex'), FROZEN,
    'PRE_A00 عوض شد. فیلدِ تازهٔ مدل این‌جا نمی‌آید (ساختِ A00 با caps از تور می‌گذرد)؛ '
    + 'فقط کفِ یک فیلدِ پیش از A00 با دلیل اصلاح می‌شود و همراهش FROZEN.');
});

test('فیلدهای امروزِ مدل که در PRE_A00 نیستند تازه‌تر از A00اند', async (t) => {
  const native = await import('./e2e/lib/native.mjs');
  const newer = [];
  for (const [table, cls] of Object.entries(native.COLLECTION_MODEL)) {
    for (const f of native.MODELS[cls]) {
      if (!N.PRE_A00[table] || !Object.prototype.hasOwnProperty.call(N.PRE_A00[table], f.key)) newer.push(`${table}.${f.key}`);
    }
  }
  assert.ok(Object.keys(native.COLLECTION_MODEL).length >= 14, 'مدل‌ها خوانده نشدند');
  t.diagnostic(newer.length ? `پس از A00: ${newer.join(', ')}` : 'هیچ فیلدِ پس از A00');
});

test('PRE_A00: جدول‌های همگام و کف‌های معتبر', () => {
  const tables = new Set(tablesOf('shop'));
  for (const [table, fields] of Object.entries(N.PRE_A00)) {
    assert.ok(tables.has(table), `${table} جدولِ همگامِ دکان نیست`);
    for (const [key, floor] of Object.entries(fields)) {
      if (floor === null) continue;
      assert.ok(/^3\.2\.\d+$/.test(floor.phone) && compat.valid(floor.phone), `${table}.${key} گوشی`);
      assert.ok(/^1\.0\.\d+$/.test(floor.desktop) && compat.valid(floor.desktop), `${table}.${key} کامپیوتر`);
    }
  }
});
