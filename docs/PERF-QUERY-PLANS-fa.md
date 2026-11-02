# PERF-03 — نقشهٔ پرس‌وجو و نمایهٔ مسیرهای پرتکرار

> v3 §۲۹.۸ (`PERF-03`)، نسخهٔ 2.14.5، ۱۴۰۵/۰۸/۱۱. ابزار: `server/scripts/query-plans.js`؛ نگهبان: `server/test/query-plans.test.js`
> (فقط PostgreSQL)؛ نمایه‌ها: `server/migrations/198_perf03_indexes.sql` (برگشت در `migrations/rollback/`).
> بار، اوج و p50/p95/p99 (PERF-02) و استخر/صف/نشتِ حافظه/backpressure (PERF-04) این‌جا **نیست**.

## اجرا

```sh
cd server
node scripts/query-plans.js --db postgres://shop:shoppass@127.0.0.1:5432/shop_plans --before --json plans.json --md plans.md
node scripts/query-plans.js --db … --scale 0.3 --only orders,sync      # کوچک‌تر / فقط چند بخش (area یا id)
TEST_DATABASE_URL=postgres://… node --test test/query-plans.test.js     # نگهبان (روی PGlite رد می‌شود)
```

⛔ ابزار پایگاه را **پاک می‌کند** (`DROP SCHEMA public`) و فقط نامی با perf/plan/bench/test یا `pglite:memory` را
می‌پذیرد. گام‌ها: همهٔ مهاجرت‌ها ⇐ دادهٔ ساختگی با SQL ِ سمتِ سرور (`generate_series`، درهم‌سازِ ثابت: هر اجرا همان داده)
⇐ `ANALYZE` ⇐ برای هر پرس‌وجو دو بار `EXPLAIN (ANALYZE, BUFFERS)` (اولی گرم‌کردن) در تراکنشی که برمی‌گردد؛ `UPDATE` ِ
کارگرها هم اثری نمی‌گذارد. `--before` همان را یک بار هم با برداشتنِ نمایه‌های ۱۹۸ (در همان تراکنشِ برگشتی) و متنِ
پیشینِ پرس‌وجوهای بازنویسی‌شده (`beforeSql`) می‌سنجد. متنِ هر پرس‌وجو همان کد است و `src` نشانیِ آن؛ خلاصهٔ سفارش و
صفِ میز مستقیم از خودِ کد (`orders.SUMMARY_SQL`، `orderDesk.QUEUE_SQL`) خوانده می‌شوند. **عوض شدنِ پرس‌وجوی پرتکرار در کد ⇒ همین فهرست هم.**

## داده (مقیاسِ ۱، ۱٫۴۲ میلیون ردیف، ۴۸۶ مگابایت)

چهار دکان با سهمِ ۶۰/۲۵/۱۰/۵ درصد. `online_orders` ۶۰ هزار (یک سال؛ ۱٪ ِ تازه‌ترین باز در هر هشت وضعیتِ باز؛
۸۰٪ ویترین؛ ۱۵٪ زمان‌دار؛ ۸ هزار شمارهٔ مشترک)، قلم ۱۵۰ هزار، رویداد ۲۳۸ هزار. Sync v1 برای A کامل و B سی درصد:
`sync_rows` ۲۵۳ هزار (۱۰ هزار کالا، ۳۰ هزار فروش، ۷۵ هزار قلم، ۷۵ هزار حرکتِ کالا، ورودِ انبار، برگشت) و `oplog`
۱۱۷ هزار. Sync v3 برای C: `sync_v3_rows` ۱۱۸ هزار (با `stockCounts`) و `sync_v3_ops` ۷۰ هزار. کاتالوگِ ویترینِ A
(۱۰ هزار، ۹۰٪ منتشر)، گفت‌وگو ۴ هزار و پیام ۶۰ هزار، ممیزی ۱۰۰ هزار، صندوقِ پیامِ مشتری ۸۱ هزار و دکان ۶۰ هزار
(چند ده ردیفِ سررسیده)، اعلان ۵۰ هزار (یک تحویل برای هر دکان/کاربر) و رویدادِ دکان ۵۰ هزار.

