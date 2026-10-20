'use strict';
/**
 * نقشِ پیک و دامنهٔ توکن — DLV-01، v3 §۳.۱۱.
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) ⛔ پیش‌فرض خاموش: بی پرچمِ `shop.delivery` دعوتِ پیک ساخته و خرج نمی‌شود.
 *   ۲) دعوت از صفحهٔ کارمندان ⇒ عضوِ `courier` + ردیفِ `shop_couriers` + توکنِ
 *      `scope='courier'` (هر راهِ ورود و هر تازه‌سازی).
 *   ۳) توکنِ پیک روی `GET /api/sync/v1/pull` و `/snapshot`، روی `/api/sync/v3/*` و
 *      روی هر `/api/shop/*` دیگر ۴۰۳ `courier_scope` می‌گیرد.
 *   ۴) فهرستِ کاملِ درها (route inventory) از خودِ روترهای زنده: جز
 *      `/api/courier/*` هیچ دری به توکنِ پیک بیش از «بی‌توکن» نمی‌دهد.
 *   ۵) توکنِ بی‌دامنهٔ پیش از پیک شدن: یا باطل می‌شود یا نقشِ عضویت می‌بندد.
 *   ۶) مسیرهای پیک با همین توکن: قبول/رد، گرفتن/در راه/رسیدم، کدِ تحویل،
 *      عکس با رضایت، ناموفق و برگشت، نقدِ گرفته‌شده، صفِ آفلاین با
 *      eventId و زمان/ترتیبِ دستگاه، شمارهٔ پوشیده و پنهان شدنِ نشانی.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const express = require('express');

/*
 *  نقشهٔ سوارشدن از خودِ روترِ زنده — پیش از ساختنِ برنامه. هر `use(path, router)`
 *  روی لایه‌اش نشانیِ سوارشدن را می‌گذارد تا فهرستِ درها از کد خوانده شود، نه
 *  از یک فهرستِ دستیِ کهنه‌شونده.
 */
const RouterProto = express.Router.prototype;
const origUse = RouterProto.use;
RouterProto.use = function patchedUse(...args) {
  const before = this.stack.length;
  const out = origUse.apply(this, args);
  const p = typeof args[0] === 'string' || Array.isArray(args[0]) ? args[0] : '/';
  for (let i = before; i < this.stack.length; i++) this.stack[i].__mount = p;
  return out;
};

const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');

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
  assert.ok(admin, 'ورودِ مدیر');
});
test.after(async () => { await h.stop(); });

async function shopOwner(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, token: u.accessToken, shopId: made.body.shop.id };
}

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}
async function enable(shopId) {
  await setFlag(shopId, 'shop.orders', true);
  await setFlag(shopId, 'shop.delivery', true);
}

let seq = 0;
const freshKey = (p = 'k') => `${p}-${++seq}-${Date.now()}`;

async function invite(owner) {
  const r = await h.post('/api/shop/invite', { role: 'courier' }, { token: owner.token });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.equal(r.body.role, 'courier');
  return r.body.code;
}

