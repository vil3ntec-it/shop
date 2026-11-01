'use strict';
/**
 * سفارشِ خرید از تأمین‌کننده — PUR-02 (وضعیت‌ها)، PUR-04 (دریافتِ کامل/جزئی، اختلاف و خراب،
 * باقی‌ماندهٔ باز) و PUR-14 (تطبیقِ سفارش، رسید و فاکتور) — §۶.۶ و سناریوی S19.
 *
 * همتای Kotlinِ این فایل `core/src/commonMain/.../core/purchase/PurchaseOrderCore.kt` است و هر دو
 * یک فایلِ بردارِ مرجع را می‌گذرانند: `core/test-vectors/purchase-orders-v1.json`. یکی را بی
 * دیگری عوض نکن. این فایل هیچ چیزی از پایگاه‌داده، شبکه یا ساعت نمی‌خواهد.
 *
 * ── واحدها ──────────────────────────────────────────────────────
 * پول = عددِ صحیحِ یک‌صدمِ افغانی (`…Minor`)، مقدار = عددِ صحیحِ هزارم (`…Milli`)، هر دو در
 * ±(2^53−1). جمعِ هر ردیف = mulDivRound(مقدار، فی، ۱۰۰۰) (نیمه به +∞، همان قاعدهٔ #231).
 * ⚠️ فقط JS: عددِ غیرصحیح/رشته به‌جای عدد همان کدِ ردِ «مقدار/فیِ نادرست» را می‌گیرد (Kotlin
 * نوعِ Long دارد و هرگز چنین ورودی‌ای نمی‌بیند).
 *
 * ── قاعده‌ها (همان فایلِ Kotlin) ─────────────────────────────────
 * ۱) ذخیره فقط draft | sent | cancelled؛ «بخشی» و «کامل» از رسیدها درمی‌آیند.
 * ۲) پذیرفته = رسیده − خراب؛ باقی‌مانده = سفارش − جمعِ پذیرفته (≥ ۰). خراب باقی‌مانده را کم نمی‌کند.
 * ۳) کامل = همهٔ باقی‌مانده‌ها صفر؛ بخشی = دست‌کم یک واحد پذیرفته و هنوز کامل نه.
 * ۴) ویرایش فقط پیش‌نویس؛ فرستادن فقط از پیش‌نویس؛ دریافت فقط فرستاده/بخشی؛ لغوِ فرستاده/بخشی دلیل می‌خواهد.
 * ۵) رسید کلیدِ یکتا دارد: همان کلید و همان ردیف‌ها ⇒ duplicate؛ ردیف‌های دیگر ⇒ id_conflict.
 * ۶) بیش از باقی‌مانده فقط با allowOver.
 */

const MAX_SAFE = 9007199254740991;
const MAX_LINES = 500;
const MAX_ID = 64;
const MAX_REF = 128;
const MILLI = 1000;
const BP = 10000;

const STATUS = Object.freeze({ DRAFT: 'draft', SENT: 'sent', PARTIAL: 'partial', COMPLETE: 'complete', CANCELLED: 'cancelled' });
const STATE = Object.freeze({ DRAFT: 'draft', SENT: 'sent', CANCELLED: 'cancelled' });
const STATES = Object.freeze([STATE.DRAFT, STATE.SENT, STATE.CANCELLED]);
const ACTION = Object.freeze({ EDIT: 'edit', SEND: 'send', CANCEL: 'cancel', RECEIVE: 'receive' });

const CODE = Object.freeze({
  BAD_STATE: 'bad_state',
  SUPPLIER_REQUIRED: 'supplier_required',
  LINES_REQUIRED: 'lines_required',
  TOO_MANY_LINES: 'too_many_lines',
  LINE_ID_REQUIRED: 'line_id_required',
  DUPLICATE_LINE: 'duplicate_line',
  PRODUCT_REQUIRED: 'product_required',
  DUPLICATE_PRODUCT: 'duplicate_product',
  BAD_QTY: 'bad_qty',
  BAD_PRICE: 'bad_price',
  OVERFLOW: 'overflow',
  NOT_DRAFT: 'not_draft',
  NOT_SENT: 'not_sent',
  CANCELLED: 'cancelled',
  COMPLETE: 'complete',
  REASON_REQUIRED: 'reason_required',
  RECEIPT_ID_REQUIRED: 'receipt_id_required',
  ID_CONFLICT: 'id_conflict',
  UNKNOWN_LINE: 'unknown_line',
  DAMAGED_OVER_RECEIVED: 'damaged_over_received',
  NOTHING_RECEIVED: 'nothing_received',
  OVER_ORDERED: 'over_ordered',
});

