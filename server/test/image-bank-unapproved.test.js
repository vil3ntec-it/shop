'use strict';
/**
 * IMG-13 (۳۴.۴) — عکسِ بی تأیید هرگز «عکسِ واقعیِ کالا» معرفی نمی‌شود؛ کالای
 * بی عکسِ تأییدشده در ویترین و برنامه جای خالی (placeholder) دارد.
 *
 * از راهِ واقعی: بانکِ مرکزیِ روشن، دکانِ با ویترینِ زنده، و همان درهایی که
 * ویترین (`/api/store/:slug/products/:id/thumb|photo`) و برنامه
 * (`/api/shop/images/products/:id`) می‌خوانند.
 *
 *   ۱) عکسِ در انتظارِ بررسی: نه در جست‌وجو، نه انتخاب‌شدنی، نه فایلش برای دکان،
 *      نه در ویترین؛ کالا «بی عکس» و جای خالی.
 *   ۲) ⛔ نسخهٔ تأییدشده (که دکانی ممکن است رویش سنجاق باشد) رد نمی‌شود؛ وگرنه
 *      ویترینِ آن دکان نسخهٔ ردشده را همچنان «عکسِ واقعی» نشان می‌داد. ردِ نسخهٔ
 *      تازهٔ منتظرِ بررسی همان قبلی است و هیچ اثری روی سنجاق ندارد.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const F = require('./image-fixtures');
const { query, one, newId, now } = require('../src/db');

let root = '';   // مدیرِ ارشد
let clerk = '';  // مدیرِ عادی
let A;
let seed = 5000;
const img = (w = 800, hh = 600) => F.jpeg(w, hh, { seed: ++seed });

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: root });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

const S = (method, path, who, body) => h.api(method, '/api/shop/images' + path, { token: who.token, body });
const AD = (method, path, token, body) => h.api(method, '/api/admin/image-bank' + path, { token, body });
const ADR = (method, path, token, buf) => h.raw(method, '/api/admin/image-bank' + path, buf, { token });
/** ویترینِ عمومی، بی نشست — همان که مرورگرِ مشتری می‌خواند */
async function store(path) {
  const r = await fetch(`${h.base()}/api/store/${A.slug}${path}`);
  return { status: r.status, buf: Buffer.from(await r.arrayBuffer()) };
}

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'root','ارشد',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Root!12345'), now()]);
  root = (await h.post('/api/admin/login', { username: 'root', password: 'Root!12345' })).body.token;
  const made = await h.post('/api/admin/admins', { username: 'clerk', password: 'Clerk!12345', role: 'admin' }, { token: root });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  clerk = (await h.post('/api/admin/login', { username: 'clerk', password: 'Clerk!12345' })).body.token;

  //  دکانِ الف با ویترینِ زنده و دو کالای منتشرشده
  const u = await h.newUser('الف', 'shop');
  const shop = await h.post('/api/shop', { name: 'دکانِ الف' }, { token: u.accessToken });
  assert.equal(shop.status, 201, JSON.stringify(shop.body));
  A = { ...u, token: u.accessToken, shopId: shop.body.shop.id, slug: `img13-${Date.now().toString(36)}` };
  await setFlag(A.shopId, 'shop.orders', true);
  await setFlag(A.shopId, 'shop.storefront', true);
  const sf = await h.put('/api/shop/storefront', {
    slug: A.slug, enabled: true, title: 'ویترینِ الف', deliveryFeeMinor: 0, minOrderMinor: 0,
  }, { token: A.token });
  assert.equal(sf.status, 200, JSON.stringify(sf.body));
  const ops = [['oil', 'روغن'], ['rice', 'برنج']].map(([id, name], i) => ({
    op_id: `op-img13-${i}`, ts: Date.now(), table: 'products', row_id: id, type: 'insert',
    fields: { name, unit: 'دانه', salePrice: 100 },
  }));
  const pushed = await h.api('POST', '/api/sync/v1/push', {
    token: A.token, headers: { 'X-App': 'shop', 'X-Device': 'dev-img13' },
    body: { device_id: 'dev-img13', schema_version: 2, ops },
  });
  assert.equal(pushed.status, 200, JSON.stringify(pushed.body));
  for (const id of ['oil', 'rice']) {
    const r = await h.put(`/api/shop/storefront/products/${id}`, { published: true }, { token: A.token });
    assert.equal(r.status, 200, JSON.stringify(r.body));
  }
  await setFlag('all', 'image_bank.central', true);
  await setFlag(A.shopId, 'shop.image_bank', true);
});
test.after(async () => { await h.stop(); });

