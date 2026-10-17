'use strict';
/**
 * ماشینِ وضعیتِ «مأموریتِ تحویل» و حسابِ تسویهٔ پیک — v3 §۲۲.۲ مرحلهٔ C،
 * §۲۰.۶، §۲۸.۵ («ماشین‌های مستقلِ سفارش، مأموریتِ پیک، COD و تسویه»).
 *
 * خالص است — نه دیتابیس، نه زمانِ سیستم — تا هر گذار و هر جمع بی سرور
 * آزموده شود. اثرِ هر گذار بر **سفارش** این‌جا نیست؛ `delivery.js` آن را
 * فقط از راهِ `orders.transitionIn` می‌برد.
 *
 *   requested → assigned (پیک قبول کرد) | rejected (پیک رد کرد) | withdrawn (دکان پس گرفت)
 *   assigned  → picked_up (بسته را گرفت) | withdrawn (پیش از حرکت)
 *             | delivered (فقط دریافتِ حضوری: مشتری با کد در دکان گرفت)
 *   picked_up → moving → arrived → delivered | failed      («در راه» و «رسیدم» اختیاری‌اند)
 *   failed    → moving (تلاشِ دوباره) | returned (بسته به دکان برگشت)
 *   picked_up | moving | arrived → returned  (لغو پس از حرکت: دکان دریافتِ بسته را تأیید می‌کند)
 *
 * ⛔ «تحویل شد» و «برگشت» پایانی‌اند: هیچ لغوی وانمود نمی‌کند بسته‌ای که
 * تحویل یا برگردانده شده هنوز قابلِ توقف است.
 * ⛔ `picked_up`ِ پیک با `picked_up`ِ سفارش (مشتری خودش برد) یکی نیست
 * (§۲۸.۵): این‌جا یعنی «پیک بسته را از دکان گرفت» ⇒ سفارش «در راه».
 */

const MODES = Object.freeze(['shop_courier', 'platform', 'external', 'pickup']);

const TRANSITIONS = Object.freeze({
  requested: ['assigned', 'rejected', 'withdrawn'],
  assigned: ['picked_up', 'withdrawn', 'delivered'],
  picked_up: ['moving', 'arrived', 'delivered', 'failed', 'returned'],
  moving: ['arrived', 'delivered', 'failed', 'returned'],
  arrived: ['delivered', 'failed', 'returned'],
  failed: ['moving', 'returned'],
  delivered: [],
  rejected: [],
  withdrawn: [],
  returned: [],
});
const STATUSES = Object.freeze(Object.keys(TRANSITIONS));

/** مأموریتِ زنده — هر سفارش در هر لحظه حداکثر یکی (ایندکسِ یکتای جزئی). */
const LIVE = Object.freeze(['requested', 'assigned', 'picked_up', 'moving', 'arrived', 'failed']);
/** بسته از دکان بیرون رفته است. */
const DEPARTED = Object.freeze(['picked_up', 'moving', 'arrived', 'failed']);

/** گذارهایی که پیک (فقط پیکِ همین مأموریت، فقط در روشِ shop_courier) می‌زند. */
const COURIER_TARGETS = Object.freeze(['assigned', 'rejected', 'picked_up', 'moving', 'arrived', 'delivered', 'failed']);

/**
 * دلیل‌ها. ناموفق همان کدهای سفارش است (DLV-09: نبود/تماس بی‌پاسخ، رد کرد،
 * پیدا نشد) به‌علاوهٔ «فراخوانده شد» برای لغو پس از حرکت.
 */
const REASONS = Object.freeze({
  rejected: ['busy', 'too_far', 'cash_limit', 'other'],
  withdrawn: ['reassign', 'order_cancelled', 'customer_request', 'other'],
  failed: ['no_answer', 'wrong_address', 'refused', 'recalled', 'other'],
  recall: ['customer_request', 'out_of_stock', 'wrong_order', 'other'],
  //  گرفته ≠ مورد انتظار (§۲۰.۶): پرداختِ ناقص، ردِ مبلغ، آسیب/کسریِ کالا
  cash: ['partial_payment', 'refused_amount', 'damaged_or_short', 'other'],
});
const REASON_REQUIRED = Object.freeze(['rejected', 'withdrawn', 'failed']);

function canTransition(from, to) {
  return Boolean(TRANSITIONS[from] && TRANSITIONS[from].includes(to));
}

function isTerminal(status) {
  return Boolean(TRANSITIONS[status]) && TRANSITIONS[status].length === 0;
}

