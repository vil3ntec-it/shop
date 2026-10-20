'use strict';
/**
 * ویترین، گامِ ۲ — ناحیه، بازه، حسابِ مشتری، صفحهٔ کالا، پرفروش/تازه،
 * خریدِ دوباره و عکسِ کوچکِ بانک (CHK-05/06/07/10/12، WEB-02/08/09/12/16، ۲۳ S134/S138/S140).
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) هندسه و زمان: درون/بیرونِ چندضلعی و دایره؛ بازه به وقتِ محلیِ دکان،
 *      بی تعطیلی و پس از مهلت نه.
 *   ۲) ⛔ دکانِ بی ناحیه ⇒ همان هزینه و حداقلِ ثابتِ امروز (هیچ تغییری).
 *   ۳) ناحیه: هزینه/حداقل/«رایگان از» ِ همان ناحیه؛ پینِ بیرون ⇒ ردِ صریح و
 *      هیچ سفارشی؛ بی پین ⇒ ناحیهٔ برگزیدهٔ مشتری با نشانِ `customer`.
 *   ۴) ⛔ ظرفیتِ بازه: پر ⇒ ۴۰۹ و هیچ سفارش؛ هم‌زمان هم از ظرفیت بیرون نمی‌زند؛
 *      لغو ظرفیت را آزاد می‌کند؛ «هرچه زودتر» ِ بسته ⇒ بازه لازم.
 *   ۵) حسابِ مشتری: فقط با شمارهٔ تأییدشده؛ نشستِ لغوشدنی؛ تاریخچه و نشانی
 *      فقط مالِ خودش؛ پاک کردنِ حساب سفارشِ دکان را پاک نمی‌کند.
 *   ۶) صفحهٔ کالا بی یادداشتِ داخلی؛ پرفروش‌ها از سفارشِ واقعی؛ خریدِ دوباره
 *      با قیمتِ امروز؛ عکسِ کوچکِ بانک فقط با پرچمِ دکان.
 */
process.env.RATE_STORE_CHECKOUT_MAX = '1000';
process.env.RATE_STORE_LOCATE_DAILY_MAX = '40';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const F = require('./image-fixtures');
const { query, one, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const sfd = require('../src/lib/storefront-delivery');

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

const push = (token, ops, device = 'dev-store') => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop', 'X-Device': device },
  body: { device_id: device, schema_version: 2, ops },
});
const op = (rowId, fields) => ({ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: rowId, type: 'insert', fields });

