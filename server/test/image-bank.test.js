'use strict';
/**
 * بانکِ تصاویر روی API و دیتابیسِ واقعی (PGlite در CI و محلی) — سناریوهای
 * IMG (۳۴.۴) و IMG-BANK (۳۷.۱۱) که سمتِ سرور دارند. شرح: ‎lib/image-bank.js‎.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const F = require('./image-fixtures');
const { query, one, many, newId, now } = require('../src/db');

let root = '';   // مدیرِ ارشد
let clerk = '';  // مدیرِ عادی
let A; let B; let C; let staffA;
const EAN_TEA = F.ean13('590123412345');
const EAN_TEA_250 = F.ean13('590123412352');
let seed = 1000;
const img = (w = 800, hh = 600) => F.jpeg(w, hh, { seed: ++seed });

async function shopOwner(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, token: u.accessToken, shopId: made.body.shop.id };
}

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: root });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

const S = (method, path, who, body) => h.api(method, '/api/shop/images' + path, { token: who.token, body });
const SR = (method, path, who, buf) => h.raw(method, '/api/shop/images' + path, buf, { token: who.token });
const AD = (method, path, token, body) => h.api(method, '/api/admin/image-bank' + path, { token, body });
const ADR = (method, path, token, buf) => h.raw(method, '/api/admin/image-bank' + path, buf, { token });
const count = async (table) => Number((await one(`SELECT COUNT(*)::int AS n FROM ${table}`)).n);

/** کالا + عکسِ تأییدشدهٔ مرکزی با نسخهٔ کوچک — همان راهِ مدیر */
async function centralImage(product, { thumb = true } = {}) {
  const p = await AD('POST', '/products', clerk, product);
  assert.equal(p.status, 201, JSON.stringify(p.body));
  const up = await ADR('POST', `/images?bank_product_id=${p.body.id}&license_status=licensed&commercial_use=true&license_source=supplier-feed`, clerk, img());
  assert.equal(up.status, 201, JSON.stringify(up.body));
  if (thumb) {
    const t = await ADR('PUT', `/images/${up.body.id}/v/1/thumb`, clerk, F.jpeg(400, 300, { seed: ++seed }));
    assert.equal(t.status, 200, JSON.stringify(t.body));
  }
  const ok = await AD('POST', `/images/${up.body.id}/approve`, root, { reason: 'مجوزِ تأمین‌کننده بررسی شد' });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  return { productId: p.body.id, imageId: up.body.id };
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
  A = await shopOwner('الف');
  B = await shopOwner('ب');
  C = await shopOwner('پ');
  const u = await h.newUser('شاگرد', 'shop');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at) VALUES ($1,$2,$3,'staff','active',$4,$4)`,
    [newId('mem'), A.shopId, u.user.id, now()]);
  staffA = { ...u, token: u.accessToken };
});
test.after(async () => { await h.stop(); });

test('پرچم خاموش (پیش‌فرض): هر دو در بسته‌اند و هیچ ردیفی نوشته نمی‌شود', async () => {
  const s = await S('GET', '/search?q=چای', A);
  assert.equal(s.status, 403);
  assert.equal(s.body.error.code, 'image_bank_disabled');
  assert.equal((await SR('POST', '/', A, img())).status, 403);
  assert.equal((await AD('POST', '/products', root, { name: 'چای' })).status, 403);
  assert.equal((await ADR('POST', '/images', root, img())).status, 403);
  for (const t of ['images', 'image_blobs', 'bank_products', 'shop_product_images', 'image_batches']) {
    assert.equal(await count(t), 0, t);
  }
  //  عکسِ همگام‌شدهٔ امروز دست‌نخورده کار می‌کند
  const legacy = await h.api('PUT', '/api/sync/v1/photos/p-legacy', {
    token: A.token, headers: { 'X-App': 'shop' }, body: { data: img().toString('base64') } });
  assert.equal(legacy.status, 200, JSON.stringify(legacy.body));

  await setFlag('all', 'image_bank.central', true);
  await setFlag(A.shopId, 'shop.image_bank', true);
  await setFlag(B.shopId, 'shop.image_bank', true);
  await setFlag(C.shopId, 'shop.image_bank', true);
});

test('IMG-BANK-02 / IMG-10: فقط مجوزِ تجاریِ ثبت‌شده و تأییدِ مدیرِ ارشد با دلیل، عکس را عمومی می‌کند', async () => {
  const p = await AD('POST', '/products', clerk, { name: 'چای سبز', brand: 'الکوزی', gtin: EAN_TEA, size_value: '500', size_unit: 'g', alt_names: ['green tea', 'شین چای'] });
  assert.equal(p.status, 201, JSON.stringify(p.body));
  const up = await ADR('POST', `/images?bank_product_id=${p.body.id}`, clerk, img());
  assert.equal(up.status, 201, JSON.stringify(up.body));
  assert.equal(up.body.status, 'NEEDS_REVIEW');

  //  پیش از تأیید: کالا پیدا می‌شود ولی عکسی ندارد
  const before = await S('GET', `/search?barcode=${EAN_TEA}`, A);
  assert.equal(before.status, 200, JSON.stringify(before.body));
  assert.equal(before.body.items[0].images.length, 0);

  assert.equal((await AD('POST', `/images/${up.body.id}/approve`, clerk, { reason: 'خوب است' })).status, 403);
  assert.equal((await AD('POST', `/images/${up.body.id}/approve`, root, {})).body.error.code, 'reason_required');
  const noLic = await AD('POST', `/images/${up.body.id}/approve`, root, { reason: 'بررسی شد' });
  assert.equal(noLic.status, 409);
  assert.equal(noLic.body.error.code, 'license_required');

  const patch = await AD('PATCH', `/images/${up.body.id}`, clerk, { license_status: 'licensed', commercial_use: true, license_source: 'قرارداد تأمین‌کننده', license_holder: 'الکوزی' });
  assert.equal(patch.status, 200, JSON.stringify(patch.body));
  const ok = await AD('POST', `/images/${up.body.id}/approve`, root, { reason: 'قرارداد شمارهٔ ۱۲ دیده شد' });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.equal(ok.body.space, 'central');
  const aud = await one(`SELECT detail FROM audit_logs WHERE action='admin.bank_image_approved' AND target_id=$1`, [up.body.id]);
  assert.match(JSON.stringify(aud.detail), /قرارداد شمارهٔ ۱۲/);

  const after = await S('GET', `/search?barcode=${EAN_TEA}`, A);
  assert.equal(after.body.items[0].images.length, 1);
});

test('IMG-BANK-03: اسکنِ بارکدِ معتبر ⇒ کالای دقیق اول، «قطعی»، حتی با رقمِ فارسی', async () => {
  await centralImage({ name: 'چای سبز کیسه‌ای', brand: 'دیگر' });           // نامِ مشابه، بی بارکد
  const fa = EAN_TEA.replace(/\d/g, (d) => '۰۱۲۳۴۵۶۷۸۹'[d]);
  for (const q of [`barcode=${EAN_TEA}`, `q=${encodeURIComponent(fa)}`]) {
    const r = await S('GET', `/search?${q}`, A);
    assert.equal(r.status, 200, JSON.stringify(r.body));
    assert.equal(r.body.items[0].product.gtin, EAN_TEA);
    assert.deepEqual(r.body.items[0].match, { kind: 'barcode_exact', certain: true, reasons: ['barcode_exact'] });
    assert.equal(r.body.barcode_valid, true);
  }
  //  نتیجه هیچ امتیازِ عددی ندارد که «قطعیت» خوانده شود
  const any = await S('GET', '/search?q=چای', A);
  assert.ok(any.body.items.every((i) => !('score' in i) && !('score' in i.match)));
});

test('IMG-BANK-04: املای متفاوتِ دری/فارسی، نیم‌فاصله و نامِ انگلیسی پیدا می‌شود؛ نامِ اصلی دست نمی‌خورد', async () => {
  await centralImage({ name: 'نوشابه‌ی کوکا', brand: 'Coca', alt_names: ['Alokozay cola'] });
  for (const q of ['چاي سبز', 'چای‌سبز', 'green tea', 'شین چای', 'نوشابهی کوکا', 'نوشابه ی كوكا']) {
    const r = await S('GET', `/search?q=${encodeURIComponent(q)}`, A);
    assert.ok(r.body.items.length > 0, q);
  }
  const r = await S('GET', `/search?q=${encodeURIComponent('چاي')}`, A);
  assert.ok(r.body.items.some((i) => i.product.name === 'چای سبز'));
  const kok = await S('GET', `/search?q=${encodeURIComponent('نوشابه ی')}`, A);
  assert.equal(kok.body.items[0].product.name, 'نوشابه‌ی کوکا');            // با نیم‌فاصلهٔ خودش
  //  غلطِ املایی در بودجهٔ هستهٔ مشترک ⇒ پیشنهاد، نه قطعی
  const typo = await S('GET', `/search?q=${encodeURIComponent('alokozai')}`, A);
  assert.equal(typo.body.items[0].match.kind, 'spelling_tolerant');
  assert.equal(typo.body.items[0].match.certain, false);
});

test('IMG-BANK-05 / IMG-BANK-17: نامِ یکسان با وزنِ متفاوت جدا و «مبهم»؛ بی تأییدِ دستی انتخاب نمی‌شود', async () => {
  const small = await centralImage({ name: 'روغن آفتاب', brand: 'زر', size_value: '1', size_unit: 'l' });
  await centralImage({ name: 'روغن آفتاب', brand: 'زر', size_value: '5', size_unit: 'l' });
  const r = await S('GET', `/search?q=${encodeURIComponent('روغن آفتاب')}`, A);
  assert.equal(r.body.items.length, 2);
  for (const i of r.body.items) {
    assert.equal(i.ambiguous, true);
    assert.deepEqual(i.distinguish, ['size']);
    assert.equal(i.match.certain, false);
  }
  const imageId = r.body.items.find((i) => i.product.size_value === '1').images[0].id;
  assert.equal(imageId, small.imageId);
  const no = await S('POST', '/select', A, { product_id: 'oil-1', source: 'central', image_id: imageId, expect: { name: 'روغن آفتاب', size_value: '5', size_unit: 'l' } });
  assert.equal(no.status, 409);
  assert.equal(no.body.error.code, 'needs_confirmation');
  const yes = await S('POST', '/select', A, { product_id: 'oil-1', source: 'central', image_id: imageId, expect: { name: 'روغن آفتاب', brand: 'زر' }, confirm: true });
  assert.equal(yes.status, 200, JSON.stringify(yes.body));
  assert.equal(yes.body.match_status, 'probable');
  const res = await S('GET', '/products/oil-1', A);
  assert.equal(res.body.confirmed_by_user, true);
  //  بارکدِ متفاوت حتی با تأیید هم نه
  const teaImg = (await S('GET', `/search?barcode=${EAN_TEA}`, A)).body.items[0].images[0].id;
  const bad = await S('POST', '/select', A, { product_id: 'tea-x', source: 'central', image_id: teaImg, expect: { barcode: EAN_TEA_250 }, confirm: true });
  assert.equal(bad.status, 409, JSON.stringify(bad.body));
  assert.equal(bad.body.error.code, 'barcode_mismatch');
});

test('IMG-BANK-06 / IMG-BANK-07 / IMG-BANK-12: انتخاب فقط برای همان دکان؛ عکسِ خصوصی برای دیگری «نیست» و رویدادِ امنیتی ثبت می‌شود', async () => {
  const r = await S('GET', `/search?barcode=${EAN_TEA}`, A);
  const imageId = r.body.items[0].images[0].id;
  const sel = await S('POST', '/select', A, { product_id: 'tea-1', source: 'central', image_id: imageId, expect: { barcode: EAN_TEA } });
  assert.equal(sel.status, 200, JSON.stringify(sel.body));
  assert.equal(sel.body.match_status, 'exact');
  assert.equal((await S('GET', '/products/tea-1', A)).body.source, 'central');
  assert.equal((await S('GET', '/products/tea-1', B)).body.source, 'none');
  //  هیچ قیمت/موجودی/دکانی در بانک
  const cols = (await many(`SELECT column_name FROM information_schema.columns WHERE table_name IN ('bank_products','shop_product_images')`)).map((c) => c.column_name);
  assert.ok(!cols.some((c) => /price|stock|qty|debt|cost/.test(c)), cols.join(','));
  assert.ok(!JSON.stringify(r.body).match(/price|stock|shop_id/));

  //  عکسِ اختصاصیِ الف
  const own = await SR('POST', '/?product_id=tea-1', A, img());
  assert.equal(own.status, 201, JSON.stringify(own.body));
  const url = `/file/${own.body.id}/1/original`;
  assert.equal((await SR('GET', url, A, undefined)).status, 200);
  const probe = await SR('GET', url, B, undefined);
  assert.equal(probe.status, 404);
  const sec = await one(`SELECT * FROM audit_logs WHERE action='security.image_access_denied' AND shop_id=$1 AND target_id=$2`, [B.shopId, own.body.id]);
  assert.ok(sec, 'رویدادِ امنیتی');
  //  شناسهٔ ناموجود همان ۴۰۴، بی رویداد (نشتِ وجود نیست)
  assert.equal((await SR('GET', '/file/img_00000000000000000000000000/1/thumb', B, undefined)).status, 404);
  const useIt = await S('POST', '/select', B, { product_id: 'x', source: 'shop', image_id: own.body.id });
  assert.equal(useIt.status, 404);
  assert.equal((await S('GET', '/mine', B)).body.items.length, 0);
  //  مدیر هم عکسِ دکان را نمی‌بیند
  assert.equal((await ADR('GET', `/file/${own.body.id}/1/original`, root, undefined)).status, 404);
});

test('IMG-BANK-08: پیشنهادِ عکسِ دکان به بانک فقط با اجازهٔ صریحِ صاحبِ دکان؛ اصل خصوصی می‌ماند', async () => {
  const own = await SR('POST', '/?product_id=soap-1', A, img());
  assert.equal((await S('POST', `/${own.body.id}/propose`, A, {})).body.error.code, 'consent_required');
  assert.equal((await S('POST', `/${own.body.id}/propose`, staffA, { consent: true })).status, 403);
  const p = await S('POST', `/${own.body.id}/propose`, A, { consent: true, product: { name: 'صابون گل', barcode: '' } });
  assert.equal(p.status, 201, JSON.stringify(p.body));
  const again = await S('POST', `/${own.body.id}/propose`, A, { consent: true });
  assert.equal(again.body.id, p.body.id);                                  // تکرار ⇒ همان
  const q = await AD('GET', '/queue?status=NEEDS_REVIEW', clerk);
  const item = q.body.items.find((i) => i.id === p.body.id);
  assert.ok(item && item.from_shop);
  const d = await AD('GET', `/images/${p.body.id}`, clerk);
  assert.equal(d.body.license.status, 'unknown');
  assert.equal(d.body.origin.consent_by, A.user.id);
  //  تا بررسی، دکانِ دیگر نه عکسِ اصل و نه نسخهٔ pending را نمی‌بیند
  assert.equal((await SR('GET', `/file/${p.body.id}/1/thumb`, B, undefined)).status, 404);
  assert.equal((await SR('GET', `/file/${own.body.id}/1/original`, B, undefined)).status, 404);
  const mine = await S('GET', '/mine?product_id=soap-1', A);
  assert.deepEqual(mine.body.items[0].proposals, [{ id: p.body.id, status: 'NEEDS_REVIEW' }]);
});

test('IMG-BANK-09 / IMG-12: نسخهٔ تازهٔ مرکزی فقط پیشنهاد است؛ سنجاقِ دکان، نشانیِ نسخه‌دار و بازگشت', async () => {
  const c = await centralImage({ name: 'برنج سیلا', brand: 'حاجی', size_value: '5', size_unit: 'kg' });
  const sel = await S('POST', '/select', A, { product_id: 'rice-1', source: 'central', image_id: c.imageId, expect: { name: 'برنج سیلا', brand: 'حاجی', size_value: '5', size_unit: 'kg' }, confirm: true });
  assert.equal(sel.status, 200, JSON.stringify(sel.body));
  const own = await SR('POST', '/?product_id=rice-1', C, img());
  await S('POST', '/select', C, { product_id: 'rice-1', source: 'shop', image_id: own.body.id });

  const v2 = await ADR('POST', `/images/${c.imageId}/versions?tool=manual-crop`, clerk, img());
  assert.equal(v2.status, 201, JSON.stringify(v2.body));
  assert.equal(v2.body.version, 2);
  await ADR('PUT', `/images/${c.imageId}/v/2/thumb`, clerk, F.jpeg(400, 300, { seed: ++seed }));
  //  نسخهٔ تأییدنشده انتخاب‌شدنی نیست
  assert.equal((await S('POST', '/select', B, { product_id: 'r', source: 'central', image_id: c.imageId, version: 2, confirm: true })).status, 409);
  assert.equal((await AD('POST', `/images/${c.imageId}/approve`, root, { reason: 'برش بهتر', version: 2 })).status, 200);

  const a = await S('GET', '/products/rice-1', A);
  assert.equal(a.body.version, 1);
  assert.equal(a.body.update_available, 2);
  assert.match(a.body.urls.thumb, /\/1\/thumb$/);
  const cRes = await S('GET', '/products/rice-1', C);
  assert.equal(cRes.body.source, 'shop');
  assert.equal(cRes.body.image_id, own.body.id);
  //  پذیرفتن و بازگشت
  await S('POST', '/select', A, { product_id: 'rice-1', source: 'central', image_id: c.imageId, version: 2, confirm: true });
  const a2 = await S('GET', '/products/rice-1', A);
  assert.equal(a2.body.version, 2);
  assert.match(a2.body.urls.thumb, /\/2\/thumb$/);                           // نشانیِ تازه ⇒ cacheِ کهنه نمی‌ماند
  await S('POST', '/select', A, { product_id: 'rice-1', source: 'central', image_id: c.imageId, version: 1, confirm: true });
  assert.equal((await S('GET', '/products/rice-1', A)).body.version, 1);
});

test('IMG-BANK-10 / IMG-13: بی نتیجه ⇒ فهرستِ خالی؛ کالای بی عکس ⇒ placeholder، نه عکسِ مشابه', async () => {
  const r = await S('GET', `/search?q=${encodeURIComponent('هیچ‌چیزِ ناموجود')}`, A);
  assert.equal(r.status, 200);
  assert.deepEqual(r.body.items, []);
  assert.equal((await S('GET', '/search', A)).body.items.length, 0);         // بی پرس‌وجو کلِ بانک نمی‌آید
  const none = await S('GET', '/products/unknown-1', A);
  assert.deepEqual([none.body.source, none.body.placeholder], ['none', true]);
  const legacy = await S('GET', '/products/p-legacy', A);                 // عکسِ امروزِ کالا همان می‌ماند
  assert.equal(legacy.body.source, 'legacy_photo');
});

test('IMG-BANK-13: فایلِ تکراری فقط نشان داده می‌شود — نه حذف، نه ادغام', async () => {
  const same = img();
  const one1 = await ADR('POST', '/images', clerk, same);
  const two = await ADR('POST', '/images', clerk, same);
  assert.deepEqual(two.body.duplicate_of, [one1.body.id]);
  assert.equal((await AD('GET', `/images/${one1.body.id}`, clerk)).status, 200);
  assert.equal((await AD('GET', `/images/${two.body.id}`, clerk)).body.duplicate_of[0], one1.body.id);
  const blobs = await one('SELECT COUNT(*)::int AS n FROM image_versions WHERE image_id IN ($1,$2)', [one1.body.id, two.body.id]);
  const hashes = await one('SELECT COUNT(DISTINCT blob_hash)::int AS n FROM image_versions WHERE image_id IN ($1,$2)', [one1.body.id, two.body.id]);
  assert.deepEqual([blobs.n, hashes.n], [2, 1]);                           // یک بار ذخیره
});

test('IMG-BANK-14: صفحه‌بندی با cursor و سقفِ ۵۰؛ فقط نشانیِ thumbnail، نه فایلِ اصل', async () => {
  for (let i = 0; i < 60; i++) {
    const p = await AD('POST', '/products', clerk, { name: `بیسکویت ${i}`, brand: 'نان' });
    assert.equal(p.status, 201);
  }
  const p1 = await S('GET', `/search?q=${encodeURIComponent('بیسکویت')}&limit=500`, A);
  assert.equal(p1.body.items.length, 50);
  assert.ok(p1.body.next_cursor);
  const p2 = await S('GET', `/search?q=${encodeURIComponent('بیسکویت')}&limit=50&cursor=${p1.body.next_cursor}`, A);
  assert.equal(p2.body.items.length, 10);
  assert.equal(p2.body.next_cursor, null);
  const ids = new Set([...p1.body.items, ...p2.body.items].map((i) => i.product.id));
  assert.equal(ids.size, 60);
  const tea = await S('GET', `/search?barcode=${EAN_TEA}`, A);
  const text = JSON.stringify(tea.body);
  assert.ok(!/original|base64|"data"/.test(text), text);
  //  کالای بی thumbnail: نشانی null، برنامه placeholder نشان می‌دهد
  assert.equal(tea.body.items[0].images[0].urls.thumb, null);
});

test('IMG-11 / IMG-12: نسخهٔ کوچک سنجیده، بی EXIF، یک‌بارنویس و با cacheِ نسخه‌دار سرو می‌شود', async () => {
  const c = await centralImage({ name: 'شامپو', brand: 'گل' }, { thumb: false });
  const base = `/images/${c.imageId}/v/1`;
  assert.equal((await ADR('PUT', `${base}/thumb`, clerk, F.jpeg(400, 400, { seed: ++seed }))).body.error.code, 'aspect_mismatch');
  assert.equal((await ADR('PUT', `${base}/thumb`, clerk, F.jpeg(800, 600, { seed: ++seed }))).body.error.code, 'derivative_too_big');
  assert.equal((await ADR('PUT', `${base}/thumb`, clerk, F.jpeg(400, 300, { orientation: 6, seed: ++seed }))).body.error.code, 'orientation_not_applied');
  assert.equal((await ADR('PUT', `${base}/thumb`, clerk, Buffer.from('not an image at all, just text'))).status, 400);
  const good = F.jpeg(400, 300, { gps: true, seed: ++seed });
  const put = await ADR('PUT', `${base}/thumb`, clerk, good);
  assert.equal(put.status, 200, JSON.stringify(put.body));
  assert.equal(put.body.stripped, true);
  assert.equal((await ADR('PUT', `${base}/thumb`, clerk, good)).body.unchanged, true);   // retry بی‌خطر
  assert.equal((await ADR('PUT', `${base}/thumb`, clerk, F.jpeg(400, 300, { seed: ++seed }))).status, 409);
  const file = await h.download(`/api/shop/images/file/${c.imageId}/1/thumb`, { token: A.token });
  assert.equal(file.status, 200);
  assert.match(file.headers.get('cache-control'), /immutable/);
  assert.equal(file.headers.get('content-type'), 'image/jpeg');
  assert.ok(file.buffer.indexOf(Buffer.from('Exif')) < 0);
  //  فایلِ اصلِ مرکزی برای دکان نیست
  assert.equal((await SR('GET', `/file/${c.imageId}/1/original`, A, undefined)).status, 404);
});

test('IMG-BANK-15: بایگانی ⇒ انتخابِ تازه نه، از جست‌وجو بیرون؛ سنجاقِ قبلی سالم', async () => {
  const c = await centralImage({ name: 'پنیر', brand: 'کابل', gtin: F.ean13('400000000001') });
  await S('POST', '/select', A, { product_id: 'cheese-1', source: 'central', image_id: c.imageId, expect: { barcode: F.ean13('400000000001') } });
  const before = await S('GET', '/search?q=پنیر', B);
  assert.equal(before.body.items[0].images.length, 1);
  const arch = await AD('POST', `/images/${c.imageId}/archive`, root, { reason: 'بستهٔ قدیمی' });
  assert.equal(arch.status, 200, JSON.stringify(arch.body));
  const s = await S('GET', '/search?q=پنیر', B);
  assert.equal(s.body.items[0].images.length, 0);
  //  «cache باطل»: نسخهٔ بانک عوض می‌شود ⇒ کشِ بی‌اینترنتیِ برنامه (`BankSearchCache.observe`) صفحه‌های قبلی را دور می‌ریزد
  assert.ok(Number(s.body.bank_rev) > Number(before.body.bank_rev), `${before.body.bank_rev} → ${s.body.bank_rev}`);
  assert.equal(s.body.live, true);
  const sel = await S('POST', '/select', B, { product_id: 'c', source: 'central', image_id: c.imageId, confirm: true });
  assert.equal(sel.body.error.code, 'image_not_selectable');
  const a = await S('GET', '/products/cheese-1', A);
  assert.equal(a.body.archived, true);
  assert.equal((await SR('GET', `/file/${c.imageId}/1/thumb`, A, undefined)).status, 200);   // سابقه نمی‌شکند
  assert.equal((await SR('GET', `/file/${c.imageId}/1/thumb`, B, undefined)).status, 404);
});

test('IMG-BANK-16: از مرکزی به عکسِ خود و بازگشت؛ منبعِ فعال همیشه صریح', async () => {
  const c = await centralImage({ name: 'شکر', brand: 'سفید', gtin: F.ean13('400000000002') });
  await S('POST', '/select', B, { product_id: 'sugar', source: 'central', image_id: c.imageId, expect: { barcode: F.ean13('400000000002') } });
  const own = await SR('POST', '/?product_id=sugar', B, img());
  await S('POST', '/select', B, { product_id: 'sugar', source: 'shop', image_id: own.body.id });
  const r = await S('GET', '/products/sugar', B);
  assert.deepEqual([r.body.source, r.body.central.image_id], ['shop', c.imageId]);
  const back = await S('POST', '/select', B, { product_id: 'sugar', source: 'central' });
  assert.equal(back.status, 200, JSON.stringify(back.body));
  const r2 = await S('GET', '/products/sugar', B);
  assert.deepEqual([r2.body.source, r2.body.image_id, r2.body.version], ['central', c.imageId, 1]);
});

test('IMG-04 (بخشِ بی AI) / IMG-08: اصل محفوظ؛ پردازشِ بیرونی ۵۰۳ و هیچ ارسال یا ردیفی', async () => {
  const own = await SR('POST', '/?product_id=x-1', A, F.jpeg(800, 600, { gps: true, seed: ++seed }));
  const before = await count('image_versions');
  const ext = await S('POST', `/${own.body.id}/process`, A, { step: 'background_removal' });
  assert.equal(ext.status, 503);
  assert.equal(ext.body.error.code, 'step_unavailable');
  assert.equal(await count('image_versions'), before);
  const strip = await S('POST', `/${own.body.id}/process`, A, { step: 'strip_metadata' });
  assert.equal(strip.status, 201, JSON.stringify(strip.body));
  assert.equal(strip.body.version, 2);
  const orig = await h.download(`/api/shop/images/file/${own.body.id}/1/original`, { token: A.token });
  assert.ok(orig.buffer.indexOf(Buffer.from('Exif')) > 0);                    // اصل دست‌نخورده
  const cfg = await S('GET', '/config', A);
  assert.equal(cfg.body.pipeline.find((s) => s.id === 'enhance').status, 'blocked_external');
});

test('IMG-BANK-01 / IMG-01 / IMG-02 / IMG-03 / IMG-09: ۱۰۰۰ ردیفِ CSV با فایل‌ها؛ گزارشِ جدا، بی توقفِ دسته، retryِ بی‌تکرار', async () => {
  const batch = await AD('POST', '/batches', clerk, { license_source: 'کاتالوگِ تأمین‌کننده', license_status: 'licensed', commercial_use: true });
  assert.equal(batch.status, 201, JSON.stringify(batch.body));
  const id = batch.body.id;
  const N = 1000;
  const lines = ['sku,barcode,name,brand,size,unit,image_path,license_status'];
  for (let i = 0; i < N; i += 25) {
    const puts = await Promise.all(Array.from({ length: 25 }, (_, k) => ADR('PUT', `/batches/${id}/files/f${i + k}.jpg`, clerk, img())));
    assert.ok(puts.every((p) => p.status === 200 && p.body.status === 'ok'));
  }
  for (let i = 0; i < N; i++) lines.push(`SKU-${i},,کالای ${i},برند,${100 + i},g,photos/f${i}.jpg,`);
  //  خرابی‌ها: exe به نامِ jpg، JPEGِ نیمه‌کاره، PNG با پسوندِ jpg (فقط نشان)
  assert.equal((await ADR('PUT', `/batches/${id}/files/fake.jpg`, clerk, Buffer.from('MZ' + 'x'.repeat(400)))).body.status, 'failed');
  assert.equal((await ADR('PUT', `/batches/${id}/files/half.jpg`, clerk, img().subarray(0, 50))).body.error, 'image_corrupt');
  assert.deepEqual((await ADR('PUT', `/batches/${id}/files/pic.jpg`, clerk, F.png(600, 600, { seed: ++seed }))).body.quality_flags, ['extension_mismatch']);
  //  ردیف‌های مشکل‌دار جای ۶ ردیفِ آخر
  lines.splice(N - 5, 6,
    'SKU-X1,,کالای خراب,برند,1,g,fake.jpg,',
    'SKU-X2,,کالای نیمه,برند,1,g,half.jpg,',
    'SKU-X3,,کالای بی فایل,برند,1,g,nothere.jpg,',
    `,123456789013,بارکد غلط,برند,1,g,f${N - 3}.jpg,`,
    `,${EAN_TEA},چای سبز,الکوزی,250,g,f${N - 2}.jpg,`,                    // IMG-09: همان بارکد، اندازهٔ دیگر
    `,,روغن آفتاب,زر,,,f${N - 1}.jpg,`);                                   // IMG-02: دو کالای هم‌نام
  const csv = lines.join('\n');

  const dry = await AD('POST', `/batches/${id}/rows`, clerk, { csv, dry_run: true });
  assert.equal(dry.status, 200, JSON.stringify(dry.body).slice(0, 300));
  assert.equal(await one(`SELECT COUNT(*)::int AS n FROM images WHERE batch_id=$1`, [id]).then((r) => r.n), 0);

  const run = await AD('POST', `/batches/${id}/rows`, clerk, { csv });
  assert.equal(run.status, 200);
  assert.equal(run.body.rows.length, N);
  assert.deepEqual(run.body.counts, { ok: N - 6, failed: 4, ambiguous: 2 });
  assert.deepEqual(run.body.counts, dry.body.counts);                      // پیش‌نمایش = نتیجه
  const byNo = Object.fromEntries(run.body.rows.map((r) => [r.row_no, r]));
  assert.equal(byNo[N - 4].error, 'image_type');
  assert.equal(byNo[N - 3].error, 'image_corrupt');
  assert.equal(byNo[N - 2].error, 'file_missing');
  assert.equal(byNo[N - 1].error, 'bad_barcode');
  const amb = await one('SELECT bank_product_id, match_status FROM images WHERE id=$1', [byNo[N].image_id]);
  assert.deepEqual([amb.bank_product_id, amb.match_status], ['', 'ambiguous']);         // وصل نشد
  assert.equal(JSON.parse((await one('SELECT proposal FROM images WHERE id=$1', [byNo[N + 1].image_id])).proposal).candidates.length, 2);
  //  همه در pending؛ هیچ‌کدام خودبه‌خود مرکزی نشد
  const sp = await many(`SELECT space, status, COUNT(*)::int AS n FROM images WHERE batch_id=$1 GROUP BY space, status`, [id]);
  assert.deepEqual(sp.map((r) => [r.space, r.status, r.n]), [['pending', 'NEEDS_REVIEW', N - 4]]);

  //  retry ِ همان CSV: هیچ عکسِ تازه‌ای ساخته نمی‌شود
  const imagesBefore = await count('images');
  const again = await AD('POST', `/batches/${id}/rows`, clerk, { csv });
  assert.equal(await count('images'), imagesBefore);
  assert.ok(again.body.rows.filter((r) => r.status !== 'failed').every((r) => r.replay));

  const rep = await h.download(`/api/admin/image-bank/batches/${id}/report?format=csv`, { token: clerk });
  const csvOut = rep.buffer.toString('utf8');
  assert.equal(csvOut.trim().split(/\r\n/).length, 1 + 4 + 2 + 2);          // سرآیند + ناموفق + مبهم + فایل‌های خراب
  assert.match(csvOut, /bad_barcode/);

  //  توقف/ادامه/لغو
  assert.equal((await AD('POST', `/batches/${id}/status`, clerk, { status: 'paused' })).status, 200);
  assert.equal((await AD('POST', `/batches/${id}/rows`, clerk, { csv: lines.slice(0, 2).join('\n') })).body.error.code, 'batch_paused');
  assert.equal((await AD('POST', `/batches/${id}/status`, clerk, { status: 'open' })).status, 200);
  const cancel = await AD('POST', `/batches/${id}/status`, clerk, { status: 'cancelled' });
  assert.equal(cancel.body.rejected, N - 4);
  assert.equal(await count('images') , imagesBefore);                      // رد شدند، پاک نه
});
