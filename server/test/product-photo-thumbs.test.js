'use strict';
/**
 * WEB-15 — نسخهٔ کارتِ عکسِ همگام‌شدهٔ کالا (مهاجرتِ ۳۵۰، `lib/product-photos.js`).
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) کارت پس از عکس با درِ جدای `PUT /sync/v1/photos/:id/thumb` (`routes/sync-photo-thumbs.js`؛ `routes/sync-v1.js` ِ
 *      مشترک با پمپ دست نمی‌خورد)، با همان زنجیرهٔ `image-pipeline`: نوعِ واقعی از محتوا، سقفِ بایت/ضلع/پیکسل، جهتِ
 *      اعمال‌شده، نسبتِ اصل، و بی EXIF/GPS/توضیح (ICC می‌ماند). کارتِ نادرست ⇒ `thumb_*` و **هیچ** نوشتنی.
 *   ۲) `of` = hash ِ اصلِ امروز (کهنه ⇒ ۴۰۹)، بی‌اثر در تکرار، بی بالا بردنِ `rev`.
 *   ۳) جداییِ دکان‌ها: دکانِ «ب» با شناسه و hash ِ «الف» نه کارتِ «الف» را می‌نویسد نه می‌خواند.
 *   ۴) ویترین: `/thumb` کارت را می‌دهد؛ کارت نیست/ناخواناست/از اصلِ پیشین است ⇒ همان عکسِ کامل (هرگز عکسِ شکسته).
 *   ۵) نشانیِ نسخه‌دار: رسیدنِ کارت ⇒ `photoVersion` ِ تازه؛ `?v=` ِ همان ⇒ یک روز، کهنه/بی v ⇒ ۵ دقیقه؛ ETag ِ محتوا.
 *   ۶) انبارِ شیء: شیءِ کارت ردیف دارد (`gc` پاکش نمی‌کند) و با جایگزینیِ اصل پاک می‌شود.
 *   ۷) مهاجرتِ ۳۵۰ و rollback ِ آن.
 *   ۸) پمپ: دستگاهِ پمپ روی درِ کارت ۴۰۳ می‌گیرد؛ عکسِ پمپ همان پاسخ و همان فهرستِ پیش از ۳۵۰ را دارد (بی کلیدِ `thumb`).
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const h = require('./helpers');
const F = require('./image-fixtures');
const { query, one, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const bytes = require('../src/lib/image-bytes');
const photos = require('../src/lib/product-photos');

let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;
const LONG = 'public, max-age=86400';
const SHORT = 'public, max-age=300';
const SYNC = { 'X-App': 'shop', 'X-Device': 'dev-thumb' };

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
const op = (rowId, fields) => ({ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: rowId, type: 'insert', fields });

/** دکانِ روشن با ویترین: برنج و روغن منتشرشده */
async function liveStore(label) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { token: u.accessToken, shopId: made.body.shop.id, slug: `th-${uniq()}` };
  await setFlag(o.shopId, 'shop.orders', true);
  await setFlag(o.shopId, 'shop.storefront', true);
  const put = await h.put('/api/shop/storefront', { slug: o.slug, enabled: true, title: `ویترینِ ${label}`, deliveryFeeMinor: 0, minOrderMinor: 0 },
    { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const p = await h.api('POST', '/api/sync/v1/push', { token: o.token, headers: SYNC, body: { device_id: 'dev-thumb', schema_version: 2, ops: [
    op('rice', { name: 'برنج', unit: 'کیلو', salePrice: 120, category: 'خوراکی' }),
    op('oil', { name: 'روغن', unit: 'بوتل', salePrice: 300, category: 'خوراکی' }),
  ] } });
  assert.equal(p.status, 200, JSON.stringify(p.body));
  for (const id of ['rice', 'oil']) {
    const r = await h.put(`/api/shop/storefront/products/${id}`, { published: true }, { token: o.token });
    assert.equal(r.status, 200, JSON.stringify(r.body));
  }
  return o;
}

const b64 = (b) => (Buffer.isBuffer(b) ? b.toString('base64') : b);
const syncPut = (s, id, data) => h.api('PUT', `/api/sync/v1/photos/${id}`, { token: s.token, headers: SYNC, body: { data: b64(data) } });
const thumbPut = (s, id, data, of) => h.api('PUT', `/api/sync/v1/photos/${id}/thumb`, { token: s.token, headers: SYNC, body: { data: b64(data), of } });
/** همان دو گامِ برنامه: عکس، و درست پس از آن کارتش با `of` = hash ِ همان عکس */
async function withThumb(s, id, data, thumb) {
  const put = await syncPut(s, id, data);
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const t = await thumbPut(s, id, thumb, put.body.hash);
  assert.equal(t.status, 200, JSON.stringify(t.body));
  return { put, thumb: t };
}
const list = async (s) => (await h.api('GET', '/api/sync/v1/photos?since=0', { token: s.token, headers: SYNC })).body;
const original = async (s, id) => (await h.api('GET', `/api/sync/v1/photos/${id}`, { token: s.token, headers: SYNC })).body;
async function pic(s, p, headers = {}) {
  const r = await fetch(`${h.base()}/api/store/${s.slug}${p}`, { headers });
  return { status: r.status, cache: r.headers.get('cache-control'), etag: r.headers.get('etag'), type: r.headers.get('content-type'),
    nosniff: r.headers.get('x-content-type-options'), buf: Buffer.from(await r.arrayBuffer()) };
}
/** `photoVersion` ِ کالا در کاتالوگ، `?ids=`، صفحهٔ کالا و تازه‌ها — باید یکی باشند */
async function versionOf(s, id) {
  const all = [
    (await h.get(`/api/store/${s.slug}/products`)).body.items.find((p) => p.id === id),
    (await h.get(`/api/store/${s.slug}/products?ids=${id}`)).body.items.find((p) => p.id === id),
    (await h.get(`/api/store/${s.slug}/products/${id}`)).body.product,
    (await h.get(`/api/store/${s.slug}/highlights`)).body.newArrivals.find((p) => p.id === id),
  ].map((p) => p && p.photoVersion);
  assert.ok(all.every((v) => v === all[0]), `چهار راه یک نسخه: ${JSON.stringify(all)}`);
  return all[0];
}
const errCode = (r) => (r.body && r.body.error && r.body.error.code) || r.body?.error;

test('کارت پس از عکس: سنجیده، بی EXIF/GPS/توضیح (ICC می‌ماند)؛ ویترین کارت را می‌دهد', async () => {
  const s = await liveStore('کارت');
  const full = F.jpeg(800, 600, { seed: 11 });
  const card = F.jpeg(320, 240, { seed: 12, gps: true });
  assert.ok(card.includes(Buffer.from('Exif\0\0', 'latin1')) && card.includes(Buffer.from('shot at', 'latin1')), 'سنجشِ خودِ آزمون');
  const put = await syncPut(s, 'rice', full);
  assert.equal(put.status, 200, JSON.stringify(put.body));
  assert.equal('thumb' in put.body, false, 'پاسخِ درِ عکس همان پیشین');
  assert.deepEqual((await list(s)).items.map((i) => [i.product_id, i.thumb]), [['rice', false]]);
  const tp = await thumbPut(s, 'rice', card, put.body.hash);
  assert.equal(tp.status, 200, JSON.stringify(tp.body));
  assert.deepEqual((await list(s)).items.map((i) => [i.product_id, i.thumb]), [['rice', true]]);

  const v = await versionOf(s, 'rice');
  const t = await pic(s, `/products/rice/thumb?v=${v}`);
  assert.equal(t.status, 200);
  assert.ok(t.buf.equals(bytes.stripMetadata(card)), 'همان کارت، پس از strip_metadata');
  assert.ok(!t.buf.includes(Buffer.from('Exif\0\0', 'latin1')), 'بی EXIF/GPS');
  assert.ok(!t.buf.includes(Buffer.from('shot at', 'latin1')), 'بی توضیحِ مکان');
  assert.ok(t.buf.includes(Buffer.from('ICC_PROFILE', 'latin1')), 'رنگ‌نمای ICC می‌ماند');
  assert.equal(t.type, 'image/jpeg');
  //  صفحهٔ کالا همان عکسِ کامل
  const big = await pic(s, `/products/rice/photo?v=${v}`);
  assert.deepEqual([big.status, big.cache], [200, LONG]);
  assert.ok(big.buf.equals(full));
  //  همان اصل، بی کارت ⇒ کارت می‌ماند (برنامهٔ کهنه یا فرستادنِ دوباره)
  assert.equal((await syncPut(s, 'rice', full)).status, 200);
  assert.equal((await list(s)).items[0].thumb, true);
  assert.ok((await pic(s, '/products/rice/thumb')).buf.equals(t.buf));
  //  PNG و WebP هم (نوع از محتوا)
  const png = F.png(320, 240);
  await withThumb(s, 'oil', F.jpeg(640, 480, { seed: 13 }), png);
  const pt = await pic(s, '/products/oil/thumb');
  assert.equal(pt.type, 'image/png');
  assert.ok(pt.buf.equals(bytes.stripMetadata(png)));
});

test('کارتِ نادرست ⇒ thumb_* و هیچ نوشتنی: نوع، بایت، ضلع، سقفِ پیکسل، جهت، بزرگ‌تر از اصل، نسبت، خراب، نارشته', async () => {
  const noThumb = async (s) => (await one(`SELECT COUNT(*)::int AS n FROM product_photo_thumbs WHERE account_id=$1`, [s.shopId])).n;
  const s = await liveStore('کارتِ بد');
  const full = F.jpeg(800, 600, { seed: 21 });
  const ok = await syncPut(s, 'rice', full);
  assert.equal(ok.status, 200);
  const before = { hash: ok.body.hash, rev: (await list(s)).head };
  const gif = Buffer.concat([Buffer.from('GIF89a', 'latin1'), Buffer.alloc(400, 1)]);
  const huge = Buffer.concat([F.jpeg(320, 240, { seed: 22 }), Buffer.alloc(66 * 1024, 7)]);
  const cases = [
    ['GIF', gif, 415, 'thumb_bad_type'],
    ['متن با نامِ jpg', Buffer.from('<svg xmlns="http://www.w3.org/2000/svg"></svg>'.repeat(10)), 415, 'thumb_bad_type'],
    ['بیش از ۶۴KB', huge, 413, 'thumb_too_large'],
    ['ضلعِ ۴۰۱', F.jpeg(401, 301, { seed: 23 }), 400, 'thumb_too_big'],
    ['بمبِ فشرده (۱۲٬۰۰۰ پیکسل در یک بُعد)', F.pngBomb(12000, 100), 400, 'thumb_too_big'],
    ['EXIF orientation ِ ۶', F.jpeg(320, 240, { seed: 24, orientation: 6 }), 400, 'thumb_orientation_not_applied'],
    ['نسبتِ مربع برای اصلِ ۴:۳', F.jpeg(320, 320, { seed: 25 }), 400, 'thumb_aspect_mismatch'],
    ['JPEG ِ خراب', Buffer.concat([Buffer.from([0xff, 0xd8, 0xff, 0xe0, 0x00, 0x02]), Buffer.alloc(300, 0x11)]), 400, 'thumb_corrupt'],
    ['دو SOF (سرآیندِ دومِ ابعاد)', F.jpeg(320, 240, { seed: 26, sof: [4000, 3000] }), 400, 'thumb_corrupt'],
    ['بریده (بی EOI)', F.jpeg(320, 240, { seed: 26, eoi: false }), 400, 'thumb_corrupt'],
  ];
  for (const [label, buf, status, code] of cases) {
    const b = await thumbPut(s, 'rice', buf, before.hash);
    assert.deepEqual([b.status, errCode(b)], [status, code], `${label}: ${JSON.stringify(b.body).slice(0, 200)}`);
    assert.equal(await noThumb(s), 0, `${label}: هیچ کارتی ننشست`);
    assert.equal((await list(s)).head, before.rev, `${label}: rev همان`);
    assert.equal((await original(s, 'rice')).hash, before.hash, `${label}: اصل همان`);
  }
  //  نارشته (آرایه/شیء/عدد/تهی) ⇒ ۴۰۰ بی رمزگشایی
  for (const odd of [[1, 2, 3], { a: 1 }, 42, '', null]) {
    const b = await h.api('PUT', '/api/sync/v1/photos/rice/thumb', { token: s.token, headers: SYNC, body: { data: odd, of: before.hash } });
    assert.deepEqual([b.status, errCode(b)], [400, 'thumb_empty'], JSON.stringify(odd));
  }
  //  درِ عکس فیلدِ `thumb` را نمی‌شناسد (همان پیشین): نادیده، بی کارت
  const ignored = await h.api('PUT', '/api/sync/v1/photos/rice', { token: s.token, headers: SYNC,
    body: { data: full.toString('base64'), thumb: F.jpeg(320, 240, { seed: 32 }).toString('base64') } });
  assert.deepEqual([ignored.status, 'thumb' in ignored.body, await noThumb(s)], [200, false, 0]);
  //  بزرگ‌تر از اصل (اصلِ ۳۰۰×۲۰۰ و کارتِ ۳۲۰×۲۱۳)
  const small = await syncPut(s, 'oil', F.jpeg(300, 200, { seed: 27 }));
  const up = await thumbPut(s, 'oil', F.jpeg(320, 213, { seed: 28 }), small.body.hash);
  assert.deepEqual([up.status, errCode(up)], [400, 'thumb_upscaled'], JSON.stringify(up.body));
  assert.equal(await noThumb(s), 0);
  //  اصلِ عمودیِ دوربین (EXIF ۶ ⇒ ایستاده ۶۰۰×۸۰۰): کارتِ ۲۴۰×۳۲۰ درست است، ۳۲۰×۲۴۰ نه
  const camera = await syncPut(s, 'oil', F.jpeg(800, 600, { seed: 29, orientation: 6 }));
  const wrong = await thumbPut(s, 'oil', F.jpeg(320, 240, { seed: 30 }), camera.body.hash);
  assert.deepEqual([wrong.status, errCode(wrong)], [400, 'thumb_aspect_mismatch']);
  assert.equal((await thumbPut(s, 'oil', F.jpeg(240, 320, { seed: 31 }), camera.body.hash)).status, 200);
  //  کتابخانه هم همان (برای هر راهِ دیگری که روزی کارت بگیرد)
  assert.throws(() => photos.checkThumb(gif, full), (e) => e.code === 'thumb_bad_type' && e.status === 415);
  assert.throws(() => photos.checkThumb(Buffer.alloc(photos.THUMB_MAX_BYTES + 1, 0xff), full), (e) => e.code === 'thumb_too_large');
});

test('درِ کارت: فقط اصلِ امروز، بی‌اثر در تکرار، بی بالا بردنِ rev؛ نشانیِ تازه', async () => {
  const s = await liveStore('کارتِ کهنه');
  const full = F.jpeg(800, 600, { seed: 41 });
  const put = await syncPut(s, 'rice', full);
  assert.equal(put.status, 200);
  assert.equal((await list(s)).items[0].thumb, false, 'عکسِ بی کارت در فهرست');
  const head = (await list(s)).head;
  const vNo = await versionOf(s, 'rice');
  const card = F.jpeg(320, 240, { seed: 42 });

  for (const [of, status, code] of [['0'.repeat(32), 409, 'thumb_stale'], ['xyz', 400, 'thumb_bad_of'], [undefined, 400, 'thumb_bad_of']]) {
    const r = await thumbPut(s, 'rice', card, of);
    assert.deepEqual([r.status, errCode(r)], [status, code], JSON.stringify(r.body));
  }
  assert.deepEqual([(await thumbPut(s, 'oil', card, put.body.hash)).status], [404], 'بی عکس ⇒ ۴۰۴');
  const r1 = await thumbPut(s, 'rice', card, put.body.hash);
  assert.equal(r1.status, 200, JSON.stringify(r1.body));
  assert.deepEqual([r1.body.of, r1.body.width, r1.body.height, r1.body.mime, r1.body.unchanged], [put.body.hash, 320, 240, 'image/jpeg', false]);
  const r2 = await thumbPut(s, 'rice', card, put.body.hash);
  assert.deepEqual([r2.status, r2.body.unchanged, r2.body.hash], [200, true, r1.body.hash], 'همان کارت دوباره ⇒ بی‌اثر');
  const after = await list(s);
  assert.equal(after.head, head, 'rev بالا نرفت (دستگاه‌های دیگر چیزی نمی‌گیرند)');
  assert.equal(after.items[0].thumb, true);

  const vYes = await versionOf(s, 'rice');
  assert.notEqual(vYes, vNo, 'رسیدنِ کارت ⇒ نشانیِ تازه');
  const t = await pic(s, `/products/rice/thumb?v=${vYes}`);
  assert.deepEqual([t.status, t.cache], [200, LONG]);
  assert.ok(t.buf.equals(bytes.stripMetadata(card)));
  //  کارتِ دیگر برای همان اصل ⇒ جایگزین (نه دو ردیف)
  const card2 = F.jpeg(320, 240, { seed: 43 });
  const r3 = await thumbPut(s, 'rice', card2, put.body.hash);
  assert.equal(r3.body.unchanged, false);
  assert.equal((await one(`SELECT COUNT(*)::int AS n FROM product_photo_thumbs WHERE account_id=$1`, [s.shopId])).n, 1);
  assert.ok((await pic(s, '/products/rice/thumb')).buf.equals(bytes.stripMetadata(card2)));
});

test('بازگشت به عکسِ کامل: بی کارت، کارتِ اصلِ پیشین، کارتِ ناخوانا؛ عکسِ برداشته ⇒ ۴۰۴', async () => {
  const s = await liveStore('بازگشت');
  const full1 = F.jpeg(800, 600, { seed: 51 });
  await syncPut(s, 'rice', full1);
  //  بی کارت ⇒ همان عکسِ کامل، همان نسخهٔ پیشین (نشانی‌های امروز نمی‌شکنند)
  const v0 = await versionOf(s, 'rice');
  const t0 = await pic(s, `/products/rice/thumb?v=${v0}`);
  assert.deepEqual([t0.status, t0.cache], [200, LONG]);
  assert.ok(t0.buf.equals(full1));
  //  کارت رسید، بعد اصلِ تازه بی کارت ⇒ کارتِ اصلِ پیشین هرگز سرو نمی‌شود و ردیفش می‌رود
  const card1 = F.jpeg(320, 240, { seed: 52 });
  await withThumb(s, 'rice', full1, card1);
  const full2 = F.jpeg(800, 600, { seed: 53 });
  assert.equal((await syncPut(s, 'rice', full2)).status, 200);
  assert.equal((await list(s)).items[0].thumb, false);
  const t2 = await pic(s, '/products/rice/thumb');
  assert.equal(t2.status, 200);
  assert.ok(t2.buf.equals(full2), 'اصلِ تازه، نه کارتِ اصلِ پیشین');
  assert.equal(await one(`SELECT 1 FROM product_photo_thumbs WHERE account_id=$1 AND product_id='rice'`, [s.shopId]), null);
  assert.equal((await thumbPut(s, 'rice', card1, (await syncPut(s, 'rice', full1)).body.hash)).status, 200);
  //  کارتِ کهنه‌شده (ردیفی که هنوز به اصلِ دیگری اشاره دارد) هم سرو نمی‌شود
  await query(`UPDATE product_photo_thumbs SET of_hash = $2 WHERE account_id = $1`, [s.shopId, 'f'.repeat(32)]);
  assert.ok((await pic(s, '/products/rice/thumb')).buf.equals(full1));
  assert.equal((await list(s)).items[0].thumb, false, 'و برنامه دوباره می‌سازد');
  //  کارتِ ناخوانا (شیءِ گم‌شده یا مالِ دکانِ دیگر) ⇒ همان عکسِ کامل، نه خطا
  const h1 = (await original(s, 'rice')).hash;
  await query(`UPDATE product_photo_thumbs SET of_hash = $2, data = NULL, store_key = $3 WHERE account_id = $1`,
    [s.shopId, h1, `i1/${'0'.repeat(16)}/${'1'.repeat(32)}.${'2'.repeat(16)}`]);
  const broken = await pic(s, '/products/rice/thumb');
  assert.equal(broken.status, 200);
  assert.ok(broken.buf.equals(full1), 'هرگز عکسِ شکسته');
  //  عکس برداشته ⇒ کارت هم می‌رود و هر نشانی ۴۰۴
  const del = await h.api('DELETE', '/api/sync/v1/photos/rice', { token: s.token, headers: SYNC });
  assert.equal(del.status, 200);
  assert.equal(await one(`SELECT 1 FROM product_photo_thumbs WHERE account_id=$1`, [s.shopId]), null);
  assert.equal((await pic(s, '/products/rice/thumb')).status, 404);
  assert.equal((await pic(s, '/products/rice/photo')).status, 404);
});

test('جداییِ دکان‌ها: «ب» با شناسه و hash ِ «الف» کارتِ «الف» را نه می‌نویسد نه می‌خواند', async () => {
  const A = await liveStore('الف');
  const B = await liveStore('ب');
  const fullA = F.jpeg(800, 600, { seed: 61 });
  const cardA = F.jpeg(320, 240, { seed: 62 });
  const pa = (await withThumb(A, 'rice', fullA, cardA)).put;
  //  «ب» عکسی برای rice ندارد ⇒ ۴۰۴، و کارتِ «الف» همان
  const evil = F.jpeg(320, 240, { seed: 63 });
  const w = await thumbPut(B, 'rice', evil, pa.body.hash);
  assert.equal(w.status, 404, JSON.stringify(w.body));
  assert.ok((await pic(A, '/products/rice/thumb')).buf.equals(bytes.stripMetadata(cardA)));
  //  «ب» عکسِ خودش را برای همان شناسه می‌گذارد ⇒ ردیفِ جدا؛ هیچ‌کدام دیگری را نمی‌بیند
  const fullB = F.jpeg(800, 600, { seed: 64 });
  const pb = (await withThumb(B, 'rice', fullB, evil)).put;
  assert.ok((await pic(A, '/products/rice/thumb')).buf.equals(bytes.stripMetadata(cardA)), '«الف» همان');
  assert.ok((await pic(B, '/products/rice/thumb')).buf.equals(bytes.stripMetadata(evil)), '«ب» مالِ خودش');
  //  ویترینِ «ب» با hash ِ «الف» در `?v=` هم فقط کارتِ خودش را می‌دهد (و کوتاه)
  const crossV = await pic(B, `/products/rice/thumb?v=${await versionOf(A, 'rice')}`);
  assert.deepEqual([crossV.cache, crossV.buf.equals(bytes.stripMetadata(evil))], [SHORT, true]);
  //  «ب» با of ِ «الف» روی عکسِ خودش ⇒ ۴۰۹، نه نوشتن
  const stale = await thumbPut(B, 'rice', evil, pa.body.hash);
  assert.deepEqual([stale.status, errCode(stale)], [409, 'thumb_stale']);
  //  کتابخانه: همان شناسه با حسابِ دیگر ⇒ مالِ همان حساب
  const viaA = await photos.forStore({ app: 'shop', accountId: A.shopId }, 'rice', 'thumb');
  const viaB = await photos.forStore({ app: 'shop', accountId: B.shopId }, 'rice', 'thumb');
  assert.ok(viaA.data.equals(bytes.stripMetadata(cardA)) && viaB.data.equals(bytes.stripMetadata(evil)));
  assert.equal(await photos.forStore({ app: 'pump', accountId: A.shopId }, 'rice', 'thumb'), null, 'برنامهٔ دیگر هم نه');
  await assert.rejects(photos.putThumb({ app: 'pump', accountId: A.shopId }, 'rice', evil, pa.body.hash), (e) => e.status === 403 && e.code === 'shop_only');
  const rows = await query(`SELECT account_id, of_hash FROM product_photo_thumbs WHERE account_id = ANY($1::text[]) ORDER BY account_id`,
    [[A.shopId, B.shopId]]);
  assert.equal(rows.rows.length, 2);
  assert.deepEqual(rows.rows.map((r) => r.of_hash).sort(), [pa.body.hash, pb.body.hash].sort());
});

test('سرآیندها: نسخهٔ همان ⇒ یک روز، کهنه/بی نسخه ⇒ ۵ دقیقه، ETag ِ کارت و ۳۰۴؛ نوعِ واقعی و nosniff', async () => {
  const s = await liveStore('سرآیند');
  const full = F.jpeg(800, 600, { seed: 71 });
  await syncPut(s, 'rice', full);
  const vOld = await versionOf(s, 'rice');
  const card = F.jpeg(320, 240, { seed: 72 });
  const { put } = await withThumb(s, 'rice', full, card);
  const v = await versionOf(s, 'rice');
  assert.match(v, /^[0-9a-f]{12}$/);
  assert.notEqual(v, vOld);
  const hit = await pic(s, `/products/rice/thumb?v=${v}`);
  assert.deepEqual([hit.status, hit.cache, hit.type, hit.nosniff], [200, LONG, 'image/jpeg', 'nosniff']);
  const thumbHash = (await one(`SELECT hash FROM product_photo_thumbs WHERE account_id=$1`, [s.shopId])).hash;
  assert.equal(hit.etag, `"${thumbHash}"`, 'ETag ِ خودِ کارت، نه عکسِ کامل');
  const fullTag = await pic(s, `/products/rice/photo?v=${v}`);
  assert.deepEqual([fullTag.status, fullTag.cache, fullTag.etag], [200, LONG, `"${put.body.hash}"`]);
  assert.equal((await pic(s, `/products/rice/thumb?v=${v}`, { 'If-None-Match': hit.etag })).status, 304);
  assert.equal((await pic(s, `/products/rice/thumb?v=${v}`, { 'If-None-Match': fullTag.etag })).status, 200, 'ETag ِ عکسِ کامل ≠ کارت');
  for (const q of ['', `?v=${vOld}`, '?v=000000000000', `?v=${v}&v=${v}`]) {
    const r = await pic(s, `/products/rice/thumb${q}`);
    assert.deepEqual([r.status, r.cache], [200, SHORT], `«${q}» ⇒ همان کارت با ۵ دقیقه`);
    assert.ok(r.buf.equals(hit.buf));
  }
  //  نشانیِ کهنه (پیش از کارت) دیگر عکسِ کامل را یک روز نگه نمی‌دارد
  assert.equal((await pic(s, `/products/rice/photo?v=${vOld}`)).cache, SHORT);
});

test('انبارِ شیء: شیءِ کارت ردیف دارد (gc نگهش می‌دارد)، و با اصلِ تازه یا برداشتن پاک می‌شود', async () => {
  const config = require('../src/config');
  const store = require('../src/lib/image-store');
  const A = require('../src/lib/image-store-adapters');
  const move = require('../src/lib/image-store-move');
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'thumb-store-'));
  const prev = { ...config.imageStore };
  Object.assign(config.imageStore, { mode: 'disk', read: '', dir });
  const disk = A.disk({ dir });
  store.useBackend(disk);
  try {
    assert.ok(move.TABLES.some((t) => t.table === 'product_photo_thumbs'), 'جدولِ کارت در فهرستِ انبار');
    const ctx = { app: 'shop', accountId: `acct-${uniq()}` };
    const card = F.jpeg(320, 240, { seed: 82 });
    const out = await photos.put(ctx, 'p1', F.jpeg(800, 600, { seed: 81 }), 'dev');
    assert.equal((await photos.putThumb(ctx, 'p1', card, out.hash)).unchanged, false);
    const row = await one(`SELECT data, store_key FROM product_photo_thumbs WHERE account_id=$1`, [ctx.accountId]);
    assert.equal(row.data, null);
    assert.ok(store.owns(store.accountTenant('shop', ctx.accountId), row.store_key), 'کلیدِ همان tenant');
    assert.ok((await photos.forStore(ctx, 'p1', 'thumb')).data.equals(bytes.stripMetadata(card)));
    const gc = await move.gc({ olderThanMs: 0, now: Date.now() + 60_000, apply: true });
    assert.equal(gc.orphans, 0, JSON.stringify(gc));
    assert.ok((await disk.list()).some((o) => o.key === row.store_key), 'gc شیءِ کارت را نگه داشت');
    //  اصلِ تازه بی کارت ⇒ ردیف و شیءِ کارتِ پیشین هر دو می‌روند
    await photos.put(ctx, 'p1', F.jpeg(800, 600, { seed: 83 }), 'dev');
    assert.equal(await one(`SELECT 1 FROM product_photo_thumbs WHERE account_id=$1`, [ctx.accountId]), null);
    assert.ok(!(await disk.list()).some((o) => o.key === row.store_key), 'شیءِ کارتِ پیشین پاک شد');
    //  کارتِ تازه با درِ کارت، بعد برداشتنِ عکس ⇒ شیء پاک
    const hash = (await photos.get(ctx, 'p1')).hash;
    await photos.putThumb(ctx, 'p1', F.jpeg(320, 240, { seed: 84 }), hash);
    const k2 = (await one(`SELECT store_key FROM product_photo_thumbs WHERE account_id=$1`, [ctx.accountId])).store_key;
    assert.ok(k2);
    await photos.remove(ctx, 'p1', 'dev');
    assert.ok(!(await disk.list()).some((o) => o.key === k2));
  } finally {
    Object.assign(config.imageStore, prev);
    store.useBackend(null);
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test('پمپ: دستگاهِ پمپ روی درِ کارت ۴۰۳ می‌گیرد؛ عکسِ پمپ همان پاسخ و فهرستِ پیش از ۳۵۰ (بی کلیدِ thumb)', async () => {
  const code = await h.post('/api/admin/pump/vip-codes', { plan: 'custom', days: 30 }, { token: admin });
  assert.equal(code.status, 201, JSON.stringify(code.body));
  const act = await h.post('/api/pump/device/activate', {
    code: code.body.code, device: { uid: `pc-thumb-${uniq()}`, name: 'کامپیوترِ پمپ', platform: 'windows' },
    station: { code: `st-thumb-${uniq()}`, name: 'پمپِ کارت' },
  });
  assert.equal(act.status, 201, JSON.stringify(act.body));
  const PUMP = { 'X-App': 'pump', 'X-Device': 'pc-thumb' };
  const pump = (method, path, body) => h.api(method, path, { token: act.body.deviceToken, headers: PUMP, body });
  //  درِ عکسِ پمپ (`routes/sync-v1.js`، دست‌نخورده): همان کلیدهای پاسخ و فهرست
  const full = F.jpeg(800, 600, { seed: 95 });
  const put = await pump('PUT', '/api/sync/v1/photos/pp1', { data: full.toString('base64'), device_id: 'pc-thumb' });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  assert.deepEqual(Object.keys(put.body).sort(), ['hash', 'ok', 'product_id', 'rev', 'size']);
  const ls = await pump('GET', '/api/sync/v1/photos?since=0');
  assert.equal(ls.status, 200);
  assert.deepEqual(ls.body.items.map((i) => Object.keys(i).sort()), [['deleted', 'hash', 'product_id', 'rev', 'size', 'updated_at']]);
  //  درِ کارت: فقط دکان ⇒ ۴۰۳، حتی با of ِ درست؛ هیچ ردیفی برای پمپ
  for (const of of [put.body.hash, '0'.repeat(32), undefined]) {
    const r = await pump('PUT', '/api/sync/v1/photos/pp1/thumb', { data: F.jpeg(320, 240, { seed: 96 }).toString('base64'), of });
    assert.deepEqual([r.status, errCode(r)], [403, 'shop_only'], JSON.stringify(r.body));
  }
  assert.equal((await one(`SELECT COUNT(*)::int AS n FROM product_photo_thumbs WHERE app <> 'shop'`)).n, 0);
  //  برداشتن هم همان پاسخ
  const del = await pump('DELETE', '/api/sync/v1/photos/pp1?device_id=pc-thumb');
  assert.equal(del.status, 200);
  assert.deepEqual(Object.keys(del.body).sort(), ['ok', 'product_id', 'removed', 'rev']);
  //  بی توکن ⇒ ۴۰۱ (سقفِ آی‌پی و احراز پیش از «فقط دکان»)
  assert.equal((await h.api('PUT', '/api/sync/v1/photos/pp1/thumb', { headers: SYNC, body: { data: 'x', of: put.body.hash } })).status, 401);
});

test('مهاجرتِ ۳۵۰: جدول و قیدها هست؛ rollback برش می‌دارد و ردیفِ خودش را پاک می‌کند؛ دوباره ساخته می‌شود', async () => {
  const has = async () => !!(await one(`SELECT 1 AS x FROM information_schema.tables WHERE table_schema='public' AND table_name='product_photo_thumbs'`));
  assert.equal(await has(), true);
  //  قیدها: بی بایت، ابعادِ بیش از ۴۰۰، یا نوعِ دیگر ⇒ پایگاه هم نمی‌پذیرد
  const ctx = { app: 'shop', accountId: `mig-${uniq()}` };
  const p = await photos.put(ctx, 'm1', F.jpeg(800, 600, { seed: 91 }), 'dev');
  const ins = (cols) => query(`INSERT INTO product_photo_thumbs (app, account_id, product_id, of_hash, data, store_key, hash, mime, width, height, size, updated_at)
    VALUES ('shop', $1, 'm1', $2, $3, $4, 'h', $5, $6, $7, $8, 0)`, [ctx.accountId, p.hash, ...cols]);
  await assert.rejects(ins([null, '', 'image/jpeg', 320, 240, 100]), /product_photo_thumbs_bytes/);
  await assert.rejects(ins([Buffer.from('x'), '', 'image/jpeg', 401, 240, 100]), /product_photo_thumbs_dims/);
  await assert.rejects(ins([Buffer.from('x'), '', 'image/gif', 320, 240, 100]), /product_photo_thumbs_mime/);
  await assert.rejects(ins([Buffer.from('x'), '', 'image/jpeg', 320, 240, 70000]), /product_photo_thumbs_size/);
  await assert.rejects(query(`INSERT INTO product_photo_thumbs (app, account_id, product_id, of_hash, data, hash, mime, width, height, size, updated_at)
    VALUES ('pump', $1, 'm1', 'x', 'x', 'h', 'image/jpeg', 1, 1, 1, 0)`, [ctx.accountId]), /product_photo_thumbs_shop/, 'پمپ هرگز کارت ندارد');
  await assert.rejects(query(`INSERT INTO product_photo_thumbs (app, account_id, product_id, of_hash, data, hash, mime, width, height, size, updated_at)
    VALUES ('shop', $1, 'no-photo', 'x', 'x', 'h', 'image/jpeg', 1, 1, 1, 0)`, [ctx.accountId]), /foreign key/);

  const dir = path.join(__dirname, '..', 'migrations');
  await query(fs.readFileSync(path.join(dir, 'rollback', '350_product_photo_thumbs.down.sql'), 'utf8'));
  assert.equal(await has(), false);
  assert.equal(await one(`SELECT 1 FROM schema_migrations WHERE version='350_product_photo_thumbs.sql'`), null);
  assert.ok(await photos.get(ctx, 'm1'), 'عکسِ اصل دست نخورد');
  await require('../src/migrate').run();
  assert.equal(await has(), true, 'مهاجرت دوباره نشست');
  assert.ok(await one(`SELECT 1 FROM schema_migrations WHERE version='350_product_photo_thumbs.sql'`));
  const again = await photos.putThumb(ctx, 'm1', F.jpeg(320, 240, { seed: 92 }), p.hash);
  assert.equal(again.unchanged, false);
});
