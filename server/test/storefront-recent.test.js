'use strict';
/**
 * ویترین — «دیده‌شده‌های اخیر» (WEB-13) پشتِ پرچمِ خاموشِ `shop.storefront.recent`.
 *
 *   ۱) ⛔ خاموش ⇒ `GET /api/store/:slug` بی کلیدِ `recent` (صفحه recent.js را بار نمی‌کند)؛ روشن ⇒ `recent: true`.
 *   ۲) `recent.js` از همان مبدأ و جدا از بارِ نخست سرو می‌شود؛ بارِ نخست زیرِ ۷۵KB می‌ماند.
 *   ۳) قاعده‌ها همان `core/product/RecentlyViewedTest` (Kotlin): تازه‌ترین اول، بی تکرار، حداکثر ۱۲،
 *      کهنه‌تر از ۳۰ روز بیرون، ردیفِ بد کنار.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');
const R = require('../public/store/recent.js');

const PUB = path.join(__dirname, '..', 'public');
let admin = '';
test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  admin = (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
});
test.after(async () => { await h.stop(); });

const flag = async (scope, key, value) => {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
};

test('پرچم: خاموش بی کلید، روشن `recent: true`؛ recent.js جدا سرو می‌شود', async () => {
  const u = await h.newUser('اخیر', 'shop');
  const shop = (await h.post('/api/shop', { name: 'دکانِ اخیر' }, { token: u.accessToken })).body.shop;
  await flag(shop.id, 'shop.orders', true);
  await flag(shop.id, 'shop.storefront', true);
  const slug = `rv-${Date.now().toString(36)}`;
  assert.equal((await h.put('/api/shop/storefront', { slug, enabled: true, title: 'ویترینِ اخیر', contactPhone: '0700000001' },
    { token: u.accessToken })).status, 200);
  const off = await h.get(`/api/store/${slug}`);
  assert.equal(off.status, 200);
  assert.equal('recent' in off.body, false);
  await flag(shop.id, 'shop.storefront.recent', true);
  assert.equal((await h.get(`/api/store/${slug}`)).body.recent, true);
  await flag('all', 'shop.storefront.recent', true);
  await flag(shop.id, 'shop.storefront.recent', false);
  assert.equal('recent' in (await h.get(`/api/store/${slug}`)).body, false, 'مقدارِ خودِ دکان بر all جلو می‌افتد');
  await flag('all', 'shop.storefront.recent', false);

  const js = await fetch(`${h.base()}/store/recent.js`);
  assert.equal(js.status, 200);
  assert.match(js.headers.get('content-type') || '', /javascript/);
  const first = ['store/store.js', 'kit/kit.js', 'kit/boot.js'].reduce((s, f) => s + fs.statSync(path.join(PUB, f)).size, 0);
  assert.ok(first < 75 * 1024, `بارِ نخست ${first} بایت`);
  const html = fs.readFileSync(path.join(PUB, 'store', 'index.html'), 'utf8');
  assert.doesNotMatch(html, /recent\.js/, 'recent.js در بارِ نخست نیست');
});

test('قاعده‌ها: همان پرونده‌های RecentlyViewedTest ِ هسته', () => {
  const T = 1_800_000_000_000;
  const day = 24 * 60 * 60 * 1000;
  let l = [];
  l = R.add(l, 'rice', T - 3000);
  l = R.add(l, 'oil', T - 2000);
  l = R.add(l, 'soap', T - 1000);
  l = R.add(l, 'rice', T);
  assert.deepEqual(l.map((x) => x.id), ['rice', 'soap', 'oil']);
  assert.equal(l[0].at, T);

  l = [];
  for (let i = 1; i <= 20; i++) l = R.add(l, `p${i}`, T - (20 - i) * 1000);
  assert.equal(l.length, R.MAX);
  assert.equal(l[0].id, 'p20');
  assert.equal(l[l.length - 1].id, 'p9');
  assert.deepEqual(R.fresh([{ id: 'a', at: T - 31 * day }, { id: 'b', at: T - 29 * day }], T).map((x) => x.id), ['b']);

  const raw = [{ id: 'ok', at: T - 10 }, { id: '', at: T - 5 }, { id: 'bad id', at: T - 4 }, { id: 'x'.repeat(81), at: T - 3 },
    { id: 'far', at: T + 31 * day }, { id: 'ok', at: T - 20 }, null, 'junk', { id: 'n', at: 'NaN' }];
  assert.deepEqual(R.fresh(raw, T), [{ id: 'ok', at: T - 10 }]);
  assert.deepEqual(R.add(raw, 'no/slash', T).map((x) => x.id), ['ok']);
  assert.deepEqual(R.fresh([{ id: 'b', at: T }, { id: 'a', at: T }], T).map((x) => x.id), ['a', 'b']);
  assert.deepEqual(R.fresh('not a list', T), [], 'حافظهٔ خراب ⇒ فهرستِ خالی');
  assert.deepEqual(R.fresh([{ id: 'k', at: T, extra: 'x' }], T), [{ id: 'k', at: T }], 'فقط id و at می‌مانند');
});
