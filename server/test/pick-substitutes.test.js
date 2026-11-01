'use strict';
/**
 * جایگزینی هنگامِ جمع‌آوری (PCK-05، PCK-06؛ S37) — پشتِ پرچمِ خاموشِ `shop.orders.picking`.
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) ⛔ خاموش ⇒ درِ کارمند ۴۰۳، درِ مشتری ۴۰۴ و هیچ ردیف.
 *   ۲) PCK-06: فقط جایگزینِ امن (دسته، زیردسته، حلال، نوعِ واحد)؛ پیشنهادِ دوتایی؛ کالای بارکددار فقط با اسکن.
 *   ۳) PCK-06: قیمتِ شفاف — مشتری قیمتِ واحدِ کمتر را می‌پردازد؛ بسته ⇒ قلمِ سفارش = جایگزین.
 *   ۴) PCK-05: مشتری با لینکِ پیگیری می‌پذیرد/رد می‌کند؛ کارمند پس از تماس؛ واتس‌اپ فقط با `orders.contact`.
 *   ۵) PCK-05: پس از مهلت ترجیحِ قلم؛ «جایگزین نه» پیشنهاد نمی‌گیرد؛ بسته تا تصمیم صبر می‌کند.
 *   ۶) ⛔ کلیدِ یکتا، هم‌زمانی، «از نو»، دکانِ دیگر، توکنِ دیگر، نقش.
 */
process.env.RATE_STORE_CHECKOUT_MAX = '40';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');

let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;
const key = (p = 'k') => `${p}-${uniq()}-key`;

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  admin = (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
  assert.ok(admin, 'ورودِ مدیر');
});
test.after(async () => { await h.stop(); });
test.beforeEach(() => resetAll());

async function shopOwner(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, token: u.accessToken, shopId: made.body.shop.id };
}

