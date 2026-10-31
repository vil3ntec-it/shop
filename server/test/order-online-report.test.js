'use strict';
/**
 * RPT-14 — گزارشِ فروشِ اینترنتی (`GET /api/shop/orders/online-report`، `lib/order-online-report.js`)
 * پشتِ پرچمِ خاموشِ `shop.orders.online_report`.
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) ⛔ خاموش ⇒ ۴۰۳ `online_report_disabled` و هیچ ردیفی در دفترِ جست‌وجوی بی‌نتیجه.
 *   ۲) قیف از خطِ زمانیِ واقعیِ سفارش (ثبت ⇐ قبول ⇐ بسته‌بندی ⇐ سپردن ⇐ انجام)، از دست رفته‌ها
 *      به دلیل، ناموفقِ تحویل جدا، نرخ‌ها به ده‌هزارم و زمان‌ها (میانه و صدکِ ۹۰) دقیقاً همان
 *      عددِ دست‌حساب‌شده؛ فقط سفارشِ ویترینِ غیرِآزمایشیِ همان بازه.
 *   ۳) جمعِ پولِ «انجام‌شده» عددِ صحیحِ یک‌صدم است.
 *   ۴) جست‌وجوی بی‌نتیجه: صفحهٔ نخست شمرده می‌شود؛ جست‌وجوی بانتیجه، صفحهٔ بعد و شبهِ
 *      شماره تلفن نه؛ سقفِ عبارتِ تازه در روز؛ نگهداریِ ۹۲روزه.
 *   ۵) ⛔ دکانِ دیگر گزارش و جست‌وجوهای این دکان را نمی‌بیند؛ بازهٔ بد ⇒ ۴۰۰.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, many, one, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const R = require('../src/lib/order-online-report');

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
});
test.after(async () => { await h.stop(); });
test.beforeEach(() => resetAll());

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

const push = (token, ops, device = 'dev-rpt') => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop', 'X-Device': device },
  body: { device_id: device, schema_version: 2, ops },
});
const op = (rowId, fields) => ({ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: rowId, type: 'insert', fields });

async function liveStore(label) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { token: u.accessToken, shopId: made.body.shop.id };
  await setFlag(o.shopId, 'shop.orders', true);
  await setFlag(o.shopId, 'shop.storefront', true);
  const slug = `rpt-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001' }, { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  assert.equal((await push(o.token, [op('rice', { name: 'برنج', unit: 'کیلو', salePrice: 120.5, category: 'خوراکی' })])).status, 200);
  assert.equal((await h.put('/api/shop/storefront/products/rice', { published: true }, { token: o.token })).status, 200);
  return { ...o, slug };
}

const tickets = new Map();
async function ticketFor(slug, phone) {
  const k = `${slug}|${phone}`;
  if (tickets.has(k)) return tickets.get(k);
  const s = await h.post(`/api/store/${slug}/verify/start`, { phone });
  assert.equal(s.status, 200, JSON.stringify(s.body));
  const c = await h.post(`/api/store/${slug}/verify/check`, { phone, code: s.body.devCode });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  tickets.set(k, c.body.ticket);
  return c.body.ticket;
}

/** یک سفارشِ ویترین (۲ کیلو برنج) — شناسهٔ داخلی. */
async function place(s, fulfillment, phone) {
  const body = {
    fulfillment, customer: { name: 'مشتری', phone }, lines: [{ productId: 'rice', qtyMilli: 2000 }],
    ...(fulfillment === 'delivery' ? { address: { lat: 34.52, lng: 69.18, landmark: 'نزدیکِ مسجد' } } : {}),
  };
  const r = await h.post(`/api/store/${s.slug}/checkout`, body, {
    headers: { 'Idempotency-Key': `ck-${uniq()}-key`, 'X-Store-Ticket': await ticketFor(s.slug, phone) },
  });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return (await one('SELECT id FROM online_orders WHERE shop_id = $1 ORDER BY order_no DESC LIMIT 1', [s.shopId])).id;
}

