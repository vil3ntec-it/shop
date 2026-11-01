'use strict';
/**
 * قفسهٔ انتظار و اسکنِ «آماده» (PCK-10) و جمع‌آوریِ چند سفارش با هم (PCK-12) — v3 §۶.۱۷، پشتِ همان
 * پرچمِ خاموشِ `shop.orders.picking` (درها در `routes/order-picking.js` می‌سنجند).
 *
 *   قفسه ──── کیسهٔ بسته‌شده با برچسبِ QR ِ خودش (`VLNB-<سفارش>-<کیسه>-<همه>`، PCK-09) روی یک جای قفسهٔ
 *             انتظار اسکن می‌شود. اسکنِ دوباره بی‌خطر است (هر کیسه یک ردیف). همهٔ کیسه‌ها اسکن شد ⇒
 *             «آماده»: `ready_scanned_at`، و برای «دریافت از دکان» گذارِ `packed ⇒ ready_for_pickup` فقط با
 *             `orders.transitionIn`. ارسالی همان «بسته‌بندی‌شده» می‌ماند تا پیک بردارد (گذارِ پیک دست نمی‌خورد).
 *             برچسبِ کهنه (تعدادِ کیسهٔ دیگر) یا سفارشِ بسته‌نشده پذیرفته نمی‌شود.
 *   گروهی ─── ۲ تا ۱۰ سفارشِ «قبول‌شده» با یک کلیدِ یکتایی یک دسته می‌شوند: برای هر کدام همان برگهٔ
 *             جمع‌آوری (`order-picking.start`) با `batch_id` ِ مشترک، و یک فهرستِ یک‌جا به ترتیبِ قفسه
 *             (`pick-core.pickOrder`) که هر قلم می‌گوید در «سبدِ» کدام سفارش برود. رویداد و بسته‌بندی همان
 *             درهای هر سفارش‌اند (کسرِ خودکار و کیسه جدا برای هر سفارش).
 *
 * ⛔ شناسهٔ دسته از کلیدِ یکتایی ساخته می‌شود ⇒ تکرارِ همان درخواست همان دسته. همهٔ سفارش‌ها پیش از هر
 * نوشتن سنجیده می‌شوند (دکانِ دیگر ⇒ ۴۰۴، «قبول‌نشده» یا در دستهٔ دیگر ⇒ ۴۰۹ و هیچ برگه‌ای).
 */
const crypto = require('crypto');
const db = require('../db');
const { now, tx } = db;
const P = require('./pick-core');
const orders = require('./orders');
const picking = require('./order-picking');
const { badRequest, notFound, conflict } = require('../middleware/errors');

const ID_RE = /^[0-9A-Z]{26}$/;
const BATCH_RE = /^B[0-9a-f]{24}$/;
const LABEL_RE = /^VLNB-(\d{1,9})-(\d{1,2})-(\d{1,2})$/;
const MIN_BATCH = 2;
const MAX_BATCH = 10;

const str = (v) => (typeof v === 'string' ? v.trim() : '');
const num = (v) => (v === null || v === undefined ? null : Number(v));

function withData(err, data) {
  err.data = data;
  return err;
}

// ════════════════════════════════════════════════════════════════════
//  PCK-10 — قفسهٔ انتظار
// ════════════════════════════════════════════════════════════════════

/** برچسبِ کیسه ⇒ `{orderNo, bag, of}` — رقمِ فارسی و فاصلهٔ بارکدخوان پاک؛ وگرنه ۴۰۰ */
function parseLabel(raw) {
  const s = P.latin(str(raw)).replace(/\s+/g, '').toUpperCase();
  const m = LABEL_RE.exec(s);
  if (!m) throw badRequest('برچسبِ کیسه شناخته نشد', 'bad_label');
  const out = { orderNo: Number(m[1]), bag: Number(m[2]), of: Number(m[3]) };
  if (out.bag < 1 || out.of < 1 || out.bag > out.of || out.of > picking.MAX_BAGS) throw badRequest('برچسبِ کیسه شناخته نشد', 'bad_label');
  return out;
}

function slotOf(raw) {
  const s = P.locPart(str(raw));
  if (!s) throw badRequest('جای قفسهٔ انتظار لازم است', 'slot_required');
  return s;
}

/**
 * اسکنِ یک کیسه روی قفسهٔ انتظار. ⇒ `{orderId, orderNo, slot, bag, bags, scanned, ready, readyNow, status}`
 */