async function member(shopId, role, name) {
  const u = await h.newUser(name, 'shop');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
     VALUES ($1,$2,$3,$4,'active',$5,$5)`,
    [newId('mem'), shopId, u.user.id, role, now()]
  );
  return { ...u, token: u.accessToken };
}

async function setFlag(scope, k, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key: k, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

const push = (token, ops, device = 'dev-sub') => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop', 'X-Device': device },
  body: { device_id: device, schema_version: 2, ops },
});
const op = (rowId, fields) => ({ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: rowId, type: 'insert', fields });

const OIL = '6260000000024';
const OIL2 = '6260000000031';

/** دکان با کالاهای هم‌دسته و ناهم‌دسته؛ گوشتِ «الف» و «گاو» حلال ثبت می‌شوند. */
async function subShop(label, { picking = true } = {}) {
  const o = await shopOwner(label);
  await setFlag(o.shopId, 'shop.orders', true);
  if (picking) await setFlag(o.shopId, 'shop.orders.picking', true);
  const p = await push(o.token, [
    op('oil', { name: 'روغن', unit: 'بوتل', salePrice: 300, category: 'خوراکی', subcategory: 'روغن', brand: 'آفتاب', barcodes: [OIL] }),
    op('oil2', { name: 'روغنِ دوم', unit: 'بوتل', salePrice: 320, category: 'خوراکی', subcategory: 'روغن', barcodes: [OIL2] }),
    op('oil3', { name: 'روغنِ ارزان', unit: 'بوتل', salePrice: 250, category: 'خوراکی', subcategory: 'روغن' }),
    op('vinegar', { name: 'سرکه', unit: 'بوتل', salePrice: 90, category: 'خوراکی', subcategory: 'سرکه' }),
    op('soap', { name: 'صابون', unit: 'دانه', salePrice: 40, category: 'بهداشتی' }),
    op('meat', { name: 'گوشتِ گوسفند', unit: 'کیلو', salePrice: 500, category: 'گوشت' }),
    op('meat2', { name: 'گوشتِ دیگر', unit: 'کیلو', salePrice: 450, category: 'گوشت' }),
    op('meat3', { name: 'گوشتِ گاو', unit: 'کیلو', salePrice: 480, category: 'گوشت' }),
  ]);
  assert.equal(p.status, 200, JSON.stringify(p.body));
  if (picking) {
    for (const id of ['meat', 'meat3']) {
      const r = await h.put(`/api/shop/orders/pick/products/${id}`, { halal: 'halal' }, { token: o.token });
      assert.equal(r.status, 200, JSON.stringify(r.body));
    }
  }
  return o;
}

const LINES = [
  { productId: 'oil', name: 'روغن', unit: 'بوتل', qtyMilli: 2000, unitPriceMinor: 30000 },
  { productId: 'meat', name: 'گوشتِ گوسفند', unit: 'کیلو', qtyMilli: 1000, unitPriceMinor: 50000, substitution: 'best_match' },
  { productId: 'vinegar', name: 'سرکه', unit: 'بوتل', qtyMilli: 1000, unitPriceMinor: 9000, substitution: 'none' },
];

async function acceptedOrder(o, { lines = LINES } = {}) {
  const r = await h.post('/api/shop/orders', {
    source: 'phone', fulfillment: 'pickup', paymentMethod: 'cod',
    customer: { name: 'احمد', phone: '0791234567' }, lines,
  }, { token: o.token, headers: { 'Idempotency-Key': key('order') } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const t = await h.post(`/api/shop/orders/${r.body.order.id}/transition`, { to: 'accepted' }, { token: o.token });
  assert.equal(t.status, 200, JSON.stringify(t.body));
  return r.body.order;
}

const start = (o, id) => h.post(`/api/shop/orders/${id}/pick/start`, {}, { token: o.token });
const send = (o, id, events) => h.post(`/api/shop/orders/${id}/pick/events`, { events }, { token: o.token });
const ev = (lineNo, type, extra = {}) => ({ eventId: key('ev'), lineNo, type, ...extra });
const missing = async (o, id, lineNo) => {
  const r = await send(o, id, [ev(lineNo, 'not_found')]);
  assert.equal(r.body.results[0].status, 'accepted', JSON.stringify(r.body));
};
const suggest = (o, id, lineNo) => h.get(`/api/shop/orders/${id}/pick/substitutes?lineNo=${lineNo}`, { token: o.token });
const propose = (o, id, body) => h.post(`/api/shop/orders/${id}/pick/substitutions`, { eventId: key('sub'), ...body }, { token: o.token });
const decide = (o, id, substitutionId, decision) => h.post(`/api/shop/orders/${id}/pick/substitutions/decide`,
  { substitutionId, decision }, { token: o.token });
const pack = (o, id, body = { bags: 1 }) => h.post(`/api/shop/orders/${id}/pick/pack`, body, { token: o.token, headers: { 'Idempotency-Key': key('pack') } });
const sheetOf = async (o, id) => (await h.get(`/api/shop/orders/${id}/pick`, { token: o.token })).body.pick;
const expire = (id) => query('UPDATE order_pick_substitutions SET deadline_at = $2 WHERE id = $1', [id, now() - 1000]);
const subRows = async (shopId) => (await one(
  `SELECT (SELECT COUNT(*) FROM order_pick_substitutions WHERE shop_id=$1)::int
        + (SELECT COUNT(*) FROM pick_product_attrs WHERE shop_id=$1)::int AS n`, [shopId]
)).n;

/** اسکنِ روغن (۲ عدد) و وزنِ گوشت تا بسته فقط به قلمِ زیرِ آزمون بسته باشد */
async function pickRest(o, id, skip = []) {
  const evs = [];
  if (!skip.includes(1)) evs.push(ev(1, 'scan', { barcode: OIL, qtyMilli: 2000 }));
  if (!skip.includes(2)) evs.push(ev(2, 'weigh', { qtyMilli: 1000 }));
  if (!skip.includes(3)) evs.push(ev(3, 'pick', { qtyMilli: 1000 }));
  const r = await send(o, id, evs);
  for (const x of r.body.results) assert.equal(x.status, 'accepted', JSON.stringify(x));
}

/* ══════════════════════════════════════════════════════════════════
   ۱) پرچمِ خاموش
   ══════════════════════════════════════════════════════════════════ */

test('⛔ پرچمِ جمع‌آوری خاموش: درهای جایگزین ۴۰۳ picking_disabled و هیچ ردیف', async () => {
  const o = await subShop('جایگزین-خاموش', { picking: false });
  const order = await acceptedOrder(o);
  for (const r of [
    await suggest(o, order.id, 1),
    await propose(o, order.id, { lineNo: 1, productId: 'oil2', barcode: OIL2 }),
    await decide(o, order.id, '01ARZ3NDEKTSV4RRFFQ69G5FAV', 'accept'),
    await h.put('/api/shop/orders/pick/products/oil', { halal: 'halal' }, { token: o.token }),
  ]) {
    assert.equal(r.status, 403, JSON.stringify(r.body));
    assert.equal(r.body.error.code, 'picking_disabled');
  }
  assert.equal(await subRows(o.shopId), 0);
});

/* ══════════════════════════════════════════════════════════════════
   ۲) PCK-06 — جایگزینِ امن
   ══════════════════════════════════════════════════════════════════ */

test('PCK-06: دو پیشنهادِ امن — نه دستهٔ دیگر، نه زیردستهٔ دیگر، نه حلال با نامعلوم؛ کالای پیدانشده هم نه', async () => {
  const o = await subShop('جایگزین-امن');
  const order = await acceptedOrder(o);
  assert.equal((await start(o, order.id)).status, 201);
  await missing(o, order.id, 1);
  await missing(o, order.id, 2);

  const s1 = await suggest(o, order.id, 1);
  assert.equal(s1.status, 200, JSON.stringify(s1.body));
  //  هم‌زیردسته: oil2 (۳۲۰) و oil3 (۲۵۰)؛ هیچ‌کدام هم‌برند نیست ⇒ نزدیک‌ترین قیمت به ۳۰۰ پیش‌تر
  assert.deepEqual(s1.body.suggestions.map((x) => x.productId), ['oil2', 'oil3']);
  const [a, b] = s1.body.suggestions;
  assert.equal(a.shelfUnitPriceMinor, 32000);
  assert.equal(a.chargedUnitPriceMinor, 30000, 'گران‌تر ⇒ همان قیمتِ سفارش');
  assert.equal(a.totalMinor, 60000);
  assert.equal(a.needsScan, true);
  assert.equal(b.chargedUnitPriceMinor, 25000, 'ارزان‌تر ⇒ قیمتِ خودش');
  assert.equal(b.needsScan, false);

  //  گوشتِ حلال ⇒ فقط گوشتِ گاوِ حلال (نه «دیگر» ِ نامعلوم)
  const s2 = await suggest(o, order.id, 2);
  assert.deepEqual(s2.body.suggestions.map((x) => x.productId), ['meat3']);
  assert.equal(s2.body.preference, 'best_match');

  //  پیشنهادِ ناامن با شناسهٔ دستی رد می‌شود — با دلیل
  for (const [lineNo, productId, reason] of [
    [1, 'soap', 'category_mismatch'], [1, 'vinegar', 'subcategory_mismatch'], [2, 'meat2', 'halal_mismatch'],
    [1, 'meat3', 'category_mismatch'], [1, 'oil', 'same_product'],
  ]) {
    const r = await propose(o, order.id, { lineNo, productId });
    assert.equal(r.status, 409, `${productId}: ${JSON.stringify(r.body)}`);
    assert.equal(r.body.error.code, 'unsafe_substitute');
    assert.equal(r.body.error.reason, reason, productId);
  }
  //  کالای «پیدا نشد» ِ دیگر (وظیفهٔ باز) جایگزین نمی‌شود و پیشنهاد هم نمی‌شود
  const o2 = await acceptedOrder(o, { lines: [{ productId: 'oil2', name: 'روغنِ دوم', unit: 'بوتل', qtyMilli: 1000, unitPriceMinor: 32000 }] });
  await start(o, o2.id);
  await missing(o, o2.id, 1);
  assert.deepEqual((await suggest(o, order.id, 1)).body.suggestions.map((x) => x.productId), ['oil3']);
  const held = await propose(o, order.id, { lineNo: 1, productId: 'oil2', barcode: OIL2 });
  assert.equal(held.status, 409);
  assert.equal(held.body.error.code, 'substitute_unavailable');
  assert.equal(await one('SELECT COUNT(*)::int AS n FROM order_pick_substitutions WHERE shop_id=$1', [o.shopId]).then((r) => r.n), 0);
});

test('PCK-06/PCK-02: جایگزینِ بارکددار فقط با اسکنِ خودش؛ فقط قلمِ «پیدا نشد»؛ مقدار تا سفارش؛ «جایگزین نه» پیشنهاد نمی‌گیرد', async () => {
  const o = await subShop('جایگزین-اسکن');
  const order = await acceptedOrder(o);
  await start(o, order.id);
  let r = await propose(o, order.id, { lineNo: 1, productId: 'oil2', barcode: OIL2 });
  assert.equal(r.status, 409);
  assert.equal(r.body.error.code, 'line_not_missing', 'قلمِ هنوز جمع‌نشده');
  await missing(o, order.id, 1);
  r = await propose(o, order.id, { lineNo: 1, productId: 'oil2' });
  assert.equal(r.body.error.code, 'scan_required');
  r = await propose(o, order.id, { lineNo: 1, productId: 'oil2', barcode: OIL });
  assert.equal(r.body.error.code, 'wrong_item');
  r = await propose(o, order.id, { lineNo: 1, productId: 'oil2', barcode: OIL2, qtyMilli: 3000 });
  assert.equal(r.body.error.code, 'over_qty');
  r = await propose(o, order.id, { lineNo: 1, productId: 'oil2', barcode: OIL2, waitMinutes: 1 });
  assert.equal(r.status, 400);
  assert.equal(r.body.error.code, 'bad_wait');
  r = await propose(o, order.id, { lineNo: 1, productId: 'nope', barcode: OIL2 });
  assert.equal(r.status, 404);
  //  رقمِ فارسی و فاصلهٔ بارکدخوان پاک می‌شود
  r = await propose(o, order.id, { lineNo: 1, productId: 'oil2', barcode: ' ۶۲۶۰۰۰۰۰۰۰۰۳۱ ' });
  assert.equal(r.status, 201, JSON.stringify(r.body));

  await missing(o, order.id, 3);
  r = await propose(o, order.id, { lineNo: 3, productId: 'oil3' });
  assert.equal(r.status, 409);
  assert.equal(r.body.error.code, 'no_substitutes_wanted');
  assert.equal(r.body.error.preference, 'none');
});

test('PCK-06: حلال بودن را فقط انباردار/مدیر عوض می‌کند (جمع‌آور نه)؛ فقط کالای همین دکان', async () => {
  const o = await subShop('جایگزین-حلال');
  const staff = await member(o.shopId, 'staff', 'جمع‌آورِ حلال');
  let r = await h.put('/api/shop/orders/pick/products/meat2', { halal: 'halal' }, { token: staff.accessToken });
  assert.equal(r.status, 403, JSON.stringify(r.body));
  r = await h.put('/api/shop/orders/pick/products/meat2', { halal: 'maybe' }, { token: o.token });
  assert.equal(r.status, 400);
  r = await h.put('/api/shop/orders/pick/products/ghost', { halal: 'halal' }, { token: o.token });
  assert.equal(r.status, 404);
  const other = await subShop('جایگزین-حلال-ب');
  r = await h.put('/api/shop/orders/pick/products/meat2', { halal: 'non_halal' }, { token: other.token });
  assert.equal(r.status, 200, 'کالای هم‌نامِ دکانِ خودش');
  assert.equal((await one('SELECT halal FROM pick_product_attrs WHERE shop_id=$1 AND product_id=$2', [o.shopId, 'meat2'])), null,
    'دکانِ دیگر ویژگیِ کالای «الف» را ننوشت');
  r = await h.put('/api/shop/orders/pick/products/meat', { halal: '' }, { token: o.token });
  assert.equal(r.status, 200);
  assert.equal(r.body.halal, '');
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM pick_product_attrs WHERE shop_id=$1 AND product_id=$2', [o.shopId, 'meat'])).n, 0);
});

/* ══════════════════════════════════════════════════════════════════
   ۳) PCK-05/06 — کارمند تأیید می‌کند؛ قیمتِ کمتر؛ بسته
   ══════════════════════════════════════════════════════════════════ */

test('PCK-05/06: کارمند پس از تماس می‌پذیرد ⇒ قلمِ سفارش = جایگزین با قیمتِ کمتر؛ گران‌تر هرگز؛ کسرِ خودکار درست', async () => {
  const o = await subShop('جایگزین-کارمند');
  const staff = await member(o.shopId, 'staff', 'جمع‌آورِ کارمند');
  const order = await acceptedOrder(o);
  await start(o, order.id);
  await pickRest(o, order.id, [1, 2]);
  await missing(o, order.id, 1);
  await missing(o, order.id, 2);

  //  روغنِ گران‌تر (۳۲۰) با همان ۳۰۰ ِ سفارش؛ شاگرد پیشنهاد می‌دهد، واتس‌اپ ندارد (شماره نمی‌بیند)
  const p1 = await h.post(`/api/shop/orders/${order.id}/pick/substitutions`,
    { eventId: key('sub'), lineNo: 1, productId: 'oil2', barcode: OIL2 }, { token: staff.accessToken });
  assert.equal(p1.status, 201, JSON.stringify(p1.body));
  assert.equal(p1.body.substitution.chargedUnitPriceMinor, 30000);
  assert.equal(p1.body.substitution.totalMinor, 60000);
  assert.equal(p1.body.substitution.status, 'pending');
  assert.equal(p1.body.customer.link, '', 'سفارشِ تلفنی لینکِ ویترین ندارد');
  assert.equal(p1.body.customer.whatsappUrl, '');
  const dl = p1.body.substitution.deadlineAt - p1.body.substitution.createdAt;
  assert.equal(dl, 10 * 60 * 1000, 'مهلتِ پیش‌فرض ۱۰ دقیقه');

  //  گوشتِ حلالِ ارزان‌تر (۴۸۰) با نیم کیلو
  const p2 = await propose(o, order.id, { lineNo: 2, productId: 'meat3', qtyMilli: 500, waitMinutes: 5 });
  assert.equal(p2.status, 201, JSON.stringify(p2.body));
  assert.equal(p2.body.substitution.chargedUnitPriceMinor, 48000);
  assert.equal(p2.body.substitution.totalMinor, 24000);

  //  بسته تا تصمیم صبر می‌کند
  let pk = await pack(o, order.id);
  assert.equal(pk.status, 409, JSON.stringify(pk.body));
  assert.equal(pk.body.error.code, 'substitution_pending');
  assert.equal(pk.body.error.pending, 2);

  let d = await decide(o, order.id, p1.body.substitution.id, 'accept');
  assert.equal(d.status, 200, JSON.stringify(d.body));
  assert.equal(d.body.substitution.status, 'accepted');
  assert.equal(d.body.substitution.decidedBy, 'staff');
  d = await decide(o, order.id, p1.body.substitution.id, 'accept');
  assert.equal(d.body.replayed, true, 'تکرارِ همان تصمیم');
  d = await decide(o, order.id, p1.body.substitution.id, 'reject');
  assert.equal(d.status, 409);
  assert.equal(d.body.error.code, 'already_decided');
  d = await decide(o, order.id, p2.body.substitution.id, 'accept');
  assert.equal(d.status, 200);

  const sheet = await sheetOf(o, order.id);
  const l1 = sheet.lines.find((l) => l.lineNo === 1);
  assert.equal(l1.status, 'substituted');
  assert.equal(l1.substitute.productId, 'oil2');
  assert.equal(sheet.substitutions.length, 2);
  //  جمعِ تا این لحظه: ۶۰۰ + ۲۴۰ + ۹۰ ؛ کسر = گوشت ۵۰۰ − ۲۴۰
  assert.equal(sheet.totals.subtotalMinor, 60000 + 24000 + 9000);
  assert.equal(sheet.totals.deductedMinor, 26000);

  pk = await pack(o, order.id, { bags: 2 });
  assert.equal(pk.status, 200, JSON.stringify(pk.body));
  assert.equal(pk.body.pick.totals.totalMinor, 93000);
  const ol = await many('SELECT * FROM online_order_lines WHERE order_id=$1 ORDER BY line_no', [order.id]);
  assert.deepEqual(ol.map((l) => [l.line_no, l.product_id, l.name, Number(l.qty_milli), Number(l.unit_price_minor), Number(l.line_total_minor)]), [
    [1, 'oil2', 'روغنِ دوم', 2000, 30000, 60000],
    [2, 'meat3', 'گوشتِ گاو', 500, 48000, 24000],
    [3, 'vinegar', 'سرکه', 1000, 9000, 9000],
  ]);
  const oo = await one('SELECT subtotal_minor, total_minor, items_count, status FROM online_orders WHERE id=$1', [order.id]);
  assert.equal(Number(oo.total_minor), 93000);
  assert.equal(Number(oo.items_count), 3);
  assert.equal(oo.status, 'packed');
  const note = await one(`SELECT detail FROM online_order_events WHERE order_id=$1 AND kind='note' ORDER BY seq DESC LIMIT 1`, [order.id]);
  const det = typeof note.detail === 'string' ? JSON.parse(note.detail) : note.detail;
  assert.deepEqual(det.pick.substitutions.map((x) => [x.lineNo, x.from, x.to]), [[1, 'oil', 'oil2'], [2, 'meat', 'meat3']]);
  const rep = await require('../src/lib/verify-data').verify({ shopId: o.shopId });
  assert.deepEqual(rep.checks.filter((c) => c.count > 0).map((c) => c.id), [], 'verify-data بی خطا');
});

test('PCK-05/07: ردِ جایگزین ⇒ قلم حذف و کسر؛ «از نو» پیشنهاد را پس می‌گیرد', async () => {
  const o = await subShop('جایگزین-رد');
  const order = await acceptedOrder(o);
  await start(o, order.id);
  await pickRest(o, order.id, [1]);
  await missing(o, order.id, 1);
  const p = await propose(o, order.id, { lineNo: 1, productId: 'oil3' });
  assert.equal(p.status, 201, JSON.stringify(p.body));
  let again = await propose(o, order.id, { lineNo: 1, productId: 'oil3' });
  assert.equal(again.status, 409);
  assert.equal(again.body.error.code, 'substitution_open');

  //  «از نو» ⇒ پس گرفته، قلم دوباره در انتظار؛ دوباره پیدا نشد ⇒ پیشنهادِ تازه ممکن
  const rs = await send(o, order.id, [ev(1, 'reset')]);
  assert.equal(rs.body.results[0].status, 'accepted');
  assert.equal((await one('SELECT status, decided_by FROM order_pick_substitutions WHERE id=$1', [p.body.substitution.id])).status, 'withdrawn');
  assert.equal((await sheetOf(o, order.id)).lines.find((l) => l.lineNo === 1).status, 'pending');
  const late = await decide(o, order.id, p.body.substitution.id, 'accept');
  assert.equal(late.status, 409, 'پس‌گرفته پذیرفته نمی‌شود');
  await missing(o, order.id, 1);
  again = await propose(o, order.id, { lineNo: 1, productId: 'oil3' });
  assert.equal(again.status, 201, JSON.stringify(again.body));
  const d = await decide(o, order.id, again.body.substitution.id, 'reject');
  assert.equal(d.body.substitution.status, 'rejected');

  const pk = await pack(o, order.id);
  assert.equal(pk.status, 200, JSON.stringify(pk.body));
  //  روغن (۶۰۰) حذف ⇒ ۵۰۰ + ۹۰
  assert.equal(pk.body.pick.totals.totalMinor, 59000);
  assert.equal(pk.body.pick.totals.deductedMinor, 60000);
  assert.deepEqual((await many('SELECT product_id FROM online_order_lines WHERE order_id=$1 ORDER BY line_no', [order.id])).map((l) => l.product_id),
    ['meat', 'vinegar']);
});

test('PCK-05: «از نو» روی جایگزینِ پذیرفته ⇒ قلم با کالای اصل از نو؛ وظیفهٔ تک‌برخوردِ «پیدا نشد» کنار می‌رود', async () => {
  const o = await subShop('جایگزین-ازنو');
  const order = await acceptedOrder(o);
  await start(o, order.id);
  await missing(o, order.id, 1);
  const p = await propose(o, order.id, { lineNo: 1, productId: 'oil3' });
  await decide(o, order.id, p.body.substitution.id, 'accept');
  assert.equal((await sheetOf(o, order.id)).lines.find((l) => l.lineNo === 1).status, 'substituted');
  const rs = await send(o, order.id, [ev(1, 'reset')]);
  assert.equal(rs.body.results[0].status, 'accepted', JSON.stringify(rs.body));
  const line = (await sheetOf(o, order.id)).lines.find((l) => l.lineNo === 1);
  assert.equal(line.status, 'pending');
  assert.equal(line.substitute, null);
  assert.equal((await one('SELECT status FROM order_pick_substitutions WHERE id=$1', [p.body.substitution.id])).status, 'withdrawn');
  assert.equal((await one(`SELECT COUNT(*)::int AS n FROM pick_issues WHERE shop_id=$1 AND product_id='oil' AND status='open'`, [o.shopId])).n, 0);
});

/* ══════════════════════════════════════════════════════════════════
   ۴) PCK-05 — مهلت و ترجیحِ ذخیره‌شده
   ══════════════════════════════════════════════════════════════════ */

test('PCK-05: پس از مهلت ترجیحِ قلم — «بهترین مشابه» ⇒ پذیرش، بی ترجیح ⇒ رد؛ تصمیمِ دیر ۴۰۹ با نتیجه', async () => {
  const o = await subShop('جایگزین-مهلت');
  const order = await acceptedOrder(o);
  await start(o, order.id);
  await pickRest(o, order.id, [1, 2]);
  await missing(o, order.id, 1);
  await missing(o, order.id, 2);
  const p1 = await propose(o, order.id, { lineNo: 1, productId: 'oil3' });                    // ترجیح ''
  const p2 = await propose(o, order.id, { lineNo: 2, productId: 'meat3', qtyMilli: 1000 });   // best_match
  assert.equal(p2.body.substitution.preference, 'best_match');

  await expire(p1.body.substitution.id);
  const sh = await sheetOf(o, order.id);
  const v1 = sh.substitutions.find((x) => x.id === p1.body.substitution.id);
  assert.equal(v1.expired, true);
  assert.equal(v1.willApply, 'reject');
  const late = await decide(o, order.id, p1.body.substitution.id, 'accept');
  assert.equal(late.status, 409, JSON.stringify(late.body));
  assert.equal(late.body.error.code, 'substitution_expired');
  assert.equal(late.body.error.status, 'rejected');
  const r1 = await one('SELECT status, decided_by FROM order_pick_substitutions WHERE id=$1', [p1.body.substitution.id]);
  assert.deepEqual([r1.status, r1.decided_by], ['rejected', 'timeout'], 'نتیجهٔ مهلت ماند (تراکنش بسته شد)');

  //  گوشت: مهلت می‌گذرد و بسته‌بندی خودش ترجیح را اعمال می‌کند
  let pk = await pack(o, order.id);
  assert.equal(pk.body.error.code, 'substitution_pending', 'مهلتِ گوشت هنوز نگذشته');
  await expire(p2.body.substitution.id);
  pk = await pack(o, order.id);
  assert.equal(pk.status, 200, JSON.stringify(pk.body));
  const r2 = await one('SELECT status, decided_by FROM order_pick_substitutions WHERE id=$1', [p2.body.substitution.id]);
  assert.deepEqual([r2.status, r2.decided_by], ['accepted', 'timeout']);
  //  روغن رد ⇒ حذف؛ گوشتِ گاو ۴۸۰ (کمتر از ۵۰۰)؛ سرکه ۹۰
  assert.equal(pk.body.pick.totals.totalMinor, 48000 + 9000);
  assert.deepEqual((await many('SELECT product_id FROM online_order_lines WHERE order_id=$1 ORDER BY line_no', [order.id])).map((l) => l.product_id),
    ['meat3', 'vinegar']);
});

/* ══════════════════════════════════════════════════════════════════
   ۵) PCK-05 — مشتری با لینکِ پیگیری
   ══════════════════════════════════════════════════════════════════ */

async function storefrontOrder(label) {
  const o = await subShop(label);
  await setFlag(o.shopId, 'shop.storefront', true);
  const slug = `sb-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug, enabled: true, title: 'ویترینِ جایگزین', contactPhone: '0700000001' }, { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  for (const id of ['oil', 'oil2', 'oil3']) {
    assert.equal((await h.put(`/api/shop/storefront/products/${id}`, { published: true }, { token: o.token })).status, 200);
  }
  const phone = `079${String(1000000 + (seq % 8999999)).slice(-7)}`;
  const v = await h.post(`/api/store/${slug}/verify/start`, { phone });
  const t = await h.post(`/api/store/${slug}/verify/check`, { phone, code: v.body.devCode });
  assert.equal(t.status, 200, JSON.stringify(t.body));
  const ck = await h.post(`/api/store/${slug}/checkout`, {
    fulfillment: 'pickup', customer: { name: 'مشتریِ ویترین', phone }, lines: [{ productId: 'oil', qtyMilli: 1000 }, { productId: 'oil3', qtyMilli: 1000 }],
  }, { headers: { 'Idempotency-Key': key('ck'), 'X-Store-Ticket': t.body.ticket } });
  assert.equal(ck.status, 201, JSON.stringify(ck.body));
  const id = (await one('SELECT id FROM online_orders WHERE shop_id=$1 ORDER BY order_no DESC LIMIT 1', [o.shopId])).id;
  assert.equal((await h.post(`/api/shop/orders/${id}/transition`, { to: 'accepted' }, { token: o.token })).status, 200);
  return { o, slug, id, token: ck.body.order.token, phone };
}

