'use strict';
/**
 * ORD-16 — «فرستادنِ دوبارهٔ» پیامِ وضعیتِ سفارش به مشتری از برنامهٔ مدیریت (`lib/customer-notify.js`).
 *
 *   پرچم ── `shop.orders.notice_resend` (همان قاعدهٔ `orders.flagOf`؛ ⛔ پیش‌فرض خاموش). خودِ پیام‌ها پرچمِ
 *           `shop.customer_notify` را می‌خواهند؛ آن خاموش ⇒ ۴۰۹ `notify_disabled` (کارگر ردیف را لغو می‌کرد).
 *   چه ──── همان ردیفِ صندوق (یکتاییِ `(order_id, event)` ِ NTF-04 دست نمی‌خورد): دوباره `pending`، تلاش‌ها از
 *           صفر، همان زنجیرهٔ کانال‌ها و همان سقفِ هر مشتری (`PER_HOUR`/`PER_DAY`). پیامِ تازه‌ای ساخته نمی‌شود.
 *   کی ──── فقط [RESENDABLE] (نرفت، کانالی نبود، سقفِ مشتری): «رفت» دوباره نمی‌رود (پیامِ دوتایی)، «کنار رفت»/
 *           «دیر شد»/«لغو» کهنه‌اند. پیامِ کهنه‌تر وقتی پیامِ تازه‌تری برای همان سفارش هست ⇒ `notice_superseded`؛
 *           پیرتر از `MAX_AGE_MS` (کارگر هم منقضی‌اش می‌کرد) ⇒ `notice_too_old`.
 *   چقدر ── حداکثر [MAX_RESENDS] بار برای هر پیام (`resend_limit`) — شمارش از رویدادهای همین سفارش.
 *   ایمنی ─ ردیفِ سفارش و ردیفِ صندوق `FOR UPDATE`؛ کلیدِ یکتایی در رویداد ⇒ تکرارِ همان درخواست (دو کلیک،
 *           قطعِ نت پس از ثبت) همان پاسخ، بی تلاشِ دوم؛ همان کلید برای پیامِ دیگر ⇒ ۴۰۹ `idempotency_key_reused`.
 *   سابقه ─ رویدادِ `note` با `detail.type = 'notice_resend'` (کننده، پیام، حالِ پیشین) در خطِ زمانی (ORD-16)؛
 *           مشتری خطِ زمانیِ `note` را نمی‌بیند. بی شماره، متن یا لینک.
 */
const { now, tx } = require('../db');
const { ulid } = require('./ulid');
const orders = require('./orders');
const notify = require('./customer-notify');
const { notFound, conflict } = require('../middleware/errors');

const FLAG_KEY = 'shop.orders.notice_resend';
const RESENDABLE = Object.freeze(['failed', 'no_channel', 'rate_limited']);
const MAX_RESENDS = 3;
const EVENT_TYPE = 'notice_resend';
const NOTICE_ID_RE = /^[0-9A-Z]{26}$/;

const flagOf = (shopId) => orders.flagOf(shopId, FLAG_KEY);

/** آنچه برنامه برای دکمهٔ «فرستادنِ دوباره» لازم دارد (`GET /orders/config`، فقط با پرچم). */
const configOf = () => ({ statuses: [...RESENDABLE], maxResends: MAX_RESENDS });

const fail = (message, code, extra = {}) => Object.assign(conflict(message, code), extra);

/**
 * پیامِ [noticeId] ِ سفارشِ [orderId] را دوباره در صف می‌گذارد.
 * @returns {Promise<{notice:object, replayed:boolean, prevStatus:string, orderNo:number}>}
 */
