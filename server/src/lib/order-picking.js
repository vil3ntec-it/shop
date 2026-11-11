'use strict';
/**
 * جمع‌آوری و بسته‌بندیِ سفارش (PCK، v3 §۶.۱۷؛ سناریوهای S31 و S37) — پشتِ پرچمِ خاموشِ
 * `shop.orders.picking`.
 *
 *   پرچم ──── `shop.orders.picking` در «تنظیماتِ زنده» (scope = شناسهٔ دکان یا `all`؛ مقدارِ
 *             خودِ دکان جلو می‌افتد و فقط `true` روشن است — همان `orders.flagOf`). ⛔ پیش‌فرض
 *             خاموش: هیچ ردیفی نوشته نمی‌شود، ویترین و سفارش همان رفتارِ پیشین را دارند.
 *   شروع ──── «قبول‌شده» ⇒ «در حال جمع‌آوری» (فقط با `orders.transitionIn`) و برگهٔ جمع‌آوری با
 *             جای قفسهٔ هر کالا (PCK-01). تکرار همان برگه را برمی‌گرداند.
 *   رویداد ── هر کارِ جمع‌آور یک رویداد با کلیدِ یکتای دستگاه (صفِ آفلاین بی‌خطر دوباره می‌فرستد؛
 *             همان کلید با بدنهٔ دیگر ⇒ رد). اسکن فقط با بارکدِ همان کالا (PCK-02؛ اشتباه ⇒ رد و
 *             شمارش)، «پیدا نکردم» همیشه (PCK-03)، وزنِ واقعی در حدِ مجاز (PCK-04)؛ بیش از حد ⇒ رد، یا با پرچمِ
 *             خاموشِ `shop.orders.weight_hold` نگه‌داشت تا پاسخِ مشتری (S56، `lib/pick-weight-hold.js`).
 *   پیدانشد ─ وظیفهٔ بررسی برای مدیر و برداشتنِ کالا از ویترین تا رسیدگی (PCK-14).
 *   بسته ──── همهٔ قلم‌ها تکلیف‌دار ⇒ جمعِ تازه با `pick-core.settle` (کسرِ خودکارِ قلمِ پیدانشده و کم،
 *             PCK-07)، قلم‌های سفارش و مبلغِ نقدِ موردِ انتظارِ مأموریتِ هنوز‌نرفته در همان تراکنش، و
 *             «بسته‌بندی‌شده» با تعدادِ کیسه و برچسب (PCK-09). مبلغِ پیشین در برگه و رویداد می‌ماند.
 *             ⛔ سفارشِ پرداخت‌شده: هر تغییرِ مبلغ ⇒ ۴۰۹ (`paid_needs_refund` برای کسر، `paid_total_increase`
 *             برای افزایش) — کسر فقط با «پس داده شد» ِ دستی و پرچمِ `shop.orders.manual_refund` (‎lib/order-pack-refund.js‎).
 *   شاخص ──── زمانِ جمع‌آوری، درصدِ پیدا شدن و نرخِ جایگزینی از برگه‌های بسته‌شده (PCK-13).
 *
 * ⛔ موجودیِ دکان دست نمی‌خورد (تصمیمِ صاحبِ مخزن، ۱۴۰۵/۰۷/۲۷): فروش را برنامه هنگامِ تحویل با
 * قلم‌های **تازهٔ** سفارش ثبت می‌کند. ترتیبِ قفل‌ها: `online_orders` ⇐ `order_picks` ⇐ قلم‌ها.
 */
const crypto = require('crypto');
const db = require('../db');
const { many, now, tx } = db;
const orders = require('./orders');
const P = require('./pick-core');
const { ulid } = require('./ulid');
const { stable } = require('./idempotency');
const { badRequest, notFound, conflict, ApiError } = require('../middleware/errors');
//  جایگزینی (PCK-05/06) — دیر بار می‌شود (آن پرونده هم پرچمِ همین‌جا را می‌خواند)
const subs = () => require('./pick-substitutes');
const packRefund = () => require('./order-pack-refund');   //  تصمیمِ ۱-۶ ب: «پس داده شد» ِ دستی
//  S56: وزنِ بیش از حد تا پاسخِ مشتری (پرچمِ خاموشِ `shop.orders.weight_hold`) — دیر بار می‌شود
const holds = () => require('./pick-weight-hold');

const FLAG_KEY = 'shop.orders.picking';
const KEY_RE = /^[A-Za-z0-9_.:-]{8,128}$/;
const ID_RE = /^[0-9A-Z]{26}$/;
const MAX_EVENTS = 100;
const MAX_BAGS = 50;
const MAX_QTY_MILLI = 10_000_000;
const EVENT_TYPES = ['scan', 'pick', 'weigh', 'not_found', 'reset'];
//  مأموریتی که بسته هنوز از دکان بیرون نرفته — مبلغِ نقدِ موردِ انتظارش با جمعِ تازه عوض می‌شود
const BEFORE_DISPATCH = ['requested', 'assigned'];
const DISPATCHED = ['picked_up', 'moving', 'arrived', 'failed'];
//  پولی که دکان گرفته و هنوز (کامل) برنگشته — جمعِ سفارش در بسته‌بندی دست نمی‌خورد
const PAID = ['paid', 'partially_refunded'];

// ════════════════════════════════════════════════════════════════════
//  پرچم
// ════════════════════════════════════════════════════════════════════

/** پرچمِ جمع‌آوری برای این دکان (بی `shop.orders` هم خوانده می‌شود؛ درها جدا می‌سنجند). */
async function flagOf(shopId, run = null) {
  return orders.flagOf(shopId, FLAG_KEY, run);
}

