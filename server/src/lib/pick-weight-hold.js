'use strict';
/**
 * وزنِ بیش از حد تا پاسخِ مشتری (S56 «وزنِ نهایی»، §۱۶؛ PCK-04) — پشتِ پرچمِ خاموشِ `shop.orders.weight_hold`
 * (با `shop.orders.picking`؛ درها در `routes/order-picking.js` و `routes/pick-substitutes-store.js` می‌سنجند).
 * ⛔ خاموش ⇒ هیچ ردیف و همان ردِ پیشینِ `over_tolerance`.
 *
 *   نگه‌داشت ── وزنِ واقعیِ بیش از حدِ مجاز (±۱۰٪) دیگر رد نمی‌شود: قلم «مانده» می‌ماند و یک نگه‌داشت با مبلغِ همان
 *               وزن (قیمتِ واحدِ ثبت‌شدهٔ سفارش) و مهلت ساخته می‌شود. بسته‌بندی تا پاسخ ۴۰۹ `weight_hold_pending`.
 *               سفارشِ پرداخت‌شده نگه‌داشت نمی‌گیرد (پولِ بیشتر را راهی برای گرفتن نیست ⇒ همان `over_tolerance`).
 *   پاسخ ───── مشتری با لینکِ پیگیری (همان توکنِ امضاشدهٔ ویترین) یا کارمند پس از تماس (`orders.manage`):
 *               پذیرش ⇒ قلم «برداشته شد» با همان وزن و مبلغ؛ رد ⇒ قلم «حذف شد» و از جمع کسر (PCK-07).
 *   مهلت ───── بی‌پاسخ تا مهلت ⇒ رد (policy: بی رضایت هرگز گران‌تر نمی‌شود) — هر جا خوانده شود، بی کارِ زمان‌بندی‌شده.
 *   جمع‌آور ── وزنِ تازه، «پیدا نکردم» یا «از نو» روی همان قلم نگه‌داشتِ باز/پذیرفته را پس می‌گیرد (سابقه می‌ماند).
 *   سقف ────── بسته‌بندی جمعِ اقلام را با `capOf` و **هر قلم** را با `overCap` می‌سنجد: هر قلم تا مقدارِ سفارش (وزنی + حدِ
 *               مجاز)، و قلمِ نگه‌داشتِ پذیرفته تا همان مبلغی که مشتری پذیرفت — بیشتر ⇒ ۴۰۹ `over_cap` (سپرِ سرور، نه فقط
 *               سنجشِ رویداد؛ SR47-2: قلمِ حذف‌شده بیشیِ قلمِ دیگر را نمی‌پوشاند).
 *
 * ⛔ ترتیبِ قفل‌ها همان جمع‌آوری: `order_picks` ⇐ قلم ⇐ نگه‌داشت.
 */
const db = require('../db');
const { now, tx } = db;
const P = require('./pick-core');
const orders = require('./orders');
const { ulid } = require('./ulid');
const { badRequest, notFound, conflict } = require('../middleware/errors');

const FLAG_KEY = 'shop.orders.weight_hold';
const WAIT_MIN = 10;
const MINUTE = 60 * 1000;
const ID_RE = /^[0-9A-Z]{26}$/;
//  پولی که دکان گرفته — جمعِ بیشتر گرفتنی نیست (همان `PAID` ِ بسته‌بندی)
const PAID = ['paid', 'partially_refunded'];

const str = (v) => (typeof v === 'string' ? v.trim() : '');

function withData(err, data) {
  err.data = data;
  return err;
}

/** پرچمِ نگه‌داشت برای این دکان (درها پرچمِ جمع‌آوری را هم جدا می‌سنجند). */
async function flagOf(shopId, run = null) {
  return orders.flagOf(shopId, FLAG_KEY, run);
}