test('IMG-13: عکسِ در انتظارِ بررسی نه انتخاب‌شدنی است، نه در ویترین؛ کالا جای خالی دارد', async () => {
  const p = await AD('POST', '/products', clerk, { name: 'روغن آفتاب', brand: 'زر' });
  assert.equal(p.status, 201, JSON.stringify(p.body));
  //  بی مجوزِ تجاری ⇒ فقط «منتظرِ بررسی»
  const up = await ADR('POST', `/images?bank_product_id=${p.body.id}`, clerk, img());
  assert.equal(up.status, 201, JSON.stringify(up.body));
  assert.equal(up.body.status, 'NEEDS_REVIEW');
  const th = await ADR('PUT', `/images/${up.body.id}/v/1/thumb`, clerk, F.jpeg(400, 300, { seed: ++seed }));
  assert.equal(th.status, 200, JSON.stringify(th.body));

  const found = await S('GET', `/search?q=${encodeURIComponent('روغن آفتاب')}`, A);
  assert.equal(found.status, 200, JSON.stringify(found.body));
  assert.equal(found.body.items.length, 1);
  assert.deepEqual(found.body.items[0].images, [], 'عکسِ بی تأیید در نتیجهٔ جست‌وجو آمد');
  const sel = await S('POST', '/select', A, { product_id: 'oil', source: 'central', image_id: up.body.id, confirm: true });
  assert.equal(sel.status, 404, JSON.stringify(sel.body));
  assert.equal((await h.download(`/api/shop/images/file/${up.body.id}/1/thumb`, { token: A.token })).status, 404);

  //  برنامه: «بی عکس» و جای خالی؛ ویترین: نشانِ «عکس ندارد» و هیچ فایلی
  const card = await S('GET', '/products/oil', A);
  assert.deepEqual([card.body.source, card.body.placeholder], ['none', true]);
  const cat = await h.get(`/api/store/${A.slug}/products`);
  assert.equal(cat.status, 200, JSON.stringify(cat.body));
  assert.equal(cat.body.items.find((x) => x.id === 'oil').photo, false);
  assert.equal((await store('/products/oil/thumb')).status, 404);
  assert.equal((await store('/products/oil/photo')).status, 404);
});

