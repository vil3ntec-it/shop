'use strict';
/**
 * M5 — رزروِ «نرم» ِ موجودی برای سفارشِ آنلاین (قراردادِ بازار §۱.۴، §۱.۷ و ردیفِ M5 ِ §۳؛ v3 §۳۲.۲ بندِ ۲).
 *
 *   کِی ─── فقط وقتی هر دو پرچم روشن‌اند (`shop.catalog` و `shop.stock_reserve`، هر دو پیش‌فرض خاموش)
 *           **و** موجودیِ سرور «تازه» است (`catalog.freshness`). وگرنه همان رفتارِ امروز: بی رزرو،
 *           «در حال بررسی» (تصمیمِ ۱۴۰۵/۰۷/۲۷ برای آن حالت به جای خود است). سفارشِ آزمایشی هرگز.
 *   چه ─── ساختِ سفارش (`orders.create`) زیرِ قفلِ `orders:<دکان>` و سپس `catalog:<دکان>` (همان قفلِ
 *           همهٔ نوشتن‌های موجودی) «آزاد = قابل‌فروش − رزروِ فعالِ دیگران» را می‌سنجد و برای هر قلم یک
 *           ردیفِ `stock_reservations` (`kind = soft`) با `expires_at = اکنون + reserve_ttl_sec` و یک حرکتِ
 *           `reserve` در دفتر می‌نویسد ⇒ دو مشتریِ هم‌زمانِ آخرین عدد: دقیقاً یکی می‌گذرد، دیگری ۴۰۹
 *           `out_of_stock` با پیامِ روشن. تکرارِ همان کلیدِ یکتایی پیش از این‌جا برمی‌گردد (دو بار رزرو نیست)
 *           و نمایهٔ یکتای `(shop, order, line) WHERE held` پشتوانهٔ دوم است.
 *   عمر ── قبول ⇒ بی انقضا تا پایانِ سفارش؛ رد/لغو/ردِ خودکار/تحویل ⇒ آزاد؛ فروشِ تحویلِ همان سفارش
 *           (`o-<سفارش>-…` یا `w-<سفارش>-…`) که از دستگاه رسید ⇒ «انجام‌شده» (همان لحظه که موجودی کم شد،
 *           رزرو برداشته می‌شود ⇒ دو بار شمرده نمی‌شود). انقضا: تنبل هنگامِ خواندن (`heldOf` فقط
 *           نگه‌داشتهٔ نگذشته را می‌شمارد) + جاروی دوره‌ای (`sweep`) که ردیف و دفتر را می‌بندد.
 *   INV-19  فروشِ حضوری **هرگز** رد نمی‌شود و رزرو جلویش را نمی‌گیرد (سندِ `recorded` ِ دستگاه این‌جا
 *           نمی‌آید). اگر فروشِ حضوری موجودیِ رزروشده را برد، **سفارش** به بررسی می‌رود، نه فروش:
 *           تازه‌ترین رزروها `short_at` می‌گیرند، وظیفهٔ `reservation_short` باز می‌شود و جزئیاتِ سفارش
 *           `stockHold.review = true` نشان می‌دهد. دکان تصمیم می‌گیرد (مثلاً ردِ «ناموجود»).
 *   دفتر ─ هر رزرو/آزادسازی یک حرکتِ `reserve`/`release` با کلیدِ یکتا (`res:<رزرو>:hold|release|commit`)
 *           ⇒ تطبیقِ INV-16 (`stock.reconcile`، `verify-data` ِ `sum.stock_reserved`) همان می‌ماند.
 */
const { query, many, now, tx } = require('../db');
const stock = require('./stock');
const { ulid } = require('./ulid');

const DEFAULT_TTL_SEC = 1800;
/** وضعیت‌هایی که رزرو را آزاد می‌کنند (پایانِ سفارش یا کالا دست به دست شد) */
const RELEASE_ON = Object.freeze(['rejected', 'auto_rejected', 'cancelled_by_customer', 'cancelled',
  'picked_up', 'delivered', 'returned_to_store', 'restocked']);
const TICK_MS = 60 * 1000;
const BATCH = 100;

const num = (v) => (v === null || v === undefined ? 0 : Number(v));
const runOf = (c) => (c ? (t, p) => c.query(t, p) : query);

async function enabled(shopId, c) {
  return (await stock.flagOf(shopId, c)) && (await stock.flagOf(shopId, c, stock.RESERVE_FLAG_KEY));
}