/** دکانِ روشن با ویترین و چهار کالا (سه منتشرشده). */
async function liveStore(label, settings = {}) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { ...u, token: u.accessToken, shopId: made.body.shop.id };
  await setFlag(o.shopId, 'shop.orders', true);
  await setFlag(o.shopId, 'shop.storefront', true);
  //  ناحیه و بازه پرچمِ جدا و پیش‌فرض خاموش دارند؛ آزمون‌های این‌جا روشنش می‌کنند
  await setFlag(o.shopId, 'shop.storefront.zones', true);
  const slug = `v-${uniq()}`;
  const put = await h.put('/api/shop/storefront', {
    slug, enabled: true, title: `ویترینِ ${label}`, deliveryFeeMinor: 5000, minOrderMinor: 0, ...settings,
  }, { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const p = await push(o.token, [
    op('rice', { name: 'برنج', unit: 'کیلو', salePrice: 120.5, category: 'خوراکی', notes: 'رازِ خرید: عمده از فلانی' }),
    op('oil', { name: 'روغن', unit: 'بوتل', salePrice: 300, category: 'خوراکی' }),
    op('salt', { name: 'نمک', unit: 'بسته', salePrice: 20, category: 'خوراکی' }),
    op('soap', { name: 'صابون', unit: 'دانه', salePrice: 40, category: 'بهداشتی' }),
  ]);
  assert.equal(p.status, 200, JSON.stringify(p.body));
  for (const id of ['rice', 'oil', 'salt']) {
    const r = await h.put(`/api/shop/storefront/products/${id}`, { published: true }, { token: o.token });
    assert.equal(r.status, 200, JSON.stringify(r.body));
  }
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

const PHONE = '0791112233';
const PHONE_B = '0792223344';
const PHONE_C = '0797778899';
const INSIDE = { lat: 34.53, lng: 69.17 };
const OUTSIDE = { lat: 34.62, lng: 69.31 };
const NEAR_CENTER = { lat: 34.452, lng: 69.101 };
const POLY = [[34.50, 69.12], [34.50, 69.22], [34.56, 69.22], [34.56, 69.12]];
// برنج ۲ کیلو (۲۴۱) + روغن ۱ (۳۰۰) = ۵۴۱ افغانی
const SUBTOTAL = 54100;

function body(over = {}, phone = PHONE) {
  return {
    fulfillment: 'delivery',
    customer: { name: 'مشتریِ آزمایشی', phone },
    address: { text: 'کارته‌سه، کوچهٔ دوم', landmark: 'پشتِ مسجد', ...INSIDE },
    lines: [{ productId: 'rice', qtyMilli: 2000 }, { productId: 'oil', qtyMilli: 1000 }],
    ...over,
  };
}
const checkout = (slug, b, { key = `ck-${uniq()}-key`, ticket, session } = {}) => h.post(`/api/store/${slug}/checkout`, b, {
  headers: {
    'Idempotency-Key': key, ...(ticket ? { 'X-Store-Ticket': ticket } : {}), ...(session ? { 'X-Store-Session': session } : {}),
  },
});
const count = async (shopId) => (await one('SELECT COUNT(*)::int AS n FROM online_orders WHERE shop_id=$1', [shopId])).n;
const putDelivery = (s, b) => h.put('/api/shop/storefront/delivery', b, { token: s.token });

const ZONES = [
  { name: 'کارته‌سه', kind: 'polygon', polygon: POLY, feeMinor: 3000, minOrderMinor: 0, freeOverMinor: 100000 },
  { name: 'دارالامان', kind: 'radius', centerLat: 34.45, centerLng: 69.10, radiusM: 1500, feeMinor: 8000, minOrderMinor: 60000 },
];
const EVERY_DAY = [0, 1, 2, 3, 4, 5, 6];

/* ══════════════════════════════════════════════════════════════════
   ۱) هندسه و زمان (توابعِ خالص)
   ══════════════════════════════════════════════════════════════════ */

test('هندسه: درون/بیرونِ چندضلعی و دایره؛ نخستین ناحیهٔ فعال برنده است', () => {
  assert.equal(sfd.inPolygon(INSIDE.lat, INSIDE.lng, POLY), true);
  assert.equal(sfd.inPolygon(OUTSIDE.lat, OUTSIDE.lng, POLY), false);
  //  چندضلعیِ مقعر (L): گوشهٔ خالی بیرون است
  const L = [[0, 0], [0, 4], [2, 4], [2, 2], [4, 2], [4, 0]];
  assert.equal(sfd.inPolygon(1, 1, L), true);
  assert.equal(sfd.inPolygon(3, 3, L), false);
  const d = sfd.distanceM(34.45, 69.10, NEAR_CENTER.lat, NEAR_CENTER.lng);
  assert.ok(d > 200 && d < 300, `فاصله ${d}`);
  const zones = [
    { id: 'A', active: false, kind: 'polygon', polygon: POLY },
    { id: 'B', active: true, kind: 'radius', centerLat: 34.53, centerLng: 69.17, radiusM: 500 },
    { id: 'C', active: true, kind: 'polygon', polygon: POLY },
  ];
  assert.equal(sfd.zoneAt(zones, INSIDE.lat, INSIDE.lng).id, 'B', 'ناحیهٔ خاموش نادیده؛ ترتیبِ مالک');
  assert.equal(sfd.zoneAt(zones, OUTSIDE.lat, OUTSIDE.lng), null);
});

test('زمان: بازه به وقتِ محلیِ دکان (کابل ‎+04:30‎ و برلین با ساعتِ تابستانی)', () => {
  assert.equal(new Date(sfd.epochOf('2026-10-20', 9 * 60, 'Asia/Kabul')).toISOString(), '2026-10-20T04:30:00.000Z');
  assert.equal(new Date(sfd.epochOf('2026-10-20', 1440, 'Asia/Kabul')).toISOString(), '2026-10-20T19:30:00.000Z');
  //  روزِ تغییرِ ساعت (۲۵ اکتبر ۲۰۲۶ در اروپا): ۱۰ صبح پس از تغییر ‎+01:00‎ است
  assert.equal(new Date(sfd.epochOf('2026-10-25', 10 * 60, 'Europe/Berlin')).toISOString(), '2026-10-25T09:00:00.000Z');
  assert.equal(new Date(sfd.epochOf('2026-07-01', 10 * 60, 'Europe/Berlin')).toISOString(), '2026-07-01T08:00:00.000Z');
});

test('بازه‌ها: فقط روزهای قاعده، بی تعطیلی، و نه پس از مهلت', () => {
  //  «اکنون» = دوشنبه ۱۹ اکتبر ۲۰۲۶، ۰۹:۰۰ کابل
  const at = sfd.epochOf('2026-10-19', 9 * 60, 'Asia/Kabul');
  const sf = { shop_id: 'x', timezone: 'Asia/Kabul', closed_dates: ['2026-10-20'], slot_days: 4 };
  const rules = [
    { id: 'r1', active: true, fulfillment: 'delivery', weekdays: EVERY_DAY, startMin: 600, endMin: 720, capacity: 2, cutoffMin: 90 },
    { id: 'r2', active: true, fulfillment: 'delivery', weekdays: [3], startMin: 960, endMin: 1080, capacity: 5, cutoffMin: 0 },
    { id: 'r3', active: true, fulfillment: 'pickup', weekdays: EVERY_DAY, startMin: 600, endMin: 660, capacity: 1, cutoffMin: 0 },
    { id: 'r4', active: false, fulfillment: 'delivery', weekdays: EVERY_DAY, startMin: 1200, endMin: 1260, capacity: 1, cutoffMin: 0 },
  ];
  const slots = sfd.slotsOf(sf, rules, 'delivery', at);
  const keys = slots.map((s) => `${s.date}@${s.startMin}`);
  //  امروز ۱۰:۰۰ با مهلتِ ۹۰ دقیقه (۰۸:۳۰) گذشته؛ ۲۰ اکتبر تعطیل؛ چهارشنبه (۳) بازهٔ دوم هم دارد؛ قاعدهٔ خاموش نه
  assert.deepEqual(keys, ['2026-10-21@600', '2026-10-21@960', '2026-10-22@600']);
  assert.equal(slots[0].endsAt - slots[0].startsAt, 2 * 60 * 60 * 1000);
  assert.deepEqual(sfd.slotsOf(sf, rules, 'pickup', at).map((s) => s.date), ['2026-10-19', '2026-10-21', '2026-10-22']);
});

/* ══════════════════════════════════════════════════════════════════
   ۲) تنظیمِ مالک
   ══════════════════════════════════════════════════════════════════ */

test('تنظیمِ ناحیه و بازه: ورودیِ بد رد، هم‌پوشانی رد، جایگزینیِ اتمی، شاگرد نه، پرچمِ خاموش نه', async () => {
  const s = await liveStore('تنظیمِ ارسال');
  const bad = [
    [{ zones: [{ name: 'x', kind: 'polygon', polygon: [[1, 1], [2, 2]] }] }, 'bad_zone'],
    [{ zones: [{ name: 'خط', kind: 'polygon', polygon: [[1, 1], [2, 2], [3, 3]] }] }, 'bad_zone'],
    [{ zones: [{ name: 'x', kind: 'radius', centerLat: 34, centerLng: 69, radiusM: 10 }] }, 'bad_delivery_settings'],
    [{ zones: [{ name: '', kind: 'radius', centerLat: 34, centerLng: 69, radiusM: 1000 }] }, 'bad_zone'],
    [{ zones: [{ name: 'x', kind: 'circle' }] }, 'bad_zone'],
    [{ slotRules: [{ fulfillment: 'delivery', weekdays: [1], startMin: 600, endMin: 600, capacity: 1 }] }, 'bad_slot_rule'],
    [{ slotRules: [{ fulfillment: 'boat', weekdays: [1], startMin: 600, endMin: 700, capacity: 1 }] }, 'bad_slot_rule'],
    [{ slotRules: [
      { fulfillment: 'delivery', weekdays: [1, 2], startMin: 600, endMin: 720, capacity: 1 },
      { fulfillment: 'delivery', weekdays: [2], startMin: 700, endMin: 800, capacity: 1 },
    ] }, 'slot_overlap'],
    [{ slotDays: 30 }, 'bad_delivery_settings'],
  ];
  for (const [b, code] of bad) {
    const r = await putDelivery(s, b);
    assert.equal(r.status, 400, JSON.stringify(b));
    assert.equal(r.body.error.code, code, JSON.stringify(r.body));
  }
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM storefront_zones WHERE shop_id=$1', [s.shopId])).n, 0);

  const ok = await putDelivery(s, {
    zones: ZONES, allowAsap: false, slotDays: 5,
    slotRules: [
      { fulfillment: 'delivery', weekdays: [1, 2], startMin: 600, endMin: 720, capacity: 3 },
      { fulfillment: 'pickup', weekdays: [2], startMin: 700, endMin: 800, capacity: 1 },
    ],
  });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.equal(ok.body.zones.length, 2);
  assert.deepEqual(ok.body.zones.map((z) => z.name), ['کارته‌سه', 'دارالامان']);
  assert.deepEqual(ok.body.slotRules[0].weekdays, [1, 2]);
  assert.equal(ok.body.allowAsap, false);
  assert.equal(ok.body.slotDays, 5);
  //  فهرستِ نیامده دست نمی‌خورد؛ فهرستِ آمده کامل جایگزین
  const z1 = await putDelivery(s, { zones: [ZONES[1]] });
  assert.equal(z1.body.zones.length, 1);
  assert.equal(z1.body.slotRules.length, 2);
  const got = await h.get('/api/shop/storefront/delivery', { token: s.token });
  assert.equal(got.status, 200);
  assert.equal(got.body.zones[0].kind, 'radius');

  //  شاگرد (بی settings.write) نمی‌تواند
  const staff = await h.newUser('شاگردِ ارسال', 'shop');
  await query(`INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
               VALUES ($1,$2,$3,'staff','active',$4,$4)`, [newId('mem'), s.shopId, staff.user.id, now()]);
  assert.equal((await h.put('/api/shop/storefront/delivery', { zones: [] }, { token: staff.accessToken })).status, 403);
  //  پرچمِ خاموش ⇒ ۴۰۳
  await setFlag(s.shopId, 'shop.storefront', false);
  const off = await putDelivery(s, { zones: [] });
  assert.equal(off.status, 403);
  assert.equal(off.body.error.code, 'storefront_disabled');
  await setFlag(s.shopId, 'shop.storefront', true);
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM storefront_zones WHERE shop_id=$1', [s.shopId])).n, 1);
});

/* ══════════════════════════════════════════════════════════════════
   ۳) ناحیه و هزینهٔ ارسال
   ══════════════════════════════════════════════════════════════════ */

test('⛔ پرچمِ `shop.storefront.zones` خاموش ⇒ رفتارِ امروز؛ ناحیه‌های ذخیره‌شده پاک نمی‌شوند', async () => {
  const s = await liveStore('پرچمِ ناحیه');
  assert.equal((await putDelivery(s, {
    zones: ZONES, allowAsap: false,
    slotRules: [{ fulfillment: 'pickup', weekdays: EVERY_DAY, startMin: 600, endMin: 720, capacity: 1 }],
  })).status, 200);
  await setFlag(s.shopId, 'shop.storefront.zones', false);
  const t = await ticketFor(s.slug, PHONE);
  const cfg = await h.get('/api/shop/storefront/config', { token: s.token });
  assert.equal(cfg.body.zones, false);
  const off = await putDelivery(s, { zones: [] });
  assert.equal(off.status, 403);
  assert.equal(off.body.error.code, 'zones_disabled');
  assert.equal((await h.get('/api/shop/storefront/delivery', { token: s.token })).status, 403);
  const prof = await h.get(`/api/store/${s.slug}`);
  assert.deepEqual(prof.body.store.delivery, { zoned: false, zones: [], allowAsap: true, scheduling: { delivery: false, pickup: false } });
  assert.equal((await h.get(`/api/store/${s.slug}/delivery?lat=${OUTSIDE.lat}&lng=${OUTSIDE.lng}`)).body.inZone, true);
  assert.equal((await h.get(`/api/store/${s.slug}/slots?fulfillment=pickup`)).body.scheduling, false);
  //  پینِ بیرون پذیرفته می‌شود و هزینه همان ثابتِ امروز است؛ «هرچه زودتر» هم
  const far = await checkout(s.slug, body({ address: { text: 'هر جا', ...OUTSIDE }, expectedTotalMinor: SUBTOTAL + 5000 }), { ticket: t });
  assert.equal(far.status, 201, JSON.stringify(far.body));
  assert.equal(far.body.order.deliveryFeeMinor, 5000);
  assert.equal(far.body.order.zoneName, '');
  const asap = await checkout(s.slug, body({ fulfillment: 'pickup', address: undefined }), { ticket: t });
  assert.equal(asap.status, 201, JSON.stringify(asap.body));
  assert.equal((await checkout(s.slug, body({ fulfillment: 'pickup', address: undefined, slotStartsAt: Date.now() + 86400000 }), { ticket: t })).body.error.code, 'slot_unavailable');
  //  روشن کردنِ دوباره ⇒ همان ناحیه‌ها
  await setFlag(s.shopId, 'shop.storefront.zones', true);
  assert.equal((await h.get(`/api/store/${s.slug}`)).body.store.delivery.zones.length, 2);
  assert.equal((await h.get('/api/shop/storefront/config', { token: s.token })).body.zones, true);
});

test('بی ناحیه ⇒ همان هزینهٔ ثابتِ امروز، پین اختیاری (رفتارِ پیشین)', async () => {
  const s = await liveStore('بی‌ناحیه');
  const t = await ticketFor(s.slug, PHONE);
  const prof = await h.get(`/api/store/${s.slug}`);
  assert.equal(prof.body.store.delivery.zoned, false);
  const loc = await h.get(`/api/store/${s.slug}/delivery?lat=${OUTSIDE.lat}&lng=${OUTSIDE.lng}`);
  assert.deepEqual([loc.body.zoned, loc.body.inZone], [false, true]);
  const r = await checkout(s.slug, body({ address: { text: 'نشانی بی پین' } }), { ticket: t });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.equal(r.body.order.deliveryFeeMinor, 5000);
  assert.equal(r.body.order.totalMinor, SUBTOTAL + 5000);
  assert.equal(r.body.order.zoneName, '');
  assert.equal(r.body.order.scheduledFor, null);
});

test('CHK-07/10: هزینهٔ ناحیه، «رایگان از…»، حداقلِ ناحیه، پینِ بیرون ⇒ رد و هیچ سفارش', async () => {
  const s = await liveStore('ناحیه‌دار');
  assert.equal((await putDelivery(s, { zones: ZONES })).status, 200);
  const t = await ticketFor(s.slug, PHONE);

  const prof = await h.get(`/api/store/${s.slug}`);
  assert.equal(prof.body.store.delivery.zoned, true);
  assert.deepEqual(prof.body.store.delivery.zones.map((z) => [z.name, z.feeMinor]), [['کارته‌سه', 3000], ['دارالامان', 8000]]);
  assert.ok(!JSON.stringify(prof.body.store.delivery).includes('69.12'), 'مختصاتِ ناحیه عمومی نیست');

  const inside = await h.get(`/api/store/${s.slug}/delivery?lat=${INSIDE.lat}&lng=${INSIDE.lng}`);
  assert.equal(inside.body.inZone, true);
  assert.equal(inside.body.zone.name, 'کارته‌سه');
  const out = await h.get(`/api/store/${s.slug}/delivery?lat=${OUTSIDE.lat}&lng=${OUTSIDE.lng}`);
  assert.deepEqual([out.body.inZone, out.body.zone, out.body.pickup], [false, null, true]);
  assert.equal((await h.get(`/api/store/${s.slug}/delivery?lat=abc&lng=1`)).status, 400);

  //  داخلِ چندضلعی: هزینهٔ همان ناحیه، و مبلغِ دیده‌شده باید با آن بخواند
  const a = await checkout(s.slug, body({ expectedTotalMinor: SUBTOTAL + 3000 }), { ticket: t });
  assert.equal(a.status, 201, JSON.stringify(a.body));
  assert.equal(a.body.order.deliveryFeeMinor, 3000);
  assert.equal(a.body.order.zoneName, 'کارته‌سه');
  const row = await one('SELECT address FROM online_orders WHERE shop_id=$1 ORDER BY created_at DESC LIMIT 1', [s.shopId]);
  const addr = typeof row.address === 'string' ? JSON.parse(row.address) : row.address;
  assert.equal(addr.zone.source, 'pin');

  //  «رایگان از ۱۰۰۰»: ۴ برنج + ۲ روغن = ۱۰۸۲ ⇒ هزینه ۰
  const big = await checkout(s.slug, body({ lines: [{ productId: 'rice', qtyMilli: 4000 }, { productId: 'oil', qtyMilli: 2000 }] }), { ticket: t });
  assert.equal(big.status, 201, JSON.stringify(big.body));
  assert.equal(big.body.order.deliveryFeeMinor, 0);

  //  دایره با حداقلِ ۶۰۰: سبدِ ۵۴۱ کم است — حداقلِ همان ناحیه برمی‌گردد
  const n0 = await count(s.shopId);
  const low = await checkout(s.slug, body({ address: { text: 'دارالامان', ...NEAR_CENTER } }), { ticket: t });
  assert.equal(low.status, 400, JSON.stringify(low.body));
  assert.equal(low.body.error.code, 'below_min_order');
  assert.equal(low.body.error.minOrderMinor, 60000);
  assert.equal(low.body.error.quote.deliveryFeeMinor, 8000);

  //  بیرون از همهٔ ناحیه‌ها ⇒ رد، با پیشنهادِ «دریافت از دکان»
  const far = await checkout(s.slug, body({ address: { text: 'بیرون', ...OUTSIDE } }), { ticket: t });
  assert.equal(far.status, 400, JSON.stringify(far.body));
  assert.equal(far.body.error.code, 'out_of_zone');
  assert.equal(far.body.error.pickup, true);
  assert.equal(far.body.error.zones.length, 2);
  //  جمعِ دیده‌شدهٔ قدیمی (هزینهٔ ثابت) ⇒ price_changed، نه ثبتِ بی‌صدا
  const stale = await checkout(s.slug, body({ expectedTotalMinor: SUBTOTAL + 5000 }), { ticket: t });
  assert.equal(stale.status, 409);
  assert.equal(stale.body.error.code, 'price_changed');
  assert.equal(stale.body.error.quote.deliveryFeeMinor, 3000);
  assert.equal(await count(s.shopId), n0, 'هیچ سفارشی از ردها ساخته نشد');

  //  بی پین: ناحیهٔ برگزیدهٔ مشتری؛ بی هیچ ⇒ zone_required
  const none = await checkout(s.slug, body({ address: { text: 'نشانیِ بی پین' } }), { ticket: t });
  assert.equal(none.body.error.code, 'zone_required');
  assert.equal(none.body.error.zones.length, 2);
  const zoneId = prof.body.store.delivery.zones[0].id;
  const picked = await checkout(s.slug, body({ address: { text: 'نشانیِ بی پین', zoneId } }), { ticket: t });
  assert.equal(picked.status, 201, JSON.stringify(picked.body));
  assert.equal(picked.body.order.deliveryFeeMinor, 3000);
  const r2 = await one('SELECT address FROM online_orders WHERE shop_id=$1 ORDER BY created_at DESC LIMIT 1', [s.shopId]);
  assert.equal((typeof r2.address === 'string' ? JSON.parse(r2.address) : r2.address).zone.source, 'customer');
  assert.equal((await checkout(s.slug, body({ address: { text: 'x', zoneId: 'NOTAZONE' } }), { ticket: t })).body.error.code, 'bad_zone');

  //  دریافت از دکان: بی ناحیه و بی هزینه
  const pick = await checkout(s.slug, body({ fulfillment: 'pickup', address: undefined }), { ticket: t });
  assert.equal(pick.status, 201, JSON.stringify(pick.body));
  assert.equal(pick.body.order.deliveryFeeMinor, 0);
});

test('⛔ حریمِ جای دکان: کاوشِ دودویی با «درون/بیرون» از خانهٔ ۳۰۰ متریِ شبکه دقیق‌تر نمی‌شود؛ ثبت همان پاسخ؛ سقفِ روزانه', async () => {
  //  بازبینیِ امنیتی: با ۱۲۱ درخواستِ بی‌توکن مرکزِ دایره (= جای دکان) با خطای صفر متر پیدا شد
  assert.ok(sfd.GRID_M >= 300);
  const s = await liveStore('کاوش');
  const t = await ticketFor(s.slug, PHONE);
  const C = { lat: 34.4512, lng: 69.1034 };
  //  لبهٔ شمالیِ دایره درست وسطِ یک ردیفِ شبکه ⇒ هر کاوشِ «خانه‌ای» دست‌کم نیم‌خانه خطا دارد
  const edgeLat = sfd.snapPin(C.lat + 1500 / sfd.M_PER_DEG_LAT, C.lng).lat;
  const R = Math.round(sfd.distanceM(C.lat, C.lng, edgeLat, C.lng));
  const zone = { name: 'دایره', kind: 'radius', centerLat: C.lat, centerLng: C.lng, radiusM: R, feeMinor: 1000, minOrderMinor: 0 };
  assert.equal((await putDelivery(s, { zones: [zone] })).status, 200);

  const inZone = async (lat, lng = C.lng) => {
    const r = await h.get(`/api/store/${s.slug}/delivery?lat=${lat}&lng=${lng}`);
    assert.equal(r.status, 200, JSON.stringify(r.body));
    assert.deepEqual(Object.keys(r.body).sort(), ['inZone', 'pickup', 'zone', 'zoned'], 'نه فاصله، نه لبه');
    return r.body.inZone;
  };
  let lo = C.lat;
  let hi = C.lat + 0.05;
  assert.equal(await inZone(lo), true);
  assert.equal(await inZone(hi), false);
  for (let i = 0; i < 26; i++) {
    const mid = (lo + hi) / 2;
    if (await inZone(mid)) lo = mid; else hi = mid;
  }
  const est = (lo + hi) / 2;
  const err = Math.abs(sfd.distanceM(C.lat, C.lng, est, C.lng) - R);
  assert.ok(err >= sfd.GRID_M / 2 - 10, `کاوش لبه را تا ${err.toFixed(2)} متر پیدا کرد`);
  //  آن‌چه کاوش پیدا می‌کند خطِ شبکه است، نه لبهٔ ناحیه
  const rowM = (lat) => lat * sfd.M_PER_DEG_LAT;
  const toGrid = Math.abs(rowM(est) - Math.round(rowM(est) / sfd.GRID_M) * sfd.GRID_M);
  assert.ok(toGrid < 1, `فاصلهٔ برآورد تا خطِ شبکه ${toGrid.toFixed(2)} متر`);
  //  هر دو نقطهٔ یک خانه یک پاسخ دارند (هندسهٔ خالص)
  const zs = [{ ...zone, id: 'Z', active: true }];
  for (let i = 0; i < 200; i++) {
    const p = { lat: edgeLat + (Math.random() - 0.5) * 0.004, lng: C.lng + (Math.random() - 0.5) * 0.004 };
    const c = sfd.snapPin(p.lat, p.lng);
    assert.equal(Boolean(sfd.zoneForPin(zs, p.lat, p.lng)), Boolean(sfd.zoneAt(zs, c.lat, c.lng)));
    const q = sfd.snapPin(c.lat + 0.3 * (p.lat - c.lat), c.lng - 0.3 * (p.lng - c.lng));
    assert.deepEqual(q, c, 'نقطهٔ دیگرِ همان خانه همان مرکز را دارد');
  }

  //  ثبت با همان سنجشِ خانه‌ای: پینِ ۴۰ متر درونِ لبهٔ واقعی، در خانه‌ای که مرکزش بیرون است
  const pin = { lat: edgeLat - 40 / sfd.M_PER_DEG_LAT, lng: C.lng };
  assert.ok(sfd.distanceM(C.lat, C.lng, pin.lat, pin.lng) < R, 'پین واقعاً درونِ دایره است');
  const said = await inZone(pin.lat, pin.lng);
  const r = await checkout(s.slug, body({ address: { text: 'لبه', ...pin } }), { ticket: t });
  assert.equal(r.status === 201, said, `ویترین گفت ${said}، ثبت ${r.status} ${JSON.stringify(r.body)}`);
  if (!said) assert.equal(r.body.error.code, 'out_of_zone');

  //  سقفِ روزانهٔ هر (IP، ویترین) — و پاک‌سازیِ ربع‌ساعته آن را صفر نمی‌کند
  const max = Number(process.env.RATE_STORE_LOCATE_DAILY_MAX);
  let used = 2 + 26 + 1;
  while (used < max) { await inZone(C.lat); used++; }
  const over = await h.get(`/api/store/${s.slug}/delivery?lat=${C.lat}&lng=${C.lng}`);
  assert.equal(over.status, 429);
  require('../src/middleware/ratelimit').sweep(Date.now() + 20 * 60 * 1000);
  assert.equal((await h.get(`/api/store/${s.slug}/delivery?lat=${C.lat}&lng=${C.lng}`)).status, 429, 'سقفِ روزانه پس از ربع ساعت باز نمی‌شود');
  //  ویترینِ دیگر سطلِ خودش را دارد
  const other = await liveStore('کاوشِ دوم');
  assert.equal((await h.get(`/api/store/${other.slug}/delivery?lat=${C.lat}&lng=${C.lng}`)).status, 200);
});

/* ══════════════════════════════════════════════════════════════════
   ۴) بازهٔ زمان‌بندی و ظرفیت
   ══════════════════════════════════════════════════════════════════ */

test('CHK-12: ظرفیتِ بازه — پر ⇒ ۴۰۹ و هیچ سفارش؛ هم‌زمان بیرون نمی‌زند؛ لغو آزاد می‌کند', async () => {
  const s = await liveStore('بازه‌دار');
  const set = await putDelivery(s, {
    slotDays: 3,
    slotRules: [{ fulfillment: 'pickup', weekdays: EVERY_DAY, startMin: 0, endMin: 1440, capacity: 1, cutoffMin: 0 }],
  });
  assert.equal(set.status, 200, JSON.stringify(set.body));
  const t = await ticketFor(s.slug, PHONE);
  const t2 = await ticketFor(s.slug, PHONE_B);

  const list = await h.get(`/api/store/${s.slug}/slots?fulfillment=pickup`);
  assert.equal(list.status, 200, JSON.stringify(list.body));
  assert.equal(list.body.scheduling, true);
  assert.equal(list.body.allowAsap, true);
  assert.ok(list.body.slots.length >= 2, JSON.stringify(list.body));
  assert.ok(!('remaining' in list.body.slots[0]) && !('booked' in list.body.slots[0]), 'عددِ سفارش‌ها به مشتری گفته نمی‌شود');
  const [s1, s2] = list.body.slots;
  assert.equal((await h.get(`/api/store/${s.slug}/slots?fulfillment=delivery`)).body.slots.length, 0);

  const pickup = (phone, slotStartsAt) => body({ fulfillment: 'pickup', address: undefined, slotStartsAt }, phone);
  const a = await checkout(s.slug, pickup(PHONE, s1.startsAt), { ticket: t });
  assert.equal(a.status, 201, JSON.stringify(a.body));
  assert.equal(a.body.order.scheduledFor, s1.startsAt);
  assert.equal(a.body.order.slotEndsAt, s1.endsAt);

  const n0 = await count(s.shopId);
  const full = await checkout(s.slug, pickup(PHONE_B, s1.startsAt), { ticket: t2 });
  assert.equal(full.status, 409, JSON.stringify(full.body));
  assert.equal(full.body.error.code, 'slot_full');
  assert.equal(full.body.error.slots.find((x) => x.startsAt === s1.startsAt).available, false);
  assert.equal(await count(s.shopId), n0);
  const after = await h.get(`/api/store/${s.slug}/slots?fulfillment=pickup`);
  assert.equal(after.body.slots.find((x) => x.startsAt === s1.startsAt).available, false);

  //  بازهٔ ساختگی ⇒ slot_unavailable
  const fake = await checkout(s.slug, pickup(PHONE_B, s2.startsAt + 60000), { ticket: t2 });
  assert.equal(fake.status, 409);
  assert.equal(fake.body.error.code, 'slot_unavailable');

  //  دو درخواستِ هم‌زمان برای آخرین جای بازهٔ دوم ⇒ فقط یکی
  const both = await Promise.all([
    checkout(s.slug, pickup(PHONE, s2.startsAt), { ticket: t }),
    checkout(s.slug, pickup(PHONE_B, s2.startsAt), { ticket: t2 }),
  ]);
  assert.deepEqual(both.map((r) => r.status).sort(), [201, 409], JSON.stringify(both.map((r) => r.body)));
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM online_orders WHERE shop_id=$1 AND scheduled_for=$2',
    [s.shopId, s2.startsAt])).n, 1);

  //  لغوِ مشتری ⇒ جا آزاد
  const cancel = await h.post(`/api/store/${s.slug}/orders/${a.body.order.token}/cancel`, {});
  assert.equal(cancel.status, 200, JSON.stringify(cancel.body));
  const again = await checkout(s.slug, pickup(PHONE_B, s1.startsAt), { ticket: t2 });
  assert.equal(again.status, 201, JSON.stringify(again.body));

  //  «هرچه زودتر» بسته ⇒ برای این روش بازه لازم است؛ برای روشِ بی‌بازه نه
  assert.equal((await putDelivery(s, { allowAsap: false })).status, 200);
  const asap = await checkout(s.slug, pickup(PHONE, null), { ticket: t });
  assert.equal(asap.status, 400);
  assert.equal(asap.body.error.code, 'slot_required');
  assert.ok(Array.isArray(asap.body.error.slots));
  const del = await checkout(s.slug, body(), { ticket: t });
  assert.equal(del.status, 201, JSON.stringify(del.body));
  assert.equal((await checkout(s.slug, body({ slotStartsAt: s1.startsAt }), { ticket: t })).body.error.code, 'slot_unavailable');
});