function holdView(r, at = now()) {
  const expired = r.status === 'pending' && Number(r.deadline_at) <= at;
  const total = Number(r.total_minor);
  const original = Number(r.original_total_minor);
  return {
    id: r.id,
    lineNo: Number(r.line_no),
    orderedQtyMilli: Number(r.ordered_qty_milli),
    actualQtyMilli: Number(r.actual_qty_milli),
    unitPriceMinor: Number(r.unit_price_minor),
    originalTotalMinor: original,
    totalMinor: total,
    extraMinor: Math.max(0, total - original),
    status: r.status,
    decidedBy: r.decided_by,
    decidedAt: r.decided_at === null || r.decided_at === undefined ? null : Number(r.decided_at),
    deadlineAt: Number(r.deadline_at),
    expired,
    //  مهلت گذشته و هنوز بسته نشده: همین اعمال می‌شود (بسته‌بندی یا تصمیمِ بعدی آن را می‌بندد)
    ...(expired ? { willApply: 'reject' } : {}),
    createdAt: Number(r.created_at),
  };
}

// ════════════════════════════════════════════════════════════════════
//  درونِ تراکنشِ جمع‌آوری (پس از قفلِ برگه و قلم)
// ════════════════════════════════════════════════════════════════════

/** نگه‌داشتِ باز/پذیرفتهٔ قلم پس گرفته می‌شود (وزنِ تازه، «پیدا نکردم»، «از نو») — سابقه می‌ماند. */
async function withdrawLine(c, orderId, lineNo, actorId, at) {
  await c.query(
    `UPDATE order_pick_weight_holds SET status='withdrawn', decided_by='picker', decided_actor=$3, decided_at=$4, updated_at=$4
      WHERE order_id=$1 AND line_no=$2 AND status IN ('pending','accepted')`, [orderId, lineNo, actorId || '', at]
  );
}

/** قلم نگه‌داشتِ «در انتظار» دارد؟ («از نو» روی قلمِ وزنی‌ای که هنوز اسکن نشده) */
async function lineHeld(c, orderId, lineNo) {
  return Boolean((await c.query(
    `SELECT 1 FROM order_pick_weight_holds WHERE order_id=$1 AND line_no=$2 AND status='pending' LIMIT 1`, [orderId, lineNo]
  )).rows[0]);
}

/**
 * وزنِ بیش از حد ⇒ نگه‌داشتِ تازه (نگه‌داشتِ پیشینِ همان قلم پس گرفته می‌شود)؛ پرچمِ خاموش یا سفارشِ
 * پرداخت‌شده ⇒ null (همان ردِ `over_tolerance`).
 */
async function openIn(c, { shopId, orderId, line, actualMilli, eventKey, actorId, at }) {
  if (!(await flagOf(shopId, (t, p) => c.query(t, p)))) return null;
  const o = (await c.query('SELECT payment_status FROM online_orders WHERE shop_id=$1 AND id=$2', [shopId, orderId])).rows[0];
  if (!o || PAID.includes(o.payment_status)) return null;
  const lineNo = Number(line.line_no);
  await withdrawLine(c, orderId, lineNo, actorId, at);
  const price = Number(line.unit_price_minor);
  return (await c.query(
    `INSERT INTO order_pick_weight_holds (id, order_id, shop_id, line_no, ordered_qty_milli, actual_qty_milli, unit_price_minor,
       original_total_minor, total_minor, deadline_at, event_key, proposed_by, created_at, updated_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$13) RETURNING *`,
    [ulid(at), orderId, shopId, lineNo, Number(line.ordered_qty_milli), actualMilli, price, Number(line.original_total_minor),
      P.lineTotalMinor(actualMilli, price), at + WAIT_MIN * MINUTE, eventKey, actorId || '', at]
  )).rows[0];
}

/** نگه‌داشتِ ساخته‌شده با همین رویداد (تکرارِ رویدادِ «وزن») */
async function ofEvent(c, shopId, eventKey) {
  const r = (await c.query('SELECT * FROM order_pick_weight_holds WHERE shop_id=$1 AND event_key=$2', [shopId, eventKey])).rows[0];
  return r ? holdView(r) : null;
}

