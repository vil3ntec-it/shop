'use strict';
/**
 * ORD-07 — «عقب انداختنِ» یک سفارش: زمانِ آماده‌شدنِ سفارشِ قبول‌شده جلو می‌رود.
 *
 *   پرچم ── `shop.orders.delay` (همان قاعدهٔ `orders.flagOf`؛ ⛔ پیش‌فرض خاموش) روی
 *           `shop.orders`؛ نوشتن کلیدِ اشتراکِ `online_orders` را هم می‌خواهد (SUB-10).
 *   کی ──── فقط [DELAY_STATUSES]: قبول‌شده، در حالِ جمع‌آوری، بسته‌بندی‌شده — پیش از «آماده»
 *           یا «ارسال». سفارشِ تازه هنوز زمانی ندارد (زمان را «قبول» می‌گذارد، #227).
 *   چقدر ── `minutes` (۱ تا [MAX_MINUTES]) روی زمانِ **ذخیره‌شده** در سرور، نه ساعتِ کلاینت:
 *           پایه = `ready_at`، وگرنه زمانِ سفارشِ زمان‌بندی‌شده، وگرنه اکنون؛ و هرگز پیش از اکنون
 *           (سفارشی که دیر شده از همین حالا عقب می‌رود). سقف: اکنون + ۷ روز (همان سقفِ «قبول»).
 *   ایمنی ─ ردیفِ سفارش `FOR UPDATE` قفل می‌شود؛ کلیدِ یکتایی (`Idempotency-Key`) در رویداد
 *           می‌نشیند ⇒ تکرارِ همان درخواست (دو کلیک، قطعِ شبکه پس از ثبت) دوباره عقب نمی‌برد
 *           و همان سفارش را برمی‌گرداند؛ همان کلید با بدنهٔ دیگر ⇒ ۴۰۹ `idempotency_key_reused`.
 *           `expectedReadyAt` (زمانی که کاربر دید؛ `null` یعنی «بی زمان») ⇒ اگر کسِ دیگری
 *           زودتر عوضش کرده ۴۰۹ `ready_changed` با زمانِ تازه — دو عقب‌انداختنِ هم‌زمان روی هم
 *           جمع نمی‌شوند مگر کاربر عمداً بخواهد.
 *   سابقه ─ یک رویدادِ `note` (نوعِ موجود در `online_order_events`؛ مهاجرت نمی‌خواهد) با
 *           `detail.type = 'ready_delay'`، زمانِ پیشین و تازه، دلیل و توضیح، کننده و زمان (ORD-16).
 *           وضعیتِ سفارش عوض نمی‌شود (`from_status = to_status`)؛ `version` بالا می‌رود تا صفِ
 *           زندهٔ دستگاه‌ها تازه شود. مشتری زمانِ تازه را در پیگیری می‌بیند (`readyAt`).
 */
const db = require('../db');
const { now, tx } = db;
const { ulid } = require('./ulid');
const orders = require('./orders');
const { stripControl } = require('./validate');
const { badRequest, notFound, conflict } = require('../middleware/errors');

const FLAG_KEY = 'shop.orders.delay';
const DELAY_STATUSES = Object.freeze(['accepted', 'picking', 'packed']);
/** دلیل کد است، متن در `note`؛ «other» توضیح می‌خواهد. */
const DELAY_REASONS = Object.freeze(['busy', 'out_of_stock', 'courier', 'customer_request', 'other']);
const MAX_MINUTES = 24 * 60;
const MAX_AHEAD_MS = 7 * 24 * 60 * 60 * 1000;
const EVENT_TYPE = 'ready_delay';

const flagOf = (shopId) => orders.flagOf(shopId, FLAG_KEY);

/** آنچه برنامه برای دکمهٔ «عقب انداختن» لازم دارد (`GET /orders/config`). */
const configOf = () => ({ statuses: [...DELAY_STATUSES], reasons: [...DELAY_REASONS], maxMinutes: MAX_MINUTES });

const nul = (v) => (v === null || v === undefined ? null : Number(v));

