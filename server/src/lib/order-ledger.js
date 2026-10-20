'use strict';
/**
 * پیوندِ سفارشِ آنلاین با فروشِ دفترِ دکان — **فقط‌خواندنی**.
 *
 * ⛔ تصمیمِ صاحبِ مخزن (۱۴۰۵/۰۷/۲۷): سمتِ مشتری به موجودی دست نمی‌زند. سرور دیگر هیچ
 * فاکتورِ W، قلم، حرکتِ انبار یا سندِ برگشتی در دفترِ دکان (`oplog`/`sync_rows`) نمی‌نویسد
 * و هیچ موجودی‌ای رزرو یا کم نمی‌کند. وقتی دکان سفارش را تحویل می‌دهد، **برنامهٔ دکان**
 * (اندروید و کامپیوتر، `OrderFulfillment.kt`) یک فروشِ عادی با `SalesEngine` ثبت می‌کند:
 *
 *   شناسه ── فروش `o-<شناسهٔ سفارش>` با سریِ `O` و شمارهٔ همان سفارش؛ قلم‌ها، حرکت‌ها و
 *            ممیزی هم شناسهٔ ثابتِ مشتق از سفارش دارند. دو دستگاه یا تلاشِ دوباره ⇒ **همان
 *            ردیف‌ها و همان مقدارها**؛ Sync v1 آن‌ها را روی هم می‌نشاند ⇒ یک فروش، یک بار کسر.
 *   لحظه ── `HANDED_OVER`: مشتری گرفت (`picked_up`)، به پیک داده شد (`out_for_delivery`) یا
 *            رسید (`delivered`) — هر کدام زودتر. زمان و روزِ فاکتور = زمانِ همان گذار.
 *   برگشت ── سفارشی که پس از بیرون رفتن به دکان برگشت (`returned_to_store`/`restocked`) ⇒
 *            برنامه همان فروشِ `o-…` را **لغو** می‌کند (وضعیت، نه شمارنده: لغو تکرارپذیر
 *            است ولی `returnedQty` در Sync v1 `$inc` است و دو دستگاه آن را دو بار می‌افزودند).
 *   جاافتاده ─ [handoverPending]: سفارش‌های بیرون‌رفته‌ای که فروشی (`o-…` یا فاکتورِ W ِ
 *            قدیمی `w-…`) در دفترِ همگام‌شدهٔ دکان ندارند، و سفارش‌های برگشته‌ای که فروشِ
 *            لغونشده دارند. برنامه پیش از **هر** ثبت همین را برای همان سفارش می‌پرسد.
 *   کف ───── [FLOOR] (زمانِ به‌کارگیریِ این قابلیت روی سرور؛ `ORDERS_HANDOVER_FLOOR_MS`):
 *            تحویلِ پیش از آن هرگز فهرست نمی‌شود — آن روزها یا فاکتورِ W بود، یا کسرِ سروری
 *            (`res:…:sale`)، یا فروشِ دستیِ صندوق.
 *   اجازه ── [capsOf]: فقط نقشی که `sales`/`saleItems`/`stockMovements`/`auditLog` را می‌نویسد
 *            (و با تخفیف، `sales.discount`؛ برای لغو، `sales.cancel`) ثبت می‌کند؛ وگرنه
 *            Sync v1 ردیف را رد می‌کرد و فروش فقط روی همان دستگاه می‌ماند.
 *
 * فاکتورهای W ِ پیش از این تصمیم (`order_ledger_docs`، migration 095، و حلقه‌هایشان در
 * `hash_chain`) دست‌نخورده می‌مانند؛ این پرونده فقط می‌خواندشان (`docsOf`) و برنامه آن‌ها
 * را «فروشِ همین سفارش» می‌شمارد تا دوباره کم نشود. پرچمِ `shop.orders.ledger` دیگر
 * خوانده نمی‌شود.
 *
 * ⛔ `lib/sync-v1.js` دست نمی‌خورد.
 */
const { can } = require('./permissions');

