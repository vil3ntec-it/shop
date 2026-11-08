-- ============================================================
--  Rollback ِ 305_return_line_index.sql — فقط با دست.
--
--  فقط نمایه‌ها برداشته می‌شوند؛ هیچ داده‌ای نمی‌رود و سقفِ برگشتِ قلم (`src/lib/return-line-cap.js`) بی آن‌ها هم درست
--  کار می‌کند — فقط کندتر (پیمایشِ برگشت‌های دکان). پس از اجرای دوبارهٔ ۳۰۵ همان نمایه‌ها از نو ساخته می‌شوند.
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/305_return_line_index.down.sql
-- ============================================================

DROP INDEX IF EXISTS ix_sync_v3_rows_return_item;
DROP INDEX IF EXISTS ix_sync_rows_return_item;
DROP INDEX IF EXISTS ix_sale_returns_item;
DELETE FROM schema_migrations WHERE version = '305_return_line_index.sql';
