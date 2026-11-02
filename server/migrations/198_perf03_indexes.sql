-- ============================================================
--  PERF-03 — نمایه‌های مسیرهای پرتکرار (v3 §۲۹.۸)
--
--  هر نمایه را یک نقشهٔ `EXPLAIN (ANALYZE, BUFFERS)` روی دادهٔ ساختگیِ واقع‌نما خواسته است
--  (`node scripts/query-plans.js --before`؛ جدول و عددها در `docs/PERF-QUERY-PLANS-fa.md`):
--
--  ix_sync_v3_rows_moves_product     `sync-v3.js sumMoves()` — موجودیِ کالا پس از **هر** حرکتِ منفی (هر قلمِ فروش
--                                    در دکانِ v3): Seq Scan روی همهٔ `sync_v3_rows` (۱۱۸ هزار ردیف، ~۲۵ms).
--  ix_sync_rows_stock_ref            `role-rows.js v1Db.moved/taken` — داوریِ push ِ صندوق‌دار (برگشت/لغو) با
--  ix_sync_v3_rows_stock_ref         `refId` ِ حرکتِ کالا و ورودِ انبار: Seq Scan روی همهٔ `sync_rows`/`sync_v3_rows`.
--  ix_sync_v3_rows_product_barcodes  `sync-v3.js after()` — بارکدِ تکراری برای هر کالای تازه/ویرایش‌شده: همهٔ کالاهای
--                                    دکان با `@>` خوانده می‌شد (ورودِ گروهیِ کالا ⇒ n²).
--  ix_online_orders_shop_phone       `order-risk.js` (هر ثبتِ ویترین: «مشتریِ آشنا»، نمای خطر) و
--                                    `storefront-promo-guard.js` (اولین خرید): همهٔ سفارش‌های دکان خوانده می‌شد؛
--                                    نمایهٔ `ix_online_orders_storefront_phone` جزئی است (`source='storefront'`) و این
--                                    پرس‌وجوها `source` ندارند.
--
--  کاتالوگِ «دسته»/جست‌وجو این‌جا نمایه نگرفت: نمایهٔ جزئیِ عبارتیِ دسته به کار رفت ولی planner آمارِ نمایهٔ جزئی را
--  نمی‌خواند (۴۹ ردیف به جای ۱۵۶۸) و باز Seq Scan ِ storefront_products ماند ⇒ کارِ جدا (سند، «مانده»).
--
--  ⛔ CONCURRENTLY نه: هر مهاجرت با ردیفِ `schema_migrations` اش یک تراکنش است (`src/migrate.js`) و
--  CREATE INDEX CONCURRENTLY درونِ تراکنش ممکن نیست؛ همهٔ مهاجرت‌های پیشین هم همین‌طور‌اند. ساختِ نمایه
--  نوشتن روی همان جدول را تا پایانِ ساخت نگه می‌دارد (پیش از بالا آمدنِ سرور).
--  فقط افزودنی: نه جدولی، نه ستونی. نمایه‌های Sync جزئی‌اند (`app='shop'`) ⇒ ردیف‌های پمپ و Sync ِ آن دست نمی‌خورند.
--  برگشت: migrations/rollback/198_perf03_indexes.down.sql
-- ============================================================

CREATE INDEX IF NOT EXISTS ix_sync_v3_rows_moves_product
  ON sync_v3_rows (account_id, (data->>'productId'))
  WHERE app = 'shop' AND table_name = 'stockMovements';

CREATE INDEX IF NOT EXISTS ix_sync_rows_stock_ref
  ON sync_rows (account_id, table_name, (data->>'refId'))
  WHERE app = 'shop' AND table_name IN ('stockMovements', 'warehouseEntries');

CREATE INDEX IF NOT EXISTS ix_sync_v3_rows_stock_ref
  ON sync_v3_rows (account_id, table_name, (data->>'refId'))
  WHERE app = 'shop' AND table_name IN ('stockMovements', 'warehouseEntries');

CREATE INDEX IF NOT EXISTS ix_sync_v3_rows_product_barcodes
  ON sync_v3_rows USING gin ((data->'barcodes') jsonb_path_ops)
  WHERE app = 'shop' AND table_name = 'products';

CREATE INDEX IF NOT EXISTS ix_online_orders_shop_phone
  ON online_orders (shop_id, customer_phone);
