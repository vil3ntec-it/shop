-- ============================================================
--  Rollback ِ 465_sync_v3_counts_product.sql — فقط با دست.
--
--  فقط نمایه برداشته می‌شود؛ رفتار همان است و جست‌وجوی «شمارشِ دیررسیده» دوباره همهٔ شمارش‌های دکان را می‌خواند.
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/465_sync_v3_counts_product.down.sql
-- ============================================================

DROP INDEX IF EXISTS ix_sync_v3_rows_counts_product;

DELETE FROM schema_migrations WHERE version = '465_sync_v3_counts_product.sql';
