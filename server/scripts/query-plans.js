#!/usr/bin/env node
'use strict';
/**
 * PERF-03 — نقشهٔ پرس‌وجوی مسیرهای پرتکرار و نمایه‌ها (`docs/PERF-QUERY-PLANS-fa.md`).
 *
 *   node scripts/query-plans.js --db postgres://shop:shoppass@127.0.0.1:5432/shop_plans
 *   node scripts/query-plans.js --db … --scale 0.3            # دادهٔ کوچک‌تر (پیش‌فرض ۱)
 *   node scripts/query-plans.js --db … --before               # «پیش/پس»: نمایه‌های مهاجرتِ ۱۹۸ در تراکنشی برگشتی برداشته می‌شوند
 *   node scripts/query-plans.js --db … --json out.json --md out.md --only orders,sync
 *
 * گام‌ها: طرحِ پایگاه از نو (همان `resetDatabase` ِ آزمون‌ها: DROP SCHEMA public) ⇐ همهٔ مهاجرت‌ها ⇐
 * دادهٔ ساختگیِ واقع‌نما برای چهار دکان (سفارشِ آنلاین با قلم و رویداد، ردیف‌های Sync v1/v3 و oplog،
 * کاتالوگِ ویترین، گفت‌وگو، ممیزی، صندوقِ پیامِ مشتری/دکان، اعلان) با SQL ِ سمتِ سرور (generate_series،
 * درهم‌سازِ ثابت ⇒ هر اجرا همان داده) ⇐ ANALYZE ⇐ `EXPLAIN (ANALYZE, BUFFERS)` ِ پرس‌وجوهای فهرستِ `QUERIES`
 * (هر کدام با نشانیِ پرونده/تابعِ همان پرس‌وجو در کد) در تراکنشی که برمی‌گردد (UPDATE ِ کارگرها هم اثری نمی‌گذارد).
 * گزارش: زمان، هزینه، بافر، Seq Scan روی جدولِ بزرگ، ردیف‌های دورریخته و پیشنهادِ نمایه.
 *
 * ⛔ پایگاه را **پاک می‌کند**: فقط `pglite:memory` یا پایگاهی که نامش perf/plan/bench/test دارد.
 * هیچ دادهٔ واقعی‌ای این‌جا نیست. مرجع PostgreSQL است؛ PGlite را به آن تعمیم نده (۲۹.۶ بند ۸).
 * نگهبانِ ماندگار: `test/query-plans.test.js` (فقط PostgreSQL) — پرس‌وجوهای کلیدی نمایهٔ خودشان را دارند.
 */
const fs = require('fs');
const path = require('path');

const DAY = 86_400_000;
const BIG_ROWS = 10_000;          //  جدولِ «بزرگ» برای هشدارِ Seq Scan
const SLOW_MS = 20;
const SHOPS = Object.freeze({ A: 'qp-shop-a', B: 'qp-shop-b', C: 'qp-shop-c', D: 'qp-shop-d' });

//  ── درهم‌سازِ ثابت (بی random): همان داده در هر اجرا و هر پایگاه ──
const hx = (expr, salt) => `abs(hashint8((${expr})::bigint * 31 + ${salt}))`;
//  سهمِ دکان‌ها از سفارش/ممیزی/اعلان: A ۶۰٪، B ۲۵٪، C ۱۰٪، D ۵٪
const shopOf = (i) => `(CASE WHEN ${i} % 20 < 12 THEN '${SHOPS.A}' WHEN ${i} % 20 < 17 THEN '${SHOPS.B}'
  WHEN ${i} % 20 < 19 THEN '${SHOPS.C}' ELSE '${SHOPS.D}' END)`;
const pad = (expr, n) => `lpad((${expr})::text, ${n}, '0')`;

const DONE = ['rejected', 'auto_rejected', 'cancelled_by_customer', 'cancelled', 'picked_up', 'delivered',
  'returned_to_store', 'restocked', 'return_requested', 'refunded_partial', 'refunded_full'];
const OPEN = ['new', 'accepted', 'picking', 'packed', 'ready_for_pickup', 'out_for_delivery', 'delivery_failed', 'retry_scheduled'];
const lit = (xs) => `ARRAY[${xs.map((x) => `'${x}'`).join(',')}]`;

/** اندازه‌های پایه در مقیاسِ ۱ (هر جدول ≤ ~۲۵۰ هزار ردیف). */
function sizesOf(scale) {
  const n = (base, min = 20) => Math.max(min, Math.round(base * scale));
  return {
    orders: n(60_000, 200), conversations: n(4_000), messages: n(60_000, 200), audit: n(100_000, 200),
    deliveries: n(50_000, 200), events: n(50_000, 200), notices: n(200, 5),
    v1: { products: n(10_000, 60), sales: n(30_000, 100), saleItems: n(75_000, 250), stockMovements: n(75_000, 250),
      warehouseEntries: n(3_000, 30), returns: n(1_500, 20) },
  };
}

/**
 * دادهٔ ساختگی را می‌کارد و `ctx` ِ پرس‌وجوها را برمی‌گرداند. `run(sql, params)` همان `db.query`.
 * دکانِ A و B روی Sync v1 (`sync_rows`/`oplog`)، دکانِ C روی v3 (`sync_v3_rows`/`sync_v3_ops`).
 */
