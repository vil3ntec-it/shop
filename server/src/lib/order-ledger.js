'use strict';
/**
 * پیوندِ سفارشِ آنلاین با فروشِ دفترِ دکان — **فقط‌خواندنی**.
 *
 * ⛔ تصمیمِ صاحبِ مخزن (۱۴۰۵/۰۷/۲۷): سمتِ مشتری به موجودی دست نمی‌زند. سرور دیگر هیچ
 * فاکتورِ W، قلم، حرکتِ انبار یا سندِ برگشتی در دفترِ دکان (`oplog`/`sync_rows`) نمی‌نویسد
 * و هیچ موجودی‌ای رزرو یا کم نمی‌کند. وقتی دکان سفارش را تحویل می‌دهد، **برنامهٔ دکان**
 * (اندروید و کامپیوتر، `OrderFulfillment.kt`) یک فروشِ عادی با `SalesEngine` ثبت می‌کند:
 *
 *   شناسه ── فروش `o-<شناسهٔ سفارش>`؛ قلم‌ها، حرکت‌ها و ممیزی هم شناسهٔ ثابتِ مشتق از
 *            سفارش دارند (`OrderFulfillment.ids`). دو دستگاه یا تلاشِ دوباره ⇒ **همان
 *            ردیف‌ها**؛ Sync v1 آن‌ها را روی هم می‌نشاند (همان `row_id`) ⇒ یک فروش، یک بار
 *            کسرِ موجودی. برنامه پیش از ساختن هم دفتر را می‌گردد (`orderId`).
 *   لحظه ── `HANDED_OVER`: مشتری گرفت (`picked_up`)، به پیک داده شد (`out_for_delivery`)
 *            یا رسید (`delivered`) — هر کدام زودتر. برگشت/لغو پس از آن «مرجوعیِ» عادیِ
 *            همان فاکتور در برنامه است.
 *   جاافتاده ─ گذاری که دستگاهِ دکان نزده (پیک از برنامهٔ پیک، یا دستگاهی که پیش از ثبت
 *            بسته شد) را `handoverPending` به برنامه می‌دهد: سفارش‌های بیرون‌رفته‌ای که
 *            فروشی (`o-…` یا فاکتورِ W ِ قدیمی `w-…`) در دفترِ همگام‌شدهٔ دکان ندارند.
 *
 * فاکتورهای W ِ پیش از این تصمیم (`order_ledger_docs`، migration 095، و حلقه‌هایشان در
 * `hash_chain`) دست‌نخورده می‌مانند؛ این پرونده فقط می‌خواندشان (`docsOf`) و برنامه آن‌ها
 * را «فروشِ همین سفارش» می‌شمارد تا دوباره کم نشود. پرچمِ `shop.orders.ledger` دیگر
 * خوانده نمی‌شود.
 *
 * ⛔ `lib/sync-v1.js` دست نمی‌خورد.
 */

const SERIES = 'W';
/** گذارهایی که کالا را از دکان بیرون می‌برند ⇒ برنامه فروش را ثبت می‌کند */
const HANDED_OVER = Object.freeze(['picked_up', 'out_for_delivery', 'delivered']);
/** وضعیت‌هایی که کالا هنوز بیرون است ⇒ فروش باید در دفتر باشد (برگشته به دکان دیگر نه) */
const OUT_OF_SHOP = Object.freeze([
  'picked_up', 'out_for_delivery', 'delivered', 'delivery_failed', 'retry_scheduled', 'return_requested',
]);
const MAX_PENDING = 50;

/** شناسهٔ فروشِ برنامه برای یک سفارش — همان `OrderFulfillment.saleIdOf` */
const saleIdOf = (orderId) => `o-${orderId}`;
/** فاکتورِ W ِ قدیمیِ سرور برای همان سفارش (پیش از این تصمیم) */
const legacySaleIdOf = (orderId) => `w-${orderId}`;

const part = (v) => String(v || '').replace(/[^A-Za-z0-9_.:-]/g, '_').slice(0, 60) || '-';
/** نامِ زنجیرهٔ هشِ سریِ W ِ یک دکان (فقط برای سنجشِ سندهای قدیمی) */
function chainOf(shopId) {
  return `orders/${part(shopId)}/${SERIES}`;
}

