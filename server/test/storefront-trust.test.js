'use strict';
/**
 * ویترین — «گزارشِ مشکل» (ABUSE-01، v3 §۳۸.۶): رابطِ وبِ درهای `/api/store/:slug/trust/…` (`store/trust.js` ِ دیربار).
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) ⛔ پرچمِ خاموشِ `market.trust` ⇒ `GET /api/store/:slug` بی `features.trust` (صفحه trust.js را بار نمی‌کند)؛
 *      روشن ⇒ `trust: true`؛ catalog.js اسکریپت را فقط زیرِ همین کلید می‌خواهد؛ trust.js بیرون از بارِ نخست.
 *   ۲) قاعده‌های فرم (`draft`) همان قاعده‌های سرور‌اند: هر بدنه‌ای که فرم می‌سازد سرور می‌پذیرد، و هر چه فرم رد می‌کند
 *      سرور هم رد می‌کند (یا معنایی ندارد: «دیگر» ِ بی شرح).
 *   ۳) سرتاسری با همان بدنه‌ها: شکایت از دکان، از سفارشِ خودِ خریدار و گزارشِ کالا ⇒ پرونده؛ تکرار ⇒ همان پرونده؛ پیگیری با
 *      توکن؛ دکان پرونده را بی شماره، نام، توکن و شمارهٔ سفارشِ شاکی می‌بیند.
 *   ۴) سقفِ نرخ: سقفِ روزانه ⇒ ۴۲۹ `too_many_reports` و ترجمهٔ همین کد (و هر کدِ دیگرِ درها) در هر سه زبان.
 *   ۵) فهرستِ «گزارش‌های من» روی دستگاه (`keep`): فقط توکنِ درست، بی تکرار، حداکثر ۱۰.
 */
process.env.TRUST_DAILY_PER_PHONE = '4';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const config = require('../src/config');
const T = require('../public/store/trust.js');
const { FIRST_LOAD } = require('./storefront-budget');
const mailOf = (p) => `c${String(p).replace(/\D/g, '').slice(-9)}@test.local`;   //  «فقط ایمیل»: بلیتِ مشتری با ایمیل (شمارهٔ تماس جدا)

const PUB = path.join(__dirname, '..', 'public');
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
  config.trust.dailyPerIp = 10_000;
});
test.after(() => h.stop());
test.beforeEach(() => resetAll());

const flag = async (scope, key, value) => {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
};

async function liveStore(label) {
  const u = await h.newUser(label, 'shop');
  const shop = (await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken })).body.shop;
  await flag(shop.id, 'shop.orders', true);
  await flag(shop.id, 'shop.storefront', true);
  const slug = `tr-${uniq()}`;
  assert.equal((await h.put('/api/shop/storefront', { slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001' },
    { token: u.accessToken })).status, 200);
  const push = await h.api('POST', '/api/sync/v1/push', {
    token: u.accessToken, headers: { 'X-App': 'shop', 'X-Device': 'dev-tr' },
    body: { device_id: 'dev-tr', schema_version: 2, ops: ['rice', 'oil', 'tea', 'salt'].map((id, i) => ({
      op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: id, type: 'insert',
      fields: { name: `کالا ${i}`, unit: 'دانه', salePrice: 100 + i, category: 'خوراکی' },
    })) },
  });
  assert.equal(push.status, 200, JSON.stringify(push.body));
  for (const id of ['rice', 'oil', 'tea', 'salt']) {
    assert.equal((await h.put(`/api/shop/storefront/products/${id}`, { published: true }, { token: u.accessToken })).status, 200);
  }
  return { token: u.accessToken, shopId: shop.id, slug };
}

async function ticketFor(slug, phone) {
  const s = await h.post(`/api/store/${slug}/verify/start`, { email: mailOf(phone) });
  assert.equal(s.status, 200, JSON.stringify(s.body));
  const c = await h.post(`/api/store/${slug}/verify/check`, { email: mailOf(phone), code: s.body.devCode });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  return c.body.ticket;
}

