'use strict';
/**
 * S79 — کارگرِ «سفارشِ گیرکرده» (`lib/order-stuck-alert.js`، پرچمِ خاموشِ `shop.orders.stuck_alert`):
 * سفارشِ پس از قبول که با آستانه‌های پنل (`console.delivery_thresholds`) گیر کرده هشدار می‌گیرد — رویدادِ
 * `note`، سابقه و پوش — و **هیچ** گذاری نمی‌خورد: وضعیت، `updated_at` و مأموریت همان می‌مانند.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const stuck = require('../src/lib/order-stuck-alert');

const MIN = 60_000;
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
  assert.ok(admin);
});
test.after(async () => { stuck.stop(); await h.stop(); });

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

async function shop(label, { on = true } = {}) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { ...u, token: u.accessToken, shopId: made.body.shop.id };
  await setFlag(o.shopId, 'shop.orders', true);
  if (on) await setFlag(o.shopId, stuck.FLAG_KEY, true);
  return o;
}

/** سفارشِ دستیِ واقعی؛ وضعیت، روش و زمانِ آخرین تغییر مستقیم روی ردیف گذاشته می‌شود تا مرزها دقیق باشند. */
async function order(s, { status, fulfillment = 'delivery', updatedAt, test: isTest = false }) {
  const r = await h.post('/api/shop/orders', {
    source: 'phone', fulfillment: 'pickup', customer: { name: 'نهان‌نام' },
    lines: [{ name: 'برنج', qtyMilli: 1000, unitPriceMinor: 50000 }], ...(isTest ? { test: true } : {}),
  }, { token: s.token, headers: { 'Idempotency-Key': `st-${uniq()}-key` } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const id = r.body.order.id;
  await query('UPDATE online_orders SET status=$2, fulfillment=$3, updated_at=$4 WHERE id=$1', [id, status, fulfillment, updatedAt]);
  return id;
}

async function mission(s, orderId, status, updatedAt) {
  await query(
    `INSERT INTO delivery_assignments (id, shop_id, order_id, attempt_no, mode, status, created_at, updated_at)
     VALUES ($1,$2,$3,1,'shop_courier',$4,$5,$5)`, [newId('da'), s.shopId, orderId, status, updatedAt]
  );
}

const alerts = (shopId) => many(
  `SELECT order_id, from_status, to_status, actor_type, detail FROM online_order_events
    WHERE shop_id=$1 AND kind='note' AND detail->>'type'='stuck_alert' ORDER BY order_id, seq`, [shopId]);
const snapshot = async (id) => ({
  order: await one('SELECT status, updated_at, fulfillment FROM online_orders WHERE id=$1', [id]),
  missions: await many('SELECT status, updated_at, version FROM delivery_assignments WHERE order_id=$1 ORDER BY attempt_no', [id]),
  transitions: Number((await one(`SELECT COUNT(*)::int AS n FROM online_order_events WHERE order_id=$1 AND kind <> 'note'`, [id])).n),
});

test('S79: پرچم خاموش ⇒ هیچ هشدار، سابقه یا تغییری', async () => {
  const s = await shop('خاموش', { on: false });
  const t = Date.now();
  const id = await order(s, { status: 'packed', updatedAt: t - 90 * MIN });
  const before = await snapshot(id);
  assert.equal(await stuck.sweep(t), 0);
  assert.deepEqual(await alerts(s.shopId), []);
  assert.equal((await one(`SELECT COUNT(*)::int n FROM audit_logs WHERE shop_id=$1 AND action='order.stuck_alert'`, [s.shopId])).n, 0);
  assert.deepEqual(await snapshot(id), before);
});

test('S79: گیر پس از قبول ⇒ یک هشدار برای هر کد و دکان، پوش به اعضا؛ شناسایی وضعیت و مأموریت را عوض نمی‌کند', async () => {
  const s = await shop('گیر');
  await setFlag(s.shopId, 'shop.order_notify', true);
  const push = require('../src/lib/push');
  const realSend = push.sendTo;
  const pushed = [];
  push.sendTo = async (target, msg) => { pushed.push({ target, msg }); return { sent: 1, total: 1 }; };
  try {
    const t = Date.now();
    const packed = await order(s, { status: 'packed', updatedAt: t - 31 * MIN });               // نسپرده > ۳۰ دقیقه
    const fresh = await order(s, { status: 'packed', updatedAt: t - 29 * MIN });                // هنوز در مهلت
    const pickupPacked = await order(s, { status: 'packed', fulfillment: 'pickup', updatedAt: t - 90 * MIN });   // دریافت از دکان: پیک ندارد
    const assigned = await order(s, { status: 'packed', updatedAt: t - 40 * MIN });
    await mission(s, assigned, 'assigned', t - 45 * MIN);                                      // پیک برنداشته > ۳۰
    const failed = await order(s, { status: 'delivery_failed', updatedAt: t - 241 * MIN });     // ناموفقِ بی تصمیم > ۲۴۰
    const isNew = await order(s, { status: 'new', updatedAt: t - 90 * MIN });                  // «تازه» راهِ خودش را دارد
    const trial = await order(s, { status: 'packed', updatedAt: t - 90 * MIN, test: true });
    const done = await order(s, { status: 'delivered', updatedAt: t - 900 * MIN });
    const ids = [packed, fresh, pickupPacked, assigned, failed, isNew, trial, done];
    const before = await Promise.all(ids.map(snapshot));

    assert.equal(await stuck.sweep(t), 3);
    const got = (await alerts(s.shopId)).map((e) => [e.order_id, e.detail.code]);
    assert.deepEqual(got.sort(), [[packed, 'dispatch_overdue'], [assigned, 'pickup_overdue'], [failed, 'failed_unresolved']].sort());
    for (const e of await alerts(s.shopId)) {
      assert.equal(e.actor_type, 'system');
      assert.equal(e.from_status, e.to_status, 'رویدادِ یادداشت گذار نیست');
    }
    //  ⛔ هیچ چیز عوض نشد: وضعیت، زمانِ آخرین تغییر، مأموریت و شمارِ گذارها
    assert.deepEqual(await Promise.all(ids.map(snapshot)), before);

    //  دورِ دوم ⇒ هشدارِ دوم نیست؛ «fresh» از مهلت می‌گذرد و دو جاروبِ هم‌زمان فقط یک هشدار می‌سازند
    assert.equal(await stuck.sweep(t + MIN), 0);
    const both = await Promise.all([stuck.sweep(t + 2 * MIN), stuck.sweep(t + 2 * MIN)]);
    assert.equal(both[0] + both[1], 1);
    assert.deepEqual((await alerts(s.shopId)).filter((e) => e.order_id === fresh).map((e) => e.detail.code), ['dispatch_overdue']);
    assert.equal((await alerts(s.shopId)).length, 4);
    assert.equal((await one(`SELECT COUNT(*)::int n FROM audit_logs WHERE shop_id=$1 AND action='order.stuck_alert'`, [s.shopId])).n, 4);
    assert.deepEqual(await Promise.all(ids.map(snapshot)), before);

    assert.equal(pushed.length, 4);
    for (const p of pushed) {
      assert.equal(p.target.shopId, s.shopId);
      assert.equal(p.msg.data.type, 'order_stuck_alert');
      assert.ok(stuck.TEXT[p.msg.data.code] === p.msg.body, p.msg.body);
      //  فقط همین کلیدها: نه نامِ مشتری، نه شماره، نه نشانی
      assert.deepEqual(Object.keys(p.msg.data).sort(), ['code', 'orderId', 'orderNo', 'shopId', 'type']);
      assert.ok(!/نهان‌نام|\+93/.test(`${p.msg.title} ${p.msg.body}`), `${p.msg.title} ${p.msg.body}`);
    }
  } finally {
    push.sendTo = realSend;
  }
});

test('S79: پس از پیشرفت و گیرِ تازه ⇒ هشدارِ تازه؛ آستانهٔ پنل خوانده می‌شود؛ دکانِ دیگر جدا', async () => {
  const s = await shop('دوباره');
  const other = await shop('همسایه', { on: false });
  const t = Date.now();
  const id = await order(s, { status: 'packed', updatedAt: t - 31 * MIN });
  const theirs = await order(other, { status: 'packed', updatedAt: t - 31 * MIN });
  assert.equal(await stuck.sweep(t), 1);
  //  سفارش جلو رفت (به پیک سپرده شد) و دوباره در مأموریت گیر کرد
  await mission(s, id, 'requested', t + 5 * MIN);
  assert.equal(await stuck.sweep(t + 10 * MIN), 0, 'هنوز در مهلتِ قبولِ پیک (۱۵ دقیقه)');
  assert.equal(await stuck.sweep(t + 21 * MIN), 1);
  assert.deepEqual((await alerts(s.shopId)).map((e) => e.detail.code), ['dispatch_overdue', 'courier_unaccepted']);
  assert.deepEqual(await alerts(other.shopId), []);
  assert.equal((await one('SELECT status FROM online_orders WHERE id=$1', [theirs])).status, 'packed');

  //  آستانهٔ پنل (۱۲۰ دقیقه) ⇒ سفارشِ ۳۱ دقیقه‌ای دیگر گیر نیست
  const s2 = await shop('آستانه');
  await setFlag('all', 'console.delivery_thresholds', { dispatchMin: 120 });
  try {
    await order(s2, { status: 'packed', updatedAt: t - 31 * MIN });
    assert.equal(await stuck.sweep(t), 0);
    assert.deepEqual(await alerts(s2.shopId), []);
  } finally {
    await query(`DELETE FROM live_config WHERE key='console.delivery_thresholds' AND scope='all'`);
  }
});
