'use strict';
/**
 * ⛔ سقفِ نرخِ درهای تازهٔ دستهٔ ۳۱ (همراهِ `routes-idor-31.test.js`): سطلِ IP **پیش از** احراز — توکنِ نادرست یا
 * بی توکن هم شمرده می‌شود و پس از سقف ۴۲۹ می‌گیرد، نه ۴۰۱ (پس حدس زدنِ توکن هم بسته است) — و سطلِ حساب **پس از** آن:
 * همان کاربر از IP ِ تازه هم ۴۲۹، کاربر یا دکانِ دیگر باز. سقف‌ها پیش از ساختنِ برنامه (`h.start`) تنگ می‌شوند.
 */
process.env.TRUST_PROXY = 'true';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
//  `helpers` سقفِ عمومی را برای همهٔ آزمون‌ها باز می‌کند (RATE_GENERAL_MAX=100000)؛ این‌جا پیش از `h.start()` (ساختنِ
//  روترها و سطلِ سراسری) تنگ می‌شود تا خودِ سطل‌ها سنجیده شوند
Object.assign(require('../src/config').rateLimit, { generalMax: 6, sessionMax: 4, syncIpMax: 4, syncMax: 5 });
const { query, one, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');

let A;
let B;
let ipN = 0;
const freshIp = () => { ipN += 1; return `198.18.${Math.floor(ipN / 250)}.${(ipN % 250) + 1}`; };
const from = (ip, token) => ({ headers: { 'X-Forwarded-For': ip }, ...(token ? { token } : {}) });

async function shopWith(label) {
  resetAll();
  const o = await h.newUser(`صاحبِ ${label}`, 'shop');
  resetAll();
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: o.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const id = made.body.shop.id;
  const S = { id, owner: { token: o.accessToken, userId: o.user.id } };
  for (const role of ['manager', 'accountant']) {
    resetAll();
    const u = await h.newUser(`${role}-${label}`, 'shop');
    await query(`INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
                 VALUES ($1,$2,$3,$4,'active',$5,$5)`, [newId('mem'), id, u.user.id, role, now()]);
    S[role] = { token: u.accessToken, userId: u.user.id };
  }
  resetAll();
  return S;
}

test.before(async () => {
  await h.start();
  A = await shopWith('الف');
  B = await shopWith('ب');
});
test.after(async () => { await h.stop(); });
test.beforeEach(() => resetAll());

test('سطلِ IP پیش از احراز: توکنِ نادرست و بی توکن پس از سقف ۴۲۹ (نه ۴۰۱)؛ توکنِ درست از همان IP هم بسته؛ IP ِ دیگر باز', async () => {
  const doors = [
    ['GET', '/api/shop/staff-keys', 6], ['GET', '/api/shop/conflict-inbox', 6], ['GET', '/api/shop/conflict-inbox/mine', 6],
    ['GET', '/api/shop/customer-access?customerId=d-1', 6], ['POST', '/api/shop/customer-access', 6], ['GET', '/api/shop/settings-sync', 6],
    ['GET', '/api/shop/ledger-merge/config?v=3', 6], ['GET', '/api/shop/stock-ledger/config', 6],
    ['POST', '/api/auth/staff/device-key', 4], ['PUT', '/api/sync/v1/photos/p-1/thumb', 4],
  ];
  for (const [method, path, max] of doors) {
    resetAll();
    const ip = freshIp();
    for (let i = 1; i <= max; i++) {
      const r = await h.api(method, path, { ...from(ip, i % 2 ? `bad-${'x'.repeat(30)}-${i}` : null), body: method === 'GET' ? null : {} });
      assert.equal(r.status, 401, `${method} ${path} تلاشِ ${i}: ${r.status} ${JSON.stringify(r.body)}`);
    }
    const blocked = await h.api(method, path, { ...from(ip, 'bad-token-xxxxxxxxxxxxxxxxxxxxxxxx'), body: method === 'GET' ? null : {} });
    assert.equal(blocked.status, 429, `${method} ${path}: ${blocked.status}`);
    assert.equal(JSON.stringify(blocked.body).includes('bad-token'), false, 'پاسخِ ۴۲۹ توکن را برنمی‌گرداند');
    const real = await h.api(method, path, { ...from(ip, A.owner.token), body: method === 'GET' ? null : {} });
    assert.equal(real.status, 429, `${method} ${path}: توکنِ درست از همان IP هم پشتِ همان سطل`);
    const other = await h.api(method, path, { ...from(freshIp(), A.owner.token), body: method === 'GET' ? null : {} });
    assert.notEqual(other.status, 429, `${method} ${path}: IP ِ دیگر باز`);
  }
});

test('سطلِ حساب پس از احراز: دستگاه‌های کارمند (۶۰ برای هر کاربر) از IP ِ تازه هم ۴۲۹؛ کاربرِ دیگرِ همان دکان باز', async () => {
  for (let i = 1; i <= 60; i++) {
    const r = await h.get('/api/shop/staff-keys', from(freshIp(), A.owner.token));
    assert.equal(r.status, 200, `تلاشِ ${i}: ${JSON.stringify(r.body)}`);
  }
  assert.equal((await h.get('/api/shop/staff-keys', from(freshIp(), A.owner.token))).status, 429);
  assert.equal((await h.get('/api/shop/staff-keys', from(freshIp(), A.manager.token))).status, 200, 'سطلِ هر کاربر');
  //  خواندنِ دیگرِ همان کاربر (صندوق) سطلِ خودش را دارد
  assert.equal((await h.get('/api/shop/conflict-inbox', from(freshIp(), A.owner.token))).status, 200);
});

test('سطلِ حساب: صندوقِ صاحب (۱۲۰ خواندن و ۳۰ نوشتن برای هر کاربر)؛ نوشتنِ بی‌ردیف هم شمرده می‌شود', async () => {
  for (let i = 1; i <= 30; i++) {
    const r = await h.post(`/api/shop/conflict-inbox/${newId('cf')}/dismiss`, { reason: 'آزمونِ سقف' }, from(freshIp(), A.owner.token));
    assert.equal(r.status, 404, `نوشتنِ ${i}: ${JSON.stringify(r.body)}`);
  }
  const w = await h.post(`/api/shop/conflict-inbox/${newId('cf')}/resubmit`, {}, from(freshIp(), A.owner.token));
  assert.equal(w.status, 429, 'دوباره ثبت و کنار گذاشتن یک سطل‌اند');
  assert.equal((await h.post(`/api/shop/conflict-inbox/${newId('cf')}/resubmit`, {}, from(freshIp(), A.manager.token))).status, 404);
  //  سطلِ هر کاربرِ روتر (۱۲۰) همهٔ درخواست‌های پس از احراز را می‌شمارد: ۳۰ کنار گذاشتن + یک «دوباره ثبت» ِ بسته = ۳۱
  for (let i = 32; i <= 120; i++) {
    const r = await h.get('/api/shop/conflict-inbox', from(freshIp(), A.owner.token));
    assert.equal(r.status, 200, `خواندنِ ${i}`);
  }
  assert.equal((await h.get('/api/shop/conflict-inbox/mine', from(freshIp(), A.owner.token))).status, 429, 'سطلِ خواندنِ همان کاربر');
});

test('سطلِ دکان: سابقهٔ دسترسی (۱۲۰ برای هر دکان) — صاحب سطلِ دکان را پر کند حسابدارِ همان دکان هم ۴۲۹؛ دکانِ دیگر باز', async () => {
  for (let i = 1; i <= 120; i++) {
    const r = await h.get('/api/shop/customer-access?customerId=d-1', from(freshIp(), A.owner.token));
    assert.equal(r.status, 200, `تلاشِ ${i}`);
  }
  assert.equal((await h.get('/api/shop/customer-access?customerId=d-1', from(freshIp(), A.accountant.token))).status, 429);
  assert.equal((await h.get('/api/shop/customer-access?customerId=d-1', from(freshIp(), B.owner.token))).status, 200);
});

test('سطلِ حساب ِ همگام‌سازی روی درِ کارت: پس از سقفِ حساب (۵) ۴۲۹ از IP ِ تازه هم؛ دکانِ دیگر باز', async () => {
  const body = { data: [], of: '0'.repeat(32) };
  for (let i = 1; i <= 5; i++) {
    const r = await h.put('/api/sync/v1/photos/p-1/thumb', body, from(freshIp(), A.owner.token));
    assert.equal(r.status, 400, `تلاشِ ${i}: ${JSON.stringify(r.body)}`);
  }
  assert.equal((await h.put('/api/sync/v1/photos/p-1/thumb', body, from(freshIp(), A.owner.token))).status, 429);
  assert.equal((await h.put('/api/sync/v1/photos/p-1/thumb', body, from(freshIp(), B.owner.token))).status, 400);
  //  هیچ ردیفی
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM product_photo_thumbs')).n, 0);
});