async function walk(s, id, steps) {
  for (const st of steps) {
    const [to, reason] = Array.isArray(st) ? st : [st, undefined];
    const r = await h.post(`/api/shop/orders/${id}/transition`, { to, ...(reason ? { reason } : {}) }, { token: s.token });
    assert.equal(r.status, 200, `${to}: ${JSON.stringify(r.body)}`);
  }
}

/** زمان‌های خطِ زمانی را قطعی کن: ثبت = T، رویدادِ وضعیتِ iام = T + offsets[i] ثانیه. */
async function retime(id, T, offsets) {
  await query('UPDATE online_orders SET created_at = $2 WHERE id = $1', [id, T]);
  const ev = await many('SELECT id, kind FROM online_order_events WHERE order_id = $1 ORDER BY seq', [id]);
  let i = 0;
  for (const e of ev) {
    const at = e.kind === 'status' ? T + offsets[i++] * 1000 : T;
    await query('UPDATE online_order_events SET created_at = $2 WHERE id = $1', [e.id, at]);
  }
  assert.equal(i, offsets.length, 'شمارِ رویدادهای وضعیت');
}

const S = 1000;
const PATH = ['accepted', 'picking', 'packed'];

test('خاموش ⇒ ۴۰۳ و هیچ ردیفِ جست‌وجو؛ روشن ⇒ قیف، نرخ و زمانِ دقیق', async () => {
  const s = await liveStore('گزارش');
  const off = await h.get('/api/shop/orders/online-report', { token: s.token });
  assert.equal(off.status, 403);
  assert.equal(off.body.error.code, 'online_report_disabled');
  assert.equal((await h.get(`/api/store/${s.slug}/products?q=زعفران`)).status, 200);
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM storefront_search_misses WHERE shop_id = $1', [s.shopId])).n, 0,
    'خاموش ⇒ هیچ نوشتنی');

  const A = '0791110001';
  const B = '0791110002';
  const o1 = await place(s, 'delivery', A);
  await walk(s, o1, [...PATH, 'out_for_delivery', 'delivered']);
  const o2 = await place(s, 'pickup', A);
  await walk(s, o2, [...PATH, 'ready_for_pickup', 'picked_up']);
  const o3 = await place(s, 'pickup', A);
  await walk(s, o3, [['rejected', 'out_of_stock']]);
  const o4 = await place(s, 'pickup', B);
  await walk(s, o4, ['accepted', ['cancelled', 'out_of_stock']]);
  const o5 = await place(s, 'delivery', B);
  await walk(s, o5, [...PATH, 'out_for_delivery', ['delivery_failed', 'no_answer'], 'retry_scheduled', 'out_for_delivery', 'delivered']);
  const o6 = await place(s, 'pickup', B);
  //  بیرون از گزارش: آزمایشی، تلفنی، و ثبت‌شده پیش از بازه
  const x1 = await place(s, 'pickup', A);
  await walk(s, x1, PATH);
  await query('UPDATE online_orders SET is_test = true WHERE id = $1', [x1]);
  const x2 = await place(s, 'pickup', B);
  await walk(s, x2, ['accepted']);
  await query("UPDATE online_orders SET source = 'phone' WHERE id = $1", [x2]);
  const x3 = await place(s, 'pickup', A);
  await walk(s, x3, ['accepted']);

  const T = now() - 10 * 24 * 60 * 60 * S;
  await retime(o1, T, [60, 90, 600, 700, 1900]);
  await retime(o2, T, [120, 130, 300, 310, 2000]);
  await retime(o3, T, [20]);
  await retime(o4, T, [240, 300]);
  await retime(o5, T, [30, 40, 400, 500, 800, 900, 1000, 1600]);
  await retime(o6, T, []);
  await retime(x1, T, [5, 6, 7]);
  await retime(x2, T, [5]);
  await retime(x3, T - 3600 * S, [5]);

  await setFlag(s.shopId, 'shop.orders.online_report', true);
  const q = `from=${T}&to=${T + 7 * 24 * 3600 * S}`;
  const r = await h.get(`/api/shop/orders/online-report?${q}`, { token: s.token });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const b = r.body;
  assert.equal(b.enabled, true);
  assert.deepEqual(b.window, { from: T, to: T + 7 * 24 * 3600 * S });
  assert.deepEqual(b.funnel, { placed: 6, accepted: 4, packed: 3, handedOver: 3, completed: 3 });
  assert.deepEqual(b.lost, { rejected: 1, auto_rejected: 0, cancelled_by_customer: 0, cancelled: 1, returned_to_store: 0, deliveryFailed: 1 });
  assert.deepEqual(b.rates, { acceptBp: 6667, completeBp: 5000, lostBp: 3333, deliveryFailBp: 5000 });
  assert.deepEqual(b.times.accept, { n: 4, medianMs: 60 * S, p90Ms: 240 * S });
  assert.deepEqual(b.times.prep, { n: 3, medianMs: 370 * S, p90Ms: 540 * S });
  assert.deepEqual(b.times.delivery, { n: 2, medianMs: 1100 * S, p90Ms: 1200 * S });
  assert.deepEqual(b.times.total, { n: 3, medianMs: 1900 * S, p90Ms: 2000 * S });
  const totals = await many('SELECT total_minor FROM online_orders WHERE id = ANY($1::text[])', [[o1, o2, o5]]);
  const want = totals.reduce((sum, t) => sum + Number(t.total_minor), 0);
  assert.ok(Number.isSafeInteger(want) && want > 0);
  assert.equal(b.completedTotalMinor, want);
  assert.equal(b.currency, 'AFN');
  assert.equal(b.visits, null, 'بازدید تا MKT-10 عددِ ساختگی ندارد');

  //  ⛔ دکانِ دیگر فقط دادهٔ خودش را می‌بیند
  const other = await liveStore('دیگر');
  await setFlag(other.shopId, 'shop.orders.online_report', true);
  const ro = await h.get(`/api/shop/orders/online-report?${q}`, { token: other.token });
  assert.equal(ro.status, 200);
  assert.equal(ro.body.funnel.placed, 0);
  assert.deepEqual(ro.body.times.accept, { n: 0, medianMs: null, p90Ms: null });
  assert.equal(ro.body.rates.acceptBp, null, 'مخرجِ صفر ⇒ null، نه ۰');
  //  بازهٔ بد
  for (const bad of ['from=10&to=5', `from=0&to=${93 * 24 * 3600 * S}`, 'from=-1', 'to=abc']) {
    const x = await h.get(`/api/shop/orders/online-report?${bad}`, { token: s.token });
    assert.equal(x.status, 400, bad);
    assert.equal(x.body.error.code, 'bad_window', bad);
  }
});