ماشین: PostgreSQL 16.15 ِ محلی با تنظیمِ پیش‌فرض (`shared_buffers` 128MB، `work_mem` 4MB، `random_page_cost` 4).
ماشینِ مشترک است و زمان از اجرایی به اجرای دیگر تا دو برابر جابه‌جا می‌شود؛ داوری روی **شکلِ نقشه، بافر و ردیف‌های
دورریخته** است، نه میلی‌ثانیه.

## داوری

| داوری | یعنی |
|---|---|
| `seq-scan` | Seq Scan روی جدولی با ≥۱۰ هزار ردیف |
| `wide-scan` | ≥۵۰۰۰ ردیفِ دورریخته (Filter/Recheck/Join Filter) و ≥۲۰ برابرِ ردیف‌های برگشتی |
| `slow` | بیش از ۲۰ms |
| `ok` | هیچ‌کدام |

## نتیجه: ۴۶ پرس‌وجو، پیش (2.14.4) ⇐ پس (۱۹۸ و بازنویسیِ دو پرس‌وجو)

داوری‌ها: پیش ۳۲ `ok`، ۸ `seq-scan`، ۶ `wide-scan` ⇐ پس ۴۰ `ok`، ۱ `seq-scan`، ۴ `wide-scan`، ۱ `slow`. (`catalog.categories`
«پیش» ۱۰٫۸ms و «پس» ۲۲٫۷ms با همان نقشه و همان ۵۳۷ بافر است — نوسانِ ماشین، نه پسرفت.)