/* ══════════════════════════════════════════════════════════════════
   ۵) حسابِ مشتری (۲۳، S134/S138/S140)
   ══════════════════════════════════════════════════════════════════ */

test('حساب: فقط با شمارهٔ تأییدشده؛ نشست جای بلیت؛ شمارهٔ دیگر نه؛ خروج و انقضا', async () => {
  const s = await liveStore('حساب');
  const no = await h.post(`/api/store/${s.slug}/account`, {});
  assert.equal(no.status, 401);
  assert.equal(no.body.error.code, 'verification_required');
  assert.equal((await h.post(`/api/store/${s.slug}/account`, {}, { headers: { 'X-Store-Ticket': 'v1.forged.1.x' } })).status, 401);

  const t = await ticketFor(s.slug, PHONE);
  const made = await h.post(`/api/store/${s.slug}/account`, { name: 'زهره' }, { headers: { 'X-Store-Ticket': t } });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const S1 = made.body.session;
  assert.match(S1, /^sc1\.[A-Za-z0-9_-]{43}$/);
  assert.equal(made.body.customer.phone, '+93791112233');
  assert.equal(made.body.customer.name, 'زهره');
  const stored = await one('SELECT token_hash FROM store_customer_sessions WHERE customer_id=$1', [made.body.customer.id]);
  assert.notEqual(stored.token_hash, S1, 'توکن خام ذخیره نمی‌شود');
  //  ورودِ دوباره همان حساب است و نامِ قبلی را عوض نمی‌کند
  const again = await h.post(`/api/store/${s.slug}/account`, { name: 'دیگری' }, { headers: { 'X-Store-Ticket': t } });
  assert.equal(again.body.customer.id, made.body.customer.id);
  assert.equal(again.body.customer.name, 'زهره');

  const auth = { headers: { 'X-Store-Session': S1 } };
  const me = await h.get(`/api/store/${s.slug}/account`, auth);
  assert.equal(me.status, 200, JSON.stringify(me.body));
  assert.deepEqual(me.body.addresses, []);
  assert.equal((await h.get(`/api/store/${s.slug}/account`)).status, 401);

  //  S134: نشست جای بلیت؛ سفارش به همان شماره
  const o = await checkout(s.slug, body({ fulfillment: 'pickup', address: undefined }), { session: S1 });
  assert.equal(o.status, 201, JSON.stringify(o.body));
  //  نشستِ شمارهٔ الف برای شمارهٔ ب سفارش نمی‌سازد
  const other = await checkout(s.slug, body({ fulfillment: 'pickup', address: undefined }, PHONE_B), { session: S1 });
  assert.equal(other.status, 403);
  assert.equal(other.body.error.code, 'phone_not_verified');

  //  خروج ⇒ همان نشست دیگر کار نمی‌کند؛ نشستِ دوم سالم می‌ماند
  const S2 = again.body.session;
  assert.equal((await h.post(`/api/store/${s.slug}/account/logout`, {}, auth)).body.signedOut, true);
  assert.equal((await h.get(`/api/store/${s.slug}/account`, auth)).status, 401);
  assert.equal((await checkout(s.slug, body({ fulfillment: 'pickup', address: undefined }), { session: S1 })).status, 401);
  assert.equal((await h.get(`/api/store/${s.slug}/account`, { headers: { 'X-Store-Session': S2 } })).status, 200);
  //  منقضی ⇒ ۴۰۱
  await query('UPDATE store_customer_sessions SET expires_at = $2 WHERE customer_id = $1', [made.body.customer.id, now() - 1]);
  assert.equal((await h.get(`/api/store/${s.slug}/account`, { headers: { 'X-Store-Session': S2 } })).status, 401);
  //  مسدود ⇒ ورود نه
  await query(`UPDATE store_customers SET status='blocked' WHERE id=$1`, [made.body.customer.id]);
  const blocked = await h.post(`/api/store/${s.slug}/account`, {}, { headers: { 'X-Store-Ticket': t } });
  assert.equal(blocked.status, 403);
  assert.equal(blocked.body.error.code, 'customer_blocked');
  //  حساب سراسری است (به شماره)؛ برای آزمون‌های بعد برمی‌گردد
  await query(`UPDATE store_customers SET status='active' WHERE id=$1`, [made.body.customer.id]);
});

