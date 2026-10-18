'use strict';
/**
 * ماشینِ وضعیتِ سفارشِ آنلاین — بخشِ ۳.۹ بندِ ۲ی مشخصات (فاز ۴).
 *
 * ⛔ وضعیت **فقط** از راهِ گذارِ مجازِ همین جدول عوض می‌شود (جدولِ ۳.۴:
 * «onlineOrder… وضعیت فقط از راهِ گذارِ مجازِ ماشینِ وضعیت»). این پرونده
 * خالص است — نه دیتابیس، نه زمانِ سیستم — تا هر گذار بی سرور آزموده شود.
 *
 *   new → accepted | rejected | auto_rejected | cancelled_by_customer
 *   accepted → picking → packed → ready_for_pickup | out_for_delivery
 *   ready_for_pickup → picked_up | cancelled(no_show)
 *   out_for_delivery → delivered | delivery_failed
 *   delivery_failed → retry_scheduled → out_for_delivery | returned_to_store
 *   returned_to_store → restocked
 *   (پیش از ارسال: accepted | picking | packed) → cancelled
 *   delivered | picked_up → return_requested → refunded_partial | refunded_full
 *
 * وضعیتِ پرداخت و وضعیتِ «نقد هنگامِ تحویل» دو ماشینِ جدا هستند.
 */
const { can } = require('./permissions');

const ORDER_TRANSITIONS = Object.freeze({
  new: ['accepted', 'rejected', 'auto_rejected', 'cancelled_by_customer'],
  accepted: ['picking', 'cancelled'],
  picking: ['packed', 'cancelled'],
  packed: ['ready_for_pickup', 'out_for_delivery', 'cancelled'],
  ready_for_pickup: ['picked_up', 'cancelled'],
  out_for_delivery: ['delivered', 'delivery_failed'],
  delivery_failed: ['retry_scheduled'],
  retry_scheduled: ['out_for_delivery', 'returned_to_store'],
  returned_to_store: ['restocked'],
  delivered: ['return_requested'],
  picked_up: ['return_requested'],
  return_requested: ['refunded_partial', 'refunded_full'],
  rejected: [],
  auto_rejected: [],
  cancelled_by_customer: [],
  cancelled: [],
  restocked: [],
  refunded_partial: [],
  refunded_full: [],
});

const ORDER_STATUSES = Object.freeze(Object.keys(ORDER_TRANSITIONS));

/** دلیل‌های مجاز. «other» همیشه توضیح می‌خواهد. */
const REASONS = Object.freeze({
  //  ORD-06
  rejected: ['out_of_stock', 'closed', 'busy', 'out_of_area', 'other'],
  //  ORD-05 — کارِ سیستم
  auto_rejected: ['timeout', 'other'],
  cancelled: ['customer_request', 'out_of_stock', 'no_show', 'other'],
  //  recalled: دکان پس از حرکتِ پیک لغو کرد و بسته برمی‌گردد (تحویل و پیک)
  delivery_failed: ['no_answer', 'wrong_address', 'refused', 'recalled', 'other'],
});

/** گذارهایی که بی دلیل پذیرفته نیستند. */
const REASON_REQUIRED = Object.freeze(['rejected', 'auto_rejected', 'cancelled', 'delivery_failed']);

/** فقط سیستم (زمان‌سنجِ قبول، ORD-05) — هیچ کاربری دستی نمی‌زند. */
const SYSTEM_ONLY = Object.freeze(['auto_rejected']);

/** پول برمی‌گردد ⇒ دسترسیِ `orders.refund` (صاحب و مدیر — همان قاعدهٔ `sales.cancel`). */
const MONEY_TARGETS = Object.freeze(['refunded_partial', 'refunded_full']);

//  ── پرداخت (جدا از وضعیتِ سفارش) ─────────────────────────────────
//  «پرداخت‌شده» فقط از «در انتظار» — یعنی فقط با رویدادِ تأییدشدهٔ
//  ارائه‌دهنده (جدولِ ۳.۴). failed → pending: تلاشِ تازهٔ پرداخت.
const PAYMENT_TRANSITIONS = Object.freeze({
  unpaid: ['pending'],
  pending: ['paid', 'failed'],
  failed: ['pending'],
  paid: ['partially_refunded', 'refunded'],
  partially_refunded: ['refunded'],
  refunded: [],
});

//  ── نقد هنگامِ تحویل ───────────────────────────────────────────────
const COD_TRANSITIONS = Object.freeze({
  to_collect: ['collected_by_courier', 'collected_in_store'],
  collected_by_courier: ['settled_to_shop'],
  collected_in_store: ['settled_to_shop'],
  settled_to_shop: [],
});

//  ── برگه‌های بخشِ «سفارش‌ها» (ORD-12) ───────────────────────────────
const TABS = Object.freeze(['new', 'preparing', 'ready', 'on_the_way', 'scheduled', 'done']);
const OPEN_TABS = Object.freeze(['new', 'preparing', 'ready', 'on_the_way', 'scheduled']);

function nextOf(status) {
  return ORDER_TRANSITIONS[status] ? [...ORDER_TRANSITIONS[status]] : [];
}

function canTransition(from, to) {
  return Boolean(ORDER_TRANSITIONS[from] && ORDER_TRANSITIONS[from].includes(to));
}

function isTerminal(status) {
  return Boolean(ORDER_TRANSITIONS[status]) && ORDER_TRANSITIONS[status].length === 0;
}

const fail = (code) => ({ ok: false, code });

