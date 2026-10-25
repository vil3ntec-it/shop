'use strict';
/**
 * ORD-05 — زمان‌سنجِ قبول (lib/order-timeout.js، مهاجرتِ ۱۲۱).
 *
 *   - دکانی که همین لحظه می‌پذیرد ⇒ مهلت = ثبت + ۱۵ دقیقه؛ پیش از آن دست نمی‌خورد،
 *     پس از آن auto_rejected با دلیلِ timeout و بازیگرِ سیستم؛ مشتری در پیگیری می‌بیند.
 *   - تکرارِ کلیدِ یکتایی سفارش را دوباره مُهر نمی‌زند.
 *   - سفارشِ «بعداً» (دکانِ بسته) مُهر نمی‌خورد و هرگز خودکار رد نمی‌شود.
 *   - سفارشی که پیش از مهلت پذیرفته شد دست نمی‌خورد.
 *   - دو جاروبِ هم‌زمان ⇒ هر سفارش یک بار رد و یک رویدادِ وضعیت.
 *   - «با تأخیر» (اتصالِ تأییدنشده) و سفارشِ زمان‌بندی‌شده مُهر نمی‌خورند؛ arm با
 *     ساعتِ ساختِ سفارش می‌سنجد.
 *   - سفارشِ خطادار صف را نمی‌گیرد؛ ردِ خودکار سابقه و اعلانِ دکان دارد.
 */
process.env.RATE_STORE_CHECKOUT_MAX = '200';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const timeout = require('../src/lib/order-timeout');
const STALE = require('../src/config').storefront.staleMs;
const sfRow = (shopId) => one('SELECT * FROM storefronts WHERE shop_id=$1', [shopId]);
const seenAt = (shopId, t) => query(
  `UPDATE sync_devices SET last_seen_at=$2 WHERE app='shop' AND account_kind='shop' AND account_id=$1`, [shopId, t]);

let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;
const MIN = 60_000;

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

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