test('⛔ بلیت و نشست: حساب فقط با بلیتِ چند دقیقهٔ اخیر؛ «خروج از همه‌جا» و پاک کردنِ حساب همهٔ بلیت‌ها و نشست‌ها را می‌بندند', async () => {
  //  بازبینیِ امنیتی: هر بلیتِ ۱۴ روزه بی‌نهایت نشستِ ۱۸۰ روزه می‌ساخت و خروج/پاک کردن بلیت را باطل نمی‌کرد
  const sf = require('../src/lib/storefront');
  const s = await liveStore('بلیت');
  const LOCAL = '0795556677';
  const PH = '+93795556677';
  const pickup = () => body({ fulfillment: 'pickup', address: undefined }, LOCAL);
  const fresh = async () => {
    const a = await h.post(`/api/store/${s.slug}/verify/start`, { phone: LOCAL });
    const c = await h.post(`/api/store/${s.slug}/verify/check`, { phone: LOCAL, code: a.body.devCode });
    assert.equal(c.status, 200, JSON.stringify(c.body));
    return c.body.ticket;
  };
  const signIn = (ticket) => h.post(`/api/store/${s.slug}/account`, {}, { headers: { 'X-Store-Ticket': ticket } });
  const as = (session) => ({ headers: { 'X-Store-Session': session } });

  //  ۱) بلیتِ امضاشده و نامنقضی ولی ۱۱ دقیقه پیش صادرشده ⇒ حساب نه؛ ثبت هنوز آری
  const stale = sf.ticketOf(PH, Date.now() - 11 * 60 * 1000).ticket;
  const no = await signIn(stale);
  assert.equal(no.status, 401, 'بلیتِ کهنه نشستِ ۱۸۰ روزه نمی‌سازد');
  assert.equal(no.body.error.code, 'verification_required');
  assert.equal((await checkout(s.slug, pickup(), { ticket: stale })).status, 201, 'ثبت با مهلتِ کاملِ بلیت');

  //  ۲) «خروج از همه‌جا»: هر دو نشست و هر بلیتِ پیشین (تازه و کهنه) بسته می‌شوند
  const t1 = await fresh();
  const S1 = (await signIn(t1)).body.session;
  const S2 = (await signIn(t1)).body.session;
  assert.ok(S1 && S2 && S1 !== S2);
  assert.equal((await h.post(`/api/store/${s.slug}/account/logout-all`, {})).status, 401, 'بی نشست نه');
  const all = await h.post(`/api/store/${s.slug}/account/logout-all`, {}, as(S1));
  assert.equal(all.status, 200, JSON.stringify(all.body));
  assert.deepEqual(all.body, { signedOut: 2, ticketsRevoked: true });
  for (const S of [S1, S2]) assert.equal((await h.get(`/api/store/${s.slug}/account`, as(S))).status, 401);
  for (const t of [t1, stale]) {
    assert.equal((await signIn(t)).status, 401);
    assert.equal((await checkout(s.slug, pickup(), { ticket: t })).status, 401);
    assert.equal((await h.get(`/api/store/${s.slug}/orders`, { headers: { 'X-Store-Ticket': t } })).status, 401);
  }
  //  بلیتِ v1 (پیش از نسل) هم پس از لغو بسته است
  const crypto = require('crypto');
  const config = require('../src/config');
  const key = crypto.createHash('sha256').update(`storefront-order-token\n${config.secrets.api || config.secrets.jwt}`).digest();
  const exp = Date.now() + 86_400_000;
  const v1 = `v1.${Buffer.from(PH).toString('base64url')}.${exp.toString(36)}.${crypto.createHmac('sha256', key).update(`customer-phone\n${PH}\n${exp}`).digest('base64url').slice(0, 32)}`;
  assert.equal((await checkout(s.slug, pickup(), { ticket: v1 })).status, 401, 'v1 = نسلِ صفر');
  const v1Other = (ph) => {
    const e = Date.now() + 86_400_000;
    return `v1.${Buffer.from(ph).toString('base64url')}.${e.toString(36)}.${crypto.createHmac('sha256', key).update(`customer-phone\n${ph}\n${e}`).digest('base64url').slice(0, 32)}`;
  };
  assert.equal((await checkout(s.slug, body({ fulfillment: 'pickup', address: undefined }, '0796667788'), { ticket: v1Other('+93796667788') })).status, 201,
    'بلیتِ v1 ِ شمارهٔ هرگز لغونشده هنوز کار می‌کند');

  //  ۳) کدِ تازه ⇒ بلیتِ نسلِ تازه و حساب دوباره
  const t2 = await fresh();
  const S3 = (await signIn(t2)).body.session;
  assert.ok(S3);
  assert.equal((await checkout(s.slug, pickup(), { ticket: t2 })).status, 201);

  //  ۴) پاک کردنِ حساب ⇒ بلیتِ همین الانِ آن شماره هم بسته؛ شمارهٔ خام در جدولِ نسل نیست
  assert.equal((await h.del(`/api/store/${s.slug}/account`, as(S3))).status, 200);
  assert.equal((await checkout(s.slug, pickup(), { ticket: t2 })).status, 401);
  assert.equal((await signIn(t2)).status, 401);
  const rows = await query('SELECT phone_key, gen FROM store_phone_revocations');
  assert.ok(rows.rows.some((r) => Number(r.gen) === 2), JSON.stringify(rows.rows));
  assert.ok(!JSON.stringify(rows.rows).includes('795556677'), 'شمارهٔ خام ذخیره نمی‌شود');
  //  بلیتِ دست‌خورده (نسلِ دیگر با همان امضا) رد
  const forged = t2.replace(/\.([0-9a-z]{1,7})\.([A-Za-z0-9_-]{32})$/, '.2.$2');
  assert.equal((await checkout(s.slug, pickup(), { ticket: forged })).status, 401);
});