const ISSUE = Object.freeze({
  RECEIVED_OVER_ORDER: 'received_over_order',
  BILLED_OVER_RECEIVED: 'billed_over_received',
  BILLED_UNDER_RECEIVED: 'billed_under_received',
  PRICE_OVER_ORDER: 'price_over_order',
  PRICE_UNDER_ORDER: 'price_under_order',
  NOT_ON_ORDER: 'not_on_order',
});

/* ── عدد ─────────────────────────────────────────────────────────── */

const isSafe = (x) => Number.isSafeInteger(x);
const overflow = () => { throw new RangeError('purchase-order-core: overflow'); };

function add(a, b) {
  const r = a + b;
  if (!isSafe(r)) overflow();
  return r;
}

const BIG_MAX = BigInt(MAX_SAFE);

/** round(a × b / c) با نیمه به سمتِ +∞ = floor((2ab + c) / 2c)؛ ۰ < c < 2^31 */
function mulDivRound(a, b, c) {
  if (!Number.isSafeInteger(c) || c < 1 || c > 2147483647) {
    throw new RangeError('purchase-order-core: mulDivRound needs 0 < c < 2^31');
  }
  if (!isSafe(a) || !isSafe(b)) overflow();
  const C = BigInt(c);
  const n = BigInt(a) * BigInt(b) * 2n + C;
  const d = 2n * C;
  let q = n / d;
  if (n % d < 0n) q -= 1n;
  if (q > BIG_MAX || q < -BIG_MAX) overflow();
  return Number(q);
}

const lineTotalMinor = (qtyMilli, priceMinor) => mulDivRound(qtyMilli, priceMinor, MILLI);

function totalOrNull(q, p) {
  try { return lineTotalMinor(q, p); } catch (e) { if (e instanceof RangeError) return null; throw e; }
}

const ID = new RegExp(`^[A-Za-z0-9._:-]{1,${MAX_ID}}$`);
/** شناسهٔ خودِ سفارش (ردیف، کلیدِ رسید): همان الگوی Kotlin */
const idOk = (id) => typeof id === 'string' && ID.test(id);
/** «خالی» = فقط فاصله، تب و خطِ تازه — همان تعریفِ Kotlin، نه String.trim */
const blank = (x) => x == null || (typeof x === 'string' && /^[ \t\n\r]*$/.test(x));
/** شناسهٔ دفتر (کالا/تأمین‌کننده) */
const refOk = (x) => typeof x === 'string' && x.length >= 1 && x.length <= MAX_REF && !blank(x);

const fail = (code, lineId, index) => ({ code, lineId: lineId === undefined ? null : lineId, index: index === undefined ? -1 : index });

/* ── سفارش (PUR-02) ──────────────────────────────────────────────── */

/** سفارشِ درست؟ null یعنی درست؛ ترتیبِ سنجش همان Kotlin. */
function checkOrder(o) {
  if (!o || !STATES.includes(o.state)) return fail(CODE.BAD_STATE);
  if (!refOk(o.supplierId)) return fail(CODE.SUPPLIER_REQUIRED);
  if (!Array.isArray(o.lines) || o.lines.length === 0) return fail(CODE.LINES_REQUIRED);
  if (o.lines.length > MAX_LINES) return fail(CODE.TOO_MANY_LINES);
  const ids = new Set();
  const products = new Set();
  let total = 0;
  for (let i = 0; i < o.lines.length; i++) {
    const l = o.lines[i] || {};
    const lid = typeof l.id === 'string' ? l.id : null;
    if (!idOk(l.id)) return fail(CODE.LINE_ID_REQUIRED, lid, i);
    if (ids.has(l.id)) return fail(CODE.DUPLICATE_LINE, l.id, i);
    ids.add(l.id);
    if (!refOk(l.productId)) return fail(CODE.PRODUCT_REQUIRED, l.id, i);
    if (products.has(l.productId)) return fail(CODE.DUPLICATE_PRODUCT, l.id, i);
    products.add(l.productId);
    if (!isSafe(l.qtyMilli) || l.qtyMilli <= 0) return fail(CODE.BAD_QTY, l.id, i);
    if (!isSafe(l.priceMinor) || l.priceMinor < 0) return fail(CODE.BAD_PRICE, l.id, i);
    const lt = totalOrNull(l.qtyMilli, l.priceMinor);
    if (lt === null) return fail(CODE.OVERFLOW, l.id, i);
    try { total = add(total, lt); } catch (e) { return fail(CODE.OVERFLOW, l.id, i); }
  }
  return null;
}

