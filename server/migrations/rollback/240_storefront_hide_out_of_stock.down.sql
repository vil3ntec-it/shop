-- ============================================================
--  Rollback ِ 240_storefront_hide_out_of_stock.sql — فقط با دست.
--
--  تنظیمِ «کالای ناموجود را پنهان کن» فراموش می‌شود؛ ویترین به رفتارِ پیشین برمی‌گردد (ناموجود با نشانِ «ناموجود»).
--  پیش از اجرا کدِ پیش از این نسخه را برگردانید.
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/240_storefront_hide_out_of_stock.down.sql
-- ============================================================

ALTER TABLE storefronts DROP COLUMN IF EXISTS hide_out_of_stock;
DELETE FROM schema_migrations WHERE version = '240_storefront_hide_out_of_stock.sql';