/**
 * آیا این گذار برای این سفارش و این کننده مجاز است؟
 * @param {{status:string, fulfillment:string}} order
 * @param {string} to
 * @param {{actor:{type:string, role?:string}, reason?:string, note?:string}} ctx
 * @returns {{ok:true}|{ok:false, code:string}}
 */
function check(order, to, { actor = {}, reason = '', note = '' } = {}) {
  const from = order.status;
  if (!canTransition(from, to)) return fail('invalid_transition');

  //  «بسته‌شده» راهش را از نوعِ تحویل می‌گیرد
  if (to === 'ready_for_pickup' && order.fulfillment !== 'pickup') return fail('fulfillment_mismatch');
  if (to === 'out_for_delivery' && order.fulfillment !== 'delivery') return fail('fulfillment_mismatch');

  if (SYSTEM_ONLY.includes(to) && actor.type !== 'system') return fail('system_only');
  if (MONEY_TARGETS.includes(to) && actor.type !== 'system' && !can(actor.role, 'orders.refund')) {
    return fail('permission_denied');
  }

  if (REASON_REQUIRED.includes(to) && !reason) return fail('reason_required');
  if (reason && REASONS[to] && !REASONS[to].includes(reason)) return fail('bad_reason');
  //  نمودار: ready_for_pickup → cancelled فقط «نیامد»
  if (to === 'cancelled' && from === 'ready_for_pickup' && reason !== 'no_show') return fail('bad_reason');
  if (reason === 'other' && !String(note || '').trim()) return fail('note_required');
  return { ok: true };
}

/** دکمه‌های مجازِ یک کارت برای همین کاربر (دلیل را خودِ دکمه می‌پرسد). */
function nextFor(order, actor) {
  return nextOf(order.status).filter((to) => {
    let reason = REASONS[to] ? REASONS[to][0] : '';
    if (to === 'cancelled' && order.status === 'ready_for_pickup') reason = 'no_show';
    return check(order, to, { actor, reason, note: 'x' }).ok;
  });
}

function canPayment(from, to) {
  return Boolean(PAYMENT_TRANSITIONS[from] && PAYMENT_TRANSITIONS[from].includes(to));
}

function canCod(from, to) {
  return Boolean(COD_TRANSITIONS[from] && COD_TRANSITIONS[from].includes(to));
}

/**
 * برگهٔ یک سفارش. سفارشِ زمان‌بندی‌شده تا «قبول» نشده در «تازه» می‌ماند —
 * باید قبول شود و هشدارش (ORD-02) نباید پنهان شود؛ پس از قبول، تا زمانش
 * نرسیده در «زمان‌بندی‌شده» است.
 */
function tabOf(order, at) {
  const s = order.status;
  if (s === 'new') return 'new';
  if (s === 'accepted') {
    const due = order.scheduled_for == null ? null : Number(order.scheduled_for);
    return due != null && due > at ? 'scheduled' : 'preparing';
  }
  if (s === 'picking' || s === 'packed') return 'preparing';
  if (s === 'ready_for_pickup') return 'ready';
  if (s === 'out_for_delivery' || s === 'delivery_failed' || s === 'retry_scheduled') return 'on_the_way';
  return 'done';
}

/**
 * شرطِ SQLِ هر برگه — همان `tabOf`، برای پرس‌وجوی صفحه‌بندی‌شده.
 * `atParam` جایگاهِ پارامترِ «اکنون» است (مثلاً `'$2'`) — هیچ عددی در متن نمی‌نشیند.
 */
function tabWhere(tab, atParam) {
  const at = atParam;
  switch (tab) {
    case 'new': return `status = 'new'`;
    case 'scheduled': return `status = 'accepted' AND scheduled_for IS NOT NULL AND scheduled_for > ${at}`;
    case 'preparing':
      return `(status IN ('picking','packed') OR (status = 'accepted' AND (scheduled_for IS NULL OR scheduled_for <= ${at})))`;
    case 'ready': return `status = 'ready_for_pickup'`;
    case 'on_the_way': return `status IN ('out_for_delivery','delivery_failed','retry_scheduled')`;
    case 'done':
      return `status NOT IN ('new','accepted','picking','packed','ready_for_pickup','out_for_delivery','delivery_failed','retry_scheduled')`;
    default: return null;
  }
}

/**
 * مبلغِ ردیف به واحدِ کوچک — قاعدهٔ یگانهٔ گردکردنِ بخشِ ۳.۴:
 * `floorDiv(qty.milli × unitPrice.minor + 50_000, 100_000) × 100`
 * (گرد به یک افغانی، نیمه به سمتِ +∞؛ با BigInt تا ممیزِ شناور خطا نکند).
 */
function lineTotalMinor(qtyMilli, unitPriceMinor) {
  const n = BigInt(qtyMilli) * BigInt(unitPriceMinor) + 50000n;
  const d = 100000n;
  let q = n / d;
  if (n % d !== 0n && n < 0n) q -= 1n;   // floorDiv: BigInt به سمتِ صفر می‌بُرد
  const out = q * 100n;
  if (out > BigInt(Number.MAX_SAFE_INTEGER) || out < -BigInt(Number.MAX_SAFE_INTEGER)) {
    throw new RangeError('مبلغِ ردیف بیش از حد بزرگ است');
  }
  return Number(out);
}

module.exports = {
  ORDER_TRANSITIONS, ORDER_STATUSES, REASONS, REASON_REQUIRED, SYSTEM_ONLY, MONEY_TARGETS,
  PAYMENT_TRANSITIONS, COD_TRANSITIONS, TABS, OPEN_TABS,
  nextOf, canTransition, isTerminal, check, nextFor, canPayment, canCod, tabOf, tabWhere, lineTotalMinor,
};