test('S138: تاریخچهٔ حساب فقط سفارش‌های همین شماره در همین ویترین؛ صفحه‌بندی', async () => {
  const s = await liveStore('تاریخچه');
  const other = await liveStore('دکانِ دیگر');
  const tA = await ticketFor(s.slug, PHONE);
  const tB = await ticketFor(s.slug, PHONE_B);
  const tOther = await ticketFor(other.slug, PHONE);
  const pickup = (phone) => body({ fulfillment: 'pickup', address: undefined }, phone);
  for (let i = 0; i < 3; i++) assert.equal((await checkout(s.slug, pickup(PHONE), { ticket: tA })).status, 201);
  assert.equal((await checkout(s.slug, pickup(PHONE_B), { ticket: tB })).status, 201);
  assert.equal((await checkout(other.slug, pickup(PHONE), { ticket: tOther })).status, 201);

  const S1 = (await h.post(`/api/store/${s.slug}/account`, {}, { headers: { 'X-Store-Ticket': tA } })).body.session;
  const auth = { headers: { 'X-Store-Session': S1 } };
  const p1 = await h.get(`/api/store/${s.slug}/account/orders?limit=2`, auth);
  assert.equal(p1.status, 200, JSON.stringify(p1.body));
  assert.equal(p1.body.orders.length, 2);
  assert.ok(p1.body.nextCursor);
  assert.deepEqual(p1.body.orders[0].preview, ['برنج', 'روغن']);
  const p2 = await h.get(`/api/store/${s.slug}/account/orders?limit=2&cursor=${encodeURIComponent(p1.body.nextCursor)}`, auth);
  assert.equal(p2.body.orders.length, 1);
  assert.equal(p2.body.nextCursor, null);
  const nos = [...p1.body.orders, ...p2.body.orders].map((x) => x.orderNo);
  assert.deepEqual(nos, [3, 2, 1], 'سفارشِ شمارهٔ دیگر (۴) نیست');
  //  همان نشست در ویترینِ دیگر فقط سفارشِ همان دکان
  const elsewhere = await h.get(`/api/store/${other.slug}/account/orders`, auth);
  assert.equal(elsewhere.body.orders.length, 1);
  //  توکنِ هر ردیف همان پیگیری است
  const tr = await h.get(`/api/store/${s.slug}/orders/${p1.body.orders[0].token}`);
  assert.equal(tr.status, 200);
  assert.equal(tr.body.order.orderNo, 3);
  //  «سفارش‌های من» ِ قدیمی هم با نشست کار می‌کند
  assert.equal((await h.get(`/api/store/${s.slug}/orders`, auth)).body.orders.length, 3);
  assert.equal((await h.get(`/api/store/${s.slug}/account/orders?cursor=bad`, auth)).status, 400);
});

