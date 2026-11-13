'use strict';
/**
 * CRM-15 — دو فهرستِ پشتِ `settings.write` که شمارهٔ مشتری دارند: «خبرم کن» ِ بیرونِ محدوده
 * (`GET /api/shop/storefront/area-requests`) و سدِ تخفیفِ ویترین (`GET /api/shop/storefront/promo-guard`).
 *
 * تا امروز مدیری که صاحب «شمارهٔ مشتریان» (`customers.contact`) را برایش بسته بود (لایهٔ کاربر یا نقش)، در همین دو
 * فهرست شمارهٔ کامل می‌گرفت. سنجه‌ها: صاحب و مدیرِ بی لایه همان شمارهٔ کامل؛ مدیرِ بسته (کاربر یا نقش) فقط پوشیده
 * (`ContactMask`: جز دو رقمِ آخر) و هیچ جای پاسخ شمارهٔ کامل نیست؛ شمار، ناحیه، دستگاه و شناسه همان؛ «خبر دادم» با
 * همان شناسه کار می‌کند؛ صندوق‌دار همان ۴۰۳؛ دکانِ دیگر هیچ.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');

const POLY = [[34.50, 69.12], [34.50, 69.22], [34.56, 69.22], [34.56, 69.12]];
const ZONE = { name: 'کارته‌سه', kind: 'polygon', polygon: POLY, feeMinor: 3000, minOrderMinor: 0 };
const PHONES = ['+93791200003', '+93791200004', '+93791200005'];

let admin = '';
let shopId = '';
let otherShop = '';
const T = {};

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}
async function member(shop, role, name) {
  const u = await h.newUser(name, 'shop');
  await query(`INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at) VALUES ($1,$2,$3,$4,'active',$5,$5)`,
    [newId('mem'), shop, u.user.id, role, now()]);
  return { ...u, token: u.accessToken, userId: u.user.id };
}
const get = (who, path) => { resetAll(); return h.get(`/api/shop/storefront/${path}`, { token: T[who].token }); };
const layer = async (path, perms) => {
  resetAll();
  //  لایهٔ نقش: درِ دکانش برداشته شد ⇒ همان `staff-perms.setRoleLayer` ِ درِ ارشدِ پنل (`h.roleLayer`)
  const r = path.startsWith('roles/') ? await h.roleLayer(T.owner.token, path.slice('roles/'.length), perms)
    : await h.put(`/api/shop/permissions/${path}`, { perms }, { token: T.owner.token });
  assert.equal(r.status, 200, JSON.stringify(r.body));
};
const full = (body) => PHONES.some((p) => JSON.stringify(body).includes(p.slice(4)));

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(`INSERT INTO admins (id, username, name, password_hash, role, status, created_at) VALUES ($1,'cl-admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]);
  admin = (await h.post('/api/admin/login', { username: 'cl-admin', password: 'Admin!12345' })).body.token;
  async function shop(label) {
    const u = await h.newUser(label, 'shop');
    const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
    assert.equal(made.status, 201, JSON.stringify(made.body));
    const id = made.body.shop.id;
    for (const k of ['shop.orders', 'shop.storefront', 'shop.storefront.zones', 'shop.storefront.notify_area', 'shop.storefront.promo_guard']) {
      await setFlag(id, k, true);
    }
    const o = { ...u, token: u.accessToken, userId: u.user.id };
    const sf = await h.put('/api/shop/storefront', { slug: `cl-${id.slice(-8)}`, enabled: true, title: `ویترینِ ${label}`, minOrderMinor: 0 }, { token: o.token });
    assert.equal(sf.status, 200, JSON.stringify(sf.body));
    const z = await h.put('/api/shop/storefront/delivery', { zones: [ZONE] }, { token: o.token });
    assert.equal(z.status, 200, JSON.stringify(z.body));
    return { id, o };
  }
  ({ id: shopId, o: T.owner } = await shop('شماره‌ها'));
  ({ id: otherShop, o: T.other } = await shop('شماره‌های ب'));
  T.manager = await member(shopId, 'manager', 'مدیرِ باز');
  T.closed = await member(shopId, 'manager', 'مدیرِ بسته');
  T.cashier = await member(shopId, 'cashier', 'صندوق‌دار');
  const t = now();
  for (const [i, phone] of PHONES.entries()) {
    //  «خبرم کن» درونِ ناحیهٔ امروز ⇒ شماره در `contacts`
    await query(`INSERT INTO storefront_area_requests (id, shop_id, phone, cell_lat, cell_lng, created_at, updated_at) VALUES ($1,$2,$3,34.53,69.17,$4,$4)`,
      [`ar-${i}`, shopId, phone, t - 1000 + i]);
    //  سه شماره از یک دستگاه ⇒ «مشکوک»
    await query(`INSERT INTO storefront_promo_claims (shop_id, client_key, kind, promo_id, code, phone_key, device_key, discount_minor, created_at)
      VALUES ($1,$2,'code','pr-1','OFF10',$3,'dev-k1',5000,$4)`, [shopId, `ck-${i}`, phone, t - 1000 + i]);
  }
});
test.after(async () => { await h.stop(); });

test('بی لایه: صاحب و مدیر شمارهٔ کامل را در هر دو فهرست می‌بینند (همان امروز)', async () => {
  for (const who of ['owner', 'manager', 'closed']) {
    const a = await get(who, 'area-requests');
    assert.equal(a.status, 200, JSON.stringify(a.body));
    assert.deepEqual(a.body.contacts.map((c) => c.phone), PHONES, who);
    const g = await get(who, 'promo-guard');
    assert.equal(g.status, 200, JSON.stringify(g.body));
    assert.deepEqual(g.body.recent.map((r) => r.phone).sort(), PHONES, who);
    assert.deepEqual(g.body.suspicious.devices[0].phones, PHONES, who);
  }
  assert.equal((await get('cashier', 'area-requests')).status, 403);
  assert.equal((await get('cashier', 'promo-guard')).status, 403);
});

test('مدیرِ بسته (لایهٔ کاربر): فقط شمارهٔ پوشیده؛ شمار، ناحیه، دستگاه و شناسه همان؛ «خبر دادم» کار می‌کند', async () => {
  await layer(`members/${T.closed.userId}`, { 'customers.contact': { mode: 'deny' } });
  const a = await get('closed', 'area-requests');
  assert.equal(a.status, 200, JSON.stringify(a.body));
  assert.ok(!full(a.body), JSON.stringify(a.body));
  assert.deepEqual(a.body.contacts.map((c) => [c.id, c.phone, c.zone]),
    [['ar-0', '+•••••••••03', ZONE.name], ['ar-1', '+•••••••••04', ZONE.name], ['ar-2', '+•••••••••05', ZONE.name]]);
  assert.deepEqual([a.body.total, a.body.covered], [3, 3]);
  const g = await get('closed', 'promo-guard');
  assert.equal(g.status, 200, JSON.stringify(g.body));
  assert.ok(!full(g.body), JSON.stringify(g.body));
  assert.deepEqual(g.body.recent.map((r) => r.phone).sort(), ['+•••••••••03', '+•••••••••04', '+•••••••••05']);
  assert.deepEqual([g.body.suspicious.devices[0].count, g.body.suspicious.devices[0].phones.length], [3, 3]);
  assert.equal(g.body.recent[0].discountMinor, 5000);
  //  مدیرِ باز در همان دکان همچنان کامل (لایهٔ کاربر فقط همان یک نفر)
  assert.deepEqual((await get('manager', 'area-requests')).body.contacts.map((c) => c.phone), PHONES);
  //  «خبر دادم» با شناسه، بی شماره
  resetAll();
  const done = await h.post('/api/shop/storefront/area-requests/ar-0/notified', {}, { token: T.closed.token });
  assert.equal(done.status, 200, JSON.stringify(done.body));
  assert.deepEqual((await get('owner', 'area-requests')).body.contacts.map((c) => c.phone), PHONES.slice(1));
  await layer(`members/${T.closed.userId}`, {});
});

test('مدیرِ بسته (لایهٔ نقش): همهٔ مدیران پوشیده؛ صاحب هرگز؛ دکانِ دیگر هیچ', async () => {
  await layer('roles/manager', { 'customers.contact': { mode: 'deny' } });
  for (const who of ['manager', 'closed']) {
    assert.ok(!full((await get(who, 'area-requests')).body), who);
    assert.ok(!full((await get(who, 'promo-guard')).body), who);
  }
  assert.deepEqual((await get('owner', 'area-requests')).body.contacts.map((c) => c.phone), PHONES.slice(1), 'صاحب همیشه کامل');
  assert.deepEqual((await get('owner', 'promo-guard')).body.suspicious.devices[0].phones, PHONES);
  const other = await get('other', 'area-requests');
  assert.deepEqual(other.body.contacts, []);
  assert.deepEqual((await get('other', 'promo-guard')).body.recent, []);
  await layer('roles/manager', {});
});
