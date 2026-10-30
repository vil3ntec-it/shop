'use strict';
/**
 * دفترِ موجودیِ سرور — v3 §۳۲.۱، §۳۲.۲، §۳۸.۳ (INV-01..16@32، INV-19، CHK-18).
 *
 *   تصمیم ── هر «می‌شود یا نه؟» (کمبود، رزروِ بیش از آزاد، موجودیِ منفی،
 *            وظیفهٔ بررسی، دلیلِ لازم) با همان هستهٔ خالصِ `stock-core.js`
 *            گرفته می‌شود که برنامه‌ها هم دارند — این‌جا هیچ حسابِ تازه‌ای نیست.
 *            (`stock-core.js` کپیِ بایت‌به‌بایتِ `license/stock-core.js` است؛
 *            آزمونِ `catalog-stock` برابری را قفل می‌کند، چون imageِ سرور فقط
 *            پوشهٔ `server/` را دارد.)
 *   دفتر ─── `stock_moves` فقط افزوده می‌شود؛ هر حرکت کلیدِ یکتایی دارد و
 *            تکرارش بی‌اثر است. `stock_levels` همیشه = جمعِ حرکت‌ها.
 *   سفارش ── ⛔ تصمیمِ صاحبِ مخزن (۱۴۰۵/۰۷/۲۷): سفارشِ آنلاین موجودی را **نه رزرو
 *            می‌کند نه کم**. دکان کالا را از برنامهٔ خودش تحویل می‌دهد و همان‌جا فروشِ
 *            عادی ثبت می‌شود؛ این دفتر فقط آن‌چه دستگاه‌ها ثبت کرده‌اند را می‌خواند.
 *            «قابل‌فروش» = جمعِ موجودیِ مکان‌های قابل‌فروش (بخشِ ۳.۶). ساختِ سفارش فقط
 *            می‌سنجد (`check`)، بی نوشتن. جدولِ `stock_reservations` (migration 082)
 *            می‌ماند ولی دیگر نوشته یا شمرده نمی‌شود.
 *   هم‌زمانی  همهٔ نوشتن‌های موجودیِ یک دکان پشتِ یک قفلِ تراکنشی (`catalog:<shop>`).
 *   دستگاه ── فروشِ ثبت‌شدهٔ دستگاه (آفلاین) با حالتِ `recorded` می‌نشیند:
 *            هرگز رد نمی‌شود؛ منفی شدن «وظیفهٔ بررسی» می‌سازد (INV-19).
 *
 * ⛔ پرچمِ `shop.catalog` (تنظیماتِ زنده) پیش‌فرض خاموش است؛ خاموش ⇒ هیچ
 * ردیفی نوشته نمی‌شود.
 */
const core = require('./stock-core');
const { query } = require('../db');
const { ulid } = require('./ulid');
const { ApiError, badRequest, conflict } = require('../middleware/errors');

const FLAG_KEY = 'shop.catalog';
const LOCATIONS = Object.freeze(['shop_floor', 'warehouse', 'damaged', 'in_transit']);
//  فقط این دو می‌توانند «قابل‌فروش» باشند — خراب و در‌راه هرگز (§۳۲.۱)
const SELLABLE_CHOICES = Object.freeze(['shop_floor', 'warehouse']);
const DEFAULTS = Object.freeze({
  sellableLocations: Object.freeze(['shop_floor', 'warehouse']),
  staleAfterSec: 900,
  showQty: 'hidden',
  prepMinutes: null,
  deviceLocation: 'shop_floor',
  syncSource: 'v1',
});
//  جایگاهِ «جمعِ قابل‌فروش» در دفترِ موقتِ هسته (فقط برای تصمیمِ رزرو)
const AGG = '*sellable';

const runOf = (c) => (c ? (t, p) => c.query(t, p) : query);
const num = (v) => (v === null || v === undefined ? 0 : Number(v));

function decode(v) {
  if (typeof v !== 'string') return v;
  try { return JSON.parse(v); } catch { return null; }
}