test('جست‌وجوی بی‌نتیجه: صفحهٔ نخست، بی شماره تلفن، سقفِ روزانه و نگهداری', async () => {
  const s = await liveStore('جست‌وجو');
  await setFlag(s.shopId, 'shop.orders.online_report', true);
  const search = async (qs) => {
    const r = await h.get(`/api/store/${s.slug}/products?${qs}`);
    assert.equal(r.status, 200, qs);
    return r.body;
  };
  assert.equal((await search(`q=${encodeURIComponent('زعفران')}`)).items.length, 0);
  assert.equal((await search(`q=${encodeURIComponent('  زعفران ')}`)).items.length, 0);
  assert.equal((await search(`q=${encodeURIComponent('زعفران')}&cursor=WyJ6IiwieiJd`)).items.length, 0);
  assert.equal((await search(`q=${encodeURIComponent('برنج')}`)).items.length, 1, 'بانتیجه');
  await search(`q=${encodeURIComponent('۰۷۹۱ ۲۳۴ ۵۶۷۸')}`);
  await search(`q=${encodeURIComponent('0791234567')}`);
  await search('q=x');
  await search(`q=${encodeURIComponent('كيك')}`);
  const rows = await many('SELECT q, n FROM storefront_search_misses WHERE shop_id = $1 ORDER BY q', [s.shopId]);
  assert.deepEqual(rows.map((x) => [x.q, Number(x.n)]), [['زعفران', 2], ['کیک', 1]],
    'نرمال، صفحهٔ نخست، بی بانتیجه، بی شبهِ تلفن، بی تک‌حرف');

  const r = await h.get('/api/shop/orders/online-report', { token: s.token });
  assert.equal(r.status, 200);
  assert.deepEqual(r.body.zeroResultSearches.map((x) => [x.q, x.n]), [['زعفران', 2], ['کیک', 1]]);
  assert.ok(r.body.zeroResultSearches.every((x) => Number.isSafeInteger(x.lastAt)));
  //  ⛔ دکانِ دیگر
  const other = await liveStore('جست‌وجوی دیگر');
  await setFlag(other.shopId, 'shop.orders.online_report', true);
  assert.deepEqual((await h.get('/api/shop/orders/online-report', { token: other.token })).body.zeroResultSearches, []);

  //  سقفِ عبارتِ تازه در روز: عبارتِ دیده‌شده بالا می‌رود، تازه نه
  const day = Date.UTC(2026, 0, 10, 12);
  for (let i = 0; i < R.MISS_DAILY_CAP; i++) assert.equal(await R.noteMiss(other.shopId, `عبارت ${i}`, day), true);
  assert.equal(await R.noteMiss(other.shopId, 'عبارتِ تازه', day), false, 'بالای سقف');
  assert.equal(await R.noteMiss(other.shopId, 'عبارت 7', day), true, 'دیده‌شده هنوز شمرده می‌شود');
  assert.equal(await R.noteMiss(other.shopId, 'عبارتِ تازه', day + 24 * 3600 * S), true, 'روزِ بعد سقفِ تازه');
  assert.equal((await one('SELECT n FROM storefront_search_misses WHERE shop_id = $1 AND q = $2', [other.shopId, 'عبارت 7'])).n, 2);
  //  پرچمِ خاموش ⇒ noteMiss هیچ نمی‌نویسد
  await setFlag(other.shopId, 'shop.orders.online_report', false);
  assert.equal(await R.noteMiss(other.shopId, 'خاموش', now()), false);
  //  نگهداری: کهنه‌تر از ۹۲ روز پاک
  const removed = await R.sweep(day + 93 * 24 * 3600 * S);
  assert.ok(removed >= R.MISS_DAILY_CAP, `${removed}`);
  assert.equal((await one("SELECT COUNT(*)::int AS n FROM storefront_search_misses WHERE shop_id = $1 AND q = 'عبارتِ تازه'", [other.shopId])).n, 0);
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM storefront_search_misses WHERE shop_id = $1', [s.shopId])).n, 2, 'امروزی می‌ماند');
});