async function seed(run, { scale = 1, now = Date.now(), log = () => {} } = {}) {
  const S = sizesOf(scale);
  const N = S.orders;
  const openFrom = N - Math.max(40, Math.ceil(N * 0.01));     //  ۱٪ ِ تازه‌ترین سفارش‌ها باز است (دست‌کم ۴۰)
  const step = async (name, sql, params = []) => {
    const t = Date.now();
    const r = await run(sql, params);
    log(`  ${name.padEnd(28)} ${String(r.rowCount ?? '').padStart(8)} ردیف  ${Date.now() - t}ms`);
  };

  await step('users/shops', `
    WITH u AS (INSERT INTO users (id, name, email, created_at, updated_at)
               SELECT 'qp-user-' || k, 'صاحبِ ' || k, 'qp-' || k || '@example.invalid', $1, $1 FROM unnest(ARRAY['a','b','c','d']) k RETURNING id)
    INSERT INTO shops (id, owner_user_id, name, created_at, updated_at)
    SELECT 'qp-shop-' || substr(id, 9), id, 'دکانِ ' || substr(id, 9), $1, $1 FROM u`, [now]);

  //  ── سفارش‌ها: یک سال، تازه‌ترین ۱٪ باز؛ ۸۰٪ ویترین؛ ۱۵٪ زمان‌دار؛ ۸ هزار شماره میانِ دکان‌ها ──
  await step('online_orders', `
    WITH s AS (
      SELECT i, $1::bigint - ((${N} - i)::bigint * ${365 * DAY} / ${N}) - (${hx('i', 9)} % 60000) AS created,
             i > ${openFrom} AS is_open, ${hx('i', 4)} % 100 AS r
        FROM generate_series(1, ${N}) i)
    INSERT INTO online_orders (id, shop_id, order_no, client_key, request_hash, source, is_test, status, fulfillment,
           payment_method, customer_name, customer_phone, items_count, subtotal_minor, total_minor,
           scheduled_for, accept_deadline_at, created_at, updated_at)
    SELECT 'qo' || ${pad('i', 8)}, ${shopOf('i')}, i, 'ck' || i, 'h',
           CASE WHEN ${hx('i', 1)} % 10 < 8 THEN 'storefront' ELSE 'manual' END, i % 250 = 0, st,
           CASE WHEN ${hx('i', 2)} % 10 < 7 THEN 'delivery' ELSE 'pickup' END, 'cod', 'مشتری',
           '+93700' || ${pad(`${hx('i', 3)} % 8000`, 6)}, 1 + i % 4, 50000, 50000,
           CASE WHEN ${hx('i', 5)} % 100 < 15 THEN created + (1 + r % 48) * 3600000 END,
           CASE WHEN st = 'new' THEN created + 600000 END,
           created, CASE WHEN is_open THEN created + 60000 ELSE created + (1 + r % 5) * 3600000 END
      FROM s, LATERAL (SELECT CASE WHEN is_open THEN (${lit(OPEN)})[1 + r % 8]
                         WHEN r < 70 THEN 'delivered' WHEN r < 85 THEN 'picked_up' WHEN r < 90 THEN 'cancelled_by_customer'
                         WHEN r < 95 THEN 'rejected' ELSE 'auto_rejected' END AS st) x`, [now]);
  await step('online_order_lines', `
    INSERT INTO online_order_lines (id, order_id, shop_id, line_no, product_id, name, qty_milli, unit_price_minor, line_total_minor)
    SELECT o.id || '-' || k, o.id, o.shop_id, k, 'p' || ${pad(`1 + ${hx('o.order_no * 7 + k', 6)} % ${S.v1.products}`, 6)},
           'کالا', 1000, 5000, 5000
      FROM online_orders o, generate_series(1, o.items_count) k WHERE o.id LIKE 'qo%'`);
  await step('online_order_events', `
    INSERT INTO online_order_events (id, order_id, shop_id, seq, kind, from_status, to_status, actor_type, detail, created_at)
    SELECT o.id || '-e' || k, o.id, o.shop_id, k, CASE WHEN k = 1 THEN 'created' ELSE 'status' END,
           CASE k WHEN 2 THEN 'new' WHEN 3 THEN 'accepted' WHEN 4 THEN 'packed' ELSE '' END,
           CASE k WHEN 1 THEN '' WHEN 2 THEN 'accepted' WHEN 3 THEN 'packed' ELSE o.status END,
           CASE WHEN k = 1 THEN 'customer' ELSE 'user' END, '{}'::jsonb, o.created_at + k * 600000
      FROM online_orders o, generate_series(1, CASE WHEN o.status = ANY(${lit(DONE)}) THEN 4 ELSE 1 END) k
     WHERE o.id LIKE 'qo%'`);

  //  ── Sync v1: دکانِ A کامل، B سی درصد ──
  for (const [shop, f] of [[SHOPS.A, 1], [SHOPS.B, 0.3]]) {
    const z = Object.fromEntries(Object.entries(S.v1).map(([k, v]) => [k, Math.max(10, Math.round(v * f))]));
    const ins = (table, n, idExpr, dataExpr, deleted = 'NULL') => step(`sync_rows ${table} ${shop.slice(-1)}`, `
      INSERT INTO sync_rows (app, account_kind, account_id, table_name, row_id, data, deleted_at, created_at, updated_at)
      SELECT 'shop', 'shop', $1, '${table}', ${idExpr}, ${dataExpr}, ${deleted}, $2, $2 FROM generate_series(1, ${n}) i`, [shop, now]);
    const sale = (i) => `'s' || ${pad(`1 + ((${i} - 1)::bigint * ${z.sales} / ${z.saleItems})`, 7)}`;
    const prod = (i, salt) => `'p' || ${pad(`1 + ${hx(i, salt)} % ${z.products}`, 6)}`;
    await ins('products', z.products, `'p' || ${pad('i', 6)}`, `jsonb_build_object(
        'name', (ARRAY['برنج','روغن','چای سبز','چاي سیاه','صابون','آب','کیک','شیر'])[1 + i % 8] || ' ' || i,
        'salePrice', 10 + i % 90, 'category', (ARRAY['خوراکی','بهداشتی','نوشیدنی','لبنیات','نان','میوه'])[1 + i % 6],
        'active', i % 40 <> 0, 'barcodes', jsonb_build_array('62' || ${pad('i', 10)}))`,
    `CASE WHEN i % 97 = 0 THEN $2::bigint END`);
    await ins('sales', z.sales, `'s' || ${pad('i', 7)}`, `jsonb_build_object(
        'createdAt', $2::bigint - ((${z.sales} - i)::bigint * ${365 * DAY} / ${z.sales}),
        'status', CASE WHEN i % 50 = 0 THEN 'cancelled' ELSE 'done' END, 'total', 500 + i % 9000)`);
    await ins('saleItems', z.saleItems, `'si' || ${pad('i', 8)}`,
      `jsonb_build_object('saleId', ${sale('i')}, 'productId', ${prod('i', 7)}, 'quantity', 1 + i % 3)`);
    await ins('stockMovements', z.stockMovements, `'sm' || ${pad('i', 8)}`,
      `jsonb_build_object('productId', ${prod('i', 7)}, 'refId', ${sale('i')}, 'type', 'sale', 'qty', -1)`);
    await ins('warehouseEntries', z.warehouseEntries, `'w' || ${pad('i', 6)}`,
      `jsonb_build_object('refId', 'pr' || (i % 500), 'productId', ${prod('i', 8)}, 'units', 10)`);
    await ins('returns', z.returns, `'r' || ${pad('i', 6)}`,
      `jsonb_build_object('docId', 'rd' || (i / 2), 'saleId', 's' || ${pad(`1 + ${hx('i', 10)} % ${z.sales}`, 7)}, 'quantity', 1)`);
  }
  await step('oplog (A)', `
    INSERT INTO oplog (id, app, account_kind, account_id, device_id, op_id, client_ts, table_name, row_id, op_type, received_at)
    SELECT 'ol-' || table_name || '-' || row_id, app, account_kind, account_id, 'dev-' || (length(row_id) % 5),
           'op-' || table_name || '-' || row_id, $2, table_name, row_id, 'insert', $2
      FROM sync_rows WHERE account_id = $1 AND table_name IN ('products','sales','saleItems','returns')
     ORDER BY table_name, row_id`, [SHOPS.A, now]);

  //  ── Sync v3: دکانِ C (شصت درصد ِ A) با حرکت/شمارشِ کالا (`qtyMilli`) و HLC ِ صعودی ──
  {
    const z = Object.fromEntries(Object.entries(S.v1).map(([k, v]) => [k, Math.max(10, Math.round(v * 0.6))]));
    z.stockCounts = Math.max(10, Math.round(z.products * 0.15));
    const hlc = (i, t) => `${pad(`$2::bigint - ${365 * DAY} + (${i})::bigint * ${Math.floor((365 * DAY) / (z[t] + 1))}`, 15)} || '-0000-dev'`;
    const prod = (i, salt) => `'p' || ${pad(`1 + ${hx(i, salt)} % ${z.products}`, 6)}`;
    const ins = (table, idExpr, dataExpr) => step(`sync_v3_rows ${table}`, `
      INSERT INTO sync_v3_rows (app, account_kind, account_id, table_name, row_id, data, row_hlc, inserted, created_at, updated_at)
      SELECT 'shop', 'shop', $1, '${table}', ${idExpr}, ${dataExpr}, ${hlc('i', table)}, true, $2, $2
        FROM generate_series(1, ${z[table]}) i`, [SHOPS.C, now]);
    const sale = (i) => `'s' || ${pad(`1 + ((${i} - 1)::bigint * ${z.sales} / ${z.saleItems})`, 7)}`;
    await ins('products', `'p' || ${pad('i', 6)}`, `jsonb_build_object('name', 'کالا ' || i, 'salePrice', 10 + i % 90,
        'barcodes', jsonb_build_array('62' || ${pad('i', 10)}))`);
    await ins('sales', `'s' || ${pad('i', 7)}`, `jsonb_build_object('createdAt', $2::bigint - ((${z.sales} - i)::bigint * ${365 * DAY} / ${z.sales}),
        'status', 'done', 'amountMinor', 5000)`);
    await ins('saleItems', `'si' || ${pad('i', 8)}`, `jsonb_build_object('saleId', ${sale('i')}, 'productId', ${prod('i', 7)}, 'quantityMilli', 1000)`);
    await ins('stockMovements', `'sm' || ${pad('i', 8)}`, `jsonb_build_object('productId', ${prod('i', 7)}, 'refId', ${sale('i')},
        'type', 'sale', 'qtyMilli', CASE WHEN i % 25 = 0 THEN 40000 ELSE -1000 END)`);
    await ins('stockCounts', `'sc' || ${pad('i', 6)}`, `jsonb_build_object('productId', ${prod('i', 11)}, 'countedQtyMilli', 5000)`);
    await ins('warehouseEntries', `'w' || ${pad('i', 6)}`, `jsonb_build_object('refId', 'pr' || (i % 300), 'productId', ${prod('i', 8)}, 'units', 10)`);
    await ins('returns', `'r' || ${pad('i', 6)}`, `jsonb_build_object('docId', 'rd' || (i / 2), 'saleId', 's' || ${pad('1 + i', 7)}, 'quantityMilli', 1000)`);
    await step('sync_v3_ops (C)', `
      INSERT INTO sync_v3_ops (app, account_kind, account_id, op_id, device_id, hlc, table_name, row_id, kind, effect, status, received_at)
      SELECT app, account_kind, account_id, 'v3-' || table_name || '-' || row_id, 'dev-' || (length(row_id) % 5), row_hlc,
             table_name, row_id, 'insert', '{}'::jsonb, 'applied', $2
        FROM sync_v3_rows WHERE account_id = $1 AND table_name IN ('products','sales','stockMovements','stockCounts')
       ORDER BY row_hlc`, [SHOPS.C, now]);
  }

  //  ── ویترین: کالاهای A، ۹۰٪ منتشر ──
  await step('storefront_products', `
    INSERT INTO storefront_products (shop_id, product_id, published, updated_at, published_at)
    SELECT account_id, row_id, right(row_id, 1) <> '9', $2, $2 FROM sync_rows
     WHERE app = 'shop' AND account_kind = 'shop' AND account_id = $1 AND table_name = 'products'`, [SHOPS.A, now]);

  //  ── گفت‌وگو (A دو سوم، B یک سوم) ──
  const NC = S.conversations;
  const convShop = (c) => `(CASE WHEN (${c}) % 3 < 2 THEN '${SHOPS.A}' ELSE '${SHOPS.B}' END)`;
  await step('shop_chat_conversations', `
    INSERT INTO shop_chat_conversations (id, shop_id, customer_phone, customer_name, order_id, seq, rev, last_message_at, created_at, updated_at)
    SELECT 'qc' || i, ${convShop('i')}, '+93799' || ${pad('i', 6)}, 'مشتری ' || i, '', ${Math.ceil(S.messages / NC)},
           ${Math.ceil(S.messages / NC)}, $1::bigint - i::bigint * 60000, $1::bigint - ${90 * DAY}, $1::bigint - i::bigint * 60000
      FROM generate_series(1, ${NC}) i`, [now]);
  await step('shop_chat_messages', `
    INSERT INTO shop_chat_messages (id, conversation_id, shop_id, seq, rev, sender, client_id, request_hash, kind, body, created_at)
    SELECT 'qm' || i, 'qc' || c, ${convShop('c')}, 1 + (i - 1) / ${NC}, 1 + (i - 1) / ${NC},
           CASE WHEN i % 2 = 0 THEN 'shop' ELSE 'customer' END, 'm' || i, 'h', 'text', 'سلام', $1::bigint - (${S.messages} - i)::bigint * 60000
      FROM generate_series(1, ${S.messages}) i, LATERAL (SELECT 1 + (i - 1) % ${NC} AS c) x`, [now]);

  //  ── ممیزی، صندوقِ پیام و اعلان (اعلانِ همگانی: یک تحویل برای هر دکان/کاربر، نه هزاران برای یک دکان) ──
  await step('audit_logs', `
    INSERT INTO audit_logs (id, shop_id, actor_type, user_id, action, target_type, target_id, detail, created_at)
    SELECT 'qa' || i, ${shopOf('i')}, 'user', 'qp-user-a',
           (ARRAY['order.status','stock.adjust','staff.login','order.review_ack','product.update','order.start_alert'])[1 + i % 6],
           'online_order', 'qo' || ${pad(`1 + ${hx('i', 12)} % ${N}`, 8)}, '{}'::jsonb,
           $1::bigint - ((${S.audit} - i)::bigint * ${365 * DAY} / ${S.audit})
      FROM generate_series(1, ${S.audit}) i`, [now]);
  await step('customer_notify_outbox', `
    INSERT INTO customer_notify_outbox (id, shop_id, order_id, event, lang, phone_key, status, channel, attempts,
           next_attempt_at, sent_at, created_at, updated_at)
    SELECT 'qn' || o.order_no || ev, o.shop_id, o.id, ev, 'fa', o.customer_phone,
           CASE WHEN o.order_no > ${N - 40} THEN 'pending' ELSE 'sent' END, 'sms', 1,
           CASE WHEN o.order_no > ${N - 40} THEN $1::bigint - 1000 ELSE o.created_at END,
           CASE WHEN o.order_no > ${N - 40} THEN NULL ELSE o.created_at + 5000 END, o.created_at, o.created_at
      FROM online_orders o, unnest(CASE WHEN o.status = 'delivered' THEN ARRAY['order_received','order_completed']
                                        ELSE ARRAY['order_received'] END) ev
     WHERE o.id LIKE 'qo%' AND o.source = 'storefront'`, [now]);
  await step('order_notify_outbox', `
    INSERT INTO order_notify_outbox (id, shop_id, order_id, order_no, event, status, attempts, next_attempt_at, acked_at, created_at, updated_at)
    SELECT 'qd' || o.order_no, o.shop_id, o.id, o.order_no, 'order_created',
           CASE WHEN o.order_no > ${N - 20} THEN 'pending' ELSE 'acked' END, 1,
           CASE WHEN o.order_no > ${N - 20} THEN $1::bigint - 1000 ELSE o.created_at END,
           CASE WHEN o.order_no > ${N - 20} THEN NULL ELSE o.created_at + 30000 END, o.created_at, o.created_at
      FROM online_orders o WHERE o.id LIKE 'qo%'`, [now]);
  await step('notices', `
    INSERT INTO notices (id, app, title, body, status, created_at, updated_at)
    SELECT 'qnt' || i, 'shop', 'اعلان ' || i, 'متن', 'sent', $1, $1 FROM generate_series(1, ${S.notices}) i`, [now]);
  await step('notice_deliveries', `
    INSERT INTO notice_deliveries (id, notice_id, app, user_id, tenant_id, channel, status, created_at)
    SELECT 'qnd' || i, 'qnt' || (1 + i % ${S.notices}), 'shop', 'qu' || (i % 2000),
           CASE WHEN i % 250 = 1 THEN '${SHOPS.A}' ELSE 'qt' || (i % 2000) END,
           CASE WHEN i % 10 = 0 THEN 'push' ELSE 'inapp' END, CASE WHEN i % 3 = 0 THEN 'sent' ELSE 'read' END,
           $1::bigint - ((${S.deliveries} - i)::bigint * ${180 * DAY} / ${S.deliveries})
      FROM generate_series(1, ${S.deliveries}) i`, [now]);
  await step('shop_events', `
    INSERT INTO shop_events (id, shop_id, user_id, kind, title, created_at)
    SELECT 'qe' || i, ${shopOf('i')}, 'qp-user-a', 'sale', 'فروش', $1::bigint - ((${S.events} - i)::bigint * ${180 * DAY} / ${S.events})
      FROM generate_series(1, ${S.events}) i`, [now]);

  await run('ANALYZE');
  return contextOf(run, now);
}