// ════════════════════════════════════════════════════════════════════
//  پرچم و تنظیمات
// ════════════════════════════════════════════════════════════════════

/** پرچمِ دکان: مقدارِ خودِ دکان بر `all` جلو می‌افتد؛ فقط `true` روشن است. */
async function flagOf(shopId, c) {
  const rows = (await runOf(c)(
    'SELECT scope, value FROM live_config WHERE key=$1 AND scope IN ($2,$3)', [FLAG_KEY, 'all', String(shopId)]
  )).rows;
  let v;
  for (const r of rows) if (r.scope === 'all') v = decode(r.value);
  for (const r of rows) if (r.scope !== 'all') v = decode(r.value);
  return v === true;
}

function normSellable(v) {
  const list = Array.isArray(decode(v)) ? decode(v) : [];
  const out = list.filter((x, i) => SELLABLE_CHOICES.includes(x) && list.indexOf(x) === i);
  return out.length ? out : [...DEFAULTS.sellableLocations];
}

async function settingsOf(shopId, c) {
  const r = (await runOf(c)('SELECT * FROM catalog_settings WHERE shop_id=$1', [shopId])).rows[0];
  if (!r) return { ...DEFAULTS, sellableLocations: [...DEFAULTS.sellableLocations] };
  return {
    sellableLocations: normSellable(r.sellable_locations),
    staleAfterSec: Number(r.stale_after_sec),
    showQty: r.show_qty,
    prepMinutes: r.prep_minutes === null || r.prep_minutes === undefined ? null : Number(r.prep_minutes),
    deviceLocation: r.device_location,
    syncSource: r.sync_source,
  };
}

function intIn(v, field, min, max) {
  if (typeof v !== 'number' || !Number.isSafeInteger(v) || v < min || v > max) {
    throw badRequest(`${field} باید عددِ صحیح بینِ ${min} و ${max} باشد`, 'bad_setting');
  }
  return v;
}

/** تنظیم با «اجازهٔ تنظیمات»؛ هر فیلدِ نیامده همان مقدارِ قبلی می‌ماند. */
async function saveSettings(shopId, body, userId, at) {
  const b = body && typeof body === 'object' ? body : {};
  const cur = await settingsOf(shopId);
  const next = { ...cur };
  if (b.sellableLocations !== undefined) {
    if (!Array.isArray(b.sellableLocations) || !b.sellableLocations.length
        || b.sellableLocations.some((x) => !SELLABLE_CHOICES.includes(x))
        || new Set(b.sellableLocations).size !== b.sellableLocations.length) {
      throw badRequest('مکانِ قابل‌فروش فقط قفسه و/یا انبار است', 'bad_setting');
    }
    next.sellableLocations = [...b.sellableLocations];
  }
  //  `reserveTtlSec` دیگر معنایی ندارد (سفارش رزرو نمی‌کند) — آمدنش نادیده گرفته می‌شود
  if (b.staleAfterSec !== undefined) next.staleAfterSec = intIn(b.staleAfterSec, 'مهلتِ تازگیِ موجودی (ثانیه)', 60, 604800);
  if (b.showQty !== undefined) {
    if (!['hidden', 'exact'].includes(b.showQty)) throw badRequest('نمایشِ عدد: hidden یا exact', 'bad_setting');
    next.showQty = b.showQty;
  }
  if (b.prepMinutes !== undefined) {
    next.prepMinutes = b.prepMinutes === null ? null : intIn(b.prepMinutes, 'زمانِ آماده‌سازی (دقیقه)', 1, 10080);
  }
  if (b.deviceLocation !== undefined) {
    if (!SELLABLE_CHOICES.includes(b.deviceLocation)) throw badRequest('مکانِ موجودیِ دستگاه: قفسه یا انبار', 'bad_setting');
    next.deviceLocation = b.deviceLocation;
  }
  if (b.syncSource !== undefined) {
    if (!['v1', 'v3'].includes(b.syncSource)) throw badRequest('منبعِ همگام‌سازی: v1 یا v3', 'bad_setting');
    next.syncSource = b.syncSource;
  }
  await query(
    `INSERT INTO catalog_settings (shop_id, sellable_locations, stale_after_sec, show_qty,
       prep_minutes, device_location, sync_source, updated_by, updated_at)
     VALUES ($1,$2::jsonb,$3,$4,$5,$6,$7,$8,$9)
     ON CONFLICT (shop_id) DO UPDATE SET sellable_locations=excluded.sellable_locations,
       stale_after_sec=excluded.stale_after_sec,
       show_qty=excluded.show_qty, prep_minutes=excluded.prep_minutes, device_location=excluded.device_location,
       sync_source=excluded.sync_source, updated_by=excluded.updated_by, updated_at=excluded.updated_at`,
    [shopId, JSON.stringify(next.sellableLocations), next.staleAfterSec, next.showQty,
      next.prepMinutes, next.deviceLocation, next.syncSource, userId || '', at]
  );
  return next;
}