| پرس‌وجو | پیش (ms / داوری) | پس (ms / داوری) | هزینه | بافر | نمایه |
|---|---|---|---|---|---|
| `catalog.firstPage` | 0.23 / ok | 0.32 / ok | 1121→1121 | 167→167 | ix_sync_rows_shop_product_name، ix_storefront_products_published |
| `catalog.search` | 14.8 / wide-scan | 22.11 / wide-scan | 4133→4133 | 537→537 | ix_storefront_products_published، sync_rows_pkey |
| `catalog.category` | 6.55 / wide-scan | 11.69 / wide-scan | 1237→1237 | 537→537 | ix_storefront_products_published، sync_rows_pkey |
| `catalog.categories` | 10.81 / ok | 22.67 / slow | 1169→1169 | 537→537 | ix_storefront_products_published، sync_rows_pkey |
| `orders.list.new` | 0.07 / ok | 0.07 / ok | 182→182 | 22→22 | ix_online_orders_shop_status |
| `orders.list.preparing` | 0.2 / ok | 0.34 / ok | 467→467 | 29→29 | ix_online_orders_shop_status |
| `orders.list.scheduled` | 0.05 / ok | 0.05 / ok | 14→14 | 3→3 | ix_online_orders_shop_scheduled، ix_online_orders_open_status |
| `orders.list.done` | 0.23 / ok | 0.32 / ok | 15→15 | 85→85 | ix_online_orders_updated_all |
| `orders.summary` | 16.86 / seq-scan | 0.53 / ok | 4172→971 | 1940→50 | ix_online_orders_shop_updated، ix_online_orders_shop_status |
| `orderDesk.queueOf` | 15.62 / seq-scan | 0.18 / ok | 3231→592 | 1940→33 | ix_online_orders_shop_status |
| `orders.detail.lines` | 0.03 / ok | 0.03 / ok | 12→12 | 4→4 | ix_online_order_lines_order |
| `orders.detail.events` | 0.04 / ok | 0.02 / ok | 14→14 | 4→4 | online_order_events_order_id_seq_key |
| `storefront.myOrders` | 0.05 / ok | 0.04 / ok | 28→19 | 8→8 | ix_online_orders_shop_phone |
| `orderRisk.duplicateOf` | 0.04 / ok | 0.04 / ok | 13→13 | 7→7 | ix_online_orders_storefront_phone |
| `orderRisk.countOf` | 3.56 / wide-scan | 0.04 / ok | 2409→41 | 1908→10 | ix_online_orders_shop_phone |
| `orderRisk.profile` | 7.64 / seq-scan | 0.06 / ok | 2990→42 | 1940→10 | ix_online_orders_shop_phone |
| `promoGuard.firstOrder` | 7.52 / seq-scan | 0.07 / ok | 316→2 | 1940→4 | ix_online_orders_shop_phone |
| `orderRate.recent` | 0.04 / ok | 0.03 / ok | 13→13 | 6→6 | ix_online_orders_storefront_phone |
| `orders.slotCounts` | 0.05 / ok | 0.04 / ok | 70→70 | 14→14 | ix_online_orders_shop_scheduled |
| `reports.onlineOrders` | 26.42 / seq-scan | 35.81 / seq-scan | 9351→9351 | 3675→3675 | ix_online_orders_storefront_created |
| `reports.ownerSales` | 48.88 / wide-scan | 40.47 / wide-scan | 9699→9699 | 903→903 | sync_rows_pkey |
| `v1.pull` | 0.68 / ok | 0.32 / ok | 137→137 | 31→31 | idx_oplog_account_seq |
| `v1.head` | 0.07 / ok | 0.03 / ok | 0→0 | 4→4 | idx_oplog_account_seq |
| `v1.pushRowLock` | 0.06 / ok | 0.03 / ok | 8→8 | 6→6 | sync_rows_pkey |
| `v1.deleteOwner` | 0.05 / ok | 0.05 / ok | 8→8 | 4→4 | idx_oplog_row |
| `v3.pull` | 0.32 / ok | 0.23 / ok | 32→32 | 15→15 | idx_sync_v3_ops_seq |
| `v3.pushRowLock` | 0.07 / ok | 0.04 / ok | 8→8 | 5→5 | sync_v3_rows_pkey |
| `v3.sumMoves` | 21.43 / seq-scan | 0.04 / ok | 5518→397 | 3292→9 | ix_sync_v3_rows_moves_product |
| `v3.lateCount` | 0.28 / ok | 0.39 / ok | 1618→1618 | 40→40 | sync_v3_rows_pkey |
| `v3.barcodeDup` | 3.86 / wide-scan | 0.22 / ok | 3880→292 | 218→18 | ix_sync_v3_rows_product_barcodes |
| `roleRows.v1.saleItems` | 0.07 / ok | 0.07 / ok | 3086→3086 | 17→17 | idx_sync_rows_sale_items_by_sale |
| `roleRows.v1.moved` | 33.53 / seq-scan | 0.05 / ok | 10935→2981 | 6188→17 | ix_sync_rows_stock_ref |
| `roleRows.v1.taken` | 1.05 / ok | 0.05 / ok | 4533→65 | 99→18 | ix_sync_rows_stock_ref |
| `roleRows.v1.returns` | 0.83 / ok | 0.55 / ok | 2594→2594 | 56→56 | sync_rows_pkey |
| `roleRows.v3.moved` | 23.16 / seq-scan | 0.13 / ok | 6071→1757 | 3368→17 | ix_sync_v3_rows_stock_ref |
| `roleRows.v3.taken` | 0.85 / ok | 0.04 / ok | 2682→43 | 66→18 | ix_sync_v3_rows_stock_ref |
| `worker.customerNotify` | 0.46 / ok | 0.46 / ok | 21→21 | 365→357 | ix_customer_notify_due |
| `worker.orderNotify` | 0.59 / ok | 0.4 / ok | 312→312 | 300→293 | ix_order_notify_due |
| `worker.orderTimeout` | 0.11 / ok | 0.13 / ok | 8→8 | 21→21 | ix_online_orders_accept_deadline |
| `worker.orderStartAlert` | 0.35 / ok | 0.36 / ok | 437→437 | 84→84 | ix_online_orders_shop_scheduled، online_order_events_order_id_seq_key |
| `chat.inbox` | 0.76 / ok | 1.16 / ok | 1225→1225 | 686→686 | idx_shop_chat_conv_inbox، online_orders_pkey، shop_chat_messages_conversation_id_seq_key، shop_chat_messages_pkey |
| `chat.changes` | 0.09 / ok | 0.12 / ok | 42→42 | 12→12 | idx_shop_chat_msg_rev |
| `audit.shopList` | 0.09 / ok | 0.09 / ok | 9→9 | 7→7 | idx_audit_created |
| `audit.once` | 1.75 / wide-scan | 1.6 / wide-scan | 493→493 | 162→162 | idx_audit_created |
| `notices.inbox` | 0.56 / ok | 0.48 / ok | 423→423 | 205→205 | idx_ndeliv_user، idx_ndeliv_tenant |
| `events.list` | 0.05 / ok | 0.05 / ok | 92→92 | 5→5 | idx_events_shop |

