'use strict';
/**
 * M3 — لوگو و تصویرِ سرِ ویترین (`/api/shop/storefront/media`، مهاجرتِ ۱۳۳).
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) ⛔ پرچمِ `shop.storefront.media` خاموش ⇒ ۴۰۳ `media_disabled`، هیچ ردیف، نمای عمومی تهی.
 *   ۲) نوع فقط از محتوا (پسوند/Content-Type باور نمی‌شود)؛ فقط JPEG/PNG/WebP؛ فایلِ خراب ۴۰۰.
 *   ۳) سقفِ حجم (۴۱۳ `too_large`) و ابعادِ ثابت: لوگو مربع ۱۲۸..۵۱۲، سر ۳:۱ با عرضِ ۶۰۰..۱۵۰۰؛
 *      EXIF orientation > ۱ رد؛ فرادادهٔ حساس (EXIF/GPS/متن) پاک.
 *   ۴) نسخه: هر بارگذاری نسخهٔ تازه، همان محتوا ⇒ همان نسخه، فقط دو نسخهٔ آخر، «برداشتن» شماره را
 *      بازنمی‌گرداند؛ عمومی فقط نسخهٔ جاری.
 *   ۵) دسترسی: فقط صاحب/مدیرِ همان دکان می‌نویسد؛ شاگرد می‌بیند ولی نمی‌نویسد؛ دکانِ دیگر
 *      عکسِ این دکان را با هیچ نشانی‌ای نمی‌خواند.
 *   ۶) هم‌زمانی: بارگذاری‌های هم‌زمان شماره‌های یکتا و دقیقاً یک نسخهٔ جاری.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const F = require('./image-fixtures');
const { query, many, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');

let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;
const BASE = '/api/shop/storefront/media';

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

/** دکانِ با ویترینِ روشن؛ `media` پرچمِ M3 را روشن می‌کند. */
async function liveStore(label, { media = true } = {}) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { ...u, token: u.accessToken, shopId: made.body.shop.id };
  await setFlag(o.shopId, 'shop.orders', true);
  await setFlag(o.shopId, 'shop.storefront', true);
  if (media) await setFlag(o.shopId, 'shop.storefront.media', true);
  const slug = `m-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug, enabled: true, title: `ویترینِ ${label}` }, { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  return { ...o, slug };
}

const up = (s, kind, buf, type = 'image/jpeg') => h.raw('PUT', `${BASE}/${kind}`, buf, { token: s.token, headers: { 'Content-Type': type } });
const rows = (shopId) => many('SELECT kind, version, current, mime FROM storefront_media WHERE shop_id=$1 ORDER BY kind, version', [shopId]);
const code = (r) => r.body?.error?.code;

test('پرچم خاموش: ۴۰۳ media_disabled، هیچ ردیف، نمای عمومی تهی', async () => {
  const s = await liveStore('خاموش', { media: false });
  const cfg = await h.get('/api/shop/storefront/config', { token: s.token });
  assert.equal(cfg.body.media, false);
  const g = await h.get(BASE, { token: s.token });
  assert.equal(g.status, 403);
  assert.equal(code(g), 'media_disabled');
  const p = await up(s, 'logo', F.jpeg(256, 256));
  assert.equal(p.status, 403);
  assert.equal(code(p), 'media_disabled');
  assert.equal((await h.del(`${BASE}/logo`, { token: s.token })).status, 403);
  assert.deepEqual(await rows(s.shopId), []);
  const pub = await h.get(`/api/store/${s.slug}`);
  assert.equal(pub.status, 200);
  assert.deepEqual(pub.body.store.media, { logo: null, header: null });
});

test('بارگذاریِ درست: نسخهٔ ۱، فراداده پاک، نشانیِ کارمند و عمومی', async () => {
  const s = await liveStore('درست');
  assert.equal((await h.get('/api/shop/storefront/config', { token: s.token })).body.media, true);
  const r = await up(s, 'logo', F.jpeg(256, 256, { gps: true }));
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.version, 1);
  assert.equal(r.body.logo.width, 256);
  assert.equal(r.body.logo.mime, 'image/jpeg');
  assert.equal(r.body.logo.url, `${BASE}/logo/1`);
  assert.equal(r.body.header, null);

  const f = await h.download(r.body.logo.url, { token: s.token });
  assert.equal(f.status, 200);
  assert.equal(f.headers.get('content-type'), 'image/jpeg');
  assert.match(f.headers.get('cache-control'), /immutable/);
  assert.ok(!f.buffer.includes(Buffer.from('Exif\0\0', 'latin1')), 'EXIF/GPS پاک');
  assert.ok(!f.buffer.includes(Buffer.from('34.5,69.1', 'latin1')), 'متنِ COM پاک');

  const h1 = await h.raw('PUT', `${BASE}/header`, F.png(1200, 400), { token: s.token, headers: { 'Content-Type': 'image/png' } });
  assert.equal(h1.status, 200, JSON.stringify(h1.body));
  assert.equal(h1.body.header.mime, 'image/png');

  const pub = await h.get(`/api/store/${s.slug}`);
  assert.equal(pub.body.store.media.logo.url, `/api/store/${s.slug}/media/logo/1`);
  assert.equal(pub.body.store.media.header.width, 1200);
  assert.equal(pub.body.store.media.logo.hash, undefined, 'نمای عمومی hash ندارد');
  const pf = await h.download(pub.body.store.media.header.url);
  assert.equal(pf.status, 200);
  assert.equal(pf.headers.get('content-type'), 'image/png');
  assert.ok(!pf.buffer.includes(Buffer.from('tEXt', 'latin1')), 'قطعهٔ متنیِ PNG پاک');

  const audit = await many(`SELECT action FROM audit_logs WHERE shop_id=$1 AND action LIKE 'storefront.media%'`, [s.shopId]);
  assert.equal(audit.length, 2);
});

test('نوع از محتوا: PNG با Content-Type ِ jpeg همان PNG است؛ GIF و بایتِ خراب رد', async () => {
  const s = await liveStore('نوع');
  const a = await up(s, 'logo', F.png(300, 300), 'image/jpeg');
  assert.equal(a.status, 200, JSON.stringify(a.body));
  assert.equal(a.body.logo.mime, 'image/png');
  const w = await up(s, 'logo', F.webp(300, 300), 'application/octet-stream');
  assert.equal(w.status, 200, JSON.stringify(w.body));
  assert.equal(w.body.logo.mime, 'image/webp');

  const gif = Buffer.concat([Buffer.from('GIF89a', 'latin1'), Buffer.from([0x2c, 0x01, 0x2c, 0x01, 0, 0, 0]), Buffer.alloc(100)]);
  const g = await up(s, 'logo', gif, 'image/jpeg');
  assert.equal(g.status, 400, JSON.stringify(g.body));
  const junk = await up(s, 'logo', Buffer.from('<?php echo 1; ?>'.repeat(20)), 'image/jpeg');
  assert.equal(junk.status, 400);
  const cut = F.jpeg(256, 256).subarray(0, 60);
  assert.equal((await up(s, 'logo', cut)).status, 400);
  assert.equal((await up(s, 'logo', Buffer.alloc(0))).status, 400);
  assert.equal((await up(s, 'banner', F.jpeg(256, 256))).status, 400);
  assert.equal(code(await up(s, 'banner', F.jpeg(256, 256))), 'bad_kind');
  //  فقط دو نسخهٔ درست
  assert.deepEqual((await rows(s.shopId)).map((r) => r.version), [1, 2]);
});

test('حجم و ابعادِ ثابت: ۴۱۳ too_large، نسبت، کمینه/بیشینه، چرخشِ EXIF', async () => {
  const s = await liveStore('اندازه');
  const pad = (buf, n) => Buffer.concat([buf, Buffer.alloc(n)]);
  const big = await up(s, 'logo', pad(F.jpeg(256, 256), 151 * 1024));
  assert.equal(big.status, 413);
  assert.equal(code(big), 'too_large');
  const huge = await up(s, 'header', pad(F.jpeg(1200, 400), 600 * 1024));
  assert.equal(huge.status, 413);
  assert.equal(code(huge), 'too_large');

  assert.equal(code(await up(s, 'logo', F.jpeg(256, 200))), 'aspect_mismatch');
  assert.equal(code(await up(s, 'logo', F.jpeg(100, 100))), 'too_small');
  assert.equal(code(await up(s, 'logo', F.jpeg(600, 600))), 'too_big');
  assert.equal(code(await up(s, 'header', F.jpeg(400, 400))), 'aspect_mismatch');
  assert.equal(code(await up(s, 'header', F.jpeg(450, 150))), 'too_small');
  assert.equal(code(await up(s, 'header', F.jpeg(1800, 600))), 'too_big');
  assert.equal(code(await up(s, 'logo', F.jpeg(256, 256, { orientation: 6 }))), 'orientation_not_applied');
  assert.deepEqual(await rows(s.shopId), []);
  //  مرزها پذیرفته می‌شوند
  assert.equal((await up(s, 'logo', F.jpeg(128, 128))).status, 200);
  assert.equal((await up(s, 'header', F.jpeg(1500, 500))).status, 200);
});

test('نسخه: تازه برای هر محتوا، تکرار بی‌اثر، دو نسخهٔ آخر، برداشتن شماره را برنمی‌گرداند', async () => {
  const s = await liveStore('نسخه');
  const v1 = await up(s, 'logo', F.jpeg(256, 256, { seed: 1 }));
  const v2 = await up(s, 'logo', F.jpeg(256, 256, { seed: 2 }));
  assert.equal(v2.body.version, 2);
  const again = await up(s, 'logo', F.jpeg(256, 256, { seed: 2 }));
  assert.equal(again.status, 200);
  assert.equal(again.body.version, 2);
  assert.equal(again.body.replayed, true);
  //  کارمند نسخهٔ پیشین را هنوز می‌خواند؛ عمومی فقط جاری
  assert.equal((await h.download(v1.body.logo.url, { token: s.token })).status, 200);
  assert.equal((await h.download(`/api/store/${s.slug}/media/logo/1`)).status, 404);
  assert.equal((await h.download(`/api/store/${s.slug}/media/logo/2`)).status, 200);

  await up(s, 'logo', F.jpeg(256, 256, { seed: 3 }));
  assert.deepEqual((await rows(s.shopId)).map((r) => [r.version, r.current]), [[2, false], [3, true]]);
  assert.equal((await h.download(v1.body.logo.url, { token: s.token })).status, 404);

  const d = await h.del(`${BASE}/logo`, { token: s.token });
  assert.equal(d.status, 200);
  assert.equal(d.body.removed, true);
  assert.equal(d.body.logo, null);
  assert.equal((await h.get(`/api/store/${s.slug}`)).body.store.media.logo, null);
  assert.equal((await h.download(`/api/store/${s.slug}/media/logo/3`)).status, 404);
  assert.equal((await h.del(`${BASE}/logo`, { token: s.token })).body.removed, false);

  const v4 = await up(s, 'logo', F.jpeg(256, 256, { seed: 3 }));
  assert.equal(v4.body.version, 4, 'پس از برداشتن، همان محتوا هم نسخهٔ تازه است');
  assert.equal(v4.body.replayed, false);
});

test('دسترسی: شاگرد فقط می‌بیند، دکانِ دیگر نمی‌خواند، بی‌توکن ۴۰۱', async () => {
  const a = await liveStore('الف');
  const b = await liveStore('ب');
  assert.equal((await up(a, 'logo', F.jpeg(256, 256))).status, 200);

  const staff = await h.newUser('شاگرد', 'shop');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
     VALUES ($1,$2,$3,'staff','active',$4,$4)`,
    [newId('mem'), a.shopId, staff.user.id, now()]
  );
  const st = { token: staff.accessToken };
  assert.equal((await h.get(BASE, st)).status, 200);
  assert.equal((await h.download(`${BASE}/logo/1`, st)).status, 200);
  const sp = await up({ token: staff.accessToken }, 'logo', F.jpeg(300, 300));
  assert.equal(sp.status, 403);
  assert.equal((await h.del(`${BASE}/logo`, st)).status, 403);

  //  مدیرِ دکان می‌نویسد
  const mgr = await h.newUser('مدیر', 'shop');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
     VALUES ($1,$2,$3,'manager','active',$4,$4)`,
    [newId('mem'), a.shopId, mgr.user.id, now()]
  );
  assert.equal((await up({ token: mgr.accessToken }, 'logo', F.jpeg(300, 300))).status, 200);

  //  دکانِ ب همان نشانی را می‌زند ⇒ فقط دکانِ خودش (هیچ) ⇒ ۴۰۴
  assert.equal((await h.download(`${BASE}/logo/1`, { token: b.token })).status, 404);
  assert.equal((await h.get(BASE, { token: b.token })).body.logo, null);
  //  shop_id از بدنه/سرآیند خوانده نمی‌شود
  const r = await h.raw('PUT', `${BASE}/logo?shopId=${a.shopId}`, F.jpeg(256, 256, { seed: 9 }), { token: b.token, headers: { 'X-Shop': a.shopId } });
  assert.ok(r.status === 200 || r.status === 403, JSON.stringify(r.body));
  assert.equal((await rows(a.shopId)).filter((x) => x.current).length, 1);
  assert.equal((await many('SELECT 1 FROM storefront_media WHERE shop_id=$1', [a.shopId])).length, 2);

  assert.equal((await h.raw('PUT', `${BASE}/logo`, F.jpeg(256, 256))).status, 401);
  assert.equal((await h.download(`${BASE}/logo/1`)).status, 401);
});

test('هم‌زمانی: پنج بارگذاریِ هم‌زمان ⇒ شماره‌های یکتا و یک جاری', async () => {
  const s = await liveStore('هم‌زمان');
  const out = await Promise.all([1, 2, 3, 4, 5].map((i) => up(s, 'header', F.jpeg(1200, 400, { seed: 100 + i }))));
  for (const r of out) assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(out.map((r) => r.body.version).sort(), [1, 2, 3, 4, 5]);
  const left = await rows(s.shopId);
  assert.equal(left.filter((r) => r.current).length, 1);
  assert.equal(left.length, 2);
  assert.equal(left.find((r) => r.current).version, 5);
});

test('سقفِ روزانهٔ بارگذاری به‌ازای دکان', async () => {
  const s = await liveStore('سقف');
  const config = require('../src/config');
  const max = config.rateLimit.storefrontMediaDailyMax;
  let last;
  for (let i = 0; i <= max; i++) last = await up(s, 'logo', F.jpeg(256, 256, { seed: 1000 + i }));
  assert.equal(last.status, 429);
});