const fail = (code) => ({ ok: false, code });

/**
 * آیا این گذار برای این مأموریت و این کننده مجاز است؟
 * @param {{status:string, mode:string, courier_id:string}} a
 * @param {string} to
 * @param {{actor:{kind:'courier'|'staff', courierId?:string, active?:boolean, canAssign?:boolean, canHandover?:boolean},
 *          reason?:string, note?:string}} ctx
 */
function check(a, to, { actor = {}, reason = '', note = '' } = {}) {
  if (!canTransition(a.status, to)) return fail('invalid_transition');
  //  بی «گرفتنِ بسته» فقط دریافتِ حضوری «تحویل شد» می‌شود
  if (a.status === 'assigned' && to === 'delivered' && a.mode !== 'pickup') return fail('invalid_transition');
  if (a.mode === 'pickup' && to !== 'delivered' && to !== 'withdrawn') return fail('invalid_transition');

  if (actor.kind === 'courier') {
    if (a.mode !== 'shop_courier' || !actor.courierId || actor.courierId !== a.courier_id) return fail('not_your_mission');
    if (!COURIER_TARGETS.includes(to)) return fail('staff_only');
    //  پیکِ غیرفعال رویدادهای مأموریتِ گرفته‌شده را هنوز می‌فرستد (§۲۸.۶:
    //  رویدادِ مالیِ آفلاین بی‌صدا دور ریخته نمی‌شود)، ولی کارِ تازه نمی‌پذیرد
    if (to === 'assigned' && actor.active === false) return fail('courier_inactive');
  } else if (actor.kind === 'staff') {
    if (to === 'returned') {
      //  دریافتِ فیزیکیِ بسته در دکان
      if (!actor.canHandover) return fail('permission_denied');
    } else if (to === 'withdrawn') {
      if (!actor.canAssign) return fail('permission_denied');
    } else if (a.mode === 'pickup') {
      //  دریافتِ حضوری: فقط «تحویل شد» با کد (یا تأییدِ دستیِ مدیر)
      if (to !== 'delivered') return fail('invalid_transition');
      if (!actor.canHandover) return fail('permission_denied');
    } else if (a.mode === 'external') {
      //  شرکتِ بیرونی: ثبت و تطبیقِ دستی (وب‌هوک: BLOCKED-EXTERNAL)
      if (!actor.canAssign) return fail('permission_denied');
    } else {
      //  پیکِ دکان: کارمند فقط «تحویل شد / ناموفق» را جای پیک ثبت می‌کند
      //  (گوشیِ پیک خاموش، مشتریِ بی‌گوشی) — با مجوزِ بالاتر و توضیح
      if (!['delivered', 'failed'].includes(to)) return fail('courier_only');
      if (!actor.canAssign) return fail('permission_denied');
      if (!String(note || '').trim()) return fail('note_required');
    }
  } else {
    return fail('permission_denied');
  }

  if (REASON_REQUIRED.includes(to) && !reason) return fail('reason_required');
  if (reason && REASONS[to] && !REASONS[to].includes(reason)) return fail('bad_reason');
  if (reason === 'other' && !String(note || '').trim()) return fail('note_required');
  return { ok: true };
}

/**
 * اثرِ گذارِ مأموریت بر سفارش — فهرستِ گذارهای سفارش به ترتیب.
 * هر گام فقط وقتی برداشته می‌شود که سفارش هنوز به آن نرسیده باشد.
 * @returns {{steps:Array<{to:string, reason?:string, note?:string}>}|{error:string}}
 */
