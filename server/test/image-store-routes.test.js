'use strict';
/**
 * انبارِ عکس از درهای واقعی (`lib/image-store.js`، مهاجرتِ ۲۵۵): همان بایت و همان سرآیند در حالتِ `db` و `disk`،
 * و جداییِ دکان‌ها.
 *
 *   ۱) دکانِ «الف» در حالتِ db و دکانِ «ب» در حالتِ disk همان عکس‌ها را می‌فرستند (عکسِ کالا با همگام‌سازی، لوگو،
 *      عکسِ گزارشِ مشکل، صندوقِ عکس) ⇒ هر در (ویترینِ عکس/کوچک با و بی `?v=`، ۳۰۴، لوگو، همگام‌سازی، صندوق،
 *      عکسِ گزارش برای دکان) بایت‌به‌بایت و سرآیند‌به‌سرآیند یکی است: Content-Type، Cache-Control، ETag، nosniff،
 *      Content-Length. ردیف‌های «الف» در پایگاه‌اند و هیچ شیئی ندارند؛ ردیف‌های «ب» بی `data` و با شیء.
 *   ۲) پس از `copy` + `purge` ِ ابزار عکس‌های «الف» از دیسک می‌آیند و پاسخ‌ها همان پاسخ‌های حالتِ db است.
 *   ۳) جایگزینی/برداشتنِ عکس و نسخهٔ سوم لوگو شیءِ کهنه را پاک می‌کند (شیءِ یتیم نمی‌ماند).
 *   ۴) ⛔ جدایی: ردیفِ «ب» که به شیءِ «الف» اشاره کند (کلیدِ درست، حدس‌زده یا دزدیده) هیچ بایتی نمی‌گیرد و «الف»
 *      دست‌نخورده می‌ماند؛ دکانِ دیگر عکسِ گزارشِ این دکان را نمی‌بیند؛ شیءها در پوشهٔ tenant ِ خودشان‌اند.
 */
process.env.RATE_STORE_CHECKOUT_MAX = '500';
const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('crypto');
const fs = require('fs');
const os = require('os');
const path = require('path');
const h = require('./helpers');
const F = require('./image-fixtures');
const { query, one, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const config = require('../src/config');
const store = require('../src/lib/image-store');
const move = require('../src/lib/image-store-move');
const mailOf = (p) => `c${String(p).replace(/\D/g, '').slice(-9)}@test.local`;   //  «فقط ایمیل»: بلیتِ مشتری با ایمیل (شمارهٔ تماس جدا)

let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;
const DIR = fs.mkdtempSync(path.join(os.tmpdir(), 'img-routes-'));
const SYNC = { 'X-App': 'shop', 'X-Device': 'dev-img' };
const HEADERS = ['content-type', 'cache-control', 'etag', 'x-content-type-options', 'content-length'];
const sha = (b) => crypto.createHash('sha256').update(b).digest('hex');
const setMode = (mode) => { config.imageStore.mode = mode; config.imageStore.dir = DIR; };

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
test.after(async () => {
  setMode('db');
  await h.stop();
  fs.rmSync(DIR, { recursive: true, force: true });
});
test.beforeEach(() => resetAll());

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}
const op = (rowId, fields) => ({ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: rowId, type: 'insert', fields });

async function liveStore(label) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const s = { token: u.accessToken, shopId: made.body.shop.id, slug: `is-${uniq()}` };
  for (const k of ['shop.orders', 'shop.storefront', 'shop.storefront.media', 'shop.orders.issue_reports']) await setFlag(s.shopId, k, true);
  assert.equal((await h.put('/api/shop/storefront', { slug: s.slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001',
    deliveryFeeMinor: 0, minOrderMinor: 0 }, { token: s.token })).status, 200);
  const p = await h.api('POST', '/api/sync/v1/push', { token: s.token, headers: SYNC, body: { device_id: 'dev-img', schema_version: 2, ops: [
    op('rice', { name: 'برنج', unit: 'کیلو', salePrice: 120, category: 'خوراکی' }),
    op('oil', { name: 'روغن', unit: 'بوتل', salePrice: 300, category: 'خوراکی' }),
  ] } });
  assert.equal(p.status, 200, JSON.stringify(p.body));
  for (const id of ['rice', 'oil']) assert.equal((await h.put(`/api/shop/storefront/products/${id}`, { published: true }, { token: s.token })).status, 200);
  return s;
}

const RICE = F.jpeg(320, 320, { seed: 801 });
const OIL = F.png(64, 64, { seed: 3 });
const LOGO = F.jpeg(256, 256, { seed: 802 });
const ISSUE = F.jpeg(320, 240, { seed: 803 });
//  صندوقِ عکس فقط نوع را از محتوا می‌سنجد و دست‌کم ۱۰۰ بایت می‌خواهد
const INBOX = Buffer.concat([F.webp(40, 30), Buffer.alloc(200, 7)]);