// ════════════════════════════════════════════════════════════════════
//  ورودی
// ════════════════════════════════════════════════════════════════════

const str = (v) => (typeof v === 'string' ? v.trim() : '');
const num = (v) => (v === null || v === undefined ? null : Number(v));
const asJson = (x) => {
  if (x && typeof x === 'object') return x;
  try { return JSON.parse(x) || {}; } catch { return {}; }
};
const hashOf = (obj) => crypto.createHash('sha256').update(stable(obj)).digest('hex');

function text(v, max) {
  const { stripControl } = require('./validate');
  return stripControl(str(v)).slice(0, max);
}

function keyOf(raw, field = 'شناسهٔ یکتای رویداد') {
  const k = str(raw);
  if (!k) throw badRequest(`${field} لازم است`, 'event_id_required');
  if (!KEY_RE.test(k)) throw badRequest(`${field} نامعتبر است`, 'bad_event_id');
  return k;
}

function orderIdOf(raw) {
  const id = str(raw);
  if (!ID_RE.test(id)) throw notFound('سفارش پیدا نشد', 'order_not_found');
  return id;
}

function qtyOf(v, field) {
  if (typeof v !== 'number' || !Number.isSafeInteger(v) || v <= 0 || v > MAX_QTY_MILLI) {
    throw badRequest(`${field} باید عددِ صحیحِ مثبت (هزارم) باشد`, 'bad_qty');
  }
  return v;
}

/** یک رویدادِ جمع‌آور ⇒ شکلِ سنجیده (بی تکیه بر ترتیبِ کلیدها؛ اثرِ انگشت از همین). */
function parseEvent(raw) {
  const e = raw && typeof raw === 'object' ? raw : {};
  const eventId = keyOf(e.eventId);
  const type = str(e.type);
  if (!EVENT_TYPES.includes(type)) throw badRequest('نوعِ رویداد معتبر نیست', 'bad_event_type');
  const lineNo = Number(e.lineNo);
  if (!Number.isSafeInteger(lineNo) || lineNo < 1 || lineNo > 10_000) throw badRequest('شمارهٔ قلم معتبر نیست', 'bad_line');
  const out = { eventId, type, lineNo };
  if (type === 'scan') {
    out.barcode = P.normBarcode(e.barcode);
    if (!out.barcode) throw badRequest('بارکد خالی است', 'bad_barcode');
    out.qtyMilli = e.qtyMilli === undefined || e.qtyMilli === null ? 1000 : qtyOf(e.qtyMilli, 'مقدارِ اسکن');
  } else if (type === 'pick') {
    out.qtyMilli = qtyOf(e.qtyMilli, 'مقدارِ برداشته');
  } else if (type === 'weigh') {
    out.qtyMilli = qtyOf(e.qtyMilli, 'وزنِ واقعی');
  }
  return out;
}

//  کالا: بارکد و بودن — `lib/pick-barcodes.js`
const { barcodesOf } = require('./pick-barcodes');
//  جای قفسه (PCK-01) و وظیفهٔ «پیدا نکردم» (PCK-14) — `lib/pick-issues.js`
const issuesLib = require('./pick-issues');
const { openIssue, undoIssue } = issuesLib;

// ════════════════════════════════════════════════════════════════════
//  مدلِ خواندنی
// ════════════════════════════════════════════════════════════════════

/** متنِ QRِ برچسبِ کیسه (PCK-09): `VLNB-<شمارهٔ سفارش>-<کیسه>-<همهٔ کیسه‌ها>` — فقط رقم و خطِ تیره */
function labelsOf(orderNo, bags) {
  const out = [];
  for (let i = 1; i <= bags; i++) out.push({ bag: i, of: bags, qr: `VLNB-${orderNo}-${i}-${bags}` });
  return out;
}

function lineView(l) {
  return {
    lineNo: Number(l.line_no),
    productId: l.product_id,
    name: l.name,
    unit: l.unit,
    status: l.status,
    orderedQtyMilli: Number(l.ordered_qty_milli),
    pickedQtyMilli: Number(l.picked_qty_milli),
    unitPriceMinor: Number(l.unit_price_minor),
    originalTotalMinor: Number(l.original_total_minor),
    weighed: l.weighed === true,
    location: { aisle: l.aisle, shelf: l.shelf, level: l.level },
    scans: Number(l.scans),
    wrongScans: Number(l.wrong_scans),
    //  PCK-05/06: جایگزینِ پذیرفته‌شده (کالا، مقدار و قیمتِ کمتری که مشتری می‌پردازد)
    substitute: l.status === 'substituted' ? asJson(l.substitute) : null,
  };
}

const coreLine = (v) => ({
  lineNo: v.lineNo, name: v.name, status: v.status, orderedQtyMilli: v.orderedQtyMilli,
  pickedQtyMilli: v.pickedQtyMilli,
  unitPriceMinor: v.status === 'substituted' && v.substitute ? Number(v.substitute.chargedUnitPriceMinor) : v.unitPriceMinor,
  originalTotalMinor: v.originalTotalMinor,
  aisle: v.location.aisle, shelf: v.location.shelf, level: v.location.level,
});

