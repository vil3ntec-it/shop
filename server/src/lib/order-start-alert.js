'use strict';
/**
 * ORD-18 — هشدارِ «شروعِ سفارشِ زمان‌بندی‌شده» پیش از زمانش.
 *
 *   پرچم ── `shop.orders.start_alert` (همان قاعدهٔ `orders.flagOf`؛ ⛔ پیش‌فرض خاموش). خاموش ⇒ کارگر به آن
 *           دکان دست نمی‌زند و هیچ رویداد یا اعلانی نیست.
 *   کی ──── سفارشِ زمان‌بندی‌شده‌ای که هنوز آماده‌سازی‌اش شروع نشده (`new` یا `accepted`) و زمانش تا
 *           [LEAD_MS] (۳۰ دقیقه) دیگر می‌رسد — یا رسیده و هنوز شروع نشده (تا [LATE_MS] پس از آن، برای
 *           سروری که چند دقیقه خاموش بوده). آزمایشی نه.
 *   چه ──── یک رویدادِ `note` با `detail.type = 'start_alert'` در خطِ زمانیِ سفارش (کننده: سیستم؛ ORD-16)،
 *           یک سطرِ سابقهٔ عملیات، و پوشِ اعلانِ سفارش به اعضای دکان (همان پرچم و گیرنده‌های `shop.order_notify`؛
 *           خاموش ⇒ فقط رویداد و سابقه). وضعیتِ سفارش عوض نمی‌شود.
 *   ایمنی ─ هر سفارش **یک بار**: ردیفِ سفارش `FOR UPDATE` قفل و نبودِ رویداد در همان تراکنش سنجیده می‌شود ⇒
 *           دو نمونهٔ سرور یا دو جاروبِ پیاپی اعلانِ دوم نمی‌سازند. خطای اعلان رویداد را پس نمی‌گیرد.
 */
const { many, now, tx } = require('../db');
const { ulid } = require('./ulid');
const orders = require('./orders');

const FLAG_KEY = 'shop.orders.start_alert';
const LEAD_MS = 30 * 60 * 1000;
const LATE_MS = 15 * 60 * 1000;
const TICK_MS = 60 * 1000;
const BATCH = 200;
const EVENT_TYPE = 'start_alert';
const WAITING = Object.freeze(['new', 'accepted']);
const FA_DIGITS = '۰۱۲۳۴۵۶۷۸۹';
const faNum = (n) => String(n).replace(/[0-9]/g, (d) => FA_DIGITS[Number(d)]);

const flagOf = (shopId) => orders.flagOf(shopId, FLAG_KEY);

/** سفارش‌های نامزد (همهٔ دکان‌ها)؛ پرچم برای هر دکان جدا سنجیده می‌شود. */
function candidates(at) {
  return many(
    `SELECT o.id, o.shop_id, o.order_no, o.scheduled_for, o.status, o.fulfillment
       FROM online_orders o
      WHERE o.status = ANY($1::text[]) AND o.is_test = false AND o.scheduled_for IS NOT NULL
        AND o.scheduled_for > $2 AND o.scheduled_for <= $3
        AND NOT EXISTS (SELECT 1 FROM online_order_events e WHERE e.order_id = o.id AND e.kind = 'note'
                          AND e.detail->>'type' = $4)
      ORDER BY o.scheduled_for, o.id
      LIMIT $5`,
    [WAITING, at - LATE_MS, at + LEAD_MS, EVENT_TYPE, BATCH]
  );
}

