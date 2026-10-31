'use strict';
/**
 * DATA-03 (§۲۹.۸): «crash میانِ نوشتنِ چندجدولی سندِ نیمه‌کاره نسازد» — در سرور.
 *
 * شکست میانِ نوشتن با یک trigger شبیه‌سازی می‌شود: وقتی جدول/عملِ مسلح‌شده نوشته شود، trigger
 * خطا می‌دهد. این همان لحظه‌ای است که فرآیند می‌میرد یا اتصال می‌افتد، چون PostgreSQL تراکنشِ COMMIT‌نشده را
 * در هر دو حالت برمی‌گرداند. پس هر نوشتنی که **بیرون** از تراکنشِ کار رفته باشد (`query` ِ سراسری روی
 * PostgreSQL به استخر می‌رود، نه به `c`) همین‌جا می‌ماند و آزمون سرخ می‌شود.
 *
 * برای هر کار، آخرین نوشتنِ آن مسلح می‌شود و پس از شکست هیچ ردی از گام‌های پیشین نباید بماند.
 * تکرارِ همان درخواست (همان کلیدِ یکتایی) پس از رفعِ خطا هم باید دقیقاً یک بار اثر کند:
 *   ۱) ساختِ سفارش: online_orders + lines + events ⇐ شکست در events.
 *   ۲) گذارِ سفارش: UPDATE online_orders + events ⇐ شکست در events.
 *   ۳) بازپرداخت: online_order_refunds + events + UPDATE online_orders ⇐ شکست در UPDATE.
 *   ۴) اعطای اشتراک با کلیدِ یکتایی: subscriptions + subscription_history + idempotency_keys ⇐ شکست در
 *      idempotency_keys. تا ۲.۱۴.۴ `grantLocked` با `one`/`query` ِ سراسری می‌نوشت و روی PostgreSQL
 *      اشتراک و تاریخچه می‌ماند و تکرار دورهٔ دوم می‌نشاند (روی PGlite همه در یک تراکنش بود).
 *   ۵) شکستِ فقط تاریخچه اعطا را نمی‌بندد (SAVEPOINT، همان قاعدهٔ `logChange`): روی PGlite تا ۲.۱۴.۴
 *      همان شکست تراکنش را «aborted» می‌کرد و کلِ اعطا ۵۰۰ می‌شد.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');

const DAY = 24 * 60 * 60 * 1000;
const ARMED = ['online_orders', 'online_order_events', 'online_order_refunds', 'subscriptions',
  'subscription_history', 'idempotency_keys'];
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
  await query('CREATE TABLE test_crash_at (tbl text NOT NULL, op text NOT NULL)');
  await query(`CREATE FUNCTION test_crash() RETURNS trigger LANGUAGE plpgsql AS $f$
    BEGIN
      IF EXISTS (SELECT 1 FROM test_crash_at WHERE tbl = TG_TABLE_NAME AND op = TG_OP) THEN
        RAISE EXCEPTION 'شبیه‌سازیِ شکست میانِ نوشتن: % %', TG_OP, TG_TABLE_NAME;
      END IF;
      RETURN NEW;
    END $f$`);
  for (const t of ARMED) {
    await query(`CREATE TRIGGER test_crash_${t} BEFORE INSERT OR UPDATE ON ${t} FOR EACH ROW EXECUTE FUNCTION test_crash()`);
  }
});
test.after(async () => { await h.stop(); });

/** کار را با شکست در (جدول، عمل) اجرا می‌کند؛ پس از آن، سلاح همیشه برداشته می‌شود. */
async function crashing(tbl, op, fn) {
  await query('INSERT INTO test_crash_at (tbl, op) VALUES ($1,$2)', [tbl, op]);
  try { return await fn(); } finally { await query('DELETE FROM test_crash_at'); }
}

async function shopOwner(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { token: u.accessToken, shopId: made.body.shop.id };
}
async function setFlag(scope, key) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value: true }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