const SERIES = 'W';
/** سریِ فاکتورِ تحویلِ سفارش در برنامه (هیچ دستگاهی آن را نمی‌گیرد: `doc-series.LETTERS` بی O) */
const ORDER_SERIES = 'O';
/** گذارهایی که کالا را از دکان بیرون می‌برند ⇒ برنامه فروش را ثبت می‌کند */
const HANDED_OVER = Object.freeze(['picked_up', 'out_for_delivery', 'delivered']);
/** وضعیت‌هایی که کالا هنوز بیرون است ⇒ فروش باید در دفتر باشد */
const OUT_OF_SHOP = Object.freeze([
  'picked_up', 'out_for_delivery', 'delivered', 'delivery_failed', 'retry_scheduled', 'return_requested',
]);
/** کالا به دکان برگشت ⇒ فروشِ تحویل لغو می‌شود */
const BACK_IN_SHOP = Object.freeze(['returned_to_store', 'restocked']);
const MAX_PENDING = 50;
/** کف: زمانِ به‌کارگیریِ «فروشِ تحویل در برنامه» روی سرور (پیش‌فرض: روزِ ادغام) */
const FLOOR = (() => {
  const v = Number(process.env.ORDERS_HANDOVER_FLOOR_MS);
  return Number.isSafeInteger(v) && v > 0 ? v : Date.UTC(2026, 9, 20);
})();
/** جدول‌هایی که ثبتِ فروشِ تحویل می‌نویسد */
const SALE_TABLES = Object.freeze(['sales', 'saleItems', 'stockMovements', 'auditLog']);

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

/**
 * آیا این نقش فروشِ تحویل را می‌تواند بنویسد؟ — همان چیزی که Sync v1 می‌سنجد: `data.write`،
 * و اگر هستهٔ اجازه (`perm-core.canWrite`، نقش‌های تازهٔ #336) هست، نوشتنِ همهٔ جدول‌ها.
 * تخفیف ⇒ `sales.discount` (`authorizeOp`: discount_not_allowed)؛ لغو ⇒ `sales.cancel`.
 */
