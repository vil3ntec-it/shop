'use strict';
/**
 * دکانِ فعالِ نشست برای کاربرِ چنددکانی — بندِ ۲۹.۲ قاعدهٔ ۱ و ۲، بندِ ۲۱.۱،
 * AUTH-09 و TEN-01 (مهاجرتِ ۰۹۷، `tokens.shop_id`).
 *
 * ⚠️ امروز برنامه جلوی عضویتِ دوم را می‌گیرد (`already_member` در ساخت و
 * پیوستن). عضویتِ دوم این‌جا مستقیم در جدول نشانده می‌شود — همان حالتی که
 * دادهٔ قدیمی، کارِ پشتیبانی یا تصمیمِ آیندهٔ محصول می‌سازد — تا ثابت شود
 * سرور در آن حالت هم دکان را فقط از نشستِ سنجیده برمی‌دارد.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, newId, now, one } = require('../src/db');

test.before(async () => { await h.start(); });
test.after(async () => { await h.stop(); });

async function owner(label) {
  const u = await h.newUser(label);
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const shopId = made.body.shop.id;
  assert.equal((await h.put('/api/shop/settings', { settings: { mark: label } }, { token: u.accessToken })).status, 200);
  return { ...u, shopId };
}
async function addMember(shopId, userId, role = 'staff') {
  const t = now();
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
     VALUES ($1,$2,$3,$4,'active',$5,$5)`,
    [newId('mem'), shopId, userId, role, t]
  );
}
const markOf = async (token) => {
  const r = await h.get('/api/shop/settings', { token });
  return r.status === 200 ? r.body.settings?.mark ?? r.body.data?.mark : `HTTP ${r.status} ${r.body?.error?.code}`;
};

test('آزمونِ مرجع: کاربرِ تک‌دکانی همان رفتارِ امروز را دارد', async () => {
  const a = await owner('تک');
  assert.equal(await markOf(a.accessToken), 'تک');
  const list = await h.get('/api/shop/memberships', { token: a.accessToken });
  assert.equal(list.status, 200);
  assert.equal(list.body.explicit, false);
  assert.equal(list.body.activeShopId, a.shopId);
  assert.equal(list.body.shops.length, 1);
  const again = await h.signIn(a);
  assert.deepEqual(again.shops, [{ id: a.shopId, name: 'دکانِ تک', role: 'owner' }]);
});

test('چنددکانی: پیش‌فرض همان قبلی؛ انتخابِ صریح نشست را به دکانِ دیگر می‌بندد و با تازه‌سازی می‌ماند', async () => {
  const a = await owner('الف');
  const b = await owner('ب');
  await addMember(b.shopId, a.user.id, 'staff');

  const s = await h.signIn(a);
  assert.equal(s.shops.length, 2, 'ورود فهرستِ دکان‌ها را می‌دهد');
  assert.equal(s.shop.id, a.shopId, 'پیش‌فرض: دکانی که صاحبش است — رفتارِ امروز');
  assert.equal(await markOf(s.accessToken), 'الف');

  const pick = await h.post('/api/shop/active', { shopId: b.shopId }, { token: s.accessToken });
  assert.equal(pick.status, 200, JSON.stringify(pick.body));
  assert.equal(pick.body.shop.role, 'staff');
  assert.equal(await markOf(s.accessToken), 'ب');
  const shop = await h.get('/api/shop', { token: s.accessToken });
  assert.equal(shop.body.role, 'staff', 'نقش هم از همان دکان');
  //  شاگردِ دکانِ «ب» نمی‌تواند تنظیماتِ «ب» را بنویسد — مجوز از همان عضویت
  assert.equal((await h.put('/api/shop/settings', { settings: { mark: 'x' } }, { token: s.accessToken })).status, 403);

  const r = await h.post('/api/auth/refresh', { refreshToken: s.refreshToken });
  assert.equal(r.status, 200);
  assert.equal(await markOf(r.body.accessToken), 'ب', 'تازه‌سازی دکانِ برگزیده را نگه می‌دارد');

  const log = await one(`SELECT * FROM audit_logs WHERE action='auth.active_shop' AND user_id=$1`, [a.user.id]);
  assert.equal(log.shop_id, b.shopId);

  //  نشستِ دستگاهِ دیگرِ همین آدم دست نخورد
  const other = await h.api('POST', '/api/auth/login', {
    body: { identifier: a.email, password: a.password, device: { deviceId: 'dev-other', name: 'x', platform: 'test' } },
  });
  assert.equal(await markOf(other.body.accessToken), 'الف');
});

test('AUTH-09/TEN-01: دکانِ بی‌عضویت رد؛ شناسهٔ دکان در بدنه/سرآیند/نشانی نادیده', async () => {
  const a = await owner('ج');
  const c = await owner('د');
  const no = await h.post('/api/shop/active', { shopId: c.shopId }, { token: a.accessToken });
  assert.equal(no.status, 403);
  assert.equal(no.body.error.code, 'not_member');
  const ghost = await h.post('/api/shop/active', { shopId: 'shp_nist' }, { token: a.accessToken });
  assert.equal(ghost.status, 403);
  assert.equal(ghost.body.error.code, 'not_member', 'دکانِ ناموجود همان پاسخ را دارد');
  assert.equal((await h.post('/api/shop/active', { shopId: a.shopId })).status, 401, 'بی توکن');

  const sneaky = await h.api('GET', `/api/shop/settings?shopId=${c.shopId}&shop_id=${c.shopId}`, {
    token: a.accessToken, headers: { 'X-Shop-Id': c.shopId },
  });
  assert.equal(sneaky.status, 200);
  assert.notEqual(sneaky.body.settings?.mark ?? sneaky.body.data?.mark, 'د');
  assert.equal(await markOf(a.accessToken), 'ج');
});

test('عضویتِ دکانِ برگزیده تمام شد ⇒ خطای روشن، نه افتادنِ بی‌صدا به دکانِ دیگر', async () => {
  const a = await owner('ه');
  const b = await owner('و');
  await addMember(b.shopId, a.user.id, 'staff');
  const s = await h.signIn(a);
  assert.equal((await h.post('/api/shop/active', { shopId: b.shopId }, { token: s.accessToken })).status, 200);

  await query(`UPDATE shop_members SET status='suspended' WHERE shop_id=$1 AND user_id=$2`, [b.shopId, a.user.id]);
  for (const path of ['/api/shop/settings', '/api/shop', '/api/events']) {
    const r = await h.get(path, { token: s.accessToken });
    assert.equal(r.status, 403, `${path} ⇒ ${r.status}`);
    assert.equal(r.body.error.code, 'active_shop_unavailable', path);
  }
  //  و راهِ برگشت روشن است: دکانِ خودش را دوباره برمی‌گزیند
  assert.equal((await h.post('/api/shop/active', { shopId: a.shopId }, { token: s.accessToken })).status, 200);
  assert.equal(await markOf(s.accessToken), 'ه');
});