// ════════════════════════════════════════════════════════════════════
//  قفل و تصمیم با هسته
// ════════════════════════════════════════════════════════════════════

/** همهٔ نوشتن‌های موجودیِ یک دکان پشتِ هم (ساختِ سفارش، همگام‌سازی، حرکتِ دستی). */
function lock(c, shopId) {
  return c.query('SELECT pg_advisory_xact_lock(hashtext($1))', [`catalog:${shopId}`]);
}

/**
 * یک سند روی جایگاهی با موجودی/رزروِ معلوم — تصمیم با `stock-core`.
 * جایگاه از «مانده» (نه از کلِ تاریخچه) ساخته می‌شود: سرور لات و بها
 * نگه نمی‌دارد، پس همین دو عدد همهٔ آن چیزی است که هسته برای تصمیم می‌خواهد.
 */
function decide(productId, wh, onHandMilli, reservedMilli, doc, mode) {
  let book = core.emptyBook();
  const base = { productId, warehouseId: wh, userId: 'system', at: 1 };
  if (onHandMilli !== 0) {
    book = core.apply(book, { ...base, id: 'seed.qty', type: 'adjustment', qtyMilli: onHandMilli, reason: 'opening' },
      { mode: 'recorded' }).book;
  }
  if (reservedMilli > 0) {
    book = core.apply(book, { ...base, id: 'seed.res', type: 'reserve', qtyMilli: reservedMilli }, { mode: 'recorded' }).book;
  }
  const out = core.apply(book, { ...doc, productId, warehouseId: wh }, { mode });
  const p = core.position(out.book, productId, wh);
  return { result: out.result, onHand: p ? p.qtyMilli : onHandMilli, reserved: p ? p.reservedMilli : reservedMilli };
}

const REFUSALS = {
  insufficient_stock: [409, 'موجودیِ این مکان کافی نیست'],
  reason_required: [400, 'دلیل لازم است'],
  bad_qty: [400, 'مقدار معتبر نیست'],
  time_required: [400, 'زمانِ حرکت معتبر نیست'],
  user_required: [400, 'کاربرِ حرکت معلوم نیست'],
  release_exceeds_reserved: [409, 'آزادسازی بیش از رزرو است'],
};

function refusal(result) {
  const [status, message] = REFUSALS[result.error] || [400, 'این حرکت مجاز نیست'];
  const err = new ApiError(status, result.error, message);
  if (result.availableMilli !== undefined) err.data = { availableMilli: result.availableMilli };
  return err;
}

// ════════════════════════════════════════════════════════════════════
//  مانده‌ها
// ════════════════════════════════════════════════════════════════════