async function loadSheet(run, shopId, orderId) {
  const pick = (await run('SELECT * FROM order_picks WHERE shop_id=$1 AND order_id=$2', [shopId, orderId])).rows[0];
  if (!pick) return null;
  const order = (await run(
    'SELECT order_no, status, discount_minor, delivery_fee_minor, total_minor FROM online_orders WHERE shop_id=$1 AND id=$2',
    [shopId, orderId]
  )).rows[0];
  const raw = (await run('SELECT * FROM order_pick_lines WHERE order_id=$1 ORDER BY line_no', [orderId])).rows.map(lineView);
  const byNo = new Map(raw.map((l) => [l.lineNo, l]));
  const lines = P.pickOrder(raw.map(coreLine)).map((n) => byNo.get(n));
  const packed = pick.status === 'packed';
  //  پیش از بسته: جمعِ تا این لحظه با تخفیف و ارسالِ امروزِ سفارش؛ پس از بسته: همان جمعِ ثبت‌شده
  const s = P.settle({
    lines: raw.map(coreLine),
    discountMinor: Number(order.discount_minor),
    deliveryFeeMinor: Number(order.delivery_fee_minor),
  });
  const pending = raw.filter((l) => l.status === 'pending').length;
  const weightHolds = await holds().forSheet(run, orderId);
  return {
    orderId,
    orderNo: Number(order.order_no),
    orderStatus: order.status,
    status: pick.status,
    pickerId: pick.picker_id,
    bags: Number(pick.bags),
    startedAt: Number(pick.started_at),
    packedAt: num(pick.packed_at),
    version: Number(pick.version),
    toleranceBp: P.DEFAULT_TOLERANCE_BP,
    lines,
    progress: { total: raw.length, pending, done: raw.length - pending },
    totals: {
      originalTotalMinor: Number(pick.original_total_minor),
      subtotalMinor: s.subtotalMinor,
      discountMinor: s.discountMinor,
      deliveryFeeMinor: s.deliveryFeeMinor,
      totalMinor: packed ? Number(pick.final_total_minor) : s.totalMinor,
      deductedMinor: s.deductedMinor,
    },
    labels: packed ? labelsOf(Number(order.order_no), Number(pick.bags)) : [],
    substitutions: await subs().forSheet(run, orderId),
    //  S56: نگه‌داشتِ وزن — فقط وقتی ردیفی هست (پرچمِ خاموش ⇒ همان شکلِ پیشینِ برگه)
    ...(weightHolds.length ? { weightHolds } : {}),
  };
}

/** برگهٔ یک سفارش؛ سفارشِ این دکان که هنوز جمع‌آوری نشده ⇒ null (سفارشِ دکانِ دیگر ⇒ ۴۰۴). */
async function sheet(shopId, orderId) {
  const id = orderIdOf(orderId);
  const out = await loadSheet((t, p) => db.query(t, p), shopId, id);
  if (out) return out;
  const exists = (await db.query('SELECT 1 FROM online_orders WHERE shop_id=$1 AND id=$2', [shopId, id])).rows[0];
  if (!exists) throw notFound('سفارش پیدا نشد', 'order_not_found');
  return null;
}

// ════════════════════════════════════════════════════════════════════
//  شروع
// ════════════════════════════════════════════════════════════════════

/**
 * شروعِ جمع‌آوری. «قبول‌شده» ⇒ «در حال جمع‌آوری»؛ سفارشی که پیش‌تر (بی پرچم) «در حال جمع‌آوری»
 * شده هم برگه می‌گیرد. برگهٔ موجود ⇒ همان (`replayed`)، بی رویدادِ دوم.
 * @returns {{sheet:object, replayed:boolean}}
 */
async function start(shopId, orderId, actor) {
  const id = orderIdOf(orderId);
  return tx(async (c) => {
    const run = (t, p) => c.query(t, p);
    const order = (await c.query(
      'SELECT * FROM online_orders WHERE shop_id=$1 AND id=$2 FOR UPDATE', [shopId, id]
    )).rows[0];
    if (!order) throw notFound('سفارش پیدا نشد', 'order_not_found');
    const prev = (await c.query('SELECT order_id FROM order_picks WHERE order_id=$1', [id])).rows[0];
    if (prev) return { sheet: await loadSheet(run, shopId, id), replayed: true };

    if (order.status === 'accepted') {
      await orders.transitionIn(c, shopId, id, { to: 'picking', from: 'accepted' }, actor);
    } else if (order.status !== 'picking') {
      const err = conflict('فقط سفارشِ «قبول‌شده» جمع‌آوری می‌شود', 'order_not_pickable');
      err.data = { current: order.status };
      throw err;
    }

    const at = now();
    const lines = (await c.query('SELECT * FROM online_order_lines WHERE order_id=$1 ORDER BY line_no', [id])).rows;
    const ids = [...new Set(lines.map((l) => l.product_id).filter(Boolean))];
    const locs = new Map((ids.length ? (await c.query(
      'SELECT product_id, aisle, shelf, level FROM pick_locations WHERE shop_id=$1 AND product_id = ANY($2::text[])',
      [shopId, ids]
    )).rows : []).map((r) => [r.product_id, r]));

    await c.query(
      `INSERT INTO order_picks (order_id, shop_id, status, picker_id, original_subtotal_minor, original_total_minor,
         started_at, updated_at)
       VALUES ($1,$2,'picking',$3,$4,$5,$6,$6)`,
      [id, shopId, actor.id || '', Number(order.subtotal_minor), Number(order.total_minor), at]
    );
    for (const l of lines) {
      const loc = locs.get(l.product_id) || {};
      await c.query(
        `INSERT INTO order_pick_lines (order_id, shop_id, line_no, product_id, name, unit, ordered_qty_milli,
           unit_price_minor, original_total_minor, weighed, aisle, shelf, level, updated_at)
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14)`,
        [id, shopId, Number(l.line_no), l.product_id, l.name, l.unit, Number(l.qty_milli), Number(l.unit_price_minor),
          Number(l.line_total_minor), P.isWeighedUnit(l.unit), loc.aisle || '', loc.shelf || '', loc.level || '', at]
      );
    }
    await c.query(
      `INSERT INTO order_pick_events (id, order_id, shop_id, event_key, request_hash, kind, outcome, detail, actor_id, created_at)
       VALUES ($1,$2,$3,$4,'','start','accepted',$5::jsonb,$6,$7)`,
      [ulid(at), id, shopId, `start:${id}`, JSON.stringify({ lines: lines.length }), actor.id || '', at]
    );
    return { sheet: await loadSheet(run, shopId, id), replayed: false };
  });
}

