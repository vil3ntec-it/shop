'use strict';
/**
 * انتقالِ میانِ شعبه‌ها — قاعده‌های خالص (بی پایگاه): شکلِ درخواست، ماشینِ وضعیت و حرکت‌های هر گام (INV-11/S21، فازِ ۴ ِ
 * `docs/BRANCHES-fa.md` §۱۰). نوشتن و قفل در `lib/branch-transfers.js`.
 *
 *   ماشینِ وضعیت (همان CHECK و تریگرِ مهاجرتِ ۹۳۵):
 *     (ارسال) ⇒ in_transit ─┬─ دریافتِ ناقص ⇒ partial ─┬─ دریافتِ ناقص ⇒ partial
 *                           │                         ├─ دریافتِ پایانی / همه رسید ⇒ received
 *                           │                         └─ رد ⇒ rejected
 *                           ├─ دریافتِ پایانی / همه رسید ⇒ received
 *                           └─ رد ⇒ rejected
 *     received و rejected پایانی‌اند.
 *
 *   هر حرکت یک «سو» دارد: `from` = بخشِ شعبهٔ فرستنده (کالا تا پذیرفتن مالِ فرستنده است و «در مسیر»ش همان‌جاست)، `to` =
 *   بخشِ شعبهٔ گیرنده (فقط ورودِ رسیده). کلیدِ یکتای هر حرکت `bxfer:<شناسه>:<گام>:<ردیف>` (جدا از `xfer:` ِ انتقالِ درونِ شعبه).
 *
 *   دریافتِ ناقص (قاعدهٔ روشن، همان انتقالِ درونِ شعبه):
 *     • هر عدد = رسیده در **همین گام**؛ بیش از ماندهٔ در مسیر ⇒ ۴۰۰ `over_received`.
 *     • `final: false` ⇒ کالای نیامده = هیچ؛ بقیه در مسیر می‌ماند (`partial`)؛ گامِ خالی ⇒ ۴۰۰ `nothing_received`؛
 *       اگر با همین گام همه رسید ⇒ `received`.
 *     • `final` (پیش‌فرض) ⇒ کالای نیامده = «بقیه‌اش رسید»؛ هر کسری فقط با دلیل (`reason_required`) و هرگز بی‌صدا: حرکتِ
 *       `adjustment` ِ منفی در `in_transit` ِ فرستنده + وظیفهٔ `transfer_short` برای مدیرِ فرستنده.
 *     • رد ⇒ فقط ماندهٔ در مسیر به مبدأ برمی‌گردد؛ آن‌چه رسیده در گیرنده می‌ماند. دلیل اجباری.
 */
const { badRequest } = require('../middleware/errors');
const { parseReceive } = require('./stock-transfers');

const ENDPOINTS = Object.freeze(['shop_floor', 'warehouse']);
const STATUSES = Object.freeze(['in_transit', 'partial', 'received', 'rejected']);
const OPEN = Object.freeze(['in_transit', 'partial']);
/** گذارهای مجاز؛ کلیدِ '' = پیش از ارسال */
const TRANSITIONS = Object.freeze({
  '': Object.freeze(['in_transit']),
  in_transit: Object.freeze(['partial', 'received', 'rejected']),
  partial: Object.freeze(['partial', 'received', 'rejected']),
  received: Object.freeze([]),
  rejected: Object.freeze([]),
});
const MAX_LINES = 100;
const MAX_QTY_MILLI = 1_000_000_000;
const ID_RE = /^bxf_[0-9a-f]{24}$/;

const str = (v) => (typeof v === 'string' ? v.trim() : '');
const num = (v) => (v === null || v === undefined ? 0 : Number(v));
const isObj = (v) => v !== null && typeof v === 'object' && !Array.isArray(v);
const allowed = (from, to) => (TRANSITIONS[from] || []).includes(to);

function noteOf(v, max) {
  const { stripControl } = require('./validate');
  return require('./ops-errors').maskCards(stripControl(str(v)).slice(0, max));
}

function endpointOf(v) {
  const l = str(v);
  if (!ENDPOINTS.includes(l)) throw badRequest('مکانِ مبدأ/مقصد فقط «قفسه» یا «انبار» است', 'bad_location');
  return l;
}

