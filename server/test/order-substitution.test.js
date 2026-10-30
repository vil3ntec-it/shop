'use strict';
/**
 * ترجیحِ جایگزینی — CHK-15 (و یادداشتِ هر قلم، CHK-16) · مهاجرتِ ۱۳۹.
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) ⛔ پرچمِ `shop.orders.substitution` خاموش ⇒ همان رفتارِ امروز: ویترین ترجیح نمی‌گیرد،
 *      کارمند فقط ترجیحِ قلم (بی نام)، کلیدهای تازه خوانده نمی‌شوند.
 *   ۲) یکی کلی و یکی برای هر قلم: قلمِ بی ترجیح ترجیحِ کلی را در ثبت می‌گیرد؛ «کالای مشخص»
 *      برای کلِ سفارش نیست و برای قلم جایگزینِ نام‌دار می‌خواهد.
 *   ۳) ویترین: جایگزینِ مشخص کالای همان ویترین است، نامش را سرور همان لحظه می‌خواند؛ نبودش
 *      ⇒ ۴۰۹ و هیچ سفارش. مشتری ترجیحِ خودش را در پیگیری می‌بیند، کارمند در جزئیات.
 *   ۴) CHECK ِ ۱۳۹ پشتِ سرِ کد؛ تکرارِ همان کلید سفارشِ دوم نمی‌سازد.
 */
process.env.RATE_STORE_CHECKOUT_MAX = '1000';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');

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

const push = (token, ops, device = 'dev-subst') => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop', 'X-Device': device },
  body: { device_id: device, schema_version: 2, ops },
});
const op = (rowId, fields) => ({ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: rowId, type: 'insert', fields });