// ════════════════════════════════════════════════════════════════════
//  رویدادهای جمع‌آور
// ════════════════════════════════════════════════════════════════════

const REJECT = {
  pick_closed: 'برگهٔ جمع‌آوریِ این سفارش بسته است',
  line_not_found: 'این قلم در سفارش نیست',
  line_done: 'تکلیفِ این قلم روشن شده؛ برای دوباره «از نو» بزنید',
  wrong_item: 'این کالا مالِ این قلم نیست',
  scan_required: 'این کالا بارکد دارد؛ اسکن کنید',
  not_weighed: 'این قلم وزنی نیست',
  over_qty: 'بیش از مقدارِ سفارش',
  over_tolerance: 'وزن از حدِ مجاز بیشتر است؛ کمتر بردارید',
  nothing_to_reset: 'این قلم هنوز دست نخورده است',
  event_key_reused: 'این شناسهٔ رویداد پیش‌تر برای کارِ دیگری به کار رفته است',
};

const rejection = (code, extra = {}) => ({ status: 'rejected', code, message: REJECT[code] || 'مجاز نیست', ...extra });

/**
 * یک رویداد در تراکنشِ خودش. ⇒ `{eventId, status: accepted|rejected, code?, line?, replayed?}`.
 * ردشده‌ها هم ثبت می‌شوند تا تکرارِ همان رویداد همان جواب را بگیرد (و اسکنِ اشتباه شمرده شود).
 */