/** پارامترهای پرس‌وجوها از خودِ داده (ثابت، چون داده ثابت است). */
async function contextOf(run, now) {
  const one = async (sql, p = []) => (await run(sql, p)).rows[0] || {};
  const order = await one(`SELECT id, customer_phone FROM online_orders WHERE shop_id = $1 AND source = 'storefront'
                            AND status = 'delivered' ORDER BY id LIMIT 1 OFFSET 100`, [SHOPS.A]);
  const v1 = await one('SELECT COALESCE(MAX(server_seq), 0)::bigint AS head FROM oplog WHERE app=$1 AND account_kind=$2 AND account_id=$3', ['shop', 'shop', SHOPS.A]);
  const v3 = await one('SELECT COALESCE(MAX(server_seq), 0)::bigint AS head, MIN(server_seq)::bigint AS low FROM sync_v3_ops WHERE app=$1 AND account_kind=$2 AND account_id=$3', ['shop', 'shop', SHOPS.C]);
  const v1low = await one('SELECT MIN(server_seq)::bigint AS low FROM oplog WHERE account_id=$1', [SHOPS.A]);
  const sales = (await run(`SELECT row_id FROM sync_rows WHERE app='shop' AND account_kind='shop' AND account_id=$1 AND table_name='sales'
                              ORDER BY row_id LIMIT 5 OFFSET 200`, [SHOPS.A])).rows.map((r) => r.row_id);
  const midHlc = await one(`SELECT row_hlc FROM sync_v3_rows WHERE app='shop' AND account_kind='shop' AND account_id=$1
                              AND table_name='stockMovements' ORDER BY row_id LIMIT 1 OFFSET 10`, [SHOPS.C]);
  const head1 = Number(v1.head || 0);
  const head3 = Number(v3.head || 0);
  const low1 = Number(v1low.low || 0);
  const low3 = Number(v3.low || 0);
  return {
    now, shops: SHOPS,
    orderId: order.id || '', phone: order.customer_phone || '',
    phoneForms: order.customer_phone ? [order.customer_phone, `0${order.customer_phone.slice(3)}`] : [],
    newPhoneForms: ['+93799000001', '0799000001'],   //  مشتریِ تازه (بی سفارش): حالتِ معمولِ «اولین خرید»
    productId: 'p000042', barcode: `62${'42'.padStart(10, '0')}`, saleIds: sales, returnIds: ['rd10', 'r000020'],
    warehouseRefs: ['pr10', 'pr11'], convId: 'qc1', userId: 'qu1',
    v1: { head: head1, mid: Math.floor((low1 + head1) / 2) }, v3: { head: head3, mid: Math.floor((low3 + head3) / 2) },
    hlc: midHlc.row_hlc || '',
  };
}