/** دکانِ روشن با ویترین و یک کالا؛ همگام‌سازیِ همین حالا ⇒ «همین لحظه می‌پذیرد». */
async function liveStore(label) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { ...u, token: u.accessToken, shopId: made.body.shop.id };
  await setFlag(o.shopId, 'shop.orders', true);
  await setFlag(o.shopId, 'shop.storefront', true);
  const slug = `t-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001' },
    { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const p = await h.api('POST', '/api/sync/v1/push', {
    token: o.token, headers: { 'X-App': 'shop', 'X-Device': 'dev-timeout' },
    body: { device_id: 'dev-timeout', schema_version: 2, ops: [{
      op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: 'rice', type: 'insert',
      fields: { name: 'برنج', unit: 'کیلو', salePrice: 120, category: 'خوراکی' },
    }] },
  });
  assert.equal(p.status, 200, JSON.stringify(p.body));
  const r = await h.put('/api/shop/storefront/products/rice', { published: true }, { token: o.token });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return { ...o, slug };
}

async function ticketFor(slug, phone) {
  const s = await h.post(`/api/store/${slug}/verify/start`, { phone });
  const c = await h.post(`/api/store/${slug}/verify/check`, { phone, code: s.body.devCode });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  return c.body.ticket;
}

async function order(s, phone = '0791113001', key = `ot-${uniq()}-key`) {
  const r = await h.post(`/api/store/${s.slug}/checkout`, {
    fulfillment: 'pickup', customer: { name: 'مشتری', phone }, lines: [{ productId: 'rice', qtyMilli: 1000 }],
  }, { headers: { 'Idempotency-Key': key, 'X-Store-Ticket': await ticketFor(s.slug, phone) } });
  assert.ok(r.status === 201 || r.status === 200, JSON.stringify(r.body));
  const row = await one('SELECT id, created_at, accept_deadline_at, status FROM online_orders WHERE shop_id=$1 AND client_key=$2',
    [s.shopId, key]);
  return { ...row, key, token: r.body.order.token || r.body.order.trackingToken, body: r.body };
}

const statusEvents = async (id) => (await query(
  `SELECT to_status, actor_type, reason FROM online_order_events WHERE order_id=$1 AND kind='status' ORDER BY seq`, [id]
)).rows;

test('دکانِ پذیرا: مهلت = ثبت + ۱۵ دقیقه؛ پس از آن auto_rejected (timeout، سیستم) و مشتری می‌بیند', async () => {
  assert.equal(timeout.ACCEPT_TIMEOUT_MS, 15 * MIN);
  const s = await liveStore('پذیرا');
  const o = await order(s);
  assert.equal(Number(o.accept_deadline_at), Number(o.created_at) + 15 * MIN);
  const deadline = Number(o.accept_deadline_at);

  assert.equal(await timeout.sweep(deadline - 1), 0, 'پیش از مهلت دست نمی‌خورد');
  assert.equal((await one('SELECT status FROM online_orders WHERE id=$1', [o.id])).status, 'new');
  assert.equal(await timeout.sweep(deadline), 1);
  const row = await one('SELECT status, status_reason FROM online_orders WHERE id=$1', [o.id]);
  assert.deepEqual([row.status, row.status_reason], ['auto_rejected', 'timeout']);
  assert.deepEqual(await statusEvents(o.id), [{ to_status: 'auto_rejected', actor_type: 'system', reason: 'timeout' }]);
  //  سابقه، حتی بی پرچمِ اعلان
  const aud = await one(`SELECT actor_type, detail FROM audit_logs WHERE action='order.auto_rejected' AND target_id=$1`, [o.id]);
  assert.equal(aud.actor_type, 'system');

  //  مشتری: «سفارش‌های من» با همان بلیت
  const mine = await h.get(`/api/store/${s.slug}/orders`, { headers: { 'X-Store-Ticket': await ticketFor(s.slug, '0791113001') } });
  assert.equal(mine.status, 200, JSON.stringify(mine.body));
  const seen = mine.body.orders.find((x) => x.orderNo === o.body.order.orderNo);
  assert.equal(seen.status, 'auto_rejected');
  //  پیگیری با توکنِ سفارش: خطِ زمانی همان رد را دارد
  const track = await h.get(`/api/store/${s.slug}/orders/${o.body.order.token}`);
  assert.equal(track.status, 200, JSON.stringify(track.body));
  assert.equal(track.body.order.timeline.at(-1).status, 'auto_rejected');
});

test('تکرارِ کلیدِ یکتایی سفارش را دوباره مُهر نمی‌زند', async () => {
  const s = await liveStore('تکرار');
  const o = await order(s, '0791113002');
  //  مهلت را دستی جلو می‌بریم؛ تکرار نباید آن را به «ثبت + ۱۵» برگرداند
  await query('UPDATE online_orders SET accept_deadline_at = $2 WHERE id=$1', [o.id, Number(o.created_at) + 99 * MIN]);
  const again = await order(s, '0791113002', o.key);
  assert.equal(again.body.replayed, true);
  assert.equal(Number(again.accept_deadline_at), Number(o.created_at) + 99 * MIN);
  assert.equal(await timeout.arm({ shop_id: s.shopId, ...(await one('SELECT * FROM storefronts WHERE shop_id=$1', [s.shopId])) }, o.id),
    null, 'arm ِ دوباره هم مُهر نمی‌زند');
});

test('سفارشِ «بعداً» (دکانِ بسته) مُهر نمی‌خورد و هرگز خودکار رد نمی‌شود', async () => {
  const s = await liveStore('بسته');
  const d = await h.post('/api/shop/storefront/presence', { eventId: `ev-${uniq()}-c`, mode: 'closed' }, { token: s.token });
  assert.equal(d.status, 200, JSON.stringify(d.body));
  assert.equal(d.body.status.orderAcceptance, 'later');
  const o = await order(s, '0791113003');
  assert.equal(o.accept_deadline_at, null);
  await timeout.sweep(now() + 10 * 24 * 60 * MIN);
  assert.equal((await one('SELECT status FROM online_orders WHERE id=$1', [o.id])).status, 'new');
  assert.deepEqual(await statusEvents(o.id), []);
});

test('سفارشی که پیش از مهلت پذیرفته شد دست نمی‌خورد', async () => {
  const s = await liveStore('پذیرفته');
  const o = await order(s, '0791113004');
  const ok = await h.post(`/api/shop/orders/${o.id}/transition`, { to: 'accepted' }, { token: s.token });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.equal(await timeout.sweep(Number(o.accept_deadline_at) + MIN), 0);
  assert.equal((await one('SELECT status FROM online_orders WHERE id=$1', [o.id])).status, 'accepted');
  assert.deepEqual((await statusEvents(o.id)).map((e) => e.to_status), ['accepted']);
});

test('دو جاروبِ هم‌زمان: هر سفارش یک بار رد و یک رویدادِ وضعیت', async () => {
  const s = await liveStore('هم‌زمان');
  const a = await order(s, '0791113005');
  const b = await order(s, '0791113006');
  const at = Math.max(Number(a.accept_deadline_at), Number(b.accept_deadline_at));
  const [x, y] = await Promise.all([timeout.sweep(at), timeout.sweep(at)]);
  assert.equal(x + y, 2, `هر دو سفارش روی‌هم یک بار: ${x}+${y}`);
  for (const o of [a, b]) {
    assert.deepEqual((await statusEvents(o.id)).map((e) => e.to_status), ['auto_rejected']);
  }
  assert.equal(await timeout.sweep(at + MIN), 0, 'جاروبِ بعدی کاری ندارد');
});

test('«با تأخیر» (اتصالِ تأییدنشده) مُهر نمی‌خورد؛ arm با ساعتِ ساختِ سفارش می‌سنجد', async () => {
  const s = await liveStore('تأخیر');
  await seenAt(s.shopId, now() - STALE - MIN);
  const o = await order(s, '0791113007');
  assert.equal(o.accept_deadline_at, null, 'delayed ⇒ بی زمان‌سنج');

  //  مرز: در لحظهٔ ساخت هنوز «تأییدشده» بود، چند ثانیه بعد نه
  const created = Number(o.created_at);
  await seenAt(s.shopId, created - STALE + 1000);
  const sf = await sfRow(s.shopId);
  assert.equal(await timeout.arm(sf, o.id, { at: created + 5000 }), null, 'ساعتِ دیرتر ⇒ دیگر نمی‌پذیرفت');
  assert.equal(await timeout.arm(sf, o.id, { at: created }), created + timeout.ACCEPT_TIMEOUT_MS, 'ساعتِ ساخت ⇒ می‌پذیرفت');
});

test('سفارشِ زمان‌بندی‌شده «بعداً» است و مُهر نمی‌خورد', async () => {
  const s = await liveStore('زمان‌بندی');
  const o = await order(s, '0791113008');
  await query('UPDATE online_orders SET accept_deadline_at=NULL, scheduled_for=$2 WHERE id=$1', [o.id, now() + 24 * 60 * MIN]);
  const sf = await sfRow(s.shopId);
  assert.equal(await timeout.arm(sf, o.id, { at: Number(o.created_at) }), null);
  await query('UPDATE online_orders SET scheduled_for=NULL WHERE id=$1', [o.id]);
  assert.ok(await timeout.arm(sf, o.id, { at: Number(o.created_at) }), 'بی زمان‌بندی همان سفارش مُهر می‌خورد');
});

test('سفارشِ خطادار صف را نمی‌گیرد؛ پس از چند بار زمان‌سنجش برداشته می‌شود؛ ردِ خودکار به دکان اعلان می‌دهد', async () => {
  const s = await liveStore('خطادار');
  await setFlag(s.shopId, 'shop.order_notify', true);
  const bad = await order(s, '0791113009');
  const good = await order(s, '0791113010');
  const orders = require('../src/lib/orders');
  const push = require('../src/lib/push');
  const realTransition = orders.transition;
  const realSend = push.sendTo;
  const pushed = [];
  orders.transition = async (shopId, id, ...rest) => {
    if (id === bad.id) throw new Error('boom');
    return realTransition(shopId, id, ...rest);
  };
  push.sendTo = async (target, msg) => { pushed.push({ target, msg }); return { sent: 1, total: 1 }; };
  try {
    let at = Math.max(Number(bad.accept_deadline_at), Number(good.accept_deadline_at));
    assert.ok(await timeout.sweep(at) >= 1);
    assert.equal((await one('SELECT status FROM online_orders WHERE id=$1', [good.id])).status, 'auto_rejected');
    assert.equal(Number((await one('SELECT accept_deadline_at FROM online_orders WHERE id=$1', [bad.id])).accept_deadline_at),
      at + timeout.RETRY_MS, 'مهلتِ خطادار جلو رفت');
    //  اعلانِ دکان از همان راهِ اعلانِ سفارش
    const m = pushed.find((p) => p.msg.data.orderId === good.id);
    assert.equal(m.msg.data.type, 'order_auto_rejected');
    assert.equal(m.target.shopId, s.shopId);
    assert.ok(m.target.onlyUserIds.includes(s.user.id), 'صاحبِ دکان گیرنده است');
    for (let i = 1; i < timeout.MAX_FAILURES; i++) {
      at += timeout.RETRY_MS;
      await timeout.sweep(at);
    }
    const row = await one('SELECT status, accept_deadline_at FROM online_orders WHERE id=$1', [bad.id]);
    assert.deepEqual([row.status, row.accept_deadline_at], ['new', null], 'زمان‌سنج برداشته شد؛ سفارش تازه ماند');
    assert.ok(await one(`SELECT 1 AS x FROM audit_logs WHERE action='order.auto_reject_failed' AND target_id=$1`, [bad.id]));
  } finally {
    orders.transition = realTransition;
    push.sendTo = realSend;
  }
});