test('CHK-09: نشانی‌های نام‌دار فقط مالِ خودِ مشتری؛ سقف؛ پاک کردنِ حساب سفارش را نگه می‌دارد (S140)', async () => {
  const s = await liveStore('نشانی');
  const tA = await ticketFor(s.slug, PHONE);
  const tB = await ticketFor(s.slug, PHONE_B);
  const A = { headers: { 'X-Store-Session': (await h.post(`/api/store/${s.slug}/account`, {}, { headers: { 'X-Store-Ticket': tA } })).body.session } };
  const B = { headers: { 'X-Store-Session': (await h.post(`/api/store/${s.slug}/account`, {}, { headers: { 'X-Store-Ticket': tB } })).body.session } };
  const url = `/api/store/${s.slug}/account/addresses`;
  assert.equal((await h.post(url, { label: '' , text: 'x' }, A)).body.error.code, 'bad_address');
  assert.equal((await h.post(url, { label: 'خانه', lat: 34.5 }, A)).body.error.code, 'bad_address');
  assert.equal((await h.post(url, { label: 'خانه' }, A)).body.error.code, 'bad_address');
  const home = await h.post(url, { label: 'خانه', text: 'کارته‌سه', landmark: 'مسجد', ...INSIDE }, A);
  assert.equal(home.status, 201, JSON.stringify(home.body));
  assert.equal(home.body.address.lat, INSIDE.lat);
  //  ب نشانیِ الف را نمی‌بیند، عوض نمی‌کند، پاک نمی‌کند
  assert.deepEqual((await h.get(url, B)).body.addresses, []);
  assert.equal((await h.put(`${url}/${home.body.address.id}`, { label: 'دزدی', text: 'x' }, B)).status, 404);
  assert.equal((await h.del(`${url}/${home.body.address.id}`, B)).status, 404);
  const ed = await h.put(`${url}/${home.body.address.id}`, { label: 'خانهٔ نو', text: 'کارته‌چار' }, A);
  assert.equal(ed.status, 200);
  assert.equal(ed.body.address.lat, null);
  for (let i = 1; i < 10; i++) assert.equal((await h.post(url, { label: `ن${i}`, text: 'x' }, A)).status, 201);
  const over = await h.post(url, { label: 'یازدهم', text: 'x' }, A);
  assert.equal(over.status, 409);
  assert.equal(over.body.error.code, 'too_many_addresses');

  //  سفارشِ الف، بعد پاک کردنِ حساب
  assert.equal((await checkout(s.slug, body({ fulfillment: 'pickup', address: undefined }), { session: A.headers['X-Store-Session'] })).status, 201);
  const del = await h.del(`/api/store/${s.slug}/account`, A);
  assert.equal(del.status, 200, JSON.stringify(del.body));
  assert.equal((await h.get(`/api/store/${s.slug}/account`, A)).status, 401);
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM store_customer_addresses a JOIN store_customers c ON c.id=a.customer_id WHERE c.phone=$1', ['+93791112233'])).n, 0);
  assert.equal((await one(`SELECT COUNT(*)::int AS n FROM online_orders WHERE shop_id=$1 AND customer_phone='+93791112233'`, [s.shopId])).n, 1, 'سفارش سندِ دکان است و می‌ماند');
  assert.equal((await h.get(url, B)).status, 200, 'حسابِ ب دست نخورد');
});