//  ════════════════════════════════════════════════════════════════════
//  فهرستِ پرس‌وجوها — متنِ هر کدام همان کد است (`src:` نشانیِ آن)؛ `expect` = نمایه‌ای که باید به کار برود
//  (آزمونِ `query-plans.test.js` همین را می‌سنجد). عوض شدنِ پرس‌وجو در کد ⇒ همین‌جا هم.
//  ════════════════════════════════════════════════════════════════════
const S_ = () => require('../src/lib/order-states');
const SHOP_ROWS = "r.app = 'shop' AND r.account_kind = 'shop' AND r.table_name = 'products'";
const LIVE_PRODUCT = "r.deleted_at IS NULL AND COALESCE(r.data->>'active', 'true') <> 'false'";
const SQL_NAME = "translate(lower(r.data->>'name'), 'يىكۀةأإآ' || chr(1600) || chr(8204), 'ییکههااا  ')";
const NOT_HIDDEN = `NOT EXISTS (SELECT 1 FROM trust_sanctions ts WHERE ts.shop_id = sp.shop_id AND ts.product_id = sp.product_id
                     AND ts.kind = 'hide_listing' AND ts.lifted_at IS NULL)`;
const PRODUCT_FROM = `storefront_products sp
  JOIN sync_rows r ON ${SHOP_ROWS} AND r.account_id = sp.shop_id AND r.row_id = sp.product_id AND ${NOT_HIDDEN}`;
const PRODUCT_COLS = `r.row_id AS id, r.data, sp.online_price_minor, sp.max_qty_milli,
  EXISTS (SELECT 1 FROM product_photos ph WHERE ph.app = 'shop' AND ph.account_id = sp.shop_id
            AND ph.product_id = r.row_id AND ph.data IS NOT NULL) AS has_photo`;
const ACC = 'app=$1 AND account_kind=$2 AND account_id=$3';
const V1_BASE = "app='shop' AND account_kind='shop' AND account_id=$1";
const acc = (shop) => ['shop', 'shop', shop];
const CHAT_VIEW = `SELECT c.*, s.name AS shop_name, o.order_no,
      (SELECT COUNT(*)::int FROM shop_chat_messages m
        WHERE m.conversation_id = c.id AND m.sender = 'customer'
          AND m.deleted_at IS NULL AND m.seq > c.shop_read_seq) AS unread,
      EXISTS (SELECT 1 FROM shop_chat_blocks b WHERE b.shop_id = c.shop_id
               AND b.customer_phone = c.customer_phone AND b.blocker = 'shop') AS blocked_by_shop,
      EXISTS (SELECT 1 FROM shop_chat_blocks b WHERE b.shop_id = c.shop_id
               AND b.customer_phone = c.customer_phone AND b.blocker = 'customer') AS blocked_by_customer,
      (SELECT json_build_object('kind', m.kind, 'body', LEFT(m.body, 80), 'sender', m.sender,
                                'deleted', m.deleted_at IS NOT NULL, 'at', m.created_at)
         FROM shop_chat_messages m WHERE m.conversation_id = c.id ORDER BY m.seq DESC LIMIT 1) AS last,
      (SELECT COALESCE(json_agg(json_build_object('messageId', p.message_id, 'seq', pm.seq, 'at', p.created_at)
                                ORDER BY p.created_at DESC, p.message_id), '[]'::json)
         FROM shop_chat_pins p JOIN shop_chat_messages pm ON pm.id = p.message_id
        WHERE p.conversation_id = c.id AND p.side = 'shop' AND pm.deleted_at IS NULL) AS pinned
    FROM shop_chat_conversations c
    JOIN shops s ON s.id = c.shop_id
    LEFT JOIN online_orders o ON o.id = c.order_id AND o.shop_id = c.shop_id`;
const MESSAGE_COLS = `m.*, mm.mime AS media_mime, mm.size AS media_size, tm.size AS thumb_size,
  (h.message_id IS NOT NULL) AS hidden`;
const MESSAGE_JOINS = `LEFT JOIN shop_chat_media mm ON mm.id = m.media_id AND mm.conversation_id = m.conversation_id
  LEFT JOIN shop_chat_media tm ON tm.id = m.thumb_id AND tm.conversation_id = m.conversation_id`;
const CREATED = "(CASE WHEN data->>'createdAt' ~ '^[0-9]{1,15}$' THEN (data->>'createdAt')::bigint ELSE 0 END)";
const DEAD = ['rejected', 'auto_rejected', 'cancelled_by_customer', 'cancelled'];
const MOVE_TYPES = ['sale', 'sale_cancel', 'customer_return', 'damage'];   //  کلیدهای `DOC_MOVE_TYPES` ِ lib/perm-core.js

function tabList(tab) {
  const S = S_();
  const col = { done: 'updated_at', scheduled: 'scheduled_for' }[tab] || 'created_at';
  const dir = tab === 'done' ? 'DESC' : 'ASC';
  const cond = S.tabWhere(tab, '$2');
  const at = cond.includes('$2');
  return {
    sql: `SELECT * FROM online_orders WHERE shop_id=$1 AND ${cond} ORDER BY ${col} ${dir}, id ${dir} LIMIT $${at ? 3 : 2}`,
    params: (c) => (at ? [c.shops.A, c.now, 51] : [c.shops.A, 51]),
  };
}