const orderTotalMinor = (o) => o.lines.reduce((acc, l) => add(acc, lineTotalMinor(l.qtyMilli, l.priceMinor)), 0);

/** پیشرفتِ هر ردیف با همهٔ رسیدها */
function progress(o, receipts) {
  const rec = new Map();
  const dmg = new Map();
  for (const r of receipts || []) {
    for (const l of r.lines) {
      rec.set(l.lineId, add(rec.get(l.lineId) || 0, l.receivedMilli));
      dmg.set(l.lineId, add(dmg.get(l.lineId) || 0, l.damagedMilli));
    }
  }
  return o.lines.map((l) => {
    const received = rec.get(l.id) || 0;
    const damaged = dmg.get(l.id) || 0;
    const accepted = add(received, -damaged);
    return {
      lineId: l.id, productId: l.productId, orderedMilli: l.qtyMilli,
      receivedMilli: received, damagedMilli: damaged, acceptedMilli: accepted,
      remainingMilli: Math.max(0, l.qtyMilli - accepted), overMilli: Math.max(0, accepted - l.qtyMilli),
    };
  });
}

function status(o, receipts) {
  if (o.state === STATE.CANCELLED) return STATUS.CANCELLED;
  if (o.state === STATE.DRAFT) return STATUS.DRAFT;
  const p = progress(o, receipts);
  if (p.every((x) => x.remainingMilli === 0)) return STATUS.COMPLETE;
  if (p.some((x) => x.acceptedMilli > 0)) return STATUS.PARTIAL;
  return STATUS.SENT;
}

function allowed(s) {
  switch (s) {
    case STATUS.DRAFT: return [ACTION.EDIT, ACTION.SEND, ACTION.CANCEL];
    case STATUS.SENT:
    case STATUS.PARTIAL: return [ACTION.CANCEL, ACTION.RECEIVE];
    default: return [];
  }
}

function refusal(s) {
  if (s === STATUS.DRAFT) return fail(CODE.NOT_SENT);
  if (s === STATUS.CANCELLED) return fail(CODE.CANCELLED);
  if (s === STATUS.COMPLETE) return fail(CODE.COMPLETE);
  return fail(CODE.NOT_DRAFT);
}

/* نتیجهٔ گذار: { changed: state } | { unchanged: true } | { failure } */

function edit(current, receipts, next) {
  const s = status(current, receipts);
  if (s !== STATUS.DRAFT) return { failure: s === STATUS.CANCELLED ? fail(CODE.CANCELLED) : fail(CODE.NOT_DRAFT) };
  if (!next || next.state !== STATE.DRAFT) return { failure: fail(CODE.BAD_STATE) };
  const bad = checkOrder(next);
  return bad ? { failure: bad } : { changed: STATE.DRAFT };
}

function send(o, receipts) {
  const s = status(o, receipts);
  if (s === STATUS.DRAFT) {
    const bad = checkOrder(o);
    return bad ? { failure: bad } : { changed: STATE.SENT };
  }
  if (s === STATUS.CANCELLED) return { failure: fail(CODE.CANCELLED) };
  return { unchanged: true };
}

function cancel(o, receipts, reason) {
  const s = status(o, receipts);
  if (s === STATUS.DRAFT) return { changed: STATE.CANCELLED };
  if (s === STATUS.SENT || s === STATUS.PARTIAL) {
    return blank(reason) ? { failure: fail(CODE.REASON_REQUIRED) } : { changed: STATE.CANCELLED };
  }
  if (s === STATUS.COMPLETE) return { failure: fail(CODE.COMPLETE) };
  return { unchanged: true };
}

/* ── دریافت (PUR-04) ─────────────────────────────────────────────── */

const nonZero = (lines) => lines.filter((l) => l.receivedMilli !== 0 || l.damagedMilli !== 0);
const byLine = (a, b) => (a.lineId < b.lineId ? -1 : a.lineId > b.lineId ? 1 : 0);

function sameLines(a, b) {
  const x = nonZero(a).slice().sort(byLine);
  const y = nonZero(b).slice().sort(byLine);
  if (x.length !== y.length) return false;
  return x.every((l, i) => l.lineId === y[i].lineId && l.receivedMilli === y[i].receivedMilli
    && l.damagedMilli === y[i].damagedMilli && l.priceMinor === y[i].priceMinor);
}