function parse(body) {
  const b = body && typeof body === 'object' ? body : {};
  const m = b.minutes;
  if (typeof m !== 'number' || !Number.isSafeInteger(m) || m < 1 || m > MAX_MINUTES) {
    throw badRequest(`مدتِ عقب انداختن باید ۱ تا ${MAX_MINUTES} دقیقه باشد`, 'bad_minutes');
  }
  const reason = typeof b.reason === 'string' ? b.reason.trim() : '';
  if (reason && !DELAY_REASONS.includes(reason)) throw badRequest('دلیل معتبر نیست', 'bad_reason');
  const note = stripControl(typeof b.note === 'string' ? b.note.trim() : '').slice(0, 300);
  if (reason === 'other' && !note) throw badRequest('برای «دلیلِ دیگر» توضیح لازم است', 'note_required');
  let expected;
  if (Object.prototype.hasOwnProperty.call(b, 'expectedReadyAt')) {
    const e = b.expectedReadyAt;
    if (e !== null && (typeof e !== 'number' || !Number.isSafeInteger(e) || e < 0)) {
      throw badRequest('زمانِ دیده‌شده معتبر نیست', 'bad_expected_ready_at');
    }
    expected = e;
  }
  return { minutes: m, reason, note, expected };
}

/**
 * سفارش را عقب می‌اندازد.
 * @param {{key:string, body:object, actor:{type:string, id?:string, role?:string}}} p
 * @returns {Promise<{order:object, replayed:boolean, readyAt:number, prevReadyAt:number|null}>}
 */
async function delay(shopId, id, { key, body, actor }) {
  const p = parse(body);
  const hash = orders.hashOf(body);
  const oid = String(id || '');
  const out = await tx(async (c) => {
    const row = (await c.query(
      'SELECT id, order_no, status, ready_at, scheduled_for FROM online_orders WHERE shop_id=$1 AND id=$2 FOR UPDATE',
      [shopId, oid]
    )).rows[0];
    if (!row) throw notFound('سفارش پیدا نشد', 'order_not_found');

    //  تکرارِ همان درخواست ⇒ همان نتیجه، بی رویدادِ دوم (پیش از هر سنجشِ دیگر: سفارش شاید
    //  از آن پس جلو رفته باشد و پاسخِ گم‌شده باید همان «انجام شد» بماند)
    const prev = (await c.query(
      `SELECT detail FROM online_order_events
        WHERE order_id=$1 AND kind='note' AND detail->>'type'=$2 AND detail->>'key'=$3 LIMIT 1`,
      [row.id, EVENT_TYPE, key]
    )).rows[0];
    if (prev) {
      const d = typeof prev.detail === 'string' ? JSON.parse(prev.detail) : prev.detail;
      if (d.hash !== hash) throw conflict('این کلیدِ یکتایی پیش‌تر برای درخواستِ دیگری به کار رفته است', 'idempotency_key_reused');
      return { replayed: true, readyAt: Number(d.readyAt), prevReadyAt: nul(d.prevReadyAt), orderNo: Number(row.order_no) };
    }

    if (!DELAY_STATUSES.includes(row.status)) {
      const err = conflict('این سفارش دیگر عقب انداختنی نیست', 'delay_not_allowed');
      err.current = row.status;
      throw err;
    }
    const current = nul(row.ready_at);
    if (p.expected !== undefined && p.expected !== current) {
      const err = conflict('زمانِ آماده‌شدن همین حالا عوض شده است', 'ready_changed');
      err.current = current;
      throw err;
    }
    const at = now();
    const base = Math.max(current ?? nul(row.scheduled_for) ?? at, at);
    const readyAt = base + p.minutes * 60_000;
    if (readyAt > at + MAX_AHEAD_MS) throw badRequest('زمانِ آماده بیش از هفت روز جلوتر نمی‌رود', 'bad_ready_time');

    const seq = Number((await c.query(
      'SELECT COALESCE(MAX(seq), 0) + 1 AS n FROM online_order_events WHERE order_id=$1', [row.id]
    )).rows[0].n);
    await c.query(
      'UPDATE online_orders SET ready_at=$3, version=version+1, updated_at=$4 WHERE shop_id=$1 AND id=$2',
      [shopId, row.id, readyAt, at]
    );
    await c.query(
      `INSERT INTO online_order_events (id, order_id, shop_id, seq, kind, from_status, to_status,
         actor_type, actor_id, reason, note, detail, created_at)
       VALUES ($1,$2,$3,$4,'note',$5,$5,$6,$7,$8,$9,$10::jsonb,$11)`,
      [ulid(at), row.id, shopId, seq, row.status, actor.type, actor.id || '', p.reason, p.note,
        JSON.stringify({ type: EVENT_TYPE, readyAt, prevReadyAt: current, minutes: p.minutes, key, hash }), at]
    );
    return { replayed: false, readyAt, prevReadyAt: current, orderNo: Number(row.order_no) };
  });
  return { ...out, order: await orders.get(shopId, oid, actor) };
}

module.exports = { FLAG_KEY, DELAY_STATUSES, DELAY_REASONS, MAX_MINUTES, EVENT_TYPE, flagOf, configOf, parse, delay };