const QUERIES = [
  //  ── ویترین (lib/storefront.js: catalog، categories) ──
  //  نمایهٔ ۱۱۳ (`ix_sync_rows_shop_product_name`) روی دادهٔ بزرگ به کار می‌رود؛ روی دادهٔ کوچکِ آزمون planner به حق مرتب می‌کند
  { id: 'catalog.firstPage', area: 'catalog', src: 'lib/storefront.js catalog()',
    sql: `SELECT ${PRODUCT_COLS} FROM ${PRODUCT_FROM} WHERE sp.shop_id = $1 AND sp.published AND ${LIVE_PRODUCT}
      ORDER BY COALESCE(r.data->>'name', ''), r.row_id LIMIT $2`, params: (c) => [c.shops.A, 41] },
  { id: 'catalog.search', area: 'catalog', src: 'lib/storefront.js catalog() q=',
    sql: `SELECT ${PRODUCT_COLS} FROM ${PRODUCT_FROM} WHERE sp.shop_id = $1 AND sp.published AND ${LIVE_PRODUCT} AND ${SQL_NAME} LIKE $2
      ORDER BY COALESCE(r.data->>'name', ''), r.row_id LIMIT $3`, params: (c) => [c.shops.A, '%چای%', 41] },
  { id: 'catalog.category', area: 'catalog', src: 'lib/storefront.js catalog() category=',
    hint: 'نمایهٔ جزئیِ (account_id, data->>category, name) به کار می‌رود ولی planner آمارِ نمایهٔ جزئی را نمی‌خواند (۴۹ ردیف به جای ۱۵۶۸) ⇒ کاتالوگِ واحدِ S1 یا آمار؛ سند، «مانده»',
    sql: `SELECT ${PRODUCT_COLS} FROM ${PRODUCT_FROM} WHERE sp.shop_id = $1 AND sp.published AND ${LIVE_PRODUCT} AND r.data->>'category' = $2
      ORDER BY COALESCE(r.data->>'name', ''), r.row_id LIMIT $3`, params: (c) => [c.shops.A, 'نوشیدنی', 41] },
  { id: 'catalog.categories', area: 'catalog', src: 'lib/storefront.js categories()',
    sql: `SELECT DISTINCT r.data->>'category' AS c FROM ${PRODUCT_FROM}
      WHERE sp.shop_id = $1 AND sp.published AND ${LIVE_PRODUCT} AND COALESCE(r.data->>'category', '') <> ''
      ORDER BY 1 LIMIT 60`, params: (c) => [c.shops.A] },

  //  ── سفارش‌ها: میزِ سفارش، خلاصهٔ ۲۰ثانیه‌ای، جزئیات (lib/orders.js، lib/order-desk.js) ──
  { id: 'orders.list.new', area: 'orders', src: 'lib/orders.js list(tab=new)', expect: ['ix_online_orders_shop_status'], ...tabList('new') },
  { id: 'orders.list.preparing', area: 'orders', src: 'lib/orders.js list(tab=preparing)', ...tabList('preparing') },
  { id: 'orders.list.scheduled', area: 'orders', src: 'lib/orders.js list(tab=scheduled)', ...tabList('scheduled') },
  { id: 'orders.list.done', area: 'orders', src: 'lib/orders.js list(tab=done)', ...tabList('done') },
  { id: 'orders.summary', area: 'orders', src: 'lib/orders.js summary() — هر ۲۰ ثانیه برای هر میزِ سفارشِ باز',
    //  هر دو فقط سفارشِ باز را می‌خوانند؛ `expectCond`: شرطِ نمایه `status` دارد (پرس‌وجوی پیشین همان نمایه را فقط با shop_id می‌خواند)
    expect: ['ix_online_orders_shop_status', 'ix_online_orders_open_status'], expectCond: /status/,
    get sql() { return require('../src/lib/orders').SUMMARY_SQL; },
    params: (c) => [c.shops.A, c.now, require('../src/lib/orders').OPEN_STATUSES],
    //  پیش از PERF-03 (۲.۱۴.۴): همهٔ سفارش‌های دکان
    get beforeSql() {
      const S = S_();
      const cols = S.OPEN_TABS.map((tab) => `COUNT(*) FILTER (WHERE ${S.tabWhere(tab, '$2')})::int AS "${tab}"`);
      return `SELECT ${cols.join(', ')},
            MIN(created_at) FILTER (WHERE status = 'new') AS oldest,
            MAX(updated_at) AS rev
       FROM online_orders WHERE shop_id = $1`;
    },
    beforeParams: (c) => [c.shops.A, c.now] },
  { id: 'orderDesk.queueOf', area: 'orders', src: 'lib/order-desk.js queueOf() — توقفِ خودکار، هر دقیقه و پس از هر سفارش',
    expect: ['ix_online_orders_shop_status', 'ix_online_orders_open_status'], expectCond: /status/,
    get sql() { return require('../src/lib/order-desk').QUEUE_SQL; },
    params: (c) => [c.shops.A, c.now, require('../src/lib/order-desk').OPEN_STATUSES],
    beforeSql: `SELECT COUNT(*) FILTER (WHERE status = ANY($3::text[]))::int AS open,
            MIN(created_at) FILTER (WHERE status = 'new') AS oldest
       FROM online_orders
      WHERE shop_id = $1 AND is_test = false AND (scheduled_for IS NULL OR scheduled_for <= $2)` },
  { id: 'orders.detail.lines', area: 'orders', src: 'lib/orders.js loadDetail()', expect: ['ix_online_order_lines_order', 'online_order_lines_order_id_line_no_key'],
    sql: 'SELECT * FROM online_order_lines WHERE order_id=$1 ORDER BY line_no', params: (c) => [c.orderId] },
  { id: 'orders.detail.events', area: 'orders', src: 'lib/orders.js loadDetail()', expect: ['online_order_events_order_id_seq_key'],
    sql: 'SELECT * FROM online_order_events WHERE order_id=$1 ORDER BY seq', params: (c) => [c.orderId] },
  { id: 'storefront.myOrders', area: 'orders', src: 'lib/storefront.js (سفارش‌های من)',
    sql: `SELECT id, order_no, status, total_minor, items_count, created_at, track_gen FROM online_orders
      WHERE shop_id = $1 AND source = 'storefront' AND customer_phone = $2
      ORDER BY created_at DESC, id DESC LIMIT 20`, params: (c) => [c.shops.A, c.phone] },
  { id: 'orderRisk.duplicateOf', area: 'orders', src: 'lib/order-risk.js duplicateOf() (ثبتِ ویترین)',
    expect: ['ix_online_orders_storefront_phone', 'ix_online_orders_shop_phone'],
    sql: `SELECT id, order_no, fulfillment, address, created_at FROM online_orders
      WHERE customer_phone = ANY($1::text[]) AND source = 'storefront' AND shop_id = $2
        AND created_at > $3 AND created_at < $4 AND id <> $5
        AND NOT is_test AND status <> ALL($6::text[])
      ORDER BY created_at DESC LIMIT 10`,
    params: (c) => [c.phoneForms, c.shops.A, c.now - 30 * DAY, Number.MAX_SAFE_INTEGER, '', DEAD] },
  { id: 'orderRisk.countOf', area: 'orders', src: 'lib/order-risk.js countOf() — هر ثبتِ ویترین («مشتریِ آشنا»)', expect: ['ix_online_orders_shop_phone'],
    hint: 'online_orders (shop_id, customer_phone)',
    sql: `SELECT COUNT(*)::int AS n FROM online_orders
      WHERE shop_id = $1 AND customer_phone = ANY($2::text[]) AND NOT is_test AND status = ANY($3::text[])`,
    params: (c) => [c.shops.A, c.phoneForms, DEAD] },
  { id: 'orderRisk.profile', area: 'orders', src: 'lib/order-risk.js (نمای خطرِ مشتری)', expect: ['ix_online_orders_shop_phone'],
    sql: `SELECT status, status_reason, COUNT(*)::int AS n, MIN(created_at) AS first_at, MAX(created_at) AS last_at
       FROM online_orders
      WHERE shop_id = $1 AND customer_phone = ANY($2::text[]) AND id <> $3 AND NOT is_test
      GROUP BY status, status_reason`, params: (c) => [c.shops.A, c.phoneForms, c.orderId] },
  { id: 'promoGuard.firstOrder', area: 'orders', src: 'lib/storefront-promo-guard.js (اولین خرید، مشتریِ تازه)', expect: ['ix_online_orders_shop_phone'],
    hint: 'online_orders (shop_id, customer_phone)',
    sql: 'SELECT 1 FROM online_orders WHERE shop_id=$1 AND customer_phone = ANY($2::text[]) LIMIT 1',
    params: (c) => [c.shops.A, c.newPhoneForms] },
  { id: 'orderRate.recent', area: 'orders', src: 'lib/order-rate.js (سقفِ ثبتِ ویترین، دامنهٔ دکان)',
    sql: `SELECT created_at FROM online_orders
      WHERE customer_phone = ANY($1::text[]) AND source = 'storefront' AND created_at > $2 AND shop_id = $4
      ORDER BY created_at DESC OFFSET $3 LIMIT 1`, params: (c) => [c.phoneForms, c.now - DAY, 2, c.shops.A] },
  { id: 'orders.slotCounts', area: 'orders', src: 'lib/storefront-delivery.js (ظرفیتِ بازه‌ها)',
    sql: `SELECT scheduled_for AS t, COUNT(*)::int AS n FROM online_orders
      WHERE shop_id = $1 AND fulfillment = $2 AND scheduled_for >= $3 AND scheduled_for < $4
        AND status <> ALL($5::text[])
      GROUP BY scheduled_for`, params: (c) => [c.shops.A, 'delivery', c.now, c.now + 3 * DAY, DEAD] },
  { id: 'reports.onlineOrders', area: 'reports', src: 'lib/order-online-report.js report() (۳۰ روز)',
    sql: `SELECT o.id, o.created_at, o.total_minor, o.fulfillment,
            MIN(e.created_at) FILTER (WHERE e.to_status = 'accepted') AS accepted_at,
            MIN(e.created_at) FILTER (WHERE e.to_status = 'packed') AS packed_at,
            MIN(e.created_at) FILTER (WHERE e.to_status IN ('out_for_delivery','ready_for_pickup')) AS handed_at,
            MIN(e.created_at) FILTER (WHERE e.to_status = 'out_for_delivery') AS out_at,
            MIN(e.created_at) FILTER (WHERE e.to_status = 'delivered') AS delivered_at,
            MIN(e.created_at) FILTER (WHERE e.to_status IN ('delivered','picked_up')) AS done_at,
            COALESCE(BOOL_OR(e.to_status = 'delivery_failed'), false) AS failed_delivery,
            ARRAY_REMOVE(ARRAY_AGG(DISTINCT e.to_status) FILTER (WHERE e.to_status = ANY($4::text[])), NULL) AS lost
       FROM online_orders o
       LEFT JOIN online_order_events e ON e.order_id = o.id AND e.kind = 'status'
      WHERE o.shop_id = $1 AND o.source = 'storefront' AND o.is_test = false
        AND o.created_at >= $2 AND o.created_at < $3
      GROUP BY o.id`, params: (c) => [c.shops.A, c.now - 30 * DAY, c.now, [...DEAD, 'returned_to_store']] },
  { id: 'reports.ownerSales', area: 'reports', src: 'lib/owner-report.js rowsInWindow(sales، یک روز)',
    sql: `SELECT row_id, data, deleted_at FROM sync_rows
      WHERE ${V1_BASE} AND table_name='sales'
        AND ((${CREATED} >= $2 AND ${CREATED} < $3) OR (${CREATED} = 0 AND data->>'date' >= $4 AND data->>'date' <= $5))
      ORDER BY row_id LIMIT 50001`, params: (c) => [c.shops.A, c.now - DAY, c.now, '2099-01-01', '2099-01-01'] },

  //  ── Sync v1 (lib/sync-v1.js — فقط خواندنِ متن؛ پرونده دست نمی‌خورد) ──
  { id: 'v1.pull', area: 'sync', src: 'lib/sync-v1.js pull', expect: ['idx_oplog_account_seq'],
    sql: `SELECT op_id FROM oplog WHERE ${ACC} AND server_seq > $4 AND server_seq <= $5 AND device_id <> $6
      ORDER BY server_seq ASC LIMIT 1001`, params: (c) => [...acc(c.shops.A), c.v1.mid, c.v1.head, 'dev-x'] },
  { id: 'v1.head', area: 'sync', src: 'lib/sync-v1.js headSeq', expect: ['idx_oplog_account_seq'],
    sql: `SELECT COALESCE(MAX(server_seq),0)::bigint AS head FROM oplog WHERE ${ACC}`, params: (c) => acc(c.shops.A) },
  { id: 'v1.pushRowLock', area: 'sync', src: 'lib/sync-v1.js applyOp', expect: ['sync_rows_pkey'],
    sql: `SELECT * FROM sync_rows WHERE ${ACC} AND table_name=$4 AND row_id=$5 FOR UPDATE`,
    params: (c) => [...acc(c.shops.A), 'products', c.productId] },
  { id: 'v1.deleteOwner', area: 'sync', src: 'lib/sync-v1.js (سازندهٔ ردیف)', expect: ['idx_oplog_row'],
    sql: `SELECT device_id FROM oplog WHERE ${ACC} AND table_name=$4 AND row_id=$5 AND op_type='insert'
      ORDER BY server_seq ASC LIMIT 1`, params: (c) => [...acc(c.shops.A), 'sales', c.saleIds[0]] },

  //  ── Sync v3 (lib/sync-v3.js) ──
  { id: 'v3.pull', area: 'sync', src: 'lib/sync-v3.js pull', expect: ['idx_sync_v3_ops_account_seq', 'idx_sync_v3_ops_seq'],
    sql: `SELECT op_id, server_seq, device_id, origin, hlc, table_name, row_id, kind, effect
       FROM sync_v3_ops WHERE ${ACC} AND server_seq > $4 AND server_seq <= $5 AND device_id <> $6
        AND effect IS NOT NULL
      ORDER BY server_seq ASC LIMIT $7`, params: (c) => [...acc(c.shops.C), c.v3.mid, c.v3.head, 'dev-x', 501] },
  { id: 'v3.pushRowLock', area: 'sync', src: 'lib/sync-v3.js applyOne', expect: ['sync_v3_rows_pkey'],
    sql: `SELECT * FROM sync_v3_rows WHERE ${ACC} AND table_name=$4 AND row_id=$5 FOR UPDATE`,
    params: (c) => [...acc(c.shops.C), 'products', c.productId] },
  { id: 'v3.sumMoves', area: 'sync', src: 'lib/sync-v3.js sumMoves() — هر حرکتِ منفی (هر قلمِ فروش)', expect: ['ix_sync_v3_rows_moves_product'],
    hint: 'sync_v3_rows (account_id, (data->>\'productId\')) WHERE app=\'shop\' AND table_name=\'stockMovements\'',
    sql: `SELECT COALESCE(SUM((data->>'qtyMilli')::bigint), 0)::bigint AS s FROM sync_v3_rows
      WHERE ${ACC} AND table_name='stockMovements' AND inserted AND data->>'productId' = $4
        AND ($5 = '' OR row_hlc <= $5)`, params: (c) => [...acc(c.shops.C), c.productId, ''] },
  { id: 'v3.lateCount', area: 'sync', src: 'lib/sync-v3.js after() — هر حرکتِ کالا', expect: ['ix_sync_v3_rows_counts_product'],
    hint: 'sync_v3_rows (account_id, (data->>\'productId\'), row_hlc) WHERE app=\'shop\' AND table_name=\'stockCounts\' (۴۶۵)',
    sql: `SELECT row_id, row_hlc FROM sync_v3_rows
          WHERE ${ACC} AND table_name='stockCounts' AND inserted AND data->>'productId' = $4
            AND row_hlc >= $5 AND row_id <> $6
          ORDER BY row_hlc DESC LIMIT 1`, params: (c) => [...acc(c.shops.C), c.productId, c.hlc, ''] },
  { id: 'v3.barcodeDup', area: 'sync', src: 'lib/sync-v3.js after() — هر بارکدِ کالای تازه/ویرایش‌شده', expect: ['ix_sync_v3_rows_product_barcodes'],
    hint: 'sync_v3_rows USING gin ((data->\'barcodes\') jsonb_path_ops) WHERE app=\'shop\' AND table_name=\'products\'',
    sql: `SELECT row_id FROM sync_v3_rows WHERE ${ACC} AND table_name=$4 AND row_id <> $5 AND deleted_at IS NULL
            AND data->'barcodes' @> $6::jsonb ORDER BY row_id`,
    params: (c) => [...acc(c.shops.C), 'products', 'p999999', JSON.stringify([c.barcode])] },

  //  ── داوریِ ردیفِ نقش (lib/role-rows.js) — push ِ صندوق‌دار ──
  { id: 'roleRows.v1.saleItems', area: 'role-rows', src: 'lib/role-rows.js v1Db.saleItems', expect: ['idx_sync_rows_sale_items_by_sale'],
    sql: `SELECT row_id, data FROM sync_rows WHERE ${V1_BASE} AND table_name='saleItems' AND deleted_at IS NULL
            AND data->>'saleId' = ANY($2::text[]) ORDER BY row_id COLLATE "C"`, params: (c) => [c.shops.A, c.saleIds] },
  { id: 'roleRows.v1.moved', area: 'role-rows', src: 'lib/role-rows.js v1Db.moved', expect: ['ix_sync_rows_stock_ref'],
    hint: 'sync_rows (account_id, table_name, (data->>\'refId\')) WHERE app=\'shop\' AND table_name IN (\'stockMovements\',\'warehouseEntries\')',
    sql: `SELECT data FROM sync_rows WHERE ${V1_BASE} AND table_name='stockMovements' AND deleted_at IS NULL
            AND data->>'refId' = ANY($2::text[]) AND data->>'type' = ANY($3::text[]) ORDER BY row_id COLLATE "C"`,
    params: (c) => [c.shops.A, c.saleIds, MOVE_TYPES] },
  { id: 'roleRows.v1.taken', area: 'role-rows', src: 'lib/role-rows.js v1Db.taken', expect: ['ix_sync_rows_stock_ref'],
    sql: `SELECT data FROM sync_rows WHERE ${V1_BASE} AND table_name='warehouseEntries' AND deleted_at IS NULL
            AND data->>'refId' = ANY($2::text[]) ORDER BY row_id COLLATE "C"`, params: (c) => [c.shops.A, c.warehouseRefs] },
  { id: 'roleRows.v1.returns', area: 'role-rows', src: 'lib/role-rows.js v1Db.returns',
    sql: `SELECT row_id, data FROM sync_rows WHERE ${V1_BASE} AND table_name='returns' AND deleted_at IS NULL
            AND (row_id = ANY($2::text[]) OR data->>'docId' = ANY($2::text[])) ORDER BY row_id COLLATE "C"`,
    params: (c) => [c.shops.A, c.returnIds] },
  { id: 'roleRows.v3.moved', area: 'role-rows', src: 'lib/role-rows.js v3Db.moved', expect: ['ix_sync_v3_rows_stock_ref'],
    hint: 'sync_v3_rows (account_id, table_name, (data->>\'refId\')) WHERE app=\'shop\' AND table_name IN (\'stockMovements\',\'warehouseEntries\')',
    sql: `SELECT data FROM sync_v3_rows WHERE ${V1_BASE} AND table_name='stockMovements' AND inserted AND deleted_at IS NULL
            AND data->>'refId' = ANY($2::text[]) AND data->>'type' = ANY($3::text[]) ORDER BY row_id COLLATE "C"`,
    params: (c) => [c.shops.C, c.saleIds, MOVE_TYPES] },
  { id: 'roleRows.v3.taken', area: 'role-rows', src: 'lib/role-rows.js v3Db.taken', expect: ['ix_sync_v3_rows_stock_ref'],
    sql: `SELECT data FROM sync_v3_rows WHERE ${V1_BASE} AND table_name='warehouseEntries' AND inserted AND deleted_at IS NULL
            AND data->>'refId' = ANY($2::text[]) ORDER BY row_id COLLATE "C"`, params: (c) => [c.shops.C, c.warehouseRefs] },

  //  ── کارگرها (polling) ──
  { id: 'worker.customerNotify', area: 'workers', src: 'lib/customer-notify.js claimDue() هر ۵ ثانیه', expect: ['ix_customer_notify_due'],
    sql: `UPDATE customer_notify_outbox
        SET status='sending', locked_at=$1, updated_at=$1, attempts = attempts + 1
      WHERE id IN (SELECT id FROM customer_notify_outbox WHERE ((status='pending' AND next_attempt_at <= $1) OR (status='sending' AND locked_at < $2))
                    ORDER BY next_attempt_at ASC LIMIT 20)
        AND ((status='pending' AND next_attempt_at <= $1) OR (status='sending' AND locked_at < $2))
      RETURNING *`, params: (c) => [c.now, c.now - 180_000] },
  { id: 'worker.orderNotify', area: 'workers', src: 'lib/order-notify.js claimDue() هر ۲ ثانیه', expect: ['ix_order_notify_due'],
    sql: `UPDATE order_notify_outbox
        SET status='sending', locked_at=$1, updated_at=$1,
            attempts  = attempts  + CASE WHEN provider_accepted_at IS NULL THEN 1 ELSE 0 END,
            reminders = reminders + CASE WHEN provider_accepted_at IS NULL THEN 0 ELSE 1 END
      WHERE id IN (SELECT id FROM order_notify_outbox WHERE ((status IN ('pending','provider_accepted') AND next_attempt_at <= $1)
               OR (status='sending' AND locked_at < $2))
                    ORDER BY next_attempt_at ASC LIMIT 20)
        AND ((status IN ('pending','provider_accepted') AND next_attempt_at <= $1)
               OR (status='sending' AND locked_at < $2))
      RETURNING *`, params: (c) => [c.now, c.now - 180_000] },
  { id: 'worker.orderTimeout', area: 'workers', src: 'lib/order-timeout.js sweep() هر دقیقه', expect: ['ix_online_orders_accept_deadline'],
    sql: `SELECT id, shop_id, order_no FROM online_orders
      WHERE status = 'new' AND accept_deadline_at IS NOT NULL AND accept_deadline_at <= $1
      ORDER BY accept_deadline_at LIMIT $2`, params: (c) => [c.now, 100] },
  { id: 'worker.orderStartAlert', area: 'workers', src: 'lib/order-start-alert.js candidates() هر دقیقه',
    sql: `SELECT o.id, o.shop_id, o.order_no, o.scheduled_for, o.status, o.fulfillment
       FROM online_orders o
      WHERE o.status = ANY($1::text[]) AND o.is_test = false AND o.scheduled_for IS NOT NULL
        AND o.scheduled_for > $2 AND o.scheduled_for <= $3
        AND NOT EXISTS (SELECT 1 FROM online_order_events e WHERE e.order_id = o.id AND e.kind = 'note'
                          AND e.detail->>'type' = $4)
      ORDER BY o.scheduled_for, o.id
      LIMIT $5`, params: (c) => [['new', 'accepted'], c.now - 15 * 60_000, c.now + 30 * 60_000, 'start_alert', 200] },

  //  ── گفت‌وگو (lib/shop-chat.js) ──
  { id: 'chat.inbox', area: 'chat', src: 'lib/shop-chat.js list() (صندوقِ دکان)', expect: ['idx_shop_chat_conv_inbox'],
    sql: `${CHAT_VIEW} WHERE c.shop_id = $1 AND (c.seq > 0 OR c.order_id <> '') ORDER BY c.updated_at DESC, c.id DESC LIMIT $2`,
    params: (c) => [c.shops.A, 31] },
  { id: 'chat.changes', area: 'chat', src: 'lib/shop-chat.js changes()', expect: ['idx_shop_chat_msg_rev'],
    sql: `SELECT ${MESSAGE_COLS} FROM shop_chat_messages m ${MESSAGE_JOINS}
       LEFT JOIN shop_chat_hidden h ON h.message_id = m.id AND h.viewer = $3
      WHERE m.conversation_id = $1 AND m.rev > $2 ORDER BY m.rev ASC LIMIT $4`, params: (c) => [c.convId, 5, 'shop', 101] },

  //  ── ممیزی و اعلان ──
  { id: 'audit.shopList', area: 'audit', src: 'routes/shop.js (ممیزیِ دکان)', expect: ['idx_audit_shop', 'idx_audit_created'],
    sql: `SELECT id, user_id, action, target_type, target_id, detail, created_at
       FROM audit_logs WHERE shop_id=$1 ORDER BY created_at DESC LIMIT $2`, params: (c) => [c.shops.A, 100] },
  { id: 'audit.once', area: 'audit', src: 'lib/audit.js log({ once }) و lib/order-ledger.js',
    sql: 'SELECT 1 AS x FROM audit_logs WHERE shop_id=$1 AND created_at >= $4 AND action=$2 AND target_id=$3 LIMIT 1',
    params: (c) => [c.shops.A, 'order.review_ack', `${c.orderId}:review`, c.now - 30 * DAY] },
  { id: 'notices.inbox', area: 'notify', src: 'lib/notices.js inboxFor()',
    sql: `SELECT * FROM notice_deliveries
      WHERE app=$1 AND channel='inapp'
        AND (($2 <> '' AND user_id=$2) OR ($3 <> '' AND tenant_id=$3))
      ORDER BY created_at DESC LIMIT $4`, params: (c) => ['shop', c.userId, c.shops.A, 50] },
  { id: 'events.list', area: 'notify', src: 'lib/events.js list()', expect: ['idx_events_shop'],
    sql: `SELECT * FROM shop_events
        WHERE shop_id=$1 AND created_at > $2
        ORDER BY created_at DESC LIMIT $3`, params: (c) => [c.shops.A, c.now - 7 * DAY, 50] },
];