## یافته‌ها

### رفع شد

| پرس‌وجو (چند بار) | پیش | کار | پس |
|---|---|---|---|
| `v3.sumMoves` — موجودیِ کالا پس از **هر** حرکتِ منفی، یعنی هر قلمِ فروشِ دکانِ v3 (`sync-v3.js`) | Seq Scan ِ همهٔ `sync_v3_rows`، ۳۲۹۲ بافر، ~۲۱ms | `ix_sync_v3_rows_moves_product` | ۹ بافر، ۰٫۰۴ms |
| `roleRows.v1.moved` / `v3.moved` — داوریِ push ِ صندوق‌دار با `refId` ِ حرکتِ کالا (`role-rows.js`) | Seq Scan ِ همهٔ `sync_rows` (۶۱۸۸ بافر، ~۳۳ms) / `sync_v3_rows` (~۲۳ms) | `ix_sync_rows_stock_ref`، `ix_sync_v3_rows_stock_ref` | ۱۷ بافر، ≤۰٫۱ms |
| `roleRows.*.taken` — ورودِ انبار با `refId` | همهٔ ورودهای انبارِ دکان (~۱ms، با رشدِ دفتر خطی) | همان دو نمایه (`table_name` ستونِ دومِ آن‌ها) | ۰٫۰۵ms |
| `v3.barcodeDup` — بارکدِ تکراری برای هر کالای تازه/ویرایش‌شده؛ ورودِ گروهیِ کالا ⇒ n² | همهٔ کالاهای دکان با `@>` (۶۰۰۰ ردیفِ دورریخته) | `ix_sync_v3_rows_product_barcodes` (GIN، `jsonb_path_ops`) | ۰٫۲ms |
| `orderRisk.countOf` («مشتریِ آشنا»، هر ثبتِ ویترین با `shop.order_risk`)، `orderRisk.profile` (نمای خطر)، `promoGuard.firstOrder` (اولین خرید، مشتریِ تازه) | همهٔ سفارش‌های دکان (۱۹۴۰ بافر؛ نمایهٔ تلفنِ موجود جزئیِ `source='storefront'` است و این پرس‌وجوها `source` ندارند) | `ix_online_orders_shop_phone` | ۴ تا ۱۰ بافر، <۰٫۱ms |
| `orders.summary` — خلاصهٔ میزِ سفارش، هر ۲۰ ثانیه برای هر میزِ باز | Seq Scan ِ همهٔ سفارش‌های دکان (۱۹۴۰ بافر، ~۱۷ms) | **بازنویسی، بی نمایه**: فقط `status = ANY(OPEN_STATUSES)` (هر برگهٔ باز فقط همین‌ها را دارد) و `rev` با زیرپرسشِ جدا | ۵۰ بافر، ۰٫۵ms |
| `orderDesk.queueOf` — توقفِ خودکار، هر دقیقه و پس از هر سفارش | همان (~۱۶ms) | **بازنویسی**: همان شرطِ `status` ِ دو شمارش در WHERE | ۳۳ بافر، ۰٫۲ms |