test('IMG-13: نسخهٔ تأییدشده‌ای که دکان رویش است رد نمی‌شود؛ ویترین هرگز نسخهٔ ردشده را عکسِ واقعی نشان نمی‌دهد', async () => {
  const p = await AD('POST', '/products', clerk, { name: 'برنج سیلا', brand: 'حاجی', size_value: '5', size_unit: 'kg' });
  assert.equal(p.status, 201, JSON.stringify(p.body));
  const up = await ADR('POST', `/images?bank_product_id=${p.body.id}&license_status=licensed&commercial_use=true&license_source=supplier-feed`, clerk, img());
  assert.equal(up.status, 201, JSON.stringify(up.body));
  const id = up.body.id;
  const version = async (n, tool) => {
    const v = await ADR('POST', `/images/${id}/versions?tool=${tool}`, clerk, img());
    assert.deepEqual([v.status, v.body.version], [201, n], JSON.stringify(v.body));
    assert.equal((await ADR('PUT', `/images/${id}/v/${n}/thumb`, clerk, F.jpeg(400, 300, { seed: ++seed }))).status, 200);
  };
  assert.equal((await ADR('PUT', `/images/${id}/v/1/thumb`, clerk, F.jpeg(400, 300, { seed: ++seed }))).status, 200);
  assert.equal((await AD('POST', `/images/${id}/approve`, root, { reason: 'مجوزِ تأمین‌کننده' })).status, 200);
  await version(2, 'manual-crop');
  assert.equal((await AD('POST', `/images/${id}/approve`, root, { reason: 'برش بهتر', version: 2 })).status, 200);

  //  دکان نسخهٔ ۲ را می‌گذارد و ویترینش همان را نشان می‌دهد
  const sel = await S('POST', '/select', A, {
    product_id: 'rice', source: 'central', image_id: id, version: 2,
    expect: { name: 'برنج سیلا', brand: 'حاجی', size_value: '5', size_unit: 'kg' }, confirm: true,
  });
  assert.equal(sel.status, 200, JSON.stringify(sel.body));
  const shown = await store('/products/rice/thumb');
  assert.equal(shown.status, 200);
  const pinned = await h.download(`/api/shop/images/file/${id}/2/thumb`, { token: A.token });
  assert.ok(shown.buf.equals(pinned.buffer), 'ویترین نسخهٔ سنجاق‌شده را نشان نداد');

  //  نسخهٔ ۳ تأیید و جاری می‌شود؛ دکان روی ۲ می‌ماند (فقط پیشنهاد)
  await version(3, 'light');
  assert.equal((await AD('POST', `/images/${id}/approve`, root, { reason: 'نورِ بهتر', version: 3 })).status, 200);
  const before = await S('GET', '/products/rice', A);
  assert.deepEqual([before.body.version, before.body.update_available], [2, 3]);

  //  ⛔ ردِ نسخهٔ تأییدشدهٔ ۲ (همان که ویترینِ دکان نشان می‌دهد) بسته است
  const rj = await AD('POST', `/images/${id}/reject`, root, { reason: 'بستهٔ قدیمی', version: 2 });
  assert.equal(rj.status, 409, JSON.stringify(rj.body));
  assert.equal(rj.body.error.code, 'version_approved');
  assert.equal((await one('SELECT review FROM image_versions WHERE image_id=$1 AND version=2', [id])).review, 'approved');
  assert.equal(await one(`SELECT 1 AS x FROM audit_logs WHERE action='admin.bank_image_rejected' AND target_id=$1`, [id]), null);
  //  ویترین همان نسخهٔ تأییدشدهٔ ۲ را نشان می‌دهد و برنامه همان را می‌گوید
  assert.ok((await store('/products/rice/thumb')).buf.equals(shown.buf), 'ویترین عکسِ دیگری نشان داد');
  assert.deepEqual([(await S('GET', '/products/rice', A)).body.version], [2]);
  //  اصل (نسخهٔ ۱) و نسخهٔ جاری هم مثلِ قبل رد نمی‌شوند
  assert.equal((await AD('POST', `/images/${id}/reject`, root, { reason: 'اصل', version: 1 })).body.error.code, 'version_current');
  assert.equal((await AD('POST', `/images/${id}/reject`, root, { reason: 'جاری', version: 3 })).body.error.code, 'version_current');

  //  ردِ نسخهٔ تازهٔ منتظرِ بررسی همان قبلی است؛ نسخهٔ ردشده انتخاب نمی‌شود و ویترین دست نمی‌خورد
  await version(4, 'denoise');
  const rj4 = await AD('POST', `/images/${id}/reject`, root, { reason: 'رنگِ بسته‌بندی عوض شد', version: 4 });
  assert.deepEqual([rj4.status, rj4.body.review], [200, 'rejected']);
  const s4 = await S('POST', '/select', A, { product_id: 'rice', source: 'central', image_id: id, version: 4, confirm: true });
  assert.equal(s4.status, 409, JSON.stringify(s4.body));
  assert.equal(s4.body.error.code, 'version_not_approved');
  assert.ok((await store('/products/rice/thumb')).buf.equals(shown.buf));
  //  نسخهٔ ناموجود همان «نیست»
  assert.equal((await AD('POST', `/images/${id}/reject`, root, { reason: 'نیست', version: 9 })).status, 404);
});