/** پذیرش ⇒ قلم «برداشته شد» با همان وزن؛ رد ⇒ قلم «حذف شد» (کسر در بسته‌بندی). */
async function apply(c, h, decision, by, actorId, at) {
  const status = decision === 'accept' ? 'accepted' : 'rejected';
  await c.query(
    `UPDATE order_pick_weight_holds SET status=$2, decided_by=$3, decided_actor=$4, decided_at=$5, updated_at=$5 WHERE id=$1`,
    [h.id, status, by, actorId || '', at]
  );
  await c.query(
    `UPDATE order_pick_lines SET status=$3, picked_qty_milli=$4, updated_at=$5 WHERE order_id=$1 AND line_no=$2 AND status='pending'`,
    [h.order_id, Number(h.line_no), status === 'accepted' ? 'picked' : 'removed', status === 'accepted' ? Number(h.actual_qty_milli) : 0, at]
  );
  await c.query('UPDATE order_picks SET version = version + 1, updated_at=$2 WHERE order_id=$1', [h.order_id, at]);
  return status;
}

/** نگه‌داشت‌های مهلت‌گذشته ⇒ رد (درونِ تراکنشِ فراخواننده، پس از قفلِ برگه) */
async function applyTimeouts(c, orderId, at = now()) {
  const rows = (await c.query(
    `SELECT * FROM order_pick_weight_holds WHERE order_id=$1 AND status='pending' AND deadline_at <= $2 ORDER BY line_no FOR UPDATE`,
    [orderId, at]
  )).rows;
  for (const r of rows) await apply(c, r, 'reject', 'timeout', 'system', at);
  return rows.length;
}

/** نگه‌داشتِ هنوز در انتظار (مهلت نگذشته) ⇒ بسته‌بندی صبر می‌کند */
async function pendingOf(c, orderId) {
  return (await c.query(
    `SELECT id, line_no, deadline_at FROM order_pick_weight_holds WHERE order_id=$1 AND status='pending' ORDER BY deadline_at`,
    [orderId]
  )).rows;
}

/** شمارهٔ قلم ⇒ مبلغی که مشتری پذیرفت */
async function acceptedOf(c, orderId) {
  const rows = (await c.query(
    `SELECT line_no, total_minor FROM order_pick_weight_holds WHERE order_id=$1 AND status='accepted'`, [orderId]
  )).rows;
  return new Map(rows.map((r) => [Number(r.line_no), Number(r.total_minor)]));
}

/**
 * سقفِ هر قلمِ برگه (شمارهٔ قلم ⇒ مبلغ، پیش از تخفیف و کرایه؛ `capOf` = جمعِ آن‌ها): هر قلم تا مقدارِ سفارش (وزنی: + حدِ مجاز)،
 * جایگزین هرگز بیش از اصل (قیمت و مقدارِ کمتر، PCK-06)، و قلمِ نگه‌داشتِ پذیرفته تا همان مبلغی که مشتری پذیرفت. قلم‌ها = `lineView` ِ برگه.
 */
function capsOf(lines, accepted = new Map()) {
  const caps = new Map();
  for (const l of lines) {
    if (accepted.has(l.lineNo)) {
      caps.set(l.lineNo, accepted.get(l.lineNo));
      continue;
    }
    const ordered = Number(l.orderedQtyMilli);
    const max = l.weighed ? ordered + Math.floor((ordered * P.DEFAULT_TOLERANCE_BP) / 10000) : ordered;
    caps.set(l.lineNo, Math.max(Number(l.originalTotalMinor) || 0, P.lineTotalMinor(max, Number(l.unitPriceMinor))));
  }
  return caps;
}

function capOf(lines, accepted = new Map()) {
  let cap = 0;
  for (const v of capsOf(lines, accepted).values()) cap += v;
  return cap;
}

/**
 * SR47-2: قلم‌هایی که جمعِ تسویه‌شده‌شان (`settle().lines`) از سقفِ **خودشان** بیشتر است — سنجشِ جمع به تنهایی بیشیِ یک قلمِ
 * بی رضایت را با سهمِ قلمِ حذف‌شده یا کم‌برداشته می‌پوشاند.
 */
function overCap(lines, settled, accepted = new Map()) {
  const caps = capsOf(lines, accepted);
  return settled.filter((l) => l.totalMinor > (caps.get(l.lineNo) || 0)).map((l) => l.lineNo);
}