/** همان عکس‌ها برای یک دکان: کالا (همگام‌سازی)، لوگو، صندوقِ عکس و عکسِ گزارشِ مشکلِ یک سفارش. */
async function upload(s) {
  for (const [id, buf] of [['rice', RICE], ['oil', OIL]]) {
    const r = await h.api('PUT', `/api/sync/v1/photos/${id}`, { token: s.token, headers: SYNC, body: { data: buf.toString('base64') } });
    assert.equal(r.status, 200, JSON.stringify(r.body));
  }
  const logo = await h.raw('PUT', `/api/shop/storefront/media/logo`, LOGO, { token: s.token, headers: { 'Content-Type': 'image/jpeg' } });
  assert.equal(logo.status, 200, JSON.stringify(logo.body));
  const inbox = await h.api('POST', '/api/sync/v1/photo-inbox', { token: s.token, headers: SYNC, body: { data: INBOX.toString('base64'), pair: '123456' } });
  assert.equal(inbox.status, 200, JSON.stringify(inbox.body));
  s.inboxId = inbox.body.id;
  const phone = '0791112233';
  const st = await h.post(`/api/store/${s.slug}/verify/start`, { email: mailOf(phone) });
  const c = await h.post(`/api/store/${s.slug}/verify/check`, { email: mailOf(phone), code: st.body.devCode });
  const order = await h.post(`/api/store/${s.slug}/checkout`, {
    fulfillment: 'pickup', customer: { name: 'مشتری', phone }, lines: [{ productId: 'rice', qtyMilli: 1000 }],
  }, { headers: { 'Idempotency-Key': `ck-${uniq()}-key`, 'X-Store-Ticket': c.body.ticket } });
  assert.equal(order.status, 201, JSON.stringify(order.body));
  const token = order.body.order.token;
  const rep = await h.post(`/api/store/${s.slug}/orders/${token}/issues`, { kind: 'damaged', text: 'پاره بود', requestKey: `rk-${uniq()}` });
  assert.equal(rep.status, 201, JSON.stringify(rep.body));
  const ph = await h.post(`/api/store/${s.slug}/orders/${token}/issues/${rep.body.issue.id}/photos`, { imageBase64: ISSUE.toString('base64') });
  assert.equal(ph.status, 201, JSON.stringify(ph.body));
  s.issue = { id: rep.body.issue.id, photo: ph.body.photo.id };
}

async function fetchRaw(p, { token = null, headers = {} } = {}) {
  const r = await fetch(`${h.base()}${p}`, { headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...headers } });
  const buf = Buffer.from(await r.arrayBuffer());
  return { status: r.status, sha: sha(buf), len: buf.length, headers: Object.fromEntries(HEADERS.map((k) => [k, r.headers.get(k)])), buf };
}
const strip = ({ buf, ...rest }) => rest;

/** پاسخِ هر درِ عکس برای یک دکان (بی بخش‌های وابسته به slug/شناسه) */
async function snap(s) {
  const tag = (await h.get(`/api/store/${s.slug}/products?ids=rice`)).body.items.find((p) => p.id === 'rice').photoVersion;
  assert.match(String(tag), /^[0-9a-f]{12}$/);
  const photo = await fetchRaw(`/api/store/${s.slug}/products/rice/photo`);
  const out = {
    photo: strip(photo),
    photoV: strip(await fetchRaw(`/api/store/${s.slug}/products/rice/photo?v=${tag}`)),
    thumb: strip(await fetchRaw(`/api/store/${s.slug}/products/oil/thumb`)),
    notModified: strip(await fetchRaw(`/api/store/${s.slug}/products/rice/photo`, { headers: { 'If-None-Match': photo.headers.etag } })),
    logo: strip(await fetchRaw(`/api/store/${s.slug}/media/logo/1`)),
    issue: strip(await fetchRaw(`/api/shop/order-feedback/issues/${s.issue.id}/photos/${s.issue.photo}`, { token: s.token })),
  };
  const sync = await h.api('GET', '/api/sync/v1/photos/rice', { token: s.token, headers: SYNC });
  out.sync = { status: sync.status, hash: sync.body.hash, type: sync.body.type, sha: sha(Buffer.from(sync.body.data, 'base64')) };
  const inbox = await h.api('GET', `/api/sync/v1/photo-inbox/${s.inboxId}`, { token: s.token, headers: SYNC });
  out.inbox = { status: inbox.status, type: inbox.body.type, sha: sha(Buffer.from(inbox.body.data, 'base64')) };
  return out;
}

