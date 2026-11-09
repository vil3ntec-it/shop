'use strict';
/**
 * WEB-11 — «فهرستِ خرید» ِ حسابِ مشتری روی سرور (`lib/store-shopping-list.js`، مهاجرتِ ۴۳۲؛ درها در `routes/storefront.js`).
 *
 *   ۱) پرچمِ ♥ خاموش ⇒ ۴۰۴ `favorites_disabled` و هیچ ردیفی؛ بی نشست ⇒ ۴۰۱؛ ویترینِ بسته ⇒ ۴۰۴ `store_not_found`.
 *   ۲) ادغامِ فهرستِ دستگاه: بیشینهٔ مقدار، کالای تازه پشتِ فهرست، تکرارش بی‌اثر (نسخه همان)؛ پر ⇒ ۴۰۹ `list_full` و هیچ.
 *   ۳) جایگزینی با نسخهٔ دیده‌شده: کهنه ⇒ ۴۰۹ `list_changed` با فهرستِ تازه؛ همان محتوا ⇒ ۲۰۰ و نسخه همان (تکرارِ امن).
 *   ۴) قیمت، نام و موجودی هرگز در ردیف نیست؛ کالای پنهان و نبوده هر دو فقط `available: false` (بی شمارشِ کالای پنهان).
 *   ۵) IDOR: مشتریِ دیگر و ویترینِ دیگر جدا؛ `customerId`/`shopId` ِ بدنه نادیده؛ پاکِ حساب ⇒ پاک.
 *   ۶) سقف‌ها: ۱۰۰ قلم، ۵۰ فهرستِ پُر برای هر مشتری؛ بدنهٔ بد ⇒ ۴۰۰ و هیچ تغییری.
 *   ۷) سطلِ `store-acct-sync` به‌ازای IP و پیش از نشست؛ سهمِ ورود/ثبت (`store-checkout`) را نمی‌خورد.
 */
process.env.RATE_STORE_CHECKOUT_MAX = '6';
process.env.RATE_STORE_SYNC_MAX = '30';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const L = require('../src/lib/store-shopping-list');

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

const flag = async (scope, key, value) => {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
};
const op = (id, fields) => ({ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: id, type: 'insert', fields });