/** همهٔ نگه‌داشت‌های یک برگه (برای برگهٔ جمع‌آور) */
async function forSheet(run, orderId, at = now()) {
  const rows = (await run(
    'SELECT * FROM order_pick_weight_holds WHERE order_id=$1 ORDER BY line_no, created_at, id', [orderId]
  )).rows;
  return rows.map((r) => holdView(r, at));
}

// ════════════════════════════════════════════════════════════════════
//  تصمیم (مشتری، کارمند، مهلت)
// ════════════════════════════════════════════════════════════════════

function parseDecision(body) {
  const b = body && typeof body === 'object' ? body : {};
  const decision = str(b.decision);
  if (!['accept', 'reject'].includes(decision)) throw badRequest('تصمیم: accept یا reject', 'bad_decision');
  return decision;
}

/**
 * تصمیمِ یک نگه‌داشت. `by`: `customer` (لینک) یا `staff` (پس از تماس). تکرارِ همان تصمیم ⇒ همان (`replayed`)؛
 * تصمیمِ دیگر پس از بسته شدن ⇒ ۴۰۹ `already_decided`؛ مهلتِ گذشته ⇒ رد اعمال و ۴۰۹ `weight_hold_expired`.
 */
async function decide(shopId, orderId, holdId, body, { by, actorId }) {
  const id = str(orderId);
  if (!ID_RE.test(id)) throw notFound('سفارش پیدا نشد', 'order_not_found');
  const hid = str(holdId);
  if (!ID_RE.test(hid)) throw notFound('نگه‌داشت پیدا نشد', 'weight_hold_not_found');
  const decision = parseDecision(body);
  const out = await tx(async (c) => {
    const pick = (await c.query('SELECT * FROM order_picks WHERE shop_id=$1 AND order_id=$2 FOR UPDATE', [shopId, id])).rows[0];
    if (!pick) {
      const order = (await c.query('SELECT 1 FROM online_orders WHERE shop_id=$1 AND id=$2', [shopId, id])).rows[0];
      if (!order) throw notFound('سفارش پیدا نشد', 'order_not_found');
      throw notFound('جمع‌آوریِ این سفارش هنوز شروع نشده است', 'pick_not_started');
    }
    const h = (await c.query(
      'SELECT * FROM order_pick_weight_holds WHERE shop_id=$1 AND order_id=$2 AND id=$3 FOR UPDATE', [shopId, id, hid]
    )).rows[0];
    if (!h) throw notFound('نگه‌داشت پیدا نشد', 'weight_hold_not_found');
    const at = now();
    if (h.status === 'pending' && Number(h.deadline_at) <= at) {
      await apply(c, h, 'reject', 'timeout', 'system', at);
      return { expired: true };
    }
    if (h.status !== 'pending') {
      const same = (decision === 'accept' && h.status === 'accepted') || (decision === 'reject' && h.status === 'rejected');
      if (same) return { row: h, replayed: true };
      throw withData(conflict('برای این وزن پیش‌تر تصمیم گرفته شده است', 'already_decided'), { status: h.status, decidedBy: h.decided_by });
    }
    if (pick.status !== 'picking') throw conflict('برگهٔ جمع‌آوریِ این سفارش بسته است', 'pick_closed');
    await apply(c, h, decision, by, actorId, at);
    return { row: (await c.query('SELECT * FROM order_pick_weight_holds WHERE id=$1', [hid])).rows[0], replayed: false };
  });
  if (out.expired) {
    throw withData(conflict('مهلتِ پاسخ گذشته است؛ قلم حذف و از جمع کسر شد', 'weight_hold_expired'), { status: 'rejected' });
  }
  return { hold: holdView(out.row), replayed: out.replayed };
}

// ════════════════════════════════════════════════════════════════════
//  مشتری (با توکنِ پیگیریِ ویترین)
// ════════════════════════════════════════════════════════════════════