function decode(v) {
  if (typeof v !== 'string') return v;
  try { return JSON.parse(v); } catch { return null; }
}

function docView(r) {
  if (!r) return null;
  return {
    id: r.id, orderId: r.order_id, kind: r.kind, series: r.series, docNo: Number(r.doc_no),
    label: `${r.series}-${Number(r.doc_no)}`, saleRowId: r.sale_row_id, onStatus: r.on_status,
    totalMinor: Number(r.total_minor), lines: decode(r.lines) || [], ops: Number(r.ops),
    firstSeq: r.first_seq === null ? null : Number(r.first_seq), lastSeq: r.last_seq === null ? null : Number(r.last_seq),
    chain: r.chain, chainSeq: Number(r.chain_seq), hash: r.hash, createdAt: Number(r.created_at),
  };
}

/** سندهای W ِ قدیمیِ یک سفارش (پنل و آزمون) */
async function docsOf(run, orderId) {
  const q = typeof run === 'function' ? run : (t, p) => run.query(t, p);
  return (await q('SELECT * FROM order_ledger_docs WHERE order_id=$1 ORDER BY kind DESC', [orderId])).rows.map(docView);
}

/**
 * سفارش‌های تحویل‌شده‌ای که فروششان هنوز در دفترِ همگام‌شدهٔ دکان نیست — برنامه برای هر
 * کدام همان فروشِ عادی را می‌سازد (شناسهٔ ثابت ⇒ تکرار بی‌اثر).
 *
 * `since` (میلی‌ثانیه): فقط سفارش‌هایی که **نخستین** گذارِ تحویلشان از این لحظه به بعد است.
 * برنامه لحظهٔ نخستین به‌کارگیریِ همین قابلیت را می‌فرستد تا سفارش‌های تحویل‌شدهٔ پیش از
 * آن (که دکان شاید دستی در صندوق زده) دوباره فروخته نشوند. سفارشِ آزمایشی هرگز.
 * ⇒ { items: [جزئیاتِ سفارش با قلم‌ها و `handedOverAt`], serverTime }
 */
async function handoverPending(shopId, { since = 0, limit } = {}, viewer) {
  const db = require('../db');
  const orders = require('./orders');
  const lim = Math.min(Math.max(Number(limit) || 20, 1), MAX_PENDING);
  const from = Number.isSafeInteger(Number(since)) && Number(since) > 0 ? Number(since) : 0;
  const v3 = (await db.one("SELECT to_regclass('sync_v3_rows') AS t")).t;
  const none = (table) => `NOT EXISTS (SELECT 1 FROM ${table} r WHERE r.app='shop' AND r.account_kind='shop'
        AND r.account_id = o.shop_id AND r.table_name='sales' AND r.row_id IN ('o-' || o.id, 'w-' || o.id)
        AND r.deleted_at IS NULL)`;
  const rows = await db.many(
    `SELECT o.id, MIN(e.created_at)::bigint AS handed_at
       FROM online_orders o
       JOIN online_order_events e ON e.order_id = o.id AND e.kind = 'status' AND e.to_status = ANY($2::text[])
      WHERE o.shop_id = $1 AND o.is_test = false AND o.status = ANY($3::text[])
        AND ${none('sync_rows')} ${v3 ? `AND ${none('sync_v3_rows')}` : ''}
      GROUP BY o.id
     HAVING MIN(e.created_at) >= $4
      ORDER BY MIN(e.created_at), o.id
      LIMIT $5`,
    [shopId, HANDED_OVER, OUT_OF_SHOP, from, lim]
  );
  const items = [];
  for (const r of rows) {
    const o = await orders.get(shopId, r.id, viewer);
    items.push({ ...o, handedOverAt: Number(r.handed_at), saleId: saleIdOf(r.id) });
  }
  return { items, serverTime: Date.now() };
}

module.exports = {
  SERIES, HANDED_OVER, OUT_OF_SHOP, MAX_PENDING,
  saleIdOf, legacySaleIdOf, chainOf, docsOf, handoverPending,
};
