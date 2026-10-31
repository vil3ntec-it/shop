'use strict';
/**
 * فهرستِ کاملِ مسیرهای خصوصیِ دکان (`/api/shop/*`) — بندِ ۲۹.۲ قاعدهٔ ۲ و ۱۱،
 * ۲۹.۴ و آزمون‌های TEN-01 / AUTH-09.
 *
 * مسیرها از **خودِ سورس** خوانده می‌شوند، نه از فهرستِ دستی: سوار شدن‌ها از
 * `src/app.js` (‎`api.use('/shop…', require('./routes/…'))`‎) و روترهای تودرتو از
 * `router.use(require('./…'))`. پس مسیرِ تازه‌ای که فردا اضافه شود — یا فایلِ
 * تازه‌ای که زیرِ `/shop` سوار شود — بی هیچ کارِ دستی همین‌جا سنجیده می‌شود.
 *
 * برای هر مسیر:
 *   ۱) بی توکن ⇒ ۴۰۱
 *   ۲) با توکنِ بخشِ پمپ (همان آدم) ⇒ ۴۰۱ — توکنِ بخشِ دیگر این‌جا وجود ندارد
 *   ۳) با توکنِ صاحبِ دکانِ «ب» و شناسه‌های واقعیِ دکانِ «الف» در نشانی و بدنه ⇒
 *      هیچ پاسخی شناسه یا دادهٔ «الف» را برنمی‌گرداند و هیچ خطای ۵۰۰ی نیست
 *   ۴) در پایان، همهٔ ردیف‌های «الف» در **هر** جدولی که ستونِ `shop_id` دارد
 *      بایت‌به‌بایت همان پیش از آزمون‌اند — توکنِ «ب» چیزی از «الف» عوض نکرد
 */
//  پرداختِ حساب‌پی (PAY-21) با ارائه‌دهندهٔ ساختگی — قصدِ واقعیِ «الف» برای `/shop/payments/intents/:id`
process.env.PAYMENTS_PROVIDER = 'fake';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const nodePath = require('node:path');
const crypto = require('node:crypto');
const h = require('./helpers');
const F = require('./image-fixtures');
const { many, one, query, newId, now } = require('../src/db');
const { can } = require('../src/lib/permissions');
const chat = require('../src/lib/shop-chat');
const orders = require('../src/lib/orders');
const trust = require('../src/lib/market-trust');

test.before(async () => { await h.start(); });
test.after(async () => { await h.stop(); });

const SRC = nodePath.join(__dirname, '..', 'src');
const ROUTES = nodePath.join(SRC, 'routes');

/** `/shop…` ← فایل، از `app.js`؛ روترِ تودرتو همان پیشوندِ پدر را می‌گیرد */
function shopMounts() {
  const app = fs.readFileSync(nodePath.join(SRC, 'app.js'), 'utf8');
  const out = [];
  for (const m of app.matchAll(/api\.use\(\s*'(\/shop(?:\/[^']*)?)'\s*,\s*require\('\.\/routes\/([\w-]+)'\)\s*\)/g)) {
    out.push({ prefix: m[1], file: `${m[2]}.js` });
  }
  for (let i = 0; i < out.length; i++) {
    const src = fs.readFileSync(nodePath.join(ROUTES, out[i].file), 'utf8');
    for (const n of src.matchAll(/router\.use\(\s*require\('\.\/([\w-]+)'\)\s*\)/g)) {
      if (!out.some((o) => o.file === `${n[1]}.js` && o.prefix === out[i].prefix)) out.push({ prefix: out[i].prefix, file: `${n[1]}.js` });
    }
  }
  return out;
}

/**
 * اجازه‌هایی که یک مسیر **در خودِ سورس** می‌خواهد: `requirePermission('x')` روی
 * همان خط و روی هر `router.use(...)`ِ پیش از آن، و ثابت‌هایی مثلِ
 * `const reply = requirePermission('chat.reply')` که به نام روی مسیر می‌نشینند.
 */
