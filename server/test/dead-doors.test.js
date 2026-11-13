'use strict';
/**
 * درهای بی صدازننده برداشته شدند (۱۴۰۵/۰۸/۲۲، `docs/SECURITY-AUDIT-fa.md`، قاعدهٔ «درِ بی صدازننده نگه داشته نمی‌شود»
 * در `docs/RULES-fa.md`) — سطحِ حمله کم شد و برنگردد:
 *
 *   ۱) درِ مستقیمِ داده (`routes/data.js`، ۵۱ در: `/api/products|inventory|sales|sale-items|suppliers|purchases|returns|
 *      debtors|expenses|payments` با GET/POST و GET/PUT/DELETE ِ `/:id`، و `POST /api/sales/full`) — راهِ دومِ نوشتن روی
 *      دفترِ دکان که هر نگهبانِ تازه باید دو بار رویش وصله می‌شد؛ هیچ برنامه‌ای صدایش نمی‌زد.
 *   ۲) نام‌ها و درهای مردهٔ دکان، «من»، پورتال و ورود (فهرستِ `GONE`).
 *
 * با توکنِ معتبرِ صاحب هر کدام ۴۰۴ (نه ۴۰۱، نه نوشتن) — با `/api` و `/api/v1`؛ بی‌توکن هیچ‌کدام ۲xx نیست؛ هیچ ردیفی از
 * دفترِ دکان عوض نمی‌شود؛ و فهرستِ زندهٔ مسیرها هیچ‌کدام را ندارد. درهایی که برنامهٔ کهنهٔ در دست (APK ِ پوستهٔ
 * WebView ِ `android/` و نسخهٔ بومیِ بایگانی‌شده) هنوز صدا می‌زند عمداً ماندند و همین‌جا زنده سنجیده می‌شوند (`KEPT`)،
 * تا آزمون بی‌اثر نباشد (۴۰۴ از سوار نشدنِ کلِ `/api` نیامده باشد).
 */
const inventory = require('./route-inventory');

inventory.tapMounts();   //  پیش از ساختنِ برنامه

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { one } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');

const SETS = ['products', 'inventory', 'sales', 'sale-items', 'suppliers', 'purchases', 'returns', 'debtors', 'expenses', 'payments'];
const DATA = [
  ...SETS.flatMap((p) => [`GET /api/${p}`, `POST /api/${p}`, `GET /api/${p}/:id`, `PUT /api/${p}/:id`, `DELETE /api/${p}/:id`]),
  'POST /api/sales/full',
];
const GONE = [
  ...DATA,
  'GET /api/shop/memberships', 'POST /api/shop/leave', 'GET /api/shop/settings', 'PUT /api/shop/settings',
  'PUT /api/shop/permissions/roles/:role', 'GET /api/shop/approvals',
  'GET /api/me/settings', 'PUT /api/me/settings', 'POST /api/me/redeem', 'POST /api/me/errors',
  'GET /api/portal/subscriptions',
  'POST /api/auth/staff-login',
];
/** ماندند — برنامهٔ کهنهٔ در دست صدایشان می‌زند (دلیل در `docs/SECURITY-AUDIT-fa.md`) یا قاعده‌ای در RULES دارند */
const KEPT = [
  ['GET /api/billing/status', 200], ['GET /api/billing/plans', 200],
  ['GET /api/shop', 200], ['GET /api/shop/audit', 200], ['GET /api/shop/me', 200], ['GET /api/portal/me', 200],
  ['GET /api/sync', 200],
];

const url = (key, prefix = '/api') => key.split(' ')[1].replace(/^\/api/, prefix).replace(/:[A-Za-z]+/g, 'x1');
const bodyOf = (key) => (/^(GET|DELETE) /.test(key) ? null : { id: 'gone-1', data: { name: 'نباید بنشیند' }, sale: { id: 'gone-s' }, items: [{ id: 'gone-i' }], perms: {}, settings: { a: 1 }, cloudSync: true, code: 'ZZZZ-ZZZZ', message: 'x' });