/** pid ⇒ { location: {onHand, low} } — فقط شعبهٔ اصلی (چند شعبه: §۳۸.۳، هنوز API ندارد). */
async function levelsOf(c, shopId, ids) {
  const map = new Map();
  if (!ids.length) return map;
  const rows = (await runOf(c)(
    `SELECT product_id, location, on_hand_milli, low_milli FROM stock_levels
      WHERE shop_id=$1 AND branch_id='' AND product_id = ANY($2::text[])`, [shopId, ids]
  )).rows;
  for (const r of rows) {
    if (!map.has(r.product_id)) map.set(r.product_id, {});
    map.get(r.product_id)[r.location] = { onHand: num(r.on_hand_milli), low: r.low_milli === null ? null : num(r.low_milli) };
  }
  return map;
}

const onHandAt = (lv, loc) => (lv && lv[loc] ? lv[loc].onHand : 0);
const sellableOnHand = (lv, settings) => settings.sellableLocations.reduce((s, loc) => s + onHandAt(lv, loc), 0);

/**
 * موجودیِ «آزاد» به تفکیک: رزرو اول از مکانِ نخست (قفسه) برداشته می‌شود،
 * چون برداشت برای سفارش هم از همان‌جاست. کلِ آزاد = max(0، قابل‌فروش − رزرو).
 */
function freeOf(lv, held, settings) {
  const total = Math.max(0, sellableOnHand(lv, settings) - held);
  let rem = held;
  const per = {};
  for (const loc of settings.sellableLocations) {
    const have = Math.max(0, onHandAt(lv, loc));
    const take = Math.min(have, rem);
    rem -= take;
    per[loc] = have - take;
  }
  const first = settings.sellableLocations[0];
  const ready = Math.min(per[first] || 0, total);
  return { totalMilli: total, readyMilli: ready, laterMilli: total - ready, perLocation: per };
}

async function bump(c, shopId, productId, location, delta, at) {
  await c.query(
    `INSERT INTO stock_levels (shop_id, product_id, branch_id, location, on_hand_milli, updated_at)
     VALUES ($1,$2,'',$3,$4,$5)
     ON CONFLICT (shop_id, product_id, branch_id, location) DO UPDATE
       SET on_hand_milli = stock_levels.on_hand_milli + excluded.on_hand_milli, updated_at = excluded.updated_at`,
    [shopId, productId, location, delta, at]
  );
}

// ════════════════════════════════════════════════════════════════════
//  دفتر و وظیفه
// ════════════════════════════════════════════════════════════════════

/** ⇒ آیا تازه نوشته شد (کلیدِ تکراری ⇒ false، بی هیچ اثری). */
async function insertMove(c, shopId, m, at) {
  const r = await c.query(
    `INSERT INTO stock_moves (id, shop_id, product_id, branch_id, location, type, qty_milli,
       delta_on_hand_milli, delta_reserved_milli, mode, source, source_key, ref_type, ref_id,
       actor_type, actor_id, reason, note, at, created_at)
     VALUES ($1,$2,$3,'',$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17,$18,$19)
     ON CONFLICT (shop_id, source_key) DO NOTHING RETURNING id`,
    [ulid(at), shopId, m.productId, m.location, m.type, m.qtyMilli, m.deltaOnHand || 0, m.deltaReserved || 0,
      m.mode, m.source, m.sourceKey, m.refType || '', m.refId || '', m.actorType, m.actorId || '',
      m.reason || '', m.note || '', m.at || at, at]
  );
  return r.rows.length > 0;
}

async function openTask(c, shopId, { kind, productId, location = '', dedupe, detail = {} }, at) {
  await c.query(
    `INSERT INTO stock_tasks (id, shop_id, kind, product_id, branch_id, location, dedupe, detail, created_at, updated_at)
     VALUES ($1,$2,$3,$4,'',$5,$6,$7::jsonb,$8,$8)
     ON CONFLICT (shop_id, dedupe) WHERE status = 'open'
     DO UPDATE SET hits = stock_tasks.hits + 1, detail = excluded.detail, updated_at = excluded.updated_at`,
    [ulid(at), shopId, kind, productId, location, dedupe, JSON.stringify(detail), at]
  );
}

