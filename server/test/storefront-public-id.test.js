'use strict';
/**
 * S3 ِ قراردادِ بازار — شناسهٔ عمومیِ پایدارِ دکان و نشانیِ پیشین (مهاجرتِ ۱۲۲).
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) مهاجرت روی دادهٔ نمونه: ردیف‌های موجود ULID ِ یکتا با زمانِ `created_at`
 *      می‌گیرند؛ rollback تمیز برمی‌گردد و اجرای دوباره همان شکل را می‌سازد.
 *   ۲) ⛔ `public_id` تغییرناپذیر است (تریگر) و با عوض شدنِ نشانی نمی‌ماند.
 *   ۳) ⛔ پرچمِ خاموش ⇒ پاسخ‌ها همان امروز: نشانیِ کهنه ۴۰۴، بی `publicId`.
 *   ۴) پرچمِ روشن ⇒ نشانیِ کهنه (حتی دو بار عوض‌شده) به نشانیِ تازه می‌رسد؛
 *      `?id=` نشانیِ امروز را می‌دهد؛ نشانیِ زنده بر پیشین مقدم است؛ ویترینِ
 *      بسته از هیچ راهی پیدا نمی‌شود.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const migrate = require('../src/migrate');
const { resetAll } = require('../src/middleware/ratelimit');
const { ALPHABET } = require('../src/lib/ulid');

const DOWN = path.join(__dirname, '..', 'migrations', 'rollback', '122_storefront_public_id.down.sql');
const ULID_RE = /^[0-9A-HJKMNP-TV-Z]{26}$/;
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

/** دکانِ روشن با ویترین؛ پرچمِ S3 خاموش. */
async function liveStore(label) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { token: u.accessToken, shopId: made.body.shop.id };
  await setFlag(o.shopId, 'shop.orders', true);
  await setFlag(o.shopId, 'shop.storefront', true);
  const slug = `p-${uniq()}`;
  await setSlug(o, slug, { enabled: true, title: `ویترینِ ${label}` });
  return { ...o, slug };
}
async function setSlug(o, slug, extra = {}) {
  const r = await h.put('/api/shop/storefront', { slug, ...extra }, { token: o.token });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  o.slug = slug;
}
const pidOf = async (shopId) => (await one('SELECT public_id FROM storefronts WHERE shop_id=$1', [shopId])).public_id;
/** بی دنبال کردنِ تغییرِ مسیر. */
const manual = (p) => fetch(`${h.base()}${p}`, { redirect: 'manual' });

/** ۱۰ نویسهٔ اولِ ULID ⇒ میلی‌ثانیه. */
const timeOf = (id) => [...id.slice(0, 10)].reduce((t, ch) => t * 32 + ALPHABET.indexOf(ch), 0);

/* ══════════════════════════════════════════════════════════════════
   ۱) مهاجرت و rollback روی دادهٔ نمونه
   ══════════════════════════════════════════════════════════════════ */