async function scan(shopId, body, actor) {
  const b = body && typeof body === 'object' ? body : {};
  const label = parseLabel(b.qr);
  const slot = slotOf(b.slot);
  return tx(async (c) => {
    const order = (await c.query(
      'SELECT id, order_no, status, fulfillment FROM online_orders WHERE shop_id=$1 AND order_no=$2 FOR UPDATE', [shopId, label.orderNo]
    )).rows[0];
    if (!order) throw notFound('سفارش پیدا نشد', 'order_not_found');
    const pick = (await c.query('SELECT * FROM order_picks WHERE order_id=$1 FOR UPDATE', [order.id])).rows[0];
    if (!pick) throw notFound('جمع‌آوریِ این سفارش هنوز شروع نشده است', 'pick_not_started');
    if (pick.status !== 'packed') throw withData(conflict('این سفارش هنوز بسته‌بندی نشده است', 'not_packed'), { pickStatus: pick.status });
    if (!['packed', 'ready_for_pickup'].includes(order.status)) {
      throw withData(conflict('این سفارش دیگر روی قفسهٔ انتظار نیست', 'order_not_on_shelf'), { current: order.status });
    }
    const bags = Number(pick.bags);
    if (label.of !== bags) {
      throw withData(conflict('این برچسب با تعدادِ کیسه‌های بسته نمی‌خواند', 'label_mismatch'), { bags });
    }
    const at = now();
    await c.query(
      `INSERT INTO order_pick_shelf_scans (order_id, shop_id, bag, slot, scanned_by, scanned_at) VALUES ($1,$2,$3,$4,$5,$6)
       ON CONFLICT (order_id, bag) DO UPDATE SET slot=excluded.slot, scanned_by=excluded.scanned_by, scanned_at=excluded.scanned_at`,
      [order.id, shopId, label.bag, slot, actor.id || '', at]
    );
    const scanned = Number((await c.query(
      'SELECT COUNT(*)::int AS n FROM order_pick_shelf_scans WHERE order_id=$1 AND bag <= $2', [order.id, bags]
    )).rows[0].n);
    const ready = scanned >= bags;
    const readyNow = ready && (pick.ready_scanned_at === null || pick.ready_scanned_at === undefined);
    await c.query(
      `UPDATE order_picks SET shelf_slot=$2, ready_scanned_at = CASE WHEN $3 THEN COALESCE(ready_scanned_at, $4) ELSE ready_scanned_at END,
              version=version+1, updated_at=$4 WHERE order_id=$1`,
      [order.id, slot, ready, at]
    );
    let status = order.status;
    if (readyNow && order.fulfillment === 'pickup' && order.status === 'packed') {
      await orders.transitionIn(c, shopId, order.id, { to: 'ready_for_pickup', from: 'packed' }, actor);
      status = 'ready_for_pickup';
    }
    return { orderId: order.id, orderNo: Number(order.order_no), slot, bag: label.bag, bags, scanned, ready, readyNow, status };
  });
}

/** آن‌چه روی قفسهٔ انتظار است (بسته‌شده و هنوز تحویل‌نشده)، به ترتیبِ جا ⇒ `{shelf:[…]}` */
async function shelf(shopId) {
  const rows = (await db.query(
    `SELECT p.order_id, p.shelf_slot, p.bags, p.ready_scanned_at, p.packed_at, o.order_no, o.status, o.fulfillment,
            (SELECT COUNT(*)::int FROM order_pick_shelf_scans s WHERE s.order_id = p.order_id AND s.bag <= p.bags) AS scanned
       FROM order_picks p JOIN online_orders o ON o.id = p.order_id AND o.shop_id = p.shop_id
      WHERE p.shop_id=$1 AND p.status='packed' AND p.shelf_slot <> '' AND o.status IN ('packed','ready_for_pickup')
      ORDER BY p.packed_at LIMIT 200`, [shopId]
  )).rows;
  const list = rows.map((r) => ({
    orderId: r.order_id, orderNo: Number(r.order_no), slot: r.shelf_slot, bags: Number(r.bags), scanned: Number(r.scanned),
    ready: r.ready_scanned_at !== null && r.ready_scanned_at !== undefined, readyAt: num(r.ready_scanned_at),
    status: r.status, fulfillment: r.fulfillment,
  }));
  //  همان ترتیبِ طبیعیِ جای قفسه (A2 پیش از A10)
  list.sort((a, b) => P.compareNatural(a.slot, b.slot) || (a.orderNo - b.orderNo));
  return { shelf: list };
}

// ════════════════════════════════════════════════════════════════════
//  PCK-12 — جمع‌آوریِ گروهی
// ════════════════════════════════════════════════════════════════════

function batchIdOf(shopId, key) {
  return `B${crypto.createHash('sha256').update(`pick-batch\n${shopId}\n${key}`).digest('hex').slice(0, 24)}`;
}

function parseBatch(body) {
  const b = body && typeof body === 'object' ? body : {};
  const ids = Array.isArray(b.orderIds) ? b.orderIds.map(str) : null;
  if (!ids || ids.length < MIN_BATCH || ids.length > MAX_BATCH) {
    throw badRequest(`${MIN_BATCH} تا ${MAX_BATCH} سفارش`, 'bad_batch');
  }
  if (new Set(ids).size !== ids.length) throw badRequest('سفارشِ تکراری در دسته', 'bad_batch');
  for (const id of ids) if (!ID_RE.test(id)) throw notFound('سفارش پیدا نشد', 'order_not_found');
  return ids;
}

