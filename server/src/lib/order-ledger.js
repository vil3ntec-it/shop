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
 *   کف ───── **هر دکان جدا**، در «تنظیماتِ زنده» (`live_config`، scope = دکان، کلیدِ [FLOOR_KEY]،
 *            رزرو: پنل نمی‌نویسد/پاک نمی‌کند) — یک بار، هرگز جابه‌جا، هرگز زودتر از زمانِ به‌کارگیری
 *            ([deployFloor]، `ORDERS_HANDOVER_FLOOR_MS`):
 *              • دکانی که کسرِ سفارشیِ سرور داشت (`shop.orders.ledger` یا `shop.catalog` روشن) ⇒
 *                لحظهٔ **راه‌اندازیِ سرور** ([claimFloorsAtStart]) — تحویلِ پس از استقرار گم نمی‌شود؛
 *              • بقیه ⇒ نخستین `GET /orders/config?caps=saleOnHandover` ([claimFloor]).
 *            فروشِ خودکار فقط وقتی **نخستین** گذارِ تحویل پس از کف است. سفارشی که پیش از کف بیرون
 *            رفت و پس از آن گذارِ تحویلِ دیگری گرفت («ارسال» پیش، «رسید» پس) ⇒ `review`
 *            (`before_floor`): شاید دستگاهِ کهنه دستی فروخته باشد ⇒ صاحب تصمیم می‌گیرد. تا کف
 *            نیست، هیچ (برنامهٔ کهنه فروش نمی‌سازد).
 *   اجازه ── **هر سفارش جدا** ([judgeOf] ⇒ `recordable`/`reversible` روی هر ردیف): همان سنجشِ
 *            Sync v1 برای **همین کاربر** — نوشتنِ `sales`/`saleItems`/`stockMovements`/`auditLog`
 *            ([capsOf]؛ شاگرد بی اشتراکِ `multi_device` نه، همان `requireSyncWrite` — [capsFor])، و با تخفیف `sales.discount` با اجازهٔ مؤثر (لایه‌های نقش و عضو،
 *            `perm-core.evaluate`) روی درصد و مبلغِ تخفیفِ **همین سفارش**؛ برای لغو `sales.cancel`.
 *            «نیازمندِ تأیید» یا بیش از سقف ⇒ نه (v1 بی برگه ردش می‌کرد و فروش فقط روی همان دستگاه
 *            می‌ماند) ⇒ برنامه آن را به دستگاهِ صاحب/مدیر می‌سپارد و در فهرست می‌شمارد.
 *
 * فاکتورهای W ِ پیش از این تصمیم (`order_ledger_docs`، migration 095، و حلقه‌هایشان در
 * `hash_chain`) دست‌نخورده می‌مانند؛ این پرونده فقط می‌خواندشان (`docsOf`) و برنامه آن‌ها
 * را «فروشِ همین سفارش» می‌شمارد تا دوباره کم نشود. پرچمِ `shop.orders.ledger` دیگر
 * خوانده نمی‌شود.
 *
 * ⛔ `lib/sync-v1.js` دست نمی‌خورد.
 */
const { can } = require('./permissions');
const core = require('./perm-core');
const staffPerms = require('./staff-perms');

const SERIES = 'W';
/** سریِ فاکتورِ تحویلِ سفارش در برنامه (هیچ دستگاهی آن را نمی‌گیرد: `doc-series.LETTERS` بی O) */
const ORDER_SERIES = 'O';
/** گذارهایی که کالا را از دکان بیرون می‌برند ⇒ برنامه فروش را ثبت می‌کند */
const HANDED_OVER = Object.freeze(['picked_up', 'out_for_delivery', 'delivered']);
/**
 * وضعیت‌هایی که کالا هنوز بیرون است ⇒ فروش باید در دفتر باشد. `refunded_partial`: بخشی از پول
 * برگشت ولی کالا پیشِ مشتری ماند (`refunded_full` نه: کالا یا پولش کامل برگشته است).
 */
const OUT_OF_SHOP = Object.freeze([
  'picked_up', 'out_for_delivery', 'delivered', 'delivery_failed', 'retry_scheduled', 'return_requested',
  'refunded_partial',
]);
/** کالا به دکان برگشت ⇒ فروشِ تحویل لغو می‌شود */
const BACK_IN_SHOP = Object.freeze(['returned_to_store', 'restocked']);
const MAX_PENDING = 50;
/** کلیدِ کفِ هر دکان در «تنظیماتِ زنده» (scope = شناسهٔ دکان) */
const FLOOR_KEY = 'shop.orders.handover_floor';
/** پرچمِ توانِ برنامه در `GET /orders/config?caps=…` */
const SALE_CAP = 'saleOnHandover';

