'use strict';
/**
 * رقابت‌های PostgreSQL ِ واقعی — دورِ دوم (دنبالهٔ `pg-concurrency.test.js`). PGlite یک اتصال دارد و هر تراکنش همهٔ
 * پرسش‌های دیگر را پشتِ خودش نگه می‌دارد، پس رقابتِ درونِ تراکنش آن‌جا پیش نمی‌آید؛ رقابتِ «میانِ دو گامِ جدا» (۴)
 * آن‌جا هم هست.
 *
 *   ۱) سقفِ اعضا (`staff-codes.redeem`) — دو کدِ جدا برای یک دکان، و یک حساب با کدِ دو دکان: «بشمار، بعد بنویس» ِ
 *      بی قفل ⇒ عضوِ بیش از سقف / عضوِ دو دکان. حالا قفلِ حساب ⇐ قفلِ دکان.
 *   ۲) کدِ اشتراکِ آفلاین بی ردیف (دیتابیسِ بازگردانده‌شده، `shop-offline-codes.redeem`) — دو آوردنِ هم‌زمان هر دو
 *      ردیف می‌ساختند ⇒ `23505` (۵۰۰). حالا `ON CONFLICT DO NOTHING` و قفلِ همان ردیف.
 *   ۳) تسویهٔ پیک (`delivery-settle.settle`) مأموریت ⇐ سفارش قفل می‌کرد و رویدادِ دوبارهٔ پیک سفارش ⇐ مأموریت ⇒
 *      بن‌بست (`40P01`). حالا سفارش ⇐ مأموریت (همان `lockOrderOf` ِ دورِ نخست).
 *   ۴) «باطل کردنِ این دستگاه» / «خروج از همه» ِ صاحب میانِ سنجشِ راز و نوشتنِ نشستِ ورودِ دوبارهٔ شاگرد ⇒ نشستِ
 *      زنده روی دستگاهِ باطل. حالا راز پس از نوشتنِ نشست دوباره سنجیده می‌شود.
 *   ردِ گمان (همین روش، بی تغییرِ کد): نشانیِ ویترین، کارتِ عکس در برابرِ عکسِ تازه، بازپرداختِ سفارش.
 *
 * ⛔ دیده و رفع‌نشده — کدِ مشترک با پمپ (دست نمی‌خورد؛ فقط سند):
 *   • `lib/offline-codes.js:430` (`redeem` ِ کدِ آفلاینِ پمپ، `POST /api/pump/device/offline-code`) همان رقابتِ ۲ است:
 *     `SELECT … FOR UPDATE` روی ردیفِ نبوده و سپس `INSERT` ِ ساده ⇒ دو آوردنِ هم‌زمانِ کدی که ردیفش نیست ⇒ `23505` روی
 *     `pump_offline_codes.serial` (۵۰۰). بازتولید: همان آزمونِ ۲ با `pump_offline_codes` و دو پمپ. رفع: همان
 *     `ON CONFLICT (serial) DO NOTHING` و دوباره `SELECT … FOR UPDATE`.
 *   • `lib/discounts.js:123` (`quote`) سقفِ `max_uses` و «یک بار برای هر مشتری» را بی قفل می‌خواند و `:173` (`useCode`)
 *     بی سنجشِ دوباره `uses = uses + 1` می‌نویسد ⇒ دو صدورِ هم‌زمانِ اشتراک با کدِ `max_uses = 1`
 *     (`POST /api/admin/subscriptions` با `discountCode`، یا `admin-pump.js:279/305`) هر دو تخفیف می‌گیرند و `uses = 2`.
 *     رفع (برای صاحبِ کدِ مشترک): `UPDATE discount_codes SET uses = uses + 1 WHERE id=$1 AND (max_uses IS NULL OR uses < max_uses)
 *     RETURNING` درونِ همان تراکنشِ صدور، و قفلِ «کد + مشتری» برای `once_per_customer`.
 */
process.env.PAYMENTS_PROVIDER = 'fake';
process.env.PAYMENTS_FAKE_SECRET = 'test-only-fake-secret-pg-concurrency-2';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const F = require('./image-fixtures');
const db = require('../src/db');
const { query, one, many, newId, now } = db;
const { resetAll } = require('../src/middleware/ratelimit');
const staffCodes = require('../src/lib/staff-codes');
const entitlement = require('../src/lib/entitlement');
const offline = require('../src/lib/shop-offline-codes');
const orders = require('../src/lib/orders');
const deviceKeys = require('../src/lib/staff-device-keys');