async function applyEvent(shopId, orderId, ev, actor) {
  const hash = hashOf({ orderId, ...ev });
  return tx(async (c) => {
    const pick = (await c.query(
      'SELECT * FROM order_picks WHERE shop_id=$1 AND order_id=$2 FOR UPDATE', [shopId, orderId]
    )).rows[0];
    if (!pick) throw notFound('جمع‌آوریِ این سفارش هنوز شروع نشده است', 'pick_not_started');

    const prev = (await c.query(
      'SELECT order_id, request_hash, outcome, code, line_no FROM order_pick_events WHERE shop_id=$1 AND event_key=$2',
      [shopId, ev.eventId]
    )).rows[0];
    if (prev) {
      if (prev.request_hash !== hash || prev.order_id !== orderId) return { eventId: ev.eventId, ...rejection('event_key_reused') };
      const line = (await c.query('SELECT * FROM order_pick_lines WHERE order_id=$1 AND line_no=$2', [orderId, ev.lineNo])).rows[0];
      const hold = ev.type === 'weigh' && prev.outcome === 'accepted' ? await holds().ofEvent(c, shopId, ev.eventId) : null;
      return {
        eventId: ev.eventId, status: prev.outcome, replayed: true,
        ...(prev.code ? { code: prev.code, message: REJECT[prev.code] || '' } : {}),
        ...(line ? { line: lineView(line) } : {}),
        ...(hold ? { hold } : {}),
      };
    }

    const at = now();
    const record = async (outcome, code, detail = {}) => {
      await c.query(
        `INSERT INTO order_pick_events (id, order_id, shop_id, event_key, request_hash, kind, line_no, outcome, code,
           detail, actor_id, created_at)
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10::jsonb,$11,$12)`,
        [ulid(at), orderId, shopId, ev.eventId, hash, ev.type, ev.lineNo, outcome, code, JSON.stringify(detail),
          actor.id || '', at]
      );
    };
    const reject = async (code, detail = {}, extra = {}) => {
      await record('rejected', code, detail);
      return { eventId: ev.eventId, ...rejection(code, extra) };
    };

    const orderStatus = (await c.query('SELECT status FROM online_orders WHERE id=$1', [orderId])).rows[0]?.status;
    if (pick.status !== 'picking' || orderStatus !== 'picking') return reject('pick_closed', { orderStatus });

    const line = (await c.query(
      'SELECT * FROM order_pick_lines WHERE order_id=$1 AND line_no=$2 FOR UPDATE', [orderId, ev.lineNo]
    )).rows[0];
    if (!line) return reject('line_not_found');

    const ordered = Number(line.ordered_qty_milli);
    const picked = Number(line.picked_qty_milli);
    const open = line.status === 'pending';
    let next = null;           // { status, picked }
    let hold = null;           // S56: نگه‌داشتِ تازهٔ وزن
    const detail = {};

    switch (ev.type) {
      case 'scan': {
        if (!open) return reject('line_done');
        const codes = await barcodesOf((t, p) => c.query(t, p), shopId, line.product_id);
        if (!P.barcodeMatches(codes || [], ev.barcode)) {
          //  PCK-02: کالای اشتباه پذیرفته نمی‌شود — شمرده می‌شود (شاخص و بازبینی)
          await c.query(
            'UPDATE order_pick_lines SET wrong_scans = wrong_scans + 1, updated_at=$3 WHERE order_id=$1 AND line_no=$2',
            [orderId, ev.lineNo, at]
          );
          return reject('wrong_item', { barcode: ev.barcode });
        }
        detail.barcode = ev.barcode;
        if (line.weighed) {
          //  کالای وزنی: اسکن فقط «همین کالاست»؛ مقدار را وزن می‌گوید
          next = { status: 'pending', picked };
        } else {
          if (picked + ev.qtyMilli > ordered) return reject('over_qty', { pickedMilli: picked, orderedMilli: ordered });
          const p = picked + ev.qtyMilli;
          next = { status: p === ordered ? 'picked' : 'pending', picked: p };
        }
        await c.query('UPDATE order_pick_lines SET scans = scans + 1 WHERE order_id=$1 AND line_no=$2', [orderId, ev.lineNo]);
        break;
      }
      case 'pick': {
        if (!open) return reject('line_done');
        //  تأییدِ دستی فقط برای کالای بی‌بارکد (یا قلمِ دستیِ بی‌کالا) — PCK-02
        const codes = await barcodesOf((t, p) => c.query(t, p), shopId, line.product_id);
        if (codes && codes.length) return reject('scan_required');
        if (line.weighed) return reject('scan_required', { hint: 'weigh' });
        if (picked + ev.qtyMilli > ordered) return reject('over_qty', { pickedMilli: picked, orderedMilli: ordered });
        const p = picked + ev.qtyMilli;
        next = { status: p === ordered ? 'picked' : 'pending', picked: p };
        break;
      }
      case 'weigh': {
        if (!open) return reject('line_done');
        if (!line.weighed) return reject('not_weighed');
        const codes = await barcodesOf((t, p) => c.query(t, p), shopId, line.product_id);
        if (codes && codes.length && Number(line.scans) === 0) return reject('scan_required');
        const verdict = P.weighVerdict(ordered, ev.qtyMilli, P.DEFAULT_TOLERANCE_BP);
        if (verdict === 'over') {
          //  S56: پرچمِ روشن و سفارشِ پرداخت‌نشده ⇒ قلم باز می‌ماند و مشتری می‌پذیرد یا رد می‌کند
          hold = await holds().openIn(c, { shopId, orderId, line, actualMilli: ev.qtyMilli, eventKey: ev.eventId, actorId: actor.id, at });
          if (!hold) {
            const maxMilli = ordered + Math.floor((ordered * P.DEFAULT_TOLERANCE_BP) / 10000);
            return reject('over_tolerance', { actualMilli: ev.qtyMilli, maxMilli }, { maxMilli });
          }
          detail.verdict = verdict;
          detail.hold = hold.id;
          next = { status: 'pending', picked };
          break;
        }
        await holds().withdrawLine(c, orderId, ev.lineNo, actor.id, at);
        detail.verdict = verdict;
        next = { status: verdict === 'ok' ? 'picked' : 'short', picked: ev.qtyMilli };
        break;
      }
      case 'not_found': {
        //  PCK-03: همیشه پذیرفته — مگر تکلیفِ قلم روشن شده باشد
        if (!open) return reject('line_done');
        await holds().withdrawLine(c, orderId, ev.lineNo, actor.id, at);
        next = { status: picked > 0 ? 'short' : 'not_found', picked };
        if (line.product_id) {
          await openIssue(c, shopId, line.product_id, orderId,
            { name: line.name, orderedMilli: ordered, pickedMilli: picked, by: actor.id || '' }, at);
          detail.issue = true;
        }
        break;
      }
      case 'reset': {
        if (open && picked === 0 && Number(line.scans) === 0 && !(await holds().lineHeld(c, orderId, ev.lineNo))) return reject('nothing_to_reset');
        if ((line.status === 'not_found' || line.status === 'short' || line.status === 'substituted') && line.product_id) {
          await undoIssue(c, shopId, line.product_id, orderId, at);
        }
        //  PCK-05: پیشنهادِ باز یا پذیرفتهٔ همین قلم پس گرفته می‌شود
        await subs().withdrawLine(c, orderId, ev.lineNo, actor.id || '', at);
        await holds().withdrawLine(c, orderId, ev.lineNo, actor.id, at);   //  S56
        detail.from = line.status;
        next = { status: 'pending', picked: 0 };
        await c.query('UPDATE order_pick_lines SET scans = 0 WHERE order_id=$1 AND line_no=$2', [orderId, ev.lineNo]);
        break;
      }
      default:
        return reject('bad_event_type');
    }

    await c.query(
      `UPDATE order_pick_lines SET status=$3, picked_qty_milli=$4, updated_at=$5 WHERE order_id=$1 AND line_no=$2`,
      [orderId, ev.lineNo, next.status, next.picked, at]
    );
    await c.query(
      'UPDATE order_picks SET version = version + 1, updated_at=$2, picker_id = COALESCE(NULLIF($3, \'\'), picker_id) WHERE order_id=$1',
      [orderId, at, actor.id || '']
    );
    await record('accepted', '', { ...detail, status: next.status, pickedMilli: next.picked });
    const after = (await c.query('SELECT * FROM order_pick_lines WHERE order_id=$1 AND line_no=$2', [orderId, ev.lineNo])).rows[0];
    return { eventId: ev.eventId, status: 'accepted', line: lineView(after), ...(hold ? { hold: holds().holdView(hold, at) } : {}) };
  });
}

/**
 * دسته‌ای از رویدادها به ترتیبِ دستگاه، هر کدام جدا (یکی ردشد، بقیه می‌روند).
 * ⇒ `{results, sheet}`
 */