//  ════════════════════════════════════════════════════════════════════
//  اجرای EXPLAIN و داوری
//  ════════════════════════════════════════════════════════════════════

/** نمایه‌هایی که مهاجرتِ ۱۹۸ می‌سازد (از خودِ پرونده). */
function newIndexes() {
  const dir = path.join(__dirname, '..', 'migrations');
  const f = fs.readdirSync(dir).find((x) => x.startsWith('198_'));
  if (!f) return [];
  return [...fs.readFileSync(path.join(dir, f), 'utf8').matchAll(/CREATE INDEX IF NOT EXISTS (\w+)/g)].map((m) => m[1]);
}

/** گره‌های نقشه (عمق‌اول) با شمارهٔ واقعیِ ردیف و دورریخته‌ها. */
function nodesOf(plan) {
  const out = [];
  (function walk(n) {
    const loops = n['Actual Loops'] || 1;
    out.push({
      type: n['Node Type'], rel: n['Relation Name'] || '', index: n['Index Name'] || '',
      rows: (n['Actual Rows'] ?? 0) * loops,
      removed: ((n['Rows Removed by Filter'] || 0) + (n['Rows Removed by Index Recheck'] || 0) + (n['Rows Removed by Join Filter'] || 0)) * loops,
      filter: n.Filter || n['Index Cond'] || n['Recheck Cond'] || '',
      cond: n['Index Cond'] || '',
      cost: n['Total Cost'],
    });
    (n.Plans || []).forEach(walk);
  })(plan);
  return out;
}