const rows = async (shopId) => (await query(
  `SELECT 'p' AS t, (data IS NOT NULL) AS in_db, store_key FROM product_photos WHERE account_id=$1
   UNION ALL SELECT 'i', (data IS NOT NULL), store_key FROM photo_inbox WHERE account_id=$1
   UNION ALL SELECT 'm', (data IS NOT NULL), store_key FROM storefront_media WHERE shop_id=$1
   UNION ALL SELECT 'o', (data IS NOT NULL), store_key FROM order_issue_photos WHERE shop_id=$1`, [shopId])).rows;
const filesOf = (tenant) => {
  const d = path.join(DIR, 'i1', store.segOf(tenant));
  return fs.existsSync(d) ? fs.readdirSync(d).sort() : [];
};

let dbSnap = null;
let A = null;
let B = null;

test('همان بایت و همان سرآیند: دکانِ حالتِ db و دکانِ حالتِ disk', async () => {
  setMode('db');
  A = await liveStore('الف');
  await upload(A);
  dbSnap = await snap(A);
  assert.equal(dbSnap.photo.status, 200);
  assert.equal(dbSnap.photo.sha, sha(RICE));
  assert.equal(dbSnap.photo.headers['content-type'], 'image/jpeg');
  assert.equal(dbSnap.photo.headers['x-content-type-options'], 'nosniff');
  assert.equal(dbSnap.photo.headers['cache-control'], 'public, max-age=300');
  assert.equal(dbSnap.photoV.headers['cache-control'], 'public, max-age=86400');
  assert.equal(dbSnap.notModified.status, 304);
  assert.equal(dbSnap.thumb.sha, sha(OIL));
  assert.equal(dbSnap.logo.status, 200);
  assert.equal(dbSnap.issue.status, 200);
  assert.equal(dbSnap.inbox.sha, sha(INBOX));
  const ra = await rows(A.shopId);
  assert.equal(ra.length, 5);
  assert.ok(ra.every((r) => r.in_db && r.store_key === ''), 'حالتِ db ⇒ همه در پایگاه، بی کلید');
  assert.equal(fs.existsSync(path.join(DIR, 'i1')), false, 'حالتِ db هیچ شیئی نمی‌سازد');

  setMode('disk');
  B = await liveStore('ب');
  await upload(B);
  const rb = await rows(B.shopId);
  assert.equal(rb.length, 5);
  assert.ok(rb.every((r) => !r.in_db && store.owns(store.shopTenant(B.shopId), r.store_key)), JSON.stringify(rb));
  assert.deepEqual(filesOf(store.shopTenant(B.shopId)), rb.map((r) => r.store_key.split('/')[2]).sort(), 'هر ردیف یک شیء، در پوشهٔ tenant ِ خودش');
  assert.deepEqual(await snap(B), dbSnap, 'حالتِ disk ⇒ همان بایت و سرآیند');
  assert.deepEqual(await snap(A), dbSnap, 'ردیف‌های پایگاهیِ «الف» در حالتِ disk هم همان');
});

test('ابزار: copy و purge ⇒ «الف» از دیسک، پاسخ همان', async () => {
  setMode('disk');
  const out = await move.copy({});
  assert.equal(out.product_photos.copied, 2);
  assert.equal(out.storefront_media.copied, 1);
  assert.equal(out.order_issue_photos.copied, 1);
  assert.equal(out.photo_inbox.copied, 1);
  assert.deepEqual(await snap(A), dbSnap, 'رونوشت (هر دو) ⇒ همان');
  const purged = await move.purge({ confirm: true });
  assert.equal(purged.product_photos.purged, 2);
  const ra = await rows(A.shopId);
  assert.ok(ra.every((r) => !r.in_db && store.owns(store.shopTenant(A.shopId), r.store_key)), JSON.stringify(ra));
  assert.deepEqual(await snap(A), dbSnap, 'فقط از دیسک ⇒ همان');
});

