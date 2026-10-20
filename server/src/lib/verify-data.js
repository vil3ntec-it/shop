'use strict';
/**
 * verify-data — بررسیِ **فقط‌خواندنیِ** ناوردایی‌های داده (DATA-*، بخشِ ۲۹.۵ و ۳.۴).
 *
 * هر بررسی یک پرس‌وجوی جداست که ردیف‌های ناقض را می‌شمارد و چند نمونه (فقط
 * شناسه، بی نام و تلفن) برمی‌گرداند. همه داخلِ یک تراکنشِ `READ ONLY` می‌دوند:
 * اگر روزی پرس‌وجویی به اشتباه بنویسد، خودِ پایگاه داده آن را رد می‌کند.
 *
 * چهار خانواده:
 *   money.* / qty.*   پول عددِ صحیح به «واحدِ کوچک» (یک‌صدمِ افغانی) و مقدار به «هزارم» —
 *                     در دفترِ JSONِ همگام‌سازی یعنی: عدد باشد، بیش از ۲ (پول) یا ۳ (مقدار)
 *                     رقمِ اعشار نداشته باشد و از ۲^۵۳−۱ بیرون نزند. ستون‌های `*_minor`
 *                     و `*_milli` ِ SQL خودشان bigint‌اند.
 *   orphan.*          ردیفِ بی‌صاحب: قلمِ فروشِ بی فاکتور، تراکنشِ بی قرض‌دار، دفترِ بی دکان،
 *                     ردیفِ سفارشی که `shop_id`ش با سفارش نمی‌خواند (مرزِ دکان).
 *   append_only.*     دفترهای فقط‌افزودنی: هر ردیفِ `sync_rows` opِ نویسنده‌اش را در
 *                     `oplog` دارد؛ رویدادها و بازپرداخت‌های سفارش شماره‌های پیوسته از ۱
 *                     دارند و آخرین رویدادِ وضعیت همان وضعیتِ سفارش است؛ زنجیرهٔ هش سالم است.
 *   sum.*             جمع‌ها: جمعِ فاکتور = جمعِ قلم‌ها، نهایی = جمع − تخفیف، پرداخت + مانده
 *                     = نهایی، مرجوعی = جمعِ برگشت‌ها، جمعِ سفارش = جمعِ ردیف‌ها، موجودیِ هر جا
 *                     = جمعِ حرکت‌های دفترِ موجودی، رزروِ باز = جمعِ حرکتِ رزرو/آزادسازی، …
 *
 * شدت: `error` = دادهٔ خراب یا ناسازگار (کدِ خروجِ ۲)؛ `warn` = خلافِ سیاست در دفترِ
 * قدیمی که هنوز راهِ درستش ساخته نشده (فقط گزارش).
 *
 * ⛔ هیچ چیزی نمی‌نویسد، هیچ چیزی را «درست» نمی‌کند. اصلاح، سندِ اصلاحی با دلیل است (۲۹.۵ بند ۶).
 */
const db = require('../db');

const MAX_SAFE = '9007199254740991';
const SAMPLES = 5;

/* ---------- فیلدهای پول و مقدار در دفترِ JSON (نام‌های `bodyOf`ِ sync-core.js) ---------- */
const MONEY_FIELDS = {
  products: ['salePrice', 'purchasePrice', 'wholesalePrice'],
  sales: ['total', 'discount', 'finalTotal', 'paidAmount', 'remaining', 'debtGiven', 'debtSettled'],
  saleItems: ['unitPrice', 'purchasePrice', 'totalPrice'],
  returns: ['amount'],
  transactions: ['amount'],
  expenses: ['amount'],
  warehouseEntries: ['price'],
  purchases: ['totalAmount', 'paidAmount'],
  supplierPayments: ['amount'],
};
//  [جدول، فیلد، باید مثبت باشد]
const QTY_FIELDS = [
  ['saleItems', 'quantity', true],
  ['saleItems', 'returnedQty', false],
  ['returns', 'quantity', true],
  ['warehouseEntries', 'units', false],
  ['stockMovements', 'qty', false],
];
//  [فرزند، فیلد، والد، والد باید زنده باشد، خالی مجاز است]
const RELATIONS = [
  ['saleItems', 'saleId', 'sales', true, false],
  ['saleItems', 'productId', 'products', false, false],
  ['returns', 'saleId', 'sales', false, false],
  ['returns', 'saleItemId', 'saleItems', false, false],
  ['transactions', 'debtorId', 'debtors', false, false],
  ['sales', 'debtorId', 'debtors', false, true],
  ['warehouseEntries', 'productId', 'products', false, false],
  ['stockMovements', 'productId', 'products', false, true],
];
//  سندهای مالیِ قطعی — حذفِ نرمشان «حذفِ خاموش» است (۲۹.۵ بند ۶)
const POSTED = ['sales', 'saleItems', 'returns', 'transactions'];