async function events(shopId, orderId, body, actor) {
  const id = orderIdOf(orderId);
  const list = body && Array.isArray(body.events) ? body.events : null;
  if (!list || list.length === 0) throw badRequest('رویدادی نیامده است', 'bad_events');
  if (list.length > MAX_EVENTS) throw badRequest(`حداکثر ${MAX_EVENTS} رویداد`, 'bad_events');
  const exists = (await db.query('SELECT 1 FROM order_picks WHERE shop_id=$1 AND order_id=$2', [shopId, id])).rows[0];
  if (!exists) {
    const order = (await db.query('SELECT 1 FROM online_orders WHERE shop_id=$1 AND id=$2', [shopId, id])).rows[0];
    if (!order) throw notFound('سفارش پیدا نشد', 'order_not_found');
    throw notFound('جمع‌آوریِ این سفارش هنوز شروع نشده است', 'pick_not_started');
  }
  const results = [];
  for (const raw of list) {
    let ev;
    try {
      ev = parseEvent(raw);
    } catch (err) {
      results.push({ eventId: str(raw && raw.eventId), status: 'rejected', code: err.code || 'bad_event', message: err.message });
      continue;
    }
    results.push(await applyEvent(shopId, id, ev, actor));
  }
  //  S56: نگه‌داشتِ تازه ⇒ لینکِ پیگیری و واتس‌اپ ِ مشتری (همان راهِ پیشنهادِ جایگزین)
  if (results.some((r) => r.hold && !r.replayed)) {
    const order = (await db.query('SELECT id, source, customer_phone FROM online_orders WHERE shop_id=$1 AND id=$2', [shopId, id])).rows[0];
    const customer = await subs().customerLinks(shopId, order, actor);
    for (const r of results) if (r.hold && !r.replayed) r.customer = customer;
  }
  return { results, sheet: await loadSheet((t, p) => db.query(t, p), shopId, id) };
}

// ════════════════════════════════════════════════════════════════════
//  بسته‌بندی
// ════════════════════════════════════════════════════════════════════

/**
 * پایانِ جمع‌آوری: جمعِ تازه (PCK-07)، قلم‌های سفارش، نقدِ موردِ انتظارِ مأموریتِ هنوز‌نرفته و
 * «بسته‌بندی‌شده» — همه در یک تراکنش. تکرارِ همان کلید ⇒ همان برگه.
 * @returns {{sheet:object, replayed:boolean, deductedMinor:number}}
 */