function capsOf(role) {
  let canWrite = null;
  try { canWrite = require('./perm-core').canWrite; } catch { /* پیش از #336 */ }
  const writes = (t) => (typeof canWrite === 'function' ? canWrite(role, t) !== false : true);
  const recordSale = can(role, 'data.write') && SALE_TABLES.every(writes);
  return {
    recordSale,
    saleDiscount: recordSale && can(role, 'sales.discount'),
    saleCancel: recordSale && can(role, 'sales.cancel'),
  };
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

function parseAfter(raw) {
  if (!raw) return null;
  const m = /^(\d{1,16}):([A-Za-z0-9_-]{1,64})$/.exec(String(raw));
  if (!m) throw require('../middleware/errors').badRequest('نشانگرِ صفحه معتبر نیست', 'bad_cursor');
  return { at: Number(m[1]), id: m[2] };
}

/**
 * سفارش‌هایی که برنامه باید برایشان کاری بکند — فقط‌خواندنی، به ترتیبِ زمانِ تحویل.
 *
 *   items ────── `action: 'sale'`: بیرون رفته (OUT_OF_SHOP)، **یکی از** گذارهای تحویلش
 *                از `max(since, FLOOR)` به بعد است (سفارشی که پیش از آن «ارسال» و پس از آن
 *                «رسید» شده هم می‌آید)، و هیچ فروشی (`o-…`/`w-…`) در دفترِ همگام‌شده ندارد.
 *                صفحه‌بندی با `after` (`<زمان>:<شناسه>`)؛ سفارشی که برنامه نتوانست ثبت کند
 *                (کمبود، اجازه) جلوی بقیه را نمی‌گیرد.
 *   reversals ── `action: 'reverse'`: به دکان برگشته و فروشِ `o-…` ِ لغونشده دارد.
 *   `orderId` ⇒ فقط همان سفارش (برنامه پیش از هر ثبتِ دستی یا پس از «وضعیت عوض شد» می‌پرسد).
 * سفارشِ آزمایشی هرگز.
 */
async function handoverPending(shopId, { since = 0, after = '', orderId = '', limit } = {}, viewer) {
  const db = require('../db');
  const orders = require('./orders');
  const lim = Math.min(Math.max(Number(limit) || 20, 1), MAX_PENDING);
  const asked = Number(since);
  const from = Math.max(FLOOR, Number.isSafeInteger(asked) && asked > 0 ? asked : 0);
  const cur = parseAfter(after);
  const only = String(orderId || '').slice(0, 64);
  const v3 = (await db.one("SELECT to_regclass('sync_v3_rows') AS t")).t;
  const rowsOf = (table, cond) => `SELECT 1 FROM ${table} r WHERE r.app='shop' AND r.account_kind='shop'
        AND r.account_id = o.shop_id AND r.table_name='sales' AND r.deleted_at IS NULL AND ${cond}`;
  const noSale = (table) => `NOT EXISTS (${rowsOf(table, "r.row_id IN ('o-' || o.id, 'w-' || o.id)")})`;
  const liveSale = (table) => `EXISTS (${rowsOf(table, "r.row_id = 'o-' || o.id AND COALESCE(r.data->>'status', '') <> 'cancelled'")})`;

  const params = [shopId, HANDED_OVER, OUT_OF_SHOP, from];
  let extra = '';
  if (only) { params.push(only); extra += ` AND o.id = $${params.length}`; }
  let having = '';
  if (cur) {
    params.push(cur.at, cur.id);
    having = `HAVING MIN(e.created_at) > $${params.length - 1} OR (MIN(e.created_at) = $${params.length - 1} AND o.id > $${params.length})`;
  }
  params.push(lim + 1);
  const saleRows = await db.many(
    `SELECT o.id, MIN(e.created_at)::bigint AS at
       FROM online_orders o
       JOIN online_order_events e ON e.order_id = o.id AND e.kind = 'status'
                                 AND e.to_status = ANY($2::text[]) AND e.created_at >= $4
      WHERE o.shop_id = $1 AND o.is_test = false AND o.status = ANY($3::text[]) AND o.updated_at >= $4 ${extra}
        AND ${noSale('sync_rows')} ${v3 ? `AND ${noSale('sync_v3_rows')}` : ''}
      GROUP BY o.id
      ${having}
      ORDER BY 2, 1
      LIMIT $${params.length}`,
    params
  );
  const page = saleRows.slice(0, lim);
  const last = page[page.length - 1];

  const rparams = [shopId, BACK_IN_SHOP, from];
  let rextra = '';
  if (only) { rparams.push(only); rextra = ` AND o.id = $${rparams.length}`; }
  const backRows = await db.many(
    `SELECT o.id, MIN(e.created_at)::bigint AS at
       FROM online_orders o
       JOIN online_order_events e ON e.order_id = o.id AND e.kind = 'status' AND e.to_status = ANY($2::text[])
      WHERE o.shop_id = $1 AND o.is_test = false AND o.status = ANY($2::text[]) AND o.updated_at >= $3 ${rextra}
        AND (${liveSale('sync_rows')}${v3 ? ` OR ${liveSale('sync_v3_rows')}` : ''})
      GROUP BY o.id
      ORDER BY 2, 1
      LIMIT ${MAX_PENDING}`,
    rparams
  );

  const view = async (r, action) => {
    const o = await orders.get(shopId, r.id, viewer);
    const at = Number(r.at);
    return { ...o, action, saleId: saleIdOf(r.id), ...(action === 'sale' ? { handedOverAt: at } : { returnedAt: at }) };
  };
  const items = [];
  for (const r of page) items.push(await view(r, 'sale'));
  const reversals = [];
  for (const r of backRows) reversals.push(await view(r, 'reverse'));
  return {
    items, reversals, since: from,
    nextAfter: saleRows.length > lim && last ? `${Number(last.at)}:${last.id}` : null,
    serverTime: Date.now(),
  };
}

module.exports = {
  SERIES, ORDER_SERIES, HANDED_OVER, OUT_OF_SHOP, BACK_IN_SHOP, MAX_PENDING, FLOOR, SALE_TABLES,
  saleIdOf, legacySaleIdOf, chainOf, docsOf, capsOf, handoverPending,
};