/**
 * یک حرکتِ موجودیِ مکان‌دار: تصمیم با هسته ← دفتر ← مانده ← وظیفه.
 * m: { productId, location, type, qtyMilli, mode, source, sourceKey, refType, refId,
 *      actorType, actorId, reason, note, at }
 * ⇒ { duplicate, onHandAfter, review }
 */
async function postMove(c, shopId, m, at) {
  if (!LOCATIONS.includes(m.location)) throw badRequest('مکانِ موجودی معتبر نیست', 'bad_location');
  const spec = core.TYPES[m.type];
  if (!spec || spec.reserve) throw new Error(`stock: نوعِ حرکتِ مکان‌دار نیست: ${m.type}`);
  const lv = (await levelsOf(c, shopId, [m.productId])).get(m.productId);
  const doc = {
    id: m.sourceKey, type: m.type, qtyMilli: m.qtyMilli,
    userId: m.actorId || m.actorType, at: m.at || at, reason: m.reason || '',
  };
  //  سرور فقط مقدار نگه می‌دارد؛ بها مالِ دستگاه است (D-23) ⇒ بهای صفر
  if (spec.cost === 'required') doc.costMinor = 0;
  const d = decide(m.productId, m.location, onHandAt(lv, m.location), 0, doc, m.mode);
  if (!d.result.ok) throw refusal(d.result);
  const delta = core.signedQtyMilli(doc);
  if (!(await insertMove(c, shopId, { ...m, deltaOnHand: delta, deltaReserved: 0 }, at))) {
    return { duplicate: true, onHandAfter: onHandAt(lv, m.location), review: false };
  }
  await bump(c, shopId, m.productId, m.location, delta, at);
  const task = d.result.reviewTask;
  if (task) {
    await openTask(c, shopId, {
      kind: 'negative_stock', productId: m.productId, location: m.location,
      dedupe: `neg:${m.productId}:${m.location}`,
      detail: { reasons: task.reasons, shortMilli: task.shortMilli, qtyAfterMilli: task.qtyAfterMilli,
        sourceKey: m.sourceKey, refType: m.refType || '', refId: m.refId || '' },
    }, at);
  }
  return { duplicate: false, onHandAfter: d.onHand, review: !!task };
}

/**
 * پس از هر دسته حرکت: «موجودیِ کم» (§۳۲.۲ بندِ ۷) وظیفه می‌سازد. (سفارش دیگر رزرو
 * نمی‌کند، پس «رزروِ بی‌پشتوانه» هم دیگر نیست.)
 */
async function checkProducts(c, shopId, ids, settings, at) {
  if (!ids.length) return;
  const lvs = await levelsOf(c, shopId, ids);
  const mins = new Map((await c.query(
    'SELECT product_id, min_stock_milli FROM catalog_products WHERE shop_id=$1 AND product_id = ANY($2::text[])',
    [shopId, ids]
  )).rows.map((r) => [r.product_id, num(r.min_stock_milli)]));
  for (const pid of ids) {
    const lv = lvs.get(pid) || {};
    const sell = sellableOnHand(lv, settings);
    const min = mins.get(pid) || 0;
    if (min > 0 && sell <= min) {
      await openTask(c, shopId, {
        kind: 'low_stock', productId: pid, dedupe: `low:${pid}`, detail: { freeMilli: sell, thresholdMilli: min },
      }, at);
    }
    for (const loc of LOCATIONS) {
      if (lv[loc] && lv[loc].low !== null && lv[loc].onHand <= lv[loc].low) {
        await openTask(c, shopId, {
          kind: 'low_stock', productId: pid, location: loc, dedupe: `low:${pid}:${loc}`,
          detail: { onHandMilli: lv[loc].onHand, thresholdMilli: lv[loc].low },
        }, at);
      }
    }
  }
}

// ════════════════════════════════════════════════════════════════════
//  سنجشِ فقط‌خواندنیِ سفارش — تصمیمِ صاحبِ مخزن (۱۴۰۵/۰۷/۲۷)
// ════════════════════════════════════════════════════════════════════

