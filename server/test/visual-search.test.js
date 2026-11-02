'use strict';
/**
 * AI-11 روی API و دیتابیسِ واقعی (PGlite): «جست‌وجو با عکس» ِ کالاهای مشابه (`lib/visual-search.js`).
 * مجموعهٔ مصنوعیِ قطعیِ IMG-15 (`image-fixtures.js`): همان صحنه با رمزگذار/اندازهٔ دیگر، جابه‌جاییِ کوچک، تار،
 * صحنهٔ دیگر و نسخهٔ تاریک. رتبه، آستانه، جداییِ دکان‌ها، ذخیره‌نشدنِ عکسِ پرسش، پرکردنِ دسته‌ای و بی‌تکرار،
 * پرچمِ خاموش ⇒ همان پاسخ، و برچسبی که هرگز ادعای یکی بودن نمی‌کند.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const F = require('./image-fixtures');
const { query, one, many, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const vs = require('../src/lib/visual-search');
const quality = require('../src/lib/image-quality');
const bytes = require('../src/lib/image-bytes');

const W = 800, H = 600;
const png = (opts) => F.pngImage(W, H, F.scene(W, H, opts));
const IMG = {
  base: png(),
  shift: png({ box: [0.28, 0.22, 0.78, 0.82] }),
  shift2: png({ box: [0.32, 0.26, 0.82, 0.86] }),
  blur: F.pngImage(400, 300, F.blurred(400, 300, F.scene(400, 300), 3)),
  other: png({ box: [0.05, 0.1, 0.45, 0.5] }),
  dark: png({ bg: 25, lo: 0, hi: 60 }),
};
//  پرسش‌ها: همان صحنه با JPEG ِ رنگیِ ۴:۲:۰ و اندازهٔ دیگر؛ صحنهٔ درشت‌خانه (هیچ کالایی درونِ آستانه نیست)؛ نوارِ پهن
const Q_SAME = F.jpegImage(640, 480, F.scene(640, 480), { quality: 80, color: true });
const Q_NONE = png({ cells: 8 });
const Q_WIDE = png({ box: [0.1, 0.4, 0.9, 0.6] });

let root = '';
let A; let B;
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: root });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}
async function shopOwner(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { token: u.accessToken, shopId: made.body.shop.id };
}
async function pushProducts(s, ids) {
  const dev = `dev-${uniq()}`;
  const p = await h.api('POST', '/api/sync/v1/push', {
    token: s.token, headers: { 'X-App': 'shop', 'X-Device': dev },
    body: { device_id: dev, schema_version: 2, ops: ids.map((id) => ({
      op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: id, type: 'insert',
      fields: { name: `کالای ${id}`, unit: 'عدد', salePrice: 100, category: 'آزمون' },
    })) },
  });
  assert.equal(p.status, 200, JSON.stringify(p.body));
}
async function putPhoto(shopId, productId, buf) {
  await query(
    `INSERT INTO product_photos (app, account_id, product_id, data, hash, size, rev, updated_at)
     VALUES ('shop',$1,$2,$3,'',$4,nextval('product_photo_rev'),$5)
     ON CONFLICT (app, account_id, product_id) DO UPDATE SET data=EXCLUDED.data, size=EXCLUDED.size, rev=EXCLUDED.rev`,
    [shopId, productId, buf, buf.length, now()]);
}
const search = (who, buf, qs = '') => h.raw('POST', `/api/shop/visual-search${qs}`, buf, { token: who.token });
const ids = (r) => r.body.items.map((x) => x.product_id);
const nIndex = async (shopId) => Number((await one('SELECT COUNT(*)::int AS n FROM product_visual_hashes WHERE shop_id=$1', [shopId])).n);

/** شمارِ ردیف‌های همهٔ جدول‌ها — هیچ درجی از عکسِ پرسش */
async function rowCounts() {
  const tables = await many(`SELECT table_name FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE' ORDER BY 1`);
  const out = {};
  for (const t of tables) out[t.table_name] = Number((await one(`SELECT COUNT(*)::int AS n FROM "${t.table_name}"`)).n);
  return out;
}

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'root','ارشد',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Root!12345'), now()]);
  root = (await h.post('/api/admin/login', { username: 'root', password: 'Root!12345' })).body.token;
  A = await shopOwner('الف');
  B = await shopOwner('ب');
  await pushProducts(A, [...Object.keys(IMG).map((k) => `p-${k}`), 'p-nophoto']);
  for (const [k, buf] of Object.entries(IMG)) await putPhoto(A.shopId, `p-${k}`, buf);
  await pushProducts(B, ['q-base']);
  await putPhoto(B.shopId, 'q-base', IMG.base);
});
test.after(async () => { await h.stop(); });
test.beforeEach(() => resetAll());

