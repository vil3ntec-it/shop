'use strict';
/**
 * S79 — هشدارِ «سفارشِ گیرکرده» به خودِ دکان، در مرحله‌های پس از قبول.
 *
 *   پرچم ── `shop.orders.stuck_alert` (همان قاعدهٔ `orders.flagOf`؛ ⛔ پیش‌فرض خاموش). خاموش ⇒ کارگر به آن
 *           دکان دست نمی‌زند و هیچ رویداد، سابقه یا اعلانی نیست.
 *   کی ──── سفارشِ باز و واقعی (نه آزمایشی) که `order-watch.classify` با آستانه‌های پنل
 *           (`console.delivery_thresholds`، همان عددهای پنلِ مرکزی ۴.۷) برایش یکی از کدهای [CODES] را می‌دهد:
 *           بسته آماده و نسپرده، پیکِ بی‌قبول، بستهٔ برنداشته، در راهِ بی خبر، ناموفقِ بی تصمیم، فراخوانیِ بی نتیجه.
 *           «تازه» ِ پذیرفته‌نشده (`accept_overdue`) این‌جا نیست — راهِ خودش را دارد (ORD-05 ردِ خودکار، ORD-18).
 *   چه ──── یک رویدادِ `note` با `detail.type = 'stuck_alert'` و کدِ گیر در خطِ زمانیِ سفارش (کننده: سیستم)،
 *           سطرِ سابقهٔ `order.stuck_alert` و پوش به اعضای دکان (همان پرچم و گیرنده‌های `shop.order_notify`؛
 *           خاموش ⇒ فقط رویداد و سابقه).
 *   ⛔ شناسایی چیزی را عوض نمی‌کند: نه وضعیتِ سفارش، نه `updated_at` ِ آن، نه مأموریت — هیچ سفارشی خودکار
 *      «تحویل‌شده» یا «لغوشده» نمی‌شود (S79؛ گذار فقط با `orders.transition`).
 *   ایمنی ─ هر «دورِ گیر» **یک بار**: کلیدِ رویداد = کد + زمانِ مرجعِ همان کد (آخرین تغییرِ سفارش یا مأموریت)؛
 *           ردیفِ سفارش `FOR UPDATE` قفل، کد دوباره سنجیده و نبودِ کلید در همان تراکنش دیده می‌شود ⇒ دو نمونهٔ
 *           سرور یا دو جاروبِ پیاپی هشدارِ دوم نمی‌سازند؛ پس از پیشرفت و گیرِ تازه، هشدارِ تازه. خطای اعلان
 *           رویداد را پس نمی‌گیرد.
 */
const { many, now, tx } = require('../db');
const { ulid } = require('./ulid');
const orders = require('./orders');
const watch = require('./order-watch');

const FLAG_KEY = 'shop.orders.stuck_alert';
const EVENT_TYPE = 'stuck_alert';
const TICK_MS = 5 * 60 * 1000;
const BATCH = 300;
/** کدهای «گیر کرده» ِ `order-watch` که دکان کاری برایشان دارد (جز «تازه»). */
const CODES = Object.freeze(watch.STUCK.filter((c) => c !== 'accept_overdue'));
/** سفارش‌های باز پس از قبول (همان «هنوز کار دارد» ِ پنل، بی «تازه»). */
const OPEN = Object.freeze(watch.ORDER_OPEN.filter((s) => s !== 'new'));

const FA_DIGITS = '۰۱۲۳۴۵۶۷۸۹';
const faNum = (n) => String(n).replace(/[0-9]/g, (d) => FA_DIGITS[Number(d)]);

const TEXT = Object.freeze({
  dispatch_overdue: 'بسته آماده است ولی هنوز به پیک سپرده نشده است.',
  courier_unaccepted: 'پیک هنوز این مأموریت را قبول نکرده است.',
  pickup_overdue: 'پیک هنوز بسته را از دکان برنداشته است.',
  delivery_overdue: 'بسته مدتی است در راه است و خبرِ تازه‌ای نیامده است.',
  failed_unresolved: 'تحویلِ ناموفق هنوز بی تصمیم مانده است.',
  recall_pending: 'فراخوانیِ بسته هنوز نتیجه‌ای ندارد.',
});

const flagOf = (shopId) => orders.flagOf(shopId, FLAG_KEY);

/** زمانِ مرجعِ هر کد — همان ستونی که `classify` با آستانه می‌سنجد. */
function refAtOf(code, r) {
  if (code === 'dispatch_overdue') return Number(r.updated_at);
  if (code === 'recall_pending') return Number(r.a_recall_requested_at);
  if (code === 'failed_unresolved' && r.a_status !== 'failed') return Number(r.updated_at);
  return Number(r.a_updated_at);
}