function fmtMilli(m) {
  const s = (m / 1000).toFixed(3).replace(/\.?0+$/, '');
  return s === '-0' ? '0' : s;
}

function outOfStock(short) {
  const s = short[0];
  const what = s.name ? `«${s.name}»` : 'یکی از کالاها';
  const err = conflict(
    s.availableMilli > 0
      ? `موجودیِ ${what} کافی نیست؛ اکنون فقط ${fmtMilli(s.availableMilli)} ${s.unit || 'واحد'} قابلِ سفارش است`
      : `${what} اکنون موجود نیست`,
    'out_of_stock'
  );
  err.data = { lines: short };
  return err;
}

/**
 * ⛔ سفارشِ آنلاین موجودیِ سرور را **نه رزرو می‌کند نه کم**: کالا را دکان از برنامهٔ
 * خودش تحویل می‌دهد و همان‌جا یک فروشِ عادی ثبت می‌شود (`OrderFulfillment.kt`)؛ موجودی
 * با همگام‌سازی به سرور، ویترین و دستگاه‌های دیگر می‌رسد. این‌جا فقط **سنجیده** می‌شود
 * که مقدارِ خواسته از موجودیِ قابل‌فروشِ همین لحظه بیشتر نباشد (کالای «تمام‌شده» سفارش
 * نمی‌گیرد) — با همان هستهٔ `stock-core` و **بی هیچ نوشتنی**. دو مشتریِ هم‌زمانِ آخرین
 * عدد هر دو می‌گذرند؛ دکان یکی را با دلیلِ «ناموجود» رد می‌کند و برنامه هنگامِ تحویل
 * بیش از موجودی نمی‌فروشد (همان سیاستِ صندوق).
 * lines: [{ lineNo, productId, qtyMilli }] به واحدِ موجودی. کمبود ⇒ `out_of_stock` (۴۰۹).
 */
async function check(c, shopId, { lines, settings, names = new Map() }) {
  const ids = [...new Set(lines.map((l) => l.productId))].sort();
  const lvs = await levelsOf(c, shopId, ids);
  const need = new Map();
  for (const l of lines) need.set(l.productId, (need.get(l.productId) || 0) + l.qtyMilli);
  const short = [];
  for (const pid of ids) {
    const d = decide(pid, AGG, sellableOnHand(lvs.get(pid), settings), 0, {
      id: `check:${pid}`, type: 'reserve', qtyMilli: need.get(pid), userId: 'check', at: 1,
    }, 'local');
    if (!d.result.ok) {
      const n = names.get(pid) || {};
      short.push({
        productId: pid, name: n.name || '', unit: n.unit || '',
        lineNos: lines.filter((l) => l.productId === pid).map((l) => l.lineNo),
        requestedMilli: need.get(pid), availableMilli: d.result.availableMilli || 0,
      });
    }
  }
  if (short.length) throw outOfStock(short);
  return { ok: true };
}

// ════════════════════════════════════════════════════════════════════
//  تطبیق (INV-16)
// ════════════════════════════════════════════════════════════════════

/**
 * مانده‌ها = جمعِ دفتر، و رزروِ دفتر = رزروهای فعال؛ با `productId` همان
 * حرکت‌ها با هستهٔ `stock-core` (حالتِ recorded: تاریخچه واقعیت است) از نو
 * بازپخش و با مانده‌ها سنجیده می‌شود.
 */