/* ---------- منبع‌های دفتر: Sync v1 (برنامه‌های امروز) و Sync v3 (پشتِ پرچم) ---------- */
const SOURCES = [
  { name: 'v1', rows: 'sync_rows', ops: 'oplog' },
  { name: 'v3', rows: 'sync_v3_rows', ops: 'sync_v3_ops' },
];

const quote = (s) => `'${String(s).replace(/'/g, "''")}'`;
const valuesOf = (tuples) => tuples.map((t) => `(${t.map((v) => (typeof v === 'number' || typeof v === 'boolean' ? String(v) : quote(v))).join(',')})`).join(',');
const LEDGER = (a) => `${a}.app = 'shop' AND ${a}.account_kind = 'shop' AND ($1::text IS NULL OR ${a}.account_id = $1)`;

/**
 * یک عدد در JSON که باید «دقیق در مقیاس» باشد. CASE ترتیبِ ارزیابی را تضمین می‌کند:
 * رشتهٔ ناعددی هرگز به numeric تبدیل نمی‌شود.
 */
const badNumber = (expr, scale) => `CASE WHEN jsonb_typeof(${expr}) <> 'number' THEN true
  ELSE ((${expr})::text::numeric * ${scale}) <> trunc((${expr})::text::numeric * ${scale})
    OR abs((${expr})::text::numeric * ${scale}) > ${MAX_SAFE} END`;
//  عددِ JSON یا NULL — هرگز رشتهٔ ناعددی را cast نمی‌کند (ترتیبِ شرط‌های WHERE تضمین‌شده نیست)
const num = (a, f) => `(CASE WHEN jsonb_typeof(${a}.data->'${f}') = 'number' THEN (${a}.data->>'${f}')::numeric END)`;
const isNum = (a, f) => `jsonb_typeof(${a}.data->'${f}') = 'number'`;

