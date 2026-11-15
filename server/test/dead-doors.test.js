'use strict';
/**
 * درهای بی صدازننده برداشته شدند (۱۴۰۵/۰۸/۲۲، `docs/SECURITY-AUDIT-fa.md`، قاعدهٔ «درِ بی صدازننده نگه داشته نمی‌شود»
 * در `docs/RULES-fa.md`) — سطحِ حمله کم شد و برنگردد:
 *
 *   ۱) درِ مستقیمِ داده (`routes/data.js`، ۵۱ در: `/api/products|inventory|sales|sale-items|suppliers|purchases|returns|
 *      debtors|expenses|payments` با GET/POST و GET/PUT/DELETE ِ `/:id`، و `POST /api/sales/full`) — راهِ دومِ نوشتن روی
 *      دفترِ دکان که هر نگهبانِ تازه باید دو بار رویش وصله می‌شد؛ هیچ برنامه‌ای صدایش نمی‌زد.
 *   ۲) نام‌ها و درهای مردهٔ دکان، «من»، پورتال و ورود (فهرستِ `GONE`).
 *   ۳) دورِ دوم (e-rows-44u): نام‌های دومِ حسابِ مشتری زیرِ `/api/market/account/**` (همان `/api/store/:slug/account/*`)،
 *      نسخه و «پردازشِ» عکسِ دکان، جزئیاتِ یک تسویهٔ پیک، فهرستِ جریمه‌های دکان، نامِ دومِ شرایط، ویترین با شناسهٔ
 *      عمومی و مسیرِ پیشنهادیِ سرورِ پیک (فهرستِ `GONE_2` و `GONE_COURIER`). پرچم‌های همان
 *      درها روشن‌اند (`FLAGS_ON`) تا ۴۰۴ از دروازهٔ پرچم نیاید.
 *
 * با توکنِ معتبرِ صاحب هر کدام ۴۰۴ (نه نوشتن) — با `/api` و `/api/v1`؛ بی‌توکن همان ۴۰۱ ِ درِ ناشناخته (دروازهٔ پایانیِ
 * `/api` که با `routes/data.js` نرفت: درِ پرچمِ خاموش «همان درِ ناشناخته» است، `account-cart-v1.json`)؛ هیچ ردیفی از
 * دفترِ دکان عوض نمی‌شود؛ و فهرستِ زندهٔ مسیرها هیچ‌کدام را ندارد. درهایی که برنامهٔ کهنهٔ در دست (APK ِ پوستهٔ
 * WebView ِ `android/` و نسخهٔ بومیِ بایگانی‌شده) هنوز صدا می‌زند عمداً ماندند و همین‌جا زنده سنجیده می‌شوند (`KEPT`)،
 * تا آزمون بی‌اثر نباشد (۴۰۴ از سوار نشدنِ کلِ `/api` نیامده باشد).
 */
const inventory = require('./route-inventory');

inventory.tapMounts();   //  پیش از ساختنِ برنامه

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { one, query, now } = require('../src/db');
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
  //  «فقط ایمیل» (۱۴۰۵/۰۸/۲۳): کدِ پیامکیِ افزودنِ شماره (AUTH-08 ِ پیشین) ⇐ به‌جایش `POST /api/me/email/code`
  'POST /api/me/phone/code',
];
/** دورِ دوم — هر کدام با «⇐ به‌جایش» در B15 ِ `docs/SECURITY-AUDIT-fa.md` */
const GONE_2 = [
  ...['', '/logout', '/logout-all', '/phone'].map((p) => `POST /api/market/account${p}`),
  'GET /api/market/account', 'PUT /api/market/account', 'DELETE /api/market/account', 'GET /api/market/account/orders',
  'GET /api/market/account/addresses', 'POST /api/market/account/addresses', 'PUT /api/market/account/addresses/:id',
  'DELETE /api/market/account/addresses/:id',
  'POST /api/shop/images/:id/versions', 'POST /api/shop/images/:id/process',
  'GET /api/shop/delivery/settlements/:id', 'GET /api/shop/trust/sanctions',
  'GET /api/terms', 'GET /api/store',
];
GONE.push(...GONE_2);
/** درِ پیک فقط با توکنِ پیک دیده می‌شود ⇒ ۴۰۴ ِ آن در `courier-route.test.js`؛ این‌جا فقط نبودن در فهرستِ زنده */
const GONE_COURIER = ['GET /api/courier/route'];
/** پرچمِ همان درها روشن ⇒ ۴۰۴ ِ پس از برداشتن از نبودنِ در است، نه از دروازهٔ پرچم */
const FLAGS_ON = ['market.directory', 'market.account', 'market.public_id', 'shop.image_bank', 'shop.orders', 'shop.delivery', 'market.trust'];
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
  for (const key of FLAGS_ON) {
    const scope = key.startsWith('market.') ? 'all' : shopId;
    await query('DELETE FROM live_config WHERE scope=$1 AND key=$2', [scope, key]);
    await query('INSERT INTO live_config (scope, key, value, updated_at) VALUES ($1,$2,$3,$4)', [scope, key, 'true', now()]);
  }
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