/**
 * دستهٔ تازه با کلیدِ یکتایی. ⇒ `{batch, replayed}`. هیچ برگه‌ای ساخته نمی‌شود مگر همه سنجیده شوند.
 */
async function createBatch(shopId, { key, body }, actor) {
  const ids = parseBatch(body);
  const batchId = batchIdOf(shopId, key);
  const rows = (await db.query(
    `SELECT o.id, o.status, p.batch_id, p.status AS pick_status FROM online_orders o
       LEFT JOIN order_picks p ON p.order_id = o.id
      WHERE o.shop_id=$1 AND o.id = ANY($2::text[])`, [shopId, ids]
  )).rows;
  if (rows.length !== ids.length) throw notFound('سفارش پیدا نشد', 'order_not_found');
  if (rows.every((r) => r.batch_id === batchId)) return { batch: await batch(shopId, batchId), replayed: true };
  for (const r of rows) {
    if (r.batch_id && r.batch_id !== batchId) throw withData(conflict('این سفارش در دستهٔ دیگری است', 'already_batched'), { orderId: r.id });
    if (r.pick_status && r.pick_status !== 'picking') throw withData(conflict('برگهٔ این سفارش بسته است', 'pick_closed'), { orderId: r.id });
    if (!r.pick_status && r.status !== 'accepted' && r.status !== 'picking') {
      throw withData(conflict('فقط سفارشِ «قبول‌شده» جمع‌آوری می‌شود', 'order_not_pickable'), { orderId: r.id, current: r.status });
    }
  }
  for (const id of ids) await picking.start(shopId, id, actor);
  const r = await db.query(
    `UPDATE order_picks SET batch_id=$3, version=version+1, updated_at=$4
      WHERE shop_id=$1 AND order_id = ANY($2::text[]) AND (batch_id='' OR batch_id=$3) AND status='picking' RETURNING order_id`,
    [shopId, ids, batchId, now()]
  );
  if (r.rows.length !== ids.length) {
    //  هم‌زمان کسی یکی را در دستهٔ دیگر گذاشت یا بست ⇒ این دسته ناقص نمی‌ماند
    await db.query(`UPDATE order_picks SET batch_id='' WHERE shop_id=$1 AND batch_id=$2`, [shopId, batchId]);
    throw conflict('یکی از سفارش‌ها هم‌زمان عوض شد؛ دوباره بسازید', 'batch_conflict');
  }
  return { batch: await batch(shopId, batchId), replayed: false };
}

/**
 * فهرستِ یک‌جای دسته به ترتیبِ قفسه. هر قلم: سفارش، «سبد» (۱..n به ترتیبِ شمارهٔ سفارش)، قلم و وضعیت.
 */
async function batch(shopId, rawId) {
  const batchId = str(rawId);
  if (!BATCH_RE.test(batchId)) throw notFound('دسته پیدا نشد', 'batch_not_found');
  const ids = (await db.query(
    'SELECT order_id FROM order_picks WHERE shop_id=$1 AND batch_id=$2', [shopId, batchId]
  )).rows.map((r) => r.order_id);
  if (!ids.length) throw notFound('دسته پیدا نشد', 'batch_not_found');
  const sheets = [];
  for (const id of ids) sheets.push(await picking.sheet(shopId, id));
  sheets.sort((a, b) => a.orderNo - b.orderNo);
  const rows = [];
  sheets.forEach((s, i) => {
    for (const l of s.lines) {
      rows.push({
        orderId: s.orderId, orderNo: s.orderNo, bin: i + 1, lineNo: l.lineNo, productId: l.productId, name: l.name,
        unit: l.unit, status: l.status, orderedQtyMilli: l.orderedQtyMilli, pickedQtyMilli: l.pickedQtyMilli,
        weighed: l.weighed, location: l.location,
      });
    }
  });
  const order = P.pickOrder(rows.map((r, i) => ({
    lineNo: i, name: r.name, aisle: r.location.aisle, shelf: r.location.shelf, level: r.location.level,
  })));
  const pending = rows.filter((r) => r.status === 'pending').length;
  return {
    batchId,
    orders: sheets.map((s, i) => ({ orderId: s.orderId, orderNo: s.orderNo, bin: i + 1, status: s.status, progress: s.progress })),
    lines: order.map((i) => rows[i]),
    progress: { total: rows.length, pending, done: rows.length - pending },
  };
}

module.exports = { MIN_BATCH, MAX_BATCH, parseLabel, scan, shelf, createBatch, batch, batchIdOf };