function ledgerChecks(src) {
  const R = src.rows;
  const money = Object.entries(MONEY_FIELDS).flatMap(([t, fs]) => fs.map((f) => [t, f]));
  const sumWhere = (a) => `${LEDGER(a)} AND ${a}.deleted_at IS NULL`;
  return [
    {
      id: 'money.minor_units', severity: 'error',
      title: 'مبلغ عدد نیست، بیش از ۲ رقمِ اعشار (کسری از پول) دارد، یا از ۲^۵۳−۱ بیرون است',
      sql: `WITH spec(t, f) AS (VALUES ${valuesOf(money)})
        SELECT r.account_id, r.table_name || '/' || r.row_id || '.' || s.f AS ref, r.data->>s.f AS value
          FROM ${R} r JOIN spec s ON s.t = r.table_name
         WHERE ${sumWhere('r')} AND jsonb_typeof(r.data->s.f) IS NOT NULL AND jsonb_typeof(r.data->s.f) <> 'null'
           AND ${badNumber('r.data->s.f', 100)}`,
    },
    {
      id: 'qty.thousandths', severity: 'error',
      title: 'مقدار عدد نیست، بیش از ۳ رقمِ اعشار (هزارم) دارد، بیرون از بازه است، یا جایی که باید مثبت باشد نیست',
      sql: `WITH spec(t, f, pos) AS (VALUES ${valuesOf(QTY_FIELDS)})
        SELECT r.account_id, r.table_name || '/' || r.row_id || '.' || s.f AS ref, r.data->>s.f AS value
          FROM ${R} r JOIN spec s ON s.t = r.table_name
         WHERE ${sumWhere('r')} AND jsonb_typeof(r.data->s.f) IS NOT NULL AND jsonb_typeof(r.data->s.f) <> 'null'
           AND (${badNumber('r.data->s.f', 1000)}
                OR (s.pos AND (CASE WHEN jsonb_typeof(r.data->s.f) = 'number' THEN (r.data->>s.f)::numeric END) <= 0))`,
    },
    {
      id: 'orphan.ledger_row', severity: 'error',
      title: 'ردیفِ زنده‌ای که به ردیفِ ناموجود (یا فاکتورِ حذف‌شده) اشاره می‌کند',
      sql: `WITH rel(t, f, p, alive, optional) AS (VALUES ${valuesOf(RELATIONS)})
        SELECT c.account_id, c.table_name || '/' || c.row_id || '.' || rel.f AS ref, c.data->>rel.f AS value
          FROM ${R} c JOIN rel ON rel.t = c.table_name
         WHERE ${sumWhere('c')}
           AND NOT (rel.optional AND COALESCE(c.data->>rel.f, '') = '')
           AND NOT EXISTS (
             SELECT 1 FROM ${R} p
              WHERE p.app = c.app AND p.account_kind = c.account_kind AND p.account_id = c.account_id
                AND p.table_name = rel.p AND p.row_id = COALESCE(c.data->>rel.f, '')
                AND (NOT rel.alive OR p.deleted_at IS NULL))`,
    },
    {
      id: 'orphan.ledger_account', severity: 'error',
      title: 'دفترِ همگام‌سازی برای دکانی که دیگر وجود ندارد',
      sql: `SELECT r.account_id, r.account_id AS ref, count(*)::text AS value
          FROM ${R} r WHERE ${LEDGER('r')}
           AND NOT EXISTS (SELECT 1 FROM shops s WHERE s.id = r.account_id)
         GROUP BY r.account_id`,
    },
    {
      id: 'append_only.oplog_missing', severity: 'error',
      title: `ردیفی که opِ آخرین نویسنده‌اش در ${src.ops} نیست (دفترِ فقط‌افزودنی دست خورده)`,
      sql: `SELECT r.account_id, r.table_name || '/' || r.row_id AS ref, r.updated_seq::text AS value
          FROM ${R} r
         WHERE ${LEDGER('r')}
           AND NOT EXISTS (
             SELECT 1 FROM ${src.ops} o
              WHERE o.app = r.app AND o.account_kind = r.account_kind AND o.account_id = r.account_id
                AND o.table_name = r.table_name AND o.row_id = r.row_id AND o.server_seq = r.updated_seq)`,
    },
    {
      id: 'append_only.posted_deleted', severity: 'warn',
      title: 'سندِ مالیِ قطعی (فاکتور، قلم، مرجوعی، تراکنش) حذفِ نرم شده — اصلاح باید با سندِ برگشتی باشد',
      sql: `SELECT r.account_id, r.table_name || '/' || r.row_id AS ref, r.deleted_at::text AS value
          FROM ${R} r
         WHERE ${LEDGER('r')} AND r.deleted_at IS NOT NULL AND r.table_name IN (${POSTED.map(quote).join(',')})`,
    },
    {
      id: 'sum.sale_total', severity: 'error',
      title: 'جمعِ فاکتور با جمعِ قلم‌هایش نمی‌خواند',
      sql: `SELECT s.account_id, 'sales/' || s.row_id AS ref, (s.data->>'total') || ' ≠ ' || i.sum::text AS value
          FROM ${R} s
          JOIN (SELECT account_id, data->>'saleId' AS sale_id, sum(CASE WHEN jsonb_typeof(data->'totalPrice') = 'number' THEN (data->>'totalPrice')::numeric END) AS sum
                  FROM ${R} WHERE app = 'shop' AND account_kind = 'shop' AND table_name = 'saleItems'
                   AND deleted_at IS NULL AND jsonb_typeof(data->'totalPrice') = 'number'
                   AND ($1::text IS NULL OR account_id = $1)
                 GROUP BY 1, 2) i ON i.account_id = s.account_id AND i.sale_id = s.row_id
         WHERE ${sumWhere('s')} AND s.table_name = 'sales' AND ${isNum('s', 'total')}
           AND abs(${num('s', 'total')} - i.sum) >= 0.005`,
    },
    {
      id: 'sum.sale_final', severity: 'error',
      title: 'مبلغِ نهایی ≠ بیشینهٔ (صفر، جمع − تخفیف)، یا پرداخت + مانده ≠ نهایی، یا تسویه از نسیه بیشتر است',
      sql: `SELECT s.account_id, 'sales/' || s.row_id AS ref,
                   concat_ws(' ', s.data->>'total', s.data->>'discount', s.data->>'finalTotal', s.data->>'paidAmount', s.data->>'remaining') AS value
          FROM ${R} s
         WHERE ${sumWhere('s')} AND s.table_name = 'sales'
           AND ${isNum('s', 'total')} AND ${isNum('s', 'finalTotal')}
           AND (abs(${num('s', 'finalTotal')} - greatest(0, ${num('s', 'total')} - COALESCE(${num('s', 'discount')}, 0))) >= 0.005
             OR (${isNum('s', 'paidAmount')} AND ${isNum('s', 'remaining')}
                 AND abs(${num('s', 'paidAmount')} + ${num('s', 'remaining')} - ${num('s', 'finalTotal')}) >= 0.005)
             OR (${isNum('s', 'debtSettled')} AND ${num('s', 'debtSettled')}
                 > COALESCE(${num('s', 'debtGiven')}, 0) + 0.004))`,
    },
    {
      id: 'sum.sale_item_line', severity: 'warn',
      title: 'مبلغِ قلم با «مقدار × قیمت» بیش از یک افغانی فرق دارد (قاعدهٔ گردکردن هنوز در همهٔ برنامه‌ها یکی نیست)',
      sql: `SELECT i.account_id, 'saleItems/' || i.row_id AS ref,
                   concat_ws(' ', i.data->>'quantity', '×', i.data->>'unitPrice', '≠', i.data->>'totalPrice') AS value
          FROM ${R} i
         WHERE ${sumWhere('i')} AND i.table_name = 'saleItems'
           AND ${isNum('i', 'quantity')} AND ${isNum('i', 'unitPrice')} AND ${isNum('i', 'totalPrice')}
           AND abs(${num('i', 'totalPrice')} - ${num('i', 'quantity')} * ${num('i', 'unitPrice')}) > 1`,
    },
    {
      id: 'sum.returned_qty', severity: 'error',
      title: 'مقدارِ مرجوعیِ قلم با جمعِ ردیف‌های مرجوعی نمی‌خواند یا از مقدارِ فروخته بیشتر است',
      sql: `SELECT i.account_id, 'saleItems/' || i.row_id AS ref,
                   concat_ws(' ', i.data->>'returnedQty', '/', i.data->>'quantity', 'returns', COALESCE(r.sum, 0)::text) AS value
          FROM ${R} i
          LEFT JOIN (SELECT account_id, data->>'saleItemId' AS item_id, sum(CASE WHEN jsonb_typeof(data->'quantity') = 'number' THEN (data->>'quantity')::numeric END) AS sum
                       FROM ${R} WHERE app = 'shop' AND account_kind = 'shop' AND table_name = 'returns'
                        AND deleted_at IS NULL AND jsonb_typeof(data->'quantity') = 'number'
                        AND ($1::text IS NULL OR account_id = $1)
                      GROUP BY 1, 2) r ON r.account_id = i.account_id AND r.item_id = i.row_id
         WHERE ${sumWhere('i')} AND i.table_name = 'saleItems' AND ${isNum('i', 'quantity')}
           AND (abs(COALESCE(${num('i', 'returnedQty')}, 0) - COALESCE(r.sum, 0)) >= 0.0005
             OR COALESCE(${num('i', 'returnedQty')}, 0) < 0
             OR COALESCE(${num('i', 'returnedQty')}, 0) > ${num('i', 'quantity')})`,
    },
  ].map((c) => ({ ...c, id: src.name === 'v1' ? c.id : `${c.id}@v3`, source: src }));
}