/**
 * درخواستِ ارسال ⇒ { toClaim, from, to, lines: [{ productId, sentMilli }], note }.
 * `toBranch` (شعبهٔ مقصد) **هدف** است نه هویت: باید صریح بیاید ('' یا 'main' = اصلی) و درِ لایهٔ پایین با شعبه‌های فعالِ
 * همین دکان سنجیده می‌شود. شعبهٔ فرستنده هرگز از بدنه نیست (فقط سرآیندِ سنجیده).
 */
function parseSend(body) {
  const b = isObj(body) ? body : {};
  if (typeof b.toBranch !== 'string') throw badRequest('شعبهٔ مقصد لازم است', 'bad_destination');
  const from = endpointOf(b.from);
  const to = endpointOf(b.to);
  const raw = Array.isArray(b.lines) ? b.lines : [];
  if (!raw.length) throw badRequest('دست‌کم یک کالا لازم است', 'lines_required');
  if (raw.length > MAX_LINES) throw badRequest(`حداکثر ${MAX_LINES} کالا در یک انتقال`, 'too_many_lines');
  const seen = new Set();
  const lines = raw.map((l) => {
    const productId = str(l && l.productId);
    if (!productId || productId.length > 128 || productId.includes('\u0000')) throw badRequest('کالا لازم است', 'product_required');
    if (seen.has(productId)) throw badRequest('هر کالا یک بار در انتقال می‌آید', 'duplicate_product');
    seen.add(productId);
    const q = l.qtyMilli;
    if (typeof q !== 'number' || !Number.isSafeInteger(q) || q < 1 || q > MAX_QTY_MILLI) {
      throw badRequest('مقدار باید عددِ صحیحِ مثبت (هزارم) باشد', 'bad_qty');
    }
    return { productId, sentMilli: q };
  });
  return { toClaim: b.toBranch.trim().slice(0, 80), from, to, lines, note: noteOf(b.note, 300) };
}

const received = (l) => (l.receivedMilli === null || l.receivedMilli === undefined ? 0 : num(l.receivedMilli));

/** ردیف‌های `lines` ⇒ نمای هر ردیف (همان شکلِ انتقالِ درونِ شعبه) */
function linesView(raw, status) {
  const open = OPEN.includes(status);
  return (Array.isArray(raw) ? raw : JSON.parse(raw || '[]')).map((l) => {
    const got = l.receivedMilli === null || l.receivedMilli === undefined ? null : num(l.receivedMilli);
    return {
      productId: l.productId, sentMilli: num(l.sentMilli), receivedMilli: got,
      shortMilli: got === null || open ? 0 : num(l.sentMilli) - got,
      pendingMilli: open ? num(l.sentMilli) - (got || 0) : 0,
    };
  });
}

/** حرکت‌های ارسال (همه در بخشِ فرستنده): مبدأ ⇒ `in_transit` */
function sendMoves(id, req) {
  return req.lines.flatMap((l, i) => [
    { side: 'from', productId: l.productId, location: req.from, type: 'transfer_out', qtyMilli: l.sentMilli, reason: 'branch_transfer_send', sourceKey: `bxfer:${id}:send-out:${i + 1}` },
    { side: 'from', productId: l.productId, location: 'in_transit', type: 'transfer_in', qtyMilli: l.sentMilli, reason: 'branch_transfer_send', sourceKey: `bxfer:${id}:send-in:${i + 1}` },
  ]);
}

/**
 * طرحِ دریافت ⇐ `(t, part) => plan`؛ `t` همان نمای انتقال (`lines` با `sentMilli`/`receivedMilli`)، `part` شمارهٔ این گامِ دریافت.
 * plan: { kind, status, lines, reason, note, final, stepLines, moves, tasks }
 */