/** فقط چهار فیلدِ ورودیِ هر ردیف (فیلدهای اضافهٔ درخواست کنار می‌روند) */
const inputLine = (l) => ({ lineId: l.lineId, receivedMilli: l.receivedMilli, damagedMilli: l.damagedMilli, priceMinor: l.priceMinor });

/**
 * دریافتِ کامل یا جزئی. نتیجه: { ok: {receipt, lines, totalMinor, status} } | { duplicate: receipt } | { failure }.
 * ترتیبِ سنجش همان Kotlin: کلید ⇒ تکراری/تعارض ⇒ وضعیت ⇒ ردیف‌ها ⇒ «هیچ» ⇒ اضافه/سرریز.
 */
function receive(o, receipts, input, allowOver = false) {
  const prior = receipts || [];
  if (!input || !idOk(input.id)) return { failure: fail(CODE.RECEIPT_ID_REQUIRED) };
  const lines = Array.isArray(input.lines) ? input.lines.map((l) => inputLine(l || {})) : [];
  const same = prior.find((r) => r.id === input.id);
  if (same) {
    const comparable = lines.every((l) => isSafe(l.receivedMilli) && isSafe(l.damagedMilli));
    return comparable && sameLines(same.lines, lines) ? { duplicate: same } : { failure: fail(CODE.ID_CONFLICT) };
  }
  const s = status(o, prior);
  if (!allowed(s).includes(ACTION.RECEIVE)) return { failure: refusal(s) };

  const known = new Set(o.lines.map((l) => l.id));
  const seen = new Set();
  for (let i = 0; i < lines.length; i++) {
    const l = lines[i];
    if (typeof l.lineId !== 'string' || !known.has(l.lineId)) return { failure: fail(CODE.UNKNOWN_LINE, typeof l.lineId === 'string' ? l.lineId : null, i) };
    if (seen.has(l.lineId)) return { failure: fail(CODE.DUPLICATE_LINE, l.lineId, i) };
    seen.add(l.lineId);
    if (!isSafe(l.receivedMilli) || l.receivedMilli < 0 || !isSafe(l.damagedMilli) || l.damagedMilli < 0) {
      return { failure: fail(CODE.BAD_QTY, l.lineId, i) };
    }
    if (l.damagedMilli > l.receivedMilli) return { failure: fail(CODE.DAMAGED_OVER_RECEIVED, l.lineId, i) };
    if (!isSafe(l.priceMinor) || l.priceMinor < 0) return { failure: fail(CODE.BAD_PRICE, l.lineId, i) };
  }
  const kept = nonZero(lines);
  if (kept.length === 0) return { failure: fail(CODE.NOTHING_RECEIVED) };

  let before;
  try { before = new Map(progress(o, prior).map((p) => [p.lineId, p])); } catch (e) {
    if (e instanceof RangeError) return { failure: fail(CODE.OVERFLOW) };
    throw e;
  }
  let total = 0;
  const posted = [];
  for (const l of kept) {
    const i = lines.indexOf(l);
    const expected = before.get(l.lineId).remainingMilli;
    const accepted = l.receivedMilli - l.damagedMilli;
    const over = Math.max(0, accepted - expected);
    if (over > 0 && !allowOver) return { failure: fail(CODE.OVER_ORDERED, l.lineId, i) };
    const lt = totalOrNull(accepted, l.priceMinor);
    if (lt === null) return { failure: fail(CODE.OVERFLOW, l.lineId, i) };
    try { total = add(total, lt); } catch (e) { return { failure: fail(CODE.OVERFLOW, l.lineId, i) }; }
    posted.push({
      lineId: l.lineId, expectedMilli: expected, receivedMilli: l.receivedMilli, damagedMilli: l.damagedMilli,
      acceptedMilli: accepted, shortMilli: Math.max(0, expected - l.receivedMilli), overMilli: over,
      priceMinor: l.priceMinor, totalMinor: lt,
    });
  }
  const receipt = { id: input.id, lines: kept };
  let after;
  //  جمع‌های همهٔ رسیدها با هم هم باید در سقف بمانند، تا status/totals روی دادهٔ ثبت‌شده هرگز نشکنند
  try {
    const all = prior.concat([receipt]);
    totals(o, all);
    after = status(o, all);
  } catch (e) {
    if (e instanceof RangeError) return { failure: fail(CODE.OVERFLOW) };
    throw e;
  }
  return { ok: { receipt, lines: posted, totalMinor: total, status: after } };
}