/* ══════════════════════════════════════════════════════════════════
   ۶) صفحهٔ کالا، پرفروش/تازه، خریدِ دوباره، عکسِ کوچک
   ══════════════════════════════════════════════════════════════════ */

test('WEB-08: صفحهٔ کالا با توضیح و مشخصاتِ عمومی، بی یادداشتِ داخلی؛ مشابه و مکمل', async () => {
  const s = await liveStore('صفحهٔ کالا');
  const set = await h.put('/api/shop/storefront/products/rice', {
    published: true, description: 'برنجِ دانه‌بلند', specs: [{ name: 'وزن', value: '۱ کیلو' }, { name: '', value: 'بی‌نام' }],
  }, { token: s.token });
  assert.equal(set.status, 200, JSON.stringify(set.body));
  assert.deepEqual(set.body.specs, [{ name: 'وزن', value: '۱ کیلو' }]);
  //  به‌روزرسانیِ بی توضیح (برنامهٔ قدیمی) توضیح را پاک نمی‌کند
  await h.put('/api/shop/storefront/products/rice', { published: true, onlinePriceMinor: 13000 }, { token: s.token });
  //  «مکمل» فقط با k-گمنامی: سه سفارش از سه شماره (برنج + نمک)
  for (const ph of [PHONE, PHONE_B, PHONE_C]) {
    assert.equal((await checkout(s.slug, body({ fulfillment: 'pickup', address: undefined,
      lines: [{ productId: 'rice', qtyMilli: 1000 }, { productId: 'salt', qtyMilli: 1000 }] }, ph), { ticket: await ticketFor(s.slug, ph) })).status, 201);
  }

  const r = await h.get(`/api/store/${s.slug}/products/rice`);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.product.description, 'برنجِ دانه‌بلند');
  assert.equal(r.body.product.priceMinor, 13000);
  assert.deepEqual(r.body.product.specs, [{ name: 'وزن', value: '۱ کیلو' }]);
  assert.ok(!JSON.stringify(r.body).includes('رازِ خرید'), 'یادداشتِ داخلیِ دفتر بیرون نمی‌رود');
  //  ۳۲.۳: بی کاتالوگِ روشن (`shop.catalog`) ⇒ «در حال بررسی»، هرگز «موجود» ِ ساختگی
  assert.equal(r.body.product.stock.status, 'unverified');
  assert.deepEqual(r.body.similar.map((p) => p.id).sort(), ['oil', 'salt']);
  assert.deepEqual(r.body.complementary.map((p) => p.id), ['salt']);
  assert.equal((await h.get(`/api/store/${s.slug}/products/soap`)).status, 404, 'منتشرنشده');
  assert.equal((await h.get(`/api/store/${s.slug}/products/${encodeURIComponent('../x')}`)).status, 404);
  const staff = await h.get('/api/shop/storefront/products', { token: s.token });
  assert.equal(staff.body.items.find((p) => p.id === 'rice').description, 'برنجِ دانه‌بلند');
});

test('WEB-02: پرفروش‌ها از سفارشِ واقعی (بی لغوشده)، تازه‌ها از انتشار؛ WEB-12 خریدِ دوباره با قیمتِ امروز', async () => {
  const s = await liveStore('پرفروش');
  const empty = await h.get(`/api/store/${s.slug}/highlights`);
  assert.equal(empty.status, 200);
  assert.deepEqual(empty.body.bestsellers, [], 'بی سفارش ⇒ هیچ «پرفروش» ِ ساختگی');
  assert.deepEqual(empty.body.newArrivals.map((p) => p.id).sort(), ['oil', 'rice', 'salt']);

  const t = await ticketFor(s.slug, PHONE);
  const pick = (lines, ph = PHONE) => body({ fulfillment: 'pickup', address: undefined, lines }, ph);
  const by = async (ph, lines) => {
    const r = await checkout(s.slug, pick(lines, ph), { ticket: await ticketFor(s.slug, ph) });
    assert.equal(r.status, 201, JSON.stringify(r.body));
    return r;
  };
  //  روغن: شش سفارش از سه شماره؛ نمک: سه سفارش از سه شماره؛ برنج: فقط یک مشتری
  for (const ph of [PHONE, PHONE_B, PHONE_C]) for (let i = 0; i < 2; i++) await by(ph, [{ productId: 'oil', qtyMilli: 1000 }]);
  const first = await by(PHONE, [{ productId: 'rice', qtyMilli: 1000 }, { productId: 'salt', qtyMilli: 3000 }]);
  await by(PHONE_B, [{ productId: 'salt', qtyMilli: 1000 }]);
  await by(PHONE_C, [{ productId: 'salt', qtyMilli: 1000 }]);
  //  سفارشِ لغوشده نمی‌شمارد (وگرنه نمک با هفت سفارش از روغن جلو می‌زد)
  for (let i = 0; i < 4; i++) {
    const c = await checkout(s.slug, pick([{ productId: 'salt', qtyMilli: 1000 }]), { ticket: t });
    await h.post(`/api/store/${s.slug}/orders/${c.body.order.token}/cancel`, {});
  }
  const hl = await h.get(`/api/store/${s.slug}/highlights`);
  assert.deepEqual(hl.body.bestsellers.map((p) => p.id), ['oil', 'salt'], 'برنجِ یک مشتری پرفروش نیست (k-گمنامی)');
  //  کالای برداشته‌شده از ویترین پیشنهاد نمی‌شود
  await h.put('/api/shop/storefront/products/oil', { published: false }, { token: s.token });
  assert.ok(!(await h.get(`/api/store/${s.slug}/highlights`)).body.bestsellers.some((p) => p.id === 'oil'));

  //  خریدِ دوباره: قیمتِ امروز، و کالای بیرون‌رفته «نیست»
  await h.put('/api/shop/storefront/products/rice', { published: true, onlinePriceMinor: 15000 }, { token: s.token });
  await h.put('/api/shop/storefront/products/salt', { published: false }, { token: s.token });
  const re = await h.get(`/api/store/${s.slug}/orders/${first.body.order.token}/reorder`);
  assert.equal(re.status, 200, JSON.stringify(re.body));
  const rice = re.body.lines.find((l) => l.productId === 'rice');
  assert.deepEqual([rice.available, rice.previousPriceMinor, rice.priceMinor], [true, 12050, 15000]);
  const salt = re.body.lines.find((l) => l.productId === 'salt');
  assert.deepEqual([salt.available, salt.priceMinor, salt.reason], [false, null, 'not_in_store']);
  assert.equal((await h.get(`/api/store/${s.slug}/orders/AAAA.BBBB/reorder`)).status, 404);
  assert.equal(await count(s.shopId), 13, 'خریدِ دوباره سفارشی نمی‌سازد');
});