let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;
const pause = (ms) => new Promise((r) => setTimeout(r, ms));
const PG = !db.isPglite();

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

async function newShop(label) {
  const u = await h.newUser(`صاحبِ ${label}`, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, token: u.accessToken, shopId: made.body.shop.id };
}

/** دکانِ تازه با اشتراکِ چندکاربره */
async function paidShop(label, maxMembers = 50) {
  const o = await newShop(label);
  const g = await h.post('/api/admin/subscriptions', { shopId: o.shopId, plan: 'm1', days: 30 }, { token: admin });
  assert.ok(g.status < 300, JSON.stringify(g.body));
  await query('UPDATE shops SET max_members=$2 WHERE id=$1', [o.shopId, maxMembers]);
  return o;
}

/** تراکنشِ پیوستن کُند (دیتابیسِ کُندِ CI): پس از قفلِ کد، پیش از شمردن */
async function slowJoin(shopIds, fn) {
  const orig = entitlement.entitlementOf;
  entitlement.entitlementOf = async (shopId, at, run) => {
    const out = await orig(shopId, at, run);
    if (run && shopIds.includes(shopId)) await pause(300);
    return out;
  };
  try {
    return await fn();
  } finally {
    entitlement.entitlementOf = orig;
  }
}

/* ══════════════════════════════════════════════════════════════════ */

test('سقفِ اعضا: دو کدِ جدا برای آخرین جای خالیِ دکان در یک لحظه ⇒ فقط یکی عضو می‌شود (نه بیش از سقف)', async () => {
  const o = await paidShop('سقف');
  const active = async () => Number((await one(`SELECT COUNT(*)::int AS n FROM shop_members WHERE shop_id=$1 AND status='active'`, [o.shopId])).n);
  await query('UPDATE shops SET max_members=$2 WHERE id=$1', [o.shopId, (await active()) + 1]);
  const a = await staffCodes.create(o.shopId, o.user.id, { role: 'staff', maxUses: 1 });
  const b = await staffCodes.create(o.shopId, o.user.id, { role: 'staff', maxUses: 1 });
  const join = (code, uid) => h.post('/api/auth/staff', { code, name: `شاگرد ${uid}`, device: { uid, name: 'گوشی', platform: 'android' } });
  const rs = await slowJoin([o.shopId], () => Promise.all([join(a.code, `cap-${uniq()}`), join(b.code, `cap-${uniq()}`)]));
  const got = rs.map((r) => (r.status === 201 ? 'ok' : `${r.status} ${r.body.error && r.body.error.code}`)).sort();
  assert.deepEqual(got, ['403 shop_full', 'ok'], JSON.stringify(rs.map((r) => r.body)));
  const max = Number((await one('SELECT max_members FROM shops WHERE id=$1', [o.shopId])).max_members);
  assert.equal(await active(), max);
});

test('عضوِ یک دکان: یک حساب با کدِ دو دکان در یک لحظه ⇒ عضوِ یکی (نه هر دو)', async () => {
  const [x, y] = [await paidShop('اول'), await paidShop('دوم')];
  const u = await h.newUser('شاگردِ دو کد', 'shop');
  const cx = await staffCodes.create(x.shopId, x.user.id, { role: 'staff', maxUses: 1 });
  const cy = await staffCodes.create(y.shopId, y.user.id, { role: 'staff', maxUses: 1 });
  const join = (code) => h.post('/api/shop/join', { code }, { token: u.accessToken });
  const rs = await slowJoin([x.shopId, y.shopId], () => Promise.all([join(cx.code), join(cy.code)]));
  const got = rs.map((r) => (r.status < 300 ? 'ok' : `${r.status} ${r.body.error && r.body.error.code}`)).sort();
  assert.deepEqual(got, ['409 already_member', 'ok'], JSON.stringify(rs.map((r) => r.body)));
  const n = Number((await one(`SELECT COUNT(*)::int AS n FROM shop_members WHERE user_id=$1 AND status='active'`, [u.user.id])).n);
  assert.equal(n, 1);
});

/* ══════════════════════════════════════════════════════════════════ */