/** نمای مشتری — نه شناسهٔ کارمند، نه رویداد؛ فقط آن‌چه برای تصمیم لازم است */
function customerView(r, line, at) {
  const v = holdView(r, at);
  return {
    id: v.id, lineNo: v.lineNo, status: v.status, expired: v.expired, deadlineAt: v.deadlineAt,
    ...(v.expired ? { willApply: v.willApply } : {}),
    decidedBy: v.decidedBy === 'customer' || v.decidedBy === 'timeout' ? v.decidedBy : (v.decidedBy ? 'shop' : ''),
    name: line ? line.name : '', unit: line ? line.unit : '',
    orderedQtyMilli: v.orderedQtyMilli, actualQtyMilli: v.actualQtyMilli, unitPriceMinor: v.unitPriceMinor,
    originalTotalMinor: v.originalTotalMinor, totalMinor: v.totalMinor, extraMinor: v.extraMinor,
  };
}

/** سفارشِ همین ویترین از توکن — یا ۴۰۴ (توکنِ نادرست، سفارشِ دیگر)؛ TEN-03: لینکِ منقضی/باطل همان ۴۱۰ ِ پیگیری */
async function orderOfToken(store, token) {
  const id = await require('./storefront').orderIdOfLink(store, token);
  const o = (await db.query('SELECT source FROM online_orders WHERE shop_id=$1 AND id=$2', [store.shop_id, id])).rows[0];
  if (!o || o.source !== 'storefront') throw notFound('سفارش پیدا نشد', 'order_not_found');
  return id;
}

async function linesOf(orderId) {
  return new Map((await db.query('SELECT line_no, name, unit FROM order_pick_lines WHERE order_id=$1', [orderId])).rows
    .map((l) => [Number(l.line_no), l]));
}

async function forCustomer(store, token) {
  const id = await orderOfToken(store, token);
  const rows = (await db.query(
    `SELECT * FROM order_pick_weight_holds WHERE shop_id=$1 AND order_id=$2 AND status <> 'withdrawn'
      ORDER BY line_no, created_at, id`, [store.shop_id, id]
  )).rows;
  const at = now();
  //  `now`: ساعتِ سرور تا شمارشِ «… دقیقه مانده» ِ کارت با ساعتِ گوشی کج نشود (داور همان مهلتِ سرور است)
  if (!rows.length) return { weightHolds: [], now: at };
  const lines = await linesOf(id);
  return { weightHolds: rows.map((r) => customerView(r, lines.get(Number(r.line_no)), at)), now: at };
}

/**
 * کلیدِ `weightHold` ِ پیگیریِ ویترین (`lib/store-order-view.js`): فقط با هر دو پرچم و دستِ‌کم یک نگه‌داشتِ «در انتظار» ِ
 * مهلت‌نگذشته ⇒ `{open}`؛ وگرنه undefined (نه اسکریپتِ `store/weight-hold.js`، نه واژه‌نامه‌اش، نه کارت). فقط خواندن.
 */
async function trackView(shopId, orderId, at = now()) {
  if (!(await require('./order-picking').flagOf(shopId)) || !(await flagOf(shopId))) return undefined;
  const open = Number((await db.query(
    `SELECT COUNT(*)::int AS n FROM order_pick_weight_holds WHERE shop_id=$1 AND order_id=$2 AND status='pending' AND deadline_at > $3`,
    [shopId, orderId, at]
  )).rows[0].n);
  return open ? { open } : undefined;
}

async function decideByCustomer(store, token, holdId, body) {
  const id = await orderOfToken(store, token);
  const out = await decide(store.shop_id, id, holdId, body, { by: 'customer', actorId: '' });
  const row = (await db.query('SELECT * FROM order_pick_weight_holds WHERE id=$1', [out.hold.id])).rows[0];
  return { weightHold: customerView(row, (await linesOf(id)).get(out.hold.lineNo), now()), replayed: out.replayed };
}

module.exports = {
  FLAG_KEY, WAIT_MIN,
  flagOf, holdView, withdrawLine, lineHeld, openIn, ofEvent, applyTimeouts, pendingOf, acceptedOf, capsOf, capOf, overCap, forSheet,
  decide, forCustomer, decideByCustomer, trackView,
};