function guardsOf(src, at) {
  const named = new Map([...src.matchAll(/const\s+(\w+)\s*=\s*requirePermission\(\s*'([^']+)'\s*\)/g)].map((m) => [m[1], m[2]]));
  const permsIn = (s) => [
    ...[...s.matchAll(/requirePermission\(\s*'([^']+)'\s*\)/g)].map((m) => m[1]),
    ...[...s.matchAll(/\b(\w+)\b/g)].map((m) => named.get(m[1])).filter(Boolean),
  ];
  const head = src.slice(at, src.indexOf('(req', at) > 0 ? src.indexOf('(req', at) : at + 200);
  const out = new Set(permsIn(head));
  for (const u of src.slice(0, at).matchAll(/router\.use\(([^;]*?)\);/g)) for (const p of permsIn(u[1])) out.add(p);
  return [...out];
}

function shopRoutes() {
  const re = /router\.(get|post|put|patch|delete)\(\s*(\[[^\]]*\]|[`'"][^`'"]+[`'"])\s*,/g;
  const out = [];
  const seen = new Set();
  for (const { prefix, file } of shopMounts()) {
    const src = fs.readFileSync(nodePath.join(ROUTES, file), 'utf8');
    for (const m of src.matchAll(re)) {
      const paths = [...m[2].matchAll(/[`'"]([^`'"]+)[`'"]/g)].map((x) => x[1]);
      for (const p of paths) {
        const path = prefix + (p === '/' ? '' : p);
        const key = `${m[1].toUpperCase()} ${path}`;
        if (seen.has(key)) continue;
        seen.add(key);
        out.push({ method: m[1].toUpperCase(), path, file, perms: guardsOf(src, m.index) });
      }
    }
  }
  return out;
}

async function owner(label) {
  const u = await h.newUser(label);
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, shopId: made.body.shop.id };
}

/** اثرِ کاملِ یک دکان: همهٔ ردیف‌هایش در هر جدولی که `shop_id` دارد */
async function footprint(shopId) {
  const tables = await many(
    `SELECT DISTINCT table_name FROM information_schema.columns
      WHERE table_schema='public' AND column_name='shop_id' ORDER BY table_name`
  );
  const out = {};
  for (const { table_name: t } of tables) {
    const r = await one(
      `SELECT COUNT(*)::int AS n, COALESCE(md5(string_agg(x::text, '|' ORDER BY x::text)), '') AS h
         FROM "${t}" x WHERE x.shop_id = $1`, [shopId]
    );
    out[t] = `${r.n}:${r.h}`;
  }
  return out;
}

test('هر مسیرِ خصوصیِ /api/shop: بی توکن و با توکنِ پمپ ۴۰۱؛ توکنِ دکانِ دیگر هیچ دادهٔ این دکان را نمی‌بیند و عوض نمی‌کند', async () => {
  const routes = shopRoutes();
  const files = new Set(routes.map((r) => r.file));
  for (const f of ['shop.js', 'shop-staff.js', 'orders.js', 'storefront-admin.js', 'delivery.js', 'shop-chat.js', 'sync.js']) {
    assert.ok(files.has(f), `routes/${f} در فهرستِ سوارشده‌ها نیست`);
  }
  assert.ok(routes.length > 60, `فقط ${routes.length} مسیر پیدا شد`);

  //  دکانِ «الف» با دادهٔ واقعی: تنظیمات، کارمند، کدِ کارمند
  const a = await owner('الف');
  const MARK = 'رازِ-دکانِ-الف-۷۳۱';
  assert.equal((await h.put('/api/shop/settings', { settings: { mark: MARK } }, { token: a.accessToken })).status, 200);
  const code = (await h.get('/api/shop/staff-code', { token: a.accessToken })).body.code;
  const staff = await h.newUser('شاگردِ الف');
  assert.ok([200, 201].includes((await h.post('/api/shop/join', { code }, { token: staff.accessToken })).status));
  const members = (await h.get('/api/shop/members', { token: a.accessToken })).body.members;
  const memberId = members.find((m) => m.role === 'staff').id;
  const inv = await h.post('/api/shop/invite', { role: 'staff' }, { token: a.accessToken });
  assert.equal(inv.status, 201, JSON.stringify(inv.body));
  const codeId = (await h.get('/api/shop/staff-codes', { token: a.accessToken })).body.codes[0]?.id;
  assert.ok(codeId, 'کدِ واقعیِ دکانِ «الف» برای نشانی');

  const b = await owner('ب');
  const pump = await h.signIn(b, 'pump');
  const secrets = [a.shopId, MARK, memberId, codeId, inv.body.code].filter(Boolean);

  const concrete = (p) => p
    .replace(/\/members\/:id\b/, `/members/${memberId}`)
    .replace(/\/staff-codes\/:id\b/, `/staff-codes/${codeId}`)
    .replace(/:[A-Za-z_]+/g, 'x1');

  const before = await footprint(a.shopId);
  const failures = [];
  for (const r of routes) {
    const key = `${r.method} ${r.path}`;
    const url = `/api${concrete(r.path)}`;
    const body = r.method === 'GET' || r.method === 'DELETE' ? null : { shopId: a.shopId, shop_id: a.shopId, id: memberId };

    const anon = await h.api(r.method, url, { body });
    if (anon.status !== 401) failures.push(`${key} بی‌توکن ⇒ ${anon.status}`);

    const other = await h.api(r.method, url, { body, token: pump.accessToken });
    if (other.status !== 401) failures.push(`${key} با توکنِ پمپ ⇒ ${other.status}`);

    const cross = await h.api(r.method, url, {
      body, token: b.accessToken, headers: { 'X-Shop-Id': a.shopId },
    });
    if (cross.status >= 500) failures.push(`${key} با توکنِ دکانِ «ب» ⇒ ${cross.status}`);
    const text = JSON.stringify(cross.body ?? '');
    for (const s of secrets) if (text.includes(s)) failures.push(`${key} با توکنِ دکانِ «ب» دادهٔ «الف» را برگرداند (${s.slice(0, 8)}…)`);
  }
  assert.deepEqual(failures, [], failures.join('\n'));

  const after = await footprint(a.shopId);
  assert.deepEqual(after, before, 'توکنِ دکانِ «ب» هیچ ردیفی از دکانِ «الف» را عوض نکرد');
  //  و خودِ «الف» هنوز به همه‌چیزش می‌رسد — آزمون بی‌اثر نبود
  const mine = await h.get('/api/shop/settings', { token: a.accessToken });
  assert.equal(mine.status, 200);
  assert.ok(JSON.stringify(mine.body).includes(MARK));
});

/* ══════════════════════════════════════════════════════════════════════
   ۲) ردیفِ واقعیِ «الف» برای **هر** مسیرِ شناسه‌دار — همه با APIها/لایه‌های واقعی
   ══════════════════════════════════════════════════════════════════════
   آزمونِ بالا جز اعضا و کدِ کارمند به جای هر `:id` «x1» می‌گذاشت؛ پس دسترسیِ
   دکانِ دیگر به سفارش/پیک/چت/کالا/ویترین/عکسِ **واقعیِ** «الف» هرگز سنجیده
   نمی‌شد. این‌جا برای هر منبع یک ردیفِ واقعیِ «الف» ساخته می‌شود و:
     الف) صاحبِ «الف» به هر GETِ شناسه‌دار می‌رسد (شناسه‌ها واقعی‌اند)
     ب) صاحبِ دکانِ «ب» با همان شناسه‌ها روی **هر** مسیرِ شناسه‌دار ۴۰۳/۴۰۴ می‌گیرد
     ج) شاگردِ «الف» روی هر مسیری که اجازه‌اش (از خودِ سورس) مالِ شاگرد نیست ۴۰۳
     د) توکنِ پیکِ «الف» روی هیچ مسیرِ /api/shop رد نمی‌شود جز با ۴۰۱/۴۰۳
     هـ) پیک فقط مأموریتِ خودش را می‌بیند و فقط روی آن کار می‌کند
   و ردیف‌های «الف» پس از همه بایت‌به‌بایت همان‌اند.
   مسیرِ شناسه‌دارِ تازه‌ای که ردیفِ واقعی برایش این‌جا ساخته نشود، آزمون را سرخ می‌کند. */

const FLAGS = ['shop.orders', 'shop.orders.refunds', 'shop.delivery', 'shop.chat', 'shop.catalog',
  'shop.storefront', 'shop.image_bank', 'shop.order_notify', 'market.trust', 'pay.hesabpay'];
//  جمع‌آوری و بسته‌بندی (PCK) — درهای `/shop/orders/pick/…` و `/shop/orders/:id/pick…`
FLAGS.push('shop.orders.picking');

/**
 * شناسه‌هایی که ردیفِ سراسری نیستند، کلیدِ فضای خودِ هر دکان‌اند: نامِ نقش، و
 * شناسهٔ کالا در «کدام عکس برای این کالا» (کالا شناسهٔ ساختهٔ دستگاه است؛ برای
 * «ب» همان «کالای بی‌عکس» است)، و شناسهٔ قاعدهٔ تخفیف (ساختهٔ برنامه؛ upsert با
 * کلیدِ (shop_id, id) — «ب» با شناسهٔ «الف» فقط ردیفِ خودش را می‌سازد/پاک می‌کند).
 * این‌ها برای «ب» ۲۰۰ِ **فضای خودِ «ب»** می‌دهند — نشت نبودنش را همان سنجهٔ «هیچ
 * شناسهٔ دیگرِ الف برنگشت» و ردپای بایت‌به‌بایت می‌گیرد (و برای تخفیف، سنجهٔ صریحِ پایین).
 */
const PER_SHOP_KEYS = new Set([
  'PUT /shop/permissions/roles/:role', 'GET /shop/images/products/:productId',
  'PUT /shop/promos/rules/:id', 'DELETE /shop/promos/rules/:id',
]);
/** شناسهٔ کالا و قاعدهٔ تخفیف کلیدِ فضای دکان‌اند، نه راز — برای «ب» بازتابِ همان نشانی‌اند */
const NOT_SECRET = new Set(['product', 'promoRule']);

let k = 0;
const fresh = (p) => `${p}-${++k}-${Date.now()}`;
const jpegB64 = () => Buffer.concat([Buffer.from([0xff, 0xd8, 0xff, 0xe0]), Buffer.alloc(300, 7)]).toString('base64');

async function adminToken() {
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'inv-admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  const t = (await h.post('/api/admin/login', { username: 'inv-admin', password: 'Admin!12345' })).body.token;
  assert.ok(t, 'ورودِ مدیر');
  return t;
}

/** پیک با «ورود با کدِ شاگرد» — همان راهِ برنامهٔ گوشی (`courier-scope.test.js`). */
async function courierOf(o, name) {
  const inv = await h.post('/api/shop/invite', { role: 'courier' }, { token: o.accessToken });
  assert.equal(inv.status, 201, JSON.stringify(inv.body));
  const uid = fresh('courier-phone');
  const r = await h.post('/api/auth/staff-login', { code: inv.body.code, name, device: { uid, name: 'گوشیِ پیک', platform: 'android' } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const token = r.body.accessToken;
  const id = (await h.get('/api/courier/me', { token })).body.courier.id;
  return { token, uid, id };
}

let evN = 0;
const ev = (assignmentId, type, extra = {}) => ({
  eventId: fresh('ev'), assignmentId, type, deviceTime: Date.now(), deviceSeq: ++evN, ...extra,
});
async function courierSend(c, events) {
  const r = await h.post('/api/courier/events', { deviceId: c.uid, events }, { token: c.token });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.results;
}

/** همهٔ ردیف‌های واقعیِ «الف»؛ هر کدام از درِ خودش. */
async function seedShop(a) {
  const ids = {};
  //  کالا (+ وظیفهٔ «موجودیِ کم») — از همگام‌سازیِ برنامه، همان راهِ دستگاه
  const device = `dev-inv-${a.shopId}`.slice(0, 60);
  const push = async (ops) => {
    const r = await h.api('POST', '/api/sync/v1/push', {
      token: a.accessToken, body: { device_id: device, schema_version: 2, ops },
      headers: { 'X-App': 'shop', 'X-Device': device },
    });
    assert.equal(r.status, 200, JSON.stringify(r.body));
    for (const x of r.body.results) assert.ok(['applied', 'duplicate'].includes(x.status), JSON.stringify(x));
  };
  let opN = 0;
  const op = (type, table, rowId, fields) => ({
    op_id: `01JINV${String(Date.now()).slice(-8)}${String(++opN).padStart(8, '0')}`, ts: Date.now(), table, row_id: rowId, type, fields,
  });
  ids.product = 'inv-prod-a1';
  await push([
    op('insert', 'products', ids.product, { name: 'کالای الف', unit: 'دانه', salePrice: 50, purchasePrice: 40, active: true, barcodes: [], minStock: 3, createdAt: Date.now() }),
    op('insert', 'warehouseEntries', 'inv-entry-a1', { productId: ids.product, units: 4, cartons: 0, perCarton: 0, unit: 'دانه', price: 40, createdAt: Date.now() }),
  ]);
  assert.equal((await h.get(`/api/shop/catalog/products/${ids.product}`, { token: a.accessToken })).status, 200);
  await push([
    op('insert', 'sales', 'inv-sale-a1', { status: 'completed', total: 0, createdAt: Date.now() }),
    op('insert', 'saleItems', 'inv-item-a1', { saleId: 'inv-sale-a1', productId: ids.product, quantity: 1, returnedQty: 0, unitPrice: 50, createdAt: Date.now() }),
  ]);
  assert.equal((await h.get(`/api/shop/catalog/products/${ids.product}`, { token: a.accessToken })).status, 200);
  const tasks = (await h.get('/api/shop/catalog/tasks?status=open', { token: a.accessToken })).body.tasks;
  ids.task = tasks.find((t) => t.kind === 'low_stock' && t.productId === ids.product)?.id;
  assert.ok(ids.task, 'وظیفهٔ «موجودیِ کم»ِ الف');

  //  ویترین
  const sf = await h.put(`/api/shop/storefront/products/${ids.product}`, { published: true, onlinePriceMinor: 6000 }, { token: a.accessToken });
  assert.equal(sf.status, 200, JSON.stringify(sf.body));

  //  عکسِ اختصاصیِ دکان
  const own = await h.raw('POST', `/api/shop/images/?product_id=${ids.product}`, F.jpeg(800, 600, { seed: 731 }), { token: a.accessToken });
  assert.equal(own.status, 201, JSON.stringify(own.body));
  ids.image = own.body.id;

  //  سفارشِ مشتری (اعلانِ سفارش) — از لایهٔ سفارش، همان راهِ ویترین
  await orders.create(a.shopId, {
    key: fresh('notify'), actor: { type: 'customer', id: '' },
    body: { source: 'phone', fulfillment: 'pickup', paymentMethod: 'cod', customer: { name: 'مشتریِ الف', phone: '0791234567' },
      lines: [{ name: 'برنج', qtyMilli: 1000, unitPriceMinor: 5000 }] },
  });
  ids.notify = (await one('SELECT id FROM order_notify_outbox WHERE shop_id=$1 ORDER BY created_at LIMIT 1', [a.shopId]))?.id;
  assert.ok(ids.notify, 'ردیفِ اعلانِ سفارشِ الف');

  //  سفارشِ تلفنیِ تحویلی ⇒ مأموریتِ پیک ⇒ عکس ⇒ تحویل ⇒ تسویه
  const TOTAL = 1_240_000;
  const made = await h.post('/api/shop/orders', {
    source: 'phone', fulfillment: 'delivery', paymentMethod: 'cod',
    customer: { name: 'احمد', phone: '0791234567' },
    address: { landmark: 'نزدیکِ مسجد', text: 'کوچهٔ سوم', lat: 34.5, lng: 69.1 },
    delivery: { photoConsent: true, callBeforeArrival: true },
    lines: [{ name: 'برنج', qtyMilli: 1000, unitPriceMinor: 1_200_000 }], deliveryFeeMinor: 40_000,
  }, { token: a.accessToken, headers: { 'Idempotency-Key': fresh('order') } });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  ids.order = made.body.order.id;
  for (const to of ['accepted', 'picking', 'packed']) {
    const t = await h.post(`/api/shop/orders/${ids.order}/transition`, { to }, { token: a.accessToken });
    assert.equal(t.status, 200, `${to}: ${JSON.stringify(t.body)}`);
  }
  //  جمع‌آوری (PCK): سفارشِ دیگرِ «الف» ⇒ برگه ⇒ «پیدا نکردم» ⇒ وظیفهٔ بررسی
  const po = await h.post('/api/shop/orders', {
    source: 'phone', fulfillment: 'pickup', paymentMethod: 'cod', customer: { name: 'رحیم' },
    lines: [{ productId: ids.product, name: 'کالای الف', qtyMilli: 1000, unitPriceMinor: 5000 }],
  }, { token: a.accessToken, headers: { 'Idempotency-Key': fresh('pick-order') } });
  assert.equal(po.status, 201, JSON.stringify(po.body));
  assert.equal((await h.post(`/api/shop/orders/${po.body.order.id}/transition`, { to: 'accepted' }, { token: a.accessToken })).status, 200);
  assert.equal((await h.post(`/api/shop/orders/${po.body.order.id}/pick/start`, {}, { token: a.accessToken })).status, 201);
  const nf = await h.post(`/api/shop/orders/${po.body.order.id}/pick/events`,
    { events: [{ eventId: fresh('pick-ev'), lineNo: 1, type: 'not_found' }] }, { token: a.accessToken });
  assert.equal(nf.body.results?.[0]?.status, 'accepted', JSON.stringify(nf.body));
  ids.pickIssue = (await h.get('/api/shop/orders/pick/issues', { token: a.accessToken })).body.issues[0]?.id;
  assert.ok(ids.pickIssue, 'وظیفهٔ «پیدا نکردم» ِ الف');
  //  جمع‌آوریِ گروهی (PCK-12): دو سفارشِ قبول‌شدهٔ دیگرِ «الف» ⇒ یک دسته
  const bo = [];
  for (let i = 0; i < 2; i++) {
    const r = await h.post('/api/shop/orders', {
      source: 'phone', fulfillment: 'pickup', paymentMethod: 'cod', customer: { name: `دسته ${i}` },
      lines: [{ productId: ids.product, name: 'کالای الف', qtyMilli: 1000, unitPriceMinor: 5000 }],
    }, { token: a.accessToken, headers: { 'Idempotency-Key': fresh('batch-order') } });
    assert.equal(r.status, 201, JSON.stringify(r.body));
    assert.equal((await h.post(`/api/shop/orders/${r.body.order.id}/transition`, { to: 'accepted' }, { token: a.accessToken })).status, 200);
    bo.push(r.body.order.id);
  }
  const bt = await h.post('/api/shop/orders/pick/batches', { orderIds: bo }, { token: a.accessToken, headers: { 'Idempotency-Key': fresh('batch') } });
  assert.equal(bt.status, 201, JSON.stringify(bt.body));
  ids.pickBatch = bt.body.batch.batchId;

  const courier = await courierOf(a, 'پیکِ الف');
  ids.courier = courier.id;
  const asg = await h.post(`/api/shop/delivery/orders/${ids.order}/assign`,
    { mode: 'shop_courier', courierId: courier.id, overrideCashCap: true }, { token: a.accessToken });
  assert.equal(asg.status, 201, JSON.stringify(asg.body));
  ids.assignment = asg.body.assignment.id;
  for (const x of await courierSend(courier, ['assigned', 'picked_up', 'moving', 'arrived'].map((t) => ev(ids.assignment, t)))) {
    assert.equal(x.status, 'applied', JSON.stringify(x));
  }
  const ph = await h.post(`/api/courier/missions/${ids.assignment}/photos`, { eventId: fresh('photo'), imageBase64: jpegB64(), parcelOnly: true }, { token: courier.token });
  assert.equal(ph.status, 201, JSON.stringify(ph.body));
  ids.photo = ph.body.photoId;
  const [del] = await courierSend(courier, [ev(ids.assignment, 'delivered', { cashCollectedMinor: TOTAL, code: asg.body.code })]);
  assert.equal(del.status, 'applied', JSON.stringify(del));
  const st = await h.post(`/api/shop/delivery/couriers/${courier.id}/settlements`,
    { assignmentIds: [ids.assignment], handedOverMinor: TOTAL, receivedMinor: TOTAL },
    { token: a.accessToken, headers: { 'Idempotency-Key': fresh('settle') } });
  assert.equal(st.status, 201, JSON.stringify(st.body));
  ids.settlement = st.body.settlement.id;

  //  چت: گفت‌وگوی مشتری، پیامش، رسانهٔ دکان
  const me = { side: 'customer', shopId: a.shopId, phone: '+93791112233' };
  ids.conv = (await chat.open(me, { name: 'احمد' })).conversation.id;
  ids.message = (await chat.send(me, ids.conv, { clientId: `client-${fresh('m')}-0000`, body: 'سلام، برنج دارید؟' })).message.id;
  const media = await h.raw('POST', `/api/shop/chat/conversations/${ids.conv}/media`, F.jpeg(320, 240, { seed: 9 }),
    { token: a.accessToken, headers: { 'Content-Type': 'image/jpeg' } });
  assert.equal(media.status, 201, JSON.stringify(media.body));
  ids.media = media.body.mediaId;
  assert.ok(ids.media, JSON.stringify(media.body));

  //  شاگرد (عضو)، کدِ کارمند، دستگاه، کلیدِ تأییدکننده
  const code = (await h.get('/api/shop/staff-code', { token: a.accessToken })).body.code;
  const staff = await h.newUser('شاگردِ الفِ دوم');
  assert.ok([200, 201].includes((await h.post('/api/shop/join', { code }, { token: staff.accessToken })).status));
  ids.member = (await h.get('/api/shop/members', { token: a.accessToken })).body.members.find((m) => m.userId === staff.user.id || m.user_id === staff.user.id)?.id;
  assert.ok(ids.member, 'عضوِ شاگردِ الف');
  assert.equal((await h.post('/api/shop/invite', { role: 'staff' }, { token: a.accessToken })).status, 201);
  ids.code = (await h.get('/api/shop/staff-codes', { token: a.accessToken })).body.codes[0]?.id;
  assert.ok(ids.code, 'کدِ کارمندِ الف');
  const devs = await h.get('/api/shop/devices', { token: a.accessToken });
  assert.equal(devs.status, 200, JSON.stringify(devs.body));
  ids.device = (devs.body.devices || devs.body.items || [])[0]?.id;
  assert.ok(ids.device, `دستگاهِ الف: ${JSON.stringify(devs.body)}`);
  const { publicKey } = crypto.generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
  const key = await h.post('/api/shop/approver-keys',
    { public_key: publicKey.export({ format: 'der', type: 'spki' }).toString('base64'), label: 'صندوقِ الف', device_id: 'pos-1' },
    { token: a.accessToken });
  assert.ok([200, 201].includes(key.status), JSON.stringify(key.body));
  ids.approverKey = key.body.key.id;

  //  قاعدهٔ تخفیف — شناسه را برنامه می‌سازد؛ کلیدِ فضای خودِ دکان
  ids.promoRule = 'inv-promo-a1';
  const promo = await h.put(`/api/shop/promos/rules/${ids.promoRule}`,
    { rule: { name: 'تخفیفِ الف', kind: 'percent', bp: 1000 } }, { token: a.accessToken });
  assert.equal(promo.status, 200, JSON.stringify(promo.body));

  //  شکایتِ بازارچه از «الف» + پاسخِ دکان — از لایهٔ اعتماد، همان راهِ مشتریِ ویترین
  const filed = await trust.file({ shop_id: a.shopId }, { phone: '+93791112244', ip: '' },
    { subject: 'shop', category: 'rude', body: 'فروشنده بد برخورد کرد' }, 'complaint');
  assert.equal(filed.duplicate, false, JSON.stringify(filed));
  ids.trustCase = (await one('SELECT id FROM trust_cases WHERE shop_id=$1 ORDER BY created_at LIMIT 1', [a.shopId]))?.id;
  assert.ok(ids.trustCase, 'پروندهٔ شکایتِ الف');
  const resp = await h.post(`/api/shop/trust/cases/${ids.trustCase}/responses`,
    { body: 'پوزش؛ پیگیری شد', clientId: `client-${fresh('t')}-0000` }, { token: a.accessToken });
  assert.equal(resp.status, 201, JSON.stringify(resp.body));

  //  قصدِ پرداختِ حساب‌پی (QR ِ صندوق) — از همان درِ برنامهٔ دکان
  const pi = await h.post('/api/shop/payments/intents',
    { kind: 'pos_sale', refId: 'inv-sale-a1', amount: 5000, currency: 'AFN', idempotencyKey: fresh('pay-intent') },
    { token: a.accessToken });
  assert.equal(pi.status, 201, JSON.stringify(pi.body));
  ids.paymentIntent = pi.body.id;

  return { ids, staff, courier };
}

/** هر `:param` ⇒ شناسهٔ واقعیِ همان منبعِ «الف» */
function concreteFor(ids) {
  const rules = [
    [/^(\/shop\/chat\/conversations\/):id/, ids.conv],
    [/(\/messages\/):mid/, ids.message],
    [/(\/media\/):mediaId/, ids.media],
    [/^(\/shop\/catalog\/products\/):id/, ids.product],
    [/^(\/shop\/catalog\/tasks\/):id/, ids.task],
    [/^(\/shop\/order-notify\/):id/, ids.notify],
    [/(\/pick\/issues\/):issueId/, ids.pickIssue],
    [/(\/pick\/locations\/):productId/, ids.product],
    [/(\/pick\/batches\/):batchId/, ids.pickBatch],
    [/^(\/shop\/orders\/):id/, ids.order],
    [/^(\/shop\/storefront\/products\/):productId/, ids.product],
    [/^(\/shop\/images\/products\/):productId/, ids.product],
    [/^(\/shop\/images\/file\/):id/, ids.image],
    [/^(\/shop\/images\/):id/, ids.image],
    [/(\/):version\b/, '1'],
    [/^(\/shop\/delivery\/couriers\/):id/, ids.courier],
    [/^(\/shop\/delivery\/orders\/):orderId/, ids.order],
    [/^(\/shop\/delivery\/assignments\/):id/, ids.assignment],
    [/^(\/shop\/delivery\/photos\/):photoId/, ids.photo],
    [/^(\/shop\/delivery\/settlements\/):id/, ids.settlement],
    [/^(\/shop\/members\/):id/, ids.member],
    [/^(\/shop\/staff-codes\/):id/, ids.code],
    [/^(\/shop\/permissions\/roles\/):role/, 'staff'],
    [/^(\/shop\/permissions\/members\/):id/, ids.member],
    [/^(\/shop\/devices\/):id/, ids.device],
    [/^(\/shop\/approver-keys\/):id/, ids.approverKey],
    [/^(\/shop\/promos\/rules\/):id/, ids.promoRule],
    [/^(\/shop\/trust\/cases\/):id/, ids.trustCase],
    [/^(\/shop\/payments\/intents\/):id/, ids.paymentIntent],
  ];
  const QUERY = { 'DELETE /shop/chat/conversations/:id/messages/:mid': '?for=all' };
  return (r) => {
    let p = r.path;
    for (const [re, v] of rules) p = p.replace(re, `$1${v}`);
    //  اندازه: نسخهٔ کوچکِ ساختهٔ برنامه را PUT می‌کند؛ فایلِ اصل را GET
    return p.replace(/(\/):size\b/, `$1${r.method === 'PUT' ? 'thumb' : 'original'}`) + (QUERY[`${r.method} ${r.path}`] || '');
  };
}

/** بدنهٔ **معتبر** برای هر مسیر — تا اعتبارسنجی جلوی رسیدن به «این ردیف مالِ کیست» را نگیرد. */
function bodyFor(r, ids) {
  const key = `${r.method} ${r.path}`;
  const B = {
    'POST /shop/orders/:id/transition': { to: 'accepted' },
    'POST /shop/orders/:id/refunds': { amountMinor: 1000, reason: 'other', note: 'آزمون', method: 'cash' },
    'POST /shop/orders/:id/pick/events': { events: [{ eventId: fresh('pev'), lineNo: 1, type: 'not_found' }] },
    'POST /shop/orders/:id/pick/pack': { bags: 1 },
    'POST /shop/orders/pick/issues/:issueId/resolve': { action: 'resolved', note: 'آزمون' },
    'PUT /shop/orders/pick/locations/:productId': { aisle: '1' },
    //  قفسهٔ انتظار و جمع‌آوریِ گروهی (PCK-10/12)
    'POST /shop/orders/pick/shelf/scan': { qr: 'VLNB-1-1-1', slot: 'A1' },
    'POST /shop/orders/pick/batches': { orderIds: [ids.order, ids.order.replace(/.$/, '0')] },
    'PATCH /shop/delivery/couriers/:id': { active: false },
    'POST /shop/delivery/orders/:orderId/assign': { mode: 'shop_courier', courierId: ids.courier, overrideCashCap: true },
    'POST /shop/delivery/assignments/:id/status': { to: 'returned', note: 'آزمون' },
    'POST /shop/delivery/assignments/:id/recall': { reason: 'other', note: 'آزمون' },
    'POST /shop/delivery/couriers/:id/settlements': { assignmentIds: [ids.assignment], handedOverMinor: 1, receivedMinor: 1 },
    'PUT /shop/chat/conversations/:id/status': { status: 'closed' },
    'POST /shop/chat/conversations/:id/messages': { clientId: `client-${fresh('x')}-0000`, body: 'سلام' },
    'PATCH /shop/chat/conversations/:id/messages/:mid': { body: 'ویرایش' },
    'POST /shop/chat/conversations/:id/receipts': { readUpTo: 1, deliveredUpTo: 1 },
    'POST /shop/chat/conversations/:id/reports': { reason: 'spam', note: 'آزمون' },
    'PUT /shop/catalog/products/:id/low': { location: 'shop_floor', lowMilli: 5000 },
    'POST /shop/catalog/tasks/:id/resolve': { action: 'resolved', note: 'آزمون' },
    'POST /shop/order-notify/:id/ack': { kind: 'opened' },
    'PUT /shop/storefront/products/:productId': { published: false, onlinePriceMinor: 1 },
    'POST /shop/images/:id/report': { kind: 'wrong_product', note: 'آزمون' },
    'POST /shop/images/:id/process': { step: 'strip_metadata' },
    'POST /shop/images/:id/propose': { consent: true },
    'PATCH /shop/members/:id': { role: 'manager' },
    'PUT /shop/permissions/members/:id': { perms: {} },
    'PUT /shop/permissions/roles/:role': { perms: {} },
    'PUT /shop/promos/rules/:id': { rule: { name: 'تخفیفِ ب', kind: 'percent', bp: 500 } },
    'POST /shop/trust/cases/:id/responses': { body: 'پاسخِ ب', clientId: `client-${fresh('t')}-0000` },
    'POST /shop/trust/cases/:id/appeal': { body: 'اعتراضِ «ب» به این تصمیم' },
  };
  return B[key] ?? (r.method === 'GET' || r.method === 'DELETE' ? null : {});
}

/** بدنهٔ خام (بایت) برای درهای بارگذاری */
const RAW = new Set([
  'POST /shop/chat/conversations/:id/media',
  'POST /shop/images/:id/versions',
  'PUT /shop/images/:id/v/:version/:size',
]);

function call(r, url, body, token, extraHeaders = {}) {
  const headers = { 'Idempotency-Key': fresh('idem'), ...extraHeaders };
  if (RAW.has(`${r.method} ${r.path}`)) {
    return h.raw(r.method, url, F.jpeg(200, 150, { seed: 5 }), { token, headers: { ...headers, 'Content-Type': 'image/jpeg' } });
  }
  return h.api(r.method, url, { body, token, headers });
}

test('هر مسیرِ شناسه‌دارِ /api/shop با ردیفِ واقعیِ «الف»: دکانِ «ب» ۴۰۳/۴۰۴؛ شاگرد بی اجازه ۴۰۳؛ پیک فقط درِ پیک و فقط مأموریتِ خودش', async () => {
  const admin = await adminToken();
  for (const key of FLAGS) {
    const r = await h.put('/api/admin/pump/live-config', { scope: 'all', key, value: true }, { token: admin });
    assert.equal(r.status, 200, `${key}: ${JSON.stringify(r.body)}`);
  }
  const a = await owner('الفِ شناسه‌دار');
  const { ids, staff, courier } = await seedShop(a);
  const b = await owner('بِ شناسه‌دار');
  const bCourier = await courierOf(b, 'پیکِ ب');
  const other = await courierOf(a, 'پیکِ دومِ الف');

  const routes = shopRoutes();
  const withIds = routes.filter((r) => r.path.includes(':'));
  assert.ok(withIds.length >= 40, `فقط ${withIds.length} مسیرِ شناسه‌دار`);
  const concrete = concreteFor(ids);
  const secrets = Object.entries(ids).filter(([n, x]) => !NOT_SECRET.has(n) && typeof x === 'string' && x.length >= 8).map(([, x]) => x);

  //  هیچ `:param`ی بی ردیفِ واقعی نماند — مسیرِ تازه یعنی ردیفِ تازه همین‌جا
  const unseeded = withIds.filter((r) => concrete(r).includes(':')).map((r) => `${r.method} ${r.path}`);
  assert.deepEqual(unseeded, [], `برای این مسیرها ردیفِ واقعیِ «الف» ساخته نشده:\n${unseeded.join('\n')}`);

  //  الف) شناسه‌ها واقعی‌اند: صاحبِ «الف» به هر GETِ شناسه‌دار می‌رسد
  const own = [];
  for (const r of withIds.filter((x) => x.method === 'GET')) {
    const res = await call(r, `/api${concrete(r)}`, null, a.accessToken);
    if (res.status !== 200) own.push(`${r.method} ${r.path} برای صاحبِ «الف» ⇒ ${res.status} ${JSON.stringify(res.body).slice(0, 160)}`);
  }
  assert.deepEqual(own, [], own.join('\n'));

  const before = await footprint(a.shopId);
  const promoOfA = () => one('SELECT * FROM shop_promotions WHERE shop_id=$1 AND id=$2', [a.shopId, ids.promoRule]);
  const promoBefore = await promoOfA();
  assert.ok(promoBefore && promoBefore.deleted === false, 'قاعدهٔ تخفیفِ «الف» ساخته شده');
  const failures = [];
  for (const r of withIds) {
    const key = `${r.method} ${r.path}`;
    const url = `/api${concrete(r)}`;
    const body = bodyFor(r, ids);

    //  ب) دکانِ «ب» با شناسهٔ واقعیِ «الف»
    const cross = await call(r, url, body, b.accessToken, { 'X-Shop-Id': a.shopId });
    const ok = PER_SHOP_KEYS.has(key) ? cross.status < 500 : [403, 404].includes(cross.status);
    if (!ok) failures.push(`${key} دکانِ «ب» ⇒ ${cross.status} ${JSON.stringify(cross.body).slice(0, 160)}`);
    const text = JSON.stringify(cross.body ?? '');
    for (const s of secrets) if (text.includes(s)) failures.push(`${key} دکانِ «ب» شناسهٔ «الف» را برگرداند (${s.slice(0, 10)}…)`);

    //  د) توکنِ پیکِ «الف» (دامنهٔ courier) و پیکِ «ب»
    for (const [who, c] of [['پیکِ «الف»', courier], ['پیکِ «ب»', bCourier]]) {
      const res = await call(r, url, body, c.token);
      if (![401, 403].includes(res.status)) failures.push(`${key} ${who} ⇒ ${res.status} ${JSON.stringify(res.body).slice(0, 120)}`);
    }

    //  ج) شاگردِ «الف»: اجازه‌ای که سورس می‌خواهد و نقشِ شاگرد ندارد ⇒ ۴۰۳
    const lacks = r.perms.filter((p) => !can('staff', p));
    if (lacks.length) {
      const res = await call(r, url, body, staff.accessToken);
      if (res.status !== 403) failures.push(`${key} شاگرد (بی ${lacks.join('،')}) ⇒ ${res.status} ${JSON.stringify(res.body).slice(0, 120)}`);
    }
  }
  assert.deepEqual(failures, [], failures.join('\n'));

  //  قاعدهٔ تخفیف، صریح: PUT/DELETEِ «ب» با شناسهٔ «الف» فقط فضای خودِ «ب» است —
  //  ردیفِ «الف» پس از هر کدام بایت‌به‌بایت همان می‌ماند
  assert.deepEqual(await promoOfA(), promoBefore, 'درهای بالا قاعدهٔ تخفیفِ «الف» را عوض نکردند');
  const promoUrl = `/api/shop/promos/rules/${ids.promoRule}`;
  const bPut = await h.put(promoUrl, { rule: { name: 'تخفیفِ ب', kind: 'percent', bp: 500 } }, { token: b.accessToken });
  assert.equal(bPut.status, 200, JSON.stringify(bPut.body));
  assert.ok(await one('SELECT 1 AS x FROM shop_promotions WHERE shop_id=$1 AND id=$2 AND deleted=false', [b.shopId, ids.promoRule]),
    'PUTِ «ب» در فضای خودِ «ب» نشست');
  assert.deepEqual(await promoOfA(), promoBefore, 'PUTِ «ب» با شناسهٔ «الف» ردیفِ «الف» را عوض نکرد');
  const bDel = await h.del(promoUrl, { token: b.accessToken });
  assert.equal(bDel.status, 200, JSON.stringify(bDel.body));
  assert.deepEqual(await promoOfA(), promoBefore, 'DELETEِ «ب» با شناسهٔ «الف» ردیفِ «الف» را پاک نکرد');

  //  آزمونِ شاگرد بی‌اثر نبود: دست‌کم درهای اعضا، تسویه، عکس و بازپرداخت را سنجید
  for (const p of ['members.manage', 'delivery.settle', 'delivery.photo', 'orders.refund', 'chat.moderate', 'stock.adjust']) {
    assert.ok(withIds.some((r) => r.perms.includes(p)), `هیچ مسیرِ شناسه‌داری «${p}» نخواست — خواندنِ اجازه‌ها از سورس شکسته؟`);
  }

  const after = await footprint(a.shopId);
  assert.deepEqual(after, before, 'دکانِ «ب»، شاگرد و پیک‌ها هیچ ردیفی از «الف» را عوض نکردند');

  //  هـ) دامنهٔ پیک: پیکِ دیگرِ همان دکان و پیکِ دکانِ دیگر مأموریتِ «الف» را نه می‌بینند و نه رویش کار می‌کنند
  for (const [who, c] of [['پیکِ دومِ «الف»', other], ['پیکِ «ب»', bCourier]]) {
    for (const scope of ['active', 'done']) {
      const ms = await h.get(`/api/courier/missions?scope=${scope}`, { token: c.token });
      assert.equal(ms.status, 200, JSON.stringify(ms.body));
      assert.ok(!JSON.stringify(ms.body).includes(ids.assignment), `${who} مأموریتِ «الف» را در فهرستش دید (${scope})`);
    }
    const [x] = (await h.post('/api/courier/events', { deviceId: c.uid, events: [ev(ids.assignment, 'moving')] }, { token: c.token })).body.results;
    assert.equal(x.status, 'rejected', `${who}: ${JSON.stringify(x)}`);
    assert.equal(x.code, 'assignment_not_found', `${who}: ${JSON.stringify(x)}`);
    const ph = await h.post(`/api/courier/missions/${ids.assignment}/photos`, { eventId: fresh('ph'), imageBase64: jpegB64(), parcelOnly: true }, { token: c.token });
    assert.ok([403, 404].includes(ph.status), `${who} عکس روی مأموریتِ «الف» ⇒ ${ph.status} ${JSON.stringify(ph.body)}`);
  }
  //  خودِ پیکِ «الف» هنوز مأموریتش را می‌بیند — آزمون بی‌اثر نبود
  const mine = await h.get('/api/courier/missions?scope=done', { token: courier.token });
  assert.ok(JSON.stringify(mine.body).includes(ids.assignment), JSON.stringify(mine.body).slice(0, 300));
  assert.deepEqual(await footprint(a.shopId), before, 'پیک‌های دیگر هم چیزی از «الف» را عوض نکردند');
});