const ORDER_SCOPE = (a) => `($1::text IS NULL OR ${a}.shop_id = $1)`;
const ORDER_CHECKS = [
  {
    id: 'sum.order_lines', severity: 'error',
    title: 'ردیفِ سفارش ≠ گرد(مقدار × قیمت)، یا جمعِ سفارش ≠ جمعِ ردیف‌ها، یا کل ≠ جمع − تخفیف + کرایه، یا شمارِ اقلام نمی‌خواند',
    //  همان `lineTotalMinor`ِ order-states.js: floorDiv(milli×minor + 50_000, 100_000) × 100
    sql: `SELECT o.shop_id AS account_id, 'online_orders/' || o.id AS ref,
                 concat_ws(' ', o.subtotal_minor, COALESCE(l.sum, 0), o.total_minor, o.items_count, COALESCE(l.n, 0), COALESCE(l.bad, 0)) AS value
        FROM online_orders o
        LEFT JOIN (SELECT order_id, sum(line_total_minor) AS sum, count(*) AS n,
                          count(*) FILTER (WHERE line_total_minor <> floor((qty_milli::numeric * unit_price_minor + 50000) / 100000) * 100) AS bad
                     FROM online_order_lines GROUP BY order_id) l ON l.order_id = o.id
       WHERE ${ORDER_SCOPE('o')}
         AND (COALESCE(l.bad, 0) > 0 OR o.subtotal_minor <> COALESCE(l.sum, 0) OR o.items_count <> COALESCE(l.n, 0)
           OR o.total_minor <> o.subtotal_minor - o.discount_minor + o.delivery_fee_minor)`,
  },
  {
    id: 'sum.order_refunds', severity: 'error',
    title: 'جمعِ بازپرداخت‌های سفارش از مبلغِ کلِ آن بیشتر است',
    sql: `SELECT o.shop_id AS account_id, 'online_orders/' || o.id AS ref, r.sum::text || ' > ' || o.total_minor AS value
        FROM online_orders o
        JOIN (SELECT order_id, sum(amount_minor) AS sum FROM online_order_refunds GROUP BY order_id) r ON r.order_id = o.id
       WHERE ${ORDER_SCOPE('o')} AND r.sum > o.total_minor`,
  },
  {
    id: 'orphan.order_tenant', severity: 'error',
    title: 'ردیف، رویداد، بازپرداخت یا ارسالِ سفارش با shop_idِ دکانِ دیگری (مرزِ دکان)',
    sql: `SELECT x.shop_id AS account_id, x.kind || '/' || x.id AS ref, x.order_shop AS value FROM (
        SELECT 'online_order_lines' AS kind, c.id, c.shop_id, o.shop_id AS order_shop FROM online_order_lines c JOIN online_orders o ON o.id = c.order_id WHERE c.shop_id <> o.shop_id
        UNION ALL SELECT 'online_order_events', c.id, c.shop_id, o.shop_id FROM online_order_events c JOIN online_orders o ON o.id = c.order_id WHERE c.shop_id <> o.shop_id
        UNION ALL SELECT 'online_order_refunds', c.id, c.shop_id, o.shop_id FROM online_order_refunds c JOIN online_orders o ON o.id = c.order_id WHERE c.shop_id <> o.shop_id
        UNION ALL SELECT 'delivery_assignments', c.id, c.shop_id, o.shop_id FROM delivery_assignments c JOIN online_orders o ON o.id = c.order_id WHERE c.shop_id <> o.shop_id
      ) x WHERE ($1::text IS NULL OR x.shop_id = $1 OR x.order_shop = $1)`,
  },
  {
    id: 'orphan.storefront_product', severity: 'error',
    title: 'کالای ویترین که در دفترِ دکان هیچ ردیفی ندارد',
    sql: `SELECT sp.shop_id AS account_id, 'storefront_products/' || sp.product_id AS ref, sp.published::text AS value
        FROM storefront_products sp
       WHERE ${ORDER_SCOPE('sp')}
         AND NOT EXISTS (SELECT 1 FROM sync_rows r WHERE r.app = 'shop' AND r.account_kind = 'shop'
                            AND r.account_id = sp.shop_id AND r.table_name = 'products' AND r.row_id = sp.product_id)`,
  },
  {
    id: 'append_only.order_events', severity: 'error',
    title: 'رویدادهای سفارش از ۱ و پیوسته نیستند، نخستین «ثبت» نیست، یا آخرین وضعیت/نقدِ رویدادها با سفارش نمی‌خواند',
    sql: `SELECT o.shop_id AS account_id, 'online_orders/' || o.id AS ref,
                 concat_ws(' ', COALESCE(e.n, 0), COALESCE(e.mx, 0), e.first_kind, e.last_status, o.status, e.last_cod, o.cod_status) AS value
        FROM online_orders o
        LEFT JOIN (
          SELECT order_id, count(*) AS n, min(seq) AS mn, max(seq) AS mx,
                 (array_agg(kind ORDER BY seq))[1] AS first_kind,
                 (array_agg(to_status ORDER BY seq DESC) FILTER (WHERE kind IN ('created', 'status')))[1] AS last_status,
                 (array_agg(to_status ORDER BY seq DESC) FILTER (WHERE kind = 'cod'))[1] AS last_cod
            FROM online_order_events GROUP BY order_id) e ON e.order_id = o.id
       WHERE ${ORDER_SCOPE('o')}
         AND (e.n IS NULL OR e.mn <> 1 OR e.mx <> e.n OR e.first_kind <> 'created'
           OR e.last_status IS DISTINCT FROM o.status
           OR (e.last_cod IS NOT NULL AND e.last_cod <> o.cod_status))`,
  },
  {
    id: 'append_only.order_refunds', severity: 'error',
    title: 'شماره‌های بازپرداختِ سفارش از ۱ و پیوسته نیستند (ردیفی پاک شده)',
    sql: `SELECT o.shop_id AS account_id, 'online_orders/' || o.id AS ref, r.n::text || '/' || r.mx AS value
        FROM online_orders o
        JOIN (SELECT order_id, count(*) AS n, min(seq) AS mn, max(seq) AS mx FROM online_order_refunds GROUP BY order_id) r
          ON r.order_id = o.id
       WHERE ${ORDER_SCOPE('o')} AND (r.mn <> 1 OR r.mx <> r.n)`,
  },
];