test('AI-11 خالص: همان dHash ِ IMG-15، هیستوگرامِ ۸ خانه، و آستانهٔ همینگ/هیستوگرام با مرزِ دقیق', () => {
  for (const buf of [IMG.base, Q_SAME, IMG.blur]) {
    const info = bytes.inspect(buf);
    assert.equal(quality.fingerprint(buf, info).dhash, quality.analyze(buf, info).dhash, 'همان هشِ «مشابهِ احتمالی»');
  }
  assert.equal(quality.fingerprint(F.pngImage(200, 200, () => 128), bytes.inspect(F.pngImage(200, 200, () => 128))), null, 'یک‌رنگ');
  const q = { dhash: '0000000000000000', hist: '0f00000000000055' };
  const bits = (n) => (n === 64 ? 'ffffffffffffffff' : (2n ** BigInt(n) - 1n).toString(16).padStart(16, '0'));
  const L = vs.LIMITS;
  const rows = [
    { product_id: 'edge', dhash: bits(L.HAMMING_MAX), hist: q.hist },
    { product_id: 'over', dhash: bits(L.HAMMING_MAX + 1), hist: q.hist },
    { product_id: 'twin-b', dhash: bits(0), hist: '0f00000000000055' },
    { product_id: 'twin-a', dhash: bits(0), hist: '0f00000000000055' },
    { product_id: 'near-hist', dhash: bits(1), hist: '0a00000000000050' },   // L1 = ۱۰
    { product_id: 'hist-edge', dhash: bits(2), hist: '3700000000000023' },   // L1 = ۴۰ + ۵۰ = ۹۰ (درون)
    { product_id: 'hist-over', dhash: bits(0), hist: '5f00000000000005' },   // L1 = ۸۰ + ۸۰ = ۱۶۰
    { product_id: 'unhashed', dhash: '-', hist: '' },
  ];
  assert.equal(vs.histL1(q.hist, '0a00000000000050'), 10);
  assert.equal(vs.histL1(q.hist, '5f00000000000005'), 160);
  assert.ok(vs.histL1(q.hist, '3700000000000023') <= L.HIST_MAX);
  const r = vs.rank(q, rows);
  assert.deepEqual(r.map((x) => x.product_id), ['twin-a', 'twin-b', 'near-hist', 'hist-edge', 'edge'],
    'همینگ، سپس L1، سپس شناسه؛ مرزِ آستانه درون، یک بیت بیشتر بیرون، هیستوگرامِ دور بیرون');
  assert.equal(r.find((x) => x.product_id === 'edge').distance, L.HAMMING_MAX);
});

test('AI-11: پرچمِ خاموش ⇒ همان ۴۰۴ ِ مسیرِ ناشناختهٔ امروز، بی خواندنِ عکس و بی نمایه', async () => {
  const off = await search(A, Q_SAME);
  const unknown = await h.raw('POST', '/api/shop/no-such-route-ai11', Q_SAME, { token: A.token });
  assert.equal(off.status, unknown.status);
  assert.deepEqual(off.body, unknown.body);
  const anon = await h.raw('POST', '/api/shop/visual-search', Q_SAME);
  assert.equal(anon.status, 401, 'بی توکن ⇒ ۴۰۱ مثلِ هر درِ دکان');
  assert.deepEqual(await vs.backfill(A.shopId), { hashed: 0, removed: 0, more: false, enabled: false });
  assert.deepEqual(await vs.sweep(), { hashed: 0 });
  assert.equal(await nIndex(A.shopId), 0, 'خاموش ⇒ هیچ ردیفی');
  //  ویترین هم: پرچمِ خاموش ⇒ هیچ نمایه‌ای ساخته نمی‌شود
  assert.equal(Number((await one('SELECT COUNT(*)::int AS n FROM product_visual_hashes')).n), 0);
});

