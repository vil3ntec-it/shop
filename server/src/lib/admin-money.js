'use strict';
/**
 * S124 (§۲۱.۱۹) — «گزارشِ پولِ مدیر»: هفت عددِ جدا در یک جا، هر کدام از سندِ خودش (نه تخمین).
 * سوار بر `GET /api/admin/system/reconciliation?money=1` (همان مرزِ مدیرِ ارشد، همان بازه و همان ردیفِ سابقه).
 *
 *   فروشِ صندوق ── فاکتورها و برگشتی‌های همگام‌شدهٔ هر دکان (`owner-report.salesLedgerOf`: همان ردیف‌ها و همان قاعدهٔ
 *                  بازهٔ گزارشِ صاحب) با هنجارِ `report-core.ledger`: فاکتورِ لغوشده بیرون، `finalTotal` ِ فاکتور،
 *                  `amount` ِ برگشتی (برگشتِ فاکتورِ لغوشده بیرون). ⛔ فاکتورِ تحویلِ سفارشِ آنلاین (`o-…` ِ
 *                  `OrderFulfillment`، `channel = online`، و W ِ قدیمیِ `w-…`) و برگشتی‌اش این‌جا شمرده **نمی‌شود**:
 *                  همان پول در «فروشِ آنلاین» است ⇒ هیچ فروشی دو بار در GMV نمی‌آید.
 *   فروشِ آنلاین ─ همان `sales` ِ ردیف‌های تطبیق (تحویل/برده‌شده در بازه، بی سفارشِ آزمایشی).
 *   GMV ───────── فروشِ صندوق + فروشِ آنلاین، پیش از برگشت.
 *   بازپرداخت ─── برگشتیِ صندوق + بازپرداختِ آنلاین (`online_order_refunds`)، جدا و جمع؛ `netMinor` = GMV − بازپرداخت.
 *   وصول‌شده ──── نقدی که پیکِ دکان در بازه از مشتری گرفت (`courier.collectedMinor`)؛ «تحویلِ دکان» جدا (`receivedMinor`).
 *   تسویه‌نشده ── نقدِ تحویل‌شده‌ای که هنوز تسویه نشده، همین لحظه (`courier.heldMinor`).
 *   درآمدِ پلتفرم ─ همان `central-finance.summary` ِ بخشِ دکان در همان بازه (هر ارز جدا، به یکای `sub_payments`).
 *
 *   ⛔ دکانی که مدیر «آزمایشی» نشان زده (`shop_census_marks.kind = 'test'`) در هیچ عددِ دکانیِ این بلوک نیست
 *     (`testShopsExcluded`). پول به یک‌صدمِ افغانی (عددِ صحیح). بیش از [maxShops] دکانِ صندوق ⇒ `pos.truncated`؛
 *     بیش از سقفِ ردیف در یک دکان ⇒ `pos.partial` (همان `MAX_ROWS` ِ گزارشِ صاحب). هیچ نام، شماره یا نشانیِ مشتری.
 */
const { many } = require('../db');
const R = require('./report-core');
const rep = require('./owner-report');

const ONLINE_ID = /^[ow]-/;
const N = (v) => Number(v) || 0;

const isOnlineSale = (s) => s.channel === 'online' || ONLINE_ID.test(s.id);

/** جمعِ صندوقِ یک دکان در بازهٔ ms (همان قاعدهٔ `rowsInWindow`: `createdAt`، وگرنه `date` ِ روزِ محلی). */
function posTotals(raw, w) {
  const L = R.ledger(raw);
  const inWin = (x) => (x.createdAt > 0 ? x.createdAt >= w.fromAt && x.createdAt < w.until : x.date >= w.from && x.date <= w.to);
  const t = { invoices: 0, salesMinor: 0, returnCount: 0, returnsMinor: 0 };
  for (const s of L.sales) {
    if (s.cancelled || isOnlineSale(s) || !inWin(s)) continue;
    t.invoices += 1;
    t.salesMinor += s.finalTotal;
  }
  for (const r of L.returns) {
    const s = L.saleById[r.saleId] || null;
    if ((s && (s.cancelled || isOnlineSale(s))) || ONLINE_ID.test(r.saleId) || !inWin(r)) continue;
    t.returnCount += 1;
    t.returnsMinor += r.amount;
  }
  return t;
}