/** رویدادِ هشدار برای یک سفارش — `true` اگر همین فراخوانی ثبتش کرد. */
async function record(o, at) {
  return tx(async (c) => {
    const row = (await c.query(
      'SELECT id, status, scheduled_for, is_test FROM online_orders WHERE shop_id = $1 AND id = $2 FOR UPDATE', [o.shop_id, o.id]
    )).rows[0];
    if (!row || !WAITING.includes(row.status) || row.is_test === true || row.scheduled_for === null) return false;
    const due = Number(row.scheduled_for);
    if (due <= at - LATE_MS || due > at + LEAD_MS) return false;
    const done = (await c.query(
      `SELECT 1 FROM online_order_events WHERE order_id = $1 AND kind = 'note' AND detail->>'type' = $2 LIMIT 1`,
      [row.id, EVENT_TYPE]
    )).rows[0];
    if (done) return false;
    const seq = Number((await c.query(
      'SELECT COALESCE(MAX(seq), 0) + 1 AS n FROM online_order_events WHERE order_id = $1', [row.id]
    )).rows[0].n);
    await c.query(
      `INSERT INTO online_order_events (id, order_id, shop_id, seq, kind, from_status, to_status,
         actor_type, actor_id, reason, note, detail, created_at)
       VALUES ($1,$2,$3,$4,'note',$5,$5,'system','','','',$6::jsonb,$7)`,
      [ulid(at), row.id, o.shop_id, seq, row.status,
        JSON.stringify({ type: EVENT_TYPE, scheduledFor: due, leadMinutes: Math.round((due - at) / 60000) }), at]
    );
    return true;
  });
}

/** اعلانِ «شروع کنید» به اعضای دکان — همان پرچم و گیرنده‌های اعلانِ سفارش. */
async function notify(o, at) {
  try {
    const on = require('./order-notify');
    if (!(await on.flagOf(o.shop_id))) return { sent: 0, skipped: 'flag_off' };
    const onlyUserIds = await on.recipientIds(o.shop_id);
    const mins = Math.max(0, Math.round((Number(o.scheduled_for) - at) / 60000));
    const no = Number(o.order_no);
    return (await require('./push').sendTo({ shopId: o.shop_id, app: 'shop', onlyUserIds }, {
      title: `سفارشِ زمان‌بندی‌شده — شمارهٔ ${faNum(no)}`,
      body: mins > 0 ? `تا ${faNum(mins)} دقیقهٔ دیگر وقتش است؛ آماده‌سازی را شروع کنید.` : 'وقتش رسیده و هنوز شروع نشده است.',
      channel: on.CHANNEL,
      data: { type: 'order_start_alert', orderId: o.id, orderNo: String(no), shopId: o.shop_id },
    })) || {};
  } catch (err) {
    console.error('[order-start-alert] اعلان', err && err.message);
    return { sent: 0, error: String((err && err.message) || err) };
  }
}

/** یک دور: هشدارِ سفارش‌های وقت‌رسیده. خروجی: شمارِ هشدارهای تازه. */
async function sweep(at = now()) {
  const flags = new Map();
  let n = 0;
  for (const o of await candidates(at)) {
    try {
      if (!flags.has(o.shop_id)) flags.set(o.shop_id, await flagOf(o.shop_id));
      if (!flags.get(o.shop_id)) continue;
      if (!(await record(o, at))) continue;
      n++;
      await require('./audit').log({
        shopId: o.shop_id, actorType: 'system', action: 'order.start_alert', targetType: 'online_order', targetId: o.id,
        detail: { orderNo: Number(o.order_no), scheduledFor: Number(o.scheduled_for) },
      }).catch(() => {});
      await notify(o, at);
    } catch (err) {
      console.error('[order-start-alert]', o.id, err && err.message);
    }
  }
  return n;
}

let timer = null;
let sweeping = false;
async function tick() {
  if (sweeping) return 0;
  sweeping = true;
  try { return await sweep(); } finally { sweeping = false; }
}
function start() {
  if (timer) return;
  timer = setInterval(() => tick().catch((err) => console.error('[order-start-alert]', err.message)), TICK_MS);
  if (timer.unref) timer.unref();
}
function stop() {
  if (timer) clearInterval(timer);
  timer = null;
}

module.exports = { FLAG_KEY, LEAD_MS, LATE_MS, EVENT_TYPE, flagOf, sweep, start, stop };
