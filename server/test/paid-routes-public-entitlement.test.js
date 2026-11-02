'use strict';
/**
 * SUB-10 (ادامهٔ `paid-routes-entitlement.test.js`، که فقط نوشتنی‌های `/api/shop` را می‌سنجد):
 * آزمونِ مستقیمِ APIهای پولیِ **بیرون از /api/shop** — ویترینِ عمومی (`/api/store`)، درِ پیک
 * (`/api/courier`) و بازارچه (`/api/market`) — با اشتراکِ منقضی، معلق و لغوشده (§۲۹.۸، §۲.۳).
 *
 * مسیرها از خودِ سورس خوانده می‌شوند (`route-inventory.js`). هر مسیر دقیقاً در یک دسته است و
 * مسیرِ تازهٔ بی دسته آزمون را سرخ می‌کند:
 *   STORE_PAID — ویترین و چتِ مشتری (`storefront.js`، `shop-chat-store.js`)؛ کلیدهای پولیِ
 *                `storefront` + `online_orders`. پس از پایانِ اشتراک **همه** ۴۰۴ `store_not_found`
 *                (همان پاسخِ «ویترین نیست»؛ نه ثبتِ سفارش، نه کد، نه حساب، نه چت)؛ با اشتراکِ فعال هرگز.
 *   STORE_FREE — شکایت و گزارشِ مشتری (`trust-store.js`): v3 §۳۸.۶ — از دکانِ بسته یا معلق هم
 *                می‌شود شکایت و پیگیری کرد ⇒ پایانِ اشتراک هرگز آن را نمی‌بندد.
 *   COURIER    — درِ پیک (`courier.js`): مأموریتی که پیش از پایانِ اشتراک واگذار شده پس از آن هم
 *                پذیرفته می‌شود و پیش می‌رود (سفارشِ در جریان و پولِ گرفته‌شده واقعیت‌اند، §۲۸.۶)؛ کارِ
 *                تازه فقط از درِ PAID ِ دکان واگذار می‌شود ⇒ `POST /shop/delivery/orders/:id/assign`
 *                ۴۰۳ `subscription_*`.
 *   MARKET     — بازارچه (`market.js`): دکانِ بی اشتراک در فهرست و جست‌وجو نیست و ویترینش از
 *                بازارچه هم باز نمی‌شود.
 *   STORE_LOOKUP — درِ بی `:slug` ِ ویترین (S3: `GET /api/store?id=<شناسهٔ عمومی>`، پرچمِ `market.public_id`):
 *                با اشتراکِ فعال نشانیِ امروز را می‌دهد؛ پس از پایانِ اشتراک دقیقاً همان پاسخِ شناسهٔ
 *                ناموجود (هیچ نشتی از بودنِ دکان یا وضعیتِ اشتراک)، هرگز ۲۰۰.
 * `/api/receipt` (فاکتورِ صندوقِ پایه) و `/api/visit` (تپشِ بازدید) کلیدِ پولی ندارند و این‌جا نیستند.
 */