test('AI-11: پرکردنِ دسته‌ای — سقفِ هر دسته، بی‌تکرار، منبعِ تازه دوباره، منبعِ رفته پاک، فقط دکانِ روشن', async () => {
  await setFlag(A.shopId, vs.FLAG, true);
  const total = Object.keys(IMG).length;
  const r1 = await vs.backfill(A.shopId, { limit: 2 });
  assert.deepEqual(r1, { hashed: 2, removed: 0, more: true, enabled: true }, 'سقفِ دسته');
  assert.equal(await nIndex(A.shopId), 2);
  const r2 = await vs.backfill(A.shopId, { limit: 50 });
  assert.equal(r2.hashed, total - 2);
  assert.equal(r2.more, false);
  const snap = await many('SELECT product_id, src, dhash, hist, updated_at FROM product_visual_hashes WHERE shop_id=$1 ORDER BY 1', [A.shopId]);
  assert.equal(snap.length, total, 'کالای بی عکس ردیف ندارد');
  assert.ok(snap.every((x) => /^[0-9a-f]{16}$/.test(x.dhash) && /^[0-9a-f]{16}$/.test(x.hist)));
  assert.deepEqual(await vs.backfill(A.shopId, { limit: 50 }), { hashed: 0, removed: 0, more: false, enabled: true }, 'بی‌تکرار');
  assert.deepEqual(await many('SELECT product_id, src, dhash, hist, updated_at FROM product_visual_hashes WHERE shop_id=$1 ORDER BY 1', [A.shopId]), snap);
  assert.equal(await nIndex(B.shopId), 0, 'دکانِ پرچم‌خاموش دست نمی‌خورد');

  //  عکسِ تازه برای یک کالا ⇒ فقط همان یکی دوباره؛ عکسِ حذف‌شده ⇒ ردیف پاک
  await putPhoto(A.shopId, 'p-shift2', IMG.shift);
  assert.deepEqual(await vs.backfill(A.shopId), { hashed: 1, removed: 0, more: false, enabled: true });
  await putPhoto(A.shopId, 'p-shift2', IMG.shift2);
  await putPhoto(A.shopId, 'p-gone', IMG.other);
  assert.deepEqual(await vs.backfill(A.shopId), { hashed: 2, removed: 0, more: false, enabled: true }, 'p-shift2 دوباره و p-gone ِ تازه');
  await query(`UPDATE product_photos SET data=NULL WHERE app='shop' AND account_id=$1 AND product_id='p-gone'`, [A.shopId]);
  assert.deepEqual(await vs.backfill(A.shopId), { hashed: 0, removed: 1, more: false, enabled: true }, 'عکسِ حذف‌شده ⇒ ردیف پاک');
  assert.equal(await nIndex(A.shopId), total);

  //  کارِ دوره‌ای: فقط دکان‌های روشن، با سقفِ کلِ نوبت
  await setFlag(B.shopId, vs.FLAG, true);
  assert.deepEqual(await vs.sweep({ budget: 5 }), { hashed: 1 });
  assert.equal(await nIndex(B.shopId), 1);
  assert.deepEqual(await vs.sweep(), { hashed: 0 }, 'نوبتِ دوم هیچ');
});