/** نقشه نمایهٔ چشم‌داشتهٔ `q` را (با شرطِ `expectCond`، اگر هست) به کار برده است؟ */
function meets(q, nodes) {
  return nodes.some((n) => n.index && q.expect.includes(n.index) && (!q.expectCond || q.expectCond.test(n.cond)));
}

async function explainOne(client, q, ctx, { analyze = true } = {}) {
  const opts = analyze ? 'ANALYZE, BUFFERS, FORMAT JSON' : 'FORMAT JSON';
  await client.query('SAVEPOINT qp');
  try {
    const r = await client.query(`EXPLAIN (${opts}) ${q.sql}`, q.params(ctx));
    const raw = r.rows[0]['QUERY PLAN'] ?? Object.values(r.rows[0])[0];
    const root = (typeof raw === 'string' ? JSON.parse(raw) : raw)[0];
    return root;
  } finally {
    await client.query('ROLLBACK TO SAVEPOINT qp');
  }
}

function judge(q, root, big) {
  const nodes = nodesOf(root.Plan);
  const ms = root['Execution Time'] ?? null;
  const seq = nodes.filter((n) => n.type === 'Seq Scan' && (big.get(n.rel) || 0) >= BIG_ROWS);
  const returned = root.Plan['Actual Rows'] ?? 0;
  const removed = nodes.reduce((s, n) => s + n.removed, 0);
  const wide = removed >= 5000 && removed >= 20 * Math.max(1, returned);
  const verdict = seq.length ? 'seq-scan' : wide ? 'wide-scan' : (ms !== null && ms > SLOW_MS) ? 'slow' : 'ok';
  const b = root.Plan;
  return {
    id: q.id, area: q.area, src: q.src, ms: ms === null ? null : Math.round(ms * 100) / 100,
    cost: Math.round(root.Plan['Total Cost']), rows: returned, removed,
    buffers: (b['Shared Hit Blocks'] || 0) + (b['Shared Read Blocks'] || 0),
    seqScans: seq.map((n) => `${n.rel}(${n.rows + n.removed})`),
    indexes: [...new Set(nodes.filter((n) => n.index).map((n) => n.index))],
    shape: nodes.map((n) => n.type + (n.index ? `(${n.index})` : n.rel ? `(${n.rel})` : '')).join(' > '),
    verdict,
    hint: verdict === 'ok' ? '' : (q.hint || nodes.filter((n) => n.removed > 0 || n.type === 'Seq Scan').map((n) => `${n.rel || n.index}: ${n.filter}`).join(' | ')),
  };
}