test('مهاجرت: ردیف‌های موجود پر می‌شوند؛ rollback و اجرای دوباره تمیزند', async () => {
  const a = await liveStore('نمونه-۱');
  const b = await liveStore('نمونه-۲');
  const old = a.slug;
  await setSlug(a, `p-${uniq()}`);

  //  پیش از ۱۲۲: rollback و دادهٔ نمونهٔ بی ستون، با created_at ِ کهنه
  await query(fs.readFileSync(DOWN, 'utf8'));
  const cols = await one(`SELECT COUNT(*)::int AS n FROM information_schema.columns
                           WHERE table_name='storefronts' AND column_name='public_id'`);
  assert.equal(cols.n, 0, 'rollback ستون را برداشت');
  assert.equal((await one("SELECT to_regclass('storefront_slug_redirects') AS t")).t, null);
  assert.equal((await one("SELECT COUNT(*)::int AS n FROM schema_migrations WHERE version='122_storefront_public_id.sql'")).n, 0);
  //  هنوز درست کار می‌کند (کدِ خواننده فقط پشتِ پرچم است) — جز نوشتنِ تاریخچه
  assert.equal((await h.get(`/api/store/${a.slug}`)).status, 200);
  const past = Date.UTC(2025, 0, 2, 3, 4, 5, 678);
  await query('UPDATE storefronts SET created_at=$2 WHERE shop_id=$1', [b.shopId, past]);

  await migrate.run();
  const rows = (await query('SELECT shop_id, public_id, created_at FROM storefronts')).rows;
  assert.ok(rows.length >= 2);
  for (const r of rows) {
    assert.match(r.public_id, ULID_RE);
    assert.equal(timeOf(r.public_id), Number(r.created_at), 'زمانِ ULID همان created_at');
  }
  assert.equal(new Set(rows.map((r) => r.public_id)).size, rows.length, 'یکتا');
  assert.equal(timeOf(await pidOf(b.shopId)), past);
  //  نشانیِ پیشینِ پیش از ۱۲۲ ثبت نشده (جدول نبود) — فقط از امروز به بعد
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM storefront_slug_redirects WHERE old_slug=$1', [old])).n, 0);

  //  اجرای دوباره بی اثر
  const again = await migrate.run();
  assert.ok(!JSON.stringify(again || '').includes('122_'), 'دوباره اجرا نمی‌شود');
});

test('⛔ public_id تغییرناپذیر است و ردیفِ تازه خودکار پر می‌شود', async () => {
  const a = await liveStore('تغییرناپذیر');
  const pid = await pidOf(a.shopId);
  assert.match(pid, ULID_RE);
  await assert.rejects(
    query('UPDATE storefronts SET public_id=$2 WHERE shop_id=$1', [a.shopId, '01ARZ3NDEKTSV4RRFFQ69G5FAV']),
    /immutable/
  );
  await assert.rejects(query("UPDATE storefronts SET public_id='bad' WHERE shop_id=$1", [a.shopId]));
  //  عوض شدنِ نشانی و تنظیم‌ها شناسه را عوض نمی‌کند
  await setSlug(a, `p-${uniq()}`, { title: 'نامِ تازه' });
  assert.equal(await pidOf(a.shopId), pid);
});

/* ══════════════════════════════════════════════════════════════════
   ۲) پرچمِ خاموش ⇒ همان پاسخِ امروز
   ══════════════════════════════════════════════════════════════════ */

test('⛔ پرچمِ خاموش: نشانیِ کهنه ۴۰۴، بی publicId، ?id= همان پاسخِ امروزِ /api/store', async () => {
  const a = await liveStore('خاموش');
  const old = a.slug;
  const pid = await pidOf(a.shopId);
  await setSlug(a, `p-${uniq()}`);
  assert.equal((await one('SELECT shop_id FROM storefront_slug_redirects WHERE old_slug=$1', [old])).shop_id, a.shopId,
    'تاریخچه از همین حالا نوشته می‌شود');

  const none = await h.get(`/api/store/p-nothing-${uniq()}`);
  const gone = await h.get(`/api/store/${old}`);
  assert.equal(gone.status, 404);
  assert.deepEqual(gone.body.error.code, none.body.error.code);
  assert.equal((await manual(`/api/store/${old}/highlights`)).status, 404);
  assert.equal((await manual(`/store/${old}`)).status, 404);

  const live = await h.get(`/api/store/${a.slug}`);
  assert.equal(live.status, 200);
  assert.equal('publicId' in live.body.store, false);

  const plain = await h.get('/api/store');
  const byId = await h.get(`/api/store?id=${pid}`);
  assert.equal(byId.status, plain.status);
  assert.deepEqual(byId.body, plain.body);
  const page = await manual(`/store?id=${pid}`);
  assert.equal(page.status, (await manual('/store')).status);
});

/* ══════════════════════════════════════════════════════════════════
   ۳) پرچمِ روشن
   ══════════════════════════════════════════════════════════════════ */

