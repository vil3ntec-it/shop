'use strict';
/**
 * WEB-23 (§۶.۱۴): پروفایلِ عمومیِ ویترین هیچ دادهٔ شخصیِ صاحبِ دکان ندارد —
 * نه نام، نه عکس، نه نشانی، نه ایمیل/تلفنِ حساب، نه جای ثبت‌نام.
 *
 *   ۱) `profile()` فقط کلیدهای فهرستِ مجاز را بیرون می‌دهد، هر ستونی هم که ردیف داشته باشد.
 *   ۲) پاسخِ HTTPِ `GET /api/store/:slug` برای دکانی که صاحبش نام، شهر، تلفن و
 *      مختصات دارد، هیچ‌کدام از آن‌ها را (در هیچ عمقی) ندارد.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const sf = require('../src/lib/storefront');

const PROFILE_KEYS = [
  'slug', 'title', 'tagline', 'contactPhone', 'accent', 'status', 'fulfillment',
  'deliveryFeeMinor', 'minOrderMinor', 'currency', 'payment', 'categories',
].sort();
const PERSONAL_KEY = /(^|_)(owner|user|person|full_?name|first_?name|last_?name|avatar|photo|picture|image|address|email|city|lat|lng|location|birth)/i;

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
  assert.ok(admin);
});
test.after(async () => { await h.stop(); });
test.beforeEach(() => resetAll());

function keysDeep(x, out = []) {
  if (Array.isArray(x)) x.forEach((v) => keysDeep(v, out));
  else if (x && typeof x === 'object') for (const [k, v] of Object.entries(x)) { out.push(k); keysDeep(v, out); }
  return out;
}

test('WEB-23: profile() فقط کلیدهای مجاز را می‌دهد؛ ستون‌های شخصیِ ردیف بیرون نمی‌روند', () => {
  const row = {
    shop_id: 'shp-1', slug: 'abc', title: 'دکانِ نمونه', tagline: 'شعار', contact_phone: '0700000001', accent: '#123456',
    accept_delivery: true, accept_pickup: false, delivery_fee_minor: '500', min_order_minor: '0',
    //  آن‌چه هرگز نباید بیرون برود، حتی اگر روزی به ردیف بپیوندد
    owner_user_id: 'usr-secret', owner_name: 'نامِ-شخصیِ-صاحب', owner_email: 'owner@private.test',
    owner_phone: '0799999999', owner_photo: 'https://x.test/me.jpg', avatar: 'a.png', address: 'کوچهٔ خصوصی ۷',
    city: 'شهرِ-خصوصی', last_lat: 34.5553, last_lng: 69.2075,
  };
  const p = sf.profile(row, [], null);
  assert.deepEqual(Object.keys(p).sort(), PROFILE_KEYS);
  const json = JSON.stringify(p);
  for (const v of ['usr-secret', 'نامِ-شخصیِ-صاحب', 'owner@private.test', '0799999999', 'me.jpg', 'a.png',
    'کوچهٔ خصوصی', 'شهرِ-خصوصی', '34.5553', '69.2075', 'shp-1']) {
    assert.ok(!json.includes(v), `«${v}» در پروفایلِ عمومی آمد`);
  }
  assert.deepEqual(keysDeep(p).filter((k) => PERSONAL_KEY.test(k)), []);
});

test('WEB-23: GET /api/store/:slug نام، ایمیل، تلفن، شهر و مختصاتِ صاحب را نمی‌دهد', async () => {
  const ownerName = 'زرغونه‌-خصوصی-WEB23';
  const u = await h.newUser(ownerName, 'shop');
  await query(
    `UPDATE users SET phone = $2, city = $3, last_lat = $4, last_lng = $5, last_location_at = $6 WHERE id = $1`,
    [u.user.id, '0788123456', 'شهرِ-خصوصی-WEB23', 34.5553, 69.2075, now()]
  );
  const made = await h.post('/api/shop', { name: 'دکانِ عمومی' }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  for (const key of ['shop.orders', 'shop.storefront']) {
    const r = await h.put('/api/admin/pump/live-config', { scope: made.body.shop.id, key, value: true }, { token: admin });
    assert.equal(r.status, 200, JSON.stringify(r.body));
  }
  const slug = `privacy-${Date.now().toString(36)}`;
  const put = await h.put('/api/shop/storefront', {
    slug, enabled: true, title: 'ویترینِ عمومی', tagline: 'خوش آمدید', contactPhone: '0700000001',
  }, { token: u.accessToken });
  assert.equal(put.status, 200, JSON.stringify(put.body));

  const r = await h.get(`/api/store/${slug}`);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.store.title, 'ویترینِ عمومی');
  assert.equal(r.body.store.contactPhone, '+93700000001', 'تلفنِ تماسِ خودِ ویترین (نه حساب) دیده می‌شود');
  const { delivery: _d, ordering, ...store } = r.body.store;
  assert.deepEqual(Object.keys(store).sort(), PROFILE_KEYS);
  //  «سفارش می‌پذیرد؟» (shop.orders.subscribers_only) — فقط همین، بی هیچ دادهٔ دکان یا اشتراک
  assert.deepEqual(ordering, { accepting: true });
  const json = JSON.stringify(r.body);
  for (const v of [ownerName, u.email, '0788123456', 'شهرِ-خصوصی-WEB23', '34.5553', '69.2075', u.user.id]) {
    assert.ok(!json.includes(v), `«${v}» در پاسخِ عمومیِ ویترین آمد`);
  }
  assert.deepEqual(keysDeep(r.body).filter((k) => PERSONAL_KEY.test(k)), []);
});
