-- ============================================================
--  Rollback ِ 198_perf03_indexes.sql — فقط با دست.
--
--  فقط نمایه‌ها برداشته می‌شوند؛ هیچ داده‌ای نمی‌رود و کدِ همین نسخه بی آن‌ها هم درست کار می‌کند
--  (فقط کندتر: نقشه‌های «پیش» در docs/PERF-QUERY-PLANS-fa.md). پس از اجرای دوبارهٔ ۱۹۸ همان نمایه‌ها از نو ساخته می‌شوند.
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/198_perf03_indexes.down.sql
-- ============================================================

DROP INDEX IF EXISTS ix_sync_v3_rows_moves_product;
DROP INDEX IF EXISTS ix_sync_rows_stock_ref;
DROP INDEX IF EXISTS ix_sync_v3_rows_stock_ref;
DROP INDEX IF EXISTS ix_sync_v3_rows_product_barcodes;
DROP INDEX IF EXISTS ix_online_orders_shop_phone;
DELETE FROM schema_migrations WHERE version = '198_perf03_indexes.sql';