async function liveStore(label) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { token: u.accessToken, shopId: made.body.shop.id };
  for (const k of ['shop.orders', 'shop.storefront']) await flag(o.shopId, k, true);
  const slug = `sl-${uniq()}`;
  assert.equal((await h.put('/api/shop/storefront', { slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001' }, { token: o.token })).status, 200);
  const p = await h.api('POST', '/api/sync/v1/push', {
    token: o.token, headers: { 'X-App': 'shop', 'X-Device': 'dev-sl' },
    body: { device_id: 'dev-sl', schema_version: 2, ops: [
      op('rice', { name: 'برنج', unit: 'کیلو', salePrice: 120 }), op('oil', { name: 'روغن', unit: 'بوتل', salePrice: 300 }),
      op('soap', { name: 'صابون', unit: 'دانه', salePrice: 40 }),
    ] },
  });
  assert.equal(p.status, 200, JSON.stringify(p.body));
  for (const id of ['rice', 'oil']) assert.equal((await h.put(`/api/shop/storefront/products/${id}`, { published: true }, { token: o.token })).status, 200);
  return { ...o, slug };
}

async function customer(slug, phone) {
  const s = await h.post(`/api/store/${slug}/verify/start`, { phone });
  const c = await h.post(`/api/store/${slug}/verify/check`, { phone, code: s.body.devCode });
  const a = await h.post(`/api/store/${slug}/account`, {}, { headers: { 'X-Store-Ticket': c.body.ticket } });
  assert.equal(a.status, 201, JSON.stringify(a.body));
  return { headers: { 'X-Store-Session': a.body.session }, id: a.body.customer.id };
}
const rows = async (shopId) => (await one('SELECT COUNT(*)::int AS n FROM store_customer_lists WHERE shop_id = $1', [shopId])).n;
const lines = (r) => r.body.list.lines.map((l) => [l.productId, l.qtyMilli, l.available]);

test('خاموش ۴۰۴ و بی ردیف؛ بی نشست ۴۰۱؛ ادغام بیشینه و بی‌اثر در تکرار؛ جایگزینی با نسخه؛ تکرارِ امن', async () => {
  const s = await liveStore('فهرست');
  const k = await customer(s.slug, '0791250001');
  const base = `/api/store/${s.slug}/account/shopping-list`;
  assert.equal((await h.get(base)).status, 401, 'بی نشست');
  assert.equal((await h.put(base, { version: 0, items: [] })).status, 401);
  for (const r of [await h.get(base, k), await h.put(base, { version: 0, items: [{ productId: 'rice', qtyMilli: 1000 }] }, k),
    await h.post(`${base}/merge`, { items: [{ productId: 'rice', qtyMilli: 1000 }] }, k)]) {
    assert.deepEqual([r.status, r.body.error.code], [404, 'favorites_disabled']);
  }
  assert.equal(await rows(s.shopId), 0, 'خاموش ⇒ هیچ نوشتنی');

  await flag(s.shopId, 'shop.storefront.favorites', true);
  const empty = await h.get(base, k);
  assert.equal(empty.status, 200, JSON.stringify(empty.body));
  assert.deepEqual(empty.body.list, { version: 0, updatedAt: null, maxLines: L.MAX_LINES, lines: [], items: [] });

  //  ادغامِ نخستینِ دستگاه
  const m1 = await h.post(`${base}/merge`, { items: [{ productId: 'rice', qtyMilli: 2000 }, { productId: 'oil', qtyMilli: 1000 }] }, k);
  assert.equal(m1.status, 200, JSON.stringify(m1.body));
  assert.equal(m1.body.list.version, 1);
  assert.deepEqual(lines(m1), [['rice', 2000, true], ['oil', 1000, true]]);
  assert.deepEqual(m1.body.list.items.map((p) => [p.id, p.priceMinor]), [['rice', 12000], ['oil', 30000]], 'قیمتِ امروزِ کاتالوگ');
  const again = await h.post(`${base}/merge`, { items: [{ productId: 'rice', qtyMilli: 2000 }, { productId: 'oil', qtyMilli: 1000 }] }, k);
  assert.equal(again.body.list.version, 1, 'تکرارِ همان ادغام ⇒ بی‌اثر و نسخه همان');
  //  دستگاهِ دیگر: مقدارِ کمتر نمی‌نشیند، بیشتر می‌نشیند، کالای تازه پشتِ فهرست
  const m2 = await h.post(`${base}/merge`, { items: [{ productId: 'oil', qtyMilli: 3000 }, { productId: 'rice', qtyMilli: 500 }, { productId: 'soap', qtyMilli: 1000 }] }, k);
  assert.equal(m2.body.list.version, 2);
  assert.deepEqual(lines(m2), [['rice', 2000, true], ['oil', 3000, true], ['soap', 1000, false]], 'بیشینه، نه جمع؛ صابونِ منتشرنشده فقط «نیست»');

  //  ⛔ ردیف فقط شناسه و مقدار: نام، قیمت و موجودی هرگز
  const stored = (await one('SELECT items::text AS t FROM store_customer_lists WHERE shop_id = $1', [s.shopId])).t;
  assert.ok(!/price|name|stock|unit/i.test(stored), stored);
  assert.deepEqual(Object.keys(m2.body.list.lines[0]).sort(), ['available', 'productId', 'qtyMilli']);

  //  جایگزینی با نسخهٔ دیده‌شده
  const want = { version: 2, items: [{ productId: 'oil', qtyMilli: 1000 }] };
  const put = await h.put(base, want, k);
  assert.equal(put.status, 200, JSON.stringify(put.body));
  assert.equal(put.body.list.version, 3);
  assert.deepEqual(lines(put), [['oil', 1000, true]]);
  //  تکرارِ همان درخواست (پاسخ گم شد، دو زدن) ⇒ ۲۰۰ با همان فهرست و همان نسخه
  const replay = await h.put(base, want, k);
  assert.equal(replay.status, 200, JSON.stringify(replay.body));
  assert.equal(replay.body.list.version, 3, 'تکرار نسخه را بالا نمی‌برد');
  //  نوشتنِ کهنهٔ دستگاهِ دیگر با محتوای دیگر ⇒ ۴۰۹ با فهرستِ تازه، و هیچ چیز عوض نمی‌شود
  const stale = await h.put(base, { version: 2, items: [{ productId: 'rice', qtyMilli: 9000 }] }, k);
  assert.deepEqual([stale.status, stale.body.error.code], [409, 'list_changed']);
  assert.equal(stale.body.error.list.version, 3);
  assert.deepEqual(stale.body.error.list.lines.map((l) => l.productId), ['oil']);
  assert.deepEqual(lines(await h.get(base, k)), [['oil', 1000, true]]);
  //  خالی کردن = فهرستِ خالی؛ ردیف می‌ماند و نسخه به صفر برنمی‌گردد (بی ABA)
  const clear = await h.put(base, { version: 3, items: [] }, k);
  assert.deepEqual([clear.status, clear.body.list.version, clear.body.list.lines], [200, 4, []]);
  assert.equal((await h.put(base, { version: 0, items: [{ productId: 'rice', qtyMilli: 1000 }] }, k)).status, 409, 'نسخهٔ ۰ ِ کهنه هرگز دوباره پذیرفته نمی‌شود');
});

test('کالای پنهان و نبوده یکسان؛ بدنهٔ بد ۴۰۰ و بی تغییر؛ سقفِ قلم ۴۰۹ `list_full`', async () => {
  const s = await liveStore('فهرستِ مرز');
  await flag(s.shopId, 'shop.storefront.favorites', true);
  const k = await customer(s.slug, '0791250011');
  const base = `/api/store/${s.slug}/account/shopping-list`;
  const r = await h.put(base, { version: 0, items: [{ productId: 'soap', qtyMilli: 1000 }, { productId: 'ghost', qtyMilli: 1000 }] }, k);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(r.body.list.lines, [{ productId: 'soap', qtyMilli: 1000, available: false }, { productId: 'ghost', qtyMilli: 1000, available: false }],
    'پنهان و نبوده یک شکل دارند ⇒ فهرست راهِ شمردنِ کالای پنهان نیست');
  assert.deepEqual(r.body.list.items, []);

  const many = Array.from({ length: L.MAX_LINES + 1 }, (_, i) => ({ productId: `p${i}`, qtyMilli: 1000 }));
  const bads = [
    { version: 1, items: 'rice' }, { version: 1 }, { version: -1, items: [] }, { version: '1', items: [] }, { items: [] },
    { version: 1, items: [{ productId: 'bad id', qtyMilli: 1000 }] },
    { version: 1, items: [{ productId: 'rice', qtyMilli: 1000 }, { productId: 'rice', qtyMilli: 2000 }] },
    { version: 1, items: [{ productId: 'rice', qtyMilli: 0 }] }, { version: 1, items: [{ productId: 'rice', qtyMilli: 1.5 }] },
    { version: 1, items: [{ productId: 'rice', qtyMilli: 10_000_001 }] }, { version: 1, items: [{ productId: 'rice', qtyMilli: '1000' }] },
    { version: 1, items: many },
  ];
  for (const b of bads) {
    const x = await h.put(base, b, k);
    assert.equal(x.status, 400, `${JSON.stringify(b).slice(0, 80)} ⇒ ${x.status}`);
  }
  assert.equal((await h.post(`${base}/merge`, { items: many }, k)).status, 400);
  assert.equal((await h.post(`${base}/merge`, {}, k)).status, 400);
  assert.equal((await h.get(base, k)).body.list.version, 1, 'هیچ بدنهٔ بدی چیزی ننوشت');

  //  پر: ۹۹ + ۲ تازه ⇒ ۴۰۹ و هیچ؛ ۹۸ + ۲ ⇒ درست تا خودِ سقف
  const fill = Array.from({ length: L.MAX_LINES - 2 }, (_, i) => ({ productId: `f${i}`, qtyMilli: 1000 }));
  assert.equal((await h.post(`${base}/merge`, { items: fill }, k)).body.list.lines.length, L.MAX_LINES);
  const full = await h.post(`${base}/merge`, { items: [{ productId: 'rice', qtyMilli: 1000 }] }, k);
  assert.deepEqual([full.status, full.body.error.code, full.body.error.list.lines.length], [409, 'list_full', L.MAX_LINES]);
  assert.equal((await h.get(base, k)).body.list.version, 2);
});

test('IDOR: مشتریِ دیگر، ویترینِ دیگر و شناسه‌های بدنه؛ ویترینِ بسته؛ سقفِ فهرست‌های هر مشتری؛ پاکِ حساب ⇒ پاک', async () => {
  const a = await liveStore('فهرستِ الف');
  const b = await liveStore('فهرستِ ب');
  for (const s of [a, b]) await flag(s.shopId, 'shop.storefront.favorites', true);
  const ka = await customer(a.slug, '0791250021');
  const kb = await customer(a.slug, '0791250022');
  const urlA = `/api/store/${a.slug}/account/shopping-list`;
  const urlB = `/api/store/${b.slug}/account/shopping-list`;
  //  شناسه‌های ممتاز در بدنه نادیده‌اند: نوشتن همیشه روی حسابِ نشست و ویترینِ نشانی
  const w = await h.put(urlA, { version: 0, customerId: kb.id, shopId: b.shopId, items: [{ productId: 'rice', qtyMilli: 4000 }] }, ka);
  assert.equal(w.status, 200, JSON.stringify(w.body));
  assert.equal((await one('SELECT customer_id, shop_id FROM store_customer_lists WHERE customer_id = $1', [ka.id])).shop_id, a.shopId);
  assert.equal(await one('SELECT 1 AS x FROM store_customer_lists WHERE customer_id = $1', [kb.id]), null);
  //  مشتریِ دیگرِ همان ویترین: فهرستِ خودش (خالی)، و نوشتنش فهرستِ «الف» را عوض نمی‌کند
  assert.deepEqual((await h.get(urlA, kb)).body.list.lines, []);
  assert.equal((await h.put(urlA, { version: 0, items: [{ productId: 'oil', qtyMilli: 1000 }] }, kb)).status, 200);
  assert.deepEqual(lines(await h.get(urlA, ka)), [['rice', 4000, true]]);
  //  همان حساب در ویترینِ دیگر: فهرستِ جدا
  assert.deepEqual((await h.get(urlB, ka)).body.list.lines, []);
  //  نشستِ ساختگی/دست‌کاری‌شده ⇒ ۴۰۱
  assert.equal((await h.get(urlA, { headers: { 'X-Store-Session': `sc1.${'A'.repeat(43)}` } })).status, 401);

  //  سقفِ فهرست‌های پُرِ هر مشتری (همهٔ ویترین‌ها)؛ فهرستِ خالی شمرده نمی‌شود و فهرستِ پُرِ موجود همیشه نوشتنی است
  const at = now();
  const owner = (await one('SELECT owner_user_id FROM shops WHERE id = $1', [a.shopId])).owner_user_id;
  for (let i = 0; i < L.MAX_LISTS - 1; i++) {
    const id = newId('shp');
    await query('INSERT INTO shops (id, owner_user_id, name, created_at, updated_at) VALUES ($1,$2,$3,$4,$4)', [id, owner, `پر ${i}`, at]);
    await query(`INSERT INTO store_customer_lists (customer_id, shop_id, items, version, updated_at) VALUES ($1,$2,'[{"productId":"x","qtyMilli":1}]'::jsonb,1,$3)`, [ka.id, id, at]);
  }
  const tooMany = await h.post(`${urlB}/merge`, { items: [{ productId: 'rice', qtyMilli: 1000 }] }, ka);
  assert.deepEqual([tooMany.status, tooMany.body.error.code], [409, 'too_many_lists']);
  assert.deepEqual((await h.get(urlB, ka)).body.list.lines, [], 'رد ⇒ هیچ چیز ننشست');
  assert.equal((await h.put(urlA, { version: 1, items: [{ productId: 'rice', qtyMilli: 5000 }] }, ka)).status, 200, 'فهرستِ پُرِ موجود همچنان نوشتنی');
  assert.equal((await h.put(urlA, { version: 2, items: [] }, ka)).status, 200);
  assert.equal((await h.post(`${urlB}/merge`, { items: [{ productId: 'rice', qtyMilli: 1000 }] }, ka)).status, 200, 'خالی شدنِ یکی ⇒ جا برای دیگری');

  //  ویترینِ بسته ⇒ همان ۴۰۴ ِ «ویترین نیست»، پیش از نشست
  assert.equal((await h.put('/api/shop/storefront', { slug: b.slug, enabled: false, title: 'بسته', contactPhone: '0700000001' }, { token: b.token })).status, 200);
  const closed = await h.get(urlB, ka);
  assert.deepEqual([closed.status, closed.body.error.code], [404, 'store_not_found']);

  //  پاک کردنِ حساب ⇒ فهرست‌ها هم (cascade)
  assert.equal((await h.del(`/api/store/${a.slug}/account`, ka)).status, 200);
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM store_customer_lists WHERE customer_id = $1', [ka.id])).n, 0);
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM store_customer_lists WHERE customer_id = $1', [kb.id])).n, 1, 'مشتریِ دیگر دست نخورد');
});

test('سطلِ `store-acct-sync`: سقف به‌ازای IP و پیش از نشست؛ ورود (`store-checkout`) آزاد می‌ماند', async () => {
  const s = await liveStore('فهرستِ سطل');
  await flag(s.shopId, 'shop.storefront.favorites', true);
  resetAll();
  const k = await customer(s.slug, '0791250031');
  const base = `/api/store/${s.slug}/account/shopping-list`;
  let v = 0;
  for (let i = 1; i <= 30; i++) {
    const r = await h.put(base, { version: v, items: [{ productId: 'rice', qtyMilli: i * 1000 }] }, k);
    assert.equal(r.status, 200, `${i}: ${JSON.stringify(r.body)}`);
    v = r.body.list.version;
  }
  const over = await h.post(`${base}/merge`, { items: [] }, k);
  assert.equal(over.status, 429, JSON.stringify(over.body));
  assert.ok(Number(over.headers.get('retry-after')) > 0);
  assert.equal((await h.put(base, { version: v, items: [] })).status, 429, 'بی نشست هم همان سطل (پیش از احراز)');
  assert.equal((await h.get(base, k)).status, 200, 'خواندن سطلِ خودش را دارد');
  //  ورودِ دوباره (سطلِ `store-checkout`) هنوز آزاد است
  await customer(s.slug, '0791250032');
});

test('هم‌زمانی: پنج نوشتنِ نخستِ هم‌زمان با نسخهٔ ۰ ⇒ فقط یکی؛ پنج ادغامِ هم‌زمانِ یکسان ⇒ یک نسخه', async () => {
  const s = await liveStore('فهرستِ هم‌زمان');
  await flag(s.shopId, 'shop.storefront.favorites', true);
  resetAll();
  const k = await customer(s.slug, '0791250041');
  const k2 = await customer(s.slug, '0791250042');
  const base = `/api/store/${s.slug}/account/shopping-list`;
  const puts = await Promise.all([1, 2, 3, 4, 5].map((q) => h.put(base, { version: 0, items: [{ productId: 'rice', qtyMilli: q * 1000 }] }, k)));
  assert.deepEqual(puts.map((r) => r.status).sort(), [200, 409, 409, 409, 409], puts.map((r) => r.status).join(','));
  const won = puts.find((r) => r.status === 200).body.list;
  assert.deepEqual([won.version, (await h.get(base, k)).body.list.lines], [1, won.lines], 'برنده همان که ماند؛ بازنده‌ها چیزی ننوشتند');
  const merges = await Promise.all([1, 2, 3, 4, 5].map(() => h.post(`${base}/merge`, { items: [{ productId: 'oil', qtyMilli: 1000 }] }, k2)));
  assert.ok(merges.every((r) => r.status === 200), merges.map((r) => r.status).join(','));
  assert.equal((await h.get(base, k2)).body.list.version, 1, 'ادغامِ تکراری و هم‌زمان ⇒ یک نوشتن');
});

test('مهاجرتِ ۴۳۲: rollback جدول و ردیفِ خودش را برمی‌دارد؛ دوباره نشستن همان', async () => {
  const fs = require('node:fs');
  const path = require('node:path');
  const dir = path.join(__dirname, '..', 'migrations');
  const name = '432_store_customer_lists.sql';
  const down = fs.readFileSync(path.join(dir, 'rollback', name.replace('.sql', '.down.sql')), 'utf8');
  const up = fs.readFileSync(path.join(dir, name), 'utf8');
  const has = async () => (await query("SELECT 1 FROM information_schema.tables WHERE table_name='store_customer_lists'")).rows.length;
  const mark = await one('SELECT * FROM schema_migrations WHERE version = $1', [name]);
  assert.ok(mark, 'مهاجرت نشسته است');
  await query(down);
  assert.equal(await has(), 0);
  assert.equal(await one('SELECT 1 AS x FROM schema_migrations WHERE version = $1', [name]), null);
  await query(up);
  await query('INSERT INTO schema_migrations (version, checksum, applied_at) VALUES ($1,$2,$3)', [mark.version, mark.checksum, mark.applied_at]);
  assert.equal(await has(), 1);
});
