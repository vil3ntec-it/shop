'use strict';
/**
 * PRD-17 / PRD-27 (تصمیمِ ۴-۲۲ ِ صاحب‌کار، ۱۴۰۵/۰۸/۲۰) — گالریِ عکس‌های بیشترِ کالا در برگهٔ کالای ویترین
 * (`lib/storefront-gallery.js`؛ `GET /api/store/:slug/products/:id/gallery` و `…/gallery/:tag/:size`).
 *
 *   ۱) پیش‌فرض خاموش: بی `shop.storefront.gallery` (یا بی `shop.image_bank`) نه `features.gallery`، نه فهرست، نه فایل.
 *   ۲) روشن: فقط عکس‌های تکمیلیِ همین کالای همین دکان با نسخهٔ `display`؛ نه عکسِ ویترینِ انتخاب‌شده، نه `main`/`promo`،
 *      نه عکسِ برداشته‌شده؛ بی شناسهٔ درونی؛ `tag` ِ ۱۲ hex و پایدار برای همان (عکس، نسخه).
 *   ۳) فایل همان بایت‌های بانک، فقط `thumb`/`display`؛ tag ِ کالا یا دکانِ دیگر و کالای منتشرنشده ۴۰۴؛ سقفِ ۸.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const F = require('./image-fixtures');
const { query, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');

let admin = '';
let seq = 0;
let seed = 9100;
const uniq = () => `${Date.now().toString(36)}${++seq}`;
const pic = (w = 800, hh = 600) => F.jpeg(w, hh, { seed: ++seed });
const TAG = /^[0-9a-f]{12}$/;

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
const SYNC = { 'X-App': 'shop', 'X-Device': 'dev-gal' };

/** دکانِ روشن با ویترین و بانکِ تصاویر: برنج و روغن منتشرشده، صابون نه. */
async function liveStore(label, { gallery = true } = {}) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { token: u.accessToken, shopId: made.body.shop.id, slug: `gal-${uniq()}` };
  for (const k of ['shop.orders', 'shop.storefront', 'shop.image_bank']) await setFlag(o.shopId, k, true);
  if (gallery) await setFlag(o.shopId, 'shop.storefront.gallery', true);
  const put = await h.put('/api/shop/storefront', { slug: o.slug, enabled: true, title: `ویترینِ ${label}`, deliveryFeeMinor: 0, minOrderMinor: 0 },
    { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const p = await h.api('POST', '/api/sync/v1/push', { token: o.token, headers: SYNC, body: { device_id: 'dev-gal', schema_version: 2, ops: [
    op('rice', { name: 'برنج', unit: 'کیلو', salePrice: 120, category: 'خوراکی' }),
    op('oil', { name: 'روغن', unit: 'بوتل', salePrice: 300, category: 'خوراکی' }),
    op('soap', { name: 'صابون', unit: 'دانه', salePrice: 40, category: 'بهداشتی' }),
  ] } });
  assert.equal(p.status, 200, JSON.stringify(p.body));
  for (const id of ['rice', 'oil']) {
    const r = await h.put(`/api/shop/storefront/products/${id}`, { published: true }, { token: o.token });
    assert.equal(r.status, 200, JSON.stringify(r.body));
  }
  return o;
}

/** عکسِ دکان با نقش برای کالا، با نسخهٔ `display` (مثلِ برنامه) مگر `display: false`. */
async function extra(s, pid, role, { display = true } = {}) {
  const r = await h.raw('POST', `/api/shop/images?product_id=${pid}&role=${role}`, pic(), { token: s.token });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  if (display) {
    const d = await h.raw('PUT', `/api/shop/images/${r.body.id}/v/1/display`, F.jpeg(600, 450, { seed: ++seed }), { token: s.token });
    assert.equal(d.status, 200, JSON.stringify(d.body));
  }
  return r.body.id;
}

const gallery = (s, pid, slug = s.slug) => h.get(`/api/store/${slug}/products/${pid}/gallery`);
async function file(s, pid, tag, size, slug = s.slug) {
  const r = await fetch(`${h.base()}/api/store/${slug}/products/${pid}/gallery/${tag}/${size}`);
  return { status: r.status, type: r.headers.get('content-type'), cache: r.headers.get('cache-control'), nosniff: r.headers.get('x-content-type-options'),
    buf: Buffer.from(await r.arrayBuffer()) };
}
const bankFile = async (s, id, v, size) => (await h.download(`/api/shop/images/file/${id}/${v}/${size}`, { token: s.token })).buffer;

test('پیش‌فرض خاموش: بی پرچمِ گالری یا بی بانکِ تصاویر نه features.gallery، نه فهرست، نه فایل', async () => {
  const A = await liveStore('خاموش', { gallery: false });
  await extra(A, 'rice', 'back');
  const home = await h.get(`/api/store/${A.slug}`);
  assert.equal(home.status, 200, JSON.stringify(home.body));
  assert.ok(!home.body.features || home.body.features.gallery === undefined, 'بی پرچم ⇒ کلیدِ gallery نیست');
  assert.equal((await gallery(A, 'rice')).status, 404);
  assert.equal((await file(A, 'rice', '0123456789ab', 'display')).status, 404);
  //  گالری روشن ولی بانکِ تصاویر خاموش ⇒ همان «نیست»
  await setFlag(A.shopId, 'shop.storefront.gallery', true);
  await setFlag(A.shopId, 'shop.image_bank', false);
  const home2 = await h.get(`/api/store/${A.slug}`);
  assert.ok(!home2.body.features || home2.body.features.gallery === undefined);
  assert.equal((await gallery(A, 'rice')).status, 404);
});

test('روشن: فقط تکمیلی‌های همین کالا با نسخهٔ نمایش؛ بی شناسهٔ درونی؛ فایل همان بایت‌های بانک', async () => {
  const A = await liveStore('روشن');
  const home = await h.get(`/api/store/${A.slug}`);
  assert.equal(home.body.features?.gallery, true);
  const back = await extra(A, 'rice', 'back');
  await extra(A, 'rice', 'label');
  await extra(A, 'rice', 'detail', { display: false });        //  بی نسخهٔ نمایش ⇒ هنوز نه
  await extra(A, 'rice', 'main');                               //  نقشِ اصلی ⇒ نه
  const promo = await extra(A, 'rice', 'promo');                //  عکسِ ویترین ⇒ نه
  const shown = await extra(A, 'rice', 'variant');              //  تکمیلی که عکسِ ویترین شد ⇒ نه
  const sel = await h.post('/api/shop/images/select', { product_id: 'rice', source: 'shop', image_id: shown, version: 1 }, { token: A.token });
  assert.equal(sel.status, 200, JSON.stringify(sel.body));
  await extra(A, 'oil', 'back');                                //  کالای دیگر ⇒ نه
  void promo;

  const g = await gallery(A, 'rice');
  assert.equal(g.status, 200, JSON.stringify(g.body));
  assert.deepEqual(g.body.items.map((i) => i.role), ['back', 'label']);
  for (const i of g.body.items) {
    assert.match(i.tag, TAG);
    assert.deepEqual(Object.keys(i).sort(), ['role', 'tag']);
  }
  assert.ok(!JSON.stringify(g.body).includes('img_'), 'شناسهٔ درونیِ عکس بیرون نمی‌رود');

  const f = await file(A, 'rice', g.body.items[0].tag, 'display');
  assert.equal(f.status, 200);
  assert.equal(f.type, 'image/jpeg');
  assert.equal(f.nosniff, 'nosniff');
  assert.equal(f.cache, 'public, max-age=86400');
  assert.ok(f.buf.equals(await bankFile(A, back, 1, 'display')), 'همان نسخهٔ نمایشِ بانک');
  assert.equal((await file(A, 'rice', g.body.items[1].tag, 'original')).status, 404, 'اصل هرگز');
  assert.equal((await file(A, 'rice', g.body.items[1].tag, 'thumb')).status, 404, 'نسخهٔ کوچکِ ساخته‌نشده');
  assert.equal((await file(A, 'oil', g.body.items[0].tag, 'display')).status, 404, 'tag ِ کالای دیگر');
  assert.equal((await file(A, 'rice', 'ffffffffffff', 'display')).status, 404);
  assert.equal((await file(A, 'rice', encodeURIComponent('../x'), 'display')).status, 404);

  //  tag پایدار است (همان عکس و نسخه ⇒ همان نشانی)
  assert.deepEqual((await gallery(A, 'rice')).body.items, g.body.items);

  //  برداشتن از کالا ⇒ از گالری بیرون
  assert.equal((await h.post(`/api/shop/images/${back}/detach`, {}, { token: A.token })).status, 200);
  assert.deepEqual((await gallery(A, 'rice')).body.items.map((i) => i.role), ['label']);
  assert.equal((await file(A, 'rice', g.body.items[0].tag, 'display')).status, 404);
});

test('کالای منتشرنشده و دکانِ دیگر: ۴۰۴؛ سقفِ ۸ عکس', async () => {
  const A = await liveStore('مرز');
  const B = await liveStore('دیگر');
  await extra(A, 'soap', 'back');
  assert.equal((await gallery(A, 'soap')).status, 404, 'کالای منتشرنشده');
  await extra(A, 'rice', 'back');
  const ga = await gallery(A, 'rice');
  assert.equal(ga.body.items.length, 1);
  //  همان شناسهٔ کالا در ویترینِ دکانِ دیگر عکسِ این دکان را نمی‌بیند
  const gb = await gallery(B, 'rice');
  assert.equal(gb.status, 200);
  assert.deepEqual(gb.body.items, []);
  assert.equal((await file(A, 'rice', ga.body.items[0].tag, 'display', B.slug)).status, 404);
  for (let i = 0; i < 9; i++) await extra(A, 'oil', i % 2 ? 'detail' : 'variant');
  assert.equal((await gallery(A, 'oil')).body.items.length, 8);
});
