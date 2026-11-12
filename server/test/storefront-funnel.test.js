'use strict';
/**
 * RPT-14 — بالای قیفِ ویترینِ وب (بازدید ⇐ دیدنِ کالا ⇐ سبد) در «گزارشِ فروشِ اینترنتی» (`lib/storefront-funnel.js`،
 * مهاجرتِ ۴۹۱، `POST /api/store/:slug/funnel/:step`).
 *
 *   ۱) ⛔ پرچمِ `shop.orders.online_report` خاموش ⇒ `features.funnel` نیست، در همان پاسخِ مسیرِ ناشناخته و هیچ ردیف؛
 *      گزارش همان `visits: null`.
 *   ۲) یکتا در روز: همان بازدیدکننده (HMAC ِ دکان + روز + IP + مرورگر) هر گام را یک بار؛ گام‌ها پیوسته‌اند (سبد ⇒
 *      دیدن و بازدید هم)، پس بازدید ≥ دیدن ≥ سبد. گامِ ناشناخته ⇒ ۴۰۰ `bad_step` و هیچ ردیف.
 *   ۳) حریم: نه IP، نه مرورگر، نه شناسه‌ای از بیرون در پایگاه؛ فقط چکیدهٔ روزانه.
 *   ۴) سقفِ روزانهٔ بازدیدکنندهٔ تازه برای هر دکان (سیلِ ساختگی جدول را پر نمی‌کند)؛ بازدیدکنندهٔ شمرده‌شده هنوز گام می‌گیرد.
 *   ۵) جداییِ دکان‌ها (shop_id فقط از slug)؛ بازهٔ گزارش به روزِ UTC؛ نگهداری: ردِ بازدیدکننده ۲ روز، شمارِ روز ۹۲ روز.
 *   ۶) هم‌زمان (PostgreSQL): بیست تپشِ هم‌زمانِ همان بازدیدکننده ⇒ دقیقاً یک بازدید/دیدن/سبد.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./helpers');
const { query, many, one, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const F = require('../src/lib/storefront-funnel');

let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;
const DAY = 86_400_000;

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

async function liveStore(label, { report = true } = {}) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { token: u.accessToken, shopId: made.body.shop.id };
  await setFlag(o.shopId, 'shop.orders', true);
  await setFlag(o.shopId, 'shop.storefront', true);
  //  پیش‌فرض‌روشن است (`lib/flag-defaults.js`، ۱۴۰۵/۰۸/۲۲) ⇒ «خاموش» `false` ِ صریح است
  await setFlag(o.shopId, 'shop.orders.online_report', report);
  const slug = `fn-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug, enabled: true, title: `ویترینِ ${label}` }, { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  return { ...o, slug };
}

const beat = (s, step, ua = 'Mozilla/5.0 (Linux; Android 13) test') =>
  h.post(`/api/store/${s.slug}/funnel/${step}`, undefined, { headers: { 'User-Agent': ua } });
const report = (s, q = '') => h.get(`/api/shop/orders/online-report${q}`, { token: s.token });
const top = async (s, q) => { const r = await report(s, q); assert.equal(r.status, 200, JSON.stringify(r.body)); return r.body; };

test('RPT-14 بالای قیف: پرچمِ خاموش ⇒ نه `features.funnel`، درِ تپش همان مسیرِ ناشناخته، نه ردیف؛ گزارش همان `visits: null`', async () => {
  const s = await liveStore('قیفِ خاموش', { report: false });
  const pub = await h.get(`/api/store/${s.slug}`);
  assert.equal(pub.status, 200);
  assert.ok(!(pub.body.features && 'funnel' in pub.body.features), 'کلید نیست');
  const [r, unknown] = [await beat(s, 'visit'), await h.post(`/api/store/${s.slug}/no-such-door/visit`, undefined)];
  assert.equal(r.status, unknown.status, 'همان پاسخِ مسیرِ ناشناخته');
  assert.deepEqual(r.body, unknown.body);
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM storefront_funnel_days WHERE shop_id=$1', [s.shopId])).n, 0);
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM storefront_funnel_seen WHERE shop_id=$1', [s.shopId])).n, 0);
  //  گزارش روشن، بی هیچ شمارشی ⇒ همان «عددِ ساختگی نه»
  await setFlag(s.shopId, 'shop.orders.online_report', true);
  const b = await top(s);
  assert.equal(b.visits, null);
  assert.equal(b.top, null);
  assert.equal((await h.get(`/api/store/${s.slug}`)).body.features.funnel, true, 'روشن ⇒ ویترین تپش می‌فرستد');
});

test('RPT-14 بالای قیف: یکتا در روز، گام‌های پیوسته، گامِ ناشناخته ۴۰۰، بی IP و مرورگر در پایگاه', async () => {
  const s = await liveStore('قیفِ یکتا');
  const other = await liveStore('قیفِ دکانِ دیگر');
  for (let i = 0; i < 3; i++) assert.equal((await beat(s, 'visit')).status, 204);
  assert.equal((await beat(s, 'view')).status, 204);
  assert.equal((await beat(s, 'view')).status, 204);
  //  بازدیدکنندهٔ دوم مستقیم به سبد (پیوندِ کالا) ⇒ بازدید و دیدن هم
  assert.equal((await beat(s, 'cart', 'Mozilla/5.0 (iPhone) other')).status, 204);
  //  بازدیدکنندهٔ سوم فقط بازدید
  assert.equal((await beat(s, 'visit', 'Mozilla/5.0 (Windows) third')).status, 204);
  const bad = await beat(s, 'checkout');
  assert.equal(bad.status, 400);
  assert.equal(bad.body.error.code, 'bad_step');
  //  دکانِ دیگر جدا
  assert.equal((await beat(other, 'visit')).status, 204);

  const b = await top(s);
  assert.equal(b.visits, 3);
  assert.equal(b.top.visits, 3);
  assert.equal(b.top.productViews, 2);
  assert.equal(b.top.cartAdds, 1);
  assert.equal(b.top.viewBp, 6667, '۲ از ۳ به ده‌هزارم، گردِ نیمه به بالا');
  assert.equal(b.top.cartBp, 3333);
  assert.equal(b.top.web, true, 'فقط ویترینِ وب شمرده می‌شود');
  assert.equal(b.top.since, Math.floor(now() / DAY) * DAY);
  assert.equal((await top(other)).top.visits, 1);
  //  بازه‌ای بی روزِ امروز ⇒ صفر، و همان «از کی شمرده می‌شود»
  const old = await top(s, `?from=${now() - 5 * DAY}&to=${now() - 3 * DAY}`);
  assert.equal(old.top.visits, 0);
  assert.equal(old.visits, 0);

  //  حریم: هیچ IP، مرورگر یا شناسهٔ بیرونی؛ ردِ بازدیدکننده فقط چکیده
  const rows = await many('SELECT * FROM storefront_funnel_seen WHERE shop_id=$1', [s.shopId]);
  assert.equal(rows.length, 3 + 2 + 1, 'سه بازدیدکننده × گام‌هایشان');
  const dump = JSON.stringify(rows);
  assert.ok(!dump.includes('127.0.0.1') && !dump.includes('Mozilla') && !dump.includes('::1'), dump.slice(0, 200));
  for (const r of rows) assert.match(r.visitor, /^[A-Za-z0-9_-]{22}$/);
});

test('RPT-14 بالای قیف: سقفِ روزانهٔ بازدیدکنندهٔ تازه؛ شمرده‌شده هنوز پیش می‌رود؛ ویترینِ بسته ۴۰۴', async () => {
  const s = await liveStore('قیفِ سقف');
  const keep = process.env.STORE_FUNNEL_DAILY_CAP;
  process.env.STORE_FUNNEL_DAILY_CAP = '2';
  try {
    for (const ua of ['ua-1', 'ua-2', 'ua-3', 'ua-4']) assert.equal((await beat(s, 'visit', ua)).status, 204);
    assert.equal((await beat(s, 'cart', 'ua-1')).status, 204, 'بازدیدکنندهٔ شمرده‌شده سبد می‌گیرد');
    assert.equal((await beat(s, 'cart', 'ua-5')).status, 204, 'تازه پس از سقف: پاسخ همان، شمارش نه');
    const b = await top(s);
    assert.deepEqual([b.top.visits, b.top.productViews, b.top.cartAdds], [2, 1, 1]);
    assert.equal(b.top.capped, true, 'سقف خورده ⇒ گزارش می‌گوید');
  } finally {
    if (keep === undefined) delete process.env.STORE_FUNNEL_DAILY_CAP; else process.env.STORE_FUNNEL_DAILY_CAP = keep;
  }
  assert.equal((await top(s)).top.capped, false, 'زیرِ سقفِ پیش‌فرض');
  //  ویترینِ بسته ⇒ همان ۴۰۴ ِ ویترین
  assert.equal((await h.put('/api/shop/storefront', { enabled: false }, { token: s.token })).status, 200);
  const closed = await beat(s, 'visit', 'ua-6');
  assert.equal(closed.status, 404);
  assert.equal(closed.body.error.code, 'store_not_found');
});

test('RPT-14 بالای قیف: نگهداری — ردِ بازدیدکننده پس از ۲ روز، شمارِ روز پس از ۹۲ روز', async () => {
  const s = await liveStore('قیفِ نگهداری');
  const today = Math.floor(now() / DAY);
  await query(`INSERT INTO storefront_funnel_days (shop_id, day, visits, views, carts) VALUES ($1,$2,5,3,1),($1,$3,7,2,0)`,
    [s.shopId, today - 93, today - 10]);
  await query(`INSERT INTO storefront_funnel_seen (shop_id, day, visitor, step) VALUES ($1,$2,'aaaaaaaaaaaaaaaaaaaaaa',1),($1,$3,'bbbbbbbbbbbbbbbbbbbbbb',1)`,
    [s.shopId, today - 3, today - 1]);
  await F.sweep(now());
  assert.deepEqual((await many('SELECT day FROM storefront_funnel_days WHERE shop_id=$1 ORDER BY day', [s.shopId])).map((r) => Number(r.day)), [today - 10]);
  assert.deepEqual((await many('SELECT day FROM storefront_funnel_seen WHERE shop_id=$1 ORDER BY day', [s.shopId])).map((r) => Number(r.day)), [today - 1]);
  const b = await top(s, `?from=${(today - 30) * DAY}&to=${now()}`);
  assert.deepEqual([b.top.visits, b.top.productViews, b.top.cartAdds], [7, 2, 0]);
  assert.equal(b.top.since, (today - 10) * DAY);
});

test('RPT-14 بالای قیف: بیست تپشِ هم‌زمانِ همان بازدیدکننده ⇒ دقیقاً یک بازدید، یک دیدن، یک سبد', async () => {
  const s = await liveStore('قیفِ هم‌زمان');
  const out = await Promise.all(Array.from({ length: 20 }, (_, i) => beat(s, ['visit', 'view', 'cart'][i % 3], 'ua-same')));
  assert.ok(out.every((r) => r.status === 204), out.map((r) => r.status).join(','));
  const d = await one('SELECT visits, views, carts FROM storefront_funnel_days WHERE shop_id=$1', [s.shopId]);
  assert.deepEqual([d.visits, d.views, d.carts].map(Number), [1, 1, 1]);
});

test('مهاجرتِ ۴۹۱: rollback دو جدول و ردیفِ خودش را برمی‌دارد؛ بی جدول گزارش و تپش نمی‌افتند؛ دوباره نشستن همان', async () => {
  const s = await liveStore('قیفِ rollback');
  assert.equal((await beat(s, 'visit')).status, 204);
  const dir = path.join(__dirname, '..', 'migrations');
  const name = '491_storefront_funnel.sql';
  const down = fs.readFileSync(path.join(dir, 'rollback', name.replace('.sql', '.down.sql')), 'utf8');
  const up = fs.readFileSync(path.join(dir, name), 'utf8');
  const has = async () => (await query(
    "SELECT 1 FROM information_schema.tables WHERE table_name IN ('storefront_funnel_days','storefront_funnel_seen')")).rows.length;
  const mark = await one('SELECT * FROM schema_migrations WHERE version = $1', [name]);
  assert.ok(mark, 'مهاجرت نشسته است');
  await query(down);
  assert.equal(await has(), 0);
  assert.equal(await one('SELECT 1 AS x FROM schema_migrations WHERE version = $1', [name]), null);
  assert.equal((await beat(s, 'visit', 'ua-other')).status, 204, 'تپش بی جدول خطا نمی‌دهد');
  const b = await top(s);
  assert.equal(b.visits, null, 'بی جدول ⇒ همان «عدد نیست»');
  await query(up);
  await query('INSERT INTO schema_migrations (version, checksum, applied_at) VALUES ($1,$2,$3)', [mark.version, mark.checksum, mark.applied_at]);
  assert.equal(await has(), 2);
  assert.equal((await beat(s, 'visit', 'ua-third')).status, 204);
  assert.equal((await top(s)).visits, 1);
});