test('PCK-05: مشتری با لینکِ پیگیری می‌بیند و می‌پذیرد؛ واتس‌اپ فقط برای نقشِ دارای «تماس»؛ نمای مشتری بی دادهٔ داخلی', async () => {
  const { o, slug, id, token, phone } = await storefrontOrder('جایگزین-مشتری');
  const staff = await member(o.shopId, 'staff', 'شاگردِ ویترین');
  await start(o, id);
  await missing(o, id, 1);
  //  شاگرد: لینک دارد، واتس‌اپ (شمارهٔ مشتری) ندارد
  const ps = await h.post(`/api/shop/orders/${id}/pick/substitutions`,
    { eventId: key('sub'), lineNo: 1, productId: 'oil2', barcode: OIL2 }, { token: staff.accessToken });
  assert.equal(ps.status, 201, JSON.stringify(ps.body));
  //  بی SERVER_URL ⇒ زیردامنهٔ ویترین؛ با آن ⇒ `/store/<slug>` — هر دو به صفحهٔ همان سفارش
  assert.ok(ps.body.customer.link.includes(slug), ps.body.customer.link);
  assert.ok(ps.body.customer.link.endsWith(`#/o/${encodeURIComponent(token)}`), ps.body.customer.link);
  assert.equal(ps.body.customer.whatsappUrl, '');
  //  صاحب: همان پیشنهاد (همان کلید ⇒ بازپخش) با واتس‌اپ به شمارهٔ بین‌المللیِ مشتری
  const again = await h.post(`/api/shop/orders/${id}/pick/substitutions`,
    { eventId: 'x'.repeat(4), lineNo: 1, productId: 'oil2' }, { token: o.token });
  assert.equal(again.status, 400, 'کلیدِ کوتاه');
  const subId = ps.body.substitution.id;
  await query('UPDATE order_pick_substitutions SET proposed_by=$2 WHERE id=$1', [subId, staff.user.id]);
  const owner = await h.post(`/api/shop/orders/${id}/pick/substitutions/decide`, { substitutionId: 'bad', decision: 'accept' }, { token: o.token });
  assert.equal(owner.status, 404);
  const links = await require('../src/lib/pick-substitutes').customerLinks(o.shopId,
    await one('SELECT * FROM online_orders WHERE id=$1', [id]), { role: 'owner' });
  assert.equal(links.whatsappUrl, `https://wa.me/93${phone.slice(1)}?text=${encodeURIComponent(links.link)}`);

  const view = await h.get(`/api/store/${slug}/orders/${token}/substitutions`);
  assert.equal(view.status, 200, JSON.stringify(view.body));
  assert.equal(view.body.substitutions.length, 1);
  const s = view.body.substitutions[0];
  assert.deepEqual(s.original, { name: 'روغن', unit: 'بوتل', qtyMilli: 1000, unitPriceMinor: 30000, totalMinor: 30000 });
  assert.deepEqual(s.substitute, { name: 'روغنِ دوم', unit: 'بوتل', qtyMilli: 1000, unitPriceMinor: 30000, totalMinor: 30000 });
  const raw = JSON.stringify(view.body);
  for (const leak of [staff.user.id, OIL2, 'shelfUnitPriceMinor', 'proposed', phone]) assert.ok(!raw.includes(leak), `نمای مشتری ${leak} دارد`);

  const acc = await h.post(`/api/store/${slug}/orders/${token}/substitutions/${subId}`, { decision: 'accept' });
  assert.equal(acc.status, 200, JSON.stringify(acc.body));
  assert.equal(acc.body.substitution.status, 'accepted');
  assert.equal(acc.body.substitution.decidedBy, 'customer');
  const rep = await h.post(`/api/store/${slug}/orders/${token}/substitutions/${subId}`, { decision: 'accept' });
  assert.equal(rep.body.replayed, true);
  const flip = await h.post(`/api/store/${slug}/orders/${token}/substitutions/${subId}`, { decision: 'reject' });
  assert.equal(flip.status, 409);
  assert.equal(flip.body.error.code, 'already_decided');
  const bad = await h.post(`/api/store/${slug}/orders/${token}/substitutions/${subId}`, { decision: 'maybe' });
  assert.equal(bad.status, 400);

  await send(o, id, [ev(2, 'pick', { qtyMilli: 1000 })]);
  const pk = await pack(o, id);
  assert.equal(pk.status, 200, JSON.stringify(pk.body));
  //  مشتری در پیگیری قلمِ تازه را با همان قیمتِ کمتر می‌بیند
  const tr = await h.get(`/api/store/${slug}/orders/${token}`);
  assert.equal(tr.body.order.lines[0].name, 'روغنِ دوم');
  assert.equal(tr.body.order.lines[0].unitPriceMinor, 30000);
  assert.equal(tr.body.order.totalMinor, 55000);
});