test('ریاضی: نرخ با عددِ صحیح، رتبهٔ نزدیک، کلیدِ جست‌وجو', () => {
  assert.equal(R.bp(1, 3), 3333);
  assert.equal(R.bp(2, 3), 6667);
  assert.equal(R.bp(1, 8), 1250);
  assert.equal(R.bp(0, 5), 0);
  assert.equal(R.bp(3, 0), null);
  assert.deepEqual(R.stats([]), { n: 0, medianMs: null, p90Ms: null });
  assert.deepEqual(R.stats([5]), { n: 1, medianMs: 5, p90Ms: 5 });
  assert.deepEqual(R.stats([10, null, 1, 3, 2, -4, 7, 1.5]), { n: 5, medianMs: 3, p90Ms: 10 });
  assert.deepEqual(R.stats(Array.from({ length: 10 }, (_, i) => (i + 1) * 100)), { n: 10, medianMs: 500, p90Ms: 900 });
  assert.equal(R.missKey('  ZAفران  '), 'zaفران');
  assert.equal(R.missKey('a'), '');
  assert.equal(R.missKey('شماره ۰۷۹۱۲۳۴۵۶۷'), '');
  assert.equal(R.missKey('0791-234-567'), '');
  assert.equal(R.missKey('کد 123456'), 'کد 123456', 'شش رقم شماره تلفن نیست');
  assert.equal(R.missKey('ب'.repeat(80)).length, 60);
});