test('جایگزینی، برداشتن و نسخهٔ سومِ لوگو شیءِ کهنه را پاک می‌کنند', async () => {
  setMode('disk');
  const tenant = store.shopTenant(B.shopId);
  const keyOf = async (sql, args) => (await one(sql, args)).store_key;
  const before = await keyOf(`SELECT store_key FROM product_photos WHERE account_id=$1 AND product_id='rice'`, [B.shopId]);
  const next = F.jpeg(320, 320, { seed: 900 });
  assert.equal((await h.api('PUT', '/api/sync/v1/photos/rice', { token: B.token, headers: SYNC, body: { data: next.toString('base64') } })).status, 200);
  const after = await keyOf(`SELECT store_key FROM product_photos WHERE account_id=$1 AND product_id='rice'`, [B.shopId]);
  assert.notEqual(after, before);
  assert.ok(!filesOf(tenant).includes(before.split('/')[2]), 'شیءِ عکسِ پیشین پاک شد');
  assert.equal((await fetchRaw(`/api/store/${B.slug}/products/rice/photo`)).sha, sha(next));

  assert.equal((await h.api('DELETE', '/api/sync/v1/photos/rice', { token: B.token, headers: SYNC })).status, 200);
  assert.ok(!filesOf(tenant).includes(after.split('/')[2]), 'برداشتن ⇒ شیء پاک');
  assert.equal((await fetchRaw(`/api/store/${B.slug}/products/rice/photo`)).status, 404);
  const list = await h.api('GET', '/api/sync/v1/photos?since=0', { token: B.token, headers: SYNC });
  assert.equal(list.body.items.find((i) => i.product_id === 'rice').deleted, true, 'همان نشانِ «برداشته» برای برنامه');

  const v1 = await keyOf(`SELECT store_key FROM storefront_media WHERE shop_id=$1 AND version=1`, [B.shopId]);
  for (const seed of [811, 812]) {
    const r = await h.raw('PUT', `/api/shop/storefront/media/logo`, F.jpeg(256, 256, { seed }), { token: B.token, headers: { 'Content-Type': 'image/jpeg' } });
    assert.equal(r.status, 200, JSON.stringify(r.body));
  }
  assert.ok(!filesOf(tenant).includes(v1.split('/')[2]), 'نسخهٔ ۱ ِ لوگو (بیرون از دو نسخهٔ آخر) با شیءش رفت');
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM storefront_media WHERE shop_id=$1', [B.shopId])).n, 2);
  const same = await h.raw('PUT', `/api/shop/storefront/media/logo`, F.jpeg(256, 256, { seed: 812 }), { token: B.token, headers: { 'Content-Type': 'image/jpeg' } });
  assert.equal(same.body.replayed, true);
  const keys = (await query('SELECT store_key FROM storefront_media WHERE shop_id=$1', [B.shopId])).rows.map((r) => r.store_key.split('/')[2]);
  const logoFiles = filesOf(tenant).filter((f) => !keys.includes(f));
  const others = (await rows(B.shopId)).map((r) => r.store_key.split('/')[2]);
  assert.deepEqual(logoFiles.filter((f) => !others.includes(f)), [], 'تکرارِ همان لوگو شیءِ یتیم نساخت');
});

test('⛔ جدایی: ردیفی که به شیءِ دکانِ دیگر اشاره کند هیچ بایتی نمی‌گیرد', async () => {
  setMode('disk');
  const aKey = (await one(`SELECT store_key FROM product_photos WHERE account_id=$1 AND product_id='oil'`, [A.shopId])).store_key;
  assert.ok(store.owns(store.shopTenant(A.shopId), aKey));
  //  کلیدِ درستِ «الف» در ردیفِ «ب» (همان که حدس، نشت یا تزریقِ SQL می‌ساخت)
  await query(`UPDATE product_photos SET store_key=$2, data=NULL WHERE account_id=$1 AND product_id='oil'`, [B.shopId, aKey]);
  const bOil = await fetchRaw(`/api/store/${B.slug}/products/oil/thumb`);
  assert.equal(bOil.status, 404, 'شیءِ دکانِ دیگر ⇒ «عکس نیست»');
  const bSync = await h.api('GET', '/api/sync/v1/photos/oil', { token: B.token, headers: SYNC });
  assert.equal(bSync.status, 404);
  assert.equal((await fetchRaw(`/api/store/${A.slug}/products/oil/thumb`)).sha, sha(OIL), '«الف» دست‌نخورده');
  //  عکسِ گزارشِ «الف» با نشستِ «ب» (شناسه‌های درست) ⇒ ۴۰۴
  const cross = await fetchRaw(`/api/shop/order-feedback/issues/${A.issue.id}/photos/${A.issue.photo}`, { token: B.token });
  assert.equal(cross.status, 404);
  assert.notEqual(store.segOf(store.shopTenant(A.shopId)), store.segOf(store.shopTenant(B.shopId)));
  //  کلید هرگز به بیرون نمی‌رود: نه در فهرست، نه در کاتالوگ
  const listed = JSON.stringify((await h.api('GET', '/api/sync/v1/photos?since=0', { token: A.token, headers: SYNC })).body);
  const catalog = JSON.stringify((await h.get(`/api/store/${A.slug}/products`)).body);
  for (const text of [listed, catalog]) assert.ok(!text.includes(aKey.split('/')[2]) && !text.includes('store_key'), text.slice(0, 200));
});
