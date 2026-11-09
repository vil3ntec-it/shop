'use strict';
/**
 * ویترین — پیش‌نمایشِ پیوند (WEB-20) با عکسِ نسخه‌دار و لوگوی خودِ دکان (WEB-01)، پشتِ پرچم‌های خاموشِ
 * `shop.storefront.seo` و `shop.storefront.media` (`lib/store-seo.js`).
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) og:image ِ کالا همان `?v=` ِ `photoVersion` ِ نمای کالاست (IMG-12): واتس‌اپ و فیسبوک پیش‌نمایش را با نشانیِ
 *      og:image نگه می‌دارند؛ عکسِ تازه ⇒ نشانیِ تازه ⇒ پیش‌نمایشِ تازه، و خزنده همان عکس را با کشِ یک‌روزه می‌گیرد.
 *      عکسِ بانک (پرچمِ دکان) بر عکسِ همگام‌شده مقدم است، همان ترتیبِ `storefront.photo()`.
 *   ۲) لوگوی بارگذاری‌شدهٔ دکان (M3) پیش‌نمایشِ پیوندِ خودِ دکان و کالای بی‌عکس است، با ابعاد و نوعِ واقعی
 *      (`og:image:width/height/type`) تا فیسبوک همان بارِ اول عکس را نشان دهد؛ نشانیِ مطلق از دامنهٔ پیکربندی،
 *      نه از Host. پرچمِ media خاموش ⇒ همان امروز (بی og:image ِ دکان).
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const h = require('./helpers');
const F = require('./image-fixtures');
const { query, newId, now } = require('../src/db');
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

const push = (token, ops, device = 'dev-seo-media') => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop', 'X-Device': device },
  body: { device_id: device, schema_version: 2, ops },
});
const op = (rowId, fields) => ({ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: rowId, type: 'insert', fields });

/** دکانِ روشن با ویترین و سئو؛ برنج (بی عکس) و روغن منتشرشده. */
async function liveStore(label) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { token: u.accessToken, shopId: made.body.shop.id };
  await setFlag(o.shopId, 'shop.orders', true);
  await setFlag(o.shopId, 'shop.storefront', true);
  await setFlag(o.shopId, 'shop.storefront.seo', true);
  const slug = `seom-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug, enabled: true, title: `ویترینِ ${label}`, tagline: 'تازه هر روز' }, { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const p = await push(o.token, [
    op('rice', { name: 'برنج', unit: 'کیلو', salePrice: 120, category: 'خوراکی' }),
    op('oil', { name: 'روغن', unit: 'بوتل', salePrice: 1200, category: 'خوراکی' }),
  ]);
  assert.equal(p.status, 200, JSON.stringify(p.body));
  for (const id of ['rice', 'oil']) {
    assert.equal((await h.put(`/api/shop/storefront/products/${id}`, { published: true }, { token: o.token })).status, 200);
  }
  return { ...o, slug, origin: `https://${slug}.vill3n.top` };
}

/** عکسِ همگام‌شدهٔ کالا با hash ِ داده‌شده (همان ردیفِ ۰۴۵). */
async function syncedPhoto(s, productId, hash) {
  await query(
    `INSERT INTO product_photos (app, account_id, product_id, data, hash, size, rev, updated_at)
     VALUES ('shop', $1, $2, $3, $4, 4, 1, $5)
     ON CONFLICT (app, account_id, product_id) DO UPDATE SET hash = EXCLUDED.hash, rev = product_photos.rev + 1`,
    [s.shopId, productId, Buffer.from([0x89, 0x50, 0x4e, 0x47]), hash, now()]
  );
}

function rawGet(p, host) {
  const u = new URL(h.base());
  return new Promise((resolve, reject) => {
    http.get({ hostname: u.hostname, port: u.port, path: p, headers: host ? { Host: host } : {} }, (res) => {
      let body = '';
      res.setEncoding('utf8');
      res.on('data', (c) => { body += c; });
      res.on('end', () => resolve({ status: res.statusCode, headers: res.headers, body }));
    }).on('error', reject);
  });
}

const metaOf = (html, prop) => {
  const m = new RegExp(`<meta property="${prop}" content="([^"]*)">`).exec(html);
  return m ? m[1] : null;
};
const ldOf = (html) => {
  const m = /<script type="application\/ld\+json">([^<]*)<\/script>/.exec(html);
  return m ? JSON.parse(m[1]) : null;
};
const versionOf = async (s, id) => (await h.get(`/api/store/${s.slug}/products/${id}`)).body.product.photoVersion;

