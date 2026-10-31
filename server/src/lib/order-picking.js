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
 *             شمارش)، «پیدا نکردم» همیشه (PCK-03)، وزنِ واقعی در حدِ مجاز (PCK-04).
 *   پیدانشد ─ وظیفهٔ بررسی برای مدیر و برداشتنِ کالا از ویترین تا رسیدگی (PCK-14).
 *   بسته ──── همهٔ قلم‌ها تکلیف‌دار ⇒ جمعِ تازه با `pick-core.settle` (کسرِ خودکارِ قلمِ پیدانشده و کم،
 *             PCK-07)، قلم‌های سفارش و مبلغِ نقدِ موردِ انتظارِ مأموریتِ هنوز‌نرفته در همان تراکنش، و
 *             «بسته‌بندی‌شده» با تعدادِ کیسه و برچسب (PCK-09). مبلغِ پیشین در برگه و رویداد می‌ماند.
 *             ⛔ سفارشِ پرداخت‌شده: هر تغییرِ مبلغ ⇒ ۴۰۹ (`paid_needs_refund` برای کسر، `paid_total_increase`
 *             برای افزایش) — دفترِ بازپرداخت حالتِ «معلق» و روشِ آنلاین ندارد، پس کسر بی‌ردپا نمی‌ماند.
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

const FLAG_KEY = 'shop.orders.picking';
const KEY_RE = /^[A-Za-z0-9_.:-]{8,128}$/;
const ID_RE = /^[0-9A-Z]{26}$/;
const PRODUCT_RE = /^[A-Za-z0-9_.:-]{1,80}$/;
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
async function flagOf(shopId) {
  return orders.flagOf(shopId, FLAG_KEY);
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

// ════════════════════════════════════════════════════════════════════
//  کالا: بارکد و بودن
// ════════════════════════════════════════════════════════════════════

function barcodesOfData(d) {
  const list = Array.isArray(d.barcodes) ? d.barcodes : (typeof d.barcode === 'string' ? [d.barcode] : []);
  return list.filter((b) => typeof b === 'string' && b.trim()).slice(0, 20);
}

/**
 * بارکدهای امروزِ کالا: کاتالوگِ سرور اگر هست، وگرنه دفترِ همگام‌سازی (v1، سپس v3).
 * ⇒ `null` اگر کالا هیچ‌جا نیست؛ `[]` اگر هست و بارکد ندارد.
 */
async function barcodesOf(run, shopId, productId) {
  if (!productId) return null;
  const cat = (await run(
    'SELECT barcodes FROM catalog_products WHERE shop_id=$1 AND product_id=$2 AND NOT deleted', [shopId, productId]
  )).rows[0];
  if (cat) return barcodesOfData({ barcodes: asJson(cat.barcodes) });
  for (const table of ['sync_rows', 'sync_v3_rows']) {
    const r = (await run(
      `SELECT data FROM ${table} WHERE app='shop' AND account_kind='shop' AND account_id=$1
          AND table_name='products' AND row_id=$2 AND deleted_at IS NULL`, [shopId, productId]
    )).rows[0];
    if (r) return barcodesOfData(asJson(r.data));
  }
  return null;
}

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
  };
}

