'use strict';
/**
 *  درخواستِ خرید از نسخه‌ی وب هم باید برسد.
 *
 *  ── چه چیزی شکسته بود ─────────────────────────────────────────────
 *  نسخه‌ی وب `POST /api/v1/billing/request` را با بدنه‌ی `{planCode}`
 *  صدا می‌زد. سرور نه چنین مسیری داشت (فقط `/me/purchase-request`) و
 *  نه فیلدِ `planCode` را می‌خواند (فقط `plan`). یعنی هر کسی از سایت
 *  «می‌خرم» را می‌زد، درخواستش ۴۰۴ می‌گرفت و بی‌صدا گم می‌شد — و
 *  چون سمتِ وب داخل `catch` خالی بود، کاربر هم چیزی نمی‌دید.
 *
 *  اینجا هر چهار حالت سنجیده می‌شود: هر دو مسیر، با هر دو نامِ فیلد.
 *  ──────────────────────────────────────────────────────────────────
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');

test.before(async () => { await h.start(); });
test.after(async () => { await h.stop(); });

async function ownerWithShop(name) {
  const u = await h.newUser(name);
  const shop = await h.post('/api/shop', { name: `دکان ${name}` }, { token: u.accessToken });
  assert.ok(shop.status < 300, `دکان ساخته نشد: ${JSON.stringify(shop.body)}`);
  return u;
}

test('مسیرِ /billing/request با planCode — همان که وب می‌فرستد', async () => {
  const u = await ownerWithShop('وبی');
  const r = await h.post('/api/v1/billing/request', { planCode: 'm6' }, { token: u.accessToken });
  assert.equal(r.status, 201);
  assert.equal(r.body.request.plan, 'm6');
  assert.equal(r.body.request.status, 'pending');
});

test('مسیرِ قدیمیِ /me/purchase-request با plan هنوز کار می‌کند', async () => {
  const u = await ownerWithShop('نیتیوی');
  const r = await h.post('/api/v1/me/purchase-request', { plan: 'y1' }, { token: u.accessToken });
  assert.equal(r.status, 201);
  assert.equal(r.body.request.plan, 'y1');
});

test('هر مسیر، هر دو نامِ فیلد را می‌پذیرد', async () => {
  const u = await ownerWithShop('دوزبانه');
  const a = await h.post('/api/v1/billing/request', { plan: 'm6' }, { token: u.accessToken });
  const b = await h.post('/api/v1/me/purchase-request', { planCode: 'y1' }, { token: u.accessToken });
  assert.equal(a.status, 201);
  assert.equal(b.status, 201);
});

test('بدونِ پلن رد می‌شود، و بدونِ توکن هم', async () => {
  const u = await ownerWithShop('بی‌پلن');
  const empty = await h.post('/api/v1/billing/request', {}, { token: u.accessToken });
  assert.ok(empty.status >= 400, 'درخواستِ بی‌پلن نباید پذیرفته شود');

  const anon = await h.post('/api/v1/billing/request', { planCode: 'm6' });
  assert.ok(anon.status === 401 || anon.status === 403, `بی‌توکن باید بسته باشد، شد ${anon.status}`);
});

test('کاربرِ بی‌دکان نمی‌تواند درخواست بدهد', async () => {
  const u = await h.newUser('بی‌دکان');
  const r = await h.post('/api/v1/billing/request', { planCode: 'm6' }, { token: u.accessToken });
  assert.equal(r.status, 403);
  assert.equal(r.body.error.code, 'no_shop');
});