test('⛔ مشتری: توکنِ نادرست، سفارشِ دکانِ دیگر، ویترینِ دیگر و پرچمِ خاموش ⇒ ۴۰۴ و هیچ تغییر', async () => {
  const A = await storefrontOrder('جایگزین-توکن-الف');
  const B = await storefrontOrder('جایگزین-توکن-ب');
  await start(A.o, A.id);
  await missing(A.o, A.id, 1);
  const p = await propose(A.o, A.id, { lineNo: 1, productId: 'oil3' });
  const subId = p.body.substitution.id;
  const forged = `${A.id}.${'A'.repeat(32)}`;
  for (const [slug, token] of [[A.slug, forged], [B.slug, A.token], [A.slug, B.token], [A.slug, 'nope']]) {
    const g = await h.get(`/api/store/${slug}/orders/${token}/substitutions`);
    assert.ok([404].includes(g.status), `${slug} ${token}: ${g.status}`);
    const d = await h.post(`/api/store/${slug}/orders/${token}/substitutions/${subId}`, { decision: 'accept' });
    assert.equal(d.status, 404, `${slug}: ${JSON.stringify(d.body)}`);
  }
  //  توکنِ درستِ B با پیشنهادِ A ⇒ پیشنهاد پیدا نشد
  const cross = await h.post(`/api/store/${B.slug}/orders/${B.token}/substitutions/${subId}`, { decision: 'accept' });
  assert.equal(cross.status, 404);
  //  کارمندِ B با سفارش و پیشنهادِ A
  const st = await decide(B.o, A.id, subId, 'accept');
  assert.equal(st.status, 404);
  const sg = await suggest(B.o, A.id, 1);
  assert.equal(sg.status, 404);
  assert.equal((await one('SELECT status FROM order_pick_substitutions WHERE id=$1', [subId])).status, 'pending');

  await setFlag(A.o.shopId, 'shop.orders.picking', false);
  const off = await h.get(`/api/store/${A.slug}/orders/${A.token}/substitutions`);
  assert.equal(off.status, 404);
  const offD = await h.post(`/api/store/${A.slug}/orders/${A.token}/substitutions/${subId}`, { decision: 'accept' });
  assert.equal(offD.status, 404);
  assert.equal((await one('SELECT status FROM order_pick_substitutions WHERE id=$1', [subId])).status, 'pending');
  //  پیگیریِ عادیِ سفارش با پرچمِ خاموش همان رفتارِ پیشین
  assert.equal((await h.get(`/api/store/${A.slug}/orders/${A.token}`)).status, 200);
});