برابریِ نتیجهٔ دو بازنویسی با پرس‌وجوی پیشین را نگهبان روی هر چهار دکان، دکانِ بی‌سفارش و سه «اکنون» می‌سنجد؛
آزمون‌های موجودِ سفارش و میز سبزند: روی PostgreSQL ‏`online-orders`، `order-desk`، `order-start-alert`، `order-presence`،
`order-market-fields`، `perf-catalog` و `migrate-eol` (۴۶/۴۶)، و روی PGlite ‏`online-orders`، `order-desk`، `order-start-alert` و
`perf-catalog` (۳۱/۳۱).

### سالم بود (بی تغییر)

کاتالوگِ صفحهٔ نخست (نمایهٔ ۱۱۳)، هر شش برگهٔ میزِ سفارش، جزئیاتِ سفارش (قلم و رویداد)، «سفارش‌های من»، تکرارِ سفارش،
سقفِ ثبت، ظرفیتِ بازه، Sync v1 (pull، head، قفلِ ردیفِ push، سازندهٔ ردیف)، Sync v3 (pull، قفلِ ردیف)، قلم‌های فروشِ
داوری (نمایهٔ ۱۱۹)، هر چهار کارگرِ polling (صندوقِ پیامِ مشتری و دکان، ردِ خودکار، هشدارِ شروع)، صندوقِ گفت‌وگو و تغییرهای
آن، ممیزیِ دکان، اعلانِ درون‌برنامه و رویدادهای دکان.

### مانده (این PR نمایه نمی‌گذارد)

- **کاتالوگ با جست‌وجو، دسته و فهرستِ دسته‌ها** (`catalog.search` ~۱۵–۲۲ms، `catalog.category` ~۷–۱۴ms، `catalog.categories`
  ~۱۱–۲۳ms برای ۱۰ هزار کالا؛ خطی با شمارِ کالا). `categories()` در هر باز شدنِ ویترین است (`routes/storefront.js`).
  نمایهٔ جزئیِ `(account_id, data->>'category', name)` آزموده شد: به کار رفت ولی planner آمارِ عبارتِ نمایهٔ **جزئی** را
  نمی‌خواند (۴۹ ردیف به جای ۱۵۶۸)، Hash Join با Seq Scan ِ `storefront_products` ماند و فقط ~۲ برابر تند شد؛ آمارِ چندستونی
  (`CREATE STATISTICS … (table_name, data->>'category')`) هم برآورد را فقط به ۸۱ رساند. پس در ۱۹۸ نیامد. راه‌ها: ستونِ دسته در
  کاتالوگِ واحدِ S1 (`CATALOG_FROM`)، نگه‌داشتنِ فهرستِ دسته‌ها با `rev` ِ دکان، و برای `LIKE '%…%'` نمایهٔ trigram (افزونهٔ
  `pg_trgm`؛ PGlite را جدا بسنجید).
- **گزارشِ سفارشِ آنلاین** (`reports.onlineOrders`، ۳۰ روز، ~۲۶–۴۰ms): نمایهٔ لازم هست (`online_order_events (order_id, seq)`)
  ولی planner با `random_page_cost = 4` Hash Join و Seq Scan ِ همهٔ رویدادها را ارزان‌تر می‌بیند؛ همان پرس‌وجو با Nested Loop
  روی همان نمایه ~۱۵ms است. تنظیمِ پایگاهِ SSD (`random_page_cost ≈ 1.1`) کارِ استقرار است (`docker-compose.yml` تنظیمی ندارد)
  و پیش از آن باید روی همهٔ این فهرست دوباره سنجیده شود.
- **گزارشِ روزانهٔ صاحب** (`reports.ownerSales`، ~۴۰–۵۰ms): همهٔ فروش‌های دکان را با `createdAt` ِ درونِ jsonb می‌خواند؛
  روزی یک بار برای هر دکان — پذیرفتنی؛ نمایهٔ عبارتی روی جدولِ پرنوشتنِ فروش ارزشش را ندارد.
- `audit.once` (~۱٫۶ms، ممیزیِ ۳۰ روزِ همهٔ دکان‌ها با `idx_audit_created`) و `v3.lateCount` (پیشوندِ کلیدِ `stockCounts`؛
  ۰٫۴ms با ۹۰۰ شمارش و خطی با آن) — پذیرفتنی؛ با رشدِ شمارش دوباره بسنجید.
