'use strict';
/**
 * ویترین، هزینه‌های ارسال (مهاجرتِ ۱۳۸) — CHK-06 و CHK-07.
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) CHK-06: ناحیهٔ دارای «هزینهٔ سبدِ کوچک» سبدِ زیرِ حداقل را به‌جای رد با
 *      همان هزینه می‌پذیرد؛ بالای حداقل هزینه‌ای نیست؛ ناحیهٔ بی آن همان ردِ امروز.
 *   ۲) CHK-07: هزینهٔ بازهٔ «ارسال» روی هزینهٔ ارسال می‌آید؛ «رایگان از…» فقط
 *      هزینهٔ ناحیه را برمی‌دارد؛ «هرچه زودتر» و «دریافت از دکان» بی هزینهٔ بازه.
 *   ۳) هر هزینه از همان سبد دیده می‌شود (برگهٔ قیمت، سفارش، فهرستِ بازه و ناحیه)
 *      و جمعِ دیده‌شدهٔ کهنه ⇒ price_changed، هرگز ثبتِ بی‌صدا.
 *   ۴) ⛔ برنامهٔ پیش از ۱۳۸ (کلیدِ نیامده) تنظیمِ صاحب دکان را پاک نمی‌کند؛ فقط null/۰ ِ صریح.
 *   ۵) تکرارِ همان کلیدِ یکتایی پس از تغییرِ هزینه همان سفارش و همان مبلغ را برمی‌گرداند.
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

const push = (token, ops, device = 'dev-fees') => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop', 'X-Device': device },
  body: { device_id: device, schema_version: 2, ops },
});
const op = (rowId, fields) => ({ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: rowId, type: 'insert', fields });

/** دکانِ روشن با ویترین، ناحیه/بازهٔ روشن و دو کالای منتشرشده. */
async function liveStore(label) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { ...u, token: u.accessToken, shopId: made.body.shop.id };
  for (const k of ['shop.orders', 'shop.storefront', 'shop.storefront.zones']) await setFlag(o.shopId, k, true);
  const slug = `f-${uniq()}`;
  const put = await h.put('/api/shop/storefront', {
    slug, enabled: true, title: `ویترینِ ${label}`, deliveryFeeMinor: 5000, minOrderMinor: 0,
  }, { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const p = await push(o.token, [
    op('rice', { name: 'برنج', unit: 'کیلو', salePrice: 120.5, category: 'خوراکی' }),
    op('oil', { name: 'روغن', unit: 'بوتل', salePrice: 300, category: 'خوراکی' }),
  ]);
  assert.equal(p.status, 200, JSON.stringify(p.body));
  for (const id of ['rice', 'oil']) {
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
const INSIDE = { lat: 34.53, lng: 69.17 };
const POLY = [[34.50, 69.12], [34.50, 69.22], [34.56, 69.22], [34.56, 69.12]];
const EVERY_DAY = [0, 1, 2, 3, 4, 5, 6];
//  برنج ۲ کیلو (۲۴۱) + روغن ۱ (۳۰۰) = ۵۴۱ افغانی
const SMALL = [{ productId: 'rice', qtyMilli: 2000 }, { productId: 'oil', qtyMilli: 1000 }];
const SMALL_SUB = 54100;
//  برنج ۴ + روغن ۲ = ۱۰۸۲ افغانی
const BIG = [{ productId: 'rice', qtyMilli: 4000 }, { productId: 'oil', qtyMilli: 2000 }];
const BIG_SUB = 108200;

const zone = (over = {}) => ({
  name: 'کارته‌سه', kind: 'polygon', polygon: POLY, feeMinor: 3000, minOrderMinor: 60000, ...over,
});
function body(over = {}) {
  return {
    fulfillment: 'delivery',
    customer: { name: 'مشتریِ آزمایشی', phone: PHONE },
    address: { text: 'کارته‌سه، کوچهٔ دوم', landmark: 'پشتِ مسجد', ...INSIDE },
    lines: SMALL,
    ...over,
  };
}
const checkout = (slug, b, { key = `ck-${uniq()}-key`, ticket } = {}) => h.post(`/api/store/${slug}/checkout`, b, {
  headers: { 'Idempotency-Key': key, ...(ticket ? { 'X-Store-Ticket': ticket } : {}) },
});
const count = async (shopId) => (await one('SELECT COUNT(*)::int AS n FROM online_orders WHERE shop_id=$1', [shopId])).n;
const putDelivery = (s, b) => h.put('/api/shop/storefront/delivery', b, { token: s.token });
//  شناسهٔ سفارش همان بخشِ نخستِ توکنِ پیگیری است (`<ULID>.<HMAC>`)
const staffOrder = (s, order) => h.get(`/api/shop/orders/${order.token.split('.')[0]}`, { token: s.token });

/* ══════════════════════════════════════════════════════════════════
   ۱) CHK-06 — سبدِ کوچک به‌جای حداقل
   ══════════════════════════════════════════════════════════════════ */

test('CHK-06: زیرِ حداقل با «هزینهٔ سبدِ کوچک» پذیرفته و هزینه دیده می‌شود؛ بالای حداقل بی آن؛ ناحیهٔ بی آن همان رد', async () => {
  const s = await liveStore('سبدِ کوچک');
  const set = await putDelivery(s, { zones: [zone({ smallBasketFeeMinor: 2500 })] });
  assert.equal(set.status, 200, JSON.stringify(set.body));
  assert.equal(set.body.zones[0].smallBasketFeeMinor, 2500);
  const t = await ticketFor(s.slug, PHONE);

  //  نمای عمومی: مشتری پیش از سبد می‌بیند زیرِ حداقل چه می‌شود
  const prof = await h.get(`/api/store/${s.slug}`);
  assert.equal(prof.body.store.delivery.zones[0].smallBasketFeeMinor, 2500);
  const here = await h.get(`/api/store/${s.slug}/delivery?lat=${INSIDE.lat}&lng=${INSIDE.lng}`);
  assert.equal(here.body.zone.smallBasketFeeMinor, 2500);

  //  جمعِ دیده‌شدهٔ بی هزینهٔ سبدِ کوچک ⇒ price_changed با ریزِ هزینه، و هیچ سفارش
  const n0 = await count(s.shopId);
  const stale = await checkout(s.slug, body({ expectedTotalMinor: SMALL_SUB + 3000 }), { ticket: t });
  assert.equal(stale.status, 409, JSON.stringify(stale.body));
  assert.equal(stale.body.error.code, 'price_changed');
  assert.equal(stale.body.error.quote.smallBasketFeeMinor, 2500);
  assert.equal(stale.body.error.quote.deliveryFeeMinor, 5500);
  assert.equal(stale.body.error.quote.totalMinor, SMALL_SUB + 5500);
  assert.equal(await count(s.shopId), n0);

  const a = await checkout(s.slug, body({ expectedTotalMinor: SMALL_SUB + 5500 }), { ticket: t });
  assert.equal(a.status, 201, JSON.stringify(a.body));
  assert.equal(a.body.order.deliveryFeeMinor, 5500, 'ناحیه ۳۰ + سبدِ کوچک ۲۵');
  assert.equal(a.body.order.smallBasketFeeMinor, 2500);
  assert.equal(a.body.order.slotFeeMinor, 0);
  assert.equal(a.body.order.totalMinor, SMALL_SUB + 5500);
  //  صاحب دکان همان ریز را در نشانیِ سفارش می‌بیند
  const st = await staffOrder(s, a.body.order);
  assert.equal(st.status, 200, JSON.stringify(st.body));
  assert.equal(st.body.order.deliveryFeeMinor, 5500);
  assert.equal(st.body.order.address.zone.smallBasketFeeMinor, 2500);
  assert.equal(st.body.order.totalMinor, SMALL_SUB + 5500);

  //  بالای حداقل ⇒ فقط هزینهٔ ناحیه، و ریزِ سبدِ کوچک در هیچ‌جا نیست
  const big = await checkout(s.slug, body({ lines: BIG }), { ticket: t });
  assert.equal(big.status, 201, JSON.stringify(big.body));
  assert.equal(big.body.order.deliveryFeeMinor, 3000);
  assert.equal(big.body.order.smallBasketFeeMinor, 0);
  const st2 = await staffOrder(s, big.body.order);
  assert.ok(!('smallBasketFeeMinor' in st2.body.order.address.zone));

  //  پاک کردنِ صریح ⇒ همان ردِ امروز با حداقلِ ناحیه
  assert.equal((await putDelivery(s, { zones: [zone({ smallBasketFeeMinor: null })] })).status, 200);
  const n1 = await count(s.shopId);
  const low = await checkout(s.slug, body(), { ticket: t });
  assert.equal(low.status, 400, JSON.stringify(low.body));
  assert.equal(low.body.error.code, 'below_min_order');
  assert.equal(low.body.error.minOrderMinor, 60000);
  assert.ok(!('smallBasketFeeMinor' in low.body.error.quote));
  assert.equal(await count(s.shopId), n1);
});

test('CHK-06: ورودیِ بد رد — بی حداقل، صفر، منفی، بیش از سقف؛ هیچ ناحیه‌ای نیمه ذخیره نمی‌شود', async () => {
  const s = await liveStore('سبدِ کوچکِ بد');
  const bad = [
    [zone({ minOrderMinor: 0, smallBasketFeeMinor: 1000 }), 'bad_zone'],
    [zone({ smallBasketFeeMinor: 0 }), 'bad_delivery_settings'],
    [zone({ smallBasketFeeMinor: -5 }), 'bad_delivery_settings'],
    [zone({ smallBasketFeeMinor: 100_000_001 }), 'bad_delivery_settings'],
    [zone({ smallBasketFeeMinor: 12.5 }), 'bad_delivery_settings'],
  ];
  for (const [z, code] of bad) {
    const r = await putDelivery(s, { zones: [zone({ name: 'خوب' }), z] });
    assert.equal(r.status, 400, JSON.stringify(z));
    assert.equal(r.body.error.code, code, JSON.stringify(r.body));
  }
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM storefront_zones WHERE shop_id=$1', [s.shopId])).n, 0);
  //  CHECK ِ ۱۳۸ پشتِ سرِ کد: حتی نوشتنِ مستقیم «سبدِ کوچک بی حداقل» را نمی‌پذیرد
  assert.equal((await putDelivery(s, { zones: [zone()] })).status, 200);
  await assert.rejects(query('UPDATE storefront_zones SET min_order_minor=0, small_basket_fee_minor=10 WHERE shop_id=$1', [s.shopId]));
});

/* ══════════════════════════════════════════════════════════════════
   ۲) CHK-07 — هزینهٔ زمانِ تحویل
   ══════════════════════════════════════════════════════════════════ */

test('CHK-07: هزینهٔ بازه روی ارسال می‌آید و دیده می‌شود؛ «رایگان از…» فقط ناحیه را برمی‌دارد؛ «هرچه زودتر» بی آن', async () => {
  const s = await liveStore('بازهٔ پولی');
  const set = await putDelivery(s, {
    zones: [zone({ minOrderMinor: 0, freeOverMinor: 100000 })],
    slotDays: 3,
    slotRules: [
      { fulfillment: 'delivery', weekdays: EVERY_DAY, startMin: 0, endMin: 1440, capacity: 50, cutoffMin: 0, feeMinor: 2000 },
      { fulfillment: 'pickup', weekdays: EVERY_DAY, startMin: 0, endMin: 1440, capacity: 50, cutoffMin: 0 },
    ],
  });
  assert.equal(set.status, 200, JSON.stringify(set.body));
  assert.deepEqual(set.body.slotRules.map((r) => [r.fulfillment, r.feeMinor]), [['delivery', 2000], ['pickup', 0]]);
  const t = await ticketFor(s.slug, PHONE);

  const list = await h.get(`/api/store/${s.slug}/slots?fulfillment=delivery`);
  assert.equal(list.status, 200, JSON.stringify(list.body));
  assert.ok(list.body.slots.length >= 1);
  assert.ok(list.body.slots.every((x) => x.feeMinor === 2000), 'مشتری هزینهٔ بازه را پیش از انتخاب می‌بیند');
  const slot = list.body.slots[list.body.slots.length - 1];
  assert.ok((await h.get(`/api/store/${s.slug}/slots?fulfillment=pickup`)).body.slots.every((x) => x.feeMinor === 0));

  const a = await checkout(s.slug, body({ slotStartsAt: slot.startsAt, expectedTotalMinor: SMALL_SUB + 5000 }), { ticket: t });
  assert.equal(a.status, 201, JSON.stringify(a.body));
  assert.equal(a.body.order.deliveryFeeMinor, 5000, 'ناحیه ۳۰ + بازه ۲۰');
  assert.equal(a.body.order.slotFeeMinor, 2000);
  assert.equal(a.body.order.smallBasketFeeMinor, 0);
  const st = await staffOrder(s, a.body.order);
  assert.equal(st.body.order.delivery.slot.feeMinor, 2000);
  assert.equal(st.body.order.totalMinor, SMALL_SUB + 5000);

  //  «رایگان از ۱۰۰۰»: ناحیه رایگان، بازه همچنان (انتخابِ خودِ مشتری)
  const big = await checkout(s.slug, body({ lines: BIG, slotStartsAt: slot.startsAt }), { ticket: t });
  assert.equal(big.status, 201, JSON.stringify(big.body));
  assert.equal(big.body.order.deliveryFeeMinor, 2000);
  assert.equal(big.body.order.totalMinor, BIG_SUB + 2000);

  //  «هرچه زودتر» ⇒ بی هزینهٔ بازه
  const asap = await checkout(s.slug, body(), { ticket: t });
  assert.equal(asap.status, 201, JSON.stringify(asap.body));
  assert.equal(asap.body.order.deliveryFeeMinor, 3000);
  assert.equal(asap.body.order.slotFeeMinor, 0);
  const st3 = await staffOrder(s, asap.body.order);
  assert.ok(!st3.body.order.delivery.slot);

  //  دریافت از دکان با بازه ⇒ هیچ هزینه‌ای
  const pslot = (await h.get(`/api/store/${s.slug}/slots?fulfillment=pickup`)).body.slots[0];
  const pick = await checkout(s.slug, body({ fulfillment: 'pickup', address: undefined, slotStartsAt: pslot.startsAt }), { ticket: t });
  assert.equal(pick.status, 201, JSON.stringify(pick.body));
  assert.equal(pick.body.order.deliveryFeeMinor, 0);
  assert.equal(pick.body.order.slotFeeMinor, 0);
});

test('CHK-07: هزینهٔ بازهٔ «دریافت از دکان» و ورودیِ بد رد؛ CHECK ِ ۱۳۸ پشتِ سرِ کد', async () => {
  const s = await liveStore('بازهٔ بد');
  const rule = (over) => ({ fulfillment: 'delivery', weekdays: [1], startMin: 600, endMin: 720, capacity: 2, ...over });
  const bad = [
    [rule({ fulfillment: 'pickup', feeMinor: 100 }), 'bad_slot_rule'],
    [rule({ feeMinor: -1 }), 'bad_delivery_settings'],
    [rule({ feeMinor: 100_000_001 }), 'bad_delivery_settings'],
    [rule({ feeMinor: 'ده' }), 'bad_delivery_settings'],
  ];
  for (const [r, code] of bad) {
    const out = await putDelivery(s, { slotRules: [r] });
    assert.equal(out.status, 400, JSON.stringify(r));
    assert.equal(out.body.error.code, code, JSON.stringify(out.body));
  }
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM storefront_slot_rules WHERE shop_id=$1', [s.shopId])).n, 0);
  //  «دریافت» با هزینهٔ صفرِ صریح پذیرفته است
  assert.equal((await putDelivery(s, { slotRules: [rule({ fulfillment: 'pickup', feeMinor: 0 })] })).status, 200);
  await assert.rejects(query('UPDATE storefront_slot_rules SET fee_minor=5 WHERE shop_id=$1', [s.shopId]));
});

/* ══════════════════════════════════════════════════════════════════
   ۳) ⛔ برنامهٔ پیش از ۱۳۸ پاک نمی‌کند؛ تکرارِ کلید همان مبلغ
   ══════════════════════════════════════════════════════════════════ */

test('⛔ کلیدِ نیامده (برنامهٔ کهنه) هزینهٔ سبدِ کوچک و بازه را نگه می‌دارد؛ null/۰ ِ صریح پاک می‌کند', async () => {
  const s = await liveStore('برنامهٔ کهنه');
  const r1 = { fulfillment: 'delivery', weekdays: [1, 2], startMin: 600, endMin: 720, capacity: 3 };
  const r2 = { fulfillment: 'delivery', weekdays: [3], startMin: 900, endMin: 960, capacity: 1 };
  assert.equal((await putDelivery(s, {
    zones: [zone({ smallBasketFeeMinor: 2500 }), zone({ name: 'دارالامان', smallBasketFeeMinor: 4000 })],
    slotRules: [{ ...r1, feeMinor: 1500 }, { ...r2, feeMinor: 700 }],
  })).status, 200);

  //  همان شکلی که برنامهٔ پیش از ۱۳۸ می‌فرستد: بی هیچ کلیدِ تازه، ترتیب عوض، یک ناحیه نام عوض
  const old = await putDelivery(s, {
    zones: [zone({ name: 'دارالامان' }), zone({ name: 'کارته‌سهٔ نو' })],
    slotRules: [r2, { ...r1, endMin: 750 }],
  });
  assert.equal(old.status, 200, JSON.stringify(old.body));
  assert.deepEqual(old.body.zones.map((z) => [z.name, z.smallBasketFeeMinor]), [['دارالامان', 4000], ['کارته‌سهٔ نو', null]]);
  assert.deepEqual(old.body.slotRules.map((r) => [r.weekdays.join(), r.feeMinor]), [['3', 700], ['1,2', 0]]);

  //  کلیدِ نیامده ولی حداقل صفر شده ⇒ هزینهٔ سبدِ کوچک بی‌معناست و پاک می‌شود (نه خطای CHECK)
  const noMin = await putDelivery(s, { zones: [zone({ name: 'دارالامان', minOrderMinor: 0 })] });
  assert.equal(noMin.status, 200, JSON.stringify(noMin.body));
  assert.equal(noMin.body.zones[0].smallBasketFeeMinor, null);

  //  برنامهٔ تازه: null و ۰ ِ صریح پاک می‌کنند
  assert.equal((await putDelivery(s, { zones: [zone({ smallBasketFeeMinor: 900 })], slotRules: [{ ...r2, feeMinor: 700 }] })).status, 200);
  const cleared = await putDelivery(s, { zones: [zone({ smallBasketFeeMinor: null })], slotRules: [{ ...r2, feeMinor: 0 }] });
  assert.equal(cleared.status, 200, JSON.stringify(cleared.body));
  assert.equal(cleared.body.zones[0].smallBasketFeeMinor, null);
  assert.equal(cleared.body.slotRules[0].feeMinor, 0);
});

test('تکرارِ همان کلید پس از تغییرِ هزینه ⇒ همان سفارش و همان مبلغ؛ هم‌زمان یک سفارش', async () => {
  const s = await liveStore('تکرار');
  assert.equal((await putDelivery(s, { zones: [zone({ smallBasketFeeMinor: 2500 })] })).status, 200);
  const t = await ticketFor(s.slug, PHONE);
  const key = `ck-${uniq()}-same`;
  const [a, b] = await Promise.all([checkout(s.slug, body(), { key, ticket: t }), checkout(s.slug, body(), { key, ticket: t })]);
  assert.deepEqual([a.status, b.status].sort(), [200, 201], JSON.stringify([a.body, b.body]));
  assert.equal(a.body.order.token, b.body.order.token);
  assert.equal(a.body.order.deliveryFeeMinor, 5500);

  assert.equal((await putDelivery(s, { zones: [zone({ smallBasketFeeMinor: 9000 })] })).status, 200);
  const again = await checkout(s.slug, body(), { key, ticket: t });
  assert.equal(again.status, 200, JSON.stringify(again.body));
  assert.equal(again.body.replayed, true);
  assert.equal(again.body.order.deliveryFeeMinor, 5500);
  assert.equal(again.body.order.smallBasketFeeMinor, 2500);
  assert.equal(await count(s.shopId), 1);
});
