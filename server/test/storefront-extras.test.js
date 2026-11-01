'use strict';
/**
 * ویترین — علاقه‌مندی‌ها (WEB-11) و بنرهای زمان‌بندی‌شدهٔ هر ناحیه (WEB-24)، `lib/storefront-extras.js`.
 *
 *   ۱) ⛔ خاموش ⇒ درِ عمومی ۴۰۴ و درِ دکان ۴۰۳، بی هیچ نوشتن؛ بی نشستِ مشتری ⇒ ۴۰۱.
 *   ۲) علاقه‌مندی: فقط کالای فروختنی، تکرار بی‌اثر، تازه‌ترین اول، کالای پنهان‌شده دیده
 *      نمی‌شود ولی ردیفش می‌ماند، مشتریِ دیگر و ویترینِ دیگر جدا، پاک کردنِ حساب ⇒ پاک، سقف.
 *   ۳) بنر: فقط بازهٔ اکنون، ناحیه (خالی = همه‌جا)، اولویت، پیوندِ کالای نافروختنی پنهان،
 * نوشتنِ یک‌جا با اعتبارسنجی (بدی ⇒ هیچ تغییری)، دکانِ دیگر نمی‌بیند.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const X = require('../src/lib/storefront-extras');

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
});
test.after(async () => { await h.stop(); });
test.beforeEach(() => resetAll());

const flag = async (scope, key, value) => {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
};
const push = (token, ops) => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop', 'X-Device': 'dev-x' }, body: { device_id: 'dev-x', schema_version: 2, ops },
});
const op = (id, fields) => ({ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: id, type: 'insert', fields });

async function liveStore(label) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  const o = { token: u.accessToken, shopId: made.body.shop.id };
  await flag(o.shopId, 'shop.orders', true);
  await flag(o.shopId, 'shop.storefront', true);
  const slug = `ex-${uniq()}`;
  assert.equal((await h.put('/api/shop/storefront', { slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001' }, { token: o.token })).status, 200);
  assert.equal((await push(o.token, [
    op('rice', { name: 'برنج', unit: 'کیلو', salePrice: 120, category: 'خوراکی' }),
    op('oil', { name: 'روغن', unit: 'بوتل', salePrice: 300, category: 'خوراکی' }),
    op('soap', { name: 'صابون', unit: 'دانه', salePrice: 40, category: 'بهداشتی' }),
  ])).status, 200);
  for (const id of ['rice', 'oil']) {
    assert.equal((await h.put(`/api/shop/storefront/products/${id}`, { published: true }, { token: o.token })).status, 200);
  }
  return { ...o, slug };
}

async function customer(slug, phone) {
  const s = await h.post(`/api/store/${slug}/verify/start`, { phone });
  const c = await h.post(`/api/store/${slug}/verify/check`, { phone, code: s.body.devCode });
  const a = await h.post(`/api/store/${slug}/account`, {}, { headers: { 'X-Store-Ticket': c.body.ticket } });
  assert.ok(a.status < 300, JSON.stringify(a.body));
  return { headers: { 'X-Store-Session': a.body.session } };
}
const favCount = async (shopId) => (await one('SELECT COUNT(*)::int AS n FROM store_customer_favorites WHERE shop_id = $1', [shopId])).n;

test('WEB-11 علاقه‌مندی: خاموش ۴۰۴، روشن فقط فروختنی، جدا برای هر مشتری و ویترین', async () => {
  const s = await liveStore('دلخواه');
  const k = await customer(s.slug, '0791240001');
  const base = `/api/store/${s.slug}/account/favorites`;
  assert.equal((await h.get(base)).status, 401, 'بی نشست');
  const off = await h.put(`${base}/rice`, {}, k);
  assert.equal(off.status, 404);
  assert.equal(off.body.error.code, 'favorites_disabled');
  assert.equal((await h.get(base, k)).status, 404);
  assert.equal(await favCount(s.shopId), 0, 'خاموش ⇒ هیچ نوشتنی');

  await flag(s.shopId, 'shop.storefront.favorites', true);
  assert.deepEqual((await h.put(`${base}/rice`, {}, k)).body, { productId: 'rice', added: true });
  assert.deepEqual((await h.put(`${base}/rice`, {}, k)).body, { productId: 'rice', added: false }, 'تکرار بی‌اثر');
  await query("UPDATE store_customer_favorites SET created_at = created_at - 1000 WHERE product_id = 'rice'");
  assert.equal((await h.put(`${base}/oil`, {}, k)).status, 200);
  for (const bad of ['soap', 'nothing', 'bad%20id']) {
    const r = await h.put(`${base}/${bad}`, {}, k);
    assert.equal(r.status, 404, bad);
    assert.equal(r.body.error.code, 'product_not_found', bad);
  }
  let list = (await h.get(base, k)).body;
  assert.deepEqual(list.items.map((p) => p.id), ['oil', 'rice'], 'تازه‌ترین اول');
  assert.equal(list.items[0].priceMinor, 30000, 'نمای زندهٔ کاتالوگ');
  assert.equal(list.max, X.MAX_FAVORITES);

  //  کالای پنهان‌شده دیده نمی‌شود ولی ردیفش می‌ماند
  assert.equal((await h.put('/api/shop/storefront/products/oil', { published: false }, { token: s.token })).status, 200);
  assert.deepEqual((await h.get(base, k)).body.items.map((p) => p.id), ['rice']);
  assert.equal(await favCount(s.shopId), 2);
  await h.put('/api/shop/storefront/products/oil', { published: true }, { token: s.token });
  assert.deepEqual((await h.get(base, k)).body.items.map((p) => p.id), ['oil', 'rice'], 'برگشتِ کالا ⇒ دوباره دیده می‌شود');

  //  مشتریِ دیگر و ویترینِ دیگر جدا
  const k2 = await customer(s.slug, '0791240002');
  assert.deepEqual((await h.get(base, k2)).body.items, []);
  assert.deepEqual((await h.del(`${base}/rice`, k2)).body, { deleted: false }, 'مشتریِ دیگر چیزی پاک نمی‌کند');
  const t = await liveStore('دلخواهِ دیگر');
  await flag(t.shopId, 'shop.storefront.favorites', true);
  const kt = await customer(t.slug, '0791240001');
  assert.deepEqual((await h.get(`/api/store/${t.slug}/account/favorites`, kt)).body.items, [], 'همان شماره، ویترینِ دیگر');

  assert.deepEqual((await h.del(`${base}/rice`, k)).body, { deleted: true });
  assert.deepEqual((await h.get(base, k)).body.items.map((p) => p.id), ['oil']);

  //  سقف
  const cust = await one("SELECT id FROM store_customers WHERE phone = '+93791240001'");
  for (let i = 0; i < X.MAX_FAVORITES - 1; i++) {
    await query('INSERT INTO store_customer_favorites (customer_id, shop_id, product_id, created_at) VALUES ($1,$2,$3,$4)', [cust.id, s.shopId, `fill-${i}`, 1]);
  }
  const full = await h.put(`${base}/rice`, {}, k);
  assert.equal(full.status, 409);
  assert.equal(full.body.error.code, 'too_many_favorites');

  //  پاک کردنِ حساب ⇒ علاقه‌مندی‌ها هم
  assert.equal((await h.del(`/api/store/${s.slug}/account`, k)).status, 200);
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM store_customer_favorites WHERE customer_id = $1', [cust.id])).n, 0);
});

const Z1 = '01HZZZZZZZZZZZZZZZZZZZZZZ1';
const Z2 = '01HZZZZZZZZZZZZZZZZZZZZZZ2';

test('WEB-24 بنر: خاموش ۴۰۳/۴۰۴، بازهٔ اکنون، ناحیه، اولویت، پیوندِ نافروختنی پنهان', async () => {
  const s = await liveStore('بنر');
  const adminUrl = '/api/shop/storefront/banners';
  const pub = (q = '') => h.get(`/api/store/${s.slug}/banners${q}`);
  const off = await h.get(adminUrl, { token: s.token });
  assert.equal(off.status, 403);
  assert.equal(off.body.error.code, 'banners_disabled');
  assert.equal((await h.put(adminUrl, { banners: [] }, { token: s.token })).status, 403);
  assert.equal((await pub()).status, 404);

  await flag(s.shopId, 'shop.storefront.banners', true);
  const t = now();
  const H = 3600 * 1000;
  const put = await h.put(adminUrl, {
    banners: [
      { title: 'همه‌جا', body: 'تخفیفِ هفته', startAt: t - H, endAt: t + H },
      { title: 'فقط ناحیهٔ ۱', startAt: t - H, endAt: t + H, zoneIds: [Z1, Z1], sort: 50, link: { type: 'category', value: 'خوراکی' } },
      { title: 'آینده', startAt: t + H, endAt: t + 2 * H },
      { title: 'گذشته', startAt: t - 2 * H, endAt: t - H },
      { title: 'روغن', startAt: t - H, endAt: t + H, link: { type: 'product', value: 'oil' }, sort: 10 },
      { title: 'صابونِ پنهان', startAt: t - H, endAt: t + H, link: { type: 'product', value: 'soap' }, sort: 99 },
    ],
  }, { token: s.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  assert.equal(put.body.banners.length, 6);
  assert.deepEqual(put.body.banners.find((b) => b.title === 'فقط ناحیهٔ ۱').zoneIds, [Z1], 'ناحیهٔ تکراری یکی');

  const titles = async (q) => (await pub(q)).body.banners.map((b) => b.title);
  assert.deepEqual(await titles(''), ['روغن', 'همه‌جا'], 'بی ناحیه ⇒ فقط همه‌جا؛ آینده/گذشته/صابون نه');
  assert.deepEqual(await titles(`?zone=${Z1}`), ['فقط ناحیهٔ ۱', 'روغن', 'همه‌جا'], 'اولویت');
  assert.deepEqual(await titles(`?zone=${Z2}`), ['روغن', 'همه‌جا']);
  assert.deepEqual(await titles('?zone=bad'), ['روغن', 'همه‌جا']);
  const one1 = (await pub(`?zone=${Z1}`)).body.banners[0];
  assert.deepEqual(Object.keys(one1).sort(), ['body', 'endAt', 'id', 'link', 'startAt', 'title'], 'ناحیه‌ها و اولویت بیرون نمی‌روند');
  assert.deepEqual(one1.link, { type: 'category', value: 'خوراکی' });
  //  کالای پیوند پنهان شد ⇒ بنرش هم
  await h.put('/api/shop/storefront/products/oil', { published: false }, { token: s.token });
  assert.deepEqual(await titles(''), ['همه‌جا']);

  //  اعتبارسنجی: هیچ‌چیز نیمه‌کاره نوشته نمی‌شود
  for (const bad of [
    { banners: 'x' },
    { banners: Array.from({ length: X.MAX_BANNERS + 1 }, () => ({ title: 'a', startAt: t, endAt: t + 1 })) },
    { banners: [{ title: '', startAt: t, endAt: t + 1 }] },
    { banners: [{ title: 'a', startAt: t, endAt: t }] },
    { banners: [{ title: 'a', startAt: t, endAt: t + 93 * 24 * H }] },
    { banners: [{ title: 'a', startAt: -1, endAt: t }] },
    { banners: [{ title: 'a', startAt: t, endAt: t + 1, zoneIds: ['nope'] }] },
    { banners: [{ title: 'a', startAt: t, endAt: t + 1, link: { type: 'product', value: 'bad id' } }] },
    { banners: [{ title: 'a', startAt: t, endAt: t + 1, sort: 101 }] },
  ]) {
    const r = await h.put(adminUrl, bad, { token: s.token });
    assert.equal(r.status, 400, JSON.stringify(bad).slice(0, 80));
    assert.equal(r.body.error.code, 'bad_banner');
  }
  assert.equal((await h.get(adminUrl, { token: s.token })).body.banners.length, 6, 'نوشتنِ بد هیچ چیز را عوض نکرد');

  //  دکانِ دیگر
  const o = await liveStore('بنرِ دیگر');
  await flag(o.shopId, 'shop.storefront.banners', true);
  assert.deepEqual((await h.get(adminUrl, { token: o.token })).body.banners, []);
  assert.deepEqual((await h.get(`/api/store/${o.slug}/banners`)).body.banners, []);

  //  خالی کردن
  assert.deepEqual((await h.put(adminUrl, { banners: [] }, { token: s.token })).body.banners, []);
  assert.deepEqual(await titles(''), []);
});