const WIN = `((${rep.CREATED} >= $1 AND ${rep.CREATED} < $2) OR (${rep.CREATED} = 0 AND data->>'date' >= $3 AND data->>'date' <= $4))`;
const HAS_POS = (T) => `EXISTS (SELECT 1 FROM ${T} r WHERE r.app = 'shop' AND r.account_kind = 'shop' AND r.account_id = s.id
                       AND r.table_name IN ('sales','returns') AND ${WIN})`;

/** دکان‌های بی نشانِ «آزمایشی» که در بازه فاکتور یا برگشتیِ همگام‌شده دارند (هر دو انبارِ v1 و v3). */
async function posShops(w, maxShops) {
  return many(
    `SELECT s.id AS shop_id FROM shops s
      WHERE NOT EXISTS (SELECT 1 FROM shop_census_marks m WHERE m.shop_id = s.id AND m.kind = 'test')
        AND (${HAS_POS('sync_rows')} OR ${HAS_POS('sync_v3_rows')})
      ORDER BY s.id LIMIT $5`,
    [w.fromAt, w.until, w.from, w.to, maxShops + 1]
  );
}

async function posOf(w, maxShops) {
  const shops = await posShops(w, maxShops);
  const out = { shops: 0, invoices: 0, salesMinor: 0, returnCount: 0, returnsMinor: 0, netMinor: 0, partial: false,
    truncated: shops.length > maxShops };
  for (const { shop_id: id } of shops.slice(0, maxShops)) {
    const L = await rep.salesLedgerOf(id, w);
    const t = posTotals({ sales: L.sales, returns: L.returns }, w);
    if (L.partial) out.partial = true;
    if (!t.invoices && !t.returnCount) continue;
    out.shops += 1;
    for (const k of ['invoices', 'salesMinor', 'returnCount', 'returnsMinor']) out[k] += t[k];
  }
  out.netMinor = out.salesMinor - out.returnsMinor;
  return out;
}

/**
 * بلوکِ `money` ِ گزارشِ تطبیق. `win` = {from, to, fromDate, toDate} ِ `reconWindow`؛ `rows` = همهٔ ردیف‌های دکانِ
 * همان پاسخ (پیش از برش به `maxShops`).
 */
async function moneyOf(win, rows, { maxShops = 200 } = {}) {
  const w = { fromAt: win.from, until: win.to, from: win.fromDate, to: win.toDate };
  const test = new Set((await many(`SELECT shop_id FROM shop_census_marks WHERE kind = 'test'`)).map((r) => r.shop_id));
  const live = rows.filter((r) => !test.has(r.shopId));
  const sum = (f) => live.reduce((a, r) => a + N(f(r)), 0);
  const online = {
    orders: sum((r) => r.sales.count), salesMinor: sum((r) => r.sales.totalMinor),
    refundCount: sum((r) => r.refunds.count), refundsMinor: sum((r) => r.refunds.totalMinor),
  };
  online.netMinor = online.salesMinor - online.refundsMinor;
  const [pos, platform] = await Promise.all([
    posOf(w, maxShops),
    require('./central-finance').summary({ from: win.from, to: win.to, app: 'shop' }),
  ]);
  const gmvMinor = pos.salesMinor + online.salesMinor;
  const refundsMinor = pos.returnsMinor + online.refundsMinor;
  return {
    currency: 'AFN', testShopsExcluded: test.size,
    gmvMinor, posSalesMinor: pos.salesMinor, onlineSalesMinor: online.salesMinor,
    refundsMinor, netMinor: gmvMinor - refundsMinor,
    collectedMinor: sum((r) => r.courier.collectedMinor), handedOverMinor: sum((r) => r.courier.receivedMinor),
    unsettledMinor: sum((r) => r.courier.heldMinor),
    pos, online,
    platformRevenue: { app: 'shop', currencies: platform.currencies, totals: platform.totals },
  };
}

module.exports = { moneyOf, posTotals, isOnlineSale };
