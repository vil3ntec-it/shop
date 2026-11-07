-- ============================================================
--  Rollback ِ 226_storefront_name_trgm.sql — فقط با دست. فقط نمایه است؛ داده دست نمی‌خورد.
--
--  افزونه‌ها (`pg_trgm`، `btree_gin`) می‌مانند: `pg_trgm` را نمایهٔ بازار (مهاجرتِ ۱۱۴) هم به کار می‌برد و
--  ماندنِ افزونهٔ بی‌کار هیچ اثری ندارد. برداشتنِ `btree_gin` (اگر جز این نمایه چیزی آن را نمی‌خواهد) با دست:
--    DROP EXTENSION IF EXISTS btree_gin;
--
--  اجرا:
--    psql "$DATABASE_URL" -1 -f migrations/rollback/226_storefront_name_trgm.down.sql
-- ============================================================

DROP INDEX IF EXISTS ix_sync_rows_product_name_trgm;
DELETE FROM schema_migrations WHERE version = '226_storefront_name_trgm.sql';