const PC_A = offline.computerOfFingerprint(`win-${'a'.repeat(32)}`);

test('کدِ آفلاینِ بی ردیف (دیتابیسِ بازگردانده‌شده): دو دکان در یک لحظه ⇒ یکی می‌گیرد، دیگری «جای دیگر» — نه 23505', async () => {
  const made = await h.post('/api/admin/shop-offline-codes', { plan: 'sub', computer: PC_A, days: 30 }, { token: admin });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const code = made.body.code;
  const { serial } = await offline.verify(code);
  await query('DELETE FROM shop_offline_codes WHERE serial=$1', [serial]);
  const [x, y] = [await newShop('آفلاینِ اول'), await newShop('آفلاینِ دوم')];   //  بی اشتراک ⇒ «گرفت»، نه «پوشیده»
  const redeem = (o) => h.post('/api/vip/offline-code', { code, computer: PC_A }, { token: o.token });

  let rs;
  if (PG) {
    //  هر دو «نیست» دیده‌اند و پشتِ یک ثبتِ نیمه‌کارهٔ همان `serial` ایستاده‌اند؛ آن کنار می‌رود ⇒ هر دو با هم می‌نویسند
    let release;
    const gate = new Promise((r) => { release = r; });
    const blocker = db.tx(async (c) => {
      await c.query(
        `INSERT INTO shop_offline_codes (id, serial, plan, computer, issued_at, ends_at, created_by, created_at)
         VALUES ($1,$2,'sub','',0,0,'test',0)`, [newId('sfc'), serial]);
      await gate;
      throw Object.assign(new Error('rollback'), { rollback: true });
    }).catch((e) => { if (!e.rollback) throw e; });
    const pending = Promise.all([redeem(x), redeem(y)]);
    for (let i = 0; i < 100; i++) {
      const w = await one(`SELECT COUNT(*)::int AS n FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock'`);
      if (Number(w.n) >= 2) break;
      await pause(20);
    }
    release();
    await blocker;
    rs = await pending;
  } else {
    rs = await Promise.all([redeem(x), redeem(y)]);
  }
  const got = rs.map((r) => (r.status === 200 ? r.body.status : `${r.status} ${r.body.error && r.body.error.code}`)).sort();
  assert.deepEqual(got, ['409 code_used_elsewhere', 'granted'], JSON.stringify(rs.map((r) => r.body)));
  const rows = await many('SELECT redeemed_shop_id FROM shop_offline_codes WHERE serial=$1', [serial]);
  assert.equal(rows.length, 1);
  assert.ok([x.shopId, y.shopId].includes(rows[0].redeemed_shop_id));
});

/* ══════════════════════════════════════════════════════════════════ */

let evN = 0;
const evt = (id, type, extra = {}) => ({ eventId: `ev2-${Date.now()}-${++evN}`, assignmentId: id, type, deviceTime: Date.now(), ...extra });

