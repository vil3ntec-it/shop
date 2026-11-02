-- ============================================================
--  Rollback ِ 193_storefront_eta.sql — فقط با دست، پس از برگرداندنِ کد.
--
--  ⚠️ فقط تنظیمِ زمانِ تقریبیِ تحویلِ دکان‌ها پاک می‌شود؛ سفارش‌ها و ناحیه‌ها دست نمی‌خورند.
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/193_storefront_eta.down.sql
-- ============================================================

DROP TABLE IF EXISTS storefront_eta;
DELETE FROM schema_migrations WHERE version = '193_storefront_eta.sql';