test('پرچمِ روشن: نشانیِ کهنه (دو بار عوض‌شده) ⇒ نشانیِ تازه؛ ?id= ⇒ نشانیِ امروز', async () => {
  const a = await liveStore('روشن');
  await setFlag(a.shopId, 'market.public_id', true);
  const pid = await pidOf(a.shopId);
  const first = a.slug;
  await setSlug(a, `p-${uniq()}`);
  const second = a.slug;
  await setSlug(a, `p-${uniq()}`);

  for (const old of [first, second]) {
    const r = await manual(`/api/store/${old}`);
    assert.equal(r.status, 308);
    assert.equal(r.headers.get('location'), `/api/store/${a.slug}`);
    assert.equal((await r.json()).error.slug, a.slug);
    const sub = await manual(`/api/store/${old.toUpperCase()}/highlights?x=1`);
    assert.equal(sub.status, 308);
    assert.equal(sub.headers.get('location'), `/api/store/${a.slug}/highlights?x=1`);
    const page = await manual(`/store/${old}`);
    assert.equal(page.status, 301);
    assert.equal(page.headers.get('location'), `/store/${a.slug}`);
  }
  //  دنبال کردن ⇒ همان ویترین
  const followed = await h.get(`/api/store/${first}`);
  assert.equal(followed.status, 200);
  assert.equal(followed.body.store.slug, a.slug);
  assert.equal(followed.body.store.publicId, pid);

  const byId = await h.get(`/api/store?id=${pid.toLowerCase()}`);
  assert.equal(byId.status, 200);
  assert.deepEqual(byId.body, { publicId: pid, slug: a.slug });
  const page = await manual(`/store?id=${pid}`);
  assert.equal(page.status, 302);
  assert.equal(page.headers.get('location'), `/store/${a.slug}`);
  //  نبود ⇒ همان پاسخِ امروزِ `/api/store`
  const today = (await h.get('/api/store')).status;
  assert.equal((await h.get('/api/store?id=01ARZ3NDEKTSV4RRFFQ69G5FAV')).status, today);
  assert.equal((await h.get('/api/store?id=../etc')).status, today);

  //  برگشت به نشانیِ پیشین ⇒ ردیفِ پیشین پاک، نشانی زنده
  await setSlug(a, first);
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM storefront_slug_redirects WHERE old_slug=$1', [first])).n, 0);
  assert.equal((await manual(`/api/store/${first}`)).status, 200);
  assert.equal(await pidOf(a.shopId), pid);
});

test('⛔ نشانیِ زنده مقدم است: دکانِ دیگر نشانیِ رهاشده را می‌گیرد', async () => {
  const a = await liveStore('رها-۱');
  const b = await liveStore('رها-۲');
  await setFlag(a.shopId, 'market.public_id', true);
  const freed = a.slug;
  await setSlug(a, `p-${uniq()}`);
  assert.equal((await manual(`/api/store/${freed}`)).status, 308);
  await setSlug(b, freed);
  const r = await h.get(`/api/store/${freed}`);
  assert.equal(r.status, 200);
  assert.equal(r.body.store.title, 'ویترینِ رها-۲');
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM storefront_slug_redirects WHERE old_slug=$1', [freed])).n, 0);
});

test('⛔ ویترینِ بسته از راهِ نشانیِ کهنه یا شناسه پیدا نمی‌شود', async () => {
  const a = await liveStore('بسته');
  await setFlag(a.shopId, 'market.public_id', true);
  const pid = await pidOf(a.shopId);
  const old = a.slug;
  await setSlug(a, `p-${uniq()}`, { enabled: false });
  assert.equal((await manual(`/api/store/${old}`)).status, 404);
  assert.equal((await manual(`/store/${old}`)).status, 404);
  const today = (await h.get('/api/store')).status;
  assert.equal((await h.get(`/api/store?id=${pid}`)).status, today);
  //  پرچمِ ویترین خاموش ⇒ همان
  await setSlug(a, a.slug, { enabled: true });
  assert.equal((await manual(`/api/store/${old}`)).status, 308);
  await setFlag(a.shopId, 'shop.storefront', false);
  assert.equal((await manual(`/api/store/${old}`)).status, 404);
  assert.equal((await h.get(`/api/store?id=${pid}`)).status, today);
});
