-- ============================================================
--  Rollback ِ 199_visual_search.sql — فقط با دست، پس از برگرداندنِ کد.
--
--  ⚠️ فقط نمایهٔ هشِ عکسِ کالاها پاک می‌شود (از عکس‌ها دوباره ساختنی است)؛ خودِ عکس‌ها دست نمی‌خورند.
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/199_visual_search.down.sql
-- ============================================================

DROP TABLE IF EXISTS product_visual_hashes;
DELETE FROM schema_migrations WHERE version = '199_visual_search.sql';