test('⛔ k-گمنامی: «پرفروش» و «مکمل» از سبدِ یک یا دو مشتری ساخته نمی‌شوند — دست‌کم ۳ سفارش از ۳ شماره', async () => {
  //  بازبینیِ حریم: از یک سفارش، سبدِ همان یک مشتری روی ویترین می‌آمد
  assert.equal(require('../src/lib/storefront').K_ANON, 3);
  const s = await liveStore('گمنامی');
  const basket = (ph) => body({ fulfillment: 'pickup', address: undefined,
    lines: [{ productId: 'rice', qtyMilli: 1000 }, { productId: 'salt', qtyMilli: 1000 }] }, ph);
  const order = async (ph) => assert.equal((await checkout(s.slug, basket(ph), { ticket: await ticketFor(s.slug, ph) })).status, 201);
  const look = async () => ({
    best: (await h.get(`/api/store/${s.slug}/highlights`)).body.bestsellers.map((p) => p.id).sort(),
    comp: (await h.get(`/api/store/${s.slug}/products/rice`)).body.complementary.map((p) => p.id),
  });
  //  یک مشتری با چهار سفارش هنوز «یک نفر» است
  for (let i = 0; i < 4; i++) await order(PHONE);
  assert.deepEqual(await look(), { best: [], comp: [] }, 'سبدِ یک نفر');
  await order(PHONE_B);
  assert.deepEqual(await look(), { best: [], comp: [] }, 'دو نفر هنوز کم است');
  await order(PHONE_C);
  assert.deepEqual(await look(), { best: ['rice', 'salt'], comp: ['salt'] });
});

test('۳۲.۳ + WEB-09: کاتالوگِ روشن (`shop.catalog`) ⇒ موجودیِ واقعی از catalog.publicStock؛ ناموجود پیشنهاد نمی‌شود', async () => {
  const s = await liveStore('موجودی');
  const before = await h.get(`/api/store/${s.slug}/products`);
  assert.ok(before.body.items.every((p) => p.stock.status === 'unverified'), 'پرچمِ کاتالوگ خاموش ⇒ «در حال بررسی»');
  await setFlag(s.shopId, 'shop.catalog', true);
  const p = await push(s.token, [{
    op_id: `op-${uniq()}`, ts: Date.now(), table: 'warehouseEntries', row_id: `we-${uniq()}`, type: 'insert',
    fields: { productId: 'rice', units: 10, cartons: 0, perCarton: 0, unit: 'کیلو', price: 100, createdAt: Date.now() },
  }]);
  assert.equal(p.status, 200, JSON.stringify(p.body));
  const list = await h.get(`/api/store/${s.slug}/products`);
  const st = Object.fromEntries(list.body.items.map((x) => [x.id, x.stock.status]));
  assert.ok(['in_stock', 'in_warehouse'].includes(st.rice), JSON.stringify(st));
  assert.equal(st.oil, 'out_of_stock');
  assert.equal(st.salt, 'out_of_stock');
  const detail = await h.get(`/api/store/${s.slug}/products/rice`);
  assert.deepEqual(detail.body.similar, [], 'ناموجود در «مشابه» نیست');
  const hl = await h.get(`/api/store/${s.slug}/highlights`);
  assert.deepEqual(hl.body.newArrivals.map((x) => x.id), ['rice']);
  //  کالای ناموجود در صفحهٔ خودش دیده می‌شود ولی «ناموجود»
  assert.equal((await h.get(`/api/store/${s.slug}/products/oil`)).body.product.stock.status, 'out_of_stock');
});

test('WEB-16: عکسِ کوچکِ بانک فقط با پرچمِ دکان؛ وگرنه عکسِ همگام‌شده؛ منتشرنشده هرگز', async () => {
  const s = await liveStore('عکس');
  const legacy = F.jpeg(320, 320, { seed: 71 });
  const up = await h.api('PUT', '/api/sync/v1/photos/rice', {
    token: s.token, headers: { 'X-App': 'shop', 'X-Device': 'dev-store' }, body: { data: legacy.toString('base64') },
  });
  assert.equal(up.status, 200, JSON.stringify(up.body));
  const get = async (p) => {
    const r = await fetch(`${h.base()}/api/store/${s.slug}${p}`);
    return { status: r.status, buf: Buffer.from(await r.arrayBuffer()), type: r.headers.get('content-type') };
  };
  const before = await get('/products/rice/thumb');
  assert.equal(before.status, 200);
  assert.ok(before.buf.equals(legacy), 'پرچمِ خاموش ⇒ همان عکسِ همگام‌شده');

  await setFlag(s.shopId, 'shop.image_bank', true);
  const own = await h.raw('POST', '/api/shop/images/?product_id=rice', F.jpeg(800, 600, { seed: 72 }), { token: s.token });
  assert.equal(own.status, 201, JSON.stringify(own.body));
  const thumb = F.jpeg(400, 300, { seed: 73 });
  const d = await h.raw('PUT', `/api/shop/images/${own.body.id}/v/1/thumb`, thumb, { token: s.token });
  assert.equal(d.status, 200, JSON.stringify(d.body));
  const sel = await h.post('/api/shop/images/select', { product_id: 'rice', source: 'shop', image_id: own.body.id }, { token: s.token });
  assert.equal(sel.status, 200, JSON.stringify(sel.body));

  const banked = await get('/products/rice/thumb');
  assert.equal(banked.status, 200);
  assert.match(banked.type, /image\/jpeg/);
  assert.equal(banked.buf.length > 0 && !banked.buf.equals(legacy), true, 'نسخهٔ کوچکِ بانک');
  //  اندازهٔ «display» ساخته نشده ⇒ همان عکسِ همگام‌شده
  assert.ok((await get('/products/rice/photo')).buf.equals(legacy));
  //  کالای بی عکسِ همگام‌شده ولی با عکسِ بانک ⇒ در فهرست «عکس دارد»
  const own2 = await h.raw('POST', '/api/shop/images/?product_id=oil', F.jpeg(800, 600, { seed: 74 }), { token: s.token });
  await h.raw('PUT', `/api/shop/images/${own2.body.id}/v/1/thumb`, F.jpeg(400, 300, { seed: 75 }), { token: s.token });
  await h.post('/api/shop/images/select', { product_id: 'oil', source: 'shop', image_id: own2.body.id }, { token: s.token });
  const cat = await h.get(`/api/store/${s.slug}/products`);
  assert.equal(cat.body.items.find((p) => p.id === 'oil').photo, true);
  assert.equal(cat.body.items.find((p) => p.id === 'salt').photo, false);

  //  پرچم خاموش ⇒ دوباره همان عکسِ همگام‌شده
  await setFlag(s.shopId, 'shop.image_bank', false);
  assert.ok((await get('/products/rice/thumb')).buf.equals(legacy));
  assert.equal((await get('/products/oil/thumb')).status, 404);
  //  کالای منتشرنشده ⇒ ۴۰۴
  assert.equal((await get('/products/soap/thumb')).status, 404);
});