/**
 * زمانِ به‌کارگیریِ این قابلیت روی سرور (`ORDERS_HANDOVER_FLOOR_MS`، میلی‌ثانیه) — کفِ هیچ دکانی
 * پیش از آن نیست. بی env ⇒ ۰: کفِ ذخیره‌شده خودش لحظهٔ نخستین config پس از به‌کارگیری است.
 * هر بار خوانده می‌شود (عوض کردنش راه‌اندازیِ دوباره نمی‌خواهد).
 */
function deployFloor() {
  const v = Number(process.env.ORDERS_HANDOVER_FLOOR_MS);
  return Number.isSafeInteger(v) && v > 0 ? v : 0;
}
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

/**
 * [capsOf] + اشتراکِ دکان، همان `requireSyncWrite` ِ push: صاحب و مدیر همیشه؛ شاگرد فقط با
 * `multi_device` (وگرنه push ِ فروش ۴۰۳ می‌گرفت و فروش فقط روی همان گوشی می‌ماند).
 */
async function capsFor(shopId, role) {
  const base = capsOf(role);
  if (!base.recordSale || role === 'owner' || role === 'manager') return base;
  const { entitlementOf } = require('./entitlement');
  const ent = await entitlementOf(shopId);
  if (ent.features.includes('multi_device')) return base;
  return { recordSale: false, saleDiscount: false, saleCancel: false };
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

/** کفِ ذخیره‌شدهٔ دکان (میلی‌ثانیه) — یا null اگر هنوز هیچ برنامهٔ «فروشِ تحویل» آن را ندیده */
async function floorOf(shopId) {
  const db = require('../db');
  const r = await db.one('SELECT value FROM live_config WHERE scope=$1 AND key=$2', [String(shopId), FLOOR_KEY]);
  const v = r ? Number(decode(r.value)) : NaN;
  return Number.isSafeInteger(v) && v > 0 ? v : null;
}

/** کفِ مؤثرِ دکان: max(کفِ ذخیره‌شده، زمانِ به‌کارگیری) — یا null (هیچ تحویلی فهرست نمی‌شود) */
async function sinceOf(shopId) {
  const v = await floorOf(shopId);
  return v === null ? null : Math.max(v, deployFloor());
}

/**
 * برنامه‌ای که «فروشِ تحویل» دارد این دکان را می‌بیند ⇒ کف اگر نیست همین لحظه (زمانِ سرور) گذاشته
 * می‌شود؛ هست ⇒ دست نمی‌خورد (دو دستگاهِ هم‌زمان: نخستین درج می‌ماند). ⇒ کفِ مؤثر.
 */
async function claimFloor(shopId, by = '') {
  const have = await sinceOf(shopId);
  if (have !== null) return have;
  const db = require('../db');
  await require('./live-config').setOnce(String(shopId), FLOOR_KEY, Math.max(db.now(), deployFloor()), `app:${String(by || '')}`.slice(0, 80));
  return sinceOf(shopId);
}

/** پرچم‌هایی که کدِ پیش از این تصمیم با آن‌ها سفارش را خودکار در موجودی/دفتر می‌نشاند */
const LEGACY_FLAGS = Object.freeze(['shop.orders.ledger', 'shop.catalog']);

/**
 * راه‌اندازیِ سرور: هر دکانی که یکی از [LEGACY_FLAGS] برایش روشن است (`true` ِ خودِ دکان، یا
 * `all` بی `false` ِ خودِ دکان) و هنوز کف ندارد ⇒ کف = همین لحظه (زمانِ سرور). پس کفِ این دکان‌ها
 * لحظهٔ استقرار است، نه نخستین config ِ برنامهٔ تازه: سفارشی که میانِ این دو تحویل شد (و کدِ
 * پیشین خودش کم می‌کرد، پس دستگاهِ کهنه دستی نمی‌فروخت) گم نمی‌شود. تکرار ⇒ هیچ. ⇒ شمارِ تازه‌ها
 */
async function claimFloorsAtStart(at) {
  const db = require('../db');
  const t = Math.max(Number.isSafeInteger(at) ? at : db.now(), deployFloor());
  const rows = await db.many(
    'SELECT scope, key, value FROM live_config WHERE key = ANY($1::text[])', [[...LEGACY_FLAGS, FLOOR_KEY]]
  );
  const allOn = new Set();
  const own = new Map();                     //  دکان ⇒ Map(پرچم ⇒ مقدار)
  const floored = new Set();
  for (const r of rows) {
    if (r.key === FLOOR_KEY) { floored.add(r.scope); continue; }
    const v = decode(r.value) === true;
    if (r.scope === 'all') { if (v) allOn.add(r.key); continue; }
    if (!own.has(r.scope)) own.set(r.scope, new Map());
    own.get(r.scope).set(r.key, v);
  }
  const shops = new Set();
  for (const [shop, flags] of own) if ([...flags.values()].some(Boolean)) shops.add(shop);
  if (allOn.size) {
    for (const r of await db.many('SELECT id FROM shops')) {
      const flags = own.get(r.id);
      if ([...allOn].some((k) => !flags || !flags.has(k) || flags.get(k))) shops.add(r.id);
    }
  }
  const lc = require('./live-config');
  let n = 0;
  for (const shop of shops) {
    if (floored.has(shop) || !/^[A-Za-z0-9_-]{1,80}$/.test(shop)) continue;
    const r = await lc.setOnce(shop, FLOOR_KEY, t, 'system:start');
    if (r.inserted) n += 1;
  }
  return n;
}

/**
 * اجازهٔ ثبت و لغوِ فروشِ تحویل برای **همین کاربر** و **همین سفارش** — همان سنجشی که Sync v1
 * (`authorizeOp` + `staff-approval.v1Guard`) روی opهای همان فروش می‌کند:
 *   ثبت ⇒ [capsOf].recordSale؛ با تخفیف: `can(sales.discount)` و `evaluate(sales.discount,
 *          {pct, amount})` = allow — درصد از جمعِ پیش از تخفیف (قلم‌ها + کرایه = `sale.total`).
 *   لغو ⇒ recordSale، `can(sales.cancel)` و `evaluate(sales.cancel)` = allow.
 * «نیازمندِ تأیید» = نه: برنامه برگه‌ای نمی‌آورد و v1 بی برگه ردش می‌کند.
 */
async function judgeOf(shopId, viewer) {
  const role = viewer && viewer.role;
  const base = await capsFor(shopId, role);
  const layers = await staffPerms.layersFor(shopId, role, viewer && viewer.id);
  const allow = (perm, req) => core.evaluate(role, layers, perm, req).decision === 'allow';
  return {
    recordable(o) {
      if (!base.recordSale) return false;
      const d = Number(o.discountMinor) || 0;
      if (d <= 0) return true;
      const bp = core.discountBp(d, (Number(o.subtotalMinor) || 0) + (Number(o.deliveryFeeMinor) || 0));
      return can(role, 'sales.discount') && allow('sales.discount', { pct: bp / 100, amount: d });
    },
    reversible(o) {
      return base.recordSale && can(role, 'sales.cancel') && allow('sales.cancel', { amount: Number(o.totalMinor) || 0 });
    },
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
 *   items ────── `action: 'sale'`: بیرون رفته (OUT_OF_SHOP)، **نخستین** گذارِ تحویلش از
 *                `max(since, کفِ دکان)` به بعد است، و هیچ فروشی (`o-…`/`w-…`) در دفترِ همگام‌شده ندارد.
 *                `recordable`: همین کاربر می‌تواند فروشِ همین سفارش را بنویسد ([judgeOf]).
 *                صفحه‌بندی با `after` (`<زمان>:<شناسه>`)؛ سفارشی که برنامه نتوانست ثبت کند
 *                (کمبود، اجازه) جلوی بقیه را نمی‌گیرد.
 *   reversals ── `action: 'reverse'`: به دکان برگشته و فروشِ `o-…` ِ لغونشده دارد؛ `reversible`.
 *   review ───── `action: 'review'`، `reviewCode: 'before_floor'`: بیرون‌رفتهٔ بی‌فروش که نخستین گذارِ
 *                تحویلش **پیش از کف** و گذارِ تحویلِ دیگری **پس از کف** دارد — هرگز خودکار نه؛ صاحب
 *                بررسی می‌کند (شاید دستگاهِ کهنه دستی فروخته باشد).
 *   `orderId` ⇒ فقط همان سفارش (برنامه پیش از هر ثبتِ دستی یا پس از «وضعیت عوض شد» می‌پرسد).
 * سفارشِ آزمایشی هرگز. دکانی که هنوز کف ندارد ⇒ هیچ (`since: null`).
 */
async function handoverPending(shopId, { since = 0, after = '', orderId = '', limit } = {}, viewer) {
  const db = require('../db');
  const orders = require('./orders');
  const lim = Math.min(Math.max(Number(limit) || 20, 1), MAX_PENDING);
  const cur = parseAfter(after);
  const floor = await sinceOf(shopId);
  if (floor === null) return { items: [], reversals: [], review: [], since: null, nextAfter: null, serverTime: Date.now() };
  const asked = Number(since);
  const from = Math.max(floor, Number.isSafeInteger(asked) && asked > 0 ? asked : 0);
  const only = String(orderId || '').slice(0, 64);
  const v3 = (await db.one("SELECT to_regclass('sync_v3_rows') AS t")).t;
  const rowsOf = (table, cond) => `SELECT 1 FROM ${table} r WHERE r.app='shop' AND r.account_kind='shop'
        AND r.account_id = o.shop_id AND r.table_name='sales' AND r.deleted_at IS NULL AND ${cond}`;
  const noSale = (table) => `NOT EXISTS (${rowsOf(table, "r.row_id IN ('o-' || o.id, 'w-' || o.id)")})`;
  const liveSale = (table) => `EXISTS (${rowsOf(table, "r.row_id = 'o-' || o.id AND COALESCE(r.data->>'status', '') <> 'cancelled'")})`;

  const params = [shopId, HANDED_OVER, OUT_OF_SHOP, from];
  let extra = '';
  if (only) { params.push(only); extra += ` AND o.id = $${params.length}`; }
  //  **نخستین** گذارِ تحویل از کف به بعد (نه «هر» گذار): سفارشی که پیش از کف بیرون رفت ⇒ review
  let having = 'HAVING MIN(e.created_at) >= $4';
  if (cur) {
    params.push(cur.at, cur.id);
    having += ` AND (MIN(e.created_at) > $${params.length - 1} OR (MIN(e.created_at) = $${params.length - 1} AND o.id > $${params.length}))`;
  }
  params.push(lim + 1);
  const saleRows = await db.many(
    `SELECT o.id, MIN(e.created_at)::bigint AS at
       FROM online_orders o
       JOIN online_order_events e ON e.order_id = o.id AND e.kind = 'status' AND e.to_status = ANY($2::text[])
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

  //  پیش از کف بیرون رفت، پس از آن گذارِ تحویلِ دیگری گرفت ⇒ بررسیِ صاحب (فقط صفحهٔ نخست)
  const vparams = [shopId, HANDED_OVER, OUT_OF_SHOP, floor];
  let vextra = '';
  if (only) { vparams.push(only); vextra = ` AND o.id = $${vparams.length}`; }
  const reviewRows = cur ? [] : await db.many(
    `SELECT o.id, MIN(e.created_at)::bigint AS at
       FROM online_orders o
       JOIN online_order_events e ON e.order_id = o.id AND e.kind = 'status' AND e.to_status = ANY($2::text[])
      WHERE o.shop_id = $1 AND o.is_test = false AND o.status = ANY($3::text[]) AND o.updated_at >= $4 ${vextra}
        AND ${noSale('sync_rows')} ${v3 ? `AND ${noSale('sync_v3_rows')}` : ''}
      GROUP BY o.id
     HAVING MIN(e.created_at) < $4 AND MAX(e.created_at) >= $4
      ORDER BY 2, 1
      LIMIT ${MAX_PENDING}`,
    vparams
  );

  const judge = await judgeOf(shopId, viewer);
  const view = async (r, action) => {
    const o = await orders.get(shopId, r.id, viewer);
    const at = Number(r.at);
    const extra = action === 'sale' ? { handedOverAt: at, recordable: judge.recordable(o) }
      : action === 'reverse' ? { returnedAt: at, reversible: judge.reversible(o) }
        : { handedOverAt: at, reviewCode: 'before_floor' };
    return { ...o, action, saleId: saleIdOf(r.id), ...extra };
  };
  const items = [];
  for (const r of page) items.push(await view(r, 'sale'));
  const reversals = [];
  for (const r of backRows) reversals.push(await view(r, 'reverse'));
  const review = [];
  for (const r of reviewRows) review.push(await view(r, 'review'));
  return {
    items, reversals, review, since: from,
    nextAfter: saleRows.length > lim && last ? `${Number(last.at)}:${last.id}` : null,
    serverTime: Date.now(),
  };
}

module.exports = {
  SERIES, ORDER_SERIES, HANDED_OVER, OUT_OF_SHOP, BACK_IN_SHOP, MAX_PENDING, FLOOR_KEY, SALE_CAP, SALE_TABLES,
  LEGACY_FLAGS, saleIdOf, legacySaleIdOf, chainOf, docsOf, capsOf, capsFor, judgeOf, deployFloor, floorOf, sinceOf,
  claimFloor, claimFloorsAtStart, handoverPending,
};