const coreLine = (v) => ({
  lineNo: v.lineNo, name: v.name, status: v.status, orderedQtyMilli: v.orderedQtyMilli,
  pickedQtyMilli: v.pickedQtyMilli, unitPriceMinor: v.unitPriceMinor, originalTotalMinor: v.originalTotalMinor,
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

/** «پیدا نکردم» ⇒ وظیفهٔ باز برای مدیر (یکی برای هر کالا) — PCK-14 */
async function openIssue(c, shopId, productId, orderId, detail, at) {
  await c.query(
    `INSERT INTO pick_issues (id, shop_id, kind, product_id, order_id, last_order_id, detail, created_at, updated_at)
     VALUES ($1,$2,'not_found',$3,$4,$4,$5::jsonb,$6,$6)
     ON CONFLICT (shop_id, product_id) WHERE status = 'open'
     DO UPDATE SET hits = pick_issues.hits + 1, last_order_id = excluded.last_order_id,
                   detail = excluded.detail, updated_at = excluded.updated_at`,
    [ulid(at), shopId, productId, orderId, JSON.stringify(detail), at]
  );
}

/** «از نو» روی قلمِ پیدانشده: اگر وظیفه فقط از همین سفارش بود، خودِ سیستم کنارش می‌گذارد */
async function undoIssue(c, shopId, productId, orderId, at) {
  const iss = (await c.query(
    `SELECT id, hits, order_id FROM pick_issues WHERE shop_id=$1 AND product_id=$2 AND status='open' FOR UPDATE`,
    [shopId, productId]
  )).rows[0];
  if (!iss) return;
  if (Number(iss.hits) <= 1 && iss.order_id === orderId) {
    await c.query(
      `UPDATE pick_issues SET status='dismissed', resolved_by='system', resolved_at=$2, updated_at=$2,
              note='جمع‌آور «پیدا نکردم» را برگرداند' WHERE id=$1`, [iss.id, at]
    );
  } else {
    await c.query('UPDATE pick_issues SET hits = GREATEST(hits - 1, 1), updated_at=$2 WHERE id=$1', [iss.id, at]);
  }
}

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
      return {
        eventId: ev.eventId, status: prev.outcome, replayed: true,
        ...(prev.code ? { code: prev.code, message: REJECT[prev.code] || '' } : {}),
        ...(line ? { line: lineView(line) } : {}),
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
          const maxMilli = ordered + Math.floor((ordered * P.DEFAULT_TOLERANCE_BP) / 10000);
          return reject('over_tolerance', { actualMilli: ev.qtyMilli, maxMilli }, { maxMilli });
        }
        detail.verdict = verdict;
        next = { status: verdict === 'ok' ? 'picked' : 'short', picked: ev.qtyMilli };
        break;
      }
      case 'not_found': {
        //  PCK-03: همیشه پذیرفته — مگر تکلیفِ قلم روشن شده باشد
        if (!open) return reject('line_done');
        next = { status: picked > 0 ? 'short' : 'not_found', picked };
        if (line.product_id) {
          await openIssue(c, shopId, line.product_id, orderId,
            { name: line.name, orderedMilli: ordered, pickedMilli: picked, by: actor.id || '' }, at);
          detail.issue = true;
        }
        break;
      }
      case 'reset': {
        if (open && picked === 0 && Number(line.scans) === 0) return reject('nothing_to_reset');
        if ((line.status === 'not_found' || line.status === 'short') && line.product_id) {
          await undoIssue(c, shopId, line.product_id, orderId, at);
        }
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
    return { eventId: ev.eventId, status: 'accepted', line: lineView(after) };
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
    const live = (await c.query(
      'SELECT id, status FROM delivery_assignments WHERE order_id=$1 AND status = ANY($2::text[])', [id, DISPATCHED]
    )).rows[0];
    if (live) throw conflict('بسته پیش‌تر به پیک سپرده شده است', 'already_dispatched');
    //  ⛔ پولِ گرفته‌شده: جمعِ تازه نه کم می‌شود (بازپرداختِ بی‌ردپا) نه زیاد (پولِ بی‌رضایت)
    const paidMinor = Number(order.total_minor);
    if (PAID.includes(order.payment_status) && (s.deductedMinor > 0 || s.totalMinor !== paidMinor)) {
      const down = s.deductedMinor > 0;
      const err = new ApiError(409, down ? 'paid_needs_refund' : 'paid_total_increase', down
        ? 'این سفارش پرداخت شده است؛ کم کردنِ مبلغ بازپرداخت می‌خواهد — همهٔ قلم‌ها را بردارید یا سفارش را لغو و بازپرداخت کنید'
        : 'این سفارش پرداخت شده است؛ مبلغِ بیشتر بی رضایتِ مشتری گرفته نمی‌شود — همان مقدارِ سفارش را بردارید');
      err.data = { paidMinor, totalMinor: s.totalMinor, deductedMinor: s.deductedMinor };
      throw err;
    }

    const at = now();
    //  قلم‌های سفارش = آن‌چه واقعاً برداشته شد (برنامه فروشِ تحویل را از همین‌ها می‌سازد)
    for (const l of s.lines) {
      if (l.qtyMilli === 0) {
        await c.query('DELETE FROM online_order_lines WHERE order_id=$1 AND line_no=$2', [id, l.lineNo]);
      } else if (s.changedLines.includes(l.lineNo)) {
        await c.query(
          'UPDATE online_order_lines SET qty_milli=$3, line_total_minor=$4 WHERE order_id=$1 AND line_no=$2',
          [id, l.lineNo, l.qtyMilli, l.totalMinor]
        );
      }
    }
    const kept = s.lines.filter((l) => l.qtyMilli > 0).length;
    const moneyChanged = s.totalMinor !== Number(order.total_minor);
    await c.query(
      `UPDATE online_orders SET subtotal_minor=$3, discount_minor=$4, total_minor=$5, items_count=$6,
              version=version+1, updated_at=$7 WHERE shop_id=$1 AND id=$2`,
      [shopId, id, s.subtotalMinor, s.discountMinor, s.totalMinor, kept, at]
    );
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
    return { sheet: await loadSheet(run, shopId, id), replayed: false, deductedMinor: s.deductedMinor };
  });
}