async function resend(shopId, orderId, noticeId, { key, actor }) {
  const oid = String(orderId || '').slice(0, 64);
  const nid = String(noticeId || '');
  if (!NOTICE_ID_RE.test(nid)) throw notFound('پیام پیدا نشد', 'notice_not_found');
  const out = await tx(async (c) => {
    const order = (await c.query(
      'SELECT id, order_no, status FROM online_orders WHERE shop_id=$1 AND id=$2 FOR UPDATE', [shopId, oid]
    )).rows[0];
    if (!order) throw notFound('سفارش پیدا نشد', 'order_not_found');

    //  تکرارِ همان درخواست ⇒ همان نتیجه (پیش از هر سنجشِ دیگر: پیام شاید از آن پس رفته باشد)
    const prev = (await c.query(
      `SELECT detail FROM online_order_events
        WHERE order_id=$1 AND kind='note' AND detail->>'type'=$2 AND detail->>'key'=$3 LIMIT 1`,
      [order.id, EVENT_TYPE, key]
    )).rows[0];
    const row = (await c.query(
      'SELECT * FROM customer_notify_outbox WHERE id=$1 AND shop_id=$2 AND order_id=$3 FOR UPDATE', [nid, shopId, order.id]
    )).rows[0];
    if (prev) {
      const d = typeof prev.detail === 'string' ? JSON.parse(prev.detail) : prev.detail;
      if (d.noticeId !== nid) throw conflict('این کلیدِ یکتایی پیش‌تر برای پیامِ دیگری به کار رفته است', 'idempotency_key_reused');
      return { replayed: true, row, prevStatus: String(d.prevStatus || ''), orderNo: Number(order.order_no) };
    }
    if (!row) throw notFound('پیام پیدا نشد', 'notice_not_found');
    if (!(await notify.flagOf(shopId, (t, p) => c.query(t, p)))) throw fail('پیام به مشتری برای این دکان خاموش است', 'notify_disabled');
    if (!RESENDABLE.includes(row.status)) {
      throw fail('این پیام دوباره فرستادنی نیست', 'notice_not_resendable', { current: row.status });
    }
    //  فقط «دیرتر» (هم‌لحظه نه: شناسهٔ ULID ِ یک میلی‌ثانیه ترتیب ندارد؛ هم‌لحظهٔ نرفته را `onStatusIn` خودش کنار زده)
    const newer = (await c.query(
      'SELECT 1 FROM customer_notify_outbox WHERE order_id=$1 AND id <> $2 AND created_at > $3 LIMIT 1',
      [order.id, row.id, row.created_at]
    )).rows[0];
    if (newer) throw fail('پیامِ تازه‌تری برای همین سفارش هست', 'notice_superseded');
    const at = now();
    if (at - Number(row.created_at) > notify.MAX_AGE_MS) throw fail('این پیام دیگر به کار نمی‌آید', 'notice_too_old');
    const used = Number((await c.query(
      `SELECT COUNT(*)::int AS n FROM online_order_events
        WHERE order_id=$1 AND kind='note' AND detail->>'type'=$2 AND detail->>'noticeId'=$3`,
      [order.id, EVENT_TYPE, row.id]
    )).rows[0].n);
    if (used >= MAX_RESENDS) throw fail(`هر پیام حداکثر ${MAX_RESENDS} بار دوباره فرستاده می‌شود`, 'resend_limit', { max: MAX_RESENDS });

    const next = (await c.query(
      `UPDATE customer_notify_outbox
          SET status='pending', attempts=0, next_attempt_at=$2, locked_at=NULL, tried='[]'::jsonb, last_error='',
              updated_at=$2
        WHERE id=$1 AND status = ANY($3::text[]) RETURNING *`,
      [row.id, at, RESENDABLE]
    )).rows[0];
    if (!next) throw fail('این پیام دوباره فرستادنی نیست', 'notice_not_resendable', { current: row.status });
    const seq = Number((await c.query(
      'SELECT COALESCE(MAX(seq), 0) + 1 AS n FROM online_order_events WHERE order_id=$1', [order.id]
    )).rows[0].n);
    await c.query(
      `INSERT INTO online_order_events (id, order_id, shop_id, seq, kind, from_status, to_status,
         actor_type, actor_id, reason, note, detail, created_at)
       VALUES ($1,$2,$3,$4,'note',$5,$5,$6,$7,'','',$8::jsonb,$9)`,
      [ulid(at), order.id, shopId, seq, order.status, actor.type, actor.id || '',
        JSON.stringify({ type: EVENT_TYPE, noticeId: row.id, event: row.event, prevStatus: row.status, count: used + 1, key }), at]
    );
    return { replayed: false, row: next, prevStatus: row.status, orderNo: Number(order.order_no) };
  });
  if (!out.replayed) notify.soon();
  return { notice: out.row ? notify.shape(out.row) : null, replayed: out.replayed, prevStatus: out.prevStatus, orderNo: out.orderNo };
}

module.exports = { FLAG_KEY, RESENDABLE, MAX_RESENDS, EVENT_TYPE, flagOf, configOf, resend };