async function ttlSecOf(c, shopId) {
  const r = (await runOf(c)('SELECT reserve_ttl_sec FROM catalog_settings WHERE shop_id=$1', [shopId])).rows[0];
  const v = r ? num(r.reserve_ttl_sec) : DEFAULT_TTL_SEC;
  return Number.isSafeInteger(v) && v >= 60 ? v : DEFAULT_TTL_SEC;
}

/** رزروِ فعال (نگه‌داشته و نگذشته) به تفکیکِ کالا ⇒ Map<pid, milli>. بی ردیف ⇒ Map ِ خالی. */
async function heldOf(c, shopId, ids, at = now()) {
  const map = new Map();
  if (!ids || !ids.length) return map;
  const rows = (await runOf(c)(
    `SELECT product_id, COALESCE(SUM(qty_milli),0)::bigint AS q FROM stock_reservations
      WHERE shop_id=$1 AND kind='soft' AND status='held' AND product_id = ANY($2::text[])
        AND (expires_at IS NULL OR expires_at > $3)
      GROUP BY product_id`, [shopId, ids, at]
  )).rows;
  for (const r of rows) map.set(r.product_id, num(r.q));
  return map;
}

/** حرکتِ آزادسازیِ دفتر برای ردیف‌هایی که همین حالا بسته شدند */
async function postRelease(c, shopId, rows, suffix, reason, at) {
  for (const r of rows) {
    await stock.insertMove(c, shopId, {
      productId: r.product_id, location: '', type: 'release', qtyMilli: num(r.qty_milli),
      deltaOnHand: 0, deltaReserved: -num(r.qty_milli), mode: 'local', source: 'order',
      sourceKey: `res:${r.id}:${suffix}`, refType: 'order', refId: r.order_id,
      actorType: 'system', actorId: '', reason, at,
    }, at);
  }
}

/** رزروهای گذشتهٔ یک دکان ⇒ `expired` + آزادسازی در دفتر. درونِ تراکنش و زیرِ قفلِ `catalog:<دکان>`. */
async function expireDueIn(c, shopId, at) {
  const rows = (await c.query(
    `UPDATE stock_reservations SET status='expired', closed_at=$2, close_reason='ttl'
      WHERE shop_id=$1 AND kind='soft' AND status='held' AND expires_at IS NOT NULL AND expires_at <= $2
      RETURNING id, product_id, order_id, qty_milli`, [shopId, at]
  )).rows;
  await postRelease(c, shopId, rows, 'release', 'reservation_expired', at);
  return rows.length;
}

/**
 * درونِ `catalog.checkOrder` (تراکنشِ `orders.create`): آیا این سفارش رزرو می‌گیرد؟ ⇒ null یا
 * `{ held, ttlSec }`. روشن و تازه ⇒ قفلِ موجودیِ دکان گرفته و رزروهای گذشته بسته می‌شوند، تا سنجش و
 * نوشتنِ رزرو هر دو زیرِ همان قفل باشند (هیچ فروش/رزروِ دیگری میانِ این دو نمی‌نشیند).
 */
async function planIn(c, shopId, settings, ids, at) {
  if (!(await stock.flagOf(shopId, c, stock.RESERVE_FLAG_KEY))) return null;
  const fr = await require('./catalog').freshness(shopId, settings, at);
  if (!fr.fresh) return null;
  await stock.lock(c, shopId);
  await expireDueIn(c, shopId, at);
  return { held: await heldOf(c, shopId, ids, at), ttlSec: await ttlSecOf(c, shopId) };
}

/** پس از درجِ سفارش، در همان تراکنش و زیرِ همان قفل: یک رزرو برای هر قلمِ کالادار. */
async function holdIn(c, shopId, { orderId, lines, ttlSec, at }) {
  const expires = at + ttlSec * 1000;
  for (const l of lines) {
    const id = ulid(at);
    await c.query(
      `INSERT INTO stock_reservations (id, shop_id, order_id, line_no, product_id, branch_id, qty_milli,
         status, expires_at, created_at, kind)
       VALUES ($1,$2,$3,$4,$5,'',$6,'held',$7,$8,'soft')`,
      [id, shopId, orderId, l.lineNo, l.productId, l.qtyMilli, expires, at]
    );
    await stock.insertMove(c, shopId, {
      productId: l.productId, location: '', type: 'reserve', qtyMilli: l.qtyMilli,
      deltaOnHand: 0, deltaReserved: l.qtyMilli, mode: 'local', source: 'order',
      sourceKey: `res:${id}:hold`, refType: 'order', refId: orderId,
      actorType: 'system', actorId: '', reason: 'online_order', at,
    }, at);
  }
  return { expiresAt: expires };
}