//  دفترِ موجودیِ سرور (migrations/083) و سندهای دفترِ سفارش (095)
const STOCK_CHECKS = [
  {
    id: 'sum.stock_levels', severity: 'error',
    title: 'موجودیِ هر جا (stock_levels) با جمعِ حرکت‌های دفترِ موجودی (stock_moves) نمی‌خواند',
    sql: `SELECT COALESCE(l.shop_id, m.shop_id) AS account_id,
                 concat_ws('/', COALESCE(l.product_id, m.product_id), COALESCE(l.location, m.location)) AS ref,
                 COALESCE(l.on_hand_milli, 0)::text || ' ≠ ' || COALESCE(m.sum, 0)::text AS value
        FROM (SELECT * FROM stock_levels l WHERE ($1::text IS NULL OR l.shop_id = $1)) l
        FULL JOIN (SELECT shop_id, product_id, branch_id, location, sum(delta_on_hand_milli) AS sum
                     FROM stock_moves WHERE location <> '' AND ($1::text IS NULL OR shop_id = $1)
                    GROUP BY 1, 2, 3, 4) m
          ON m.shop_id = l.shop_id AND m.product_id = l.product_id AND m.branch_id = l.branch_id AND m.location = l.location
       WHERE COALESCE(l.on_hand_milli, 0) <> COALESCE(m.sum, 0)`,
  },
  {
    id: 'sum.stock_reserved', severity: 'error',
    title: 'رزروهای باز با جمعِ حرکت‌های رزرو/آزادسازیِ دفترِ موجودی نمی‌خوانند',
    sql: `SELECT COALESCE(r.shop_id, m.shop_id) AS account_id, COALESCE(r.product_id, m.product_id) AS ref,
                 COALESCE(r.sum, 0)::text || ' ≠ ' || COALESCE(m.sum, 0)::text AS value
        FROM (SELECT shop_id, product_id, branch_id, sum(qty_milli) AS sum FROM stock_reservations
               WHERE status = 'held' AND ($1::text IS NULL OR shop_id = $1) GROUP BY 1, 2, 3) r
        FULL JOIN (SELECT shop_id, product_id, branch_id, sum(delta_reserved_milli) AS sum FROM stock_moves
                    WHERE ($1::text IS NULL OR shop_id = $1) GROUP BY 1, 2, 3) m
          ON m.shop_id = r.shop_id AND m.product_id = r.product_id AND m.branch_id = r.branch_id
       WHERE COALESCE(r.sum, 0) <> COALESCE(m.sum, 0)`,
  },
  {
    id: 'orphan.order_ledger_doc', severity: 'error',
    title: 'سندِ دفترِ سفارش (فاکتور/برگشتِ سریِ W) به سفارشی ناموجود یا سفارشِ دکانِ دیگر اشاره می‌کند',
    sql: `SELECT d.shop_id AS account_id, 'order_ledger_docs/' || d.id AS ref, d.order_id AS value
        FROM order_ledger_docs d LEFT JOIN online_orders o ON o.id = d.order_id
       WHERE ($1::text IS NULL OR d.shop_id = $1) AND (o.id IS NULL OR o.shop_id <> d.shop_id)`,
  },
];