const ORDER = {
  source: 'phone', fulfillment: 'pickup', paymentMethod: 'cod', customer: { name: 'احمد', phone: '0791234567' },
  lines: [{ name: 'برنج', unit: 'کیلو', qtyMilli: 2000, unitPriceMinor: 2500 }],
};
const createOrder = (s, key) => h.post('/api/shop/orders', ORDER, { token: s.token, headers: { 'Idempotency-Key': key } });
const move = (s, id, to) => h.post(`/api/shop/orders/${id}/transition`, { to }, { token: s.token });
const counts = async (shopId) => Object.fromEntries(await Promise.all(
  ['online_orders', 'online_order_lines', 'online_order_events', 'online_order_refunds'].map(async (t) =>
    [t, (await one(`SELECT COUNT(*)::int n FROM ${t} WHERE shop_id=$1`, [shopId])).n])));

test('DATA-03 ۱) شکست در رویدادِ ساختِ سفارش ⇒ نه سفارش، نه ردیف؛ تکرارِ همان کلید ⇒ یک سفارش', async () => {
  const s = await shopOwner('ساخت');
  await setFlag(s.shopId, 'shop.orders');
  const key = `crash-create-${Date.now()}`;
  const bad = await crashing('online_order_events', 'INSERT', () => createOrder(s, key));
  assert.equal(bad.status, 500, JSON.stringify(bad.body));
  assert.deepEqual(await counts(s.shopId),
    { online_orders: 0, online_order_lines: 0, online_order_events: 0, online_order_refunds: 0 });

  const ok = await createOrder(s, key);
  assert.equal(ok.status, 201, JSON.stringify(ok.body));
  assert.equal((await createOrder(s, key)).status, 200, 'تکرارِ همان کلید پس از موفقیت همان سفارش است');
  assert.deepEqual(await counts(s.shopId),
    { online_orders: 1, online_order_lines: 1, online_order_events: 1, online_order_refunds: 0 });
});

test('DATA-03 ۲) شکست در رویدادِ گذار ⇒ وضعیت و نسخهٔ سفارش دست‌نخورده', async () => {
  const s = await shopOwner('گذار');
  await setFlag(s.shopId, 'shop.orders');
  const o = (await createOrder(s, `crash-move-${Date.now()}`)).body.order;
  const before = await one('SELECT status, version, updated_at FROM online_orders WHERE id=$1', [o.id]);
  const bad = await crashing('online_order_events', 'INSERT', () => move(s, o.id, 'accepted'));
  assert.equal(bad.status, 500, JSON.stringify(bad.body));
  assert.deepEqual(await one('SELECT status, version, updated_at FROM online_orders WHERE id=$1', [o.id]), before);
  assert.equal((await counts(s.shopId)).online_order_events, 1, 'فقط رویدادِ «ساخته شد»');

  assert.equal((await move(s, o.id, 'accepted')).status, 200);
  assert.equal((await one('SELECT status FROM online_orders WHERE id=$1', [o.id])).status, 'accepted');
});

test('DATA-03 ۳) شکست در آخرین گامِ بازپرداخت ⇒ نه ردیفِ دفتر، نه رویداد؛ تکرارِ همان کلید ⇒ یک ردیف', async () => {
  const s = await shopOwner('بازپرداخت');
  await setFlag(s.shopId, 'shop.orders');
  await setFlag(s.shopId, 'shop.orders.refunds');
  const o = (await createOrder(s, `crash-refund-${Date.now()}`)).body.order;
  for (const to of ['accepted', 'picking', 'packed', 'ready_for_pickup', 'picked_up', 'return_requested']) {
    assert.equal((await move(s, o.id, to)).status, 200, to);
  }
  const before = await counts(s.shopId);
  const ver = (await one('SELECT version FROM online_orders WHERE id=$1', [o.id])).version;
  const key = `crash-refund-key-${Date.now()}`;
  const refund = () => h.post(`/api/shop/orders/${o.id}/refunds`, { amountMinor: 1000, reason: 'خراب' },
    { token: s.token, headers: { 'Idempotency-Key': key } });

  const bad = await crashing('online_orders', 'UPDATE', refund);
  assert.equal(bad.status, 500, JSON.stringify(bad.body));
  assert.deepEqual(await counts(s.shopId), before, 'هیچ ردیفِ دفتر یا رویدادی نماند');
  assert.equal((await one('SELECT version FROM online_orders WHERE id=$1', [o.id])).version, ver);

  assert.equal((await refund()).status, 201);
  assert.equal((await refund()).status, 200, 'تکرارِ همان کلید');
  assert.equal((await counts(s.shopId)).online_order_refunds, 1);
});