async function pack(shopId, orderId, { key, body }, actor) {
  const id = orderIdOf(orderId);
  const b = body && typeof body === 'object' ? body : {};
  const bags = b.bags;
  if (typeof bags !== 'number' || !Number.isSafeInteger(bags) || bags < 1 || bags > MAX_BAGS) {
    throw badRequest(`تعدادِ کیسه ۱ تا ${MAX_BAGS}`, 'bad_bags');
  }
  return tx(async (c) => {
    const run = (t, p) => c.query(t, p);
    const order = (await c.query('SELECT * FROM online_orders WHERE shop_id=$1 AND id=$2 FOR UPDATE', [shopId, id])).rows[0];
    if (!order) throw notFound('سفارش پیدا نشد', 'order_not_found');
    const pick = (await c.query('SELECT * FROM order_picks WHERE order_id=$1 FOR UPDATE', [id])).rows[0];
    if (!pick) throw notFound('جمع‌آوریِ این سفارش هنوز شروع نشده است', 'pick_not_started');
    if (pick.status === 'packed') {
      if (pick.pack_key === key) {
        return { sheet: await loadSheet(run, shopId, id), replayed: true, deductedMinor: 0 };
      }
      throw conflict('این سفارش پیش‌تر بسته‌بندی شده است', 'already_packed');
    }
    if (pick.status !== 'picking' || order.status !== 'picking') {
      const err = conflict('این سفارش دیگر در حالِ جمع‌آوری نیست', 'order_not_picking');
      err.data = { current: order.status };
      throw err;
    }

    //  PCK-05: مهلتِ گذشته ⇒ ترجیحِ مشتری؛ پیشنهادِ هنوز در انتظار ⇒ صبر (بی رضایت گران‌تر نمی‌شود)
    await subs().applyTimeouts(c, id, now());
    const waiting = await subs().pendingOf(c, id);
    if (waiting.length) {
      const err = conflict('مشتری هنوز دربارهٔ جایگزین تصمیم نگرفته است', 'substitution_pending');
      err.data = { pending: waiting.length, deadlineAt: Number(waiting[0].deadline_at) };
      throw err;
    }
    //  S56: وزنِ بیش از حد — مهلتِ گذشته ⇒ حذف و کسر؛ هنوز در انتظار ⇒ صبر (بی رضایت گران‌تر نمی‌شود)
    await holds().applyTimeouts(c, id, now());
    const held = await holds().pendingOf(c, id);
    if (held.length) {
      const err = conflict('مشتری هنوز دربارهٔ وزنِ بیشتر تصمیم نگرفته است', 'weight_hold_pending');
      err.data = { pending: held.length, deadlineAt: Number(held[0].deadline_at) };
      throw err;
    }
    const lines = (await c.query('SELECT * FROM order_pick_lines WHERE order_id=$1 ORDER BY line_no', [id])).rows.map(lineView);
    const s = P.settle({
      lines: lines.map(coreLine), discountMinor: Number(order.discount_minor), deliveryFeeMinor: Number(order.delivery_fee_minor),
    });
    if (s.pending > 0) {
      const err = new ApiError(409, 'lines_pending', `تکلیفِ ${s.pending} قلم هنوز روشن نیست`);
      err.data = { pending: s.pending };
      throw err;
    }
    if (s.subtotalMinor === 0) {
      throw conflict('هیچ قلمی برداشته نشد؛ سفارش را با دلیلِ «ناموجود» لغو کنید', 'nothing_picked');
    }
    //  سقفِ پول (سپرِ سرور): هر قلم تا مقدارِ سفارش + حدِ وزن، یا همان مبلغی که مشتری برای وزنِ بیشتر پذیرفت (S56)
    const accepted = await holds().acceptedOf(c, id);
    const capMinor = holds().capOf(lines, accepted);
    const overLines = holds().overCap(lines, s.lines, accepted);   //  SR47-2: هر قلم جدا
    if (s.subtotalMinor > capMinor || overLines.length) {
      const err = conflict('جمعِ اقلام از سقفِ سفارش و رضایتِ مشتری بیشتر است', 'over_cap');
      err.data = { capMinor, subtotalMinor: s.subtotalMinor, lines: overLines };
      throw err;
    }
    const live = (await c.query(
      'SELECT id, status FROM delivery_assignments WHERE order_id=$1 AND status = ANY($2::text[])', [id, DISPATCHED]
    )).rows[0];
    if (live) throw conflict('بسته پیش‌تر به پیک سپرده شده است', 'already_dispatched');
    //  ⛔ پولِ گرفته‌شده: جمعِ تازه نه کم می‌شود (بازپرداختِ بی‌ردپا) نه زیاد (پولِ بی‌رضایت)
    const paidMinor = Number(order.total_minor);
    const manual = PAID.includes(order.payment_status) && s.totalMinor < paidMinor ? await packRefund().allowIn(run, shopId, b, actor) : null;
    if (PAID.includes(order.payment_status) && !manual && (s.deductedMinor > 0 || s.totalMinor !== paidMinor)) {
      const down = s.deductedMinor > 0;
      const err = new ApiError(409, down ? 'paid_needs_refund' : 'paid_total_increase', down
        ? 'این سفارش پرداخت شده است؛ کم کردنِ مبلغ بازپرداخت می‌خواهد — همهٔ قلم‌ها را بردارید یا سفارش را لغو و بازپرداخت کنید'
        : 'این سفارش پرداخت شده است؛ مبلغِ بیشتر بی رضایتِ مشتری گرفته نمی‌شود — همان مقدارِ سفارش را بردارید');
      err.data = { paidMinor, totalMinor: s.totalMinor, deductedMinor: s.deductedMinor, manualRefund: down && await packRefund().offered(run, shopId, actor) };
      throw err;
    }

    const at = now();
    //  قلم‌های سفارش = آن‌چه واقعاً برداشته شد (برنامه فروشِ تحویل را از همین‌ها می‌سازد)
    const byNo = new Map(lines.map((l) => [l.lineNo, l]));
    for (const l of s.lines) {
      const sub = byNo.get(l.lineNo).status === 'substituted' ? byNo.get(l.lineNo).substitute : null;
      if (sub && l.qtyMilli > 0) {
        //  PCK-05/06: قلمِ سفارش = کالای جایگزین با قیمتِ کمتر (فروشِ تحویل و موجودیِ برنامه از همین)
        await c.query(
          `UPDATE online_order_lines SET product_id=$3, name=$4, unit=$5, qty_milli=$6, unit_price_minor=$7, line_total_minor=$8
            WHERE order_id=$1 AND line_no=$2`,
          [id, l.lineNo, sub.productId, text(sub.name, 200), text(sub.unit, 30), l.qtyMilli, Number(sub.chargedUnitPriceMinor), l.totalMinor]
        );
      } else if (l.qtyMilli === 0) {
        await c.query('DELETE FROM online_order_lines WHERE order_id=$1 AND line_no=$2', [id, l.lineNo]);
      } else if (s.changedLines.includes(l.lineNo)) {
        await c.query(
          'UPDATE online_order_lines SET qty_milli=$3, line_total_minor=$4 WHERE order_id=$1 AND line_no=$2',
          [id, l.lineNo, l.qtyMilli, l.totalMinor]
        );
      }
    }
    //  O-06 = ب: نگه‌داشتهٔ بازِ سفارش = آن‌چه واقعاً برداشته شد (جایگزین ⇒ کالای جایگزین؛ برداشته‌نشده ⇒ آزاد)
    await require('./stock-reserve').syncOrderIn(c, shopId, id, at);
    const kept = s.lines.filter((l) => l.qtyMilli > 0).length;
    const moneyChanged = s.totalMinor !== Number(order.total_minor);
    await c.query(
      `UPDATE online_orders SET subtotal_minor=$3, discount_minor=$4, total_minor=$5, items_count=$6,
              version=version+1, updated_at=$7 WHERE shop_id=$1 AND id=$2`,
      [shopId, id, s.subtotalMinor, s.discountMinor, s.totalMinor, kept, at]
    );
    const refund = manual ? await packRefund().recordIn(c, { shopId, order, actor, refund: manual, paidMinor, totalMinor: s.totalMinor, at, key }) : null;
    //  مأموریتِ هنوز‌نرفته (دریافتِ حضوری یا پیکِ سپرده‌شده): نقدِ موردِ انتظار = جمعِ تازه
    if (moneyChanged && order.payment_method === 'cod' && order.payment_status !== 'paid') {
      await c.query(
        `UPDATE delivery_assignments SET expected_collect_minor=$2, version=version+1, updated_at=$3
          WHERE order_id=$1 AND status = ANY($4::text[])`,
        [id, s.totalMinor, at, BEFORE_DISPATCH]
      );
    }
    const removedNames = lines.filter((l) => s.removedLines.includes(l.lineNo)).map((l) => l.name);
    const seq = Number((await c.query(
      'SELECT COALESCE(MAX(seq), 0) + 1 AS n FROM online_order_events WHERE order_id=$1', [id]
    )).rows[0].n);
    const pickDetail = {
      originalTotalMinor: Number(order.total_minor), totalMinor: s.totalMinor, deductedMinor: s.deductedMinor,
      removedLines: s.removedLines, changedLines: s.changedLines, bags,
      substitutions: lines.filter((l) => l.status === 'substituted' && l.substitute).map((l) => ({
        lineNo: l.lineNo, from: l.productId, to: l.substitute.productId, chargedUnitPriceMinor: Number(l.substitute.chargedUnitPriceMinor),
      })),
      //  S56: وزنِ بیشتری که مشتری پذیرفت (ردشده‌ها در `removedLines`)
      ...(accepted.size ? { weightHolds: [...accepted].map(([lineNo, totalMinor]) => ({ lineNo, totalMinor })) } : {}),
    };
    await c.query(
      `INSERT INTO online_order_events (id, order_id, shop_id, seq, kind, from_status, to_status,
         actor_type, actor_id, reason, note, detail, created_at)
       VALUES ($1,$2,$3,$4,'note','','',$5,$6,'',$7,$8::jsonb,$9)`,
      [ulid(at), id, shopId, seq, actor.type, actor.id || '',
        text(s.deductedMinor > 0
          ? `جمع‌آوری: ${s.removedLines.length} قلم حذف و ${s.changedLines.length} قلم تغییر کرد${removedNames.length ? ` (${removedNames.join('، ')})` : ''}`
          : 'جمع‌آوری: همهٔ قلم‌ها برداشته شد', 300),
        JSON.stringify({ pick: pickDetail }), at]
    );
    await require('./delivery-prefs').dropStaleTenderIn(c, shopId, id, at);   //  CHK-24: جایگزین/«پیدا نشد» و اسکناسِ مشتری
    await orders.transitionIn(c, shopId, id, { to: 'packed', from: 'picking' }, actor);
    await c.query(
      `UPDATE order_picks SET status='packed', bags=$2, final_total_minor=$3, pack_key=$4, packed_at=$5,
              version=version+1, updated_at=$5 WHERE order_id=$1`,
      [id, bags, s.totalMinor, key, at]
    );
    await c.query(
      `INSERT INTO order_pick_events (id, order_id, shop_id, event_key, request_hash, kind, outcome, detail, actor_id, created_at)
       VALUES ($1,$2,$3,$4,$5,'pack','accepted',$6::jsonb,$7,$8)`,
      [ulid(at), id, shopId, `pack:${key}`, hashOf({ bags }), JSON.stringify(pickDetail), actor.id || '', at]
    );
    return { sheet: await loadSheet(run, shopId, id), replayed: false, deductedMinor: s.deductedMinor, refund };
  });
}