async function tableExists(q, name) {
  const r = await q(`SELECT to_regclass($1) IS NOT NULL AS ok`, [`public.${name}`]);
  return r.rows[0].ok === true;
}

/** فهرستِ همهٔ بررسی‌ها — برای `--list` و آزمون. */
function listChecks() {
  return [...SOURCES.flatMap(ledgerChecks), ...ORDER_CHECKS, ...STOCK_CHECKS, { id: 'append_only.hash_chain', severity: 'error', title: 'زنجیرهٔ هشِ سابقه شکسته است' }]
    .map(({ id, severity, title }) => ({ id, severity, title }));
}

/**
 * همهٔ بررسی‌ها (یا فقط `only`)، روی همهٔ دکان‌ها (یا فقط `shopId`).
 * ⇒ { ok, errors, warnings, checks: [{ id, severity, title, count, samples, ms, skipped? }] }
 */
async function verify({ shopId = null, only = null, samples = SAMPLES, hashChain = true } = {}) {
  const want = (id) => !only || !only.length || only.some((o) => id === o || id.startsWith(`${o}.`) || id.startsWith(`${o}@`) || id.split('@')[0] === o);
  const out = [];
  await db.tx(async (c) => {
    await c.query('SET TRANSACTION READ ONLY');
    //  PGlite آمارِ planner ندارد (autovacuum نیست) و بی آمار، join های این بررسی‌ها را
    //  حلقهٔ تو در تو می‌دود — O(n²): ۷ هزار ردیف ۹۶ ثانیه (سنجیده). بی حلقهٔ تو در تو
    //  همه hash/merge join می‌شوند و خطی‌اند، با آمار یا بی آن. فقط همین تراکنش.
    await c.query('SET LOCAL enable_nestloop = off');
    const q = (sql, params) => c.query(sql, params);
    const present = {};
    for (const t of ['sync_rows', 'oplog', 'sync_v3_rows', 'sync_v3_ops', 'online_orders', 'online_order_refunds', 'delivery_assignments', 'storefront_products', 'stock_moves', 'stock_levels', 'stock_reservations', 'order_ledger_docs']) {
      present[t] = await tableExists(q, t);
    }
    const checks = [
      ...SOURCES.filter((s) => present[s.rows] && present[s.ops]).flatMap(ledgerChecks),
      ...(present.online_orders && present.online_order_refunds && present.delivery_assignments && present.storefront_products ? ORDER_CHECKS : []),
      ...(present.stock_moves && present.stock_levels && present.stock_reservations ? STOCK_CHECKS.filter((c) => !c.id.startsWith('orphan.order_ledger')) : []),
      ...(present.order_ledger_docs && present.online_orders ? STOCK_CHECKS.filter((c) => c.id.startsWith('orphan.order_ledger')) : []),
    ];
    for (const ch of checks) {
      if (!want(ch.id)) continue;
      const t0 = Date.now();
      const r = await q(
        `SELECT count(*) OVER () AS n, x.account_id, x.ref, x.value FROM (${ch.sql}) x ORDER BY x.account_id, x.ref LIMIT ${Math.max(1, Number(samples) || SAMPLES)}`,
        [shopId]
      );
      out.push({
        id: ch.id, severity: ch.severity, title: ch.title,
        count: r.rows.length ? Number(r.rows[0].n) : 0,
        samples: r.rows.map((x) => ({ shop: x.account_id, ref: x.ref, value: x.value === null ? null : String(x.value).slice(0, 120) })),
        ms: Date.now() - t0,
      });
    }
  });

  if (hashChain && want('append_only.hash_chain')) {
    //  verifyAll خودش فقط می‌خواند (همان scripts/verify-chain.js)
    const t0 = Date.now();
    const { verifyAll } = require('./hash-chain-store');
    const rep = await verifyAll({ rows: true });
    const broken = rep.results.filter((x) => !x.ok && (!shopId || String(x.chain).includes(shopId)));
    out.push({
      id: 'append_only.hash_chain', severity: 'error', title: 'زنجیرهٔ هشِ سابقه شکسته است',
      count: broken.length, samples: broken.slice(0, samples).map((x) => ({ shop: '', ref: x.chain, value: `#${x.seq} ${x.reason}` })),
      ms: Date.now() - t0,
    });
  }
  const errors = out.filter((x) => x.severity === 'error').reduce((s, x) => s + x.count, 0);
  const warnings = out.filter((x) => x.severity === 'warn').reduce((s, x) => s + x.count, 0);
  return { ok: errors === 0, errors, warnings, shopId: shopId || null, at: db.now(), checks: out };
}

module.exports = { verify, listChecks, MONEY_FIELDS, QTY_FIELDS, RELATIONS, POSTED };