process.env.SYNC_V3_ENABLED = '1';
process.env.MARKET_GATE_CACHE_MS = '0';
process.env.MARKET_ELIGIBLE_CACHE_MS = '0';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const nodePath = require('node:path');
const h = require('./helpers');
const inv = require('./route-inventory');
const { query, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');

const DAY = 24 * 60 * 60 * 1000;
const ROUTES = nodePath.join(__dirname, '..', 'src', 'routes');
const CATEGORY = {
  'storefront.js': 'STORE_PAID', 'shop-chat-store.js': 'STORE_PAID', 'trust-store.js': 'STORE_FREE',
  'pick-substitutes-store.js': 'STORE_PAID',   //  PCK-05: پاسخِ مشتری به جایگزین — همان ویترینِ پولی (sf.open)
  'order-amend-store.js': 'STORE_PAID',        //  ORD-14: رضایتِ مشتری به ویرایشِ سفارش — همان ویترینِ پولی
  'storefront-promos-store.js': 'STORE_PAID',  //  MKT-01/09: تخفیفِ ویترین — همان ویترینِ پولی (sf.open)
  'delivery-rating-store.js': 'STORE_PAID',    //  DLV-18: امتیازِ مشتری به پیک — همان ویترینِ پولی (sf.open)
  'storefront-finder-store.js': 'STORE_PAID',  //  WEB-07/10: فیلتر و «خبرم کن» — همان ویترینِ پولی (sf.open)
  'courier.js': 'COURIER', 'market.js': 'MARKET',
};
/** دسته‌ای که به‌جای دستهٔ پرونده برای یک مسیرِ خاص است (کلید = `روش مسیر`). */
const ROUTE_CATEGORY = { 'GET /store': 'STORE_LOOKUP' };
const FLAGS = ['shop.orders', 'shop.storefront', 'shop.chat', 'shop.delivery', 'market.trust', 'market.public_id'];

/** `${base}` ِ روترهای چت و شکایت همان ثابتِ `const base = '…'` ِ پرونده است. */
const baseOf = (file) => (fs.readFileSync(nodePath.join(ROUTES, file), 'utf8').match(/const base = '([^']+)'/) || [])[1] || '';
const publicRoutes = () => ['/store', '/courier', '/market'].flatMap((p) => inv.routes(p))
  .map((r) => (r.path.includes('${base}') ? { ...r, path: r.path.replace('${base}', baseOf(r.file)) } : r))
  .map((r) => ({ ...r, key: `${r.method} ${r.path}` }))
  .map((r) => ({ ...r, cat: ROUTE_CATEGORY[r.key] || CATEGORY[r.file] }));
const code = (r) => r.body?.error?.code || '';
/** STORE_LOOKUP با شناسهٔ عمومیِ همین دکان (یا شناسهٔ ناموجودِ هم‌شکل). */
const lookup = (r, id) => { resetAll(); return h.api(r.method, `/api${r.path}?id=${id}`); };
const UNKNOWN_PUBLIC_ID = '01ZZZZZZZZZZZZZZZZZZZZZZZZ';
const urlOf = (r, slug) => `/api${r.path.replace(':slug', slug).replace(/:[A-Za-z_]+/g, 'x1')}`;
const call = (r, slug) => { resetAll(); return h.api(r.method, urlOf(r, slug), { body: r.method === 'GET' ? null : {}, headers: { 'Idempotency-Key': `k-${newId('k')}` } }); };

let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  admin = (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
  assert.ok(admin);
  await setFlag('all', 'market.directory', true);
});
test.after(async () => { await h.stop(); });

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

const push = (token, ops, device) => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop', 'X-Device': device },
  body: { device_id: device, schema_version: 2, ops },
});

/**
 * دکانِ مشترک (نه آزمایشی) با ویترینِ روشن، کالای منتشرشده با نامِ یکتا، فهرستِ بازارچه، و پیکی
 * که یک سفارشِ بسته‌بندی‌شده پیش از پایانِ اشتراک به او واگذار شده است.
 */