test('بی‌توکن هر درِ برداشته همان پاسخِ درِ ناشناخته است (دروازهٔ پایانیِ `/api`: ۴۰۱، بی وجود/نبودِ مسیر)', async () => {
  //  درِ ناشناخته‌ای که هرگز نبود ⇒ همان ۴۰۱ ِ دروازه؛ برنامه‌ها روی همین سوارند (`account-cart-v1.json`)
  const unknown = await h.post('/api/no-such-door', { a: 1 });
  assert.equal(unknown.status, 401, JSON.stringify(unknown.body));
  const bad = [];
  for (const key of GONE) {
    resetAll();
    const r = await h.api(key.split(' ')[0], url(key), { body: bodyOf(key) });
    if (r.status !== 401) bad.push(`${key} بی‌توکن ⇒ ${r.status} ${JSON.stringify(r.body).slice(0, 120)}`);
    else if (DATA.includes(key) && JSON.stringify(r.body) !== JSON.stringify(unknown.body)) bad.push(`${key} بی‌توکن پاسخِ دیگری از درِ ناشناخته داد: ${JSON.stringify(r.body)}`);
  }
  assert.deepEqual(bad, [], bad.join('\n'));
});

test('فهرستِ زندهٔ مسیرها هیچ درِ برداشته را ندارد؛ درهای ماندهٔ برنامهٔ کهنه و جانشین‌ها زنده‌اند', async () => {
  const keys = new Set(inventory.live(h.server().listeners('request')[0]).map((r) => r.key));
  assert.ok(keys.size > 600, `فقط ${keys.size} در — فهرستِ زنده خوانده نشد؟`);
  const back = [...GONE, ...GONE_COURIER].filter((k) => keys.has(k));
  assert.deepEqual(back, [], `درِ برداشته برگشت:\n${back.join('\n')}`);
  //  نام‌های کهنه‌ای که APK ِ در دست صدا می‌زند (هم‌دستگیرهٔ درِ زنده) و درِ قاعده‌دار
  for (const k of ['GET /api/billing/status', 'GET /api/billing/plans', 'POST /api/billing/request', 'POST /api/shop/invite',
    'GET /api/shop', 'POST /api/shop', 'PUT /api/shop', 'POST /api/shop/staff/join', 'POST /api/shop/active', 'GET /api/shop/audit',
    'POST /api/auth/staff', 'POST /api/sync', 'GET /api/sync', 'GET /api/portal/settings', 'POST /api/portal/redeem', 'POST /api/vip/redeem',
    'POST /api/errors',
    //  جانشین‌های دورِ دوم (B15 ِ `SECURITY-AUDIT-fa.md`)
    'POST /api/store/:slug/account', 'GET /api/store/:slug/account', 'GET /api/store/:slug/account/addresses', 'POST /api/shop/images',
    'GET /api/shop/delivery/settlements', 'GET /api/shop/trust/cases/:id', 'GET /api/auth/terms', 'GET /api/store/:slug',
    'PUT /api/shop/till-pin/members/:id', 'GET /api/courier/me']) {
    assert.ok(keys.has(k), `${k} باید بماند`);
  }
  for (const [key, want] of KEPT) {
    resetAll();
    const r = await h.api(key.split(' ')[0], url(key), { token: owner.accessToken });
    assert.equal(r.status, want, `${key} ⇒ ${r.status} ${JSON.stringify(r.body).slice(0, 120)}`);
  }
});
