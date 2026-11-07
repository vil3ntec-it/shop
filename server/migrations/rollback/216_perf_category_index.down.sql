-- ============================================================
--  Rollback ِ 216_perf_category_index.sql — فقط با دست. فقط نمایه است؛ داده دست نمی‌خورد.
--
--  اجرا:
--    psql "$DATABASE_URL" -1 -f migrations/rollback/216_perf_category_index.down.sql
-- ============================================================

DROP INDEX IF EXISTS ix_sync_rows_shop_product_category;
DELETE FROM schema_migrations WHERE version = '216_perf_category_index.sql';
