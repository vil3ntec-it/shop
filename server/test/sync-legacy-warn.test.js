'use strict';
/**
 * F0-02 — راهِ قدیمِ همگام‌سازی (`/api/sync`، `/api/v1/shop/sync/push|pull`)
 * هنوز کار می‌کند ولی هشدار ثبت می‌کند.
 *
 * چرا: برنامهٔ اندروید و کامپیوتر از دکمهٔ «همگام‌سازی حالا» هنوز
 * `Syncer.run()` را صدا می‌زدند — push/pullِ قدیمی که مستقیم در دفتر ادغام
 * می‌کند و سایهٔ v1 را جلو نمی‌برد. برنامه درست شد؛ سرور طبقِ «قاعدهٔ
 * سازگاری» (docs/spec/02-current.md) کلاینتِ قدیمی را رد نمی‌کند، فقط در
 * لاگ می‌گوید چه کسی هنوز این راه را می‌رود.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');

test.before(async () => { await h.start(); });
test.after(async () => { await h.stop(); });

async function ownerWithShop(label) {
  const u = await h.newUser(`صاحب-${label}`, 'shop');
  await h.api('POST', '/api/shop', { token: u.accessToken, body: { name: `دکان ${label}` } });
  return u;
}

const legacyLines = (warn) => warn.mock.calls
  .map(c => c.arguments.map(String).join(' '))
  .filter(s => s.includes('[sync:legacy]'));

test('push و pullِ قدیمی هنوز کار می‌کنند، ولی هر کدام هشدارِ [sync:legacy] می‌دهند', async (t) => {
  const warn = t.mock.method(console, 'warn', () => {});
  const u = await ownerWithShop('قدیم-۱');

  const pushed = await h.api('POST', '/api/sync', {
    token: u.accessToken,
    headers: { 'X-App': 'shop', 'X-App-Version': '2.10.0' },
    body: {
      deviceId: 'legacy-dev-1',
      changes: [{ collection: 'products', id: 'p-legacy-1', updatedAt: Date.now(), data: { name: 'چای' } }],
    },
  });
  assert.equal(pushed.status, 200, JSON.stringify(pushed.body));
  assert.equal(pushed.body.applied, 1, 'سازگاری: کلاینتِ قدیمی هنوز می‌نویسد');

  let lines = legacyLines(warn);
  assert.equal(lines.length, 1, `یک هشدار برای push: ${JSON.stringify(lines)}`);
  assert.match(lines[0], /POST/);
  assert.match(lines[0], /legacy-dev-1/);
  assert.match(lines[0], /2\.10\.0/, 'نسخهٔ برنامه در هشدار هست تا معلوم باشد کدام نسخه هنوز این راه را می‌رود');

  const pulled = await h.api('GET', '/api/sync?since=0&deviceId=legacy-dev-2', { token: u.accessToken });
  assert.equal(pulled.status, 200, JSON.stringify(pulled.body));
  assert.ok(pulled.body.changes.some(c => c.id === 'p-legacy-1'), 'pullِ قدیمی همان ردیف را می‌دهد');
  lines = legacyLines(warn);
  assert.equal(lines.length, 2, 'pull هم هشدار دارد');
  assert.match(lines[1], /GET/);

  //  نامِ قدیمی‌تر هم (`/api/v1/shop/sync/push`) همان رفتار را دارد
  const oldName = await h.api('POST', '/api/v1/shop/sync/push', {
    token: u.accessToken,
    body: { deviceId: 'legacy-dev-3', changes: [] },
  });
  assert.equal(oldName.status, 200, JSON.stringify(oldName.body));
  assert.equal(legacyLines(warn).length, 3);
});

test('هشدار برای هر حساب و دستگاه هر ده دقیقه یک بار است، نه هر درخواست', async (t) => {
  const warn = t.mock.method(console, 'warn', () => {});
  const u = await ownerWithShop('قدیم-۲');
  for (let i = 0; i < 4; i++) {
    const r = await h.api('GET', '/api/sync/pull?since=0&deviceId=legacy-spam', { token: u.accessToken });
    assert.equal(r.status, 200);
  }
  assert.equal(legacyLines(warn).length, 1);
});

test('Sync v1 هشدارِ راهِ قدیم نمی‌دهد', async (t) => {
  const warn = t.mock.method(console, 'warn', () => {});
  const u = await ownerWithShop('تازه');
  const r = await h.api('GET', '/api/sync/v1/pull?device_id=v1-dev&since=0', {
    token: u.accessToken, headers: { 'X-App': 'shop' },
  });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(legacyLines(warn).length, 0);
});