function totals(o, receipts) {
  const price = new Map(o.lines.map((l) => [l.id, l.priceMinor]));
  let accepted = 0;
  for (const r of receipts || []) {
    for (const l of r.lines) {
      if (price.has(l.lineId)) accepted = add(accepted, lineTotalMinor(l.receivedMilli - l.damagedMilli, l.priceMinor));
    }
  }
  const remaining = progress(o, receipts).reduce((acc, p) => add(acc, lineTotalMinor(p.remainingMilli, price.get(p.lineId))), 0);
  return { orderedMinor: orderTotalMinor(o), acceptedMinor: accepted, remainingMinor: remaining };
}

/* ── تطبیقِ سه‌طرفه (PUR-14) ─────────────────────────────────────── */

function match(o, receipts, bills, tol) {
  const t = { qtyMilli: 0, priceBp: 0, absMinor: 0, ...(tol || {}) };
  if (t.qtyMilli < 0 || t.priceBp < 0 || t.absMinor < 0) throw new RangeError('purchase-order-core: negative tolerance');
  const byId = new Map(o.lines.map((l) => [l.id, l]));
  const byProduct = new Map(o.lines.map((l) => [l.productId, l]));
  const billedQty = new Map();
  const billedSum = new Map();
  const outQty = new Map();
  const outSum = new Map();
  for (const b of bills || []) {
    const line = (b.lineId != null && byId.get(b.lineId)) || byProduct.get(b.productId);
    if (line) {
      billedQty.set(line.id, add(billedQty.get(line.id) || 0, b.qtyMilli));
      billedSum.set(line.id, add(billedSum.get(line.id) || 0, b.totalMinor));
    } else {
      outQty.set(b.productId, add(outQty.get(b.productId) || 0, b.qtyMilli));
      outSum.set(b.productId, add(outSum.get(b.productId) || 0, b.totalMinor));
    }
  }
  const prog = new Map(progress(o, receipts).map((p) => [p.lineId, p]));
  const lines = [];
  let accepted = 0;
  let billed = 0;
  for (const l of o.lines) {
    const p = prog.get(l.id);
    const bq = billedQty.get(l.id) || 0;
    const bs = billedSum.get(l.id) || 0;
    const expected = lineTotalMinor(bq, l.priceMinor);
    const issues = [];
    if (p.acceptedMilli > l.qtyMilli) issues.push(ISSUE.RECEIVED_OVER_ORDER);
    if (bq - p.acceptedMilli > t.qtyMilli) issues.push(ISSUE.BILLED_OVER_RECEIVED);
    if (p.acceptedMilli - bq > t.qtyMilli) issues.push(ISSUE.BILLED_UNDER_RECEIVED);
    const allow = Math.max(t.absMinor, mulDivRound(Math.abs(expected), t.priceBp, BP));
    if (bs - expected > allow) issues.push(ISSUE.PRICE_OVER_ORDER);
    if (expected - bs > allow) issues.push(ISSUE.PRICE_UNDER_ORDER);
    accepted = add(accepted, lineTotalMinor(p.acceptedMilli, l.priceMinor));
    billed = add(billed, bs);
    lines.push({
      lineId: l.id, productId: l.productId, orderedMilli: l.qtyMilli, acceptedMilli: p.acceptedMilli,
      billedMilli: bq, orderPriceMinor: l.priceMinor, expectedMinor: expected, billedMinor: bs, issues,
    });
  }
  for (const pid of [...outQty.keys()].sort()) {
    const bs = outSum.get(pid);
    billed = add(billed, bs);
    lines.push({
      lineId: null, productId: pid, orderedMilli: 0, acceptedMilli: 0, billedMilli: outQty.get(pid),
      orderPriceMinor: 0, expectedMinor: 0, billedMinor: bs, issues: [ISSUE.NOT_ON_ORDER],
    });
  }
  return { lines, matched: lines.every((x) => x.issues.length === 0), orderedMinor: orderTotalMinor(o), acceptedMinor: accepted, billedMinor: billed };
}

module.exports = {
  MAX_SAFE, MAX_LINES, MAX_ID, MAX_REF, MILLI, BP,
  STATUS, STATE, STATES, ACTION, CODE, ISSUE,
  mulDivRound, lineTotalMinor, blank, idOk, refOk,
  checkOrder, orderTotalMinor, progress, status, allowed, edit, send, cancel,
  receive, totals, match,
};