// ════════════════════════════════════════════════════════════════════
//  شاخص‌ها (PCK-13)
// ════════════════════════════════════════════════════════════════════

const DAY = 24 * 60 * 60 * 1000;
const MAX_RANGE = 92 * DAY;

async function metrics(shopId, { from, to } = {}) {
  const at = now();
  const t = to === undefined || to === '' ? at : Number(to);
  const f = from === undefined || from === '' ? t - 7 * DAY : Number(from);
  if (!Number.isSafeInteger(f) || !Number.isSafeInteger(t) || f > t) throw badRequest('بازه معتبر نیست', 'bad_range');
  if (t - f > MAX_RANGE) throw badRequest('بازه حداکثر ۹۲ روز', 'bad_range');
  const picks = await many(
    `SELECT order_id, started_at, packed_at FROM order_picks
      WHERE shop_id=$1 AND status='packed' AND packed_at >= $2 AND packed_at <= $3
      ORDER BY packed_at LIMIT 5000`, [shopId, f, t]
  );
  const ids = picks.map((p) => p.order_id);
  const lines = ids.length ? await many(
    'SELECT order_id, status FROM order_pick_lines WHERE order_id = ANY($1::text[])', [ids]
  ) : [];
  const byOrder = new Map();
  for (const l of lines) {
    if (!byOrder.has(l.order_id)) byOrder.set(l.order_id, []);
    byOrder.get(l.order_id).push({ status: l.status });
  }
  const wrong = ids.length ? await many(
    `SELECT COUNT(*)::int AS n FROM order_pick_events WHERE shop_id=$1 AND order_id = ANY($2::text[])
        AND kind='scan' AND code='wrong_item'`, [shopId, ids]
  ) : [{ n: 0 }];
  const m = P.metrics(picks.map((p) => ({
    startedAt: Number(p.started_at), packedAt: Number(p.packed_at), lines: byOrder.get(p.order_id) || [],
  })));
  return { from: f, to: t, ...m, wrongScans: Number(wrong[0]?.n || 0) };
}

module.exports = {
  FLAG_KEY, MAX_EVENTS, MAX_BAGS,
  flagOf, parseEvent, labelsOf, sheet, start, events, pack, metrics,
  //  `lib/pick-issues.js` — همان نام‌ها برای خواننده‌های پیشین
  setLocation: issuesLib.setLocation, issues: issuesLib.issues, resolveIssue: issuesLib.resolveIssue,
  heldIn: issuesLib.heldIn, unorderable: issuesLib.unorderable, applyHolds: issuesLib.applyHolds,
};