test('AI-11: رتبه روی مجموعهٔ مصنوعی، آستانه، وتوی هیستوگرام و برچسبِ «مشابه»', async () => {
  const r = await search(A, Q_SAME);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(ids(r), ['p-base', 'p-shift', 'p-shift2', 'p-blur'], 'همان صحنه، جابه‌جاییِ کوچک، بیشتر، تار؛ صحنهٔ دیگر و تاریک نه');
  assert.deepEqual(r.body.items.map((x) => x.distance), [0, 1, 4, 4]);
  assert.ok(r.body.items.every((x) => x.label === 'مشابه' && x.name === `کالای ${x.product_id}` && x.similarity <= 100));
  assert.equal(r.body.label, 'مشابه');
  assert.deepEqual(r.body.threshold, { hamming_max: vs.LIMITS.HAMMING_MAX, hist_max: vs.LIMITS.HIST_MAX });
  assert.equal(r.body.indexed, Object.keys(IMG).length);
  assert.deepEqual(Object.keys(r.body).sort(), ['indexed', 'items', 'label', 'notice', 'threshold'], 'هیچ «انتخاب‌شده» یا افزودنِ خودکار');
  assert.doesNotMatch(JSON.stringify(r.body), /همان|یکسان|عینِ|خودِ همین/, 'هرگز ادعای یکی بودن');
  assert.doesNotMatch(vs.NOTICE + vs.LABEL, /همان/);

  assert.deepEqual(ids(await search(A, Q_SAME, '?limit=2')), ['p-base', 'p-shift']);
  const none = await search(A, Q_NONE);
  assert.equal(none.status, 200);
  assert.deepEqual(none.body.items, [], 'بیرون از آستانه هیچ (نزدیک‌ترین همینگ ۱۳ > ۱۰)');
  //  نوارِ پهن: صحنهٔ دیگر (همینگ ۶) درون؛ نسخهٔ تاریک با همینگ ۸ ولی هیستوگرامِ دور (L1 ≈ ۱۸۴) بیرون
  assert.deepEqual(ids(await search(A, Q_WIDE)), ['p-other']);
});

test('AI-11: سنجشِ عکسِ پرسش مثلِ درِ بارگذاری، و هیچ ردیفی از آن ذخیره نمی‌شود', async () => {
  const before = await rowCounts();
  const indexBefore = await many('SELECT * FROM product_visual_hashes ORDER BY shop_id, product_id');
  assert.equal((await search(A, Q_SAME)).status, 200);
  assert.equal((await search(A, Q_WIDE)).status, 200);
  assert.deepEqual(await rowCounts(), before, 'هیچ درجی در هیچ جدولی');
  assert.deepEqual(await many('SELECT * FROM product_visual_hashes ORDER BY shop_id, product_id'), indexBefore, 'نمایه دست نخورد');

  const empty = await search(A, Buffer.alloc(0));
  assert.equal(empty.status, 400);
  assert.equal(empty.body.error.code, 'image_empty');
  const junk = await search(A, Buffer.from('not an image at all, just text bytes'));
  assert.equal(junk.status, 400, JSON.stringify(junk.body));
  const big = Buffer.concat([Q_SAME, Buffer.alloc(require('../src/lib/product-photos').MAX_BYTES)]);
  assert.equal((await search(A, big)).status, 413, 'بیش از سقفِ عکسِ کالا');
  const blank = await search(A, F.pngImage(300, 300, () => 200));
  assert.equal(blank.status, 422);
  assert.equal(blank.body.error.code, 'image_unmeasurable');
  assert.equal((await search(A, F.webp(320, 240))).body.error.code, 'image_unmeasurable', 'WebP رمزگشایی نمی‌شود ⇒ ۴۲۲، نه حدس');
});

test('AI-11: فقط کالاهای همان دکان — عکسِ یکسان در دکانِ دیگر هرگز نمی‌آید', async () => {
  const a = await search(A, Q_SAME);
  assert.ok(!ids(a).includes('q-base'));
  const b = await search(B, Q_SAME);
  assert.equal(b.status, 200, JSON.stringify(b.body));
  assert.deepEqual(ids(b), ['q-base']);
  assert.equal(b.body.indexed, 1);
  //  shopId ِ بدنه یا نشانی نادیده است: دکان فقط از توکن
  const spoof = await h.raw('POST', `/api/shop/visual-search?shopId=${A.shopId}&shop_id=${A.shopId}`, Q_SAME, { token: B.token });
  assert.deepEqual(ids(spoof), ['q-base']);
});

