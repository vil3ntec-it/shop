'use strict';
/**
 * IMG-15 روی API و دیتابیسِ واقعی (PGlite): نشان‌های کیفیتِ ‎lib/image-quality.js‎ پشتِ پرچمِ
 * خاموشِ `image_bank.quality` در بارگذاریِ دکان، مدیر و دسته‌ای؛ «مشابهِ احتمالی» در همان دامنهٔ دید.
 * خاموش ⇒ همان کلیدها و مقدارهای پیشین و هیچ نوشتنی در ستون‌های ۱۹۴.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const F = require('./image-fixtures');
const { query, one, newId, now } = require('../src/db');

let root = '';
let clerk = '';
let A; let B;

const sharp = F.pngImage(800, 600, F.scene(800, 600));
const dupJpeg = F.jpegImage(640, 480, F.scene(640, 480), { quality: 80, color: true });
const dupSmall = F.jpegImage(512, 384, F.scene(512, 384), { quality: 85 });
const dark = F.pngImage(800, 600, F.scene(800, 600, { bg: 25, lo: 0, hi: 60 }));
const other = (w, hh) => F.pngImage(w, hh, F.scene(w, hh, { box: [0.05, 0.1, 0.45, 0.5] }));
const blurry = F.pngImage(800, 600, F.blurred(800, 600, F.scene(800, 600), 4));   // نسخهٔ تارِ همان صحنه

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
const AD = (method, path, body) => h.api(method, '/api/admin/image-bank' + path, { token: clerk, body });
const ADR = (method, path, buf) => h.raw(method, '/api/admin/image-bank' + path, buf, { token: clerk });
const n = async (sql, p = []) => Number((await one(sql, p)).n);
const up = async (buf) => {
  const r = await ADR('POST', '/images', buf);
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return r.body;
};

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
  await setFlag('all', 'image_bank.central', true);
  await setFlag('all', 'shop.image_bank', true);
});
test.after(async () => { await h.stop(); });

test('IMG-15 پرچمِ خاموش (پیش‌فرض): همان کلیدها و نشان‌های پیشین، بی رمزگشایی و بی نوشتن در ستون‌های تازه', async () => {
  //  عکسِ تار و تاریک ⇒ هیچ نشانِ تازه؛ فقط too_small ِ پیشین
  const a = await up(blurry);
  assert.deepEqual(Object.keys(a), ['id', 'duplicate_of', 'status', 'width', 'height', 'quality_flags']);
  assert.deepEqual(a.quality_flags, []);
  assert.deepEqual((await up(dark)).quality_flags, []);
  assert.deepEqual((await up(other(200, 150))).quality_flags, ['too_small']);
  const s = await SR('POST', '/', A, dark);
  assert.equal(s.status, 201, JSON.stringify(s.body));
  assert.deepEqual(Object.keys(s.body), ['id', 'duplicate_of', 'version', 'width', 'height', 'mime', 'quality_flags']);
  assert.deepEqual(s.body.quality_flags, []);
  const batch = await AD('POST', '/batches', { license_source: 'آزمونِ کیفیت', license_status: 'unknown' });
  const f = await ADR('PUT', `/batches/${batch.body.id}/files/b.png`, blurry);
  assert.deepEqual(Object.keys(f.body), ['name', 'status', 'error', 'quality_flags', 'sha256']);
  assert.deepEqual(f.body.quality_flags, []);
  //  فهرست‌ها و جزئیات بی کلیدِ تازه
  const rv = await AD('GET', '/review?status=NEEDS_REVIEW&space=pending&limit=50');
  assert.equal(rv.status, 200);
  assert.ok(rv.body.items.length >= 3 && rv.body.items.every((i) => !('near_duplicate_of' in i)));
  const d = await AD('GET', `/images/${a.id}`);
  assert.equal(d.status, 200);
  assert.ok(!('near_duplicate_of' in d.body));
  const mine = await S('GET', '/mine', A);
  assert.ok(mine.body.items.every((i) => !('near_duplicate_of' in i)));
  assert.equal(await n(`SELECT COUNT(*)::int AS n FROM image_blobs WHERE dhash<>''`), 0);
  assert.equal(await n(`SELECT COUNT(*)::int AS n FROM images WHERE near_duplicate_of<>'[]'`), 0);
});

test('IMG-15 پرچمِ روشن، بانک: هر عکس نشانِ خودش؛ مشابهِ احتمالی با dHash، همان محتوا فقط در duplicate_of', async () => {
  await setFlag('all', 'image_bank.quality', true);
  const s1 = await up(sharp);
  assert.deepEqual([s1.quality_flags, s1.near_duplicate_of], [[], []]);
  assert.equal(s1.quality.measured, true);
  assert.ok(s1.quality.sharpness > 100);
  //  همان صحنه، JPEG ِ ۶۴۰×۴۸۰ ⇒ مشابه
  const d1 = await up(dupJpeg);
  assert.deepEqual([d1.quality_flags, d1.near_duplicate_of], [['near_duplicate'], [s1.id]]);
  assert.equal(d1.quality.jpeg_quality, 80);
  //  همان بایت‌ها ⇒ duplicate_of (مثلِ پیش) و نه «مشابه» ِ خودش
  const s2 = await up(sharp);
  assert.deepEqual(s2.duplicate_of, [s1.id]);
  assert.deepEqual(s2.near_duplicate_of, [d1.id]);
  //  تاری، تاریکی، کوچکی، WebP — هر کدام دقیقاً نشانِ خودش
  const b = await up(blurry);                                   // نسخهٔ تارِ همان صحنه ⇒ تار و مشابه
  assert.deepEqual(b.quality_flags, ['blurry', 'near_duplicate']);
  assert.ok(b.near_duplicate_of.includes(s1.id));
  assert.ok(b.quality.sharpness < 100);
  const dk = await up(F.pngImage(800, 600, F.scene(800, 600, { bg: 25, lo: 0, hi: 60, box: [0.55, 0.5, 0.95, 0.9] })));
  assert.deepEqual(dk.quality_flags, ['too_dark']);
  assert.deepEqual((await up(other(200, 150))).quality_flags, ['too_small']);
  const w = await up(F.webp(640, 480));
  assert.deepEqual([w.quality_flags, w.quality.reason], [['quality_unmeasured'], 'format']);
  //  بلاب‌ها: هش یا «-»
  assert.match((await one('SELECT dhash FROM image_blobs b JOIN image_versions v ON v.blob_hash=b.hash WHERE v.image_id=$1', [s1.id])).dhash, /^[0-9a-f]{16}$/);
  assert.equal((await one('SELECT dhash FROM image_blobs b JOIN image_versions v ON v.blob_hash=b.hash WHERE v.image_id=$1', [w.id])).dhash, '-');
  //  صفِ بررسی و جزئیات: شناسهٔ مشابه‌ها فقط وقتی هست
  const rv = await AD('GET', '/review?status=NEEDS_REVIEW&space=pending&limit=50');
  const byId = Object.fromEntries(rv.body.items.map((i) => [i.id, i]));
  assert.deepEqual([byId[d1.id].quality_flags, byId[d1.id].near_duplicate_of], [['near_duplicate'], [s1.id]]);
  assert.ok(!('near_duplicate_of' in byId[s1.id]));
  assert.deepEqual(byId[b.id].quality_flags, ['blurry', 'near_duplicate']);
  assert.deepEqual((await AD('GET', `/images/${d1.id}`)).body.near_duplicate_of, [s1.id]);
  //  بارگذاری هرگز رد نمی‌شود: همه ۲۰۱ بودند و همه در صف
  for (const id of [s1.id, d1.id, s2.id, b.id, dk.id, w.id]) assert.equal(byId[id].status, 'NEEDS_REVIEW');
});

test('IMG-15 دکان: مشابه فقط میانِ عکس‌های همان دکان؛ پیشنهاد به بانک دوباره در دامنهٔ بانک سنجیده می‌شود', async () => {
  const a1 = await SR('POST', '/', A, sharp);
  assert.equal(a1.status, 201, JSON.stringify(a1.body));
  assert.deepEqual([a1.body.quality_flags, a1.body.near_duplicate_of], [[], []]);   // عکس‌های بانک دیده نمی‌شوند
  const a2 = (await SR('POST', '/', A, dupJpeg)).body;
  assert.deepEqual([a2.quality_flags, a2.near_duplicate_of], [['near_duplicate'], [a1.body.id]]);
  const b1 = (await SR('POST', '/', B, dupJpeg)).body;                               // دکانِ دیگر: هیچ
  assert.deepEqual([b1.duplicate_of, b1.near_duplicate_of, b1.quality_flags], [[], [], []]);
  const mine = await S('GET', '/mine', A);
  const it = mine.body.items.find((i) => i.id === a2.id);
  assert.deepEqual([it.quality_flags, it.near_duplicate_of], [['near_duplicate'], [a1.body.id]]);
  assert.ok(!('near_duplicate_of' in mine.body.items.find((i) => i.id === a1.body.id)));
  //  پیشنهاد: شناسهٔ عکس‌های دکان به بانک نمی‌رود؛ مشابه‌ها از central/pending
  const p = await S('POST', `/${a2.id}/propose`, A, { consent: true });
  assert.equal(p.status, 201, JSON.stringify(p.body));
  const pend = await one('SELECT quality_flags, near_duplicate_of FROM images WHERE id=$1', [p.body.id]);
  const near = JSON.parse(pend.near_duplicate_of);
  assert.deepEqual(JSON.parse(pend.quality_flags), ['near_duplicate']);
  assert.ok(near.length > 0 && !near.includes(a1.body.id));
  for (const id of near) assert.ok(['central', 'pending'].includes((await one('SELECT space FROM images WHERE id=$1', [id])).space));
  //  مقدارِ خودِ دکان بر `all` جلو می‌افتد: B خاموش ⇒ همان پاسخِ پیشین
  await setFlag(B.shopId, 'image_bank.quality', false);
  const b2 = (await SR('POST', '/', B, blurry)).body;
  assert.deepEqual(Object.keys(b2), ['id', 'duplicate_of', 'version', 'width', 'height', 'mime', 'quality_flags']);
  assert.deepEqual(b2.quality_flags, []);
});

test('IMG-15 دسته‌ای: فایل با نشان و سنجه؛ فایلِ رسیده پیش از روشن شدن هنگامِ ثبت سنجیده می‌شود', async () => {
  const batch = await AD('POST', '/batches', { license_source: 'کاتالوگِ آزمون', license_status: 'unknown' });
  const id = batch.body.id;
  await setFlag('all', 'image_bank.quality', false);
  const early = await ADR('PUT', `/batches/${id}/files/early.jpg`, dupSmall);
  assert.deepEqual(Object.keys(early.body), ['name', 'status', 'error', 'quality_flags', 'sha256']);
  assert.equal((await one('SELECT dhash FROM image_blobs WHERE hash=$1', [early.body.sha256])).dhash, '');
  await setFlag('all', 'image_bank.quality', true);
  const late = await ADR('PUT', `/batches/${id}/files/late.png`, blurry);
  assert.deepEqual([late.body.status, late.body.quality_flags, late.body.quality.measured], ['ok', ['blurry'], true]);
  const run = await AD('POST', `/batches/${id}/rows`, {
    csv: ['sku,barcode,name,brand,size,unit,image_path,license_status',
      'SKU-Q1,,کالای کیفیت یک,برند,100,g,early.jpg,', 'SKU-Q2,,کالای کیفیت دو,برند,200,g,late.png,'].join('\n'),
  });
  assert.equal(run.status, 200, JSON.stringify(run.body).slice(0, 300));
  const [r1, r2] = run.body.rows;
  const i1 = await one('SELECT quality_flags, near_duplicate_of FROM images WHERE id=$1', [r1.image_id]);
  assert.deepEqual(JSON.parse(i1.quality_flags), ['near_duplicate']);                 // همان صحنهٔ بانک
  assert.ok(JSON.parse(i1.near_duplicate_of).length > 0);
  assert.match((await one('SELECT dhash FROM image_blobs WHERE hash=$1', [early.body.sha256])).dhash, /^[0-9a-f]{16}$/);
  assert.deepEqual(JSON.parse((await one('SELECT quality_flags FROM images WHERE id=$1', [r2.image_id])).quality_flags)
    .filter((f) => f !== 'near_duplicate'), ['blurry']);
});
