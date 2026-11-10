'use strict';
/**
 * حرکتِ دستیِ کارمند در کاتالوگ (INV-08، INV-12، INV-15) و آستانهٔ «موجودیِ کم» (§۳۲.۲ بندِ ۷) — از `lib/catalog.js`
 * جدا شد تا زیرِ سقفِ اندازه بماند؛ رفتار همان. وابستگی‌های همان پرونده (خواندنِ دفترِ دستگاه، نمای حرکت،
 * سنجنده‌ها و سقف‌ها) تزریق می‌شوند.
 */
const crypto = require('crypto');
const { query, one, now, tx } = require('../db');
const stock = require('./stock');
const repack = require('./catalog-repack');
const { stable } = require('./idempotency');
const { badRequest, notFound, conflict } = require('../middleware/errors');

module.exports = ({ str, text, KEY_RE, MAX_QTY_MILLI, moveView, ingestTx, afterIngest }) => {
//  نوعِ درخواست ⇒ اجازه و مکان‌های مجاز
const MANUAL = Object.freeze({
  transfer: { permission: 'stock.move', reason: false },
  damage: { permission: 'stock.adjust', reason: true },
  writeoff: { permission: 'stock.adjust', reason: true },
  return: { permission: 'stock.adjust', reason: true },
  adjust: { permission: 'stock.adjust', reason: true },
  count: { permission: 'stock.adjust', reason: true },
});
const TRANSFER_LOCS = ['shop_floor', 'warehouse', 'in_transit'];

function qtyOf(v, field = 'مقدار') {
  if (typeof v !== 'number' || !Number.isSafeInteger(v) || v <= 0 || v > MAX_QTY_MILLI) {
    throw badRequest(`${field} باید عددِ صحیحِ مثبت (هزارم) باشد`, 'bad_qty');
  }
  return v;
}

function locOf(v, allowed, def) {
  const l = str(v) || def;
  if (!allowed.includes(l)) throw badRequest('مکانِ موجودی معتبر نیست', 'bad_location');
  return l;
}

/** درخواست ⇒ حرکت‌ها (پیش از تراکنش؛ مقدارِ «شمارش» درون تراکنش حساب می‌شود). */
function parseManual(body) {
  const b = body && typeof body === 'object' ? body : {};
  const kind = str(b.kind);
  //  INV-21: شکستنِ کارتن و ساختِ بسته (`lib/catalog-repack.js`)؛ پرچمش را درِ مسیر و `manualMove` می‌سنجند
  if (repack.isKind(kind)) return repack.parse(b, { str, text, qtyOf, locOf, max: MAX_QTY_MILLI });
  const spec = MANUAL[kind];
  if (!spec) throw badRequest('نوعِ حرکت معتبر نیست', 'bad_kind');
  const productId = str(b.productId);
  if (!productId) throw badRequest('کالا لازم است', 'product_required');
  const reason = text(b.reason, 80);
  if (spec.reason && !reason) throw badRequest('دلیل لازم است', 'reason_required');
  const note = text(b.note, 300);
  const out = { kind, spec, productId, reason, note };
  if (kind === 'transfer') {
    out.from = locOf(b.from, TRANSFER_LOCS);
    out.to = locOf(b.to, TRANSFER_LOCS);
    if (out.from === out.to) throw badRequest('مبدأ و مقصد یکی است', 'bad_location');
    out.qtyMilli = qtyOf(b.qtyMilli);
  } else if (kind === 'damage') {
    out.from = locOf(b.from, TRANSFER_LOCS, 'shop_floor');
    out.qtyMilli = qtyOf(b.qtyMilli);
  } else if (kind === 'writeoff') {
    out.location = locOf(b.location, stock.LOCATIONS, 'damaged');
    out.qtyMilli = qtyOf(b.qtyMilli);
  } else if (kind === 'return') {
    out.location = locOf(b.location, stock.LOCATIONS, 'shop_floor');
    out.qtyMilli = qtyOf(b.qtyMilli);
    out.orderId = text(b.orderId, 80);
  } else if (kind === 'adjust') {
    out.location = locOf(b.location, stock.LOCATIONS);
    const q = b.qtyMilli;
    if (typeof q !== 'number' || !Number.isSafeInteger(q) || q === 0 || Math.abs(q) > MAX_QTY_MILLI) {
      throw badRequest('مقدارِ اصلاح باید عددِ صحیحِ غیرصفر (هزارم) باشد', 'bad_qty');
    }
    out.qtyMilli = q;
  } else {
    out.location = locOf(b.location, stock.LOCATIONS);
    const q = b.countedMilli;
    if (typeof q !== 'number' || !Number.isSafeInteger(q) || q < 0 || q > MAX_QTY_MILLI) {
      throw badRequest('مقدارِ شمرده‌شده باید عددِ صحیحِ نامنفی (هزارم) باشد', 'bad_qty');
    }
    out.countedMilli = q;
    //  INV-10: لحظهٔ شمردن (اختیاری). بازه درونِ تراکنش با ساعتِ سرور سنجیده می‌شود (`countedAtOf`)
    if (b.countedAt !== undefined && b.countedAt !== null) {
      if (typeof b.countedAt !== 'number' || !Number.isSafeInteger(b.countedAt) || b.countedAt <= 0) throw badCountedAt();
      out.countedAt = b.countedAt;
    }
  }
  return out;
}

//  INV-10: شمارشی که با لحظهٔ شمردن می‌آید — ساعتِ دستگاه کمی جلوتر پذیرفته، کهنه‌تر از ۳۰ روز نه
const COUNT_SKEW_MS = 5 * 60_000;
const COUNT_MAX_AGE_MS = 30 * 86_400_000;
const badCountedAt = () => badRequest('لحظهٔ شمارش نامعتبر است (نه آینده، نه کهنه‌تر از ۳۰ روز)', 'bad_counted_at');
function countedAtOf(t, at) {
  if (t === undefined) return null;
  if (t > at + COUNT_SKEW_MS || t < at - COUNT_MAX_AGE_MS) throw badCountedAt();
  return t;
}

/**
 * INV-10 (S20): گردشِ همان کالا در همان مکان **پس از** لحظهٔ شمردن (فروشِ دستگاه با زمانِ خودِ فروش، انتقال، اصلاح…)
 * که در عددِ شمرده نیست ولی در موجودیِ امروز هست ⇒ مورد انتظار = موجودیِ امروز − همین. هم‌لحظه پیش از شمردن است.
 */
async function movedAfter(c, shopId, productId, location, countedAt) {
  const r = (await c.query(
    `SELECT COALESCE(SUM(delta_on_hand_milli), 0) AS s FROM stock_moves
      WHERE shop_id=$1 AND product_id=$2 AND branch_id='' AND location=$3 AND at > $4`,
    [shopId, productId, location, countedAt]
  )).rows[0];
  return Number(r.s);
}

function hashOf(body) {
  return crypto.createHash('sha256').update(stable(body || {})).digest('hex').slice(0, 32);
}

//  هر درخواستِ دستی حداکثر دو حرکت دارد (انتقال = خروج + ورود)، و ساختِ بسته یکی برای هر جزء + خروجی ⇒ کلیدهای دقیق، نه LIKE
const MAX_PLAN = 1 + repack.MAX_PARTS;
const staffKeys = (k) => Array.from({ length: MAX_PLAN }, (_, i) => `staff:${k}:${i + 1}`);
//  به ترتیبِ ردیفِ سند (`staff:k:10` پس از `staff:k:9`، نه پیش از `:2`)
const seqOf = (r) => Number(r.source_key.slice(r.source_key.lastIndexOf(':') + 1));
const bySeq = (rows) => rows.sort((a, b) => seqOf(a) - seqOf(b));

function keyOf(raw) {
  const key = str(raw);
  if (!key) throw badRequest('کلیدِ یکتاییِ حرکت لازم است', 'idempotency_key_required');
  if (!KEY_RE.test(key)) throw badRequest('کلیدِ یکتایی نامعتبر است', 'bad_idempotency_key');
  return key;
}

/**
 * حرکتِ دستی با کلیدِ یکتایی؛ تکرار ⇒ همان نتیجه، بی اثرِ دوم.
 * اجازه را فراخواننده با `can(role, MANUAL[kind].permission)` می‌سنجد.
 * ⇒ { moves, replayed, unchanged }
 */
async function manualMove(shopId, { key, body, actor }) {
  const k = keyOf(key);
  const m = parseManual(body);
  const hash = hashOf(body);
  const at = now();
  const countedAt = m.kind === 'count' ? countedAtOf(m.countedAt, at) : null;
  let ingested = null;
  const out = await tx(async (c) => {
    await stock.lock(c, shopId);
    const prev = (await c.query(
      `SELECT * FROM stock_moves WHERE shop_id=$1 AND source_key = ANY($2::text[]) ORDER BY source_key`, [shopId, staffKeys(k)]
    )).rows;
    bySeq(prev);
    if (prev.length) {
      if (prev[0].ref_id !== hash) throw conflict('این کلیدِ یکتایی پیش‌تر برای حرکتِ دیگری به کار رفته است', 'idempotency_key_reused');
      return { moves: prev.map(moveView), replayed: true, unchanged: false };
    }
    if (m.repack && !(await repack.enabled(shopId, c))) throw badRequest('نوعِ حرکت معتبر نیست', 'bad_kind');
    const settings = await stock.settingsOf(shopId, c);
    //  دفترِ دستگاه پیش از اصلاح: شمارش و انتقال روی موجودیِ امروز حساب شوند
    ingested = await ingestTx(c, shopId, at, settings);
    const p = (await c.query('SELECT product_id FROM catalog_products WHERE shop_id=$1 AND product_id=$2', [shopId, m.productId])).rows[0];
    if (!p) throw notFound('کالا پیدا نشد', 'product_not_found');
    if (m.repack) await repack.requireProducts(c, shopId, m);

    const base = {
      productId: m.productId, mode: 'local', source: 'staff', refType: 'staff', refId: hash,
      actorType: 'user', actorId: actor.id, note: m.note, at,
    };
    const plan = [];
    if (m.repack) {
      plan.push(...repack.plan(m, base));
    } else if (m.kind === 'transfer' || m.kind === 'damage') {
      const to = m.kind === 'damage' ? 'damaged' : m.to;
      const reason = m.reason || (m.kind === 'damage' ? 'damage' : 'transfer');
      plan.push({ ...base, location: m.from, type: 'transfer_out', qtyMilli: m.qtyMilli, reason });
      plan.push({ ...base, location: to, type: 'transfer_in', qtyMilli: m.qtyMilli, reason });
    } else if (m.kind === 'writeoff') {
      plan.push({ ...base, location: m.location, type: 'damage', qtyMilli: m.qtyMilli, reason: m.reason });
    } else if (m.kind === 'return') {
      plan.push({ ...base, location: m.location, type: 'customer_return', qtyMilli: m.qtyMilli, reason: m.reason,
        note: m.orderId ? `order:${m.orderId}${m.note ? ` ${m.note}` : ''}` : m.note });
    } else if (m.kind === 'adjust') {
      plan.push({ ...base, location: m.location, type: 'adjustment', qtyMilli: m.qtyMilli, reason: m.reason });
    } else {
      const lv = (await stock.levelsOf(c, shopId, [m.productId])).get(m.productId);
      const after = countedAt === null ? 0 : await movedAfter(c, shopId, m.productId, m.location, countedAt);
      const diff = m.countedMilli - (stock.onHandAt(lv, m.location) - after);
      if (diff === 0) return { moves: [], replayed: false, unchanged: true };
      plan.push({ ...base, location: m.location, type: 'count_adjust', qtyMilli: diff, reason: m.reason });
    }
    for (let i = 0; i < plan.length; i++) {
      await stock.postMove(c, shopId, { ...plan[i], sourceKey: `staff:${k}:${i + 1}` }, at);
    }
    await stock.checkProducts(c, shopId, m.repack ? repack.ids(m) : [m.productId], settings, at);
    const rows = bySeq((await c.query(
      `SELECT * FROM stock_moves WHERE shop_id=$1 AND source_key = ANY($2::text[]) ORDER BY source_key`, [shopId, staffKeys(k)]
    )).rows);
    return { moves: rows.map(moveView), replayed: false, unchanged: false };
  });
  //  کنار گذاشتنِ کسرِ سفارشیِ پیشین در همین تراکنش ⇒ همان سطرِ ممیزیِ خواندنِ کاتالوگ
  await afterIngest(shopId, ingested);
  return out;
}

/** آستانهٔ «موجودیِ کم» برای یک مکان (§۳۲.۲ بندِ ۷)؛ null ⇒ برداشتنِ آستانه. */
async function setLow(shopId, productId, body) {
  const b = body && typeof body === 'object' ? body : {};
  const location = locOf(b.location, stock.LOCATIONS);
  let low = null;
  if (b.lowMilli !== null && b.lowMilli !== undefined) {
    if (typeof b.lowMilli !== 'number' || !Number.isSafeInteger(b.lowMilli) || b.lowMilli < 0 || b.lowMilli > MAX_QTY_MILLI) {
      throw badRequest('آستانه باید عددِ صحیحِ نامنفی (هزارم) باشد', 'bad_qty');
    }
    low = b.lowMilli;
  }
  const id = String(productId || '');
  const p = await one('SELECT 1 FROM catalog_products WHERE shop_id=$1 AND product_id=$2', [shopId, id]);
  if (!p) throw notFound('کالا پیدا نشد', 'product_not_found');
  await query(
    `INSERT INTO stock_levels (shop_id, product_id, branch_id, location, on_hand_milli, low_milli, updated_at)
     VALUES ($1,$2,'',$3,0,$4,$5)
     ON CONFLICT (shop_id, product_id, branch_id, location) DO UPDATE SET low_milli=excluded.low_milli`,
    [shopId, id, location, low, now()]
  );
  return { productId: id, location, lowMilli: low };
}

  return { MANUAL, TRANSFER_LOCS, parseManual, manualMove, setLow };
};