test('AI-11: انتخابِ بانکِ تصاویر (پرچمِ بانک روشن) جای عکسِ همگام‌شده را در نمایه می‌گیرد', async () => {
  await setFlag(A.shopId, 'shop.image_bank', true);
  const up = await h.raw('POST', '/api/shop/images?product_id=p-other', IMG.base, { token: A.token });
  assert.equal(up.status, 201, JSON.stringify(up.body));
  const sel = await h.post('/api/shop/images/select', { product_id: 'p-other', source: 'shop', image_id: up.body.id }, { token: A.token });
  assert.equal(sel.status, 200, JSON.stringify(sel.body));
  assert.equal((await vs.backfill(A.shopId)).hashed, 1);
  const row = await one(`SELECT src FROM product_visual_hashes WHERE shop_id=$1 AND product_id='p-other'`, [A.shopId]);
  assert.match(row.src, /^b[0-9a-f]{64}$/);
  assert.deepEqual(ids(await search(A, Q_SAME)).slice(0, 2).sort(), ['p-base', 'p-other']);
  await setFlag(A.shopId, 'shop.image_bank', false);
  assert.equal((await vs.backfill(A.shopId)).hashed, 1, 'بانک خاموش ⇒ دوباره همان عکسِ همگام‌شده');
  assert.deepEqual(ids(await search(A, Q_WIDE)), ['p-other']);
});

test('AI-11: ویترین پشتِ پرچمِ خودش — فقط کالاهای منتشرشده، «مشابه»، بی افزودن به سبد', async () => {
  for (const key of ['shop.orders', 'shop.storefront']) await setFlag(A.shopId, key, true);
  const slug = `vs-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug, enabled: true, title: 'ویترینِ الف', contactPhone: '0700000001', acceptPickup: true },
    { token: A.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  for (const id of ['p-base', 'p-shift2']) {
    assert.equal((await h.put(`/api/shop/storefront/products/${id}`, { published: true }, { token: A.token })).status, 200);
  }
  const url = `/api/store/${slug}/visual-search`;
  const off = await h.raw('POST', url, Q_SAME);
  const unknown = await h.raw('POST', `/api/store/${slug}/no-such-route-ai11`, Q_SAME);
  assert.equal(off.status, unknown.status, 'پرچمِ ویترین خاموش ⇒ همان مسیرِ ناشناخته');
  assert.deepEqual(off.body, unknown.body);
  await setFlag(A.shopId, vs.STORE_FLAG, true);
  const cartBefore = await rowCounts();
  const r = await h.raw('POST', url, Q_SAME);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(ids(r), ['p-base', 'p-shift2'], 'فقط منتشرشده‌ها، به ترتیبِ فاصله');
  assert.ok(r.body.items.every((x) => x.label === 'مشابه' && x.product && x.product.id === x.product_id));
  assert.doesNotMatch(JSON.stringify(r.body), /همان/);
  assert.deepEqual(await rowCounts(), cartBefore, 'نه سبد، نه سفارش، نه عکس');
  await setFlag(A.shopId, vs.FLAG, false);
  const noIndex = await h.raw('POST', url, Q_SAME);
  assert.equal(noIndex.status, unknown.status, 'پرچمِ نمایه خاموش ⇒ ویترین هم خاموش');
  await setFlag(A.shopId, vs.FLAG, true);
  assert.equal((await h.raw('POST', `/api/store/no-such-store-${uniq()}/visual-search`, Q_SAME)).body.error.code, 'store_not_found');
});

test('AI-11: سقفِ نرخ — سطلِ جدای دکان پیش از خواندنِ بدنه', async () => {
  let last = null;
  for (let i = 0; i < 61; i++) last = await search(A, Buffer.alloc(0));
  assert.equal(last.status, 429);
});