/**
 * درونِ تراکنشِ گذارِ سفارش (`orders.applyTransition`): قبول ⇒ بی انقضا تا پایانِ سفارش (رزروِ
 * گذشته برنمی‌گردد)؛ پایان/تحویل ⇒ آزاد. سفارشِ بی رزروِ نرم ⇒ یک پرسشِ خالی و هیچ.
 */
async function onTransitionIn(c, shopId, orderId, to, at) {
  if (to !== 'accepted' && !RELEASE_ON.includes(to)) return;
  const any = (await c.query(
    `SELECT 1 FROM stock_reservations WHERE shop_id=$1 AND order_id=$2 AND kind='soft' AND status='held' LIMIT 1`,
    [shopId, orderId]
  )).rows.length > 0;
  if (!any) return;
  await stock.lock(c, shopId);
  await expireDueIn(c, shopId, at);
  if (to === 'accepted') {
    await c.query(
      `UPDATE stock_reservations SET expires_at=NULL
        WHERE shop_id=$1 AND order_id=$2 AND kind='soft' AND status='held'`, [shopId, orderId]
    );
    return;
  }
  const rows = (await c.query(
    `UPDATE stock_reservations SET status='released', closed_at=$3, close_reason=$4
      WHERE shop_id=$1 AND order_id=$2 AND kind='soft' AND status='held'
      RETURNING id, product_id, order_id, qty_milli`, [shopId, orderId, at, `order_${to}`]
  )).rows;
  await postRelease(c, shopId, rows, 'release', `order_${to}`, at);
  //  کمبودِ سفارش‌های دیگرِ همان کالاها ممکن است حالا پوشش داشته باشد
  if (rows.length) await recheckShort(c, shopId, [...new Set(rows.map((r) => r.product_id))].sort(), at);
}

/** «بررسی»: تازه‌ترین رزروهایی که موجودیِ قابل‌فروش دیگر پوششان نمی‌دهد `short_at` می‌گیرند. */
async function recheckShort(c, shopId, ids, at, settings = null) {
  if (!ids.length) return [];
  const s = settings || await stock.settingsOf(shopId, c);
  const lvs = await stock.levelsOf(c, shopId, ids);
  const rows = (await c.query(
    `SELECT id, order_id, line_no, product_id, qty_milli, short_at FROM stock_reservations
      WHERE shop_id=$1 AND kind='soft' AND status='held' AND product_id = ANY($2::text[])
        AND (expires_at IS NULL OR expires_at > $3)
      ORDER BY product_id, created_at, id`, [shopId, ids, at]
  )).rows;
  const byPid = new Map();
  for (const r of rows) {
    if (!byPid.has(r.product_id)) byPid.set(r.product_id, []);
    byPid.get(r.product_id).push(r);
  }
  const shortAll = [];
  for (const [pid, list] of byPid) {
    const sell = Math.max(0, stock.sellableOnHand(lvs.get(pid), s));
    let cum = 0;
    const short = [];
    for (const r of list) {
      cum += num(r.qty_milli);
      const isShort = cum > sell;
      if (isShort) short.push(r);
      if (isShort && r.short_at === null) {
        await c.query('UPDATE stock_reservations SET short_at=$2 WHERE id=$1', [r.id, at]);
      } else if (!isShort && r.short_at !== null) {
        await c.query('UPDATE stock_reservations SET short_at=NULL WHERE id=$1', [r.id]);
      }
    }
    if (short.length) {
      await stock.openTask(c, shopId, {
        kind: 'reservation_short', productId: pid, dedupe: `soft-short:${pid}`,
        detail: { sellableMilli: sell, heldMilli: cum, orderIds: [...new Set(short.map((r) => r.order_id))].slice(0, 50) },
      }, at);
      shortAll.push(...short);
    }
  }
  return shortAll;
}