let owner;
let shopId;
test.before(async () => {
  await h.start();
  owner = await h.newUser('صاحبِ درهای مرده');
  const made = await h.post('/api/shop', { name: 'دکانِ درهای مرده' }, { token: owner.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  shopId = made.body.shop.id;
  //  دفترِ واقعی تا «هیچ ردیفی عوض نشد» بی‌اثر نباشد
  const seeded = await h.legacyPush(owner.accessToken, SETS.map((p) => h.change({ 'sale-items': 'saleItems', inventory: 'warehouseEntries', payments: 'transactions' }[p] || p,
    { id: 'x1', name: `ردیفِ ${p}` })), { deviceId: 'dev-dead' });
  assert.equal(seeded.status, 200, JSON.stringify(seeded.body));
  assert.equal(seeded.body.applied, SETS.length, JSON.stringify(seeded.body));
});
test.after(async () => { await h.stop(); });

const footprint = async () => (await one(
  `SELECT (SELECT COALESCE(max(rev), 0) FROM products WHERE shop_id=$1)::text || ':' ||
          (SELECT count(*) FROM audit_logs WHERE shop_id=$1)::text AS f`, [shopId])).f;

test('هر درِ برداشته با توکنِ معتبرِ صاحب ۴۰۴ است (`/api` و `/api/v1`)، و هیچ ردیفی از دفتر عوض نمی‌شود', async () => {
  const before = await footprint();
  const rev = Number((await h.get('/api/sync?since=0', { token: owner.accessToken })).body.serverRev);
  const bad = [];
  for (const key of GONE) {
    for (const prefix of ['/api', '/api/v1']) {
      resetAll();
      const r = await h.api(key.split(' ')[0], url(key, prefix), { token: owner.accessToken, body: bodyOf(key) });
      if (r.status !== 404) bad.push(`${key} (${prefix}) ⇒ ${r.status} ${JSON.stringify(r.body).slice(0, 120)}`);
      else if (r.body?.error?.code !== 'not_found') bad.push(`${key} (${prefix}) ⇒ ۴۰۴ ِ دیگر: ${JSON.stringify(r.body)}`);
    }
  }
  assert.deepEqual(bad, [], bad.join('\n'));
  assert.equal(await footprint(), before, 'هیچ نوشتن و هیچ سابقه‌ای');
  assert.equal(Number((await h.get('/api/sync?since=0', { token: owner.accessToken })).body.serverRev), rev, 'rev ِ دفتر همان');
  for (const p of SETS) assert.ok(JSON.stringify(await h.legacyRows(owner.accessToken, { 'sale-items': 'saleItems', inventory: 'warehouseEntries', payments: 'transactions' }[p] || p)).includes(`ردیفِ ${p}`), p);
});

test('بی‌توکن هیچ درِ برداشته ۲xx نمی‌دهد؛ درِ مستقیمِ داده و نامِ دومِ ورودِ شاگرد ۴۰۴ (دیگر ۴۰۱ ِ «همه‌گیر» نیست)', async () => {
  const bad = [];
  for (const key of GONE) {
    resetAll();
    const r = await h.api(key.split(' ')[0], url(key), { body: bodyOf(key) });
    if (r.status < 300) bad.push(`${key} بی‌توکن ⇒ ${r.status}`);
    if ((DATA.includes(key) || key === 'POST /api/auth/staff-login') && r.status !== 404) bad.push(`${key} بی‌توکن ⇒ ${r.status} (۴۰۴ می‌خواستیم)`);
  }
  assert.deepEqual(bad, [], bad.join('\n'));
  //  مسیرِ ناشناخته‌ای که هرگز نبود هم همان ۴۰۴ (درِ داده دیگر روی `/` همه را با ۴۰۱ نمی‌قاپد)
  assert.equal((await h.get('/api/no-such-door')).status, 404);
});

test('فهرستِ زندهٔ مسیرها هیچ درِ برداشته را ندارد؛ درهای ماندهٔ برنامهٔ کهنه و جانشین‌ها زنده‌اند', async () => {
  const keys = new Set(inventory.live(h.server().listeners('request')[0]).map((r) => r.key));
  assert.ok(keys.size > 600, `فقط ${keys.size} در — فهرستِ زنده خوانده نشد؟`);
  const back = GONE.filter((k) => keys.has(k));
  assert.deepEqual(back, [], `درِ برداشته برگشت:\n${back.join('\n')}`);
  //  نام‌های کهنه‌ای که APK ِ در دست صدا می‌زند (هم‌دستگیرهٔ درِ زنده) و درِ قاعده‌دار
  for (const k of ['GET /api/billing/status', 'GET /api/billing/plans', 'POST /api/billing/request', 'POST /api/shop/invite',
    'GET /api/shop', 'POST /api/shop', 'PUT /api/shop', 'POST /api/shop/staff/join', 'POST /api/shop/active', 'GET /api/shop/audit',
    'POST /api/auth/staff', 'POST /api/sync', 'GET /api/sync', 'GET /api/portal/settings', 'POST /api/portal/redeem', 'POST /api/vip/redeem',
    'POST /api/errors']) {
    assert.ok(keys.has(k), `${k} باید بماند`);
  }
  for (const [key, want] of KEPT) {
    resetAll();
    const r = await h.api(key.split(' ')[0], url(key), { token: owner.accessToken });
    assert.equal(r.status, want, `${key} ⇒ ${r.status} ${JSON.stringify(r.body).slice(0, 120)}`);
  }
});