/** پیک با «ورود با کدِ شاگرد» — همان راهِ برنامهٔ گوشی. */
async function courierJoin(owner, name) {
  const code = await invite(owner);
  const uid = `courier-phone-${++seq}`;
  const r = await h.post('/api/auth/staff-login', { code, name, device: { uid, name: 'گوشیِ پیک', platform: 'android' } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.equal(r.body.shop.role, 'courier', 'نشستِ ورود نقشِ پیک را می‌گوید (برنامه با آن حالتِ پیک را باز می‌کند)');
  return { ...r.body, token: r.body.accessToken, uid, userId: r.body.user.id };
}

const scopeOf = async (userId) => (await query(
  `SELECT kind, scope FROM tokens WHERE subject_id=$1 AND revoked_at IS NULL ORDER BY issued_at`, [userId]
)).rows;

function denied(r, label) {
  assert.equal(r.status, 403, `${label}: ${r.status} ${JSON.stringify(r.body)}`);
  assert.equal(r.body.error.code, 'courier_scope', `${label}: ${JSON.stringify(r.body)}`);
}

function deliveryOrder(over = {}) {
  return {
    source: 'phone', fulfillment: 'delivery', paymentMethod: 'cod',
    customer: { name: 'احمد', phone: '0791234567' },
    address: { landmark: 'نزدیکِ مسجدِ جامع', text: 'کارتهٔ چهار، کوچهٔ سوم', lat: 34.5, lng: 69.1 },
    delivery: { photoConsent: true, callBeforeArrival: true },
    lines: [{ name: 'برنج', qtyMilli: 1000, unitPriceMinor: 1_200_000 }],
    deliveryFeeMinor: 40_000,
    ...over,
  };
}
const TOTAL = 1_240_000;

async function packedOrder(owner, body = deliveryOrder()) {
  const r = await h.post('/api/shop/orders', body, { token: owner.token, headers: { 'Idempotency-Key': freshKey('order') } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  for (const to of ['accepted', 'picking', 'packed']) {
    const t = await h.post(`/api/shop/orders/${r.body.order.id}/transition`, { to }, { token: owner.token });
    assert.equal(t.status, 200, `${to}: ${JSON.stringify(t.body)}`);
  }
  return r.body.order;
}

async function assignTo(owner, courierId, orderId, extra = {}) {
  const r = await h.post(`/api/shop/delivery/orders/${orderId}/assign`,
    { mode: 'shop_courier', courierId, overrideCashCap: true, ...extra }, { token: owner.token });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return r.body;
}

let evSeq = 0;
/** رویدادِ صفِ برنامه: eventId یکتا + زمان و ترتیبِ خودِ دستگاه. */
const ev = (assignmentId, type, extra = {}) => ({
  eventId: `ev-${Date.now()}-${++evSeq}`, assignmentId, type, deviceTime: Date.now(), deviceSeq: evSeq, ...extra,
});
const send = (c, events) => h.post('/api/courier/events', { deviceId: c.uid, events }, { token: c.token });
async function sendOk(c, events) {
  const r = await send(c, events);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  for (const x of r.body.results) assert.equal(x.status, 'applied', JSON.stringify(x));
  return r.body;
}
const jpeg = () => Buffer.concat([Buffer.from([0xff, 0xd8, 0xff, 0xe0]), Buffer.alloc(300, 7)]).toString('base64');

/* ══════════════════════════════════════════════════════════════════
   ۱) پیش‌فرض خاموش
   ══════════════════════════════════════════════════════════════════ */

test('بی پرچم: دعوتِ پیک ۴۰۴ است و کدِ ساخته‌شده هم بعد از خاموش شدن خرج نمی‌شود', async () => {
  const a = await shopOwner('پیک‌خاموش');
  const off = await h.post('/api/shop/invite', { role: 'courier' }, { token: a.token });
  assert.equal(off.status, 404, JSON.stringify(off.body));
  assert.equal(off.body.error.code, 'delivery_disabled');
  //  دعوتِ شاگرد مثلِ همیشه
  const staff = await h.post('/api/shop/invite', { role: 'staff' }, { token: a.token });
  assert.equal(staff.status, 201);
  assert.equal(Number((await one(`SELECT COUNT(*)::int AS n FROM staff_codes WHERE shop_id=$1 AND role='courier'`, [a.shopId])).n), 0);

  //  پرچم روشن ⇒ کد ساخته می‌شود؛ خاموش ⇒ خرج نمی‌شود و عضوی ساخته نمی‌شود
  await enable(a.shopId);
  const code = await invite(a);
  await setFlag(a.shopId, 'shop.delivery', false);
  const join = await h.post('/api/auth/staff-login', { code, name: 'پیک', device: { uid: 'off-phone-1' } });
  assert.equal(join.status, 404, JSON.stringify(join.body));
  assert.equal(join.body.error.code, 'delivery_disabled');
  assert.equal(Number((await one(`SELECT COUNT(*)::int AS n FROM shop_members WHERE shop_id=$1 AND role='courier'`, [a.shopId])).n), 0);
  assert.equal(Number((await one('SELECT COUNT(*)::int AS n FROM shop_couriers WHERE shop_id=$1', [a.shopId])).n), 0);
});

/* ══════════════════════════════════════════════════════════════════
   ۲) دعوت ⇒ عضوِ پیک و توکنِ دامنه‌دار
   ══════════════════════════════════════════════════════════════════ */

test('دعوت از صفحهٔ کارمندان ⇒ عضوِ courier، ردیفِ shop_couriers و توکنِ scope=courier', async () => {
  const a = await shopOwner('دعوتِ پیک');
  await enable(a.shopId);
  const c = await courierJoin(a, 'پیکِ دعوتی');

  const m = await one('SELECT role, status FROM shop_members WHERE shop_id=$1 AND user_id=$2', [a.shopId, c.userId]);
  assert.deepEqual({ ...m }, { role: 'courier', status: 'active' });
  const row = await one('SELECT name, active, cash_cap_minor FROM shop_couriers WHERE shop_id=$1 AND user_id=$2', [a.shopId, c.userId]);
  assert.equal(row.name, 'پیکِ دعوتی');
  assert.equal(row.active, true);
  assert.equal(Number(row.cash_cap_minor), 1_000_000, 'سقفِ نقدِ پیش‌فرضِ D-18');
  for (const t of await scopeOf(c.userId)) assert.equal(t.scope, 'courier', `${t.kind} دامنه دارد`);

  //  صاحب در فهرستِ اعضا نقشِ «پیک» را می‌بیند؛ و در فهرستِ پیک‌ها هم هست
  const members = await h.get('/api/shop/members', { token: a.token });
  assert.equal(members.body.members.find((x) => x.userId === c.userId).role, 'courier');
  const couriers = await h.get('/api/shop/delivery/couriers', { token: a.token });
  assert.ok(couriers.body.items.some((x) => x.userId === c.userId));

  //  درِ پیک باز است
  const me = await h.get('/api/courier/me', { token: c.token });
  assert.equal(me.status, 200, JSON.stringify(me.body));
  assert.equal(me.body.courier.name, 'پیکِ دعوتی');

  //  ورودِ دوباره با همان کد/دستگاه کد را دوباره خرج نمی‌کند و همان دامنه را می‌دهد
  const again = await h.post('/api/auth/staff-login', { code: 'SHG-AAAA-AAAA-AAAA', name: 'x', device: { uid: c.uid } });
  assert.notEqual(again.status, 201, 'کدِ ساختگی راه نمی‌دهد');

  //  تازه‌سازی دامنه را نگه می‌دارد (و هیچ راهی برای «پهن» کردنش نیست)
  const ref = await h.post('/api/auth/refresh', { refreshToken: c.refreshToken });
  assert.equal(ref.status, 200, JSON.stringify(ref.body));
  denied(await h.get('/api/shop', { token: ref.body.accessToken }), 'بعد از تازه‌سازی');
  assert.equal((await h.get('/api/courier/me', { token: ref.body.accessToken })).status, 200);

  //  کدِ دعوتِ پیک ثابت نمی‌شود: کدِ ثابتِ دکان همان شاگرد است
  const standing = await h.get('/api/shop/staff-code', { token: a.token });
  assert.notEqual(standing.body.role, 'courier');
});

/* ══════════════════════════════════════════════════════════════════
   ۳) درهای بسته — همان آزمونِ نام‌برده در §۳.۱۱
   ══════════════════════════════════════════════════════════════════ */

test('§۳.۱۱: توکنِ پیک روی sync v1 pull/snapshot، sync v3 و /api/shop/* ۴۰۳ می‌گیرد', async () => {
  const a = await shopOwner('درهای بسته');
  await enable(a.shopId);
  const c = await courierJoin(a, 'پیکِ بسته');

  denied(await h.get('/api/sync/v1/pull', { token: c.token }), 'sync v1 pull');
  denied(await h.get('/api/sync/v1/pull?cursor=0', { token: c.token }), 'sync v1 pull cursor');
  denied(await h.get('/api/sync/v1/snapshot', { token: c.token }), 'sync v1 snapshot');
  denied(await h.post('/api/sync/v1/push', { ops: [] }, { token: c.token }), 'sync v1 push');
  //  v3: سرور خاموشش کرده ⇒ برای همه ۴۰۴ (چیزی لو نمی‌رود)؛ روشن ⇒ پیک ۴۰۳
  const config = require('../src/config');
  const wasV3 = config.syncV3.enabled;
  config.syncV3.enabled = false;
  assert.equal((await h.get('/api/sync/v3/pull', { token: c.token })).status, 404);
  config.syncV3.enabled = true;
  try {
    denied(await h.get('/api/sync/v3/pull', { token: c.token }), 'sync v3 pull');
    denied(await h.post('/api/sync/v3/push', { ops: [] }, { token: c.token }), 'sync v3 push');
    denied(await h.get('/api/sync/v3/snapshot', { token: c.token }), 'sync v3 snapshot');
    denied(await h.get('/api/sync/v3/hello', { token: c.token }), 'sync v3 hello');
  } finally { config.syncV3.enabled = wasV3; }
  //  و همان صاحبِ دکان با توکنِ خودش رد نمی‌شود (آزمونِ منفی سالم است)
  const ownerPull = await h.get('/api/sync/v1/pull', { token: a.token });
  assert.notEqual(ownerPull.status, 403, JSON.stringify(ownerPull.body));

  for (const p of ['/api/shop', '/api/shop/members', '/api/shop/orders', '/api/shop/delivery/couriers',
    '/api/shop/staff-code', '/api/shop/chat/threads', '/api/me', '/api/billing/status', '/api/sync']) {
    denied(await h.get(p, { token: c.token }), p);
  }
  denied(await h.post('/api/shop/orders', deliveryOrder(), { token: c.token, headers: { 'Idempotency-Key': freshKey('o') } }), 'ساختِ سفارش');
  denied(await h.post('/api/shop/invite', { role: 'staff' }, { token: c.token }), 'دعوت');
  denied(await h.post('/api/auth/logout-all', {}, { token: c.token }), 'logout-all');
  //  عکس‌ها (§۳.۱۱: «عکس‌ها راه ندارد»)
  denied(await h.get('/api/shop/delivery/assignments/01ARZ3NDEKTSV4RRFFQ69G5FAV/photos', { token: c.token }), 'عکس‌های تحویل');

  //  سوکتِ زنده هم همین قاعده را دارد: حسابی پیدا نمی‌شود
  const { resolveToken } = require('../src/lib/sync-v1-auth');
  const ctx = await resolveToken(c.token);
  assert.equal(ctx.accountId, '');
  assert.equal(ctx.missing, 'courier_scope');
  //  و خواننده‌ای که دامنه نمی‌شناسد توکن را «ندیده» می‌گیرد (پیش‌فرض بسته)
  const tokens = require('../src/lib/tokens');
  assert.equal(await tokens.verify(c.token, 'access', 'shop'), null);
  assert.ok(await tokens.verify(c.token, 'access', 'shop', { anyScope: true }));

  //  خروج از همین دستگاه کار می‌کند (بدنهٔ توکنِ تازه‌سازی)
  const out = await h.post('/api/auth/logout', { refreshToken: c.refreshToken }, { token: c.token });
  assert.equal(out.status, 200, JSON.stringify(out.body));
  assert.equal((await h.get('/api/courier/me', { token: c.token })).status, 401);
});

test('توکنِ بی‌دامنهٔ پیش از پیک شدن: /api/shop/join آن را می‌بندد و نشستِ دامنه‌دار می‌دهد؛ نقشِ عضویت هم می‌بندد', async () => {
  const a = await shopOwner('عضوِ پیشین');
  await enable(a.shopId);
  const u = await h.newUser('حسابِ رمزدار', 'shop');
  const code = await invite(a);
  const join = await h.post('/api/shop/join', { code }, { token: u.accessToken });
  assert.equal(join.status, 201, JSON.stringify(join.body));
  assert.equal(join.body.courier, true);
  assert.equal(join.body.role, 'courier', 'همان شکلِ پاسخِ پیوستن (ShopStateDto)');
  assert.deepEqual(join.body.permissions, [], 'نقشِ پیک هیچ مجوزی در جدولِ نقش‌ها ندارد');
  assert.ok(join.body.accessToken);
  //  نشستِ پیشین (بی‌دامنه) دیگر کار نمی‌کند — نه فقط روی /api/shop، که همه‌جا
  assert.equal((await h.get('/api/me', { token: u.accessToken })).status, 401);
  assert.equal((await h.post('/api/auth/refresh', { refreshToken: u.refreshToken })).status, 401);
  const scoped = join.body.accessToken;
  denied(await h.get('/api/shop', { token: scoped }), 'نشستِ تازه');
  assert.equal((await h.get('/api/courier/me', { token: scoped })).status, 200);
  for (const t of await scopeOf(u.user.id)) assert.equal(t.scope, 'courier');

  //  دفاعِ دوم: اگر توکنی بی‌دامنه مانده باشد (مثلاً پیش از مهاجرت)، نقشِ عضویت می‌بندد
  await query(`UPDATE tokens SET scope='' WHERE subject_id=$1`, [u.user.id]);
  denied(await h.get('/api/shop', { token: scoped }), 'نقش (requireShop)');
  denied(await h.get('/api/me', { token: scoped }), 'نقش (optionalShop)');
  denied(await h.get('/api/sync/v1/pull', { token: scoped }), 'نقش (sync v1)');
  const config = require('../src/config');
  const wasV3 = config.syncV3.enabled;
  config.syncV3.enabled = true;
  try { denied(await h.get('/api/sync/v3/pull', { token: scoped }), 'نقش (sync v3)'); } finally { config.syncV3.enabled = wasV3; }
  assert.equal((await h.get('/api/courier/me', { token: scoped })).status, 200, 'درِ پیک باز می‌ماند');
});

test('شاگردِ عادی که ردیفِ پیک دارد (رفتارِ #249) دست نخورده است', async () => {
  const a = await shopOwner('شاگردِ پیک');
  await enable(a.shopId);
  const s = await h.newUser('شاگرد', 'shop');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
     VALUES ($1,$2,$3,'staff','active',$4,$4)`, [newId('mem'), a.shopId, s.user.id, now()]
  );
  const add = await h.post('/api/shop/delivery/couriers', { userId: s.user.id }, { token: a.token });
  assert.equal(add.status, 201, JSON.stringify(add.body));
  assert.equal((await h.get('/api/shop', { token: s.accessToken })).status, 200);
  assert.equal((await h.get('/api/courier/me', { token: s.accessToken })).status, 200);
  for (const t of await scopeOf(s.user.id)) assert.equal(t.scope, '');
});

/* ══════════════════════════════════════════════════════════════════
   ۴) فهرستِ کاملِ درها
   ══════════════════════════════════════════════════════════════════ */

/** همهٔ درهای زیرِ `/api` از روترهای زنده. */
function inventory() {
  const server = h.server();
  const app = server.listeners('request')[0];
  const out = [];
  const join = (a, b) => (a + (b === '/' ? '' : b)).replace(/\/+/g, '/');
  const walk = (router, prefix) => {
    for (const layer of router.stack || []) {
      if (layer.route) {
        const paths = Array.isArray(layer.route.path) ? layer.route.path : [layer.route.path];
        for (const p of paths) {
          if (typeof p !== 'string') continue;
          for (const m of Object.keys(layer.route.methods)) {
            if (m === '_all') continue;
            out.push({ method: m.toUpperCase(), path: join(prefix, p) });
          }
        }
      } else if (layer.handle && Array.isArray(layer.handle.stack)) {
        const mounts = Array.isArray(layer.__mount) ? layer.__mount : [layer.__mount || '/'];
        for (const mp of mounts) if (typeof mp === 'string') walk(layer.handle, join(prefix, mp));
      }
    }
  };
  for (const layer of app.router.stack) {
    if (layer.__mount === '/api' && layer.handle && Array.isArray(layer.handle.stack)) walk(layer.handle, '/api');
  }
  const seen = new Set();
  return out.filter((r) => { const k = `${r.method} ${r.path}`; if (seen.has(k)) return false; seen.add(k); return true; });
}

const concrete = (p) => p
  .replace(/[{}]/g, '')
  .replace(/:app\b/g, 'shop')
  .replace(/\*[A-Za-z_]+/g, 'x1')
  .replace(/:[A-Za-z_]+/g, '01ARZ3NDEKTSV4RRFFQ69G5FAV');

/*
 *  درهایی که خودشان نشستِ فرستنده را می‌بندند — اگر بی‌دامنه صدا زده شوند
 *  توکنِ پیک را باطل می‌کنند و بقیهٔ فهرست بی‌معنا می‌شود. جداگانه بالاتر
 *  آزموده شده‌اند (خروج با توکنِ پیک کار می‌کند).
 */
const SKIP = new Set(['POST /api/auth/logout', 'POST /api/admin/logout']);

/*  بخش‌هایی که توکنِ دکان را اصلاً نمی‌شناسند (۴۰۱ «چنین نشستی نیست»):
    مدیرِ سامانه، پمپ و نمایندهٔ فروش. */
const FOREIGN = /^\/api\/(admin|pump|rep|portal)(\/|$)/;

test('فهرستِ کاملِ درها: توکنِ پیک جز /api/courier/* هیچ دری را بیش از «بی‌توکن» باز نمی‌کند', async (t) => {
  //  همگام‌سازیِ v3 روی سرورِ واقعی پشتِ پیکربندی است؛ این‌جا روشن تا درهایش هم سنجیده شوند
  const config = require('../src/config');
  const wasV3 = config.syncV3.enabled;
  config.syncV3.enabled = true;
  t.after(() => { config.syncV3.enabled = wasV3; });
  const a = await shopOwner('فهرستِ درها');
  await enable(a.shopId);
  const c = await courierJoin(a, 'پیکِ فهرست');

  const routes = inventory();
  assert.ok(routes.length > 300, `فقط ${routes.length} در پیدا شد`);
  t.diagnostic(`درها: ${routes.length}`);
  const courierRoutes = routes.filter((r) => r.path.startsWith('/api/courier/'));
  assert.ok(courierRoutes.length >= 4, `درهای پیک: ${courierRoutes.length}`);

  let shopDenied = 0;
  let syncDenied = 0;
  let scopeDenied = 0;
  const problems = [];
  for (const r of routes) {
    const key = `${r.method} ${r.path}`;
    if (SKIP.has(key)) continue;
    const url = concrete(r.path);
    const body = r.method === 'GET' || r.method === 'HEAD' ? null : {};
    const cour = await h.api(r.method, url, { token: c.token, body });
    if (r.path.startsWith('/api/courier/')) {
      if (cour.status === 401 || cour.status === 403) problems.push(`${key} ⇒ پیک ${cour.status}`);
      continue;
    }
    const anon = await h.api(r.method, url, { body });
    if (anon.status === 429 || cour.status === 429) continue;
    const isShop = /^\/api\/(shop|sync)(\/|$)/.test(r.path);
    const scoped = cour.status === 403 && cour.body?.error?.code === 'courier_scope';
    if (scoped) {
      scopeDenied += 1;
      if (r.path.startsWith('/api/shop')) shopDenied += 1;
      if (r.path.startsWith('/api/sync')) syncDenied += 1;
      continue;
    }
    //  ⛔ هر درِ دکان و همگام‌سازی: فقط و فقط ۴۰۳ courier_scope
    if (isShop) {
      problems.push(`${key} ⇒ بی‌توکن ${anon.status}، پیک ${cour.status} ${JSON.stringify(cour.body?.error || cour.body).slice(0, 120)}`);
      continue;
    }
    //  بخشِ بیگانه (مدیر/پمپ/نماینده): توکنِ دکان آن‌جا اصلاً نشست نیست
    if (cour.status === 401 && FOREIGN.test(r.path)) continue;
    //  بقیه: توکنِ پیک دقیقاً همان پاسخِ «بی‌توکن» را می‌گیرد — نه بیشتر
    const code = (x) => x.body?.error?.code || '';
    if (cour.status !== anon.status || code(cour) !== code(anon)) {
      problems.push(`${key} ⇒ بی‌توکن ${anon.status} ${code(anon)}، پیک ${cour.status} ${code(cour)}`);
    }
  }
  assert.deepEqual(problems, [], problems.join('\n'));
  //  هر درِ دکان و همگام‌سازی شمرده شد — نه نمونه‌ای از آن‌ها
  const shopCount = routes.filter((r) => /^\/api\/shop(\/|$)/.test(r.path)).length;
  const syncCount = routes.filter((r) => /^\/api\/sync(\/|$)/.test(r.path)).length;
  assert.ok(shopCount >= 70, `فقط ${shopCount} درِ /api/shop در فهرست`);
  assert.ok(syncCount >= 15, `فقط ${syncCount} درِ /api/sync در فهرست`);
  assert.equal(shopDenied, shopCount, 'هر درِ /api/shop ⇒ ۴۰۳ courier_scope');
  assert.equal(syncDenied, syncCount, 'هر درِ /api/sync ⇒ ۴۰۳ courier_scope');
  assert.ok(scopeDenied > shopDenied + syncDenied, 'درهای دیگرِ کاربر (/api/me، صورت‌حساب…) هم');

  //  و پس از این همه، توکن هنوز زنده است و درِ پیک باز
  assert.equal((await h.get('/api/courier/me', { token: c.token })).status, 200);
});

/* ══════════════════════════════════════════════════════════════════
   ۵) مسیرهای پیک با همین توکن
   ══════════════════════════════════════════════════════════════════ */

test('مسیرِ پیک: کارت، قبول، گرفتن، در راه، رسیدم، عکس با رضایت، کد، نقد؛ نشانی پس از پایان پنهان', async () => {
  const a = await shopOwner('مسیرِ پیکِ نقشی');
  await enable(a.shopId);
  const c = await courierJoin(a, 'پیکِ مسیر');
  const courierId = (await h.get('/api/courier/me', { token: c.token })).body.courier.id;

  const order = await packedOrder(a);
  const asg = await assignTo(a, courierId, order.id, { requireCode: true, fareMinor: 5_000 });
  const id = asg.assignment.id;

  const ms = await h.get('/api/courier/missions', { token: c.token });
  assert.equal(ms.status, 200, JSON.stringify(ms.body));
  const card = ms.body.items[0];
  assert.equal(card.address.landmark, 'نزدیکِ مسجدِ جامع');
  assert.match(card.customer.phoneMasked, /•/);
  assert.equal(JSON.stringify(ms.body).includes('791234567'), false, 'شمارهٔ کامل به پیک نمی‌رسد');
  assert.equal(card.requiresCode, true);
  assert.equal(card.photoAllowed, true);
  assert.equal(card.expectedCollectMinor, TOTAL);

  //  صفِ آفلاین: یک بسته با ترتیبِ دستگاه، فرستاده‌شده به ترتیبِ وارونه
  const q = [ev(id, 'assigned'), ev(id, 'picked_up'), ev(id, 'moving'), ev(id, 'arrived')];
  const res = await send(c, [...q].reverse());
  assert.equal(res.status, 200, JSON.stringify(res.body));
  for (const x of res.body.results) assert.equal(x.status, 'applied', JSON.stringify(x));
  //  همان صف دوباره (شبکه وسطِ پاسخ قطع شد) ⇒ تکراری، بی اثرِ دوم
  const replay = await send(c, q);
  for (const x of replay.body.results) assert.equal(x.status, 'duplicate', JSON.stringify(x));
  //  همان eventId با بدنهٔ دیگر ⇒ رد
  const reused = await send(c, [{ ...q[3], type: 'failed', reason: 'no_answer' }]);
  assert.equal(reused.body.results[0].status, 'rejected');
  assert.equal(reused.body.results[0].code, 'event_key_reused');

  //  عکس: فقط «بسته» و با رضایت
  const noParcel = await h.post(`/api/courier/missions/${id}/photos`, { eventId: freshKey('ph'), imageBase64: jpeg() }, { token: c.token });
  assert.equal(noParcel.status, 400);
  assert.equal(noParcel.body.error.code, 'parcel_only_required');
  const ph = await h.post(`/api/courier/missions/${id}/photos`, { eventId: freshKey('ph'), imageBase64: jpeg(), parcelOnly: true, deviceTime: Date.now() }, { token: c.token });
  assert.equal(ph.status, 201, JSON.stringify(ph.body));

  //  کدِ تحویل و نقد؛ نقدِ کمتر بی دلیل رد
  const wrong = await send(c, [ev(id, 'delivered', { code: asg.code === '000000' ? '111111' : '000000', cashCollectedMinor: TOTAL })]);
  assert.equal(wrong.body.results[0].code, 'bad_code');
  const short = await send(c, [ev(id, 'delivered', { code: asg.code, cashCollectedMinor: TOTAL - 40_000 })]);
  assert.equal(short.body.results[0].code, 'cash_reason_required');
  await sendOk(c, [ev(id, 'delivered', { code: asg.code, cashCollectedMinor: TOTAL - 40_000, cashReason: 'partial_payment' })]);

  //  پس از پایان: نشانی و مشتری دیده نمی‌شوند
  const done = await h.get('/api/courier/missions?scope=done', { token: c.token });
  const fin = done.body.items.find((x) => x.id === id);
  assert.equal(fin.status, 'delivered');
  assert.equal(fin.address, null);
  assert.equal(fin.customer, null);
  assert.equal(fin.cashCollectedMinor, TOTAL - 40_000);
  assert.equal((await h.get('/api/courier/missions', { token: c.token })).body.items.some((x) => x.id === id), false);

  //  دفترِ نقدِ پیک (DLV-11) همان مبلغِ گرفته‌شده را دارد
  const cs = await h.get('/api/shop/delivery/couriers', { token: a.token });
  assert.equal(cs.body.items.find((x) => x.id === courierId).cashHeldMinor, TOTAL - 40_000);
});

test('مسیرِ پیک: ردِ کار با دلیل؛ ناموفق ← تلاشِ دوباره ← ناموفق ← برگشت (دکان ثبت می‌کند)', async () => {
  const a = await shopOwner('ناموفقِ پیک');
  await enable(a.shopId);
  const c = await courierJoin(a, 'پیکِ ناموفق');
  const courierId = (await h.get('/api/courier/me', { token: c.token })).body.courier.id;

  //  رد
  const o1 = await packedOrder(a);
  const r1 = await assignTo(a, courierId, o1.id);
  const noReason = await send(c, [ev(r1.assignment.id, 'rejected')]);
  assert.equal(noReason.body.results[0].code, 'reason_required');
  await sendOk(c, [ev(r1.assignment.id, 'rejected', { reason: 'busy' })]);

  //  ناموفق و برگشت
  const o2 = await packedOrder(a);
  const r2 = await assignTo(a, courierId, o2.id);
  const id = r2.assignment.id;
  await sendOk(c, [ev(id, 'assigned'), ev(id, 'picked_up'), ev(id, 'moving'), ev(id, 'arrived')]);
  await sendOk(c, [ev(id, 'failed', { reason: 'no_answer' })]);
  await sendOk(c, [ev(id, 'moving'), ev(id, 'failed', { reason: 'wrong_address' })]);
  //  پیک خودش «برگشت» را ثبت نمی‌کند — دکان تحویل می‌گیرد
  const self = await send(c, [ev(id, 'returned')]);
  assert.equal(self.body.results[0].code, 'staff_only');
  const back = await h.post(`/api/shop/delivery/assignments/${id}/status`, { to: 'returned', note: 'بسته سالم برگشت' }, { token: a.token, headers: { 'Idempotency-Key': freshKey('ret') } });
  assert.equal(back.status, 200, JSON.stringify(back.body));
  const done = await h.get('/api/courier/missions?scope=done', { token: c.token });
  const fin = done.body.items.find((x) => x.id === id);
  assert.equal(fin.status, 'returned');
  assert.equal(fin.address, null, 'نشانی پس از پایانِ مأموریت پنهان');
  assert.equal(fin.cashCollectedMinor, null, 'نقدِ گرفته‌نشده در دفترِ پیک نمی‌نشیند');
});

/* ══════════════════════════════════════════════════════════════════
   ۷) «نقدِ من» و آمارِ پیک — فقط خواندن، فقط دادهٔ خودش
   ══════════════════════════════════════════════════════════════════ */

test('«نقدِ من»: نقدِ در دست، سقف، تسویه‌ها و آمار فقط برای خودِ پیک؛ پیکِ دیگر و دکانِ دیگر هیچ‌چیز نمی‌بینند', async () => {
  const a = await shopOwner('نقدِ پیک');
  await enable(a.shopId);
  const c1 = await courierJoin(a, 'پیکِ یکم');
  const c2 = await courierJoin(a, 'پیکِ دوم');
  const b = await shopOwner('دیگرِ نقد');
  await enable(b.shopId);
  const c3 = await courierJoin(b, 'پیکِ دکانِ دیگر');
  const id1 = (await h.get('/api/courier/me', { token: c1.token })).body.courier.id;

  //  دو تحویل با نقد؛ یکی تسویه می‌شود (با کسری و یادداشتِ مدیر)
  const deliver = async () => {
    const o = await packedOrder(a);
    const asg = await assignTo(a, id1, o.id, { fareMinor: 5_000 });
    const id = asg.assignment.id;
    await sendOk(c1, [ev(id, 'assigned'), ev(id, 'picked_up'), ev(id, 'moving'), ev(id, 'arrived'),
      ev(id, 'delivered', { cashCollectedMinor: TOTAL })]);
    return { id, order: o };
  };
  const first = await deliver();
  const second = await deliver();
  const st = await h.post(`/api/shop/delivery/couriers/${id1}/settlements`,
    { assignmentIds: [first.id], handedOverMinor: TOTAL, receivedMinor: TOTAL - 10_000, reason: 'کسریِ شمارش', note: 'یادداشتِ مدیر' },
    { token: a.token, headers: { 'Idempotency-Key': freshKey('settle') } });
  assert.equal(st.status, 201, JSON.stringify(st.body));
  //  یک کارِ زنده که نقدش هنوز گرفته نشده
  const o3 = await packedOrder(a);
  await assignTo(a, id1, o3.id);

  const dayStart = Date.now() - 3_600_000;
  const s1 = await h.get(`/api/courier/summary?today=${dayStart}&week=${dayStart - 86_400_000}`, { token: c1.token });
  assert.equal(s1.status, 200, JSON.stringify(s1.body));
  assert.equal(s1.headers.get('cache-control'), 'no-store');
  assert.equal(s1.body.courier.name, 'پیکِ یکم');
  assert.equal(s1.body.shop.name, 'دکانِ نقدِ پیک');
  assert.equal(s1.body.cash.inHandMinor, TOTAL, 'فقط تحویلِ تسویه‌نشده در دست است');
  assert.equal(s1.body.cash.liveExpectedMinor, TOTAL);
  assert.equal(s1.body.cash.capMinor, 1_000_000);
  assert.equal(s1.body.cash.headroomMinor, 0, 'از سقف گذشته ⇒ جای خالی صفر، نه منفی');
  assert.equal(s1.body.unsettled.length, 1);
  assert.equal(s1.body.unsettled[0].orderNo, Number(second.order.orderNo));
  assert.equal(s1.body.settlements.length, 1);
  assert.equal(s1.body.settlements[0].receivedMinor, TOTAL - 10_000);
  assert.ok(s1.body.settlements[0].shortageMinor > 0);
  assert.equal(s1.body.stats.today.delivered, 2);
  assert.equal(s1.body.stats.today.collectedCashMinor, 2 * TOTAL);
  assert.equal(s1.body.stats.today.fareMinor, 10_000);
  assert.equal(s1.body.stats.week.delivered, 2);
  const raw = JSON.stringify(s1.body);
  assert.equal(raw.includes('یادداشتِ مدیر'), false, 'یادداشتِ مدیر به پیک نمی‌رسد');
  assert.equal(raw.includes(a.user.id), false, 'شناسهٔ تأییدکننده به پیک نمی‌رسد');
  assert.equal(raw.includes('791234567'), false, 'شمارهٔ مشتری در «نقدِ من» نیست');

  //  پیکِ دیگرِ همین دکان: هیچ‌چیزِ پیکِ یکم
  const s2 = await h.get('/api/courier/summary', { token: c2.token });
  assert.equal(s2.status, 200, JSON.stringify(s2.body));
  assert.equal(s2.body.cash.inHandMinor, 0);
  assert.equal(s2.body.cash.liveExpectedMinor, 0);
  assert.deepEqual(s2.body.unsettled, []);
  assert.deepEqual(s2.body.settlements, []);
  assert.equal(s2.body.stats.week.delivered, 0);
  //  پیکِ دکانِ دیگر: دکانِ خودش، نه این
  const s3 = await h.get('/api/courier/summary', { token: c3.token });
  assert.equal(s3.status, 200, JSON.stringify(s3.body));
  assert.equal(s3.body.shop.name, 'دکانِ دیگرِ نقد');
  assert.equal(s3.body.cash.inHandMinor, 0);
  assert.deepEqual(s3.body.settlements, []);
  assert.equal(JSON.stringify(s3.body).includes('نقدِ پیک'), false);

  //  صاحبِ دکان پیک نیست؛ بی توکن هم نه؛ بازهٔ بد ⇒ ۴۰۰
  const owner = await h.get('/api/courier/summary', { token: a.token });
  assert.equal(owner.status, 403, JSON.stringify(owner.body));
  assert.equal(owner.body.error.code, 'not_a_courier');
  assert.equal((await h.get('/api/courier/summary')).status, 401);
  const bad = await h.get('/api/courier/summary?today=abc', { token: c1.token });
  assert.equal(bad.status, 400, JSON.stringify(bad.body));

  //  خطِ زمانیِ مأموریت: زمانِ سرور و این‌که کار از پیک بود یا دکان — بی شناسهٔ کاربر
  const done = await h.get('/api/courier/missions?scope=done', { token: c1.token });
  const tl = done.body.items.find((x) => x.id === second.id).timeline;
  assert.deepEqual(tl.map((x) => x.to), ['requested', 'assigned', 'picked_up', 'moving', 'arrived', 'delivered']);
  assert.equal(tl[0].by, 'shop');
  assert.equal(tl[5].by, 'courier');
  assert.ok(tl.every((x) => Number.isSafeInteger(x.at) && x.at > 0));
  assert.equal(JSON.stringify(tl).includes(c1.userId), false);
  //  پیکِ دوم مأموریت‌های پیکِ یکم را نمی‌بیند
  const other = await h.get('/api/courier/missions?scope=done', { token: c2.token });
  assert.deepEqual(other.body.items, []);
});