function receivePlan(body) {
  const { given, reason, final } = parseReceive(body);
  return (t, part) => {
    const unknown = [...given.keys()].filter((pid) => !t.lines.some((l) => l.productId === pid));
    if (unknown.length) {
      const err = badRequest('این کالا در انتقال نیست', 'product_not_in_transfer');
      err.data = { productIds: unknown };
      throw err;
    }
    const step = t.lines.map((l) => {
      const before = received(l);
      const left = l.sentMilli - before;
      const got = given.has(l.productId) ? given.get(l.productId) : (final ? left : 0);
      if (got > left) {
        const err = badRequest('بیش از ماندهٔ «در مسیر» نمی‌رسد؛ اضافه را جدا ثبت کنید', 'over_received');
        err.data = { productId: l.productId, sentMilli: l.sentMilli, receivedMilli: before + got, pendingMilli: left };
        throw err;
      }
      return { productId: l.productId, sentMilli: l.sentMilli, got, after: before + got };
    });
    if (!final && step.every((s) => s.got === 0)) throw badRequest('در این گام چیزی نرسیده است', 'nothing_received');
    const closing = final || step.every((s) => s.after === s.sentMilli);
    const short = closing ? step.filter((s) => s.after < s.sentMilli) : [];
    if (short.length && !reason) throw badRequest('برای کسری دلیل لازم است', 'reason_required');
    const tag = final ? 'recv' : `part${part}`;
    const moves = [];
    const tasks = [];
    step.forEach((s, i) => {
      const n = i + 1;
      if (s.got > 0) {
        moves.push({ side: 'from', productId: s.productId, location: 'in_transit', type: 'transfer_out', qtyMilli: s.got, reason: 'branch_transfer_receive', sourceKey: `bxfer:${t.id}:${tag}-out:${n}` });
        moves.push({ side: 'to', productId: s.productId, location: t.to, type: 'transfer_in', qtyMilli: s.got, reason: 'branch_transfer_receive', sourceKey: `bxfer:${t.id}:${tag}-in:${n}` });
      }
      const gap = closing ? s.sentMilli - s.after : 0;
      if (gap > 0) {
        moves.push({ side: 'from', productId: s.productId, location: 'in_transit', type: 'adjustment', qtyMilli: -gap, reason: `کسریِ انتقالِ شعبه: ${reason}`.slice(0, 80), sourceKey: `bxfer:${t.id}:short:${n}` });
        tasks.push({
          side: 'from', kind: 'transfer_short', productId: s.productId, location: 'in_transit', dedupe: `bxfer-short:${t.id}:${s.productId}`,
          detail: { branchTransferId: t.id, toBranch: t.toBranch.id, sentMilli: s.sentMilli, receivedMilli: s.after, shortMilli: gap, reason },
        });
      }
    });
    return {
      kind: 'receive',
      status: closing ? 'received' : 'partial',
      lines: step.map((s) => ({ productId: s.productId, sentMilli: s.sentMilli, receivedMilli: s.after })),
      reason: short.length ? reason : '',
      note: reason,
      final: closing,
      stepLines: step.filter((s) => s.got > 0).map((s) => ({ productId: s.productId, qtyMilli: s.got })),
      moves,
      tasks,
    };
  };
}

/** طرحِ رد: ماندهٔ «در مسیر» از `in_transit` ِ فرستنده به مبدأ؛ دلیل اجباری */
function rejectPlan(body) {
  const reason = noteOf(isObj(body) ? body.reason : '', 120);
  if (!reason) throw badRequest('دلیلِ رد لازم است', 'reason_required');
  return (t) => {
    const back = t.lines.map((l) => ({ productId: l.productId, sentMilli: l.sentMilli, got: received(l), left: l.sentMilli - received(l) }));
    return {
      kind: 'reject',
      status: 'rejected',
      lines: back.map((l) => ({ productId: l.productId, sentMilli: l.sentMilli, receivedMilli: l.got })),
      reason,
      note: reason,
      final: true,
      stepLines: back.filter((l) => l.left > 0).map((l) => ({ productId: l.productId, qtyMilli: l.left })),
      moves: back.flatMap((l, i) => (l.left > 0 ? [
        { side: 'from', productId: l.productId, location: 'in_transit', type: 'transfer_out', qtyMilli: l.left, reason: 'branch_transfer_reject', sourceKey: `bxfer:${t.id}:back-out:${i + 1}` },
        { side: 'from', productId: l.productId, location: t.from, type: 'transfer_in', qtyMilli: l.left, reason: 'branch_transfer_reject', sourceKey: `bxfer:${t.id}:back-in:${i + 1}` },
      ] : [])),
      tasks: [],
    };
  };
}

module.exports = {
  ENDPOINTS, STATUSES, OPEN, TRANSITIONS, MAX_LINES, ID_RE, allowed, parseSend, linesView, sendMoves, receivePlan, rejectPlan, noteOf,
};