test('WEB-20: og:image ِ کالا همان `?v=` ِ نمای کالاست؛ عکسِ تازه ⇒ نشانیِ تازه و خزنده کشِ یک‌روزه می‌گیرد', async () => {
  const s = await liveStore('نسخه');
  await syncedPhoto(s, 'oil', 'h-oil-1');
  const v1 = await versionOf(s, 'oil');
  assert.match(String(v1), /^[0-9a-f]{12}$/, 'نمای کالا نسخهٔ عکس دارد');
  const first = await rawGet(`/store/${s.slug}/p/oil`);
  assert.equal(first.status, 200);
  const img1 = `${s.origin}/api/store/${s.slug}/products/oil/photo?v=${v1}`;
  assert.equal(metaOf(first.body, 'og:image'), img1, 'og:image نسخه‌دار');
  assert.deepEqual(ldOf(first.body).image, [img1], 'JSON-LD همان نشانی');
  //  خزنده همان نشانی را می‌خواهد ⇒ همان عکسِ سرو‌شده با کشِ یک‌روزه (نه ۵ دقیقه)
  const crawl = await fetch(`${h.base()}/api/store/${s.slug}/products/oil/photo?v=${v1}`);
  assert.equal(crawl.status, 200);
  assert.equal(crawl.headers.get('cache-control'), 'public, max-age=86400');
  //  عکسِ تازه ⇒ نشانیِ تازه (واتس‌اپ/فیسبوک عکسِ کهنه را با نشانیِ کهنه نگه می‌دارند)
  await syncedPhoto(s, 'oil', 'h-oil-2');
  const v2 = await versionOf(s, 'oil');
  assert.notEqual(v2, v1);
  const second = await rawGet(`/store/${s.slug}/p/oil`);
  assert.equal(metaOf(second.body, 'og:image'), `${s.origin}/api/store/${s.slug}/products/oil/photo?v=${v2}`);
  //  عکسِ بانک (پرچمِ دکان) بر عکسِ همگام‌شده مقدم است — همان نسخهٔ نمای کالا
  await setFlag(s.shopId, 'shop.image_bank', true);
  const imageId = newId('img');
  //  گزینشِ بانک (۰۸۸): همان ردیفی که `shelf.bankIds` می‌خواند
  await query(
    `INSERT INTO shop_product_images (shop_id, product_id, source, image_id, version, updated_at)
     VALUES ($1, 'oil', 'shop', $2, 3, $3)`,
    [s.shopId, imageId, now()]
  );
  const vb = await versionOf(s, 'oil');
  assert.notEqual(vb, v2, 'نمای کالا نسخهٔ بانک را می‌گوید');
  const banked = await rawGet(`/store/${s.slug}/p/oil`);
  assert.equal(metaOf(banked.body, 'og:image'), `${s.origin}/api/store/${s.slug}/products/oil/photo?v=${vb}`, 'og:image همان نسخهٔ بانک');
});

test('WEB-01 × WEB-20: لوگوی خودِ دکان پیش‌نمایشِ پیوندِ دکان و کالای بی‌عکس است، با ابعاد و نوع؛ پرچمِ media خاموش ⇒ همان امروز', async () => {
  const s = await liveStore('لوگو');
  await syncedPhoto(s, 'oil', 'h-oil');
  //  پرچمِ media خاموش ⇒ بی og:image برای دکان و کالای بی‌عکس (همان امروز)
  const offHome = await rawGet(`/store/${s.slug}`);
  assert.equal(offHome.status, 200);
  assert.equal(metaOf(offHome.body, 'og:image'), null);
  assert.equal(metaOf((await rawGet(`/store/${s.slug}/p/rice`)).body, 'og:image'), null);

  await setFlag(s.shopId, 'shop.storefront.media', true);
  const up = await h.raw('PUT', '/api/shop/storefront/media/logo', F.jpeg(256, 256), { token: s.token, headers: { 'Content-Type': 'image/jpeg' } });
  assert.equal(up.status, 200, JSON.stringify(up.body));
  const logo = `${s.origin}/api/store/${s.slug}/media/logo/${up.body.version}`;

  const home = await rawGet(`/store/${s.slug}`);
  assert.equal(home.status, 200);
  assert.equal(metaOf(home.body, 'og:image'), logo, 'لوگو پیش‌نمایشِ پیوندِ دکان');
  assert.equal(metaOf(home.body, 'og:image:width'), '256');
  assert.equal(metaOf(home.body, 'og:image:height'), '256');
  assert.equal(metaOf(home.body, 'og:image:type'), 'image/jpeg');
  assert.equal(metaOf(home.body, 'og:image:alt'), 'ویترینِ لوگو');
  assert.match(home.body, /<meta name="twitter:card" content="summary">/, 'لوگوی مربع ⇒ کارتِ کوچک');
  const data = ldOf(home.body);
  assert.equal(data['@type'], 'Store');
  assert.equal(data.logo, logo);
  assert.deepEqual(data.image, [logo]);
  //  نشانیِ مطلق از دامنهٔ پیکربندی، نه Host
  const spoof = await rawGet(`/store/${s.slug}`, 'evil.example');
  assert.equal(metaOf(spoof.body, 'og:image'), logo);
  //  خزنده همان لوگو را می‌گیرد
  const crawl = await fetch(`${h.base()}/api/store/${s.slug}/media/logo/${up.body.version}`);
  assert.equal(crawl.status, 200);
  assert.equal(crawl.headers.get('content-type'), 'image/jpeg');

  //  کالای بی‌عکس ⇒ لوگوی دکان، نه بی‌عکس؛ JSON-LD ِ کالا عکسِ ساختگیِ کالا نمی‌گیرد
  const rice = await rawGet(`/store/${s.slug}/p/rice`);
  assert.equal(metaOf(rice.body, 'og:image'), logo);
  assert.equal(metaOf(rice.body, 'og:image:width'), '256');
  assert.match(rice.body, /<meta name="twitter:card" content="summary">/);
  assert.equal(ldOf(rice.body).image, undefined, 'لوگو عکسِ خودِ کالا نیست');
  //  کالای عکس‌دار ⇒ عکسِ خودش، نه لوگو
  const oil = await rawGet(`/store/${s.slug}/p/oil`);
  assert.match(metaOf(oil.body, 'og:image'), /\/products\/oil\/photo\?v=[0-9a-f]{12}$/);
  assert.equal(metaOf(oil.body, 'og:image:width'), null, 'ابعادِ عکسِ کالا دانسته نیست ⇒ ادعا نمی‌شود');
  assert.match(oil.body, /<meta name="twitter:card" content="summary_large_image">/);

  //  لوگو برداشته شد ⇒ پیش‌نمایش بی لوگو
  assert.equal((await h.del('/api/shop/storefront/media/logo', { token: s.token })).status, 200);
  assert.equal(metaOf((await rawGet(`/store/${s.slug}`)).body, 'og:image'), null);
});