// ════════════════════════════════════════════════════════════════════
//  جای قفسه (PCK-01)
// ════════════════════════════════════════════════════════════════════

async function productExists(shopId, productId) {
  const hit = await db.query(
    `SELECT 1 FROM catalog_products WHERE shop_id=$1 AND product_id=$2 AND NOT deleted
     UNION ALL SELECT 1 FROM sync_rows WHERE app='shop' AND account_kind='shop' AND account_id=$1
        AND table_name='products' AND row_id=$2 AND deleted_at IS NULL
     UNION ALL SELECT 1 FROM sync_v3_rows WHERE app='shop' AND account_kind='shop' AND account_id=$1
        AND table_name='products' AND row_id=$2 AND deleted_at IS NULL
     LIMIT 1`, [shopId, productId]
  );
  return hit.rows.length > 0;
}

/** جای کالا در دکان. هر سه خالی ⇒ پاک. فقط برای برگه‌های **تازه**؛ برگهٔ باز همان می‌ماند. */
async function setLocation(shopId, productId, body, userId) {
  const pid = str(productId);
  if (!PRODUCT_RE.test(pid)) throw notFound('کالا پیدا نشد', 'product_not_found');
  if (!(await productExists(shopId, pid))) throw notFound('کالا پیدا نشد', 'product_not_found');
  const b = body && typeof body === 'object' ? body : {};
  const loc = { aisle: P.locPart(text(b.aisle, 40)), shelf: P.locPart(text(b.shelf, 40)), level: P.locPart(text(b.level, 40)) };
  const at = now();
  if (!loc.aisle && !loc.shelf && !loc.level) {
    await db.query('DELETE FROM pick_locations WHERE shop_id=$1 AND product_id=$2', [shopId, pid]);
    return { productId: pid, location: null };
  }
  await db.query(
    `INSERT INTO pick_locations (shop_id, product_id, aisle, shelf, level, updated_by, updated_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7)
     ON CONFLICT (shop_id, product_id) DO UPDATE SET aisle=excluded.aisle, shelf=excluded.shelf, level=excluded.level,
       updated_by=excluded.updated_by, updated_at=excluded.updated_at`,
    [shopId, pid, loc.aisle, loc.shelf, loc.level, userId || '', at]
  );
  return { productId: pid, location: loc };
}

// ════════════════════════════════════════════════════════════════════
//  وظیفهٔ بررسی و ویترین (PCK-14)
// ════════════════════════════════════════════════════════════════════