/* ══════════════════════════════════════════════════════════════════
   ۶) کلیدِ یکتا و هم‌زمانی
   ══════════════════════════════════════════════════════════════════ */

test('⛔ پیشنهاد با کلیدِ یکتا: تکرار ⇒ همان، کلیدِ دوباره با بدنهٔ دیگر ۴۰۹؛ دو پیشنهادِ هم‌زمان ⇒ فقط یکی', async () => {
  const o = await subShop('جایگزین-هم‌زمان');
  const order = await acceptedOrder(o);
  await start(o, order.id);
  await missing(o, order.id, 1);
  const k = key('same');
  const a = await h.post(`/api/shop/orders/${order.id}/pick/substitutions`, { eventId: k, lineNo: 1, productId: 'oil3' }, { token: o.token });
  assert.equal(a.status, 201, JSON.stringify(a.body));
  const b = await h.post(`/api/shop/orders/${order.id}/pick/substitutions`, { eventId: k, lineNo: 1, productId: 'oil3' }, { token: o.token });
  assert.equal(b.status, 200);
  assert.equal(b.body.replayed, true);
  assert.equal(b.body.substitution.id, a.body.substitution.id);
  const c = await h.post(`/api/shop/orders/${order.id}/pick/substitutions`, { eventId: k, lineNo: 1, productId: 'oil2', barcode: OIL2 }, { token: o.token });
  assert.equal(c.status, 409);
  assert.equal(c.body.error.code, 'event_key_reused');

  const o2 = await acceptedOrder(o);
  await start(o, o2.id);
  await missing(o, o2.id, 1);
  const both = await Promise.all([
    propose(o, o2.id, { lineNo: 1, productId: 'oil3' }),
    propose(o, o2.id, { lineNo: 1, productId: 'oil2', barcode: OIL2 }),
  ]);
  assert.deepEqual(both.map((r) => r.status).sort(), [201, 409], JSON.stringify(both.map((r) => r.body)));
  assert.equal((await one(`SELECT COUNT(*)::int AS n FROM order_pick_substitutions WHERE order_id=$1 AND status='pending'`, [o2.id])).n, 1);

  //  تصمیمِ هم‌زمانِ مشتری و کارمند ⇒ یک تصمیم
  const sub = (await one(`SELECT id FROM order_pick_substitutions WHERE order_id=$1 AND status='pending'`, [o2.id])).id;
  const ds = await Promise.all([decide(o, o2.id, sub, 'accept'), decide(o, o2.id, sub, 'reject')]);
  assert.deepEqual(ds.map((r) => r.status).sort(), [200, 409]);
  const fin = await one('SELECT status FROM order_pick_substitutions WHERE id=$1', [sub]);
  assert.ok(['accepted', 'rejected'].includes(fin.status));
});