test('DATA-03 ۴) شکست در ثبتِ کلیدِ یکتاییِ اعطا ⇒ اشتراک و تاریخچه دست‌نخورده؛ تکرار ⇒ یک دوره', async () => {
  const s = await shopOwner('اعطا');
  const grant = (key) => h.api('POST', '/api/admin/subscriptions', {
    body: { shopId: s.shopId, plan: 'custom', days: 30 }, token: admin, headers: { 'Idempotency-Key': key },
  });
  //  یک اشتراکِ زنده تا تمدید (UPDATE) هم سنجیده شود
  const first = await grant(`crash-grant-0-${Date.now()}`);
  assert.equal(first.status, 201, JSON.stringify(first.body));
  const row = () => one('SELECT ends_at, updated_at FROM subscriptions WHERE shop_id=$1', [s.shopId]);
  const hist = async () => (await one('SELECT COUNT(*)::int n FROM subscription_history WHERE shop_id=$1', [s.shopId])).n;
  const before = await row();
  const histBefore = await hist();

  const key = `crash-grant-1-${Date.now()}`;
  const bad = await crashing('idempotency_keys', 'INSERT', () => grant(key));
  assert.equal(bad.status, 500, JSON.stringify(bad.body));
  assert.deepEqual(await row(), before, 'تمدیدِ نیمه‌کاره نماند');
  assert.equal(await hist(), histBefore, 'تاریخچهٔ تمدیدی که نشد نماند');
  assert.equal((await one('SELECT COUNT(*)::int n FROM subscriptions WHERE shop_id=$1', [s.shopId])).n, 1);

  const ok = await grant(key);
  assert.equal(ok.status, 201, JSON.stringify(ok.body));
  assert.equal((await grant(key)).status, 201, 'تکرارِ همان کلید پاسخِ اول را برمی‌گرداند');
  assert.equal(Number((await row()).ends_at), Number(before.ends_at) + 30 * DAY, 'دقیقاً یک دورهٔ دیگر');
  assert.equal(await hist(), histBefore + 1);
});

test('DATA-03 ۵) شکستِ فقط تاریخچه جلوی اعطا را نمی‌گیرد (SAVEPOINT) — همان قاعدهٔ logChange، روی هر دو راه‌انداز', async () => {
  const s = await shopOwner('تاریخچه');
  const bad = await crashing('subscription_history', 'INSERT', () => h.api('POST', '/api/admin/subscriptions', {
    body: { shopId: s.shopId, plan: 'custom', days: 30 }, token: admin, headers: { 'Idempotency-Key': `crash-hist-${Date.now()}` },
  }));
  assert.equal(bad.status, 201, JSON.stringify(bad.body));
  const sub = await one('SELECT * FROM subscriptions WHERE shop_id=$1', [s.shopId]);
  assert.ok(sub && sub.status === 'active', 'اشتراک نشست');
  assert.equal((await one('SELECT COUNT(*)::int n FROM subscription_history WHERE shop_id=$1', [s.shopId])).n, 0);
  assert.equal((await one(`SELECT COUNT(*)::int n FROM idempotency_keys WHERE result_id=$1`, [sub.id])).n, 1,
    'کلیدِ یکتایی در همان تراکنش ثبت شد');
});