/**
 * از `stock.checkProducts` (پس از هر دسته حرکتِ همگام‌سازی یا حرکتِ دستی، زیرِ قفلِ دکان): ۱) رزروِ
 * سفارشی که فروشِ تحویلش از دستگاه رسید ⇒ «انجام‌شده»؛ ۲) کمبود ⇒ بررسیِ سفارش. بی رزروِ نرم ⇒ یک پرسشِ
 * خالی و هیچ (پرچمِ خاموش هرگز ردیف ندارد).
 */
async function afterMovesIn(c, shopId, ids, settings, at) {
  if (!ids.length) return;
  const any = (await c.query(
    `SELECT 1 FROM stock_reservations WHERE shop_id=$1 AND kind='soft' AND status='held'
        AND product_id = ANY($2::text[]) LIMIT 1`, [shopId, ids]
  )).rows.length > 0;
  if (!any) return;
  await expireDueIn(c, shopId, at);
  const done = (await c.query(
    `UPDATE stock_reservations r SET status='committed', closed_at=$3, close_reason='fulfilled'
      WHERE r.shop_id=$1 AND r.kind='soft' AND r.status='held' AND r.product_id = ANY($2::text[])
        AND EXISTS (SELECT 1 FROM stock_sources s WHERE s.shop_id=r.shop_id
                     AND (s.source_key LIKE 'si:o-' || r.order_id || '-%' OR s.source_key LIKE 'si:w-' || r.order_id || '-%'))
      RETURNING r.id, r.product_id, r.order_id, r.qty_milli`, [shopId, ids, at]
  )).rows;
  await postRelease(c, shopId, done, 'commit', 'order_fulfilled', at);
  await recheckShort(c, shopId, ids, at, settings);
}

/** خلاصهٔ رزروِ یک سفارش برای جزئیاتِ سفارش ⇒ null (بی رزروِ نرم) یا { status, review, expiresAt, lines }. */
async function orderView(run, shopId, orderId, at = now()) {
  const rows = (await run(
    `SELECT line_no, status, expires_at, short_at, close_reason FROM stock_reservations
      WHERE shop_id=$1 AND order_id=$2 AND kind='soft' ORDER BY line_no, created_at`, [shopId, orderId]
  )).rows;
  if (!rows.length) return null;
  const lines = rows.map((r) => {
    let st = r.status;
    if (st === 'held' && r.expires_at !== null && num(r.expires_at) <= at) st = 'expired';
    return { lineNo: num(r.line_no), status: st, short: st === 'held' && r.short_at !== null };
  });
  const held = lines.filter((l) => l.status === 'held');
  const exp = rows.filter((r) => r.status === 'held' && r.expires_at !== null && num(r.expires_at) > at)
    .map((r) => num(r.expires_at));
  let status = rows[rows.length - 1].status;
  if (held.length) status = 'held';
  else if (lines.some((l) => l.status === 'expired')) status = 'expired';
  else if (lines.some((l) => l.status === 'committed')) status = 'committed';
  return {
    status, review: held.some((l) => l.short),
    expiresAt: exp.length ? Math.min(...exp) : null, lines,
  };
}

/** جاروی دوره‌ای: رزروهای گذشته ⇒ بسته در ردیف و دفتر (هر دکان در تراکنش و قفلِ خودش). */
async function sweep(at = now()) {
  const shops = await many(
    `SELECT DISTINCT shop_id FROM stock_reservations
      WHERE status='held' AND kind='soft' AND expires_at IS NOT NULL AND expires_at <= $1 LIMIT $2`, [at, BATCH]
  );
  let n = 0;
  for (const { shop_id: shopId } of shops) {
    try {
      n += await tx(async (c) => {
        await stock.lock(c, shopId);
        return expireDueIn(c, shopId, at);
      });
    } catch (err) {
      console.error('[stock-reserve] sweep', shopId, err.message);
    }
  }
  return n;
}

let timer = null;
let sweeping = false;
async function tick() {
  if (sweeping) return 0;
  sweeping = true;
  try { return await sweep(); } finally { sweeping = false; }
}
function start() {
  if (timer) return;
  timer = setInterval(() => tick().catch((err) => console.error('[stock-reserve]', err.message)), TICK_MS);
  if (timer.unref) timer.unref();
}
function stop() {
  if (timer) clearInterval(timer);
  timer = null;
}

module.exports = {
  DEFAULT_TTL_SEC, RELEASE_ON,
  enabled, ttlSecOf, heldOf, expireDueIn, planIn, holdIn, onTransitionIn, recheckShort, afterMovesIn,
  orderView, sweep, start, stop,
};