async function bigTables(client) {
  const r = await client.query(`SELECT relname, reltuples::bigint AS n FROM pg_class c JOIN pg_namespace s ON s.oid = c.relnamespace
                                 WHERE s.nspname = 'public' AND c.relkind = 'r'`);
  return new Map(r.rows.map((x) => [x.relname, Number(x.n)]));
}

/** همهٔ پرس‌وجوها در یک تراکنشِ برگشتی؛ `drop` = نمایه‌هایی که پیش از سنجش برداشته می‌شوند («پیش»). */
async function runAll(client, ctx, { only = null, drop = [] } = {}) {
  const big = await bigTables(client);
  const out = [];
  await client.query('BEGIN');
  try {
    for (const ix of drop) await client.query(`DROP INDEX IF EXISTS ${ix}`);
    for (const q of QUERIES) {
      if (only && !only.has(q.area) && !only.has(q.id)) continue;
      //  «پیش»: نمایه‌های ۱۹۸ برداشته و پرس‌وجوی بازنویسی‌شده همان متنِ پیشین (`beforeSql`)
      const x = drop.length && q.beforeSql ? { ...q, sql: q.beforeSql, params: q.beforeParams || q.params } : q;
      await explainOne(client, x, ctx);                       //  گرم کردنِ cache (اجرای نخست سرد است)
      out.push(judge(x, await explainOne(client, x, ctx), big));
    }
  } finally {
    await client.query('ROLLBACK');
  }
  return out;
}

function table(rows, before) {
  const b = new Map((before || []).map((r) => [r.id, r]));
  const head = before ? '| پرس‌وجو | پیش (ms / داوری) | پس (ms / داوری) | هزینه | بافر | نمایه |' : '| پرس‌وجو | ms | هزینه | بافر | داوری | نمایه |';
  const sep = before ? '|---|---|---|---|---|---|' : '|---|---|---|---|---|---|';
  const lines = rows.map((r) => {
    const ix = r.indexes.join('، ') || (r.seqScans.length ? `Seq Scan ${r.seqScans.join('، ')}` : '—');
    if (!before) return `| \`${r.id}\` | ${r.ms} | ${r.cost} | ${r.buffers} | ${r.verdict} | ${ix} |`;
    const p = b.get(r.id) || {};
    return `| \`${r.id}\` | ${p.ms} / ${p.verdict} | ${r.ms} / ${r.verdict} | ${p.cost}→${r.cost} | ${p.buffers}→${r.buffers} | ${ix} |`;
  });
  return [head, sep, ...lines].join('\n');
}

function printReport(rows, before, meta) {
  const log = console.log;
  log(`\n── PERF-03: نقشهٔ ${rows.length} پرس‌وجو — ${meta.server} — مقیاس ${meta.scale} ──`);
  for (const r of rows) {
    const p = before && before.find((x) => x.id === r.id);
    const was = p ? `  (پیش: ${p.ms}ms ${p.verdict})` : '';
    log(`  ${r.id.padEnd(26)} ${String(r.ms).padStart(9)}ms  cost ${String(r.cost).padStart(8)}  buf ${String(r.buffers).padStart(6)}  ${r.verdict === 'ok' ? 'ok' : `⚠️ ${r.verdict}`}${was}`);
    log(`  ${''.padEnd(26)} ${r.shape}`);
  }
  const seqs = rows.filter((r) => r.seqScans.length);
  log('\n── Seq Scan روی جدولِ بزرگ ──');
  log(seqs.length ? seqs.map((r) => `  ${r.id}: ${r.seqScans.join('، ')}`).join('\n') : '  هیچ');
  log('\n── ده هزینهٔ بالا ──');
  for (const r of [...rows].sort((a, b) => b.cost - a.cost).slice(0, 10)) log(`  ${String(r.cost).padStart(9)}  ${r.id}  (${r.ms}ms)`);
  const bad = rows.filter((r) => r.verdict !== 'ok');
  log('\n── پیشنهادِ نمایه / بازبینی ──');
  log(bad.length ? bad.map((r) => `  ${r.id} [${r.verdict}]: ${r.hint}`).join('\n') : '  هیچ — همهٔ پرس‌وجوها نمایهٔ خود را دارند');
}

async function main() {
  const arg = (k, d) => { const i = process.argv.indexOf(k); return i > 0 && process.argv[i + 1] ? process.argv[i + 1] : d; };
  const url = arg('--db', process.env.QUERY_PLANS_DATABASE_URL || '');
  const scale = Number(arg('--scale', 1));
  const only = arg('--only', '') ? new Set(arg('--only', '').split(',')) : null;
  if (!url) throw new Error('--db لازم است (پایگاهی که نامش perf/plan/bench/test دارد، یا pglite:memory)');
  const name = url.startsWith('pglite:') ? url : new URL(url).pathname.slice(1);
  if (!/^pglite:memory$|perf|plan|bench|test/i.test(name)) throw new Error(`«${name}» پاک می‌شود؛ فقط perf/plan/bench/test یا pglite:memory`);
  if (!(scale > 0)) throw new Error('--scale باید مثبت باشد');
  process.env.DATABASE_URL = url;
  const db = require('../src/db');
  const migrate = require('../src/migrate');
  const t0 = Date.now();
  await db.query('DROP SCHEMA public CASCADE');
  await db.query('CREATE SCHEMA public');
  await migrate.run();
  console.log(`مهاجرت: ${Date.now() - t0}ms`);
  const ctx = await seed(db.query, { scale, log: console.log });
  try { await db.query('CHECKPOINT'); } catch { /* بی دسترسی: بی‌اهمیت */ }
  const sizes = (await db.query(`SELECT relname, reltuples::bigint AS n FROM pg_class c JOIN pg_namespace s ON s.oid = c.relnamespace
                                  WHERE s.nspname = 'public' AND c.relkind = 'r' AND reltuples >= 1000 ORDER BY 2 DESC`)).rows;
  const server = (await db.query('SHOW server_version')).rows[0].server_version;
  const client = await db.getPool().connect();
  let before = null;
  let after;
  try {
    if (process.argv.includes('--before')) before = await runAll(client, ctx, { only, drop: newIndexes() });
    after = await runAll(client, ctx, { only });
  } finally {
    client.release();
  }
  const meta = { server: `${db.isPglite() ? 'PGlite' : 'PostgreSQL'} ${server}`, scale, at: new Date().toISOString(),
    tables: Object.fromEntries(sizes.map((r) => [r.relname, Number(r.n)])), dropped: before ? newIndexes() : [] };
  printReport(after, before, meta);
  const json = arg('--json', '');
  if (json) fs.writeFileSync(json, JSON.stringify({ meta, before, after }, null, 2));
  const md = arg('--md', '');
  if (md) fs.writeFileSync(md, `${table(after, before)}\n`);
  await db.closeDb();
  if (after.some((r) => r.verdict === 'seq-scan') && process.argv.includes('--strict')) process.exitCode = 1;
}

module.exports = { QUERIES, SHOPS, seed, contextOf, explainOne, nodesOf, meets, judge, runAll, newIndexes, sizesOf };

if (require.main === module) {
  main().catch((err) => { console.error('✖', err.message); process.exit(1); });
}