- **نمایهٔ تکراری**: `ix_online_order_lines_order (order_id, line_no)` همان `UNIQUE (order_id, line_no)` است (۴٫۶ مگابایت
  برای ۱۵۰ هزار قلم، هزینهٔ هر نوشتن). برداشتنش مهاجرتِ جدا می‌خواهد؛ ۱۹۸ فقط افزودنی است.

## نمایه‌های ۱۹۸

| نمایه | جدول | اندازه در این داده | پرس‌وجو |
|---|---|---|---|
| `ix_sync_v3_rows_moves_product` | `sync_v3_rows (account_id, data->>'productId')` جزئی: `app='shop'`، `stockMovements` | ۶۹۶ kB | `v3.sumMoves` |
| `ix_sync_rows_stock_ref` | `sync_rows (account_id, table_name, data->>'refId')` جزئی: `app='shop'`، حرکتِ کالا و ورودِ انبار | ۳٫۲ MB | `roleRows.v1.moved/taken` |
| `ix_sync_v3_rows_stock_ref` | همان روی `sync_v3_rows` | ۱٫۵ MB | `roleRows.v3.moved/taken` |
| `ix_sync_v3_rows_product_barcodes` | `sync_v3_rows` GIN `(data->'barcodes') jsonb_path_ops` جزئی: `products` | ۱۳۶ kB | `v3.barcodeDup` |
| `ix_online_orders_shop_phone` | `online_orders (shop_id, customer_phone)` | ۱٫۶ MB | `orderRisk.countOf/profile`، `promoGuard.firstOrder` |

همه `CREATE INDEX IF NOT EXISTS`. **CONCURRENTLY نه**: `src/migrate.js` هر مهاجرت را با ردیفِ `schema_migrations` اش در یک
تراکنش می‌زند و `CREATE INDEX CONCURRENTLY` درونِ تراکنش ممکن نیست؛ هیچ مهاجرتِ پیشینی هم آن را ندارد. ساخت پیش از بالا
آمدنِ سرور است و تا پایانش نوشتن روی همان جدول می‌ایستد. نمایه‌های Sync جزئیِ `app='shop'` اند ⇒ ردیف‌های پمپ هزینه‌ای
نمی‌دهند.

## نگهبان: `server/test/query-plans.test.js`

چهار بند فقط PostgreSQL (روی PGlite `skip` با دلیل، همان قاعدهٔ `backup.test.js`). مقیاسِ ۰٫۰۲ ِ همان داده و `EXPLAIN` بی ANALYZE
با `enable_seqscan = off` (روی دادهٔ کوچک Seq Scan به حق ارزان‌تر است؛ این‌جا پرسش «نمایه هنوز به کار می‌آید؟» است):

0. (همه‌جا، بی پایگاه) `orders.OPEN_STATUSES` دقیقاً وضعیت‌هایی است که `tabOf` به برگهٔ باز می‌برد و همان فهرستِ `NOT IN` ِ «done».
1. هر پرس‌وجوی `expect` دار (۲۹ تا از ۴۶) یکی از نمایه‌های چشم‌داشته‌اش را به کار می‌برد؛ خلاصه و صف با شرطِ `status` در نمایه.
2. هر پنج نمایهٔ ۱۹۸ در پایگاه هست و دست‌کم یک پرس‌وجوی نگهبان دارد.
3. پسرفت دیده می‌شود: متنِ پیشینِ خلاصه/صف و نبودِ نمایه‌های ۱۹۸ دقیقاً همان ۱۱ پرس‌وجو را رد می‌کنند.
4. خلاصه و صفِ بازنویسی‌شده همان نتیجهٔ پیشین را می‌دهند (چهار دکان + دکانِ بی‌سفارش × سه «اکنون»).

نتیجهٔ محلی: PostgreSQL 16.15 ‏۵/۵ سبز؛ PGlite ‏۱ سبز و ۴ رد‌شده (skip).