async function paidStore(label) {
  const owner = await h.newUser(`صاحب-${label}`, 'shop');
  const token = owner.accessToken;
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const shopId = made.body.shop.id;
  const g = await h.post('/api/admin/subscriptions', { shopId, plan: 'm1', days: 30 }, { token: admin });
  assert.ok(g.status < 300, JSON.stringify(g.body));
  for (const key of FLAGS) await setFlag(shopId, key, true);
  await query('UPDATE shops SET created_at=$2 WHERE id=$1', [shopId, Date.now() - 400 * DAY]);

  const slug = `p-${uniq()}`;
  const word = `کالا${uniq()}`;
  const put = await h.put('/api/shop/storefront', {
    slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001', deliveryFeeMinor: 5000,
  }, { token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const device = `dev-${uniq()}`;
  const p = await push(token, [{ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: 'rice', type: 'insert',
    fields: { name: word, unit: 'دانه', salePrice: 120, category: 'خوراکی' } }], device);
  assert.equal(p.status, 200, JSON.stringify(p.body));
  assert.equal((await h.put('/api/shop/storefront/products/rice', { published: true }, { token })).status, 200);
  assert.equal((await h.put('/api/shop/market', { listed: true, category: 'grocery' }, { token })).status, 200);

  const inviteRes = await h.post('/api/shop/invite', { role: 'courier' }, { token });
  assert.equal(inviteRes.status, 201, JSON.stringify(inviteRes.body));
  const uid = `courier-${uniq()}`;
  const login = await h.post('/api/auth/staff-login', { code: inviteRes.body.code, name: `پیکِ ${label}`, device: { uid, name: 'گوشیِ پیک', platform: 'android' } });
  assert.equal(login.status, 201, JSON.stringify(login.body));
  const courier = { token: login.body.accessToken, uid };
  const courierId = (await h.get('/api/courier/me', { token: courier.token })).body.courier.id;

  const order = async () => {
    const r = await h.post('/api/shop/orders', {
      source: 'phone', fulfillment: 'delivery', paymentMethod: 'cod',
      customer: { name: 'احمد', phone: '0791234567' },
      address: { landmark: 'نزدیکِ مسجد', text: 'کارتهٔ چهار', lat: 34.5, lng: 69.1 },
      lines: [{ name: word, qtyMilli: 1000, unitPriceMinor: 12_000 }], deliveryFeeMinor: 5000,
    }, { token, headers: { 'Idempotency-Key': `o-${uniq()}` } });
    assert.equal(r.status, 201, JSON.stringify(r.body));
    for (const to of ['accepted', 'picking', 'packed']) {
      const t = await h.post(`/api/shop/orders/${r.body.order.id}/transition`, { to }, { token });
      assert.equal(t.status, 200, `${to}: ${JSON.stringify(t.body)}`);
    }
    return r.body.order.id;
  };
  const assign = (orderId) => h.post(`/api/shop/delivery/orders/${orderId}/assign`,
    { mode: 'shop_courier', courierId, overrideCashCap: true }, { token });
  const first = await assign(await order());
  assert.equal(first.status, 201, JSON.stringify(first.body));
  const spareOrder = await order();
  const publicId = (await query('SELECT public_id FROM storefronts WHERE shop_id = $1', [shopId])).rows[0].public_id;
  return { token, shopId, subId: g.body.subscription.id, slug, publicId, word, courier, assignmentId: first.body.assignment.id, spareOrder, assign };
}

const setStatus = (s, status) => h.post(`/api/admin/subscriptions/${s.subId}/status`, { status }, { token: admin })
  .then((r) => assert.equal(r.status, 200, JSON.stringify(r.body)));
const STATES = {
  'منقضی با گذشتِ زمان': (s) => query('UPDATE subscriptions SET ends_at=$2 WHERE id=$1', [s.subId, Date.now() - 1000]),
  'منقضی (وضعیتِ expired)': (s) => setStatus(s, 'expired'),
  'معلق (suspended)': (s) => setStatus(s, 'suspended'),
  'لغوشده (cancelled)': (s) => setStatus(s, 'cancelled'),
};

const inMarket = async (s) => {
  resetAll();
  const shops = await h.get('/api/market/shops');
  assert.equal(shops.status, 200, JSON.stringify(shops.body));
  const found = await h.get(`/api/market/search?q=${encodeURIComponent(s.word)}`);
  assert.equal(found.status, 200, JSON.stringify(found.body));
  return { listed: shops.body.shops.some((x) => x.slug === s.slug), found: JSON.stringify(found.body).includes(s.slug) };
};
let evSeq = 0;
const ev = (assignmentId, type) => ({ eventId: `ev-${uniq()}`, assignmentId, type, deviceTime: Date.now(), deviceSeq: ++evSeq });

test('SUB-10: هر مسیرِ /api/store، /api/courier و /api/market دقیقاً یک دسته دارد', () => {
  const all = publicRoutes();
  const problems = all.filter((r) => !r.cat).map((r) => `${r.key} (${r.file}): دسته ندارد — پولی است یا نه؟ این‌جا بنویسید`);
  problems.push(...all.filter((r) => r.path.includes('${')).map((r) => `${r.key}: نشانیِ قالبی حل نشد`));
  assert.deepEqual(problems, [], problems.join('\n'));
  const n = (cat) => all.filter((r) => r.cat === cat).length;
  assert.ok(n('STORE_PAID') >= 40, `ویترین و چت: ${n('STORE_PAID')}`);
  assert.ok(n('STORE_FREE') >= 7 && n('COURIER') >= 6 && n('MARKET') >= 7, JSON.stringify({ free: n('STORE_FREE'), courier: n('COURIER'), market: n('MARKET') }));
  assert.ok(all.some((r) => r.key === 'POST /store/:slug/checkout') && all.some((r) => r.key === 'POST /store/:slug/chat/conversations'));
});

test('SUB-10: با اشتراکِ فعال ویترین، چت، شکایت، پیک و بازارچه باز است', async () => {
  const s = await paidStore('فعال');
  const bad = [];
  for (const r of publicRoutes().filter((x) => x.cat === 'STORE_PAID' || x.cat === 'STORE_FREE')) {
    const res = await call(r, s.slug);
    if (code(res) === 'store_not_found' || /^subscription_/.test(code(res)) || res.status >= 500) bad.push(`${r.key} ⇒ ${res.status} ${code(res)}`);
  }
  for (const r of publicRoutes().filter((x) => x.cat === 'STORE_LOOKUP')) {
    const res = await lookup(r, s.publicId);
    if (res.status !== 200 || res.body.slug !== s.slug) bad.push(`STORE_LOOKUP ${r.key} ⇒ ${res.status} ${code(res)}`);
  }
  assert.deepEqual(bad, [], bad.join('\n'));
  assert.equal((await h.get(`/api/store/${s.slug}`)).status, 200);
  assert.deepEqual(await inMarket(s), { listed: true, found: true });
  const missions = await h.get('/api/courier/missions', { token: s.courier.token });
  assert.equal(missions.status, 200, JSON.stringify(missions.body));
  assert.ok(JSON.stringify(missions.body).includes(s.assignmentId));
});

for (const [name, apply] of Object.entries(STATES)) {
  test(`SUB-10: اشتراکِ ${name} — ویترین و چت بسته، از بازارچه بیرون، کارِ تازهٔ پیک نه؛ شکایت و مأموریتِ گرفته‌شده باز`, async () => {
    const s = await paidStore(name);
    await apply(s);
    const bad = [];
    //  ۱) STORE_PAID: همه همان «ویترین نیست»، بی هیچ نشتی از وضعیتِ اشتراک
    for (const r of publicRoutes().filter((x) => x.cat === 'STORE_PAID')) {
      const res = await call(r, s.slug);
      if (res.status !== 404 || code(res) !== 'store_not_found') bad.push(`STORE_PAID ${r.key} ⇒ ${res.status} ${code(res)}`);
    }
    //  ۱ب) STORE_LOOKUP: شناسهٔ عمومیِ دکانِ بسته همان پاسخِ شناسهٔ ناموجود
    for (const r of publicRoutes().filter((x) => x.cat === 'STORE_LOOKUP')) {
      const [res, none] = [await lookup(r, s.publicId), await lookup(r, UNKNOWN_PUBLIC_ID)];
      if (res.status === 200 || res.status !== none.status || code(res) !== code(none)) {
        bad.push(`STORE_LOOKUP ${r.key} ⇒ ${res.status} ${code(res)} (ناموجود: ${none.status} ${code(none)})`);
      }
    }
    //  ۲) STORE_FREE: شکایت از دکانِ بی اشتراک هم ممکن است (§۳۸.۶)
    for (const r of publicRoutes().filter((x) => x.cat === 'STORE_FREE')) {
      const res = await call(r, s.slug);
      if (code(res) === 'store_not_found' || /^subscription_/.test(code(res)) || res.status >= 500) bad.push(`STORE_FREE ${r.key} ⇒ ${res.status} ${code(res)}`);
    }
    resetAll();
    const cfg = await h.get(`/api/store/${s.slug}/trust/config`);
    if (cfg.status !== 200 || cfg.body.enabled !== true) bad.push(`شکایت ⇒ ${cfg.status} ${code(cfg)}`);
    //  ۳) MARKET: نه در فهرست، نه در جست‌وجو
    const m = await inMarket(s);
    if (m.listed || m.found) bad.push(`بازارچه ⇒ ${JSON.stringify(m)}`);
    //  ۴) COURIER: مأموریتِ گرفته‌شده پیش می‌رود (§۲۸.۶)، واگذاریِ تازه از درِ دکان بسته است
    for (const path of ['/api/courier/me', '/api/courier/missions']) {
      const res = await h.get(path, { token: s.courier.token });
      if (res.status !== 200) bad.push(`پیک ${path} ⇒ ${res.status} ${code(res)}`);
    }
    const sent = await h.post('/api/courier/events', { deviceId: s.courier.uid,
      events: [ev(s.assignmentId, 'assigned'), ev(s.assignmentId, 'picked_up')] }, { token: s.courier.token });
    if (sent.status !== 200 || sent.body.results?.some((x) => x.status !== 'applied')) bad.push(`پیک: مأموریتِ واگذارشده ⇒ ${sent.status} ${JSON.stringify(sent.body)}`);
    const fresh = await s.assign(s.spareOrder);
    if (fresh.status !== 403 || !/^subscription_(expired|required)$/.test(code(fresh))) bad.push(`واگذاریِ تازه ⇒ ${fresh.status} ${code(fresh)}`);
    assert.deepEqual(bad, [], bad.join('\n'));
  });
}