test('ترتیبِ قفل: تسویهٔ پیک و «تحویل شد» ِ دوبارهٔ همان مأموریت (صفِ بی‌اینترنتِ گوشی) در یک لحظه ⇒ هر دو می‌نشینند، نه بن‌بست (40P01)', async () => {
  const o = await paidShop('تسویه');
  for (const k of ['shop.orders', 'shop.delivery']) await setFlag(o.shopId, k, true);
  const inv = await h.post('/api/shop/invite', { role: 'courier' }, { token: o.token });
  assert.equal(inv.status, 201, JSON.stringify(inv.body));
  const cl = await h.post('/api/auth/staff', { code: inv.body.code, name: 'کریم', device: { uid: `phone-${uniq()}`, name: 'گوشیِ پیک', platform: 'android' } });
  assert.equal(cl.status, 201, JSON.stringify(cl.body));
  const c = { token: cl.body.accessToken, id: (await h.get('/api/courier/me', { token: cl.body.accessToken })).body.courier.id };
  const says = (events) => h.post('/api/courier/events', { deviceId: 'pg-concurrency-2', events }, { token: c.token });
  const r = await h.post('/api/shop/orders', {
    source: 'phone', fulfillment: 'delivery', paymentMethod: 'cod',
    customer: { name: 'احمد', phone: '0791234567' }, address: { landmark: 'پشتِ مسجد' },
    lines: [{ name: 'برنج', qtyMilli: 2000, unitPriceMinor: 60_000 }], deliveryFeeMinor: 4_000,
  }, { token: o.token, headers: { 'Idempotency-Key': `order-${uniq()}` } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const order = r.body.order;
  for (const to of ['accepted', 'picking', 'packed']) {
    const t = await h.post(`/api/shop/orders/${order.id}/transition`, { to }, { token: o.token });
    assert.equal(t.status, 200, JSON.stringify(t.body));
  }
  const a = await h.post(`/api/shop/delivery/orders/${order.id}/assign`, { mode: 'shop_courier', courierId: c.id }, { token: o.token });
  assert.equal(a.status, 201, JSON.stringify(a.body));
  const id = a.body.assignment.id;
  const went = await says([evt(id, 'assigned'), evt(id, 'picked_up'), evt(id, 'delivered', { cashCollectedMinor: 124_000 })]);
  assert.equal(went.status, 200, JSON.stringify(went.body));
  for (const x of went.body.results) assert.equal(x.status, 'applied', JSON.stringify(x));

  //  تسویه کُند (دیتابیسِ کُندِ CI): پس از قفلِ مأموریت‌ها، پیش از گذارِ نقدِ سفارش
  const orig = orders.codIn;
  orders.codIn = async (cl2, shopId, ...rest) => {
    if (shopId === o.shopId) await pause(500);
    return orig(cl2, shopId, ...rest);
  };
  try {
    const settled = h.post(`/api/shop/delivery/couriers/${c.id}/settlements`,
      { assignmentIds: [id], handedOverMinor: 124_000, receivedMinor: 124_000 },
      { token: o.token, headers: { 'Idempotency-Key': `settle-${uniq()}` } });
    await pause(150);   //  تسویه قفل‌هایش را گرفته است
    const [s, d] = await Promise.all([settled, says([evt(id, 'delivered', { cashCollectedMinor: 124_000 })])]);
    assert.equal(s.status, 201, JSON.stringify(s.body));
    assert.equal(d.status, 200, JSON.stringify(d.body));
    assert.equal(d.body.results[0].status, 'unchanged', JSON.stringify(d.body.results[0]));
  } finally {
    orders.codIn = orig;
  }
  assert.equal((await one('SELECT cod_status FROM online_orders WHERE id=$1', [order.id])).cod_status, 'settled_to_shop');
  assert.ok((await one('SELECT settlement_id FROM delivery_assignments WHERE id=$1', [id])).settlement_id);
});

/* ══════════════════════════════════════════════════════════════════ */

/** شاگردِ برنامهٔ تازه با کدِ یک‌بارِ خودش؛ ورودِ دوباره با کدِ ثابت و رازش */
async function staffWithKey(o, name) {
  const made = await staffCodes.create(o.shopId, o.user.id, { role: 'staff', maxUses: 1 });
  const uid = `key-${uniq()}`;
  const r = await h.post('/api/auth/staff', { code: made.code, name, device: { uid, name: `گوشیِ ${name}`, platform: 'android', keyVersion: 1 } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.equal(typeof r.body.deviceKey, 'string');
  const member = await one('SELECT id FROM shop_members WHERE shop_id=$1 AND user_id=$2', [o.shopId, r.body.user.id]);
  const key = await one('SELECT id FROM staff_device_keys WHERE user_id=$1 AND revoked_at IS NULL', [r.body.user.id]);
  return { uid, key: r.body.deviceKey, keyId: key.id, memberId: member.id, userId: r.body.user.id };
}

/** ورودِ دوباره که میانِ سنجشِ راز و نوشتنِ نشست کُند است؛ در همان پنجره صاحب `revoke` را می‌زند */
async function reloginDuring(o, s, revoke) {
  const standing = (await h.get('/api/shop/staff-code', { token: o.token })).body.code;
  const orig = deviceKeys.verify;
  let first = true;
  deviceKeys.verify = async (args) => {
    const out = await orig(args);
    if (first && args && args.deviceUid === s.uid) { first = false; await pause(400); }
    return out;
  };
  try {
    const login = h.post('/api/auth/staff', { code: standing, device: { uid: s.uid, key: s.key, keyVersion: 1 } });
    await pause(150);   //  راز سنجیده شد؛ نشست هنوز نوشته نشده
    const rv = await revoke();
    assert.equal(rv.status, 200, JSON.stringify(rv.body));
    return await login;
  } finally {
    deviceKeys.verify = orig;
  }
}

/** پس از باطل شدن: یا ورود رد شد، یا نشستی که داد مرده است — هرگز نشستِ زنده روی دستگاهِ باطل */
async function noLiveSession(r, s) {
  if (r.status === 201) {
    const me = await h.get('/api/shop/me', { token: r.body.accessToken });
    assert.equal(me.status, 401, `نشستِ زنده روی دستگاهِ باطل: ${JSON.stringify(me.body)}`);
  } else {
    assert.equal(r.status, 403, JSON.stringify(r.body));
    assert.equal(r.body.error.code, 'staff_device_unverified');
  }
  const live = await one(`SELECT COUNT(*)::int AS n FROM tokens WHERE subject_id=$1 AND revoked_at IS NULL`, [s.userId]);
  assert.equal(Number(live.n), 0, 'هیچ نشستِ زنده‌ای نماند');
}

test('رازِ دستگاه: «باطل کردنِ این دستگاه» میانِ سنجشِ راز و نوشتنِ نشستِ ورودِ دوباره ⇒ نشستِ زنده نمی‌ماند', async () => {
  const o = await paidShop('باطلِ دستگاه');
  const s = await staffWithKey(o, 'سمیر');
  const r = await reloginDuring(o, s, () => h.post(`/api/shop/staff-keys/${s.keyId}/revoke`, {}, { token: o.token }));
  await noLiveSession(r, s);
});

test('رازِ دستگاه: «خروج از همه» ِ صاحب برای عضو میانِ سنجشِ راز و نوشتنِ نشست ⇒ نشستِ زنده نمی‌ماند', async () => {
  const o = await paidShop('خروجِ عضو');
  const s = await staffWithKey(o, 'شبنم');
  const r = await reloginDuring(o, s, () => h.post(`/api/shop/staff-keys/members/${s.memberId}/logout-all`, {}, { token: o.token }));
  await noLiveSession(r, s);
});

test('رازِ دستگاه (رفتارِ تک‌درخواست): ورودِ دوباره با رازِ زنده همان حساب و نشستِ زنده؛ رازِ تازه نمی‌آید', async () => {
  const o = await paidShop('ورودِ دوباره');
  const s = await staffWithKey(o, 'نسیم');
  const standing = (await h.get('/api/shop/staff-code', { token: o.token })).body.code;
  const r = await h.post('/api/auth/staff', { code: standing, device: { uid: s.uid, key: s.key, keyVersion: 1 } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.equal(r.body.user.id, s.userId);
  assert.equal(r.body.deviceKey, undefined);
  assert.equal((await h.get('/api/shop/me', { token: r.body.accessToken })).status, 200);
});

/* ══════════════════════════════════════════════════════════════════
   ردِ گمان — همان روش، بی تغییرِ کد
   ══════════════════════════════════════════════════════════════════ */

test('نشانیِ ویترین (ردِ گمان): چهار دکان یک نشانی را در یک لحظه می‌خواهند ⇒ یکی ۲۰۰، بقیه ۴۰۹ slug_taken، نه ۵۰۰', async () => {
  const shops = [];
  for (const i of [1, 2, 3, 4]) {
    const o = await paidShop(`نشانی ${i}`);
    for (const k of ['shop.orders', 'shop.storefront']) await setFlag(o.shopId, k, true);
    shops.push(o);
  }
  const slug = `same-${uniq()}`;
  const rs = await Promise.all(shops.map((o) => h.put('/api/shop/storefront', { slug, enabled: true, title: 'ویترین' }, { token: o.token })));
  const got = rs.map((r) => (r.status === 200 ? 'ok' : `${r.status} ${r.body.error && r.body.error.code}`)).sort();
  assert.deepEqual(got, ['409 slug_taken', '409 slug_taken', '409 slug_taken', 'ok'], JSON.stringify(rs.map((r) => r.body)));
  assert.equal(Number((await one('SELECT COUNT(*)::int AS n FROM storefronts WHERE slug=$1', [slug])).n), 1);
});

test('کارتِ عکس (ردِ گمان): کارتِ عکسِ پیشین و عکسِ تازه در یک لحظه ⇒ هرگز کارتِ عکسِ دیگر روی عکسِ امروز', async () => {
  const o = await paidShop('کارت');
  const SYNC = { 'X-App': 'shop', 'X-Device': 'dev-pgc2' };
  const p = await h.api('POST', '/api/sync/v1/push', { token: o.token, headers: SYNC, body: { device_id: 'dev-pgc2', schema_version: 2, ops: [
    { op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: 'rice', type: 'insert', fields: { name: 'برنج', unit: 'کیلو', salePrice: 120 } },
  ] } });
  assert.equal(p.status, 200, JSON.stringify(p.body));
  const b64 = (b) => b.toString('base64');
  const put = (data) => h.api('PUT', '/api/sync/v1/photos/rice', { token: o.token, headers: SYNC, body: { data: b64(data) } });
  const thumb = (data, of) => h.api('PUT', '/api/sync/v1/photos/rice/thumb', { token: o.token, headers: SYNC, body: { data: b64(data), of } });
  let cur = await put(F.jpeg(800, 600, { seed: 1 }));
  assert.equal(cur.status, 200, JSON.stringify(cur.body));
  for (let i = 0; i < 6; i++) {
    const was = cur.body.hash;
    const [t, n] = await Promise.all([thumb(F.jpeg(320, 240, { seed: 100 + i }), was), put(F.jpeg(800, 600, { seed: 2 + i }))]);
    assert.equal(n.status, 200, JSON.stringify(n.body));
    assert.ok([200, 409].includes(t.status), JSON.stringify(t.body));
    if (t.status === 409) assert.equal(t.body.error.code, 'thumb_stale');
    const photo = await one(`SELECT hash FROM product_photos WHERE account_id=$1 AND product_id='rice'`, [o.shopId]);
    const th = await one(`SELECT of_hash FROM product_photo_thumbs WHERE account_id=$1 AND product_id='rice'`, [o.shopId]);
    assert.equal(photo.hash, n.body.hash, `دور ${i}`);
    assert.ok(!th || th.of_hash === photo.hash, `دور ${i}: کارتِ ${th && th.of_hash} روی عکسِ ${photo.hash}`);
    cur = n;
  }
});

test('بازپرداختِ سفارش (ردِ گمان): چهار بازپرداختِ هم‌زمانِ ۴۰٪ ⇒ دو می‌نشیند، جمع هرگز بیش از سفارش', async () => {
  const o = await paidShop('بازپرداخت');
  await setFlag(o.shopId, 'shop.orders', true);
  await setFlag(o.shopId, require('../src/lib/order-refunds').FLAG_KEY, true);
  const made = await h.post('/api/shop/orders', {
    source: 'phone', fulfillment: 'pickup', paymentMethod: 'cod', customer: { name: 'احمد', phone: '0791234567' },
    lines: [{ name: 'برنج', qtyMilli: 1000, unitPriceMinor: 10_000 }],
  }, { token: o.token, headers: { 'Idempotency-Key': `order-${uniq()}` } });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const id = made.body.order.id;
  for (const to of ['accepted', 'picking', 'packed', 'ready_for_pickup', 'picked_up', 'return_requested']) {
    const t = await h.post(`/api/shop/orders/${id}/transition`, { to }, { token: o.token });
    assert.equal(t.status, 200, `${to}: ${JSON.stringify(t.body)}`);
  }
  const rs = await Promise.all([1, 2, 3, 4].map((i) => h.post(`/api/shop/orders/${id}/refunds`,
    { amountMinor: 4_000, reason: `برگشت ${i}` }, { token: o.token, headers: { 'Idempotency-Key': `refund-${uniq()}` } })));
  const got = rs.map((r) => (r.status < 300 ? 'ok' : `${r.status} ${r.body.error && r.body.error.code}`)).sort();
  assert.deepEqual(got, ['409 refund_exceeds_remaining', '409 refund_exceeds_remaining', 'ok', 'ok'], JSON.stringify(rs.map((r) => r.body)));
  const rows = await many('SELECT seq, amount_minor FROM online_order_refunds WHERE order_id=$1 ORDER BY seq', [id]);
  assert.deepEqual(rows.map((x) => [Number(x.seq), Number(x.amount_minor)]), [[1, 4_000], [2, 4_000]]);
});