function orderSteps(from, to, orderStatus, { mode = 'shop_courier', reason = '', note = '' } = {}) {
  const s = orderStatus;
  if (to === 'picked_up') {
    if (s === 'out_for_delivery') return { steps: [] };
    if (s !== 'packed') return { error: 'order_not_ready' };
    return { steps: [{ to: 'out_for_delivery' }] };
  }
  if (to === 'delivered') {
    if (mode === 'pickup') {
      if (s === 'picked_up') return { steps: [] };
      if (s !== 'ready_for_pickup') return { error: 'order_not_ready' };
      return { steps: [{ to: 'picked_up' }] };
    }
    if (s === 'delivered') return { steps: [] };
    if (s !== 'out_for_delivery') return { error: 'order_state_conflict' };
    return { steps: [{ to: 'delivered' }] };
  }
  if (to === 'failed') {
    if (s === 'delivery_failed') return { steps: [] };
    if (s !== 'out_for_delivery') return { error: 'order_state_conflict' };
    return { steps: [{ to: 'delivery_failed', reason, note }] };
  }
  if (to === 'moving' && from === 'failed') {
    //  تلاشِ دوباره (DLV-09): سفارش «زمان‌بندیِ دوباره» ← «در راه»
    if (s === 'out_for_delivery') return { steps: [] };
    if (s === 'delivery_failed') return { steps: [{ to: 'retry_scheduled', note: 'retry' }, { to: 'out_for_delivery' }] };
    if (s === 'retry_scheduled') return { steps: [{ to: 'out_for_delivery' }] };
    return { error: 'order_state_conflict' };
  }
  if (to === 'returned') {
    //  بسته واقعاً به دکان برگشت ⇒ سفارش «برگشته به دکان» (نمودارِ ۳.۹:
    //  ناموفق ← تصمیمِ کارِ بعدی ← برگشت). فراخوانی ⇒ ناموفق با «recalled».
    if (s === 'returned_to_store') return { steps: [] };
    const steps = [];
    if (s === 'out_for_delivery') steps.push({ to: 'delivery_failed', reason: reason || 'recalled', note });
    if (s === 'out_for_delivery' || s === 'delivery_failed') steps.push({ to: 'retry_scheduled', note: 'return_to_store' });
    if (s === 'out_for_delivery' || s === 'delivery_failed' || s === 'retry_scheduled') {
      steps.push({ to: 'returned_to_store' });
      return { steps };
    }
    return { error: 'order_state_conflict' };
  }
  return { steps: [] };
}

/**
 * حسابِ تسویهٔ پیک (DLV-12، §۲۰.۶، §۱۵.۷ بندِ ۹) — همه عددِ صحیح به واحدِ کوچک.
 *
 *   expectedCash   = Σ مبلغِ موردِ انتظار از مشتری
 *   collectedCash  = Σ مبلغی که پیک واقعاً گرفت
 *   collectionDiff = collected − expected   (کمتر از مشتری ⇒ منفی؛ پرونده برای بررسی)
 *   fareTotal      = Σ کرایه
 *   handoverDue    = collected − (کرایه اگر از همین نقد برداشته شود)
 *   handedOver     = آن‌چه پیک می‌گوید تحویل داد
 *   received       = آن‌چه مدیر شمرد
 *   difference     = received − handoverDue ⇒ shortage | excess
 *
 * ⛔ اختلافِ مشتری (collectionDiff) خودکار «تسویه‌شده» حساب نمی‌شود؛ جدا
 * گزارش می‌شود. تسویهٔ پیک فقط آن‌چه را پیک واقعاً گرفت مبنا می‌گیرد.
 */
function settlementTotals(rows, { handedOverMinor = 0, receivedMinor = 0, farePaidFromCash = false } = {}) {
  let expected = 0n;
  let collected = 0n;
  let fares = 0n;
  for (const r of rows) {
    expected += BigInt(r.expected_collect_minor || 0);
    collected += BigInt(r.cash_collected_minor || 0);
    fares += BigInt(r.fare_minor || 0);
  }
  const handoverDue = farePaidFromCash ? collected - fares : collected;
  const received = BigInt(receivedMinor);
  const diff = received - handoverDue;
  const out = {
    count: rows.length,
    expectedCashMinor: expected,
    collectedCashMinor: collected,
    collectionDiffMinor: collected - expected,
    fareTotalMinor: fares,
    farePaidFromCash: Boolean(farePaidFromCash),
    handoverDueMinor: handoverDue,
    handedOverMinor: BigInt(handedOverMinor),
    receivedMinor: received,
    differenceMinor: diff,
    shortageMinor: diff < 0n ? -diff : 0n,
    excessMinor: diff > 0n ? diff : 0n,
  };
  for (const [k, v] of Object.entries(out)) {
    if (typeof v !== 'bigint') continue;
    if (v > BigInt(Number.MAX_SAFE_INTEGER) || v < -BigInt(Number.MAX_SAFE_INTEGER)) {
      throw new RangeError('جمعِ تسویه بیش از حد بزرگ است');
    }
    out[k] = Number(v);
  }
  return out;
}

module.exports = {
  MODES, TRANSITIONS, STATUSES, LIVE, DEPARTED, COURIER_TARGETS, REASONS, REASON_REQUIRED,
  canTransition, isTerminal, check, orderSteps, settlementTotals,
};