/** همان بدنه‌ای که فرمِ trust.js می‌سازد، با همان در */
function file(slug, ticket, kind, form) {
  const d = T.draft(kind, form);
  assert.ok(d.body, `فرم رد کرد: ${d.problem}`);
  return h.api('POST', `/api/store/${slug}/trust/${kind === 'report' ? 'reports' : 'complaints'}`, {
    body: d.body, headers: ticket ? { 'X-Store-Ticket': ticket } : {},
  });
}

/* ══════════════════════════════════════════════════════════════════
   ۱) پرچم و بار
   ══════════════════════════════════════════════════════════════════ */

test('پرچم: خاموش بی `features.trust` و درها ۴۰۴؛ روشن `trust: true`؛ trust.js فقط زیرِ همان کلید و بیرون از بارِ نخست', async () => {
  const s = await liveStore('پرچم');
  const off = await h.get(`/api/store/${s.slug}`);
  assert.equal(off.status, 200);
  assert.equal(Boolean(off.body.features && off.body.features.trust), false);
  assert.equal((await h.get(`/api/store/${s.slug}/trust/config`)).status, 404);

  await flag(s.shopId, 'market.trust', true);
  const on = await h.get(`/api/store/${s.slug}`);
  assert.equal(on.body.features.trust, true);
  const cfg = (await h.get(`/api/store/${s.slug}/trust/config`)).body;
  //  دسته‌های فرم همان دسته‌های سرورند
  assert.deepEqual(cfg.categories, { complaint: T.COMPLAINT, report: T.REPORT });
  assert.equal(cfg.limits.bodyMax, T.MAX_BODY);

  const js = await fetch(`${h.base()}/store/trust.js`);
  assert.equal(js.status, 200);
  assert.match(js.headers.get('content-type') || '', /javascript/);
  const cat = fs.readFileSync(path.join(PUB, 'store/catalog.js'), 'utf8');
  assert.match(cat, /if \(f\.trust\) \{\s*new Promise[\s\S]{0,200}'\/store\/trust\.js'/, 'catalog.js trust.js را فقط با f.trust می‌خواهد');
  for (const f of ['store/store.js', 'store/index.html', 'kit/kit.js', 'kit/boot.js']) {
    assert.doesNotMatch(fs.readFileSync(path.join(PUB, f), 'utf8'), /trust\.js/, `${f} trust.js را در بارِ نخست می‌خواهد`);
  }
  const first = ['store/store.js', 'kit/kit.js', 'kit/boot.js'].reduce((n, f) => n + fs.statSync(path.join(PUB, f)).size, 0);
  assert.ok(first <= FIRST_LOAD, `بارِ نخست ${first}`);
});

/* ══════════════════════════════════════════════════════════════════
   ۲) قاعده‌های فرم
   ══════════════════════════════════════════════════════════════════ */

test('فرم: دستهٔ شناخته، شرح تا ۲۰۰۰ و برای «دیگر» لازم، شمارهٔ سفارش رقم (فارسی هم)، کالای درست', () => {
  assert.equal(T.draft('complaint', { category: 'nope' }).problem, 'category');
  assert.equal(T.draft('report', { category: 'rude', productId: 'rice' }).problem, 'category', 'دستهٔ شکایت برای گزارشِ کالا نه');
  assert.equal(T.draft('complaint', { category: 'other', text: '   ' }).problem, 'text');
  assert.equal(T.draft('complaint', { category: 'damaged', text: 'x'.repeat(2001) }).problem, 'text');
  assert.deepEqual(T.draft('complaint', { category: 'damaged', text: ' شکسته ' }).body, { category: 'damaged', body: 'شکسته', subject: 'shop' });
  assert.deepEqual(T.draft('complaint', { category: 'wrong_item', orderNo: '۱۲' }).body,
    { category: 'wrong_item', body: '', subject: 'order', orderNo: 12 });
  assert.deepEqual(T.draft('complaint', { category: 'wrong_item', orderNo: '#7 ' }).body.orderNo, 7);
  for (const bad of ['0', '-3', '1.5', 'abc', '12a']) assert.equal(T.draft('complaint', { category: 'fraud', orderNo: bad }).problem, 'order', bad);
  assert.equal(T.draft('report', { category: 'counterfeit', productId: '' }).problem, 'product');
  assert.equal(T.draft('report', { category: 'counterfeit', productId: 'a b' }).problem, 'product');
  assert.deepEqual(T.draft('report', { category: 'counterfeit', productId: 'rice' }).body, { category: 'counterfeit', body: '', productId: 'rice' });
});

test('«گزارش‌های من» روی دستگاه: تازه‌ترین اول، بی تکرار، فقط توکنِ درست، حداکثر ۱۰', () => {
  const tk = (i) => `tc_${String(i).padStart(24, '0')}.${'A'.repeat(32)}`;
  assert.deepEqual(T.keep(null, tk(1)), [tk(1)]);
  assert.deepEqual(T.keep([tk(1), tk(2)], tk(2)), [tk(2), tk(1)]);
  assert.deepEqual(T.keep([tk(1), 'bad', 5, null, `${tk(3)}x`], undefined), [tk(1)]);
  const many = Array.from({ length: 15 }, (_, i) => tk(i));
  assert.equal(T.keep(many, tk(99)).length, T.KEEP);
  assert.equal(T.keep(many, tk(99))[0], tk(99));
});

/* ══════════════════════════════════════════════════════════════════
   ۳) سرتاسری با همان بدنه‌ها
   ══════════════════════════════════════════════════════════════════ */

test('ABUSE-01 ویترین: شکایت از دکان و سفارشِ خود، گزارشِ کالا ⇒ پرونده؛ تکرار همان؛ دکان شاکی را نمی‌شناسد', async () => {
  const s = await liveStore('سرتاسری');
  await flag(s.shopId, 'market.trust', true);
  const phone = '0791112233';

  //  بی بلیت ⇒ ۴۰۱ (فرم همین‌جا کد می‌خواهد)
  const nope = await file(s.slug, '', 'complaint', { category: 'rude', text: 'برخورد بد' });
  assert.equal(nope.status, 401);
  assert.equal(nope.body.error.code, 'verification_required');

  const ticket = await ticketFor(s.slug, phone);
  const ck = await h.post(`/api/store/${s.slug}/checkout`, {
    fulfillment: 'pickup', customer: { name: 'مشتری', phone }, lines: [{ productId: 'rice', qtyMilli: 1000 }],
  }, { headers: { 'Idempotency-Key': `ck-${uniq()}-key`, 'X-Store-Ticket': ticket } });
  assert.equal(ck.status, 201, JSON.stringify(ck.body));
  const orderNo = ck.body.order.orderNo;

  const shopCase = await file(s.slug, ticket, 'complaint', { category: 'rude', text: 'برخورد بد' });
  assert.equal(shopCase.status, 201, JSON.stringify(shopCase.body));
  const orderCase = await file(s.slug, ticket, 'complaint', { category: 'wrong_item', orderNo: String(orderNo), text: 'روغن به‌جای برنج' });
  assert.equal(orderCase.status, 201, JSON.stringify(orderCase.body));
  assert.equal(orderCase.body.case.subject, 'order');
  const report = await file(s.slug, ticket, 'report', { category: 'counterfeit', productId: 'oil', text: 'بسته‌بندی تقلبی' });
  assert.equal(report.status, 201, JSON.stringify(report.body));

  //  سفارشی که به شمارهٔ دیگری است ⇒ «پیدا نشد» (ترجمه در واژه‌نامهٔ اصلی)
  const other = await file(s.slug, await ticketFor(s.slug, '0792223344'), 'complaint', { category: 'fraud', orderNo: String(orderNo) });
  assert.equal(other.status, 404);
  assert.equal(other.body.error.code, 'order_not_found');

  //  تکرارِ همان موضوعِ باز ⇒ همان پرونده (۲۰۰)
  const again = await file(s.slug, ticket, 'complaint', { category: 'rude', text: 'باز هم' });
  assert.equal(again.status, 200);
  assert.equal(again.body.duplicate, true);
  assert.equal(again.body.case.token, shopCase.body.case.token);

  //  پیگیری با توکن، بی بلیت؛ پاسخِ خریدار شماره و نشانهٔ کسی را ندارد
  const tracked = await h.get(`/api/store/${s.slug}/trust/cases/${encodeURIComponent(report.body.case.token)}`);
  assert.equal(tracked.status, 200);
  assert.equal(tracked.body.case.status, 'open');
  const pubText = JSON.stringify([shopCase.body, orderCase.body, report.body, tracked.body]);
  assert.doesNotMatch(pubText, /0791112233|\+93791112233|reporter|ip_?key|"flags"/i);

  //  دکان: سه پرونده، بی شماره، توکن، شمارهٔ سفارش و نشانهٔ شاکی
  const list = await h.api('GET', '/api/shop/trust/cases', { token: s.token });
  assert.equal(list.status, 200, JSON.stringify(list.body));
  assert.equal(list.body.cases.length, 3);
  const shopText = JSON.stringify(list.body) + JSON.stringify((await h.api('GET', `/api/shop/trust/cases/${list.body.cases[0].id}`, { token: s.token })).body);
  assert.doesNotMatch(shopText, /0791112233|791112233|tc_[0-9a-f]{24}\.|reporter|orderNo|ip_?key/);
});

/* ══════════════════════════════════════════════════════════════════
   ۴) سقفِ نرخ و ترجمهٔ کدها
   ══════════════════════════════════════════════════════════════════ */

test('سقفِ روزانه ⇒ ۴۲۹ too_many_reports؛ هر کدِ خطای درها ترجمهٔ خودش را در هر سه زبان دارد', async () => {
  const s = await liveStore('سقف');
  await flag(s.shopId, 'market.trust', true);
  const ticket = await ticketFor(s.slug, '0793334455');
  const forms = [
    ['complaint', { category: 'rude', text: 'یک' }],
    ['report', { category: 'counterfeit', productId: 'rice' }],
    ['report', { category: 'misleading', productId: 'oil' }],
    ['report', { category: 'wrong_price', productId: 'tea' }],
  ];
  for (const [kind, f] of forms) assert.equal((await file(s.slug, ticket, kind, f)).status, 201);
  const over = await file(s.slug, ticket, 'report', { category: 'offensive', productId: 'salt' });
  assert.equal(over.status, 429, JSON.stringify(over.body));
  assert.equal(over.body.error.code, 'too_many_reports');

  const codes = ['too_many_reports', 'bad_category', 'text_too_long', 'product_not_found', 'case_not_found', 'trust_unavailable',
    'order_not_found', 'verification_required'];
  for (const l of ['fa', 'en', 'ps']) {
    const d = Object.assign({}, JSON.parse(fs.readFileSync(path.join(PUB, `kit/i18n/${l}.json`), 'utf8')),
      JSON.parse(fs.readFileSync(path.join(PUB, `kit/i18n/trust.${l}.json`), 'utf8')));
    for (const c of codes) assert.ok(d[`err.${c}`], `${l}: err.${c}`);
    //  دسته‌ها، وضعیت‌ها، نتیجه‌ها و ایرادهای فرم (کلیدهای پویای trust.js)
    for (const c of T.COMPLAINT) assert.ok(d[`tr.c.${c}`], `${l}: tr.c.${c}`);
    for (const c of T.REPORT) assert.ok(d[`tr.r.${c}`], `${l}: tr.r.${c}`);
    for (const c of ['open', 'in_review', 'decided', 'appealed', 'closed']) assert.ok(d[`tr.s.${c}`], `${l}: tr.s.${c}`);
    for (const c of ['upheld', 'dismissed', 'reversed']) assert.ok(d[`tr.o.${c}`], `${l}: tr.o.${c}`);
    for (const c of ['category', 'text', 'order', 'product']) assert.ok(d[`tr.bad.${c}`], `${l}: tr.bad.${c}`);
  }
});