function issueView(r) {
  return {
    id: r.id, kind: r.kind, productId: r.product_id, orderId: r.order_id, lastOrderId: r.last_order_id,
    status: r.status, hits: Number(r.hits), detail: asJson(r.detail), createdAt: Number(r.created_at),
    updatedAt: Number(r.updated_at), resolvedBy: r.resolved_by, resolvedAt: num(r.resolved_at), note: r.note,
  };
}

async function issues(shopId, { status = 'open', limit } = {}) {
  if (!['open', 'resolved', 'dismissed'].includes(status)) throw badRequest('وضعیتِ وظیفه معتبر نیست', 'bad_status');
  const lim = Math.min(Math.max(Number.parseInt(limit, 10) || 50, 1), 100);
  const rows = await many(
    'SELECT * FROM pick_issues WHERE shop_id=$1 AND status=$2 ORDER BY created_at DESC, id DESC LIMIT $3',
    [shopId, status, lim]
  );
  return { issues: rows.map(issueView) };
}

/** رسیدگیِ مدیر: «رسیدگی شد» (کالا دوباره در ویترین) یا «کنار گذاشته شد» — توضیح لازم. */
async function resolveIssue(shopId, issueId, body, userId) {
  const id = str(issueId);
  if (!ID_RE.test(id)) throw notFound('وظیفهٔ باز پیدا نشد', 'issue_not_found');
  const b = body && typeof body === 'object' ? body : {};
  const action = str(b.action);
  if (!['resolved', 'dismissed'].includes(action)) throw badRequest('کار: resolved یا dismissed', 'bad_action');
  const note = text(b.note, 300);
  if (!note) throw badRequest('توضیحِ رسیدگی لازم است', 'note_required');
  const at = now();
  const r = await db.query(
    `UPDATE pick_issues SET status=$3, resolved_by=$4, resolved_at=$5, note=$6, updated_at=$5
      WHERE shop_id=$1 AND id=$2 AND status='open' RETURNING *`, [shopId, id, action, userId || '', at, note]
  );
  if (!r.rows.length) throw notFound('وظیفهٔ باز پیدا نشد', 'issue_not_found');
  return { issue: issueView(r.rows[0]) };
}

/** کالاهایی که «پیدا نشد» ِ بازشان هنوز رسیدگی نشده — پرچم خاموش ⇒ هیچ (رفتارِ پیشین). */
async function heldIn(run, shopId, productIds) {
  const ids = [...new Set((productIds || []).map(String).filter(Boolean))];
  if (!ids.length || !(await flagOf(shopId))) return new Set();
  const rows = (await run(
    `SELECT product_id FROM pick_issues WHERE shop_id=$1 AND status='open' AND product_id = ANY($2::text[])`,
    [shopId, ids]
  )).rows;
  return new Set(rows.map((r) => r.product_id));
}

/**
 * ثبتِ سفارشِ ویترین (`storefront.pricer`): کالاهایی که سفارش نمی‌گیرند = آن‌چه ویترین خودش رد می‌کند
 * (`gone`) + کالای «پیدا نشد» ِ رسیدگی‌نشده (PCK-14؛ پرچم خاموش ⇒ فقط `gone`، رفتارِ پیشین).
 */
async function unorderable(run, shopId, ids, gone) {
  const held = await heldIn(run, shopId, ids);
  return ids.filter((id) => gone(id) || held.has(id));
}

/** نشانِ موجودیِ ویترین: کالای «پیدا نشد» تا رسیدگی «تمام شده» است (PCK-14). */
async function applyHolds(shopId, items) {
  if (!items || !items.length) return items;
  const held = await heldIn((t, p) => db.query(t, p), shopId, items.map((p) => p.id));
  if (!held.size) return items;
  for (const p of items) {
    if (held.has(p.id)) p.stock = { status: 'out_of_stock', reason: 'pick_review', checkedAt: now() };
  }
  return items;
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
  flagOf, parseEvent, labelsOf, sheet, start, events, pack, setLocation,
  issues, resolveIssue, heldIn, unorderable, applyHolds, metrics,
};
