'use strict';
/**
 * مصرف و هزینهٔ پیامک در پنل — OPS-09 (مسیرهای `admin-system` روی دفترِ ۱۲۰).
 *
 *   ۱) هر دو مسیر فقط مدیرِ ارشد؛ بی توکن ۴۰۱، مدیرِ عادی ۴۰۳.
 *   ۲) PUT /sms-cost: عددِ صحیحِ نامنفی ⇒ ذخیره و سابقه با شناسهٔ همین مدیر؛ نادرست ۴۰۰ و بی‌تغییر.
 *   ۳) GET /sms-usage: همان `report()` — هر دکان جدا، بی‌دکان جدا، هزینه با بهای لحظهٔ ثبت.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const usage = require('../src/lib/sms-usage');

let root = '';
let clerk = '';
let rootId = '';
let shopId = '';

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  rootId = newId('adm');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'root','ارشد',$2,'superadmin','active',$3)`,
    [rootId, await pw.hashPassword('Root!12345'), now()]
  );
  root = (await h.post('/api/admin/login', { username: 'root', password: 'Root!12345' })).body.token;
  await h.post('/api/admin/admins', { username: 'clerk', password: 'Clerk!12345', role: 'admin' }, { token: root });
  clerk = (await h.post('/api/admin/login', { username: 'clerk', password: 'Clerk!12345' })).body.token;
  const u = await h.newUser('پیامکی', 'shop');
  shopId = (await h.post('/api/shop', { name: 'دکانِ پیامکی' }, { token: u.accessToken })).body.shop.id;
});
test.after(async () => { await h.stop(); });

test('فقط مدیرِ ارشد', async () => {
  assert.equal((await h.get('/api/admin/system/sms-usage')).status, 401);
  assert.equal((await h.get('/api/admin/system/sms-usage', { token: clerk })).status, 403);
  assert.equal((await h.put('/api/admin/system/sms-cost', { unitCostMinor: 5 }, { token: clerk })).status, 403);
  assert.equal(await usage.unitCost(), 0);
});

test('بها: ذخیره با سابقهٔ همین مدیر؛ نادرست ۴۰۰ و بی‌تغییر', async () => {
  const ok = await h.put('/api/admin/system/sms-cost', { unitCostMinor: 250 }, { token: root });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.deepEqual(ok.body, { before: 0, unitCostMinor: 250, currency: 'AFN' });
  const log = await one(`SELECT user_id FROM audit_logs WHERE action='admin.sms_unit_cost_set' ORDER BY created_at DESC LIMIT 1`);
  assert.equal(log.user_id, rootId);
  for (const bad of [-1, 2.5, 'x', null]) {
    const r = await h.put('/api/admin/system/sms-cost', { unitCostMinor: bad }, { token: root });
    assert.equal(r.status, 400, String(bad));
    assert.equal(r.body.error.code, 'bad_sms_cost');
  }
  assert.equal(await usage.unitCost(), 250);
});

test('مصرف: هر دکان جدا و بی‌دکان جدا، هزینه با بهای لحظهٔ ثبت', async () => {
  await usage.record({ shopId, purpose: 'store_phone', via: 'sms', ok: true, parts: 2 });
  await usage.record({ shopId, purpose: 'store_phone', via: 'log', ok: true });
  await usage.record({ purpose: 'login', via: 'sms', ok: false });
  await h.put('/api/admin/system/sms-cost', { unitCostMinor: 1000 }, { token: root });
  const r = await h.get('/api/admin/system/sms-usage?days=7', { token: root });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.headers.get('cache-control'), 'no-store');
  assert.equal(r.body.days, 7);
  assert.equal(r.body.unitCostMinor, 1000);
  const shop = r.body.shops.find((s) => s.shopId === shopId);
  assert.deepEqual([shop.shopName, shop.attempts, shop.sent, shop.mock, shop.costMinor], ['دکانِ پیامکی', 2, 1, 1, 500]);
  const none = r.body.shops.find((s) => s.shopId === '');
  assert.deepEqual([none.attempts, none.failed, none.costMinor], [1, 1, 0]);
  assert.equal(r.body.total.costMinor, 500);
});