const LATEST_MISSION = `LEFT JOIN LATERAL (
         SELECT a.status AS a_status, a.mode AS a_mode, a.updated_at AS a_updated_at,
                a.recall_outcome AS a_recall_outcome, a.recall_requested_at AS a_recall_requested_at
           FROM delivery_assignments a WHERE a.order_id = o.id ORDER BY a.attempt_no DESC LIMIT 1) la ON true`;

/** نامزدها (همهٔ دکان‌ها، کهنه‌ترین تغییر اول)؛ پرچم برای هر دکان جدا سنجیده می‌شود. */
function candidates() {
  return many(
    `SELECT o.id, o.shop_id, o.order_no, o.status, o.fulfillment, o.created_at, o.updated_at, la.*
       FROM online_orders o
       ${LATEST_MISSION}
      WHERE o.status = ANY($1::text[]) AND o.is_test = false
      ORDER BY o.updated_at, o.id
      LIMIT $2`,
    [OPEN, BATCH]
  );
}

/** کدهای گیرِ همین لحظه، با کلیدِ هر کدام. */
function stuckOf(r, thresholds, at) {
  return watch.classify(r, thresholds, at).stuck
    .filter((code) => CODES.includes(code))
    .map((code) => ({ code, key: `${code}:${refAtOf(code, r)}` }));
}

/** رویدادِ هشدارِ یک کد — `true` اگر همین فراخوانی ثبتش کرد. */
async function record(o, hit, thresholds, at) {
  return tx(async (c) => {
    const row = (await c.query(
      `SELECT o.id, o.status, o.fulfillment, o.created_at, o.updated_at, o.is_test, la.*
         FROM online_orders o ${LATEST_MISSION}
        WHERE o.shop_id = $1 AND o.id = $2 FOR UPDATE OF o`, [o.shop_id, o.id]
    )).rows[0];
    if (!row || row.is_test === true || !OPEN.includes(row.status)) return false;
    if (!stuckOf(row, thresholds, at).some((x) => x.key === hit.key)) return false;
    const done = (await c.query(
      `SELECT 1 FROM online_order_events WHERE order_id = $1 AND kind = 'note' AND detail->>'type' = $2
          AND detail->>'key' = $3 LIMIT 1`, [row.id, EVENT_TYPE, hit.key]
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
        JSON.stringify({ type: EVENT_TYPE, code: hit.code, key: hit.key, since: refAtOf(hit.code, row) }), at]
    );
    return true;
  });
}

/** اعلان به اعضای دکان — همان پرچم و گیرنده‌های اعلانِ سفارش. */
async function notify(o, code) {
  try {
    const on = require('./order-notify');
    if (!(await on.flagOf(o.shop_id))) return { sent: 0, skipped: 'flag_off' };
    const onlyUserIds = await on.recipientIds(o.shop_id);
    const no = Number(o.order_no);
    return (await require('./push').sendTo({ shopId: o.shop_id, app: 'shop', onlyUserIds }, {
      title: `سفارشِ گیرکرده — شمارهٔ ${faNum(no)}`,
      body: TEXT[code] || 'این سفارش مدتی است جلو نرفته است.',
      channel: on.CHANNEL,
      data: { type: 'order_stuck_alert', code, orderId: o.id, orderNo: String(no), shopId: o.shop_id },
    })) || {};
  } catch (err) {
    console.error('[order-stuck-alert] اعلان', err && err.message);
    return { sent: 0, error: String((err && err.message) || err) };
  }
}

/** یک دور: هشدارِ سفارش‌های گیرکرده. خروجی: شمارِ هشدارهای تازه. */
async function sweep(at = now()) {
  const thresholds = (await require('./admin-market').thresholds()).values;
  const flags = new Map();
  let n = 0;
  for (const o of await candidates()) {
    try {
      const hits = stuckOf(o, thresholds, at);
      if (!hits.length) continue;
      if (!flags.has(o.shop_id)) flags.set(o.shop_id, await flagOf(o.shop_id));
      if (!flags.get(o.shop_id)) continue;
      for (const hit of hits) {
        if (!(await record(o, hit, thresholds, at))) continue;
        n++;
        await require('./audit').log({
          shopId: o.shop_id, actorType: 'system', action: 'order.stuck_alert', targetType: 'online_order', targetId: o.id,
          detail: { orderNo: Number(o.order_no), code: hit.code, status: o.status },
        }).catch(() => {});
        await notify(o, hit.code);
      }
    } catch (err) {
      console.error('[order-stuck-alert]', o.id, err && err.message);
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
  timer = setInterval(() => tick().catch((err) => console.error('[order-stuck-alert]', err.message)), TICK_MS);
  if (timer.unref) timer.unref();
}
function stop() {
  if (timer) clearInterval(timer);
  timer = null;
}

module.exports = { FLAG_KEY, EVENT_TYPE, CODES, TEXT, flagOf, sweep, start, stop };