async function reconcile(shopId, { productId = '' } = {}) {
  const p = productId ? [shopId, productId] : [shopId];
  const pf = productId ? 'AND product_id=$2' : '';
  const ledger = (await query(
    `SELECT product_id, location, COALESCE(SUM(delta_on_hand_milli),0)::bigint AS on_hand,
            COALESCE(SUM(delta_reserved_milli),0)::bigint AS reserved, COUNT(*)::int AS moves
       FROM stock_moves WHERE shop_id=$1 ${pf} GROUP BY product_id, location`, p
  )).rows;
  const levels = (await query(
    `SELECT product_id, location, on_hand_milli FROM stock_levels WHERE shop_id=$1 AND branch_id='' ${pf}`, p
  )).rows;
  const holds = (await query(
    `SELECT product_id, COALESCE(SUM(qty_milli),0)::bigint AS q FROM stock_reservations
      WHERE shop_id=$1 AND status='held' ${pf} GROUP BY product_id`, p
  )).rows;

  const key = (a, b) => `${a}\u0000${b}`;
  const lev = new Map(levels.map((r) => [key(r.product_id, r.location), num(r.on_hand_milli)]));
  const led = new Map();
  const ledRes = new Map();
  let moves = 0;
  for (const r of ledger) {
    moves += num(r.moves);
    if (r.location) led.set(key(r.product_id, r.location), num(r.on_hand));
    ledRes.set(r.product_id, (ledRes.get(r.product_id) || 0) + num(r.reserved));
  }
  const hold = new Map(holds.map((r) => [r.product_id, num(r.q)]));
  const mismatches = [];
  for (const k of new Set([...lev.keys(), ...led.keys()])) {
    const a = lev.get(k) || 0;
    const b = led.get(k) || 0;
    if (a !== b) {
      const [pid, loc] = k.split('\u0000');
      mismatches.push({ productId: pid, location: loc, levelMilli: a, ledgerMilli: b });
    }
  }
  for (const pid of new Set([...ledRes.keys(), ...hold.keys()])) {
    const a = hold.get(pid) || 0;
    const b = ledRes.get(pid) || 0;
    if (a !== b) mismatches.push({ productId: pid, location: '', heldMilli: a, ledgerReservedMilli: b });
  }

  let replay = null;
  if (productId) {
    const docs = (await query(
      `SELECT source_key, type, qty_milli, location, actor_type, actor_id, reason, at FROM stock_moves
        WHERE shop_id=$1 AND product_id=$2 ORDER BY created_at, id`, [shopId, productId]
    )).rows;
    let book = core.emptyBook();
    for (const m of docs) {
      const doc = {
        id: m.source_key, type: m.type, productId, warehouseId: m.location || AGG,
        qtyMilli: num(m.qty_milli), userId: m.actor_id || m.actor_type, at: num(m.at) || 1, reason: m.reason || 'ledger',
      };
      if (core.TYPES[m.type].cost === 'required') doc.costMinor = 0;
      const out = core.apply(book, doc, { mode: 'recorded' });
      if (!out.result.ok) {
        mismatches.push({ productId, sourceKey: m.source_key, replayError: out.result.error });
        continue;
      }
      book = out.book;
    }
    replay = {};
    for (const pos of core.positions(book)) {
      if (pos.warehouseId === AGG) replay.reservedMilli = pos.reservedMilli;
      else replay[pos.warehouseId] = pos.qtyMilli;
    }
    for (const loc of LOCATIONS) {
      if ((replay[loc] || 0) !== (lev.get(key(productId, loc)) || 0)) {
        mismatches.push({ productId, location: loc, replayMilli: replay[loc] || 0, levelMilli: lev.get(key(productId, loc)) || 0 });
      }
    }
    if ((replay.reservedMilli || 0) !== (hold.get(productId) || 0)) {
      mismatches.push({ productId, location: '', replayReservedMilli: replay.reservedMilli || 0, heldMilli: hold.get(productId) || 0 });
    }
  }
  return { ok: mismatches.length === 0, moves, mismatches, replay };
}

module.exports = {
  FLAG_KEY, LOCATIONS, SELLABLE_CHOICES, DEFAULTS,
  flagOf, settingsOf, saveSettings, lock, decide,
  levelsOf, freeOf, sellableOnHand, onHandAt, fmtMilli,
  insertMove, postMove, openTask, checkProducts,
  check, outOfStock,
  reconcile,
};