/** دکانِ روشن با ویترین و سه کالای منتشرشده + یک کالای منتشرنشده؛ پرچمِ جایگزینی به انتخاب. */
async function liveStore(label, { substitution }) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { ...u, token: u.accessToken, shopId: made.body.shop.id };
  for (const k of ['shop.orders', 'shop.storefront']) await setFlag(o.shopId, k, true);
  if (substitution) await setFlag(o.shopId, 'shop.orders.substitution', true);
  const slug = `s-${uniq()}`;
  const put = await h.put('/api/shop/storefront', {
    slug, enabled: true, title: `ویترینِ ${label}`, deliveryFeeMinor: 0, minOrderMinor: 0, acceptPickup: true,
  }, { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const p = await push(o.token, [
    op('milk', { name: 'شیرِ پگاه', unit: 'بوتل', salePrice: 60, category: 'لبنیات' }),
    op('milk2', { name: 'شیرِ کابل', unit: 'بوتل', salePrice: 55, category: 'لبنیات' }),
    op('banana', { name: 'موز', unit: 'کیلو', salePrice: 90, category: 'میوه' }),
    op('secret', { name: 'کالای پنهان', unit: 'دانه', salePrice: 10, category: 'میوه' }),
  ]);
  assert.equal(p.status, 200, JSON.stringify(p.body));
  for (const id of ['milk', 'milk2', 'banana']) {
    const r = await h.put(`/api/shop/storefront/products/${id}`, { published: true }, { token: o.token });
    assert.equal(r.status, 200, JSON.stringify(r.body));
  }
  return { ...o, slug };
}

async function ticketFor(slug, phone) {
  const s = await h.post(`/api/store/${slug}/verify/start`, { phone });
  assert.equal(s.status, 200, JSON.stringify(s.body));
  const c = await h.post(`/api/store/${slug}/verify/check`, { phone, code: s.body.devCode });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  return c.body.ticket;
}

const PHONE = '0791112233';
const storeBody = (over = {}) => ({
  fulfillment: 'pickup',
  customer: { name: 'مشتریِ آزمایشی', phone: PHONE },
  lines: [
    { productId: 'milk', qtyMilli: 2000, note: 'تاریخِ تازه', substitution: 'specific', substituteProductId: 'milk2' },
    { productId: 'banana', qtyMilli: 1000, note: 'موزِ سبز' },
  ],
  substitution: 'refund',
  ...over,
});
const checkout = (slug, b, { key = `ck-${uniq()}-key`, ticket } = {}) => h.post(`/api/store/${slug}/checkout`, b, {
  headers: { 'Idempotency-Key': key, ...(ticket ? { 'X-Store-Ticket': ticket } : {}) },
});
const staffBody = (over = {}) => ({
  source: 'phone', fulfillment: 'pickup', paymentMethod: 'cod',
  customer: { name: 'احمد', phone: '0791234567' },
  substitution: 'best_match',
  lines: [
    { name: 'برنج', qtyMilli: 1000, unitPriceMinor: 12000 },
    { name: 'روغن', qtyMilli: 1000, unitPriceMinor: 30000, substitution: 'none' },
    { name: 'شیر', qtyMilli: 1000, unitPriceMinor: 6000, substitution: 'specific', substituteName: 'شیرِ کابل', substituteProductId: 'milk2' },
  ],
  ...over,
});
const staffCreate = (s, body, key = `st-${uniq()}-key`) => h.post('/api/shop/orders', body, { token: s.token, headers: { 'Idempotency-Key': key } });
const idOf = (o) => o.token.split('.')[0];
const count = async (shopId) => (await one('SELECT COUNT(*)::int AS n FROM online_orders WHERE shop_id=$1', [shopId])).n;
const pick = (lines, ...keys) => lines.map((l) => keys.map((k) => l[k]));

/* ══════════════════════════════════════════════════════════════════
   ۱) ⛔ پرچمِ خاموش ⇒ رفتارِ امروز
   ══════════════════════════════════════════════════════════════════ */

test('⛔ پرچمِ خاموش: ویترین ترجیح نمی‌گیرد؛ کارمند فقط ترجیحِ قلم و بی نام؛ /config خاموش', async () => {
  const s = await liveStore('جایگزینیِ خاموش', { substitution: false });
  const cfg = await h.get('/api/shop/orders/config', { token: s.token });
  assert.equal(cfg.body.substitution, false);

  const t = await ticketFor(s.slug, PHONE);
  //  حتی «کالای مشخص» ِ ساختگی هم سنجیده نمی‌شود — کلیدها خوانده نمی‌شوند
  const a = await checkout(s.slug, storeBody({ lines: [{ productId: 'milk', qtyMilli: 1000, substitution: 'specific', substituteProductId: 'nope' }] }), { ticket: t });
  assert.equal(a.status, 201, JSON.stringify(a.body));
  const st = await h.get(`/api/shop/orders/${idOf(a.body.order)}`, { token: s.token });
  assert.equal(st.body.order.substitution, '');
  assert.deepEqual(pick(st.body.order.lines, 'substitution', 'substituteName'), [['', '']]);

  const b = await staffCreate(s, staffBody({ substitution: 'specific' }));
  assert.equal(b.status, 201, JSON.stringify(b.body));
  assert.equal(b.body.order.substitution, '');
  //  ترجیحِ خودِ قلم مثلِ امروز می‌ماند؛ ترجیحِ کلی و نامِ جایگزین نه
  assert.deepEqual(pick(b.body.order.lines, 'substitution', 'substituteName'), [['', ''], ['none', ''], ['specific', '']]);
});

/* ══════════════════════════════════════════════════════════════════
   ۲) سفارشِ کارمند
   ══════════════════════════════════════════════════════════════════ */

test('CHK-15 کارمند: قلمِ بی ترجیح ترجیحِ کلی را می‌گیرد؛ «کالای مشخص» نامِ جایگزین می‌خواهد و برای کل نیست', async () => {
  const s = await liveStore('جایگزینیِ کارمند', { substitution: true });
  assert.equal((await h.get('/api/shop/orders/config', { token: s.token })).body.substitution, true);

  const a = await staffCreate(s, staffBody());
  assert.equal(a.status, 201, JSON.stringify(a.body));
  assert.equal(a.body.order.substitution, 'best_match');
  assert.deepEqual(pick(a.body.order.lines, 'substitution', 'substituteName', 'substituteProductId'), [
    ['best_match', '', ''], ['none', '', ''], ['specific', 'شیرِ کابل', 'milk2'],
  ]);
  const got = await h.get(`/api/shop/orders/${a.body.order.id}`, { token: s.token });
  assert.deepEqual(pick(got.body.order.lines, 'substitution', 'substituteName'), pick(a.body.order.lines, 'substitution', 'substituteName'));

  const n0 = await count(s.shopId);
  const bad = [
    staffBody({ substitution: 'specific' }),
    staffBody({ substitution: 'maybe' }),
    staffBody({ lines: [{ name: 'شیر', qtyMilli: 1000, unitPriceMinor: 6000, substitution: 'specific' }] }),
    staffBody({ lines: [{ name: 'شیر', qtyMilli: 1000, unitPriceMinor: 6000, substitution: 'specific', substituteName: 'x', substituteProductId: 'a b' }] }),
  ];
  for (const b of bad) {
    const r = await staffCreate(s, b);
    assert.equal(r.status, 400, JSON.stringify(b));
    assert.equal(r.body.error.code, 'bad_substitution', JSON.stringify(r.body));
  }
  assert.equal(await count(s.shopId), n0, 'هیچ سفارشی از ردها');

  //  بی ترجیحِ کلی ⇒ همان ترجیحِ قلم؛ نامِ جایگزین فقط برای «کالای مشخص» نوشته می‌شود
  const c = await staffCreate(s, staffBody({
    substitution: '',
    lines: [{ name: 'برنج', qtyMilli: 1000, unitPriceMinor: 12000, substitution: 'refund', substituteName: 'نباید بماند' }],
  }));
  assert.equal(c.status, 201, JSON.stringify(c.body));
  assert.deepEqual(pick(c.body.order.lines, 'substitution', 'substituteName'), [['refund', '']]);
});

/* ══════════════════════════════════════════════════════════════════
   ۳) ویترین
   ══════════════════════════════════════════════════════════════════ */

test('CHK-15/16 ویترین: ترجیحِ قلم و کلی، جایگزینِ مشخص با نامِ سرور، یادداشتِ هر قلم — مشتری و کارمند هر دو می‌بینند', async () => {
  const s = await liveStore('جایگزینیِ ویترین', { substitution: true });
  const t = await ticketFor(s.slug, PHONE);
  //  نامِ ساختگیِ مشتری خوانده نمی‌شود؛ نام از دفترِ همان لحظه
  const a = await checkout(s.slug, storeBody({
    lines: [{ ...storeBody().lines[0], substituteName: 'نامِ ساختگی' }, storeBody().lines[1]],
  }), { ticket: t });
  assert.equal(a.status, 201, JSON.stringify(a.body));
  assert.equal(a.body.order.substitution, 'refund');
  assert.deepEqual(pick(a.body.order.lines, 'productId', 'substitution', 'substituteName', 'note'), [
    ['milk', 'specific', 'شیرِ کابل', 'تاریخِ تازه'], ['banana', 'refund', '', 'موزِ سبز'],
  ]);
  //  پیگیریِ مشتری همان را دارد، و کارمند در جزئیات
  const tr = await h.get(`/api/store/${s.slug}/orders/${a.body.order.token}`);
  assert.equal(tr.status, 200, JSON.stringify(tr.body));
  assert.deepEqual(pick(tr.body.order.lines, 'substitution', 'substituteName'), [['specific', 'شیرِ کابل'], ['refund', '']]);
  const st = await h.get(`/api/shop/orders/${idOf(a.body.order)}`, { token: s.token });
  assert.equal(st.body.order.substitution, 'refund');
  assert.deepEqual(pick(st.body.order.lines, 'substitution', 'substituteProductId', 'substituteName', 'note'), [
    ['specific', 'milk2', 'شیرِ کابل', 'تاریخِ تازه'], ['refund', '', '', 'موزِ سبز'],
  ]);

  const n0 = await count(s.shopId);
  //  جایگزینِ منتشرنشده یا ناشناس ⇒ ۴۰۹ با نشانیِ همان قلم؛ هیچ سفارش
  for (const sub of ['secret', 'ghost']) {
    const r = await checkout(s.slug, storeBody({ lines: [{ productId: 'milk', qtyMilli: 1000, substitution: 'specific', substituteProductId: sub }] }), { ticket: t });
    assert.equal(r.status, 409, `${sub}: ${JSON.stringify(r.body)}`);
    assert.equal(r.body.error.code, 'substitute_unavailable');
    assert.equal(r.body.error.productId, 'milk');
    assert.equal(r.body.error.substituteProductId, sub);
  }
  const bad = [
    storeBody({ lines: [{ productId: 'milk', qtyMilli: 1000, substitution: 'specific', substituteProductId: 'milk' }] }),
    storeBody({ lines: [{ productId: 'milk', qtyMilli: 1000, substitution: 'specific' }] }),
    storeBody({ lines: [{ productId: 'milk', qtyMilli: 1000, substitution: 'whatever' }] }),
    storeBody({ substitution: 'specific' }),
  ];
  for (const b of bad) {
    const r = await checkout(s.slug, b, { ticket: t });
    assert.equal(r.status, 400, JSON.stringify(b));
    assert.equal(r.body.error.code, 'bad_substitution', JSON.stringify(r.body));
  }
  assert.equal(await count(s.shopId), n0);

  //  جایگزینِ مشخص که بعد از دیدنِ مشتری از ویترین برداشته شد ⇒ ۴۰۹، نه جایگزینِ بی‌نام
  assert.equal((await h.put('/api/shop/storefront/products/milk2', { published: false }, { token: s.token })).status, 200);
  const gone = await checkout(s.slug, storeBody(), { ticket: t });
  assert.equal(gone.status, 409, JSON.stringify(gone.body));
  assert.equal(gone.body.error.code, 'substitute_unavailable');
  assert.equal(await count(s.shopId), n0);
});

test('تکرارِ همان کلید ⇒ همان سفارش؛ CHECK ِ ۱۳۹ پشتِ سرِ کد', async () => {
  const s = await liveStore('جایگزینیِ تکرار', { substitution: true });
  const t = await ticketFor(s.slug, PHONE);
  const key = `ck-${uniq()}-same`;
  const [a, b] = await Promise.all([checkout(s.slug, storeBody(), { key, ticket: t }), checkout(s.slug, storeBody(), { key, ticket: t })]);
  assert.deepEqual([a.status, b.status].sort(), [200, 201], JSON.stringify([a.body, b.body]));
  assert.equal(a.body.order.token, b.body.order.token);
  assert.equal(await count(s.shopId), 1);

  //  ستون‌ها هستند و مقدارِ درست پذیرفته می‌شود؛ نادرست را خودِ پایگاه رد می‌کند
  const id = idOf(a.body.order);
  await query('UPDATE online_orders SET substitution=$2 WHERE id=$1', [id, 'none']);
  await query(`UPDATE online_order_lines SET substitute_name='شیرِ دیگر' WHERE order_id=$1 AND substitution = 'specific'`, [id]);
  await assert.rejects(query('UPDATE online_orders SET substitution=$2 WHERE id=$1', [id, 'specific']));
  await assert.rejects(query(
    `UPDATE online_order_lines SET substitute_name='x' WHERE order_id=$1 AND substitution <> 'specific'`, [id]));
});
